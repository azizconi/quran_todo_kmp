#!/bin/zsh
# Raw App Store captures from ONE iPhone simulator (iPhone 17 Pro Max, 1320x2868), debug build, demo data of IosDebugSeed.
#   capture_ios.sh <udid> <bundle id> <raw out dir>          then:  python3 build_app_store.py all --raw <raw out dir>
# The 6.5" set is derived from the 6.9" frames by build_app_store.py (no second simulator).
# Debug launch arguments (QuranHifzApp.swift / Router.swift): -qhSeedDemo YES, -qhOpen quranhifz://..., -qhMushaf YES, -qhTajweed YES.
UD=$1; BID=$2; OUT=$3
xcrun simctl status_bar $UD override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
shoot() { # lang name url wait extra...
  local lang=$1 name=$2 url=$3 wait=$4; shift 4
  case $lang in ru) loc=ru_RU;; tg) loc=tg_TJ;; uz) loc=uz_UZ;; en) loc=en_US;; tr) loc=tr_TR;; esac
  mkdir -p $OUT/$lang
  xcrun simctl terminate $UD $BID >/dev/null 2>&1
  xcrun simctl launch $UD $BID -qhSeedDemo YES -AppleLanguages "($lang)" -AppleLocale $loc -qhOpen "$url" "$@" >/dev/null
  sleep $wait
  xcrun simctl io $UD screenshot $OUT/$lang/$name.png >/dev/null 2>&1
}
for l in ru tg uz en tr; do
  shoot $l 01_lesson quranhifz://home 8
  shoot $l 02_reader quranhifz://reader/2/255 8
  shoot $l 03_mushaf quranhifz://reader/2/255 10 -qhMushaf YES
  shoot $l 04_tajweed quranhifz://reader/2/255 25 -qhTajweed YES   # the markup of a long surah takes a while to parse in a debug build
  shoot $l 05_tafsir quranhifz://tafsir/2/255 8
  shoot $l 06_plan quranhifz://plan 8
  shoot $l 07_progress quranhifz://progress 8
  shoot $l 08_quiz quranhifz://quiz 8
done
