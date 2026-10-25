package software.larsartmann.webphone.sip

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The silence half of the watchdog (the wedged-socket case the
 * failure callbacks never report): a registered socket that has been
 * quiet longer than the refresh cadence is DEAD and must be rebuilt.
 */
class SipEngineSilenceTest {

    private fun TestScope.engine(): SipEngine = SipEngine(
        // backgroundScope: the silence watchdog is a daemon loop — it
        // must not hold the test open (runTest cancels it at the end).
        scope = backgroundScope,
        config = SipEngine.Config(
            wsUrl = "wss://example.org/sip",
            sipDomain = "example.org",
            extension = "200",
            password = "secret",
        ),
        sender = { },
    )

    private fun registerOk(callId: String): String = listOf(
        "SIP/2.0 200 OK",
        "Via: SIP/2.0/WSS host.invalid;branch=z9hG4bKserver1;rport",
        "From: <sip:200@example.org>;tag=a",
        "To: <sip:200@example.org>",
        "Call-ID: $callId",
        "CSeq: 1 REGISTER",
        "",
    ).joinToString("\r\n")

    @Test
    fun `unregistered engine is never silent`() = runTest {
        val engine = engine()
        assertFalse(engine.isSilent(System.currentTimeMillis()))
        assertFalse(engine.isSilent(Long.MAX_VALUE))
    }

    @Test
    fun `registered engine goes silent past the threshold`() = runTest {
        val engine = engine()
        engine.receive(registerOk("reg-silence"))
        val now = System.currentTimeMillis()
        // The 200 itself was wire activity: fresh.
        assertFalse(engine.isSilent(now))
        // Quiet longer than a refresh cadence (300 s) plus margin: dead.
        assertTrue(engine.isSilent(now + 6 * 60_000L + 1))
    }
}
