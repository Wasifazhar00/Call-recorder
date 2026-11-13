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


# CallVault product profile: keep only the native audio-handoff library required by
# resilient call recording. Whisper/llama inference engines are excluded from this product.
native_cmake = ENGINE / "app/src/main/cpp/CMakeLists.txt"
native_cmake.write_text("""# Native component required for resilient call-recording audio handoff.
cmake_minimum_required(VERSION 3.22.1)
project(audiohandoff)
add_link_options($<$<NOT:$<CONFIG:Debug>>:-Wl,--strip-all>)
add_link_options("LINKER:--build-id=none")
get_filename_component(CALLVAULT_REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../.. ABSOLUTE)
add_compile_options("-ffile-prefix-map=${CALLVAULT_REPO_ROOT}=.")
add_library(audiohandoff SHARED audiohandoff.cpp)
find_library(log-lib log)
target_link_libraries(audiohandoff ${log-lib})
""", encoding="utf-8")
for obsolete in (
    ENGINE / "app/src/main/cpp/whispercv.cpp",
    ENGINE / "app/src/main/cpp/llamacv.cpp",
    ENGINE / "app/src/main/assets/ggml-silero-v5.1.2.bin",
):
    if obsolete.exists():
        obsolete.unlink()

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
        # Insertions retain their anchor comment inside the replacement block.
        # Skip those on the second profile pass, but still apply string substitutions
        # when the replacement already exists elsewhere in the file.
        if old in new and new in text:
            continue
        count = text.count(old)
        if count:
            text = text.replace(old, new)
        elif new not in text:
            sys.exit(f"{relative_path}: neither original nor patched code found for {description}")
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
        declaration_anchor = "\n/**\n"
        if src.count(declaration_anchor) < 1:
            sys.exit(f"{relative}: declaration insertion anchor changed")
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
        src = src.replace(declaration_anchor, helper + declaration_anchor, 1)
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
("import androidx.compose.ui.res.stringResource\n", "import androidx.compose.ui.res.stringResource\nimport androidx.compose.ui.platform.LocalConfiguration\n", "configuration import"),
("""internal fun callTimeLabel(millis: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))""",
"""internal fun callTimeLabel(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
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

# A completed user must not be ejected from Home/Settings merely because they turn
# Shizuku off. Initial onboarding still requires a working transport; returning users
# retain access to Settings so they can pair embedded ADB or re-enable Shizuku.
patch_file("app/src/main/java/com/baba/callvault/onboarding/OnboardingStatus.kt", [
("""        /**
         * Returns true only when every prerequisite is satisfied, including the disclaimer.
         */""",
"""        /**
         * A returning user may remain in Home while changing recorder transports.
         * This deliberately does not make setup complete: it only prevents a transport
         * switch from trapping the user outside Settings. All actual permissions remain required.
         */
        fun canReturnToHome(): Boolean =
            wizardCompleted &&
                disclaimerAccepted &&
                notificationsGranted &&
                contactsGranted &&
                phoneStateGranted &&
                callLogGranted &&
                batteryExempted

        /**
         * Returns true only when every prerequisite is satisfied, including the disclaimer.
         */""", "returning-user transport fallback")
])

patch_file("app/src/main/java/com/baba/callvault/AppNavigationScreen.kt", [
("""        !status.isComplete()       -> AppScreen.Permissions""",
"""        !status.isComplete() && !status.canReturnToHome() -> AppScreen.Permissions""",
"keep returning users in Home after transport changes")
])

patch_file("app/src/test/java/com/baba/callvault/onboarding/OnboardingGateTest.kt", [
("""    /**
     * Storage deliberately does not gate this — folder choice moved into the wizard. Pinned because it
""",
"""    /** Turning Shizuku off after setup must not trap a returning user on onboarding. */
    @Test
    fun `completed user can return to home without a transport`() {
        val returningUser = status(adbConnected = false)
        assertFalse(returningUser.isComplete())
        assertTrue(returningUser.canReturnToHome())
    }

    /** Revoked permissions still require the user to return to setup. */
    @Test
    fun `returning user with a revoked permission cannot bypass onboarding`() {
        val returningUser = status(adbConnected = false, batteryExempted = false)
        assertFalse(returningUser.canReturnToHome())
    }

    /**
     * Storage deliberately does not gate this — folder choice moved into the wizard. Pinned because it
""", "returning-user navigation regression tests")
])


# Add a read-only report from the actual shell recorder host. Never attempt protected grants.
patch_file("app/src/main/aidl/com/baba/callvault/server/IRecorderService.aidl", [
("""    String approveWirelessDebuggingNetwork(boolean alwaysAllow);
}""",
"""    String approveWirelessDebuggingNetwork(boolean alwaysAllow);

    /** Read-only protected capture permission report. Appended to preserve Binder transaction IDs. */
    String capturePermissionReport();
}""", "append capture permission diagnostics")
])

patch_file("app/src/main/java/com/baba/callvault/server/RecorderServiceImpl.kt", [
("""    companion object {
        private const val TAG = "CV:RecorderServer"
""",
"""    override fun capturePermissionReport(): String {
        val uid = android.os.Process.myUid()
        val permissions = listOf(
            "android.permission.CAPTURE_AUDIO_OUTPUT",
            "android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT",
        )
        val checks = permissions.joinToString("\\n") { permission ->
            val status = runCatching {
                val process = ProcessBuilder(
                    "/system/bin/cmd", "package", "check-permission", permission, "com.android.shell"
                ).redirectErrorStream(true).start()
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    "unknown (check timed out)"
                } else {
                    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                    when {
                        output.equals("granted", ignoreCase = true) -> "granted"
                        output.equals("denied", ignoreCase = true) -> "denied"
                        else -> "unknown ($output)"
                    }
                }
            }.getOrElse { "unknown (" + it.javaClass.simpleName + ")" }
            "$permission: $status"
        }
        return "Recorder host UID: $uid\\nPermission checks target com.android.shell (not the CallVault app):\\n$checks\\nA granted permission is necessary but does not prove live cellular-call capture."
    }

    companion object {
        private const val TAG = "CV:RecorderServer"
""", "read-only protected capture permission report")
])

patch_file("app/src/main/java/com/baba/callvault/ui/screens/SettingsAboutDebugSection.kt", [
("""import com.baba.callvault.server.RecorderBackend
""",
"""import com.baba.callvault.server.RecorderBackend
import com.baba.callvault.server.RecorderConnection
""", "recorder connection import"),
("""    SettingsSection(title = stringResource(R.string.settings_section_debug), expanded = expanded, onToggle = onToggle) {
        SettingsToggleRow(
""",
"""    SettingsSection(title = stringResource(R.string.settings_section_debug), expanded = expanded, onToggle = onToggle) {
        CapturePermissionDiagnostics()
        SettingsDivider()
        SettingsToggleRow(
""", "capture diagnostics section")
])

patch_file("app/src/main/java/com/baba/callvault/ui/screens/SettingsAboutDebugSection.kt", [
("""/** How much of the log the in-app viewer shows. The file self-trims at 1000 lines. */
""",
"""/**
 * Reads the live recorder host instead of inferring its privileges from the app UI.
 * A permission result is diagnostic only; it does not guarantee cellular-call capture.
 */
@Composable
private fun CapturePermissionDiagnostics() {
    var report by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Capture capability diagnostics", style = MaterialTheme.typography.titleSmall)
        Text(
            "Reads the active recorder UID and checks Android protected audio permissions for the shell host. This is read-only and does not record a call.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        CvSecondaryButton(
            text = if (checking) "Checking…" else "Check recorder permissions",
            onClick = {
                if (!checking) {
                    checking = true
                    scope.launch {
                        report = withContext(Dispatchers.IO) {
                            val service = RecorderConnection.service
                                ?: return@withContext "Recorder service is not connected. Start the selected backend and check again."
                            runCatching { service.capturePermissionReport() }
                                .getOrElse { "Could not query recorder: " + it.javaClass.simpleName + ": " + (it.message ?: "no details") }
                        }
                        checking = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (checking) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        report?.let {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Denied or unknown means Android/OEM firmware is not exposing that capability to the shell host. Granted does not prove successful two-sided cellular recording; that still requires incoming and outgoing call tests on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How much of the log the in-app viewer shows. The file self-trims at 1000 lines. */
""", "capture diagnostics UI")
])

# Keep the new AIDL report at the end of the positional transaction table.
patch_file("app/src/test/java/com/baba/callvault/server/RecorderTransactionCodesTest.kt", [
("""        assertEquals(first + 27, IRecorderService.Stub.TRANSACTION_approveWirelessDebuggingNetwork)
""",
"""        assertEquals(first + 27, IRecorderService.Stub.TRANSACTION_approveWirelessDebuggingNetwork)
        assertEquals(first + 28, IRecorderService.Stub.TRANSACTION_capturePermissionReport)
""", "capture permission report transaction code")
])

patch_file("app/src/main/java/com/baba/callvault/ui/screens/SettingsAboutDebugSection.kt", [
("""import kotlinx.coroutines.withContext
""",
"""import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
""", "diagnostics coroutine launch import")
])


# CallVault recorder-only product profile. Remove all user-facing entry points to
# transcription/summarisation and cancel any old queued AI work on first Home entry.
# Keep the upstream data/schema classes intact so existing installations upgrade
# without destructive database migrations or loss of recordings/history.
patch_file("app/src/main/java/com/baba/callvault/ui/screens/HubScreen.kt", [
("""                item {
                    HubCard(
                        icon = Icons.AutoMirrored.Filled.Article,
                        accent = brand.info,
                        count = stringResource(R.string.home_transcripts_count, transcriptsCount),
                        title = stringResource(R.string.home_transcripts_title),
                        body = stringResource(R.string.home_hub_transcripts_body),
                        onClick = { onOpenSection(HomeSection.Transcripts) },
                    )
                }
                item {
                    HubCard(
                        icon = Icons.Filled.AutoAwesome,
                        accent = brand.success,
                        count = stringResource(R.string.home_summaries_count, summariesCount),
                        title = stringResource(R.string.home_summaries_title),
                        body = stringResource(R.string.home_hub_summaries_body),
                        onClick = { onOpenSection(HomeSection.Summaries) },
                    )
                }
""", """                // AI transcript and summary cards are excluded from this product profile.
""", "remove transcript and summary hub cards")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/ScopedSettingsContent.kt", [
("val groups = SettingsGroups.forScope(scope)",
 "val groups = SettingsGroups.forScope(scope).filterNot { it == SettingsGroup.MODEL_AND_LANGUAGE || it == SettingsGroup.WHEN_TO_TRANSCRIBE || it == SettingsGroup.SUMMARIES }",
 "remove transcription and summary settings groups"),
("val all = SettingsGroups.forScope(SettingsScope.ALL)",
 "val all = SettingsGroups.forScope(SettingsScope.ALL).filterNot { it == SettingsGroup.MODEL_AND_LANGUAGE || it == SettingsGroup.WHEN_TO_TRANSCRIBE || it == SettingsGroup.SUMMARIES }",
 "remove transcription and summary settings search results")
])
patch_file("app/src/main/AndroidManifest.xml", [
("""            android:name=".ShareImportActivity"
            android:exported="true"
""",
"""            android:name=".ShareImportActivity"
            android:exported="false"
""", "remove external audio-to-transcription share target")
])
patch_file("app/src/main/java/com/baba/callvault/ui/common/TranscriptActionButton.kt", [
("""    val action = TranscriptRowAction.forStatus(status)
    val description = stringResource(action.contentDescriptionRes)

    // Work in flight is shown, never re-triggerable: a second tap would only queue the same call
    // again, and the first tap already committed the phone to minutes of CPU.
    //
    // The ring itself is shared with the Summaries page — see [WorkProgressRing], which holds the
    // two traps (a spinner that reads as a hang, and M3's coral track) in one place.
    if (!action.isTappable) {
        WorkProgressRing(percent = percent, modifier = modifier)
        return
    }

    IconButton(
        onClick = when (action) {
            TranscriptRowAction.TRANSCRIBE -> onTranscribe
            TranscriptRowAction.OPEN -> onOpen
            TranscriptRowAction.RETRY -> onRetry
            TranscriptRowAction.BUSY -> return // unreachable: handled above
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = when (action) {
                TranscriptRowAction.TRANSCRIBE -> Icons.Outlined.Subtitles
                TranscriptRowAction.OPEN -> Icons.AutoMirrored.Filled.Article
                TranscriptRowAction.RETRY -> Icons.Filled.ErrorOutline
                TranscriptRowAction.BUSY -> Icons.Outlined.Subtitles // unreachable
            },
            contentDescription = description,
            // A finished transcript is worth drawing attention to; a failure more so.
            tint = when (action) {
                TranscriptRowAction.OPEN -> MaterialTheme.colorScheme.primary
                TranscriptRowAction.RETRY -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
""",
"""    // Transcription is excluded from the CallVault recorder-only product profile.
    // Keep the stable composable signature for upstream call sites and upgrade compatibility.
""", "remove transcription action from recording rows")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/ShareClipScreen.kt", [
("""            when {
                !includeText -> share(clip, null)
                transcribed -> {
                    // The transcript is read in the background; give it a moment rather than share no text.
                    val lines = withTimeoutOrNull(LINES_WAIT_MS) { snapshotFlow { linesNow }.first { it.isNotEmpty() } }
                    share(clip, ClipTranscript.render(ClipTranscript.linesIn(lines ?: linesNow, cutRange)))
                }
                source == TextSource.PART && !callInProgress -> {
                    work = ClipWork.TranscribingPart
                    when (val outcome = ClipTranscriber.transcribe(context, clip, cutRange)) {
                        is ClipTranscriber.Outcome.Done -> share(clip, ClipTranscript.render(outcome.lines))
                        ClipTranscriber.Outcome.Busy -> work = ClipWork.TextProblem(R.string.share_clip_busy)
                        ClipTranscriber.Outcome.NotSetUp -> work = ClipWork.TextProblem(R.string.share_clip_not_set_up)
                        is ClipTranscriber.Outcome.Failed -> work = ClipWork.TextProblem(R.string.share_clip_transcribe_failed)
                    }
                }
                else -> {
                    // The whole call: through the app's own transcription (with its own confirmations), then only
                    // this part's lines.
                    work = ClipWork.WaitingForCall
                    if (!callInProgress) {
                        onTranscribeWholeCall()
                        // A previous run's FAILED must not count as this run's result, and a declined confirmation
                        // leaves it NONE for ever: wait to see this run start first.
                        val started = withTimeoutOrNull(START_WAIT_MS) {
                            snapshotFlow { statusNow }
                                .first { it == TranscriptStatus.QUEUED || it == TranscriptStatus.RUNNING || it == TranscriptStatus.DONE }
                        }
                        if (started == null) {
                            work = ClipWork.TextProblem(R.string.share_clip_transcribe_failed)
                            return@launch
                        }
                    }
                    val ended = snapshotFlow { statusNow }
                        .first { it == TranscriptStatus.DONE || it == TranscriptStatus.FAILED }
                    if (ended == TranscriptStatus.DONE) {
                        // A silent call has no lines at all, so this waits a little and no longer.
                        val lines = withTimeoutOrNull(LINES_WAIT_MS) { snapshotFlow { linesNow }.first { it.isNotEmpty() } }
                        share(clip, ClipTranscript.render(ClipTranscript.linesIn(lines ?: emptyList(), cutRange)))
                    } else {
                        work = ClipWork.TextProblem(R.string.share_clip_transcribe_failed)
                    }
                }
            }
""",
"""            // Recorder-only product profile: clips are shared as audio only.
            share(clip, null)
""", "remove transcription from clip sharing"),
("""            TranscriptChoice(
                include = includeText,
                onInclude = { includeText = it },
                transcribed = transcribed,
                callInProgress = callInProgress,
                linesInPart = if (transcribed) ClipTranscript.linesIn(callLines, range).size else -1,
                source = source,
                onSource = { source = it },
            )
""",
"""            // Transcript and transcription controls are excluded from the recorder-only product profile.
""", "remove transcript controls from clip sharing")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/PlaybackScreen.kt", [
("""            // Above the transcript row and the note, because it answers the question the transcript
            // answers more slowly — and because a stamp in it is a jump into the player above.
            SummaryCard(
                state = summaryState,
                onCreate = onSummarise,
                onRedo = onSummarise,
                onStop = onStopSummary,
                // Seek, then play. A jump point that silently moved the cursor without starting
                // would look like nothing happened.
                onSeek = { millis ->
                    onSeek(millis.toInt())
                    if (!isPlaying) onPlay()
                }
            )
""", """            // Summary UI is excluded from the CallVault recorder-only product profile.
""", "remove summary card from recording playback")
])

patch_file("app/src/main/java/com/baba/callvault/ui/screens/PlaybackScreen.kt", [
("""            // The same one-slot control the list rows use — transcribe / in progress / read / retry
            // — rather than a second thing that means the same. Retry is transcribe: on this screen
            // there is one recording and one thing a failed run can do next.
            TranscriptActionButton(
                status = transcriptStatus,
                onTranscribe = onTranscribe,
                onOpen = onOpenTranscript,
                onRetry = onTranscribe,
                modifier = Modifier.size(ACTION_TARGET),
                // Was left at its default of 0, so this one spun without ever filling while the
                // identical control in the list showed a figure. A ring that turns for minutes and
                // never moves is indistinguishable from a hang — which is the whole reason the
                // percentage was added to that control in the first place.
                percent = transcriptPercent
            )
""", """            // Transcription controls are excluded from the CallVault recorder-only product profile.
""", "remove playback transcription action")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/LibraryRowMenu.kt", [
("""            if (menu.transcribeAgain) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transcript_retranscribe)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = {
                        close()
                        onTranscribeAgain()
                    },
                )
            }
""", """            // Re-transcription is excluded from the recorder-only product profile.
""", "remove transcript retry from library row menu")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/LibrarySelectionUi.kt", [
("""        selection.onTranscribeAgain?.let { onTranscribeAgain ->
            IconButton(onClick = onTranscribeAgain, enabled = hasPicked) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.transcript_retranscribe),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
""", """        // Bulk re-transcription is excluded from the recorder-only product profile.
""", "remove bulk transcript retry action")
])
patch_file("app/src/main/java/com/baba/callvault/ui/common/TranscriptView.kt", [
("TranscriptPageMenu(summaryState, onSummarise, onTranscribeAgain)",
 "TranscriptPageMenu(summaryState, onSummarise, null)",
 "remove re-transcription action from transcript reader"),
])
patch_file("app/src/main/java/com/baba/callvault/AppNavigationScreen.kt", [
("""    val section = chosenSection ?: HomeSection.opening(resolvedScreen, storedSectionKey) ?: HomeSection.Hub
""",
"""    val restoredSection = HomeSection.opening(resolvedScreen, storedSectionKey)
    val requestedSection = chosenSection ?: restoredSection
    val section = requestedSection
        .takeUnless { it == HomeSection.Transcripts || it == HomeSection.Summaries }
        ?: HomeSection.Hub
""", "route legacy AI sections to the hub"),
("""        val target = HomeSection.forNotification(resolvedScreen, notificationDestination)
        if (target != null) goToSection(target)
""",
"""        val requestedTarget = HomeSection.forNotification(resolvedScreen, notificationDestination)
        val target = if (requestedTarget == HomeSection.Transcripts || requestedTarget == HomeSection.Summaries) {
            HomeSection.Hub
        } else requestedTarget
        if (target != null) goToSection(target)
""", "route legacy transcript notifications to the hub")
])
patch_file("app/src/main/java/com/baba/callvault/ui/screens/HomeScreen.kt", [
("import com.baba.callvault.data.AppPreferences\n",
 "import com.baba.callvault.data.AppPreferences\nimport com.baba.callvault.data.TranscriptionMode\n",
 "manual transcription mode import"),
("""    // A recording named from outside the app — the share target's Open button — joins the same
""",
"""    // Recorder-only product profile: cancel old transcription/summarisation work and prevent
    // the old automatic sweep from being re-created by the saved preference.
    LaunchedEffect(Unit) {
        AppPreferences(context).setTranscriptionMode(TranscriptionMode.MANUAL)
        TranscriptionScheduler.stopNow(context)
        SummaryScheduler.stopNow(context)
    }

    // A recording named from outside the app — the share target's Open button — joins the same
""", "cancel legacy AI work when Home opens"),
("""    // A transcript named from outside the app — the "transcript ready" notification. Straight to the
    // reading view rather than through a list: the page reads from the transcripts database and needs
    // no catalog row, so unlike a recording there is nothing to wait for — and for the case this
    // exists for, a finished "Transcribe only" import, there will never BE a catalog row to wait for.
    //
    // playbackFor is cleared because the reading view is only drawn when no recording is open (see
    // readingPresentation): without this, a tap arriving while the user had left a recording open
    // would draw the transcript as a sheet over that other recording's screen.
    LaunchedEffect(openTranscript) {
        val requested = openTranscript ?: return@LaunchedEffect
        onSelectSection(HomeSection.Transcripts)
        playbackFor = null
        readingFromSummary = false
        readingOrder = ArrayList()
        readingFor = requested
        onOpenTranscriptHandled()
    }
""",
"""    // Acknowledge legacy transcript notifications without navigating into removed product features.
    LaunchedEffect(openTranscript) {
        if (openTranscript != null) {
            AppLogger.i("CV:Profile", "Ignored legacy transcript notification in recorder-only build")
            onOpenTranscriptHandled()
        }
    }
""", "disable legacy transcript deep links")
])

# Shizuku-Next is a drop-in manager: it retains Shizuku's package/API/permission contract.
# The app must continue using the public Shizuku API and discover the manager by its declared permission,
# never hard-code a fork-specific package name.
print(
    "Verified Shizuku-first defaults (Shizuku backend + automatic incoming/outgoing recording); "
    "ADB prerequisite tests now explicitly select standalone mode."
)
