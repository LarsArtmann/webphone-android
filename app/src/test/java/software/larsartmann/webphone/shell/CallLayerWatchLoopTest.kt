package software.larsartmann.webphone.shell

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import software.larsartmann.webphone.App
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The session watcher's loop contract, driven through the REAL loop
 * ([CallLayer.sessionWatchLoop]) with a fake probe — never the live
 * stack (the meta-proof lesson). Two verdicts are pinned:
 *
 * 1. the probe LEADS the loop: the first verdict lands at wire time,
 *    not ten minutes in (a dead cookie discovered only at the first
 *    tick meant ten extra minutes of a lane that cannot ring);
 * 2. a DEAD probe tears the lane down through the loop itself —
 *    notice on the visible account channel, ledger SESSION_DEAD.
 */
@RunWith(RobolectricTestRunner::class)
class CallLayerWatchLoopTest {
    private val appContext = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetSeams() {
        WakeLane.report(WakeLaneState.UNKNOWN)
        CallLayer.deregisterPushDevice = { false }
        CallLayer.sessionWatchIntervalMs = 10 * 60 * 1000L
    }

    @After
    fun restoreSeams() {
        CallLayer.sessionProbe = { SessionApi.probeSession() }
        CallLayer.deregisterPushDevice = { SessionApi.deregisterPushDevice(it) }
    }

    @Test
    fun `the first probe lands at wire time - not after the first interval`() =
        runBlocking {
            var probes = 0
            CallLayer.sessionProbe = {
                probes += 1
                SessionApi.SessionProbe.ALIVE
            }
            CallLayer.sessionWatchIntervalMs = 60_000
            val job = launch { CallLayer.sessionWatchLoop(appContext, laneAlive = { true }) }
            withTimeout(5_000) {
                while (probes == 0) delay(25)
            }
            delay(200)
            assertEquals(
                1,
                probes,
                "the probe must lead the loop - one verdict at wire time, then the long sleep",
            )
            job.cancel()
        }

    @Test
    fun `a dead probe tears the lane down through the real loop`() =
        runBlocking {
            CallLayer.sessionProbe = { SessionApi.SessionProbe.DEAD }
            CallLayer.sessionWatchLoop(appContext, laneAlive = { true })

            assertEquals(WakeLaneState.SESSION_DEAD, WakeLane.state.value)
            val posted =
                shadowOf(appContext.getSystemService(NotificationManager::class.java)).allNotifications
            assertEquals(1, posted.size)
            assertEquals(App.CHANNEL_ACCOUNT, posted[0].channelId)
        }
}
