# Android 17 and call recording: what users report (research 2026-10-20)

📐 Desk research only: store reviews, web pages, GitHub issues, Reddit archive. Nothing here was
reproduced on a device. Counts come from keyword regexes over review text, so treat them as
approximate.

Android 17 stable reached Pixels on **2026-06-16/17**. A Pixel build of **2026-09-26
(CP3A.260905.009, Sept patch)** changed developer-settings behaviour again. One UI 9 shipped on the
Fold 8 / S26 line (Aug 2026). ColorOS 17 was unveiled 2026-09-17 and replaces OxygenOS from 17 on.
HyperOS on Android 17 is on the Xiaomi 17 series.

## Sources and reachability

| Source | Status |
|---|---|
| Google Play (google-play-scraper 1.x, venv) | ✅ Searched 3 queries × 7 countries (us, in, il, de, br, ru, id). **Germany search failed** (the library returned a parse error for all 3 de queries); de *reviews* still pulled. 71,419 unique reviews pulled across 17 apps × 7 locales, 20,824 dated since 2026-06-01. |
| Apple App Store | ⏭️ Skipped on purpose: iOS is irrelevant to an Android 17 question. |
| Web (WebSearch + WebFetch) | ✅ Mostly. **403 on** Samsung Members (r2.community.samsung.com), Android Authority, The Hacker News, Notebookcheck; used search snippets and mirror articles (securityaffairs, kitsumed blog, Android Developers Blog) instead. |
| GitHub issues (`gh`) | ✅ RikkaApps/Shizuku, thedjchi/Shizuku, kitsumed/ShizuCallRecorder, chenxiaolong/BCR, Genymobile/scrcpy. |
| Reddit | ✅ partly, via the arctic-shift archive (`searching-reddit` skill); reddit.com itself is blocked from this machine. 8 subs harvested (≈32k posts). r/shizuku returned 0 posts; keyword/comment search hit HTTP 422; 7 smaller subs not covered (see §4). |

**Big caveat on Play reviews:** reviews do not carry the device or OS version. Only 10 of 71k
reviews name "Android 17" (or One UI 9 / ColorOS 17) explicitly. Everything else below is
"recent complaint" data that *coincides* with the Android 17 / One UI 9 rollout; it cannot be
attributed to Android 17 per review.

## 1. Play Store landscape (call-recording-relevant apps, by installs)

"Ranks" = how many of the 18 successful (query, country) searches the app appeared in (top 30).

