# Gin step 2: server gameplay and minimal client fx of all six abilities (branch `gin-uryu`)

Design: `design/GIN.md` section 4. All numbers are the first guesses of the design, now stored as params of the six Gin `AbilitySpec` rows (`BalanceConfig`) with the Gin-only key names in `core/gin/GinParams`. The game was NOT run (not allowed here); everything below is covered by unit tests of the pure rules and by a clean `./gradlew build` (about 1000 tests), nothing was seen in game.

## What was built

### Pure rules `core/gin/` (no Minecraft imports, `CorePurityTest` passes)
- `GinRecast`: per Gin. Phases NONE / MARKED(target, expiry) / CHANNEL(start, end). `decide(now)` says what a press of the Korose key means (MARK = normal cast, RECAST, BUSY). Results are `Outcome(kind, cooldownTicks)` (-1 = leave the cooldown): dissolve 600 (30 s), interrupt 300 (15 s), a cleanse during the channel 300 except the target's death and Gin's own end (free). Interrupts: ONE hit of at least 4 HP (`onHit`), `onKnockback`, `onControl`, `onStateChange`. Reach check 64 blocks at the start of the channel. `refresh` = Renjin timer reset.
- `SliverBook`: target uuid -> owner uuid + expiry tick; `place`, `refresh`, `extendTo` (the channel keeps its sliver), `expire`, `cleanse(target, reason)`, `cleanseOwner(owner, reason)`; reasons MILK, TARGET_DEATH, TIMER, OUT_OF_RANGE, OWNER_DEATH, OWNER_LEFT_BANKAI, DISSOLVED, REPLACED, INTERRUPTED.
- `RootBook` (cast root timing, a later root never shortens a longer one), `GinCover` (Hakufuku: hidden window, "next thrust has no flash" window, broken by damage), `GinGeometry` (yaw offset, the 8 sweep slices, body slack, 64 block reach, snake hook pull step), `GinParams`, `GinFxModes` (layout of the Gin effect events).

### Server glue
- `server/GinAbilities` (the step 1 stub, now real; called by `AbilityExecutor.dispatch` as before):
  - **Ikorose**: `Targeting.firstOnRay` length 14, width 0.6 (half width 0.3 is in the row, the ray test expands the box by 0.3), 5 HP, knockback 1.5, Gin rooted 8 ticks after.
  - **Hyapponzashi**: phase 0 roots Gin for 46 ticks (1.0 s wind-up + 0.8 s sweep + 0.5 s recovery); phases 1..8 at ticks 20, 22 .. 34 are eight 15 degree slices of the 120 degree arc around the look at the start of the sweep (`CastContext.releaseAim`), radius 8, 3 HP once per target, at most 8 targets.
  - **Snake Hook**: line up to 16; a block, a mob or a player starts `SelfPull` (server velocity 0.9 blocks per tick toward the anchor, stops 1.5 blocks short, at most 30 ticks, no teleport); a player is also hurt for 2 HP. A miss only shows the line.
  - **Butou: Renjin**: 8 phases (thrusts) at ticks 0, 3, 5, 8, 10, 13, 15, 18; each re-reads the look, hits the first target on a line of 24 for 1.5 HP with the i-frame reset; phase 0 roots Gin for 24 ticks. A hit on a marked target calls `GinManager.onRenjinHit` (sliver timer back to 12 s).
  - **Korose press 1**: line 48, 9 HP, i-frame reset. The sliver is placed only when the target is alive and really lost at least `sliverMinHp` (1 HP), so a Byakuya barrier that eats the thrust leaves none.
  - **Hakufuku**: `GinManager.hide`: Invisibility for 60 ticks (no particles, no icon), Glowing removed, 80 tick window for a flash-free next thrust.
