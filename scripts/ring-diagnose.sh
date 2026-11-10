#!/usr/bin/env bash
# ring-diagnose.sh — the next "why no ring" is ONE command.
#
# Collects every fact the ring chain needs from a connected device
# (install, grants, battery exemption, push socket, the app's own
# verdict logs) and prints a hint table keyed on what it found. The
# ring chain: FS dialplan -> ringhook -> POST /hooks/ring -> webphone
# fanout -> ntfy -> this phone's websocket -> RingEvent -> Telecom.
# Everything server-side of ntfy is owner territory; this script
# proves (or indicts) the phone half and names the next move.
#
# Usage: bash scripts/ring-diagnose.sh [serial]
#   (serial defaults to ANDROID_SERIAL or the single attached device;
#    run inside `nix develop` — the devShell owns the adb this
#    session must use, two adb trees share port 5037 and kill each
#    other's server)

set -u

PACKAGE="software.larsartmann.webphone"
SERIAL="${1:-${ANDROID_SERIAL:-}}"

adb_pick() {
    if [ -n "$SERIAL" ]; then
        adb -s "$SERIAL" "$@"
    else
        adb "$@"
    fi
}

hr() { printf '%s\n' "--------------------------------------------------------------"; }

hr
echo "WebPhone ring diagnosis — $(date '+%Y-%m-%d %H:%M:%S')"
hr

DEVICES="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
if [ -z "$DEVICES" ]; then
    echo "FAIL  no adb device in 'device' state."
    echo "      Wireless pairing drifts: adb connect <phone-ip>:<port>, then re-run."
    exit 1
fi
if [ -z "$SERIAL" ]; then
    COUNT="$(printf '%s\n' "$DEVICES" | wc -l)"
    if [ "$COUNT" -ne 1 ]; then
        echo "FAIL  $COUNT devices attached, none pinned. Re-run with a serial:"
        printf '      %s\n' $DEVICES
        exit 1
    fi
    SERIAL="$(printf '%s\n' "$DEVICES" | head -1)"
fi
echo "device: $SERIAL"

# --- the app is installed, and WHICH build -------------------------------
DUMP="$(adb_pick shell dumpsys package "$PACKAGE" 2>/dev/null)"
if [ -z "$DUMP" ]; then
    echo "FAIL  $PACKAGE is not installed on this device — nothing to diagnose."
    exit 1
fi
VERSION="$(printf '%s\n' "$DUMP" | grep -m1 'versionName' | sed 's/.*versionName=//')"
GRANTED="$(printf '%s\n' "$DUMP" | grep -m1 'POST_NOTIFICATIONS' | sed 's/.*granted=//')"
FIRST_INSTALL="$(printf '%s\n' "$DUMP" | grep -m1 'firstInstallTime' | awk '{print $2}')"
echo "build: v$VERSION (installed since $FIRST_INSTALL)"

# --- grants: a push that arrives INVISIBLE is a silent ring --------------
echo
echo "[ grants ]"
if [ "$GRANTED" = "true" ]; then
    echo "ok    POST_NOTIFICATIONS granted"
else
    echo "HINT  POST_NOTIFICATIONS NOT granted — rings arrive INVISIBLE on API 33+."
    echo "      Fix: open WebPhone (it asks in context) or grant in Settings."
fi
WHITELIST="$(adb_pick shell dumpsys deviceidle whitelist 2>/dev/null | grep -c "$PACKAGE" || true)"
if [ "${WHITELIST:-0}" -ge 1 ]; then
    echo "ok    battery exemption (deviceidle whitelist)"
else
    echo "HINT  no battery exemption — OEM battery managers kill dataSync sockets."
    echo "      Fix: the pill asks once per install after first registration; or"
    echo "      Settings > Battery > Unrestricted for WebPhone."
fi

# --- the wake lane: is anything actually up right now? --------------------
echo
echo "[ wake lane (live services) ]"
NTFY_LIVE="$(adb_pick shell dumpsys activity services "$PACKAGE" 2>/dev/null | grep -c 'NtfySubscriptionService' || true)"
if [ "${NTFY_LIVE:-0}" -ge 1 ]; then
    echo "ok    NtfySubscriptionService is alive"
else
    echo "HINT  NtfySubscriptionService NOT running — the closed-app ring lane is down."
    echo "      Normal after a dead-session teardown; abnormal right after opening the app."
fi

# --- what the app itself said, most recent first --------------------------
echo
echo "[ app verdicts (last 24h of logcat, ring-chain tags) ]"
LOGS="$(adb_pick logcat -d -t 3000 2>/dev/null | grep -E 'SessionApi|NtfySubscription|RingEvent|CallLayer|CallEngine|SipEngine|TelecomManager.*webphone' | tail -60)"
if [ -z "$LOGS" ]; then
    echo "(no ring-chain log lines in the buffer — open the app once to generate verdicts)"
else
    printf '%s\n' "$LOGS" | sed 's/^/      /'
fi

# --- the hint table -------------------------------------------------------
echo
echo "[ hints keyed on what the log says ]"
hint() {
    if printf '%s\n' "$LOGS" | grep -q "$1"; then
        echo "  FOUND  $1"
        printf '         %s\n' "$2"
    fi
}
hint 'register answered 404' \
    'server push seam is OFF (404): set push.ntfy.url in the webphone config (owner).'
hint 'register answered 403' \
    'session/CSRF rejected (403): open the app and sign in again.'
hint 'register answered 422' \
    'wake address rejected (422): invalid token, or the account device list is full.'
hint 'GET /api/session answered 401' \
    'no session: the pill cannot arm. Open WebPhone, sign in (Pocket ID), reopen.'
hint 'GET /api/session answered null' \
    'the stack was UNREACHABLE at probe time: WiFi/DNS/VPN — check the link, then resume.'
hint 'ntfy subscription failure' \
    'websocket down: check that the origin routes <topic>/ws to ntfy, and the network link.'
hint 'ntfy subscription open' \
    'socket was open at some point — the lane transport itself came up.'
hint 'RingEvent' \
    'a ring push ARRIVED — if the phone stayed silent below this line, the ring
         surface (notification/Telecom) is the failing stage; capture a full logcat around it.'
hint 'Sign-in rejected' \
    'SIP auth was rejected: the session identity was stale — sign in again.'
hint 'push device registered' \
    'the wake address was accepted at some point — the registry half is healthy.'

echo
hr
echo "Still silent with all hints green? The failure is server-side of ntfy:"
echo "ringhook.enable + push.ntfy.url on the stack, and a live POST /hooks/ring."
echo "Then capture: adb -s $SERIAL logcat -d > /tmp/ring.log and re-run this."
hr
