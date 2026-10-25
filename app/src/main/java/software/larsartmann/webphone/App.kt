package software.larsartmann.webphone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
        origin = prefs().getString(KEY_ORIGIN, DEFAULT_ORIGIN) ?: DEFAULT_ORIGIN
    }

    private fun prefs() = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)

    companion object {
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_PUSH = "push"

        internal const val PREFS_SETTINGS = "webphone-settings"
        private const val KEY_ORIGIN = "origin"

        /** The shipped default: the operator's live stack. A saved
         *  origin (Settings, persisted, read back at process start)
         *  wins over this constant. */
        const val DEFAULT_ORIGIN = "https://pbx.artmann.tech"

        /** The stack origin every island request, the SIP WSS URL and
         *  the ntfy lane hang off. Volatile: Settings rewrites it
         *  mid-process and CallLayer's next wire must see the new one. */
        @Volatile var origin: String = DEFAULT_ORIGIN

        /** Persist a validated origin and make it live immediately. */
        fun saveOrigin(context: Context, value: String) {
            origin = value
            context.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                .edit().putString(KEY_ORIGIN, value).apply()
        }
    }
}
