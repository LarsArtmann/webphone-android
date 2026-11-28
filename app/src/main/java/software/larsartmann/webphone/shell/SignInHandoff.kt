package software.larsartmann.webphone.shell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import software.larsartmann.webphone.App
import java.security.SecureRandom
import java.util.Base64

/**
 * The sign-in handoff's RETURN address: the private-use URI the stack's
 * OIDC callback bounces its one-time ticket off, and Android launches
 * the shell with. PAIRED with the manifest's VIEW filter and with
 * webphone's `AndroidShellFinishURL` — change all or none.
 *
 * The scheme is a domain the app's author controls, reversed (RFC 8252
 * §7.1 requires exactly that): `larsartmann.cloud`, NOT the application
 * id's `larsartmann.software` (which resolves nowhere). Host and path
 * are pinned so nothing but the handoff can enter through it.
 */
internal const val HANDOFF_RETURN_SCHEME = "cloud.larsartmann.webphone"
internal const val HANDOFF_RETURN_HOST = "auth"
internal const val HANDOFF_RETURN_PATH = "/finish"
internal const val HANDOFF_PARAM_TICKET = "ticket"
internal const val HANDOFF_PARAM_APP_STATE = "app_state"

/** The value naming this shell on the wire: the `shell` query parameter
 *  of the OIDC begin hop and the `X-WebPhone-Shell` header of the
 *  ticket redemption. */
internal const val HANDOFF_SHELL = "android"

/** The nonce alphabet the stack accepts for `app_state` — the shell only
 *  ever sends values of this shape, and only ever accepts a ticket of it
 *  (a ticket is minted by the stack from the same alphabet). */
internal val HANDOFF_TOKEN_SHAPE = Regex("[A-Za-z0-9_-]{16,128}")

/**
 * The sign-in handoff: the shell hands the stack's OIDC dance to the
 * SYSTEM browser, and the stack hands the fresh session back through a
 * one-time ticket.
 *
 * WHY a browser at all — the in-shell "auth detour" ([navigationVerdict])
 * stays as the FALLBACK lane, but it is not the Pocket ID path: RFC 8252
 * §8.12 forbids embedded user-agents for authorization requests, and the
 * stack's IdP makes the rule bite. Pocket ID is passkey-ONLY, and in a
 * WebView's FOR_APP WebAuthn mode Android signs the ceremony with the
 * APP's origin (`android:apk-key-hash:…`), not the web origin. Pocket ID
 * accepts exactly its own App URL as an origin (`RPOrigins:
 * []string{deps.AppURL}`, backend/internal/webauthn/service.go) and
 * declined to change that (pocket-id/pocket-id#1845, closed NOT_PLANNED
 * 2026-10-15: "open the login page in an external browser"). No
 * assetlinks file bridges that gap. The system browser can, because a
 * browser's WebAuthn origin IS the web origin — so the dance runs where
 * the user's passkeys already live: Chrome Custom Tabs (the user's
 * default browser, inside the shell's task, so Back returns to the app).
 *
 * THE CONTRACT (webphone `internal/server/auth/sso_handoff.go`; the
 * cross-repo note under docs/drafts/):
 *
 * 1. The shell asks the stack whether it speaks the handoff at all
 *    (`GET /api/auth/oauth2/shell` → 204). A stack that predates the
 *    contract keeps the WebView lane — never a split-brain in which the
 *    browser signs in and the shell does not.
 * 2. Tapping the island's Pocket ID door launches
 *    `/api/auth/oauth2/begin?shell=android&app_state=<nonce>` in the
 *    browser.
 * 3. The stack's callback mints the session but delivers NO cookie to
 *    the browser; it bounces a one-time ticket off the return address
 *    (`cloud.larsartmann.webphone://auth/finish?ticket=…&app_state=…`),
 *    and Android launches the shell with it.
 * 4. The shell redeems the ticket out-of-band
 *    (`GET /api/auth/oauth2/finish?ticket=…` with the `X-WebPhone-Shell`
 *    header) through the ONE cookie store, then re-wires — the session
 *    never touches the browser's jar.
 *
 * THE NONCE (`app_state`) is the shell's login-CSRF fence. A ticket is a
 * bearer credential for the session it was minted for, and anyone who
 * finishes a handoff dance can obtain one — for their OWN account. A
 * forged return link carrying such a ticket would otherwise sign the
 * shell into the attacker's account. So the shell opens every attempt
 * with a random nonce, requires the return link to echo it, and keeps
 * the pending attempt on disk: a process kill during the dance (the
 * shell sits behind the browser) must not void a sign-in the user has
 * already completed. An attempt dies with its stack (never redeem on an
 * origin the user switched away from mid-dance) and with its TTL (the
 * stack's own attempt cookies live ten minutes).
 *
 * An object like the other engines and seams: the attempt and the
 * learned support are per-process facts about ONE stack origin.
 */
