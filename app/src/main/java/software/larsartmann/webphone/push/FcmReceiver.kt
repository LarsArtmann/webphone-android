package software.larsartmann.webphone.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * The FCM lane: the server's push seam sends a DATA message
 * {event, from, call_id} (deliberately not a notification payload —
 * the shell owns its CallStyle rendering; a stock notification would
 * double-ring with a shade entry we never styled).
 *
 * A build without google-services.json never receives here; the ntfy
 * lane (NtfySubscriptionService) carries the same ring fact on
 * de-Googled devices.
 */
class FcmReceiver : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        RingEvent.route(
            this,
            event = data["event"] ?: return,
            from = data["from"],
            callId = data["call_id"],
        )
    }

    override fun onNewToken(token: String) {
        // Rotation: the registry upserts by stable device id, so the
        // new token replaces the old row on the next register pass
        // (SessionApi.registerPushDevice, kicked from MainActivity).
    }
}
