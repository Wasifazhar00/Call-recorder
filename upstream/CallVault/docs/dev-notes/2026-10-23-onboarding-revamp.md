# Onboarding redesign (`feat/onboarding-revamp`)

🧪 VERIFYING — the maintainer approved the look from the before/after page on 2026-10-24. Claude then ran
standalone and Shizuku setup on the OP9 over adb (below). ✅ VERIFIED 2026-10-25: the maintainer made a real
call to the OP9 after setup (Shizuku mode, auto-record on) and reported it recorded properly. Still unconfirmed:
the "Developer options off" line (📐 unseeable over adb, see below) and the Shizuku row in Settings.
## What changed

Same idea as the new Settings layout: one line per option, a control, and an ⓘ for the explanation.

- **Permissions screen.** Each permission is one row: icon, label, pill (Granted / Required), ⓘ. The old
  descriptions sit behind the ⓘ. The hero is a title, a ⓘ beside it and one line. The Wireless-debugging
  card is a title row with a Connected / Not connected pill and a ⓘ (how-to for the current state).
  The Shizuku offer card is a title and a ⓘ; buttons are unchanged.
- **Status lines that need action stay visible** and say what to do: "Developer options off — tap Build
  number 7×", "Wireless debugging is off", "Tap Pair, then enter the pairing code". The longer how-to is
  behind the ⓘ. "Required", the Shizuku "Nothing records until you do" and "setup is done" lines also stay inline.
- **Wizard steps (7–9 steps).** Each step has a header title + ⓘ + one line. Options are one line with a
  control and a ⓘ. Cards were rebuilt as `OptionCard(title, explanation, selected, onClick)`.
- **Models step.** Download **or Import** for both models (transcription and summaries), the #58 import.
  The language note shows only when the stored language really is the phone default.
- **Untouched:** `DisclaimerScreen`, permission requests, pairing, the bottom button, Shizuku logic.
- **Code.** `WizardScreen.kt` is split one file per step (`ui/screens/wizard/`). Moved private
  composables became `internal`. `PermissionsScreen.kt` is 717 lines (cap 800).
- **Guard test `OnboardingInfoModeTest`** (4 tests). It scans the wizard and permissions sources and fails
  if an explanation string is passed inline instead of through ⓘ, if a ⓘ has an empty explanation, or if one
  of the listed status keys is no longer shown as plain `Text` / pill / `DevModeStatusLine`. It
  **cannot catch**: nested parentheses or variables in an empty explanation, a status key that is inline
  but unreachable, or a string returned from a function (`stepInfo()`); `wizard_storage_subtitle` is
  allow-listed for that reason. It cannot judge layout.
- **Strings.** New: `permissions_ui_hero_line`, `permission_status_devmode_off_tap_build`,
  `permission_status_wd_on_enter_code` (15 locales). Deleted: `permission_devmode_on`,
  `permission_devmode_off`, `permission_wd_on`.

## Emulator run, 2026-10-23

AVD `mymemory-test`, **Shizuku mode**, debug build.

- **English:** permissions screen and wizard rows read as one line each; models step shows Download and
  Import for both models; the ADB ⓘ dialog shows the right how-to.
- **Arabic (RTL):** layout mirrors correctly (ⓘ on the left, dropdown ⓘ, models step).
- **Russian at 1.3× font:** first run found broken labels: permission labels split mid-word
  ("Уведомлени/я"), the ADB title clipped ("Wireless debuggi"), the Shizuku buttons split mid-word.
  Fixed in `09f6b7d8` (label + pill now wrap as a unit, Shizuku buttons in a `FlowRow`) and **re-checked**:
  labels whole, pills drop under the label, ADB title full, buttons wrap whole, Arabic still mirrors.
- **NOT seen:** the **standalone (paired) flow** and the **Reliability step** (USB "Charging only" row).
  Pairing on the emulator breaks the emulator's adb, so it was not tried. Their look is unchecked.

## Decisions taken on the maintainer's behalf

- Moved private composables became `internal` so they compile across files. Cost: wider visibility.
- The guard test's first version ignored each test separately, so the models-step test ran at once; the
  ignores were removed once the permissions screen was done.
- Permissions screen stayed in one file (no extra file needed).
- Emulator "before" screenshots came from a build of `cf3a0d70`.
- The final review's fix wave covered: clipped labels, actionable status lines, unused imports, the AAC
  tip (no ⓘ when AAC is already chosen), Shizuku buttons wrapping, the hero line wording.
- The Shizuku card gap and the storage-folder row's double padding were left as cosmetic.
- The "Pair after Shizuku ready" button problem (below) was not fixed here: one concern per branch.
- The merge into `integration/next` happens after the final review.