- `server/GinManager` (package `server` because it needs the package-private `Targeting`, `DmgLog`, `ZanpakutoManager.Session`): sliver registry + every cleanse path, the recast, the channel tick, cover, cleanup events (own `AFTER_DAMAGE`, `AFTER_DEATH`, `DISCONNECT`, `SERVER_STOPPING`, tick registered at the end of `ZanpakutoManager.init`).
  - **Recast**: `ZanpakutoManager.performCast` asks `GinManager.tryRecast` BEFORE the machine. A live mark => press 2: 1.5 s channel (rooted with `CastRoot`), nothing is charged, no GCD, the machine never sees it (this is why `StateMachine.cast` needs no change). It returns null for every other press, so press 1 is an ordinary cast (25 reiatsu, 12 s cooldown from the machine). Key and voice share this path. A press during the channel answers DENIED_STATE.
  - **Dissolve**: after the channel, if the target is still valid and within the world: 18 HP of INDIRECT_MAGIC (armor ignored) with `ZanpakutoManager.bypassMitigation` set, so the sword block and the Byakuya barrier in `allowDamage` do not touch it; vanilla totem logic still runs (the damage type is not in `bypasses_invulnerability`). Then the Korose cooldown is set to 600.
  - **Channel interrupts** (polled every tick): push (horizontal move of more than `pushLimit` 0.9 blocks from the start, because a rooted player does not move by himself), control (frozen, a Slowness that is not the hidden one of the root, nausea, levitation, blindness), state change (left BANKAI / sealed / dead). Hits come from `AFTER_DAMAGE` (the damage that really landed). An interrupt spends the sliver (see decisions) and sets the Korose cooldown to 300 ticks (15 s) counted from the interrupt (the machine value is replaced through `StateMachine.setCooldown`).
  - **Cooldown sync**: the cooldown attachment only syncs when the SET of cooldowns changes, so `applyCooldown` sets `lastCooldownKeys = null` and runs `apply(p, s, List.of(), null)` to force the sync of the new value.
  - **Cleanse paths**: the marker status effect is gone before the timer (milk bucket, totem pop, `/effect clear`) = MILK; target dead (`AFTER_DEATH` and the poll); target farther than 64 blocks, in another dimension, offline or unloaded = OUT_OF_RANGE; 12 s timer; Gin dead / logged out / left BANKAI via `GinManager.onBankaiEnded` (one line in `endBankaiEffects`, which every end of a bankai reaches).
- `server/gin/CastRoot` (public): hidden Slowness (amplifier from the row, IV) plus temporary movement-speed and jump-strength attribute modifiers of -100 percent with the ONE id `reiatsu_test:gin_cast_root`; removed by tick, `release`, death, logout, a state check of the tick (SEALED / BASE / not Gin), `SERVER_STOPPING`. `SelfPull` (public): the hook pull. `SliverEffect` (public): the marker status effect `reiatsu_test:gin_sliver` (no icon, no particles, no effect; lang key in en_us / ru_ru).

### Client (minimal, additive)
- `client/fx/GinFx` (new), hooked by one appended branch in `FxEvents`. Effect ids: 60 / 61 releases (ring + sparks), 62 to 67 abilities. The event of the key press (5 params, from the machine) draws only the pose / sound; geometry arrives in Gin's own events (`GinFxModes`): STRIKE (line from the hand to the end point: extends in 2 ticks, falls back in 3, `FxShapes.strip` core + glow), SWEEP (13 strips with staggered delays = the swinging blade), HOOK (line, kept taut for the pull), CHANNEL, DISSOLVE, INTERRUPT, SLIVER / GONE (sent to the OWNER only; a pulsing white glow on the carrier). Hakufuku: shimmer, then no flash and no Gin particles for the hidden time.
- The exported `gin_shinso_ray` mesh is copied and declared in the manifest as an effect mesh but NOT drawn: the strip is used (the permitted fallback). Reason: no mesh pass exists for a stretched mesh along an arbitrary axis from the hand without new renderer code.
- Items: `models/obj/gin_shinso/` (4 OBJ + meta), `textures/item/gin_shinso_diffuse/emissive.png` copied from `blender/export/gin_shinso/`; `zanpakuto/gin_shinso.json` now uses them (sealed: drawn blade in the hand + sheathed hilt `other`, base / shikai / bankai: the drawn blade, saya separate for the draw); `hold` 0.28 (the sword value 0.33 scaled to the wakizashi, test: 0.16 above the grip, > saya mouth).

### Tests (pure, JUnit)
`core/gin/GinRecastTest` (phases, press meaning, cooldown outcomes 600 / 300 / unchanged, every interrupt rule, keepsMark variant, cleanse in channel and in mark for every reason, Renjin refresh), `SliverBookTest` (lifetime to the tick, every cleanse reason, owner cleanse, refresh rules, extend, replace), `GinRowsTest` (all damage / length / time numbers from the BalanceConfig rows, press 1 cost and cooldown on the real `StateMachine`, `setCooldown` seam, full flow with the `Fx` fixture), `GinMechanicsTest` (root, cover, slices cover 120 degrees exactly once, pull stops 1.5 short), `GinManifestTest` (manifest resolves to the real OBJ / PNG), plus a Gin case in `DrawAnimationTest`.

