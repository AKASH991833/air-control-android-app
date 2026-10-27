#!/bin/bash
# CI emulator smoke test. No real calls: CALL_PHONE is never exercised.
mkdir -p out
shot() { timeout 30 adb exec-out screencap -p > "out/$1.png"; }
timeout 120 adb install -r apk/app-debug.apk > out/install.txt 2>&1
for p in CAMERA RECORD_AUDIO POST_NOTIFICATIONS; do timeout 20 adb shell pm grant dev.aircontrol.app android.permission.$p; done
adb logcat -c
timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity; sleep 5; shot 01_main
timeout 20 adb shell am force-stop dev.aircontrol.app
timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity --ez debug_start true; sleep 20; shot 02_tracking_on
timeout 30 adb shell dumpsys activity services dev.aircontrol.app 2>&1 | head -40 > out/services.txt
timeout 30 adb shell dumpsys notification --noredact 2>&1 | grep -A6 "AirControl is watching" | head -20 > out/notification.txt
timeout 30 adb shell am start -n dev.aircontrol.app/.CaptureActivity; sleep 8; shot 03_camera_mode
timeout 30 adb shell am start -n dev.aircontrol.app/.EnrollActivity; sleep 6; shot 04_enroll
timeout 30 adb logcat -d 2>&1 | grep -E "AirControl|AndroidRuntime|FATAL|mediapipe|CameraX" | tail -80 > out/logcat.txt
exit 0