## Accepted trade-offs

- The conditional Drive-sync and immediate-upload notes are now always in the header ⓘ, not shown only when relevant.
- The Shizuku offer card has a small empty gap between title and buttons (the body moved into the ⓘ). Seen, cosmetic.
- The storage folder row has extra padding inside its card (seen on the emulator, tolerable).
- When connected, the ADB card shows only the Connected pill, not "Developer Options: on / Wireless debugging: on".
- Permission rows are separate cards with their own ⓘ, not one grouped card as in Settings.
- Persian wording of "Pair" in the new status key may not match its `permission_adb_pair` exactly. 📐 CALCULATED (read from code).

## OP9 run, 2026-10-24 (release-signed `2.4.7-rc1-onb`, versionCode 20462, `fix/onboarding-leftovers`)

Driven by Claude over USB adb on the rooted OP9 (Android 14), after `pm clear`. Seen, not yet confirmed by
the maintainer:

- **Standalone, end to end:** disclaimer → Use CallVault → notifications → "Wireless debugging is off" line
  (matched the phone) → Pair opened Developer options at the switch → notification went Waiting → Searching
  → Pairing service found → code typed in the notification → "Paired ✓" → card Connected, button "Grant
  Access" → contacts, phone, call log → 8-step wizard → home "Ready to record · Active – CallVault".
- **Wireless debugging on, not paired:** line reads "Tap Pair, then enter the pairing code".
- **Reliability step** (step 3 of 8, standalone only): "Keep recording when the screen locks — USB is set to
  Charging only". Correct: the phone's USB config was `adb` only.
- **Shizuku path:** after "Ready — using Shizuku" the button now reads "Grant Access" (was "Pair"). After a
  failed switch ("Not ready yet") it also reads "Grant Access". Allowing CallVault in Shizuku now flips the
  card to "setup is done" at once; revoking it in Shizuku's app shows on return. Home: "Active · Shizuku".
- Not seen: the "Developer options off" line; the Settings ▸ Shizuku "Allow" row (same listener, untested).
- Noticed, left alone: the Shizuku question comes back after leaving the screen (the answer is remembered
  only while the screen is open); the bottom button reads "Authorize" while it is really asking for
  notifications.

The OP9 is left in Shizuku mode, paired with CallVault's own key too.

## Found during the redesign — fixed 2026-10-24 on `fix/onboarding-leftovers`

All four below are fixed (🧪). The first two were seen fixed on the OP9; the hint and Russian strings are
checked by text only (`AdbHintNamesButtonTest` guards the hints). Also fixed: the welcome screen had the same
status-bar overlap, and a failed Shizuku switch left the button on "Pair".

- **After "Use Shizuku" → "Ready — using Shizuku", the permissions screen says setup is done but the
  bottom button still reads "Pair" and opens Developer options.** Back goes to wizard step 1.
  Reproduced on the pre-redesign build `cf3a0d70`, so it predates this work.
- The "SETUP" eyebrow overlaps the status bar on the permissions screen (also in the "before" screenshots).
- Russian "входящий вызовов" is ungrammatical.
- `permission_adb_hint_wd_on` (and `permission_adb_hint`) still say "Tap **Authorize**" while the button
  says "**Pair**". The fix wave did not change this (checked in the current strings). The new status line
  correctly says "Pair".
- Also noted: English hint copy such as the old hero subtitle is unchanged behind the ⓘ.

## Maintainer's check

Updated 2026-10-24. The look is approved (before/after page). Left for the maintainer:

1. One real call on the OP9 after setup, to confirm it records. (2026-10-24: the first test call was not
   recorded because auto-record incoming/outgoing defaults to off and the wizard was clicked through. Both
   were then switched on by hand on the OP9.) ✅ VERIFIED 2026-10-25: the maintainer's next call to the OP9
   was recorded properly.
2. Auto-record default: decided 2026-10-25 — leave as is (incoming/outgoing stay off by default, no wizard
   warning added).

📐 Developer-options-off line: not seeable by Claude on any device. With Developer options off on the emulator
(2026-10-24) the card said "Wireless debugging is off", by design: `DeveloperOptionsPolicy` counts USB debugging
or a running adbd as "on" (issue #40: the setting itself can be hidden), and any device Claude watches over adb
has both. The OFF rule is unit-tested (`DeveloperOptionsPolicyTest`) and `devOptionsProvenOff` shows
`permission_status_devmode_off_tap_build`. Only a phone with all debugging off, set up without Shizuku, shows it.

Tell me what you saw and I will update this note.
