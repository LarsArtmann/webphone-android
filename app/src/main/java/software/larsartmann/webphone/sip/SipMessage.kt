package software.larsartmann.webphone.sip

/**
 * A parsed SIP message (request or response) over the WebSocket
 * transport. Text-protocol SIP in, structured headers out; this is
 * the seam the engine reasons over, and the only place raw SIP bytes
 * are shaped.
 *
 * v0 scope: the header subset the FreeSWITCH island contract needs
 * (Via, From, To, Call-ID, CSeq, Contact, Max-Forwards, Allow,
 * Content-Type, Authorization, Refer-To, Event, Subscription-State).
 */
data class SipMessage(
    val isRequest: Boolean,
    val method: String,
    val uri: String,
    val statusCode: Int,
    val reason: String,
    val headers: List<SipHeader>,
    val body: String,
) {
    fun header(name: String): String? = headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    fun headersOf(name: String): List<String> =
        headers.filter { it.name.equals(name, ignoreCase = true) }.map { it.value }

    fun cseq(): Pair<Long, String>? =
        header("CSeq")?.let { raw ->
            val parts = raw.trim().split(" ", limit = 2)
            if (parts.size == 2) {
                parts[0].toLongOrNull()?.let { n -> n to parts[1].trim().uppercase() }
            } else {
                null
            }
        }

    fun callId(): String = header("Call-ID").orEmpty()

    fun render(): String =
        buildString {
            if (isRequest) {
                append("$method $uri SIP/2.0\r\n")
            } else {
                append("SIP/2.0 $statusCode $reason\r\n")
            }
            for (h in headers) {
                append("${h.name}: ${h.value}\r\n")
            }
            append("\r\n")
            append(body)
        }

    companion object {
        fun parse(raw: String): SipMessage? {
            val divider = raw.indexOf("\r\n\r\n")
            val head = if (divider >= 0) raw.substring(0, divider) else raw
            val body = if (divider >= 0) raw.substring(divider + 4) else ""

            val lines = head.split("\r\n")
            val first = lines.firstOrNull() ?: return null
            val isRequest = !first.startsWith("SIP/2.0")

            var method = ""
            var uri = ""
            var statusCode = 0
            var reason = ""
            if (isRequest) {
                val parts = first.split(" ")
                // A request line is METHOD SP URI SP "SIP/2.0" — any 3
                // tokens is not a request ("garbage without structure"
                // used to parse as an INVITE-shaped nothing).
                if (parts.size < 3 || parts[2] != "SIP/2.0") return null
                method = parts[0]
                uri = parts[1]
            } else {
                val parts = first.split(" ", limit = 3)
                if (parts.size < 2) return null
                statusCode = parts[1].toIntOrNull() ?: return null
                if (statusCode !in 100..699) return null
                reason = if (parts.size == 3) parts[2] else ""
            }

            val headers =
                lines.drop(1).mapNotNull { line ->
                    val colon = line.indexOf(':')
                    if (colon <= 0) return@mapNotNull null
                    SipHeader(line.substring(0, colon).trim(), line.substring(colon + 1).trim())
                }
            return SipMessage(isRequest, method, uri, statusCode, reason, headers, body)
        }
    }
}

data class SipHeader(
    val name: String,
    val value: String,
)
