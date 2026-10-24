package software.larsartmann.webphone.shell

import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import android.app.AlertDialog
import android.app.Application
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shell's Robolectric smoke: MainActivity must come up with the
 * WebView chrome and — first run, default origin — prompt for the
 * stack origin (the fact the app cannot guess). CallLayer wiring is
 * invoked inside onCreate; a crash there fails this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainActivityLaunchTest {

    @Test
    fun `main activity launches with the webview chrome`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.window)
                val decor = activity.window.decorView
                assertTrue(decor.childCount > 0, "the shell must render content")
            }
        }
    }

    @Test
    fun `first run with the default origin prompts for the stack origin`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(App.DEFAULT_ORIGIN, App.origin)
        ActivityScenario.launch(MainActivity::class.java).use { }

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog?
        assertNotNull(dialog, "first run must prompt for the stack origin")
        assertTrue(dialog.isShowing)
    }
}
