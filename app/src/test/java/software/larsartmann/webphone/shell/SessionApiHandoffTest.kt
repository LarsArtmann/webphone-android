package software.larsartmann.webphone.shell

import android.webkit.CookieManager
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import software.larsartmann.webphone.App
import software.larsartmann.webphone.shell.SessionApi.HandoffRedemption
import software.larsartmann.webphone.shell.SessionApi.HandoffSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sign-in handoff's two HTTP seams against a scripted stack (the
 * wire contract webphone's `internal/server/auth/sso_handoff.go` owns,
 * exercised through the REAL client and the REAL cookie bridge):
 *
 * - the capability probe: one GET whose status is the stack's whole
 *   answer (2xx speaks it, 404 never heard of it, anything else or a
 *   dead link is UNREACHABLE — never read as a verdict about the stack);
 * - the ticket redemption: one GET carrying the shell header, whose
 *   redirect is RETURNED for the WebView to land on and never followed,
 *   and whose session cookie lands in the shell's one cookie store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34])
class SessionApiHandoffTest {
    private lateinit var server: MockWebServer
    private val ticket = "t".repeat(43)

    @Before
    fun pointTheSeamAtTheScriptedStack() {
        CallLayer.wiringEnabled = false
        server = MockWebServer()
        server.start()
        App.origin = server.url("/").toString().trimEnd('/')
        CookieManager.getInstance().removeAllCookies(null)
    }

    @After
    fun stopTheScriptedStack() {
        server.shutdown()
    }

    private fun redirect(
        landing: String,
        cookie: String? = null,
    ): MockResponse =
        MockResponse()
            .setResponseCode(303)
            .addHeader("Location", landing)
            .apply { cookie?.let { addHeader("Set-Cookie", it) } }

    private fun redeem(): HandoffRedemption = SessionApi.redeemSignInHandoff(ticket)

    @Test
    fun `the capability classifier reads the stack's whole answer`() {
        assertEquals(HandoffSupport.SUPPORTED, SessionApi.handoffSupportFromCode(204))
        assertEquals(HandoffSupport.SUPPORTED, SessionApi.handoffSupportFromCode(200))
        assertEquals(HandoffSupport.UNSUPPORTED, SessionApi.handoffSupportFromCode(404))
        val unreachable =
            listOf(null, 500, 502, 429, 401, 403, 301, 302).map(SessionApi::handoffSupportFromCode)
        assertEquals(
            List(unreachable.size) { HandoffSupport.UNREACHABLE },
            unreachable,
            "only the stack's own 404 says no — anything else is a non-answer",
        )
    }

    @Test
    fun `the probe asks the shell route and reads 204 as support`() {
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals(HandoffSupport.SUPPORTED, SessionApi.probeSignInHandoff())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/auth/oauth2/shell", request.path)
    }

    @Test
    fun `a stack without the route is unsupported and an outage is unreachable`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(HandoffSupport.UNSUPPORTED, SessionApi.probeSignInHandoff())

        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(HandoffSupport.UNREACHABLE, SessionApi.probeSignInHandoff())

        server.shutdown()
        assertEquals(
            HandoffSupport.UNREACHABLE,
            SessionApi.probeSignInHandoff(),
            "a dead link is never a verdict about the stack",
        )
    }

    @Test
    fun `redemption presents the ticket with the shell header and never follows the landing`() {
        server.enqueue(redirect("/", "webphone_session=ticket-session; Path=/; HttpOnly; Secure"))
        // Only a followed redirect would ever consume this one.
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals(HandoffRedemption.Landed("/"), redeem())

        assertEquals(1, server.requestCount, "the landing page is the WebView's to render, not this client's")
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/auth/oauth2/finish?ticket=$ticket", request.path)
        assertEquals(HANDOFF_SHELL, request.getHeader(SessionApi.HANDOFF_SHELL_HEADER))
    }

    @Test
    fun `the redeemed session lands in the shell's one cookie store`() {
        // No `Secure` here on purpose: the scripted stack is plain
        // loopback http, and a Secure cookie is only ever stored from a
        // secure origin. The real stack is TLS-fronted and sets it.
        server.enqueue(redirect("/", "webphone_session=ticket-session; Path=/; HttpOnly"))

        redeem()

        val jar = CookieManager.getInstance().getCookie(App.origin)
        assertTrue(
            jar?.contains("webphone_session=ticket-session") == true,
            "the island must boot signed-in on its next load — the jar holds: $jar",
        )
    }

    @Test
    fun `a stale ticket lands on the stack's own refusal sentence`() {
        server.enqueue(redirect("/login?sso_error=stale"))

        assertEquals(HandoffRedemption.Landed("/login?sso_error=stale"), redeem())
    }

    @Test
    fun `an answer outside the contract fails`() {
        val outside =
            listOf(
                MockResponse().setResponseCode(400),
                MockResponse().setResponseCode(303),
                redirect("https://evil.example/"),
                redirect("//evil.example/"),
                MockResponse().setResponseCode(200),
                MockResponse().setResponseCode(302).addHeader("Location", "/"),
            )
        for (answer in outside) {
            server.enqueue(answer)
            assertEquals(HandoffRedemption.Failed, redeem(), "status ${answer.status}")
        }

        server.shutdown()
        assertEquals(HandoffRedemption.Failed, redeem(), "a dead link")
    }

    @Test
    fun `a ticket outside the stack's shape is never presented`() {
        val forged =
            listOf(
                "",
                "short",
                "has spaces has spaces has spaces",
                "t".repeat(129),
                "$ticket==",
                "$ticket&extra=1",
            )
        for (bad in forged) {
            assertEquals(HandoffRedemption.Failed, SessionApi.redeemSignInHandoff(bad), "'$bad'")
        }
        assertEquals(0, server.requestCount, "a forged ticket never leaves the shell")
    }

    @Test
    fun `the landing classifier accepts only the contract's origin-relative redirect`() {
        assertEquals("/", SessionApi.handoffLanding(303, "/"))
        assertEquals("/login?sso_error=stale", SessionApi.handoffLanding(303, "/login?sso_error=stale"))
        assertEquals(null, SessionApi.handoffLanding(303, "///"), "an empty host is a foreign authority")
        assertEquals(null, SessionApi.handoffLanding(303, "//evil.example"))
        assertEquals(null, SessionApi.handoffLanding(303, "https://evil.example"))
        assertEquals(null, SessionApi.handoffLanding(303, null))
        assertEquals(null, SessionApi.handoffLanding(303, ""))
        assertEquals(null, SessionApi.handoffLanding(302, "/"))
        assertEquals(null, SessionApi.handoffLanding(null, "/"))
    }
}
