package software.larsartmann.webphone.shell

import android.net.Uri

/** Where an inbound deep link (ACTION_VIEW) must land. */
internal sealed interface DeepLinkTarget {
    /** A same-origin stack path (`https://pbx…/messages/42`): the shell
     *  loads it in the session WebView — App Links into island pages. */
    data class ShellPath(
        val uri: Uri,
    ) : DeepLinkTarget

    /** A dial request (`tel:`, `sip:`): the native dialer opens
     *  pre-filled — the Call button still gates on registration
     *  (dialer honesty), the link only carries the number. */
    data class Dial(
        val number: String,
    ) : DeepLinkTarget

    /** Anything else — a foreign https link that slipped through the
     *  manifest, a schemeless intent, a blank tel:. The app opens its
     *  home state; a foreign page is NEVER rendered (the WebView
     *  carries the session cookie) and NEVER re-handed-off (the
     *  system already declined to keep it — handing it back would
     *  loop). */
    data object None : DeepLinkTarget
}

/**
 * Routes an inbound deep link, PURE so the intake is unit-testable:
 *
 * - `tel:`/`callto:` → the number, URI params stripped (visual
 *   separators stay — the user sees what the link carried, and the
 *   dialer edits freely; but `;transport=`, `;phone-context=`,
 *   `;postd=`, `;isub=` are routing metadata, not dialable digits);
 * - `sip:` → the user part, params stripped (`sip:100@pbx` and
 *   `sip:100;transport=tcp@pbx` both dial `100` — the host and the
 *   transport are the server's business, the extension is the
 *   dialable);
 * - `http(s):` → a stack path only when the authority matches the
 *   configured origin; every other host is [DeepLinkTarget.None].
 */
internal fun deepLinkTarget(
    uri: Uri?,
    origin: Uri,
): DeepLinkTarget {
    if (uri == null) return DeepLinkTarget.None
    return when (uri.scheme?.lowercase()) {
        "tel", "callto" ->
            uri.schemeSpecificPart
                .substringBefore(';')
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { DeepLinkTarget.Dial(it) }
                ?: DeepLinkTarget.None

        "sip" ->
            uri.schemeSpecificPart
                .substringBefore('@')
                .substringBefore(';')
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { DeepLinkTarget.Dial(it) }
                ?: DeepLinkTarget.None

        "http", "https" ->
            if (sameAuthority(uri, origin)) {
                DeepLinkTarget.ShellPath(uri)
            } else {
                DeepLinkTarget.None
            }

        else -> DeepLinkTarget.None
    }
}
