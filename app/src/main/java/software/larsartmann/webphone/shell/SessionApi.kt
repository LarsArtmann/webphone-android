package software.larsartmann.webphone.shell

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import software.larsartmann.webphone.App
import java.util.Collections

/**
 * The origin's session seam, from the island's contract: the WebView
 * login mints the session cookie (the ONE login surface — the shell
 * never grows a second login form), then this client resumes the
 * session (GET /api/session carries the SIP credentials back,
 * no-store) and registers the device's push address
 * (POST /api/push-devices, CSRF via the /api/csrf dance — the same
 * rotate-on-login contract the island's authedFetch rides).
 */
object SessionApi {

    private const val TAG = "SessionApi"
    private val json = "application/json".toMediaType()

    /** The session cookie the WebView holds, shared with OkHttp. */
    val cookieJar = object : CookieJar {
        private val store = Collections.synchronizedMap(mutableMapOf<String, List<Cookie>>())

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .build()

    data class SessionIdentity(
        val extension: String,
        val sipDomain: String,
        val sipPassword: String,
        val websocketUrl: String,
    )

    /** Resume the session cookie into SIP credentials (the island's
     *  boot resume path). 401 = not signed in (the WebView must show
     *  the login first). */
    fun resumeSession(): SessionIdentity? {
        val body = call("GET", "/api/session", null) ?: return null
        return runCatching {
            val root = JSONObject(body)
            // The typed sessionIdentity shape: extension + SIP
            // connection facts (the server owns the field names).
            SessionIdentity(
                extension = root.optString("extension"),
                sipDomain = root.optString("sip_domain").ifEmpty { root.optString("sipDomain") },
                sipPassword = root.optString("sip_password").ifEmpty { root.optString("sipPassword") },
                websocketUrl = root.optString("websocket_url").ifEmpty {
                    App.origin.replaceFirst("http", "ws").trimEnd('/') + "/sip"
                },
            )
        }.getOrNull()?.takeIf { it.extension.isNotEmpty() }
    }

    /** Register this install's push wake address (upsert — token
     *  rotation replaces the row server-side). */
    fun registerPushDevice(context: Context, transport: String, token: String): Boolean {
        val payload = JSONObject()
            .put("device_id", DeviceIdentity.deviceId(context))
            .put("transport", transport)
            .put("token", token)
            .toString()
        val csrf = fetchCsrf() ?: return false
        val request = Request.Builder()
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
        val request = Request.Builder()
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

    private fun call(method: String, path: String, body: String?): String? {
        val request = Request.Builder()
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
                    response.body?.string()
                }
            }
        }.getOrNull()
    }

    private fun sessionCookieHeader(): String {
        val url = runCatching { HttpUrl.parse(App.origin) }.getOrNull() ?: return ""
        return cookieJar.loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
    }
}
