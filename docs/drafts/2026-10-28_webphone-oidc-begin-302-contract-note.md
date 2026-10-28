<!-- draft: cross-repo contract note to LarsArtmann/webphone (M049) -->

Keep `GET /api/auth/oauth2/begin` a server-side 302 — the Android in-shell login depends on it

## Why

The Android shell (webphone-android) renders the island in a same-origin-only WebView. The island's login card links `GET /api/auth/oauth2/begin`, and the shell's auth-detour policy blesses the IdP authority ONLY when that hop arrives as a main-frame SERVER redirect (Chromium's `shouldOverrideUrlLoading` with `request.isRedirect == true`). That blessing is what keeps the whole Pocket ID dance inside the shell so the session cookie mints in the WebView's cookie jar.

## What breaks

If the begin → IdP hop ever becomes a client-side redirect (meta refresh, JS `location` change) or a cross-origin form POST, the hop arrives unjudged or unmarked: the detour never engages, the IdP page ejects to Chrome, and the session cookie mints in the WRONG cookie jar. The app then never registers — no SIP leg, no calls.

## Source-level cause (the behavior to preserve)

webphone @ `5bbf6b5`, `internal/server/sso_api.go:58` (handler `ssoBegin`, route wired at `internal/server/server.go:404`):

```go
http.Redirect(w, r, challenge.URL, http.StatusFound)
```

A real 302, with `Referrer-Policy: no-referrer` + `Cache-Control: no-store` set at lines 56-57. Device-proven 2026-10-27 on the emulator: the begin deep link lands in-shell and logcat shows `WebSurface: auth detour blessed via redirect: auth.larsartmann.cloud`.

## Operator steps (if the login flow ever changes)

- [ ] Keep the `http.Redirect(..., StatusFound)` hop for `begin` — no `hx-redirect`, no JS, no `<meta>` hop on this route.
- [ ] If `begin` ever needs a POST or a client-side hop, change the Android seam FIRST (webphone-android `WebSurface.navigateOut` + the commit-side belt) or in-shell login breaks.
- [ ] Re-run the emulator auth-detour pass after ANY `/api/auth/*` route change.

## To verify

After tapping the island's login card: `adb logcat | grep "auth detour blessed"` fires, the Pocket ID pages render in the shell's task, and no Chrome window appears.

💘 Generated with Crush
