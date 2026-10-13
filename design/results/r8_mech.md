# r8 mechanics round (worktree r8-mech)

Six server/core changes from the user's play test. Nothing here was run in the game: every statement about in-game behaviour is UNVERIFIED. Build and unit tests are the only check (see the end).

All new numbers live in `core/state/MechSpec.java`, exposed through `BalanceConfig` (`abilityBlockReduction()`, `meleeHitReiatsu()`, `meleeHitTenths()`, `byakuyaAttackTickInterval()`, `barrierPush*()`); `BalanceConfig.withMech(...)` replaces them in tests. Pure rules are in `core/` (no Minecraft imports, `CorePurityTest` stays green); the server classes only read the game and call them.

## 1. Block against abilities (30 percent)

- Rule: `BlockRules.abilityDamage(blocking, damage, reduction)` (pure). `BalanceConfig.abilityBlockReduction() = 0.30`, clamped to 0..1.
- Where: `server/Targeting.hurt(...)`. It is THE damage function: every ability and kido hit goes through it (Tsukishiro, Hakuren, Shirafune, ice spike, the Rukia bankai field ticks, Byakuya attack landing and flight ticks, cage pulses, scatter, Hakuteiken, Byakurai, Shakkaho, the `/reiatsu` dev hit). Area hits, beams and damage over time ticks all call it once per victim per tick, so each is cut separately. If the target holds the block (`ZanpakutoItem.isBlocking(target)`: right mouse with a zanpakuto in use) the amount is multiplied by 0.70 BEFORE `LivingEntity.damage`. A `[dmg] ... block cut A -> B` line is logged, and the normal `[dmg]` line keeps `asked=` as the original amount.
- Not touched: melee (still `BlockParry`: parry window, then 50 percent), because `BlockRules.resolve` only handles `DamageKind.MELEE`; abilities are `DamageKind.OTHER`, so there is no double cut. There is no parry window for abilities.
- Composition (all multiplicative, so never above 100 percent): ability cut 30 percent, then in `ZanpakutoManager.allowDamage` the Byakuya barrier keeps 20 percent (cut 80), then vanilla armor, resistance and absorption. 10 HP ability on a blocking Byakuya inside the barrier: `10 * 0.7 * 0.2 = 1.4 HP` (86 percent cut), tested with the real `StateMachine.absorbBarrier`. Rage only gains from damage that landed (`AFTER_DAMAGE`), it does not scale damage, so there is nothing to compose with there.
- Gap (not fixed): vanilla freezing damage that follows `Targeting.chill` (frozen ticks above the threshold, 1 HP each 40 ticks, no attacker) is vanilla `freeze` damage and does not pass `Targeting.hurt`, so the block does not cut it. It is small.

## 2. Byakuya attack mode (Z), fewer damage ticks

- `AbilityHitGate` (pure, `core/combat`): per victim table; after a damage tick the victim is immune to THIS ability for `byakuyaAttackTickInterval` ticks (default 4 = 0.2 s). One gate per cast (`CastContext.hitGate`), so the caster and the ability are implied, and the landing burst phases (`AbilityExecutor.modeAttack`) and the flight sweep (`BladeFlights.sweepHits`) share it. It is its own table: vanilla `hurtTime` / `timeUntilRegen` are not used for the gate, so the victim is still open to every other source.
- Damage per tick unchanged: flight tick 0.5 HP (`FlightConfig.hitDamage`), landing tick 1.0 HP (`MODE_ATTACK` damage). Existing limits unchanged: flight cooldown 8 ticks (4 on a leg change), at most 6 flight hits, landing phases at ticks 10, 12, 14, 20.
- Where the "about 3 hearts" came from: 4 landing ticks of 1.0 (the sphere of radius 3 at the aim point, with `resetInvulnerability` true) plus up to 6 flight ticks of 0.5.
- Maximum for ONE victim standing in the whole flight (worst case: swept every tick from tick 3 to 42 and inside the landing sphere; the model is `AbilityHitGateTest.maxDamage`, it uses the real `HitCooldown`, `FlightConfig` and `AbilityHitGate`):

