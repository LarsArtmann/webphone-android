package software.larsartmann.webphone.sip

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The INVITE-challenge ladder (RFC 3261 §13.2.2 / §22.1): a PBX may
 * 401/407 the INVITE itself — the answer is a new transaction in the
 * SAME dialog with digest credentials and the SAME offer SDP, bounded
 * like the REGISTER ladder so a bad password ends the call instead of
 * looping forever.
 */
class SipEngineAuthTest {

    private class Recorder {
        val frames = mutableListOf<String>()
        fun send(raw: String) {
            frames.add(raw)
        }
    }

    private class RecordingListener : SipEngine.Listener {
        val states = mutableListOf<SipEngine.RegistrationState>()
        var incoming: SipEngine.IncomingInvite? = null
        val progress = mutableListOf<Pair<String, Boolean>>()
        val established = mutableListOf<String>()
        val ended = mutableListOf<String>()
        val transferVerdicts = mutableListOf<Pair<String, String>>()

        override fun onRegistrationState(state: SipEngine.RegistrationState) {
            states.add(state)
        }

        override fun onIncomingCall(invite: SipEngine.IncomingInvite) {
            incoming = invite
        }

        override fun onCallProgress(callId: String, ringing: Boolean) {
            progress.add(callId to ringing)
        }

        override fun onCallEstablished(callId: String, remoteSdp: String) {
            established.add(callId)
        }

        override fun onCallEnded(callId: String) {
            ended.add(callId)
        }

        override fun onTransferVerdict(callId: String, verdict: String) {
            transferVerdicts.add(callId to verdict)
        }
    }

    private fun TestScope.engineWith(recorder: Recorder, listener: RecordingListener): SipEngine =
        SipEngine(
            scope = this,
            config = SipEngine.Config(
                wsUrl = "wss://example.org/sip",
                sipDomain = "example.org",
                extension = "200",
                password = "secret",
            ),
            sender = recorder::send,
        ).also { it.listener = listener }

    private fun challengeResponse(
        status: Int,
        headerName: String,
        callId: String,
        nonce: String,
        stale: Boolean = false,
    ): String = buildString {
        append("SIP/2.0 $status ${if (status == 401) "Unauthorized" else "Payment Required"}\r\n")
        append("Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKserver1;rport\r\n")
        append("From: \"WebPhone\" <sip:200@example.org>;tag=caller1\r\n")
        append("To: <sip:100@example.org>;tag=callee9\r\n")
        append("Call-ID: $callId\r\n")
        append("CSeq: 1 INVITE\r\n")
        append(
            "$headerName: Digest realm=\"example.org\", nonce=\"$nonce\""
                + (if (stale) ", stale=true" else "") + ", qop=\"auth\"\r\n",
        )
        append("\r\n")
    }

    private fun parse(raw: String): SipMessage = assertNotNull(SipMessage.parse(raw))

    @Test
    fun `401 on invite acks then re-invites with credentials and the same sdp`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val offer = "v=0\r\no=- 1 1 IN IP4 0.0.0.0"
        val callId = engine.invite("100", offer) { }
        engine.receive(challengeResponse(401, "WWW-Authenticate", callId, nonce = "nonce1"))

        assertEquals(3, recorder.frames.size, "ACK for the 401, then the credentialed INVITE")
        val ack = parse(recorder.frames[1])
        assertEquals("ACK", ack.method)
        assertEquals("1 ACK", ack.header("CSeq"))

