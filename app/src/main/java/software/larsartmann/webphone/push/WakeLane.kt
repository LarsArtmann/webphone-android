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
 */
enum class WakeLaneState {
    /** Nothing wired yet — the lane's fate is simply not known. */
    UNKNOWN,

    /** The server accepted this install's wake address AND the ntfy
     *  subscription socket is open. */
    ARMED,

    /** The stack would not take the wake address (push seam off /
     *  registration refused) — closed-app rings are impossible until
     *  the server side changes. */
    REFUSED,

    /** The session watcher tore the lane down (dead session): ringing
     *  is paused until the user opens the app and signs in again. */
    SESSION_DEAD,

    /** The subscription socket is down (transport failure) — the
     *  reconnect loop is trying. */
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
