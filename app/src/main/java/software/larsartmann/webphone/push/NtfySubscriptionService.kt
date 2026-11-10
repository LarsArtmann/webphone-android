package software.larsartmann.webphone.push

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import java.util.concurrent.TimeUnit

/**
 * The ntfy lane: a low-cost dataSync foreground service holding the
 * websocket subscription to the self-hosted ntfy server. The server's
 * push seam publishes the ring fact as a compact-JSON message on the
 * device's unguessable topic; this service parses it and hands it to
 * the SAME RingRouter the FCM lane uses.
 *
 * Doze/battery-manager variance is the accepted trade (owner decision
 * 2026-10-23: BOTH transports) — the reconnect loop is bounded and
 * backs off exponentially, the web island's watchdog lesson applied.
 */
class NtfySubscriptionService : Service() {
    private var socket: WebSocket? = null
    private var reconnectAttempts = 0

    @Volatile private var reconnecting = false

    /** Set by the explicit stop path AND onDestroy: the pending
     *  reconnect thread must not resurrect the subscription after the
     *  service was told to die (a stop leak — the socket came back and
     *  rang forever with no owner). */
    @Volatile private var stopped = false

    /** The DISCONNECTED flicker grace: a socket flap that heals
     *  within this window never surfaces as "wake off" — the pill
     *  would strobe on every reconnect otherwise (the flap ends in
     *  ARMED again; the verdict the user never needed to see). The
     *  grace thread re-checks the socket at wake time, so a healed
     *  link suppresses the report entirely. Internal: the pin drives
     *  the window with a much shorter value. */
    internal var disconnectGraceMs = 2_500L

    /** Test seam: the reconnect backoff's base (the flicker pin makes
     *  the heal land INSIDE the grace window; production keeps 1 s). */
    internal var reconnectBackoffBaseMs = 1_000L

    /** Set while a grace thread is pending — a second failure inside
     *  the window must not stack a second thread. */
    @Volatile private var disconnectGracePending = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notification: Notification =
            Notification
                .Builder(this, App.CHANNEL_PUSH)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(getString(R.string.ntfy_subscription_active))
                .setOngoing(true)
                .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        if (intent?.action == ACTION_STOP) {
            stopped = true
            socket?.close(1000, "stopped")
            socket = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        stopped = false

        // Sticky restart delivers a NULL intent — the server/topic
        // must survive in prefs or the de-Googled push lane dies
        // silently on the first system restart (the v0.1 defect).
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val server = intent?.getStringExtra(EXTRA_SERVER) ?: prefs.getString(KEY_SERVER, null)
        val topic = intent?.getStringExtra(EXTRA_TOPIC) ?: prefs.getString(KEY_TOPIC, null)
        if (server.isNullOrEmpty() || topic.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        prefs
            .edit()
            .putString(KEY_SERVER, server)
            .putString(KEY_TOPIC, topic)
            .apply()
        subscribe(server, topic)
        return START_STICKY
    }

    private fun subscribe(
        server: String,
        topic: String,
    ) {
        // Idempotent: start() is re-invoked on every MainActivity
        // resume; one socket per service instance is the contract.
        if (socket != null) return
        // The verdict journey is visible from the first millisecond:
        // an open-then-armed socket shows STARTING in between instead
        // of the UNKNOWN void (register-204-but-socket-dead showed NO
        // verdict at all before). The reconnect path re-enters here
        // too — via the refusal-respecting helper, since a reconnect
        // must not clear a standing register refusal.
        reportSocketState(WakeLaneState.STARTING)
        val client =
            OkHttpClient
                .Builder()
                .pingInterval(25, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()
        val request =
            Request
                .Builder()
                .url(subscriptionUrl(server, topic))
                .build()
        socket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        Log.i(TAG, "ntfy subscription open")
                        reconnectAttempts = 0
                        disconnectGracePending = false
                        reportSocketState(WakeLaneState.ARMED)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        routeMessage(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        // The HTTP status names the refusal class when the
                        // failure HAS one (404 = the topic route is absent,
                        // 403 = read auth) — without it the log line cannot
                        // tell "link down" from "server said no".
                        Log.w(
                            TAG,
                            "ntfy subscription failure: ${t.message} (http ${response?.code ?: "-"})",
                        )
                        onTransportDown()
                        scheduleReconnect(server, topic)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        onTransportDown()
                        scheduleReconnect(server, topic)
                    }
                },
            )
    }

