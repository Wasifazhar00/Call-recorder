# Upstream review: ShizuCallRecorder since our fork (2026-10-22)

📐 CALCULATED — code read only (upstream cloned read-only into the session scratchpad, never built or run);
our code read on integration/next. Nothing adopted, nothing posted on GitHub.

We vendored kitsumed/ShizuCallRecorder on 2026-06-09 (base ≈ `95701fc`, between their v1.0.2 and v1.1.0) and
had never re-read their code since. Since then: 113 commits (+7 translation), releases v1.1.0 → v1.3.3, and
on 2026-10-20 a large audio rework (`d292812`, PR #132). Upstream is now on **F-Droid** (reproducible,
developer-signed: `Binaries:` + `AllowedAPKSigningKeys`) and **IzzyOnDroid**.

## Worth acting on

| # | Item | From | Size | Notes |
|---|---|---|---|---|
| 1 | **"Ignore these contacts" misses differently written numbers.** `ContactIgnoreRule.kt:65` compares `normalisePhoneNumber` output exactly; that only strips formatting, so a contact saved as `050-123 4567` never matches a caller reported as `+972501234567`. The record-only list already uses `sameNumber` (last 9 digits). | our own bug, found while comparing upstream `db5a01c` | S | Fails safe (records). Confirmed in code 2026-10-22; not tested on a phone. |
| 2 | **Contact names go into carrier file names uncleaned.** A `_in`/`_out`/`voip` inside a name can make `RecordingsRepository.parseName` (`:665`, `:704-714`) misread direction and date (breaks merge, call-log match, date shown). VoIP already has `sanitiseForFileName` (`VoipRecordingCoordinator.kt:869`); share it. `/` itself does not create folders here (provider replaces it). | upstream `9e14cb0` (#125) | S | Rare; from code reading. Only new names change. |
| 3 | **TalkBack: switch rows read as two items.** `ToggleListItem.kt:45-49`, `SettingsToggleRow` (36 uses), wizard toggle card. Plus selected/on-off state on multi-select ticks and the star chip; no `heading()` anywhere. | `641490f` (#89) | S–M | No visible change. |
| 4 | **Disclaimer and Permissions screens may sit under the status bar** (fixed 28dp top, `DisclaimerScreen.kt:106,114`, `PermissionsScreen.kt:276,288`). | `be033b7` (#90) | S | Unconfirmed; check on a phone. |
| 5 | **Notifications blocked in app settings go unnoticed** — `PermissionChecks.kt:31-38` checks only the POST_NOTIFICATIONS grant, not `areNotificationsEnabled()`. | `77f870d` part 2 | XS | |
| 6 | **libphonenumber 9.0.31 → 9.0.41** (`libs.versions.toml:25`). | `9ba5893`, `b039106` | XS | Fresh numbering-plan data. |
| 7 | **Foreign numbers without "+"** read as local (cross-country rule, file name). Port the `"+"+raw` retry **only if the result is valid** (upstream's merged code does not check). | `347ffca` (#84) | S | Carrier quirk; low impact. |
| 8 | **Build toward F-Droid / reproducible builds:** add `dependenciesInfo { includeInApk=false; includeInBundle=false }`; remove Foojay (`settings.gradle.kts:23`, `gradle-daemon-jvm.properties`); literal versionCode/Name; drop `CI_BUILD_NUMBER` from BuildConfig; CI as an unsigned verification build (`SKIP_SIGNING`), removing the `run_number` versionCode and the broken keystore step (CI fails "Tag number over 30" = bad secret). Fastlane metadata later. | `3c8efd4`, `f51ce84`, `53f771a`, `b371ddf`, `5a704f2` | M | F-Droid blockers in `fdroid-readiness` still stand (scrcpy download, updater, silero .bin). ⚠️ IzzyOnDroid vetted upstream for LLM-written code for a month; CallVault is openly AI-assisted — decide before applying there. |
| 9 | **Post-recording notification** (Open / Share / Delete). We only toast + vibrate. | `fd59112`, `e463f03`, `3adab09` | M | Visual mock first. Opt-in, quiet; Delete must use our full delete path (transcripts, notes, sidecars, Drive); new notification id; all three capture paths; onboarding decision. |
| 10 | **Recording overlay** = reference implementation for our backlog #1 "Record button overlay". Over-other-apps bubble, over lock screen, draggable, remembered position. | `dd93573` + 3 fixes | M–L | Visual mock first. |
| 11 | **Debug/log export reachable from onboarding** (a user stuck in setup cannot send a log today). | `be027e4` | S | Mock first. Unconfirmed there is no path. |
| 12 | **Android 11 start workaround** (bring `com.android.shell` to foreground, retry `startRecording`). We have nothing for API 30 in `server/`. | `d292812` | ? | First find out whether Android 11 falls back to scrcpy today (log or emulator). |

Ideas only: InCallService detection mode (`b4cf0af`; non-UI, no dialer role, appop `manage_ongoing_calls`) would give number at start, a real answer signal and the SIM — but ColorOS refuses shell appops and vivo/Xiaomi revert them; contact picker storing contact IDs (`d0903e1`, #54); HE-AAC v1 / VBR; VOICE_COMMUNICATION + `setPrivacySensitive(false)` as VoIP near source (A/B by ear on OP12 first); app user-profile id in log export (`63c004e`).

## Not taken

- **Dual UPLINK+DOWNLINK / MIC+DOWNLINK and single-side sources** — break both-sides-or-fail and the no-second-voice-capture rule. Upstream's own #130 reporter: "downlink records to both tracks… uplink is not recorded" — the same failure our OP12 A/B found.
- VAD ducking/AGC on the delivered file (would distort our speaker turns); their VoIP far-side settings (we are ahead: 48 kHz, registered before the call).
- Already have (often stronger): direct AudioRecord, vendor-constructor bypass, shell attribution, silence detection, grant read-back, empty-number crash (#98), Android 13 typed parcelable fix, late-number fix, log redaction.
- Not applicable: SAF subfolders, date sub-placeholders (#40, our Honor preset covers it), AppLogger R8 crash (we use explicit tags), role-name fix, sponsor UI, polish of screens we replaced, deleting the Gradle wrapper.
