#!/bin/bash
# CI emulator smoke test. No real calls: CALL_PHONE is never exercised.
mkdir -p out
shot() { timeout 30 adb exec-out screencap -p > "out/$1.png"; timeout 20 adb shell dumpsys window 2>&1 | grep -E "mCurrentFocus" | head -1 > "out/$1.focus.txt"; }
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
# every screen at three sizes (test variant exports the screens; release does not)
for cfg in "default:" "tall:1080x2400 420" "narrow:540x1200 420"; do
  tag=${cfg%%:*}; spec=${cfg#*:}
  if [ -n "$spec" ]; then timeout 20 adb shell wm size ${spec% *}; timeout 20 adb shell wm density ${spec#* }; sleep 3; fi
  for scr in MainActivity SettingsActivity CaptureActivity EnrollActivity; do
    timeout 30 adb shell am start -n dev.aircontrol.app/.$scr > out/am_${tag}_$scr.txt 2>&1; sleep 7; shot ${tag}_$scr
    timeout 20 adb shell am force-stop dev.aircontrol.app
  done
  timeout 30 adb shell am start -n dev.aircontrol.app/.MainActivity > /dev/null 2>&1; sleep 4
  for n in 1 2 3 4; do timeout 20 adb shell input swipe 300 1000 300 150 250; sleep 1; shot ${tag}_Main_scroll$n; done
  timeout 20 adb shell am force-stop dev.aircontrol.app
  timeout 30 adb shell am start -n dev.aircontrol.app/.SettingsActivity > /dev/null 2>&1; sleep 4
  for n in 1 2; do timeout 20 adb shell input swipe 300 1000 300 150 250; sleep 1; shot ${tag}_Settings_scroll$n; done
  timeout 20 adb shell am force-stop dev.aircontrol.app
done
exit 0
