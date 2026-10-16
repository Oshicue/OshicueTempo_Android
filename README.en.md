# Oshicue Tempo (Android)

[简体中文](README.md) · [繁體中文](README.zh-Hant.md) · [日本語](README.ja.md) · **English**

A native Android multi-member timer for fan meet-and-greet events. **Tempo** means rhythm.

This is the complete Android port of `OshicueTempo_iOS` (a native SwiftUI iOS app): Kotlin + Jetpack Compose, MediaPlayer, AlarmManager and NotificationChannel. Minimum Android 10 (API 29), no network or account dependencies, no third-party runtime libraries.

## Building and running

Open this directory in Android Studio (or use the command line), JDK 17 or later:

```sh
./gradlew :app:assembleDebug          # debug build
./gradlew :app:testDebugUnitTest      # core and persistence tests (16)
./gradlew :app:assembleRelease        # signed release (needs keystore.properties)
```

Release signing mirrors the iOS project's team setup: the bundled `oshicue-tempo-release.jks` and `keystore.properties` provide a self-signed configuration; without `keystore.properties` only an unsigned build is produced. Built artifacts:

- `OshicueTempo-Android-1.0.0.apk` (R8-shrunk signed release, about 3.6 MB)
- `OshicueTempo-Android-1.0.0-debug.apk`
- `OshicueTempo-Android-1.0.0-Source.zip` (source archive; no signing material or build outputs)

## Usage

1. Tap the manage button in the top-right corner, create a group, and set its name and seconds per ticket (1–3600 s).
2. Add members with a name, a representative color and their own end-of-round sound.
3. Choose the current group on the main screen. Four members fit on one page; more members can be swiped, and the arrows beside the page dots also work.
4. Tap a member's clock, enter the number of tickets on the number pad, then tap "Confirm & Start". **Total time = seconds per ticket × tickets this round.**
5. Each member can pause, resume, reset or start the next round independently. Switching groups never stops other members' countdowns.

An editable fictional group LUMINA with four members ships with the app so you can try it on first launch. Delete the sample and add real groups whenever you like.

A round accepts 1–999 tickets and lasts at most 24 hours. A running round keeps the ticket price it started with; editing the group's duration does not change a round already running. Deleting a member or group and saving cancels the related timers and alerts. Resetting a round asks for confirmation.

## Alert sounds and recording import

Four original short alert sounds are included and can be previewed in Sounds & Alerts: Starlight, Heartbeat, Bell and Level Up. When several members finish together, every cue mixes in the foreground at once, with the volume shared evenly among them.

To import a recording, tap "Import Recording from Files" in Sounds & Alerts and pick audio with the system file picker (SAF). M4A, WAV, AIFF, CAF, MP3 and AAC are supported; recordings must be shorter than 30 seconds and no larger than 50 MB. Imported audio is also copied to the system notification sounds directory (MediaStore) for the notification channel, while in-app playback uses the original file in app storage.

In the foreground, sounds play at media volume and request audio focus that ducks other apps; the screen stays awake while a timer runs. Locked-screen and background alerts are fired by exact AlarmManager alarms, with one high-importance notification channel per sound, and need notification permission; if the system revokes exact alarms, the alert still fires, just less precisely (it may be delayed). Preview the sounds and check the volume and notification permission on a real device before the event.

Countdowns are computed from an absolute deadline, so switching apps, locking the screen or reopening restores the correct remaining time; after a reboot or an app update, pending alerts are rescheduled automatically. An expired round shows "Time's up" when it comes back to the foreground and never replays a cue the background notification already handled.

## Appearance

Light and dark interfaces follow the system appearance automatically; there is no in-app switch. Light mode uses a pure white background with lightly tinted white cards, while dark mode keeps the Oshicue black. When the system appearance changes, the timer board, entry sheets, number pad, group and member editors and sound settings all update together without resetting running or paused timers.

Members keep their original representative colors; swatches and timer rings use the same representative color in both appearances (the same contrast-compensation algorithm as iOS). Light mode uses clear neutral dark text, with lighter card tints and shadows. A white representative color gets a fine neutral backing on its clock. The wordmark and countdown digits keep using the bundled Unbounded variable font.

## Language support

Simplified Chinese, Traditional Chinese, Japanese and English are natively supported and follow the system language; on Android 13 and later you can switch per app in the system's "App languages" settings (declared through `localeConfig`). The interface, accessibility labels, error messages, sound names and local notifications are all translated; group names, member names and recording names you enter stay as they are.

Only the English interface calls the event **MEET & GREET**; Simplified Chinese and Japanese use 特典会, Traditional Chinese uses 特典會.

## Data and brand

Groups, members, the selected group and each round's countdown are saved automatically to `files/OshicueTempo/state.json` in the app's private directory. The JSON structure is identical to the iOS app's (`version: 1`, sorted keys, `millisecondsSince1970` dates, omitted nulls), so saves written on either platform can be read by the other. Writes use a temporary file and an atomic replace; if reading fails, the original data is preserved and writing stops.

The Oshicue brand colors are used throughout: black `#0A0A0D`, warm white `#F4F3EE` and pink `#FF3D6E`. The wordmark and timer digits use the **Unbounded** variable font (SIL Open Font License, bundled under `app/src/main/assets/licenses/`). The icon and wordmark reuse the iOS project's brand assets (`docs/brand/`); the adaptive launcher icons are generated by `tools/generate_launcher_icons.swift`.

## Project layout and verification

- `app/src/main/kotlin/com/oshicue/tempo/App`: application lifecycle and screen coordination (`TempoApplication`, `MainActivity`, `TempoRoot`).
- `app/src/main/kotlin/com/oshicue/tempo/core`: group, member and countdown models, input validation and snapshot JSON coding.
- `app/src/main/kotlin/com/oshicue/tempo/data`: `TempoStore` persistence, `TempoAudio` playback and import, `TempoNotifications` channels and alarms, broadcast receivers.
- `app/src/main/kotlin/com/oshicue/tempo/ui`: the four-member timer board, ticket entry, group editor and sound settings.
- `app/src/main/kotlin/com/oshicue/tempo/i18n`: the key map generated from the iOS `Localizable.strings`.
- `app/src/test`: time arithmetic, state persistence and failure-path tests (16).
- `tools/convert_localization.py`: regenerates the four-language strings and the key map from the iOS resources.
- `../misc/OshicueTempo_iOS/`: the iOS sources kept as the porting reference in the workspace's `misc/` folder, next to this project directory (together with the original archive; not included in the source archive).

```sh
python3 tools/convert_localization.py ../misc/OshicueTempo_iOS/OshicueTempo/Core/Resources \
  app/src/main/res app/src/main/kotlin/com/oshicue/tempo/i18n
swift tools/generate_launcher_icons.swift ../misc/OshicueTempo_iOS/docs/brand/app-icon.png app/src/main/res
```

Current verification results are in `docs/VERIFICATION.md`.