internal object SignInHandoff {
    private const val TAG = "SignInHandoff"
    private const val PREFS = "webphone-signin-handoff"
    private const val KEY_NONCE = "nonce"
    private const val KEY_ORIGIN = "origin"
    private const val KEY_ISSUED_AT_MS = "issued_at_ms"

    /** 32 random bytes → 43 base64url characters: unguessable, and
     *  inside the stack's accepted `app_state` shape. */
    private const val NONCE_BYTES = 32

    /** The stack's own OIDC attempt cookies expire after ten minutes, so
     *  a return link older than that could not be redeemed anyway. */
    private val ATTEMPT_TTL_MS = 10 * 60 * 1000L

    /** One open sign-in attempt: the nonce the return link must echo, the
     *  stack it was opened on, and when. */
    internal data class Attempt(
        val nonce: String,
        val origin: String,
        val issuedAtMs: Long,
    )

    /** What the shell learned about ONE stack's handoff support. Keyed by
     *  origin: a verdict learned on the old stack must not follow the
     *  user onto a stack they just switched to. */
    internal data class Learned(
        val origin: String,
        val support: SessionApi.HandoffSupport,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _learned = MutableStateFlow<Learned?>(null)
    private var learningOrigin: String? = null
    private var learning: Job? = null

    /** Test seams (production defaults are the real wire and the real
     *  browser): the capability probe and the browser launch, plus the
     *  wall clock the attempt TTL reads. */
    internal var probe: () -> SessionApi.HandoffSupport = { SessionApi.probeSignInHandoff() }
    internal var openBrowser: (Context, Uri) -> Boolean = ::openInBrowser
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    /** The CURRENT origin's verdict, or null while it is unknown (never
     *  asked, still in flight, or asked of a different stack). */
    fun support(): SessionApi.HandoffSupport? = supportOf(_learned.value, App.origin)

    /** Suspend until the current origin has a verdict (the caller bounds
     *  the wait — an unreachable stack answers UNREACHABLE, a dead one
     *  never answers at all). */
    suspend fun awaitSupport(): SessionApi.HandoffSupport =
        _learned
            .map { supportOf(it, App.origin) }
            .filterNotNull()
            .first()

    /** Ask the current stack whether it speaks the handoff. Single-flight
     *  per origin, safe to call from every resume: the wire path calls it
     *  whenever the session resume comes back signed-out, so the verdict
     *  is normally in hand long before the user reaches the login card. */
    fun learnSupport() {
        val origin = App.origin
        if (learning?.isActive == true && learningOrigin == origin) return
        learningOrigin = origin
        learning =
            scope.launch {
                val support =
                    runCatching { probe() }
                        .getOrDefault(SessionApi.HandoffSupport.UNREACHABLE)
                _learned.value = Learned(origin, support)
                Log.i(TAG, "sign-in handoff: $origin answered $support")
            }
    }

    /** The writer's single seam (the learning path and the pins that
     *  drive it without a wire). */
    internal fun teach(
        origin: String,
        support: SessionApi.HandoffSupport,
    ) {
        _learned.value = Learned(origin, support)
    }

    /** Hand one sign-in entry to the browser and open an attempt for it.
     *  False means no browser took the entry — the caller then keeps the
     *  navigation in the shell (the WebView lane). */
    fun begin(
        context: Context,
        entry: Uri,
    ): Boolean {
        val attempt = Attempt.issue()
        val launch =
            entry
                .buildUpon()
                .appendQueryParameter(HANDOFF_PARAM_SHELL, HANDOFF_SHELL)
                .appendQueryParameter(HANDOFF_PARAM_APP_STATE, attempt.nonce)
                .build()
        if (!openBrowser(context, launch)) {
            Log.i(TAG, "sign-in handoff: no browser took the entry; the shell keeps it")
            return false
        }
        attempt.persist(context)
        Log.i(TAG, "sign-in handoff: entry handed to the browser (${entry.host})")
        return true
    }

    /** Claim the ticket of the attempt THIS shell opened. Null — and no
     *  network — for every return link that is not the answer to a live
     *  attempt of the current stack. A link that echoes the wrong nonce
     *  leaves the open attempt alone: a forged or replayed link must not
     *  void a dance the user is still in the middle of. */
    fun takeTicket(
        context: Context,
        ticket: String?,
        appState: String?,
    ): String? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val attempt = Attempt.of(preferences)
        if (attempt == null) {
            Log.i(TAG, "sign-in handoff: return link without an open attempt — ignored")
            return null
        }
        if (attempt.origin != App.origin) {
            attempt.drop(preferences)
            Log.i(TAG, "sign-in handoff: return link for another stack — ignored")
            return null
        }
        if (nowMs() - attempt.issuedAtMs > ATTEMPT_TTL_MS) {
            attempt.drop(preferences)
            Log.i(TAG, "sign-in handoff: return link after the attempt expired — ignored")
            return null
        }
        if (appState != attempt.nonce) {
            Log.w(TAG, "sign-in handoff: return link does not echo this shell's attempt — ignored")
            return null
        }
        // Single use: the ticket dies with its attempt, redeemed or not.
        attempt.drop(preferences)
        return ticket
    }

