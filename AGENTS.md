# AGENTS.md

## Scope

This file applies to the entire repository.

## Project

ScalePaw is an Android Compose rewrite of openScale: a body-weight and body-composition tracker with Bluetooth scale support. `docs/design.md` is the source of truth for UI decisions.

## Build & Run

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew :app:testDebugUnitTest` runs unit tests.
- `./gradlew ktlintCheck` / `ktlintFormat` lint and format Kotlin sources.

## Tech Stack

- Kotlin, Jetpack Compose, Material 3 Expressive, Navigation 3 (single activity, one `NavDisplay`)
- Room for measurements, users, metric types and goals; DataStore for settings; SharedPreferences only for synchronous driver state (`ble/DriverStore`)
- WorkManager for automatic backups, AlarmManager for reminders, Glance for the widget
- Manual dependency injection: `AppContainer` (`context.container`)

## Architecture

- `data/` holds persistence and transfer; `domain/` holds pure calculations and must not touch Android APIs, so it stays unit-testable.
- `ScaleRepository` is the only writer. Screens read `container.session.state`, one `StateFlow` with the active user, metric types, measurements and settings.
- Measurements are a timestamp plus a value per metric type. Derived metrics (BMI, LBM, WHR, WHtR, BMR, TDEE, caliper fat) are recomputed by `MeasurementProcessor` on every save and are never entered by hand.
- `ble/` owns transports and the scale lifecycle. A driver only sees `ScaleDeviceHandler`'s callbacks; `ScaleManager` maps its `ScaleMeasurement` into a `ScaleReading` and hands it to `ScaleRepository.saveScaleReading`.

## openScale drivers

- `ble/openscale/{scales,libs}` are ported from openScale and keep their GPL headers. Change them as little as possible so upstream fixes can be merged: package names, the UI toolkit (Yoin components instead of raw Material), and nothing else unless it is a bug.
- `ble/openscale/{vocab,util}` replace the openScale and blessed types the drivers use. Extend them rather than editing a driver.
- `ScaleFactory` order matters: the first handler that claims a device wins.
- Every driver change needs its openScale test vector under `app/src/test/java/com/gpo/scalepaw/ble/openscale` to keep passing.

## UI rules

- Colour blocks, not dividers. Use `Section`, `SettingRow`, `SwitchRow`, `ChoiceRow`, `ScalePage`, `ConfirmDialog` and `ScaleTextField` instead of raw Material lists and fields.
- Icons come from `YoinSymbols`, `PiliSymbols` or `ScaleSymbols`; never add material-icons.
- Every interaction animates with spring specs from `YoinMotion` and gives haptic feedback.
- All user-facing text goes in `res/values/strings.xml` with a Simplified Chinese translation in `res/values-zh-rCN/strings.xml`.

## Code style

- Kotlin naming conventions; feature-based packages; `StateFlow` over `LiveData`.
- New files carry no licence header. Ported files keep theirs.
- Tests are named `should_x_when_y`.
- Comments only for constraints the code cannot show.