| App | Package | Developer | Installs | Rating | Ratings | Last update | Monetisation | Ranks |
|---|---|---|---:|---:|---:|---|---|---:|
| Phone by Google | `com.google.android.dialer` | Google LLC | 5,902,177,209 | 4.43 | 52.1M | 2026-10-15 | – | (searched by id) |
| Truecaller | `com.truecaller` | Truecaller | 1,905,042,958 | 4.38 | 29.8M | 2026-10-16 | ads, IAP | 1 |
| CallApp | `com.callapp.contacts` | CallApp | 432,406,501 | 4.26 | 1.83M | 2026-10-19 | ads, IAP | 9 |
| Call Recorder Automatic (yoni) | `il.co.smedia.callrecorder.yoni` | Tap AI | 79,433,944 | 2.40 | 504k | 2024-10-23 | ads, IAP | 15 |
| Call Recorder – Auto Recording | `call.recorder.automatic.acr` | Call Team | 69,762,480 | 3.87 | 324k | 2022-10-10 | ads, IAP | 15 |
| **Cube ACR** | `com.catalinagroup.callrecorder` | Cube Apps | **56,633,781** | 4.09 | 912k | 2026-10-05 | ads, IAP | **18** |
| Automatic Call Recorder ACR | `com.q4u.autocallrecorder` | Q4U | 46,145,404 | 3.44 | 710k | 2026-09-23 | ads, IAP | 9 |
| Shizuku (dependency, not a recorder) | `moe.shizuku.privileged.api` | Xingchen & Rikka | 43,317,515 | 4.23 | 57.7k | 2026-09-04 | – | – |
| Call Recorder – callX | `com.smsrobot.callrecorder` | SMSROBOT | 32,331,649 | 4.03 | 440k | 2022-10-31 | ads, IAP | 18 |
| Call Recorder Automatic – ACR | `com.app.autocallrecorder` | Quantum4u | 28,912,271 | 3.62 | 216k | 2026-10-15 | ads, IAP | 4 |
| Automatic Call Recorder Pro | `com.smsrobot.callu` | SMSROBOT | 28,512,090 | 4.04 | 430k | 2022-10-31 | ads, IAP | 18 |
| drupe | `mobi.drupe.app` | drupe | 27,800,329 | 4.07 | 376k | 2026-09-07 | ads, IAP | 10 |
| All Call Recorder | `com.starbox.callrecorder` | Starbox | 22,853,347 | 3.01 | 380k | 2025-10-09 | ads | 6 |
| Call Recorder – Auto Recording | `com.m24apps.acr` | m24apps | 22,203,173 | 3.27 | 334k | 2026-10-09 | ads, IAP | 11 |
| Auto Call recorder App | `record.phone.call` | Instant Digital | 17,817,255 | 3.42 | 31k | 2026-09-18 | ads, IAP | 15 |
| Call recorder | `com.bkapps.callrecord` | PkDevs | 15,255,955 | 3.58 | 124k | 2026-10-14 | ads | 2 |
| Call Recorder Automatic | `com.asjlnwawal.callrecorder` | – | 11,084,267 | 3.97 | 166k | 2025-03-23 | ads | 18 |
| ODialer (OPPO/OnePlus dialer) | `com.oplus.dialer` | ColorOS | 10,721,110 | 4.00 | 64k | 2026-10-20 | – | 2 |
| Call Recorder | `com.lma.callrecorder` | Lucky Mobile Apps | 8,080,984 | 3.31 | 215k | 2026-04-13 | ads, IAP | 18 |
| Talker ACR | `com.talker.acr` | Joicoo | 7,671,447 | 3.88 | 42k | 2026-10-05 | ads, IAP | 18 |
| **ACR Phone** | `com.nll.cb` | NLL APPS | 6,485,339 | 4.16 | 58k | 2026-10-01 | ads, IAP | 5 |
| Call Recorder – SKVALEX | `org.skvalex.cr` | SKVALEX | 860,288 | 3.12 | 4.9k | 2023-11-03 (Play) | paid unlock | – |

Notes: Appliqato's Call Recorder (`com.appstar.callrecorder`) is **gone from Play (404)**. Boldbeast
only has a VoIP recorder listed (`com.boldbeast.voiprecorder`, almost no recent reviews). Skvalex's
Play listing is stale since 2023; its live builds are off-store. Voice-memo apps (Samsung Voice
Recorder, Easy Voice Recorder, …) rank for "call recording" but are not call recorders.

**Who leads by store data:** Cube ACR is the only third-party recorder that ranks in all 18 searches
*and* is actively updated with a 4+ rating at scale. The higher-install "Call Recorder Automatic"
apps are stale (2022–2024) and rated 2.4–3.9. ACR Phone is far smaller but active. For most users
the "recorder" is the OEM dialer (Google Phone, Samsung, ODialer).

## 2. Review themes since 2026-06-01 (Android 17 window)

### 2a. Explicit Android 17 mentions (all 10 reviews found, any date)

| App | Date | ★ | What they said (paraphrase / short quote) |
|---|---|---|---|
| Cube ACR | 2026-10-06 | 1 | (RU) "Updated to Android 17, the app stopped launching. Says a program update is required." Build 2.3.196, an old one. |
| Shizuku | 2026-09-28 | 1 | (RU) "Doesn't work on Android 17." |
| Shizuku | 2026-09-06 | 1 | (RU) "Android 17: crashes however many times you start the service." |
| Shizuku | 2026-08-24 | 3 | "please update for android 17" |
| Talker ACR | 2026-06-24 | 5 | "Works OK on Pixel 9a/Android 17. High quality recording." |
| Truecaller | 2026-10-19 | 1 | "app not working in galaxy A57, android 17" (not recording-specific) |
| Google Phone | 2026-10-11 | 1 | vibrate-on-answer broken after "updating the app and Android 17" (not recording) |
| Cube ACR | 2026-09-04, 2026-03-09 | 1 | Samsung **Galaxy A17** (device name, Android 16), false positives for "A17" |

