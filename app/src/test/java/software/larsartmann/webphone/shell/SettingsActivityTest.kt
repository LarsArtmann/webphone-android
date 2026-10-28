package software.larsartmann.webphone.shell

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings save flow, shadowed: a non-https origin is refused and
 * keeps the old one; a valid origin goes live IMMEDIATELY (App.origin)
 * AND persists (the process-restart read-back), and the activity
 * finishes — the teardown-then-rewire contract the shell resume rides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsActivityTest {
    private fun views(root: View?): List<android.view.View> =
        when (root) {
            null -> emptyList()
            is ViewGroup ->
                buildList {
                    add(root)
                    for (i in 0 until root.childCount) addAll(views(root.getChildAt(i)))
                }
            else -> listOf(root)
        }

    @Test
    fun `invalid origin is refused and keeps the old one`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val input = views(activity.window.decorView).filterIsInstance<EditText>().single()
                val save =
                    views(activity.window.decorView)
                        .filterIsInstance<TextView>()
                        .single { it.text == app.getString(R.string.settings_save) }
                input.setText("http://not-https.example.org")
                save.performClick()
            }
        }
        assertEquals(App.DEFAULT_ORIGIN, App.origin, "an invalid origin must not go live")
    }

    @Test
    fun `valid origin goes live and persists`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val input = views(activity.window.decorView).filterIsInstance<EditText>().single()
                    val save =
                        views(activity.window.decorView)
                            .filterIsInstance<TextView>()
                            .single { it.text == app.getString(R.string.settings_save) }
                    input.setText("https://stack.example.org")
                    save.performClick()
                    assertTrue(activity.isFinishing, "save completes the settings surface")
                }
            }
            assertEquals("https://stack.example.org", App.origin, "the saved origin must be live immediately")
            val prefs = app.getSharedPreferences(App.PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
            assertEquals("https://stack.example.org", prefs.getString("origin", null), "and persisted")
        } finally {
            App.saveOrigin(app, App.DEFAULT_ORIGIN)
        }
    }
}
