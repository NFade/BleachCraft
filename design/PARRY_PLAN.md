# PARRY PLAN: melee attacks, blocking and parry for two players

Status: DRAFT, nothing implemented. Branch `parry` (worktree `.claude/worktrees/parry`, base `main` f840f17).

## 0. Goal and scope

- Two players can fight with swords: normal melee hits, a block held on RMB, and a parry with a 0.2 s window.
- Not in this step: directional (Mordhau-style) attacks, stamina, katana speed, Shunpo combos. Those come after the basic fight is verified.
- Abilities (Z H B, U I O, Y) are untouched. They keep working during a block only where it makes sense (see section 3).

## 1. Current state (verified in code)

- `registry/ZanpakutoItem.java` `use()`: on RMB in SEALED it draws the sword (SEALED to BASE). It always returns PASS, so no use animation and no block. That is the reason the comment says "no block placement".
- `client/input/ReiatsuKeys.java`: keys J draw, R release, G bankai, V seal, Y shunpo, Z H B shikai slots, U I O bankai slots. Free candidates: C, X.
- `core/state/StateMachine.java`: states SEALED, BASE, SHIKAI, BANKAI. `BASE` is the drawn base form (no abilities, no release).
- `server/ZanpakutoManager.java` line 414: `ServerLivingEntityEvents.ALLOW_DAMAGE` hook (`allowDamage`). This is where a parry can cancel damage.

## 2. Input: block on RMB

- RMB is the only input. Right now it draws the sword in SEALED. Plan: keep the draw in SEALED and start a block in drawn states (BASE, SHIKAI, BANKAI).
- Implementation: `ZanpakutoItem.getUseAction()` returns `UseAction.BLOCK`, `getMaxUseTime()` returns a long value (for example 72000). The vanilla use-hold then tracks the block. In SEALED, `use()` keeps the draw path and returns PASS.
- Known bug (from LOG.md): right click can draw the sword while the cursor is on a normal block. Fix it while doing this, because the draw path and the block path now share RMB.
- UNVERIFIED: exact Yarn names `UseAction.BLOCK`, `Item.getMaxUseTime`, `LivingEntity.isBlocking`, `LivingEntity.getBlockingItem` in 1.21.1. Check by javap before writing code.

## 3. Parry: 0.2 s window

- On the server, record the tick when the block began (`blockStartTick`), per player. The vanilla start-use packet gives the start; the server can read `player.getActiveHand()` and `isUsingItem()`.
- In `allowDamage`: if the target is blocking and `now - blockStartTick <= 4` ticks (0.2 s at 20 tick/s), it is a parry. Cancel the damage, knock the attacker back, and send a short stagger to the attacker (no damage).
- If blocking but outside the window: reduce damage (for example 50 percent, a value to tune) and keep the block. Vanilla shield logic does this differently; we use our own rule.
- Ability damage (Targeting.hurt, DamageTypes.INDIRECT_MAGIC) must not be parried. Parry only applies to melee sources (`source.isOf(DamageTypes.PLAYER_ATTACK)` or equivalent, UNVERIFIED).
- Parry feedback: a short sound and a spark on both sides (later, Phase 6 FX).

## 4. Attacks

- Keep vanilla melee (left click), no changes in this step. The katana speed (attack speed attribute on `ZanpakutoItem` settings) is a separate later step.
- Make sure a block does not stop attacks: while blocking, left click should still work or should be explicitly blocked. Decision: blocking stops attacking (typical for PvP), so the player must release RMB to attack. Confirm with the user.

## 5. Abilities during a block

- Decision needed: can the player cast Z H B (shikai) while blocking? Default in this plan: no, the block is released before a cast. Shunpo (Y) during a block is allowed (it is a dodge).

## 6. Steps (one Sonnet agent per step, max 2 in parallel, short report)

| Step | Work | Check |
|---|---|---|
| 1 | Check Yarn names by javap (section 2 list). Fix the RMB draw-on-block bug. | `gradlew build test` green |
| 2 | Block on RMB in BASE, SHIKAI, BANKAI via `UseAction.BLOCK`. Block state on the server. | Unit test for the state rules; in-game: block visible to the other player |
| 3 | Parry window in `allowDamage` (0.2 s), reduce damage outside it, no parry for ability damage. | Unit test of the window math (pure function in core/, no Minecraft imports); harness scenario with two clients |
| 4 | Feedback (sound, stagger on attacker). | Screenshots as one sheet |

## 7. Tests

- Pure logic in `core/` (window math, damage reduction, state rules). CorePurityTest must stay green.
- Harness: a two-client scenario (block, parry, normal hit, ability during block). One game run for all scenarios (runPhase, batched).

## 8. Open questions for the user

1. Attacks during a block: blocking stops attacks (default) or both allowed?
2. Abilities during a block: no (default), or yes?
3. Damage reduction outside the parry window: 50 percent (default), or a different value?

## 9. UNVERIFIED

- All Yarn names in section 2 and section 3.
- That the right-click draw bug is still present on main (only LOG.md says so).
- That `allowDamage` runs before the damage is applied on both server and client for the attacker's stagger.
