package software.larsartmann.webphone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The notification shade's call actions (CallStyle Answer/Decline and
 * the ongoing call's Hang up): explicit intents only (not exported),
 * each an explicit user act — the same honesty rule as the in-call
 * surface's buttons. No UI is launched; the state change is the act.
 */
class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_ANSWER -> {
                CallEngine.answer()
                CallService.start(context)
            }
            ACTION_DECLINE -> CallEngine.decline()
            ACTION_HANGUP -> {
                CallEngine.hangUp()
                CallService.stop(context)
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "software.larsartmann.webphone.call.ANSWER"
        const val ACTION_DECLINE = "software.larsartmann.webphone.call.DECLINE"
        const val ACTION_HANGUP = "software.larsartmann.webphone.call.HANGUP"
    }
}
