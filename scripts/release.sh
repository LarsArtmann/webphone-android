#!/usr/bin/env bash
# The release lane: cut a version, sign it with the real key, tag it,
# and publish the APK to a Forgejo release so Obtainium can install it
# and offer the NEXT one — the end of `adb install` / wireless debugging
# as the only way onto a phone.
#
# Why Forgejo releases and not a fake "updates.json": Obtainium's Forgejo
# source already does the whole job against the plain REST API —
# `GET /api/v1/repos/{owner}/{repo}/releases` gives it the tag as the
# available version and the attached `.apk` as the artifact. The forge
# is the source of truth; there is no second index to drift from it.
#
# Usage:
#   scripts/release.sh --init-key            # ONE-TIME: signing key + properties
#   scripts/release.sh bump patch|minor|major
#   scripts/release.sh --doctor              # preflight only, publishes nothing
#   scripts/release.sh [--dry-run] [--notes FILE]
#
# The gate is the full verification set the repo already trusts:
# assembleRelease + testDebugUnitTest + ktlintCheck, then the 16 KB
# page-alignment check on the SIGNED artifact, an apksigner verification,
# and the WCAG contrast guard. A release that fails any of them never
# reaches the forge.
#
# Environment:
#   FORGEJO_BASE_URL  default https://forgejo.home.lan
#   FORGEJO_REPO      default lars/webphone-android
#   FORGEJO_TOKEN     else ~/.config/webphone-android/forgejo-token
#   FORGEJO_INSECURE=1  skip TLS verification (the forge serves the
#                       internal dnsblockd CA; this host trusts it, the
#                       release artifact does not care either way)
#   WEBPHONE_RELEASE_PROPERTIES  else ~/.config/webphone-android/release.properties
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    echo "release: not inside a git work tree" >&2
    exit 2
}
cd "$ROOT"

CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/webphone-android"
PROPS="${WEBPHONE_RELEASE_PROPERTIES:-$CONFIG_DIR/release.properties}"
TOKEN_FILE="$CONFIG_DIR/forgejo-token"
BASE_URL="${FORGEJO_BASE_URL:-https://forgejo.home.lan}"
REPO="${FORGEJO_REPO:-lars/webphone-android}"
PYTHON="${PYTHON:-python3}"

MODE="release"
DRY_RUN=0
NOTES_FILE=""
BUMP=""

die() {
    echo "release: $*" >&2
    exit 1
}

# The forge's SSH endpoint prints an "AUTHORIZED ACCESS ONLY" banner on
# every connection — twice per release is noise, not information.
GIT_SSH_QUIET=(-c core.sshCommand="ssh -o LogLevel=ERROR")

note() {
    echo "== $*"
}

usage() {
    # The header block is the usage text: print it up to the `set -euo
    # pipefail` line, without the comment markers.
    sed -n '2,/^set -euo pipefail$/p' "$0" | sed -e '$d' -e 's/^# \{0,1\}//'
}

