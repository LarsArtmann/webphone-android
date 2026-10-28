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

        val notifications =
            shadowOf(
                app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
            ).allNotifications
        assertEquals(1, notifications.size)
        // CallStyle renders the PERSON as the title (the caller's name
        // IS the incoming-call headline); the category stays a call.
        assertEquals(
            "Alice",
            notifications[0].extras.getString(android.app.Notification.EXTRA_TITLE),
        )
        assertEquals(
            android.app.Notification.CATEGORY_CALL,
            notifications[0].category,
        )
    }

    @Test
    fun `ring_end stands the ring down`() {
        val app = grantedApp()
        RingEvent.route(app, "ring", "Alice", "call-1")
        RingEvent.route(app, "ring_end", null, null)

        val notifications =
            shadowOf(
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
            val notifications =
                shadowOf(
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

    /** W150 pin: `ring_end` for an already-ANSWERED call is a silent
     *  stand-down — the live call survives (cancelRinging no-ops off
     *  RINGING_IN) and NO missed-call note appears (the call is not
     *  missed, it is in progress). */
    @Test
    fun `ring_end after a local answer spares the live call and posts nothing`() {
        val app = grantedApp()
        try {
            CallEngine.onIncomingCall(
                SipEngine.IncomingInvite(
                    callId = "answered",
                    fromUser = "100",
                    fromDisplay = "Alice",
                    sdp = "v=0",
                    respondWith = { _, _ -> },
                ),
            )
            CallEngine.onCallEstablished("answered", remoteSdp = "v=0")
            RingEvent.route(app, "ring_end", null, null)

            assertEquals(
                CallEngine.Phase.ACTIVE,
                CallEngine.state.value.phase,
                "a stale ring_end must not kill the answered call",
            )
            val notifications =
                shadowOf(
                    app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
                ).allNotifications
            assertTrue(
                notifications.none {
                    it.extras.getString(android.app.Notification.EXTRA_TITLE) ==
                        app.getString(R.string.missed_call)
                },
                "an answered call is not a missed call",
            )
        } finally {
            CallEngine.hangUp()
        }
    }

    /** W170: with the registration DEFINITIVELY broken (auth rejected),
     *  a ring push must not promise a call that can never land — no
     *  Telecom line, no full-screen ring; a plain heads-up naming the
     *  cause takes its place and rides the ring notification id, so a
     *  later ring_end still clears it. */
    @Test
    fun `ring while auth-rejected posts the cause instead of a false ring`() {
        val app = grantedApp()
        try {
            CallEngine.onRegistrationState(SipEngine.RegistrationState.AUTH_REJECTED)
            RingEvent.route(app, "ring", "Alice", "call-1")

            val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            assertTrue(
                shadowOf(telecom).allIncomingCalls.isEmpty(),
                "an auth-rejected line must not ring Telecom",
            )
            val notifications =
                shadowOf(
                    app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
                ).allNotifications
            assertEquals(1, notifications.size)
            assertEquals(
                "Alice",
                notifications[0].extras.getString(android.app.Notification.EXTRA_TITLE),
            )
            assertEquals(
                app.getString(R.string.registration_auth_rejected),
                notifications[0].extras.getString(android.app.Notification.EXTRA_TEXT),
            )
        } finally {
            CallEngine.onRegistrationState(SipEngine.RegistrationState.OFFLINE)
        }
    }
}
