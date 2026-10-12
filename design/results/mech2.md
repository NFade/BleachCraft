# mech2 (2026-10-12): barrier, bankai cleanup, bankai without a full bar, frost territory

Branch `mech2` (from `main` 80b855d), four local commits, one per task. Nothing pushed. In-game status of every item is in the last section.

## Task 1: Byakuya shikai barrier (H, `MODE_BARRIER`)

### What was wrong

I read the two-client session in `mod/run/logs/latest.log` (Player47 against TestB, 18:01 to 18:09): the damage cut itself WORKED on the
server. Example: `[dmg] byakuya.shikai.mode_attack#0 TestB -> Player47 asked=2.0 lost=0.4`, and Tsukishiro `asked=6.0 lost=1.2` on the barrier holder
(80 percent absorbed both times). So the "does not work" report came from the parts that were never there or that hid the effect:

1. **No control immunity existed.** Nothing stopped Slowness, freezing ticks or knockback on the barrier holder (Rukia's Tsukishiro, the cage,
   Scatter, every mob). The barrier lived only in `ZanpakutoManager.allowDamage`. Fixed with three mixins (below).
2. **A 20 HP pool that collapsed the dome early** (`barrierPoolTenths` 200): one Scatter (16) or Hakuteiken (24) hit used it up and the dome
   vanished after one hit, long before its 5 s. Default is now no cap (`barrierPoolTenths` 0 = unlimited, `StateMachine.POOL_UNLIMITED`).
3. **The log lied.** A hit that the barrier lowered is re-applied inside the hook and the outer `damage()` returns false, so `[dmg]`
   printed `result=REFUSED_UNKNOWN` for hits that did cost health. `HitRules.outcome` now says DAMAGED whenever health went down.

The damage path itself (`ALLOW_DAMAGE` hook order: block/parry first, then the barrier, re-entrant `p.damage` with the cut amount) is unchanged.
`mode_attack` still ends an active barrier (rule C8 of STATE_MACHINE, kept).

### What is blocked while the barrier stands (`core/combat/BarrierRules`, knob `barrierControlImmune`, default true)

| Kind | Blocked | Hook |
|---|---|---|
| Status effects | slowness, mining_fatigue, weakness, nausea, blindness, darkness, levitation (vanilla, potions, `/effect`, mobs, our abilities) | `LivingEntityMixin.canHaveStatusEffect` HEAD |
| Freezing | any increase of the freezing ticks (Rukia ice through `Targeting.chill`, powder snow); old ice still drains | `EntityMixin.setFrozenTicks` HEAD |
| Knockback | every `takeKnockback` (our Scatter and Hakuteiken pushes and every vanilla hit) | `LivingEntityMixin.takeKnockback` HEAD |
| Damage | 80 percent less for the whole 100 ticks (`barrierReductionPercent`, 80) | `ZanpakutoManager.allowDamage` |

Not blocked: damage effects (poison, wither, hunger), buffs, direct velocity changes by explosions or `setVelocity` (our abilities use
`takeKnockback` only). There is no stun in the mod. The hooks run on the server only (`world.isClient` is skipped), so a client packet is never refused.

### Config knobs (`BalanceConfig`)
`barrierTicks` 100, `barrierPoolTenths` 0 (no cap), `barrierReductionPercent` 80, `barrierControlImmune` true; `withBarrier(pool, percent, immune)` for tests.

### Checks
Pure: `BarrierRulesTest` (5), `AbilityTest` (pool cap, unlimited default, immunity ends with death/reset), `HitRulesTest` (partial hit is DAMAGED).
In the world: `/reiatsu dev barrier_check` (`server/BarrierCheck`, runs on the executing survival player through the real hooks, with positive
controls before and after the barrier: 10 magic damage costs 10 HP without it and 2 HP with it; slowness, weakness, nausea, blindness,
levitation, mining fatigue, darkness refused; speed lets through; `Targeting.chill` leaves freezing ticks 0; `Targeting.knockback` does not move
the caster; after the barrier everything lands again). `runPhase4` calls it (`mech2 barrier_check` checks).

## Task 2: effects must end with the bankai, deterministically

### Root causes found
Server:
* The 45 s timer path (`endBankai`) emitted no cancel at all, and death/logout/dimension (`reset`) broadcast no end message. The only thing that ended
  anchors was the effect id 10/11 (`AnchorPlan`), so on death/logout/dimension the clients got nothing.
