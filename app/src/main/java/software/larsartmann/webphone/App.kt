package software.larsartmann.webphone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import software.larsartmann.webphone.call.WebRtcEngine

/**
 * Process-wide setup: the notification channels the call surfaces
 * post to, and the WebRTC factory (initialized once — the native
 * audio-processing module is expensive and stateful).
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CALLS,
                getString(R.string.channel_calls),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                // The ring surface: a call must break through Do Not
                // Disturb the way the platform expects calling apps to.
                setBypassDnd(true)
            },
        )
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PUSH,
                getString(R.string.channel_push),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        WebRtcEngine.initialize(this)
    }

    companion object {
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_PUSH = "push"

        /** The operator sets the stack origin once (Settings in v0.1:
         *  a BuildConfig-free constant until the settings UI lands). */
        @Volatile var origin: String = "https://pbx.example.org"
    }
}
