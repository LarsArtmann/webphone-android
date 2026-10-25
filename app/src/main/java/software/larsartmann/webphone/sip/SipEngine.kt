package software.larsartmann.webphone.sip

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The SIP-over-WSS user agent — the native re-owning of the island's
 * call contract: REGISTER (digest-challenged), outbound INVITE with
 * WebRTC SDP, 180/200/ACK, hold re-INVITE (sendonly/sendrecv), BYE,
 * DTMF over INFO (`application/dtmf-relay`, the EQUALS form — the
 * colon form is 200-OK'd by FreeSWITCH and silently dropped), REFER
 * with NOTIFY sipfrag verdict parsing, and the bounded watchdog the
 * web island learned the hard way (sip.js 0.x hangs in reconnect; the
 * engine must re-REGISTER explicitly and set UI state explicitly —
 * the socket layer never does it for us).
 *
 * v0 (this scaffold): REGISTER + outbound call + BYE + DTMF + hold
 * are implemented; incoming INVITE answering and REFER/NOTIFY ride
 * the same dispatch and are marked UNTESTED against live FreeSWITCH
 * until the first device train.
 */
class SipEngine(
    private val scope: CoroutineScope,
    private val config: Config,
    /** Test seam: when set, every outbound frame is handed here
     *  instead of the websocket (the protocol tests ride it). */
    private val sender: ((String) -> Unit)? = null,
) {

    data class Config(
        val wsUrl: String,
        val sipDomain: String,
        val extension: String,
        val password: String,
        val displayName: String = "WebPhone",
    )

    interface Listener {
        /** The registration verdict: REGISTERED (live), AUTH_REJECTED
         *  (the PBX refused our credentials — distinct from a network
         *  outage so the UI can say WHICH lie it told), OFFLINE. */
        fun onRegistrationState(state: RegistrationState)
        fun onIncomingCall(invite: IncomingInvite)
        fun onCallProgress(callId: String, ringing: Boolean)
        fun onCallEstablished(callId: String, remoteSdp: String)
        fun onCallEnded(callId: String)
        /** A NOTIFY sipfrag body from a REFER we sent: the transfer
         *  verdict (SIP/2.0 200 OK = success; the server executes the
         *  transfer — we only report the verdict). */
        fun onTransferVerdict(callId: String, verdict: String)
    }

    enum class RegistrationState { REGISTERED, AUTH_REJECTED, OFFLINE }

    data class IncomingInvite(
        val callId: String,
        val fromUser: String,
        val fromDisplay: String,
        val sdp: String,
        val respondWith: (status: Int, sdp: String?) -> Unit,
    )

    var listener: Listener? = null

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private val cseq = AtomicLong(0)
    private val localTag = UUID.randomUUID().toString().substring(0, 8)
    private val callLegs = ConcurrentHashMap<String, Leg>()
    private var refreshJob: Job? = null
    private var watchdogJob: Job? = null
    @Volatile private var registered = false
    @Volatile private var lastActivity = 0L

    private class Leg(
        val callId: String,
        /** Mutated from the caller thread (re-INVITE) while the OkHttp
         *  reader thread dispatches responses — volatile keeps the
         *  ladder's CSeq/Via/To coherent across the race. */
        @Volatile var inviteCseq: Long,
        @Volatile var remoteTarget: String,
        @Volatile var fromHeader: String,
        @Volatile var toHeader: String,
        /** The INVITE's own Via (branch included): CANCEL must reuse it
         *  verbatim, or the server matches the CANCEL to no transaction
         *  and the ring-out never stops. */
        @Volatile var inviteVia: String,
        /** A CANCEL after a final response is a protocol crime — the
         *  dialog is confirmed, only BYE may end it. */
        @Volatile var confirmed: Boolean,
        /** The WebRTC offer this leg was dialed with: the INVITE-challenge
         *  re-INVITE must present the SAME SDP (a fresh offer would
         *  renegotiate ICE under a dialog the server never agreed to). */
        val offerSdp: String?,
        /** 401/407 challenges seen on this leg's INVITE ladder. */
        var authChallenges: Int = 0,
    )

    // -- lifecycle -------------------------------------------------

    fun connect() {
        disconnect()
        val request = Request.Builder().url(config.wsUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "wss open")
                register()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastActivity = System.currentTimeMillis()
                receive(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "wss failure: ${t.message}")
                onLinkLost()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "wss closed: $code")
                onLinkLost()
            }
        })
    }

    fun disconnect() {
        refreshJob?.cancel()
        watchdogJob?.cancel()
        socket?.close(1000, "shutdown")
        socket = null
        setRegistration(RegistrationState.OFFLINE)
    }

    /** Test seam for the silence decision: the registration is up but
     *  the wire has carried nothing for longer than the threshold. A
     *  LIVE socket always answers the expires/2 refresh (300 s), so
     *  anything quieter than 6 minutes is a wedged link the callbacks
     *  will never report (the island's silent-death lesson, ported). */
    internal fun isSilent(nowMs: Long): Boolean =
        registered && nowMs - lastActivity > SILENCE_THRESHOLD_MS

    /**
     * The bounded watchdog: the registration must be REFRESHED
     * (expires/2) and REBUILT on silence — a wedged socket that never
     * fires callbacks must not leave the UI believing it is reachable
     * (the island's connection.js lesson, ported).
     */
    private fun onLinkLost() {
        setRegistration(RegistrationState.OFFLINE)
        startReconnectBackoff()
    }

    private fun startReconnectBackoff() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(RECONNECT_BACKOFF_MS)
            connect()
        }
    }

    /** The silence half of the watchdog: poll the last wire activity;
     *  a registered-but-silent socket gets rebuilt (reconnect implies a
     *  fresh REGISTER — the UI state follows the 200, never the
     *  socket). Runs instead of the backoff job once the registration
     *  is confirmed. */
    private fun startSilenceWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(SILENCE_POLL_MS)
                if (isSilent(System.currentTimeMillis())) {
                    Log.w(TAG, "socket silent > ${SILENCE_THRESHOLD_MS}ms — rebuilding")
                    watchdogJob = null
                    connect()
                    return@launch
                }
            }
        }
    }

    private fun setRegistration(state: RegistrationState) {
        val registeredNow = state == RegistrationState.REGISTERED
        if (registered == registeredNow && lastReported == state) return
        registered = registeredNow
        lastReported = state
        listener?.onRegistrationState(state)
    }

    @Volatile private var lastReported: RegistrationState? = null

    // -- REGISTER --------------------------------------------------

    private fun register(authorization: String? = null) {
        val uri = "sip:${config.sipDomain}"
        val seq = cseq.incrementAndGet()
        val callId = UUID.randomUUID().toString()
        val request = SipMessage(
            isRequest = true, method = "REGISTER", uri = uri, statusCode = 0, reason = "",
            headers = buildList {
                add(SipHeader("Via", "SIP/2.0/WSS ${hostToken()};branch=z9hG4bK${UUID.randomUUID().toString().take(12)};rport"))
                add(SipHeader("Max-Forwards", "70"))
                add(SipHeader("From", "<sip:${config.extension}@${config.sipDomain}>;tag=$localTag"))
                add(SipHeader("To", "<sip:${config.extension}@${config.sipDomain}>"))
                add(SipHeader("Call-ID", callId))
                add(SipHeader("CSeq", "$seq REGISTER"))
                add(SipHeader("Contact", "<sip:${config.extension}@${hostToken()};transport=wss>;expires=$REGISTER_EXPIRES"))
                add(SipHeader("Allow", "INVITE, ACK, CANCEL, BYE, REFER, INFO, NOTIFY, OPTIONS"))
                if (authorization != null) add(SipHeader("Authorization", authorization))
            },
            body = "",
        )
        send(request)
    }

    /** Auth challenges answered on the CURRENT registration ladder —
     *  bounded so a wrong password cannot loop REGISTERs forever. */
    @Volatile private var registerAuthChallenges = 0

    // -- outbound call ---------------------------------------------

    fun invite(destination: String, offerSdp: String, onLocalAnswerNeeded: (callId: String) -> Unit): String {
        val callId = UUID.randomUUID().toString()
        val seq = cseq.incrementAndGet()
        val target = "sip:$destination@${config.sipDomain}"
        val via = "SIP/2.0/WSS ${hostToken()};branch=z9hG4bK${UUID.randomUUID().toString().take(12)};rport"
        val leg = Leg(
            callId = callId,
            inviteCseq = seq,
            remoteTarget = target,
            fromHeader = "\"${config.displayName}\" <sip:${config.extension}@${config.sipDomain}>;tag=${UUID.randomUUID().toString().take(8)}",
            toHeader = "<$target>",
            inviteVia = via,
            confirmed = false,
            offerSdp = offerSdp,
        )
        callLegs[callId] = leg
        val request = SipMessage(
            isRequest = true, method = "INVITE", uri = target, statusCode = 0, reason = "",
            headers = buildList {
                add(SipHeader("Via", via))
                add(SipHeader("Max-Forwards", "70"))
                add(SipHeader("From", leg.fromHeader))
                add(SipHeader("To", leg.toHeader))
                add(SipHeader("Call-ID", callId))
                add(SipHeader("CSeq", "$seq INVITE"))
                add(SipHeader("Contact", "<sip:${config.extension}@${hostToken()};transport=wss>"))
                add(SipHeader("Content-Type", "application/sdp"))
            },
            body = offerSdp,
        )
        send(request)
        return callId
    }

    fun ack(callId: String, toHeaderOverride: String? = null) {
        val leg = callLegs[callId] ?: return
        send(
            SipMessage(
                isRequest = true, method = "ACK", uri = leg.remoteTarget, statusCode = 0, reason = "",
                headers = buildList {
                    add(SipHeader("Via", leg.inviteVia))
                    add(SipHeader("Max-Forwards", "70"))
                    add(SipHeader("From", leg.fromHeader))
                    add(SipHeader("To", toHeaderOverride ?: leg.toHeader))
                    add(SipHeader("Call-ID", callId))
                    add(SipHeader("CSeq", "${leg.inviteCseq} ACK"))
                },
                body = "",
            ),
        )
    }

    /** Hold/resume re-INVITE: sendonly pauses the stream toward the
     *  network (no silence to hallucinate on, on the ASR seam too). */
    fun reinvite(callId: String, sdp: String, hold: Boolean) {
        val leg = callLegs[callId] ?: return
        val seq = cseq.incrementAndGet()
        leg.inviteCseq = seq
        leg.inviteVia = "SIP/2.0/WSS ${hostToken()};branch=z9hG4bK${UUID.randomUUID().toString().take(12)};rport"
        val direction = if (hold) "sendonly" else "sendrecv"
        val munged = sdp.replace(Regex("a=(sendrecv|sendonly|recvonly|inactive)"), "a=$direction")
        send(
            SipMessage(
                isRequest = true, method = "INVITE", uri = leg.remoteTarget, statusCode = 0, reason = "",
                headers = buildList {
                    add(SipHeader("Via", leg.inviteVia))
                    add(SipHeader("Max-Forwards", "70"))
                    add(SipHeader("From", leg.fromHeader))
                    add(SipHeader("To", leg.toHeader))
                    add(SipHeader("Call-ID", callId))
                    add(SipHeader("CSeq", "$seq INVITE"))
                    add(SipHeader("Contact", "<sip:${config.extension}@${hostToken()};transport=wss>"))
                    add(SipHeader("Content-Type", "application/sdp"))
                },
                body = munged,
            ),
        )
    }

    /** Ring-out abort: CANCEL the pending INVITE (same Call-ID, same
     *  CSeq number with method CANCEL, the INVITE's own Via, and the
     *  To tag from any provisional response — RFC 3261 §9.1). After a
     *  final response the guard refuses: only BYE may end a confirmed
     *  dialog. Unknown/removed leg: no-op. */
    fun cancel(callId: String) {
        val leg = callLegs[callId] ?: return
        if (leg.confirmed) return
        send(
            SipMessage(
                isRequest = true, method = "CANCEL", uri = leg.remoteTarget, statusCode = 0, reason = "",
                headers = buildList {
                    add(SipHeader("Via", leg.inviteVia))
                    add(SipHeader("Max-Forwards", "70"))
                    add(SipHeader("From", leg.fromHeader))
                    add(SipHeader("To", leg.toHeader))
                    add(SipHeader("Call-ID", leg.callId))
                    add(SipHeader("CSeq", "${leg.inviteCseq} CANCEL"))
                },
                body = "",
            ),
        )
    }

    fun bye(callId: String) {
        val leg = callLegs.remove(callId) ?: return
        val seq = cseq.incrementAndGet()
        send(
            SipMessage(
                isRequest = true, method = "BYE", uri = leg.remoteTarget, statusCode = 0, reason = "",
                headers = buildList {
                    add(SipHeader("Via", "SIP/2.0/WSS ${hostToken()};branch=z9hG4bK${UUID.randomUUID().toString().take(12)};rport"))
                    add(SipHeader("Max-Forwards", "70"))
                    add(SipHeader("From", leg.fromHeader))
                    add(SipHeader("To", leg.toHeader))
                    add(SipHeader("Call-ID", callId))
                    add(SipHeader("CSeq", "$seq BYE"))
                },
                body = "",
            ),
        )
    }

    /** DTMF: the EQUALS form of application/dtmf-relay. Never the
     *  colon form — FreeSWITCH 200-OKs it and drops the digit. */
    fun dtmf(callId: String, digit: Char) {
        val leg = callLegs[callId] ?: return
        val seq = cseq.incrementAndGet()
        val body = "Signal=$digit\r\nDuration=160"
        send(
            SipMessage(
                isRequest = true, method = "INFO", uri = leg.remoteTarget, statusCode = 0, reason = "",
                headers = buildList {
                    add(SipHeader("Via", "SIP/2.0/WSS ${hostToken()};branch=z9hG4bK${UUID.randomUUID().toString().take(12)};rport"))
                    add(SipHeader("Max-Forwards", "70"))
                    add(SipHeader("From", leg.fromHeader))
                    add(SipHeader("To", leg.toHeader))
                    add(SipHeader("Call-ID", callId))
                    add(SipHeader("CSeq", "$seq INFO"))
                    add(SipHeader("Content-Type", "application/dtmf-relay"))
                },
                body = body,
            ),
        )
    }

    /** Attended/blind transfer: REFER; the server executes it, the
     *  verdict arrives as NOTIFY sipfrag (see the dispatch below).
     *  UNTESTED against live FreeSWITCH (v0 marker). */
    fun refer(callId: String, target: String) {
        val leg = callLegs[callId] ?: return
        val seq = cseq.incrementAndGet()
        send(
            SipMessage(
                isRequest = true, method = "REFER", uri = leg.remoteTarget, statusCode = 0, reason = "",
                headers = buildList {
                    add(SipHeader("Via", "SIP/2.0/WSS ${hostToken()};branch=z9hG4bK${UUID.randomUUID().toString().take(12)};rport"))
                    add(SipHeader("Max-Forwards", "70"))
                    add(SipHeader("From", leg.fromHeader))
                    add(SipHeader("To", leg.toHeader))
                    add(SipHeader("Call-ID", callId))
                    add(SipHeader("CSeq", "$seq REFER"))
                    add(SipHeader("Refer-To", "<sip:$target@${config.sipDomain}>"))
                    add(SipHeader("Supported", "replaces"))
                },
                body = "",
            ),
        )
    }

    // -- inbound dispatch ------------------------------------------

    internal fun receive(raw: String) {
        val message = SipMessage.parse(raw) ?: return
        if (message.isRequest) {
            receiveRequest(message)
        } else {
            receiveResponse(message)
        }
    }

    private fun receiveRequest(message: SipMessage) {
        when (message.method) {
            "INVITE" -> receiveInvite(message)
            "BYE" -> {
                respond(message, 200)
                val callId = message.callId()
                callLegs.remove(callId)
                listener?.onCallEnded(callId)
            }
            "NOTIFY" -> {
                // sipfrag body carries the REFER verdict
                // ("Content-Type: message/sipfrag;version=2.0").
                respond(message, 200)
                val sipfrag = message.body.lineSequence()
                    .firstOrNull { it.startsWith("SIP/2.0") }
                if (sipfrag != null) {
                    listener?.onTransferVerdict(message.callId(), sipfrag.removePrefix("SIP/2.0").trim())
                }
            }
            "INFO" -> respond(message, 200)
            "OPTIONS" -> respond(message, 200)
            "ACK" -> Unit
            else -> respond(message, 200)
        }
    }

    private fun receiveInvite(message: SipMessage) {
        val from = message.header("From").orEmpty()
        val user = Regex("sip:([^@>]+)").find(from)?.groupValues?.get(1).orEmpty()
        val display = Regex("\"([^\"]*)\"").find(from)?.groupValues?.get(1).orEmpty()
        listener?.onIncomingCall(
            IncomingInvite(
                callId = message.callId(),
                fromUser = user,
                fromDisplay = display.ifEmpty { user },
                sdp = message.body,
                respondWith = { status, sdp ->
                    respondToInvite(message, status, sdp)
                },
            ),
        )
    }

    private fun respondToInvite(invite: SipMessage, status: Int, sdp: String?) {
        val headers = buildList {
            add(SipHeader("Via", invite.headersOf("Via").joinToString(", ")))
            add(SipHeader("From", invite.header("From").orEmpty()))
            add(SipHeader("To", invite.header("To").orEmpty() + ";tag=${UUID.randomUUID().toString().take(8)}"))
            add(SipHeader("Call-ID", invite.callId()))
            add(SipHeader("CSeq", invite.header("CSeq").orEmpty()))
            add(SipHeader("Contact", "<sip:${config.extension}@${hostToken()};transport=wss>"))
            if (sdp != null) add(SipHeader("Content-Type", "application/sdp"))
        }
        send(
            SipMessage(
                isRequest = false, method = "", uri = "", statusCode = status,
                reason = reasonPhrase(status), headers = headers, body = sdp.orEmpty(),
            ),
        )
        if (status >= 200) {
            callLegs[invite.callId()] = Leg(
                callId = invite.callId(),
                inviteCseq = invite.cseq()?.first ?: 1,
                remoteTarget = invite.uri,
                fromHeader = invite.header("To").orEmpty(),
                toHeader = invite.header("From").orEmpty(),
                inviteVia = "",
                confirmed = true,
                offerSdp = null,
            )
        }
    }

    private fun receiveResponse(message: SipMessage) {
        val callId = message.callId()
        val cseq = message.cseq()
        when {
            // REGISTER ladder: challenge → authorized → registered.
            cseq?.second == "REGISTER" -> when (message.statusCode) {
                401, 407 -> {
                    // Bounded: a wrong password would otherwise loop
                    // REGISTER/401 forever (the wire floods, the badge
                    // flickers). Two challenges, three when the last
                    // challenge says stale=true (only the nonce was
                    // old — the retry is guaranteed final).
                    registerAuthChallenges += 1
                    val challenge = Digest.parseChallenge(
                        message.header("WWW-Authenticate") ?: message.header("Proxy-Authenticate").orEmpty(),
                    )
                    val limit = if (challenge?.stale == true) 3 else 2
                    if (registerAuthChallenges > limit || challenge == null) {
                        setRegistration(RegistrationState.AUTH_REJECTED)
                        return
                    }
                    val authorization = Digest.authorization(
                        method = "REGISTER",
                        uri = "sip:${config.sipDomain}",
                        username = config.extension,
                        password = config.password,
                        challenge = challenge,
                    )
                    register(authorization)
                }
                200 -> {
                    registerAuthChallenges = 0
                    setRegistration(RegistrationState.REGISTERED)
                    scheduleRefresh()
                    startSilenceWatchdog()
                }
                else -> setRegistration(RegistrationState.OFFLINE)
            }
            // INVITE ladder: ring → answer → ACK; a digest challenge
            // re-INVITEs with credentials; other finals tear down.
            cseq?.second == "INVITE" -> {
                val leg = callLegs[callId]
                when {
                    message.statusCode == 180 || message.statusCode == 183 -> {
                        // The provisional response's To tag belongs to
                        // this dialog now (CANCEL/BYE must carry it).
                        if (leg != null) {
                            message.header("To")?.let { leg.toHeader = it }
                        }
                        listener?.onCallProgress(callId, ringing = true)
                    }
                    message.statusCode == 200 -> {
                        leg?.confirmed = true
                        if (leg != null) {
                            leg.toHeader = message.header("To") ?: leg.toHeader
                            val contact = message.header("Contact")
                            if (contact != null) leg.remoteTarget = contact.substringAfter('<').substringBefore('>')
                        }
                        ack(callId, toHeaderOverride = message.header("To"))
                        listener?.onCallEstablished(callId, message.body)
                    }
                    message.statusCode == 401 || message.statusCode == 407 -> {
                        // PBXs that challenge the INVITE (not just
                        // REGISTER) — answer with credentials, same
                        // dialog, same SDP, new transaction.
                        if (leg != null) {
                            ack(callId, toHeaderOverride = message.header("To"))
                            reinviteWithAuth(leg, message, message.statusCode)
                        }
                    }
                    message.statusCode >= 300 -> {
                        // A UAC must ACK non-2xx finals too (RFC 3261
                        // §17.1.1.2) — the 487 after our CANCEL is the
                        // everyday case; without the ACK FreeSWITCH
                        // keeps the INVITE transaction open.
                        if (leg != null) ack(callId, toHeaderOverride = message.header("To"))
                        callLegs.remove(callId)
                        listener?.onCallEnded(callId)
                    }
                }
            }
            // BYE/REFER/INFO/CANCEL responses need no ladder (fire-and-confirm).
        }
    }

    /** The INVITE-challenge rung: answer 401/407 with a fresh INVITE
     *  carrying the digest credentials (WWW- vs Proxy- flavor), the
     *  SAME offer SDP, and a new CSeq/Via (a new transaction in the
     *  same dialog — RFC 3261 §13.2.2 / §22.1). Bounded like REGISTER:
     *  two challenges, three when stale=true; exhaustion ends the call
     *  instead of looping. */
    private fun reinviteWithAuth(leg: Leg, challengeResponse: SipMessage, status: Int) {
        val challengeHeader = if (status == 407) "Proxy-Authenticate" else "WWW-Authenticate"
        val challenge = Digest.parseChallenge(challengeResponse.header(challengeHeader).orEmpty())
        leg.authChallenges += 1
        if (challenge == null || leg.offerSdp == null || leg.authChallenges > (if (challenge.stale) 3 else 2)) {
            callLegs.remove(leg.callId)
            listener?.onCallEnded(leg.callId)
            return
        }
        val seq = cseq.incrementAndGet()
        leg.inviteCseq = seq
        leg.inviteVia = "SIP/2.0/WSS ${hostToken()};branch=z9hG4bK${UUID.randomUUID().toString().take(12)};rport"
        message.header("To")?.let { leg.toHeader = it }
        val authHeader = if (status == 407) "Proxy-Authorization" else "Authorization"
        val authorization = Digest.authorization(
            method = "INVITE",
            uri = leg.remoteTarget,
            username = config.extension,
            password = config.password,
            challenge = challenge,
        )
        send(
            SipMessage(
                isRequest = true, method = "INVITE", uri = leg.remoteTarget, statusCode = 0, reason = "",
                headers = buildList {
                    add(SipHeader("Via", leg.inviteVia))
                    add(SipHeader("Max-Forwards", "70"))
                    add(SipHeader("From", leg.fromHeader))
                    add(SipHeader("To", leg.toHeader))
                    add(SipHeader("Call-ID", leg.callId))
                    add(SipHeader("CSeq", "$seq INVITE"))
                    add(SipHeader("Contact", "<sip:${config.extension}@${hostToken()};transport=wss>"))
                    add(SipHeader(authHeader, authorization))
                    add(SipHeader("Content-Type", "application/sdp"))
                },
                body = leg.offerSdp,
            ),
        )
    }

    private fun respond(request: SipMessage, status: Int) {
        send(
            SipMessage(
                isRequest = false, method = "", uri = "", statusCode = status,
                reason = reasonPhrase(status),
                headers = buildList {
                    add(SipHeader("Via", request.headersOf("Via").joinToString(", ")))
                    add(SipHeader("From", request.header("From").orEmpty()))
                    add(SipHeader("To", request.header("To").orEmpty()))
                    add(SipHeader("Call-ID", request.callId()))
                    add(SipHeader("CSeq", request.header("CSeq").orEmpty()))
                },
                body = "",
            ),
        )
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(REGISTER_EXPIRES * 1000L / 2)
            if (registered) register() else connect()
        }
    }

    private fun send(message: SipMessage) {
        val raw = message.render()
        if (sender != null) {
            sender.invoke(raw)
        } else {
            socket?.send(raw)
        }
    }

    private fun hostToken(): String = UUID.randomUUID().toString().take(12) + ".invalid"

    private companion object {
        const val TAG = "SipEngine"
        const val REGISTER_EXPIRES = 600
        const val RECONNECT_BACKOFF_MS = 3_000L

        /** Silence watchdog: a live socket answers the expires/2 refresh
         *  within seconds, so 6 quiet minutes is a dead link; polled
         *  every 30 s. */
        const val SILENCE_THRESHOLD_MS = 6 * 60_000L
        const val SILENCE_POLL_MS = 30_000L

        fun reasonPhrase(status: Int): String = when (status) {
            100 -> "Trying"
            180 -> "Ringing"
            183 -> "Session Progress"
            200 -> "OK"
            486 -> "Busy Here"
            487 -> "Request Terminated"
            500 -> "Server Internal Error"
            603 -> "Decline"
            else -> ""
        }
    }
}
