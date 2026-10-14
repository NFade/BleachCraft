# Uryu U4 and U5 (+ U5b/U6a): bow, projectiles, shikai (branch `gin-uryu`)

Source of truth: `design/URYU_PLAN.md` (revision 3). The game was NOT run (not allowed); everything below was derived from the mapped 1.21.1 bytecode and the code of this mod. Build and all unit tests pass (`./gradlew build`).

## What changed

### Bow (task 1)
- `registry/UryuCrossItem.java`: `use` starts the use hold on both sides (`setCurrentHand`, CONSUME) when the stack is drawn (`isBow`: component `release_state` is not SEALED) and the hand is the main hand; `getUseAction` = `UseAction.BOW`; `getMaxUseTime` = 72000; `onStoppedUsing(stack, world, user, remainingUseTicks)` runs on the SERVER only (the stop-use packet of the key release calls `LivingEntity.stopUsingItem` -> `ItemStack.onStoppedUsing`) and calls `UryuManager.releaseBow(player, 72000 - remaining)`. SEALED: `use` passes, the pendant does nothing.
- `core/uryu/BowDraw.java`: the vanilla curve `(t*t + 2t)/3`, t = ticks/20, capped at 1; `fires(p)` = p >= 0.1 (so a release needs 3 ticks); damage factor = power, speed factor = `0.3 + 0.7 * power`. `UryuBalance.BOW_*`.
- `server/uryu/UryuManager.java:359` `releaseBow`: power -> `Shots.basic(stacks, arterie, power)`, consumes Focus, spawns the Heilig Pfeil in the `ArrowSim` (no vanilla arrow, no ammo, no durability), plays the bow sound. `BASIC_COOLDOWN` is now 4 ticks (anti spam only; the draw is the pause).
- Interaction with the sword code: `registry/ZanpakutoItem.java:61` `isBlocking` is false for `UryuCrossItem`, so there is no parry window, no ability lock, no block pose, no shield veto in `LivingEntityMixin.blockedByShield`. Better Combat: `uryu_cross` has no `weapon_attributes` json, so BC never attacks with it (unchanged). `client/model/FpMotion.java:262` `handModel` returns null for Uryu (no two handed sword stance). The third person scabbard/pose code only works when the model has a scabbard mesh, which Uryu has not.
- Hand pose: vanilla does it. `PlayerEntityRenderer.getArmPose` returns `BOW_AND_ARROW` for `UseAction.BOW` of the active hand (both arms forward, third person), and `HeldItemRenderer.renderFirstPersonItem` applies the vanilla bow pull transform (`applyBowTransformation`: move, tilt, 20 percent stretch along the arrow axis) for first person. The mod's first person arm (vanilla arm drawn in `FirstPersonHand`) is drawn inside the same matrix, so it follows.

