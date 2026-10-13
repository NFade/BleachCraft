# Hakuteiken beam aim (branch fix/hakuteiken-aim)

Report: the white beam of Shukei: Hakuteiken (key I) looks slightly to the RIGHT of the crosshair.

## Root cause (with numbers)

The server hit line was never biased: `CastContext` takes `eye` and `dir = getRotationVec`, the hit is `Targeting.segment(eye, dir, 0..|eye-aim|, halfWidth 2.0)`. The bias is in the client visual (`HakuteikenFx.pose`, `strike`):

1. Origin offset to the right. The beam (and the blade mesh in WingHaloRenderer, which reads the same `pose.hx/hy/hz`) starts at a "right hand" point: feet + 0.30 to the right (`hak.handSide`) + 1.40 up + look*0.6. Relative to the eye (1.62) that is 0.30 right, 0.22 down, 0.6 forward. The camera ray (crosshair) goes through the eye, the beam goes hand -> end, so in first person the beam is a line from the lower right that only meets the crosshair at its far end. Not a miscalculated direction, a parallax from a lateral anchor.
2. The shoulder offset was rotated by the BODY yaw (`bodyYaw`), not the head yaw. Body lags the head up to ~50 deg: lateral part 0.30*cos(d) = 0.28 / 0.23 / 0.19 m at d = 20 / 40 / 50 deg, plus 0.10 / 0.19 / 0.23 m sideways-forward drift, so the anchor wandered around the eye while turning.
3. Press-time aim. The effect event goes out at the key press and carries press-time aim (`end` fixed in `onEvent`); the server hit (phase 0 at tick 30) and burst (phase 1, tick 36) also used the press-time `CastContext`. The strike lands 1.5 s later. If the player turns during the condensing, both the beam and the hit go to where the crosshair WAS (a 10 deg turn = 2.6 m at 15 m). Not the cause of a constant right bias, but it breaks "beam = crosshair" the same way Shirafune did before r8 item 3.

Visual lateral/angular error of the OLD beam (convergent, target L m away, yaw 0 pitch 0; right / down in m and the angle to the crosshair as seen from the eye, depth d along the look):

| target L | depth d | right | down | total deg | right-only deg |
|---|---|---|---|---|---|
| 28 (miss, max range) | 5 | 0.252 | 0.185 | 3.57 | 2.88 |
| 28 | 15 | 0.142 | 0.104 | 0.67 | 0.54 |
| 28 | 30 | beyond the beam end (28) | | | |
| 15 (hit) | 5 | 0.208 | 0.153 | 2.96 | 2.39 |
| 15 | 15 | 0 (end) | | | |

If the beam were parallel to the look the right-only error would be 3.43 / 1.15 / 0.57 deg at 5 / 15 / 30 m. The start point itself is 0.30 m right, 0.22 m down at 0.6 m depth (about 27 deg off the crosshair from the eye). Server hit line vs visual: at most 0.37 m apart (well inside half width 2.0), so damage was not affected, only what you see.

## Fix

- New pure helper `core/fx/HakuteikenAim.java` (no Minecraft imports): `reach` (min of range and distance to the block), `right` (horizontal right of the LOOK), `origin` (eye + right*side - up*down + look*forward, look frame, not body frame), `target` (point on the crosshair ray), `direction`, `offsetAtDepth` (right/up/angle of a beam point relative to the crosshair, for tests). Used by both sides: client (`HakuteikenFx.pose`, `refreshAim`) and server (`AbilityExecutor.hakuteiken` length, `CastContext.effectParams` size2).
- Client `HakuteikenFx.pose`: hand built with `HakuteikenAim.origin` in the head-yaw frame. Owner's own first person view uses side `hak.handSideFp` = 0.0 (tunable), so the beam lies in the vertical plane of the crosshair: right bias 0 at every depth (it comes from below the crosshair and ends exactly on it). Other viewers and third person keep the 0.30 right-hand anchor, converging on the crosshair target point. Tunables `hak.handSide`, `hak.handDown` (replaces `hak.handHeight`), `hak.handForward`.
- Aim at the strike, not the press. Client: `HakuteikenFx.refreshAim` re-reads the owner's look every frame until the strike and once more inside `strike()`, clips the look ray with a block raycast to `range` like the server, updates `st.end` and `st.lineLength` (so the white line, the blade direction and the burst follow the crosshair; frozen with `aimLocked` from the strike). `hak.liveAim` = 0 disables it. Owner entity not loaded (harness fake owner) -> the press aim stays. Server: `AbilityExecutor.hakuteiken` uses `CastContext.releaseAim(p)` (new, caches one `resampled(p)` read at the first phase that asks), so the line (tick 30) and the burst (tick 36) share the strike-time eye/look/aim. The effect event is NOT moved (the condensing still starts at the press).
- Not changed: damage values, `HALF_WIDTH` (2.0), `RADIUS`, `LENGTH`, balance config, WingHaloRenderer / wing rendering (it only reads `pose.h*`, `st.end`, `st.lineLength`; `lineLength` lost `final`).

## Files

- added `mod/src/main/java/dev/minebleach/reiatsutest/core/fx/HakuteikenAim.java`
- added `mod/src/test/java/dev/minebleach/reiatsutest/core/fx/HakuteikenAimTest.java`
- changed `mod/src/client/java/dev/minebleach/reiatsutest/client/fx/HakuteikenFx.java`
- changed `mod/src/main/java/dev/minebleach/reiatsutest/server/AbilityExecutor.java`
- changed `mod/src/main/java/dev/minebleach/reiatsutest/server/CastContext.java`

## Tests (HakuteikenAimTest, 7)

right-vector convention; origin stays in the look frame (side and forward components) at yaw -180..180 x pitch -80..80; first-person beam has 0 right bias at depths 1, 3, half, full reach for several yaw/pitch and reach 5/15/28, ends at angle 0; third-person beam converges (0.30*(28-d)/(28-0.6), 0 at the end, NaN beyond); beam direction passes through the crosshair target and matches the parallax angle; the old-anchor numbers above; `reach` clipping.

## Build

`./gradlew build` from `mod/`: BUILD SUCCESSFUL (one earlier run failed on my own test assumption at pitch -12; fixed to pitch 0). Whole suite incl. CorePurityTest passes.

## UNVERIFIED (never seen in a real game frame, the game was not launched)

- How the first person beam looks with side 0 (starts 0.22 below the crosshair at 0.6 m, may read as coming from the bottom centre of the screen; tune `hak.handSideFp` / `hak.handDown`).
- WingHaloRenderer blade mesh and the condensing petals now converge on the new hand point in first person; not checked visually.
- Strike-time aiming: client look at the strike vs server look at tick 30 differ by about the round trip time (rotation packets), so a fast flick near the strike can still differ by a few tenths of a degree; remote viewers use the interpolated owner look.
- Sneaking: hand height now follows the eye height (eye - 0.22), previously feet + 1.40; not checked.
- `getPerspective().isFirstPerson()` branch and the client block raycast match the server in real worlds.
