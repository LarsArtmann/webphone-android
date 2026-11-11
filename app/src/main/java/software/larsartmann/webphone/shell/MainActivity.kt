package software.larsartmann.webphone.shell

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
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
import androidx.appcompat.widget.AppCompatButton
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
import software.larsartmann.webphone.call.CallActivity
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.call.DialpadActivity
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import software.larsartmann.webphone.ui.circleBackground
import software.larsartmann.webphone.ui.dp
import software.larsartmann.webphone.ui.dpf
import software.larsartmann.webphone.ui.iconButton
import software.larsartmann.webphone.ui.pillBackground
import software.larsartmann.webphone.ui.pulse
import software.larsartmann.webphone.ui.styleText
import software.larsartmann.webphone.ui.themeColor

/**
 * The line pill's text pair: the visible label (registration state,
 * suffixed with the wake lane's verdict once it is known) and the
 * accessibility description that carries the REASON when the wake
 * lane is off — the why a bare "wake off" pill cannot say. File-level
 * (not a method) so the pin builds labels without an activity.
 */
internal fun linePillText(
    context: Context,
    info: CallEngine.RegistrationInfo,
    wake: WakeLaneState,
): Pair<String, String?> {
    val registration =
        context.getString(
            when {
                info.status == CallEngine.Registration.REGISTERED -> R.string.registration_registered
                info.status == CallEngine.Registration.CONNECTING -> R.string.registration_connecting
                // The cause matters: "the PBX refused you" and "no
                // route to the PBX" ask for different fixes.
                info.failure == CallEngine.RegistrationFailure.AUTH_REJECTED -> R.string.registration_auth_rejected
                else -> R.string.registration_off
            },
        )
    return when (wake) {
        WakeLaneState.UNKNOWN -> registration to null
        WakeLaneState.STARTING ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_starting)) to null
        WakeLaneState.ARMED ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_on)) to null
        WakeLaneState.REFUSED_SEAM_OFF ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_off)) to
                context.getString(R.string.wake_reason_refused_seam_off)
        WakeLaneState.REFUSED_INVALID ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_off)) to
                context.getString(R.string.wake_reason_refused_invalid)
        WakeLaneState.REFUSED_AUTH ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_off)) to
                context.getString(R.string.wake_reason_refused_auth)
        WakeLaneState.REFUSED_OTHER ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_off)) to
                context.getString(R.string.wake_reason_refused_other)
        WakeLaneState.SESSION_DEAD ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_off)) to
                context.getString(R.string.wake_reason_session_dead)
        WakeLaneState.DISCONNECTED ->
            context.getString(R.string.line_and_wake, registration, context.getString(R.string.wake_off)) to
                context.getString(R.string.wake_reason_disconnected)
    }
}

/** The wake-off detail sheet's body line (file-level so the pin
 *  builds it without an activity): the state's full sentence — the
 *  pill's content description is one tap away, not in your face, and
 *  a bare "wake off" never says what to DO. */
internal fun wakeDetailBody(
    context: Context,
    state: WakeLaneState,
): String =
    when (state) {
        WakeLaneState.UNKNOWN -> context.getString(R.string.wake_detail_unknown)
        WakeLaneState.STARTING -> context.getString(R.string.wake_starting)
        WakeLaneState.ARMED -> context.getString(R.string.wake_detail_armed)
        WakeLaneState.REFUSED_SEAM_OFF -> context.getString(R.string.wake_reason_refused_seam_off)
        WakeLaneState.REFUSED_INVALID -> context.getString(R.string.wake_reason_refused_invalid)
        WakeLaneState.REFUSED_AUTH -> context.getString(R.string.wake_reason_refused_auth)
        WakeLaneState.REFUSED_OTHER -> context.getString(R.string.wake_reason_refused_other)
        WakeLaneState.SESSION_DEAD -> context.getString(R.string.wake_reason_session_dead)
        WakeLaneState.DISCONNECTED -> context.getString(R.string.wake_reason_disconnected)
    }

/** The detail sheet's "last checked" line: a refusal without a
 *  timestamp cannot say how stale it is (a seam-off verdict from
 *  yesterday reads differently than one from a minute ago). 0 means
 *  the lane never heard a definite answer. */
