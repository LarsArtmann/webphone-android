package software.larsartmann.webphone.call

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telecom.CallAttributes
import android.telecom.CallControl
import android.telecom.CallControlCallback
import android.telecom.CallEventCallback
import android.telecom.CallException
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.function.Consumer

/**
 * The outbound Telecom bridge for the MODERN VoIP API (API 34+):
 * `TelecomManager.addCall` — the classic self-managed outgoing entry
 * (`placeCall(handle, uri, extras)`) is GONE from the API 36 stubs,
 * and `onCreateOutgoingConnection` therefore can never fire. This is
 * the path that makes BT/watches/audio-routing see an outbound call
 * on every current device.
 *
 * On API 31-33 the app dials DIRECTLY through CallEngine (no OS call
 * surface outbound — the honest degradation; inbound stays fully
 * integrated on every version via the classic ConnectionService).
 */
object TelecomOutgoing {

    private const val TAG = "TelecomOutgoing"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var control: CallControl? = null
    private var watcher: Job? = null

    /** AttachGuard-style generation: Telecom's addCall result is async,
     *  so a superseded placement's late callback (or its state watcher)
     *  must never touch the CURRENT session's bookkeeping — stale
     *  sessions disconnect themselves instead of orphaning. */
    @Volatile private var generation = 0

    /** Place `number` through Telecom's addCall session. Returns
     *  false when the API is unavailable (the caller falls back to a
     *  direct dial). The engine only dials AFTER Telecom accepts the
     *  session — a Telecom rejection falls back to a direct dial too,
     *  so the user's tap always rings somewhere. */
    fun place(context: Context, number: String): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        val appContext = context.applicationContext
        val attributes = CallAttributes.Builder(
            CallConnectionService.accountHandle(appContext),
            CallAttributes.AUDIO_CALL,
            number,
            Uri.fromParts("webphone", number, null),
        )
            .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
            .build()
        generation += 1
        val gen = generation
        telecom.addCall(
            attributes,
            appContext.mainExecutor,
            object : android.os.OutcomeReceiver<CallControl, CallException> {
                override fun onResult(result: CallControl) {
                    onCallControlReady(appContext, result, number, gen)
                }

                override fun onError(error: CallException) {
                    Log.w(TAG, "telecom addCall rejected: ${error.message} — direct dial fallback")
                    CallEngine.dial(number)
                }
            },
            object : CallControlCallback {
                override fun onAnswer(videoState: Int, wasAccepted: Consumer<Boolean>) {
                    // Outgoing session — nothing to answer.
                    wasAccepted.accept(false)
                }

                override fun onDisconnect(cause: DisconnectCause, wasAccepted: Consumer<Boolean>) {
                    wasAccepted.accept(true)
                    CallEngine.hangUp()
                }

                override fun onSetActive(wasAccepted: Consumer<Boolean>) {
                    wasAccepted.accept(true)
                    // The OS resumed the call (BT unhold): mirror it.
                    if (CallEngine.state.value.phase == CallEngine.Phase.HELD) {
                        CallEngine.toggleHold()
                    }
                }

                override fun onSetInactive(wasAccepted: Consumer<Boolean>) {
                    wasAccepted.accept(true)
                    // The OS held the call (BT hold): mirror it.
                    if (CallEngine.state.value.phase == CallEngine.Phase.ACTIVE) {
                        CallEngine.toggleHold()
                    }
                }

                override fun onCallStreamingStarted(wasAccepted: Consumer<Boolean>) {
                    // Not a product capability (single voice line).
                    wasAccepted.accept(false)
                }
            },
            object : CallEventCallback {
                override fun onAvailableCallEndpointsChanged(endpoints: MutableList<android.telecom.CallEndpoint>) = Unit
                override fun onCallEndpointChanged(endpoint: android.telecom.CallEndpoint) = Unit
                override fun onCallStreamingFailed(reason: Int) = Unit
                override fun onMuteStateChanged(muted: Boolean) = Unit
                override fun onEvent(event: String, extras: android.os.Bundle) = Unit
            },
        )
        return true
    }

    private fun onCallControlReady(appContext: Context, callControl: CallControl, number: String, gen: Int) {
        if (gen != generation) {
            // A newer placement superseded this session — disconnect it
            // so Telecom never holds an orphan the UI has forgotten.
            callControl.disconnect(
                DisconnectCause(DisconnectCause.LOCAL),
                appContext.mainExecutor,
                failure("disconnect-superseded"),
            )
            return
        }
        control = callControl
        CallEngine.dial(number)
        watcher?.cancel()
        watcher = scope.launch {
            CallEngine.state.collect { state ->
                when (state.phase) {
                    CallEngine.Phase.ACTIVE -> callControl.setActive(
                        appContext.mainExecutor,
                        failure("setActive"),
                    )
                    CallEngine.Phase.HELD -> callControl.setInactive(
                        appContext.mainExecutor,
                        failure("setInactive"),
                    )
                    CallEngine.Phase.ENDED -> {
                        callControl.disconnect(
                            DisconnectCause(
                                when (state.endedBy) {
                                    CallEngine.EndedBy.LOCAL -> DisconnectCause.LOCAL
                                    CallEngine.EndedBy.FAILED -> DisconnectCause.ERROR
                                    else -> DisconnectCause.REMOTE
                                },
                            ),
                            appContext.mainExecutor,
                            failure("disconnect"),
                        )
                        if (gen == generation) cleanup() else coroutineContext[Job]?.cancel()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun failure(operation: String): android.os.OutcomeReceiver<Void, CallException> =
        object : android.os.OutcomeReceiver<Void, CallException> {
            override fun onResult(result: Void) = Unit

            override fun onError(error: CallException) {
                Log.w(TAG, "telecom $operation failed: ${error.message}")
            }
        }

    private fun cleanup() {
        watcher?.cancel()
        watcher = null
        control = null
        scope.coroutineContext[Job]?.cancelChildren()
    }
}
