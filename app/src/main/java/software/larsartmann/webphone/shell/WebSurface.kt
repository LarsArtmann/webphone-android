package software.larsartmann.webphone.shell

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import software.larsartmann.webphone.App
import software.larsartmann.webphone.R
import software.larsartmann.webphone.ui.dp
import software.larsartmann.webphone.ui.pillBackground
import software.larsartmann.webphone.ui.styleText
import software.larsartmann.webphone.ui.themeColor

/** Where a navigation attempt must go. */
internal sealed interface NavigationVerdict {

    /** Same-origin pages and WebView-internal schemes: load in the shell. */
    data object LoadInShell : NavigationVerdict

    /** Everything else (other hosts, `tel:`, `mailto:`, …): the system
     *  picks a handler — never the shell's WebView. */
    data class HandOff(val intent: Intent) : NavigationVerdict
}

/**
 * Decides navigation scope, PURE so the policy is unit-testable without
 * a WebView: http(s) navigations stay only on the configured origin's
 * authority (host + normalized port); every other host and every
 * non-web scheme becomes a system hand-off. Subdomains are DIFFERENT
 * origins by intent — an operator app must not render arbitrary web
 * inside its session-cookie-bearing WebView.
 */
internal fun navigationVerdict(uri: Uri?, origin: Uri): NavigationVerdict {
    if (uri == null) return NavigationVerdict.LoadInShell
    val scheme = uri.scheme?.lowercase() ?: return NavigationVerdict.HandOff(intent(uri))
    return when {
        scheme == "http" || scheme == "https" ->
            if (sameAuthority(uri, origin)) {
                NavigationVerdict.LoadInShell
            } else {
                NavigationVerdict.HandOff(intent(uri))
            }

        // WebView-internal mechanics (about:blank, data: previews,
        // blob:/javascript: URIs the page machinery produces).
        scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript" ->
            NavigationVerdict.LoadInShell

        else -> NavigationVerdict.HandOff(intent(uri))
    }
}

private fun intent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW, uri)

private fun sameAuthority(uri: Uri, origin: Uri): Boolean {
    if (uri.host?.lowercase() != origin.host?.lowercase()) return false
    return effectivePort(uri) == effectivePort(origin)
}

/** Uri.port is -1 while the port is the scheme default — normalize
 *  before comparing, or `origin:443` links would look foreign. */
private fun effectivePort(uri: Uri): Int =
    if (uri.port != -1) uri.port else if (uri.scheme?.lowercase() == "http") 80 else 443

/**
 * The WebView shell surface, hardened for daily-driver use. Owns the
 * one WebView, its clients and the offline overlay — MainActivity owns
 * only the floating chrome.
 *
 * What lives here and why:
 *
 * - **Navigation scope**: same-origin pages render in the shell;
 *   everything else hands off to the system (browser, dialer, mail).
 *   The WebView carries the session cookie — it must never be pointed
 *   at arbitrary web content.
 * - **Offline overlay**: a main-frame transport failure (no route to
 *   the PBX) replaces Chromium's ugly error page with a native
 *   "can't reach the stack" panel and a Retry button. Sits BELOW the
 *   floating chrome so Settings stays reachable while offline (a wrong
 *   origin is one of the two likely causes). Server-rendered error
 *   pages (the styled 404) are NOT intercepted — those surfaces are
 *   the origin's own contract; this overlay only covers the
 *   server-unreachable case.
 * - **Renderer-death rebuild** (`onRenderProcessGone`): the default
 *   lets the renderer crash KILL THE APP. We destroy the dead WebView,
 *   build a fresh one and reload the origin — a phone app must survive
 *   its renderer dying.
 * - **File chooser** (`onShowFileChooser`): without a WebChromeClient
 *   the web composer's attach button (`<input type=file>`, MMS) is a
 *   silent dead button. Now it opens the system picker; multi-select
 *   passes through when the input asks for it.
 * - **Web permissions denied explicitly**: call audio is NATIVE by the
 *   platform decision (never move signaling/audio back into the
 *   WebView) — the island's getUserMedia asks are refused up front
 *   instead of hanging until timeout.
 * - **Dark-mode seam** (API 33+): the web app is dark-first and
 *   declares `color-scheme: dark light` with `prefers-color-scheme`
 *   blocks plus its own manual override — with algorithmic darkening
 *   allowed, WebView reports the APP theme (Theme.WebPhone is DayNight
 *   and pins `android:isLightTheme`), the pages render their OWN dark
 *   theme (no recoloring — they declare color-scheme), and the web
 *   Settings' manual light/dark override still wins on top. Below API
 *   33 the WebView stays light; the web app's own theme toggle is the
 *   fallback there.
 * - **Cookie durability**: the island login mints the session cookie
 *   inside the WebView — flushed on every page finish and on pause so
 *   a process kill cannot eat the login.
 * - **Battery**: paused WebViews stop layout/JS work; the SSE lane's
 *   live updates only matter while visible (EventSource reconnects on
 *   resume), and ring-while-closed never rode the WebView anyway.
 */
internal class WebSurface(private val activity: ComponentActivity) {

    val container: FrameLayout = FrameLayout(activity)

    private var webView: WebView
    private var loadedOrigin: String? = null
    private var pendingPicker: ValueCallback<Array<Uri>>? = null
    private val offlineOverlay: LinearLayout

