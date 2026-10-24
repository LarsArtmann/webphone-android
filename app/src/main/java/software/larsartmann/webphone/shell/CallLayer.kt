package software.larsartmann.webphone.shell

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import software.larsartmann.webphone.App
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.push.NtfySubscriptionService
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
    @Volatile private var engine: SipEngine? = null
    private var sessionWatch: Job? = null

    fun ensureWired(context: Context) {
        if (engine != null) return
        val appContext = context.applicationContext
        scope.launch {
            CallEngine.registrationWiringStarted()
            val identity = SessionApi.resumeSession() ?: run {
                CallEngine.registrationWiringFailed()
                return@launch
            }
            val created = SipEngine(
                scope,
                SipEngine.Config(
                    wsUrl = identity.websocketUrl,
                    sipDomain = identity.sipDomain,
                    extension = identity.extension,
                    password = identity.sipPassword,
                ),
            )
            if (!attachOnce(created)) return@launch
            created.connect()
            registerPushLanes(appContext)
            NtfySubscriptionService.start(
                appContext,
                App.origin,
                DeviceIdentity.ntfyTopic(appContext),
            )
            startSessionWatch(appContext)
        }
    }

    /** The session watcher: while wired, poll the session seam. A
     *  dead session means the island logged out — stop ringing for an
     *  account we can no longer resume: drop the ntfy lane, tear the
     *  engine down. The server-side device row deregistration is BEST
     *  EFFORT here (the logout usually already killed the cookie, so
     *  the DELETE runs unauthorized — the JS-bridge alternative in the
     *  plan needs the operator's pick; until then the shell at least
     *  stops listening). */
    private fun startSessionWatch(appContext: Context) {
        sessionWatch?.cancel()
        sessionWatch = scope.launch {
            while (isActive && engine != null) {
                delay(SESSION_WATCH_INTERVAL_MS)
                if (SessionApi.sessionAlive()) continue
                runCatching { SessionApi.deregisterPushDevice(appContext) }
                NtfySubscriptionService.stop(appContext)
                shutdown()
                break
            }
        }
    }

    private fun attachOnce(created: SipEngine): Boolean = synchronized(this) {
        if (engine != null) return false
        engine = created
        CallEngine.attach(created)
        true
    }

    /** Register this install's wake addresses with the stack: the ntfy
     *  topic always, the FCM token when Play services AND a
     *  google-services.json build exist (best effort — the ntfy lane
     *  is the zero-Google default, its absence must never throw). */
    private suspend fun registerPushLanes(context: Context) {
        SessionApi.registerPushDevice(context, "ntfy", DeviceIdentity.ntfyTopic(context))
        runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }
            .getOrNull()
            ?.let { SessionApi.registerPushDevice(context, "fcm", it) }
    }

    fun shutdown() {
        sessionWatch?.cancel()
        sessionWatch = null
        CallEngine.detach()
        engine = null
    }

    private companion object {
        const val SESSION_WATCH_INTERVAL_MS = 10 * 60 * 1000L
    }
}
