package software.larsartmann.webphone.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
 * The dialer entry — the only way a call starts from this side:
 * number in, mic permission, dial, in-call surface. The single-call
 * policy is enforced here too: a second attempt while a call is live
 * opens the call screen instead of silently replacing the call.
 */
class DialpadActivity : AppCompatActivity() {

    private lateinit var numberView: EditText

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) placeCall()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        numberView = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 28f
            gravity = Gravity.CENTER
            hint = "100"
        }

        val keys = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (rowDigits in listOf("123", "456", "789", "*0#")) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (digit in rowDigits) {
                row.addView(
                    Button(this).apply {
                        text = digit.toString()
                        textSize = 24f
                        setOnClickListener { numberView.append(digit.toString()) }
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                    },
                )
            }
            keys.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val call = Button(this).apply { text = getString(R.string.call) }
        val registrationHint = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
        }
        val backspace = Button(this).apply {
            text = "\u232B"
            setOnClickListener {
                val text = numberView.text
                if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
            }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(call, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f))
        actions.addView(backspace, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(numberView)
            addView(registrationHint)
            addView(keys)
            addView(actions)
        }
        setContentView(root)

        call.setOnClickListener { maybePlaceCall() }

        // The dialer is honest about the PBX: no Call button while the
        // SIP registration is down (dialing would no-op silently).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallEngine.registration.collect { registration ->
                    call.isEnabled = registration == CallEngine.Registration.REGISTERED
                    registrationHint.text = getString(
                        when (registration) {
                            CallEngine.Registration.REGISTERED -> R.string.registration_registered
                            CallEngine.Registration.CONNECTING -> R.string.registration_connecting
                            CallEngine.Registration.OFF -> R.string.registration_off
                        },
                    )
                    registrationHint.setTextColor(
                        ContextCompat.getColor(
                            this@DialpadActivity,
                            if (registration == CallEngine.Registration.REGISTERED) {
                                R.color.status_connected
                            } else {
                                R.color.status_pending
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun maybePlaceCall() {
        val phase = CallEngine.state.value.phase
        if (phase != CallEngine.Phase.IDLE && phase != CallEngine.Phase.ENDED) {
            // The single-call policy: a live call means the call
            // screen, not a second dial attempt.
            startActivity(
                Intent(this, CallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
            return
        }
        if (numberView.text.isBlank()) {
            Toast.makeText(this, R.string.enter_a_number, Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            placeCall()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun placeCall() {
        val number = numberView.text.toString().trim()
        if (number.isEmpty()) return
        // The Telecom path IS the outbound path now: the OS builds the
        // call (BT/watches/audio routing see it) and the
        // ConnectionService dials through CallEngine — no duplicate
        // direct-dial here.
        CallConnectionService.placeOutgoingCall(this, number)
        // The mic FGS launch path: dialing IS the user interaction.
        CallService.start(this)
        startActivity(
            Intent(this, CallActivity::class.java)
                .putExtra(RingEvent.EXTRA_FROM, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
