package software.larsartmann.webphone.shell

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.push.NtfySubscriptionService
import software.larsartmann.webphone.ui.dp
import software.larsartmann.webphone.ui.pillBackground
import software.larsartmann.webphone.ui.roundedBackground
import software.larsartmann.webphone.ui.styleText
import software.larsartmann.webphone.ui.themeColor
import software.larsartmann.webphone.ui.withRipple

/**
 * The Settings surface: the stack origin is the ONE fact this app
 * cannot guess — every island request, the SIP WSS URL and the ntfy
 * lane hang off it. Saving makes it live immediately: the call layer
 * tears down (old origin's session + registration are void) and the
 * next shell resume re-wires against the new origin.
 *
 * When the resumed identity carries MORE than one owned line, an
 * "Active line" section renders under the origin field (the handover
 * pack's picker contract): the active row is marked, the others switch
 * through `/api/session/switch` — and the switch voids the old line's
 * registration and push subscription exactly like an origin change
 * (the shell's next resume re-wires onto the switched session).
 */
class SettingsActivity : AppCompatActivity() {
    companion object {
        /** The seams the tests swap (set BEFORE launch, reset in each
         *  test's finally — onCreate's loader thread reads them): the
         *  identity fetch and the switch call default to the real
         *  network seam. */
        @Volatile
        internal var identityLoader: () -> SessionApi.SessionIdentity? = { SessionApi.resumeSession() }

        @Volatile
        internal var lineSwitcher: (String) -> SessionApi.SwitchVerdict = { SessionApi.switchLine(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val title =
            TextView(this).apply {
                text = getString(R.string.settings_title)
                styleText(24f, themeColor(R.attr.wpOnSurface), medium = true)
            }
        val explanation =
            TextView(this).apply {
                text = getString(R.string.settings_explanation)
                styleText(14f, themeColor(R.attr.wpOnSurfaceMuted))
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(8) }
            }
        // The quiet card: stroke + fill instead of an underline field.
        val originInput =
            EditText(this).apply {
                inputType = InputType.TYPE_TEXT_VARIATION_URI
                setText(App.origin)
                hint = getString(R.string.settings_hint)
                setHintTextColor(themeColor(R.attr.wpOnSurfaceMuted))
                setTextColor(themeColor(R.attr.wpOnSurface))
                textSize = 16f
                background = null
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            }
        val field =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    roundedBackground(themeColor(R.attr.wpSurface), 16f).apply {
                        setStroke(dp(1), themeColor(R.attr.wpOutline))
                    }
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(24) }
                addView(originInput)
            }
        val save =
            TextView(this).apply {
                text = getString(R.string.settings_save)
                styleText(16f, themeColor(R.attr.wpOnAccent), medium = true)
                background = pillBackground(themeColor(R.attr.wpAccent)).withRipple(this@SettingsActivity)
                minHeight = dp(56)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(56),
                        ).apply { topMargin = dp(20) }
            }

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(themeColor(R.attr.wpBackground))
                setPadding(dp(24), dp(32), dp(24), dp(24))
                addView(title)
                addView(explanation)
                addView(field)
                addView(save)
            }
        // Edge-to-edge: the system bars become the root's padding.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(24), bars.top + dp(32), dp(24), bars.bottom + dp(24))
            insets
        }
        setContentView(root)

        // The line picker loads off the main thread (resumeSession is
        // blocking network); an unreachable origin renders no section —
        // the origin field above stays the screen's whole job.
        Thread {
            val identity = runCatching { identityLoader() }.getOrNull()
            if (identity != null) {
                runOnUiThread { renderLinePicker(root, identity) }
            }
        }.start()

        save.setOnClickListener {
            val cleaned =
                originInput.text
                    .toString()
                    .trim()
                    .trimEnd('/')
            if (!cleaned.startsWith("https://") || cleaned.length <= "https://".length) {
                Toast.makeText(this, R.string.settings_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (cleaned != App.origin) {
                App.saveOrigin(this, cleaned)
                // The old origin's session, registration and push
                // subscription are void — drop them; the shell's next
                // resume re-wires everything against the new origin.
                NtfySubscriptionService.stop(this)
                CallLayer.shutdown()
            }
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** Append the "Active line" section under the origin card. The
     *  active row is marked (accent + medium, inert); the others are
     *  switch actions. */
    private fun renderLinePicker(
        root: LinearLayout,
        identity: SessionApi.SessionIdentity,
    ) {
        val lines = SessionApi.pickerLines(identity)
        if (lines.isEmpty()) return
        val sectionTitle =
            TextView(this).apply {
                text = getString(R.string.settings_line_section)
                styleText(14f, themeColor(R.attr.wpOnSurfaceMuted))
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(28) }
            }
        root.addView(sectionTitle)
        var switching = false
        for (line in lines) {
            val active = line.extension == identity.extension
            val row =
                TextView(this).apply {
                    text = line.label()
                    styleText(16f, themeColor(if (active) R.attr.wpAccent else R.attr.wpOnSurface), medium = active)
                    minHeight = dp(48)
                    gravity = Gravity.CENTER_VERTICAL
                    if (active) {
                        isClickable = false
                        isFocusable = false
                    } else {
                        background =
                            roundedBackground(themeColor(R.attr.wpSurface), 12f).withRipple(this@SettingsActivity)
                        setPadding(dp(16), dp(8), dp(16), dp(8))
                        isClickable = true
                        isFocusable = true
                    }
                    layoutParams =
                        LinearLayout
                            .LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = dp(6) }
                }
            if (!active) {
                row.setOnClickListener {
                    if (switching) return@setOnClickListener
                    switching = true
                    Thread {
                        val verdict =
                            runCatching { lineSwitcher(line.extension) }
                                .getOrDefault(SessionApi.SwitchVerdict.NETWORK)
                        runOnUiThread {
                            Toast
                                .makeText(
                                    this@SettingsActivity,
                                    SessionApi.switchMessage(verdict),
                                    Toast.LENGTH_LONG,
                                ).show()
                            if (verdict == SessionApi.SwitchVerdict.SWITCHED) {
                                // The old line's registration and push
                                // subscription are void — same teardown
                                // contract as an origin change; the
                                // shell's next resume re-wires onto the
                                // switched session.
                                NtfySubscriptionService.stop(this@SettingsActivity)
                                CallLayer.shutdown()
                                finish()
                            } else {
                                switching = false
                            }
                        }
                    }.start()
                }
            }
            root.addView(row)
        }
    }
}
