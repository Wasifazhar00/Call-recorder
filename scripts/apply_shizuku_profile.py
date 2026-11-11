#!/usr/bin/env python3
"""Apply reproducible Shizuku-first defaults and test fixtures to the exact pinned CallVault source.

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
TEST = ENGINE / "app/src/test/java/com/baba/callvault/data/health/SetupPrerequisitesTest.kt"
VOIP_GATE = ENGINE / "app/src/main/java/com/baba/callvault/services/recording/VoipTelephonyGate.kt"

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

# Lint cannot infer that Kotlin's runCatching handles SecurityException. The guarded
# Telecom call intentionally fails open; annotate that exact API boundary with its rationale.
gate_source = VOIP_GATE.read_text(encoding="utf-8")
gate_import = "import android.content.Context\n"
gate_imported = "import android.annotation.SuppressLint\n" + gate_import
if "import android.annotation.SuppressLint\n" not in gate_source:
    if gate_source.count(gate_import) != 1:
        sys.exit("VoipTelephonyGate import anchor changed; refusing unsafe patch")
    gate_source = gate_source.replace(gate_import, gate_imported, 1)
old_gate = "    fun managedCallInProgress(context: Context): Boolean = runCatching {"
new_gate = """    @SuppressLint("MissingPermission") // runCatching below explicitly handles a revoked READ_PHONE_STATE grant.
    fun managedCallInProgress(context: Context): Boolean = runCatching {"""
if new_gate not in gate_source:
    if gate_source.count(old_gate) != 1:
        sys.exit("VoipTelephonyGate permission-guard anchor changed; refusing unsafe patch")
    gate_source = gate_source.replace(old_gate, new_gate, 1)
VOIP_GATE.write_text(gate_source, encoding="utf-8")

# Carrier recording must remain globally enabled; the two direction switches control automation.
for required in (
    "const val CARRIER_RECORDING_ENABLED = true",
    "const val AUTO_RECORD_INCOMING = true",
    "const val AUTO_RECORD_OUTGOING = true",
    "val PRIVILEGED_MODE: String = PrivilegedMode.SHIZUKU.key",
):
    if source.count(required) != 1:
        sys.exit(f"Shizuku-first profile contract not satisfied: {required!r}")

# These tests exercise the built-in ADB prerequisite path. Make that fixture explicit:
# the production build's Shizuku default must not silently change what the tests are testing.
test_source = TEST.read_text(encoding="utf-8")
old_import = "import com.baba.callvault.data.AppPreferences\n"
new_import = old_import + "import com.baba.callvault.data.PrivilegedMode\n"
old_fixture = "    private val preferences = AppPreferences(context)\n"
new_fixture = old_fixture + """
    @Before
    fun selectStandaloneModeForAdbPrerequisiteTests() {
        preferences.setPrivilegedMode(PrivilegedMode.STANDALONE)
    }
"""
if new_import not in test_source:
    if test_source.count(old_import) != 1:
        sys.exit("SetupPrerequisitesTest import anchor changed; refusing unsafe patch")
    test_source = test_source.replace(old_import, new_import, 1)
if "selectStandaloneModeForAdbPrerequisiteTests" not in test_source:
    if test_source.count(old_fixture) != 1:
        sys.exit("SetupPrerequisitesTest fixture anchor changed; refusing unsafe patch")
    test_source = test_source.replace(old_fixture, new_fixture, 1)
if "import org.junit.Before" not in test_source:
    anchor = "import org.junit.After\n"
    if test_source.count(anchor) != 1:
        sys.exit("SetupPrerequisitesTest JUnit import anchor changed; refusing unsafe patch")
    test_source = test_source.replace(anchor, anchor + "import org.junit.Before\n", 1)
TEST.write_text(test_source, encoding="utf-8")

print(
    "Verified Shizuku-first defaults (Shizuku backend + automatic incoming/outgoing recording); "
    "ADB prerequisite tests now explicitly select standalone mode."
)
