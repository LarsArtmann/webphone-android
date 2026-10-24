package software.larsartmann.webphone.call

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import software.larsartmann.webphone.R
import software.larsartmann.webphone.push.RingEvent

/**
 * The in-call surface: launched by the full-screen intent (incoming)
 * or the dialer entry (outgoing), it renders the CallEngine state and
 * owns the call controls. v0.1 renders programmatically — the
 * CallStyle-composed notification arrives with the first device
 * train, this UI is the honest working surface.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var partyView: TextView
    private lateinit var controls: LinearLayout

    private var answerWhenMicGranted = false

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && answerWhenMicGranted) {
                answerWhenMicGranted = false
                CallEngine.answer()
                CallService.start(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply { textSize = 22f }
        partyView = TextView(this).apply { textSize = 32f }
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(partyView)
            addView(statusView)
            addView(controls)
        }
        setContentView(root)

        partyView.text = intent.getStringExtra(RingEvent.EXTRA_FROM) ?: getString(R.string.unknown_caller)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallEngine.state.collect { render(it) }
            }
        }
    }

    /** The ONLY answer path on this surface: an explicit Answer tap.
     *  The full-screen intent merely opens this screen — a pocket-wake
     *  must never pick up the call by itself (the v0.1 auto-answer
     *  defect). The Telecom/BT answer path bypasses this method and is
     *  equally explicit (Connection.onAnswer). */
    private fun answerCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            answerWhenMicGranted = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        CallEngine.answer()
        CallService.start(this)
    }

    private fun render(state: CallEngine.CallState) {
        statusView.text = when (state.phase) {
            CallEngine.Phase.IDLE -> ""
            CallEngine.Phase.DIALLING -> getString(R.string.dialing)
            CallEngine.Phase.RINGING_IN -> getString(R.string.incoming_call)
            CallEngine.Phase.ACTIVE -> if (state.muted) {
                getString(R.string.status_muted)
            } else {
                getString(R.string.status_active)
            }
            CallEngine.Phase.HELD -> getString(R.string.hold)
            CallEngine.Phase.ENDED -> getString(R.string.status_ended)
        }
        if (state.remoteParty.isNotEmpty()) partyView.text = state.remoteParty

        controls.removeAllViews()
        when (state.phase) {
            CallEngine.Phase.RINGING_IN -> {
                controls.addView(button(getString(R.string.answer)) { answerCall() })
                controls.addView(button(getString(R.string.decline)) { CallEngine.decline(); finish() })
            }
            CallEngine.Phase.DIALLING -> {
                controls.addView(button(getString(R.string.hang_up)) {
                    CallEngine.hangUp()
                    CallService.stop(this)
                    finish()
                })
            }
            CallEngine.Phase.ACTIVE, CallEngine.Phase.HELD -> {
                controls.addView(button(getString(R.string.mute)) { CallEngine.toggleMute() })
                controls.addView(button(getString(R.string.speaker)) { toggleSpeaker() })
                controls.addView(button(getString(R.string.hold)) { CallEngine.toggleHold() })
                dtmfRow()
                controls.addView(button(getString(R.string.hang_up)) {
                    CallEngine.hangUp()
                    CallService.stop(this)
                    finish()
                })
            }
            CallEngine.Phase.ENDED -> {
                controls.addView(button(getString(R.string.close)) { finish() })
            }
            else -> Unit
        }
        if (state.phase == CallEngine.Phase.ENDED) {
            CallService.stop(this)
        }
    }

    /** Audio output routing: the speakerpiece ⇄ built-in speaker
     *  switch rides AudioManager's communication-device API (API 31+
     *  — no deprecated isSpeakerphoneOn). Audio FOCUS is NOT touched
     *  here or in CallService: this is a self-managed Telecom call —
     *  the system granted focus when the connection went active; a
     *  second request would fight the very integration the platform
     *  decision bought. */
    private fun toggleSpeaker(): Boolean {
        val audio = getSystemService(AudioManager::class.java) ?: return false
        val speakingNow = audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val wanted = if (speakingNow) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        } else {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        val device = audio.availableCommunicationDevices.firstOrNull { it.type == wanted } ?: return false
        return audio.setCommunicationDevice(device)
    }

    private fun dtmfRow() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        "123456789*0#".forEach { digit ->
            row.addView(
                Button(this).apply {
                    text = digit.toString()
                    setOnClickListener { CallEngine.sendDtmf(digit) }
                    minimumWidth = touchTarget()
                    minimumHeight = touchTarget()
                },
            )
        }
        controls.addView(row)
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            minimumWidth = touchTarget()
            minimumHeight = touchTarget()
        }

    /** The accessibility floor: 48dp square touch targets (Android's
     *  minimum recommended for touch). */
    private fun touchTarget(): Int =
        (48 * resources.displayMetrics.density).toInt()
}
