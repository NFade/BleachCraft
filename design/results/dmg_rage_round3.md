# Round 3: ability damage to players, Rage gain and Rage HUD (2026-10-12)

Game and server were NOT run (both open at the user). Everything marked UNVERIFIED needs a check in game.

## 6) Why abilities did not hurt the other player

Evidence from `mod/run-server/logs/latest.log` (session 23:35 to 23:43): both players switched to Survival at 23:38, then Player25 cast TSUKISHIRO, HAKUREN, SHIRAFUNE and TestB cast MODE_ATTACK, SCATTER-class abilities and SHAKKAHO, all `OK ok`. No `/reiatsu pvp true` was ever issued. server.properties has `pvp=true`.

Cause (confirmed by reading the code): `Tuning.affectPlayers` defaulted to `false`, and `Targeting.valid` returns false for every `PlayerEntity` while it is false. Every area query (`cylinder`, `sphere`, `segment`, `firstOnRay`, kido `trace`) goes through `valid`, so the other player was never even a candidate, in all abilities of Rukia (tsukishiro, hakuren, shirafune, absolute zero freeze and shards), Byakuya (petal swarm attack, scatter, hakuteiken, senkei is disabled) and the kido (byakurai trace, shakkaho burst, sai). The mod had a switch, `/reiatsu pvp true`, that nobody knew about. Shunpo has no contact damage in the code (nothing to fix).

Other things that can stop a hit, all checked:

| Path | Result |
|---|---|
| Creative target | Vanilla `isInvulnerableTo`: Creative players ignore normal damage (INDIRECT_MAGIC, FREEZE). EXPECTED, not a bug. The first 7 minutes of the log were Creative for both players. Use Survival. |
| `ZanpakutoManager.allowDamage` | Only reacts to the TARGET's own sword parry (cancels a MELEE hit, abilities are `OTHER` and are never parried or blocked) and to the target's Byakuya barrier (shikai mode BARRIER absorbs it with its reiatsu, by design). |
| Spawn / respawn protection (`joinInvulnerabilityTicks`) and scoreboard team friendly fire | Vanilla; named in the log as INVULNERABLE / TEAM. |
| i-frames (`timeUntilRegen`) | Freeze abilities (`resetInvulnerability=false`) can be eaten by i-frames of a target just hit; named IFRAMES. |
| `pvp=false` in server.properties | `Targeting.refusal` SERVER_PVP_OFF. |

## What was changed

- `server/Tuning.affectPlayers` now defaults to **true** (still needs `pvp=true`). `/reiatsu pvp false` turns player hits off for tests.
- New pure core `core/combat/HitRules` (`refusal(...)` target selection, `outcome(...)` explanation of one `damage()` call) with `HitRulesTest` (8). `Targeting.valid` is now `refusal(...) == OK`.
- New `server/DmgLog` and `Targeting.hurt` rewrite: every ability hit writes one INFO line, and players the area query leaves out for a config reason are named.
- `ZanpakutoManager.allowDamage` leaves a veto note (PARRY, BARRIER, BLOCKED) for the log.
- `AbilityExecutor.run` and `KidoManager.cast/burst` label the lines with the ability name (`tsukishiro#1`, `hakuren#3`, `kido:byakurai`, `kido:shakkaho-burst`...).
- Dev command (op): `/reiatsu dev dummy_hit <targets> [hp]` (default 2 hp, INDIRECT_MAGIC, attributed to the caster, resets i-frames) answers in chat and in the log with the outcome and the target-filter verdict. Example `/reiatsu dev dummy_hit TestB 4`. Also `/reiatsu dev rage <0..100>` sets your own Rage bar for HUD tests.

### How to read the `[dmg]` log (server log `mod/run-server/logs/latest.log`)

```
[dmg] tsukishiro#1 Player25 -> TestB asked=4.0 lost=4.0 result=DAMAGED
[dmg] hakuren#2 Player25 -> TestB asked=3.0 lost=0.0 result=CREATIVE
[dmg] kido:byakurai TestB -> Player25 asked=6.0 lost=0.0 result=PARRY
[dmg] mode_attack#0 TestB -> Player25 refused=PLAYERS_OFF
```

`lost` is the real health + absorption lost. Results: DAMAGED, CREATIVE, INVULNERABLE (spawn protection etc.), TEAM, PARRY, BARRIER, BLOCKED (sword block lowered it, `lost` may still be above zero), IFRAMES, ZERO_AMOUNT, NO_HEALTH_LOST (armor ate it), REFUSED_UNKNOWN (another mod or the mixin shield). `refused=` lines (players only) are SERVER_PVP_OFF, PLAYERS_OFF, IMMUNE_TYPE, SPECTATOR; a player who is simply out of range is not listed, mobs are never listed.