    private fun supportOf(
        learned: Learned?,
        origin: String,
    ): SessionApi.HandoffSupport? = learned?.takeIf { it.origin == origin }?.support

    private fun Attempt.persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_NONCE, nonce)
            putString(KEY_ORIGIN, origin)
            putLong(KEY_ISSUED_AT_MS, issuedAtMs)
        }
    }

    private fun Attempt.drop(preferences: android.content.SharedPreferences) {
        preferences.edit { clear() }
    }

    private fun Attempt.Companion.of(preferences: android.content.SharedPreferences): Attempt? =
        Attempt(
            nonce = preferences.getString(KEY_NONCE, "").orEmpty(),
            origin = preferences.getString(KEY_ORIGIN, "").orEmpty(),
            issuedAtMs = preferences.getLong(KEY_ISSUED_AT_MS, 0L),
        ).takeIf { it.nonce.isNotEmpty() }

    /** Chrome Custom Tabs first (the user's default browser, in the
     *  shell's task); a plain browser tab when the Custom Tab intent has
     *  no taker; false when the device has no browser at all. Browsers
     *  that do not implement Custom Tabs simply open the view intent as
     *  an ordinary tab, so the default browser is always the one that
     *  answers — the one holding the user's Pocket ID session. */
    private fun openInBrowser(
        context: Context,
        url: Uri,
    ): Boolean {
        val customTab =
            CustomTabsIntent.Builder().build().intent.apply {
                data = url
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        if (runCatching { context.startActivity(customTab) }.isSuccess) return true
        val browser =
            Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE)
        if (context.packageManager.resolveActivity(browser, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return false
        }
        return runCatching { context.startActivity(browser) }.isSuccess
    }

    private fun Attempt.Companion.issue(): Attempt {
        val bytes = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Attempt(
            nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            origin = App.origin,
            issuedAtMs = nowMs(),
        )
    }
}
