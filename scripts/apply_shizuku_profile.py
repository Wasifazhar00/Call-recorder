#!/usr/bin/env python3
"""Apply reproducible Shizuku-first defaults to the exact pinned CallVault source.

Safe to run repeatedly: already-applied values are accepted, while unexpected source drift fails closed.
The checked-out submodule is modified only during the build; upstream Git history is untouched.
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
    old_count = source.count(old)
    new_count = source.count(new)
    if old_count + new_count != 1:
        sys.exit(
            f"Expected exactly one original-or-profile value for {old!r}; "
            f"found original={old_count}, profile={new_count}"
        )
    if old_count == 1:
        source = source.replace(old, new, 1)

TARGET.write_text(source, encoding="utf-8")
print("Verified Shizuku-first defaults: Shizuku backend, automatic incoming and outgoing recording.")
