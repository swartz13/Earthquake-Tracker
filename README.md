# ⚡ Earthquake Tracker (Deprem Takip)

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-blue.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

An ultra-low latency, real-time earthquake monitoring and emergency alerting Android application. It monitors seismic events around your location or chosen reference point simultaneously from **EMSC** (live global WebSocket), **AFAD** (Disaster and Emergency Management Authority of Turkey), and **Kandilli Observatory (KOERI)**.

The app is engineered with a **zero-wait alert policy**: it immediately alerts upon the *first* agency's report without waiting for consensus, and seamlessly updates notifications in real-time as other agencies publish their verified solutions.

---

## 📱 Screenshots

| 1. Live Feed & Status | 2. Multi-Agency Solutions |
| :---: | :---: |
| <img src="docs/screenshots/01_main_screen.png" width="320" alt="Live Feed" /> | <img src="docs/screenshots/02_card_detail.png" width="320" alt="Card Details" /> |
| Real-time WebSocket connection status, multi-agency confirmation dots, relative distances, and on-device settlement calculations. | Expanded card showing agency solution breakdown (AFAD, KOERI, EMSC), arrival latency, nearest major city, and estimated shaking intensity. |

| 3. Scope & Alarm Settings | 4. Sources & Diagnostics |
| :---: | :---: |
| <img src="docs/screenshots/03_settings_top.png" width="320" alt="Settings Scope" /> | <img src="docs/screenshots/04_settings_sources.png" width="320" alt="Settings Sources" /> |
| Language switcher (English / Türkçe), customizable monitoring radius, notification threshold, and loud emergency alarm settings. | Real-time GPS/fused location toggle, independent source switches (EMSC, AFAD, KOERI), and instant alarm sound test button. |

---

## ✨ Key Features

- **⚡ Zero-Delay Notification Engine**: Alerts on the very first incoming report. Subsequent agency solutions update the notification *silently* without ringing your phone twice, and populate confirmation badges (`AFAD ✓ · KOERI ✓ · EMSC ✓`).
- **🛡️ Smart Event De-duplication (`QuakeStore`)**: 
  - Dynamic **30-second time window** between incoming agency solutions.
  - Magnitude-adaptive distance tolerance ($M2 \rightarrow 45\text{ km}$, $M6 \rightarrow 105\text{ km}$) merges solutions for the same event while cleanly distinguishing real aftershock sequences.
- **📍 100% Offline Settlement & Bearing Database**:
  - Offline database of over **15,000 settlements** (`assets/yerlesimler.tsv`).
  - Computes exact relative bearing and distance on-device (e.g., *"13 km south of Düvertepe"* / *"Düvertepe'nin 13 km güneyinde"*) with zero reliance on network geocoding APIs.
- **🚨 Piercing Emergency Alarm**:
  - Configurable high-priority alarm channel that bypasses Do Not Disturb (DND) / silent mode for strong nearby earthquakes ($M \ge \text{threshold}$, within alarm radius).
- **🔋 Unrestricted Foreground Monitoring**:
  - Android 14/15 compliant foreground service with wake lock, `BOOT_COMPLETED` restart, and WorkManager watchdog recovery.
- **🌐 Full Bilingual Support**:
  - Complete, seamless in-app localization for both **English 🇬🇧** and **Turkish 🇹🇷**.

---

## 🚀 How Data Sources Work

| Channel | Method | Typical Latency | Notes |
|---|---|---|---|
| **EMSC** | Persistent **WebSocket** (`wss://seismicportal.eu/standing_order/websocket`) | Instant (sub-second) | Global stream filtered locally by radius |
| **AFAD** | HTTP REST Polling (every 15s) | $\le 15\text{ s} + \text{network}$ | Turkey national seismic network |
| **Kandilli (KOERI)** | HTTP Mirror Polling (every 25s) | $\le 25\text{ s} + \text{network}$ | Boğaziçi University Regional Earthquake-Tsunami Center |
| **EMSC Fallback** | HTTP FDSN Polling (every 90s) | Backup | Fills gaps if WebSocket disconnects |

---

## 📥 Step-by-Step Installation Guide

### Option 1: Direct APK Installation (Recommended for most users)

