package software.larsartmann.webphone.call

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R

/**
 * The call's foreground service: typed `microphone|phoneCall` at
 * startForeground — the screen-off survival the platform decision
 * bought. Started ONLY from user interaction (the push → full-screen
 * intent → answer flow is the legitimate launch path; Android 15+
 * forbids background starts of camera/mic FGS anyway). The SIP
 * registration rides the same service while a call is up.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification =
            Notification.Builder(this, App.CHANNEL_CALLS)
                .setSmallIcon(android.R.drawable.sym_call_outgoing)
                .setContentTitle(getString(R.string.call_active))
                .setOngoing(true)
                .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
        )
        // The engine attaches to the SIP socket for the call's life;
        // stopping the service tears the call down.
        if (intent?.action == ACTION_STOP) {
            CallEngine.hangUp()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    companion object {
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
