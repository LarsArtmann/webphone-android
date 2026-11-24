package software.larsartmann.webphone.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log

/**
 * The call's speaker route: the one owner of the AudioManager
 * communication-device request a live call makes. A self-managed
 * Telecom connection leaves audio routing to the app, and a request
 * made with [AudioManager.setCommunicationDevice] OUTLIVES the call
 * that made it: it stands until it is cleared or the process dies.
 * Unreleased, one speakerphone tap would put every later call of the
 * process on the loudspeaker (found by Android Lint's
 * SetAndClearCommunicationDevice, 2026-11-24).
 *
 * Two contracts, both pinned by `SpeakerRouteTest`:
 *
 * - "Speaker off" CLEARS the request instead of forcing the earpiece,
 *   so the platform's default policy routes again (a connected headset
 *   wins, the way the platform dialer behaves).
 * - [CallEngine] calls [release] on every end-of-call path, whichever
 *   surface is alive or gone: the route belongs to the call, never to
 *   the activity that toggled it.
 *
 * The API is the communication-device pair (API 31+, the minSdk — no
 * deprecated `isSpeakerphoneOn`). Audio FOCUS is NOT touched here or
 * in CallService: this is a self-managed Telecom call and the system
 * granted focus when the connection went active; a second request
 * would fight the very integration the platform decision bought.
 */
internal object SpeakerRoute {
    private const val TAG = "SpeakerRoute"

    private val lock = Any()

    /** The manager this call's request stands on; null while the
     *  platform default is in charge. Only a request made HERE is ever
     *  cleared, so a default route the platform chose is left alone. */
    private var requestedOn: AudioManager? = null

    /** Whether the loudspeaker is the live communication device. Read
     *  from the platform on every call (never cached): the fact must
     *  survive a request released by the call's end, not a toggle. */
    fun isActive(context: Context): Boolean =
        audioManager(context)?.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

    /** Flip the speaker for the live call. On = request the built-in
     *  speaker (a no-op when the device has none); off = hand routing
     *  back to the platform default. */
    fun toggle(context: Context) {
        val audio = audioManager(context) ?: return
        synchronized(lock) {
            if (audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                releaseLocked()
                return
            }
            val speaker =
                audio.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } ?: return
            if (audio.setCommunicationDevice(speaker)) {
                requestedOn = audio
            }
        }
    }

    /** Drop this call's speaker request. Safe to call on any thread and
     *  any number of times; a call that never asked for the speaker
     *  leaves the route untouched. */
    fun release() {
        synchronized(lock) { releaseLocked() }
    }

    private fun releaseLocked() {
        val audio = requestedOn ?: return
        requestedOn = null
        runCatching { audio.clearCommunicationDevice() }
            .onFailure { Log.w(TAG, "speaker route release failed: ${it.message}") }
    }

    private fun audioManager(context: Context): AudioManager? = context.getSystemService(AudioManager::class.java)
}
