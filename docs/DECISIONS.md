# Implementation decisions

## Scope and completion evidence
Two quick Council reviews used three independent worker seats (Ada formal invariants weight1.5, Feynman observable evidence1.0, Torvalds shipping1.0), two rounds each; root chaired with same-model overlap. Initial BUILD_HONEST review: three medium-confidence votes, weighted2.625/3.5, no dealbreakers. Build/publish a signed prerelease while explicitly retaining the physical-device readiness gate. Confidence is medium for implementation, low for unobserved hardware interoperability.

## Actual classic transport access
Material new evidence required a second review: official Android16 hidden-API flags mark ordinary createL2capSocket access max-target-o; public BluetoothSocketSettings supports LE/RFCOMM, not required Classic PSM0x1001. Three medium-confidence votes favored NARROW_BRIDGE, weighted2.625/3.5; no dealbreakers. One intermediate peer attribution was corrected before final votes; the final three responses are independent completed seats.

Use pinned Apache2.0 generic HiddenApiBypass6.1 solely for native createL2capSocket invocation. No global exemptions, system policy modification, privilege grants, VendorID changes, root or Xposed runtime. This is not a Bluetooth protocol library. Do not conceal its presence. Normal Bluetooth Binder permissions remain enforced. Unsupported runtime invocation/connect/framing/model/ACK fails to actionable unavailable state. Emulator construction of TYPE_L2CAP proves factory access only; physical acoustic and protocol qualification remain mandatory.

Tradeoff: internal Android runtime APIs can break on a beta/OEM build. Test exact builds; never claim blanket beta support. Kill criteria: runtime probe fails, broad exemptions/privileges become required, safe device/model attribution fails, firmware response is unrecognized, or command is unconfirmed. Disable affected feature and retain truthful available battery data.

## Native Android17 visual requirement with compile36
Use system dynamic color, MaterialExpressiveTheme, native connected ToggleButton groups and native settings rows. Material3 1.5.0-alpha18 and BOM2026.06.00 are pinned because newer available versions require compile37/AGP9.1, conflicting with the explicit mandatory compile36 stack. The app still runs on newer OS versions; compile SDK is not an upper runtime limit. No extra theme screen or custom theme switch.

## Data truth
Only protocol model-number allowlist authorizes writes. Outgoing packets never establish success. Session-generation guards reject stale callbacks. Model name, RSSI and generic Apple BLE manufacturer ID never establish pair identity. BLE data is accepted only for exact matching address, so some random-address devices may have no battery fallback; disclose it instead of merging nearby pairs. Cached metadata is optional and may require unavailable system privilege. Never duplicate one aggregate percentage into L/R/case. All readings use monotonic elapsedRealtime; unchanged cached metadata cannot refresh stale time.

## Foreground notification
Android requires a foreground notification while maintaining control. Optional permanent battery notification is off by default; an unobtrusive connection status remains required while active. Connection popup is silent and8s; permission refusal does not disable in-app data or tile.

## Review metadata
schema_version:1; mode:quick; panel_size:3; rounds_run:2 per review; provider_count:1; live:3; degraded:0; offline:0; tools_used:collaboration + primary sources; fallbacks_triggered:none; token/duration measures:unknown.

## User-reported battery/popup defect,0.1.1
User tested Pixel10Pro CP41.260831.007.A3 with AirPods5: modes switch, battery/popup absent. Phone is separate; user requested a diagnostic APK. Preserve strict battery parsing, send both documented notification masks once, and capture sanitized debug evidence before claiming hardware fix. Same release key signs diagnostics for an in-place update. Latest user request for a visible popup revises the original DEFAULT channel to a new silent HIGH channel, required for native heads-up; Android channel importance is immutable after creation. Decorated three-component RemoteViews remain within native notification chrome, no overlay permission/activity/fullscreen. Confirmed supported model can show truthful— values until charge arrives; updates retain the original8s deadline. Diagnostic test values are labelled and never written to Repository.

## Actual hardware report and launcher widget, 0.1.2

A distinct quick Council reviewed the new evidence and explicit launcher-widget request: Feynman weight 1.5, Ada 1.0, Socrates 1.0, two rounds, three live seats, one inherited model/provider. All retained FIX_APPLY at medium confidence, weighted 2.625/3.5; no dealbreakers. Agreement does not establish hardware correctness.

The report includes actual AAP left 48 / right 61 / case 100 packets and later case status 4 with level 00/FF. This proves 100 came over the wire; the later claimed 50 is not in this report. Fix deterministic availability/cache arbitration and retain history explicitly. A separate metadata cache prevents unchanged system values acquiring a new timestamp from merged state. Use native RemoteViews rather than an additional widget framework, and revalidate a live supported session for every cold command.

One documented 4d FF declaration is sent on the first explicit Adaptive request in a proven ANC session, before the mode write; initial battery subscriptions are not delayed. This is a bounded compatibility trial, not an AirPods5-qualified or CA-neutral claim. Public notes associate the mask with Adaptive and CA during audio. Do not send CA-setting writes or expose CA controls. Only incoming mode 4 confirms Adaptive. Preserve the 400 ms write interval, one retry and 1.5 s deadline.

Kill criteria: unsupported identity, unconfirmed mode, unexpected CA/audio behavior, session instability or broader privileges required. Stop further capability trials if unrelated behavior changes and retain the known three-mode path. Exact firmware/Pixel-build retesting remains required. Battery state is process-local; elapsed timestamps are never restored across reboot as fresh observations.

## Expanded capabilities and case evidence,0.2.0

One cohesive quick Council before implementation: Feynman1.5, Ada1.0, Socrates1.0; two rounds, three live same-inherited-model seats, one provider. All retained CAPABILITY at medium confidence; weighted2.625/3.5, no dealbreakers. Medium implementation confidence is not hardware qualification.

New report confirms incoming Apple mode4, delayed1807ms after request. Loading remains≤1500ms but neutral confirmation can continue to2500ms; no optimistic selection. Case status4/00 is unavailable, not zero or a guessed percentage. Separate live case BLE data must survive an unavailable bud-link report. Fix scan attribution to selected-pair epoch rather than control-session epoch.

Implement original bounded Apple AES proximity decoding only after fresh live Apple identity and returned keys; memory-only, strict layout/counter guards. Expand official AirPods model table and battery shape; older devices battery-only. Sony uses native advertised RFCOMM service, negotiated generation/identity/capabilities and device RET/NOTIFY; no reused Apple commands or fake Adaptive.

Tradeoffs/assumptions: publicly observed Apple case layout is not yet confirmed on the user's AirPods5; AES block has no authenticated-integrity claim. Sony V1/V2 and model variants are experimental until exact hardware/firmware captures qualify them. Beats and wired EarPods are not implemented. A catalog/model name alone is not evidence of supported operations.

Kill criteria: guessed identity/model generation, cached identity authorizing writes, guessed percent, optimistic mode confirmation, replay BLE refreshing freshness, universal tested claims, Sony automatic Adaptive conflated with manual Apple mode4, unknown protocol layout, unrelated setting changes, or broader privileges required. Retain available truthful charge and actionable errors. Review metadata:quick,3seats,2rounds,live3,degraded0,offline0,provider1,fallbacksnone; duration/tokens unknown.
