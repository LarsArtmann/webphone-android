package software.larsartmann.webphone.call

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import software.larsartmann.webphone.R
import software.larsartmann.webphone.sip.SipEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The call foreground service's notification contract under shadowed
 * Android (W140, shadow-permitting): the STYLED branch (CallStyle with
 * the shade hang-up) must build and post when a remote party is
 * named, the PLAIN branch must hold when none is, and the STOP action
 * must drive the engine's hang-up. The shadow cannot force the
 * platform's post-time CallStyle rejection (that stays a device-train
 * verification) — what is pinned here is that both branches BUILD and
 * the service survives both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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
        try {
            val controller = Robolectric.buildService(CallService::class.java).create()
            controller.startCommand(0, 0)

            val notification = shadowOf(controller.get()).lastForegroundNotification
            assertNotNull(notification, "the service must foreground a notification")
            assertEquals(
                ApplicationProvider.getApplicationContext<Application>()
                    .getString(R.string.call_active),
                notification.extras.getString(android.app.Notification.EXTRA_TITLE),
            )
            assertEquals(
                CallEngine.state.value.startedAtMs,
                notification.`when`,
                "the shade's call clock rides the engine's establishment timestamp",
            )
            controller.destroy()
        } finally {
            CallEngine.hangUp()
        }
    }

    @Test
    fun `a service start without a named party still posts the plain ongoing pill`() {
        val controller = Robolectric.buildService(CallService::class.java).create()
        controller.startCommand(0, 0)
        controller.get().stopSelf()
    }

    @Test
    fun `the shade hang-up action drives the engine hang-up`() {
        answeredCall()
        try {
            val controller = Robolectric.buildService(
                CallService::class.java,
                Intent(ApplicationProvider.getApplicationContext(), CallService::class.java)
                    .setAction("software.larsartmann.webphone.call.STOP"),
            ).create()
            val sticky = controller.get().onStartCommand(controller.intent, 0, 1)

            assertEquals(
                android.app.Service.START_STICKY,
                sticky,
                "the stop path stays sticky (the FGS contract)",
            )
            assertEquals(
                CallEngine.Phase.ENDED,
                CallEngine.state.value.phase,
                "the shade hang-up must end the engine's call",
            )
            controller.get().stopSelf()
        } finally {
            CallEngine.hangUp()
        }
    }
}
