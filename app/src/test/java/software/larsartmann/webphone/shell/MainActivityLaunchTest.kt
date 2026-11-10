package software.larsartmann.webphone.shell

import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shell's Robolectric smoke: MainActivity must come up with the
 * WebView chrome. CallLayer wiring is seam-disabled here (a unit test
 * must not phone the live stack — that hung the suite); the wiring
 * contract itself is pinned in AttachGuardTest against a fake.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchTest {
    @Before
    fun keepTheShellUnwired() {
        CallLayer.wiringEnabled = false
        // One classloader per test CLASS (statics persist across
        // methods): reset the wake ledger so the UNKNOWN pin cannot
        // inherit a sibling verdict's report.
        WakeLane.report(WakeLaneState.UNKNOWN)
    }

    @Test
    fun `main activity launches with the webview chrome`() {
        // The synchronous ActivityController, never ActivityScenario:
        // its close() wait-for-DESTROYED loop deadlocks the Robolectric
        // main thread on a bare launch of the singleTask shell
        // (jstack-proven 2026-10-28; DeepLinkIntakeTest tells the story).
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity.window)
        val decor = activity.window.decorView as ViewGroup
        assertTrue(decor.childCount > 0, "the shell must render content")
        controller.pause().stop().destroy()
    }

    @Test
    fun `an unknown wake lane shows no wake suffix`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val label = controller.get().statusLabel
        assertFalse(
            label.text.contains("wake"),
            "UNKNOWN means the lane's fate is not known - no verdict may be claimed",
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun `a refused wake lane shows the wake-off verdict with a reason`() {
        WakeLane.report(WakeLaneState.REFUSED)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val label = controller.get().statusLabel
        assertTrue(
            label.text.contains("wake off"),
            "the pill must carry the wake verdict: got \"${label.text}\"",
        )
        assertNotNull(
            label.contentDescription,
            "an off verdict without its reason is the silent failure again",
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun `the label builder pairs every wake verdict with its text`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = CallEngine.RegistrationInfo()
        assertEquals(
            "Not connected",
            linePillText(context, info, WakeLaneState.UNKNOWN).first,
        )
        assertTrue(linePillText(context, info, WakeLaneState.ARMED).first.contains("wake on"))
        assertTrue(linePillText(context, info, WakeLaneState.DISCONNECTED).first.contains("wake off"))
        assertTrue(linePillText(context, info, WakeLaneState.SESSION_DEAD).second != null)
    }
}
