# Shunpo micro-fix: afterimage size and screen rush

Date: 2026-10-11. Not run in game (rules). Everything visual is UNVERIFIED.

## 1. Afterimage size
- `ShunpoFx.render`: the model scale was a hard-coded `0.9375` (the vanilla `PlayerEntityRenderer` scale). It is now `ascale`, read from `shunpo.afterimageScale`, default **1.0**, clamped to 0.25..2.0.
- Consequence: with the default, the copies are 6.7 % taller than the live player (2.0 blocks of model vs 1.875). If the copies must match the live player exactly, set `shunpo.afterimageScale` to `0.9375`.
- Anchor unchanged: the `translate(0, -1.501, 0)` puts the feet at the origin for any scale, so the copies still stand on the path.
- Alpha (`ALPHA0` 0.6, `pow(1 - t, 1.4)`), `LIFE` 0.5 s and `STAGGER` 0.02 s are untouched.

## 2. Screen rush (first person, caster only)
- `ShunpoFx.play` calls `ScreenFx.shunpoRush(...)` only when `client.player.getId() == e.casterId()`, so other players' shunpo does not touch the local screen.
- `ScreenFx.shunpoRush`: stores a start time, a duration (clamped 0.1..1.0 s), a line level and a FOV kick. Returns at once with `reduceMotion` or `photosensitiveSafe` (both effects off). Both are multiplied by `screenFxScale`.
- Radial speed lines: the existing `speed_lines.png` texture, drawn in `renderUnderHud` (InGameHud HEAD) with additive blend. Alpha rises in 0.04 s and falls linearly to 0 at the end. Scale grows 1.0 to 1.12. This is a separate slot from `ScreenFx.speedLines` (StormFx), so the two do not overwrite each other.
- FOV kick: `ScreenFx.fovKickDeg()` rises in `min(0.06, 0.25 * dur)` s, then eases back to 0 (`FxMath.ios`). It is added by `GameRendererMixin` to `getFov(Camera, float, boolean)D` (Yarn name checked in `mappings.tiny`: `getFov`, intermediary `method_3196`). Only when `changingFov` is true, so the hand keeps its FOV and does not swim.
- The view is not blocked for long: the lines and the kick last `shunpo.speedFxTime` (default 0.3 s).

## Hot reload keys (`mod/run/fx_override.json`, read by `FxTune`, checked every 30 frames)
| key | default | meaning |
|---|---|---|
| `shunpo.afterimageScale` | 1.0 | model scale of the afterimages (0.25..2.0) |
| `shunpo.speedFx` | 0.6 | level of the radial lines (0 = off), x `screenFxScale` |
| `shunpo.speedFxTime` | 0.3 | duration in seconds of lines and kick (0.1..1.0) |
| `shunpo.fovKick` | 10.0 | FOV addition in degrees, x `screenFxScale` (suggested 8..12) |

Example: `{"shunpo.afterimageScale": 0.9375, "shunpo.fovKick": 8, "shunpo.speedFx": 0.4}`

## Files
- `mod/src/client/java/dev/minebleach/reiatsutest/client/fx/ShunpoFx.java`
- `mod/src/client/java/dev/minebleach/reiatsutest/client/fx/ScreenFx.java`
- `mod/src/client/java/dev/minebleach/reiatsutest/client/mixin/GameRendererMixin.java`

## Tests
- `./gradlew test` (JDK 21, GRADLE_USER_HOME on D:): exit 0, 380 tests, 0 failures, 0 errors. No new unit test: the new code is client rendering with no pure core part.
- Harness checks in `Phase4Harness` (D2, D7) still refer to the same fields (`liveImages`, `drawnLastFrame`, `maxDrawnAlpha`, `LIFE`, `lastTrailParticles`, `lastDistance`), which are unchanged.

## Not checked in game (UNVERIFIED)
- Look of the 2.0-block copies against the live player at default scale.
- Whether `speed_lines.png` reads as radial lines at the screen edges (the preview of the PNG was blank white, so its alpha layout was not seen).
- FOV kick feel at 10 degrees, and the interaction with sprint FOV and with Sodium/Iris.
