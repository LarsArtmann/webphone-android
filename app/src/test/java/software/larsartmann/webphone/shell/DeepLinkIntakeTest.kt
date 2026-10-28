package software.larsartmann.webphone.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import software.larsartmann.webphone.App
import software.larsartmann.webphone.call.CallActivity
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.call.DialpadActivity
import software.larsartmann.webphone.sip.SipEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The exported deep-link intake end-to-end (Robolectric): a cold-start
 * VIEW intent routes through MainActivity's onCreate, a warm one through
 * onNewIntent (singleTask) — tel:/sip: land in the dialer PRE-FILLED,
 * stack App Links load in the shell, foreign links open the app's home
 * state and nothing else.
 *
 * The activities are driven by Robolectric's SYNCHRONOUS
 * ActivityController, never ActivityScenario: the scenario's close()
 * parks in a wait-for-DESTROYED loop that runs ON the Robolectric main
 * thread, so the destroy dispatch can never be delivered — a bare
 * launch of the singleTask shell hung the whole suite on exactly that
 * for hours (jstack-proven 2026-10-28: the executor sat at ~20 s CPU
 * forever while the test worker waited on its own main thread). The
 * controller drives create→resume→destroy inline; there is no wait to
 * lose.
 */
@RunWith(RobolectricTestRunner::class)
class DeepLinkIntakeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun keepTheShellUnwired() {
        CallLayer.wiringEnabled = false
    }

    /** The origin must be set BEFORE the drive: onCreate routes the
     *  cold link against it. (The ActivityScenario variant set it
     *  after the fact and only worked because the shipped default
     *  origin matched the test's.) */
    private fun launchMain(intent: Intent? = null): ActivityController<MainActivity> {
        App.origin = "https://pbx.artmann.tech"
        val controller =
            if (intent == null) {
                Robolectric.buildActivity(MainActivity::class.java)
            } else {
                Robolectric.buildActivity(MainActivity::class.java, intent)
            }
        return controller.setup()
    }

    /** Synchronous teardown — the shape ActivityScenario could never
     *  deliver here (see the class KDoc). */
    private fun ActivityController<MainActivity>.teardown() {
        pause().stop().destroy()
    }

    private fun viewIntent(uri: Uri): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
        }

    @Test
    fun `a tel deep link cold-starts the dialer prefilled`() {
        val controller = launchMain(viewIntent(Uri.parse("tel:+491701234567")))
        val started = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(started, "the dialer must open for a tel: link")
        assertEquals(DialpadActivity::class.java.name, started.component?.className)
        assertEquals("+491701234567", started.getStringExtra(DialpadActivity.EXTRA_NUMBER))
        controller.teardown()
    }

    @Test
    fun `a sip deep link dials the user part`() {
        val controller = launchMain(viewIntent(Uri.parse("sip:100@pbx.artmann.tech")))
        val started = shadowOf(controller.get()).nextStartedActivity
        assertEquals(DialpadActivity::class.java.name, started?.component?.className)
        assertEquals("100", started?.getStringExtra(DialpadActivity.EXTRA_NUMBER))
        controller.teardown()
    }

    @Test
    fun `a stack app link loads that path in the shell`() {
        val controller = launchMain(viewIntent(Uri.parse("https://pbx.artmann.tech/messages/42")))
        assertEquals(
            "https://pbx.artmann.tech/messages/42",
            shadowOf(controller.get().webSurface.currentWebView()).lastLoadedUrl,
        )
        controller.teardown()
    }

    @Test
    fun `a foreign deep link opens the home state only`() {
        val controller = launchMain(viewIntent(Uri.parse("https://evil.example/phish")))
        assertEquals(
            "https://pbx.artmann.tech",
            shadowOf(controller.get().webSurface.currentWebView()).lastLoadedUrl,
            "the origin root loads; the foreign link never renders",
        )
        assertNull(shadowOf(controller.get()).nextStartedActivity, "no dialer, no hand-off loop")
        controller.teardown()
    }

    @Test
    fun `a warm deep link routes through onNewIntent`() {
        val controller = launchMain()
        val activity = controller.get()
        while (shadowOf(activity).nextStartedActivity != null) {
            // drain launch-time side effects
        }
        // Full warm path (N017/N018 seam): setIntent + routing,
        // exactly what the protected override does past the
        // framework call.
        activity.onNewIntentDelegate(viewIntent(Uri.parse("https://pbx.artmann.tech/voicemail/7")))
        assertEquals(
            "https://pbx.artmann.tech/voicemail/7",
            activity.intent.dataString,
            "the activity intent must re-aim at the deep link",
        )
        assertEquals(
            "https://pbx.artmann.tech/voicemail/7",
            shadowOf(activity.webSurface.currentWebView()).lastLoadedUrl,
        )
        controller.teardown()
    }

    @Test
    fun `a warm tel link re-aims the intent and opens the dialer`() {
        val controller = launchMain()
        val activity = controller.get()
        while (shadowOf(activity).nextStartedActivity != null) {
            // drain launch-time side effects
        }
        activity.onNewIntentDelegate(viewIntent(Uri.parse("tel:+49170999")))
        assertEquals("tel:+49170999", activity.intent.dataString)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(DialpadActivity::class.java.name, started?.component?.className)
        assertEquals("+49170999", started?.getStringExtra(DialpadActivity.EXTRA_NUMBER))
        controller.teardown()
    }

    @Test
    fun `a tel link while a call is live opens the call screen not the dialer`() {
        CallEngine.onIncomingCall(
            SipEngine.IncomingInvite(
                callId = "link-live",
                fromUser = "100",
                fromDisplay = "Alice",
                sdp = "v=0",
                respondWith = { _, _ -> },
            ),
        )
        try {
            val controller = launchMain(viewIntent(Uri.parse("tel:+49170999")))
            val started = shadowOf(controller.get()).nextStartedActivity
            assertEquals(
                CallActivity::class.java.name,
                started?.component?.className,
                "single-call policy: a live line routes the link to the call screen",
            )
            assertNull(
                started?.getStringExtra(DialpadActivity.EXTRA_NUMBER),
                "the link's number must not seed a second dialer",
            )
            controller.teardown()
        } finally {
            CallEngine.cancelRinging()
        }
    }
}
