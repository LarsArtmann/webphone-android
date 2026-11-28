package software.larsartmann.webphone.shell

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.CallActivity
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.call.DialpadActivity
import software.larsartmann.webphone.push.NtfySubscriptionService
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

/** The line-switch verdict's toast sentence (file-level so the pin
 *  builds it without an activity): SUCCESS names the line it landed
 *  on; every refusal says WHICH failure it was — a bare "failed"
 *  collapses four different futures into one silence. */
internal fun switchToastText(
    context: Context,
    verdict: SessionApi.SwitchVerdict,
    label: String,
): String =
    when (verdict) {
        SessionApi.SwitchVerdict.SWITCHED -> context.getString(R.string.switch_success, label)
        SessionApi.SwitchVerdict.NOT_MAPPED -> context.getString(R.string.switch_not_mapped)
        SessionApi.SwitchVerdict.SEAM_OFF -> context.getString(R.string.switch_seam_off)
        SessionApi.SwitchVerdict.NETWORK -> context.getString(R.string.switch_network)
        SessionApi.SwitchVerdict.OTHER -> context.getString(R.string.switch_other)
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
    internal lateinit var wakeDetailLines: LinearLayout
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
     *  gate holds); a stack App Link opens that path in the shell; the
     *  sign-in handoff's return link redeems the ticket the browser
     *  bounced back ([completeSignInHandoff]); anything foreign falls
     *  through to the app's home state — never the session-cookie-bearing
     *  WebView, and never a re-hand-off (the system already had its
     *  chance to keep the link).
     *  Internal: the intake is unit-tested through this seam;
     *  onCreate and onNewIntent are one-line delegates. */
    internal fun routeDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        when (val target = deepLinkTarget(intent.data, App.origin.toUri())) {
            is DeepLinkTarget.ShellPath -> webSurface.loadUri(target.uri)

            is DeepLinkTarget.SignInReturn -> completeSignInHandoff(target)

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

    /** The sign-in handoff's return link (the browser bounced the ticket
     *  back and the system launched the shell with it): claim it against
     *  the open attempt, then redeem it off the UI thread. A link that is
     *  not the answer to this shell's own attempt is dropped before any
     *  network — see [SignInHandoff.takeTicket]. */
    internal fun completeSignInHandoff(
        handoff: DeepLinkTarget.SignInReturn,
        redeem: (String) -> SessionApi.HandoffRedemption = handoffRedeemer,
    ) {
        val ticket = SignInHandoff.takeTicket(this, handoff.ticket, handoff.appState) ?: return
        lifecycleScope.launch {
            applySignInHandoff(withContext(Dispatchers.IO) { redeem(ticket) })
        }
    }

    /** The redemption's consequence (internal so the pin drives it
     *  without the thread): the stack named the page to land on, so the
     *  WebView lands there — `/` signed in, or the login page carrying
     *  the stack's own refusal sentence — and the call layer is
     *  re-decided from the cookie up either way (a refusal simply finds
     *  no session). With no usable answer the shell returns to the origin
     *  root and SAYS so: silence after a sign-in the user just completed
     *  is the one thing this surface must never show. */
    internal fun applySignInHandoff(verdict: SessionApi.HandoffRedemption) {
        when (verdict) {
            is SessionApi.HandoffRedemption.Landed ->
                webSurface.loadUri((App.origin.trimEnd('/') + verdict.landing).toUri())

            SessionApi.HandoffRedemption.Failed -> {
                webSurface.retry()
                Toast.makeText(this, R.string.signin_handoff_failed, Toast.LENGTH_LONG).show()
            }
        }
        CallLayer.rewire(this)
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

    // The direct exemption dialog needs the manifest permission
    //  Android Lint's BatteryLife check flags (it is a Play Store
    //  policy signal). This app ships on the sideload lane only — the
    //  battery manager killing the wake socket is the #1 missed-ring
    //  cause (AGENTS), so the ask stays; the settings deep-link below
    //  remains the fallback for OEMs that broke the dialog.
    @SuppressLint("BatteryLife")
    private fun maybeRequestBatteryExemption() {
        if (requestedBatteryExemption) return
        val power = getSystemService(PowerManager::class.java) ?: return
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences(App.PREFS_SETTINGS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_ASKED, false)) return
        requestedBatteryExemption = true
        prefs.edit { putBoolean(KEY_BATTERY_ASKED, true) }
        val direct =
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri(),
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
            wakeDetailLines =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(16), 0, 0)
                    visibility = View.GONE
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
                wakeDetailLines,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
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
     *  Retry button: re-wiring a working lane is not a user task. The
     *  owned-lines picker renders from [CallLayer.lastIdentity] (the
     *  same object the SIP engine registered with) — injectable so
     *  the pin drives the rendering without a wire. */
    internal fun showWakeDetail(identity: SessionApi.SessionIdentity? = CallLayer.lastIdentity) {
        wakeDetailTitle.text = getString(R.string.wake_detail_title)
        wakeDetailBodyText.text = wakeDetailBody(this, wake)
        wakeDetailLastChecked.text = wakeLastCheckedLine(this, WakeLane.lastProbeAtMs)
        wakeDetailRetry.visibility = if (wake == WakeLaneState.ARMED) View.GONE else View.VISIBLE
        renderLineRows(identity)
        wakeDetail.visibility = View.VISIBLE
    }

    /** The owned-lines picker: visible only when the login owns MORE
     *  than one line (a single-line account needs no switcher). The
     *  active line (lines[0] by the server's contract) is marked and
     *  inert; every other owned line is a switch affordance. Rebuilt
     *  on every show — the identity moves with each re-wire, stale
     *  rows must not linger. */
    private fun renderLineRows(identity: SessionApi.SessionIdentity?) {
        wakeDetailLines.removeAllViews()
        val lines = identity?.lines ?: emptyList()
        wakeDetailLines.visibility = if (lines.size > 1) View.VISIBLE else View.GONE
        if (lines.size < 2) return
        wakeDetailLines.addView(
            TextView(this).apply {
                styleText(14f, themeColor(R.attr.wpOnSurface), medium = true)
                text = getString(R.string.lines_section_title)
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        val active = lines.first()
        for (line in lines) {
            val isActive = line == active
            val row =
                AppCompatButton(this).apply {
                    if (isActive) {
                        background = pillBackground(themeColor(R.attr.wpAccent))
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.wp_on_accent))
                    } else {
                        background = pillBackground(ContextCompat.getColor(this@MainActivity, R.color.chrome_glass))
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.chrome_on_glass))
                    }
                    textSize = 15f
                    isAllCaps = false
                    setPadding(dp(28), dp(12), dp(28), dp(12))
                    minimumHeight = dp(48)
                    text =
                        if (isActive) {
                            getString(R.string.line_active_label, line.label())
                        } else {
                            line.label()
                        }
                    if (!isActive) {
                        setOnClickListener { switchToLine(line.extension) }
                    }
                }
            wakeDetailLines.addView(
                row,
                LinearLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(8)
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
            )
        }
    }

    /** Switch the session onto another line the SAME login owns. The
     *  switch POST is blocking OkHttp — off the UI thread, with the
     *  verdict landed back on it. A live call blocks the switch: the
     *  re-wire the success path runs would tear it down mid-
     *  conversation. [switchCall] is the injectable seam (the pin
     *  drives [applySwitchVerdict] directly instead of the thread). */
    internal fun switchToLine(
        extension: String,
        switchCall: (String) -> SessionApi.SwitchVerdict = SessionApi::switchLine,
    ) {
        val phase = CallEngine.state.value.phase
        if (phase != CallEngine.Phase.IDLE && phase != CallEngine.Phase.ENDED) {
            Toast.makeText(this, R.string.switch_in_call, Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val verdict = switchCall(extension)
            runOnUiThread { applySwitchVerdict(extension, verdict) }
        }.start()
    }

    /** The switch verdict's UI consequence (internal so the pin drives
     *  it without the thread): SWITCHED dismisses the sheet and re-wires
     *  the whole shell — the old line's registration, push subscription
     *  and web session are void the moment the server re-minted the
     *  session; every refusal keeps the sheet open (pick again or
     *  close — the pill still describes the untouched line). */
    internal fun applySwitchVerdict(
        extension: String,
        verdict: SessionApi.SwitchVerdict,
    ) {
        val label =
            CallLayer.lastIdentity
                ?.lines
                ?.firstOrNull { it.extension == extension }
                ?.label()
                ?: extension
        Toast.makeText(this, switchToastText(this, verdict, label), Toast.LENGTH_SHORT).show()
        if (verdict != SessionApi.SwitchVerdict.SWITCHED) return
        wakeDetail.visibility = View.GONE
        NtfySubscriptionService.stop(this)
        webSurface.retry()
        CallLayer.rewire(this)
    }

    private fun retryWake() {
        wakeDetail.visibility = View.GONE
        CallLayer.rewire(this)
    }

    override fun onDestroy() {
        // The invoker exists only from API 33 (onCreate's guard); the
        // explicit version check keeps that invariant provable to
        // Android Lint instead of asking it to trust a nullable field.
        if (Build.VERSION.SDK_INT >= 33) {
            backInvoker?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
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
    // GestureBackNavigation is suppressed for the same reason: this
    // override IS the back path on API 31/32, where the platform still
    // calls it; 33+ rides the OnBackInvokedCallback registered in
    // onCreate and never reaches this method.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webSurface.isInitialized && webSurface.canGoBack()) {
            webSurface.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val KEY_BATTERY_ASKED = "battery_exemption_asked"

        /** The handoff redemption seam (the pins substitute a recorder —
         *  a unit test must not phone the live stack). */
        @Volatile
        internal var handoffRedeemer: (String) -> SessionApi.HandoffRedemption = { SessionApi.redeemSignInHandoff(it) }
    }
}