        val reinvite = parse(recorder.frames[2])
        assertEquals("INVITE", reinvite.method)
        assertEquals(callId, reinvite.callId())
        assertEquals("2 INVITE", reinvite.header("CSeq"))
        assertEquals(offer, reinvite.body, "the challenge re-INVITE must carry the SAME offer")
        val authorization = assertNotNull(reinvite.header("Authorization"))
        assertTrue(authorization.startsWith("Digest username=\"200\", realm=\"example.org\", nonce=\"nonce1\""))
        assertTrue(authorization.contains("uri=sip:100@example.org"))
        assertTrue(reinvite.header("To").orEmpty().contains("callee9"), "dialog tag rides along")
        assertTrue(reinvite.header("Via").orEmpty().contains("z9hG4bK"), "a fresh branch for the new transaction")
        assertTrue(listener.ended.isEmpty(), "a challenge is not a call failure")
    }

    @Test
    fun `407 answers with proxy credentials`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0") { }
        engine.receive(challengeResponse(407, "Proxy-Authenticate", callId, nonce = "nonce7"))

        val reinvite = parse(recorder.frames[2])
        assertNull(reinvite.header("Authorization"), "407 must not produce a WWW credential")
        val proxyAuth = assertNotNull(reinvite.header("Proxy-Authorization"))
        assertTrue(proxyAuth.contains("nonce=\"nonce7\""))
    }

    @Test
    fun `second challenge ends the call instead of looping`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0") { }
        engine.receive(challengeResponse(401, "WWW-Authenticate", callId, nonce = "nonce1"))
        engine.receive(challengeResponse(401, "WWW-Authenticate", callId, nonce = "nonce2"))

        assertEquals(listOf(callId), listener.ended, "two failed credentials must not loop")
        assertEquals(4, recorder.frames.size, "INVITE, ACK, INVITE, ACK — then give up")
    }

    @Test
    fun `stale=true grants one extra retry then stops`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0") { }
        engine.receive(challengeResponse(401, "WWW-Authenticate", callId, nonce = "n1", stale = true))
        engine.receive(challengeResponse(401, "WWW-Authenticate", callId, nonce = "n2", stale = true))
        // Both stale challenges were answered:
        assertEquals(5, recorder.frames.size, "INVITE, ACK, INVITE, ACK, INVITE")

        engine.receive(challengeResponse(401, "WWW-Authenticate", callId, nonce = "n3", stale = true))
        assertEquals(listOf(callId), listener.ended, "the stale grace is one round, not infinite")
    }

    @Test
    fun `200 after the auth ladder establishes normally`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0") { }
        engine.receive(challengeResponse(401, "WWW-Authenticate", callId, nonce = "nonce1"))
        engine.receive(
            buildString {
                append("SIP/2.0 200 OK\r\n")
                append("Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKserver1;rport\r\n")
                append("From: \"WebPhone\" <sip:200@example.org>;tag=caller1\r\n")
                append("To: <sip:100@example.org>;tag=callee9\r\n")
                append("Call-ID: $callId\r\n")
                append("CSeq: 2 INVITE\r\n")
                append("Contact: <sip:100@uac.example.org;transport=ws>\r\n")
                append("\r\n")
            },
        )
        assertEquals(listOf(callId), listener.established)
    }

    @Test
    fun `register wrong-password loop is bounded`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        engine.receive(
            listOf(
                "SIP/2.0 401 Unauthorized",
                "Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKserver1;rport",
                "From: <sip:200@example.org>;tag=a",
                "To: <sip:200@example.org>",
                "Call-ID: reg-1",
                "CSeq: 1 REGISTER",
                "WWW-Authenticate: Digest realm=\"example.org\", nonce=\"r1\"",
                "",
            ).joinToString("\r\n"),
        )
        engine.receive(
            listOf(
                "SIP/2.0 401 Unauthorized",
                "Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKserver1;rport",
                "From: <sip:200@example.org>;tag=a",
                "To: <sip:200@example.org>",
                "Call-ID: reg-1",
                "CSeq: 2 REGISTER",
                "WWW-Authenticate: Digest realm=\"example.org\", nonce=\"r2\"",
                "",
            ).joinToString("\r\n"),
        )

        val registerCount = recorder.frames.count { raw -> parse(raw).method == "REGISTER" }
        assertEquals(1, registerCount, "bad credentials get ONE credentialed retry, never a loop")
        assertEquals(listOf(SipEngine.RegistrationState.AUTH_REJECTED), listener.states)
    }
}
