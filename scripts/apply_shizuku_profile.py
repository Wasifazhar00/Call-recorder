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
worker_import = "import android.content.Context" + chr(10)
worker_imported = worker_import + "import android.os.Build" + chr(10)
if "import android.os.Build" + chr(10) not in worker_source:
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



# Release-lint remediation for the pinned upstream revision. These edits are applied
# deterministically to the submodule at build time; every anchor is checked so upstream
# drift fails closed rather than silently producing a partial profile.
def patch_file(relative_path, edits):
    path = ENGINE / relative_path
    text = path.read_text(encoding="utf-8")
    for old, new, description in edits:
        if new in text:
            continue
        count = text.count(old)
        if count < 1:
            sys.exit(f"{relative_path}: missing anchor for {description}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

# API 31 WorkManager stopReason access: API 30 has no stop reason. On API 30 we
# conservatively retain URI grants because we cannot distinguish retry from cancel.
transfer = ENGINE / "app/src/main/java/com/baba/callvault/transcription/model/ModelTransferWorker.kt"
transfer_source = transfer.read_text(encoding="utf-8")
if "import android.os.Build\n" not in transfer_source:
    anchor = "import android.net.Uri\n"
    if transfer_source.count(anchor) != 1:
        sys.exit("ModelTransferWorker Build import anchor changed")
    transfer_source = transfer_source.replace(anchor, anchor + "import android.os.Build\n", 1)
transfer_edits = [
("""    private fun willRunAgain(): Boolean = isStopped && stopReason != WorkInfo.STOP_REASON_CANCELLED_BY_APP

    private fun stopReasonName(): String =
        if (stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) "cancelled" else "reason $stopReason"
""",
"""    private fun willRunAgain(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return isStopped && stopReason != WorkInfo.STOP_REASON_CANCELLED_BY_APP
    }

    private fun stopReasonName(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "stop reason unavailable before Android 12"
        return if (stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) "cancelled" else "reason $stopReason"
    }
""", "API-guarded WorkManager stopReason")]
for old, new, desc in transfer_edits:
    if new not in transfer_source:
        if transfer_source.count(old) != 1: sys.exit("ModelTransferWorker stop-reason anchor changed")
        transfer_source = transfer_source.replace(old, new, 1)
transfer.write_text(transfer_source, encoding="utf-8")

# MediaExtractor and MediaCodec flag domains are different. Translate supported flags;
# encrypted samples cannot be safely remuxed by MediaMuxer and are rejected explicitly.
mux_files = [
("app/src/main/java/com/baba/callvault/data/merge/AudioConcat.kt", "info.flags = extractor.sampleFlags"),
("app/src/main/java/com/baba/callvault/data/merge/AudioSplit.kt", "info.flags = extractor.sampleFlags"),
("app/src/main/java/com/baba/callvault/data/clips/ClipCutter.kt", "info.flags = extractor.sampleFlags"),
("app/src/main/java/com/baba/callvault/transcription/cloud/CloudAudioSource.kt", "info.set(0, size, timeUs - firstUs, extractor.sampleFlags)"),
]
for relative, flag_line in mux_files:
    path = ENGINE / relative
    src = path.read_text(encoding="utf-8")
    if "private fun muxerFlagsFromSampleFlags" not in src:
        package_end = src.index("\n\n", src.index("package "))
        helper = """

/** Convert MediaExtractor sample flags to the distinct MediaCodec.BufferInfo flag domain. */
private fun muxerFlagsFromSampleFlags(sampleFlags: Int): Int {
    check(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
        "Encrypted samples cannot be copied into an unencrypted MediaMuxer output"
    }
    var flags = 0
    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
        flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
    }
    if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
        flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
    }
    return flags
}
"""
        src = src[:package_end] + helper + src[package_end:]
    if "import android.media.MediaExtractor\n" not in src:
        anchor = "import android.media.MediaFormat\n"
        if src.count(anchor) != 1: sys.exit(f"{relative}: MediaExtractor import anchor changed")
        src = src.replace(anchor, "import android.media.MediaExtractor\n" + anchor, 1)
    new_line = flag_line.replace("extractor.sampleFlags", "muxerFlagsFromSampleFlags(extractor.sampleFlags)")
    if new_line not in src:
        if src.count(flag_line) != 1: sys.exit(f"{relative}: sample flag anchor changed")
        src = src.replace(flag_line, new_line, 1)
    path.write_text(src, encoding="utf-8")

# Compose locale reads must be observable so configuration changes recompose the label.
patch_file("app/src/main/java/com/baba/callvault/ui/screens/CallHistoryUi.kt", [
("import androidx.compose.ui.platform.LocalContext\n", "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalConfiguration\n", "configuration import"),
("""internal fun callTimeLabel(millis: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))""",
"""internal fun callTimeLabel(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val time = SimpleDateFormat("HH:mm", locale).format(Date(millis))""", "observable locale"),
("""else -> SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(millis))""",
"""else -> SimpleDateFormat("dd/MM/yy HH:mm", locale).format(Date(millis))""", "observable date locale")
])

# Resolve resource strings in composition, not through LocalContext.getString inside callbacks.
patch_file("app/src/main/java/com/baba/callvault/ui/screens/PermissionsScreen.kt", [
("""    // Permission launchers must live inside a composable - the system dialog can only be
""",
"""    val cloudFolderRejectedMessage = stringResource(R.string.folder_cloud_rejected)

    // Permission launchers must live inside a composable - the system dialog can only be
""", "permission screen resource"),
("activityContext.getString(R.string.folder_cloud_rejected)", "cloudFolderRejectedMessage", "permission callback resource")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/SettingsScreen.kt", [
("""    // Folder picker — PersistentFolderPickerContract keeps access alive after a reboot.
""",
"""    val cloudFolderRejectedMessage = stringResource(R.string.folder_cloud_rejected)
    val driveFolderOverlapMessage = stringResource(R.string.folder_drive_overlaps_recordings)
    val importDoneFormat = stringResource(R.string.settings_import_done)
    val importFailedMessage = stringResource(R.string.settings_import_failed)

    // Folder picker — PersistentFolderPickerContract keeps access alive after a reboot.
""", "settings screen resource values"),
("context.getString(R.string.folder_cloud_rejected)", "cloudFolderRejectedMessage", "cloud folder callback"),
("context.getString(R.string.folder_drive_overlaps_recordings)", "driveFolderOverlapMessage", "drive overlap callback"),
("context.getString(R.string.settings_import_done, applied)", "String.format(java.util.Locale.getDefault(), importDoneFormat, applied)", "import result formatting"),
("context.getString(R.string.settings_import_failed)", "importFailedMessage", "import failure resource")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/WizardScreen.kt", [
("""    val updateTrigger by viewModel.updateTrigger.collectAsState()

    // Device/recording folder picker""",
"""    val updateTrigger by viewModel.updateTrigger.collectAsState()
    val cloudFolderRejectedMessage = stringResource(R.string.folder_cloud_rejected)
    val driveFolderOverlapMessage = stringResource(R.string.folder_drive_overlaps_recordings)

    // Device/recording folder picker""", "wizard resource values"),
("context.getString(R.string.folder_cloud_rejected)", "cloudFolderRejectedMessage", "wizard cloud folder callback"),
("context.getString(R.string.folder_drive_overlaps_recordings)", "driveFolderOverlapMessage", "wizard drive overlap callback")
])

# Polish and Russian require few/many plural categories. Preserve each translation's
# existing wording for those categories rather than leaving the resource incomplete.
import re
for locale in ("pl", "ru"):
    for filename in ("strings.xml", "strings_home.xml"):
        path = ENGINE / f"app/src/main/res/values-{locale}/{filename}"
        src = path.read_text(encoding="utf-8")
        def complete_plural(match):
            body = match.group(0)
            if 'quantity="few"' in body and 'quantity="many"' in body:
                return body
            other = re.search(r'<item quantity="other">(.*?)</item>', body, re.S)
            if not other:
                sys.exit(f"{path}: plural without other quantity")
            value = other.group(1)
            additions = ""
            if 'quantity="few"' not in body:
                additions += f'        <item quantity="few">{value}</item>\n'
            if 'quantity="many"' not in body:
                additions += f'        <item quantity="many">{value}</item>\n'
            return body.replace(other.group(0), additions + other.group(0), 1)
        src = re.sub(r'<plurals\b[^>]*>.*?</plurals>', complete_plural, src, flags=re.S)
        path.write_text(src, encoding="utf-8")

# Telephony is optional hardware: tablets/Chromebooks can install the app without
# falsely claiming telephony is mandatory.
manifest = ENGINE / "app/src/main/AndroidManifest.xml"
manifest_src = manifest.read_text(encoding="utf-8")
feature = '<uses-feature android:name="android.hardware.telephony" android:required="false" />'
if feature not in manifest_src:
    anchor = '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:tools="http://schemas.android.com/tools">\n'
    if manifest_src.count(anchor) != 1: sys.exit("Manifest feature insertion anchor changed")
    manifest_src = manifest_src.replace(anchor, anchor + "\n    " + feature + "\n", 1)
manifest.write_text(manifest_src, encoding="utf-8")

print(
    "Verified Shizuku-first defaults (Shizuku backend + automatic incoming/outgoing recording); "
    "ADB prerequisite tests now explicitly select standalone mode."
)
