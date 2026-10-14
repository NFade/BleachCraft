# Rukia shikai: aim at the END of the cast (branch gin-uryu)

Request: every Rukia SHIKAI skill applies along the look of the end of the cast (as Shirafune already did); the consecutive-hit passive gets a little weaker.

## How Shirafune does it (the pattern)

`AbilitySpec.aimsAtRelease()` (param `aimAtRelease`): `AbilityExecutor.run` re-samples the `CastContext` (`resampled(p)`) at the last phase and sends the effect event THEN (`ZanpakutoManager.apply` skips the press-time broadcast); the client timeline starts at the hit, a local telegraph covers the cast time. Hakuteiken (Byakuya) instead keeps the press event and uses `CastContext.releaseAim(p)` (one cached re-sample shared by all phases) plus `HakuteikenFx.refreshAim` (client re-reads the owner entity look).

## What changed

- **Hakuren (line wave)**: same mechanism as Hakuteiken. It is a multi-phase wave (8 steps, ticks 20..34) with a 1.0 s wind-up on the client, so the aim is taken at phase 0 (tick 20, the moment the wave LEAVES), not at the last step and not at the key press.
  - Server `server/AbilityExecutor.java:91`: `ctx = ctx.releaseAim(p)` at the top of `hakuren`. Position (wave start, frost layer height), flat direction, wall clip (`IceWall.clipDistance`), hit set and block budget all come from that one read and are shared by the 8 steps. Damage, cost, cooldown, offsets, length, width unchanged.
  - Client `client/fx/RukiaShikaiFx.java:~216-290`: `f`, `rg` (direction, right) and new `cp` (feet x,y,z) are rewritten in place at `RukiaAim.aimSeconds(spec)` = 1.0 s from the live owner entity (`EffectTimeline.caster()`: look and lerped feet, so remote viewers see it too), just before the first step is drawn. Every wave visual from 1.0 s on (crystals, flares, mist, frost, shatter, sounds) uses them. Owner not loaded: the press values stay. The wind-up (ground puncture, the four punctures in front at 0.3 s, gather at the blade) stays at the press direction on purpose (it is cast time telegraph, not hit area).
  - The effect event is NOT moved (the wind-up needs it at the press).
- **Tsukishiro (circle)**: decision, NOT changed. It is a cylinder around the caster's feet; it has no direction, so there is no look to re-read. Re-centring it on the caster at tick 20 would move the hit circle away from the ring and sigil that the client draws from 0.2 s at the press spot (a visual mismatch) and would change the gameplay (the circle follows). Left as is, documented in `core/fx/RukiaAim.java`. If a follow-the-caster circle is wanted, that is a separate change (client ring has to follow the entity until 1.0 s).
- **Shirafune**: already done (r8 item 3). ICE_WALL (bankai) untouched.
- New pure `core/fx/RukiaAim.java`: `aimsAtEnd(id)`, `aimPhase(spec)` (Shirafune release phase, Hakuren 0, others -1), `aimSeconds(spec)` (Hakuren 1.0, Shirafune 0.6), `flat(lx, lz, fallback, out)` (horizontal unit direction of a look, fallback when looking straight up/down). The client reads the read time from the spec, not a literal.

## Passive for consecutive hits (Rukia FrostMomentum, R11 T6)

Config section `rukia.passive` (`core/combat/FrostMomentum.Config.defaults()`), applied by `server/RukiaMomentum`. Only the bonus per stack was changed (-20 percent), stack counts and burn-out times unchanged:

| tier | bonus per stack | max stacks | max bonus | expire |
|---|---|---|---|---|
| shikai old | 0.05 | 4 | +20 percent (x1.20) | 80 ticks |
| shikai new | 0.04 | 4 | +16 percent (x1.16) | 80 ticks |
| bankai old | 0.08 | 5 | +40 percent (x1.40) | 100 ticks |
| bankai new | 0.064 | 5 | +32 percent (x1.32) | 100 ticks |

Both tiers were lowered (it is one passive, and shikai skills used in bankai also get stronger). If only shikai should change, set bankai back to 0.08 in `FrostMomentum.java:43` and drop the bankai rule in `ConfigMigrations.java:50`.
Existing config files hold the old numbers, which beat the code defaults, so `ConfigMigrations.SERVER_VERSION` 2 -> 3 with two rules (`core/config/ConfigMigrations.java:49-50`): a value still equal to the old default (0.05 / 0.08) becomes 0.04 / 0.064 on the next start or `/reiatsu reload`, a value the player changed stays; a backup `reiatsu_test.json.bak_v2` is written.

## Tests

- `core/fx/RukiaAimTest` (new, 6): which abilities aim at the end, Hakuren phase 0 / 1.0 s vs last step 1.7 s, Shirafune release 0.6 s, no-phase spec, wave axis follows the end look (a point west lies on the new axis, 10 blocks beside the old), `flat` unit length over yaw/pitch and the fallbacks.
- `FrostMomentumTest`: pinned numbers changed to the new values (defaults, multiplier table, one-tick-several-victims, bankai-to-shikai clamp, garbage-fallback) plus new `theBonusPerStackIsAFifthLowerThanBeforeTheEndOfCastAimRound`.
- `ConfigMigrationsTest`: new test for the two passive rules (old defaults move, user value stays, rule value = code default).
- `./gradlew build` passes.

## UNVERIFIED in game (the game was not launched)

- Hakuren client read time (client clock 1.0 s after the packet arrives) vs the server read at tick 20: they differ by about the one-way latency, so a flick exactly at that moment can differ by a few degrees. For the local player the client look is also ahead of the server look by up to a round trip.
- The wind-up punctures (press direction) then the wave turning to a new direction may look odd if the player turns hard during the 1 s.
- In-place `cp`/`f` rewrite: only the wave section uses `cp`; the 0.0 s ground puncture and the 0.3 s punctures still use the press `c`/`f`.
- Real frost block layer follows the new position/direction (server side, same code path as before with another ctx).
- Config migration on the dev config (`mod/run/config/reiatsu_test.json`, now 0.05 / 0.08).
- Feel of the passive at -20 percent.

## Manual test checklist

1. Rukia shikai, press Hakuren, turn 90 degrees during the 1 s: the ice wave and the damaged mobs are on the NEW look, not the old one. A mob standing in the old direction is not hit.
2. Same with a second player watching: the wave on their screen goes where the caster looked at tick 20.
3. Hakuren against a wall in the new direction: stops at the ice wall / absorbs as before.
4. Walk/dash during the cast: the wave starts at the caster's new position.
5. Tsukishiro: unchanged (ring at the press spot, circle hits there).
6. Shirafune: still hits along the end look.
7. Start the game with the old config: log line `[config] updated 2 keys from v2 to v3`, `[momentum] config reloaded ... bonusPerStack=0.04 ... 0.064`; five fast hits as Rukia shikai: damage x1.16 at 4 stacks (was x1.20).
