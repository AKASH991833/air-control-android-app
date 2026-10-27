#!/bin/bash
# CI emulator smoke test. No real calls: CALL_PHONE is never exercised.
mkdir -p out
shot() { timeout 30 adb exec-out screencap -p > "out/$1.png"; timeout 20 adb shell dumpsys window 2>&1 | grep -E "mCurrentFocus" | head -1 > "out/$1.focus.txt"; }
# upgrade test: old 0.1.0 -> new, settings must survive (debug-signed both, same key)
timeout 20 adb uninstall dev.aircontrol.app >/dev/null 2>&1
timeout 120 adb install apk/old-debug.apk > out/up_install_old.txt 2>&1
timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity >/dev/null 2>&1; sleep 4; timeout 20 adb shell am force-stop dev.aircontrol.app
timeout 20 adb shell "run-as dev.aircontrol.app sh -c 'mkdir -p shared_prefs; echo \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?><map><int name=\\\"hold\\\" value=\\\"1100\\\" /><string name=\\\"favName\\\">Upgrade Test</string></map>\" > shared_prefs/air.xml'" > out/up_write.txt 2>&1
timeout 20 adb shell "run-as dev.aircontrol.app cat shared_prefs/air.xml" > out/up_before.txt 2>&1
timeout 120 adb install -r apk/app-debug.apk > out/up_install_new.txt 2>&1
timeout 20 adb shell "run-as dev.aircontrol.app cat shared_prefs/air.xml" > out/up_after.txt 2>&1
timeout 20 adb shell dumpsys package dev.aircontrol.app | grep -E "versionName|versionCode" > out/up_version.txt
timeout 20 adb shell am force-stop dev.aircontrol.app
timeout 30 adb shell am start -n dev.aircontrol.app/.SettingsActivity >/dev/null 2>&1; sleep 5; shot up_settings_after
timeout 20 adb shell am force-stop dev.aircontrol.app
timeout 120 adb install -r apk/app-debug.apk > out/install.txt 2>&1
for p in CAMERA RECORD_AUDIO POST_NOTIFICATIONS; do timeout 20 adb shell pm grant dev.aircontrol.app android.permission.$p; done
adb logcat -c
(adb logcat -v time AirControl:V AndroidRuntime:E CameraX:V Camera2CameraImpl:V mediapipe:V DEBUG:V *:S > out/live.txt 2>&1 &) 
timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity > out/am1.txt 2>&1; sleep 5; shot 01_main
timeout 20 adb shell am force-stop dev.aircontrol.app
timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity --ez debug_start true > out/am2.txt 2>&1; sleep 25; shot 02_tracking_on
timeout 30 adb shell dumpsys activity services dev.aircontrol.app 2>&1 | head -60 > out/services.txt
timeout 30 adb shell "ps -A | grep aircontrol" > out/ps.txt 2>&1
timeout 30 adb shell dumpsys notification --noredact 2>&1 | grep -iB2 -A8 "aircontrol" | head -40 > out/notification.txt
timeout 30 adb shell dumpsys activity activities 2>&1 | grep -i aircontrol | head -20 > out/activities.txt
timeout 20 adb shell am force-stop dev.aircontrol.app
# every screen at two sizes (test variant exports the screens; release does not)
for cfg in "tall:1080x2400 420" "narrow:540x1200 420"; do
  tag=${cfg%%:*}; spec=${cfg#*:}
  timeout 20 adb shell wm size ${spec% *}; timeout 20 adb shell wm density ${spec#* }; sleep 3
  SZ=$(timeout 20 adb shell wm size | tail -1 | sed 's/.*: //'); W=${SZ%x*}; H=${SZ#*x}; echo "$tag $SZ" >> out/sizes.txt
  X=$((W/2)); Y1=$((H*80/100)); Y2=$((H*25/100))
  for scr in MainActivity SettingsActivity CaptureActivity EnrollActivity; do
    timeout 30 adb shell am start -n dev.aircontrol.app/.$scr > out/am_${tag}_$scr.txt 2>&1; sleep 7; shot ${tag}_$scr
    timeout 20 adb shell am force-stop dev.aircontrol.app
  done
  timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity > /dev/null 2>&1; sleep 4
  for n in 1 2 3 4 5 6; do timeout 20 adb shell input swipe $X $Y1 $X $Y2 400; sleep 1; shot ${tag}_Main_scroll$n
    if [ $n -ge 2 ]; then for k in 1 2 3 4; do shot ${tag}_burst${n}_$k; sleep 0.45; done; fi; done
  timeout 20 adb shell am force-stop dev.aircontrol.app
  timeout 30 adb shell am start -n dev.aircontrol.app/.SettingsActivity > /dev/null 2>&1; sleep 4
  for n in 1 2; do timeout 20 adb shell input swipe $X $Y1 $X $Y2 400; sleep 1; shot ${tag}_Settings_scroll$n; done
  timeout 20 adb shell am force-stop dev.aircontrol.app
done
timeout 20 adb shell wm size reset; timeout 20 adb shell wm density reset
exit 0
