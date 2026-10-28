package software.larsartmann.webphone.call

import android.content.ComponentName
import android.content.Context
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import software.larsartmann.webphone.push.RingEvent

/**
 * The self-managed Telecom integration for INBOUND calls: the OS
 * learns call state (audio focus, Bluetooth/wired routing, Do Not
 * Disturb, watches) while the app owns its UI — the platform
 * decision's core value over every web-only shape. Rings land via
 * `addNewIncomingCall` (works on every supported API level); the
 * OUTBOUND direction rides `TelecomOutgoing` (`addCall`, API 34+)
 * because the classic self-managed outgoing entry was removed from
 * the API 36 stubs — `onCreateOutgoingConnection` cannot fire and is
 * deleted (a ghost stays deleted, never polished). */
class CallConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val connection = SelfManagedConnection(applicationContext)
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.setRinging()
        connection.setCallerDisplayName(
            request.extras.getString(RingEvent.EXTRA_FROM) ?: "Unknown",
            TelecomManager.PRESENTATION_ALLOWED,
        )
        activeConnection = connection
        connection.watchCallState(connectionScope)
        return connection
    }

    /** The bridge between the Telecom world and our CallEngine. */
    private class SelfManagedConnection(
        private val appContext: Context,
    ) : Connection() {
        internal var watcher: Job? = null
        private var lastPhase: CallEngine.Phase? = null
        private var terminated = false

        /** Telecom-facing lifecycle rides the CallEngine state:
         *  dialing → active/hold → disconnected+destroy, in BOTH call
         *  directions. This is what makes remote BYE tear the Telecom
         *  connection down (before the watcher, a remote hang-up left
         *  a zombie connection the BT stack still believed was live). */
        fun watchCallState(scope: CoroutineScope) {
            watcher =
                scope.launch {
                    CallEngine.state.collect { state ->
                        if (state.phase == lastPhase || terminated) return@collect
                        lastPhase = state.phase
                        when (state.phase) {
                            CallEngine.Phase.ACTIVE -> setActive()
                            CallEngine.Phase.HELD -> setOnHold()
                            CallEngine.Phase.ENDED ->
                                terminate(
                                    when (state.endedBy) {
                                        CallEngine.EndedBy.LOCAL -> DisconnectCause.LOCAL
                                        CallEngine.EndedBy.FAILED -> DisconnectCause.ERROR
                                        else -> DisconnectCause.REMOTE
                                    },
                                )
                            else -> Unit
                        }
                    }
                }
        }

        /** The ONE disconnect path (idempotent): every end-of-call route
         *  — local hang-up, remote BYE, reject, ring stand-down, failure
         *  — funnels here exactly once. */
        fun terminate(cause: Int) {
            if (terminated) return
            terminated = true
            setDisconnected(DisconnectCause(cause))
            destroy()
            clearActive(this)
        }

        override fun onAnswer() {
            setActive()
            CallEngine.answer()
            // The BT/watch answer path has no activity behind it; the
            // call FGS must come up here too (a telecom answer is user
            // interaction — the legitimate mic-FGS launch path).
            runCatching { CallService.start(appContext) }
                .onFailure { Log.w(TAG, "call FGS start failed on telecom answer: ${it.message}") }
        }

        override fun onReject() {
            CallEngine.decline()
            terminate(DisconnectCause.REJECTED)
        }

        override fun onDisconnect() {
            CallEngine.hangUp()
            terminate(DisconnectCause.LOCAL)
        }

        override fun onHold() {
            CallEngine.toggleHold()
        }

        override fun onUnhold() {
            CallEngine.toggleHold()
        }

        override fun onSilence() {
            // The push lane's ring stops; the SIP leg keeps ringing
            // until the caller or the dialplan gives up.
        }
    }

    companion object {
        private const val TAG = "CallConnectionService"
        private var handle: PhoneAccountHandle? = null

        @Volatile private var activeConnection: SelfManagedConnection? = null
        private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        /** The outbound entry: the dialer's ONLY call path. API 34+
         *  rides the modern addCall session (BT/watches/audio routing
         *  see the call); older devices dial directly. */
        fun placeOutgoingCall(
            context: Context,
            number: String,
        ) {
            val current = CallEngine.state.value
            if (current.phase != CallEngine.Phase.IDLE && current.phase != CallEngine.Phase.ENDED) {
                return // The single-call policy; the dialer already guards.
            }
            if (!TelecomOutgoing.place(context, number)) {
                CallEngine.dial(number)
            }
        }

        /** ring_end: the ring stands down (answered elsewhere, caller
         *  gave up) — tear the ringing Telecom connection with it. */
        fun standDownRinging() {
            activeConnection?.terminate(DisconnectCause.CANCELED)
        }

        private fun clearActive(connection: SelfManagedConnection) {
            if (activeConnection === connection) {
                activeConnection = null
                connection.watcher?.cancel()
            }
        }

        fun accountHandle(context: Context): PhoneAccountHandle {
            handle?.let { return it }
            val resolved =
                PhoneAccountHandle(
                    ComponentName(context, CallConnectionService::class.java),
                    "webphone",
                )
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.registerPhoneAccount(
                PhoneAccount
                    .builder(resolved, "WebPhone")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build(),
            )
            handle = resolved
            return resolved
        }
    }
}
