package software.larsartmann.webphone.shell

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
 *
 * The one exception is [authAuthority]: the AUTH DETOUR. The island's
 * OIDC login (`/api/auth/oauth2/begin`) is a same-origin hop the
 * ORIGIN itself 302s to the IdP (Pocket ID) — HTTP redirects never
 * consult `shouldOverrideUrlLoading`, so the IdP page commits in the
 * shell without a verdict. From that moment the IdP's own SPA links
 * would be "foreign" and ejected to the system browser, abandoning
 * the login mid-dance (the session mints in Chrome's cookie jar,
 * never the shell's). While the detour is live, the IdP's authority
 * navigates in-shell too; see [WebSurface.onCommittedNavigation] for
 * why ONLY a trusted redirect can ever activate one.
 */
internal fun navigationVerdict(
    uri: Uri?,
    origin: Uri,
    authAuthority: Uri? = null,
): NavigationVerdict {
    if (uri == null) return NavigationVerdict.LoadInShell
    val scheme = uri.scheme?.lowercase() ?: return NavigationVerdict.HandOff(intent(uri))
    return when {
        scheme == "http" || scheme == "https" ->
            if (sameAuthority(uri, origin)) {
                NavigationVerdict.LoadInShell
            } else if (authAuthority != null && sameAuthority(uri, authAuthority)) {
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

internal fun sameAuthority(uri: Uri, origin: Uri): Boolean {
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
 *   at arbitrary web content. The one exception is the AUTH DETOUR
 *   (see [navigationVerdict]): while an OIDC login has legitimately
 *   landed the shell on the IdP (the origin's own 302 — the only
 *   navigation class the verdict never sees), that one foreign
 *   authority navigates in-shell until the dance returns home.
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
 * - **Downloads** (`DownloadListener`): MMS attachments and exports
 *   arrive as `download` links the WebView cannot save itself — a
 *   silent no-op without a listener. Same-origin downloads ride the
 *   system DownloadManager WITH the session cookie (the attachment
 *   routes are session-gated); foreign downloads hand off to the
 *   browser because the cookie must never leave the origin.
 * - **Main-frame HTTP errors stay PAGES**: the proxy's 502 surface and
 *   the origin's styled 404 are the origin's own error contract — the
 *   offline overlay is reserved for TRANSPORT failure (no route to
 *   the stack). See [ShellWebViewClient.onReceivedHttpError].
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
    private lateinit var offlineBody: TextView

    /** The live AUTH DETOUR (see [navigationVerdict]): the authority of
     *  the foreign page the ORIGIN's own redirect landed the shell on
     *  (the IdP of an OIDC login). Null while the shell is on-origin. */
    private var authDetour: Uri? = null

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
        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            handleDownload(url, contentDisposition, mimeType)
        }
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
        offlineBody = body
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

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            navigateOut(
                request.url,
                isServerRedirect = request.isRedirect,
                isMainFrame = request.isForMainFrame,
            )

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            hideOffline()
            // The belt (R01): a main-frame page START runs the same
            // bless/clear machine as the commit — idempotent (same
            // authority dedups), and it catches loads whose commit may
            // never fire (crashed renderer mid-load). A start the
            // verdict never allowed is by construction an unjudged
            // arrival (cross-origin POST) — the same trust argument as
            // [onCommittedNavigation].
            onCommittedNavigation(Uri.parse(url))
        }

        // The commit seam: every MAIN-FRAME commit passes HERE — including
        // the ones no verdict ever judged (cross-origin form POSTs fire no
        // shouldOverrideUrlLoading). That invisibility is what the auth
        // detour's commit-side hook keys on — see [onCommittedNavigation].
        // (Server REDIRECTS are the other half: modern Chromium consults
        // shouldOverrideUrlLoading for them — see [navigateOut].)
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            url?.let { onCommittedNavigation(Uri.parse(it)) }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            // The login POST mints the cookie in-page; flush it to disk
            // immediately so even a foreground process kill cannot eat it.
            runCatching { CookieManager.getInstance().flush() }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            onMainFrameTransportFailure(request.isForMainFrame)
        }

        // POLICY (W111, 2026-10-25): main-frame HTTP errors (status >=
        // 400 — the proxy's 502 page, the origin's styled 404) RENDER
        // AS PAGES; they never raise the offline overlay. Those bodies
        // ARE the origin's error contract (actionable HTML with a path
        // echo and a way home) and browser parity matters: swapping
        // them for a native panel would hide the origin's own recovery
        // affordances. The overlay stays reserved for TRANSPORT failure
        // (onReceivedError: no route, DNS, TLS — "stack unreachable").
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: android.webkit.WebResourceResponse,
        ) = Unit

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
            val choose = chooseIntent(fileChooserParams)
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
            // Page chatter is a debugging aid: verbose levels only in
            // debuggable builds; errors always (a production breakage
            // must still reach bugreports).
            val verbose = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val line = "[${console.sourceId()}:${console.lineNumber()}] ${console.message()}"
            when {
                console.messageLevel() == ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, line)
                verbose && console.messageLevel() == ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, line)
                verbose -> Log.d(TAG, line)
            }
            return true
        }

        /** API 33+ routes image-only inputs through the system photo
         *  picker (the native look, no storage permission); everything
         *  else — and older versions — keeps the classic chooser. */
        private fun chooseIntent(params: FileChooserParams): Intent {
            if (Build.VERSION.SDK_INT >= 33) {
                val accepts = params.acceptTypes.orEmpty()
                val imageOnly = accepts.isNotEmpty() && accepts.all { it.startsWith("image/") }
                if (imageOnly) {
                    return Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                        if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                            putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())
                        }
                    }
                }
            }
            return params.createIntent().apply {
                if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
        }
    }

    /** Follow a Settings origin change (idempotent per origin). */
    fun loadOriginIfChanged() {
        if (App.origin != loadedOrigin) {
            hideOffline()
            authDetour = null
            webView.loadUrl(App.origin)
            loadedOrigin = App.origin
        }
    }

    /** Load a deep-linked SAME-ORIGIN path (an App Link into messages,
     *  voicemail, a share target…). Defense in depth: the verdict is
     *  re-checked here, so even a mis-declared manifest filter can never
     *  aim the session-cookie-bearing WebView at a foreign page — a
     *  foreign deep link is simply not loaded (the shell keeps its
     *  origin-root state). */
    fun loadUri(uri: Uri) {
        if (navigationVerdict(uri, Uri.parse(App.origin)) !is NavigationVerdict.LoadInShell) return
        hideOffline()
        authDetour = null
        webView.loadUrl(uri.toString())
        loadedOrigin = App.origin
    }

    fun retry() {
        // A retry is a deliberate return to the origin: any live auth
        // detour dies HERE (an offline retry never commits, so the
        // commit-side hook alone would leave the blessing stale).
        authDetour = null
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
        authDetour = null
        loadOriginIfChanged()
    }

    internal fun showOffline() {
        // Re-resolve the origin at SHOW time: the overlay outlives
        // Settings changes (its body text was frozen at build time and
        // pointed at a stale stack).
        offlineBody.text = activity.getString(R.string.web_offline_body, App.origin)
        offlineOverlay.visibility = View.VISIBLE
    }

    internal fun hideOffline() {
        offlineOverlay.visibility = View.GONE
    }

    internal fun offlineVisible(): Boolean = offlineOverlay.visibility == android.view.View.VISIBLE

    /** Test seam: the LIVE WebView instance (renderer rebuilds swap it). */
    internal fun currentWebView(): WebView = webView

    /** POLICY seam (unit-tested; the Android callback is a one-line
     *  delegate): the navigation verdict APPLIED — same-origin stays
     *  in the shell (`false`), everything else starts the system
     *  hand-off and reports `true` (consumed).
     *
     *  R03 (2026-10-27, emulator-proven): modern Chromium CONSULTS this
     *  callback for server redirects — the OIDC `begin` 302 lands HERE
     *  with the IdP URL, not in doUpdateVisitedHistory. A main-frame
     *  server redirect while no detour is live is, by the same trust
     *  argument as [onCommittedNavigation], the origin's own chain
     *  (nothing user-initiated produced it): bless the target authority
     *  and keep it in-shell. Clicks and JS navigations (isRedirect=
     *  false) hand off unchanged — W105 stays true. */
    internal fun navigateOut(
        url: Uri,
        isServerRedirect: Boolean = false,
        isMainFrame: Boolean = true,
    ): Boolean {
        val verdict = navigationVerdict(url, Uri.parse(App.origin), authDetour)
        return when (verdict) {
            is NavigationVerdict.LoadInShell -> false
            is NavigationVerdict.HandOff -> {
                val scheme = url.scheme?.lowercase()
                if (isServerRedirect && isMainFrame && authDetour == null &&
                    (scheme == "http" || scheme == "https")
                ) {
                    authDetour = url
                    Log.d(TAG, "auth detour blessed via redirect: ${url.authority}")
                    false
                } else {
                    runCatching { activity.startActivity(verdict.intent) }
                        .onFailure { Log.w(TAG, "no handler for $url: ${it.message}") }
                    true
                }
            }
        }
    }

    /** POLICY seam (unit-tested; the Android callback is a one-line
     *  delegate off `doUpdateVisitedHistory`): every MAIN-FRAME commit
     *  passes through here, including the ones `shouldOverrideUrlLoading`
     *  never judged (HTTP redirects, cross-origin POSTs).
     *
     *  The trust argument, in full: a foreign http(s) page can only EVER
     *  commit in this WebView through (a) the verdict allowing it — which
     *  for foreign hosts only happens while an auth detour is already
     *  live on that same authority — or (b) an unjudged arrival: a
     *  cross-origin form POST (SOUL is not called for POSTs), or a
     *  redirect target when the redirect was itself unjudged. Server
     *  redirects that ARE judged are blessed at the [navigateOut] seam
     *  instead. A link click or a JS `location` change to a foreign host
     *  IS judged and handed off. So an unjudged foreign commit is, by
     *  construction, the origin's own server (or the IdP it chose)
     *  sending us here — THAT authority becomes the auth detour.
     *
     *  The detour is ONE authority at a time (replace, not union): the
     *  currently committed foreign page is the only one whose internal
     *  navigations may continue in-shell. A commit back on the ORIGIN
     *  (the OIDC callback is a redirect home) ends the detour.
     *
     *  Cookie safety is untouched: cookies are scoped per-authority in
     *  the shared CookieManager, so the IdP page never sees the
     *  webphone session cookie, and third-party cookie access is off. */
    internal fun onCommittedNavigation(url: Uri?) {
        if (url == null) return
        val scheme = url.scheme?.lowercase() ?: return
        if (scheme != "http" && scheme != "https") return
        val next = if (sameAuthority(url, Uri.parse(App.origin))) null else url
        if (next?.authority != authDetour?.authority) {
            Log.d(
                TAG,
                if (next == null) "auth detour cleared (back on origin: $url)"
                else "auth detour blessed: ${next.authority}",
            )
        }
        authDetour = next
    }

    /** Test seam: the live auth detour's authority (null = on-origin). */
    internal fun authDetourUri(): Uri? = authDetour

    /** POLICY seam (unit-tested; the Android callback is a one-line
     *  delegate): only a main-frame TRANSPORT failure (no route, DNS,
     *  TLS) means "stack unreachable". Sub-resource failures are
     *  routine, and HTTP error STATUSES are the origin's own pages. */
    internal fun onMainFrameTransportFailure(isMainFrame: Boolean) {
        if (!isMainFrame) return
        showOffline()
    }

    /** Same-origin downloads ride DownloadManager with the session
     *  cookie; foreign downloads hand off — the cookie must never
     *  leave the origin. Internal for direct policy tests. */
    internal fun handleDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val uri = Uri.parse(url)
        if (navigationVerdict(uri, Uri.parse(App.origin)) is NavigationVerdict.HandOff) {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                .onFailure { Log.w(TAG, "no handler for download $url: ${it.message}") }
            return
        }
        val request = DownloadManager.Request(uri).apply {
            // The attachment routes are session-gated; DownloadManager
            // makes its own HTTP fetch and needs the WebView's cookie.
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setMimeType(mimeType ?: "application/octet-stream")
            setTitle(downloadFileName(contentDisposition, url))
            // Shared Downloads collection: no destination override, no
            // legacy storage permission (minSdk 31).
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        val downloads = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { downloads.enqueue(request) }
            .onFailure { Log.w(TAG, "download enqueue failed for $url: ${it.message}") }
    }

    /** Content-Disposition filename > URL path segment > "download",
     *  stripped of path separators and control characters (the title
     *  is user-visible in the Downloads notification). */
    internal fun downloadFileName(contentDisposition: String?, url: String): String {
        val fromHeader = Regex("""filename\*?=\s*(?:\"([^\"]+)\"|([^;]+))""", RegexOption.IGNORE_CASE)
            .find(contentDisposition.orEmpty())
            ?.groupValues
            ?.let { values ->
                // groupValues yields "" (not null) for unmatched groups —
                // take the first NON-BLANK capture or fall to the URL.
                values.getOrNull(1)?.takeIf { it.isNotBlank() } ?: values.getOrNull(2)?.trim()
            }
            ?.trim()
            ?.orNullIfBlank()
        val raw = fromHeader ?: Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('/')
        val clean = buildString {
            for (ch in raw) if (ch.code > 31 && ch !in FORBIDDEN_FILE_CHARS) append(ch)
        }.trim().trimStart('.')
        return clean.ifBlank { "download" }
    }

    private companion object {
        const val TAG = "WebSurface"

        val FORBIDDEN_FILE_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    }
}

private fun String.orNullIfBlank(): String? = ifBlank { null }
