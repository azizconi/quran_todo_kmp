#!/bin/sh
# Captures the eight 2.0 screens (01_home .. 08_quiz) in every language: about 5 minutes per language on a loaded machine,
# plus 10 minutes for the one-time setup (fresh onboarding, demo backup restored for today, reader parked at Al-Mu'minun 18).
# Usage: OUT=/some/dir ./run_all.sh [lang ...]     (default: ru en tg uz tr; set SETUP=0 to skip the setup)
# Needs: emulator DoruIman_API_35 running with today's date, debug APK installed (./gradlew :composeApp:installDebug),
#        python3 with Pillow, sqlite3 is not needed any more.
cd "$(dirname "$0")" || exit 1
LANGS=${*:-ru en tg uz tr}
export TMP_DIR=${TMP_DIR:-/tmp/e3_capture}
mkdir -p "$TMP_DIR"
./demo_mode.sh
if [ "${SETUP:-1}" = "1" ]; then
  python3 make_demo_backup.py --out "$TMP_DIR/demo.json" || exit 1
  python3 -u capture.py ru setup || exit 1
fi
for L in $LANGS; do
  echo "=== $L"
  python3 -u capture.py "$L" || echo "FAILED $L"
done
./demo_mode.sh off
