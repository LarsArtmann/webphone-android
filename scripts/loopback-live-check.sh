#!/usr/bin/env bash
# Loopback live-check (wire fixture (a), pareto T16): boot a loopback
# webphone and drive the app's REAL decoders (SessionApi) against its
# LIVE /config.js + /api/session answers — the deployment-level proof
# the byte-pinned fixtures cannot give. The session half additionally
# exercises the mint → cookie → resume chain over the loopback skip
# (loopback boots skip the directory verify, so any values mint).
#
# Usage: scripts/loopback-live-check.sh [--bin /path/to/webphone] [--port 18102]
# Env: WEBPHONE_LIVE_EXT / WEBPHONE_LIVE_PASSWORD override the mint
# credentials; WEBPHONE_REPO points at the webphone checkout when the
# sibling default does not hold.
set -euo pipefail

BIN=""
PORT=18102
while [ $# -gt 0 ]; do
    case "$1" in
        --bin) BIN="$2"; shift 2 ;;
        --port) PORT="$2"; shift 2 ;;
        *) echo "unknown arg $1" >&2; exit 2 ;;
    esac
done

REPO="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
PID=""
cleanup() {
    if [ -n "$PID" ]; then
        kill "$PID" 2>/dev/null || true
    fi
    rm -rf "$TMP"
}
trap cleanup EXIT

if [ -z "$BIN" ]; then
    echo "== building webphone (the sibling checkout) =="
    BIN="$TMP/webphone-bin"
    (cd "${WEBPHONE_REPO:-$REPO/../webphone}" && go build -o "$BIN" ./cmd/webphone)
fi

echo "== booting loopback webphone on 127.0.0.1:$PORT =="
# sip_domain is REQUIRED for the config half: a bare loopback boot
# serves an empty sipDomain and the real decoder refuses it BY DESIGN
# (registering nowhere must fail loudly — that is the contract, not a
# gap). Any plausible domain value proves the decode.
env WEBPHONE_ADDR="127.0.0.1:$PORT" WEBPHONE_DATA_DIR="$TMP/data" WEBPHONE_SIP_DOMAIN="${WEBPHONE_SIP_DOMAIN:-pbx.test}" "$BIN" &
PID=$!

for _ in $(seq 1 60); do
    if curl -sf "http://127.0.0.1:$PORT/livez" >/dev/null 2>&1; then
        break
    fi
    sleep 0.5
done
curl -sf "http://127.0.0.1:$PORT/livez" >/dev/null || {
    echo "boot failed: /livez never answered" >&2
    exit 1
}

# Boot self-check: the served config.js must carry a NON-EMPTY sipDomain
# before the JVM suite runs — the decoder's empty-domain Rejection is the
# contract, so a boot without the flat WEBPHONE_SIP_DOMAIN key would fail
# here with the honest reason instead of a confusing gradle red.
curl -sf "http://127.0.0.1:$PORT/config.js" | grep -q '"sipDomain":"[^"]' || {
    echo "boot failed: config.js carries an empty sipDomain (set WEBPHONE_SIP_DOMAIN)" >&2
    exit 1
}

echo "== driving the real decoders (Robolectric JVM suite) =="
cd "$REPO"
WEBPHONE_LIVE_BASE="http://127.0.0.1:$PORT" \
    WEBPHONE_LIVE_EXT="${WEBPHONE_LIVE_EXT:-1001}" \
    WEBPHONE_LIVE_PASSWORD="${WEBPHONE_LIVE_PASSWORD:-loopback-smoke}" \
    ./gradlew --quiet :app:testDebugUnitTest --tests "software.larsartmann.webphone.wire.LoopbackLiveContractTest"

echo "loopback live-check: PASS"
