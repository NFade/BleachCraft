# Uryu round 2: Licht Regen, Sprenger root, bigger bow (branch `gin-uryu`)

Source of truth: `design/URYU_PLAN.md` (revision 4 and section 6 "U-FX pending"). The game was NOT run (not allowed). `./gradlew build` passes (all unit tests). Effects are untouched placeholders, except the two small ones named below.

## 1. Bankai H: Licht Regen (replaces the Homing Volley)

Renamed IN PLACE: `AbilityId.LICHT_REGEN` (code 22, effect 76, command `uryu.bankai.licht_regen`, slot 1). The old `Volley` class and its tests are deleted; `ArrowSim.Kind.VOLLEY` became `RAIN` (same ordinal 2, so the packet flags do not move).

Behaviour (all numbers in `core/uryu/UryuBalance.java` section "Licht Regen"):
- Cost 35, cooldown 24 s (`BalanceConfig.java:230-233`), one phase at tick 12 (0.6 s of raised bow) with `AIM_AT_RELEASE`: the existing mechanism resamples the cast at the release (`AbilityExecutor.run`), so `ctx.aim` is the point aimed at the END of the cast; the effect event is sent at the release too. Range 28 (`CastContext.java:99`).
- `UryuManager.lichtRegen` (`UryuManager.java:756`): the ground point under the aim (`groundPoint`, same as Sprenger), a seed from (player id, tick, world random), a `Rain` record. `tickRains` (`:770`) spawns every tick since the last one (a late tick loses none): 6 arrows per tick for 60 ticks = 360 arrows, each a real `ArrowSim` arrow of kind RAIN (speed 2.6, no homing, life 12 ticks, radius 0.4, 1.0 HP), stopped by terrain and roofs like every arrow.
- Square 9 x 9 (half width 4.5), axis aligned to the world grid, centred on the ground point; arrows start 14 blocks above the ground level of the centre. The slant is a fixed horizontal share of 0.12 of the speed in a direction from the seed; the start is the landing point moved back along the fall line, so every landing point lies inside the square.
- Damage in `UryuManager.onEvent` HIT: `rain.rules.allow(targetId, 1.0)` cuts it by the cap 14 HP per target and cast and gives 0 to the caster; `UryuTargets.hurt(..., reset i-frames = true)` (the caster and his team are also excluded by the `Targeting.valid` rules of `UryuHitWorld`). Block landings of rain arrows send no spark packet (6 per tick would be spam).
- Pure rules, no Minecraft types: `core/uryu/LichtRegen.java` (schedule `arrowsAt`, seeded `drop(tick, index)`, `inSquare`, per target `allow`). Tests `LichtRegenTest` (9): numbers, 6 per tick for 60 ticks, landing inside the square and start height, constant slant, determinism per seed, coverage of the square (3 x 3 cells), cap 14, cut of the last arrow, caster safe, expected HP of a standing player.
- Cap of arrows alive per world 256 -> 400 (`UryuBalance.MAX_ARROWS`); the rain alone keeps about 40 alive (6 per tick x about 6 ticks of fall). `UryuArrowsS2C.MAX_ARROWS` (arrows per list packet) 48 -> 96, otherwise rain arrows would not all be drawn.
- Cleanup: rain stops with the caster's death, logout, dimension change, a state that is no longer Uryu's (`cancelAll`) and the end of the bankai (`onBankaiEnd`, also removes the RAIN arrows in flight).
- Expected damage: a standing player (0.6 wide) takes about 360 x (0.6 + 0.8)^2 / 81 = 8.7 HP of the 14 cap; a moving one less. Tune `RAIN_ARROW_RADIUS` / `RAIN_PER_TICK` if it feels weak (with radius 0.3 it is 6.4 HP).

Placeholder fx kept working: `UryuFx.play` effect 76 -> `rainSquare` (`UryuFx.java:78-100`): a faint outline of the square on the ground for the rain's duration and a soft chime; the plain arrow renderer draws the rain arrows with a lean look (`UryuArrowRenderer`: no launch flash, 10 percent sparks, 3 piece trail).

Renamed text: `AbilityId.java:37,75`, `CastGestures.java:96`, `NewCharacterGestures.java:30` (the placeholder thrust gesture keeps 2.4 s), `StripHud.java:41` (icon cell 8, unchanged), `AbilityExecutor.java:58`, `UryuAbilities.java:22`, lang ru/en (`ability.reiatsu_test.uryu.bankai.licht_regen` "Licht Regen" / ru "Дождь света", `hud.reiatsu_test.technique.76`), `design/VOICE_PHRASES.md` (table row, section 1.34), `tools/gen_voice_phrases.py` (aliases), `voice/phrases.json` regenerated with `python -I tools/gen_voice_phrases.py`, tests `NewCharactersTest` (row 350 / 480 / 76), `R8MechTest` (Licht Regen is the second ability aimed at its release), `AbilityRulesTest` (volley tests removed), `ArrowSimTest` (homing tests now use `Kind.WING`).

