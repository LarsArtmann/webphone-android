package software.larsartmann.webphone.call

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import software.larsartmann.webphone.sip.SipEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The call foreground service's contract under shadowed Android
 * (W140, shadow-permitting): the STYLED branch (CallStyle with the
 * shade hang-up) must build and post when a remote party is named, the
 * PLAIN branch must hold when none is, and the service's lifetime is
 * the CALL's: it retires itself when the engine ends the call, with or
 * without a screen or a stop intent (d7, 2026-11-25: three `CallService
 * .stop` callers were all UI, so a call that ended while no
 * CallActivity was started left the microphone-typed service running
 * with a lying "Call in progress" notification). The shadow cannot
 * force the platform's post-time CallStyle rejection (that stays a
 * device-train verification).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34])
class CallServiceTest {
    private fun answeredCall() {
        CallEngine.onIncomingCall(
            SipEngine.IncomingInvite(
                callId = "svc-1",
                fromUser = "100",
                fromDisplay = "Alice",
                sdp = "v=0",
                respondWith = { _, _ -> },
            ),
        )
        CallEngine.onCallEstablished("svc-1", remoteSdp = "v=0")
    }

    @Test
    fun `an ongoing call posts the styled ongoing notification with the call clock`() {
        answeredCall()
        var controller: ServiceController<CallService>? = null
        try {
            controller = Robolectric.buildService(CallService::class.java).create()
            controller.startCommand(0, 0)

            val notification = shadowOf(controller.get()).lastForegroundNotification
            assertNotNull(notification, "the service must foreground a notification")
            // CallStyle renders the PERSON as the title (same as the
            // incoming style — the caller IS the headline).
            assertEquals(
                "Alice",
                notification.extras.getString(android.app.Notification.EXTRA_TITLE),
            )
            assertEquals(
                CallEngine.state.value.startedAtMs,
                notification.`when`,
                "the shade's call clock rides the engine's establishment timestamp",
            )
        } finally {
            controller?.destroy()
            CallEngine.hangUp()
        }
    }

    @Test
    fun `a service start without a named party still posts the plain ongoing pill`() {
        val controller = Robolectric.buildService(CallService::class.java).create()
        try {
            controller.startCommand(0, 0)

            val notification = shadowOf(controller.get()).lastForegroundNotification
            assertNotNull(notification, "the plain fallback must still foreground a notification")
        } finally {
            controller.destroy()
        }
    }

    /** d7: the call ends in the engine (a remote BYE here) while no
     *  CallActivity is started and nobody sends a stop intent. The
     *  service must go away on its own. */
    @Test
    fun `the service retires itself when the engine ends the call, with no screen alive`() {
        answeredCall()
        var controller: ServiceController<CallService>? = null
        try {
            controller = Robolectric.buildService(CallService::class.java).create()
            controller.startCommand(0, 0)
            val service = controller.get()
            assertFalse(shadowOf(service).isStoppedBySelf, "a live call keeps the service up")

            CallEngine.onCallEnded("svc-1")

            assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
            assertTrue(
                shadowOf(service).isStoppedBySelf,
                "a call that ended in the background must not leave the microphone-typed service running",
            )
            assertTrue(
                shadowOf(service).isForegroundStopped,
                "the ongoing-call notification must go with it",
            )
        } finally {
            controller?.destroy()
            CallEngine.hangUp()
        }
    }

    /** The previous call's ENDED is the engine's resting state until
     *  the next call overwrites it, and an outbound dial starts this
     *  service BEFORE the engine has left that state. The phase at
     *  start is therefore never a verdict: only a transition into
     *  ENDED retires the service. */
    @Test
    fun `a stale ENDED state at start does not retire the service`() {
        answeredCall()
        CallEngine.hangUp()
        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
        var controller: ServiceController<CallService>? = null
        try {
            controller = Robolectric.buildService(CallService::class.java).create()
            controller.startCommand(0, 0)
            val service = controller.get()
            assertFalse(shadowOf(service).isStoppedBySelf, "a fresh dial starts over ENDED; the service must stay")

            CallEngine.onIncomingCall(
                SipEngine.IncomingInvite(
                    callId = "svc-2",
                    fromUser = "100",
                    fromDisplay = "Bob",
                    sdp = "v=0",
                    respondWith = { _, _ -> },
                ),
            )
            assertFalse(shadowOf(service).isStoppedBySelf, "the next call keeps it up")

            CallEngine.onCallEnded("svc-2")

            assertTrue(shadowOf(service).isStoppedBySelf, "that call's end retires it")
        } finally {
            controller?.destroy()
            CallEngine.hangUp()
        }
    }
}
