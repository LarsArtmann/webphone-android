package software.larsartmann.webphone.push

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

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
@Config(sdk = [34], application = Application::class)
class NtfySubscriptionServiceTest {

    private fun startIntent(server: String, topic: String) =
        Intent(ApplicationProvider.getApplicationContext(), NtfySubscriptionService::class.java)
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
    fun `a start persists its topic and a restart aims at the NEW one`() {
        val firstController = Robolectric.buildService(NtfySubscriptionService::class.java, startIntent("https://push.example.org", "old-topic"))
            .create()
            .startCommand(0, 0)
        try {
            val prefs = firstController.get()
                .getSharedPreferences(NtfySubscriptionService.PREFS, android.content.Context.MODE_PRIVATE)
            assertEquals(
                "old-topic",
                prefs.getString(NtfySubscriptionService.KEY_TOPIC, null),
                "the running lane persists what it subscribed to",
            )

            val secondController = Robolectric.buildService(NtfySubscriptionService::class.java, startIntent("https://push.example.org", "new-topic"))
                .create()
                .startCommand(0, 0)
            try {
                assertEquals(
                    "new-topic",
                    secondController.get()
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
