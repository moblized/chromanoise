# Chroma Noise

A white/colored noise app for Android in the spirit of Chroma Doze: you **draw the sound's spectrum with your finger** and the app generates noise that matches it, live. Plus an alarm clock, timer and stopwatch built for bedtime.

**Get the app:** on your phone, open [the latest release](../../releases/latest), download the `.apk` and open it. New releases install over the old one and keep your alarms and settings.

## Features

- **Draw-your-own spectrum** — 32 bars from 20 Hz (left, red) to 20 kHz (right, violet). Drag anywhere; drag a bar to the very bottom to silence it.
- **Color presets** — White, Pink, Brown, Grey, Blue, Violet, each with the textbook slope (checked by the unit tests).
- **Save your own** sounds, and delete them (tap a saved one, then its ✕).
- **Stereo** — left and right are independent noise, so it sounds wide on headphones.
- **Sleep timer** — 15 min to 8 h, with a 30-second fade-out so it doesn't cut off abruptly. Picking a timer starts playback.
- **Keeps playing with the screen off** (foreground service + notification with a Stop button and a countdown).
- Remembers your last sound and volume.
- **Appearance** (half-circle button, top right of each tab): six **color themes** (Violet, Ocean, Forest, Ember, Rose, Mono), *Follow phone setting* / *Light* / *Dark*, and a **Pure black** switch for OLED screens. Changes apply instantly, including on the lock-screen alarm. Every theme is generated with guaranteed text contrast (checked by the unit tests).

### Alarm clock (Alarms tab)

- **Alarms** with repeat days (or once), a label, and a snooze length (5–20 min).
- **Rings over the lock screen** with big Stop / Snooze buttons, and wakes the screen.
- **Wakes you from the noise:** if noise is playing, it fades out as the alarm starts.
- **Gentle wake:** fades in from quiet to full over a minute (switch off for full volume straight away).
- **Sound:** built-in *Soft chimes* (synthesized, so it can't go missing), any of your phone's alarm sounds, or **no sound (vibrate only)**. If a phone sound fails to play, it falls back to the chimes.
- **Vibration patterns:** Pulse, Heartbeat, Rapid or Waves — tap one in the editor to feel it. With Gentle wake on, vibration starts soft and builds to full strength over the same minute as the sound (on phones that can vary vibration strength).
- Uses the **alarm volume**, so it rings in silent mode and Do Not Disturb (if alarms are allowed there).
- **Survives reboots**, even before you unlock the phone. If the phone was restarting when an alarm was due, it rings late (up to 15 min) instead of being skipped; longer than that, you get a "Missed alarm" notification.
- **Flip to snooze:** turn the phone face down while it rings (per-alarm switch, on by default). It only counts if the phone wasn't already lying face down when the alarm started, and has to stay down for half a second — then two short buzzes confirm the snooze.
- Snoozed alarms show a notification with a **Dismiss** button. Unanswered alarms stop after 10 minutes.
- The Alarms tab warns you (with a fix button) if notifications, exact alarms, lock-screen alarms or the alarm volume are switched off.
- The next alarm shows as a little pill on the Noise screen and as the alarm icon in the status bar.

### Timer

- Type a time on the keypad (fills from the right, like a microwave: 1-3-0 = 1:30) or tap a quick start: 1, 3, 5, 10, 15, 30 min, or *Again* for your last length.
- Progress ring with Pause / Resume, **+1 min** and Cancel.
- When time's up it rings like an alarm — screen off, silent mode, even after a reboot — and, like Google Clock, shows **Time's up** with the overtime counting up (−0:05, −0:06…), with **+1 min** and Stop; flip the phone face down to stop it. White noise keeps playing.
- **On screen outside the app, like Google Clock:** on Android 16+ it's a **Live Update** — a ticking chip in the status bar (on Samsung, tap it for the pop-down card with the big time, a round pause button and a progress line) and a card pinned at the top of the lock screen, with Pause, +1:00 and Reset. Older Android versions show the same live countdown and progress bar as a normal notification.
- **Samsung (One UI 8):** if the chip doesn't appear, turn on *Settings → Developer options → Live notifications for all apps* (Samsung currently limits it to its own apps unless that's on).

