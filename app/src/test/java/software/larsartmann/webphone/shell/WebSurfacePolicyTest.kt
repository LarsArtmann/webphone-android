package software.larsartmann.webphone.shell

import android.app.Application
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import software.larsartmann.webphone.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The WebView clients' delegation seams: the pure [navigationVerdict]
 * policy wired through `shouldOverrideUrlLoading` (scope guard live,
 * not just pure), the transport-vs-HTTP error split (overlay on
 * main-frame TRANSPORT failure only — HTTP error pages RENDER), and
 * the download policy (same-origin → DownloadManager with the session
 * cookie; foreign → system hand-off, never a cookie leak).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebSurfacePolicyTest {

    private val origin = "https://pbx.artmann.tech"

    private class FakeRequest(
        private val uri: Uri,
        private val mainFrame: Boolean = true,
    ) : WebResourceRequest {
        override fun getUrl(): Uri = uri
        override fun isForMainFrame(): Boolean = mainFrame
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = true
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        override fun getMethod(): String = "GET"
    }

    @Test
    fun `scope guard is wired through shouldOverrideUrlLoading`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val client = surface.currentWebView().webViewClient

                // Same-origin navigation: the WebView loads it itself.
                assertFalse(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("$origin/messages")),
                    ),
                    "same-origin navigations load in the shell",
                )
                assertNull(shadowOf(activity).nextStartedActivity, "no hand-off fired")

                // Foreign host: returns true AND hands off to the system.
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("https://evil.example/phish")),
                    ),
                    "foreign hosts never render in the shell",
                )
                val handOff = shadowOf(activity).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, handOff?.action)
                assertEquals(Uri.parse("https://evil.example/phish"), handOff?.data)

                // Non-web scheme: system handler, never the WebView.
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("tel:+491701234567")),
                    ),
                    "tel: hands off to the dialer",
                )
                assertEquals(
                    Uri.parse("tel:+491701234567"),
                    shadowOf(activity).nextStartedActivity?.data,
                )
            }
        }
    }

    @Test
    fun `transport failure raises the overlay only for the main frame`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val client = surface.currentWebView().webViewClient

                // The error argument is unread by the policy; null keeps
                // the test free of framework-internal constructors.
                client.onReceivedError(
                    surface.currentWebView(),
                    FakeRequest(Uri.parse("$origin/"), mainFrame = false),
                    null,
                )
                assertFalse(surface.offlineVisible(), "sub-resource failures stay quiet")

                client.onReceivedError(
                    surface.currentWebView(),
                    FakeRequest(Uri.parse("$origin/"), mainFrame = true),
                    null,
                )
                assertTrue(surface.offlineVisible(), "main-frame transport failure raises the overlay")
            }
        }
    }

    @Test
    fun `main-frame HTTP errors render as pages not the offline overlay`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val client = surface.currentWebView().webViewClient as WebViewClient

                // POLICY (W111): the proxy's 502 and the origin's styled
                // 404 are the origin's own error contract — pages render,
                // the overlay is reserved for "stack unreachable".
                client.onReceivedHttpError(
                    surface.currentWebView(),
                    FakeRequest(Uri.parse("$origin/nope"), mainFrame = true),
                    null,
                )
                assertFalse(surface.offlineVisible(), "HTTP error pages must render, not be masked")
            }
        }
    }

    @Test
    fun `same-origin downloads enqueue with the session cookie, foreign ones hand off`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val downloads =
                    activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                // A session cookie exists (the WebView login minted it) —
                // the same-origin request must carry it without crashing.
                android.webkit.CookieManager.getInstance().setCookie("$origin/", "wp-session=t")

                surface.handleDownload(
                    "$origin/api/messages/42/attachments/photo.jpg",
                    "attachment; filename=\"photo.jpg\"",
                    "image/jpeg",
                )
                assertEquals(1, shadowOf(downloads).requestCount, "same-origin download enqueued")
                assertNull(shadowOf(activity).nextStartedActivity, "no hand-off for same-origin")

                surface.handleDownload("https://cdn.example/f.exe", null, null)
                assertEquals(
                    1,
                    shadowOf(downloads).requestCount,
                    "foreign downloads never touch DownloadManager",
                )
                val handOff = shadowOf(activity).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, handOff?.action, "foreign download hands off")
                assertEquals(Uri.parse("https://cdn.example/f.exe"), handOff?.data)
            }
        }
    }

    @Test
    fun `download file names come from the header, the URL, or nowhere`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = activity.webSurface

                assertEquals(
                    "photo.jpg",
                    surface.downloadFileName("attachment; filename=\"photo.jpg\"", "$origin/api/x"),
                    "quoted Content-Disposition filename wins",
                )
                assertEquals(
                    "fax.pdf",
                    surface.downloadFileName("attachment; filename=fax.pdf", "$origin/api/x"),
                    "unquoted Content-Disposition filename wins",
                )
                assertEquals(
                    "export.csv",
                    surface.downloadFileName(null, "$origin/api/export.csv?token=1"),
                    "URL path segment is the fallback",
                )
                assertEquals(
                    "download",
                    surface.downloadFileName(null, "$origin/api/"),
                    "no name anywhere degrades to 'download'",
                )
                assertEquals(
                    "evil.txt",
                    surface.downloadFileName("attachment; filename=\"..\\/evil.txt\"", "$origin/"),
                    "path separators are stripped from the title",
                )
            }
        }
    }
}
