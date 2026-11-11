package software.larsartmann.webphone.shell

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import software.larsartmann.webphone.R
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wake-off detail sheet: the pill's tap-through surface that says
 * WHY the lane is off, WHEN the stack was last asked, and offers the
 * re-wire. The pure text mappings are pinned file-level; the
 * interaction pin drives the REAL pill click → sheet → Retry path
 * (Robolectric synchronous controller — never ActivityScenario, see
 * DeepLinkIntakeTest's class KDoc for the destroy-wedge history).
 *
 * The Retry pin's assertions run against the production rewire path
 * with `wiringEnabled = false`: shutdown returns the ledger to
 * UNKNOWN and ensureWired no-ops, so the pin observes the wiring
 * CONTRACT (teardown verdict) without any wire leaving the JVM.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityWakeDetailTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun keepTheShellUnwired() {
        CallLayer.wiringEnabled = false
        WakeLane.nowMs = { 1_700_000_000_000L }
    }

    @After
    fun restoreTheSeams() {
        WakeLane.nowMs = { System.currentTimeMillis() }
        WakeLane.report(WakeLaneState.UNKNOWN)
    }

    /** Synchronous teardown — the only shape that survives this
     *  activity (see DeepLinkIntakeTest). */
    private fun ActivityController<MainActivity>.teardown() {
        pause().stop().destroy()
    }

    @Test
    fun `the detail body maps every verdict to its sentence`() {
        assertEquals(
            context.getString(R.string.wake_detail_unknown),
            wakeDetailBody(context, WakeLaneState.UNKNOWN),
            "UNKNOWN describes the unwired state (the stamp line owns 'not checked')",
        )
        assertEquals(
            context.getString(R.string.wake_starting),
            wakeDetailBody(context, WakeLaneState.STARTING),
        )
        assertEquals(
            context.getString(R.string.wake_detail_armed),
            wakeDetailBody(context, WakeLaneState.ARMED),
        )
        assertEquals(
            context.getString(R.string.wake_reason_refused_seam_off),
            wakeDetailBody(context, WakeLaneState.REFUSED_SEAM_OFF),
        )
        assertEquals(
            context.getString(R.string.wake_reason_refused_invalid),
            wakeDetailBody(context, WakeLaneState.REFUSED_INVALID),
        )
        assertEquals(
            context.getString(R.string.wake_reason_refused_auth),
            wakeDetailBody(context, WakeLaneState.REFUSED_AUTH),
        )
        assertEquals(
            context.getString(R.string.wake_reason_refused_other),
            wakeDetailBody(context, WakeLaneState.REFUSED_OTHER),
        )
        assertEquals(
            context.getString(R.string.wake_reason_session_dead),
            wakeDetailBody(context, WakeLaneState.SESSION_DEAD),
        )
        assertEquals(
            context.getString(R.string.wake_reason_disconnected),
            wakeDetailBody(context, WakeLaneState.DISCONNECTED),
        )
    }

    @Test
    fun `the last-checked line admits never and formats the stamp`() {
        assertEquals(
            context.getString(R.string.wake_never_checked),
            wakeLastCheckedLine(context, 0L),
            "no probe yet — the sheet must not print a fake time",
        )
        val stamped = wakeLastCheckedLine(context, 1_700_000_000_000L)
        assertTrue(
            stamped.startsWith(context.getString(R.string.wake_last_checked, "")),
            "a probed lane carries the 'Last checked:' prefix: $stamped",
        )
        assertTrue(
            !stamped.contains(context.getString(R.string.wake_never_checked)),
            "a stamped lane is never 'not checked yet': $stamped",
        )
    }

    @Test
    fun `pill tap opens the sheet and retry tears the lane down`() {
        WakeLane.reportProbe()
        WakeLane.report(WakeLaneState.REFUSED_SEAM_OFF)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()

        activity.get().linePill.performClick()
        assertEquals(View.VISIBLE, activity.get().wakeDetail.visibility, "the pill tap opens the sheet")
        assertEquals(
            wakeDetailBody(context, WakeLaneState.REFUSED_SEAM_OFF),
            activity
                .get()
                .wakeDetailBodyText.text
                .toString(),
            "the sheet carries the refusal's full sentence",
        )
        assertEquals(
            wakeLastCheckedLine(context, WakeLane.lastProbeAtMs),
            activity
                .get()
                .wakeDetailLastChecked.text
                .toString(),
            "the sheet stamps the probe time",
        )
        assertEquals(View.VISIBLE, activity.get().wakeDetailRetry.visibility, "a refused lane offers Retry")

        activity.get().wakeDetailRetry.performClick()
        assertEquals(View.GONE, activity.get().wakeDetail.visibility, "retry dismisses the sheet")
        assertEquals(
            WakeLaneState.UNKNOWN,
            WakeLane.state.value,
            "retry tore the refused lane down (rewire → shutdown → UNKNOWN; " +
                "ensureWired no-ops with wiring off)",
        )

        activity.teardown()
    }

    @Test
    fun `an armed lane opens the sheet without a retry button`() {
        WakeLane.report(WakeLaneState.ARMED)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()

        activity.get().linePill.performClick()
        assertEquals(View.VISIBLE, activity.get().wakeDetail.visibility, "ARMED still opens the sheet")
        assertEquals(
            context.getString(R.string.wake_detail_armed),
            activity
                .get()
                .wakeDetailBodyText.text
                .toString(),
        )
        assertEquals(
            View.GONE,
            activity.get().wakeDetailRetry.visibility,
            "re-wiring a WORKING lane is not a user task",
        )

        activity.teardown()
    }
}
