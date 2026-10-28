package software.larsartmann.webphone.sip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SipMessageTest {
    private val invite =
        listOf(
            "INVITE sip:100@example.org SIP/2.0",
            "Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKabc123;rport",
            "From: \"WebPhone\" <sip:200@example.org>;tag=caller1",
            "To: <sip:100@example.org>",
            "Call-ID: call-42",
            "CSeq: 7 INVITE",
            "Max-Forwards: 70",
            "Content-Type: application/sdp",
            "",
            "v=0",
            "o=- 1 1 IN IP4 0.0.0.0",
        ).joinToString("\r\n")

    private val ringing =
        listOf(
            "SIP/2.0 180 Ringing",
            "Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKabc123;rport",
            "From: \"WebPhone\" <sip:200@example.org>;tag=caller1",
            "To: <sip:100@example.org>;tag=callee9",
            "Call-ID: call-42",
            "CSeq: 7 INVITE",
            "",
            "",
        ).joinToString("\r\n")

    @Test
    fun `request renders and parses back identically`() {
        val parsed = SipMessage.parse(invite)
        assertNotNull(parsed)
        assertTrue(parsed.isRequest)
        assertEquals("INVITE", parsed.method)
        assertEquals("sip:100@example.org", parsed.uri)
        assertEquals("call-42", parsed.callId())
        assertEquals(7L to "INVITE", parsed.cseq())
        assertEquals("v=0\r\no=- 1 1 IN IP4 0.0.0.0", parsed.body)
        assertEquals(invite, parsed.render())
    }

    @Test
    fun `response parses status reason and dialog facts`() {
        val parsed = SipMessage.parse(ringing)
        assertNotNull(parsed)
        assertTrue(!parsed.isRequest)
        assertEquals(180, parsed.statusCode)
        assertEquals("Ringing", parsed.reason)
        assertEquals(7L to "INVITE", parsed.cseq())
        assertEquals("call-42", parsed.callId())
        assertEquals("<sip:100@example.org>;tag=callee9", parsed.header("To"))
    }

    @Test
    fun `cseq method is normalized to uppercase`() {
        val parsed =
            SipMessage.parse(
                "SIP/2.0 200 OK\r\nCall-ID: c\r\nCSeq: 3 bye\r\n\r\n",
            )
        assertNotNull(parsed)
        assertEquals(3L to "BYE", parsed.cseq())
    }

    @Test
    fun `header lookup is case-insensitive`() {
        val parsed = SipMessage.parse(invite)
        assertNotNull(parsed)
        assertEquals("call-42", parsed.header("CALL-ID"))
        assertEquals("call-42", parsed.header("call-id"))
        assertEquals(listOf("7 INVITE"), parsed.headersOf("cseq"))
    }

    @Test
    fun `malformed input parses to null instead of throwing`() {
        assertNull(SipMessage.parse(""))
        assertNull(SipMessage.parse("garbage without structure"))
        assertNull(SipMessage.parse("SIP/2.0 not-a-number"))
        assertNull(SipMessage.parse("INVITE missing-version"))
    }

    @Test
    fun `request render includes crlf framing and empty body`() {
        val message =
            SipMessage(
                isRequest = true,
                method = "OPTIONS",
                uri = "sip:x@y",
                statusCode = 0,
                reason = "",
                headers = listOf(SipHeader("Call-ID", "abc")),
                body = "",
            )
        assertEquals("OPTIONS sip:x@y SIP/2.0\r\nCall-ID: abc\r\n\r\n", message.render())
    }
}
