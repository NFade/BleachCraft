# Uryu step 2: server gameplay of all six abilities (branch `gin-uryu`)

Design: `design/URYU.md`. All numbers are first guesses and live in ONE place: `core/uryu/UryuBalance.java` (the shared `BalanceConfig` rows of Uryu are untouched: cost, cooldown and the single phase at tick 0 stay as step 1 registered them). Art and client fx are placeholders only.

## What was built

### Pure core `core/uryu/` (no Minecraft imports, `CorePurityTest` passes)
- `UryuBalance`: every number of the design (Focus interval 14 / 10 ticks, move threshold 0.02, factors 1 / 1.25 / 1.6 / 2.0, arrow speed 2.2, Sprenger 3 / 16 / 40 ticks / 9 HP / x1.5, dash 8 blocks in 4 ticks, hover 12 ticks, rain 12 arrows in 24 ticks, volley 3 + Focus max 6 at 3 HP, 12 degrees per tick, cone 15, Sternenstaub 40 x 2, 20 / 12 HP).
- `Focus`: stacks 0..3, `tick(grounded, sprinting, speed, arterie)`, `reset()` (knockback, Hirenkyaku, state change), `consume()`.
- `Shots`: damage / speed / gravity / pierce of a shot by stacks (basic shot 4 HP, +20 percent with Blut Arterie; Z shot 7 HP; 3 stacks = pierce 1 extra target and no gravity).
- `ArrowSim` (+ `HitWorld` callback, `EntityHit`, `Event`, `Spec`): tick-stepped arrow list, swept test against blocks (`raycastBlock`) and entities (`entitiesAlong`), one hit per (arrow, target), pierce counter, life / expiry, homing steering with a turn limit (`turnToward`, great-circle step), terrain stops the arrow before a target behind it. Events MOVED / HIT / BLOCKED / EXPIRED.
- `SprengerTrap`: bind at 16 ticks, explosion at 40 ticks, early detonation (13.5 HP), circle area test, segment-touches test, pentagon vertices.
- `LichtRegen` (landing points, leave ticks 0, 2 .. 22), `Volley` (arrow count, cone lock, fan directions), `Sternenstaub` (needs 3 stacks, 20 then 12 HP), `Hirenkyaku` (dash / brake / hover phases, dash direction).
- Tests: `FocusTest`, `ArrowSimTest`, `AbilityRulesTest` (54 tests). Reuses `core.swarm.V3` and `SweepMath`.

### Server glue `server/uryu/` + `server/UryuTargets.java`
- `UryuManager` (init in `ReiatsuTest.onInitialize`): per-player Focus (ticked from the server tick: ground, sprint, position delta; a hit that really hurts resets it via `AFTER_DAMAGE`), one `ArrowSim` per world stepped every tick, Sprenger pentagons, Licht Regen jobs, Hirenkyaku dash jobs, the six ability bodies, the basic shot, cleanup.
- `UryuHitWorld`: block ray with collision shapes, entities through the usual target rules (`Targeting.valid`: caster safe, immune types, PvP / `affectPlayers`), homing aim point = centre of the target box.
- `UryuNet`: sends `UryuArrowsS2C` (new file, registered like the other S2C payloads): kind ARROWS (full list near the receiver per tick, one empty list when they are gone), BURST (Sprenger explosion), LINE (Sternenstaub), TRAP (new pentagon).
- `UryuTargets`: public door to the package-private `Targeting` / `DmgLog` (needed because `server.uryu` is another package). No logic.
- `UryuAbilities.run`: HEILIG_PFEIL -> `focusShot`, SPRENGER -> `sprenger(aim)`, HIRENKYAKU -> `hirenkyaku`, LICHT_REGEN -> `lichtRegen(aim, seed)`, HOMING_VOLLEY -> `homingVolley`, STERNENSTAUB -> `sternenstaub`.
- Damage types: arrows ARROW (armor applies), Licht Regen INDIRECT_MAGIC (armor ignored), Sprenger PLAYER_EXPLOSION, Sternenstaub ARROW. All through `Targeting.hurt` (block rule, Byakuya barrier cut, `[dmg]` log). I-frame reset on: rain, volley, Sprenger, Sternenstaub.
- Basic shot: right click on the `uryu_cross` item (`UryuCrossItem.use`, server side) in BASE / SHIKAI / BANKAI, instant, no reiatsu, own 20 tick cooldown, uses and clears Focus.
- Sternenstaub gate: `ZanpakutoManager.performCast` asks `UryuManager.allowsCast` BEFORE the machine charges anything; without Focus 3 the cast is refused (DENIED_STATE, action bar text "Sternenstaub needs Focus 3 (stand still)").
- Cleanup: death and logout (own Fabric events), sealed / BASE / another character (polled every tick: everything of the player goes), BANKAI left (polled, plus the delegation hook in `endBankaiEffects`: barrage and rain / volley arrows stop), server start / stop.
- Focus display (placeholder HUD): action bar text `Focus ***` / `Focus *..`, only when the count changes.

