package software.larsartmann.webphone.sip

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The wire-sequence contracts that must hold against any PBX: a
 * ring-out abort CANCELs the INVITE's own transaction (RFC 3261 §9.1),
 * a confirmed dialog can no longer be CANCELed, a 487 after our
 * CANCEL tears down cleanly and gets its ACK, and BYE rides the
 * confirmed dialog's tags.
 */
class SipEngineTest {

    private class Recorder {
        val frames = mutableListOf<String>()
        fun send(raw: String) {
            frames.add(raw)
        }
    }

    private class RecordingListener : SipEngine.Listener {
        val registration = mutableListOf<Boolean>()
        var incoming: SipEngine.IncomingInvite? = null
        val progress = mutableListOf<Pair<String, Boolean>>()
        val established = mutableListOf<String>()
        val ended = mutableListOf<String>()
        val transferVerdicts = mutableListOf<Pair<String, String>>()

        override fun onRegistrationState(registered: Boolean) {
            registration.add(registered)
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

    private fun response(status: Int, reason: String, callId: String, cseq: String, toTag: String?): String =
        buildString {
            append("SIP/2.0 $status $reason\r\n")
            append("Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKserver1;rport\r\n")
            append("From: \"WebPhone\" <sip:200@example.org>;tag=caller1\r\n")
            append("To: <sip:100@example.org>${toTag?.let { ";tag=$it" } ?: ""}\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq\r\n")
            if (status == 200) append("Contact: <sip:100@uac.example.org;transport=ws>\r\n")
            append("\r\n")
        }

    private fun branchOf(via: String?): String =
        assertNotNull(via).substringAfter("branch=").substringBefore(';')

    @Test
    fun `cancel during ring-out reuses the invite transaction`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0\r\no=- 1 1 IN IP4 0.0.0.0") {}
        assertEquals(1, recorder.frames.size)
        val invite = assertNotNull(SipMessage.parse(recorder.frames[0]))
        val inviteBranch = branchOf(invite.header("Via"))
        assertEquals("1 INVITE", invite.header("CSeq"))

        engine.receive(response(180, "Ringing", callId, "1 INVITE", toTag = "callee9"))
        assertEquals(listOf(callId to true), listener.progress)

        engine.cancel(callId)
        assertEquals(2, recorder.frames.size)
        val cancel = assertNotNull(SipMessage.parse(recorder.frames[1]))
        assertEquals("CANCEL", cancel.method)
        assertEquals(callId, cancel.callId())
        assertEquals(1L to "CANCEL", cancel.cseq())
        assertEquals(inviteBranch, branchOf(cancel.header("Via")))
        assertEquals("sip:100@example.org", cancel.uri)
        // The provisional response's To tag rides along (RFC 3261 §9.1).
        assertTrue(
            cancel.header("To").orEmpty().contains("callee9"),
            "CANCEL To must carry the dialog tag: ${cancel.header("To")}",
        )
    }

    @Test
    fun `cancel after 200 is refused - only BYE may end a confirmed dialog`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0") {}
        engine.receive(response(200, "OK", callId, "1 INVITE", toTag = "callee9"))
        // The 200 ladder ACKed and established.
        assertEquals(2, recorder.frames.size)
        assertEquals(listOf(callId), listener.established)

        engine.cancel(callId)
        assertEquals(2, recorder.frames.size, "no CANCEL may follow a final response")
    }

    @Test
    fun `487 after our cancel tears down cleanly and acks the transaction`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0") {}
        engine.receive(response(180, "Ringing", callId, "1 INVITE", toTag = "callee9"))
        engine.cancel(callId)
        engine.receive(response(487, "Request Terminated", callId, "1 INVITE", toTag = "callee9"))

        assertEquals(listOf(callId), listener.ended)
        assertEquals(3, recorder.frames.size)
        val ack = assertNotNull(SipMessage.parse(recorder.frames[2]))
        assertEquals("ACK", ack.method)
        assertEquals("1 ACK", ack.header("CSeq"))
        assertEquals("sip:100@example.org", ack.uri)
    }

    @Test
    fun `bye on a confirmed dialog carries the negotiated tags`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        val callId = engine.invite("100", "v=0") {}
        engine.receive(response(200, "OK", callId, "1 INVITE", toTag = "callee9"))
        engine.bye(callId)

        assertEquals(3, recorder.frames.size)
        val bye = assertNotNull(SipMessage.parse(recorder.frames[2]))
        assertEquals("BYE", bye.method)
        assertEquals("2 BYE", bye.header("CSeq"))
        assertEquals(callId, bye.callId())
        assertTrue(bye.header("To").orEmpty().contains("callee9"))
        // BYE is the LOCAL end — the engine does not report it back as
        // an onCallEnded event (that channel is for the remote side).
        assertEquals(emptyList(), listener.ended)
    }

    @Test
    fun `cancel of an unknown or cleaned-up leg is a no-op`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        engine.cancel("never-dialed")
        assertEquals(0, recorder.frames.size)
    }

    @Test
    fun `inbound invite surfaces from and to users with a responder`() = runTest {
        val recorder = Recorder()
        val listener = RecordingListener()
        val engine = engineWith(recorder, listener)

        engine.receive(
            listOf(
                "INVITE sip:200@example.org SIP/2.0",
                "Via: SIP/2.0/WSS fs.example.org;branch=z9hG4bKserver2;rport",
                "From: \"Alice\" <sip:100@example.org>;tag=fstag",
                "To: <sip:200@example.org>",
                "Call-ID: inbound-1",
                "CSeq: 5 INVITE",
                "Content-Type: application/sdp",
                "",
                "v=0",
            ).joinToString("\r\n"),
        )
        val invite = listener.incoming
        assertNotNull(invite)
        assertEquals("inbound-1", invite.callId)
        assertEquals("100", invite.fromUser)
        assertEquals("Alice", invite.fromDisplay)

        invite.respondWith(603, null)
        assertEquals(1, recorder.frames.size)
        val decline = assertNotNull(SipMessage.parse(recorder.frames[0]))
        assertEquals(603, decline.statusCode)
        assertEquals("inbound-1", decline.callId())
        assertEquals("5 INVITE", decline.header("CSeq"))
        // The roles are mirrored on an inbound dialog: the remote's
        // tag rides From, OUR tag was added to To (RFC 3261 §12.1.2).
        assertTrue(decline.header("To").orEmpty().contains("tag="), "our To tag must be added: ${decline.header("To")}")
        assertTrue(decline.header("From").orEmpty().contains("fstag"))
    }
}
