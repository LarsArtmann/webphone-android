package software.larsartmann.webphone.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import software.larsartmann.webphone.sip.SipEngine

/**
 * The one active-call orchestrator: SipEngine (signaling) +
 * WebRtcEngine.CallMedia (audio) + the state the Telecom surfaces and
 * the in-call UI render. Single-call by product decision (a personal
 * extension line) — a second call waits, it never silently replaces.
 */
object CallEngine : SipEngine.Listener {

    enum class Phase { IDLE, DIALLING, RINGING_IN, ACTIVE, HELD, ENDED }

    data class CallState(
        val phase: Phase = Phase.IDLE,
        val callId: String = "",
        val remoteParty: String = "",
        val muted: Boolean = false,
    )

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state

    private var sip: SipEngine? = null
    private var media: WebRtcEngine.CallMedia? = null
    private var pendingInvite: SipEngine.IncomingInvite? = null

    fun attach(engine: SipEngine) {
        sip = engine
        engine.listener = this
    }

    fun detach() {
        sip?.listener = null
        sip?.disconnect()
        sip = null
        teardown()
    }

    /** ring_end: the push lane's ring stands down (answered elsewhere,
     *  caller gave up) — refuse any pending invite and end the ringing
     *  state. No-op outside RINGING_IN. */
    fun cancelRinging() {
        if (_state.value.phase != Phase.RINGING_IN) return
        pendingInvite?.respondWith(486, null)
        pendingInvite = null
        _state.value = CallState(Phase.ENDED)
    }

    private fun rtcConfig(): PeerConnection.RTCConfiguration {
        val config = PeerConnection.RTCConfiguration(emptyList())
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        return config
    }

    // -- outbound ---------------------------------------------------

    fun dial(destination: String) {
        if (_state.value.phase != Phase.IDLE) return
        val engine = sip ?: return
        val callMedia = WebRtcEngine.CallMedia(rtcConfig())
        media = callMedia
        callMedia.createOffer { offer ->
            if (offer == null) {
                teardown()
                return@createOffer
            }
            val callId = engine.invite(destination, offer.description) { }
            _state.value = CallState(Phase.DIALLING, callId, destination)
        }
    }

    // -- inbound (from the SIP engine) -------------------------------

    override fun onIncomingCall(invite: SipEngine.IncomingInvite) {
        if (_state.value.phase != Phase.IDLE) {
            invite.respondWith(486, null)
            return
        }
        pendingInvite = invite
        _state.value = CallState(Phase.RINGING_IN, invite.callId, invite.fromDisplay)
    }

    fun answer() {
        val invite = pendingInvite ?: return
        val callMedia = WebRtcEngine.CallMedia(rtcConfig())
        media = callMedia
        callMedia.acceptOfferThenAnswer(SessionDescription(SessionDescription.Type.OFFER, invite.sdp)) { answer ->
            if (answer == null) {
                invite.respondWith(603, null)
                teardown()
                return@acceptOfferThenAnswer
            }
            invite.respondWith(200, answer.description)
            _state.value = _state.value.copy(phase = Phase.ACTIVE, callId = invite.callId)
        }
    }

    fun decline() {
        val invite = pendingInvite ?: return
        invite.respondWith(603, null)
        pendingInvite = null
        _state.value = CallState(Phase.ENDED)
    }

    // -- in-call -----------------------------------------------------

    fun hangUp() {
        val callId = _state.value.callId
        if (callId.isNotEmpty()) sip?.bye(callId)
        pendingInvite = null
        teardown()
    }

    fun toggleMute(): Boolean {
        val muted = !_state.value.muted
        media?.setMicrophoneMuted(muted)
        _state.value = _state.value.copy(muted = muted)
        return muted
    }

    fun toggleHold() {
        val current = _state.value
        if (current.phase != Phase.ACTIVE && current.phase != Phase.HELD) return
        val toHold = current.phase == Phase.ACTIVE
        val callMedia = media ?: return
        callMedia.createOffer { offer ->
            if (offer != null) {
                sip?.reinvite(current.callId, offer.description, toHold)
            }
        }
        _state.value = current.copy(phase = if (toHold) Phase.HELD else Phase.ACTIVE)
    }

    fun sendDtmf(digit: Char) {
        if (_state.value.phase != Phase.ACTIVE) return
        sip?.dtmf(_state.value.callId, digit)
    }

    fun transfer(target: String) {
        if (_state.value.phase == Phase.ACTIVE || _state.value.phase == Phase.HELD) {
            sip?.refer(_state.value.callId, target)
        }
    }

    // -- SipEngine.Listener ------------------------------------------

    override fun onRegistrationState(registered: Boolean) = Unit

    override fun onCallProgress(callId: String, ringing: Boolean) {
        if (_state.value.callId == callId && ringing) {
            _state.value = _state.value.copy(phase = Phase.DIALLING)
        }
    }

    override fun onCallEstablished(callId: String, remoteSdp: String) {
        media?.acceptAnswer(SessionDescription(SessionDescription.Type.ANSWER, remoteSdp))
        _state.value = _state.value.copy(phase = Phase.ACTIVE, callId = callId)
    }

    override fun onCallEnded(callId: String) {
        if (_state.value.callId == callId) {
            pendingInvite = null
            teardown()
        }
    }

    override fun onTransferVerdict(callId: String, verdict: String) {
        if (verdict.startsWith("2")) {
            // Server executed the transfer; our leg is going away.
            hangUp()
        }
    }

    private fun teardown() {
        media?.close()
        media = null
        _state.value = CallState(Phase.ENDED)
    }
}
