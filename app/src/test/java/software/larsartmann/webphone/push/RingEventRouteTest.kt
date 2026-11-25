package software.larsartmann.webphone.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.push.RingEvent.EXTRA_CALL_ID
import software.larsartmann.webphone.push.RingEvent.EXTRA_FROM
import software.larsartmann.webphone.shell.CallLayer
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
class RingEventRouteTest {
    /** Hermeticity: every `ring` below asks the call layer to wire
     *  itself, whose production default resumes the session over real
     *  HTTP against the live stack, and the coexistence pin drives the
     *  dead-session teardown, whose best-effort deregistration would
     *  DELETE against it. A JVM test must never reach the origin: both
     *  seams are closed for the whole class (the sandbox is per-class,
     *  so the flip cannot leak). */
    @Before
    fun hermeticCallLayer() {
        CallLayer.wiringEnabled = false
        CallLayer.deregisterPushDevice = { false }
    }

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
            // The note must SAY why the call never rang: a bare
            // "Missed call / Bob" reads as a call nobody picked up.
            assertEquals(
                app.getString(R.string.missed_call_reason, "Bob", app.getString(R.string.line_busy)),
                notifications[0].extras.getString(android.app.Notification.EXTRA_TEXT),
            )
        } finally {
            CallEngine.cancelRinging()
        }
    }

    /** Two unrelated notices must never share a notification slot. The
     *  missed-call note and the sign-in-expired notice both posted
     *  under id 1004, so whichever came second silently replaced the
     *  other: a missed call vanished under "Ringing paused", or the
     *  sign-in call-to-action vanished under a missed call (found
     *  while reading for the 2026-11-25 lint pass). */
    @Test
    fun `a missed-call note and the sign-in-expired notice coexist`() {
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
            CallLayer.onSessionDead(app)

            val titles =
                shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .allNotifications
                    .map { it.extras.getString(android.app.Notification.EXTRA_TITLE) }
                    .toSet()
            assertEquals(
                setOf(
                    app.getString(R.string.missed_call),
                    app.getString(R.string.session_expired_title),
                ),
                titles,
                "one notice must not replace the other",
            )
        } finally {
            CallEngine.cancelRinging()
        }
    }

    /** A ring that cannot post must still REACH Telecom and must SAY
     *  why nothing appeared. The ring notification is the surface the
     *  user sees, so "notifications are disabled for this app" was a
     *  silent no-ring cause (a logcat-only failure is the undiagnosable
     *  kind, the wake-lane lesson). */
    @Test
    fun `a ring that cannot post still reaches Telecom and names the refusal in the log`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.MANAGE_OWN_CALLS)
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)

        RingEvent.route(app, "ring", "Alice", "call-1")

        val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        assertEquals(
            1,
            shadowOf(telecom).allIncomingCalls.size,
            "the Telecom ring must not depend on the notification",
        )
        assertTrue(shadowOf(manager).allNotifications.isEmpty(), "nothing can be posted")
        val lines = ShadowLog.getLogsForTag("RingEvent").map { it.msg }
        assertTrue(
            lines.any { it.contains("skipped: notifications are disabled") },
            "logcat must name the refusal, got: $lines",
        )
    }

    /** API 33+ without the POST_NOTIFICATIONS grant: the explicit
     *  permission check (the one Android Lint's MissingPermission can
     *  see) must skip the post, never throw, and name the grant. */
    @Test
    fun `a ring without the notification grant on API 33 and up posts nothing and names the grant in the log`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app).grantPermissions(Manifest.permission.MANAGE_OWN_CALLS)

        RingEvent.route(app, "ring", "Alice", "call-1")

        val manager = app.getSystemService(NotificationManager::class.java)
        assertTrue(shadowOf(manager).allNotifications.isEmpty(), "nothing can be posted")
        val lines = ShadowLog.getLogsForTag("RingEvent").map { it.msg }
        assertTrue(
            lines.any { it.contains("POST_NOTIFICATIONS not granted") },
            "logcat must name the missing grant, got: $lines",
        )
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
