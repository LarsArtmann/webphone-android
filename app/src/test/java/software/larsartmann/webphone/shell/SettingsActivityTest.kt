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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
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
@Config(sdk = [31, 34])
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

    private fun line(ext: String) = SessionApi.SessionLine(extension = ext, did = "")

    private fun identity(ext: String, lines: List<SessionApi.SessionLine>) =
        SessionApi.SessionIdentity(
            extension = ext,
            lines = lines,
            sipDomain = "pbx.test",
            sipPassword = "pw",
            websocketUrl = "wss://pbx.test/sip",
        )

    /** Pump the main looper until [done] turns true (the picker loads
     *  on a background thread and renders via runOnUiThread) or the
     *  5 s budget dies. */
    private fun pumpUntil(
        scenario: ActivityScenario<SettingsActivity>,
        done: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !done()) {
            Shadows.shadowOf(ShadowLooper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }

    @Test
    fun `a single-line identity renders no picker section`() {
        SettingsActivity.identityLoader = { identity("1001", listOf(line("1001"))) }
        ActivityScenario.launch(SettingsActivity::class.java).use { s ->
            pumpUntil(s) {
                var settled = false
                s.onActivity { activity ->
                    settled =
                        views(activity.window.decorView)
                            .filterIsInstance<TextView>()
                            .none { it.text == activity.getString(R.string.settings_line_section) }
                }
                settled
            }
            s.onActivity { activity ->
                val texts =
                    views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(
                    texts.none { it == activity.getString(R.string.settings_line_section) },
                    "one owned line means no Active line section: $texts",
                )
            }
        }
    }

    @Test
    fun `a multi-line identity renders the section with the active row inert`() {
        SettingsActivity.identityLoader = { identity("1001", listOf(line("1001"), line("1002"))) }
        ActivityScenario.launch(SettingsActivity::class.java).use { s ->
            pumpUntil(s) {
                var up = false
                s.onActivity { activity ->
                    up =
                        views(activity.window.decorView)
                            .filterIsInstance<TextView>()
                            .any { it.text == activity.getString(R.string.settings_line_section) }
                }
                up
            }
            s.onActivity { activity ->
                val rows =
                    views(activity.window.decorView)
                        .filterIsInstance<TextView>()
                        .filter { it.text.toString().endsWith("1001") || it.text.toString().endsWith("1002") }
                assertEquals(2, rows.size, "both owned lines render as rows")
                val active = rows.single { !it.isClickable }
                assertEquals("1001", active.text.toString(), "the session's extension is the marked row")
                assertTrue(rows.single { it.isClickable }.isClickable, "the other line is a switch action")
            }
        }
    }

    @Test
    fun `tapping another line switches and finishes the surface`() {
        val switchedTo = java.util.concurrent.atomic.AtomicReference("")
        SettingsActivity.identityLoader = { identity("1001", listOf(line("1001"), line("1002"))) }
        SettingsActivity.lineSwitcher = { ext ->
            switchedTo.set(ext)
            SessionApi.SwitchVerdict.SWITCHED
        }
        ActivityScenario.launch(SettingsActivity::class.java).use { s ->
            pumpUntil(s) {
                var up = false
                s.onActivity { activity ->
                    up =
                        views(activity.window.decorView)
                            .filterIsInstance<TextView>()
                            .any { it.text == activity.getString(R.string.settings_line_section) }
                }
                up
            }
            s.onActivity { activity ->
                val action =
                    views(activity.window.decorView)
                        .filterIsInstance<TextView>()
                        .single { it.isClickable && it.text.toString().endsWith("1002") }
                action.performClick()
            }
            pumpUntil(s) {
                var finished = false
                s.onActivity { activity -> finished = activity.isFinishing }
                finished
            }
            s.onActivity { activity ->
                assertEquals("1002", switchedTo.get(), "the tapped line rode the switch seam")
                assertTrue(activity.isFinishing, "a switched line finishes the surface (the shell resume re-wires)")
            }
        }
    }

    @Test
    fun `a refused switch keeps the surface up for the next try`() {
        SettingsActivity.identityLoader = { identity("1001", listOf(line("1001"), line("1002"))) }
        SettingsActivity.lineSwitcher = { _ -> SessionApi.SwitchVerdict.NOT_MAPPED }
        ActivityScenario.launch(SettingsActivity::class.java).use { s ->
            pumpUntil(s) {
                var up = false
                s.onActivity { activity ->
                    up =
                        views(activity.window.decorView)
                            .filterIsInstance<TextView>()
                            .any { it.text == activity.getString(R.string.settings_line_section) }
                }
                up
            }
            s.onActivity { activity ->
                val action =
                    views(activity.window.decorView)
                        .filterIsInstance<TextView>()
                        .single { it.isClickable && it.text.toString().endsWith("1002") }
                action.performClick()
            }
            pumpUntil(s) {
                var toastGone = false
                s.onActivity { _ -> toastGone = true }
                toastGone
            }
            s.onActivity { activity ->
                assertTrue(!activity.isFinishing, "a refused switch stays on the surface")
            }
        }
    }
}
