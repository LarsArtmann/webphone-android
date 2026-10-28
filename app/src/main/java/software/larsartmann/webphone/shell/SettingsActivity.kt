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
 */
class SettingsActivity : AppCompatActivity() {
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
}
