# Rage (B4, resource only) - 2026-10-11

Scope: the third resource next to reiatsu. It fills from damage taken and from successful parries, drains out of combat, resets on death and logout, has a bar in the HUD and a spend/canSpend extension point. It does NOT gate shikai/bankai (plan question F3 "replace or add to the reiatsu requirement" is still open) and no ability spends it. Damage DEALT is not counted yet (the plan names it; the task asked for taken + parry only).

## Numbers (all in `RageConfig.defaults()`, unit = tenths of a point, bar 1000 = 100.0)
No numbers existed in PLAN/ART_BIBLE/TASKS_BACKLOG, so these are our defaults, UNVERIFIED by play.

| Key | Default | Meaning |
|---|---|---|
| maxTenths | 1000 | bar size (100.0) |
| damageTenthsPerHp | 5 | 0.5 point per hp taken (= 50 percent of the damage) |
| maxGainPerHitTenths | 100 | cap of one hit (reached at 20 hp) |
| parryGainTenths | 150 | fixed gain per successful parry |
| decayDelayTicks | 100 | 5 s without damage taken or a parry before draining |
| decayIntervalTicks | 20 | drain step every second |
| decayTenths | 10 | 1.0 point per step (full bar empties in 100 s) |

Feel: a 6 hp hit = 3.0, a parry = 15.0, so ~7 parries or ~10 capped hits fill the bar. The config is a code constant `RageManager.CONFIG` (same style as `BlockParry.CONFIG`); there is no config file in the mod. CombatConfig was not extended (its constructor is used in tests); Rage has its own record.

## Code
- core (pure, CorePurityTest passes): `core/rage/RageConfig`, `RageState` (clamped 0..max, may be spent to 0, unlike reiatsu), `RageMeter` (`onDamageTaken(hp, tick)`, `onParry(tick)`, `tick(now)`, `canSpend`, `spend`, `reset`). Clock-free, tick passed in; catches up on big tick jumps.
- server: `server/RageManager` (init from `ReiatsuTest.onInitialize` after `ZanpakutoManager.init`).
  - Parry: `ParryBus.register`, gain for `ParryEvent.defender`. A parried hit is cancelled in `ALLOW_DAMAGE`, so it gives no damage rage.
  - Damage: Fabric `ServerLivingEntityEvents.AFTER_DAMAGE` (signature verified with javap: entity, source, baseDamageTaken, damageTaken, blocked). It sees the amount that really landed: a blocked hit counts at the reduced amount (the reentrant `p.damage` in `ZanpakutoManager.allowDamage`), a hit eaten by the shikai barrier counts nothing, vanilla-shield `blocked` counts nothing, `BYPASSES_INVULNERABILITY` sources (void, /kill) are skipped. `ZanpakutoManager` and `BlockParry` were not touched.
  - Tick: drain in `END_SERVER_TICK`; reset on `AFTER_DEATH`, JOIN, and the meter is dropped on DISCONNECT / server stop.
  - API for abilities: `RageManager.canSpend(player, tenths)`, `spend(player, tenths)` (syncs at once), `meter(player)`.
- sync: new attachment `reiatsu_test:rage` (`ModAttachments.RAGE`, `RageData` value+max), owner-only, not persistent (zero on join/respawn). Written only when the value changed. Client read: `ClientState.rage()`.
- HUD: `PlateHud.drawRage`, under the reiatsu value line (y0+37): 4 px dark-brown backing with a dark-orange fill (#C2410C to #FF6A2B), pulses light orange at full (not with reduceMotion), eases to the synced value, label "Rage 45" below. Plain filled rects through the existing atlas pixel sheet, no new texture. Hidden while the bar is empty. `LegacyHud` (hud=false fallback) has no Rage bar. Lang keys `hud.reiatsu_test.rage` en "Rage", ru "Ярость".
- tests: `core/rage/RageMeterTest` (16): gain, cap, NaN/negative, parry, clamp, drain delay and steps, combat resets timer, full-bar hit counts as combat, big tick jump, spend, reset, config validation.

## To check in game (UNVERIFIED)
1. Take a hit (mob/other player): bar appears and grows ~0.5 per hp; label updates.
2. Parry (right mouse, hit within 0.2 s): +15, stays after.
3. 5 s with no hits: starts draining 1.0/s; any new hit restarts the 5 s.
4. Death/respawn and relog: bar is 0.
5. Plate layout: the bar at y0+37 vs the voice-phrase toast (x0+24, y0+36, drawn after, so it covers the bar while shown), compact mode, and the mic icon (x0+9, y0+38) that sits left of the bar.
6. Barrier shikai (Byakuya): fully absorbed hits give no Rage (by design).
7. Attachment sync with no `ServerPlayerEntity` spam: server log has one `[rage] parry` line per parry only.

## Open
F3 (rage replaces or adds to the reiatsu requirement), thresholds 1/2 opening shikai/bankai, damage dealt as a source, a command for tests (`/reiatsu rage`), Rage in `LegacyHud`.
