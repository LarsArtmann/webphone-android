package software.larsartmann.webphone.sip

import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * RFC 2617/7616 digest credentials for the REGISTER challenge.
 * FreeSWITCH answers MD5 and SHA-256; we answer with whichever
 * algorithm the challenge names (the island's sip.js does the same).
 */
object Digest {

    data class Challenge(
        val realm: String,
        val nonce: String,
        val algorithm: String,
        val qop: String?,
        val opaque: String?,
    )

    fun parseChallenge(wwwAuthenticate: String): Challenge? {
        val params = mutableMapOf<String, String>()
        val body = wwwAuthenticate.substringAfter("Digest", "").trim()
        val regex = Regex("(\\w+)=\"?([^\"]*)\"?")
        for (match in regex.findAll(body)) {
            params[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        return Challenge(
            realm = realm,
            nonce = nonce,
            algorithm = params["algorithm"] ?: "MD5",
            qop = params["qop"],
            opaque = params["opaque"],
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun authorization(
        method: String,
        uri: String,
        username: String,
        password: String,
        challenge: Challenge,
        cnonce: String = Base64.Default.encode(ByteArray(8) { it.toByte() }),
        nc: Int = 1,
    ): String {
        val algo = challenge.algorithm.replace("-", "").lowercase()
        val ha1 = hex(md5ish(algo, "$username:${challenge.realm}:$password"))
        val ha2 = hex(md5ish(algo, "$method:$uri"))
        val ncHex = "%08x".format(nc)
        // The challenge may advertise a qop LIST ("auth,auth-int");
        // the hash and the echoed qop must be the CHOSEN protection
        // quality ("auth" when offered), never the raw list — the RFC
        // 2617 §3.2.2.1 example is the contract.
        val qop = challenge.qop?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { options ->
            options.firstOrNull { it.equals("auth", ignoreCase = true) } ?: options.first()
        }
        val response = if (qop != null) {
            hex(md5ish(algo, "$ha1:${challenge.nonce}:$ncHex:$cnonce:$qop:$ha2"))
        } else {
            hex(md5ish(algo, "$ha1:${challenge.nonce}:$ha2"))
        }
        val qopPart = if (qop != null) ", qop=$qop, nc=$ncHex, cnonce=\"$cnonce\"" else ""
        val opaquePart = if (challenge.opaque != null) ", opaque=\"${challenge.opaque}\"" else ""
        return "Digest username=\"$username\", realm=\"${challenge.realm}\", nonce=\"${challenge.nonce}\", uri=$uri" +
            ", algorithm=${challenge.algorithm}, response=\"$response\"$qopPart$opaquePart"
    }

    private fun md5ish(algo: String, input: String): ByteArray = when (algo) {
        "md5" -> MessageDigest.getInstance("MD5").digest(input.toByteArray())
        "sha256" -> MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        else -> MessageDigest.getInstance("MD5").digest(input.toByteArray())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