So **direct** "Android 17 broke my recorder" evidence in the Play Store is thin: one Cube report (old build) and three Shizuku reports.

### 2b. Theme counts, Cube ACR (n = 2,896 reviews since 2026-06-01; 884 are ≤2★)

Counted only when the review also contains a failure word. "≤2★" in brackets.

| Theme | Count (≤2★) |
|---|---:|
| Records **only my side / other party silent** | **168 (107)** |
| Helper app / App Connector trouble | 109 (70) |
| WhatsApp / VoIP calls not recorded both sides | 56 (30) |
| **Wi-Fi / Wireless debugging / ADB dependency** | **38 (26)** |
| Bluetooth / headset → other side lost | 29 (11) |
| Pairing resets / keeps asking again | 22 (15) |
| Silent or empty files | 12 (10) |
| Accessibility | 7 (4) |
| "Stopped after (Android) update" | 6 (3) |

**Most important finding for CallVault: Cube ACR shipped an ADB / Wireless-debugging mode
("ADB Call Line") around July 2026**, the same transport family CallVault uses. Users' complaints
about it are almost exactly CallVault's known transport problems:

- "Recent update changes the backend functionality to ADB, ONLY works over wifi now. useless." (1★, 2026-07-04)
- "Now it just keeps asking for wireless debugging to be turned on every five minutes." (1★, 2026-07-23)
- "Wireless Debugging itself is being switched OFF approximately every 15 minutes. The Helper remains paired, battery is Unrestricted, Disable ADB Authorization Timeout is enabled…" (Note20 Ultra, Android 13; 1★, 2026-07-29)
- "as soon as I leave wifi, it will only record my side of the conversation. I am a lead generation specialist, so I move around a lot." (1★, 2026-07-22)
- "with ADB Call Line enabled it records both sides of WhatsApp calls clearly. However, ADB Call Line appears to depend on Wi-Fi/Wireless Debugging. When Wi-Fi disconnects…" (Z Fold 6, Android 16; 1★, 2026-09-04)
- "I have to reauthorize on wifi to get it to work for like an hour… I use it for work and don't have wifi." (2★, 2026-10-14, "new android update")
- "Please let the developer create an option to leverage shizuku, instead of the wireless debug method used by ACR helper which is not reliable." (4★, 2026-10-16)
- (RU) "recording of the other side works only when Wi-Fi is connected" (2026-09-18); (PT) "doesn't record if not on Wi-Fi… keeps asking for permission all the time" (2026-09-10)
- Developer reply pattern: "you can disable ADB, but then… the interlocutor will not be recorded if you use a headset."

Devices named in recent Cube one-side complaints: Galaxy S24/S24+/S25 FE/S26/S26 Ultra/A54/A56/A37, Pixel 8a, Pixel 10 Pro XL ("an Android update… caused the other caller's voice to be drastically downgraded", 2026-09-22), Moto (g86, g14, Edge 60 Neo, Razr 2024), Poco F8 Pro Max, Xiaomi 13 Pro, Xiaomi 17 Ultra ("Why is it not working"), Honor 600. Positive: "Works fine on my Pixel 11 Pro" (2026-10-06).

### 2c. ACR Phone (NLL) (n = 339 since June; 95 ≤2★)

- Silent files / "saves audio files but no voice in it": 7 (6). One-side only: 9 (6).
- Shizuku mentioned 7×: "it can't work with Shizuku which it keeps crashing all the time" (2★, 2026-10-06); "Shizuku always shows it's running but [APH] say it's not running… after few hours it's always same" (1★, 2026-10-02); "The app guide says the shizuku method is an alternative to accessibility permissions, but VoIP…" (4★, 2026-10-10).
- Accessibility: "Without root, accessibility permissions (APH) keep getting killed by the OS, leaving silent audio. The 'other' options require risky USB debugging…" (1★, 2026-08-22). "Accessibility service is required for call recording but we are unable to use Accessibility service permission on…" (2026-10-05).
- "Didn't work recording on Oxygen os 16" (2026-08-10). Galaxy "26 Note Ultra": not recording (resolved by premium).

