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
BYPASSED_AUDIO = ENGINE / "app/src/main/java/com/baba/callvault/server/BypassedAudioRecord.kt"
MODEL_WORKER = ENGINE / "app/src/main/java/com/baba/callvault/transcription/model/ModelDownloadWorker.kt"

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

# AttributionSource.myAttributionSource() was added in API 33, while this app
# supports API 30+. Use the public Builder on Android 12/12L and the framework
# helper on API 33+; API 30 keeps the legacy native_setup path above.
audio_source = BYPASSED_AUDIO.read_text(encoding="utf-8")
old_attribution = "        var source = AttributionSource.myAttributionSource()"
new_attribution = """        val callingUid = Binder.getCallingUid()
        var source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AttributionSource.myAttributionSource()
        } else {
            AttributionSource.Builder(callingUid).setPackageName("uid:$callingUid").build()
        }"""
if new_attribution not in audio_source:
    if audio_source.count(old_attribution) != 1:
        sys.exit("BypassedAudioRecord attribution-source anchor changed; refusing unsafe patch")
    audio_source = audio_source.replace(old_attribution, new_attribution, 1)
BYPASSED_AUDIO.write_text(audio_source, encoding="utf-8")

# WorkInfo.getStopReason() was added in API 31. The app supports API 30,
# where WorkManager still exposes isStopped but not a stop reason.
worker_source = MODEL_WORKER.read_text(encoding="utf-8")
worker_import = "import android.content.Context\\n"
worker_imported = "import android.content.Context\\nimport android.os.Build\\n"
if "import android.os.Build\\n" not in worker_source:
    if worker_source.count(worker_import) != 1:
        sys.exit("ModelDownloadWorker Build import anchor changed; refusing unsafe patch")
    worker_source = worker_source.replace(worker_import, worker_imported, 1)
old_stop_reason = """    private fun stopReasonName(): String = when (stopReason) {
"""
new_stop_reason = """    private fun stopReasonName(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return "stop reason unavailable before Android 12"
        }
        return when (stopReason) {
"""
if new_stop_reason not in worker_source:
    if worker_source.count(old_stop_reason) != 1:
        sys.exit("ModelDownloadWorker stop-reason API anchor changed; refusing unsafe patch")
    worker_source = worker_source.replace(old_stop_reason, new_stop_reason, 1)
    closing = """        else -> "reason $stopReason"
    }

    companion object {
"""
    replacement = """        else -> "reason $stopReason"
        }
    }

    companion object {
"""
    if worker_source.count(closing) != 1:
        sys.exit("ModelDownloadWorker stop-reason closing anchor changed; refusing unsafe patch")
    worker_source = worker_source.replace(closing, replacement, 1)
MODEL_WORKER.write_text(worker_source, encoding="utf-8")

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
# Keep alternate live transports disabled so Robolectric state from another test cannot
# change the missing-grant assertion.
old_satisfy = """    private fun satisfyAllPrerequisites() {
        preferences.setRecordingFolderUri("content://tree/a".toUri())
"""
new_satisfy = """    private fun satisfyAllPrerequisites() {
        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0)
        preferences.setOfflineRecordingEnabled(false)
        preferences.setRecordingFolderUri("content://tree/a".toUri())
"""
if new_satisfy not in test_source:
    if test_source.count(old_satisfy) != 1:
        sys.exit("SetupPrerequisitesTest prerequisite fixture anchor changed; refusing unsafe patch")
    test_source = test_source.replace(old_satisfy, new_satisfy, 1)

old_import = "import com.baba.callvault.data.AppPreferences\n"
new_import = old_import + "import com.baba.callvault.data.PrivilegedMode\n"
old_fixture = "    private val preferences = AppPreferences(context)\n"
new_fixture = old_fixture + """
    @Before
    fun selectStandaloneModeForAdbPrerequisiteTests() {
        preferences.setPrivilegedMode(PrivilegedMode.STANDALONE)
        // These tests exercise the missing-grant branch, not the alternate live transports.
        preferences.setOfflineRecordingEnabled(false)
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
