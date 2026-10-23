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
import org.json.JSONObject
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification =
            Notification.Builder(this, App.CHANNEL_PUSH)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(getString(R.string.ntfy_subscription_active))
                .setOngoing(true)
                .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        if (intent?.action == ACTION_STOP) {
            socket?.close(1000, "stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val server = intent?.getStringExtra(EXTRA_SERVER)
        val topic = intent?.getStringExtra(EXTRA_TOPIC)
        if (server.isNullOrEmpty() || topic.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        subscribe(server, topic)
        return START_STICKY
    }

    private fun subscribe(server: String, topic: String) {
        val client = OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("${server.trimEnd('/')}/$topic/ws")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ntfy subscription open")
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                routeMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ntfy subscription failure: ${t.message}")
                scheduleReconnect(server, topic)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect(server, topic)
            }
        })
    }

    private fun routeMessage(text: String) {
        // ntfy's websocket frames are the same JSON the HTTP stream
        // carries: {"id":…,"topic":…,"message":"{\"event\":…}"}. The
        // ring fact rides the message field as compact JSON (the
        // server seam's wire contract).
        runCatching {
            val envelope = JSONObject(text)
            val inner = JSONObject(envelope.optString("message"))
            RingEvent.route(
                this,
                event = inner.optString("event"),
                from = inner.optString("from").ifEmpty { null },
                callId = inner.optString("call_id").ifEmpty { null },
            )
        }
    }

    private fun scheduleReconnect(server: String, topic: String) {
        reconnectAttempts += 1
        val backoff = minOf(60_000L, 1_000L shl (reconnectAttempts - 1).coerceAtMost(6))
        Thread {
            Thread.sleep(backoff)
            if (socket != null) subscribe(server, topic)
        }.start()
    }

    override fun onDestroy() {
        socket?.close(1000, "destroyed")
        socket = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NtfySubscription"
        private const val NOTIFICATION_ID = 1003
        private const val ACTION_STOP = "software.larsartmann.webphone.push.STOP"
        private const val EXTRA_SERVER = "server"
        private const val EXTRA_TOPIC = "topic"

        fun start(context: Context, server: String, topic: String) {
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
