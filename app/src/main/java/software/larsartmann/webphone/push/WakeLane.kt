package software.larsartmann.webphone.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The wake lane's honest state — "will this phone ring when a call
 * arrives while the shell is closed". The SIP badge answers "will it
 * ring while open"; this answers the OTHER half, the one whose
 * failures were logcat-only before (a seam-off server, a refused
 * registration, a dead session, a dropped socket all looked
 * identical: silence).
 *
 * The REFUSED variants name the server's own refusal class (the
 * push-device registry's documented statuses): a 404 is the seam
 * being OFF, a 403 is the session/CSRF gate, a 422 is an invalid
 * address or a full device list. Each asks for a DIFFERENT fix on a
 * different side, so each gets its own verdict and reason.
 */
enum class WakeLaneState {
    /** Nothing wired yet — the lane's fate is simply not known. */
    UNKNOWN,

    /** The wire has begun deciding the lane's fate: the subscription
     *  socket is connecting and/or the wake address is being
     *  registered. A definite verdict follows. */
    STARTING,

    /** The server accepted this install's wake address AND the ntfy
     *  subscription socket is open. */
    ARMED,

    /** The server answered 404 for the wake address: the push seam is
     *  OFF there. No device can ring while closed until the operator
     *  enables it. */
    REFUSED_SEAM_OFF,

    /** The server answered 422: the wake address was rejected as
     *  invalid — or the account's device list is full (the server
     *  merges both into 422). */
    REFUSED_INVALID,

    /** The server answered 403: the session or the CSRF token was
     *  rejected. Signing in again usually fixes it. */
    REFUSED_AUTH,

    /** Any other refusal the server named (5xx, unexpected status) —
     *  accepted-here-unexplained-there. */
    REFUSED_OTHER,

    /** The session watcher tore the lane down (dead session): ringing
     *  is paused until the user opens the app and signs in again. */
    SESSION_DEAD,

    /** The subscription socket is down (transport failure), or the
     *  server could not be reached at all — the reconnect loop is
     *  trying. */
    DISCONNECTED,
}

/**
 * The process-wide wake-lane ledger. The ntfy service and the call
 * layer both write (they own disjoint transitions); the shell reads.
 * An object like the other engines/seams — the lane is per-process,
 * not per-call.
 */
object WakeLane {
    private val _state = MutableStateFlow(WakeLaneState.UNKNOWN)

    /** Read-mostly: the shell collects this for the line pill. */
    val state: StateFlow<WakeLaneState> = _state.asStateFlow()

    /** The writers' single seam (production writes AND the pins that
     *  prove them). */
    fun report(state: WakeLaneState) {
        _state.value = state
    }
}
