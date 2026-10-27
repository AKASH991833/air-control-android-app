#!/bin/bash
# Runs on the CI emulator (virtual scene camera). No real calls: CALL_PHONE is never exercised.
mkdir -p out
adb install -r apk/app-debug.apk
for p in CAMERA RECORD_AUDIO POST_NOTIFICATIONS; do adb shell pm grant dev.aircontrol.app android.permission.$p; done
adb logcat -c
adb shell am start -n dev.aircontrol.app/.MainActivity
sleep 4; adb exec-out screencap -p > out/01_main.png
adb shell am force-stop dev.aircontrol.app
adb shell am start -n dev.aircontrol.app/.MainActivity --ez debug_start true
sleep 15; adb exec-out screencap -p > out/02_tracking_on.png
adb shell dumpsys activity services dev.aircontrol.app | head -40 > out/services.txt
adb shell dumpsys notification --noredact | grep -A6 "AirControl is watching" | head -20 > out/notification.txt
adb shell am start -n dev.aircontrol.app/.CaptureActivity
sleep 8; adb exec-out screencap -p > out/03_camera_mode.png
adb shell am start -n dev.aircontrol.app/.EnrollActivity
sleep 6; adb exec-out screencap -p > out/04_enroll.png
adb logcat -d | grep -E "AirControl|AndroidRuntime|FATAL|mediapipe|CameraX" | tail -80 > out/logcat.txt
exit 0
