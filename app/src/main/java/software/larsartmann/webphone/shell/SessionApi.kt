package software.larsartmann.webphone.shell

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R

/**
 * The origin's session seam, from the island's contract: the WebView
 * login mints the session cookie (the ONE login surface — the shell
 * never grows a second login form), then this client resumes the
 * session (GET /api/session carries the identity and the SIP
 * credential — exactly one arm, `sip_credential` brokered or
 * `password` legacy — no-store; the SIP connection FACTS live in
 * /config.js, the island's own config source) and registers the
 * device's push address (POST /api/push-devices, CSRF via the
 * /api/csrf dance — the same rotate-on-login contract the island's
 * authedFetch rides).
 */
object SessionApi {
    private const val TAG = "SessionApi"
    private val json = "application/json".toMediaType()

    /** ONE cookie store: the WebView's CookieManager. The island login
     *  mints there; OkHttp reads and writes the same jar so the
     *  session is genuinely shared (no second, drift-prone store that
     *  never sees the login cookie — the v0.1 gap, closed). */
    private val cookieJar =
        object : CookieJar {
            override fun saveFromResponse(
                url: HttpUrl,
                cookies: List<Cookie>,
            ) {
                val manager = CookieManager.getInstance()
                for (cookie in cookies) {
                    runCatching { manager.setCookie(url.toString(), cookie.toString()) }
                }
                runCatching { manager.flush() }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val raw =
                    runCatching { CookieManager.getInstance().getCookie(url.toString()) }
                        .getOrNull() ?: return emptyList()
                return raw.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
            }
        }

    private val client =
        OkHttpClient
            .Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .build()

    /** One owned line of the signed-in account (the sessionIdentity
     *  `lines[]` wire): the PBX extension plus the DID it presents. The
     *  label is the stack's one convention — DID first, the bare
     *  extension when the operator configured no DID. */
    data class SessionLine(
        val extension: String,
        val did: String,
    ) {
        fun label(): String = if (did.isEmpty()) extension else "$did · $extension"
    }

    data class SessionIdentity(
        val extension: String,
        val lines: List<SessionLine>,
        val sipDomain: String,
        val sipPassword: String,
        val websocketUrl: String,
    )

    /** The island's connection facts from /config.js (window.
     *  PBX_CONFIG): where the SIP domain and the websocket path live —
     *  the session answer deliberately carries neither. */
    data class IslandConfig(
        val sipDomain: String,
        val websocketPath: String,
    )

    /** The session probe's verdict: a logout is DEFINITIVE (the HTTP
     *  status named it), a network failure is not — the watcher must
     *  never tear the ring lane down because a tunnel flapped (the
     *  flap would have silenced the phone forever). */
    enum class SessionProbe { ALIVE, DEAD, UNREACHABLE }

    /** The wake-address registration's verdict, the server's own
     *  contract (webphone internal/server): 204 accepted; 404 the
     *  push seam is OFF server-side; 403 the session/CSRF gate
     *  rejected; 422 the address was invalid OR the account's device
     *  list is full (the server merges both); anything else is
     *  unexpected. A null code (and a failed CSRF fetch, which needs
     *  no session and can only fail on the link) is a TRANSPORT
     *  failure, not a refusal — the pill says the link is down, never
     *  that the server said no. */
    enum class PushRegisterVerdict { REGISTERED, SEAM_OFF, INVALID, AUTH, NETWORK, OTHER }

    /** Classify one register answer. Pure (the classifier the unit
     *  test pins); [registerPushDevice] supplies the code. */
    internal fun registerVerdictFromCode(code: Int?): PushRegisterVerdict =
        when {
            code == null -> PushRegisterVerdict.NETWORK
            code == 204 -> PushRegisterVerdict.REGISTERED
            code == 404 -> PushRegisterVerdict.SEAM_OFF
            code == 403 -> PushRegisterVerdict.AUTH
            code == 422 -> PushRegisterVerdict.INVALID
            else -> PushRegisterVerdict.OTHER
        }

    /** The line-switch answer's verdict (the server owns the statuses:
     *  200 re-mints the session onto the target line and answers the
     *  FULL session shape, 403 the login owns no mapped lines or the
     *  asked extension is not one of them, 404 the identity layer is
     *  off — an extension-only login cannot switch). A null code is a
     *  transport failure, never a refusal. */
    enum class SwitchVerdict { SWITCHED, NOT_MAPPED, SEAM_OFF, NETWORK, OTHER }

    /** Classify one switch answer. Pure (the classifier the unit test
     *  pins); [switchLine] supplies the code. */
    internal fun switchVerdictFromCode(code: Int?): SwitchVerdict =
        when {
            code == null -> SwitchVerdict.NETWORK
            code in 200..299 -> SwitchVerdict.SWITCHED
            code == 403 -> SwitchVerdict.NOT_MAPPED
            code == 404 -> SwitchVerdict.SEAM_OFF
            else -> SwitchVerdict.OTHER
        }

