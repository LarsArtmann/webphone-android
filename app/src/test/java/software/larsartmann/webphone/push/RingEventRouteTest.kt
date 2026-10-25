package software.larsartmann.webphone.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.push.RingEvent.EXTRA_CALL_ID
import software.larsartmann.webphone.push.RingEvent.EXTRA_FROM
import software.larsartmann.webphone.sip.SipEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The push-payload router's shadowed smoke: a `ring` fact must reach
 * the Telecom stack as an incoming self-managed call AND surface the
 * heads-up notification; `ring_end` must stand both down. These are
 * the two surfaces that make the phone actually ring — regressions
 * here must fail before a device train does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RingEventRouteTest {

    private fun grantedApp(): Application {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.MANAGE_OWN_CALLS,
        )
        return app
    }

    @Test
    fun `ring routes to telecom as an incoming self-managed call`() {
        val app = grantedApp()
        RingEvent.route(app, "ring", "Alice", "call-1")

        val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val incoming = shadowOf(telecom).allIncomingCalls
        assertEquals(1, incoming.size)
        assertEquals("Alice", incoming[0].extras.getString(RingEvent.EXTRA_FROM))
        assertEquals("call-1", incoming[0].extras.getString(RingEvent.EXTRA_CALL_ID))
    }

    @Test
    fun `ring posts the heads-up notification`() {
        val app = grantedApp()
        RingEvent.route(app, "ring", "Alice", "call-1")

        val notifications = shadowOf(
            app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
        ).allNotifications
        assertEquals(1, notifications.size)
        assertEquals(
            app.getString(R.string.incoming_call),
            notifications[0].extras.getString(android.app.Notification.EXTRA_TITLE),
        )
    }

    @Test
    fun `ring_end stands the ring down`() {
        val app = grantedApp()
        RingEvent.route(app, "ring", "Alice", "call-1")
        RingEvent.route(app, "ring_end", null, null)

        val notifications = shadowOf(
            app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
        ).allNotifications
        assertTrue(notifications.isEmpty(), "ring_end must cancel the ring notification")
    }

    @Test
    fun `unknown events are ignored, not crashed on`() {
        val app = grantedApp()
        RingEvent.route(app, "mystery_event", "Alice", "call-1")
        val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        assertTrue(shadowOf(telecom).allIncomingCalls.isEmpty())
    }

    /** The single-call policy at the router: a second ring while a
     *  call is live must NOT add a second Telecom line — the human
     *  gets a missed-call note instead (F095/F096). The engine's RINGING
     *  state is claimed directly (no WebRTC on the JVM); it is stood
     *  back down so the shared singleton stays free for the other
     *  tests. */
    @Test
    fun `ring while a call is live posts a missed call instead of a second line`() {
        val app = grantedApp()
        try {
            CallEngine.onIncomingCall(
                SipEngine.IncomingInvite(
                    callId = "live",
                    fromUser = "100",
                    fromDisplay = "Alice",
                    sdp = "v=0",
                    respondWith = { _, _ -> },
                ),
            )
            RingEvent.route(app, "ring", "Bob", "call-2")

            val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            assertTrue(
                shadowOf(telecom).allIncomingCalls.isEmpty(),
                "a busy line must not grow a second incoming Telecom call",
            )
            val notifications = shadowOf(
                app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
            ).allNotifications
            assertEquals(1, notifications.size)
            assertEquals(
                app.getString(R.string.missed_call),
                notifications[0].extras.getString(android.app.Notification.EXTRA_TITLE),
            )
        } finally {
            CallEngine.cancelRinging()
        }
    }
}