## 7) Rage

### Why Rage did not grow on a player hit (found in the code)
1. Only damage TAKEN counted; damage dealt was not implemented ("Damage DEALT is not counted yet" in rage.md). The attacker got nothing at all.
2. Fabric does not fire `AFTER_DAMAGE` for a hit that kills the entity, so killing blows (the usual end of a mob fight) gave nothing to anyone; small mobs die in one or two hits.
3. Taken rage was 0.5 per hp on a 4 px bar: a 6 hp hit = 3 points, barely visible.
4. Creative: damage is cancelled before AFTER_DAMAGE, so no Rage (expected).
Better Combat calls the vanilla attack, so its hits reach AFTER_DAMAGE normally (no BC bypass was found; UNVERIFIED in game, check the `[rage]` log).

### New numbers (`RageConfig.defaults()`, unit tenths, bar 1000 = 100)
| Source | Old | New |
|---|---|---|
| Damage dealt (sword and abilities, players and mobs) | 0 | **0.4 point per hp, cap 8 per hit** |
| Damage taken | 0.5 per hp, cap 10 | **0.8 per hp, cap 15** |
| Parry | +15 | **+20** |
| Drain | 1.0 per s after 5 s without combat | unchanged (dealing damage also counts as combat) |

A 6 hp sword hit gives 2.4, a 20 hp ability hit 8. 5 parries fill the bar.

Code: `RageManager` listens to ALLOW_DAMAGE (remembers a player-caused hit: victim, asked amount, health), AFTER_DAMAGE (taken for the victim player, dealt for `source.getAttacker()` player, never self) and AFTER_DEATH (settles the killing blow: dealt = min(asked, health before)). Every gain logs:

```
[rage] +2.4 from dealt 6.0 hp to Zombie (Player25: 12.4/100.0)
[rage] +4.8 from taken 6.0 hp from player/TestB (Player25: 17.2/100.0)
[rage] +2.0 from dealt-kill 5.0 hp to Cow (Player25: 19.2/100.0)
[rage] +20.0 from parry (TestB: 20.0/100.0)
```
Tests: `RageMeterTest` rewritten (23), covers dealt, caps, combat timer, devSet.

### HUD (`client/hud/PlateHud.drawRage`, no new texture: primitives through the pixel sheet)
Row under the reiatsu value line at y0+37 (non-compact 128 px, compact 96 px wide): 9x9 pixel flame icon (brighter with the fill, white-hot on full), framed bar (ink outline, copper rim, dark well) with a position gradient red, orange, amber, top gloss line, bottom shade, a slow sheen band, quarter ticks at 25/50/75 percent, the value (0..100) right of the bar, caption "Ярость"/"Rage" (existing lang key, 0.75 scale) under the bar. Animation: fill rises fast and falls slower, a pale trail shows what was lost, every gain flashes the fill, fade in/out, full bar pulses (glow rim, brighter fill/number/icon). `reduceMotion` turns animation, sheen and pulse off. The row steps below the voice toast while the toast is up (same height rule as `VoiceRowHud.drawToast`), and `rageVisible()` (voice status line moves to y0+54) is now true only when the bar is really drawn. `VoiceRowHud` itself was not touched. LegacyHud (hud=false) still has no bar.

## To check in game (UNVERIFIED)
1. Both players in Survival, `/gamemode survival`; cast any ability at the other player: expect hearts lost and `result=DAMAGED` lines. Without casting: `/reiatsu dev dummy_hit TestB 4`.
2. Switch the target to Creative: `result=CREATIVE` (expected).
3. Target holds right mouse (parry window) and you hit with the sword: PARRY is only for melee; abilities ignore parry. Byakuya shikai barrier: `result=BARRIER`.
4. Hit a mob with the sword: `[rage] +... from dealt`, bar grows; kill it: `dealt-kill`. Hit the other player: both `dealt` (attacker) and `taken` (victim) lines.
5. HUD: flame, gradient, flash on gain, pulse at 100 (`/reiatsu dev rage 100`), trail when it drains, caption position vs the voice status line (y0+54) and the toast (the bar should step under it), compact mode, reduceMotion.
6. Tuning is a code change in `RageConfig.defaults()` (no config file).
