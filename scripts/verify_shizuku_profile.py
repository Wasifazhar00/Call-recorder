#!/usr/bin/env python3
"""Fail the build if the shipped CallVault module loses its call-recording profile wiring."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "vendor" / "CallVault"

def require(path: str, needles: tuple[str, ...]) -> None:
    file = ENGINE / path
    if not file.is_file():
        sys.exit(f"Required call-recorder source is missing: {path}")
    source = file.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in source]
    if missing:
        sys.exit(f"{path} is missing required profile wiring: {missing}")

require(
    "app/src/main/cpp/CMakeLists.txt",
    ("add_library(audiohandoff SHARED audiohandoff.cpp)",),
)
if "whisper" in (ENGINE / "app/src/main/cpp/CMakeLists.txt").read_text(encoding="utf-8").lower():
    sys.exit("AI inference native targets must not be included in the product build")
for removed_asset in (
    "app/src/main/cpp/whispercv.cpp",
    "app/src/main/cpp/llamacv.cpp",
    "app/src/main/assets/ggml-silero-v5.1.2.bin",
):
    if (ENGINE / removed_asset).exists():
        sys.exit(f"Removed AI runtime asset is still present: {removed_asset}")

require(
    "app/src/main/java/com/baba/callvault/data/AppPreferences.kt",
    (
        "const val CARRIER_RECORDING_ENABLED = true",
        "const val AUTO_RECORD_INCOMING = true",
        "const val AUTO_RECORD_OUTGOING = true",
        "val PRIVILEGED_MODE: String = PrivilegedMode.SHIZUKU.key",
        "val AUDIO_SOURCE = ScrcpyAudioSource.VOICE_CALL.cliKey",
        "const val IGNORE_ANONYMOUS_INCOMING = false",
        "val RECORDING_STATE = RecordingState.ACTIVE.key",
    ),
)
require(
    "app/src/main/java/com/baba/callvault/services/call/CallSessionManager.kt",
    (
        "preferences.isCarrierRecordingEnabled()",
        "preferences.isAutoRecordIncomingEnabled()",
        "preferences.isAutoRecordOutgoingEnabled()",
        "RecordingForegroundService.ACTION_START_RECORDING",
        "RecordingForegroundService.ACTION_STANDBY",
    ),
)
require(
    "app/src/main/AndroidManifest.xml",
    (
        ".services.call.PhoneStateReceiver",
        ".services.call.NewOutgoingCallReceiver",
        ".services.recording.RecordingForegroundService",
        "moe.shizuku.manager.permission.API_V23",
    ),
)
require(
    "app/build.gradle.kts",
    ("minSdk = 30", "targetSdk = 36", 'applicationId = "com.baba.callvault"'),
)
require(
    "app/src/main/java/com/baba/callvault/services/recording/AudioRecordingEngine.kt",
    ("audioSourceEnum == ScrcpyAudioSource.VOICE_CALL) 2 else 1",),
)
require(
    "app/src/main/java/com/baba/callvault/server/ShizukuBackend.kt",
    ("Shizuku.checkSelfPermission()", "Shizuku.bindUserService"),
)
require(
    "app/src/main/java/com/baba/callvault/server/RecorderUserService.kt",
    ("class RecorderUserService", "RecorderServiceImpl"),
)
require(
    "app/src/main/java/com/baba/callvault/services/recording/VoipTelephonyGate.kt",
    (
        '@SuppressLint("MissingPermission")',
        "context.getSystemService(TelecomManager::class.java)?.isInManagedCall",
        "}.getOrDefault(false)",
    ),
)

require(
    "app/src/test/java/com/baba/callvault/data/health/SetupPrerequisitesTest.kt",
    (
        'Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0)',
        "preferences.setOfflineRecordingEnabled(false)",
        "preferences.setPrivilegedMode(PrivilegedMode.STANDALONE)",
    ),
)

require(
    "app/src/main/java/com/baba/callvault/transcription/model/ModelDownloadWorker.kt",
    (
        "import android.os.Build",
        "Build.VERSION.SDK_INT < Build.VERSION_CODES.S",
        "stop reason unavailable before Android 12",
    ),
)

require(
    "app/src/main/java/com/baba/callvault/server/BypassedAudioRecord.kt",
    (
        "Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU",
        "AttributionSource.Builder(callingUid)",
        "AttributionSource.myAttributionSource()",
    ),
)


require(
    "app/src/main/aidl/com/baba/callvault/server/IRecorderService.aidl",
    ("String capturePermissionReport();",),
)
require(
    "app/src/main/java/com/baba/callvault/server/RecorderServiceImpl.kt",
    ("override fun capturePermissionReport(): String", "CAPTURE_AUDIO_OUTPUT", "CAPTURE_VOICE_COMMUNICATION_OUTPUT"),
)
require(
    "app/src/main/java/com/baba/callvault/ui/screens/SettingsAboutDebugSection.kt",
    ("CapturePermissionDiagnostics()", "service.capturePermissionReport()", "RecorderConnection.service"),
)
require(
    "app/src/test/java/com/baba/callvault/server/RecorderTransactionCodesTest.kt",
    ("TRANSACTION_capturePermissionReport",),
)

print("Call-recording profile verified: incoming + outgoing automation, carrier master switch, "
      "telephony receivers, foreground service and Shizuku binder path are present.")


# Pin the cellular-call capture route: both directions are captured through the
# privileged VOICE_CALL source in the shell-UID recorder. Do not replace this
# with two competing AudioRecord sessions without device-level concurrency tests.
require(
    "app/src/main/java/com/baba/callvault/integrations/scrcpy/ScrcpyAudioSourceMapping.kt",
    ('"voice-call" -> MediaRecorder.AudioSource.VOICE_CALL',),
)
require(
    "app/src/test/java/com/baba/callvault/integrations/scrcpy/ScrcpyAudioSourceMappingTest.kt",
    ("voice call maps to the privileged call source", "MediaRecorder.AudioSource.VOICE_CALL"),
)
