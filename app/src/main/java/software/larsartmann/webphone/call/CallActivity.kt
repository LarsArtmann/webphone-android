package software.larsartmann.webphone.call

import android.Manifest
import android.content.pm.PackageManager
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

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCall()
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

        partyView.text = intent.getStringExtra(RingEvent.EXTRA_FROM) ?: "Unknown"

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallEngine.state.collect { render(it) }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCall()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startCall() {
        when (CallEngine.state.value.phase) {
            CallEngine.Phase.RINGING_IN -> CallEngine.answer()
            else -> Unit
        }
        CallService.start(this)
    }

    private fun render(state: CallEngine.CallState) {
        statusView.text = when (state.phase) {
            CallEngine.Phase.IDLE -> ""
            CallEngine.Phase.DIALLING -> getString(R.string.channel_calls).let { "…" }
            CallEngine.Phase.RINGING_IN -> getString(R.string.incoming_call)
            CallEngine.Phase.ACTIVE -> if (state.muted) "Muted" else "Active"
            CallEngine.Phase.HELD -> getString(R.string.hold)
            CallEngine.Phase.ENDED -> "Ended"
        }
        if (state.remoteParty.isNotEmpty()) partyView.text = state.remoteParty

        controls.removeAllViews()
        when (state.phase) {
            CallEngine.Phase.RINGING_IN -> {
                controls.addView(button(getString(R.string.answer)) { CallEngine.answer(); CallService.start(this) })
                controls.addView(button(getString(R.string.decline)) { CallEngine.decline(); finish() })
            }
            CallEngine.Phase.ACTIVE, CallEngine.Phase.HELD -> {
                controls.addView(button(getString(R.string.mute)) { CallEngine.toggleMute() })
                controls.addView(button(getString(R.string.hold)) { CallEngine.toggleHold() })
                dtmfRow()
                controls.addView(button(getString(R.string.hang_up)) {
                    CallEngine.hangUp()
                    CallService.stop(this)
                    finish()
                })
            }
            CallEngine.Phase.ENDED -> controls.addView(button("Close") { finish() })
            else -> Unit
        }
        if (state.phase == CallEngine.Phase.ENDED) {
            CallService.stop(this)
        }
    }

    private fun dtmfRow() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        "123456789*0#".forEach { digit ->
            row.addView(
                Button(this).apply {
                    text = digit.toString()
                    setOnClickListener { CallEngine.sendDtmf(digit) }
                },
            )
        }
        controls.addView(row)
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }
}
