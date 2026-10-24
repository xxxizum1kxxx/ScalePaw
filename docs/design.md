# ScalePaw design

ScalePaw follows the Yoin design language (see Yoin's `docs/design.md`) and PiliPaw's conventions. This file records what applies here and the decisions specific to a body-weight tracker.

## Principles

1. **M3 Expressive first.** `LoadingIndicator` instead of spinners, `ButtonGroup` and `ToggleButton` for choices, `MaterialShapes` for avatars and status badges, `LargeFlexibleTopAppBar` on pushed pages, press-morphing icon buttons.
2. **Motion is springs.** `YoinMotion.defaultSpatialSpec()` for movement and size, `defaultEffectsSpec()` for colour and alpha. Pages push with a short horizontal slide; system back drives NavDisplay's predictive pop. Charts reveal their lines with an effects spring when data changes.
3. **Colour comes from M3 tokens.** The scheme is built from the wallpaper's seed (or a chosen seed) by `ExpressiveColorSchemeFactory`; the user can switch to the system's tonal scheme. Metric colours are the only fixed hues, and they appear as tints on surfaces, never as large fills.
4. **Colour blocks, not strokes.** No dividers or outlined cards. Groups are `surfaceContainer*` blocks with `YoinContainerShapes.Card`; rows inside a group use `ListRow` corners with 2dp gaps.
5. **Typography** is always `MaterialTheme.typography.*` in Google Sans Flex. Numbers that matter (current weight, a metric's value) use display or headline sizes with tabular figures; units sit beside them in a smaller, muted style.
6. **Copy is labels.** Short nouns, no narration, no encouragement. Explanations only where a choice has consequences (deleting, restoring, pairing).
7. **Haptics** accompany every toggle, selection, chart scrub tick, save and delete.

## Screens

- **Main shell**: five tabs (overview, chart, table, statistics, insights) in a floating navigation bar; on expanded widths the bar becomes a rail and list screens gain a detail pane.
- **Overview**: the latest weight as the hero, its change and trend, a compact chart, pinned metric tiles with evaluation colour, goals, and recent entries.
- **Measurement**: one form with every enabled metric; derived metrics show as read-only values with the inputs they need.
- **Chart**: metric chips, range toggle, optional smoothing, trend and goal lines; scrubbing shows the values at that day.
- **Table**: grouped by none/day/week/month/year with averages per period; tap a period to see its entries.
- **Statistics / compare / insights**: ranges, averages and changes; two-date comparison; long-term analyses with confidence labels.
- **Settings**: general, profiles, metrics, chart, data (CSV, backup, automatic backup), reminder, goals, Bluetooth, about.

## Bluetooth

- One status card at the top of the Bluetooth page; its shape morphs and its colour moves to `errorContainer` on failure.
- Readings save without confirmation and surface as an app-wide snackbar, so a weigh-in works from any screen.
- When a driver needs the user (choose a scale user slot, enter a consent code), an app-wide dialog asks; the driver waits.
- Driver-specific settings (keys, slots) render below the saved scale using the driver's own UI, restyled with Yoin fields.
