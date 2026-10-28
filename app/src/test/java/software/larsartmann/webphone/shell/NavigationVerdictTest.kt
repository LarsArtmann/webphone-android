package software.larsartmann.webphone.shell

import android.content.Intent
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The navigation scope policy, pinned without a WebView: the session-
 * cookie-bearing shell renders ONLY the configured origin; everything
 * else becomes a system hand-off.
 */
@RunWith(RobolectricTestRunner::class)
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
        assertEquals(
            NavigationVerdict.LoadInShell,
            navigationVerdict(Uri.parse("blob:https://pbx.artmann.tech/uuid"), origin),
        )
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

    @Test
    fun `the auth detour authority navigates in-shell while live`() {
        val detour = Uri.parse("https://auth.artmann.tech")

        // Mid-detour, the IdP's own pages (its SPA links — consulted
        // navigations that would otherwise eject the OIDC login) load.
        assertEquals(
            NavigationVerdict.LoadInShell,
            navigationVerdict(Uri.parse("https://auth.artmann.tech/login/passkey"), origin, detour),
        )

        // Without a live detour the same URL stays foreign (the
        // default call sites keep the old behavior exactly).
        assertIs<NavigationVerdict.HandOff>(
            navigationVerdict(Uri.parse("https://auth.artmann.tech/login/passkey"), origin),
        )

        // Ports distinguish the detour authority like the origin's.
        assertIs<NavigationVerdict.HandOff>(
            navigationVerdict(Uri.parse("https://auth.artmann.tech:8443/"), origin, detour),
        )

        // Subdomains of the detour host stay foreign.
        assertIs<NavigationVerdict.HandOff>(
            navigationVerdict(Uri.parse("https://sso.auth.artmann.tech/"), origin, detour),
        )

        // A THIRD host is foreign even mid-detour.
        assertIs<NavigationVerdict.HandOff>(
            navigationVerdict(Uri.parse("https://evil.example/phish"), origin, detour),
        )

        // The origin itself is always in-shell, detour or not.
        assertEquals(
            NavigationVerdict.LoadInShell,
            navigationVerdict(Uri.parse("https://pbx.artmann.tech/messages"), origin, detour),
        )
    }
}
