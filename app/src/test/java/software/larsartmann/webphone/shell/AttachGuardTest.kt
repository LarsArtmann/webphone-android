package software.larsartmann.webphone.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wiring invariant: one engine attaches; a shutdown anoints every
 * in-flight attempt an orphan and they refuse to attach after it; the
 * next attempt (new generation) wins. This is the settings-save race,
 * pinned.
 */
class AttachGuardTest {

    @Test
    fun `first attempt attaches, second is refused`() {
        val guard = AttachGuard<String>()
        val first = guard.begin()
        assertTrue(guard.attach("engine-1", first))
        assertEquals("engine-1", guard.current())
        val second = guard.begin()
        assertFalse(guard.attach("engine-2", second))
        assertEquals("engine-1", guard.current())
    }

    @Test
    fun `stale-generation attempt is refused - shutdown wins the race`() {
        val guard = AttachGuard<String>()
        val inFlight = guard.begin()
        // The resume is off doing network I/O when shutdown lands:
        guard.invalidate()
        assertFalse(guard.attach("orphan", inFlight), "an orphaned attempt must never attach")
        assertNull(guard.current())
    }

    @Test
    fun `next attempt after shutdown attaches fresh`() {
        val guard = AttachGuard<String>()
        val stale = guard.begin()
        guard.attach("engine-1", stale)
        guard.invalidate()
        guard.clear()
        val fresh = guard.begin()
        assertTrue(guard.attach("engine-2", fresh))
        assertEquals("engine-2", guard.current())
    }

    @Test
    fun `invalidate alone does not forget the attached engine`() {
        val guard = AttachGuard<String>()
        val gen = guard.begin()
        guard.attach("engine-1", gen)
        guard.invalidate()
        assertEquals("engine-1", guard.current())
        guard.clear()
        assertNull(guard.current())
    }
}
