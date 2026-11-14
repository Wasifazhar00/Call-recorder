# Import and back up models (#58)

🧪 VERIFYING — built, unit tests pass, and the emulator run below passed. Nothing has been tried on a
phone yet. **To settle it, the maintainer needs to:**

1. On a phone, Settings ▸ Summaries ▸ **Import from a file** with a real Gemma `.gguf` (2.6 GB) you
   already have → the row should go *Importing… n%* → *Downloaded and ready*, and a summary should run.
2. On a phone, **Back up** a model you have to a folder → the file there should have the same sha256 as
   the catalogue (check with any hash app, or `adb shell sha256sum` on it).

## What it does

- Each model row (transcription and summaries, in Settings and in setup step 6) has **Import from a
  file** when the model is not installed. **Back up** shows when it is installed, in Settings only (setup
  hides it on purpose: a fresh install has nothing to save). 📐 That setup hides it is read from the code,
  not seen: no emulator step looked for it. (An earlier version said "seen so on the emulator", which no
  recorded step supports.)
- Import opens the system file picker, copies the file into `files/models/<name>.import` while
  hashing it, and installs it only if the sha256 matches the catalogue. The user's copy is left where it is.
- A file whose size belongs to a different catalogue model is hashed first: if it really is that model
  the row says *"That's the file for another model (<its name>). Choose that model first, then import."*; if the hash
  does not match, *"That isn't the right file. Pick <name>."*
- Back up writes the installed file to a folder the user picks (system "save" dialog).
- Cancel, a wrong file and a full disk all leave no `*.import` behind.

## Emulator run, 2026-10-23

AVD `mymemory-test` (Android 16, arm64), debug build of `feat/model-import-backup-58` at `69a02f64`.
The real Small model (`ggml-small-q5_1.bin`, 190,085,487 bytes) was downloaded on the Mac and checked:
sha256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` = catalogue.

| # | Step | Result | Evidence (`CV:ModelTransfer` log) |
|---|------|--------|-----------------------------------|
| 1 | Settings ▸ Small ▸ Import the real file | ✔ Importing… 33% → *Downloaded and ready* | `op=import model=small-q5_1 installed bytes=190085487 ms=3281`; installed file sha256 = catalogue |
| 2 | Import a 1,000,000-byte truncated copy | ✔ *"That isn't the right file. Pick ggml-small-q5_1.bin."* | `failed wrong-file bytes=0 ms=25` |
| 3 | Other model (variant, see below) | ✔ *"That's the file for another model…"* | `model=large-v3-turbo-q5_0 failed other-model (is small-q5_1) ms=2336` |
| 4 | Back up Small to Download | ✔ row says *Saved*; file has the same sha256 | `op=backup model=small-q5_1 written bytes=190085487 ms=763`; `ggml-small-q5_1 (1).bin` sha256 `ae85e4a9…11bb` |
| 5 | `pm clear`, setup again, import the backup in step 6 | ✔ *Downloaded and ready* in the wizard | `installed bytes=190085487 ms=2618`; sha256 = catalogue |
| 6 | Cancel an import part-way | ✔ no `*.import` left | before cancel `…q8_0.bin.import` 43,651,072 bytes; `stopped (cancelled) bytes=112263168 ms=1932`; `files/models` empty after |

Seen along the way, not in the brief:

- **Same size, wrong hash** (the emulator's old Best-quality fixture: right size, different content):
  imported as Best-quality-smaller → `failed wrong-file ms=11752` (hashed, then refused); imported as
  Best quality itself → copied in full, `failed wrong-file ms=8668`, and the `.import` was deleted.
- **Not enough space**: with 738 MB free, importing the 874 MB file → *"Not enough free space to import
  it."*, `failed insufficient-storage ms=88`, nothing written.

Not done on the emulator:

- **Step 3 as written** (a real `ggml-large-v3-turbo-q5_0.bin` with Small selected): no such file, and
  574 MB more does not fit the AVD. Ran the mirror case instead: Small's file with Best-quality-smaller
  selected. Same code path (`checkSize` → `OtherModel` → `hashOnly` confirms).
- **REPLACE** (picking Import again while one runs, which WorkManager reports as `CANCELLED_BY_APP`): not
  reachable from the UI. While an import runs the row shows only *Importing… n%* and *Cancel*, in Settings
  and in setup. **Not tested: no unit or emulator coverage.** (Corrected 2026-10-23: this line used to say
  "Covered by unit tests only", which was wrong — no unit test exercises a REPLACE.)
- Gemma (2.6 GB) import or backup: too big for the AVD. That is item 1 for the maintainer above.

Emulator notes for next time: the app kept asking *"Allow wireless debugging on this network?"*, and
allowing it took the emulator's adb offline for good (until a cold boot). Revoking
`WRITE_SECURE_SETTINGS` from the app on the emulator before it starts stops the prompt. After `pm clear`,
setup was finished in Shizuku mode (Shizuku started on the emulator with its `libshizuku.so`) to avoid
pairing. The AVD needed `-memory 4096`; at 2 GB, a 190 MB `adb push` crashed `system_server`.

**What the run cost the AVD** (step 5's `pm clear`): it wiped the README demo data staged on
`mymemory-test` — the 6 recording rows, their transcripts, a summary and a note. The audio files remain;
the database rows do not. It must be re-staged before the next README screenshot shoot (logged in
`readme-impact-log.md`). The AVD was left with `WRITE_SECURE_SETTINGS` revoked from the app, "always allow
on this network" ticked, and the app set up in Shizuku mode with the Fast model.

## Changed after the emulator run (final review, 2026-10-23)

🧪 VERIFYING — unit tests and a debug build only; none of this was on the emulator or a phone.

- **Import and backup run as a foreground job**, with the existing "Background processing" notification
  ("Importing a model" / "Backing up a model"). Without it WorkManager stops a background job after ten
  minutes, and a 2.6 GB import from a slow or cloud provider would be stopped, its partial copy deleted,
  and re-run from zero. The `specialUse` subtype text in the manifest now mentions model copies.
- **Import no longer waits for "storage not low".** That constraint fires at an OEM threshold above the
  worker's own free-space check, so a phone in between was stopped mid-copy and re-run forever.
- **The newer failure wins.** A download and an import each stamp the time they failed; the row shows the
  newer one instead of always the import's.
- **"That's the file for another model" names it**: the speech model's picker label (e.g. "Best quality"),
  or the summariser's file name.
- **A source longer than the model reads as "That isn't the right file"**, not "Couldn't read that file".
