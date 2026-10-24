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
import software.larsartmann.webphone.push.RingEvent

/**
 * The self-managed Telecom integration: the OS learns call state
 * (audio focus, Bluetooth/wired routing, Do Not Disturb, watches)
 * while the app owns its UI — the platform decision's core value over
 * every web-only shape.
 */
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
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val connection = SelfManagedConnection(applicationContext)
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.setDialing()
        activeConnection = connection
        return connection
    }

    /** The bridge between the Telecom world and our CallEngine. */
    private class SelfManagedConnection(private val appContext: Context) : Connection() {
        override fun onAnswer() {
            setActive()
            CallEngine.answer()
            // The BT/watch answer path has no activity behind it; the
            // call FGS must come up here too (a telecom answer is user
            // interaction — the legitimate mic-FGS launch path).
            runCatching { CallService.start(appContext) }
        }

        override fun onReject() {
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            clearActive(this)
            CallEngine.decline()
        }

        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            clearActive(this)
            CallEngine.hangUp()
        }

        override fun onHold() {
            setOnHold()
            CallEngine.toggleHold()
        }

        override fun onUnhold() {
            setActive()
            CallEngine.toggleHold()
        }

        override fun onSilence() {
            // The push lane's ring stops; the SIP leg keeps ringing
            // until the caller or the dialplan gives up.
        }
    }

    companion object {
        private var handle: PhoneAccountHandle? = null
        @Volatile private var activeConnection: SelfManagedConnection? = null

        /** ring_end: the ring stands down (answered elsewhere, caller
         *  gave up) — tear the ringing Telecom connection with it. */
        fun standDownRinging() {
            val connection = activeConnection ?: return
            connection.setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            connection.destroy()
            activeConnection = null
        }

        private fun clearActive(connection: SelfManagedConnection) {
            if (activeConnection === connection) activeConnection = null
        }

        fun accountHandle(context: Context): PhoneAccountHandle {
            handle?.let { return it }
            val resolved = PhoneAccountHandle(
                ComponentName(context, CallConnectionService::class.java),
                "webphone",
            )
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.registerPhoneAccount(
                PhoneAccount.builder(resolved, "WebPhone")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build(),
            )
            handle = resolved
            return resolved
        }
    }
}
