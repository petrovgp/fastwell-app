# fastwell-app
Fastwell Android application for tracking daily fitness activities


Intermittent fasting timer + calorie journal + workout journal for Android 8.0+ (minSdk 26), targeting Android 16 (targetSdk 36).

## Install
Copy `Fastwell-1.0.0.apk` to the phone, open it, and allow "Install unknown apps" for the app you opened it from.

## Build
Needs a JDK, `aapt`, `dalvik-exchange` (dx), `zipalign`, `apksigner`, the API 23 platform (Debian/Ubuntu: `apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23`) and an API 36 `android.jar` at `sdk/android-36.jar`.

    ./build.sh          # -> out/Fastwell-1.0.0.apk
    tests/run.sh        # JVM tests for the fasting/calorie/streak logic

`sdk/android-36.jar` is not in the repo (53 MB). Get it from https://github.com/Reginer/aosp-android-jar (`android-36/android.jar`) and place it at `sdk/android-36.jar`.

The signing key `keystore/fastwell.jks` is deliberately not in the repo. Keep your own copy safe: Android only installs an update over the app if it is signed with the same key. Without it, `build.sh` creates a new key, and that build won't install over existing copies.

## Code map
- `MainActivity` – shell, bottom nav, edge-to-edge insets, predictive back (Android 16), snackbar, backup import/export
- `Db` – SQLite schema and queries (fasts, food entries, food cache, exercises, workouts, sets)
- `FastingLogic` – plans, stages, calorie formula, streaks (pure Java, unit-tested)
- `Notifier`, `FastReceiver`, `BootReceiver` – live timer notification, goal alarm, "End fast" action, reboot restore
- `Off` – Open Food Facts search and barcode lookup
- Screens: `Onboarding`, `Today`, `Fast`, `FastHistory`, `Food`, `Workouts`, `WorkoutEdit`, `Progress`, `Settings`
