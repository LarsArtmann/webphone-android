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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
 *
 * The service ends WITH the call, by itself: it collects the engine's
 * state and retires on the transition into ENDED, so a call that ends
 * with no screen alive (a remote BYE after the user left the in-call
 * UI) cannot leave a microphone-typed service running. Nobody sends
 * it a stop intent; the screen only renders. The collector starts
 * after `startForeground` (bringing the service down before that
 * call crashes on Android 12+) and skips the state present at
 * start, which is the PREVIOUS call's ENDED until the next dial
 * overwrites it: only a transition retires.
 */
class CallService : Service() {
    private var lifetime: Job? = null

    override fun onDestroy() {
        lifetime?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val types = foregroundTypes()
        try {
            startForeground(NOTIFICATION_ID, styledNotification(), types)
        } catch (t: Throwable) {
            Log.w(TAG, "CallStyle notification rejected (${t.message}) — plain pill")
            startForeground(NOTIFICATION_ID, plainNotification(), types)
        }
        if (lifetime == null) {
            lifetime =
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                    CallEngine.state.drop(1).collect { state ->
                        if (state.phase == CallEngine.Phase.ENDED) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CallService::class.java))
        }
    }
}
