package software.larsartmann.webphone.wire

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import software.larsartmann.webphone.shell.SessionApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The deployment-level live check (wire fixture (a), pareto T16): a
 * LOOPBACK webphone must satisfy the app's REAL decoders - no fixture,
 * the served bytes themselves. The decoders run pure
 * ([SessionApi.decodeIslandConfig] + [SessionApi.parseSessionIdentity]);
 * Robolectric is here ONLY because [SessionApi.parseSessionIdentity]
 * reads through org.json, which is a compile-time stub on the plain
 * JVM - the real parser needs the real implementation. The transport
 * stays plain java.net: the production manifest keeps cleartext OFF,
 * and that fence binds OkHttp (which the app rightly honors), not
 * java.net - so this test can speak loopback http without weakening
 * the production posture. Gated on WEBPHONE_LIVE_BASE so the ordinary
 * unit run stays hermetic; scripts/loopback-live-check.sh boots the
 * server and drives this suite. The session half additionally needs
 * WEBPHONE_LIVE_EXT + WEBPHONE_LIVE_PASSWORD (loopback boots skip the
 * directory verify, so any values mint).
 *
 * The mint rides the server's nosurf double-submit CSRF fence the way
 * the login page's own JS does: a GET /login hands out the `csrf_token`
 * cookie (the masked form) AND the `<meta name="csrf-token">` clear
 * token in its body; the POST echoes the clear token in `X-CSRF-Token`
 * beside the cookie. The mint answers 201, sets `webphone_session`,
 * and clears the anonymous csrf cookie.
 */
@RunWith(RobolectricTestRunner::class)
class LoopbackLiveContractTest {
    private val base = System.getenv("WEBPHONE_LIVE_BASE")

    private data class LiveAnswer(
        val code: Int,
        val body: String?,
        val setCookies: List<String>,
    )

    private fun http(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): LiveAnswer {
        val connection =
            (java.net.URL(base!!.trimEnd('/') + path).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 5_000
                readTimeout = 5_000
                for ((name, value) in headers) {
                    setRequestProperty(name, value)
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
        if (body != null) {
            connection.outputStream.use { it.write(body.encodeToByteArray()) }
        }
        val code = connection.responseCode
        val text =
            (
                connection.inputStream.takeIf {
                    code in 200..299
                } ?: connection.errorStream
            )?.bufferedReader()?.use { it.readText() }
        return LiveAnswer(code, text, connection.headerFields?.get("Set-Cookie").orEmpty())
    }

    private fun cookiePair(
        cookies: List<String>,
        name: String,
    ): String? =
        cookies
            .firstOrNull { it.startsWith("$name=") }
            ?.substringBefore(';')

    @Test
    fun `the live island config satisfies the real decoder`() {
        if (base.isNullOrEmpty()) return
        val (code, body, _) = http("GET", "/config.js")
        assertEquals(200, code, "the live /config.js must answer 200")
        val config =
            assertNotNull(
                SessionApi.decodeIslandConfig(body ?: fail("empty /config.js body")),
                "the real decoder must read the live /config.js bytes",
            )
        assertTrue(config.sipDomain.isNotEmpty(), "the live /config.js must carry a non-empty sipDomain")
        assertTrue(config.websocketPath.isNotEmpty(), "the live /config.js must carry a non-empty websocketPath")
    }

    @Test
    fun `the live session answer satisfies the real resume decoder`() {
        if (base.isNullOrEmpty()) return
        val extension = System.getenv("WEBPHONE_LIVE_EXT")
        val password = System.getenv("WEBPHONE_LIVE_PASSWORD")
        if (extension.isNullOrEmpty() || password.isNullOrEmpty()) return

        val handshake = http("GET", "/login")
        assertEquals(200, handshake.code, "the anonymous /login must render")
        val csrfToken =
            assertNotNull(
                """name="csrf-token" content="([^"]+)""""
                    .toRegex()
                    .find(handshake.body ?: fail("empty /login body"))
                    ?.groupValues
                    ?.get(1),
                "/login must carry the csrf-token meta tag",
            )
        val csrfCookie =
            assertNotNull(
                cookiePair(handshake.setCookies, "csrf_token"),
                "/login must set the csrf_token cookie",
            )

        val payload = """{"extension":"$extension","password":"$password"}"""
        val mint =
            http(
                "POST",
                "/api/session",
                payload,
                headers = mapOf("Cookie" to csrfCookie, "X-CSRF-Token" to csrfToken),
            )
        assertEquals(
            201,
            mint.code,
            "the loopback mint must succeed (loopback skips the directory verify): ${mint.body}",
        )
        val sessionCookie =
            assertNotNull(
                cookiePair(mint.setCookies, "webphone_session"),
                "the mint must set the session cookie",
            )

        val resume = http("GET", "/api/session", headers = mapOf("Cookie" to sessionCookie))
        assertEquals(200, resume.code, "the minted cookie must resume")
        val sessionBody = resume.body ?: fail("empty resume body")

        val configAnswer = http("GET", "/config.js")
        assertEquals(200, configAnswer.code, "the live /config.js must answer 200")
        val config = SessionApi.decodeIslandConfig(configAnswer.body ?: fail("empty /config.js body"))

        val identity =
            assertNotNull(
                SessionApi.parseSessionIdentity(sessionBody, config, base),
                "the real resume decoder must read the live session answer",
            )
        assertTrue(
            identity.lines.any { it.extension == extension },
            "the live lines[] wire must carry the signed-in extension: ${identity.lines}",
        )
    }
}
