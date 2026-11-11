#!/usr/bin/env python3
"""Apply the repository's reproducible Shizuku-first product defaults to the pinned CallVault source.

This deliberately edits the checked-out submodule only during a build; upstream history and
copyright notices remain untouched. Fail closed if the pinned source or expected defaults drift.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "vendor" / "CallVault"
PIN = "00a77594085d61aee2c8513c649911704fd970c6"
TARGET = ENGINE / "app/src/main/java/com/baba/callvault/data/AppPreferences.kt"

actual = subprocess.check_output(
    ["git", "-C", str(ENGINE), "rev-parse", "HEAD"], text=True
).strip()
if actual != PIN:
    sys.exit(f"Refusing to patch unexpected CallVault revision: {actual} (expected {PIN})")

source = TARGET.read_text(encoding="utf-8")
replacements = {
    "val PRIVILEGED_MODE: String = PrivilegedMode.STANDALONE.key":
        "val PRIVILEGED_MODE: String = PrivilegedMode.SHIZUKU.key",
    "const val AUTO_RECORD_INCOMING = false":
        "const val AUTO_RECORD_INCOMING = true",
    "const val AUTO_RECORD_OUTGOING = false":
        "const val AUTO_RECORD_OUTGOING = true",
}
for old, new in replacements.items():
    count = source.count(old)
    if count != 1:
        sys.exit(f"Expected exactly one occurrence of {old!r}; found {count}")
    source = source.replace(old, new, 1)

TARGET.write_text(source, encoding="utf-8")
print("Applied Shizuku-first defaults: Shizuku backend, automatic incoming and outgoing recording.")
