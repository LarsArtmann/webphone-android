package software.larsartmann.webphone.push

import org.json.JSONObject

/**
 * The push-fact wire contract, as pure decoders: both transports carry
 * the same fact {event, from, call_id} — FCM as a flat data map, ntfy
 * as compact JSON inside the publish body's message field. These
 * decoders are the ONE parse home (the receivers delegate), and the
 * JVM WireContractTest drives them with the byte-pinned fixtures
 * vendored from the server repo (docs/contracts/wire/ in webphone,
 * synced by scripts/sync-wire-fixtures.sh) — a renamed key reds the
 * test on either side, never at the first device boot.
 */
data class WireFact(
    val event: String,
    val from: String?,
    val callId: String?,
)

object WireFacts {
    /** The FCM data map (RemoteMessage.data on device; a plain map in
     *  tests). A frame without an event is not a fact. */
    fun fromFcmData(data: Map<String, String>): WireFact? {
        val event = data["event"] ?: return null
        return WireFact(
            event = event,
            from = data["from"]?.takeIf { it.isNotEmpty() },
            callId = data["call_id"]?.takeIf { it.isNotEmpty() },
        )
    }

    /** One ntfy websocket/HTTP-stream frame: the fact rides the
     *  message field as compact JSON. Unroutable frames answer null
     *  (the caller logs the raw text — the frame is the only
     *  evidence). */
    fun fromNtfyFrame(text: String): WireFact? =
        runCatching {
            val inner = JSONObject(JSONObject(text).optString("message"))
            fromFcmData(
                mapOf(
                    "event" to inner.optString("event"),
                    "from" to inner.optString("from"),
                    "call_id" to inner.optString("call_id"),
                ),
            )
        }.getOrNull()
}
