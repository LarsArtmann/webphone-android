package software.larsartmann.webphone.shell

import android.app.Application
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
                    shadowOf(surface.currentWebView()).loadedUrls.last().url,
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
                    shadowOf(fresh).loadedUrls.last().url,
                    "the fresh WebView re-aims the configured origin",
                )
                assertEquals(2, surface.container.childCount, "webview + offline overlay")
            }
        }
    }
}
