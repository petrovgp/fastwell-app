#!/usr/bin/env bash
# Builds a signed release APK without Gradle:
#   aapt (resources, linked against the API 23 framework resources)
#   javac (Java 8 bytecode, compiled against the API 36 android.jar)
#   dx -> classes.dex, zipalign, apksigner (v2 + v3 signatures).
# System packages needed: a JDK, aapt, zipalign, apksigner. dx and the API 23
# framework resources are bundled in tools/, so no distro Android SDK is needed.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR_36="${ANDROID_JAR_36:-sdk/android-36.jar}"
ANDROID_JAR_RES="${ANDROID_JAR_RES:-tools/framework-res-23.jar}"
DX_JAR="${DX_JAR:-tools/dx.jar}"
KEYSTORE="${KEYSTORE:-keystore/fastwell.jks}"
KS_PASS="${KS_PASS:-fastwell}"
ANDROID_JAR_36_URL="https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-36/android.jar"

missing=""
for t in java javac keytool aapt zipalign apksigner; do
  command -v "$t" >/dev/null 2>&1 || missing="$missing $t"
done
if [ -n "$missing" ]; then
  echo "Missing tools:$missing"
  echo "Install with: sudo apt install default-jdk-headless aapt zipalign apksigner"
  exit 1
fi
if [ ! -f "$ANDROID_JAR_36" ]; then
  echo "Downloading API 36 android.jar (~53 MB) to $ANDROID_JAR_36"
  mkdir -p "$(dirname "$ANDROID_JAR_36")"
  curl -fL --retry 2 -o "$ANDROID_JAR_36.part" "$ANDROID_JAR_36_URL"
  mv "$ANDROID_JAR_36.part" "$ANDROID_JAR_36"
fi

rm -rf build && mkdir -p build/gen build/classes out

echo "1/5 resources"
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$ANDROID_JAR_RES"

echo "2/5 compile"
javac -nowarn -Xlint:none -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR_36" -d build/classes \
  build/gen/app/fastwell/R.java src/app/fastwell/*.java 2>&1 | grep -v -E "^warning: \[options\]|^Picked up|^[0-9]+ warnings?$|^Note: " || true
test -f build/classes/app/fastwell/MainActivity.class

echo "3/5 dex"
java -jar "$DX_JAR" --dex --min-sdk-version=26 --output=build/classes.dex build/classes

echo "4/5 package"
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR_RES" -F build/unaligned.apk
(cd build && aapt add -f unaligned.apk classes.dex >/dev/null)
zipalign -f -p 4 build/unaligned.apk build/aligned.apk

echo "5/5 sign"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias fastwell \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fastwell, O=Fastwell" 2>/dev/null
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias fastwell \
  --min-sdk-version 26 --out out/Fastwell-1.0.0.apk build/aligned.apk
apksigner verify out/Fastwell-1.0.0.apk
echo "OK -> out/Fastwell-1.0.0.apk ($(du -h out/Fastwell-1.0.0.apk | cut -f1))"
