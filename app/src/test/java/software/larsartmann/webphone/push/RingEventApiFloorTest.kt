package software.larsartmann.webphone.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The minSdk floor pin: the ring path must survive the OLDEST Android
 * this APK installs on (API 31). The suite's default SDK is 34, so an
 * API-34-only call left unguarded in the ring path is invisible to
 * every other test, and no emulator or phone in the train runs it
 * either. The first catch was `NotificationManager#canUseFullScreenIntent()`
 * (added in 34): on 31-33 it raised NoSuchMethodError exactly where the
 * incoming call was about to surface (found by Android Lint's NewApi,
 * 2026-11-24).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RingEventApiFloorTest {
    @Test
    fun `ring posts the full-screen heads-up on the API 31 floor`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.MANAGE_OWN_CALLS)

        RingEvent.route(app, "ring", "Alice", "call-1")

        val notifications =
            shadowOf(
                app.getSystemService(NotificationManager::class.java),
            ).allNotifications
        assertEquals(1, notifications.size, "the ring must still surface on API 31")
        assertNotNull(
            notifications[0].fullScreenIntent,
            "below API 34 the full-screen grant is unconditional: the heads-up must carry the intent",
        )
    }
}