| | before | now (4 ticks) |
|---|---|---|
| key released early (head turns back at tick 18) | 6.5 HP (3.25 hearts): 4 landing + 5 flight ticks | 4.5 HP (2.25 hearts): 2 landing + 5 flight ticks |
| key held (turn back at tick 32) | 6.0 HP: 4 landing + 4 flight ticks | **5.0 HP (2.5 hearts)**: 3 landing + 4 flight ticks |

  So the new maximum per cast is **5.0 HP = 2.5 hearts** (was 6.5 HP = 3.25 hearts). A window of 4 ticks only removes the landing tick at 12 (2 ticks after the one at 10) and some flight/landing overlaps. If the user wants it clearly lower, the knob is `byakuyaAttackTickInterval`: 6 ticks gives 3.5 HP, 8 gives 2.5 HP, 10 gives 2.0 HP (same model; the test only asserts that a longer window cuts further).
- Shown in the log as fewer `[dmg] byakuya.shikai.mode_attack#...` lines; a refused tick is silent.

## 3. Rukia Shirafune aims when it is released

- New spec param `AbilityParams.AIM_AT_RELEASE` (=1 for SHIRAFUNE in `BalanceConfig`), `AbilitySpec.aimsAtRelease()` / `releasePhase()`.
- Before: `CastContext.capture` at the key press stored eye, look and aim; the single phase (tick 12) used them, and the effect event was sent at the press with the press-time aim.
- Now: `ZanpakutoManager.apply` does NOT send the effect event of such an ability at the press; `AbilityExecutor.run` at the release phase calls `ctx.resampled(p)` (position, eye, look vector, aimed point, ray length read again, seed and hit gate kept), sends `ServerFx.effect(...)` with the new context (so the event `x y z`, `dx dy dz` and params `[aim, reach, used length]` are the release ones) and then does the hit test along the new ray. The scheduler still drops the phase if the cast is cancelled (seal, death), then no event is sent at all.
- Client: nothing re-aims it. `RukiaShikaiFx.shirafune` builds the blade from `t.pos`, `t.dir` and `params[0..2]` of the event it receives, so with the event sent at the release it uses the release aim. No client file was edited.
- Open follow-up for the client agent (UNVERIFIED, not done by me): the client timeline of Shirafune assumes the event arrives at the key press (swirl at t 0, blade grows at 0.3 s, contact burst at 0.6 s = the old server hit). The event now arrives at the release, which is when the server hits, so the blade now appears 0.3 s and the contact burst 0.6 s AFTER the damage. Fix on the client side: play `shirafune` with its clock started at about 0.3 s (skip the lead-in) or move `t.at(0.3 ..)` to 0.0 and `t.at(0.6 ..)` to about 0.1. The thrust gesture of the player (`CastGestures`, thrust at about 0.64 s) already matches the release time.
- Book text (en/ru) says the blade is aimed when the cast ends.

## 4. Kido damage halved

- `KidoConfig.defaults()`: Byakurai 2.5 -> 1.25 HP; Shakkaho centre 4.0 -> 2.0 HP (edge 1.6 -> 0.8). Tests changed with the numbers: `KidoConfigTest.defaultsAreTheDocumentedNumbers` (the explicit asserts, plus the "string keeps the default" assert) and `KidoMathTest.burstDamageUsesTheSpec` (centre 2.0, edge 0.8), and `R8MechTest.kidoDamageIsHalved`. `design/results/kido.md` updated (table and the manual check line, the cooldowns row was also stale from before the 2026-10-12 rebalance). The book pages take the number from the spec; `BookFacts.number` now prints two decimals when the value has a real hundredth, so the book says 1.25 and not 1.3 (`SpellbookTest`).
- **TRAP for the user, outside the worktree and not touched:** `D:\MineBleach\mod\run\config\reiatsu_test.json` (and `run-server`, `run2`) contain an explicit `"kido"` section written BEFORE the 2026-10-12 rebalance: Byakurai damage 6.0, cooldown 60; Shakkaho damage 10.0, cooldown 140. `ModConfig.loadKido` reads those values, so the play test really used 6 and 10 (and 3 s / 7 s cooldowns), not the 2.5 and 4.0 of the code defaults, and halving the code defaults changes NOTHING in that game until the file is changed. To get "half of what the user saw" write 3.0 / 5.0 into those files; to use the new defaults delete the `"kido"` object (it is rewritten with the defaults on start: 1.25 / 2.0, cooldowns 100 / 200 ticks). Decide which. The `"bladeFlight"` section of the file equals the code defaults, so item 2 is not affected.

## 5. Melee hit reiatsu

