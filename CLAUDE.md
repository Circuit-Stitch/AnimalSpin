# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Animal Spin — an offline Android app for toddlers (1.5+). Tap an animal → TTS speaks its name → a random recorded animal sound plays. No internet, no ads, no tracking. Single-module Gradle project (`:app`), Kotlin, Jetpack Compose UI with Navigation-Compose, MVVM.

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

**Compose:** enabled via `buildFeatures { compose true }` + the `org.jetbrains.kotlin.plugin.compose` Gradle plugin (in the root `build.gradle` plugins block, applied in `:app`). Its version **must match AGP's built-in Kotlin** — currently `2.2.10` (find it with `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep kotlin-stdlib`); bump both together. Compose libs are BOM-managed and pinned to `androidx.compose:compose-bom:2025.12.01` — newer BOMs pull Compose artifacts that require `compileSdk 37` (same android-37 blocker as core-ktx above), so don't bump the BOM without raising compileSdk.

## Naming gotchas

- **Package / namespace is `casa.falconer.toys`** but **`applicationId` is `casa.falconer`** (`app/build.gradle`). They differ on purpose — don't "fix" one to match the other.
- Gradle `rootProject.name` is `Animals`, the app name is "Animal Spin", the class prefix is `AnimalSpin`, and the package is `toys`. All refer to the same app.
- Debug and release builds use different launcher icons via `manifestPlaceholders` (`appIcon`/`appIconRound`), so both can be installed side by side.

## Architecture

Single-activity app. `MainActivity` is a `ComponentActivity` whose `setContent` wraps a `MaterialTheme` around a Navigation-Compose `NavHost` with two routes: `"main"` (start, `MainScreen`) and `"settings"` (`SettingsScreen`). There is no XML layout or nav graph — the full-screen/no-title window comes from the platform `Theme.Material(.Light).NoActionBar.Fullscreen` parent on `Theme.Animals` in `res/values/themes.xml` (and `values-night`).

**Content is data-driven, not file-driven.** Two declarations in `models/Animals.kt` are the source of truth:
- `enum class Animal` — each entry binds a `@DrawableRes` image + `@StringRes` TTS phrase ("the cat says…").
- `AnimalNoise.animal_sounds` — a flat list mapping each `Animal` to one of several `@RawRes` audio clips in `res/raw/` (~55 clips). To add an animal: add an `Animal` enum entry (with a drawable + `tts_<name>_says` string), then add its `AnimalNoise` rows. No code changes elsewhere.

**Playback flow** (`MainViewModel.play`): `MainScreen`'s grid cell `onClick` → `MainViewModel.play(animal)` picks a *random* clip for that animal → TTS speaks the name → on TTS `onDone`, a `MediaPlayer` plays the clip. `TextToSpeech` and `MediaPlayer` are owned by `MainViewModel` (constructed with `AnimalSpinApp.applicationContext()`); the previous clip is stopped before each new play, and both are released in `onCleared()`.

**TTS voice handling.** `MainViewModel` re-reads prefs and applies rate/pitch + the saved voice on **every** `play()` call — so settings saved in `SettingsScreen` always take effect with no lifecycle plumbing (this is what fixed the old "Save does nothing" bug, which existed because the View-system `MainFragment` wasn't recreated on return from Settings). On async TTS init it enumerates available US non-network voices and persists their names to prefs. `SettingsScreen` renders those names as Compose `RadioButton`s (selection tracked by value, no more `hashCode()` IDs) plus two pitch/speed `Slider`s. Slider values feed **directly** into `setSpeechRate`/`setPitch` in TTS units (1.0 = normal); range is `0.5–2.0` (`valueRange` in `SettingsScreen`, mirrored by `VOICE_MIN`/`VOICE_MAX` in `SettingsViewModel` — keep them in sync; loaded prefs are `coerceIn`'d since an out-of-range value crashes the Material `Slider`). Defaults live in `SharedPreferencesProvider` (pitch/speed 1.0, voice `en-us-x-iom-local`).

**Persistence.** All settings go through `SharedPreferencesProvider` (one prefs file, `animal_spin_prefs`). It gets its `Context` from `AnimalSpinApp.applicationContext()` — a static singleton set in the `Application` subclass's `init` — so it needs no constructor args. ViewModels (`MainViewModel`, `SettingsViewModel`) each instantiate their own `SharedPreferencesProvider` directly and are obtained in the screens via `viewModel()`; there is no DI framework.

**App init** (`AnimalSpinApp.onCreate`): plants a Timber `DebugTree` in debug builds only. Use `Timber` for logging. (Flipper was removed during the 2026 toolchain upgrade — Android Studio's Layout Inspector covers what its inspector plugin did.)

## Notes

- `MainScreen` lays the animals out in a `LazyVerticalGrid(GridCells.Fixed(2))`; each cell is a square `Image` (`ContentScale.Crop`). (The old View-system `RecyclerView` + unused `ArcLayoutManager` were removed in the Compose migration.)
- Audio/image assets must have a free-to-use origin (Creative Commons or Public Domain) per the README's contribution rules.
