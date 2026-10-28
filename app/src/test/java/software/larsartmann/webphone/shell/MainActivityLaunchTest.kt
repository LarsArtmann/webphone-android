package software.larsartmann.webphone.shell

import android.app.Application
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shell's Robolectric smoke: MainActivity must come up with the
 * WebView chrome. CallLayer wiring is seam-disabled here (a unit test
 * must not phone the live stack — that hung the suite); the wiring
 * contract itself is pinned in AttachGuardTest against a fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainActivityLaunchTest {

    @Before
    fun keepTheShellUnwired() {
        CallLayer.wiringEnabled = false
    }

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
