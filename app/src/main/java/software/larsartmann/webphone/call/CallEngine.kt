package software.larsartmann.webphone.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import software.larsartmann.webphone.sip.SipEngine

/**
 * The one active-call orchestrator: SipEngine (signaling) +
 * WebRtcEngine.CallMedia (audio) + the state the Telecom surfaces and
 * the in-call UI render. Single-call by product decision (a personal
 * extension line) — a second call waits, it never silently replaces.
 * Also owns the SIP registration state (the "connected to the PBX"
 * fact the shell badge and the dialer's call gate render — until this
 * existed, the UI flew blind while unregistered).
 *
 * Thread confinement (the 2026-10-25 audit): state arrives from FOUR
 * worlds — the UI/Telecom threads (dial/answer/hangUp), the WebRTC
 * callback thread (offer/answer results), the OkHttp reader thread
 * (every SipEngine.Listener callback) and CallLayer's IO scope — so
 * every mutation of [media]/[pendingInvite]/the call epoch and every
 * media-method call runs under [callLock]. That lock is what makes
 * teardown idempotent (a double PeerConnection.dispose is a native
 * crash, not an exception) and keeps the offer/answer callbacks from
 * touching a call that was stood down under them. `_state` itself is
 * a StateFlow (safe to read anywhere); writes inside the lock keep
 * read-modify-write sequences atomic.
 */
object CallEngine : SipEngine.Listener {

    enum class Phase { IDLE, DIALLING, RINGING_IN, ACTIVE, HELD, ENDED }

    /** Who ended the call — the Telecom connection renders this as
     *  its DisconnectCause (LOCAL/REMOTE/ERROR), the UI shows it raw. */
    enum class EndedBy { NONE, LOCAL, REMOTE, FAILED }

    /** The SIP registration fact, three states because OFF and
     *  "still wiring" render differently (the dialer must not offer a
     *  Call button that would no-op). The failure cause rides along:
     *  OFF+AUTH_REJECTED ("the PBX refused you") and OFF+OFFLINE
     *  ("no route to the PBX") ask for different fixes. */
    enum class Registration { OFF, CONNECTING, REGISTERED }

    enum class RegistrationFailure { NONE, AUTH_REJECTED, OFFLINE }

    data class RegistrationInfo(
        val status: Registration = Registration.OFF,
        val failure: RegistrationFailure = RegistrationFailure.NONE,
    )

    data class CallState(
        val phase: Phase = Phase.IDLE,
        val callId: String = "",
        val remoteParty: String = "",
        val muted: Boolean = false,
        val endedBy: EndedBy = EndedBy.NONE,
        val startedAtMs: Long = 0,
    )

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state

    private val _registration = MutableStateFlow(RegistrationInfo())
    val registration: StateFlow<RegistrationInfo> = _registration

    /** CallLayer wiring started (session resume in flight) — the badge
     *  earns its CONNECTING look from here, not from the engine. */
    fun registrationWiringStarted() {
        _registration.value = RegistrationInfo(Registration.CONNECTING)
    }

    /** Wiring failed before an engine existed (no session) — the
     *  honest state is OFF, not a forever-CONNECTING badge. */
    fun registrationWiringFailed() {
        _registration.value = RegistrationInfo(Registration.OFF)
    }

    @Volatile private var sip: SipEngine? = null
    private var media: WebRtcEngine.CallMedia? = null
    private var pendingInvite: SipEngine.IncomingInvite? = null
    @Volatile private var cancelledLocally = false

    /** The call epoch: true from the first moment a call claims the
     *  line (inbound ring OR outbound media) until [teardownLocked].
     *  The guard that makes every end-of-call path idempotent. */
    private var callLive = false

    private val callLock = Any()

