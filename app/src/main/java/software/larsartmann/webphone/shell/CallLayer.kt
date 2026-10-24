package software.larsartmann.webphone.shell

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    fun ensureWired(context: Context) {
        if (engine != null) return
        val appContext = context.applicationContext
        scope.launch {
            val identity = SessionApi.resumeSession() ?: return@launch
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
        CallEngine.detach()
        engine = null
    }
}