### 2d. Shizuku (n = 2,184 since June; 426 ≤2★)

38 failing reviews about Wi-Fi/wireless debugging/pairing (21 ≤2★). Themes: pairing code never
arrives / notification without an input field; "pairs but then when I tap start, it tells me to
pair again… infinite loop" (2026-10-08); "please consider enabling it through mobile data I can't
access WiFi" (2026-10-14); the 13.6.0 update broke pairing for some ("The latest official update
broke Shizuku. USB debugging keeps enabling itself, and… app pairing no longer works", 2026-09-12);
HyperOS: "Newest update doesn't work with Poco X7 Pro with HyperOS" (DE, 2026-09-08). Plus the three
explicit Android 17 reviews above.

### 2e. OEM dialers (recording-related reviews only)

- **Google Phone** (97 recording-related since June): dominant complaint is the **mandatory
  announcement** ("call recording announcement stop please", "Please give a better call recorder
  without warning note"), 10; and "recording option missing / not available" on non-Pixel phones
  (Boltt, Nubia, Realme, Lenovo) and in Russia ("Unable to record calls in Russia… allowed by law").
  No review ties a Pixel recording failure to Android 17.
- **ODialer (OnePlus/OPPO)** (99 recording-related): "recording option has been removed due to the
  update" (2026-09-24), "call recording feature got removed" (2026-09-24), "Call Recording option is
  not available… Oneplus 13 global… in India" (2026-10-09), "OnePlus 12 android 16… more than 8 to 9
  months". This predates ColorOS 17 for most writers (region / version gating, not Android 17).
- Truecaller / CallApp: recording asks are minor (34 / 67 reviews), no Android 17 pattern.

## 3. Web, forums and GitHub (dated)

### Platform changes that hit no-root recorders on Android 17

1. **`Settings.Global.ADB_ENABLED` (and per a comment `DEVELOPMENT_SETTINGS_ENABLED`) now always
   read 0 for third-party apps.** The developer reference says: "This will always return 0 for all
   third-party apps" (quoted in thedjchi/Shizuku #301, 2026-09-17). It hit Pixels with the
   **2026-09-26 update, build CP3A.260905.009**: Shizuku 13.6.0/13.7.0 then claims "USB debugging is
   not enabled" while it is on, and loops. Reports: Pixel 6a, 7, 8 Pro, 8a, 9 Pro, 10, 10 Pro.
   - https://github.com/thedjchi/Shizuku/issues/301 (2026-09-16, 20 comments)
   - https://github.com/RikkaApps/Shizuku/issues/2599 (2026-10-14)
   - https://github.com/thedjchi/Shizuku/issues/310 (2026-10-05)
   - Patch fork: https://github.com/pengyuw96/shizuku-android17-patch-notes (Pixel 10 Pro, CP3A.260905.009); others: RazGame v13.7.0.r1366-razgame-beta, rushiranpise/Shizuku-Next, d4rken's Porter, johnsontsai11 fork.
   - Matches CallVault memory note "Android 17 hides the debugging settings".
2. **Hidden-API break:** `IPackageManager.getInstalledPackages` changed return type
   (`ParceledListSlice` → `PackageInfoList`), so official Shizuku 13.6.0 shows an empty
   authorised-app list on Android 17 from day one (2026-06-17). Apps already granted kept
   working. "I really only need Shizuku for Call Recorder by Skvalex. And while Shizuku does show 0
   apps, Call Recorder works and records the other party's audio." (2026-06-26).
   - https://github.com/RikkaApps/Shizuku/issues/2180 (33 comments: Pixel 6/6a/7/8/8 Pro/9/9 Pro/10/10 Pro/10 Pro XL)
   - https://github.com/rikkaapps/shizuku/issues/2383 (Xiaomi 17 Pro Max, S26 Ultra; 2026-08-15)
   - https://raw.githubusercontent.com/kerneldroid/Nightzuku/master/docs/android-17-compatibility.md
   - Forks that work: Shevery, Nightzuku, Shizuku+, thedjchi (per a 2026-07-22 cross-test).
3. **Local Network Protection:** wireless-debugging pairing/mDNS discovery needs
   `ACCESS_LOCAL_NETWORK` ("Nearby devices") on Android 17; a new `USE_LOOPBACK_INTERFACE`
   permission also exists (thedjchi #172, 2026-03-16). Mandatory for apps targeting SDK 37; legacy
   targets get an implicit grant "for now". Symptom when missing on the normal start path:
   a repeated system "Choose a device to connect" picker (S24 Ultra, Android 17, 2026-09-16).
   - https://github.com/thedjchi/Shizuku/issues/302 · https://github.com/thedjchi/Shizuku/issues/172
   - https://developer.android.com/privacy-and-security/local-network-permission
4. **ADB Wi-Fi 2.0** (Android Developers Blog, 2026-09-09): needs Android 17 + platform-tools
   37.0.0; new mDNS stack; "the daemon automatically turns off ADB Wi-Fi when it detects an
   untrusted network and re-enables itself once running on a user-allowed network." Relevant to
   any app that keeps wireless debugging on for capture.
   - https://android-developers.googleblog.com/2026/09/wireless-debugging-adb-wifi-2.html
5. **Proposal (not shipped) to stop adbd listening on loopback.** After CVE-2026-0073 (wireless ADB
   pairing bypass) an ADB maintainer asked on the Issue Tracker: "What about we restrict to always
   only binding to wifi interface `wlan0`?" and noted localhost "has also been the source of exploit".
   That would break on-device ADB (Shizuku, libadb, CallVault). A second tracker bug (541312863)
   concerns legacy **TCP/IP mode staying active without Wi-Fi**, which is exactly CallVault's
   off-Wi-Fi loopback path. Status as of 2026-08-04: discussion only, no commit found.
   - https://kitsumed.github.io/blog/posts/android-may-soon-restrict-on-device-adb/ (2026-07-20, edited 2026-08-04; tracker 526109803, 541312863)
   - https://gigazine.net/gsc_news/en/20260729-adb-may-restrict/ · https://securityonline.info/android-local-adb-restriction/
6. **Advanced Protection + Accessibility** (Android 17 Beta 2 onward): with Advanced Protection on,
   only apps with `isAccessibilityTool="true"` may hold an AccessibilityService; existing grants are
   revoked. Opt-in mode, so it hits few users, but it kills Accessibility-based helpers (Cube Helper,
   ACR Phone Helper) for those who enable it. No user report naming a recorder found.
   - https://securityaffairs.com/189497/security/advanced-protection-mode-in-android-17-prevents-apps-from-misusing-accessibility-services.html (2026-03)

### Recorder-specific web findings

- **ShizuCallRecorder** (FOSS, Shizuku + scrcpy-server): advertised for Android 11–16+. On
  Android 17 there's an AppLogger crash on Pixel 8 Pro (#102, 2026-08-09, fixed). #110 (2026-08-17):
  recording silently stops while shizuku_server looks alive and never recovers. #101: TCL phone,
  Android never signalled the call start. https://github.com/kitsumed/ShizuCallRecorder/issues
- **BCR (root)**: no recording breakage on Android 17; one report about viewing files after the
  LineageOS 24.0 (Android 17) upgrade (#964, closed 2026-10-14).
- **scrcpy**: "Heavy crashes on Google Pixel devices with last OS ver.(17)" (#6991, 2026-08-16),
  "Android 17 App launching issue" (#7048). Nothing on voice-call audio capture.
- **Pixel**: biggest Android 17 call bug is calls not ringing / logged as missed (from 2026-06-16;
  Pixel 6a–10 Pro XL; workaround: disable Scam Detection / Call Screen). Not recording.
  https://meteoraweb.com/en/news/google-pixel-owners-miss-calls-as-phone-fails-to-ring-on-android-17 ·
  https://www.techadvisor.com/article/3182797/google-pixel-missed-call-and-sound-popping-bugs-reported.html
  Notebookcheck reported mic/sound glitches on Android 17 QPR2 Beta 1 (page 403; snippet only).
  Pixel's native Call Recording went worldwide in Feb 2026 (not Android-17-specific) with a spoken
  disclosure. https://9to5google.com/2026/02/19/pixel-call-recording-international/
- **Samsung One UI 9**: users report the native recorder now **announces "This call is being
  recorded"** to the other party (seen first on Fold 8, Aug 2026; region-dependent per forum users).
  Samsung Members threads (403 to fetch): "Call Recording On One UI 9", "Does One UI 9 Introduce a
  Mandatory Call Recording Alert…". https://x.com/ThePhoneFuel/status/2085237743312105531 . One UI 9
  also merges WhatsApp/Meet calls into the dialer log (Android 17 API).
  Cube on One UI 9 beta: worked, but had to re-grant permission after upgrade
  (https://eu.community.samsung.com/t5/question/cube-acr-call-recorder/td-p/8314008).
- **OnePlus / ColorOS 17**: no recording-specific reports found; OnePlus moves to ColorOS on
  Android 17 (https://9to5google.com/2026/10/13/oneplus-android-17-list-schedule/).
- **Xiaomi / HyperOS on Android 17**: no recorder-specific web report found beyond the Shizuku
  issue (Xiaomi 17 Pro Max) and one Cube review (Xiaomi 17 Ultra).
- **Non-English**: Russian search (4PDA/Habr/Trashbox) found no Android 17 call-recording thread;
  Russian Play reviews supplied the Shizuku/Cube complaints above. Hebrew search found only generic
  Android 17 feature articles.

## 4. Reddit (arctic-shift archive)

**Coverage:** posts harvested and grepped locally. r/GooglePixel: 13,878 posts (2026-04-23 →
today). 130 days for r/Android (5,113), r/oneplus (3,683), r/androiddev (3,506), r/Xiaomi
(2,245), r/samsung (2,163), r/LineageOS (1,137), r/Magisk (860). Comment trees read for 5 key posts.
**Gaps:** r/shizuku returned 0 posts from the archive. A keyword search across
oneplus/Xiaomi/… and a comment-body search were refused (HTTP 422 after 12 retries) before I
switched to harvest. r/PixelBetaProgram, r/AndroidBeta, r/GalaxyS25, r/OnePlus13, r/PocoPhones,
r/callrecording and r/AndroidQuestions were **not covered** because of time and archive slowness.

**Headline: Reddit has almost no "Android 17 broke my call recorder" posts.** Recording talk is
about availability (OEM region gating, the announcement) and about the platform's ADB direction.

| Date | Sub | Post (score / comments) | Gist |
|---|---|---|---|
| 2026-07-2x | r/Android | "Android May Soon Restrict On-Device ADB, Affecting Shizuku, libadb…" (869 / 354) | Big backlash. Top comments: "Whats the point of android anymore" (738), "Google restricting sideloading means that they aren't going to let you use adb, either" (201). Link: https://www.reddit.com/r/Android/comments/1v5jxaz/ |
| 2026-09-10 | r/Android | "Introducing… ADB Wi-Fi 2.0" (277 / 34) | "They are going to make shizuku setup 10 times harder, aren't they?" (33); "Ah that's why wifi debugging is broken for me" (22); "google has neutered `adb shell pm uninstall -k --user 0` in android 17" (3). https://www.reddit.com/r/Android/comments/1wbqa0f/ |
| 2026-09-15 | r/Android | "[TIP] Android 17 QPR1 – How to Start Shizuku" | removed by mods; body not available. https://www.reddit.com/r/Android/comments/1wh9n0g/ |
| 2026-09-26 | r/GooglePixel | "ADB not working after recent update" (Pixel 7 Pro) | Same day as the Sept Pixel build that broke Shizuku. Replies: update platform-tools. https://www.reddit.com/r/GooglePixel/comments/1wqpl6t/ |
| 2026-09-26 | r/GooglePixel | "Wi-Fi and Bluetooth are turning off" (Pixel 8 Pro, CP3A.260905.009) | Matters for any Wi-Fi-debugging capture path. https://www.reddit.com/r/GooglePixel/comments/1wr54d1/ |
| 2026-08-24 | r/GooglePixel | "Still dealing with broken Wi-Fi on Android 17" (Pixel 10 Pro Fold, 9 Pro XL; 17 comments) | Wi-Fi instability since day one of Android 17. https://www.reddit.com/r/GooglePixel/comments/1vwye22/ |
| 2026-06-22 / 07-29 | r/GooglePixel | "Android 17 microphone issue" (10 Pro XL: people can't hear me on calls); "Android 17 QPR2 Beta 1 broke my microphone access" | Mic-in-use bugs; would also hit recorders. https://www.reddit.com/r/GooglePixel/comments/1uc6xb2/ · https://www.reddit.com/r/GooglePixel/comments/1v9jdlx/ |
| 2026-10-03 | r/GooglePixel | "Pixel 8 on Android 17: LE Audio microphone reports stereo but returns duplicated mono, I found the AOSP cause" (31) | Relevant to CallVault's untested LE Audio capture. https://www.reddit.com/r/GooglePixel/comments/1wwd2ad/ |
| 2026-06-07 | r/GooglePixel | "Shizuku/Pixel IMS VoLTE fix won't stick anymore (Pixel 6a)" | Shizuku grants lasting ~1 h. https://www.reddit.com/r/GooglePixel/comments/1tz21qr/ |
| 2026-09-06 | r/GooglePixel | "Auto call recording without announcement in Google Pixel 10a" (×2) | Users want the Pixel recorder without the disclosure. |
| 2026-07-08 / 06-19 / 08-17 | r/GooglePixel | Long Call Notes audio greyed out (>45 min); recordings lost when they fall off the 500-call log; India Pixel 7 can't play recordings | Native-recorder reliability, not Android-17-specific. |
| 2026-10-02 | r/oneplus | "Call Recorder on Oneplus without root" (11 / 42) | **About CallVault** (OnePlus 12 EU ROM, ODialer didn't work). Replies push ShizuCallRecorder and the "set region to India for ODialer" trick, and ask "Is it safe? The permissions it requires are a lot!". https://www.reddit.com/r/oneplus/comments/1ww8ho2/ |
| 2026-09-07 / 07-10 / 07-18 | r/oneplus | "Call recording suddenly stopped. 'Feature not available in your location'" (Nord 2, India); "16.0.5 update… call recording feature has been removed" (Nord 5); "Call recording stopped working after updates" | OEM region gating via updates (OxygenOS 13/16), not Android 17. |
| 2026-09-08 | r/oneplus | "Installed the new OnePlus Stock Dialer via the USSD code method" (49 / 47) | Workaround to get the recording dialer on August-patch devices. https://www.reddit.com/r/oneplus/comments/1waxu2f/ |
| 2026-09-01 | r/LineageOS | "Basic Call Recorder doesn't work with Lineage?" | BCR: "recording was discarded because the audio was completely silent". https://www.reddit.com/r/LineageOS/comments/1w4cjv8/ |
| 2026-09-01 | r/androiddev | "capturing the other party's voice during a cellular call" (Z Fold5; 15 comments) | Replies: impossible without root; one points to ShizuCallRecorder; one to Cube's accessibility FAQ. |
| 2026-07-11 | r/Xiaomi | "Recording phone calls" (Redmi Note 14 Pro) | Can't find any way to record calls. |
| – | r/samsung | 1 relevant post in 2,163 ("Native call recording EU s25 vs a57", removed) | Nothing on One UI 9 recorder breakage in the archive window. |

## 5. What this means for CallVault (inferences, not measured)

- The ADB_ENABLED fake-0 (Sept 2026 Pixel build) is the most widely reported Android 17 break for
  shell-uid tools. CallVault already treats both settings as unreadable (memory note); worth a
  re-check that no path still gates on them.
- Cube ACR's new Wireless-debugging mode is producing exactly the complaints CallVault designed
  around (Wi-Fi only, debugging toggled off every 5–15 min, re-pair). Its users explicitly ask for
  Shizuku or a non-Wi-Fi option. That is a positioning opportunity, and also a warning that ADB
  Wi-Fi 2.0's "untrusted network → auto-off" may make it worse on Android 17.
- Watch two pending platform risks: `ACCESS_LOCAL_NETWORK` when CallVault targets SDK 37, and the
  loopback / legacy-tcpip restriction proposal (tracker 526109803 / 541312863), which would hit
  CallVault's off-Wi-Fi path directly.
- No report found of Android 17 breaking `AudioRecord VOICE_CALL` from shell uid or playback
  capture of WhatsApp. Absence of reports is not proof.