    /** A line accepts a new call when nothing is ON it — IDLE (never
     *  dialed) and ENDED (the last call's render beat) are both free.
     *  ENDED acceptance is the stuck-line fix: teardown never rewound
     *  to IDLE, so the SECOND call of the day was silently refused
     *  (dial no-op'd, inbound INVITEs got 486'd) until the process
     *  died. The ENDED state stays visible in the UI until the next
     *  call overwrites it. */
    private val Phase.acceptsNewCall: Boolean
        get() = this == Phase.IDLE || this == Phase.ENDED

    fun attach(engine: SipEngine) {
        sip = engine
        engine.listener = this
    }

    fun detach() {
        sip?.listener = null
        sip?.disconnect()
        sip = null
        _registration.value = RegistrationInfo(Registration.OFF)
        synchronized(callLock) { teardownLocked(EndedBy.REMOTE) }
    }

    /** ring_end: the push lane's ring stands down (answered elsewhere,
     *  caller gave up) — refuse any pending invite and end the ringing
     *  state. No-op outside RINGING_IN. */
    fun cancelRinging() {
        synchronized(callLock) {
            if (_state.value.phase != Phase.RINGING_IN) return
            pendingInvite?.respondWith(486, null)
            pendingInvite = null
            teardownLocked(EndedBy.LOCAL)
        }
    }

    private fun rtcConfig(): PeerConnection.RTCConfiguration {
        val config = PeerConnection.RTCConfiguration(emptyList())
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        return config
    }

    // -- outbound ---------------------------------------------------

    fun dial(destination: String) {
        if (!_state.value.phase.acceptsNewCall) return
        cancelledLocally = false
        val engine = sip ?: return
        val callMedia = WebRtcEngine.CallMedia(rtcConfig())
        synchronized(callLock) {
            if (callLive) {
                callMedia.close()
                return
            }
            media = callMedia
            callLive = true
        }
        callMedia.createOffer { offer ->
            synchronized(callLock) {
                // The call may have been stood down while the offer was
                // brewing (hangUp/ring_end): never send, never leak.
                if (!callLive || media !== callMedia) {
                    callMedia.close()
                    return@createOffer
                }
                if (offer == null) {
                    teardownLocked(EndedBy.FAILED)
                    return@createOffer
                }
                val callId = engine.invite(destination, offer.description) { }
                _state.value = CallState(Phase.DIALLING, callId, destination)
            }
        }
    }

    // -- inbound (from the SIP engine) -------------------------------

    override fun onIncomingCall(invite: SipEngine.IncomingInvite) {
        synchronized(callLock) {
            if (!_state.value.phase.acceptsNewCall) {
                invite.respondWith(486, null)
                return
            }
            pendingInvite = invite
            callLive = true
            _state.value = CallState(Phase.RINGING_IN, invite.callId, invite.fromDisplay)
        }
    }

    fun answer() {
        val invite = synchronized(callLock) { pendingInvite } ?: return
        val callMedia = WebRtcEngine.CallMedia(rtcConfig())
        callMedia.acceptOfferThenAnswer(SessionDescription(SessionDescription.Type.OFFER, invite.sdp)) { answer ->
            synchronized(callLock) {
                if (!callLive || pendingInvite !== invite) {
                    callMedia.close()
                    return@acceptOfferThenAnswer
                }
                media = callMedia
                if (answer == null) {
                    invite.respondWith(603, null)
                    teardownLocked(EndedBy.FAILED)
                    return@acceptOfferThenAnswer
                }
                invite.respondWith(200, answer.description)
                _state.value = _state.value.copy(phase = Phase.ACTIVE, callId = invite.callId)
            }
        }
    }

    fun decline() {
        synchronized(callLock) {
            val invite = pendingInvite ?: return
            pendingInvite = null
            invite.respondWith(603, null)
            teardownLocked(EndedBy.LOCAL)
        }
    }

    // -- in-call -----------------------------------------------------

