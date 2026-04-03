# TBP Android — USB PD Bypass Monitor

A native Android app that monitors battery level and automatically toggles **USB PD battery bypass (pass-through) mode** via the **Shizuku API** — no Termux required.

This is the native successor to [tbp](https://github.com/ONDER1E/tbp), which accomplished the same goal using shell scripts in Termux. This version runs as a proper Android foreground service, integrates directly with Shizuku over IPC, and does not require a persistent wakelock to function.

---

## Why This Exists

The original shell-based tbp worked well, but had fundamental limitations:

- Required Termux and a manually acquired wakelock to stay alive
- The wakelock prevented the SoC from entering deep sleep states
- Battery reads relied on spawning `dumpsys` subprocesses every 5 seconds
- Notification actions required `termux-dialog` with fragile JSON parsing
- No boot autostart without Termux:Boot

This app replaces all of that with native Android equivalents.

---

## How It Works

Instead of polling every 5 seconds, the app registers for Android's `ACTION_BATTERY_CHANGED` broadcast. The CPU is not woken on a timer — it only wakes when the battery level actually changes, which on a plugged-in device with bypass active is infrequent.

1. Listens for `ACTION_BATTERY_CHANGED` broadcast
2. Compares battery level against configured thresholds
3. If a change is needed, calls `settings put system pass_through <0|1>` via Shizuku IPC
4. Updates the persistent foreground notification
5. Goes back to sleep — no polling, no wakelock held between events

---

## Features

- Event-driven battery monitoring — no continuous polling
- No persistent wakelock — SoC can enter deep sleep between events
- Shizuku API integration via direct IPC binder (no `rish` subprocess)
- Persistent foreground notification with Pause / Resume / Exit actions
- Hysteresis zone between thresholds to prevent rapid toggling
- Minimum cooldown between state changes
- Configurable thresholds via in-app settings
- Autostart on boot via `BOOT_COMPLETED` receiver
- Verbose logging

---

## Requirements

- Android 8.0+ (API 26)
- [Shizuku](https://shizuku.rikka.app/) — running and authorised
- USB-PD charger (PPS recommended)
- A device that supports bypass charging via `settings put system pass_through`

> See the [original tbp repo](https://github.com/ONDER1E/tbp) for the full device compatibility list.

---

## Installation

> APK releases coming soon. For now, build from source.
```bash
git clone https://github.com/ONDER1E/tbp-android.git
```

Open in Android Studio, build, and sideload the APK onto your device.

Grant Shizuku permission when prompted on first launch.

---

## Device Compatibility

This app uses the same `pass_through` system setting as the original shell scripts. Compatibility is identical — see the [tbp README](https://github.com/ONDER1E/tbp#device-compatibility) for the full list of supported device families (Samsung, ASUS ROG, Sony Xperia, RedMagic, Black Shark).

---

## Differences from tbp (Shell Version)

| Feature | tbp (shell) | tbp-android (this) |
|---|---|---|
| Runtime | Termux | Native Android |
| Battery reading | `dumpsys` via rish | `BatteryManager` API |
| Shizuku comms | `rish` subprocess | Direct IPC binder |
| Monitoring method | 5s polling loop | `ACTION_BATTERY_CHANGED` event |
| Persistent wakelock | ✅ Required | ❌ Not needed |
| Boot autostart | Termux:Boot | `BOOT_COMPLETED` receiver |
| Notification actions | `termux-dialog` | Native `PendingIntent` |
| Hysteresis | ✅ | ✅ |
| Cooldown timer | ✅ | ✅ |

---

## Safety Disclaimer

This app modifies system settings via Shizuku.
Use at your own risk.

Improper charger setups or unsupported devices may cause unexpected behaviour.

---

## Licence

See [LICENCE](./LICENCE).