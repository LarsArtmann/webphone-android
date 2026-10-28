package software.larsartmann.webphone.shell

import android.content.Context
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.asKotlinRandom

/**
 * The per-install identity: a stable device id (the registry's key —
 * token rotations replace the row, the id must never churn) and the
 * unguessable ntfy topic (the de-Googled lane's wake address).
 */
object DeviceIdentity {
    private const val PREFS = "webphone-device"
    private const val KEY_DEVICE_ID = "device-id"
    private const val KEY_NTFY_TOPIC = "ntfy-topic"

    /** 32 lowercase hex characters — unguessable by enumeration, the
     *  ntfy privacy model (a topic name is the only credential).
     *  Pure so the shape is testable; the persisted wrapper injects a
     *  SecureRandom-backed source. */
    fun generateTopic(random: kotlin.random.Random = kotlin.random.Random.Default): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    fun deviceId(context: Context): String =
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }
        }

    fun ntfyTopic(context: Context): String =
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(KEY_NTFY_TOPIC, null) ?: generateTopic(
                SecureRandom().asKotlinRandom(),
            ).also {
                prefs.edit().putString(KEY_NTFY_TOPIC, it).apply()
            }
        }
}