    private fun routeMessage(text: String) {
        // ntfy's websocket frames are the same JSON the HTTP stream
        // carries: {"id":…,"topic":…,"message":"{\"event\":…}"}. The
        // ring fact rides the message field as compact JSON — decoded
        // by the ONE parse home (WireFacts, fixture-pinned); the raw
        // frame is the only evidence when it will not parse.
        val fact =
            WireFacts.fromNtfyFrame(text) ?: run {
                Log.w(TAG, "unroutable ntfy frame: $text")
                return
            }
        RingEvent.route(
            this,
            event = fact.event,
            from = fact.from,
            callId = fact.callId,
        )
    }

    /** The socket's transport facts (starting/arm/disconnected) —
     *  but a REFUSED verdict stands until the next wire re-decides
     *  the lane: an open socket on a seam-off server would otherwise
     *  overwrite the refusal with ARMED, and ARMED would be a lie
     *  (the register answer said this install cannot be rung). The
     *  fresh wire clears the refusal by reporting STARTING
     *  unconditionally BEFORE the socket ever opens. */
    private fun reportSocketState(state: WakeLaneState) {
        when (WakeLane.state.value) {
            WakeLaneState.REFUSED_SEAM_OFF,
            WakeLaneState.REFUSED_INVALID,
            WakeLaneState.REFUSED_AUTH,
            WakeLaneState.REFUSED_OTHER,
            -> Unit

            else -> WakeLane.report(state)
        }
    }

    /** The transport died (failure or clean close): drop the socket
     *  and report DISCONNECTED — but only after the flicker grace, so
     *  a flap that heals before the window closes never surfaces as
     *  wake off. Internal: the pins drive it directly (the meta-proof
     *  lesson: drive seams, never wait on network events). */
    internal fun onTransportDown() {
        socket = null
        if (stopped) return
        if (disconnectGracePending) return
        disconnectGracePending = true
        Thread {
            Thread.sleep(disconnectGraceMs)
            disconnectGracePending = false
            if (!stopped && socket == null) reportSocketState(WakeLaneState.DISCONNECTED)
        }.start()
    }

    /** Test seam (J16/M051): the stop-leak pin invokes the resurrect
     *  vector DIRECTLY after ACTION_STOP — no dependence on the close
     *  handshake's latency under Robolectric. Production callers stay
     *  onFailure/onClosed. */
    internal fun scheduleReconnect(
        server: String,
        topic: String,
    ) {
        if (reconnecting || stopped) return
        reconnecting = true
        reconnectAttempts += 1
        val backoff = minOf(60_000L, reconnectBackoffBaseMs shl (reconnectAttempts - 1).coerceAtMost(6))
        Thread {
            Thread.sleep(backoff)
            reconnecting = false
            if (!stopped && socket == null) subscribe(server, topic)
        }.start()
    }

    override fun onDestroy() {
        stopped = true
        socket?.close(1000, "destroyed")
        socket = null
        super.onDestroy()
    }


    companion object {
        private const val TAG = "NtfySubscription"
        private const val NOTIFICATION_ID = 1003

        /** Test seam (J16/M051): the stop-leak pin sends this action
         *  through onStartCommand. */
        internal const val ACTION_STOP = "software.larsartmann.webphone.push.STOP"
        private const val EXTRA_SERVER = "server"
        private const val EXTRA_TOPIC = "topic"
        internal const val PREFS = "webphone-ntfy"
        internal const val KEY_SERVER = "server"
        internal const val KEY_TOPIC = "topic"

        /** Test seam: the websocket endpoint the lane subscribes to —
         *  ntfy's per-topic websocket is `<server>/<topic>/ws`. */
        internal fun subscriptionUrl(
            server: String,
            topic: String,
        ): String = "${server.trimEnd('/')}/$topic/ws"

        fun start(
            context: Context,
            server: String,
            topic: String,
        ) {
            context.startForegroundService(
                Intent(context, NtfySubscriptionService::class.java)
                    .putExtra(EXTRA_SERVER, server)
                    .putExtra(EXTRA_TOPIC, topic),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NtfySubscriptionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
