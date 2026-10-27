# AirControl (Android)

Control your Android phone with hand gestures using the front camera. On-device only (MediaPipe Gesture Recognizer). No frames or photos of you are stored or uploaded.

Status: v0.1 source. It compiles. It has NOT been tested on a real phone yet.

## What it does
- Start once in the app; tracking continues in the background through a camera foreground service with a visible notification and Stop button.
- Reels mode (works over other apps via an accessibility service you enable yourself):
  - Index-finger swipe up/down (motion detector) = next / previous reel or short
  - Open palm = pause/play (tap at screen center)
  - Victory = pointer on/off. Finger moves a cursor, held pinch (thumb + index) = tap
  - Fist held = call your one favourite number directly (needs the Call permission; off until you save a number)
  - Thumb down = Back. Other poses can be mapped to Home, Notifications, Screenshot
- Camera mode (the app owns the camera): Victory = photo, Fist = start/stop video. Saved to Pictures/AirControl and Movies/AirControl.
- Add my gesture: record 6+ samples of a static hand pose with your camera. Stored as hand-landmark numbers only. Matching is nearest-template, so similar poses can be confused; tune strictness in the app.
- Safety: an activation pose (default: I-love-you hand) must be held first; gestures then work for 10 seconds. Hold time and cooldown are adjustable.

## Limits (be honest)
- Camera tracking must be started while the app is visible (Android 14 rule). Another app opening the same camera can stop tracking.
- Injected taps/swipes may be ignored by some apps or secure screens. Do not use pointer taps for payments or sending anything you cannot undo.
- Direct calling uses Android's CALL_PHONE and TelecomManager.placeCall from a background service; behavior on your phone is untested. If Android blocks it, a notification says so.
- Battery use and OEM background killing are untested.

## Build
Push to GitHub: the Actions workflow builds `app-debug.apk` (artifact `AirControl-debug-apk`). Local: JDK 17, Android SDK 34, Gradle 8.7, download `gesture_recognizer.task` into `app/src/main/assets/` (the workflow does it).
