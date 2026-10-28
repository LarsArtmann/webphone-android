package software.larsartmann.webphone.shell

import android.app.Application
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import org.junit.Before
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

    @Before
    fun keepTheShellUnwired() {
        CallLayer.wiringEnabled = false
    }

    private class FakeRequest(
        private val uri: Uri,
        private val mainFrame: Boolean = true,
        private val redirect: Boolean = false,
    ) : WebResourceRequest {
        override fun getUrl(): Uri = uri
        override fun isForMainFrame(): Boolean = mainFrame
        override fun isRedirect(): Boolean = redirect
        override fun hasGesture(): Boolean = true
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        override fun getMethod(): String = "GET"
    }

    /** MainActivity's battery-exemption ask fires at launch — drain it
     *  before asserting on hand-off intents. */
    private fun drainStartedActivities(activity: MainActivity) {
        while (shadowOf(activity).nextStartedActivity != null) {
            // discard
        }
    }

    @Test
    fun `scope guard is wired through shouldOverrideUrlLoading`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val client = surface.currentWebView().webViewClient
                drainStartedActivities(activity)
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

                surface.onMainFrameTransportFailure(isMainFrame = false)
                assertFalse(surface.offlineVisible(), "sub-resource failures stay quiet")

                surface.onMainFrameTransportFailure(isMainFrame = true)
                assertTrue(surface.offlineVisible(), "main-frame transport failure raises the overlay")
            }
        }
    }

    @Test
    fun `an oidc redirect detour keeps the idp in-shell until the dance comes home`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val client = surface.currentWebView().webViewClient
                drainStartedActivities(activity)

                // The island's OIDC begin is same-origin: loads itself.
                assertFalse(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("$origin/api/auth/oauth2/begin")),
                    ),
                )

                // The origin's 302 to the IdP IS consulted on modern
                // Chromium (R03, emulator-proven 2026-10-27): the
                // main-frame server redirect blesses the detour in-shell.
                val idp = "https://auth.artmann.tech/authorize?client_id=x"
                assertFalse(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse(idp), redirect = true),
                    ),
                    "the origin's own 302 continues in-shell",
                )
                assertEquals(Uri.parse(idp), surface.authDetourUri())

                // The IdP page commits: idempotent — same authority, the
                // detour is already live (the commit-side hook stays as
                // the belt for unjudged commits, e.g. cross-origin POSTs).
                client.doUpdateVisitedHistory(surface.currentWebView(), idp, false)
                assertEquals(Uri.parse(idp), surface.authDetourUri())

                // On the IdP, its OWN consulted links stay in-shell…
                assertFalse(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("https://auth.artmann.tech/login/passkey")),
                    ),
                    "the IdP's own pages continue the dance in-shell",
                )
                // …a THIRD host is still foreign…
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("https://evil.example/phish")),
                    ),
                    "a third host never renders, even mid-detour",
                )
                // …and the origin is always in-shell.
                assertFalse(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("$origin/api/session")),
                    ),
                )

                // The IdP 302s home: the callback commit ends the detour…
                client.doUpdateVisitedHistory(
                    surface.currentWebView(),
                    "$origin/api/auth/oauth2/callback?code=c&state=s",
                    false,
                )
                assertNull(surface.authDetourUri())
                // …the IdP authority is foreign again.
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("https://auth.artmann.tech/login")),
                    ),
                    "after the dance the IdP authority hands off again",
                )
            }
        }
    }

    @Test
    fun `a server redirect blesses the detour but a click never does`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val client = surface.currentWebView().webViewClient
                drainStartedActivities(activity)

                // The R03 seam: a main-frame server redirect from clean
                // ground is the origin's own chain — blessed, in-shell.
                val idp = "https://auth.artmann.tech/authorize"
                assertFalse(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse(idp), redirect = true),
                    ),
                    "redirect blesses and loads in-shell",
                )
                assertEquals(Uri.parse(idp), surface.authDetourUri())

                // Mid-detour, a redirect to a THIRD host stays foreign
                // (single-authority policy; multi-host IdP is the open
                // operator question) — handed off, detour untouched.
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("https://evil.example/next"), redirect = true),
                    ),
                    "a third-host redirect hands off mid-detour",
                )
                assertEquals(Uri.parse(idp), surface.authDetourUri())

                // A sub-frame redirect never blesses (iframes don't aim
                // the top-level session WebView).
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("https://other.example/frame"), mainFrame = false, redirect = true),
                    ),
                    "iframe redirects never bless",
                )
                assertEquals(Uri.parse(idp), surface.authDetourUri())

                // Non-web scheme redirects (absurd, but) hand off — the
                // bless rule is http(s) only.
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("tel:+49170"), redirect = true),
                    ),
                )
                assertEquals(Uri.parse(idp), surface.authDetourUri())

                // Home: the origin commit clears the blessing.
                client.doUpdateVisitedHistory(
                    surface.currentWebView(),
                    "$origin/api/auth/oauth2/callback?code=c",
                    false,
                )
                assertNull(surface.authDetourUri())

                // And the SAME IdP URL as a user click is a plain
                // hand-off — W105 (external link) is untouched.
                assertTrue(
                    client.shouldOverrideUrlLoading(
                        surface.currentWebView(),
                        FakeRequest(Uri.parse("$idp/login")),
                    ),
                    "a click on a foreign host never enters the shell",
                )
                assertNull(surface.authDetourUri())
                val handOff = shadowOf(activity).nextStartedActivity
                assertEquals(Uri.parse("$idp/login"), handOff?.data)
            }
        }
    }

    @Test
    fun `onPageStarted is the belt - foreign start blesses, origin start clears`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface
                val client = surface.currentWebView().webViewClient

                // A foreign page start (unjudged arrival, e.g. a
                // cross-origin POST response) blesses the detour…
                val idp = "https://auth.artmann.tech/authorize"
                client.onPageStarted(surface.currentWebView(), idp, null)
                assertEquals(Uri.parse(idp), surface.authDetourUri(), "start blesses")

                // …idempotently (same authority — the stored handle
                // tracks the latest page, the verdict keys on the
                // authority, so the blessing stays live on it)…
                client.onPageStarted(surface.currentWebView(), "https://auth.artmann.tech/login", null)
                assertEquals(
                    Uri.parse("https://auth.artmann.tech/login"),
                    surface.authDetourUri(),
                    "same-authority start keeps the blessing live",
                )

                // …and an origin page start ends the detour.
                client.onPageStarted(surface.currentWebView(), "$origin/messages", null)
                assertNull(surface.authDetourUri(), "origin start clears")
            }
        }
    }

    @Test
    fun `loadUri loads same-origin deep links and refuses foreign ones`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = origin
                val surface = activity.webSurface

                surface.loadUri(Uri.parse("$origin/messages/42"))
                assertEquals(
                    "$origin/messages/42",
                    shadowOf(surface.currentWebView()).lastLoadedUrl,
                )

                // Defense in depth: even a manifest mis-declaration can
                // never aim the cookie-bearing WebView at a foreign page.
                surface.loadUri(Uri.parse("https://evil.example/phish"))
                assertEquals(
                    "$origin/messages/42",
                    shadowOf(surface.currentWebView()).lastLoadedUrl,
                    "foreign deep links are never loaded",
                )
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
                    android.webkit.WebResourceResponse(
                        "text/html",
                        "utf-8",
                        502,
                        "Bad Gateway",
                        emptyMap(),
                        null,
                    ),
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
                drainStartedActivities(activity)

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
                    surface.downloadFileName(null, "$origin?token=1"),
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
