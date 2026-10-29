# fastwell-app
Fastwell Android application for tracking daily fitness activities


Intermittent fasting timer + calorie journal + workout journal for Android 8.0+ (minSdk 26), targeting Android 16 (targetSdk 36).

## Install
Copy `Fastwell-1.0.0.apk` to the phone, open it, and allow "Install unknown apps" for the app you opened it from.

## Build
Works on Debian 12/13, Ubuntu 22.04+ and WSL. Install a JDK and three Android tools:

    sudo apt install default-jdk-headless aapt zipalign apksigner

Then:

    ./build.sh          # -> out/Fastwell-1.0.0.apk
    tests/run.sh        # JVM tests for the fasting/calorie/streak logic

`dx` and the API 23 framework resources are bundled in `tools/` (see `tools/README.md`), so no distro Android SDK packages are needed. On the first run `build.sh` downloads the API 36 `android.jar` (~53 MB, from https://github.com/Reginer/aosp-android-jar) to `sdk/android-36.jar`.

The signing key `keystore/fastwell.jks` is deliberately not in the repo. Put your copy there before building: Android only installs an update over the app if it is signed with the same key. Without it, `build.sh` creates a new key, and that build won't install over existing copies.

## Code map
- `MainActivity` – shell, bottom nav, edge-to-edge insets, predictive back (Android 16), snackbar, backup import/export
- `Db` – SQLite schema and queries (fasts, food entries, food cache, exercises, workouts, sets)
- `FastingLogic` – plans, stages, calorie formula, streaks (pure Java, unit-tested)
- `Notifier`, `FastReceiver`, `BootReceiver` – live timer notification, goal alarm, "End fast" action, reboot restore
- `Off` – Open Food Facts search and barcode lookup
- Screens: `Onboarding`, `Today`, `Fast`, `FastHistory`, `Food`, `Workouts`, `WorkoutEdit`, `Progress`, `Settings`
