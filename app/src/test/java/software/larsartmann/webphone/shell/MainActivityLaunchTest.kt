package software.larsartmann.webphone.shell

import android.app.Application
import android.content.Context
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import software.larsartmann.webphone.App
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
                val decor = activity.window.decorView as ViewGroup
                assertTrue(decor.childCount > 0, "the shell must render content")
            }
        }
    }

    @Test
    fun `first run with the default origin prompts for the stack origin`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Deterministic first-run: whatever a sibling test did with the
        // settings file, this test starts with a clean slate.
        app.getSharedPreferences(App.PREFS_SETTINGS, Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(App.DEFAULT_ORIGIN, App.origin)
        ActivityScenario.launch(MainActivity::class.java).use { }

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog, "first run must prompt for the stack origin")
        assertTrue(dialog.isShowing)
    }
}
