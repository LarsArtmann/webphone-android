# Signing key rotation

Android installs an update only when it is signed with the same key as
the installed app. There is no in-place key rotation: a new key means a
new identity for the package, and every installed device has to
uninstall once before it can take the new build (which deletes the
app's local state: the saved stack origin and the session cookie; both
come back on the next sign-in).

Because of that, rotation is a last resort. Prefer keeping the key.

## Keep the key

Both files live outside the repo, in `~/.config/webphone-android/`:

| File                 | Holds                           |
| -------------------- | ------------------------------- |
| `release.keystore`   | The RSA key (alias `webphone`)  |
| `release.properties` | keystore path, passwords, alias |

Back both up together (a keystore without its password is lost). The
`scripts/release.sh --init-key` output prints the SHA-256 fingerprint
of the certificate; store it with the backup so a restore can be
verified:

```console
keytool -list -v -keystore release.keystore -storepass "$PASS" -alias webphone | grep SHA256
```

The release gate compares the signed APK's certificate with the
keystore named in `release.properties` and refuses a mismatch, so a
restore of the wrong file stops a release instead of shipping it.

## When rotation is unavoidable (key lost or leaked)

1. Revoke what can be revoked: the leaked keystore cannot be disabled
   remotely; assume an attacker can build updates for the installed
   base until it is uninstalled.
2. `scripts/release.sh --init-key` (move the old files away first).
3. `scripts/release.sh bump major` and cut the changelog section. The
   versionCode must exceed the highest published tag (the preflight
   checks this).
4. Publish. The release notes MUST say **uninstall first**.
5. Re-serve `/.well-known/assetlinks.json` on `pbx.artmann.tech` and
   `auth.larsartmann.cloud` with the NEW certificate fingerprint
   (`scripts/assetlinks.sh` emits it): App Links and WebView passkeys
   are bound to the signing certificate and break silently otherwise.
6. On every device: uninstall, install from the forge release, sign in.

## Checking which key a device has

```console
adb shell pm path software.larsartmann.webphone   # prints base.apk path
adb pull <base.apk> /tmp/phone.apk
apksigner verify --print-certs /tmp/phone.apk
```

Compare the `Signer #1 certificate SHA-256 digest` with the keystore
fingerprint. A mismatch means uninstall-first, not `adb install -r`.
