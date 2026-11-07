#!/bin/sh
# Clean status bar for the store shots: 12:00, full battery, Wi-Fi, no notifications. `demo_mode.sh off` restores it.
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
d() { $ADB shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }
if [ "$1" = "off" ]; then
  d -e command exit
  $ADB shell settings put global sysui_demo_allowed 0
  exit 0
fi
$ADB shell settings put global sysui_demo_allowed 1
d -e command enter
d -e command clock -e hhmm 1200
d -e command battery -e level 100 -e plugged false
d -e command network -e wifi show -e level 4 -e fully true
d -e command network -e mobile hide -e nosim hide
d -e command notifications -e visible false
d -e command status -e volume hide -e bluetooth hide -e location hide -e alarm_clock hide -e sync hide