    /** The honest end: CANCEL while the ring is still out (pre-200,
     *  RFC 3261 §9.1), BYE on a confirmed dialog, a decline when an
     *  inbound call is still ringing. Never BYE-before-200 — servers
     *  200-OK the BYE of an unconfirmed dialog and keep ringing. */
    fun hangUp() {
        synchronized(callLock) {
            when (_state.value.phase) {
                Phase.RINGING_IN -> {
                    declineLocked()
                    return
                }
                Phase.DIALLING -> {
                    cancelledLocally = true
                    sip?.cancel(_state.value.callId)
                }
                else -> {
                    val callId = _state.value.callId
                    if (callId.isNotEmpty()) sip?.bye(callId)
                }
            }
            pendingInvite = null
            teardownLocked(EndedBy.LOCAL)
        }
    }

    fun toggleMute(): Boolean = synchronized(callLock) {
        val muted = !_state.value.muted
        media?.setMicrophoneMuted(muted)
        _state.value = _state.value.copy(muted = muted)
        muted
    }

    fun toggleHold() {
        synchronized(callLock) {
            val current = _state.value
            if (current.phase != Phase.ACTIVE && current.phase != Phase.HELD) return
            val toHold = current.phase == Phase.ACTIVE
            val callMedia = media ?: return
            callMedia.createOffer { offer ->
                // SIP is its own confined world (atomic CSeq, concurrent
                // leg map) — safe from this WebRTC callback thread; the
                // leg lookup no-ops once the call is gone.
                if (offer != null) sip?.reinvite(current.callId, offer.description, toHold)
            }
            _state.value = current.copy(phase = if (toHold) Phase.HELD else Phase.ACTIVE)
        }
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

    override fun onRegistrationState(state: SipEngine.RegistrationState) {
        _registration.value = when (state) {
            SipEngine.RegistrationState.REGISTERED ->
                RegistrationInfo(Registration.REGISTERED)
            SipEngine.RegistrationState.AUTH_REJECTED ->
                RegistrationInfo(Registration.OFF, RegistrationFailure.AUTH_REJECTED)
            SipEngine.RegistrationState.OFFLINE ->
                RegistrationInfo(Registration.OFF, RegistrationFailure.OFFLINE)
        }
    }

    override fun onCallProgress(callId: String, ringing: Boolean) {
        _state.update { current ->
            if (current.callId == callId && ringing) current.copy(phase = Phase.DIALLING) else current
        }
    }

    override fun onCallEstablished(callId: String, remoteSdp: String) {
        synchronized(callLock) {
            if (!callLive || _state.value.callId != callId) return
            media?.acceptAnswer(SessionDescription(SessionDescription.Type.ANSWER, remoteSdp))
            _state.value = _state.value.copy(
                phase = Phase.ACTIVE,
                callId = callId,
                // First establishment only — a re-INVITE's 200 (hold/
                // unhold) re-enters here and must not reset the clock.
                startedAtMs =
                    if (_state.value.startedAtMs == 0L) System.currentTimeMillis()
                    else _state.value.startedAtMs,
            )
        }
    }

    override fun onCallEnded(callId: String) {
        synchronized(callLock) {
            if (_state.value.callId == callId) {
                val by = if (cancelledLocally) EndedBy.LOCAL else EndedBy.REMOTE
                cancelledLocally = false
                pendingInvite = null
                teardownLocked(by)
            }
        }
    }

    override fun onTransferVerdict(callId: String, verdict: String) {
        if (verdict.startsWith("2")) {
            // Server executed the transfer; our leg is going away.
            hangUp()
        }
    }

    /** Call under [callLock]: close the media exactly once per call
     *  (double dispose is a native crash), drop the pending invite,
     *  publish ENDED for the render beat. */
    private fun teardownLocked(endedBy: EndedBy) {
        callLive = false
        pendingInvite = null
        val dying = media
        media = null
        dying?.close()
        _state.value = CallState(Phase.ENDED, endedBy = endedBy)
    }

    private fun declineLocked() {
        val invite = pendingInvite ?: return
        pendingInvite = null
        invite.respondWith(603, null)
        teardownLocked(EndedBy.LOCAL)
    }
}