## Decisions (read these)
1. **Block**: the design says a sword block or parry cancels Ikorose. The task said not to invent a rule, so ALL Gin damage goes through `Targeting.hurt` as INDIRECT_MAGIC like Rukia's and Byakuya's abilities: a blocking target takes 30 percent less (`abilityBlockReduction`), a parry (melee only, `BlockParry.kindOf`) never applies. Knockback is not reduced by the block. If the full cancel is wanted it is a change in `GinAbilities.ikorose` (skip hurt and knockback when `ZanpakutoItem.isBlocking(target)`).
2. **Interrupt spends the sliver.** The design lists no cleanse reason for it and does not say whether the mark survives. With a surviving mark the recast could be retried at once and the 15 s cooldown would mean nothing, so the interrupt spends it (`SliverBook.Reason.INTERRUPTED`). The other reading is one number: `GinParams.INTERRUPT_KEEPS_MARK` = 1 in the Korose row (tested).
3. **Cooldown after a dissolve** is 30 s FROM THE DISSOLVE (the press-1 cooldown of 12 s is replaced), after an interrupt 15 s from the interrupt.
4. Press 1 resets i-frames (like the other multi-hit abilities) so the sliver does not depend on a hit 0.3 s earlier.
5. Milk detection by a marker status effect (no mixin): a totem pop, `/effect clear` and any other effect wipe also cleanse the sliver and are reported as MILK. A totem pop on the DISSOLVE happens after the sliver was consumed.
6. Hakufuku is vanilla Invisibility: armor and the held sword stay visible (vanilla rule). Damage taken breaks it (server side removal); the client "no particles" window (3 s) is not shortened by that break.
7. Hyapponzashi has a phase 0 (root at the press) before the wind-up; Renjin phase 0 is the first thrust.
8. A channel that loses its sliver (milk, range) fizzles and costs the interrupt cooldown; the target's death and Gin's own end cost nothing extra.

## Edits to shared files (every one; only item 1 and 2 change an existing line)
| File | Line | What |
|---|---|---|
| `core/state/BalanceConfig.java` | 169-197 | the six step-1 Gin rows rewritten IN PLACE (stub rows `List.of(0)` become rows with phases and params; cost, cooldown and effect id unchanged); Hakufuku has `damage 0` because `BankaiTest` demands a damage key |
| `core/state/BalanceConfig.java` | 3 | import of `GinParams` |
| `server/ZanpakutoManager.java` | 494 | **existing line changed**: `reentrantDamage \|\| bypassMitigation \|\| ...` (generic flag, false except inside the dissolve) |
| `test/.../core/DrawAnimationTest.java` | 31-32 | **existing line changed**: one more `Case("gin_shinso", ...)` in `CASES` |
| `server/ZanpakutoManager.java` | 82-83 | field `static boolean bypassMitigation` |
| `server/ZanpakutoManager.java` | 132 | `GinManager.init()` at the end of `init` |
| `server/ZanpakutoManager.java` | 291-298 | recast delegation in `performCast` for KOROSE |
| `server/ZanpakutoManager.java` | 565 | `GinManager.onBankaiEnded` in `endBankaiEffects` |
| `server/CastContext.java` | 95, 135-136 | one range `case` line and two `effectParams` case lines for the Gin abilities |
| `core/state/StateMachine.java` | 536-546 | new generic method `setCooldown(AbilityId, int)`; nothing in the machine calls it (the Gin glue does), so Rukia / Byakuya are unchanged. Needed because the recast skips `cast` and must set 30 s / 15 s afterwards |
| `client/fx/FxEvents.java` | 39-42 | `GinFx.handles` branch (before the kido branch) |
| `lang/en_us.json`, `ru_ru.json` | 6 | `effect.reiatsu_test.gin_sliver` |
| `zanpakuto/gin_shinso.json`, `models/item/gin_shinso_display.json` | whole / 3 | Gin's own files, now the real model |
No change in `AbilityExecutor`, `Targeting`, `PhaseScheduler`, `KidoManager`, `RageManager`, `ServerFx`, Rukia or Byakuya code, and no number of Rukia / Byakuya.

