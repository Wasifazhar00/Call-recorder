# Release notes draft: next version (after 2.4.6)

🧪 VERIFYING — draft, 2026-10-20. Scope: `v2.4.6..integration/next` (now through fcb9552e, 2026-10-21). Full detail and status per line
in `CHANGELOG.md` → Unreleased. Re-check every line against the shipped strings before the release.

- **App calls in Shizuku mode** — app calls such as WhatsApp now record in Shizuku mode too (Android 14+).
- **Shizuku mode catches up** — resilient recording and speaker names work there too, and phone calls start recording faster.
- **Persian transcription** — Persian can be pinned as a transcription language.
- **Ukrainian** — the app and transcription now speak Ukrainian (translation by @kabasik007).
- **Hide Recordings** — keeps your recordings out of music players and galleries.
- **Also skip ignored contacts** — your ignored-contacts list can apply to app calls too.
- **Honor / Huawei phone app** — a new file-name format so Honor's phone app can play recordings from the call history.
- **New Settings layout** — each page shows only its own settings, grouped by what you want to do, with explanations behind ⓘ. A card at the top switches back to the classic view.
- **Approve new Wi-Fi networks automatically** — opt-in: CallVault answers Android's "Allow wireless debugging on this network?" for you, so recording doesn't wait for a tap.
- **Fixed** — Settings updates straight away when you switch modes.
- **Fixed** — the Telegram pill no longer pushes the settings icon off screen.
- **Fixed** — messages no longer point to "Settings ▸ Experimental".
- **Fixed** — moving between Wi-Fi networks no longer counts as you switching Wireless debugging off.
- **Fixed** — contact- and number-first file names show their arrow, merge, and are found by search again.
- **Fixed** — Shizuku mode: the recorder comes back after Android kills it.
- **Fixed** — Greek and Persian summaries are written in the right language.
- **Fixed** — a recording whose number arrived late kept the call's end time instead of its start.
- **Fixed** — contact names no longer appear in shared debug reports.
- **Fixed** — with Drive-only storage, a phone copy that could not be deleted no longer vanishes from the list (thanks @kabasik007).
- **Security** — other apps can no longer take over the recorder (thanks @kabasik007).

## Pre-release regression run (maintainer's OP12)

🧪 Installed 2026-10-21 16:43 over the public 2.4.6 (built from integration/next 80a30467, same signing key,
built-in mode). Without opening the app: keep-alive foreground again, one recorder daemon on the new APK,
`armVoipCapture -> true`. Waiting on the maintainer's day-to-day use: phone calls in and out, an app call,
the new Settings layout, notifications. Release only after that comes back clean.

## To do at release

- Tell the user who asked for automatic approval of the "Allow wireless debugging on this network?" prompt
  that it is in this release: Settings ▸ General ▸ Experimental ▸ "Approve new Wi-Fi networks automatically",
  built-in mode, needs USB debugging on so the recorder survives the Wi-Fi change.

- Tell the #56 reporter to try the release: pick "Honor / Huawei phone app" under the file-name format, set the
  recording folder to `Sounds/CallRecord` and the audio codec to AAC, then check the play button on a saved
  contact, an unsaved number, and a call that rang longer than it lasted.
