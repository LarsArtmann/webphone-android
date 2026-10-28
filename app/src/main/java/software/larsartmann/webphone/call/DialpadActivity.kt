package software.larsartmann.webphone.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import software.larsartmann.webphone.R
import software.larsartmann.webphone.push.RingEvent
import software.larsartmann.webphone.ui.circleBackground
import software.larsartmann.webphone.ui.dp
import software.larsartmann.webphone.ui.iconButton
import software.larsartmann.webphone.ui.mediumTypeface
import software.larsartmann.webphone.ui.pillBackground
import software.larsartmann.webphone.ui.styleText
import software.larsartmann.webphone.ui.themeColor
import software.larsartmann.webphone.ui.withRipple

/**
 * The dialer entry — the only way a call starts from this side:
 * number in, mic permission, dial, in-call surface. The single-call
 * policy is enforced here too: a second attempt while a call is live
 * opens the call screen instead of silently replacing the call.
 */
class DialpadActivity : AppCompatActivity() {
    private lateinit var numberView: EditText
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var callPill: LinearLayout
    private lateinit var recentsHolder: FrameLayout

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) placeCall()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        numberView =
            EditText(this).apply {
                inputType = InputType.TYPE_CLASS_PHONE
                textSize = 34f
                typeface = mediumTypeface
                gravity = Gravity.CENTER
                hint = "100"
                setHintTextColor(themeColor(R.attr.wpOnSurfaceMuted))
                setTextColor(themeColor(R.attr.wpOnSurface))
                letterSpacing = 0.03f
                background = null
                setPadding(0, dp(8), 0, dp(8))
            }

        statusDot =
            View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginEnd = dp(8)
                    }
            }
        statusLabel =
            TextView(this).apply {
                styleText(13f, themeColor(R.attr.wpOnSurfaceMuted))
            }
        val statusRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(statusDot)
                addView(statusLabel)
            }

        val keys =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
        for (rowDigits in listOf("123", "456", "789", "*0#")) {
            val row =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
            for (digit in rowDigits) {
                row.addView(digitKey(digit))
            }
            keys.addView(
                row,
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(10) },
            )
        }

        callPill =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.call)
                background = pillBackground(themeColor(R.attr.wpAccent)).withRipple(this@DialpadActivity)
                minimumHeight = dp(56)
                setPadding(dp(26), 0, dp(26), 0)
                isClickable = true
                isFocusable = true
                addView(
                    AppCompatImageView(this@DialpadActivity).apply {
                        setImageResource(R.drawable.ic_call)
                        setColorFilter(themeColor(R.attr.wpOnAccent))
                        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                    },
                )
                addView(
                    TextView(this@DialpadActivity).apply {
                        text = getString(R.string.call)
                        styleText(16f, themeColor(R.attr.wpOnAccent), medium = true)
                        layoutParams =
                            LinearLayout
                                .LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply { marginStart = dp(10) }
                    },
                )
            }
        val backspace =
            iconButton(
                iconRes = R.drawable.ic_backspace,
                iconTint = themeColor(R.attr.wpOnSurface),
                backgroundColor = themeColor(R.attr.wpSurfaceRaised),
                sizeDp = 56,
                contentDescriptionText = getString(R.string.delete_last_digit),
            ).apply {
                setOnClickListener {
                    val text = numberView.text
                    if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
                }
                setOnLongClickListener {
                    numberView.setText("")
                    true
                }
            }

        val actions =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, 0)
                addView(
                    callPill,
                    LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(12) },
                )
                addView(backspace)
            }

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(themeColor(R.attr.wpBackground))
                setPadding(dp(24), dp(16), dp(24), dp(20))
                addView(numberView)
                addView(
                    statusRow,
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(2) },
                )
                // The recents live in a holder the resume loop re-fills: a
                // call placed from the dialer finishes it, but the dialer
                // that survives A call (answered while the dialer was up)
                // must show the new recents WITHOUT being recreated.
                recentsHolder = FrameLayout(this@DialpadActivity)
                addView(
                    recentsHolder,
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(14) },
                )
                // Phone-dialer geometry: the pad floats centered between
                // the number block and the actions anchored at the bottom.
                addView(View(this@DialpadActivity), LinearLayout.LayoutParams(0, 0, 1f))
                addView(
                    keys,
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { gravity = Gravity.CENTER_HORIZONTAL },
                )
                addView(View(this@DialpadActivity), LinearLayout.LayoutParams(0, 0, 1f))
                addView(
                    actions,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        // Edge-to-edge: the system bars become the root's padding.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(24), bars.top + dp(16), dp(24), bars.bottom + dp(20))
            insets
        }
        setContentView(root)

        // Deep-link pre-fill (the tel:/sip: intake routed by
        // MainActivity): the link only carries the number — the user
        // still taps Call, and the Call button still gates on
        // registration (dialer honesty holds on every entry path).
        intent.getStringExtra(EXTRA_NUMBER)?.takeIf { it.isNotBlank() }?.let { number ->
            numberView.setText(number)
            numberView.setSelection(number.length)
        }

        callPill.setOnClickListener { maybePlaceCall() }
        refreshRecents()

        // The dialer is honest about the PBX: no Call button while the
        // SIP registration is down (dialing would no-op silently).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallEngine.registration.collect { info ->
                    val registered = info.status == CallEngine.Registration.REGISTERED
                    callPill.isEnabled = registered
                    callPill.alpha = if (registered) 1f else 0.4f
                    statusLabel.text =
                        getString(
                            when {
                                registered -> R.string.registration_registered
                                info.status == CallEngine.Registration.CONNECTING -> R.string.registration_connecting
                                info.failure == CallEngine.RegistrationFailure.AUTH_REJECTED ->
                                    R.string.registration_auth_rejected
                                else -> R.string.registration_off
                            },
                        )
                    statusDot.background =
                        circleBackground(
                            themeColor(
                                if (registered) R.attr.wpStatusOn else R.attr.wpStatusPending,
                            ),
                        )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A call that ended while the dialer sat in the back stack may
        // have added a recents entry — rebuild the block.
        refreshRecents()
    }

    private fun refreshRecents() {
        recentsHolder.removeAllViews()
        val section = recentsSection()
        if (section == null) {
            recentsHolder.visibility = View.GONE
        } else {
            recentsHolder.visibility = View.VISIBLE
            recentsHolder.addView(section)
        }
    }

    /** One circular dial-pad key: digit over its letters (phone
     *  vernacular), long-press 0 for the international +. */
    private fun digitKey(digit: Char): View {
        val letters =
            when (digit) {
                '2' -> "ABC"
                '3' -> "DEF"
                '4' -> "GHI"
                '5' -> "JKL"
                '6' -> "MNO"
                '7' -> "PQRS"
                '8' -> "TUV"
                '9' -> "WXYZ"
                '0' -> "+"
                else -> ""
            }
        return FrameLayout(this).apply {
            background =
                circleBackground(themeColor(R.attr.wpSurfaceRaised))
                    .withRipple(this@DialpadActivity)
            isClickable = true
            isFocusable = true
            contentDescription = digit.toString()
            addView(
                LinearLayout(this@DialpadActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = false
                    isFocusable = false
                    addView(
                        TextView(this@DialpadActivity).apply {
                            text = digit.toString()
                            styleText(26f, themeColor(R.attr.wpOnSurface), medium = true)
                        },
                    )
                    addView(
                        TextView(this@DialpadActivity).apply {
                            text = letters
                            styleText(
                                10f,
                                themeColor(R.attr.wpOnSurfaceMuted),
                                letterSpacingEm = 0.12f,
                                allCaps = true,
                            )
                            visibility = if (letters.isEmpty()) View.INVISIBLE else View.VISIBLE
                        },
                    )
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            layoutParams =
                LinearLayout.LayoutParams(dp(68), dp(68)).apply {
                    setMargins(dp(6), 0, dp(6), 0)
                }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                numberView.append(digit.toString())
            }
            if (digit == '0') {
                setOnLongClickListener {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    numberView.append("+")
                    true
                }
            }
        }
    }

    /** The recents block: an eyebrow and the last numbers as tap-to-
     *  restore capsules (they do not dial — the user still taps Call). */
    private fun recentsSection(): LinearLayout? {
        val recent =
            getSharedPreferences(PREFS_RECENTS, MODE_PRIVATE)
                .getString(KEY_RECENTS, "")
                ?.split("\n")
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (recent.isEmpty()) return null
        val eyebrow =
            TextView(this).apply {
                text = getString(R.string.recents)
                styleText(
                    11f,
                    themeColor(R.attr.wpOnSurfaceMuted),
                    medium = true,
                    letterSpacingEm = 0.12f,
                    allCaps = true,
                )
            }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recent.forEach { number ->
            chips.addView(
                TextView(this).apply {
                    text = number
                    styleText(14f, themeColor(R.attr.wpOnSurface))
                    background =
                        pillBackground(themeColor(R.attr.wpSurfaceRaised))
                            .withRipple(this@DialpadActivity)
                    minHeight = dp(48)
                    gravity = Gravity.CENTER
                    setPadding(dp(18), 0, dp(18), 0)
                    isClickable = true
                    isFocusable = true
                    layoutParams =
                        LinearLayout
                            .LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { marginEnd = dp(8) }
                    setOnClickListener { numberView.setText(number) }
                },
            )
        }
        val scroller =
            HorizontalScrollView(this).apply {
                addView(chips)
                isHorizontalScrollBarEnabled = false
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(eyebrow)
            addView(
                scroller,
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(8) },
            )
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
        if (CallEngine.registration.value.status != CallEngine.Registration.REGISTERED) {
            // The button is disabled already — this is the belt to its
            // suspenders: dialing unregistered would no-op silently.
            Toast.makeText(this, R.string.registration_off, Toast.LENGTH_SHORT).show()
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
        rememberNumber(number)
        // The mic FGS launch path: dialing IS the user interaction.
        CallService.start(this)
        startActivity(
            Intent(this, CallActivity::class.java)
                .putExtra(RingEvent.EXTRA_FROM, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    /** Last 5 dialed numbers (newline-joined in prefs — a phone input
     *  can legitimately contain commas, never newlines). */
    private fun rememberNumber(number: String) {
        val prefs = getSharedPreferences(PREFS_RECENTS, MODE_PRIVATE)
        val previous =
            prefs
                .getString(KEY_RECENTS, "")
                ?.split("\n")
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        val updated = (listOf(number) + previous.filter { it != number }).take(RECENTS_LIMIT)
        prefs.edit().putString(KEY_RECENTS, updated.joinToString("\n")).apply()
    }

    companion object {
        /** Deep-link intake extra: the number to pre-fill. The dialer
         *  stays unexported — MainActivity (the shell) is the only
         *  component that sends this, after routing a tel:/sip: link. */
        const val EXTRA_NUMBER = "software.larsartmann.webphone.extra.NUMBER"

        /** The tel:/sip: entry: dialer pre-filled, nothing dialed. */
        fun prefill(
            context: Context,
            number: String,
        ): Intent = Intent(context, DialpadActivity::class.java).putExtra(EXTRA_NUMBER, number)

        private const val PREFS_RECENTS = "webphone-dialer"
        private const val KEY_RECENTS = "recents"
        private const val RECENTS_LIMIT = 5
    }
}