## 2. Sprenger roots and forbids the shunpo

- Pure rules: `core/uryu/BindBook.java` (uuid -> end tick; `bind` keeps the longer, `active`, `onDamage` ends the bind and says whether it broke one, `release`, `expired`). `UryuBalance.SPRENGER_BIND_TICKS` = 24 (from the bind at 0.8 s to the explosion at 2.0 s = 1.2 s), the Slowness/Jump Boost numbers are gone. Tests `BindBookTest` (7): window 116..139 for a pentagon cast at 100, ends exactly at the explosion tick, any damage ends it, damage to a not bound one changes nothing, the own explosion is released first and so not counted as a break, early detonation, longer bind kept, clear.
- Server: `server/uryu/SprengerBinds.java` (new). `bind` adds two temporary attribute modifiers (ids `reiatsu_test:uryu_sprenger_root` for `GENERIC_MOVEMENT_SPEED` and `reiatsu_test:uryu_sprenger_nojump` for `GENERIC_JUMP_STRENGTH`, ADD_MULTIPLIED_TOTAL -1.0, never saved to disk) to every living entity (players and mobs) inside at the bind moment. Removed in every end case: time up (`tick`), any damage (`AFTER_DAMAGE`, not blocked, > 0), death (`AFTER_DEATH`, which also covers the killing blow), logout (`DISCONNECT`), change of dimension (the entity is no longer in its world: released through the player lookup), the caster's pentagon cancelled (caster dead, logged out, left the dimension, state changed to BASE or sealed, pentagon replaced: `UryuManager.freeBound`), the explosion (freed BEFORE the damage, `UryuManager.explode`), a focused arrow's early detonation (same path), server start/stop (`clearAll`). The explosion keeps 9 HP (13.5 early).
- `UryuManager`: `Trap` now carries the uuids it bound (`:70`), `bind` (`:549`), `explode` (`:560`), `freeBound` (`:204,216`), `SprengerBinds.tick` before the pentagons tick (`:252`), `SprengerBinds.init()` (`:119`).
- Shunpo ban (shared file, minimal insert): `ZanpakutoManager.performShunpo` `:351` at the top: `SprengerBinds.shunpoBanned(p)` -> action bar `message.reiatsu_test.denied.shunpo_bound` (lang ru/en), reply `ResultCode.COOLDOWN` (no new enum value; the HUD shunpo cell shakes with the gold seconds through the existing `SideHud.onResult`, no extra client code), no cost, no cooldown, `TransitionResult(COOLDOWN, ON_COOLDOWN, state, state, [])`.

## 3. The bow in the hand

Convention verified in the code and the manifests: OBJ units are metres = blocks of the model space (`ObjItemUnbakedModel` emits the OBJ positions around the centre 0.5 with no factor), the display transform scales that (translation in 1/16 block is applied before the scale, so the grip stays on the fist). Numbers: Rukia sword `rukia_sealed_drawn` is 0.9785 m long, display scale 2.4 in first person (2.35 blocks) and 1.6 in third person (1.57 blocks). The bow `uryu_bow` is 1.1 m (z -0.55..0.55), arrow 0.5 m. Before: scale 0.7 / 0.9 = 0.77 / 0.99 blocks (a third of the sword in first person).

New `models/item/uryu_cross_display.json`: first person scale 1.8 (x 2.57; bow 1.98 blocks, 0.84 of the sword's 2.35; the limbs reach about one block above and below the fist), third person scale 1.4 (x 1.56; bow 1.54 blocks, equal to the sword's 1.57, from about the knee to above the head; the brief's "1.2 blocks" would be scale 1.09 with this 1.1 m model, which the user already finds too small, so the sword's size was taken). Rotations and translations unchanged, so the solved orientation (bow upright, string toward the player, limbs up and down) is the same. The nocked arrow, the V string and the pull (0.04 to 0.30 model units) are baked in the same model space and scale with the display; `UryuBowPose` pre-rotates about the grip and does not depend on the scale (comment added). String ribbon half width 0.0035 -> 0.005 (1.4 cm third person, 1.8 cm first person).
Side effect handled: the sealed pendant (`uryu_cross`, 12 cm) shares the display transform and had `hand_scale` 3.0; with the bigger display it is now 1.5 (`zanpakuto/uryu_cross.json`), effective 2.7 first / 2.1 third person (was 2.1 / 2.7), i.e. 0.32 and 0.25 blocks.

## Shared-file edits (all small inserts)

