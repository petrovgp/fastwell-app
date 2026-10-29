#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
rm -rf build/test && mkdir -p build/test
javac -nowarn -d build/test src/app/fastwell/FastingLogic.java tests/FastingLogicTest.java
java -cp build/test app.fastwell.FastingLogicTest
