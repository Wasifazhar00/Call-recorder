# Fork review: kabasik007/CallVault `security/phase1-fork-hardening`

📐 CALCULATED — read the code, nothing built or tested

Fork: 12 commits by Vas Vakhulenko, 2026-10-14/15, based on our 2.4.6-era `main`.
Compared against our `integration/next` at `de3fe50a`. Fork code was read through the GitHub
compare API only. Nothing from it was built or run.

## Verdict

| Commit | What | Real for us? | Recommendation |
|---|---|---|---|
| 7211c263 (IPC part) | Only shell, system or our own uid may call `sendBinder` / `sendHandoff` | **Yes.** `sendBinder` has no caller check. `sendHandoff` already has one. | **Adopt now** (4 lines). Medium severity. |
| 7211c263 (updates part) | Updates switched off; release URLs point to the fork | No. Only a fork problem. Our updater checks the package and the signing cert. | Skip |
| 7211c263 (kill command) | Kill only daemons started from the current APK path | No. **Would break us**: after an update the APK path changes, so the old daemon would not be killed. | Skip |
| 7211c263 (authority) | `AUTHORITY = APPLICATION_ID + ".recorder"` | Not needed. The instrtest build never uses the provider. | Skip |
| 6b341fc1 | Keep the device copy in the catalog when SAF refuses the delete | **Yes**, a small bug | Adopt (2 call sites) |
| 92c51717, 193302b8, fa229fd1 | "Zilla Recorder DEV" side-by-side debug APK + fork CI | No. Fork branding and fork release flow. | Skip |
| e2af1d60 | Swap conscrypt-android for conscrypt-openjdk-uber in unit-test runtime | Not today. 2011 local tests pass on the Mac, 0 errors. Our CI runs no unit tests. | Keep in mind if we add unit tests to Linux CI |
| 02e0e691…c34d2176 | Ukrainian UI + Ukrainian transcription | New feature. Translation is fluent but **heavily shortened** and 11 strings behind. | Adapt: take as a base, fix the gaps, do the five language tables ourselves |
| c34d2176 (CI part) | Run `checkTranslations` in CI | Partly. It already gates `check` and `assembleRelease` locally. | Optional: add one CI step |

## 1. Recorder IPC — real, adopt now

**What is exposed.** `RecorderBinderProvider` is exported with no permission
(`AndroidManifest.xml:290-294`). It has to be: the shell-uid daemon reaches it as a non-app process.

**The hole.** `call()` sends `sendBinder` straight to `handleSendBinder`, with no caller check
(`RecorderBinderProvider.kt:55-58`). `handleSendBinder` wraps whatever binder arrives and calls
`RecorderConnection.onBinderReceived` (`RecorderBinderProvider.kt:115-116`). That replaces the live
recorder without any check (`RecorderConnection.kt:52-53`).
The other two entry points are already guarded: `sendHandoff` (`RecorderBinderProvider.kt:85-89`)
and the Track A probes (`RecorderBinderProvider.kt:62-66`). So this one is a gap, not a design choice.

**What another app can do.** It needs no permissions. It calls
`content://com.baba.callvault.recorder` with method `sendBinder`, passing its own `IRecorderService`
binder. From then on CallVault talks to that app instead of the daemon:
- `startRecording` / `startVoipRecording` hand it the writable output fd
  (`AudioRecordingEngine.kt:474`, `VoipRecordingCoordinator.kt:193`). It can write nothing (the call
  is lost) or write fake audio (a forged recording). `isRecording` can still say true.
- `onDaemonReady` re-arms VoIP capture on the fake binder, so the real daemon is never armed and
  app calls are lost.
- It learns when calls happen and which VoIP app is in use (`voipCallAnswered`, `voipCallerName`
  carry the package name).

**What it cannot do.** It cannot read past recordings this way. The fds it gets are for new, empty
files. It cannot get the real daemon's binder, and it gets no shell power.

**Fix.** Add the same uid check `sendHandoff` uses to `sendBinder`. The fork's version is fine
(`RecorderBinderCallerPolicy` + a JVM test). Simpler for us: one shared check at the top of `call()`
for all methods except unknown ones.
- Rejecting uid 0 is safe. Shizuku and Sui hand over the binder inside our process
  (`ShizukuBackend.kt:199`), not through this provider. Our own daemon runs as shell (2000).
  `sendHandoff` already rejects 0.
- Test on the emulator: a small second app calls `sendBinder` and must be refused, and a normal
  daemon start must still connect.

## 2. "Disable foreign APK updates" — a fork problem only