    private val pickerLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingPicker
            pendingPicker = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
        }

    init {
        CookieManager.getInstance().setAcceptCookie(true)
        webView = buildWebView()
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        offlineOverlay = buildOfflineOverlay()
        // Below the floating chrome (added by MainActivity AFTER this
        // container): offline must not cover the Settings button.
        container.addView(
            offlineOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView = WebView(activity).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= 33) {
            settings.isAlgorithmicDarkeningAllowed = true
        }
        webViewClient = ShellWebViewClient()
        webChromeClient = ShellWebChromeClient()
        // Third-party cookies are OFF per-WebView — the call needs the
        // WebView param and CRASHES on null under the Chromium glue
        // (found by the first emulator smoke, 2026-10-24).
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
    }

    private fun buildOfflineOverlay(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(activity.dp(32), activity.dp(24), activity.dp(32), activity.dp(24))
        setBackgroundColor(activity.themeColor(R.attr.wpBackground))
        visibility = View.GONE

        val title = TextView(activity).apply {
            styleText(18f, activity.themeColor(R.attr.wpOnSurface), medium = true)
            text = activity.getString(R.string.web_offline_title)
        }
        val body = TextView(activity).apply {
            styleText(14f, activity.themeColor(R.attr.wpOnSurfaceMuted))
            text = activity.getString(R.string.web_offline_body, App.origin)
            setPadding(0, activity.dp(8), 0, 0)
        }
        val retry = AppCompatButton(activity).apply {
            background = activity.pillBackground(activity.themeColor(R.attr.wpAccent))
            setTextColor(ContextCompat.getColor(activity, R.color.wp_on_accent))
            textSize = 15f
            isAllCaps = false
            setPadding(activity.dp(28), activity.dp(12), activity.dp(28), activity.dp(12))
            minimumHeight = activity.dp(48)
            text = activity.getString(R.string.web_offline_retry)
            setOnClickListener { retry() }
        }

        addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL },
        )
        addView(
            body,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = activity.dp(8) },
        )
        addView(
            retry,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(24)
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
    }

    private inner class ShellWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val verdict = navigationVerdict(request.url, Uri.parse(App.origin))
            return when (verdict) {
                is NavigationVerdict.LoadInShell -> false
                is NavigationVerdict.HandOff -> {
                    runCatching { activity.startActivity(verdict.intent) }
                        .onFailure { Log.w(TAG, "no handler for ${request.url}: ${it.message}") }
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            hideOffline()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            // The login POST mints the cookie in-page; flush it to disk
            // immediately so even a foreground process kill cannot eat it.
            runCatching { CookieManager.getInstance().flush() }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            // Sub-resource failures are routine (a favicon while flaky)
            // — only the document itself failing means "stack unreachable".
            if (!request.isForMainFrame) return
            showOffline()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Consumed: the app survives its renderer dying. The dead
            // WebView must be destroyed (holding it leaks the corpse).
            rebuildAfterRendererDeath()
            return true
        }
    }

    private inner class ShellWebChromeClient : WebChromeClient() {

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            // A stale picker (second attach tap before the first
            // resolves) must be cancelled or the WebView refuses the
            // NEXT file chooser until it times out.
            pendingPicker?.onReceiveValue(null)
            pendingPicker = filePathCallback
            val choose = fileChooserParams.createIntent().apply {
                if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
            return runCatching { pickerLauncher.launch(choose) }.fold(
                onSuccess = { true },
                onFailure = {
                    Log.w(TAG, "file chooser launch failed: ${it.message}")
                    pendingPicker = null
                    filePathCallback.onReceiveValue(null)
                    true
                },
            )
        }

        // Call audio is native (platform decision); the island's
        // getUserMedia asks get a definite NO, not a hang-then-timeout.
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onConsoleMessage(console: ConsoleMessage): Boolean {
            val line = "[${console.sourceId()}:${console.lineNumber()}] ${console.message()}"
            when (console.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, line)
                ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, line)
                else -> Log.d(TAG, line)
            }
            return true
        }
    }

    /** Follow a Settings origin change (idempotent per origin). */
    fun loadOriginIfChanged() {
        if (App.origin != loadedOrigin) {
            hideOffline()
            webView.loadUrl(App.origin)
            loadedOrigin = App.origin
        }
    }

    fun retry() {
        hideOffline()
        webView.loadUrl(App.origin)
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        webView.goBack()
    }

    /** Battery + cookie durability while the user is away. */
    fun pause() {
        webView.onPause()
        runCatching { CookieManager.getInstance().flush() }
    }

    fun resume() {
        webView.onResume()
    }

    fun destroy() {
        pendingPicker?.onReceiveValue(null)
        pendingPicker = null
        container.removeAllViews()
        webView.destroy()
    }

    /** Swap the renderer-dead WebView for a fresh one and reload the
     *  origin. Also the recovery path if a rebuild is ever needed
     *  outside the crash callback (e.g. a future origin quarantine). */
    internal fun rebuildAfterRendererDeath() {
        if (activity.isFinishing) {
            destroy()
            return
        }
        runCatching { CookieManager.getInstance().flush() }
        container.removeView(webView)
        webView.destroy()
        pendingPicker?.onReceiveValue(null)
        pendingPicker = null
        webView = buildWebView()
        container.addView(webView, 0)
        loadedOrigin = null
        loadOriginIfChanged()
    }

    internal fun showOffline() {
        offlineOverlay.visibility = View.VISIBLE
    }

    internal fun hideOffline() {
        offlineOverlay.visibility = View.GONE
    }

    internal fun offlineVisible(): Boolean = offlineOverlay.visibility == android.view.View.VISIBLE

    /** Test seam: the LIVE WebView instance (renderer rebuilds swap it). */
    internal fun currentWebView(): WebView = webView

    private companion object {
        const val TAG = "WebSurface"
    }
}
