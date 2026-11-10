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

    data class SessionIdentity(
        val extension: String,
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
        val request =
            Request
                .Builder()
                .url(App.origin.trimEnd('/') + "/api/session")
                .header("Cookie", sessionCookieHeader())
                .get()
                .build()
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
            SessionIdentity(
                extension = root.optString("extension"),
                sipDomain = config?.sipDomain.orEmpty(),
                sipPassword = root.optString("sip_credential").ifEmpty { root.optString("password") },
                websocketUrl =
                    wsOrigin.replaceFirst("http", "ws").trimEnd('/') +
                        (config?.websocketPath?.takeIf { it.isNotEmpty() } ?: "/sip"),
            )
        }.getOrNull()?.takeIf {
            it.extension.isNotEmpty() && it.sipDomain.isNotEmpty() && it.sipPassword.isNotEmpty()
        }

    /** Fetch the island's own config source (window.PBX_CONFIG) and
     *  read the SIP connection facts by key — the same keys the island
     *  boot reads. A regex over the JS literal (a full JS parse for two
     *  string keys is ceremony); an absent path falls back to the
     *  island's default. Unreachable or unparsable answers null (the
     *  caller fails the resume honestly instead of registering
     *  nowhere). */
    private fun fetchIslandConfig(): IslandConfig? {
        val body = call("GET", "/config.js", null) ?: return null
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
     *  rotation replaces the row server-side). */
    fun registerPushDevice(
        context: Context,
        transport: String,
        token: String,
    ): Boolean {
        val payload = registerPayload(DeviceIdentity.deviceId(context), transport, token)
        val csrf = fetchCsrf() ?: return false
        val request =
            Request
                .Builder()
                .url(App.origin.trimEnd('/') + "/api/push-devices")
                .header("Cookie", sessionCookieHeader())
                .header("X-CSRF-Token", csrf)
                .post(payload.toRequestBody(json))
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 204) {
                    Log.i(TAG, "push device registered ($transport)")
                    true
                } else {
                    Log.w(TAG, "push device register answered ${response.code}")
                    false
                }
            }
        }.getOrDefault(false)
    }

    /** Deregister on logout (the registry's device row goes away). */
    fun deregisterPushDevice(context: Context): Boolean {
        val csrf = fetchCsrf() ?: return false
        val request =
            Request
                .Builder()
                .url(App.origin.trimEnd('/') + "/api/push-devices?id=" + DeviceIdentity.deviceId(context))
                .header("Cookie", sessionCookieHeader())
                .header("X-CSRF-Token", csrf)
                .delete()
                .build()
        return runCatching {
            client.newCall(request).execute().use { it.code == 204 }
        }.getOrDefault(false)
    }

    private fun fetchCsrf(): String? {
        val body = call("GET", "/api/csrf", null) ?: return null
        return runCatching { JSONObject(body).optString("token") }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun call(
        method: String,
        path: String,
        body: String?,
    ): String? {
        val request =
            Request
                .Builder()
                .url(App.origin.trimEnd('/') + path)
                .header("Cookie", sessionCookieHeader())
                .method(method, body?.toRequestBody(json))
                .build()
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
