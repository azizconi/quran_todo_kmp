#!/bin/sh
# Captures the eight screens in every language (about 12 minutes per language on a loaded machine).
# Usage: OUT=/some/dir ./run_all.sh [lang ...]     (default: ru en tg uz tr)
# Needs: emulator DoruIman_API_35 running, debug APK installed (./gradlew :composeApp:installDebug), python3, sqlite3.
cd "$(dirname "$0")" || exit 1
LANGS=${*:-ru en tg uz tr}
./demo_mode.sh
for L in $LANGS; do
  echo "=== $L"
  python3 capture.py "$L" || echo "FAILED $L"
done
./demo_mode.sh off