internal fun wakeLastCheckedLine(
    context: Context,
    stampMs: Long,
): String =
    if (stampMs == 0L) {
        context.getString(R.string.wake_never_checked)
    } else {
        context.getString(
            R.string.wake_last_checked,
            DateUtils.formatDateTime(
                context,
                stampMs,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
            ),
        )
    }

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
    internal lateinit var statusLabel: TextView
    internal lateinit var linePill: LinearLayout
    private lateinit var chrome: LinearLayout
    internal lateinit var wakeDetail: LinearLayout
    internal lateinit var wakeDetailTitle: TextView
    internal lateinit var wakeDetailBodyText: TextView
    internal lateinit var wakeDetailLastChecked: TextView
    internal lateinit var wakeDetailRetry: AppCompatButton
    private var linePulse: ValueAnimator? = null
    private var requestedBatteryExemption = false
    private var backInvoker: OnBackInvokedCallback? = null
    internal var wake: WakeLaneState = WakeLaneState.UNKNOWN

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
            val invoker =
                OnBackInvokedCallback {
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
        statusDot =
            View(this).apply {
                // Decorative: the label next to it carries the state for
                // TalkBack; a bare colored dot would only add noise.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams =
                    LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginEnd = dp(8)
                    }
            }
        statusLabel =
            TextView(this).apply {
                styleText(13f, ContextCompat.getColor(this@MainActivity, R.color.chrome_on_glass), medium = true)
            }
        val linePill =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = pillBackground(ContextCompat.getColor(this@MainActivity, R.color.chrome_glass))
                setPadding(dp(14), dp(9), dp(14), dp(9))
                addView(statusDot)
                addView(statusLabel)
                // The pill is the wake-off detail sheet's TAP TARGET:
                // the verdict label is one glance, the full why + when
                // + retry is one tap. Nothing else in the chrome is
                // tappable twice.
                setOnClickListener { showWakeDetail() }
            }
        this.linePill = linePill

        val dialFab =
            iconButton(
                iconRes = R.drawable.ic_call,
                iconTint = themeColor(R.attr.wpOnAccent),
                backgroundColor = themeColor(R.attr.wpAccent),
                sizeDp = 56,
                contentDescriptionText = getString(R.string.dial),
            ).apply { elevation = dpf(6f) }

        val settingsButton =
            iconButton(
                iconRes = R.drawable.ic_settings,
                iconTint = ContextCompat.getColor(this, R.color.chrome_on_glass),
                backgroundColor = ContextCompat.getColor(this, R.color.chrome_glass),
                sizeDp = 44,
                contentDescriptionText = getString(R.string.settings),
            )

        chrome =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(
                    linePill,
                    LinearLayout
                        .LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { bottomMargin = dp(12) },
                )
                addView(dialFab)
                addView(
                    settingsButton,
                    LinearLayout
                        .LayoutParams(
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
            FrameLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END,
                ).apply { setMargins(dp(16), dp(16), dp(16), dp(16)) },
        )
        wakeDetail = buildWakeDetail()
        // Topmost: the sheet must cover the chrome (its Retry re-wires
        // the lane the chrome's pill is describing).
        root.addView(
            wakeDetail,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        setContentView(root)
        routeDeepLink(intent)
        webSurface.loadOriginIfChanged()
        dialFab.setOnClickListener { startActivity(Intent(this, DialpadActivity::class.java)) }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    CallEngine.registration.collect { renderRegistration(it) }
                }
                launch {
                    WakeLane.state.collect {
                        wake = it
                        renderRegistration(CallEngine.registration.value)
                    }
                }
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

    /** Warm deep links (singleTask: an already-running shell receives
     *  the VIEW intent here instead of a new activity). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        onNewIntentDelegate(intent)
    }

    /** Test seam (N017): everything onNewIntent does past the framework
     *  call — `setIntent` (a later `getIntent` must see the deep link,
     *  e.g. if the app re-reads it after the process is restored) and
     *  the routing. Protected overrides are untestable directly; this
     *  keeps the warm path pinned without reflection. */
    internal fun onNewIntentDelegate(intent: Intent) {
        setIntent(intent)
        routeDeepLink(intent)
    }

    /** The single exported VIEW intake (the manifest's tel:/sip:/App
     *  Link filters all hang off this activity): tel:/sip: open the
     *  dialer PRE-FILLED (the user still taps Call — the registration
     *  gate holds); a stack App Link opens that path in the shell;
     *  anything foreign falls through to the app's home state — never
     *  the session-cookie-bearing WebView, and never a re-hand-off
     *  (the system already had its chance to keep the link).
     *  Internal: the intake is unit-tested through this seam;
     *  onCreate and onNewIntent are one-line delegates. */
    internal fun routeDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        when (val target = deepLinkTarget(intent.data, Uri.parse(App.origin))) {
            is DeepLinkTarget.ShellPath -> webSurface.loadUri(target.uri)
            is DeepLinkTarget.Dial -> {
                val phase = CallEngine.state.value.phase
                if (phase != CallEngine.Phase.IDLE && phase != CallEngine.Phase.ENDED) {
                    // The single-call policy, one hop earlier than the
                    // dialer's own guard: a live call means the call
                    // screen — the link's number would only matter
                    // once the line frees up.
                    startActivity(
                        Intent(this, CallActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    startActivity(DialpadActivity.prefill(this, target.number))
                }
            }
            DeepLinkTarget.None -> Unit
        }
    }

    override fun onPause() {
        webSurface.pause()
        super.onPause()
    }

    private fun renderRegistration(info: CallEngine.RegistrationInfo) {
        val (label, wakeDescription) = linePillText(this, info, wake)
        statusLabel.text = label
        statusLabel.contentDescription = wakeDescription
        val registered = info.status == CallEngine.Registration.REGISTERED
        statusDot.background =
            circleBackground(
                ContextCompat.getColor(
                    this,
                    if (registered) R.color.chrome_dot_on else R.color.chrome_dot_pending,
                ),
            )
        // The signature moment: the line light breathes while the
        // line is up (skipped entirely when animations are off).
        linePulse?.cancel()
        linePulse =
            if (registered) {
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
        val direct =
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            )
        runCatching { startActivity(direct) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    /** The wake-off detail sheet: an in-activity overlay (same shape
     *  as the WebView's offline overlay — design tokens, no dialog
     *  dependency) because the WHY a closed app will not ring deserves
     *  a full sentence, a staleness stamp, and a retry — not a toast.
     *  Tapped in from the line pill; Retry tears the lane down and
     *  re-decides it ([CallLayer.rewire]). */
    private fun buildWakeDetail(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
            setBackgroundColor(themeColor(R.attr.wpBackground))
            visibility = View.GONE

            wakeDetailTitle =
                TextView(this@MainActivity).apply {
                    styleText(18f, themeColor(R.attr.wpOnSurface), medium = true)
                }
            wakeDetailBodyText =
                TextView(this@MainActivity).apply {
                    styleText(14f, themeColor(R.attr.wpOnSurfaceMuted))
                    setPadding(0, dp(8), 0, 0)
                }
            wakeDetailLastChecked =
                TextView(this@MainActivity).apply {
                    styleText(12f, themeColor(R.attr.wpOnSurfaceMuted))
                    setPadding(0, dp(12), 0, 0)
                }
            wakeDetailRetry =
                AppCompatButton(this@MainActivity).apply {
                    background = pillBackground(themeColor(R.attr.wpAccent))
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.wp_on_accent))
                    textSize = 15f
                    isAllCaps = false
                    setPadding(dp(28), dp(12), dp(28), dp(12))
                    minimumHeight = dp(48)
                    text = getString(R.string.wake_retry_action)
                    setOnClickListener { retryWake() }
                }
            val close =
                AppCompatButton(this@MainActivity).apply {
                    background = pillBackground(ContextCompat.getColor(this@MainActivity, R.color.chrome_glass))
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.chrome_on_glass))
                    textSize = 15f
                    isAllCaps = false
                    setPadding(dp(28), dp(12), dp(28), dp(12))
                    minimumHeight = dp(48)
                    text = getString(R.string.wake_close)
                    setOnClickListener { wakeDetail.visibility = View.GONE }
                }

            addView(wakeDetailTitle)
            addView(
                wakeDetailBodyText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(wakeDetailLastChecked)
            addView(
                wakeDetailRetry,
                LinearLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(24)
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
            )
            addView(
                close,
                LinearLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(12)
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
            )
        }

    /** Fill the sheet from the LIVE state and show it. ARMED hides the
     *  Retry button: re-wiring a working lane is not a user task. */
    internal fun showWakeDetail() {
        wakeDetailTitle.text = getString(R.string.wake_detail_title)
        wakeDetailBodyText.text = wakeDetailBody(this, wake)
        wakeDetailLastChecked.text = wakeLastCheckedLine(this, WakeLane.lastProbeAtMs)
        wakeDetailRetry.visibility = if (wake == WakeLaneState.ARMED) View.GONE else View.VISIBLE
        wakeDetail.visibility = View.VISIBLE
    }

    private fun retryWake() {
        wakeDetail.visibility = View.GONE
        CallLayer.rewire(this)
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
    // island is the product's own navigation. The DEPRECATION suppress
    // covers the super.onBackPressed() call — the deliberate API 31/32
    // tail (predictive back owns 33+; the keep-decision is in AGENTS).
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
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
