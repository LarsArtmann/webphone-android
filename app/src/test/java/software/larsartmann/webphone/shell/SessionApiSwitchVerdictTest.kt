package software.larsartmann.webphone.shell

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The line-switch verdict classifier (the pure half of
 * [SessionApi.switchLine]) and the toast table ([switchToastText]).
 * The classes it pins are the server's own contract (webphone
 * internal/server, POST /api/session/switch): 2xx re-mints the
 * session onto the target line and answers the FULL session shape;
 * 403 the login owns no mapped lines or the asked extension is not
 * one of them; 404 the identity layer is off (an extension-only
 * login cannot switch). A null code is the LINK, never the server.
 */
class SessionApiSwitchVerdictTest {
    @Test
    fun `a 2xx answer is a switch`() {
        assertEquals(SessionApi.SwitchVerdict.SWITCHED, SessionApi.switchVerdictFromCode(200))
        assertEquals(SessionApi.SwitchVerdict.SWITCHED, SessionApi.switchVerdictFromCode(204))
    }

    @Test
    fun `an unowned line is its own verdict`() {
        assertEquals(SessionApi.SwitchVerdict.NOT_MAPPED, SessionApi.switchVerdictFromCode(403))
    }

    @Test
    fun `the identity layer being off is its own verdict`() {
        assertEquals(SessionApi.SwitchVerdict.SEAM_OFF, SessionApi.switchVerdictFromCode(404))
    }

    @Test
    fun `a transport failure is network, never a refusal`() {
        assertEquals(SessionApi.SwitchVerdict.NETWORK, SessionApi.switchVerdictFromCode(null))
    }

    @Test
    fun `any other status is other`() {
        assertEquals(SessionApi.SwitchVerdict.OTHER, SessionApi.switchVerdictFromCode(500))
        assertEquals(SessionApi.SwitchVerdict.OTHER, SessionApi.switchVerdictFromCode(400))
        assertEquals(SessionApi.SwitchVerdict.OTHER, SessionApi.switchVerdictFromCode(422))
    }
}
