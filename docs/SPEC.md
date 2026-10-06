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
- No Pro/Max/older/Beats mode writes, head gestures, auto pause, extra screens, widgets, themes, cloud, ads, payments, account, root, Xposed runtime or VendorID modification.
- Independent protocol implementation. Public packet facts cited; no companion-app source copied.
- Latest OS/beta compatibility is tested per exact build, not inferred from Pixel name or guaranteed for future releases.

## Release gate
Unit tests and installable signed release are necessary. Physical AirPods acoustic confirmation, battery truth, reconnect/reboot/tile/stem update and Pixel8 coldstart<1s require hardware. User confirmed hardware unavailable; never mark those verified. docs/VERIFICATION.md is the evidence ledger.
