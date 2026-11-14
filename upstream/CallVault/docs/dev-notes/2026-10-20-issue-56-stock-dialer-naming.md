# Issue #56: filename template for the Honor/Huawei stock dialer (2026-10-20)

📐 CALCULATED — from reading our code and web research. Nothing tested on an Honor/Huawei phone.

## The request

Let the user write a custom filename template so Honor's stock dialer shows CallVault recordings in its
call log. The reporter says Honor expects `{caller_name}@{phone_number}_{yyyyMMddHHmmss}`, e.g.
`JohnDoe@+1234567890_20261019215600.m4a`.

## What we have today

- 8 fixed presets (`FileNameTemplates.kt`), a dropdown only, no free text.
- `{date}` is always `yyyyMMdd_HHmmss.SSSZ`. Honor's compact `yyyyMMddHHmmss` cannot be produced.
- VoIP names are not templated (`{date}_voip-App_caller`, `VoipRecordingCoordinator`).
- The app reads the call back from the filename: `RecordingsRepository.parseName` (`DATE_TOKEN`
  needs `\d{8}_\d{6}`), `LogRedaction.RECORDING_NAME_REGEX`, `MergeService.bumpMillis`, `ImportedRecording`.
  A new date shape must be taught to all of them or the library loses dates, call-log matching and merging.

## Research (REPORTED, not confirmed)

- Honor's own recorder: `Sounds/CallRecord/name@number_yyyyMMddHHmmss.m4a`, number formatted as written
  (`+6012-…`). Older EMUI: `/sdcard/record/…amr`.
- MagicOS 10.0.0.135 (China, 2026-03) changed Honor's own naming to `通话-<name>-<datetime>`, no number.
- **No source shows the stock dialer listing a file another app wrote.** Whether it matches by filename,
  MediaStore or its own DB is unknown. No OEM checked (Xiaomi, Samsung, vivo, Google) documents this either.
- BCR (GPL-3.0) has a full template language: `{date:<pattern>}`, `{phone_number:E.164|…}`, `[a|b|]`
  fallbacks, `/` subfolders. Proven prior art if we build a free-text template.

Full report: scratchpad `research-issue56-stock-dialer-naming.md` (session 36e66fb6).

## Separate bug found: contact names leak into debug logs (📐, regex simulated, not seen in a real log)

`RECORDING_NAME_REGEX` only matches names that START with the date. With the contact-first presets added in
2.4.5 (`{contact_name}_{phone_number}_{date}`, `{contact_name}_{date}`, …) the name before the date is not
pseudonymised: `John Doe_+15551234_20260611_143052.000+0300.ogg` → the number is redacted, "John Doe" stays.
Shipped since 2.4.5.

🧪 VERIFYING — fixed on `fix/redact-contact-first-names` (702238cc + 89f7d1a7), merged into integration/next;
unit tests pass (9 new cases in `LogRedactionTest`). To settle: on a phone, pick the "Contact · Number · Date"
preset, record a call with a saved contact, share a debug report, and search it for the contact's name — it
should appear only as `[C:xxxx]`.

## Before building anything

Ask the reporter: MagicOS version; a real filename their phone wrote (saved and unsaved contact); then
rename one CallVault `.m4a` to that pattern by hand, put it in `Sounds/CallRecord`, and check whether it
appears in that call's log entry. If yes, the feature is worth building (`.m4a` output, Honor preset or
free-text template). If no, no naming helps.

## Update 2026-10-20: reporter confirmed, preset built — 🧪 VERIFYING

