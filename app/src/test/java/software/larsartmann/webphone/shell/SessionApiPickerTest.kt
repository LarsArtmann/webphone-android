package software.larsartmann.webphone.shell

import software.larsartmann.webphone.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Settings line-picker's pure decisions, pinned: the picker exists
 * only for multi-line accounts (a single-line deployment renders
 * byte-identically to a build without the picker), and every switch
 * verdict reaches the user as its own honest sentence — the three
 * refusals never collapse into one Boolean.
 */
class SessionApiPickerTest {
    private fun line(ext: String) = SessionApi.SessionLine(extension = ext, did = "")

    private fun identity(
        ext: String,
        lines: List<SessionApi.SessionLine>,
    ) = SessionApi.SessionIdentity(
        extension = ext,
        lines = lines,
        sipDomain = "pbx.test",
        sipPassword = "pw",
        websocketUrl = "wss://pbx.test/sip",
    )

    @Test
    fun `a single-line account renders no picker`() {
        val one = identity("1001", listOf(line("1001")))
        assertTrue(SessionApi.pickerLines(one).isEmpty(), "one owned line means no picker")
    }

    @Test
    fun `a multi-line account offers every line`() {
        val two = identity("1001", listOf(line("1001"), line("1002")))
        assertEquals(2, SessionApi.pickerLines(two).size, "both owned lines are offered")
    }

    @Test
    fun `the lines answer predating the typed wire degrades to one line`() {
        // parseLines already degrades; the picker inherits the honesty:
        // a degraded single line still hides the section.
        val degraded = identity("1001", listOf(line("1001")))
        assertTrue(SessionApi.pickerLines(degraded).isEmpty())
    }

    @Test
    fun `every switch verdict has its own sentence`() {
        assertEquals(R.string.settings_line_switched, SessionApi.switchMessage(SessionApi.SwitchVerdict.SWITCHED))
        assertEquals(R.string.settings_line_not_mapped, SessionApi.switchMessage(SessionApi.SwitchVerdict.NOT_MAPPED))
        assertEquals(R.string.settings_line_seam_off, SessionApi.switchMessage(SessionApi.SwitchVerdict.SEAM_OFF))
        assertEquals(R.string.settings_line_network, SessionApi.switchMessage(SessionApi.SwitchVerdict.NETWORK))
        assertEquals(R.string.settings_line_failed, SessionApi.switchMessage(SessionApi.SwitchVerdict.OTHER))
    }
}
