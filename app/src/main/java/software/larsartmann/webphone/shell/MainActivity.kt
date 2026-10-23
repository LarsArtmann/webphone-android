package software.larsartmann.webphone.shell

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import software.larsartmann.webphone.App
import software.larsartmann.webphone.call.CallEngine
import software.larsartmann.webphone.push.NtfySubscriptionService
import software.larsartmann.webphone.sip.SipEngine

/**
 * The shell: the stack origin's own pages render in the WebView (the
 * island login mints the session cookie; Messages/Fax/Voicemail/
 * History/Contacts/Settings keep working exactly as in the browser),
 * while the native call layer (SIP + WebRTC + Telecom) registers the
 * same extension over the same WSS gateway — the call contract the
 * platform decision moved into Kotlin.
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(null, false)

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        setContentView(webView)
        webView.loadUrl(App.origin)

        wireNativeCallLayer()
    }

    /** After the WebView session exists, resume it into SIP
     *  credentials and attach the native call engine. Retried on each
     *  resume (the login may have just happened). */
    private fun wireNativeCallLayer() {
        scope.launch {
            val identity = SessionApi.resumeSession() ?: return@launch
            val engine = SipEngine(
                scope,
                SipEngine.Config(
                    wsUrl = identity.websocketUrl,
                    sipDomain = identity.sipDomain,
                    extension = identity.extension,
                    password = identity.sipPassword,
                ),
            )
            CallEngine.attach(engine)
            engine.connect()
            // The ntfy lane subscribes from boot (the FCM lane rides
            // Play services; register its token when present).
            NtfySubscriptionService.start(
                this@MainActivity,
                App.origin,
                DeviceIdentity.ntfyTopic(this@MainActivity),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) wireNativeCallLayer()
    }

    override fun onDestroy() {
        CallEngine.detach()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    // The WebView back stack, not the activity's: tab nav inside the
    // island is the product's own navigation.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
