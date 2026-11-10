package software.larsartmann.webphone.push

import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import software.larsartmann.webphone.push.WakeLaneState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The de-Googled ring lane's persistence contract (W151): the service
 * persists server+topic so a STICKY restart (null intent) can
 * resubscribe, and a topic ROTATION rides the stop→start cycle
 * (Settings save → CallLayer teardown → next resume re-wires) — the
 * fresh start must win the prefs and aim the websocket at the NEW
 * topic.
 *
 * The controllers are DESTROYED in finally: a leaked service leaves
 * its reconnect thread armed (`stopped` never set), which keeps
 * resubscribing — new OkHttp clients, new websocket attempts — on a
 * doubling backoff for the rest of the suite's JVM and wedges later
 * classes' ActivityScenario teardowns (found by jstack on the wedged
 * Gradle worker, 2026-10-28).
 */
@RunWith(RobolectricTestRunner::class)
class NtfySubscriptionServiceTest {
    @Before
    fun resetTheLedger() {
        // Statics persist across methods in a classloader: every pin
        // below asserts an exact ledger verdict.
        WakeLane.report(WakeLaneState.UNKNOWN)
    }

    private fun startIntent(
        server: String,
        topic: String,
    ) = Intent(ApplicationProvider.getApplicationContext(), NtfySubscriptionService::class.java)
        .putExtra("server", server)
        .putExtra("topic", topic)

    @Test
    fun `the websocket endpoint is server slash topic slash ws`() {
        assertEquals(
            "https://push.example.org/my-topic/ws",
            NtfySubscriptionService.subscriptionUrl("https://push.example.org", "my-topic"),
        )
        assertEquals(
            "https://push.example.org/my-topic/ws",
            NtfySubscriptionService.subscriptionUrl("https://push.example.org/", "my-topic"),
            "a trailing slash on the server must not double",
        )
    }

    @Test
    fun `an explicit stop kills the lane - no pending reconnect resurrects it`() {
        // The historical leak (2026-10-28 wedge): a stop closed the
        // socket, scheduleReconnect ran, and ~1 s later a RESURRECTED
        // websocket landed — the lane rang forever with no owner. The
        // `stopped` flag is the fix. The pin drives the resurrect
        // vector DIRECTLY (scheduleReconnect after ACTION_STOP) so the
        // verdict does not depend on the close handshake's latency
        // under Robolectric: if the flag fails, a new connection lands
        // within the backoff (~1 s) and the count moves.
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        server.start()
        val controller =
            Robolectric
                .buildService(
                    NtfySubscriptionService::class.java,
                    startIntent(server.url("/").toString(), "stop-leak"),
                ).create()
                .startCommand(0, 0)
        try {
            awaitCondition(5_000, "the subscription socket to open") { server.requestCount >= 1 }

            val stop =
                Intent(ApplicationProvider.getApplicationContext(), NtfySubscriptionService::class.java)
                    .setAction(NtfySubscriptionService.ACTION_STOP)
            assertEquals(
                Service.START_NOT_STICKY,
                controller.get().onStartCommand(stop, 0, 1),
                "an explicit stop must not come back from the dead via stickiness",
            )

            // The exact leak vector, fired by hand: schedule the
            // reconnect the closed socket would have scheduled, then
            // hold the window past its backoff. Fail FAST on any new
            // connection — movement IS the leak.
            val service = controller.get()
            service.scheduleReconnect(server.url("/").toString(), "stop-leak")
            val before = server.requestCount
            repeat(40) {
                Thread.sleep(100)
                assertEquals(
                    before,
                    server.requestCount,
                    "a pending reconnect resurrected the lane after ACTION_STOP",
                )
            }
        } finally {
            controller.destroy()
            // The close handshake is async — give the server-side
            // websocket a beat, then treat a lingering queue as teardown
            // noise (the contract assertions above already held; the
            // forkEvery fence gives the next class a fresh JVM).
            Thread.sleep(500)
            runCatching { server.shutdown() }
        }
    }

