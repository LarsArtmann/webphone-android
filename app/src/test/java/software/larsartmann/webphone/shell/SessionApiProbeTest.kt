package software.larsartmann.webphone.shell

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The session probe's verdict classifier (the pure half of
 * [SessionApi.probeSession]). The distinction it pins is the
 * no-ring failure mode's guard: a definitive logout (401/403) may
 * tear the wake lane down; a transport failure (null code, 502, any
 * other status) must be UNREACHABLE — the watcher keeps the lane and
 * waits for the next tick, because a tunnel flap must never
 * permanently silence the phone.
 */
class SessionApiProbeTest {
    @Test
    fun `success codes are alive`() {
        assertEquals(SessionApi.SessionProbe.ALIVE, SessionApi.probeFromCode(200))
        assertEquals(SessionApi.SessionProbe.ALIVE, SessionApi.probeFromCode(204))
        assertEquals(SessionApi.SessionProbe.ALIVE, SessionApi.probeFromCode(299))
    }

    @Test
    fun `a definitive rejection is dead`() {
        assertEquals(SessionApi.SessionProbe.DEAD, SessionApi.probeFromCode(401))
        assertEquals(SessionApi.SessionProbe.DEAD, SessionApi.probeFromCode(403))
    }

    @Test
    fun `a transport failure is unreachable, never dead`() {
        assertEquals(SessionApi.SessionProbe.UNREACHABLE, SessionApi.probeFromCode(null))
    }

    @Test
    fun `any other status is unreachable`() {
        assertEquals(SessionApi.SessionProbe.UNREACHABLE, SessionApi.probeFromCode(500))
        assertEquals(SessionApi.SessionProbe.UNREACHABLE, SessionApi.probeFromCode(502))
        assertEquals(SessionApi.SessionProbe.UNREACHABLE, SessionApi.probeFromCode(418))
    }
}
