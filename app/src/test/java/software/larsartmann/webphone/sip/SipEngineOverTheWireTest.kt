package software.larsartmann.webphone.sip

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The REGISTER ladder over a REAL websocket (P13/W120/W122): the
 * engine rides an actual OkHttp socket against a scripted MockWebServer
 * PBX — not the sender-seam — so the soak proves the wire behavior the
 * device will meet: challenge → credentialed retry → registered, the
 * wrong-password loop ENDS (no wire flood), and stale=true buys the
 * one extra retry. The INVITE ladder's bounds are pinned seam-level in
 * [SipEngineAuthTest] (a MockWebServer INVITE soak would need WebRTC's
 * native offer — JVM-impossible by design).
 */
class SipEngineOverTheWireTest {

    private class States {
        val seen = LinkedBlockingQueue<SipEngine.RegistrationState>()

        fun await(expected: SipEngine.RegistrationState) {
            while (true) {
                val next = assertNotNull(
                    seen.poll(10, TimeUnit.SECONDS),
                    "timed out waiting for $expected",
                )
                if (next == expected) return
            }
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var states: States
    private var socket: WebSocket? = null
    private val frames = LinkedBlockingQueue<String>()

    /** Echo helpers: SIP responses MUST reuse the request's Call-ID,
     *  CSeq and Via or the engine drops them as foreign transactions. */
    private fun headerOf(frame: String, name: String): String =
        frame.lineSequence()
            .firstOrNull { it.startsWith(name, ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?: throw AssertionError("no $name in frame:\n$frame")

    private fun respond(status: Int, reason: String, request: String, auth: String? = null) {
        val ws = assertNotNull(socket, "PBX websocket not open")
        val headers = buildList {
            add("Via: ${headerOf(request, "Via")}")
            add("From: ${headerOf(request, "From")}")
            add("To: ${headerOf(request, "To")}")
            add("Call-ID: ${headerOf(request, "Call-ID")}")
            add("CSeq: ${headerOf(request, "CSeq")}")
            add("Contact: <sip:100@pbx.example;transport=wss>")
            if (auth != null) add(auth)
            add("Content-Length: 0")
        }
        ws.send((listOf("SIP/2.0 $status $reason") + headers).joinToString("\r\n") + "\r\n\r\n")
    }

    private fun challenge(stale: Boolean): String {
        val base = "Digest realm=\"pbx.example\", nonce=\"nonce-${System.nanoTime()}\", algorithm=MD5"
        return "WWW-Authenticate: ${if (stale) "$base, stale=true" else base}"
    }

    private fun nextFrame(): String =
        assertNotNull(frames.poll(10, TimeUnit.SECONDS), "timed out waiting for a SIP frame")

    private fun awaitRegister(): String {
        while (true) {
            val frame = nextFrame()
            if (frame.startsWith("REGISTER")) return frame
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        states = States()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        socket = webSocket
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        frames.add(text)
                    }
                },
            ),
        )
        server.start()
    }

    @After
    fun tearDown() {
        socket?.close(1000, "test done")
        server.shutdown()
    }

    private fun engine(): SipEngine =
        SipEngine(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
            config = SipEngine.Config(
                wsUrl = server.url("/sip").toString(),
                sipDomain = "pbx.example",
                extension = "100",
                password = "secret",
            ),
            listener = object : SipEngine.Listener {
                override fun onRegistrationState(state: SipEngine.RegistrationState) {
                    states.seen.add(state)
                }

                override fun onIncomingCall(invite: SipEngine.IncomingInvite) = Unit
                override fun onCallProgress(callId: String, ringing: Boolean) = Unit
                override fun onCallEstablished(callId: String, remoteSdp: String) = Unit
                override fun onCallEnded(callId: String) = Unit
                override fun onTransferVerdict(callId: String, verdict: String) = Unit
            },
        )

    @Test
    fun `the challenge ladder registers over a real socket`() = runTest {
        val engine = engine()
        engine.connect()
        try {
            val first = awaitRegister()
            assertTrue("Authorization" !in first, "the first REGISTER rides bare")
            respond(401, "Unauthorized", first, challenge(stale = false))

            val second = awaitRegister()
            val authorization = headerOf(second, "Authorization")
            assertTrue(authorization.contains("username=\"100\""), authorization)
            assertTrue(authorization.contains("realm=\"pbx.example\""), authorization)
            assertTrue("response=" in authorization, authorization)
            respond(200, "OK", second)

            states.await(SipEngine.RegistrationState.REGISTERED)
        } finally {
            engine.disconnect()
        }
    }

    @Test
    fun `a wrong password ends the ladder instead of flooding the wire`() = runTest {
        val engine = engine()
        engine.connect()
        try {
            val first = awaitRegister()
            respond(401, "Unauthorized", first, challenge(stale = false))

            val second = awaitRegister()
            respond(401, "Unauthorized", second, challenge(stale = false))

            states.await(SipEngine.RegistrationState.AUTH_REJECTED)

            assertEquals(
                2,
                frames.count { it.startsWith("REGISTER") },
                "the ladder is bounded: ONE credentialed retry, then stop",
            )
        } finally {
            engine.disconnect()
        }
    }

    @Test
    fun `stale=true buys exactly one extra retry`() = runTest {
        val engine = engine()
        engine.connect()
        try {
            respond(401, "Unauthorized", awaitRegister(), challenge(stale = true))
            respond(401, "Unauthorized", awaitRegister(), challenge(stale = true))
            respond(401, "Unauthorized", awaitRegister(), challenge(stale = true))

            states.await(SipEngine.RegistrationState.AUTH_REJECTED)

            assertEquals(
                3,
                frames.count { it.startsWith("REGISTER") },
                "stale=true grants two retries, the third challenge refuses",
            )
        } finally {
            engine.disconnect()
        }
    }
}
