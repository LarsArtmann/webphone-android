package software.larsartmann.webphone.shell

import android.app.Application
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The deep-link intake router, pinned PURE: tel:/sip: become dialer
 * pre-fills, same-origin https becomes a shell path, everything else
 * (foreign hosts, odd schemes, blank numbers) is deliberately nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepLinkTargetTest {

    private val origin = Uri.parse("https://pbx.artmann.tech")

    @Test
    fun `tel links carry the number verbatim`() {
        assertEquals(
            DeepLinkTarget.Dial("+491701234567"),
            deepLinkTarget(Uri.parse("tel:+491701234567"), origin),
        )
        // Visual separators survive: the user sees what the link
        // carried and edits freely in the dialer.
        assertEquals(
            DeepLinkTarget.Dial("+49-170-1234567"),
            deepLinkTarget(Uri.parse("tel:+49-170-1234567"), origin),
        )
        assertEquals(DeepLinkTarget.Dial("100"), deepLinkTarget(Uri.parse("tel:100"), origin))
    }

    @Test
    fun `blank tel links are nothing`() {
        assertEquals(DeepLinkTarget.None, deepLinkTarget(Uri.parse("tel:"), origin))
        assertEquals(DeepLinkTarget.None, deepLinkTarget(Uri.parse("tel:%20"), origin))
    }

    @Test
    fun `sip links dial the user part`() {
        assertEquals(
            DeepLinkTarget.Dial("100"),
            deepLinkTarget(Uri.parse("sip:100@pbx.artmann.tech"), origin),
        )
        // No host: the whole part is the dialable.
        assertEquals(DeepLinkTarget.Dial("100"), deepLinkTarget(Uri.parse("sip:100"), origin))
        // A bare host carries no dialable.
        assertEquals(DeepLinkTarget.None, deepLinkTarget(Uri.parse("sip:@pbx.artmann.tech"), origin))
    }

    @Test
    fun `stack paths become shell paths`() {
        assertEquals(
            DeepLinkTarget.ShellPath(Uri.parse("https://pbx.artmann.tech/messages/42")),
            deepLinkTarget(Uri.parse("https://pbx.artmann.tech/messages/42"), origin),
        )
        // An explicit default port is the same authority.
        assertEquals(
            DeepLinkTarget.ShellPath(Uri.parse("https://pbx.artmann.tech:443/fax/1")),
            deepLinkTarget(Uri.parse("https://pbx.artmann.tech:443/fax/1"), origin),
        )
    }

    @Test
    fun `foreign https links are nothing`() {
        assertEquals(
            DeepLinkTarget.None,
            deepLinkTarget(Uri.parse("https://example.com/messages"), origin),
        )
        assertEquals(
            DeepLinkTarget.None,
            deepLinkTarget(Uri.parse("https://pbx.artmann.tech:8443/"), origin),
        )
        assertEquals(
            DeepLinkTarget.None,
            deepLinkTarget(Uri.parse("http://pbx.artmann.tech/"), origin),
        )
    }

    @Test
    fun `other schemes and null are nothing`() {
        assertEquals(DeepLinkTarget.None, deepLinkTarget(Uri.parse("mailto:x@example.org"), origin))
        assertEquals(DeepLinkTarget.None, deepLinkTarget(Uri.parse("geo:53,9"), origin))
        assertEquals(DeepLinkTarget.None, deepLinkTarget(null, origin))
    }
}
