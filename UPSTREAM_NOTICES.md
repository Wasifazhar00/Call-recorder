# Upstream provenance and license notes

The planned call-recording engine is [CallVault](https://github.com/madkongo/CallVault), a modified fork of [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder).

Pinned upstream reference reviewed: CallVault commit `00a77594085d61aee2c8513c649911704fd970c6` (release 2.5.0).

CallVault identifies its upstream as GPL-3.0-or-later with additional Section 7 terms. Its own `NOTICE.md` attributes the original ShizuCallRecorder work to kitsumed (Med), states that CallVault is a modified version, and lists third-party assets/model weights. The complete upstream LICENSE and NOTICE must be carried forward unchanged when source is imported.

## Requirements for any source import

- Preserve original copyright headers and add clear notices for local modifications.
- Include the full applicable GPL license text and upstream NOTICE.
- Make the corresponding source available when distributing binaries, as required by the applicable license.
- Audit every bundled binary, model, native library and downloaded dependency; do not assume the main project license covers them.
- Do not remove upstream attribution or imply endorsement by upstream authors.
- Mark the result as a modified independent version.
- Re-check the current upstream license and notices at the exact commit being imported.

This file records provenance only; it is not a substitute for the upstream license texts.