| File | Line | What |
|---|---|---|
| `server/ZanpakutoManager.java` | 351-358 | shunpo ban refusal at the top of `performShunpo` |
| `server/CastContext.java` | 99, 153-156 | Licht Regen aim range, effect params |
| `core/state/AbilityId.java`, `BalanceConfig.java` (230-233), `core/combat/CastGestures.java`, `NewCharacterGestures.java`, `server/AbilityExecutor.java`, `client/hud/StripHud.java` | -- | HOMING_VOLLEY renamed LICHT_REGEN, new balance row with `AIM_AT_RELEASE` and `DAMAGE` (BankaiTest requires a damage key) |
| `net/UryuArrowsS2C.java` | MAX_ARROWS | 48 -> 96 |
| `test/.../R8MechTest.java`, `NewCharactersTest.java` | -- | Licht Regen listed as aimed at the release / new row |
| lang ru/en, `design/VOICE_PHRASES.md`, `tools/gen_voice_phrases.py`, `voice/phrases.json` | -- | names, phrases, shunpo_bound message |

## UNVERIFIED in game

1. The rain: that the 6 arrows per tick all reach the clients (96 per list packet), the look of 36 simultaneous arrows with the lean renderer, FPS during the 3 s; that arrows spawned 14 blocks up in unloaded/other chunks or inside a ceiling behave (they are blocked at once inside a block); the sky aim case (the square is drawn at the aim, the server uses the floor found up to 6 blocks under it).
2. The effect event of the Licht Regen now arrives at the RELEASE (0.6 s after the key), so the placeholder thrust gesture and the HUD effect start 0.6 s late; the square outline and the first arrows come together. A raise-the-bow animation does not exist.
3. The root: that a movement-speed modifier of -100 percent really stops a sprinting or flying player and that the client receives both modifiers (attribute sync); momentum before the bind (a runner keeps sliding for a few ticks by friction); mobs with a flying speed (bats, ghasts, blazes) are not stopped (only walking speed and jump); players in a boat or on a horse are not rooted.
4. `AFTER_DAMAGE` fires for every damage source (fire, fall, other mods' abilities) and not for shield-blocked hits; the root breaks on them.
5. The shunpo refusal: the key answer COOLDOWN shakes the shunpo cell, the action bar message shows; the generic client toast logic for COOLDOWN does nothing (checked in `HudModel.onResult`, slot is null for a shunpo).
6. The bow scales: first person 1.8 may clip the top of the screen or sit too far right/low with the vanilla pull transform; third person 1.4 may touch the ground in front of the feet; the pendant size in hand (hand_scale 1.5).
7. Everything from `uryu_u4_u5.md` UNVERIFIED that was not touched.

## Manual checklist

Setup: `/give @s reiatsu_test:uryu_cross`, `/reiatsu dev god on` if needed, a second client or zombies/a player dummy; J (draw), phrase "Blut Arterie" for SHIKAI, then Rage and "Quincy Vollstandig" for BANKAI.
1. Bow size: J, look at the bow in first person (F1 off): it is a big bow, upright, string toward you, roughly half the screen high; hold right click: the string and arrow follow, scale looks consistent. F5: third person, the bow is about as long as a sword of the other characters (torso to above the head); draw in third person: archer pose, bow still upright. SEALED: the pendant in hand is about 30 cm in first person and 25 cm in third person.
2. BANKAI, press H (Licht Regen): the bow/hand goes up for 0.6 s (the old thrust gesture), then a faint square outline appears where you aimed at that moment (turn the head during the 0.6 s: it must follow the late aim), within 28 blocks. For 3 s arrows fall into the square only (9 x 9 blocks, nothing lands outside the outline), slightly slanted, none inside the roof if there is one (stand a roof over the area: arrows stop at it). Log lines: `[uryu] <name> licht regen at (...) square 9x9 for 60 ticks`.
3. Stand a zombie (or second player) in the square and stay out of it: damage in 1 HP steps (`[dmg]` lines), total roughly 6 to 12 HP and never above 14 per cast; stand in the square yourself: you take nothing; a team mate/creative player takes nothing.
4. Cooldown 24 s, cost 35 reiatsu (HUD strip). Spam the key early: denied, nothing spawns. Lose the bankai (`/seal`) during the rain: the arrows disappear.
5. SHIKAI, H (Sprenger) on a zombie and a player dummy standing inside: at 0.8 s the log says `sprenger bind: 2 rooted for 24 ticks`; both cannot walk or jump (try to push the player forward with W and space) until 2.0 s; the explosion hits for 9 HP and the log says `bind of ... ends (explosion)`.
6. Press Y (shunpo) as the rooted player: nothing moves, action bar "Bound by the Sprenger: no shunpo", the shunpo cell shakes; no cooldown starts. After the root ends, Y works.
7. Hit the rooted player (a snowball, a sword, Seele Schneider of Uryu): the log says `bind of ... broken by damage`, he can walk and shunpo at once. Same for a mob: it moves again after a hit.
8. Kill the rooted one inside the pentagon, log out, change dimension (nether portal is slow, use `/execute in minecraft:the_nether run tp @s ~ ~ ~`), die yourself as the caster, `/seal`: nobody stays rooted (movement and jump return, no stuck player; relog too).
9. A Focus 3 arrow into a pentagon with a rooted target: it explodes early (13.5 HP) and the root ends at the detonation.
