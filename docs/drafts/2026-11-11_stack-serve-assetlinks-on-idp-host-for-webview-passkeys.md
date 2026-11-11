<!-- draft: cross-repo contract note to LarsArtmann/webphone + SystemNix (the passkey-login fix, stack half) -->

Serve `/.well-known/assetlinks.json` on `auth.larsartmann.cloud` — the in-shell passkey login needs it

## Why

The Android shell (webphone-android) renders the island in a same-origin-only WebView, and the stack's IdP (Pocket ID at `auth.larsartmann.cloud`) supports **passkey-only sign-in** — the "Authenticate" button IS the login, unlocked by fingerprint/PIN. There is no password to fall back to (Pocket ID docs: "Pocket ID only supports passwordless authentication methods").

Two halves are required for that button to work inside the shell's WebView:

1. **App half (DONE, webphone-android):** Android WebView ships with WebAuthn support DISABLED; the provider has supported it since Chromium M131 but only after the embedding app opts in via androidx.webkit (`WebViewFeature.WEB_AUTHENTICATION` → `WebSettingsCompat.setWebAuthenticationSupport(settings, WEB_AUTHENTICATION_SUPPORT_FOR_APP)`). Landed in `WebSurface.optInToPasskeys` (2026-11-11).
2. **Stack half (THIS request):** in FOR_APP mode, Android's Credential Manager only serves a passkey ceremony for a relying-party host that PROVES the app via Digital Asset Links. The RP ID for Pocket ID passkeys is `auth.larsartmann.cloud` (Pocket ID's public-URL host). Verified 2026-11-11: `https://auth.larsartmann.cloud/.well-known/assetlinks.json` answers **404** — so even with the app half shipped, the ceremony still fails at the RP check and the login dead-ends exactly as before.

## What to change

Serve a Digital Asset Links statement on **both** `auth.larsartmann.cloud` and `pbx.artmann.tech` (Caddy route → static file; the same file with both relations is safe on both hosts):

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "software.larsartmann.webphone",
      "sha256_cert_fingerprints": ["<RELEASE CERT SHA-256>"]
    }
  },
  {
    "relation": ["delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "software.larsartmann.webphone",
      "sha256_cert_fingerprints": ["<RELEASE CERT SHA-256>"]
    }
  }
]
```

- `handle_all_urls` = the App Links statement (existing R18 TODO — same file, one Caddy route serves both needs on the stack origin).
- `get_login_creds` = the NEW relation; THIS is what unlocks WebView passkeys.
- The fingerprint must be the **release keystore's** (the phone runs the release-signed build since 2026-11-10). `webphone-android/scripts/assetlinks.sh ~/.config/webphone-android/release.keystore` emits the file ready to paste.
- Content-Type `application/json`, served over HTTPS at the `/.well-known/` path, both hosts.

## To verify

1. `curl -fsS https://auth.larsartmann.cloud/.well-known/assetlinks.json` → the JSON above (200, not 404).
2. On the phone (app with the androidx.webkit opt-in installed): sign-out → island login → Pocket ID in-shell → "Authenticate" → the system fingerprint/biometric sheet appears (not a silent button).
3. `adb logcat -s WebSurface` shows `webauthn in webview: FOR_APP enabled` at launch (the app-side verdict line).
4. Complete the dance: the callback lands on the origin, `GET /api/session` answers 200, the pill climbs to `Connected · wake on`.

## Source-level receipts (2026-11-11)

- Pocket ID passkey-only: https://docs.pocket-id.org/ ("only supports passwordless authentication methods, primarily passkeys").
- WebView WebAuthn opt-in requirement + M131: MDN browser-compat-data issue #29452 (Chrome WebAuthn team: "Support was enabled in M131 for all features on the table"); Android docs "Authenticate users with WebView" (androidx.webkit ≥ 1.12, `WEB_AUTHENTICATION_SUPPORT_FOR_APP`, Digital Asset Links for RP IDs).
- The 404: `GET https://auth.larsartmann.cloud/.well-known/assetlinks.json` → 404 Not Found (probed from the dev machine).
- App half: webphone-android `app/src/main/java/software/larsartmann/webphone/shell/WebSurface.kt` (`optInToPasskeys`), `gradle/libs.versions.toml` (`androidx.webkit` 1.18.0).

💘 Generated with Crush