The fork turns updates off because its builds would otherwise pull **our** releases. For us there is
no hole. The updater refuses an APK whose package differs from ours (`ApkVerifier.kt:54`) or whose
signing cert is not our pinned one (`ApkVerifier.kt:79`), before installing (`UpdateManager.kt:115`).

The same commit changes `KILL_STALE_CMD` to kill only daemons whose command line holds the
**current** APK path. Do not take this. An install-over moves the APK to a new `/data/app/~~…` path,
and the daemon is started with that path (`RecorderServerLauncher.kt:58`). The old daemon would then
survive. That is exactly the post-update daemon race we fixed before. Our command kills every
`RecorderServer` (`RecorderServerLauncher.kt:80-81`), and that is right for a single install.

## 3. SAF deletion — real bug, low severity

`SafHelper.deleteDocument` already returns false when the provider refuses (`SafHelper.kt:402-410`).
Both callers ignore that result and tell the catalog the device copy is gone anyway:
- `RecordingCopyWorker.kt:85-92` passes `deleteLocal`, not "did it delete".
- `SyncSweepWorker.kt:130-133`, same.

`markDrive` then runs `clearLocal` (`RecordingCatalog.kt:87-89`). Result: the file is still on the
phone, but the app no longer knows about it. It takes space and is never cleaned up. Later sweeps
take the "already in Drive" branch with `deleteLocalAfter = false` (`SyncSweepWorker.kt:112-114`),
which does not restore the link. No recording is lost, because the Drive copy exists.

Fix: pass `deleteLocal && deleted` to `markDrive`. The fork's `CloudCopyPolicy.shouldClearLocalReference`
helper and test can be ported as they are.

## 4. Ukrainian

**Coverage against our current base strings (1039 translatable entries):**
- 1029 present. 11 missing, all added after 2.4.6: the Honor preset (4), `transcription_language_persian`,
  hide-recordings (3), VoIP ignore-contacts (3).
- Placeholders match. Plurals use one/few/many/other correctly. No Russian-only letters.
  7 strings left in English are brand names (Drive, Shizuku, MIDI), which is fine.

**Quality.** Short UI strings read naturally. Long strings are **shortened**, not translated.
Overall the Ukrainian is 0.63 of the English length. Our Russian is 1.07. 30 of 180 long strings are
under 55% of the English. Some drop facts the user needs:
- `storage_cap_caption` drops that Drive-backed and imported recordings are never deleted by the limit.
- `notif_readiness_needs_wifi_restart_text` drops the "turn on Offline recording" advice.
- `settings_record_from_answer_description` drops that app calls need a call timer.
- `wizard_audio_subtitle` drops the Opus 16 kbps recommendation.
- Most `whatsnew_*` bodies are cut to one or two sentences.

So: a good base, but the long strings need a full pass from the English before release.
Their own doc also asks for a native-speaker review (`docs/localization/UKRAINIAN.md` in the fork).

**Transcription side.** The fork touched three of our five tables (SUPPORTED, label, `LanguageScript`).
It misses `SummaryPrompt.LANGUAGE_NAMES` and `SummaryLanguage.scriptOf`. `scriptOf` maps all Cyrillic
to `ru` (`SummaryLanguage.kt:78`), so a Ukrainian summary would be judged Russian. It needs a uk/ru
split by letters only one uses (і ї є ґ vs ы э ъ ё), like the Persian/Arabic split at line 91.
Our guard tests require every UI locale to be pinnable, so `values-uk` cannot land without `"uk"`.

**Effort.** About half a day: copy the 7 files, add 11 strings, redo ~30 long strings, add the
`transcription_language_ukrainian` label to all 14 existing locales, do the five tables plus the uk/ru
split, then run `checkTranslations` and the language tests.

**License and credit.** The fork is GPL-3.0-or-later with our Section 7 terms. Its new files say so in
their headers. We can take the work. Keep Vas Vakhulenko as the git author (cherry-pick, or
`Co-authored-by`) and name them as the Ukrainian translator in the release notes and any credits list.
Keep our licence header and §7 attribution on the files.

## 5. Tests and CI

- **Conscrypt.** The fork says every Robolectric test fails on Linux CI with
  `UnsatisfiedLinkError: no conscrypt_jni`. Locally on the Mac, 2011 tests ran on 2026-10-20 with
  0 errors. Our `build-app.yml` only assembles and runs no unit tests. Take this only if we add unit
  tests to CI.
- **Locale CI.** `checkTranslations` already runs on `check` and `assembleRelease`
  (`gradle/translation-coverage.gradle.kts:141-142`), so a release cannot skip it. A CI step would
  only catch drift earlier. Optional.
