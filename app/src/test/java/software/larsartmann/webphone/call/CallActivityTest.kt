package software.larsartmann.webphone.call

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import software.larsartmann.webphone.sip.SipEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The call screen against the engine's state, launched the way the
 * suite requires (the synchronous controller: ActivityScenario's
 * close() parks forever on Robolectric's main thread, AGENTS
 * "Robolectric activity tests NEVER use ActivityScenario").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34])
class CallActivityTest {
    @After
    fun resetEngine() {
        CallEngine.hangUp()
    }

    /** The previous call's ENDED is the engine's resting state, so a
     *  call screen opened for a NEW call first renders that stale
     *  state. It used to answer it with `CallService.stop`, whose stop
     * path hung the engine up: a stop sent for a call that was already
     *  over could kill the new one in the cradle (found while tracing
     *  d7, 2026-11-25). The screen must leave the service alone: the
     *  service ends with the call on its own. */
    @Test
    fun `a call screen opened over the previous call's ENDED state starts no service intent`() {
        endedCall("stale")

        val controller = Robolectric.buildActivity(CallActivity::class.java).setup()
        try {
            assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
            assertNull(
                shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedService,
                "a stale ENDED state must not reach the call service",
            )
        } finally {
            tearDown(controller)
        }
    }

    @Test
    fun `a call that ends while the screen is up renders Ended and still starts no service intent`() {
        CallEngine.onIncomingCall(ringing("live"))
        val controller = Robolectric.buildActivity(CallActivity::class.java).setup()
        try {
            CallEngine.onCallEnded("live")

            assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
            assertNull(
                shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedService,
                "the service retires itself when the engine ends the call",
            )
        } finally {
            tearDown(controller)
        }
    }

    private fun endedCall(callId: String) {
        CallEngine.onIncomingCall(ringing(callId))
        CallEngine.hangUp()
    }

    private fun ringing(callId: String): SipEngine.IncomingInvite =
        SipEngine.IncomingInvite(
            callId = callId,
            fromUser = "100",
            fromDisplay = "Alice",
            sdp = "v=0",
            respondWith = { _, _ -> },
        )

    private fun tearDown(controller: ActivityController<CallActivity>) {
        controller.pause().stop().destroy()
    }
}
