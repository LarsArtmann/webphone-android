package software.larsartmann.webphone.shell

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.call.DialpadActivity

/**
 * The shell: the stack origin's own pages render in the WebView (the
 * island login mints the session cookie; Messages/Fax/Voicemail/
 * History/Contacts/Settings keep working exactly as in the browser),
 * while the native call layer (SIP + WebRTC + Telecom) registers the
 * same extension over the same WSS gateway — the call contract the
 * platform decision moved into Kotlin. The dial button is the one
 * native chrome element: calling is native, everything else is web.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(null, false)

        // The ring lane is dead without the notification grant on
        // API 33+ (no heads-up, no full-screen intent) — ask up front.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()

        val dial = Button(this).apply { text = getString(R.string.dial) }
        val root = FrameLayout(this)
        root.addView(webView)
        root.addView(
            dial,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply { setMargins(48, 48, 48, 96) },
        )

        setContentView(root)
        webView.loadUrl(App.origin)
        dial.setOnClickListener { startActivity(Intent(this, DialpadActivity::class.java)) }

        // After the WebView session exists, resume it into SIP
        // credentials and attach the native call engine. Retried on
        // each resume (the login may have just happened).
        CallLayer.ensureWired(this)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) CallLayer.ensureWired(this)
    }

    override fun onDestroy() {
        CallLayer.shutdown()
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
