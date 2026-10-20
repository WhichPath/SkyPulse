# Backdrop (vendored subset)

This package is a trimmed, Android-only vendor of
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) **Backdrop**
(`io.github.kyant0:backdrop`, Apache-2.0).

Reason for vendoring: upstream 2.x is built against Kotlin 2.4 / Compose 1.12,
while RidgeCast v1.0.5 uses Kotlin 2.0 / Compose 1.7. The source was adapted to
remove KMP `expect/actual`, context receivers, and `kyant-shapes`; behavior is
otherwise the same backdrop/lens/blur/optics pipeline.

Included: LayerBackdrop, drawBackdrop, lens, blur, vibrancy, highlight, shadow,
inner shadow, and their supporting internals.

Local modifications (RidgeCast v1.0.7):
- `HighlightStyle.Default` gained `intensity` and an optional `secondary` light,
  and the default highlight AGSL shader was extended to composite **two
  independent light axes** (sun + moon). The runtime shader cache key was
  bumped to `DefaultDual` accordingly.

Copyright: Kyant and AndroidLiquidGlass contributors. Licensed under Apache-2.0;
see `LICENSE`.