- ✅ Reporter (Honor REA-NX9) renamed a CallVault `.m4a` to `Баланс@087011_20261020103708.m4a`, put it in
  `/storage/emulated/0/Sounds/CallRecord`, and the stock dialer showed a play button on that call (screenshot on #56).
  That is the reporter's confirmation, not ours.
- Built on `feat/honor-dialer-naming-56`, merged into integration/next (2811d70f):
  - Preset "Honor / Huawei phone app" = `{contact_name}@{phone_number}_{date_compact}`. Number = raw number
    as dialled (digits + leading '+', placeholders rejected); unsaved caller → number repeated as name.
  - Picking it shows the full path and "Storage ▸ Recording Folder" / "Audio configuration ▸ Audio codec"
    (built from the on-screen titles), plus what is still wrong (folder, codec, Drive-only storage).
    Seen rendering on the OP9 2026-10-20.
  - Parser, log redaction (incl. `%40` in SAF URIs) and merge naming learned the shape.
  - Fixed on the way: the call-log rename re-stamped the file with the call's END time (all presets).
- Still unknown, for the reporter's test build: unsaved-number calls, `.ogg`, number format on other
  carriers, how far the stamp may be from the call-log time (ours = when recording starts).

## Update 2026-10-20: Honor's code read — 📐 CALCULATED (decompiled stock APKs, nothing run)

Full report: scratchpad `research-issue56-extensions.md` (HnContacts + Honor Recorder from MagicOS 6, 8 (REA), 10).

- **Dialer (HnContacts `CallRecord.setup()`):** lists `<volume>/Sounds/CallRecord` with `File.list()`; no extension
  or content check. Name: `_` stripped before the last `@`, then exactly two `_` parts; number after the last `@`
  (≥3 chars, digits/`+`), compared with `PhoneNumberUtils.compare` (loose); date `yyyyMMddHHmmss` local.
  **Window: stamp must be within [call-log DATE − 1 s, DATE + duration + 1 s].** The name is used as a regex.
- **Playback goes to Honor Recorder (`com.hihonor.soundrecorder`)**, whose list accepts only amr/3gpp/wav/m4a.
  MagicOS 10 plays the path directly (content-sniffed, so .ogg would play). MagicOS 6/8 without
  `com.hihonor.phone.recorder` look the path up in that list and fall back to item 0 → a `.ogg` may play nothing
  or **another call's recording**. ⇒ **Keep the AAC (.m4a) requirement.**
- Gaps in what we built: (1) contact names with `_` or regex characters (`( [ + * ?` …) can break matching;
  (2) we stamp when recording starts; for an incoming call DATE is likely ring start, so a call that rang longer
  than it lasted falls outside the window. Fix candidates: sanitise the name; after the call, rename the stamp
  to the call-log DATE.

## Update 2026-10-20: both gaps fixed — 🧪 VERIFYING (unit tests only)

- 233d5780: name made safe for HnContacts — `_` → space, regex characters dropped; an unsaved caller's number is
  repeated as the name **without its `+`** (`380…@+380…_…`), because `+380…@` would throw as a regex.
- e7991a46: after the call, the Honor-preset file is renamed to the call-log DATE of the matching entry
  (same direction, `PhoneNumberUtils.compare`, began ≤ 3 min before our start; 8 × 500 ms retries while the
  log entry is written). Not applied to the resilient-handoff rescue path (CutOffRescueWorker) — that file keeps
  its own start time.
- Merged into integration/next (e9644840), installed on the OP9. To settle on any phone: pick the Honor preset,
  let an incoming call ring ~15 s, talk ~5 s, hang up; the file's time should equal the call-log time
  (debug log line "Renamed to the call-log time for the Honor phone app"). Only an Honor/Huawei phone can show the
  play button itself.

## Update 2026-10-20: call-log rename seen on the OP9 — 🧪 VERIFYING (read by Claude over adb)

Two incoming test calls on the OP9 (integration/next e9644840, Honor preset, codec still Opus):
- Recording started 16:27:02 (answer); the call-log entry's DATE is 16:26:38 (ring start), duration 6 s.
  The file was published as `…_20261020162702.ogg`, then renamed to `פרוזה@0507103474_20261020162638.ogg`,
  exactly the call-log time. Log line: "Renamed to the call-log time for the Honor phone app".
- The earlier call (16:26:25, 3 s) got `…_20261020162625.ogg`, also equal to its call-log time.
- Without the rename the stamp (16:27:02) would be outside Honor's window [DATE − 1 s, DATE + 6 + 1 s] = 16:26:37–16:26:45.
Still open: the play button itself (needs an Honor phone), AAC on a real call, the reporter's test build.
