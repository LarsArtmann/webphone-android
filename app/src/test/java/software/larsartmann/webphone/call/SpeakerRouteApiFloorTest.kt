package software.larsartmann.webphone.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import software.larsartmann.webphone.sip.SipEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The minSdk floor pin for the speaker route. The communication-device
 * pair it rides (`setCommunicationDevice` /
 * `clearCommunicationDevice`) arrived in API 31, exactly this APK's
 * floor, so nothing below it exists to guard against; what the pin
 * holds is that the route still behaves on the OLDEST Android the APK
 * installs on. The suite's default SDK is 34, so a path that only
 * works there is invisible to `SpeakerRouteTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SpeakerRouteApiFloorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    @Test
    fun `the speaker route toggles and is released by the call on the API 31 floor`() {
        val speaker =
            AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()
        shadowOf(audio).addAvailableCommunicationDevice(speaker, true)
        SpeakerRoute.release()
        assertFalse(SpeakerRoute.isActive(context), "no request stands before the first tap")

        SpeakerRoute.toggle(context)

        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, audio.communicationDevice?.type)
        assertTrue(SpeakerRoute.isActive(context))

        SpeakerRoute.toggle(context)

        assertNull(audio.communicationDevice, "off hands routing back to the platform default")
        assertFalse(SpeakerRoute.isActive(context))

        SpeakerRoute.toggle(context)
        assertTrue(SpeakerRoute.isActive(context))
        CallEngine.onIncomingCall(
            SipEngine.IncomingInvite(
                callId = "floor-31",
                fromUser = "100",
                fromDisplay = "Alice",
                sdp = "v=0",
                respondWith = { _, _ -> },
            ),
        )

        CallEngine.hangUp()

        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
        assertNull(
            audio.communicationDevice,
            "the call's end must release the speaker on the floor as on every later API",
        )
    }
}
