package software.larsartmann.webphone.shell

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.push.NtfySubscriptionService

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

        val title = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 24f
        }
        val explanation = TextView(this).apply { text = getString(R.string.settings_explanation) }
        val originInput = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(App.origin)
            hint = getString(R.string.settings_hint)
        }
        val save = Button(this).apply { text = getString(R.string.settings_save) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(title)
            addView(explanation)
            addView(originInput)
            addView(
                save,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        setContentView(root)

        save.setOnClickListener {
            val cleaned = originInput.text.toString().trim().trimEnd('/')
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
