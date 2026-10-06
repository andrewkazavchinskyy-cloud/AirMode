# AirMode — approved scope

User supplied the AirMode PRD on 2026-10-06, then required current stable/beta Google Pixel 10 Pro and 11 Pro compatibility and native Android 17 design.

- Kotlin, one Compose Material 3 Activity, app.airmode, min31 / compile36 / target36, Apache2.0.
- AirPods4 nonANC A3053/A3050/A3054: battery only. ANC A3056/A3055/A3057.
- AirPods5 A3531/A3532/A3533 and wireless-case A3439/A3440/A3441: ANC.
- Three individual battery components, unknown remains —, charging and >2min stale indication.
- Four listening modes with acknowledged state, no optimistic success; ≤1.5s switching, one retry700ms, ≥400ms writes.
- Native quick settings tile uses the same confirmed StateFlow and chosen cycle of ≥2 modes.
- Single permission screen; Bluetooth CONNECT/SCAN neverForLocation, optional notifications. No location, microphone, INTERNET or network code.
- Silent connection popup8s defaulton, battery FGS notification defaultoff; Android requires minimal connection FGS notification while transport is active.
- DataStore: popup/autostart defaulton, persistent defaultoff, tile modes, language ru default/en/system,onboarding. System theme and dynamic Material expressive native style.
- ACL/A2DP/HEADSET discovery, one connected pair, renamed candidates via metadata/Apple accessory services, protocol model proof before any mode write.
- 4s BLE windows ≥15s apart on connection/open; socket closes on disconnect, FGS stops30s later.
- BOOT_COMPLETED only after onboarding + autostart + observed connected headphone; background restriction yields actionable error.
- No Pro/Max/older/Beats mode writes, head gestures, auto pause, extra screens, themes, cloud, ads, payments, account, root, Xposed runtime or VendorID modification.
- Independent protocol implementation. Public packet facts cited; no companion-app source copied.
- Latest OS/beta compatibility is tested per exact build, not inferred from Pixel name or guaranteed for future releases.

## Release gate
Unit tests and installable signed release are necessary. Physical AirPods acoustic confirmation, battery truth, reconnect/reboot/tile/stem update and Pixel8 coldstart<1s require hardware. The user now supplied a sanitized report from their separate phone; it confirms received battery packets, not independent percentage accuracy or the full checklist. docs/VERIFICATION.md is the evidence ledger.

## Latest user revision: 0.1.2

The user explicitly requested a home-screen widget, overriding the original no-widget restriction. Native RemoteViews provide three battery components and direct access to the four modes through the same foreground service and live protocol/model guards. Cached widget text cannot authorize a command.

Unavailable components immediately show —; retained percentages are labelled last-known and keep their original observation time. Android metadata is shown as last-known even on first cache read, and tracked independently and cannot overwrite a present live protocol reading. Popup subscriptions remain immediate and updates keep the original 8-second deadline.

Adaptive capability negotiation is attempted once per live ANC session, only after explicit Adaptive selection. No CA-setting command or UI is added. The documented capability mask has broader effects on other firmware; physical retesting must confirm mode 4 and absence of unrelated behavior changes. No success is inferred from transmitting capabilities.

## Latest revision:0.2.0

The user supplied0.1.2 physical diagnostics and reports remaining unknown case battery, other functionality good, requesting prettier native app/widget and all Apple headphones plus Sony. Current implementation expands to all official wireless AirPods families (including Pro/Max), with battery-only older/nonANC models and supported modes per model. Beats/wired EarPods remain unimplemented and cannot be advertised as covered. Sony uses separate experimental native MDR V1/V2, verified service/version/model/capabilities and recognized schemas; unknown families/firmware disable unsupported operations. Sony Adaptive Sound Control is not Apple mode4.

The actual user's Adaptive reply arrives1807ms after write, past the original deadline. Keep visible loading≤1500ms, then neutral awaiting confirmation without optimistic mode; final failure2500ms. Retain≥400ms physical writes and one700ms retry. This explicit hardware-evidence revision supersedes the original immediate1500ms error criterion.

Apple live-session proximity keys may transiently attribute private BLE addresses and decode only known layouts. No keys on disk/in reports; clear on disconnect/session replacement. Reject replay counters/unknown fields. No synthesized case percent or nearby-pair/RSSI inference. Default offline/privacy architecture remains unchanged.
