package software.larsartmann.webphone.shell

import software.larsartmann.webphone.push.WakeLaneState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wake-address registration's verdict classifier (the pure half
 * of [SessionApi.registerPushDevice]). The classes it pins are the
 * server's own contract: 204 accepted; 404 the push seam is OFF
 * server-side; 403 the session/CSRF gate; 422 invalid address OR
 * full device list (the server merges both). A null code is the
 * LINK, never the server — a Boolean verdict collapsed these into
 * one indistinguishable `false`, which is the silence this enum
 * exists to end.
 */
class SessionApiRegisterVerdictTest {
    @Test
    fun `accepted is registered`() {
        assertEquals(SessionApi.PushRegisterVerdict.REGISTERED, SessionApi.registerVerdictFromCode(204))
    }

    @Test
    fun `the seam being off is its own verdict`() {
        assertEquals(SessionApi.PushRegisterVerdict.SEAM_OFF, SessionApi.registerVerdictFromCode(404))
    }

    @Test
    fun `a session or csrf rejection is auth`() {
        assertEquals(SessionApi.PushRegisterVerdict.AUTH, SessionApi.registerVerdictFromCode(403))
    }

    @Test
    fun `invalid address and a full device list are one verdict (both 422 server-side)`() {
        assertEquals(SessionApi.PushRegisterVerdict.INVALID, SessionApi.registerVerdictFromCode(422))
    }

    @Test
    fun `a transport failure is network, never a refusal`() {
        assertEquals(SessionApi.PushRegisterVerdict.NETWORK, SessionApi.registerVerdictFromCode(null))
    }

    @Test
    fun `any other status is other`() {
        assertEquals(SessionApi.PushRegisterVerdict.OTHER, SessionApi.registerVerdictFromCode(500))
        assertEquals(SessionApi.PushRegisterVerdict.OTHER, SessionApi.registerVerdictFromCode(200))
        assertEquals(SessionApi.PushRegisterVerdict.OTHER, SessionApi.registerVerdictFromCode(400))
    }

    @Test
    fun `every verdict maps to a wake state - registered keeps the socket's word`() {
        assertEquals(
            null,
            CallLayer.wakeStateFor(SessionApi.PushRegisterVerdict.REGISTERED),
            "REGISTERED must not overwrite the socket's verdict (STARTING stands until ARMED)",
        )
        assertEquals(
            WakeLaneState.REFUSED_SEAM_OFF,
            CallLayer.wakeStateFor(SessionApi.PushRegisterVerdict.SEAM_OFF),
        )
        assertEquals(
            WakeLaneState.REFUSED_INVALID,
            CallLayer.wakeStateFor(SessionApi.PushRegisterVerdict.INVALID),
        )
        assertEquals(WakeLaneState.REFUSED_AUTH, CallLayer.wakeStateFor(SessionApi.PushRegisterVerdict.AUTH))
        assertEquals(WakeLaneState.REFUSED_OTHER, CallLayer.wakeStateFor(SessionApi.PushRegisterVerdict.OTHER))
        assertEquals(
            WakeLaneState.DISCONNECTED,
            CallLayer.wakeStateFor(SessionApi.PushRegisterVerdict.NETWORK),
            "the link being down is a transport verdict, not a refusal",
        )
    }
}