### Stopwatch

- Start / Pause / Reset and **laps** (newest first, fastest and slowest labelled).
- Keeps counting when you leave the app or restart the phone, shown live on the lock screen and notification shade (Pause / Lap, or Resume / Reset) — and as a status-bar chip on Android 16+ (Live Updates).
- If notifications or Live Updates are switched off, the Timer and Stopwatch tabs say so with a button to turn them on.

### Date icon

- A home-screen icon that shows **today's month, day and year** (e.g. OCT / 19 / 2026) and flips to the new date at midnight by itself. Tap it to open the app. It takes the colour of your chosen theme.
- Add it from **Appearance → Date icon → Add** (or long-press the home screen → Widgets → Chroma Noise).
- It's a one-cell widget made to look like an app icon: Android only lets the phone maker's own Calendar/Clock apps change their real launcher icon, so this is how other apps get a live date icon. It uses no battery — Android's own clock text updates it.

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
| `ui/AppRoot.kt` | The four tabs; a ringing alarm or timer takes over the screen |
| `data/Palette.kt` | Generates the color themes with guaranteed contrast |
| `ui/Theme.kt`, `AppearanceDialog.kt`, `data/AppearanceStore.kt` | Applies the theme; the picker |
| `alarm/Alarm.kt` | An alarm + "when does it ring next" (unit-tested, incl. daylight-saving) |
| `alarm/AlarmStore.kt` | Saves alarms (readable before unlock) |
| `alarm/AlarmScheduler.kt` | Keeps Android's AlarmManager in sync; handles late/missed alarms |
| `alarm/AlarmReceiver.kt` | Alarm fired / snooze dismissed / reboot & time-zone changes |
| `alarm/AlarmRingService.kt` | Rings: stops the noise, plays sound, Snooze/Stop, 10-min limit |
| `alarm/AlarmSoundPlayer.kt`, `ChimeSynth.kt` | Alarm sound, fade-in, built-in chimes |
| `alarm/VibrationPattern.kt`, `Vibrations.kt` | Vibration patterns and the gentle build-up |
| `alarm/AlarmActivity.kt`, `ui/RingingScreen.kt` | The lock-screen alarm |
| `ui/AlarmsScreen.kt`, `ui/AlarmEditor.kt` | Alarm list and editor |
| `alarm/FlipDetector.kt`, `FlipToSnooze.kt` | Face-down detection (unit-tested) and the sensor hookup |
| `clock/ClockState.kt` | Timer and stopwatch maths, keypad entry (unit-tested) |
| `clock/ClockStore.kt` | Saves them, carrying running ones across a reboot |
| `clock/ClockControl.kt`, `ClockReceiver.kt`, `ClockNotifications.kt` | Scheduling the timer, notification buttons |
| `ui/TimerScreen.kt`, `ui/StopwatchScreen.kt` | Timer and stopwatch screens |
| `widget/DateWidget.kt`, `res/layout/widget_date.xml` | The home-screen date icon |

Note: the app uses the `USE_EXACT_ALARM` permission, which is fine for personal use and for alarm-clock apps; Google Play only allows it for apps whose main job is an alarm or calendar.

## Build it

**Android Studio** (on CachyOS: `paru -S android-studio`): *File → Open* this folder, let Gradle sync, press Run with your phone connected (USB debugging on).

**Command line** (needs the Android SDK and `ANDROID_HOME` set):

```bash
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # noise spectra, alarm timing, timer/stopwatch, flip, theme contrast
```

**No computer setup:** every push to `main` is built by GitHub Actions (`.github/workflows/build.yml`): it runs the tests, builds the APK and publishes it as a new [Release](../../releases) (version 1.0.*build number*).

**Signing:** all builds (GitHub and Android Studio) are signed with the shared key in `keystore/`, so updates install over each other. That key is public, which is fine for installing on your own phone; if you ever publish to the Play Store, make a private key and keep it out of the repo.

Stack: Kotlin 2.2, Jetpack Compose (Material 3), AGP 8.13, Gradle 8.14, min Android 8.0, target Android 16. No third-party libraries.
