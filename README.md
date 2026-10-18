# Chroma Noise

A white/colored noise app for Android in the spirit of Chroma Doze: you **draw the sound's spectrum with your finger** and the app generates noise that matches it, live.

## Features

- **Draw-your-own spectrum** — 32 bars from 20 Hz (left, red) to 20 kHz (right, violet). Drag anywhere; drag a bar to the very bottom to silence it.
- **Color presets** — White, Pink, Brown, Grey, Blue, Violet, each with the textbook slope (checked by the unit tests).
- **Save your own** sounds, and delete them (tap a saved one, then its ✕).
- **Stereo** — left and right are independent noise, so it sounds wide on headphones.
- **Sleep timer** — 15 min to 8 h, with a 30-second fade-out so it doesn't cut off abruptly. Picking a timer starts playback.
- **Keeps playing with the screen off** (foreground service + notification with a Stop button and a countdown).
- Remembers your last sound and volume.
- **Appearance** (half-circle button, top right of each tab): *Follow phone setting*, *Light* or *Dark*, plus a **Pure black** switch for OLED screens. Changes apply instantly, including on the lock-screen alarm (pale dawn sky in light mode, night sky in dark).

### Alarm clock (Alarms tab)

- **Alarms** with repeat days (or once), a label, and a snooze length (5–20 min).
- **Rings over the lock screen** with big Stop / Snooze buttons, and wakes the screen.
- **Wakes you from the noise:** if noise is playing, it fades out as the alarm starts.
- **Gentle wake:** fades in from quiet to full over a minute (switch off for full volume straight away).
- **Sound:** built-in *Soft chimes* (synthesized, so it can't go missing), any of your phone's alarm sounds, or **no sound (vibrate only)**. If a phone sound fails to play, it falls back to the chimes.
- **Vibration patterns:** Pulse, Heartbeat, Rapid or Waves — tap one in the editor to feel it. With Gentle wake on, vibration starts soft and builds to full strength over the same minute as the sound (on phones that can vary vibration strength).
- Uses the **alarm volume**, so it rings in silent mode and Do Not Disturb (if alarms are allowed there).
- **Survives reboots**, even before you unlock the phone. If the phone was restarting when an alarm was due, it rings late (up to 15 min) instead of being skipped; longer than that, you get a "Missed alarm" notification.
- Snoozed alarms show a notification with a **Dismiss** button. Unanswered alarms stop after 10 minutes.
- The Alarms tab warns you (with a fix button) if notifications, exact alarms, lock-screen alarms or the alarm volume are switched off.
- The next alarm shows as a little pill on the Noise screen and as the alarm icon in the status bar.

## How it works

Like Chroma Doze, it never loops a recording:

1. Your curve is turned into an amplitude for every frequency bin (interpolated in dB between bars).
2. A background thread fills those bins with random values and runs an **inverse FFT**, making a pool of twelve ~1.5-second noise blocks, each shaped by a sine window.
3. The audio thread plays an endless stream by overlapping a random block with the previous one by half. Because sin² + cos² = 1, the overlaps add up to constant loudness — no clicks and no audible repetition.

So in steady state the phone only adds two arrays together — very light on the battery. When you redraw the curve, new blocks are swapped in within about a second.

Code map:

| File | What it does |
|---|---|
| `audio/SpectrumModel.kt` | Bars ↔ frequencies ↔ amplitudes (70 dB range per bar) |
| `audio/Presets.kt` | Built-in noise colors |
| `audio/NoiseGenerator.kt`, `Fft.kt` | Makes the shaped noise blocks |
| `audio/BlockMixer.kt` | Crossfades random blocks into a stereo stream |
| `audio/NoiseEngine.kt` | AudioTrack playback, fades, sleep timer |
| `playback/NoisePlaybackService.kt` | Foreground service + notification |
| `ui/SpectrumEditor.kt` | The drawable bar graph |
| `ui/NoiseScreen.kt` | Presets, volume, timer, play button |
| `ui/AppRoot.kt` | The two tabs; ringing alarm takes over the screen |
| `ui/Theme.kt`, `AppearanceDialog.kt`, `data/AppearanceStore.kt` | Light / dark / pure-black themes and the picker |
| `alarm/Alarm.kt` | An alarm + "when does it ring next" (unit-tested, incl. daylight-saving) |
| `alarm/AlarmStore.kt` | Saves alarms (readable before unlock) |
| `alarm/AlarmScheduler.kt` | Keeps Android's AlarmManager in sync; handles late/missed alarms |
| `alarm/AlarmReceiver.kt` | Alarm fired / snooze dismissed / reboot & time-zone changes |
| `alarm/AlarmRingService.kt` | Rings: stops the noise, plays sound, Snooze/Stop, 10-min limit |
| `alarm/AlarmSoundPlayer.kt`, `ChimeSynth.kt` | Alarm sound, fade-in, built-in chimes |
| `alarm/VibrationPattern.kt`, `Vibrations.kt` | Vibration patterns and the gentle build-up |
| `alarm/AlarmActivity.kt`, `ui/RingingScreen.kt` | The lock-screen alarm |
| `ui/AlarmsScreen.kt`, `ui/AlarmEditor.kt` | Alarm list and editor |

Note: the app uses the `USE_EXACT_ALARM` permission, which is fine for personal use and for alarm-clock apps; Google Play only allows it for apps whose main job is an alarm or calendar.

## Build it

**Android Studio** (on CachyOS: `paru -S android-studio`): *File → Open* this folder, let Gradle sync, press Run with your phone connected (USB debugging on).

**Command line** (needs the Android SDK and `ANDROID_HOME` set):

```bash
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # noise-spectrum and alarm-timing tests
```

**No computer setup:** push this folder to a GitHub repo. The included workflow builds the APKs on every push; download them from the run's *Artifacts* section.

Stack: Kotlin 2.2, Jetpack Compose (Material 3), AGP 8.13, Gradle 8.14, min Android 8.0, target Android 16. No third-party libraries.
