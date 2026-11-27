# SIP UA vs live FreeSWITCH — smoke checklist

**Status:** OPERATOR-FACING (needs a live stack + a real device) ·
**Source contracts:** webphone `docs/planning/2026-11-13_16-40_android-handover-pack.md`

- webphone AGENTS § Telephony · **Created:** 2026-11-27 (pareto T21 slice 1,
  m21.3/m21.5) · First run owes: the first device train (operator-blocked,
  android TODO row "First device train").

The JVM suites prove the wire SHAPES (fixtures, decoders, verdict
classifiers). This checklist proves the DEVICE conversation: the app's
SIP UA (WebRTC offer/answer over the WSS) against the live FreeSWITCH —
the class of failure no fixture can carry (NAT, codecs, AEC, DTMF,
ring timing). Work top to bottom; every step names its honest failure
signal. `scripts/ring-diagnose.sh` is the on-record verdict tool for
the ring-while-closed legs.

## Prerequisites

1. A deployed stack with the app's target origin reachable over https
   (the Settings screen accepts it, `usesCleartextTraffic="false"` is
   non-negotiable).
2. An extension + SIP directory password that verifies against the PBX
   directory (or a passkey mapped to it — the island's `/login` is the
   one login surface; the native shell rides the WebView's session).
3. Android 12+ device (API 31 floor; CallStyle + the modern Telecom
   path), signed-in to the origin in the WebView.
4. The ntfy push lane ON in the deployed config (`push.ntfy.url`) if
   the ring-while-closed legs are in scope; the FCM lane needs its
   service account (`FCM_ACTIVATION.md`).

## A. Registration + basic call path

- [ ] **A1 Register.** Open the app → the WebView shows the island's
      boot probe; the registration pill reaches REGISTERED (green).
      _Fail signal:_ pill stays offline — check the origin in Settings
      and the stack's `/sip` WSS proxy (the Go app never terminates
      the SIP wss).
- [ ] **A2 Outbound call.** Dialpad → a working test extension → the
      native Telecom call screen comes up (CallConnectionService), the
      far end rings. _Fail signal:_ no INVITE on the PBX — the WSS
      bridge or the ICE servers.
- [ ] **A3 Two-way audio.** Both directions audible on hardware.
      _Fail signal:_ one-way audio — NAT/ICE; try the TURN lane before
      debugging the app.
- [ ] **A4 Inbound answer.** Call the device from another phone → the
      incoming call screen + ringtone; answer; audio both ways.
- [ ] **A5 Hold/resume.** The in-call hold toggle: the far side hears
      the hold state flip, audio resumes clean. The island pauses live
      transcription on hold — nothing to verify natively.
- [ ] **A6 DTMF.** During the call, dial digits; the PBX side hears
      them (`application/dtmf-relay`, `Signal=` form — the colon form
      200-OKs and silently drops, per the server repo's telephony
      notes).
- [ ] **A7 Hangup.** Both directions end the call; the shade's call
      notification clears (the ENDED phase drives
      `STOP_FOREGROUND_REMOVE`).

## B. Ring-while-closed (the product's hard requirement)

- [ ] **B1 Ring closed.** Sign out of the WebView's foreground (or
      close the app), register the push device (the session must be
      alive — Settings → check the push verdict line), call the device.
      _Pass:_ the full-screen incoming call over the lock screen
      (USE_FULL_SCREEN_INTENT), CallStyle Answer/Decline in the shade;
      answering brings the SIP leg up (the bootstrap races the INVITE).
- [ ] **B2 End cleans up.** The caller cancels → the `end` fact
      cancels the notification; an unanswered ring on THIS device
      leaves a missed-call note (its own notification id, 1005).
- [ ] **B3 Missed push ≠ missed ring.** Kill the push lane (airplane
      the data, or stop ntfy) → the PBX still rings the extension on
      its own devices. Delivery is ADVISORY by contract.
- [ ] **B4 Verdict on record.** `scripts/ring-diagnose.sh` for the
      end-to-end verdict; file its output with any red leg.

## C. Line ownership (the 2026-11-18 typed wire)

- [ ] **C1 Picker visibility.** An account with ONE line: Settings
      shows no "Active line" section (byte-parity with single-line
      deployments); an account with 2+: the section lists every owned
      line, the active one marked, others are actions.
- [ ] **C2 Switch.** Tap another line → the toast names the verdict;
      SWITCHED tears the call layer + push lane down and finishes the
      surface — the shell's next resume re-wires onto the switched
      session (fresh registration credential, re-registered push).
      _Fail signal:_ NOT_MAPPED (the line is not the login's),
      SEAM_OFF (the server's identity layer is off — extension-only
      logins cannot switch).
- [ ] **C3 No mid-call switch.** A live CallStyle call must not
      re-mint (the registration credential changes with the session);
      the Settings picker is only reachable outside a call by
      construction — verify a call in flight survives backgrounding.

## CallStyle wiring — verified static findings (m21.5, 2026-11-27)

The spec half of the handover pack is IMPLEMENTED; the checklist above
owes only the device-proof:

- Manifest: `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE_{MICROPHONE,PHONE_CALL,DATA_SYNC}`,
  `MANAGE_OWN_CALLS`; `CallActivity` carries `showWhenLocked` +
  `turnScreenOn` (the lock-screen answer path).
- Incoming ring (`push/RingEvent.kt`): Telecom `addNewIncomingCall` +
  `CallLayer.ensureWired` (the bootstrap races the INVITE) +
  `CallStyle.forIncomingCall` with Answer/Decline (PendingIntents to
  `CallNotificationReceiver`) — a CallStyle rejection falls back to
  the plain heads-up instead of swallowing the ring. The full-screen
  intent is set unconditionally below API 34 and gated on
  `canUseFullScreenIntent()` from 34 (the NewApi floor finding, pinned
  by `RingEventApiFloorTest`).
- Ongoing call (`call/CallService.kt`): `CallStyle.forOngoingCall` with
  the shade hang-up action; a rejected style falls back to a plain
  pill; the ENDED phase removes the notification.
- `end` fact → `standDown()` cancels the incoming notification and
  stands the Telecom connection down; the missed-call note fires only
  for an unanswered ring ON THIS DEVICE.
- The ring payload carries NO caller name (privacy posture — names
  never leave through third-party push); the notification shows the
  raw number, per the pack.

## Known flake modes (from the 2026-11-10/11 device sessions)

- First screencap after a cold screen is black — wake before capture.
- The answer-path race (ring push → bootstrap → INVITE) is timing
  sensitive on a cold start; re-run once before digging.
- OEM battery managers killing the dataSync socket are the #1
  missed-ring cause on ntfy devices — grant the in-context battery
  exemption (the manifest declares
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for exactly this dialog).
