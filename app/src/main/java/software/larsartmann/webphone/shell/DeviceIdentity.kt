package software.larsartmann.webphone.shell

import android.content.Context
import java.security.SecureRandom
import java.util.UUID

/**
 * The per-install identity: a stable device id (the registry's key —
 * token rotations replace the row, the id must never churn) and the
 * unguessable ntfy topic (the de-Googled lane's wake address).
 */
object DeviceIdentity {

    private const val PREFS = "webphone-device"
    private const val KEY_DEVICE_ID = "device-id"
    private const val KEY_NTFY_TOPIC = "ntfy-topic"

    fun deviceId(context: Context): String = synchronized(this) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    /** 32 lowercase hex characters — unguessable by enumeration, the
     *  ntfy privacy model (a topic name is the only credential). */
    fun ntfyTopic(context: Context): String = synchronized(this) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_NTFY_TOPIC, null) ?: SecureRandom().let { random ->
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it) }.also {
                prefs.edit().putString(KEY_NTFY_TOPIC, it).apply()
            }
        }
    }
}