    @Test
    fun `a transport flap inside the grace window never surfaces as wake off`() {
        val controller =
            Robolectric
                .buildService(NtfySubscriptionService::class.java)
                .create()
        try {
            val service = controller.get()
            service.disconnectGraceMs = 150
            WakeLane.report(WakeLaneState.ARMED)

            service.onTransportDown()
            assertEquals(
                WakeLaneState.ARMED,
                WakeLane.state.value,
                "the grace must hold the verdict - an instant DISCONNECTED is the flicker the grace exists to kill",
            )

            // The flap heals: the socket is back before the window closes.
            repeat(20) {
                Thread.sleep(50)
                assertEquals(WakeLaneState.ARMED, WakeLane.state.value)
            }
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `a transport outage past the grace window surfaces as disconnected`() {
        val controller =
            Robolectric
                .buildService(NtfySubscriptionService::class.java)
                .create()
        try {
            val service = controller.get()
            service.disconnectGraceMs = 100
            WakeLane.report(WakeLaneState.ARMED)
            service.onTransportDown()

            val deadline = System.nanoTime() + 5_000_000_000
            while (System.nanoTime() < deadline && WakeLane.state.value != WakeLaneState.DISCONNECTED) {
                Thread.sleep(50)
            }
            assertEquals(
                WakeLaneState.DISCONNECTED,
                WakeLane.state.value,
                "a REAL outage must surface - the grace is a flicker filter, not a verdict suppressor",
            )
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `the stop guard holds - a failure after ACTION_STOP never reports disconnected`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        server.start()
        val controller =
            Robolectric
                .buildService(
                    NtfySubscriptionService::class.java,
                    startIntent(server.url("/").toString(), "stop-guard"),
                ).create()
                .startCommand(0, 0)
        try {
            awaitCondition(5_000, "the subscription socket to open") { server.requestCount >= 1 }
            val stop =
                Intent(ApplicationProvider.getApplicationContext(), NtfySubscriptionService::class.java)
                    .setAction(NtfySubscriptionService.ACTION_STOP)
            controller.get().onStartCommand(stop, 0, 1)

            WakeLane.report(WakeLaneState.ARMED)
            controller.get().disconnectGraceMs = 50
            controller.get().onTransportDown()
            repeat(10) {
                Thread.sleep(50)
                assertEquals(
                    WakeLaneState.ARMED,
                    WakeLane.state.value,
                    "a stopped lane must stay silent - the stopped ledger would be a ghost verdict",
                )
            }
        } finally {
            controller.destroy()
            Thread.sleep(500)
            runCatching { server.shutdown() }
        }
    }

    private fun awaitCondition(
        timeoutMs: Long,
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("timed out after ${timeoutMs}ms waiting for $what")
    }

    @Test
    fun `a start persists its topic and a restart aims at the NEW one`() {
        val firstController =
            Robolectric
                .buildService(
                    NtfySubscriptionService::class.java,
                    startIntent("https://push.example.org", "old-topic"),
                ).create()
                .startCommand(0, 0)
        try {
            val prefs =
                firstController
                    .get()
                    .getSharedPreferences(NtfySubscriptionService.PREFS, android.content.Context.MODE_PRIVATE)
            assertEquals(
                "old-topic",
                prefs.getString(NtfySubscriptionService.KEY_TOPIC, null),
                "the running lane persists what it subscribed to",
            )

            val secondController =
                Robolectric
                    .buildService(
                        NtfySubscriptionService::class.java,
                        startIntent("https://push.example.org", "new-topic"),
                    ).create()
                    .startCommand(0, 0)
            try {
                assertEquals(
                    "new-topic",
                    secondController
                        .get()
                        .getSharedPreferences(NtfySubscriptionService.PREFS, android.content.Context.MODE_PRIVATE)
                        .getString(NtfySubscriptionService.KEY_TOPIC, null),
                    "the rotation's restart must aim at the NEW topic",
                )
                assertEquals(
                    "https://push.example.org/new-topic/ws",
                    NtfySubscriptionService.subscriptionUrl("https://push.example.org", "new-topic"),
                )
            } finally {
                secondController.destroy()
            }
        } finally {
            firstController.destroy()
        }
    }
}
