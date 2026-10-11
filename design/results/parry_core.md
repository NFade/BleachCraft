# Parry core (block on RMB, 0.2 s parry window), 2026-10-11

Plan: `design/PARRY_PLAN.md` steps 1 to 4. Better Combat (`PARRY_PLAN_BC.md`) is not touched: no swing, no BC code or assets. The block is plain vanilla use-hold, so BC step 3 (check that BC does not eat RMB) is still open.

## Decisions (user, 2026-10-11)
Block forbids attacking; abilities cannot be cast while blocking; shunpo can; melee damage -50 percent outside the parry window; parry window 0.2 s from the start of the block (4 ticks, inclusive: a hit 0..4 ticks after the start parries); block is held RMB.

## What is where
- `core/combat/` (no Minecraft imports): `CombatConfig` (window 4, multiplier 0.5, parry knockback 0.6, stagger 12 ticks Slowness III), `DamageKind`, `BlockOutcome`, `BlockRules` (window, damage resolve, `allowsAttack/Ability/Shunpo`), `ParryEvent`, `ParryBus`.
- `registry/ZanpakutoItem`: `use()` = `setCurrentHand` + CONSUME (main hand only), `getUseAction` BLOCK, `getMaxUseTime` 72000, static `isBlocking(entity)` and `blockTicks(entity)`. The block state is the vanilla item use, read live (`getItemUseTime()`), so there is no separate state to desync. Vanilla `isBlocking()` is NOT used: it is true only after 5 ticks and would eat the window.
- `server/BlockParry`: maps the damage source to `DamageKind` (only PLAYER_ATTACK / MOB_ATTACK / MOB_ATTACK_NO_AGGRO with a living attacker are MELEE; abilities use FREEZE / INDIRECT_MAGIC, so they are never parried or reduced), applies the outcome: parry = hit cancelled, attacker knocked back (`Targeting.knockback`) and slowed, trident-hit + knockback sounds, electric spark + crit particles, `ParryBus.fire`. Blocked = shield-block sound. Also an `AttackEntityCallback` that returns FAIL while blocking (server net; vanilla already ignores the attack key while an item is in use).
- `server/ZanpakutoManager.allowDamage`: block first (parry returns false, block lowers the amount), then the existing barrier on the lowered amount, one re-applied `damage()` with the reentrancy flag. `performCast`: refuses with DENIED_STATE while blocking, before the machine charges anything (key and voice both go through it; `core/voice` untouched).
- `client/input/ReiatsuKeys.castSlot`: same refusal locally (feedback without a round trip).
- `mixin/LivingEntityMixin` + `reiatsu_test.mixins.json` (new common mixin config in `fabric.mod.json`): `blockedByShield` returns false while a zanpakuto is blocking. Reason: UseAction.BLOCK makes vanilla treat the sword as a shield (full block from the front after 5 ticks, item damage, shield sounds), and the re-applied reduced damage would be zeroed.

## Rage hook (B4, not implemented)
`ParryBus.register(listener)`; the listener gets `ParryEvent(defender, attacker, tick, preventedDamage)` on the server thread. Rage should register once at init (do not call `ParryBus.clear()` on server stop; it is for tests).

## RMB bug and how to draw the sword
`ZanpakutoItem.use` no longer draws or sheathes: RMB only blocks (in every state including SEALED, as the user asked; the plan had "draw in SEALED"). The draw is the existing key **J** (toggle); the plan has no other draw method. The old "draw when the cursor is on a block" bug is gone with the draw path. Right click on an interactable block (chest, door) still wins over the block, as with a vanilla shield; sneak to bypass. HUD hint, ru/en lang, comments and `Phase4Harness` A1e (now: RMB blocks and does not draw, then J draws) updated.

## NOT done from PARRY_PLAN.md
- Step 4 feedback is server side only (sound, particles, knockback, stagger). No client FX / screen shake, no animation of the attacker's stagger.
- First/third person block pose of our own: only the vanilla BLOCK arm pose; no custom first-person block pose (our `HeldItemRendererMixin` lift/pose is untouched).
- Directional check (block only hits from the front): not in the plan, not done; a block covers all directions.
- Harness scenario with two clients (block, parry, hit, ability during block): not written; the game must not be run now. Only A1e of `Phase4Harness` was adapted.
- Better Combat steps BC1 to BC6.
- Anti-spam: releasing and re-pressing RMB restarts the window, so tapping RMB every ~0.25 s keeps a window open. A re-arm delay would be a one-line change in `BlockRules` / `CombatConfig`; decide after playtest.

## Tests
`./gradlew test` (mod/): 372 run, 0 failed (`BlockRulesTest` 9, `ParryBusTest` 2, `CorePurityTest` green); `compileClientJava` ok. Game not run.

## UNVERIFIED (check in game)
1. The mixin applies (`blockedByShield`): a hit from the front after the window must do 50 percent, not 0.
2. Parry window feel at real ping: 4 ticks counted on the server from the use-start packet.
3. RMB with the sword really starts a block and J still draws; first-person view while blocking; third-person BLOCK arm pose with our held sword model and the scabbard.
4. Does `swingHand`/katana slash fire on RMB (CONSUME should not swing).
5. Attacker knockback/stagger on a player (velocity packet) and on a mob.
6. Barrier shikai plus block order (block first, barrier on the lowered amount).
7. Block during a running ability cast is allowed (only starting a new cast is refused).
8. The `Phase4Harness` A1e change was not run.
