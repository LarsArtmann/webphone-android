package software.larsartmann.webphone.wire

import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import software.larsartmann.webphone.push.WireFact
import software.larsartmann.webphone.push.WireFacts
import software.larsartmann.webphone.shell.SessionApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * THE cross-repo wire contract, driven by the byte-pinned fixtures the
 * server renders from its own types (webphone docs/contracts/wire/,
 * re-vendored by scripts/sync-wire-fixtures.sh — run its --check when
 * the server contract moves). Every fixture decodes through the app's
 * REAL parsers: a renamed key reds THIS suite on either side, never at
 * the first device boot.
 *
 * The pinned facts per surface:
 * - push, both transports: the fact keys {event, from?, call_id?} and
 *   the urgency split (FCM android.priority HIGH for ring / NORMAL for
 *   the rest; ntfy priority 5 / 3) — the ring must pre-empt Doze, a
 *   draft ready must not wake anybody.
 * - sessionIdentity, both credential arms: exactly ONE of `password`
 *   (legacy) or `sip_credential` (brokered) rides the answer; the SIP
 *   connection facts live in /config.js, NOT here.
 * - the registration upsert: registerPayload emits exactly the keys
 *   the server's request DTO declares.
 */
@RunWith(RobolectricTestRunner::class)
class WireContractTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/wire/$name.json")) { "missing fixture $name" }
            .readBytes()
            .decodeToString()

    // -- push: FCM data messages --------------------------------------

    @Test
    fun `the FCM ring fixture is a HIGH-priority wire fact`() {
        val message = JSONObject(fixture("push-fcm-ring")).getJSONObject("message")
        assertEquals("HIGH", message.getJSONObject("android").getString("priority"), "a ring must pre-empt Doze")
        val fact =
            WireFacts.fromFcmData(message.getJSONObject("data").stringMap()) ?: fail("ring fact must decode")
        assertEquals(WireFact(event = "ring", from = "+15550000001", callId = "call-fixture-1"), fact)
    }

    @Test
    fun `the FCM ring_end fixture is a NORMAL-priority wire fact`() {
        val message = JSONObject(fixture("push-fcm-ring-end")).getJSONObject("message")
        assertEquals("NORMAL", message.getJSONObject("android").getString("priority"))
        val fact =
            WireFacts.fromFcmData(message.getJSONObject("data").stringMap()) ?: fail("ring_end fact must decode")
        assertEquals(WireFact(event = "ring_end", from = "+15550000001", callId = "call-fixture-1"), fact)
    }

    @Test
    fun `the FCM draft_ready fixture carries the draft keys and no ring fact fields`() {
        val message = JSONObject(fixture("push-fcm-draft-ready")).getJSONObject("message")
        assertEquals("NORMAL", message.getJSONObject("android").getString("priority"))
        val data = message.getJSONObject("data").stringMap()
        assertTrue("draft_id" in data, "the draft payload's shape is the server's, not invented here")
        assertTrue("contact" in data)
        // The ring-fact decoder models {event, from?, call_id?} only:
        // a draft_ready is an event-only fact (from/callId stay null —
        // the consumer never routes it as a call).
        assertEquals(WireFact(event = "draft_ready", from = null, callId = null), WireFacts.fromFcmData(data))
    }

    // -- push: ntfy publish bodies (the websocket frame's shape) -----

    @Test
    fun `the ntfy ring fixture is a priority-5 wire fact inside the message field`() {
        val body = JSONObject(fixture("push-ntfy-ring"))
        assertEquals(5, body.getInt("priority"), "priority 5 is ntfy's max-urgency ring")
        val fact = WireFacts.fromNtfyFrame(fixture("push-ntfy-ring")) ?: fail("ring frame must decode")
        assertEquals(WireFact(event = "ring", from = "+15550000001", callId = "call-fixture-1"), fact)
    }

    @Test
    fun `the ntfy ring_end and draft_ready fixtures are priority-3`() {
        assertEquals(3, JSONObject(fixture("push-ntfy-ring-end")).getInt("priority"))
        assertEquals(3, JSONObject(fixture("push-ntfy-draft-ready")).getInt("priority"))
        assertEquals(
            WireFact(event = "ring_end", from = "+15550000001", callId = "call-fixture-1"),
            WireFacts.fromNtfyFrame(fixture("push-ntfy-ring-end")),
        )
        assertEquals(
            WireFact(event = "draft_ready", from = null, callId = null),
            WireFacts.fromNtfyFrame(fixture("push-ntfy-draft-ready")),
        )
    }

    @Test
    fun `unroutable ntfy frames answer null - the receivers log and move on`() {
        assertNull(WireFacts.fromNtfyFrame("not json at all"))
        assertNull(WireFacts.fromNtfyFrame("""{"message":"plain text, not a fact"}"""))
        assertNull(WireFacts.fromNtfyFrame("""{"event":"keyless-frame"}"""))
    }

    // -- sessionIdentity: both credential arms ------------------------

    @Test
    fun `the password-arm session fixture yields a registerable identity`() {
        val root = JSONObject(fixture("session-identity-password"))
        assertFalse(root.has("sip_credential"), "the legacy arm carries ONLY the password")
        val identity =
            SessionApi.parseSessionIdentity(
                fixture("session-identity-password"),
                SessionApi.IslandConfig(sipDomain = "pbx.example.org", websocketPath = "/sip"),
                wsOrigin = "https://pbx.example.org",
            ) ?: fail("the legacy arm must resume")
        assertEquals("1001", identity.extension)
        assertEquals("pbx.example.org", identity.sipDomain)
        assertEquals("fixture-directory-password", identity.sipPassword)
        assertEquals("wss://pbx.example.org/sip", identity.websocketUrl)
    }

    @Test
    fun `the brokered-arm session fixture yields the derived credential`() {
        val root = JSONObject(fixture("session-identity-brokered"))
        assertFalse(root.has("password"), "the brokered arm carries ONLY the sip_credential")
        val identity =
            SessionApi.parseSessionIdentity(
                fixture("session-identity-brokered"),
                SessionApi.IslandConfig(sipDomain = "pbx.example.org", websocketPath = "/sip"),
                wsOrigin = "https://pbx.example.org",
            ) ?: fail("the brokered arm must resume")
        assertEquals("fixture-sip-credential", identity.sipPassword)
    }

    @Test
    fun `a session answer without the config facts is not a registerable identity`() {
        // /config.js unreachable = no sip domain = a REGISTER to
        // nowhere; fail the resume loudly (the pill reports it) instead
        // of the silent empty-credential REGISTER this drift fix ends.
        assertNull(
            SessionApi.parseSessionIdentity(
                fixture("session-identity-password"),
                config = null,
                wsOrigin = "https://pbx.example.org",
            ),
        )
    }

    @Test
    fun `a session answer without any credential arm is rejected`() {
        assertNull(
            SessionApi.parseSessionIdentity(
                """{"extension":"1001"}""",
                SessionApi.IslandConfig(sipDomain = "pbx.example.org", websocketPath = "/sip"),
                wsOrigin = "https://pbx.example.org",
            ),
        )
    }

    @Test
    fun `the websocket path follows the island config and http origins ws-ify`() {
        val identity =
            SessionApi.parseSessionIdentity(
                fixture("session-identity-password"),
                SessionApi.IslandConfig(sipDomain = "pbx.example.org", websocketPath = "/custom-ws"),
                wsOrigin = "http://pbx.lan:8080/",
            ) ?: fail("a custom path is still a registerable identity")
        assertEquals("ws://pbx.lan:8080/custom-ws", identity.websocketUrl)
        // An empty path in the config falls back to the island default.
        val defaulted =
            SessionApi.parseSessionIdentity(
                fixture("session-identity-password"),
                SessionApi.IslandConfig(sipDomain = "pbx.example.org", websocketPath = ""),
                wsOrigin = "https://pbx.example.org",
            ) ?: fail("an empty configured path falls back to /sip")
        assertEquals("wss://pbx.example.org/sip", defaulted.websocketUrl)
    }

    // -- the registration upsert ---------------------------------------

    @Test
    fun `registerPayload emits exactly the keys the server's request DTO declares`() {
        // org.json has no structural equals — pin the KEY SET (the
        // drift contract: extra or missing keys fail HERE) and every
        // value.
        val expected = JSONObject(fixture("push-device-register-request"))
        val actual =
            JSONObject(
                SessionApi.registerPayload(
                    deviceId = "fixture-device-0001",
                    transport = "ntfy",
                    token = "fixture-topic",
                ),
            )
        assertEquals(
            expected.sortedKeys(),
            actual.sortedKeys(),
            "the registration body's keys are the server contract — extra keys drift silently",
        )
        for (key in expected.sortedKeys()) assertEquals(expected.get(key), actual.get(key), "value drift on $key")
    }

    /** Android's org.json has no keySet(); sortedKeys() gives the
     *  order-stable key list the equality above needs. */
    private fun JSONObject.sortedKeys(): List<String> {
        val keys = mutableListOf<String>()
        keys().forEachRemaining { keys.add(it) }
        return keys.sorted()
    }

    private fun JSONObject.stringMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (key in keys()) map[key] = getString(key)
        return map
    }
}