### No slowdown (task 2)
- `client/mixin/ClientPlayerEntityMixin.java` (registered in `reiatsu_test.client.mixins.json`): `@Redirect(method = "tickMovement()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))`. Verified with `javap -c` on the mapped client jar (`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-*/`): in `ClientPlayerEntity.tickMovement` the call `isUsingItem()` at offset 183 guards `if (isUsingItem() && !hasVehicle())` { `input.movementSideways *= 0.2f; input.movementForward *= 0.2f; ticksLeftToDoubleTapSprint = 0 }`, and it is the only `isUsingItem` call in that method (the others are in `canStartSprinting` and `isUsingItem` itself). The redirect returns `isUsingItem() && !(activeItem instanceof UryuCrossItem)`. Sprint: vanilla `canStartSprinting` still refuses to START a sprint while using an item, an already running sprint continues (no `isUsingItem` in the sprint stop test), so a runner who starts to draw keeps running. The server has no matching slowdown: it never reads the use state for speed and the mod sets no attribute modifier for Uryu (grep of `MOVEMENT_SPEED`/`SLOWNESS` shows only Gin and Sprenger).

### Homing (task 3)
- `core/uryu/Homing.java`: `pick(...)` = nearest valid candidate within the radius and the half cone, players before mobs, never one behind a block (`visible` predicate), a locked target is kept up to radius * 1.25 (except a locked mob is dropped for a qualifying player). `ArrowSim` (`Spec.auto`, `HitWorld.candidates`, `Arrow.steering/lockedTarget`): per tick pick, then `turnToward` with the per tick limit; an already hit body is not chosen again; deterministic (candidates sorted by id).
- Numbers (`UryuBalance`): basic 2.5 degrees per tick, radius 14, half cone 60; Z 10 degrees per tick (the lead said about 8; at the arrow speed 2.4 a turn radius of 14 blocks is the lowest that still catches a player 30 degrees off, see `HomingTest`), radius 20, half cone 100. "Cone" is a HALF angle.
- Glue: `server/UryuTargets.homingTargets` (the usual `Targeting.valid` rules, no creative players), `UryuHitWorld.candidates`.
- Z arrow `UryuManager.focusShot`: 7 HP base, speed 2.4, life 60, Focus bonus as before (3 stacks: 14 HP, pierce, no gravity).

### Visible arrows (task 4)
- `client/fx/UryuArrowRenderer.java`: the real `uryu_arrow` mesh (`FxMesh.load("uryu", "uryu_arrow")`, drawn with `RenderLayer.getEntityCutoutNoCull` at full light), oriented along the velocity (proper rotation basis), extrapolated by the time since its packet; scale 1.6 (Z arrow 2.6); glowing head, a trail along the last 8 packet positions (so it bends with the homing), a ring while steering, END_ROD sparks, a launch flash when a new id appears, a hit spark and chime from the new `UryuArrowsS2C.IMPACT` packet. Packet flags per arrow = focus + 4 * kind + 32 * steering. Hooked in `FxClient.init` (one line).
- Nocked arrow and pulled string: `ObjItemUnbakedModel.buildBow` bakes the bow, a V string (own quads, both windings) and the arrow; `ObjItemBakedModel.emitBowDraw` draws them while the holder uses the item, with the pull following `BowDraw.powerAt(useTime + partialTick)` (client only, first and third person).

### Held bow display (task 5)
- Numbers solved with the vanilla frames: `models/item/uryu_cross_display.json`. First person: rotation [178.95, -9.89, 173.91] (bow upright leaning 6 degrees to the screen centre, arrow axis forward and 10 degrees to the left), translation [2.14, -0.29, -5.06] (the grip on the vanilla fist `FIST_X/Y/Z` of `FirstPersonHand`, the same translation the Rukia display uses), scale 0.7. Third person (idle arm pose `ITEM`, pitch -18 degrees): rotation [-118, 0, -180], translation 0, scale 0.9. The method: the hand frame is `Rx(-90) Ry(180)` composed with the arm rotation `Rz(roll) Ry(yaw) Rx(pitch)` and the model flip (x, -y, -z) of the entity model; the bow axes (OBJ: up +Y, arrow +Z, string side -Z, from `gin_uryu_models.md`) are mapped to the wanted world directions and the Euler angles `Rx Ry Rz` (what `Quaternionf.rotationXYZ` builds) are solved; the same method reproduces the Rukia third person display [45, 180, 0] as "blade up and forward, edge forward", which validates the frames.
- While drawing in third person the arm pose changes to `BOW_AND_ARROW` (pitch -90, yaw -0.1): `client/model/UryuBowPose.java` pre-rotates the item about its grip by `R_draw * R_idle^-1` (draw rotation [-180, 5.73, -180]), pushed in `HeldItemRendererMixin` (`reiatsu$uryuPushed`). Right handers only.
- Deviation from the brief: the bow is held in the item hand like the vanilla bow (right hand for a right hander, the other arm pulls); a bow in the left hand with the right hand drawing would need a custom arm pose (stage U7).
- The 12 cm pendant: manifest keys `hand_scale` / `hand_yaw` (new, `ItemManifest`) enlarge it 3 times and turn it to face the camera in the hand; ground, item frame and head scales are set for the pendant. New icons `uryu_cross_sealed_icon.png`, `uryu_bow_icon.png` (the item showed the Rukia icons).

### Shikai (U5)
- Z homing arrow: see above (instant cast on the key, fires along the aim).
- Sprenger fx (`UryuFx.trap/burst`): five `uryu_seele` daggers drop into the pentagon (`UryuSeeleRenderer`), a glowing pentagram on the ground (ring, star, light pillars) that pulses faster until the explosion with a flash at the bind moment, an explosion with flash, shock ring, light pillar, particles and sound.
- Hirenkyaku was removed (user decision): the "moved too quickly" kick from the 2 blocks per tick velocity packets is gone with it. The replacement Seele Schneider lunges 1.5 blocks with 3 packets of 0.36 blocks per tick (far under the speed the server accepts) and the vanilla friction does the rest (`SeeleWingTest` simulates 1.5 blocks).
- Seele Schneider (B, SHIKAI): `core/uryu/SeeleCombo`, `UryuManager.seeleSchneider/tickSeele/slash`; 3 slashes at ticks 3, 8, 13 in a 3.5 block, 100 degree cone (centre or nearest box point, line of sight), 2.5 HP `PLAYER_ATTACK`, i-frames reset, the third knocks back 1.0 (`UryuTargets.knockback`); 10 reiatsu, 5 s; `Focus.grace(12)` so the stacks survive the lunge, nothing is consumed. Fx: a big `uryu_seele` blade (scale 8.5) sweeps the cone in 0.16 s, a crescent trail, a hit spark per target (`UryuArrowsS2C.SLASH`, `IMPACT` with kind 99).
- Wing barrage (Z, BANKAI): `core/uryu/WingBarrage`, `UryuManager.wingBarrage/tickWings/fireWing`; 120 ticks, targets chosen every 8 ticks (nearest valid, line of sight, max 4, distinct, slot i fires `2 * i` ticks into the cycle), arrows leave the wing tips (`WingBarrage.tip`, body yaw), fixed target homing 12 degrees per tick, 2 HP, `Kind.WING` (i-frames reset); 28 reiatsu, 22 s; cleanup through `cancelAll` (death, logout, sealed, BASE) and `onBankaiEnd` (state left BANKAI, the existing hook in `endBankaiEffects`). Fx: `UryuFx.wing` draws two angular wings (spars, fan of feathers, trailing edge) from glow streaks with a pulse per volley; the arrow launch flash is the muzzle glow at the wing tip.
- Shunpo (user decision): `ShunpoSpec.forCharacter` (+`UryuBalance.SHUNPO_BONUS` = 2 blocks for Uryu only) used in `StateMachine.shunpo`; `ShunpoFx` Uryu branch + `UryuFx.shunpoReishi` (blue trail, Reishi platform discs at the start and the arrival); the shunpo does not reset Focus (`UryuManager.onShunpo`).
- Focus: `core/uryu/Focus` sneak rule (limit 0.09 b/t while sneaking, 0.02 standing), `grace`; reset by push through `LivingEntityMixin.takeKnockback` -> `UryuManager.onPushed` (covers hits and `Targeting.knockback`; `BarrierShove`/Gin pulls move the player, which the speed rule catches). HUD: `client/hud/PlateHud.drawFocus` three Reishi diamonds right aligned in the row under the Rage bar (rectangles only), synced by the new `UryuArrowsS2C.FOCUS` packet (replaces the action bar text).

## Edits to shared files (all small inserts)
| File | Line | What |
|---|---|---|
| `registry/ZanpakutoItem.java` | 61 | `isBlocking` false for `UryuCrossItem` |
| `mixin/LivingEntityMixin.java` | 70-72 | `else` branch of the knockback hook: `UryuManager.onPushed(self)` |
| `server/ZanpakutoManager.java` | 409 | `UryuManager.onShunpo(...)` after the teleport |
| `core/state/StateMachine.java` | 394 | `cfg.shunpo().forCharacter(...)` |
| `core/state/ShunpoSpec.java` | whole | `forCharacter` (Uryu +2 blocks) |
| `core/state/AbilityId.java`, `BalanceConfig.java`, `core/combat/CastGestures.java`, `NewCharacterGestures.java`, `server/CastContext.java`, `AbilityExecutor.java`, `client/hud/StripHud.java` | -- | rename HIRENKYAKU -> SEELE_SCHNEIDER, LICHT_REGEN -> WING_BARRAGE (same codes 20/21, effects 74/75) and the balance rows (10 / 5 s, 28 / 22 s) |
| `core/obj/ItemManifest.java` | handScale, handYaw | new optional manifest keys |
| `client/fx/FxClient.java` | 61 | `UryuArrowRenderer.init()` |
| `client/fx/FxEvents.java` | 36 | `UryuFx.clearWings` on the bankai cleanup event |
| `client/fx/ShunpoFx.java` | 104, 138, 162-165 | Uryu look |
| `client/hud/PlateHud.java` | 87, 677 | `drawFocus` |
| `client/model/FpMotion.java` | 262 | no sword stance for Uryu |
| `client/mixin/HeldItemRendererMixin.java` | 90, 110, 139 | third person draw pose |
| `client/model/ObjItemBakedModel.java`, `ObjItemUnbakedModel.java` | -- | bow draw meshes, hand scale/yaw |
| lang ru/en, `design/VOICE_PHRASES.md`, `tools/gen_voice_phrases.py`, `voice/phrases.json` (regenerated with `python -I tools/gen_voice_phrases.py`) | -- | names and phrases of the two renamed abilities |

## Exact mixin and targets
- NEW `client/mixin/ClientPlayerEntityMixin` -> `net.minecraft.client.network.ClientPlayerEntity#tickMovement()V`, `@Redirect` of `ClientPlayerEntity#isUsingItem()Z` (yarn 1.21.1+build.3).
- Existing `HeldItemRendererMixin` (`HeldItemRenderer#renderItem(LivingEntity, ItemStack, ModelTransformationMode, boolean, MatrixStack, VertexConsumerProvider, int)` HEAD / RETURN): one more push/pop pair. Existing `LivingEntityMixin#takeKnockback`: one more branch.

## Decisions worth knowing
- Focus is not consumed or reset by the Seele combo, the shunpo or the wing barrage; the Z arrow and the bow consume it.
- The impact packet and the FOCUS packet go through the existing `UryuArrowsS2C` (new kinds IMPACT 4, FOCUS 5, SLASH 6), no new payload type.
- The `LichtRegen` and `Hirenkyaku` pure classes and their tests were deleted (the old numbers are in git).

## UNVERIFIED in game
1. Everything visual: the third person bow orientation (idle and drawing) and the first person pose are computed, not seen; the two Euler triples are the first thing to tune (`uryu_cross_display.json`, `UryuBowPose.IDLE/DRAW`). The vanilla pull transform moves the bow toward the screen centre: the bow may sit too far left/high with the 0.7 scale.
2. The nocked arrow/string transform in model space (orientation of the arrow, pull distance 0.04 to 0.30), the single sided ribbon quads, back face culling of the bow mesh, emissive layers.
3. The redirect applies (a failed injection would stop the game at start-up because `defaultRequire` is 1: the log says so); the sprint behaviour while drawing.
4. `onStoppedUsing` really receives the right remaining ticks on a dedicated server, and Better Combat does not eat the right click.
5. Homing feel (turn rates, radius, half cone), the Z arrow speed (2.4) against the 10 degree turn, arrows against players in different gamemodes.
6. Arrow render: extrapolation smoothness at 20 Hz, glow sprite and streak brightness, particle count with many arrows, depth/Fabulous.
7. Sprenger fx (dagger lean and size, pentagram brightness, explosion), the slash blade (scale 8.5 hilt offset), wings (size, flare, orientation with the body yaw), the Reishi discs.
8. Seele lunge: 1.5 blocks real distance on ground/ice/water, no rubber banding; third slash knockback strength; slashes through thin walls (line of sight is tested to the centre).
9. Wing barrage targets (line of sight to the centre), arrows leaving the tips, 4 targets, cleanup on bankai end/death; the `Kind.WING` hit reset against armor.
10. Focus HUD row position with and without the Rage bar and the voice toast; the sneak rule with ice/slabs.
11. Shunpo +2 and the grace window (3 ticks) against the position sync after the teleport.
12. The inventory icons (hand drawn 32x32), pendant size in hand.

## Manual checklist
Setup: `/give @s reiatsu_test:uryu_cross`, hold it, press J (draw), the phrase "Blut Arterie" or the key for SHIKAI later; `/reiatsu dev god on` if needed. Start the client and (for a second player) a second client or use a zombie/player dummy.
1. SEALED: right click does nothing; the pendant is a visible small cross facing you in hand.
2. J: bow in hand. Hold right click: the bow pulls (vanilla shake), the string bends back, an arrow is nocked, the arm pose is the archer pose in third person (F5); release at once (under 3 ticks): nothing; hold 1 s: a glowing arrow with a trail flies, a hit spark/chime on impact, damage 4 HP at full draw (log `[uryu] bow release ...`).
3. Walk and sprint while holding the draw: speed is NOT reduced (compare with a vanilla bow or the shield); sprinting stays.
4. Fire past a player/zombie off to the side (a few blocks): the arrow bends towards the nearest player first, a mob if there is no player; it does not bend through walls.
5. SHIKAI. Z: a faster bigger glowing arrow that curves strongly into the nearest player (stand 15 blocks away, aim 20 degrees off), 7 HP (log).
6. Sneak-walk slowly for 2 s: the HUD under the bars shows the diamonds fill (3 = white, pulsing). Walk normally, sprint, jump or get hit with knockback: they empty. A shunpo (the shunpo key) keeps them.
7. Shunpo as Uryu: 2 blocks further than Rukia, blue trail, Reishi discs.
8. B: Seele Schneider at a mob: a short lunge, three slashes (0.25 s), 3 x 2.5 HP, the third knocks it back; Focus stacks stay.
9. H: Sprenger on the floor 10 blocks away: five daggers drop in, the pentagram pulses faster, bind at 0.8 s, explosion at 2 s (9 HP; early with a Focus 3 arrow).
10. J, build Rage, BANKAI. Z: wings flare behind you for 6 s, up to 4 arrows every 0.4 s from the wing tips at the 4 nearest enemies (2 HP each), you keep walking and shooting; the effect ends with the bankai, at death or at /seal.
11. Log lines to look for: `[uryu] ... bow release`, `homing arrow (Z)`, `seele slash`, `wing barrage`, `[dmg] uryu...`.
