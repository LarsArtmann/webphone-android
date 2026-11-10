package software.larsartmann.webphone.shell

import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wiring gate's contract (J16/M052): the JVM suite's hermeticity
 * rides [CallLayer.wiringEnabled] — default ON for production, flipped
 * OFF by the four Robolectric classes that launch the shell
 * (MainActivityLaunchTest, DeepLinkIntakeTest, WebSurfaceTest,
 * WebSurfacePolicyTest), none of which restores it. This class is the
 * canary for both halves of that arrangement:
 *
 * - the default is still TRUE here — a flip leaking across the
 *   per-class sandbox (or the forkEvery JVM fence) would fail this
 *   line and with it every shell-launch session that trusts the
 *   default;
 * - a disabled gate makes ensureWired a SYNCHRONOUS no-op: no
 *   CONNECTING badge (the registration StateFlow never moves), no
 *   session resume, no socket.
 */
@RunWith(RobolectricTestRunner::class)
class CallLayerWiringGateTest {
    @Test
    fun `wiring defaults to enabled despite the flipper classes`() {
        assertTrue(
            CallLayer.wiringEnabled,
            "a sibling class's wiringEnabled=false leaked across the sandbox boundary",
        )
    }

    @Test
    fun `a disabled gate makes ensureWired a synchronous no-op`() {
        val before = CallEngine.registration.value
        CallLayer.wiringEnabled = false
        try {
            CallLayer.ensureWired(ApplicationProvider.getApplicationContext())
            assertEquals(
                before,
                CallEngine.registration.value,
                "the gate must return before any wiring state (badge, session, socket) moves",
            )
        } finally {
            CallLayer.wiringEnabled = true
        }
    }

    @Test
    fun `shutdown returns the ledger to unknown - the state is being re-decided`() {
        WakeLane.report(WakeLaneState.ARMED)
        CallLayer.shutdown()
        assertEquals(
            WakeLaneState.UNKNOWN,
            WakeLane.state.value,
            "a torn-down lane must not keep claiming a verdict (Settings teardown re-wires on next resume)",
        )
    }
}
