package software.larsartmann.webphone.call

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.larsartmann.webphone.R
import software.larsartmann.webphone.push.RingEvent
import software.larsartmann.webphone.ui.circleBackground
import software.larsartmann.webphone.ui.dp
import software.larsartmann.webphone.ui.pillBackground
import software.larsartmann.webphone.ui.pulse
import software.larsartmann.webphone.ui.roundedBackground
import software.larsartmann.webphone.ui.styleText
import software.larsartmann.webphone.ui.withRipple

/**
 * The in-call surface: launched by the full-screen intent (incoming)
 * or the dialer entry (outgoing), it renders the CallEngine state and
 * owns the call controls. Always dark (calls happen in dim rooms);
 * the CallStyle-composed notification arrives with the first device
 * train, this UI is the honest working surface.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var avatarView: TextView
    private lateinit var statusView: TextView
    private lateinit var partyView: TextView
    private lateinit var controls: LinearLayout
    private var answerPadPulse: ValueAnimator? = null
    private var keypadOpen = false

    private var answerWhenMicGranted = false
    private var autoFinish: Job? = null

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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        avatarView = TextView(this).apply {
            styleText(
                40f,
                ContextCompat.getColor(this@CallActivity, R.color.call_avatar_fg),
                medium = true,
            )
            gravity = Gravity.CENTER
        }
        val avatarPad = FrameLayout(this).apply {
            background = circleBackground(
                ContextCompat.getColor(this@CallActivity, R.color.call_avatar_bg),
            )
            addView(
                avatarView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        }
        partyView = TextView(this).apply {
            styleText(
                34f,
                ContextCompat.getColor(this@CallActivity, R.color.call_text),
                medium = true,
            )
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(24) }
        }
        statusView = TextView(this).apply {
            styleText(
                13f,
                ContextCompat.getColor(this@CallActivity, R.color.call_muted),
                letterSpacingEm = 0.1f,
                allCaps = true,
            )
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    ContextCompat.getColor(this@CallActivity, R.color.call_bg_top),
                    ContextCompat.getColor(this@CallActivity, R.color.call_bg_bottom),
                ),
            )
            setPadding(dp(24), dp(48), dp(24), dp(32))
            addView(avatarPad)
            addView(partyView)
            addView(statusView)
            addView(View(this@CallActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        // Edge-to-edge: the system bars become the root's padding.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(24), bars.top + dp(48), dp(24), bars.bottom + dp(32))
            insets
        }
        setContentView(root)

        partyView.text = intent.getStringExtra(RingEvent.EXTRA_FROM) ?: getString(R.string.unknown_caller)
        renderAvatar(partyView.text.toString())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallEngine.state.collect { render(it) }
            }
        }
    }

    /** Re-ring while alive: the full-screen intent on a singleInstance
     *  activity lands here — refresh the displayed party (the state
     *  collector re-renders the controls on its own). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(RingEvent.EXTRA_FROM)?.let {
            partyView.text = it
            renderAvatar(it)
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

    private fun renderAvatar(party: CharSequence) {
        avatarView.text = party.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
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
        if (state.remoteParty.isNotEmpty()) {
            partyView.text = state.remoteParty
            renderAvatar(state.remoteParty)
        }

        answerPadPulse?.cancel()
        answerPadPulse = null
        controls.removeAllViews()
        when (state.phase) {
            CallEngine.Phase.RINGING_IN -> renderRinging()
            CallEngine.Phase.DIALLING -> renderDialling()
            CallEngine.Phase.ACTIVE, CallEngine.Phase.HELD -> renderActive(state)
            CallEngine.Phase.ENDED -> renderEnded()
            else -> Unit
        }
        if (state.phase == CallEngine.Phase.ENDED) {
            CallService.stop(this)
            scheduleEndAutoFinish()
        }
    }

    /** The signature echo: the green answer pad breathes while the
     *  phone rings (skipped when animations are disabled). */
    private fun renderRinging() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(
            circlePad(
                iconRes = R.drawable.ic_call_end,
                backgroundColor = ContextCompat.getColor(this, R.color.call_decline_bg),
                iconTint = ContextCompat.getColor(this, R.color.call_decline_fg),
                sizeDp = 76,
                label = getString(R.string.decline),
            ) {
                CallEngine.decline()
                finish()
            },
        )
        row.addView(
            View(this),
            LinearLayout.LayoutParams(dp(64), 1),
        )
        val answerButton = circlePad(
            iconRes = R.drawable.ic_call,
            backgroundColor = ContextCompat.getColor(this, R.color.call_answer),
            iconTint = ContextCompat.getColor(this, android.R.color.white),
            sizeDp = 76,
            label = getString(R.string.answer),
        ) {
            answerCall()
        }
        answerPadPulse = pulse(answerButton, this)
        row.addView(answerButton)
        controls.addView(row)
    }

    private fun renderDialling() {
        controls.addView(
            circlePad(
                iconRes = R.drawable.ic_call_end,
                backgroundColor = ContextCompat.getColor(this, R.color.call_danger),
                iconTint = ContextCompat.getColor(this, android.R.color.white),
                sizeDp = 72,
                label = getString(R.string.hang_up),
            ) {
                hangUpAndFinish()
            },
        )
    }

    private fun renderActive(state: CallEngine.CallState) {
        val toggles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        toggles.addView(
            togglePad(
                if (state.muted) R.drawable.ic_mic_off else R.drawable.ic_mic,
                active = state.muted,
                label = getString(R.string.mute),
            ) { CallEngine.toggleMute() },
        )
        toggles.addView(View(this), LinearLayout.LayoutParams(dp(20), 1))
        toggles.addView(
            togglePad(
                R.drawable.ic_speaker,
                active = speakerActive(),
                label = getString(R.string.speaker),
            ) {
                toggleSpeaker()
                // The speaker is local audio state, not CallEngine
                // state — no collector fires, so re-render for the
                // lit/unlit feedback.
                render(CallEngine.state.value)
            },
        )
        toggles.addView(View(this), LinearLayout.LayoutParams(dp(20), 1))
        toggles.addView(
            togglePad(
                R.drawable.ic_pause,
                active = state.phase == CallEngine.Phase.HELD,
                label = getString(R.string.hold),
            ) { CallEngine.toggleHold() },
        )
        controls.addView(toggles)

        controls.addView(
            keypadToggle(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(24) },
        )
        if (keypadOpen) {
            controls.addView(
                keypadCard(),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(16) },
            )
        }

        controls.addView(
            TextView(this).apply {
                background = pillBackground(
                    ContextCompat.getColor(this@CallActivity, R.color.call_danger),
                ).withRipple(this@CallActivity)
                text = getString(R.string.hang_up)
                styleText(
                    16f,
                    ContextCompat.getColor(this@CallActivity, android.R.color.white),
                    medium = true,
                )
                minHeight = dp(56)
                gravity = Gravity.CENTER
                setPadding(dp(26), 0, dp(26), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { hangUpAndFinish() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            ).apply { topMargin = dp(24) },
        )
    }

    private fun renderEnded() {
        controls.addView(
            TextView(this).apply {
                background = pillBackground(
                    ContextCompat.getColor(this@CallActivity, R.color.call_toggle_bg),
                ).withRipple(this@CallActivity)
                text = getString(R.string.close)
                styleText(15f, ContextCompat.getColor(this@CallActivity, R.color.call_text))
                minHeight = dp(52)
                gravity = Gravity.CENTER
                setPadding(dp(30), 0, dp(30), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { finish() }
            },
        )
    }

    private fun hangUpAndFinish() {
        CallEngine.hangUp()
        CallService.stop(this)
        finish()
    }

    /** A big circular call pad (answer / decline): icon circle with a
     *  quiet label beneath. */
    private fun circlePad(
        iconRes: Int,
        backgroundColor: Int,
        iconTint: Int,
        sizeDp: Int,
        label: String,
        onClick: () -> Unit,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val button = AppCompatImageButton(this@CallActivity).apply {
            setImageResource(iconRes)
            setColorFilter(iconTint)
            background = circleBackground(backgroundColor).withRipple(this@CallActivity)
            val side = dp(sizeDp)
            minimumWidth = side
            minimumHeight = side
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }
        addView(button, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
        addView(
            TextView(this@CallActivity).apply {
                text = label
                styleText(13f, ContextCompat.getColor(this@CallActivity, R.color.call_muted))
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )
    }

    /** A mid-size circular toggle (mute / speaker / hold): lit when
     *  the state it names is on. */
    private fun togglePad(
        iconRes: Int,
        active: Boolean,
        label: String,
        onClick: () -> Unit,
    ): View {
        val backgroundColor = ContextCompat.getColor(
            this,
            if (active) R.color.call_toggle_active_bg else R.color.call_toggle_bg,
        )
        val tint = ContextCompat.getColor(
            this,
            if (active) R.color.call_toggle_active_fg else R.color.call_toggle_fg,
        )
        return AppCompatImageButton(this).apply {
            setImageResource(iconRes)
            setColorFilter(tint)
            background = circleBackground(backgroundColor).withRipple(this@CallActivity)
            val side = dp(56)
            minimumWidth = side
            minimumHeight = side
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }
    }

    private fun keypadToggle(): View = TextView(this).apply {
        background = pillBackground(
            ContextCompat.getColor(
                this@CallActivity,
                if (keypadOpen) R.color.call_toggle_active_bg else R.color.call_toggle_bg,
            ),
        ).withRipple(this@CallActivity)
        text = getString(R.string.keypad)
        styleText(
            13f,
            ContextCompat.getColor(
                this@CallActivity,
                if (keypadOpen) R.color.call_toggle_active_fg else R.color.call_toggle_fg,
            ),
            medium = true,
        )
        minHeight = dp(44)
        gravity = Gravity.CENTER
        setPadding(dp(20), 0, dp(20), 0)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            keypadOpen = !keypadOpen
            render(CallEngine.state.value)
        }
    }

    /** The DTMF pad, revealed on demand: a quiet card of circular
     *  keys — no more 13 buttons in one cramped line. */
    private fun keypadCard(): View {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        for (rowDigits in listOf("123", "456", "789", "*0#")) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (digit in rowDigits) {
                row.addView(
                    TextView(this).apply {
                        text = digit.toString()
                        styleText(
                            16f,
                            ContextCompat.getColor(this@CallActivity, R.color.call_text),
                            medium = true,
                        )
                        background = circleBackground(
                            ContextCompat.getColor(this@CallActivity, R.color.call_key_bg),
                        ).withRipple(this@CallActivity)
                        minimumHeight = dp(48)
                        minimumWidth = dp(48)
                        gravity = Gravity.CENTER
                        isClickable = true
                        isFocusable = true
                        contentDescription = digit.toString()
                        setOnClickListener { CallEngine.sendDtmf(digit) }
                        layoutParams = LinearLayout.LayoutParams(
                            dp(48),
                            dp(48),
                        ).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                    },
                )
            }
            grid.addView(row)
        }
        return FrameLayout(this).apply {
            background = roundedBackground(
                ContextCompat.getColor(this@CallActivity, R.color.call_card),
                24f,
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(grid)
        }
    }

    /** An ended call screen must not linger: auto-close after a beat
     *  (the Close button stays for the user who wants to read it). */
    private fun scheduleEndAutoFinish() {
        if (autoFinish?.isActive == true) return
        autoFinish = lifecycleScope.launch {
            delay(3_000)
            if (CallEngine.state.value.phase == CallEngine.Phase.ENDED && !isFinishing) finish()
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

    private fun speakerActive(): Boolean =
        (getSystemService(AudioManager::class.java))?.communicationDevice?.type ==
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
}