read_version() {
    [ -f version.txt ] || die "version.txt is missing — it is the single source of the app version"
    local v
    v="$(tr -d '[:space:]' <version.txt)"
    v="${v#v}"
    [[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
        die "version.txt must hold MAJOR.MINOR.PATCH, found '$v'"
    # Newline-terminated: `IFS=. read -r a b c < <(read_version)` needs a
    # delimiter or `read` reports EOF and `set -e` kills the caller.
    printf '%s\n' "$v"
}

version_code() {
    local major minor patch
    IFS=. read -r major minor patch < <(read_version)
    printf '%s' "$((major * 10000 + minor * 100 + patch))"
}

print_key_fingerprint() {
    local ks="$1" pass="$2" alias="$3"
    keytool -list -v -keystore "$ks" -storepass "$pass" -alias "$alias" 2>/dev/null |
        awk '!seen && /SHA256:/ { print $2; seen = 1 }'
}

# ---------------------------------------------------------------- --init-key
init_key() {
    command -v keytool >/dev/null || die "keytool not found — run inside 'nix develop' (JDK 21)"
    mkdir -p "$CONFIG_DIR"
    chmod 700 "$CONFIG_DIR"
    local ks="$CONFIG_DIR/release.keystore"
    local alias="webphone"
    local pass
    if [ -f "$ks" ]; then
        note "keystore already exists: $ks"
        pass="$(sed -n 's/^storePassword=//p' "$PROPS" 2>/dev/null || true)"
        [ -n "$pass" ] ||
            die "the keystore exists but $PROPS does not carry its password; refusing to guess"
    else
        # 64 hex characters from the kernel CSPRNG; `tr </dev/urandom |
        # head -c` would die of SIGPIPE under `set -o pipefail`.
        pass="$(od -An -tx1 -N 32 /dev/urandom | tr -d ' \n')"
        note "generating a 4096-bit RSA release key (validity 10000 days)"
        keytool -genkeypair \
            -keystore "$ks" -storepass "$pass" -keypass "$pass" \
            -alias "$alias" -keyalg RSA -keysize 4096 -validity 10000 \
            -dname "CN=webphone-android, O=Lars Artmann, C=DE" >/dev/null
        cat >"$PROPS" <<-EOF
			# Release signing for webphone-android. OUTSIDE the repo on purpose:
			# a keystore in git is a keystore whose password is in git.
			# Keep a backup of BOTH files — losing them means every installed
			# device must uninstall before it can take an update again.
			storeFile=$ks
			storePassword=$pass
			keyAlias=$alias
			keyPassword=$pass
		EOF
        chmod 600 "$PROPS" "$ks"
    fi
    local fp
    fp="$(print_key_fingerprint "$ks" "$pass" "$alias")"
    cat <<-EOF

		Signing key ready.
		  keystore : $ks
		  props    : $PROPS (chmod 600)
		  alias    : $alias
		  SHA-256  : ${fp:-<could not read>}

		Back both files up NOW (password manager / offline copy). An Android
		update must be signed by the SAME key as the installed app; losing
		this key forces an uninstall on every device.

		Has the stack to serve assetlinks.json for App Links (TODO_LIST R18)?
		scripts/assetlinks.sh $ks prints the release-key fingerprint variant.
	EOF
}

# -------------------------------------------------------------------- bump
bump() {
    local part="$1" major minor patch
    IFS=. read -r major minor patch < <(read_version)
    case "$part" in
    patch) patch=$((patch + 1)) ;;
    minor)
        minor=$((minor + 1))
        patch=0
        ;;
    major)
        major=$((major + 1))
        minor=0
        patch=0
        ;;
    *) die "bump takes patch|minor|major (got '$part')" ;;
    esac
    printf '%s.%s.%s\n' "$major" "$minor" "$patch" >version.txt
    note "version.txt -> $major.$minor.$patch"
    cat <<-EOF

		Next: cut CHANGELOG.md — move the [Unreleased] entries under
		'## [$major.$minor.$patch] - $(date +%F)', add a fresh '[Unreleased]'
		heading, commit, push, then run scripts/release.sh.
	EOF
}