    /** The lines the Settings picker may offer: empty unless the
     *  account owns MORE than one — a single-line deployment renders
     *  byte-identically to a build without the picker (the handover
     *  pack's hide-when-single contract). Pure (pinned). */
    internal fun pickerLines(identity: SessionIdentity): List<SessionLine> =
        identity.lines.takeIf { it.size > 1 } ?: emptyList()

    /** The picker's toast per switch verdict. Pure (pinned) — the
     *  honest class reaches the user verbatim instead of one Boolean. */
    internal fun switchMessage(verdict: SwitchVerdict): Int =
        when (verdict) {
            SwitchVerdict.SWITCHED -> R.string.settings_line_switched
            SwitchVerdict.NOT_MAPPED -> R.string.settings_line_not_mapped
            SwitchVerdict.SEAM_OFF -> R.string.settings_line_seam_off
            SwitchVerdict.NETWORK -> R.string.settings_line_network
            SwitchVerdict.OTHER -> R.string.settings_line_failed
        }

    /** Classify one /api/session answer. Pure (the classifier the
     *  unit test pins); [probeSession] supplies the code. A null code
     *  is a transport failure (IOException) — never a logout. */
    internal fun probeFromCode(code: Int?): SessionProbe =
        when {
            code == null -> SessionProbe.UNREACHABLE
            code in 200..299 -> SessionProbe.ALIVE
            code == 401 || code == 403 -> SessionProbe.DEAD
            else -> SessionProbe.UNREACHABLE
        }

    /** Probe the session cookie's life for the CallLayer watcher:
     *  ALIVE keeps the lane, DEAD tears it down (the island logged
     *  out / the server expired the cookie), UNREACHABLE keeps it and
     *  waits for the next tick. */
    fun probeSession(): SessionProbe {
        val request = authedBuilder("/api/session").get().build()
        val code: Int? =
            runCatching {
                client.newCall(request).execute().use { response -> response.code }
            }.getOrNull()
        if (code == null || code !in 200..299) {
            Log.i(TAG, "GET /api/session answered $code")
        }
        return probeFromCode(code)
    }

    /** Resume the session cookie into SIP credentials (the island's
     *  boot resume path). 401 = not signed in (the WebView must show
     *  the login first). */
    fun resumeSession(): SessionIdentity? {
        val body = call("GET", "/api/session", null) ?: return null
        return parseSessionIdentity(body, fetchIslandConfig(), App.origin)
    }

    /** Decode the sessionIdentity wire shape (the server owns the
     *  field names — pinned by the vendored fixtures in the JVM suite):
     *  `extension` + exactly ONE credential arm, `sip_credential`
     *  (brokered) or `password` (legacy). The SIP connection facts are
     *  NOT in this answer — sipDomain and websocketPath come from
     *  /config.js ([IslandConfig]), wsOrigin is this install's stack
     *  origin. An answer without a credential, without the config
     *  facts, or without an extension is NOT a registerable identity:
     *  null, loudly — never the empty-credential REGISTER that
     *  silently never rang. */
    internal fun parseSessionIdentity(
        body: String,
        config: IslandConfig?,
        wsOrigin: String,
    ): SessionIdentity? =
        runCatching {
            val root = JSONObject(body)
            val extension = root.optString("extension")
            SessionIdentity(
                extension = extension,
                lines = parseLines(root, extension),
                sipDomain = config?.sipDomain.orEmpty(),
                sipPassword = root.optString("sip_credential").ifEmpty { root.optString("password") },
                websocketUrl =
                    wsOrigin.replaceFirst("http", "ws").trimEnd('/') +
                        (config?.websocketPath?.takeIf { it.isNotEmpty() } ?: "/sip"),
            )
        }.getOrNull()?.takeIf {
            it.extension.isNotEmpty() && it.sipDomain.isNotEmpty() && it.sipPassword.isNotEmpty()
        }

    /** Decode the owned-lines list (the active line is `lines[0]` by
     *  the server's contract). An answer predating the typed wire (no
     *  `lines` key) degrades to the single line the top-level facts
     *  describe — the picker stays honest against an older server
     *  instead of rendering an empty account. Pure (pinned with the
     *  vendored fixtures). */
    internal fun parseLines(
        root: JSONObject,
        activeExtension: String,
    ): List<SessionLine> {
        val array = root.optJSONArray("lines")
        val parsed =
            (0 until (array?.length() ?: 0)).mapNotNull { i ->
                val line = array?.optJSONObject(i) ?: return@mapNotNull null
                val ext = line.optString("extension")
                if (ext.isEmpty()) null else SessionLine(extension = ext, did = line.optString("did"))
            }
        return parsed.ifEmpty { listOf(SessionLine(activeExtension, root.optString("did"))) }
    }

