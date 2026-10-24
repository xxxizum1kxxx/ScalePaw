# ScalePaw

ScalePaw is a body-weight and body-composition tracker for Android that talks to Bluetooth scales. It is a Jetpack Compose rewrite of [openScale](https://github.com/oliexdev/openScale) in the Yoin design language: Material 3 Expressive, dynamic colour, Google Sans Flex, continuous corners, spring motion and Yoin Symbols.

基于 [oliexdev/openScale](https://github.com/oliexdev/openScale) 重写，采用 [Yoin](https://github.com/p2o51/Yoin) / [PiliPaw](https://github.com/p2o51/PiliPaw) 的设计语言与工程规范。ScalePaw 以 GPL-3.0-or-later 发布，见 [LICENSE](LICENSE)。

## Features

- Multiple profiles, each with their own measurements, goals and body formulas
- 30 built-in metrics plus your own; BMI, LBM, WHR, WHtR, BMR, TDEE and caliper body fat are derived automatically
- Overview, chart (smoothing, trend and goal lines), table with day/week/month/year grouping, statistics, comparison of two dates, and insights (trend, rate, plateaus, weekday and seasonal patterns, anomalies, body-composition pattern)
- Bluetooth scales: the full openScale driver set (66 handlers covering more than 75 scale families) over BLE GATT, BLE advertisements and classic SPP
- Experimental Withings driver (Body Smart, Body+ and related models): reads stored measurements over Bluetooth with the account key (kl), fetched by signing in or pasted by hand. It is read-only and unverified on real hardware; see the debugging guide in [English](docs/withings-debug.md) or [简体中文](docs/withings-debug.zh-CN.md)
- CSV import and export compatible with openScale, full backup and restore, automatic backups to a folder you choose
- Weigh-in reminders and a home-screen widget
- English and Simplified Chinese

## Build

```sh
export JAVA_HOME=/path/to/jdk-17-or-newer
./gradlew :app:assembleDebug        # debug
./gradlew :app:assembleRelease      # release, signed with the debug key
./gradlew :app:testDebugUnitTest    # unit tests, including the scale driver test vectors
```

Requires the Android SDK (compileSdk 36). minSdk is 31.

## Layout

- `app/src/main/java/com/izum1k/scalepaw/data`: Room entities and DAOs, the repository, DataStore settings, CSV/backup transfer
- `domain`: body formulas, derived metrics, evaluation tables, aggregation, smoothing and insights
- `ble`: scanning, GATT/broadcast/SPP transports and `ScaleManager`, which turns a driver's reading into a measurement
- `ble/withings`: the experimental Withings WPP driver (framing, authentication, record parsing, session state machine)
- `ble/openscale`: the openScale scale drivers (`scales`), their helper libraries (`libs`) and a thin compatibility layer (`vocab`, `util`)
- `feature/*`: one package per screen or area
- `ui`: theme, shared components, the chart and the navigation shell
- `yoin-symbols`: the Yoin Symbols icon library, vendored as a module
- `docs/design.md`: design rules; `AGENTS.md`: conventions for contributors and coding agents

## Credits

- [openScale](https://github.com/oliexdev/openScale) by olie.xdev and contributors (GPL-3.0-or-later): the feature set, CSV format, and the Bluetooth scale drivers under `app/src/main/java/com/izum1k/scalepaw/ble/openscale/{scales,libs}`, ported with package and UI changes plus these fixes: hex key fields use a Latin keyboard (S400, S800, Huawei), and the S400 publishes Packet A on its own when the scale never sends Packet B. Their test vectors live under `app/src/test/java/com/izum1k/scalepaw/ble/openscale`. Driver strings in `res/values*/strings_drivers.xml` come from openScale too.
- [Yoin](https://github.com/p2o51/Yoin) by Chen Wuyi (Apache-2.0): the theme (`ui/theme`: colour scheme factory, animated colour scheme, motion, shapes, continuous corners, typography) and the design language. See [NOTICE](NOTICE).
- [PiliPaw](https://github.com/p2o51/PiliPaw) by Chen Wuyi (GPL-3.0): system bars, wallpaper seeds, adaptive helpers, formatting helpers, the floating navigation bar, and `PiliSymbols`.
- [material-color-utilities](https://github.com/material-foundation/material-color-utilities) by Google (Apache-2.0): the colour code under `app/src/main/java/{contrast,dislike,dynamiccolor,hct,palettes,scheme,temperature,utils}`.
- [Google Sans Flex](https://github.com/googlefonts/googlesans-flex) (SIL OFL 1.1, see `licenses/GoogleSansFlex-OFL.txt`).
- [Yoin Symbols](https://github.com/p2o51/yoin-symbols) (MIT, see `yoin-symbols/LICENSE`).
- [Bouncy Castle](https://www.bouncycastle.org/) (MIT): AES-CCM for the Xiaomi S400 driver.
- [withings-local](https://github.com/pierosavi/withings-local) by pierosavi and [openwithings](https://github.com/totruok/openwithings) (both MIT): protocol notes for the Withings driver, which is written independently.