* The Rukia field anchor and the frost territory noticed the end only by a 10 tick poll (`FieldAnchorPolicy.due`), and `onLeave` threw the
  machine's logout events away.
* Scheduled phases of bankai abilities survived the timer end.
Client:
* **Stuck screen layers.** `RowsFx`/`FxScene` dropped a rows state after "anchor not seen for 2 s" (death, logout, dimension, lost event) WITHOUT
  releasing its night grade and vignettes (`ScreenFx.Layer` is held forever until released). The Byakuya night colour stayed on the screen. The
  night timeline could also create the layers AFTER the state was already ended.
* Storm and Hakuteiken states had no external end; the Rukia passive and crystals only followed the attachment; running bankai timelines could not be cancelled;
  `freeze_desat` could stay if the Absolute zero timeline was lost.
* After an instant drop the still-present anchor entity could bring the rows back (`FxScene` late-join path).

### The fix: one path
* `StateEvent.BankaiEnded(who, trigger)` is emitted by EVERY machine path that leaves BANKAI (timer, seal, death, logout, dimension, spectator,
  dev reset/state), exactly once, right after the `StateChanged` (`BankaiEndTest`, 10 tests).
* `ZanpakutoManager.endBankaiEffects` is the ONE server cleanup: `AnchorManager.reset`, `RukiaServerFx.endOf` (field anchor), `BankaiField.end`
  (territory), `FrostField.endOf` (thaw now, fast for death/logout/dimension), `SCHEDULER.cancelIf` (bankai ability phases), and the message
  `EffectIds.BANKAI_CLEANUP` (44) to the caster and everybody tracking him. Log line `[bankai-end] ...`. `onLeave` now runs it too.
* Client `client/fx/BankaiEnd`: runs on the server message AND from a per-tick watcher (owner seen in BANKAI whose synced state is no longer BANKAI, or whose
  entity is gone). Idempotent (3 s dedupe). It ends: rows (sink animation, or at once for death/logout/dimension), storm, Hakuteiken, Rukia passive
  (decal, crystals, frost edge, ribbons), swarm, running bankai timelines, every held screen layer of that owner (`ScreenFx.releaseOwner`, all layers are now
  tagged with their caster), `freeze_desat`. `RowsFx.State.dispose` releases the layers on every removal path.
* Audit: 5 s after each end the client logs `[bankai-audit] owner N ...: clean` or `LEFTOVERS rows= storm= hakuteiken= rukiaPassive= crystals=
  layers= timelines= anchors= desat=` (warning, counted in `BankaiEnd.dirtyAudits`). Server: `/reiatsu dev bankai_audit` prints `anchors, rukiaFieldAnchors,
  frostFields, scheduledBankaiPhases` (and the territory) and answers 0 when clean; `ZanpakutoManager.bankaiLeftovers` is what the harness reads.
* `/reiatsu dev reset` is new (sealed through the real machine path, cooldowns cleared, bar full).
* `runPhase4` section "mech2 audit" (`bankaiAuditSteps`): after the Rukia seal, the Byakuya 45 s timer, the Byakuya seal, and the new section E (Rukia
  bankai ended by death, dimension change, `/reiatsu dev reset`, the 45 s timer) both audits must be empty.

Not changed on purpose: after a Byakuya bankai the shikai petal swarm is not re-spawned (it was ended by the old id 11 too); a bankai ability that
already hit (damage landed) is not undone.

## Task 3: no "reiatsu must be full" for bankai

Removed `BANKAI_NOT_FULL` (enum constant, check in `StateMachine.request`, wire mapping), the voice gate that used the full bar (`GateContext.reiatsuFull`
became `bankaiReady` = enough Rage; the context-gated "bank eye" aliases are marked `requires: ["bankai_ready"]` in `phrases.json`, generator updated), the
HUD "Need full reiatsu" toast and hint (lang keys removed), the ready gem (no longer needs a full bar), book pages (now "80 Rage"), README, QA checklist,
VOICE_PHRASES, STATE_MACHINE, tests (`BankaiTest`, `TransitionTest`, `RageGateTest`, `Fx.toBankai`, voice tests renamed).
Kept: the Rage gate (bankai needs and spends 80 Rage, shikai 40, `RageConfig`). **The user's complaint "run around for minutes to accumulate" is most likely about
RAGE, not reiatsu** (reiatsu regenerates +4 per second sealed, +2 per second in bankai; the 80 Rage needs fighting). The orchestrator should confirm with the user.
Entry still behaves: 0 reiatsu cost, 45 s timer, 60 s re-entry lock, settle lock 44 ticks (`BankaiTest.entryWorksAtAlmostEmptyBar...`).

