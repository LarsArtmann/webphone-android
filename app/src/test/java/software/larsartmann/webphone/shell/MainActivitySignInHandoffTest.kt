package software.larsartmann.webphone.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import software.larsartmann.webphone.shell.SessionApi.HandoffRedemption
import software.larsartmann.webphone.shell.SessionApi.HandoffSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The handoff's intake end-to-end inside the shell (Robolectric's
 * synchronous ActivityController, the DeepLinkIntakeTest discipline): a
 * return link enters through the exported VIEW intake — cold in onCreate,
 * warm in onNewIntent — and the shell redeems it, lands where the stack
 * says, and re-decides its wire. The redemption itself is a stand-in (a
 * unit test must not phone the live stack); everything around it is real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34])
class MainActivitySignInHandoffTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val origin = "https://pbx.artmann.tech"
    private val ticket = "t".repeat(43)
    private var clock = 1_700_000_000_000L
    private val redeemed = mutableListOf<String>()
    private var answer: (String) -> HandoffRedemption = { HandoffRedemption.Landed("/") }

    @Before
    fun reset() {
        CallLayer.wiringEnabled = false
        App.origin = origin
        SignInHandoff.nowMs = { clock }
        SignInHandoff.openBrowser = { _, _ -> true }
        SignInHandoff.probe = { HandoffSupport.UNSUPPORTED }
        // One classloader per test CLASS: a sibling's attempt and verdict
        // must not leak. A verdict for another origin reads as UNKNOWN.
        SignInAttempt.of(context)?.drop(context)
        SignInHandoff.teach("https://stale.invalid", HandoffSupport.UNSUPPORTED)
        redeemed.clear()
        answer = { HandoffRedemption.Landed("/") }
        MainActivity.handoffRedeemer = { presented ->
            redeemed += presented
            answer(presented)
        }
    }

    @After
    fun restoreTheRealSeam() {
        MainActivity.handoffRedeemer = { SessionApi.redeemSignInHandoff(it) }
    }

    /** The origin must be set BEFORE the drive: onCreate routes the cold
     *  link against it. */
    private fun launchMain(intent: Intent? = null): ActivityController<MainActivity> {
        App.origin = origin
        return if (intent == null) {
            Robolectric.buildActivity(MainActivity::class.java).setup()
        } else {
            Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        }
    }

    /** Synchronous teardown — see DeepLinkIntakeTest for why never
     *  ActivityScenario. */
    private fun ActivityController<MainActivity>.teardown() {
        pause().stop().destroy()
    }

    /** The redemption's blocking leg runs on IO; its continuation is
     *  posted back to the main thread — this one — so pumping the looper
     *  is what lets the shell finish. */
    private fun awaitOnMain(
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(condition(), "the shell never reached: $what")
    }

    private fun MainActivity.loadedUrl(): String? = shadowOf(webSurface.currentWebView()).lastLoadedUrl

    private fun openAttempt(): String {
        SignInHandoff.begin(context, signInEntryUri(origin))
        return assertNotNull(SignInAttempt.of(context), "the hand-off opens an attempt").nonce
    }

    private fun returnLink(
        nonce: String,
        ticket: String = this.ticket,
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("cloud.larsartmann.webphone://auth/finish?ticket=$ticket&app_state=$nonce")
        }

    @Test
    fun `a cold return link for the open attempt is redeemed and the shell lands signed in`() {
        val nonce = openAttempt()
        WakeLane.report(WakeLaneState.ARMED)

        val controller = launchMain(returnLink(nonce))
        try {
            awaitOnMain("the island root") { redeemed.isNotEmpty() && controller.get().loadedUrl() == "$origin/" }

            assertEquals(listOf(ticket), redeemed, "the ticket is presented exactly once")
            assertNull(SignInAttempt.of(context), "the attempt is spent")
            assertEquals(
                WakeLaneState.UNKNOWN,
                WakeLane.state.value,
                "the wire is re-decided from the cookie up — no stale lane verdict survives",
            )
        } finally {
            controller.teardown()
        }
    }

    @Test
    fun `a warm return link routes through the new-intent seam`() {
        val controller = launchMain()
        try {
            assertEquals(origin, controller.get().loadedUrl(), "the shell starts on its origin root")
            val nonce = openAttempt()
            val link = returnLink(nonce)

            controller.get().onNewIntentDelegate(link)

            awaitOnMain("the island root") { redeemed.isNotEmpty() && controller.get().loadedUrl() == "$origin/" }
            assertEquals(listOf(ticket), redeemed)
            assertEquals(link.dataString, controller.get().intent.dataString, "the activity intent re-aims at the link")
        } finally {
            controller.teardown()
        }
    }

    @Test
    fun `a stale ticket lands the shell on the stack's own refusal sentence`() {
        answer = { HandoffRedemption.Landed("/login?sso_error=stale") }
        val controller = launchMain(returnLink(openAttempt()))
        try {
            val refusal = "$origin/login?sso_error=stale"
            awaitOnMain("the login page with the stack's sentence") {
                redeemed.isNotEmpty() && controller.get().loadedUrl() == refusal
            }
        } finally {
            controller.teardown()
        }
    }

    @Test
    fun `no usable answer returns to the origin and says so`() {
        answer = { HandoffRedemption.Failed }
        val controller = launchMain(returnLink(openAttempt()))
        try {
            awaitOnMain("the origin root") { redeemed.isNotEmpty() && controller.get().loadedUrl() == origin }
            assertEquals(
                context.getString(R.string.signin_handoff_failed),
                ShadowToast.getTextOfLatestToast(),
                "silence after a sign-in the user just completed is the one thing this surface must never show",
            )
        } finally {
            controller.teardown()
        }
    }

    @Test
    fun `a forged return link never reaches the redemption`() {
        openAttempt()
        val forged = "n".repeat(43)

        val controller = launchMain(returnLink(forged))
        try {
            Thread.sleep(200)
            assertTrue(redeemed.isEmpty(), "a link that echoes another attempt's nonce is dropped before any network")
            assertEquals(origin, controller.get().loadedUrl(), "the shell opens on its home state")
            assertNotNull(SignInAttempt.of(context), "the forged link does not void the dance in flight")
        } finally {
            controller.teardown()
        }
    }

    @Test
    fun `a return link after the attempt expired is ignored`() {
        val nonce = openAttempt()
        clock += 10 * 60 * 1000L + 1

        val controller = launchMain(returnLink(nonce))
        try {
            Thread.sleep(200)
            assertTrue(redeemed.isEmpty())
            assertNull(SignInAttempt.of(context), "the spent attempt is dropped")
        } finally {
            controller.teardown()
        }
    }
}