## UNVERIFIED in game
1. The root: attribute modifiers on movement speed and jump strength really stop a player (also while sprinting) and are synced to his client; the hidden Slowness is not mistaken for a foreign control effect by the interrupt check (compares amplifier and remaining time).
2. The snake hook pull: setting velocity of a player with `velocityModified` carries him about 14 blocks in about 17 ticks without a "moved too quickly" kick; stop distance; no fall damage; pulling toward a moving mob.
3. The knockback detection of the channel (0.9 blocks horizontal) against being pushed by entity collision or boats; the control list; `isFrozen`.
4. The marker status effect: registers without a client mismatch, milk / totem / `/effect clear` remove it, `/effect give` listing, the missing icon texture is never asked for.
5. The dissolve: 18 HP really skip block and barrier on a Byakuya in barrier mode, a totem pops, damage attribution and kill message, `INDIRECT_MAGIC` and Resistance.
6. Cooldown sync: the HUD shows 30 s / 15 s after the dissolve / interrupt (forced attachment sync), the press 2 of the key while the HUD shows the 12 s cooldown still goes through (the client does not gate the send).
7. Hyapponzashi slice test against the body of mobs of different width; sweep fan looks like a swinging blade; Renjin thrust cadence.
8. Hakufuku: Invisibility without particles, armor visible, the break by damage; no glow; the client flash suppression.
9. All client visuals (strips, glow pulse on the carrier for the owner only, channel dust, dissolve burst), the 64 shape cap of `FxShapes` with the 13 sweep strips plus 8 Renjin thrusts, sounds.
10. The Gin item in game: draw animation with the 0.45 m blade (hold 0.28 only tested numerically), display transform (still the sword values), tsuba and saya culling, held pose in the first and third person.
11. Better Combat preset (`weapon_attributes/gin_shinso.json` is `bettercombat:katana`) vs the rooted state: attacking while rooted.

## Manual test checklist (single player, cheats on)
Setup: `/give @s reiatsu_test:gin_shinso`, hold it, `/reiatsu dev ragefree on`, `/reiatsu state bankai gin` (or J draw, R release, G bankai), `/reiatsu full`, `/reiatsu cooldowns clear` between tests. Summon targets: `/summon minecraft:zombie ~ ~ ~5 {NoAI:1b}`; for players use a second account with `/reiatsu pvp true`. Log: `[gin]` lines and `[dmg] ... gin.` lines. Shikai keys Z / H / B, bankai keys U / I / O.
1. SHIKAI (`/reiatsu state shikai gin`) **Z Ikorose** at a zombie 10 blocks away: white line from the hand (out in 0.1 s, back in 0.15 s), zombie takes 5 HP (2.5 hearts) and is pushed back, you cannot move for 0.4 s. At 15 blocks: line goes to the end, no damage. Zombie behind a wall: line stops at the wall, no damage.
2. **H Hyapponzashi**: glow at the hand for 1 s (you are frozen in place), then a fan of light sweeps 120 degrees in 0.8 s; zombies inside radius 8 take 3 HP once each (up to 8); you can move again 0.5 s after the sweep. Zombies outside the arc or radius 8 are not hit.
3. **B Snake Hook** at a block 12 blocks away, then at a zombie, then at a second player: you fly toward it and stop about 1.5 blocks short (no teleport, no fall damage); the player also loses 2 HP; a miss shows the line only.
4. BANKAI **U Butou: Renjin** at a zombie: 8 quick thrusts in 1 s, each 1.5 HP (zombie health drops in 8 steps, total 12), rooted 1.2 s; the thrusts follow where you turn your head; with a block between, nothing hits.
5. **I Korose**, press 1 at a zombie 40 blocks away: 9 HP, a pale glow pulses on the zombie (only your client sees it), 25 reiatsu spent. Press I again within 12 s: you raise the hand, 1.5 s rooted, the zombie dissolves into white dust: 18 HP (a normal zombie dies; use a high health mob to see the number), no reiatsu spent. Press I once more at once: nothing (30 s cooldown on the HUD).
6. Recast tests: (a) wait more than 12 s after press 1: the glow ends, `[gin] sliver ... ended: TIMER`, I is a normal cast again after the 12 s cooldown. (b) mark a player, let him drink milk: glow ends (MILK). (c) mark, walk 70 blocks away (`/tp`): OUT_OF_RANGE. (d) mark, kill the target: TARGET_DEATH. (e) mark, die or `/reiatsu dev reset` (leaves bankai): OWNER_LEFT_BANKAI / OWNER_DEATH.
7. Channel interrupts: start the recast and (a) get hit by an arrow or sword for 4 or more HP, (b) get shoved, (c) get a Slowness or a kido Sai: the channel breaks (`Korose channel ended: INTERRUPTED, cooldown 300`), the target is not hurt, the cooldown is 15 s; 1 HP hits do not break it.
8. Renjin refresh: mark a zombie, wait 8 s, Renjin it (U): the glow lasts 12 s from the Renjin, not from the mark.
9. Byakuya barrier: have a second player in Byakuya shikai barrier mode; Korose press 1 does not leave a sliver if the barrier cut leaves under 1 HP; a recast on an already marked player in the barrier still does 18 HP.
10. **O Hakufuku**: you vanish (armor and sword stay visible), no particles; the next thrust within 4 s has no white flash on the hand (the line is still visible); taking damage makes you visible at once.
11. Cleanup: seal (V), die or relog during a Hyapponzashi, a Renjin and a channel: you can move at once, no Slowness left (`/effect clear` should show nothing of ours), `[gin]` log shows the ended slivers.
