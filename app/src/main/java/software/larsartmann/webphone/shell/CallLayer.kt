package software.larsartmann.webphone.shell

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.push.NtfySubscriptionService
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import software.larsartmann.webphone.sip.SipEngine

/**
 * Wires the native call layer from whatever woke the process:
 * MainActivity's resume loop OR a ring push while the app is closed —
 * the SIP leg must come up for the incoming INVITE to have somewhere
 * to land (the push rings first; the bootstrap races the INVITE; the
 * first device train tells us whether the server re-invites long
 * enough for that race to be won).
 *
 * Idempotent: one engine per process, shared across callers; a second
 * concurrent wire attempt is discarded, never stacked.
 */
object CallLayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = AttachGuard<SipEngine>()
    private var sessionWatch: Job? = null

    private const val SESSION_WATCH_INTERVAL_MS = 10 * 60 * 1000L

    /** Test seam: the watcher's tick (the loop pin drives it with a
     *  small value; production keeps the 10-minute cadence). */
    internal var sessionWatchIntervalMs = SESSION_WATCH_INTERVAL_MS

    /** Test seam: the session probe itself (the loop pin substitutes
     *  a fake — a unit test must not phone the live stack). */
    internal var sessionProbe: () -> SessionApi.SessionProbe = { SessionApi.probeSession() }

    /** Test seam: the best-effort deregister the teardown runs (the
     *  pin substitutes a no — a unit test must not phone the live
     *  stack, and the real call is worthless anyway: the dead session's
     *  cookie usually cannot authorize it). */
    internal var deregisterPushDevice: (Context) -> Boolean = { SessionApi.deregisterPushDevice(it) }

    /** Notification id 1001-1003 are the ring/FGS/ntfy lane's own
     *  surfaces (App conventions); the dead-session notice is 1004. */
    private const val SESSION_DEAD_NOTIFICATION_ID = 1004

    /**
     * Test seam for the JVM suite: the Robolectric tests launch
     * MainActivity, whose onCreate/resume wires the call layer. Left
     * enabled there, a UNIT test phones the live stack (session HTTP,
     * SIP WSS, ntfy websocket) and leaves live sockets plus reconnect
     * threads behind — that hung the suite for hours and deadlocked
     * ActivityScenario teardown (found by jstack on the wedged
     * executor, 2026-10-28). Default true; only the Robolectric
     * classes that launch the shell flip it off (each test class runs
     * in a fresh sandbox classloader, so the flip cannot leak).
     */
    @Volatile
    internal var wiringEnabled = true

    fun ensureWired(context: Context) {
        if (!wiringEnabled) return
        if (guard.current() != null) return
        val appContext = context.applicationContext
        val attempt = guard.begin()
        scope.launch {
            CallEngine.registrationWiringStarted()
            val identity =
                SessionApi.resumeSession() ?: run {
                    CallEngine.registrationWiringFailed()
                    return@launch
                }
            val created =
                SipEngine(
                    scope,
                    SipEngine.Config(
                        wsUrl = identity.websocketUrl,
                        sipDomain = identity.sipDomain,
                        extension = identity.extension,
                        password = identity.sipPassword,
                    ),
                )
            // A shutdown that raced this resume (settings save, session
            // watcher) anointed this attempt an orphan — refuse to
            // attach; the next ensureWired wires fresh.
            if (!guard.attach(created, attempt)) return@launch
            CallEngine.attach(created)
            created.connect()
            // The lane's verdict journey is visible from the first
            // millisecond — the register + socket-open window shows
            // STARTING instead of the UNKNOWN void.
            WakeLane.report(WakeLaneState.STARTING)
            NtfySubscriptionService.start(
                appContext,
                App.origin,
                DeviceIdentity.ntfyTopic(appContext),
            )
            val verdict = registerPushLanes(appContext)
            wakeStateFor(verdict)?.let { refused ->
                // The stack would not take the wake address: closed-app
                // rings are impossible — say WHICH refusal on the pill
                // instead of failing in logcat only. A REGISTERED
                // verdict reports nothing: STARTING stands until the
                // socket names the lane ARMED.
                WakeLane.report(refused)
            }
            startSessionWatch(appContext)
        }
    }

    /** The verdict-to-ledger mapping (pure, pinned): the register
     *  answer decides which wake-off reason the pill carries. */
    internal fun wakeStateFor(verdict: SessionApi.PushRegisterVerdict): WakeLaneState? =
        when (verdict) {
            SessionApi.PushRegisterVerdict.REGISTERED -> null
            SessionApi.PushRegisterVerdict.SEAM_OFF -> WakeLaneState.REFUSED_SEAM_OFF
            SessionApi.PushRegisterVerdict.INVALID -> WakeLaneState.REFUSED_INVALID
            SessionApi.PushRegisterVerdict.AUTH -> WakeLaneState.REFUSED_AUTH
            SessionApi.PushRegisterVerdict.OTHER -> WakeLaneState.REFUSED_OTHER
            SessionApi.PushRegisterVerdict.NETWORK -> WakeLaneState.DISCONNECTED
        }

    /** The session watcher: while wired, poll the session seam FIRST
     *  (the probe leads the loop — the first verdict lands at wire
     *  time, not ten minutes in) and sleep between ticks. A DEAD
     *  probe (the island logged out, the server expired the cookie)
     *  means: stop ringing for an account we can no longer resume —
     *  drop the ntfy lane, tear the engine down, and TELL the user
     *  (a silent lane death was the undiagnosable no-ring failure).
     *  An UNREACHABLE probe is a link problem, not a logout: keep
     *  watching (the next tick re-probes; a tunnel flap must never
     *  permanently silence the phone). The server-side device row
     *  deregistration is BEST EFFORT here (the logout usually already
     *  killed the cookie, so the DELETE runs unauthorized — the
     *  JS-bridge alternative in the plan needs the operator's pick;
     *  until then the shell at least stops listening). */
    private fun startSessionWatch(appContext: Context) {
        sessionWatch?.cancel()
        sessionWatch =
            scope.launch {
                sessionWatchLoop(appContext)
            }
    }

    /** The watcher loop, one hop off [startSessionWatch] so the pin
     *  drives it with a fake probe and a short tick (no 10-minute
     *  sleep, no HTTP — the meta-proof lesson). [laneAlive] is the
     *  wiring guard, injectable for the same reason. */
    internal suspend fun sessionWatchLoop(
        appContext: Context,
        laneAlive: () -> Boolean = { guard.current() != null },
    ) {
        while (currentCoroutineContext().isActive && laneAlive()) {
            delay(sessionWatchIntervalMs)
            when (sessionProbe()) {
                SessionApi.SessionProbe.ALIVE, SessionApi.SessionProbe.UNREACHABLE -> Unit
                SessionApi.SessionProbe.DEAD -> {
                    onSessionDead(appContext)
                    break
                }
            }
        }
    }

    /** The dead-session teardown, one hop off the watcher loop so the
     *  pin can drive it directly (no 10-minute sleep, no HTTP). Tells
     *  the user FIRST (the teardown kills the very lane that would
     *  carry anything later), on the VISIBLE account channel — the
     *  min-importance push channel would swallow the one notice that
     *  must not be missed — and taps through into the shell, where
     *  the island's login card is one glance away. */
    internal fun onSessionDead(appContext: Context) {
        val openShell =
            PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat
                .Builder(appContext, App.CHANNEL_ACCOUNT)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(appContext.getString(R.string.session_expired_title))
                .setContentText(appContext.getString(R.string.session_expired_body))
                .setContentIntent(openShell)
                .setAutoCancel(true)
        appContext
            .getSystemService(NotificationManager::class.java)
            ?.notify(SESSION_DEAD_NOTIFICATION_ID, builder.build())
        runCatching { deregisterPushDevice(appContext) }
        NtfySubscriptionService.stop(appContext)
        shutdown()
        WakeLane.report(WakeLaneState.SESSION_DEAD)
    }

    /** Register this install's wake addresses with the stack: the ntfy
     *  topic always, the FCM token when Play services AND a
     *  google-services.json build exist (best effort — the ntfy lane
     *  is the zero-Google default, its absence must never throw).
     *
     *  getToken() is deprecated in firebase-messaging 26 (the v1
     *  registration world: register() + the onNewToken callback) and
     *  even throws under a v1-enabled app — but this lane is DORMANT
     *  until a google-services.json exists, so the migration gates on
     *  that owner step, not on a blind API flip (recorded in AGENTS). */
    @Suppress("DEPRECATION")
    private suspend fun registerPushLanes(context: Context): SessionApi.PushRegisterVerdict {
        val ntfy = SessionApi.registerPushDevice(context, "ntfy", DeviceIdentity.ntfyTopic(context))
        runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }
            .getOrNull()
            ?.let { SessionApi.registerPushDevice(context, "fcm", it) }
        return ntfy
    }

    /** Idempotent teardown: invalidate first (in-flight wires become
     *  orphans and refuse to attach), then detach + drop the engine.
     *  The wake ledger returns to UNKNOWN — the state is being
     *  re-decided (Settings teardown re-wires on the next resume); a
     *  definitive verdict (dead session) is reported AFTER teardown
     *  by its own path. */
    fun shutdown() {
        guard.invalidate()
        sessionWatch?.cancel()
        sessionWatch = null
        guard.current()?.let { engine ->
            engine.listener = null
            engine.disconnect()
        }
        CallEngine.detach()
        guard.clear()
        WakeLane.report(WakeLaneState.UNKNOWN)
    }
}
