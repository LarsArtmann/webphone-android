package software.larsartmann.webphone.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R

/**
 * The call's foreground service: typed `microphone|phoneCall` at
 * startForeground — the screen-off survival the platform decision
 * bought. Started ONLY from user interaction (the push → full-screen
 * intent → answer flow is the legitimate launch path; Android 15+
 * forbids background starts of camera/mic FGS anyway). The SIP
 * registration rides the same service while a call is up.
 *
 * The notification is CallStyle (the platform-native ongoing-call
 * look with Hang up in the shade) whenever the engine names a remote
 * party; posting is guarded — CallStyle demands an active Telecom
 * call at post time and the API 31-33 direct-dial path has none, so
 * the plain ongoing pill is the fallback, never a crash.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            CallEngine.hangUp()
            startForeground(NOTIFICATION_ID, plainNotification(), foregroundTypes())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_STICKY
        }
        val types = foregroundTypes()
        try {
            startForeground(NOTIFICATION_ID, styledNotification(), types)
        } catch (t: Throwable) {
            Log.w(TAG, "CallStyle notification rejected (${t.message}) — plain pill")
            startForeground(NOTIFICATION_ID, plainNotification(), types)
        }
        return START_STICKY
    }

    private fun foregroundTypes(): Int =
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL

    private fun styledNotification(): Notification {
        val builder = baseNotification()
        val party = CallEngine.state.value.remoteParty
        if (party.isNotEmpty()) {
            val startedAtMs = CallEngine.state.value.startedAtMs
            if (startedAtMs > 0) {
                // The shade's own call clock (CallStyle renders elapsed
                // from `when`): the same wall-clock the in-call screen
                // ticks, visible with the screen off.
                builder.setShowWhen(true).setWhen(startedAtMs)
            }
            NotificationCompat.CallStyle
                .forOngoingCall(
                    androidx.core.app.Person
                        .Builder()
                        .setName(party)
                        .build(),
                    PendingIntent.getBroadcast(
                        this,
                        3,
                        Intent(this, CallNotificationReceiver::class.java)
                            .setAction(CallNotificationReceiver.ACTION_HANGUP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).let { builder.setStyle(it) }
        }
        return builder.build()
    }

    private fun plainNotification(): Notification = baseNotification().build()

    private fun baseNotification(): NotificationCompat.Builder =
        NotificationCompat
            .Builder(this, App.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(getString(R.string.call_active))
            .setOngoing(true)

    companion object {
        private const val TAG = "CallService"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "software.larsartmann.webphone.call.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CallService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CallService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