    /** Fetch the island's own config source (window.PBX_CONFIG) and
     *  read the SIP connection facts by key — the same keys the island
     *  boot reads. A regex over the JS literal (a full JS parse for two
     *  string keys is ceremony); an absent path falls back to the
     *  island's default. Unreachable or unparsable answers null (the
     *  caller fails the resume honestly instead of registering
     *  nowhere). */
    private fun fetchIslandConfig(): IslandConfig? = call("GET", "/config.js", null)?.let { decodeIslandConfig(it) }

    /** Decode the served config.js body (pure — the loopback live-check
     * drives it against the LIVE bytes): the two regex reads the
     * island's own boot performs. Absent path falls back to the
     * island default; absent domain is null (the caller fails the
     * resume honestly instead of registering nowhere). */
    internal fun decodeIslandConfig(body: String): IslandConfig? {
        val domain = Regex("\"sipDomain\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: return null
        val path = Regex("\"websocketPath\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "/sip"
        return IslandConfig(sipDomain = domain, websocketPath = path)
    }

    /** The registration upsert body (pure — the JVM suite pins its
     *  keys against the vendored server fixture). */
    internal fun registerPayload(
        deviceId: String,
        transport: String,
        token: String,
    ): String =
        JSONObject()
            .put("device_id", deviceId)
            .put("transport", transport)
            .put("token", token)
            .toString()

    /** Register this install's push wake address (upsert — token
     *  rotation replaces the row server-side). Returns the server's
     *  refusal class — a Boolean collapsed three distinct failures
     *  (seam off, rejected, link down) into one indistinguishable
     *  `false`, which is exactly the silence this verdict exists to
     *  end. */
    fun registerPushDevice(
        context: Context,
        transport: String,
        token: String,
    ): PushRegisterVerdict {
        val payload = registerPayload(DeviceIdentity.deviceId(context), transport, token)
        val csrf = fetchCsrf() ?: return PushRegisterVerdict.NETWORK
        val request =
            authedBuilder("/api/push-devices")
                .header("X-CSRF-Token", csrf)
                .post(payload.toRequestBody(json))
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val verdict = registerVerdictFromCode(response.code)
                if (verdict == PushRegisterVerdict.REGISTERED) {
                    Log.i(TAG, "push device registered ($transport)")
                } else {
                    Log.w(TAG, "push device register answered ${response.code} -> $verdict")
                }
                verdict
            }
        }.getOrDefault(PushRegisterVerdict.NETWORK)
    }

    /** Deregister on logout (the registry's device row goes away). */
    fun deregisterPushDevice(context: Context): Boolean {
        val csrf = fetchCsrf() ?: return false
        val request =
            authedBuilder("/api/push-devices?id=" + DeviceIdentity.deviceId(context))
                .header("X-CSRF-Token", csrf)
                .delete()
                .build()
        return runCatching {
            client.newCall(request).execute().use { it.code == 204 }
        }.getOrDefault(false)
    }

    /** Switch the session onto another line the SAME login owns (the
     *  server re-mints the session, rotates the CSRF token and deletes
     *  the old row — the cookie jar keeps working because the cookie
     *  NAME survives the re-mint). The caller owns the re-wire: the
     *  old line's registration and push subscription are void the
     *  moment the switch answers 2xx. */
    fun switchLine(extension: String): SwitchVerdict {
        val payload = JSONObject().put("extension", extension).toString()
        val csrf = fetchCsrf() ?: return SwitchVerdict.NETWORK
        val request =
            authedBuilder("/api/session/switch")
                .header("X-CSRF-Token", csrf)
                .post(payload.toRequestBody(json))
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val verdict = switchVerdictFromCode(response.code)
                if (verdict != SwitchVerdict.SWITCHED) {
                    Log.w(TAG, "line switch to $extension answered ${response.code} -> $verdict")
                }
                verdict
            }
        }.getOrDefault(SwitchVerdict.NETWORK)
    }

    private fun fetchCsrf(): String? {
        val body = call("GET", "/api/csrf", null) ?: return null
        return runCatching { JSONObject(body).optString("token") }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** The one origin-aimed, cookie-carrying request head — the
     *  session probe, the register/deregister calls and the generic
     *  `call` all share it (the request-building duplication was
     *  three copies of the same two lines drifting apart). */
    private fun authedBuilder(path: String): Request.Builder =
        Request
            .Builder()
            .url(App.origin.trimEnd('/') + path)
            .header("Cookie", sessionCookieHeader())

    private fun call(
        method: String,
        path: String,
        body: String?,
    ): String? {
        val request = authedBuilder(path).method(method, body?.toRequestBody(json)).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.i(TAG, "$method $path answered ${response.code}")
                    null
                } else {
                    response.body.string()
                }
            }
        }.getOrNull()
    }

    private fun sessionCookieHeader(): String =
        runCatching { CookieManager.getInstance().getCookie(App.origin) }.getOrNull().orEmpty()
}
