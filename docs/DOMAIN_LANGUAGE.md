<!-- docs/DOMAIN_LANGUAGE.md — the UBIQUITOUS LANGUAGE glossary. Domain terms,
     not implementation details. Every term is used in the codebase. -->

# Domain Language

> The ubiquitous language of this project. Every term used in code,
> conversations, and docs should be defined here unambiguously.

## Core terms

| Term                     | Definition                                                                                                                                              | Where used in code                                                              |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| the island               | The webphone web application: the tab surfaces (Messages, Fax, Voicemail, History, Contacts, Settings)                                                  | `shell/MainActivity.kt`, `shell/SessionApi.kt` KDocs                            |
| the stack                | The webphone server platform the island and this shell both talk to                                                                                     | README, `shell/SessionApi.kt`                                                   |
| stack origin             | The stack's base URL; the one configuration constant (`App.origin`)                                                                                     | `App.kt:45`, `shell/SessionApi.kt`                                              |
| the shell                | This app: the Android native-call shell wrapping the island's pages in a WebView                                                                        | README, `shell/` package                                                        |
| call contract            | The SIP signaling + WebRTC media ownership the platform decision moved from the browser into Kotlin                                                     | README, `shell/MainActivity.kt` KDoc                                            |
| ring fact                | The push payload `{event, from, call_id}` both transports carry; becomes a system call in the router                                                    | `push/RingRouter.kt`, `push/FcmReceiver.kt`, `push/NtfySubscriptionService.kt`  |
| lane                     | A push transport: the FCM lane (GMS devices) or the ntfy lane (de-Googled)                                                                              | README, `push/*` KDocs                                                          |
| device train             | A real-device validation round against live infrastructure (the first one retires the honesty contract)                                                 | README, `call/CallActivity.kt` KDoc                                             |
| honesty contract         | The README section listing what is implemented-but-untested; markers, not marketing                                                                     | README "Status: v0.1 scaffold"                                                  |
| session seam             | The cookie → SIP-credentials exchange: WebView login mints the cookie, `resumeSession` trades it for registration facts                                 | `shell/SessionApi.kt` KDoc                                                      |
| FGS                      | Typed Android foreground service: `microphone\|phoneCall` for a live call, `dataSync` for the ntfy subscription                                         | `AndroidManifest.xml`, `call/CallService.kt`, `push/NtfySubscriptionService.kt` |
| self-managed connection  | A Telecom `Connection` with `PROPERTY_SELF_MANAGED`: the app owns its call UI, the OS gets call-state integration (BT, watches, DND)                    | `call/CallConnectionService.kt`                                                 |
| FSI (full-screen intent) | The incoming-call notification that takes over the lockscreen; `USE_FULL_SCREEN_INTENT` permission                                                      | `push/RingRouter.kt`, `AndroidManifest.xml`                                     |
| hostToken                | The generated `<uuid12>.invalid` token in Via/Contact headers — the WSS NAT convention, deliberately not a real hostname                                | `sip/SipEngine.kt:475`                                                          |
| sipfrag                  | The `message/sipfrag` body of a NOTIFY carrying the REFER transfer verdict (`SIP/2.0 200 OK` = success)                                                 | `sip/SipEngine.kt:337`                                                          |
| equals form              | The `Signal=5` encoding of `application/dtmf-relay`; never the colon form (FreeSWITCH 200-OKs and drops it)                                             | `sip/SipEngine.kt:272`                                                          |
| watchdog                 | The bounded liveness discipline: registration refreshed at expires/2, dead socket rebuilt after backoff — UI state follows the engine, never the socket | `sip/SipEngine.kt:127`                                                          |
| de-Googled               | Devices without Play services; the ntfy lane exists for them with zero Google dependencies                                                              | README, `push/NtfySubscriptionService.kt`                                       |
| sideload lane            | Distribution outside Play; keeps the default full-screen-intent grant that Play would re-audit                                                          | README "Distribution", `push/RingRouter.kt`                                     |
| upsert by device id      | The push-registry row model: token rotation replaces the row keyed by the stable per-install `device_id`                                                | `shell/SessionApi.kt:76`, `shell/DeviceIdentity.kt`                             |

## Bounded contexts

None yet — one module, one vocabulary. Revisit if the shell grows beyond the
single `:app` module.

## Deprecated terms

None.
