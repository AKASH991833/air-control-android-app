#!/bin/bash
# CI emulator smoke test. No real calls: CALL_PHONE is never exercised.
mkdir -p out
shot() { timeout 30 adb exec-out screencap -p > "out/$1.png"; timeout 20 adb shell dumpsys window 2>&1 | grep -E "mCurrentFocus|mFocusedApp" | head -2 > "out/$1.focus.txt"; }
timeout 120 adb install -r apk/app-debug.apk > out/install.txt 2>&1
for p in CAMERA RECORD_AUDIO POST_NOTIFICATIONS; do timeout 20 adb shell pm grant dev.aircontrol.app android.permission.$p; done
adb logcat -c
timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity; sleep 5; shot 01_main
timeout 20 adb shell am force-stop dev.aircontrol.app
timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity --ez debug_start true; sleep 25; shot 02_tracking_on
timeout 30 adb shell dumpsys activity services dev.aircontrol.app 2>&1 | head -60 > out/services.txt
timeout 30 adb shell "ps -A | grep aircontrol" > out/ps.txt 2>&1
timeout 30 adb logcat -d 2>&1 | tail -400 > out/logcat_full.txt
grep -E "AirControl|AndroidRuntime|FATAL|mediapipe|CameraX|Camera" out/logcat_full.txt | tail -80 > out/logcat.txt
timeout 30 adb shell am start -n dev.aircontrol.app/.CaptureActivity; sleep 10; shot 03_camera_mode
timeout 30 adb shell am start -n dev.aircontrol.app/.EnrollActivity; sleep 8; shot 04_enroll
timeout 30 adb logcat -d 2>&1 | tail -400 > out/logcat_full2.txt
exit 0