### Client placeholder
- `client/fx/UryuFx`: arrows as `FxShapes.strip` segments (1.4 blocks, white at Focus 3, pale blue otherwise, life 0.06 s), Sprenger pentagram of 5 upright strips + 5 edges until the explosion, explosion ring + vanilla explosion particle, Sternenstaub line (white core + blue glow, 0.45 s), END_ROD trail for Hirenkyaku, END_ROD ring for the Licht Regen circle (effect ids 74 / 75).
- Models: `uryu_cross` and `uryu_bow` (+ string) wired to the real exports (`models/obj/uryu/`, `textures/item/uryu_diffuse.png` / `uryu_emissive.png`, manifest `zanpakuto/uryu_cross.json` with model `uryu`: SEALED = cross, BASE / SHIKAI / BANKAI = bow + string in hand, cross in "other"). `uryu_arrow` and `uryu_seele` are copied but not drawn yet (they are in the manifest as objects only).

## Edits to shared files (everything is an insertion; no existing line was changed)
| File | Line | What |
|---|---|---|
| `server/CastContext.java` | 96-97 | two `case` lines in the aim range switch: SPRENGER 20, LICHT_REGEN 28 (from `UryuBalance`) |
| `server/CastContext.java` | 147-159 | `effectParams` cases SPRENGER, LICHT_REGEN, HIRENKYAKU, STERNENSTAUB before `default` |
| `server/ZanpakutoManager.java` | 285-290 | gate: `UryuManager.allowsCast` (copy of the block-denied return pattern) |
| `server/ZanpakutoManager.java` | 564 | one-line hook in `endBankaiEffects`: `UryuManager.onBankaiEnd(id)` |
| `ReiatsuTest.java` | 42 | `UryuManager.init()` |
| `net/ModNetworking.java` | 22 | `UryuArrowsS2C` registration |
| `client/net/ClientNet.java` | 52-53 | receiver of `UryuArrowsS2C` |
| `client/fx/FxEvents.java` | 47-50 | `UryuFx.handles` branch (next to Gin's, separate lines) |
| `registry/UryuCrossItem.java` | 19-34 | `use` now fires the basic shot (step 1 returned `pass`); this file is Uryu's own |
| `zanpakuto/uryu_cross.json`, `models/item/uryu_cross_display.json` | whole / 3 | manifest switched from the Rukia placeholder model to `uryu`; particle texture |
| `server/UryuAbilities.java` | whole | stub body replaced (Uryu's own file) |

(Line numbers are those of the working tree at the time of writing, which also contains Gin's insertions.)

## Decisions worth knowing
- Phases stay `List.of(0)`: timed parts (2.0 s pentagon, 1.2 s barrage, dash ticks) are driven by `UryuManager` per server tick instead of extra `phaseOffsets`, so `BalanceConfig` is not touched.
- Blut Arterie (faster Focus, +20 percent basic shot) is on in SHIKAI and BANKAI.
- Z aims at the key press (server look), not "hold and release" (the client sends no aim in this mod).
- Licht Regen arrows are real falling arrows: a roof stops them (they start just below the first ceiling above the landing point). Where each lands is picked when it leaves: round robin over the valid targets in the circle with 0.6 scatter, uniformly in the circle if nobody is there (a pure random scatter would hit a 0.6 wide body about 2 percent of the time).
- Volley / rain arrows carry focus 0, so only the basic shot and Z with 3 stacks detonate a Sprenger. Detonation test: the arrow's segment of that tick touches the pentagon cylinder (radius 3, from 0.6 below to 3 above the floor), so shooting a trapped enemy with a full-Focus arrow is enough.
- Sternenstaub is a hitscan line that stops at the first block. NOT done: "clashes with and cancels an enemy Hakuteiken" (the Hakuteiken is instant damage with no beam object to cancel).
- Hirenkyaku: velocity packets for 4 ticks (2 blocks per tick), 1 tick brake, then zero velocity while airborne until tick 12 and `fallDistance = 0`; no block is placed.

## UNVERIFIED in game (the game was not run)
1. The dash really covers about 8 blocks and stops (client side movement vs server velocity packets), no "moved too quickly" kick, and the hover holds without sliding.
2. Negative Jump Boost (-10) really removes the jump on players and mobs (vanilla ignores a jump with no upward speed; the status icon is hidden).
3. `AFTER_DAMAGE` resets Focus on the real hit; standing still really builds Focus (server `isOnGround`, position delta below 0.02 while idle).
4. Arrow speed and gravity feel (2.2 blocks per tick, 0.012), hit boxes (radius 0.3 + target box), shots through leaves / fences / slabs.
5. Right click on the cross in the main hand fires once per second while the key is held, and does not also block; interaction with Better Combat.
6. Damage numbers after armor / the Byakuya barrier (ARROW and PLAYER_EXPLOSION are armor-affected on purpose; INDIRECT_MAGIC ignores armor).
7. Sprenger: ground snapping of the aimed point (1 up, 6 down), the pentagon area in the real world, the bind effects, early detonation by a full-Focus arrow.
8. The 3 stack gate of Sternenstaub through the key and the voice path (both go through `performCast`).
9. All client visuals (strips, ring, pentagram), the packet load with several arrows, the 64 shape cap of `FxShapes` (arrow strips can push out older shapes of other effects).
10. Bow in hand: the display transform is still the sword one in `uryu_cross_display.json` (bow axis, size 1.1 m, string culling), the pendant size in the sealed hold.
11. `BankaiTest.hakurenPhasesCoverTheWholeLengthAndEveryAbilityHasItsCaps` failed in the shared working tree during my full build: it comes from Gin's uncommitted `BalanceConfig` rows (params without a damage key), not from Uryu. All 54 Uryu tests and the rest of the suite pass.

## Manual test checklist
Setup: `/reiatsu` dev commands or the usual keys: take `uryu_cross`, draw (J), release SHIKAI (phrase "Blut Arterie" or the key), later BANKAI.
1. BASE: stand still 0.7 s per pip: action bar shows `Focus *..`, `**.`, `***`; move, jump or sprint: back to `...`. Right click: an arrow flies, 4 HP, 1 s pause; with 3 pips it flies faster, flat, 8 HP, goes through two mobs.
2. SHIKAI (Blut Arterie): pips come every 0.5 s; right click 4.8 HP.
3. Z Heilig Pfeil: 7 HP, 12 reiatsu, 3 s; 3 pips = 14 HP and pierce. Take a hit while standing: pips reset.
4. H Sprenger: look at the floor within 20 blocks: pentagram appears; step out before 0.8 s: no bind; stay: Slowness V and no jump, at 2.0 s explosion 9 HP (caster takes nothing). Repeat, wait 3 pips, shoot the mob in the pentagon: explodes at once, 13.5 HP.
5. B Hirenkyaku: dash about 8 blocks, over a pit you stay up for about 0.6 s, no fall damage, Focus is 0 after.
6. BANKAI: Z Licht Regen at a mob 20 blocks away: 12 arrows over 1.2 s inside the circle, 1.2 HP each, armor ignored, you are safe; under a roof the arrows start below the roof.
7. H Homing Volley: look at a mob (within 15 degrees, 30 blocks, no wall): 3 arrows curve into it (9 HP); with 3 pips 6 arrows (18 HP); behind a wall they hit the wall; no target: they fly straight.
8. B Sternenstaub: without 3 pips the key says "needs Focus 3" and costs nothing; with 3 pips: 40 long line, 20 HP to the first, 12 to the next ones, stops at walls.
9. Cleanup: die / relog / seal (V) / wait for the 45 s bankai end during a Licht Regen or a pentagon: nothing keeps firing or exploding.
10. Check the log for `[uryu]` lines and `[dmg] uryu...` lines.