# --------------------------------------------------------------- preflight
preflight() {
    local version tag head_tags

    command -v git >/dev/null || die "git not found"
    command -v "$PYTHON" >/dev/null || die "python3 not found (needed for the Forgejo API client)"

    version="$(read_version)"
    tag="v$version"

    [ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] ||
        die "releases are cut from main (currently on '$(git rev-parse --abbrev-ref HEAD)')"

    # A dry run and a doctor probe are rehearsals, so they tolerate a dirty
    # tree; a real release does not — the tag must describe exactly what
    # was built.
    if [ -n "$(git status --porcelain)" ]; then
        if [ "$DRY_RUN" = "1" ] || [ "$MODE" = "doctor" ]; then
            echo "warning: working tree is dirty (allowed for --dry-run/--doctor only)" >&2
        else
            die "working tree is dirty — commit the release state first so the tag and the artifact match"
        fi
    fi

    [ -f "$PROPS" ] ||
        die "no signing properties at $PROPS — run 'scripts/release.sh --init-key' first"
    grep -q '^storeFile=' "$PROPS" ||
        die "$PROPS has no storeFile= line — re-run --init-key with the file removed"

    # A concurrent build is how this repo lost hours before (AGENTS.md):
    # the crush-daily scheduler runs the gate on this checkout.
    if pgrep -f 'GradleWrapperMain' >/dev/null; then
        die "a gradle build is running (crush-daily gate?) — wait for it to finish"
    fi

    [ -f CHANGELOG.md ] || die "CHANGELOG.md is missing"
    grep -q "^## \[$version\]" CHANGELOG.md ||
        die "CHANGELOG.md has no '## [$version]' section — cut the changelog for this version first (scripts/release.sh bump does the arithmetic)"

    if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
        die "tag $tag already exists locally — bump version.txt (scripts/release.sh bump patch)"
    fi

    if git remote get-url origin >/dev/null 2>&1; then
        git "${GIT_SSH_QUIET[@]}" fetch --quiet origin main || die "cannot fetch origin (network / SSH agent?)"
        head_tags="$(git "${GIT_SSH_QUIET[@]}" ls-remote --tags origin "refs/tags/$tag" || true)"
        [ -z "$head_tags" ] || die "tag $tag already exists on the forge"
        local behind
        behind="$(git rev-list --count "HEAD..origin/main")"
        [ "$behind" = "0" ] ||
            die "local main is $behind commit(s) behind origin/main — pull before releasing"
    else
        echo "warning: no 'origin' remote configured; skipping the forge tag check" >&2
    fi

    if [ -z "${FORGEJO_TOKEN:-}" ] && [ -f "$TOKEN_FILE" ]; then
        FORGEJO_TOKEN="$(tr -d '[:space:]' <"$TOKEN_FILE")"
        export FORGEJO_TOKEN
    fi
    if [ "$MODE" = "release" ] && [ "$DRY_RUN" = "0" ] && [ -z "${FORGEJO_TOKEN:-}" ]; then
        die "no Forgejo token — create one (Forgejo > Settings > Applications, scope write:repository) and either export FORGEJO_TOKEN or write it to $TOKEN_FILE (chmod 600)"
    fi

    echo "release: $tag (versionCode $(version_code)) on $REPO via $BASE_URL"
}

# -------------------------------------------------------------------- gate
gate() {
    [ -n "${ANDROID_HOME:-}" ] ||
        die "ANDROID_HOME is unset — run inside 'nix develop' (the build-tools zipalign/apksigner live there)"
    local build_tools="$ANDROID_HOME/build-tools/36.0.0"
    local apk="app/build/outputs/apk/release/app-release.apk"
    local unsigned="app/build/outputs/apk/release/app-release-unsigned.apk"

    note "building + testing (assembleRelease, testDebugUnitTest, ktlintCheck)"
    ./gradlew --console=plain assembleRelease testDebugUnitTest ktlintCheck

    [ -f "$apk" ] || {
        [ -f "$unsigned" ] &&
            die "the release APK is UNSIGNED ($unsigned) — signing properties did not load; check $PROPS"
        die "release APK missing: $apk"
    }

    note "16 KB page-alignment gate"
    "$build_tools/zipalign" -c -P 16 4 "$apk" ||
        die "release APK is not 16 KB aligned (mandatory for Android 16; see AGENTS.md)"

    note "ABI completeness gate (the APK stays universal)"
    "$PYTHON" scripts/abi-check.py "$apk" ||
        die "release APK is missing an ABI (arm64-v8a/armeabi-v7a/x86/x86_64) — check the webrtc dependency"

    note "signature verification"
    "$build_tools/apksigner" verify --print-certs "$apk" | sed -n '1,8p'

    note "contrast gate (WCAG token pairs)"
    "$PYTHON" scripts/contrast-check.py
}

