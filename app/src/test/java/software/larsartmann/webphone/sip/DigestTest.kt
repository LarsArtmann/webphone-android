package software.larsartmann.webphone.sip

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DigestTest {
    private val rfc2617Challenge =
        "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", " +
            "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", " +
            "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""

    /** Inputs and expected output are RFC 2617 §3.5 verbatim (nonce is
     *  the RFC's 31-hex value); the response hash was additionally
     *  verified against an independent MD5 computation. */
    @Test
    fun `rfc 2617 example vector with multi-qop challenge`() {
        val challenge = Digest.parseChallenge(rfc2617Challenge)
        assertEquals("testrealm@host.com", challenge?.realm)
        assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", challenge?.nonce)
        assertEquals("auth,auth-int", challenge?.qop)
        assertEquals("5ccc069c403ebaf9f0171e9517f40e41", challenge?.opaque)

        val authorization =
            Digest.authorization(
                method = "GET",
                uri = "/dir/index.html",
                username = "Mufasa",
                password = "Circle Of Life",
                challenge = challenge!!,
                cnonce = "0a4f113b",
                nc = 1,
            )
        // The CHOSEN qop ("auth"), not the raw challenge list, is what
        // gets hashed and echoed (RFC 2617 §3.2.2: "a single token,
        // not a quoted list of alternatives").
        assertTrue(
            "response=\"6629fae49393a05397450978507c4ef1\"" in authorization,
            "expected the RFC response hash, got: $authorization",
        )
        assertTrue("qop=auth, nc=00000001" in authorization)
        assertTrue("cnonce=\"0a4f113b\"" in authorization)
        assertTrue("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"" in authorization)
    }

    @Test
    fun `no-qop challenge uses the rfc 2069 composition`() {
        val challenge =
            Digest.parseChallenge(
                "Digest realm=\"r\", nonce=\"n\"",
            )!!
        val expected =
            md5(
                "${md5("user:r:pw")}:n:${md5("REGISTER:sip:example.org")}",
            )
        val authorization =
            Digest.authorization(
                method = "REGISTER",
                uri = "sip:example.org",
                username = "user",
                password = "pw",
                challenge = challenge,
            )
        assertTrue("response=\"$expected\"" in authorization)
        assertTrue("qop" !in authorization)
    }

    @Test
    fun `sha-256 algorithm is selected from the challenge`() {
        val challenge =
            Digest.parseChallenge(
                "Digest realm=\"r\", nonce=\"n\", algorithm=SHA-256, qop=\"auth\"",
            )!!
        assertEquals("SHA-256", challenge.algorithm)
        val expected =
            md5ish256(
                "${md5ish256("user:r:pw")}:n:00000001:cn:auth:${md5ish256("REGISTER:sip:example.org")}",
            )
        val authorization =
            Digest.authorization(
                method = "REGISTER",
                uri = "sip:example.org",
                username = "user",
                password = "pw",
                challenge = challenge,
                cnonce = "cn",
            )
        assertTrue("response=\"$expected\"" in authorization)
        assertTrue("algorithm=SHA-256" in authorization)
    }

    @Test
    fun `challenge without algorithm defaults to md5`() {
        val challenge = Digest.parseChallenge("Digest realm=\"r\", nonce=\"n\"")
        assertEquals("MD5", challenge?.algorithm)
    }

    @Test
    fun `stale flag parses and defaults to false`() {
        assertTrue(
            Digest.parseChallenge("Digest realm=\"r\", nonce=\"n\", stale=true")!!.stale,
            "a stale nonce says the credentials were fine",
        )
        assertTrue(
            Digest.parseChallenge("Digest realm=\"r\", nonce=\"n\", stale=TRUE")!!.stale,
        )
        assertEquals(
            false,
            Digest.parseChallenge("Digest realm=\"r\", nonce=\"n\"")!!.stale,
        )
        assertEquals(
            false,
            Digest.parseChallenge("Digest realm=\"r\", nonce=\"n\", stale=false")!!.stale,
        )
    }

    @Test
    fun `unquoted parameters and missing realm`() {
        val parsed = assertNotNull(Digest.parseChallenge("Digest realm=r nonce=n"))
        assertEquals("r", parsed.realm)
        assertNull(Digest.parseChallenge("Digest nonce=\"n\""))
        assertNull(Digest.parseChallenge("Basic realm=\"x\""))
    }

    private fun md5(input: String): String =
        MessageDigest
            .getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun md5ish256(input: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
