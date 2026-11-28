package software.larsartmann.webphone.shell

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import software.larsartmann.webphone.App
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.shell.SessionApi.HandoffSupport
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sign-in handoff's own seams (Robolectric for Uri, prefs and the
 * package manager; the wire and the browser are stand-ins): the attempt
 * lifecycle (open, echo, single use, TTL, stack), the nonce fence against
 * a forged return link, the per-origin capability verdict, the real
 * browser launcher, and the manifest pairing of the return address.
 *
 * The attempt has NO in-memory form — a return link is always matched
 * against prefs — so a process kill during the dance (the shell sits
 * behind the browser) costs the user nothing; that is why every test
 * here goes through begin/takeTicket and never pokes a field.
 *
 * The shell stays unwired: this class names MainActivity (the manifest
 * pin), and the wire path would phone the live stack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34])
class SignInHandoffTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val origin = "https://pbx.artmann.tech"
    private val otherOrigin = "https://pbx.example.org"
    private var clock = 1_700_000_000_000L
    private val opened = mutableListOf<Uri>()

    @Before
    fun reset() {
        CallLayer.wiringEnabled = false
        App.origin = origin
        SignInHandoff.nowMs = { clock }
        SignInHandoff.openBrowser = { _, url ->
            opened += url
            true
        }
        SignInHandoff.probe = { HandoffSupport.UNSUPPORTED }
        // One classloader per test CLASS: a sibling's attempt and verdict
        // must not leak. A verdict for another origin reads as UNKNOWN.
        SignInAttempt.of(context)?.drop(context)
        SignInHandoff.teach("https://stale.invalid", HandoffSupport.UNSUPPORTED)
    }

    @After
    fun restoreSeams() {
        SignInHandoff.openBrowser = SignInHandoff::openInBrowser
    }

    private fun entry(): Uri = signInEntryUri(origin)

    /** Open an attempt and answer its nonce. */
    private fun openAttempt(): String {
        assertTrue(SignInHandoff.begin(context, entry()), "a browser stand-in always takes the entry")
        return assertNotNull(SignInAttempt.of(context), "the hand-off opens an attempt").nonce
    }

    @Test
    fun `begin hands the entry to the browser marked with a fresh nonce`() {
        val nonce = openAttempt()

        val launch = opened.single()
        assertEquals("https", launch.scheme)
        assertEquals("pbx.artmann.tech", launch.host)
        assertEquals(SessionApi.SIGN_IN_ENTRY_PATH, launch.path)
        assertEquals(HANDOFF_SHELL, launch.getQueryParameter(SessionApi.HANDOFF_PARAM_SHELL))
        assertEquals(nonce, launch.getQueryParameter(HANDOFF_PARAM_APP_STATE))
        assertTrue(HANDOFF_TOKEN_SHAPE.matches(nonce), "the nonce is inside the stack's accepted shape")
        assertEquals(SignInAttempt(nonce, origin, clock), SignInAttempt.of(context))
    }

    @Test
    fun `begin keeps the entry's own query and supersedes an open attempt`() {
        val withQuery = Uri.parse("$origin${SessionApi.SIGN_IN_ENTRY_PATH}?next=%2Fmessages")
        SignInHandoff.begin(context, withQuery)
        val first = SignInAttempt.of(context)!!.nonce
        SignInHandoff.begin(context, withQuery)
        val second = SignInAttempt.of(context)!!.nonce

        assertNotEquals(first, second, "every attempt opens with its own nonce")
        val launch = opened.last()
        assertEquals("/messages", launch.getQueryParameter("next"), "the entry's own query survives")
        assertEquals(second, launch.getQueryParameter(HANDOFF_PARAM_APP_STATE))
    }

    @Test
    fun `a return link is matched against the attempt on disk and yields its ticket exactly once`() {
        val nonce = openAttempt()
        val ticket = "t".repeat(43)

        assertEquals(ticket, SignInHandoff.takeTicket(context, ticket, nonce))
        assertNull(
            SignInHandoff.takeTicket(context, ticket, nonce),
            "single use: the ticket dies with its attempt, redeemed or not",
        )
        assertNull(SignInAttempt.of(context), "the attempt is closed by the redemption")
    }

    @Test
    fun `a forged return link never yields a ticket and never voids the dance`() {
        val nonce = openAttempt()
        val ticket = "t".repeat(43)

        assertNull(SignInHandoff.takeTicket(context, ticket, "n".repeat(43)), "a foreign nonce")
        assertNull(SignInHandoff.takeTicket(context, ticket, null), "no nonce")
        assertNotNull(SignInAttempt.of(context), "a forged link leaves the open attempt alone")
        assertEquals(ticket, SignInHandoff.takeTicket(context, ticket, nonce), "the real link still lands afterwards")
    }

    @Test
    fun `a return link without an attempt is ignored`() {
        assertNull(SignInHandoff.takeTicket(context, "t".repeat(43), "n".repeat(43)))
        assertTrue(opened.isEmpty(), "nothing is opened by a link nobody asked for")
    }

    @Test
    fun `an attempt dies with its stack`() {
        val nonce = openAttempt()
        App.origin = otherOrigin

        assertNull(
            SignInHandoff.takeTicket(context, "t".repeat(43), nonce),
            "never redeem on a stack switched away from",
        )
        assertNull(SignInAttempt.of(context), "the stale attempt is dropped")
    }

    @Test
    fun `an attempt outlives the stack's own attempt cookies by nothing`() {
        val nonce = openAttempt()
        val ticket = "t".repeat(43)

        clock += 10 * 60 * 1000L
        assertEquals(ticket, SignInHandoff.takeTicket(context, ticket, nonce), "the last instant inside the TTL")

        openAttempt()
        clock += 10 * 60 * 1000L + 1
        assertNull(SignInHandoff.takeTicket(context, ticket, SignInAttempt.of(context)!!.nonce), "one instant past it")
        assertNull(SignInAttempt.of(context))
    }

    @Test
    fun `the attempt verdict is pure`() {
        val attempt = SignInAttempt("n".repeat(43), origin, issuedAtMs = 1_000L)
        assertEquals(ReturnVerdict.ACCEPTED, attempt.verdictFor(origin, "n".repeat(43), nowMs = 1_000L))
        assertEquals(
            ReturnVerdict.ACCEPTED,
            attempt.verdictFor(origin, "n".repeat(43), nowMs = 1_000L + 10 * 60 * 1000L),
            "exactly ten minutes is still inside",
        )
        assertEquals(
            ReturnVerdict.EXPIRED,
            attempt.verdictFor(origin, "n".repeat(43), nowMs = 1_000L + 10 * 60 * 1000L + 1),
        )
        assertEquals(ReturnVerdict.OTHER_STACK, attempt.verdictFor(otherOrigin, "n".repeat(43), 1_000L))
        assertEquals(ReturnVerdict.OTHER_ATTEMPT, attempt.verdictFor(origin, "x".repeat(43), 1_000L))
        assertEquals(ReturnVerdict.OTHER_ATTEMPT, attempt.verdictFor(origin, null, 1_000L))
    }

    @Test
    fun `support is learned per origin and can be awaited`() =
        runBlocking {
            SignInHandoff.probe = { HandoffSupport.SUPPORTED }
            assertNull(SignInHandoff.support(), "nothing is known before the first ask")

            SignInHandoff.learnSupport()
            assertEquals(HandoffSupport.SUPPORTED, withTimeout(5_000) { SignInHandoff.awaitSupport() })
            assertEquals(HandoffSupport.SUPPORTED, SignInHandoff.support())

            App.origin = otherOrigin
            assertNull(
                SignInHandoff.support(),
                "a verdict learned on one stack says nothing about another the user switched to",
            )
        }

    @Test
    fun `learning is single-flight and a throwing probe is an unreachable stack`() =
        runBlocking {
            val release = CountDownLatch(1)
            var probes = 0
            SignInHandoff.probe = {
                probes++
                release.await(5, TimeUnit.SECONDS)
                throw IllegalStateException("transport died")
            }

            SignInHandoff.learnSupport()
            SignInHandoff.learnSupport()
            SignInHandoff.learnSupport()
            release.countDown()

            assertEquals(HandoffSupport.UNREACHABLE, withTimeout(5_000) { SignInHandoff.awaitSupport() })
            assertEquals(1, probes, "concurrent asks share one probe")
        }

    @Test
    fun `a signed-out wire attempt learns the stack's handoff support`() =
        runBlocking {
            SignInHandoff.probe = { HandoffSupport.SUPPORTED }

            CallLayer.onSignedOut()

            assertEquals(CallEngine.Registration.OFF, CallEngine.registration.value.status, "the badge says signed out")
            assertEquals(HandoffSupport.SUPPORTED, withTimeout(5_000) { SignInHandoff.awaitSupport() })
        }

    @Test
    fun `the real launcher opens the entry as a browser view`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            assertTrue(SignInHandoff.begin(activity, entry()), "Robolectric accepts any activity start")

            val started = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, started?.action)
            assertEquals(
                setOf(Intent.CATEGORY_BROWSABLE),
                started?.categories,
                "browsers only claim VIEW intents with the BROWSABLE category",
            )
            assertEquals(SessionApi.SIGN_IN_ENTRY_PATH, started?.data?.path)
            assertNotNull(SignInAttempt.of(activity))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the real launcher reports a device without any browser`() {
        // Robolectric accepts every activity start, so the device is
        // modeled at the seam a real one fails at: nothing resolves, and
        // the framework throws.
        val deviceWithoutBrowser =
            object : ContextWrapper(context) {
                override fun startActivity(intent: Intent): Unit =
                    throw ActivityNotFoundException("no browser on this device")
            }

        assertFalse(SignInHandoff.begin(deviceWithoutBrowser, entry()), "no browser took it")
        assertNull(SignInAttempt.of(context), "a hand-off that never happened opens no attempt")
    }

    @Test
    fun `the real launcher falls back to a plain browser tab when no Custom Tab answers`() {
        val plainLaunches = mutableListOf<Intent>()
        val onlyPlainBrowser =
            object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) {
                    if (intent.hasExtra(CustomTabsIntent.EXTRA_SESSION)) {
                        throw ActivityNotFoundException("no Custom Tabs provider")
                    }
                    plainLaunches += intent
                }
            }

        assertTrue(SignInHandoff.begin(onlyPlainBrowser, entry()), "the plain tab takes the entry")

        val plain = plainLaunches.single()
        assertEquals(Intent.ACTION_VIEW, plain.action)
        assertTrue(Intent.CATEGORY_BROWSABLE in plain.categories, "a browser only claims BROWSABLE view intents")
        assertEquals(SessionApi.SIGN_IN_ENTRY_PATH, plain.data?.path)
        assertNotNull(SignInAttempt.of(context))
    }

    @Test
    fun `the manifest routes the return address to the shell and nothing else`() {
        val facts = "ticket=${"t".repeat(43)}&app_state=${"n".repeat(43)}"

        fun handlersOf(link: String): List<String> =
            context
                .packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE),
                    PackageManager.MATCH_DEFAULT_ONLY,
                ).map { it.activityInfo.name }

        assertEquals(
            listOf(MainActivity::class.java.name),
            handlersOf("cloud.larsartmann.webphone://auth/finish?$facts"),
            "the pinned return address reaches the one exported intake",
        )
        assertTrue(handlersOf("cloud.larsartmann.webphone://evil/finish?$facts").isEmpty(), "another host")
        assertTrue(handlersOf("cloud.larsartmann.webphone://auth/other?$facts").isEmpty(), "another path")
    }
}
