package software.larsartmann.webphone.push

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.CallActivity
import software.larsartmann.webphone.call.CallConnectionService
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.call.CallNotificationReceiver
import software.larsartmann.webphone.shell.CallLayer
import software.larsartmann.webphone.shell.MainActivity

/**
 * The push-payload router: both transports (FCM data messages, the
 * ntfy subscription's JSON messages) carry the same ring fact
 * {event, from, call_id} — this is the ONE place it becomes a system
 * call. `ring` wires a self-managed incoming Telecom connection and
 * posts the CallStyle full-screen heads-up (USE_FULL_SCREEN_INTENT:
 * the sideload lane keeps the default grant; the runtime check
 * degrades to the plain heads-up pill when the grant is absent — the
 * graceful path the platform decision requires). `ring_end` stands
 * the ring down (answered elsewhere, caller gave up) and — when the
 * ring died unanswered HERE — leaves a missed-call note instead of a
 * silent loss.
 */
object RingEvent {
    const val EXTRA_FROM = "software.larsartmann.webphone.extra.FROM"
    const val EXTRA_CALL_ID = "software.larsartmann.webphone.extra.CALL_ID"

    fun route(context: Context, event: String, from: String?, callId: String?) {
        when (event) {
            "ring" -> ring(context, from ?: "Unknown", callId ?: "")
            "ring_end" -> standDown(context, from)
        }
    }

    /** The single-call policy from the router's seat: a live call
     *  (anything but IDLE and the last call's ENDED beat) owns the
     *  line. */
    private fun lineBusy(): Boolean =
        CallEngine.state.value.phase.let { it != CallEngine.Phase.IDLE && it != CallEngine.Phase.ENDED }

    private fun ring(context: Context, from: String, callId: String) {
        if (lineBusy()) {
            // The SIP layer 486s the second INVITE by itself; the
            // HUMAN still deserves to know the line was busy — a
            // missed-call note, never a second ring that can't be
            // answered (F095/F096).
            CallLayer.ensureWired(context)
            notifyMissedCall(context, from)
            return
        }

        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        val extras = Bundle().apply {
            putString(EXTRA_FROM, from)
            putString(EXTRA_CALL_ID, callId)
        }
        // The classic self-managed incoming-call API: account handle +
        // extras bundle; the system builds the ConnectionRequest and
        // routes it to our ConnectionService.
        telecom.addNewIncomingCall(CallConnectionService.accountHandle(context), extras)

        // Ringing while closed needs the SIP leg up for the INVITE to
        // land on (the push rings first; the bootstrap races it).
        CallLayer.ensureWired(context)

        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CallActivity::class.java)
                .putExtra(EXTRA_FROM, from)
                .putExtra(EXTRA_CALL_ID, callId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, App.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(context.getString(R.string.incoming_call))
            .setContentText(from)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
        // CallStyle: the platform-native incoming-call look with the
        // shade's Answer/Decline actions. Requires a ringing Telecom
        // call AT POST TIME — Telecom's registration of the ringing
        // connection is async, so a lost race (or a shadowed test
        // environment) must fall back to the plain heads-up, not
        // swallow the ring.
        runCatching {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    androidx.core.app.Person.Builder().setName(from).build(),
                    notificationAction(context, CallNotificationReceiver.ACTION_DECLINE, requestCode = 2),
                    notificationAction(context, CallNotificationReceiver.ACTION_ANSWER, requestCode = 1),
                ),
            )
        }
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return
        if (notifications.canUseFullScreenIntent()) {
            builder.setFullScreenIntent(fullScreen, true)
        }
        post(context, NOTIFICATION_ID_INCOMING, builder.build())
    }

    private fun standDown(context: Context, from: String?) {
        val unansweredHere = CallEngine.state.value.phase == CallEngine.Phase.RINGING_IN
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID_INCOMING)
        CallConnectionService.standDownRinging()
        CallEngine.cancelRinging()
        if (unansweredHere) {
            // The ring died without an answer on THIS device — a
            // silent loss becomes a visible one.
            notifyMissedCall(context, from ?: context.getString(R.string.unknown_caller))
        }
    }

    private fun notifyMissedCall(context: Context, from: String) {
        val contentIntent = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, App.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(context.getString(R.string.missed_call))
            .setContentText(from)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        post(context, NOTIFICATION_ID_MISSED, builder.build())
    }

    private fun notificationAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, CallNotificationReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun post(context: Context, id: Int, notification: Notification) {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return
        runCatching { compat.notify(id, notification) }
            .onFailure { Log.w("RingEvent", "notification $id rejected: ${it.message}") }
    }

    private const val NOTIFICATION_ID_INCOMING = 1001
    private const val NOTIFICATION_ID_MISSED = 1004
}
