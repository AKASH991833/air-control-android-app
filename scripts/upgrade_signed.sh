#!/bin/bash
# Signed production pair: published 0.1.0 -> candidate 0.1.1 via adb install -r (no uninstall). Settings read as root (emulator only).
mkdir -p out
P=dev.aircontrol.app
adb uninstall $P >/dev/null 2>&1
adb install apk/old.apk > out/install_old.txt 2>&1
adb shell dumpsys package $P | grep -E "versionCode|versionName" > out/old_version.txt
adb shell am start -n $P/.MainActivity >/dev/null 2>&1; sleep 5; adb shell am force-stop $P
adb root > out/root.txt 2>&1; sleep 3
adb shell "mkdir -p /data/data/$P/shared_prefs && cat > /data/data/$P/shared_prefs/air.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><int name=\"hold\" value=\"1100\" /><string name=\"favName\">Upgrade Test</string></map>
XML
chown -R \$(stat -c %u /data/data/$P) /data/data/$P/shared_prefs"
adb shell cat /data/data/$P/shared_prefs/air.xml > out/prefs_before.txt 2>&1
adb install -r tests/prod/AirControl-0.1.1.apk > out/install_new.txt 2>&1
adb shell dumpsys package $P | grep -E "versionCode|versionName" > out/new_version.txt
adb shell cat /data/data/$P/shared_prefs/air.xml > out/prefs_after.txt 2>&1
adb shell dumpsys package $P | grep -iE "signatures|signing|PackageSignatures" -A3 | head -12 > out/sig.txt
adb shell am start -n $P/.MainActivity >/dev/null 2>&1; sleep 5
adb exec-out screencap -p > out/after_launch.png
adb shell dumpsys window | grep mCurrentFocus > out/focus.txt
exit 0
