package software.larsartmann.webphone.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import software.larsartmann.webphone.shell.SessionApi

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

    // onNewToken(String) is deprecated in firebase-messaging 26 (the
    // v1-registration world replaced the fetch path with register()),
    // but 26.0.1 ships NO replacement callback — this override is the
    // contract FCM itself calls. Keeping it is deliberate; the
    // annotation satisfies OVERRIDE_DEPRECATION and documents WHY no
    // successor is adopted (the FCM lane is dormant by default — the
    // AGENTS gotcha — and the v1 migration gates on it going live).
    @Deprecated(
        "Still the rotation entry FCM calls; no replacement callback in 26.0.1.",
    )
    override fun onNewToken(token: String) {
        // Rotation: the registry upserts by stable device id, so the
        // new token replaces the old row (SessionApi.registerPushDevice
        // — off the main thread; FCM callbacks are main).
        Thread { SessionApi.registerPushDevice(applicationContext, "fcm", token) }.start()
    }
}
