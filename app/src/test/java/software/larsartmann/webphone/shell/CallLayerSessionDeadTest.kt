package software.larsartmann.webphone.shell

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import software.larsartmann.webphone.App
import software.larsartmann.webphone.push.NtfySubscriptionService
import software.larsartmann.webphone.push.WakeLane
import software.larsartmann.webphone.push.WakeLaneState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The dead-session teardown's contract, driven directly through the
 * [CallLayer.onSessionDead] seam (no 10-minute watcher sleep, no
 * HTTP — the meta-proof lesson: drive seams, never wait on network
 * events under Robolectric). When the session probe names the session
 * DEAD, the user must be TOLD the ring lane is pausing (the silent
 * lane death was the undiagnosable no-ring failure) and the lane must
 * actually stand down: stop intent to the ntfy service, engine
 * teardown, ledger SESSION_DEAD.
 */
@RunWith(RobolectricTestRunner::class)
class CallLayerSessionDeadTest {
    @Test
    fun `a dead session notifies, stands the lane down, and reports session_dead`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        CallLayer.onSessionDead(appContext)

        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(notificationManager).allNotifications
        assertEquals(1, posted.size, "the user must be told the ring lane paused")
        assertEquals(
            App.CHANNEL_ACCOUNT,
            posted[0].channelId,
            "the one notice that must not be missed rides the VISIBLE account channel - " +
                "the min-importance push channel would swallow it",
        )
        assertNotNull(
            posted[0].contentIntent,
            "the notice must tap through into the shell, where the login card is one glance away",
        )

        val stopped = shadowOf(appContext as Application).nextStartedService
        assertNotNull(stopped, "the teardown must start the stop intent")
        assertEquals(NtfySubscriptionService.ACTION_STOP, stopped.action)

        assertEquals(
            WakeLaneState.SESSION_DEAD,
            WakeLane.state.value,
            "the ledger must say why the pill shows wake off",
        )
    }

    @Test
    fun `teardown is idempotent - a second dead session changes nothing`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        CallLayer.onSessionDead(appContext)
        CallLayer.onSessionDead(appContext)
        assertEquals(WakeLaneState.SESSION_DEAD, WakeLane.state.value)
    }
}
