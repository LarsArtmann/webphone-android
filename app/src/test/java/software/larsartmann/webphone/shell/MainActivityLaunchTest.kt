package software.larsartmann.webphone.shell

import android.app.Application
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shell's Robolectric smoke: MainActivity must come up with the
 * WebView chrome. CallLayer wiring is invoked inside onCreate; a
 * crash there fails this test.
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
}
