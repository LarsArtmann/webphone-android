package software.larsartmann.webphone.call

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The dialer's deep-link pre-fill: the tel:/sip: intake carries a
 * number in, the dialer seeds it (cursor at the end — ready to append),
 * and a plain launch stays empty. The Call button still gates on
 * registration (dialer honesty is pinned in the UI state, not here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DialpadActivityTest {
    @Test
    fun `the prefill intent seeds the number with the cursor at the end`() {
        ActivityScenario
            .launch<DialpadActivity>(
                DialpadActivity.prefill(ApplicationProvider.getApplicationContext(), "+49170"),
            ).use { scenario ->
                scenario.onActivity { activity ->
                    val number = firstEditText(activity.findViewById<View>(android.R.id.content))
                    assertEquals("+49170", number.text.toString())
                    assertEquals(6, number.selectionStart)
                    assertEquals(6, number.selectionEnd)
                }
            }
    }

    @Test
    fun `a plain launch stays empty`() {
        ActivityScenario.launch(DialpadActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNull(activity.intent.getStringExtra(DialpadActivity.EXTRA_NUMBER))
                assertEquals("", firstEditText(activity.findViewById<View>(android.R.id.content)).text.toString())
            }
        }
    }

    /** The programmatic UI carries no view IDs; the dialer's number
     *  field is the one (and only) EditText in the tree. */
    private fun firstEditText(view: View): EditText =
        when (view) {
            is EditText -> view
            is ViewGroup ->
                (0 until view.childCount)
                    .asSequence()
                    .map { firstEditText(view.getChildAt(it)) }
                    .firstOrNull()
                    ?: error("no EditText found in the dialer tree")
            else -> error("no EditText found in the dialer tree")
        }
}
