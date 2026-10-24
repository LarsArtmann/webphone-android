package software.larsartmann.webphone.shell

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.call.DialpadActivity

/**
 * The shell: the stack origin's own pages render in the WebView (the
 * island login mints the session cookie; Messages/Fax/Voicemail/
 * History/Contacts/Settings keep working exactly as in the browser),
 * while the native call layer (SIP + WebRTC + Telecom) registers the
 * same extension over the same WSS gateway — the call contract the
 * platform decision moved into Kotlin. The badge + dial button are
 * the native chrome: calling is native, everything else is web.
 *
 * First run: if the stack origin is still the shipped fallback, a
 * one-time prompt routes to Settings — the app is useless against an
 * origin it invented.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusBadge: TextView

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(null, false)

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()

        statusBadge = TextView(this).apply { textSize = 14f }
        val dial = Button(this).apply { text = getString(R.string.dial) }
        val settings = Button(this).apply {
            text = getString(R.string.settings)
            textSize = 12f
        }
        val chrome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(statusBadge)
            addView(dial)
            addView(settings)
        }
        val root = FrameLayout(this)
        root.addView(webView)
        root.addView(
            chrome,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply { setMargins(48, 48, 48, 96) },
        )

        setContentView(root)
        webView.loadUrl(App.origin)
        dial.setOnClickListener { startActivity(Intent(this, DialpadActivity::class.java)) }
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        maybePromptForOrigin()

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
        if (::webView.isInitialized) CallLayer.ensureWired(this)
    }

    private fun renderRegistration(registration: CallEngine.Registration) {
        statusBadge.text = getString(
            when (registration) {
                CallEngine.Registration.REGISTERED -> R.string.registration_registered
                CallEngine.Registration.CONNECTING -> R.string.registration_connecting
                CallEngine.Registration.OFF -> R.string.registration_off
            },
        )
        statusBadge.setTextColor(
            ContextCompat.getColor(
                this,
                if (registration == CallEngine.Registration.REGISTERED) {
                    R.color.status_connected
                } else {
                    R.color.status_pending
                },
            ),
        )
        // The ring lane is dead without the notification grant on
        // API 33+ — ask in context (post-login, i.e. once registration
        // proves the session works), not on a cold launch the user
        // hasn't signed into yet.
        if (registration == CallEngine.Registration.REGISTERED &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybePromptForOrigin() {
        if (!App.originIsDefault()) return
        val prefs = getSharedPreferences(App.PREFS_SETTINGS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ORIGIN_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_ORIGIN_PROMPTED, true).apply()
        AlertDialog.Builder(this)
            .setTitle(R.string.first_run_title)
            .setMessage(R.string.first_run_message)
            .setPositiveButton(R.string.first_run_set_origin) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    override fun onDestroy() {
        CallLayer.shutdown()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    // The WebView back stack, not the activity's: tab nav inside the
    // island is the product's own navigation.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private companion object {
        const val KEY_ORIGIN_PROMPTED = "origin-prompted"
    }
}