- Unit: the bar is stored in TENTHS of a point: `maxTenths = 1000`, the HUD shows 0..100.0, and the "reiatsu 1000" in the session log is 1000 tenths = a full bar of 100.0 points. `BalanceConfig.meleeHitReiatsu() = 1.0` is in POINTS (HUD scale); `meleeHitTenths() = 10`. So one hit is 1 percent of the bar; 12 to 25 hits pay one ability (costs are 12 to 25 points).
- Rule: `MeleeHitRules.reiatsuTenths(playerMelee, holdsZanpakuto, damageTaken, perHitTenths)` (pure). Machine: `StateMachine.grantReiatsu(tenths)`, clamped to the maximum, ignored when dead, returns what was added.
- Glue: `server/MeleeReiatsu` (registered from `ZanpakutoManager.init`), the same two events the Rage "dealt" gain uses: `AFTER_DAMAGE` (damage that really landed: a parried, barrier-eaten or missed hit never gets there, a zero damage hit is refused by the rule) and `AFTER_DEATH` for the killing blow (Fabric does not fire AFTER_DAMAGE for it). Counts only `PLAYER_ATTACK` damage with the player as direct source and a zanpakuto in the main hand; abilities and kido are FREEZE / INDIRECT_MAGIC and never match. Works in every state (SEALED included). One reward per damaged target, so a Better Combat swing through two mobs pays twice. Log: `[reiatsu] <name> melee hit: +10 (reiatsu x/1000 tenths)`.

## 6. Byakuya barrier pushes everybody away

- `server/BarrierShove.tick(owner, cfg, now)` called from the per-player tick of `ZanpakutoManager` while `sm.barrierActive()` (the mode timer ends it). Every `barrierPushIntervalTicks` (2) it finds living entities in a cylinder of `barrierPushRadius` (3.5 blocks plus the entity's half width, +-2.5 blocks vertically) around the owner and sets their velocity with `BarrierPush.velocity` (pure): old horizontal velocity halved plus `barrierPushStrength` (0.6 blocks per tick) outward, cut by the knockback resistance attribute, and at least `barrierPushUp` (0.18) upward only when the entity stands on the ground (no launch in the air). No damage source is created, so nothing is damaged, no Rage, no `[dmg]` line.
- Excluded: the owner, his tamed pets, the vehicle he rides or his passengers, Creative / Spectator players, dead entities, the `reiatsu_test:immune` entity tag, and other barrier owners (their control immunity forbids any knockback). Players and mobs are pushed regardless of `/reiatsu pvp` and `affectPlayers` (a push is not damage).
- Strength check (model, not play): a pushed walker keeps about 0.55 blocks per tick outward on average (`BarrierPush.averageOutwardSpeed(0.6, 2, 0.546)`), a sprinting player walks 0.28 blocks per tick inward, net about 0.27 outward, so he cannot walk through. A strength of 0.1 would let him through (asserted). If the push feels too violent, lower `barrierPushStrength` (0.4 still beats a sprint, 0.36 average).
- The barrier itself (80 percent cut, 5 s, control immunity) is unchanged.

## Files

New: `core/state/MechSpec.java`, `core/combat/AbilityHitGate.java`, `core/combat/MeleeHitRules.java`, `core/combat/BarrierPush.java`, `server/MeleeReiatsu.java`, `server/BarrierShove.java`; tests `AbilityHitGateTest`, `MeleeHitRulesTest`, `BarrierPushTest`, `core/state/R8MechTest`.
Changed: `BalanceConfig`, `AbilityParams`, `AbilitySpec`, `StateMachine` (+`grantReiatsu`), `BlockRules` (+`abilityDamage`), `KidoConfig`, `BookFacts`, `Targeting`, `DmgLog`, `CastContext`, `AbilityExecutor`, `BladeFlights`, `ZanpakutoManager`, lang en/ru (Shirafune page, barrier page), `design/results/kido.md`; tests `BlockRulesTest`, `KidoConfigTest`, `KidoMathTest`, `SpellbookTest`.

## UNVERIFIED in the game

Everything above. In particular: that Better Combat strikes arrive as `PLAYER_ATTACK` with the player as the direct source (the Rage code assumes the same), the felt strength of the barrier push, the Shirafune visual timing (see 3), and the numbers of item 2 (the model assumes the victim is swept every tick).

## Build

`./gradlew build` from `mod/` (one run, from a fresh worktree, 2 m 17 s): BUILD SUCCESSFUL, 86 test classes, 791 tests, 0 failures, 0 skipped (including the new `AbilityHitGateTest`, `MeleeHitRulesTest`, `BarrierPushTest`, `R8MechTest`, and `CorePurityTest`). The game was not started.