## Task 4: Rukia bankai, Absolute Zero replaced by the frost territory

* `AbilityId.ABSOLUTE_ZERO` removed (code 4 stays unused; nothing persists ability ids: the `ZANPAKUTO`/`COOLDOWNS` attachments are not persistent). Removed: spec,
  executor, key slot (U stays the ice spike, I is empty for Rukia), voice command + phrases + fixtures (generator, `VOICE_PHRASES.md`, phrases.json, tests/voice),
  HUD icon mapping, spellbook entry (page 8 is now an information page "Frost territory"), FP gesture, effect 23 client timeline, lang, tests, harness scenes.
* New `core/state/BankaiFieldSpec` (in `BalanceConfig.bankaiField`): `slowAmplifier` 0 (`bankai.field.slowAmplifier`), `tickDamage` 2.0 (`bankai.field.tickDamage`),
  `periodTicks` 60 (`bankai.field.periodTicks`), `refreshTicks` 20, `slowTicks` 30, `maxTargets` 64.
* `server/BankaiField`: centre = her feet at the first tick of the bankai (the release point), radius = `frost.radius` (30) growing with the freeze wave
  (`frost.waveSpeed` 12 blocks/s; so enemies are touched only on ground that is already frozen), vertical reach `max(frost.up, frost.down)` 14.
  Targets: `Targeting.cylinder` (same `HitRules` filter as every ability: not the caster, dead, spectators, immune types, own pets, players only when
  `Tuning.affectPlayers` and server pvp) + `BankaiFieldSpec.eligible` (hostile `Monster` mobs and players; no passive animals, villagers, same-team entities,
  Creative or invulnerable players). Every 20 ticks Slowness I (30 ticks); every 60 ticks `Targeting.hurt(.., 2.0, INDIRECT_MAGIC, resetInvulnerability=true)`, logged as
  `[dmg] rukia.bankai.field ...`. Client feedback reuses `EntityFxS2C.SLOWED/HIT`.
* Damage type: `INDIRECT_MAGIC` is in `minecraft:bypasses_armor` (checked in the vanilla tag), so full netherite armor takes exactly 2.0. Caveat: Protection enchantments
  still reduce it (`bypasses_enchantments` has only `sonic_boom`), Resistance is applied normally, witches take 15 percent of magic damage. `FREEZE` was not used: it is x5 on blazes
  and magma cubes and fully ignored by strays and snow golems.
* It ends with the bankai through the task 2 cleanup; the server tick stops by itself the moment the machine is not in the Rukia bankai.
* Tests: `BankaiFieldTest` (timing over a whole 45 s bankai: 45 renewals, 15 pulses = 30 HP, no free tick between renewals; eligibility; radius growth; knobs; the old ability is gone from
  ids, voice, lang), `Round6AbilityTest`, `BankaiTest`. In the world (`runPhase4`): two zombies in full netherite (one 14 blocks away) lose whole multiples of 2.0 HP and carry Slowness I, a zombie
  45 blocks away is untouched, cows are untouched, the caster is not hurt.

## Harness changes (to get `runPhase4`/`runPhase5` green again)
Both harnesses never waived the Rage gate of round 5 (shikai 40, bankai 80), so they could not have passed since then: they now run `reiatsu dev ragefree true`, and the Rage gate has
its own checks (Phase4 A7 `DENIED_RAGE`, Phase5 V4/V8). The A7/A8 checks changed with task 3 (bankai from a half bar is accepted).

## UNVERIFIED in the game (see the last run in LOG.md "mech2")
Everything above is covered by unit tests and a compile; the in-world parts need the game: the three mixins being applied (the barrier_check proves it when it runs), frost field thaw timing after the
cleanup, `[bankai-audit]` lines on a real client, the territory numbers on real mobs and players, the two-client remote view of the cleanup.
