package software.larsartmann.webphone.shell

import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import software.larsartmann.webphone.shell.SessionApi.HandoffSupport
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The sign-in lane classifier, pinned PURE ([signInLane]): which lane
 * takes a navigation to the stack's OIDC begin hop. The distinction
 * that matters for a passkey-only IdP is the middle one — NO verdict yet
 * HOLDS the tap instead of guessing the WebView lane, because the
 * detour is the one lane Pocket ID can never complete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34])
class SignInLaneTest {
    private val origin = "https://pbx.artmann.tech"

    @Test
    fun `the stack's verdict names the lane for the exact begin hop`() {
        val entry = signInEntryUri(origin)
        assertEquals(SignInLane.BROWSER, signInLane(entry, origin, HandoffSupport.SUPPORTED))
        assertEquals(
            SignInLane.AWAIT_SUPPORT,
            signInLane(entry, origin, null),
            "no answer yet holds the tap — it never guesses the WebView lane",
        )
        assertEquals(SignInLane.DETOUR, signInLane(entry, origin, HandoffSupport.UNSUPPORTED))
        assertEquals(
            SignInLane.DETOUR,
            signInLane(entry, origin, HandoffSupport.UNREACHABLE),
            "a stack that could not be asked is no reason to open a browser",
        )
    }

    @Test
    fun `the begin hop keeps a query and still is the entry`() {
        val withQuery = Uri.parse("$origin${SessionApi.SIGN_IN_ENTRY_PATH}?next=%2Fmessages")
        assertEquals(SignInLane.BROWSER, signInLane(withQuery, origin, HandoffSupport.SUPPORTED))
        assertEquals(SignInLane.AWAIT_SUPPORT, signInLane(withQuery, origin, null))
    }

    @Test
    fun `nothing but the begin hop is an entry`() {
        val verdicts = listOf(null, HandoffSupport.SUPPORTED, HandoffSupport.UNSUPPORTED)
        val lookalikes =
            listOf(
                null,
                "$origin/messages",
                "$origin/api/auth/oauth2/begin/extra",
                "$origin/api/auth/oauth2/beginx",
                "$origin/api/auth/oauth2/callback",
                "$origin/login",
                "https://evil.example${SessionApi.SIGN_IN_ENTRY_PATH}",
                "http://pbx.artmann.tech${SessionApi.SIGN_IN_ENTRY_PATH}",
                "https://pbx.artmann.tech:8443${SessionApi.SIGN_IN_ENTRY_PATH}",
                "cloud.larsartmann.webphone://auth/finish?ticket=t&app_state=a",
            )
        for (link in lookalikes) {
            val uri = link?.let(Uri::parse)
            for (verdict in verdicts) {
                assertEquals(
                    SignInLane.NOT_ENTRY,
                    signInLane(uri, origin, verdict),
                    "$link with verdict $verdict is not a sign-in entry",
                )
            }
        }
    }

    @Test
    fun `the entry follows the configured origin`() {
        val prefixed = "https://pbx.example.org/base/"
        assertEquals(
            "https://pbx.example.org/base${SessionApi.SIGN_IN_ENTRY_PATH}",
            signInEntryUri(prefixed).toString(),
            "an origin with a path prefix keeps it",
        )
        assertEquals(SignInLane.BROWSER, signInLane(signInEntryUri(prefixed), prefixed, HandoffSupport.SUPPORTED))
        assertEquals(
            SignInLane.NOT_ENTRY,
            signInLane(signInEntryUri(prefixed), origin, HandoffSupport.SUPPORTED),
            "the same link is not an entry on another origin",
        )
        assertEquals(
            signInEntryUri(origin).toString(),
            signInEntryUri("$origin/").toString(),
            "a trailing slash on the origin changes nothing",
        )
    }
}
