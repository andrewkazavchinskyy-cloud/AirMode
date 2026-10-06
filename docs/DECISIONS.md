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
