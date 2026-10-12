# Byakuya round 5: bankai size, attack swarm timing, stuck return (2026-10-12)

Status: compiled (`compileClientJava`), unit tests for the touched code green. The game was NOT run: everything about look and feel is UNVERIFIED. Nothing committed.

## 4) Bankai rows were huge

Cause: not a regression. `git log` shows no change to `BankaiBladeRenderer`, `RowsFx`, `BladeRowLayout` since 4ca1320; the working tree changes of FxPlace/FxGround/FxEvents/ReleaseHold do not touch them. The shipped design simply is a giant blade: mesh `byakuya_bankai_blade.obj` is 8.0 m tall (ART_BIBLE B13, tip at z = 8.000, "giant bankai blade"), x scale 1.0 for rank 1 and 1.1 for rank 2 (up to 1.4 at 1000 blades), base 0.5 below ground, so about 7.5 to 8.3 blocks visible; corridor half width 6.0 blocks, spacing 1.1. At that size they read as towers next to trees (IMG13).

Fix: two hot-reload knobs (`run/fx_override.json`), read when the rows are created (a new bankai picks them up, rows already standing keep theirs):

| key | default now | was | meaning |
| --- | --- | --- | --- |
| `bankai.bladeScale` | 0.55 | 1.0 (implicit) | uniform scale of every blade (height and width). Rank 1: 8 x 0.55 = 4.4, visible about 3.9 blocks; rank 2: 4.84, visible about 4.3 |
| `rows.scale` | 0.65 | 1.0 (implicit) | spacing, corridor half width, rank step, stagger: half width 6.0 -> 3.9, spacing 1.1 -> 0.72, rank step 1.2 -> 0.78 |

Set `bankai.bladeScale` 1.0 and `rows.scale` 1.0 for the old look. Code: `RowsFx.bladeScaleSetting()/rowsScaleSetting()`, `State.bladeScale` (captured per rows state, the static buffer depends on it), `BladeRowLayout.of(n, seed, space)` (+ `corridorHalfWidth()`, old `of(n, seed)` = 1.0), `BankaiBladeRenderer` (basis scale, normal scale, rise/sink depth scaled, frustum box, tip glow sprite sizes x (0.4 + 0.6 scale)), `StormMath.Storm.heightScale` / `CondenseMath.Cloud.heightScale` (petal birth slots follow the shorter blade; times stay in blade space). Hilt scale (`bankai.hiltScale` 1.8) not touched. The 1000-blade tier gets 5 ranks: heights 4.4 to 6.2.

Test: `BladeRowLayoutTest.rowsScaleShrinks...`.

## Attack swarm (mode_attack, Z)

a) Range and timing (defaults in `FlightConfig.defaults()`, `BalanceConfig` MODE_ATTACK `AIM_RANGE`; the file `config/reiatsu_test.json` section `bladeFlight` overrides them, read at server start):

| key | was | now |
| --- | --- | --- |
| AIM_RANGE (flight range, aim ray) | 32 | 19 |
| speed / backSpeed | 30 / 30 b/s | 40 / 40 b/s |
| startDelayTicks | 4 | 3 |
| returnTick (not held) | 30 | 18 |
| maxTicks (held, hard limit) | 70 | 32 |
| turnDegPerSec | 200 | 240 (shorter window, same sweep of the crosshair) |

Cycle without holding: 3 windup + about 9 ticks out to 19 blocks, return at tick 18, about 9 ticks back = about 27 to 30 ticks = 1.35 to 1.5 s (test `defaultTimingGives...`: 26 to 32 ticks). Holding Z: steering from tick 3 to tick 32 (1.45 s), then the return, total about 2.0 s. Client ribbon lag `swarm.trackLag` 0.30 -> 0.22 s so the trailing petals arrive sooner. Landing burst phases (10, 12, 14, 20 ticks, `AbilityTest` pins them) were not changed: the tick 20 hit is at the cast's aim point while the head is already on its way back (about 15 blocks out for a 19 block cast).

The existing `mod/run-server/config/reiatsu_test.json` had the old numbers written out explicitly, so it was edited to the new ones (the running server does not see it until it restarts). `mod/run` and `mod/run2` have no bladeFlight section (defaults apply). Any other file with an explicit old section must be edited by hand or the section deleted.

b) Stuck on the return. Cause: `BladeFlights.run` raycast every head step and called `clipHead` in every phase. On BACK the head is `eye + dir * r`; if the caster moved/turned so the head ended up in or beside a block, the raycast started inside the block, `clipHead` reset `r` to the old distance every tick, and BACK has no timeout (`maxTicks` only starts BACK), so the head never reached the caster until the cast was cancelled by another cast.

Fix:
- `BladeFlight.clipHead` does nothing in BACK/DONE: the return ignores blocks (petals fly through them like spirits), only the damage sweep of the return still stops at the first block (`BladeFlights.run`).
- Watchdog: BACK ends by force after `maxBackTicks()` = ceil(range / backSpeed per tick) + 6 ticks.
- Outward: a head held at a block for 8 ticks without reaching the target (phase OUT, moved < 0.1 per tick) is recalled (`BladeFlight.recall()`, then BACK). Hanging next to a wall at the aimed point (LOITER) is not stuck.
- A server exception in the flight now also sends the DONE packet (the clients close the head path; the client already closes a head path after 1.5 s without packets).
- Flight end is always followed by the DONE head packet, the client closes the track and the swarm returns to its orbit (`SwarmFx.frame`).

Tests (`BladeFlightTest`, harness replays the server raycast): return with a pillar around the head and across the way home ends within the watchdog limit and the head is not left in the block; `clipHead` ignored on the return; wall in front of the target: recalled within 3 + 8 + 8 ticks and home; held key at a wall still ends by the hard limit; hanging at a wall surface is not recalled; recall/clip after the end are harmless; timing guard (cycle about 1.4 s, held at most 44 ticks).

## Tests run
`./gradlew compileClientJava` OK. Full `./gradlew test`: 600 tests, 21 failures, none mine: `RageMeterTest` (19) and `TransitionTest` I12/I6 belong to the parallel Rage/state work (files in flux). BladeFlightTest, BladeRowLayoutTest, HeadTrackTest, SteerAndSweepTest, CorePurityTest, AbilityTest pass.

## Check in game (all UNVERIFIED)
1. Bankai Byakuya: blades about 4 blocks, corridor about 8 wide, still dense; tip glow not oversized; rise looks like before (just smaller). Tune: `bankai.bladeScale`, `rows.scale`, `bankai.tipSize`.
2. Press Z at a mob 15 to 19 blocks away: blades reach it in about 0.5 s, come back by about 1.4 s. Hold Z: control until about 1.6 s, home by about 2 s. If too short, raise `maxTicks` / `returnTick` in the server config.
3. Stand next to a wall, cast along it and move/turn during the return: the swarm always returns, no petals hanging in the wall; log line `[steer] ... flight end (returned)`.
4. Storm (scatter) petals start from the shorter blades (heightScale), the burning dissolve passes top to bottom as before.
