package software.larsartmann.webphone.shell

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.call.DialpadActivity
import software.larsartmann.webphone.ui.circleBackground
import software.larsartmann.webphone.ui.dp
import software.larsartmann.webphone.ui.dpf
import software.larsartmann.webphone.ui.iconButton
import software.larsartmann.webphone.ui.pillBackground
import software.larsartmann.webphone.ui.pulse
import software.larsartmann.webphone.ui.styleText
import software.larsartmann.webphone.ui.themeColor

/**
 * The shell: the stack origin's own pages render in the WebView (the
 * island login mints the session cookie; Messages/Fax/Voicemail/
 * History/Contacts/Settings keep working exactly as in the browser),
 * while the native call layer (SIP + WebRTC + Telecom) registers the
 * same extension over the same WSS gateway — the call contract the
 * platform decision moved into Kotlin. The floating chrome (line pill
 * + dial + settings) is the only native surface: calling is native,
 * everything else is web. The chrome is fixed dark glass so it stays
 * legible over any page the stack renders.
 *
 * The shipped default origin is the operator's live stack; Settings
 * re-points it (persisted, live teardown on save).
 */
class MainActivity : AppCompatActivity() {

    internal lateinit var webSurface: WebSurface
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var chrome: LinearLayout
    private var linePulse: ValueAnimator? = null
    private var requestedBatteryExemption = false
    private var backInvoker: OnBackInvokedCallback? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webSurface = WebSurface(this)

        // Predictive back (API 33+): the manifest enables
        // enableOnBackInvokedCallback, which SILENCES onBackPressed —
        // without this callback the system back never reached the
        // WebView's tab history (found by review, 2026-10-25).
        if (Build.VERSION.SDK_INT >= 33) {
            val invoker = OnBackInvokedCallback {
                if (::webSurface.isInitialized && webSurface.canGoBack()) {
                    webSurface.goBack()
                } else {
                    finish()
                }
            }
            backInvoker = invoker
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                invoker,
            )
        }

        // The line pill: the phone vernacular "line light" — a live
        // dot plus a short label, dark glass so it reads over any page.
        statusDot = View(this).apply {
            // Decorative: the label next to it carries the state for
            // TalkBack; a bare colored dot would only add noise.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = dp(8)
            }
        }
        statusLabel = TextView(this).apply {
            styleText(13f, ContextCompat.getColor(this@MainActivity, R.color.chrome_on_glass), medium = true)
        }
        val linePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pillBackground(ContextCompat.getColor(this@MainActivity, R.color.chrome_glass))
            setPadding(dp(14), dp(9), dp(14), dp(9))
            addView(statusDot)
            addView(statusLabel)
        }

        val dialFab = iconButton(
            iconRes = R.drawable.ic_call,
            iconTint = themeColor(R.attr.wpOnAccent),
            backgroundColor = themeColor(R.attr.wpAccent),
            sizeDp = 56,
            contentDescriptionText = getString(R.string.dial),
        ).apply { elevation = dpf(6f) }

        val settingsButton = iconButton(
            iconRes = R.drawable.ic_settings,
            iconTint = ContextCompat.getColor(this, R.color.chrome_on_glass),
            backgroundColor = ContextCompat.getColor(this, R.color.chrome_glass),
            sizeDp = 44,
            contentDescriptionText = getString(R.string.settings),
        )

        chrome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(
                linePill,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(12) },
            )
            addView(dialFab)
            addView(
                settingsButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(12) },
            )
        }
        // Float above the system bars: insets become chrome margins.
        ViewCompat.setOnApplyWindowInsetsListener(chrome) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (view.layoutParams as FrameLayout.LayoutParams).setMargins(
                dp(16),
                bars.top + dp(16),
                dp(16),
                bars.bottom + dp(16),
            )
            insets
        }

        val root = FrameLayout(this)
        root.addView(
            webSurface.container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            chrome,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply { setMargins(dp(16), dp(16), dp(16), dp(16)) },
        )

        setContentView(root)
        routeDeepLink(intent)
        webSurface.loadOriginIfChanged()
        dialFab.setOnClickListener { startActivity(Intent(this, DialpadActivity::class.java)) }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallEngine.registration.collect { renderRegistration(it) }
            }
        }

        // After the WebView session exists, resume it into SIP
        // credentials and attach the native call engine. Retried on
        // each resume (the login may have just happened).
        CallLayer.ensureWired(this)
    }

    override fun onResume() {
        super.onResume()
        webSurface.resume()
        // Settings may have changed the origin while we were away:
        // the shell MUST follow it (the save-teardown re-wires the
        // call layer; this re-aims the WebView). Found by walking
        // the settings flow on the emulator, 2026-10-24.
        webSurface.loadOriginIfChanged()
        CallLayer.ensureWired(this)
    }

    override fun onPause() {
        webSurface.pause()
        super.onPause()
    }

    private fun renderRegistration(info: CallEngine.RegistrationInfo) {
        statusLabel.text = getString(
            when {
                info.status == CallEngine.Registration.REGISTERED -> R.string.registration_registered
                info.status == CallEngine.Registration.CONNECTING -> R.string.registration_connecting
                // The cause matters: "the PBX refused you" and "no
                // route to the PBX" ask for different fixes.
                info.failure == CallEngine.RegistrationFailure.AUTH_REJECTED -> R.string.registration_auth_rejected
                else -> R.string.registration_off
            },
        )
        val registered = info.status == CallEngine.Registration.REGISTERED
        statusDot.background = circleBackground(
            ContextCompat.getColor(
                this,
                if (registered) R.color.chrome_dot_on else R.color.chrome_dot_pending,
            ),
        )
        // The signature moment: the line light breathes while the
        // line is up (skipped entirely when animations are off).
        linePulse?.cancel()
        linePulse = if (registered) {
            statusDot.alpha = 1f
            pulse(statusDot, this)
        } else {
            statusDot.alpha = 1f
            null
        }

        if (!registered) return

        // The ring lane is dead without the notification grant on
        // API 33+ — ask in context (post-login, i.e. once registration
        // proves the session works), not on a cold launch the user
        // hasn't signed into yet.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // The ntfy lane's #1 missed-ring cause is the OEM battery
        // manager killing the dataSync socket. Ask ONCE per install,
        // post-registration (the same in-context moment as the
        // notification grant); the direct dialog needs its
        // manifest-declared permission, the settings deep-link is the
        // fallback when an OEM broke that too.
        maybeRequestBatteryExemption()
    }

    private fun maybeRequestBatteryExemption() {
        if (requestedBatteryExemption) return
        val power = getSystemService(PowerManager::class.java) ?: return
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences(App.PREFS_SETTINGS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_ASKED, false)) return
        requestedBatteryExemption = true
        prefs.edit().putBoolean(KEY_BATTERY_ASKED, true).apply()
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(direct) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    override fun onDestroy() {
        backInvoker?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        linePulse?.cancel()
        CallLayer.shutdown()
        if (::webSurface.isInitialized) webSurface.destroy()
        super.onDestroy()
    }

    // API 31/32 back path (33+ rides the OnBackInvokedCallback): the
    // WebView back stack, not the activity's — tab nav inside the
    // island is the product's own navigation.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webSurface.isInitialized && webSurface.canGoBack()) {
            webSurface.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private companion object {
        const val KEY_BATTERY_ASKED = "battery_exemption_asked"
    }
}
