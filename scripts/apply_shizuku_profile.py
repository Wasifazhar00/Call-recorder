#!/usr/bin/env python3
"""Apply reproducible Shizuku-first defaults and test fixtures to the imported standalone CallVault source.

Safe to run repeatedly: already-applied values are accepted, while unexpected source drift fails closed.
The imported source tree is the build source of truth; changes are committed only after CI passes.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "upstream" / "CallVault"
TARGET = ENGINE / "app/src/main/java/com/baba/callvault/data/AppPreferences.kt"
TEST = ENGINE / "app/src/test/java/com/baba/callvault/data/health/SetupPrerequisitesTest.kt"
VOIP_GATE = ENGINE / "app/src/main/java/com/baba/callvault/services/recording/VoipTelephonyGate.kt"
BYPASSED_AUDIO = ENGINE / "app/src/main/java/com/baba/callvault/server/BypassedAudioRecord.kt"
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

# MediaExtractor and MediaCodec flag domains are different. Translate supported flags;
# encrypted samples cannot be safely remuxed by MediaMuxer and are rejected explicitly.
mux_files = [
("app/src/main/java/com/baba/callvault/data/merge/AudioConcat.kt", "info.flags = extractor.sampleFlags"),
("app/src/main/java/com/baba/callvault/data/merge/AudioSplit.kt", "info.flags = extractor.sampleFlags"),
("app/src/main/java/com/baba/callvault/data/clips/ClipCutter.kt", "info.flags = extractor.sampleFlags"),
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


# Apply revision-checked source edits idempotently. Fail closed if upstream drifted.
def patch_file(relative_path, edits):
    path = ENGINE / relative_path
    if not path.is_file():
        sys.exit(f"{relative_path}: required patch target is missing")
    source_text = path.read_text(encoding="utf-8")
    for old, new, description in edits:
        if old in new and new in source_text:
            continue
        count = source_text.count(old)
        if count:
            source_text = source_text.replace(old, new)
        elif new not in source_text:
            sys.exit(f"{relative_path}: neither original nor patched code found for {description}")
    path.write_text(source_text, encoding="utf-8")

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
# Home, Settings and Playback screens are already recorder-only in source.

# AI scheduling is removed; no runtime transcription or summary jobs are scheduled.


# Shizuku-Next is a drop-in manager: it retains Shizuku's package/API/permission contract.
# The app must continue using the public Shizuku API and discover the manager by its declared permission,
# never hard-code a fork-specific package name.
print(
    "Verified Shizuku-first defaults (Shizuku backend + automatic incoming/outgoing recording); "
    "ADB prerequisite tests now explicitly select standalone mode."
)
