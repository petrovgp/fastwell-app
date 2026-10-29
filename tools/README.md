# Bundled build tools

These let `build.sh` run on distributions that no longer package them (for example Debian 13).

| File | What it is | Taken from | License |
| --- | --- | --- | --- |
| `dx.jar` | Android dx 10.0.0: converts Java bytecode to `classes.dex` | Debian/Ubuntu package `dalvik-exchange` 10.0.0+r36 | Apache-2.0 |
| `framework-res-23.jar` | Only `resources.arsc` + `AndroidManifest.xml` of the Android 6.0 (API 23) framework; `aapt` links the app's resources against it | Debian/Ubuntu package `android-sdk-platform-23` | Apache-2.0 |

Copyright notices and the Apache-2.0 text are in `licenses/`.
