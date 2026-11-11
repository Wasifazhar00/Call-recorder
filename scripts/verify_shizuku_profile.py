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
    "app/src/main/java/com/baba/callvault/data/AppPreferences.kt",
    (
        "const val CARRIER_RECORDING_ENABLED = true",
        "const val AUTO_RECORD_INCOMING = true",
        "const val AUTO_RECORD_OUTGOING = true",
        "val PRIVILEGED_MODE: String = PrivilegedMode.SHIZUKU.key",
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

print("Call-recording profile verified: incoming + outgoing automation, carrier master switch, "
      "telephony receivers, foreground service and Shizuku binder path are present.")
