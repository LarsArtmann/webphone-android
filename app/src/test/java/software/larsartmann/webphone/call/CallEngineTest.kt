package software.larsartmann.webphone.call

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.larsartmann.webphone.sip.SipEngine
import software.larsartmann.webphone.sip.SipMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The orchestrator's wire contracts without WebRTC on the JVM: hangUp
 * dispatches by phase (603 on a ringing inbound, BYE on a confirmed
 * dialog), and — the stuck-line regression — an ENDED line accepts
 * the next call again.
 */
class CallEngineTest {

    private class Recorder {
        val frames = mutableListOf<String>()
        fun send(raw: String) {
            frames.add(raw)
        }
    }

    private fun TestScope.attachedEngine(recorder: Recorder): SipEngine = SipEngine(
        scope = this,
        config = SipEngine.Config(
            wsUrl = "wss://example.org/sip",
            sipDomain = "example.org",
            extension = "200",
            password = "secret",
        ),
        sender = recorder::send,
    ).also { CallEngine.attach(it) }

    private fun invite(callId: String): SipEngine.IncomingInvite = SipEngine.IncomingInvite(
        callId = callId,
        fromUser = "100",
        fromDisplay = "Alice",
        sdp = "v=0",
        respondWith = { _, _ -> },
    )

    @Test
    fun `hangUp on a ringing inbound declines with 603`() = runTest {
        val recorder = Recorder()
        attachedEngine(recorder)
        var declinedStatus = 0
        CallEngine.onIncomingCall(
            invite("in-1").copy(respondWith = { status, _ -> declinedStatus = status }),
        )

        assertEquals(CallEngine.Phase.RINGING_IN, CallEngine.state.value.phase)
        assertEquals("Alice", CallEngine.state.value.remoteParty)
        CallEngine.hangUp()

        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
        assertEquals(CallEngine.EndedBy.LOCAL, CallEngine.state.value.endedBy)
        assertEquals(603, declinedStatus, "a ringing-inbound hang-up declines with 603")
    }

    @Test
    fun `hangUp on an active dialog dispatches the BYE path - not a decline`() = runTest {
        val recorder = Recorder()
        attachedEngine(recorder)
        CallEngine.onIncomingCall(invite("in-2"))
        // Confirm the dialog WITHOUT WebRTC: the established callback
        // carries the remote answer (media stays null — fine for the
        // state machine under test). The BYE frame itself (tags, CSeq)
        // is pinned in SipEngineTest; here the contract is the
        // DISPATCH: an ACTIVE hang-up must not 603/decline.
        CallEngine.onCallEstablished("in-2", remoteSdp = "v=0")

        assertEquals(CallEngine.Phase.ACTIVE, CallEngine.state.value.phase)
        CallEngine.hangUp()

        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
        assertEquals(CallEngine.EndedBy.LOCAL, CallEngine.state.value.endedBy)
        assertTrue(
            recorder.frames.none { SipMessage.parse(it)?.statusCode == 603 },
            "an ACTIVE hang-up must not decline: ${recorder.frames}",
        )
    }

    @Test
    fun `an ENDED line accepts the next call - the stuck-line regression`() = runTest {
        val recorder = Recorder()
        attachedEngine(recorder)
        CallEngine.onIncomingCall(invite("in-3"))
        CallEngine.hangUp()
        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)

        // The second call of the day must ring, not get 486'd.
        CallEngine.onIncomingCall(invite("in-4"))
        assertEquals(CallEngine.Phase.RINGING_IN, CallEngine.state.value.phase)
        assertEquals("in-4", CallEngine.state.value.callId)

        CallEngine.cancelRinging()
        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
    }

    @Test
    fun `a second inbound while one is live is refused with 486`() = runTest {
        attachedEngine(Recorder())
        var refusedStatus = 0
        CallEngine.onIncomingCall(
            invite("live").copy(
                respondWith = { status, _ -> refusedStatus = status },
            ),
        )
        CallEngine.onIncomingCall(
            invite("second").copy(
                respondWith = { status, _ -> refusedStatus = status },
            ),
        )
        assertEquals(486, refusedStatus, "single-call policy: the second INVITE gets Busy")
        assertEquals("live", CallEngine.state.value.callId)
    }

    @Test
    fun `registration verdicts map to status plus failure cause`() {
        CallEngine.onRegistrationState(SipEngine.RegistrationState.REGISTERED)
        assertEquals(
            CallEngine.RegistrationInfo(CallEngine.Registration.REGISTERED, CallEngine.RegistrationFailure.NONE),
            CallEngine.registration.value,
        )
        CallEngine.onRegistrationState(SipEngine.RegistrationState.AUTH_REJECTED)
        assertEquals(
            CallEngine.RegistrationInfo(CallEngine.Registration.OFF, CallEngine.RegistrationFailure.AUTH_REJECTED),
            CallEngine.registration.value,
        )
        CallEngine.onRegistrationState(SipEngine.RegistrationState.OFFLINE)
        assertEquals(
            CallEngine.RegistrationInfo(CallEngine.Registration.OFF, CallEngine.RegistrationFailure.OFFLINE),
            CallEngine.registration.value,
        )
    }
}
