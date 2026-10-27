package software.larsartmann.webphone.shell

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import software.larsartmann.webphone.App
import software.larsartmann.webphone.call.DialpadActivity
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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeepLinkIntakeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun viewIntent(uri: Uri): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
        }

    @Test
    fun `a tel deep link cold-starts the dialer prefilled`() {
        ActivityScenario.launch<MainActivity>(viewIntent(Uri.parse("tel:+491701234567"))).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = "https://pbx.artmann.tech"
                val started = shadowOf(activity).nextStartedActivity
                assertNotNull(started, "the dialer must open for a tel: link")
                assertEquals(DialpadActivity::class.java.name, started.component?.className)
                assertEquals("+491701234567", started.getStringExtra(DialpadActivity.EXTRA_NUMBER))
            }
        }
    }

    @Test
    fun `a sip deep link dials the user part`() {
        ActivityScenario.launch<MainActivity>(viewIntent(Uri.parse("sip:100@pbx.artmann.tech"))).use { scenario ->
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                assertEquals(DialpadActivity::class.java.name, started?.component?.className)
                assertEquals("100", started?.getStringExtra(DialpadActivity.EXTRA_NUMBER))
            }
        }
    }

    @Test
    fun `a stack app link loads that path in the shell`() {
        ActivityScenario.launch<MainActivity>(
            viewIntent(Uri.parse("https://pbx.artmann.tech/messages/42")),
        ).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = "https://pbx.artmann.tech"
                assertEquals(
                    "https://pbx.artmann.tech/messages/42",
                    shadowOf(activity.webSurface.currentWebView()).lastLoadedUrl,
                )
            }
        }
    }

    @Test
    fun `a foreign deep link opens the home state only`() {
        ActivityScenario.launch<MainActivity>(viewIntent(Uri.parse("https://evil.example/phish"))).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = "https://pbx.artmann.tech"
                assertEquals(
                    "https://pbx.artmann.tech",
                    shadowOf(activity.webSurface.currentWebView()).lastLoadedUrl,
                    "the origin root loads; the foreign link never renders",
                )
                assertNull(shadowOf(activity).nextStartedActivity, "no dialer, no hand-off loop")
            }
        }
    }

    @Test
    fun `a warm deep link routes through onNewIntent`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = "https://pbx.artmann.tech"
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
            }
        }
    }

    @Test
    fun `a warm tel link re-aims the intent and opens the dialer`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                App.origin = "https://pbx.artmann.tech"
                while (shadowOf(activity).nextStartedActivity != null) {
                    // drain launch-time side effects
                }
                activity.onNewIntentDelegate(viewIntent(Uri.parse("tel:+49170999")))
                assertEquals("tel:+49170999", activity.intent.dataString)
                val started = shadowOf(activity).nextStartedActivity
                assertEquals(DialpadActivity::class.java.name, started?.component?.className)
                assertEquals("+49170999", started?.getStringExtra(DialpadActivity.EXTRA_NUMBER))
            }
        }
    }
}