# ------------------------------------------------------------------ stage
stage() {
    local version apk="$1" dir
    version="$(read_version)"
    dir="work/release/$version"
    mkdir -p "$dir"
    local asset="$dir/webphone-android-$version.apk"
    cp "$apk" "$asset"
    local notes="$dir/notes.md"
    if [ -n "$NOTES_FILE" ]; then
        cp "$NOTES_FILE" "$notes"
    else
        # The changelog IS the release note: the section for this version,
        # stripped of the heading (the forge renders the tag as the title).
        awk -v v="## [$version]" '$0 == v || index($0, v " ") == 1 { p = 1; next } p && /^## \[/ { exit } p' \
            CHANGELOG.md >"$notes"
    fi
    {
        echo
        echo "---"
        echo
        echo "Install/update via Obtainium (Forgejo source)."
        echo
        echo "Version: \`$version\` (versionCode \`$(version_code)\`)"
        echo
        echo "Signing certificate SHA-256:"
        echo
        echo '```'
        "${ANDROID_HOME:?}/build-tools/36.0.0/apksigner" verify --print-certs "$asset" |
            awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print $2 }' |
            tr 'a-f' 'A-F' | sed 's/\(..\)/\1:/g; s/:$//'
        echo '```'
    } >>"$notes"
    STAGED_ASSET="$asset"
    STAGED_NOTES="$notes"
    note "staged: $asset ($(du -h "$asset" | cut -f1))"
}

# ----------------------------------------------------------------- publish
publish() {
    local version tag
    version="$(read_version)"
    tag="v$version"

    note "tagging $tag"
    git tag -a "$tag" -m "webphone-android $version"
    git "${GIT_SSH_QUIET[@]}" push origin main "$tag" || die "push failed — the tag exists locally; delete it (git tag -d $tag) and retry"

    note "publishing release to $REPO"
    "$PYTHON" scripts/forgejo-release.py \
        --base-url "$BASE_URL" \
        --repo "$REPO" \
        --tag "$tag" \
        --name "webphone-android $version" \
        --notes-file "$STAGED_NOTES" \
        --asset "$STAGED_ASSET" \
        "${insecure_args[@]}"
}

# --------------------------------------------------------------------- main
while [ $# -gt 0 ]; do
    case "$1" in
    --init-key) MODE="init-key" ;;
    --doctor) MODE="doctor" ;;
    --dry-run) DRY_RUN=1 ;;
    --notes)
        shift
        NOTES_FILE="${1:?--notes needs a file}"
        ;;
    bump)
        MODE="bump"
        shift
        BUMP="${1:?bump takes patch|minor|major}"
        ;;
    -h | --help)
        usage
        exit 0
        ;;
    *) die "unknown argument '$1' (try --help)" ;;
    esac
    shift
done

insecure_args=()
if [ "${FORGEJO_INSECURE:-0}" = "1" ]; then
    insecure_args=(--insecure)
fi

case "$MODE" in
init-key) init_key ;;
bump) bump "$BUMP" ;;
doctor)
    preflight
    note "signing key: $(grep '^storeFile=' "$PROPS" | cut -d= -f2-)"
    "$PYTHON" scripts/forgejo-release.py --base-url "$BASE_URL" --repo "$REPO" "${insecure_args[@]}" --check ||
        echo "release: the forge probe failed (see above) — the token/reachability blocker must be fixed before a release" >&2
    ;;
release)
    preflight
    gate
    stage app/build/outputs/apk/release/app-release.apk
    if [ "$DRY_RUN" = "1" ]; then
        note "dry run: tagged/uploaded nothing"
        note "would publish v$(read_version) from $STAGED_ASSET"
        exit 0
    fi
    publish
    cat <<-EOF

		Published. On the phone, once:
		  Obtainium > Add App > URL https://${BASE_URL#https://}/lars/webphone-android
		  - source: Forgejo (Codeberg)
		  - Forgejo access token: read-only token for this private repo
		  - per-app option "Allow insecure HTTP requests" ON — the forge's
		    certificate comes from the internal dnsblockd CA and is not in
		    Android's system trust store
		  - reachable only on the home LAN or the NetBird VPN
		After that, every release here is an update prompt on the phone.
	EOF
    ;;
esac
