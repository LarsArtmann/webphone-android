package software.larsartmann.webphone.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import software.larsartmann.webphone.App
import software.larsartmann.webphone.call.CallActivity
import software.larsartmann.webphone.call.CallConnectionService

/**
 * The push-payload router: both transports (FCM data messages, the
 * ntfy subscription's JSON messages) carry the same ring fact
 * {event, from, call_id} — this is the ONE place it becomes a system
 * call. `ring` wires a self-managed incoming Telecom connection and
 * posts the full-screen-intent heads-up (USE_FULL_SCREEN_INTENT: the
 * sideload lane keeps the default grant; planned: runtime-check
 * canUseFullScreenIntent() and degrade to the heads-up pill — the
 * graceful path the platform decision requires, not yet implemented).
 * `ring_end` stands the ring down (answered elsewhere, caller gave up).
 */
object RingEvent {
    const val EXTRA_FROM = "software.larsartmann.webphone.extra.FROM"
    const val EXTRA_CALL_ID = "software.larsartmann.webphone.extra.CALL_ID"

    fun route(context: Context, event: String, from: String?, callId: String?) {
        when (event) {
            "ring" -> ring(context, from ?: "Unknown", callId ?: "")
            "ring_end" -> {
                val notifications = context.getSystemService(NotificationManager::class.java)
                notifications.cancel(NOTIFICATION_ID_INCOMING)
            }
        }
    }

    private fun ring(context: Context, from: String, callId: String) {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        val extras = Bundle().apply {
            putString(EXTRA_FROM, from)
            putString(EXTRA_CALL_ID, callId)
        }
        // The classic self-managed incoming-call API: account handle +
        // extras bundle; the system builds the ConnectionRequest and
        // routes it to our ConnectionService.
        telecom.addNewIncomingCall(CallConnectionService.accountHandle(context), extras)

        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CallActivity::class.java)
                .putExtra(EXTRA_FROM, from)
                .putExtra(EXTRA_CALL_ID, callId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, App.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(context.getString(software.larsartmann.webphone.R.string.incoming_call))
            .setContentText(from)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_INCOMING, notification)
    }

    private const val NOTIFICATION_ID_INCOMING = 1001
}
