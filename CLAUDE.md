# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Animal Spin — an offline Android app for toddlers (1.5+). Tap an animal → TTS speaks its name → a random recorded animal sound plays. No internet, no ads, no tracking. Single-module Gradle project (`:app`), Kotlin, MVVM with AndroidX Navigation.

## Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew lint                   # Android Lint
./gradlew :app:testDebugUnitTest --tests "casa.falconer.toys.ExampleUnitTest"   # single test
```

No CI config and the test dirs contain only the template `Example*Test` classes, so tests are effectively unestablished.

**JDK gotcha (this machine):** the system default `java` is a JRE with no `javac`, so CLI Gradle builds fail at `compileDebugJavaWithJavac` with "does not provide the required capabilities: [JAVA_COMPILER]". Prefix CLI builds with the Android Studio JBR (a full JDK 21): `JAVA_HOME=/opt/android-studio/jbr ./gradlew …`. Android Studio itself builds fine (it uses that JBR automatically). `gradlew` may also need `chmod +x`.

**Toolchain:** Gradle 9.5.1 (wrapper), AGP 9.2.1, Kotlin via AGP's built-in support (no `org.jetbrains.kotlin.android` plugin — applying it is an error on AGP 9). `compileSdk 36` / `targetSdk 36` / `minSdk 23`. `core-ktx` is pinned to 1.17.0 because 1.18+ require `compileSdk 37`, and the installed cmdline-tools are too old to fetch the android-37 platform — bump both together if you raise it. `buildFeatures { buildConfig true }` is required (AGP 8+ defaults it off) because `AnimalSpinApp` reads `BuildConfig.DEBUG`.

## Naming gotchas

- **Package / namespace is `casa.falconer.toys`** but **`applicationId` is `casa.falconer`** (`app/build.gradle`). They differ on purpose — don't "fix" one to match the other.
- Gradle `rootProject.name` is `Animals`, the app name is "Animal Spin", the class prefix is `AnimalSpin`, and the package is `toys`. All refer to the same app.
- Debug and release builds use different launcher icons via `manifestPlaceholders` (`appIcon`/`appIconRound`), so both can be installed side by side.

## Architecture

Single-activity app. `MainActivity` only sets a no-title full-screen window and hosts the NavHostFragment defined by `res/navigation/nav_graph.xml`. Two destinations: `MainFragment` (start) and `SettingsFragment`.

**Content is data-driven, not file-driven.** Two declarations in `models/Animals.kt` are the source of truth:
- `enum class Animal` — each entry binds a `@DrawableRes` image + `@StringRes` TTS phrase ("the cat says…").
- `AnimalNoise.animal_sounds` — a flat list mapping each `Animal` to one of several `@RawRes` audio clips in `res/raw/` (~55 clips). To add an animal: add an `Animal` enum entry (with a drawable + `tts_<name>_says` string), then add its `AnimalNoise` rows. No code changes elsewhere.

**Playback flow** (`MainFragment.playAnimalNoise`): tap → `MainViewModel.queueAnimalNoise` picks a *random* clip for that animal → TTS speaks the name → on TTS `onDone`, a `MediaPlayer` plays the clip. `TextToSpeech` and `MediaPlayer` are held as `companion object` statics on `MainFragment` (shared across instances; stopped before each new play).

**TTS voice handling.** `MainFragment.applyTtsSettings()` re-reads prefs, sets rate/pitch, enumerates available US non-network voices, persists their names, and applies the saved selection. It runs both when async TTS init completes *and* in `onResume()` — the latter matters because returning from `SettingsFragment` does **not** recreate `MainFragment`, so settings saved there would otherwise never reach the live `TextToSpeech` (this was the "Save does nothing" bug). `SettingsFragment` renders voice names as radio buttons whose IDs are `voiceName.hashCode()`, plus pitch/speed sliders. The slider values are fed **directly** to `setSpeechRate`/`setPitch`, so they use TTS units where 1.0 = normal; the sliders range `0.5–2.0` (`valueFrom`/`valueTo` in `fragment_settings.xml`, mirrored by `VOICE_MIN`/`VOICE_MAX` — keep them in sync, and `coerceIn` any loaded value or an out-of-range saved pref crashes the Material `Slider`). Defaults live in `SharedPreferencesProvider` (pitch/speed 1.0, voice `en-us-x-iom-local`).

**Persistence.** All settings go through `SharedPreferencesProvider` (one prefs file, `animal_spin_prefs`). It gets its `Context` from `AnimalSpinApp.applicationContext()` — a static singleton set in the `Application` subclass's `init` — so it needs no constructor args. ViewModels (`MainViewModel`, `SettingsViewModel`) each instantiate their own `SharedPreferencesProvider` directly; there is no DI framework.

**App init** (`AnimalSpinApp.onCreate`): plants a Timber `DebugTree` in debug builds only. Use `Timber` for logging. (Flipper was removed during the 2026 toolchain upgrade — Android Studio's Layout Inspector covers what its inspector plugin did.)

## Notes

- `ui/main/ArcLayoutManager.kt` is a custom `RecyclerView.LayoutManager` that is **not currently wired up** — `MainFragment` uses a `GridLayoutManager(2)`. Treat it as an unused/alternate layout, not dead-code to delete blindly.
- Audio/image assets must have a free-to-use origin (Creative Commons or Public Domain) per the README's contribution rules.