1. **Download APK**:
   - Go to the [Releases](https://github.com/swartz13/Earthquake-Tracker/releases) tab on this repository.
   - Download the latest signed release APK (`DepremTakip-vX.X.apk`).
2. **Allow Installation of Unknown Apps**:
   - When prompted by Android, open your browser or file manager settings and enable **"Allow from this source"**.
   - Tap **Install**.
3. **Grant Required Permissions**:
   - **Notifications**: Required to dispatch timely earthquake alerts and foreground service status.
   - **Location**: Required if you choose "Use device location" in Settings to automatically compute distance to epicenters wherever you travel.
4. **⚠️ CRUCIAL: Configure Battery Optimization**:
   Android battery managers can terminate background network connections if not configured:
   - **Xiaomi / Redmi / POCO (MIUI / HyperOS)**:
     - Long press app icon $\rightarrow$ **App info** $\rightarrow$ **Battery saver** $\rightarrow$ Select **No restrictions**.
     - Enable **Autostart**.
   - **Samsung (OneUI)**:
     - Settings $\rightarrow$ Apps $\rightarrow$ Earthquake Tracker $\rightarrow$ **Battery** $\rightarrow$ Select **Unrestricted**.
     - Ensure the app is NOT in "Deep sleeping apps".
   - **OPPO / Realme / OnePlus (ColorOS / OxygenOS)**:
     - Long press app icon $\rightarrow$ **App info** $\rightarrow$ **Battery usage** $\rightarrow$ Enable **Allow background activity** and **Allow auto-launch**.
   - **Huawei / Honor (EMUI / MagicOS)**:
     - Settings $\rightarrow$ Apps $\rightarrow$ App launch $\rightarrow$ Set to **Manage manually** and enable all three switches (*Auto-launch*, *Secondary launch*, *Run in background*).
5. **Verify Audio Alarm**:
   - Open **Settings** (gear icon at top right) $\rightarrow$ Tap **Test alarm sound** at the bottom to verify the emergency alarm speaker output.

---

### Option 2: Building from Source (For Developers)

#### Prerequisites
- JDK 17 or higher
- Android SDK with Platform 35 and Build-Tools 35.0.0
- Git

#### Build & Run Commands

```bash
# 1. Clone repository
git clone https://github.com/swartz13/Earthquake-Tracker.git
cd Earthquake-Tracker

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Install on a connected Android phone (via USB/ADB)
./gradlew installDebug

# 4. Run Unit Tests (27 tests verifying parsing, clustering, bearings, and localization)
./gradlew test
```

> **Note on Signing**: 
> Release builds read signing credentials from a local `keystore.properties` file. If not present, release builds default to unsigned output without breaking compilation.

---

## ⚙️ Settings Reference

| Setting | Default | Description |
|---|---|---|
| **Language** | English / Türkçe | Instantly updates all labels, bearings, and expanded notifications. |
| **Monitoring Radius** | 300 km | Discards earthquakes outside this distance. |
| **Alert Threshold** | M3.5 | Minimum magnitude required to trigger a push notification. |
| **Listing Threshold** | M1.7 | Filters the main feed list without altering alert notifications. |
| **Emergency Alarm** | M3.5 · 250 km | Loud bypass alarm for high-intensity nearby events. |
| **Location** | Device Location | Uses real-time GPS/network location with offline caching and multi-provider fallback. |
| **Data Sources** | EMSC, AFAD, Kandilli | Independent toggle switches for each seismic network. |

---

## 🏛️ Architecture

```
app/src/main/java/com/berk/deprem/
├── model/
│   └── Quake.kt                 # Report (single source solution) & Quake (fused event)
├── core/
│   ├── AlertPolicy.kt           # Alert classification (notification vs alarm vs silent update)
│   ├── Geo.kt                   # Great-circle distance, forward azimuth, MMI intensity estimation
│   ├── Places.kt                # 15,342 settlement spatial index & localized headline generator
│   ├── Prefs.kt                 # Persistent user preferences & state
│   ├── QuakeStore.kt            # Multi-agency cluster matching & deduplication engine
│   └── Repo.kt                  # Central repository coordinating storage, network & status
├── data/
│   ├── EmscWebSocket.kt         # Resilient WebSocket client with exponential backoff
│   ├── Sources.kt               # AFAD, Kandilli & EMSC HTTP parsers
│   └── Time.kt                  # ISO 8601, UTC & epoch conversions
├── service/
│   ├── LocationFinder.kt        # Multi-provider (Fused + GPS + Network + Passive) location resolver
│   ├── Notifier.kt              # Android notification channels, audio attributes & layout
│   ├── QuakeMonitorService.kt   # Persistent foreground service managing polling and socket loops
│   ├── BootReceiver.kt          # Restarts service after device reboot
│   └── Watchdog.kt              # Periodic WorkManager watchdog to guarantee service recovery
└── ui/
    ├── MainActivity.kt          # Compose host activity & foreground location hook
    ├── QuakeScreen.kt           # Reactive Compose feed, cards, chips & diagnostics dialog
    ├── SettingsSheet.kt         # Comprehensive settings bottom sheet modal
    └── Strings.kt               # Type-safe AppStrings interface (English & Turkish)
```

---

## 🔒 Privacy & Battery Commitment

- **Zero Analytics & Tracking**: No telemetry, no ads, no external user tracking.
- **Local Location Processing**: Your GPS coordinates are processed exclusively on your device to calculate relative distances to epicenters. Your coordinates are **never transmitted** to any third-party server.
- **Low Power Profile**: High-efficiency WebSocket push minimizes continuous cellular radio wakeup compared to aggressive polling.

---

## ⚠️ Disclaimer

This application **does not predict earthquakes**. It monitors and delivers real-time notifications for earthquakes that have already occurred and been recorded by official seismological observatories.

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
