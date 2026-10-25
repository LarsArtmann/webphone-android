package software.larsartmann.webphone.shell

import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The navigation scope policy, pinned without a WebView: the session-
 * cookie-bearing shell renders ONLY the configured origin; everything
 * else becomes a system hand-off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavigationVerdictTest {

    private val origin = Uri.parse("https://pbx.artmann.tech")

    @Test
    fun `same-origin pages stay in the shell`() {
        assertEquals(
            NavigationVerdict.LoadInShell,
            navigationVerdict(Uri.parse("https://pbx.artmann.tech/messages"), origin),
        )
        // An explicit default port is the same authority.
        assertEquals(
            NavigationVerdict.LoadInShell,
            navigationVerdict(Uri.parse("https://pbx.artmann.tech:443/login"), origin),
        )
        // Hosts compare case-insensitively (DNS semantics).
        assertEquals(
            NavigationVerdict.LoadInShell,
            navigationVerdict(Uri.parse("https://PBX.ARTMANN.TECH/"), origin),
        )
    }

    @Test
    fun `other hosts hand off to the system`() {
        val verdict = navigationVerdict(Uri.parse("https://example.com/"), origin)
        val handOff = assertIs<NavigationVerdict.HandOff>(verdict)
        assertEquals(Intent.ACTION_VIEW, handOff.intent.action)
        assertEquals(Uri.parse("https://example.com/"), handOff.intent.data)
    }

    @Test
    fun `subdomains are foreign origins`() {
        assertIs<NavigationVerdict.HandOff>(
            navigationVerdict(Uri.parse("https://www.artmann.tech/"), origin),
        )
    }

    @Test
    fun `non-default ports distinguish origins`() {
        assertIs<NavigationVerdict.HandOff>(
            navigationVerdict(Uri.parse("https://pbx.artmann.tech:8443/"), origin),
        )
    }

    @Test
    fun `webview-internal schemes stay`() {
        assertEquals(NavigationVerdict.LoadInShell, navigationVerdict(Uri.parse("about:blank"), origin))
        assertEquals(NavigationVerdict.LoadInShell, navigationVerdict(Uri.parse("data:,"), origin))
        assertEquals(NavigationVerdict.LoadInShell, navigationVerdict(Uri.parse("blob:https://pbx.artmann.tech/uuid"), origin))
    }

    @Test
    fun `system schemes hand off`() {
        assertIs<NavigationVerdict.HandOff>(navigationVerdict(Uri.parse("tel:+1234"), origin))
        assertIs<NavigationVerdict.HandOff>(navigationVerdict(Uri.parse("mailto:someone@example.org"), origin))
        assertIs<NavigationVerdict.HandOff>(navigationVerdict(Uri.parse("geo:53,9"), origin))
    }

    @Test
    fun `null uri stays`() {
        assertEquals(NavigationVerdict.LoadInShell, navigationVerdict(null, origin))
    }
}
