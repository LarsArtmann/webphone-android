package software.larsartmann.webphone.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.AudioDeviceInfoBuilder
import software.larsartmann.webphone.sip.SipEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The speaker route is the CALL's, not the process's: a
 * communication-device request outlives the call that made it, so the
 * contract under pin is that the call's end releases it while a route
 * the app never requested survives untouched.
 */
@RunWith(RobolectricTestRunner::class)
class SpeakerRouteTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    private fun addCommunicationDevice(type: Int): AudioDeviceInfo =
        AudioDeviceInfoBuilder.newBuilder().setType(type).build().also {
            shadowOf(audio).addAvailableCommunicationDevice(it, true)
        }

    @Before
    fun releaseRoute() {
        SpeakerRoute.release()
    }

    @Test
    fun `speaker on requests the loudspeaker, off hands routing back to the platform`() {
        addCommunicationDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        assertFalse(SpeakerRoute.isActive(context), "no request stands before the first tap")

        SpeakerRoute.toggle(context)

        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, audio.communicationDevice?.type)
        assertTrue(SpeakerRoute.isActive(context))

        SpeakerRoute.toggle(context)

        assertNull(audio.communicationDevice, "speaker off clears the request, it never forces the earpiece")
        assertFalse(SpeakerRoute.isActive(context))
    }

    @Test
    fun `a device without a loudspeaker ignores the tap`() {
        addCommunicationDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)

        SpeakerRoute.toggle(context)

        assertNull(audio.communicationDevice, "nothing to request, so nothing was requested")
        assertFalse(SpeakerRoute.isActive(context))
    }

    @Test
    fun `ending the call releases the speaker route`() {
        addCommunicationDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        SpeakerRoute.toggle(context)
        assertTrue(SpeakerRoute.isActive(context))
        CallEngine.onIncomingCall(ringing("speaker-1"))

        CallEngine.hangUp()

        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
        assertNull(
            audio.communicationDevice,
            "an unreleased request would put every later call of the process on the loudspeaker",
        )
        assertFalse(SpeakerRoute.isActive(context))
    }

    @Test
    fun `a declined ring releases the route too - every end path, not only the hang-up button`() {
        addCommunicationDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        SpeakerRoute.toggle(context)
        CallEngine.onIncomingCall(ringing("speaker-2"))

        CallEngine.decline()

        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
        assertNull(audio.communicationDevice)
    }

    @Test
    fun `a route the app never requested survives the call`() {
        val earpiece = addCommunicationDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        assertTrue(audio.setCommunicationDevice(earpiece), "the platform default, set outside the app's request")
        CallEngine.onIncomingCall(ringing("speaker-3"))

        CallEngine.hangUp()

        assertEquals(CallEngine.Phase.ENDED, CallEngine.state.value.phase)
        assertEquals(
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            audio.communicationDevice?.type,
            "release clears the app's own request only; the platform's route is not ours to drop",
        )
    }

    private fun ringing(callId: String): SipEngine.IncomingInvite =
        SipEngine.IncomingInvite(
            callId = callId,
            fromUser = "100",
            fromDisplay = "Alice",
            sdp = "v=0",
            respondWith = { _, _ -> },
        )
}
