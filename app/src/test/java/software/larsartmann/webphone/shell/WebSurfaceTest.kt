package software.larsartmann.webphone.shell

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import software.larsartmann.webphone.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The hardened WebView surface's behavioral seams: the offline overlay
 * cycle (a main-frame failure must never leave Chromium's error page as
 * the product surface) and the renderer-death rebuild (the shell swaps
 * in a fresh WebView instead of dying with the renderer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebSurfaceTest {

    @Test
    fun `offline overlay shows on failure and retry reloads the origin`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = activity.webSurface
                assertFalse(surface.offlineVisible(), "overlay starts hidden")

                surface.showOffline()
                assertTrue(surface.offlineVisible(), "main-frame failure raises the overlay")

                surface.retry()
                assertFalse(surface.offlineVisible(), "retry dismisses the overlay")
                assertEquals(
                    App.origin,
                    shadowOf(surface.currentWebView()).lastLoadedUrl,
                    "retry re-aims the configured origin",
                )
            }
        }
    }

    @Test
    fun `retry after an offline detoured page clears the stale blessing`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = activity.webSurface
                val idp = "https://auth.artmann.tech/authorize"

                surface.onCommittedNavigation(Uri.parse(idp))
                assertEquals(Uri.parse(idp), surface.authDetourUri(), "detour live")

                // The IdP page dies into a transport failure; the user
                // hits Retry. No commit will ever fire for the reload's
                // target if IT fails too — the blessing must die HERE.
                surface.showOffline()
                surface.retry()

                assertNull(
                    surface.authDetourUri(),
                    "retry never leaves a stale auth blessing",
                )
                assertEquals(
                    App.origin,
                    shadowOf(surface.currentWebView()).lastLoadedUrl,
                    "retry re-aims the configured origin",
                )
            }
        }
    }

    @Test
    fun `renderer death swaps in a fresh webview and reloads`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = activity.webSurface
                val dead = surface.currentWebView()

                surface.rebuildAfterRendererDeath()

                val fresh = surface.currentWebView()
                assertNotSame(dead, fresh, "the renderer-dead WebView must be replaced")
                assertEquals(
                    App.origin,
                    shadowOf(fresh).lastLoadedUrl,
                    "the fresh WebView re-aims the configured origin",
                )
                assertEquals(2, surface.container.childCount, "webview + offline overlay")
            }
        }
    }

    @Test
    fun `navigation hands foreign origins to the system and keeps the origin in-shell`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = activity.webSurface

                val foreign = surface.navigateOut(Uri.parse("https://example.com/invoice.pdf"))
                assertTrue(foreign, "a foreign navigation is consumed by the hand-off")
                val started = shadowOf(activity).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, started?.action, "the hand-off is a system view intent")
                assertEquals("example.com", started?.data?.host, "the hand-off carries the foreign URL")

                val before = shadowOf(activity).nextStartedActivity
                assertFalse(
                    surface.navigateOut(Uri.parse("https://pbx.artmann.tech/messages")),
                    "a same-origin navigation stays in the shell",
                )
                assertSame(
                    before,
                    shadowOf(activity).nextStartedActivity,
                    "no system intent for an in-shell navigation",
                )
            }
        }
    }

    @Test
    fun `transport failures raise the overlay only for the main frame`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = activity.webSurface
                assertFalse(surface.offlineVisible(), "overlay starts hidden")

                surface.onMainFrameTransportFailure(false)
                assertFalse(
                    surface.offlineVisible(),
                    "a sub-resource failure is routine — never the overlay",
                )

                surface.onMainFrameTransportFailure(true)
                assertTrue(
                    surface.offlineVisible(),
                    "a main-frame transport failure raises the overlay",
                )

                surface.retry()
                assertFalse(surface.offlineVisible(), "retry dismisses the overlay")
            }
        }
    }
}
