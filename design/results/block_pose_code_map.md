# Block pose code map (read-only investigation, 2026-10-13)

Scope: how the zanpakuto BLOCK pose (right mouse button held, main hand) is produced in first person (FP) and third person (TP), what is pure math in `core/`, and where a data-driven replacement fits.
Paths are relative to `D:\MineBleach\mod\src\`. `main` = `main/java/dev/minebleach/reiatsutest`, `client` = `client/java/dev/minebleach/reiatsutest/client`. Nothing was run; claims about Better Combat (BC) and playerAnimator internals come from this repo's own comments and code (the BC jar was not decompiled), marked UNVERIFIED where it matters.

NOTE: `GuardMotion`, `TwoHandMotion`, `TpPose`, `FpClamp` etc. are only referenced from the **client** source set (`src/client`), not from `src/main`. The `mixin/` directory under `main` holds only server-safe mixins (`BcPoseMixin`, `LivingEntityMixin`, `EntityMixin`). `core/` has no Minecraft imports (checked: all `core/combat` files in scope are plain Java; `TpPose` only imports `core.obj.DrawRig`).

---

## 0. The block state itself (shared by both views)

| Step | Where | What |
|---|---|---|
| Input | `main/registry/ZanpakutoItem.java:36-43` `use()` | Right click on MAIN_HAND: `user.setCurrentHand(hand)`, returns CONSUME (shield pattern). Off hand passes. |
| Use action | `ZanpakutoItem.java:46-48`, `:51-53` | `getUseAction = UseAction.BLOCK`, `getMaxUseTime = 72000` (`BLOCK_MAX_USE_TICKS`, line 16). So vanilla's own BLOCK arm pose is also "on" (see TP section, it is overridden). |
| Predicate | `ZanpakutoItem.java:59-61` `isBlocking(e)` | `e.isUsingItem() && e.getActiveItem().getItem() instanceof ZanpakutoItem` (not vanilla `isBlocking`, which has a 5-tick delay). `blockTicks` at :64-66 = `getItemUseTime()` or -1. |
| Vanilla shield rule cancelled | `main/mixin/LivingEntityMixin.java:28-30` | `blockedByShield` cancelled while `isBlocking`, so the BLOCK use action does not act like a shield. |
| Rules | `main/core/combat/BlockRules.java` | Gameplay only (no pose): `allowsAttack` (:126), `allowsAbility` (:131), `inParryWindow` (:141), `resolve` (:149), `abilityDamage` (:168). `CombatConfig.defaults()` = parry window 4 ticks, blocked melee x0.5, knockback 0.6, stagger 12 ticks amp 2. |
| Server hit resolution | `main/server/BlockParry.java:59-60` | Reads `blockTicks(defender)`, calls `BlockRules.resolve`. Pure gameplay, no pose. |
| Attack gate | `main/core/combat/BcAttackGate.java:197-202` `attacksDisabled` -> `main/server/BcCombatGate.java:31-40` | Each server tick sets BC `CombatFlags.API_DISABLED` when sealed OR blocking. So BC strike animations cannot start during a block. |

`BlockRules` / `BlockParry` / `BcCombatGate` / `CombatConfig` have **no pose numbers**. The pose is entirely in the FP and TP chains below.

---

## 1. Call chains

### 1a. THIRD person (what other players see, and the local player in F5)

1. `ZanpakutoItem.isBlocking` is true (`ZanpakutoItem.java:59`). The vanilla `UseAction.BLOCK` arm pose is applied by `BipedEntityModel.setAngles` first (vanilla, overwritten below).
2. BC stance suppressed: `main/mixin/BcPoseMixin.java:33-46` injects at RETURN of `net.bettercombat.logic.PlayerAttackHelper.poseForPlayer`. If a zanpakuto is in the main hand it returns `new Pose("", offHand)` (empty main pose) via `BcAttackGate.poseSuppressed` (`BcAttackGate.java:211-213`, which simply returns `holdsZanpakuto`; state and blocking args are ignored since tp_pose 2026-10-11). Registered in `main/resources/reiatsu_test.mixins.json:6`. So BC's `pose_two_handed_katana` (inherited from `data/reiatsu_test/weapon_attributes/*.json` = `{"parent": "bettercombat:katana"}`) is never shown, sheathed or drawn, blocking or not. BC strike animations (separate layer, priority 2000) still play. The comment (`BcPoseMixin.java:25-28`) says the client reads `poseForPlayer` every tick in `AbstractClientPlayerEntityMixin.updateAnimationsOnTick` (an empty base pose clears the layers) and the server writes it to the data tracker in `PlayerEntityMixin.post_Tick` (BC classes, UNVERIFIED by me).
3. Per-frame state/solve: `client/model/TpPoseDriver.java:117-166` `update(e)`:
   - `eligible` (:84-89): player with zanpakuto in main hand, not fall-flying/swimming/sleeping/climbing, alive; also needs a baked OBJ model (`ScabbardRenderer.modelOf`, :126).
   - `blockingOf` (:100-111): `isBlocking`, plus two fallbacks (local player: active item present and `getItemUseTimeLeft()>0`; or `isUsingItem && activeHand==MAIN`).
   - Block weight `st.b` smoothed in real time (:142-147): linear ramp, up `guard.in_s`=0.10 s, down `guard.out_s`=0.15 s; `st.hold` ramps with `hold.fade_s`=0.15.
   - Re-solve at most every 2 ms (:151-163): `TpPose.solve(new TpPose.Input(p /*DrawTracker.effectiveProgress*/, st.b, left, thin, hip, disp, rig), PARAMS)` (:161).
4. Pure solver: `main/core/combat/TpPose.java:288-352` `solve`. Block frame is `guardFrame` (:192-198) blended in with eased weight `bs = smooth(b)` (:301): sword = `blend(draw, guard, bs)` (:309), scabbard = `blend(saya, guard, bs*(1-p))` (:305). Fist targets via `handPoint` (:223) and closed-form IK `solveArm` (:245-267). Returns `Result{main, off: Arm(pitch,yaw,roll,dx,dy,dz), wMain, wOff, saya, sword Frame, gT, gQ (held-item correction)}`; left hander = mirror (:342-350).
5. Arms: `client/model/TpPoseLayer.java` is a playerAnimator `IAnimation` registered at priority 1500 (`TpPoseLayer.java:395-397`, called from `client/ReiatsuTestClient.java:31`). `isActive` = `TpPoseDriver.layerWeight(player) > 0.002` (:401). `get3DTransform` (:409-448) asks `TpPoseDriver.armTarget` (`TpPoseDriver.java:270-286`: `{w, pitch, yaw, roll, dx, dy, dz}`, `w = wMain/wOff * hold`) and blends it with the incoming value `value0` for `rightArm` / `leftArm`, ROTATION and POSITION (shoulder pivot shift).
6. Safety net: `client/mixin/PlayerEntityModelMixin.java:23-37` (HEAD: `TpPoseLayer.beginPass`; RETURN, not first-person pass: `TpPoseDriver.finishPass` at `TpPoseDriver.java:220-264`). If the layer did not write the arm in this pass, or something changed it afterwards, the solver values are applied/restored on the `PlayerEntityModel` arms and sleeves. Registered in `main/resources/reiatsu_test.client.mixins.json:7`. Added in round6 "tp_block" because the vanilla block pose looked like it was winning (comment `TpPoseLayer.java:18-28`: playerAnimator injects at the first `copyTransform` in `PlayerEntityModel#setAngles`, after vanilla arm poses, so vanilla cannot win).
7. Sword in the hand: `client/mixin/HeldItemRendererMixin.java:84-88` (non-FP, main hand, handedness matches) -> `client/model/BcItemPose.java:199-237` `push`. `TpPoseDriver.grip` (:314-320) gives the rigid correction `G` (`gT`, `gQ`); it is applied at `BcItemPose.java:203-215` weighted `grip.weight * (1 - w)` where `w` is the BC strike correction weight (probe of the playerAnimator `rightItem` bone, :167-178). A BC strike cross-fades it out.
8. Scabbard (when sheathed, and for the SEALED block it travels with the sword): `client/model/ScabbardRenderer.java:213` reads `TpPoseDriver.resultFor(e)` -> `Result.saya()` / `sword()` frames.
9. Optional ability gesture layer `client/model/TpCastLayer.java` (priority 1600, `FpCast.java:93` registers) only ADDS arm rotations during casts; it does not matter for the block (blocking forbids ability casts, `BlockRules.allowsAbility`).

Layer priority order (low to high): vanilla -> `TpPoseLayer` 1500 -> `TpCastLayer` 1600 -> BC attack layer 2000.

### 1b. FIRST person (local player only)

1. Same predicate, but FP only sets the weight when the block is on the MAIN hand: `client/model/FpMotion.java:274` `blocking = enabled("guard") && ZanpakutoItem.isBlocking(player) && player.getActiveHand() == Hand.MAIN_HAND`.
2. Frame tick: `HeldItemRendererMixin.java:118-123` (HEAD of `renderItem(float, MatrixStack, Immediate, ClientPlayerEntity, int)`) -> `FpMotion.frame(player, tickDelta)` (`FpMotion.java:265-424`). Early-outs and resets if the main hand is not a zanpakuto (:269-272).
3. Weight: `weight = GuardMotion.stepWeight(weight, blocking, dt, guard.in=0.10, guard.out=0.25)` (`FpMotion.java:275-276`; math `GuardMotion.java:107-110`: linear ramp, smoothstep applied later).
4. Pose pick (`FpMotion.java:385-395`):
   - drawn sword with two-hand hold on (`twoKeys`, `fp_two.enabled`, not sealed, hand model draws in hand): `TwoHandMotion.guard(PARAMS)` (`TwoHandMotion.java:285-287`);
   - one-hand fallback: `GuardMotion.stance(PARAMS, 1f)` (`GuardMotion.java:113-116`);
   - sheathed (SEALED): `GuardMotion.sealedGuard(PARAMS)` (`GuardMotion.java:119-121`), strikes/recoils scaled by `guard.sealed_scale`=0.4.
   Then `pose = GuardMotion.pose(PARAMS, baseUse, guardPose, weight, time, parry, hit)` (:395; math `GuardMotion.java:155-158`: `base.lerp(stance, smooth(w)) + breath + parryKick + hitKick`).
   Sealed extras: rest shift `guard.sealed.rest` fades out as the block rises (:397-402). Drawn: `TwoHandMotion.idle` breathing added (:404-407). Ability gesture delta added after (:410-418).
5. Optional reach limiter (`limitGesture`, :430-476) uses `FpClamp.factors` / `FpGeometry` / `FpRig` / `FpCentre`, but only for ability GESTURES (`gs != null`); the block pose itself is NOT clamped (see risks).
6. Apply: `current = pose` (:419). Then per hand render:
   - `HeldItemRendererMixin.java:59-61` -> `FpMotion.push` (`FpMotion.java:586-604`): translate by `pose.pos`, rotate by `pose.quaternion` about the pivot (grip, or `two.pivot_s` point on the blade), for the arm + sword as one rigid body (`FirstPersonHand.render`, `HeldItemRendererMixin.java:62`, uses `FpMotion.armShift` / `armRot` at `FpMotion.java:488-505` for the right forearm fist shift/turn in two-hand mode).
   - Scabbard + the hand that holds it in the sealed block: `ScabbardRenderer.java:115` `FpMotion.pushSaya` (`FpMotion.java:511-531`), `:138` `FpMotion.sealedLeftS`, `:152` `FpMotion.turnSealedLeftArm`.
   - Free left hand: `HeldItemRendererMixin.java:114` -> `FpMotion.renderLeftGuard` (`FpMotion.java:614-694`), placed with `TwoHandMotion.leftFist` / `GuardMotion.leftFist`, weights `TwoHandMotion.leftWeight` / `GuardMotion.leftWeight`.
7. BC interplay in FP: `client/mixin/BcFirstPersonModeMixin.java:70-77` only matters for strikes (returns `FirstPersonMode.NONE` when `fp_bc_mode: 0`). Default since round 7 is `useBcFirstPerson()` = true (`FpMotion.java:169-171`), so BC's own FP strike animation is used and `FpMotion`'s strike/live-swing code is bypassed; the block (guard) path of `FpMotion` is still the one in use. `TpPoseLayer` is NOT part of the FP pass (`TpPoseLayer.java:340-342`, `PlayerEntityModelMixin.java:34`).

---

## 2. Numeric parameters that define the pose (current shipped defaults)

Conventions: FP numbers are `FpPose(x,y,z, rotX,rotY,rotZ)` in the hand frame of the item renderer (blocks, degrees), right-hander layout, left hand mirrors x of shifts and y,z of rotations. TP numbers are in player-body px (1/16 block), degrees, y down, front = -z, right-hander layout (left hander mirrored by solver). Keys marked (dev) can only be changed through `pose_override.json` in a development environment; production uses defaults.

### 2a. FIRST person

Config source: `FpMotion.PARAMS` (`FpMotion.java:69-92`), key `a.b.c` -> `pose_override.json` -> `"fp_a": {"b": {"c": ...}}` (`FpMotion.find`, :151-162). Global switches: `fp_enabled`, `fp_guard.enabled`, `fp_two.enabled` (`FpMotion.enabled`, :173-175).

Block timing
| Key | Default | Where |
|---|---|---|
| `guard.in` | 0.10 s | FpMotion.java:275 |
| `guard.out` | 0.25 s | FpMotion.java:276 |
| `two.in` / `two.out` (stance weight, not the block) | 0.30 / 0.12 s | FpMotion.java:285 |

Block poses (all `FpPose`: pos x,y,z blocks; rot x,y,z degrees)
| Pose | Key (pos / rot) | Default | Source |
|---|---|---|---|
| Two-hand block (drawn sword, normal case) | `two.guard.pos` / `two.guard.rot` | pos (-0.32, 0.10, -0.06), rot (-14, -16, 54) | `TwoHandMotion.GUARD` :248, getter :285 |
| One-hand block (fallback) | `guard.pos` / `guard.rot` | pos (-0.22, 0.14, -0.04), rot (-12, -22, 62) | `GuardMotion.GUARD` :64, `stance` :113 |
| Sheathed (SEALED) block | `guard.sealed.pos` / `guard.sealed.rot` | pos (-0.212, -0.15, 0), rot (-20, 0, -36) | `GuardMotion.SEALED_GUARD` :70, getter :119 |
| Sheathed rest hold shift (fades out during block) | `guard.sealed.rest` | (0, -0.04, 0) | :75, getter :124 |
| Left hand on scabbard (sealed block), metres from grip | `guard.sealed.left_s` | 0.18 | :77, :129 |
| Left forearm turn about its fist (sealed block, deg) | `guard.sealed.left_arm_rot` | (0, -75, 0) | :79, :134 |
| Strike/recoil scale while sealed | `guard.sealed_scale` | 0.4 | FpMotion.java:386 |

Rest stance the block blends FROM (two-hand hold, not the block, but the block is `lerp` of it)
| Key | Default | Source |
|---|---|---|
| `two.pos` / `two.rot` | (-0.47, 0.005, 0.06) / (-36, 0, -8) | `TwoHandMotion.STANCE` :243 |
| `two.pivot_s` (pivot along blade from grip, model units) | -0.10 | :250 |
| `two.left.s` (left fist on hilt) | -0.115 | :256 |
| `two.left.offset` | (0,0,0) | :258 |
| `two.left.arm.rot` | (-15, -30, -35) | :263 |
| `two.arm.shift` (right fist vs sword) | (0.0176, 0.1436, 0.0205) | :268 |
| `two.arm.rot` | (-15, 30, 35) | :270 |
| `two.left.in_dx/in_dy/in_dz` (left hand entry) | 0.30 / -0.90 / 0.10 | FpMotion.java:~670 |
| `two.breath`, `two.breath_hz` | 0.003, 0.4 Hz | :296-297 |
| `guard.left.s`, `guard.left.offset` (one-hand fallback) | 0.30, (0,-0.02,0.06) | FpMotion.java:634, GuardMotion.java:82 |
| `guard.left.in_dx/in_dy` | 0.25, -0.8 | FpMotion.java |
| Held lift of the sword in hand frame (manifest `held.lift`, `held.arm`) | lift (0, 0.06, 0), arm (0, -0.12, 0) | `assets/reiatsu_test/zanpakuto/{sode_no_shirayuki,senbonzakura}.json` line 8; dev override keys `lift`, `arm`, `roll_stow`, `roll_held` (`HeldPose.of`) |
| Display transform (`FpRig.DEFAULT`) | rot (-172, 1, -173.1), trans (2.14/16, -0.29/16, -5/16), scale 2.4, lift (0,0.06,0) | `FpRig.java:41` (real values come from the item display JSON at runtime) |

Blocking breath and recoils (added on the pose)
| Key | Default | Source |
|---|---|---|
| `guard.breath` / `guard.breath_hz` | 0.004 / 0.5 Hz | GuardMotion.java:141-142 |
| Parry kick `guard.parry.pos/rot/rise/decay/freq/side_x` | pos (0,0.04,-0.14), rot (-16,0,5), 0.05 s, 0.15 s, 3.0 Hz, 0.04 | :80, :86, :186 |
| Hit kick `guard.hit.*` | pos (0,-0.01,0.05), rot (7,0,-2), 0.03 s, 0.10 s, 3.5 Hz, 0.03 | :81, :86 |

Reach limits (only for gestures, not the block): `FpClamp.NEAR` 0.12, `FpCentre.HALF_X/HALF_Y` keep-out rectangle, `FpClamp.Limits.of` (xMax 1.0, yMin -1.2, yMax 0.95) (`FpClamp.java:13,34-36`).

### 2b. THIRD person

Config source: `TpPoseDriver.PARAMS` (`TpPoseDriver.java:47-74`): `pose_override.json` section `"tp"`, flat dotted keys, any subset. Only read when `FabricLoader.isDevelopmentEnvironment()` (`HeldPose.java:263,272-274`).

| Key | Default | Meaning | Source |
|---|---|---|---|
| `guard.pos` | (0.5, 7.5, -6.0) px | grip centre of the sword in the block | `TpPose.GUARD_POS` :105, getter :121 |
| `guard.tilt` | 40 deg | tip angle above horizontal | `GUARD_TILT` :106 |
| `guard.fwd` | 5 deg | blade turned forward | `GUARD_FWD` :107, used :194 |
| `guard.edge` | 0 deg | roll about the blade | :196 |
| `guard.lat_r`, `guard.lat_l` | 1.3, -1.3 px | sideways place of right/left fist on the sword | `GUARD_LAT_R/L` :109-110 |
| `guard.c_l` | -2.4 px | left fist along blade (lower on hilt) | `GUARD_C_L` = `C_L` :111 |
| `grip.c_r` | -0.8 px | right fist along blade (`C_R`) | :95 |
| `grip.c_l` | -2.4 px (`C_L`) | | :97 |
| `grip.lat_r`, `grip.lat_l` | -1.8, 1.8 px | non-block grip lateral | TpPose.java:314-316 |
| `sealed.lat_r`, `sealed.c_r` | 0.0, -0.8 | right hand on the sealed hilt | :315,317 |
| `saya.lat`, `saya.c` | 0.0, 4.5 px | left hand on scabbard | :322 |
| `arm.max_shift` | 6.0 px | IK shoulder slide limit | :319 |
| `arm.yaw_r`, `arm.yaw_l` | 0, 0 deg | arm twist | :324-325 |
| `hold.pos`, `hold.tilt`, `hold.edge` | (0,6.5,-5.4), 12, 0 | upright two-hand hold (block blends from this) | :114,118,183 |
| `draw.pull` | 0.5 | share of draw done by left hand | :294 |
| `guard.in_s`, `guard.out_s`, `hold.fade_s` | 0.10, 0.15, 0.15 s | weights ramp | TpPoseDriver.java:143-145 |
| Arm skeleton constants (hardcoded) | rest shoulder (+-5, 2 or 2.5 if slim, 0) px, hand centre (+-1 or 0.5, 8, 0) | | TpPose.java:247-248 |
| Item chain constants (hardcoded) | `rotX(-90) rotY(180) translate(1/16, 0.125, -0.625)` | vanilla held item chain | TpPose.java:331 |
| `smooth()` easing on `b` | smoothstep | | TpPose.java:301 |
| Display transform used | from item model THIRD_PERSON_RIGHT_HAND | `TpPoseDriver.java:155-158` |
| Hip scabbard | pos (3, 9, -2.5) px, dir (0.35, 0.3, 0.89), scale 1.6 | manifest `draw.hip`, mirrored in `run/pose_override.json "hip"` |

State of the dev override file `D:\MineBleach\mod\run\pose_override.json` (read at runtime in dev only; NOT shipped): sections `hip`, `tp`, `bc_item`, `fp_two`, `fp_cast`, `fp_guard`, `fp_swing`. `tp` values equal the code defaults. `fp_two.guard.pos` there is (-0.32, **0.22**, -0.06) while the code default is y **0.10** (r8 lowered it); because the override wins in dev, a dev session currently shows the OLD pose, production shows the new default. `fp_guard` contains only the `sealed` block (same as defaults). `bc_item` (BC strike correction only): rot (-55,0,0), pos (0,-1.05,0.18), pivot (0,-2,1.75), fade_ticks 3.

BC side: `data/reiatsu_test/weapon_attributes/*.json` contains only `{"parent": "bettercombat:katana"}`, so no `pose` field of our own exists. The suppression is code (`BcPoseMixin`), not data.

---

## 3. What is pure math in `core/` (testable) vs Minecraft glue

Pure, no Minecraft imports (`src/main/java/.../core/combat`):
- `GuardMotion` (weights, stance lerp, breath, kicks, envelope, left fist), `TwoHandMotion` (poses, keys, left fist, arm shift/rot), `FpParams` (key lookup interface), `FpPose`, `FpEase`, `FpRig` (hand frame geometry), `FpGeometry`, `FpClamp`, `FpCentre`, `SwingMotion`, `LiveSwing`, `TpPose` (+ `TpMat4`), `BlockRules`, `BcAttackGate`, `CombatConfig`, `BlockOutcome`, `ParryBus/Event`, `DamageKind`.
- Tests that already exist (`src/test/java/.../core/combat`): `TpPoseTest` (307 lines), `SealedGuardTest` (116), `TwoHandMotionTest` (283), `FpMotionTest` (227), `FpClampTest`, `FpGeometryTest`, `FpCentreTest`, `BlockRulesTest` (141). The block FP maths and the TP solver are already unit-tested.

Minecraft glue (everything that touches MC / playerAnimator / BC):
- Server: `ZanpakutoItem` (use / isBlocking), `BlockParry`, `BcCombatGate`, `LivingEntityMixin` (`main`).
- `main/mixin/BcPoseMixin` (BC `PlayerAttackHelper.poseForPlayer`, Pseudo, remap=false; reads `ZanpakutoItem`, `ModComponents.RELEASE_STATE`).
- Client TP: `TpPoseDriver` (state, smoothing, input gathering), `TpPoseLayer` (playerAnimator `IAnimation`), `PlayerEntityModelMixin` (`PlayerEntityModel#setAngles`), `BcItemPose` (held-item matrix), `ScabbardRenderer`/`ScabbardFeature`.
- Client FP: `FpMotion` (clocks, `PARAMS`, applies the pose; contains a lot of state logic that is NOT in core), `FirstPersonHand`, `HeldItemRendererMixin`, `HandMath`, `HeldPose` (override file), `DrawTracker`.
- `HeldPose.overrideObject` is the only data-driven hook today, and it is dev-only.

Note: `TpPoseDriver.update` mixes game logic (eligibility, `blockingOf`) with smoothing; the smoothing math is trivially duplicated in `GuardMotion.stepWeight` (FP) and inline (TP :146), two different ramps (FP in/out 0.10/0.25, TP 0.10/0.15).

---

## 4. Could playerAnimator animations (JSON in `assets/reiatsu_test/player_animations`) replace the TP pose?

Short answer: yes for the arms; it needs a thin new layer plus a decision on the sword item bone. The infrastructure is half there.

Already in the repo:
- playerAnimator 2.0.4 is a hard dependency (`fabric.mod.json:24`, `build.gradle:128,134`).
- A `IAnimation` layer pattern exists three times: `TpPoseLayer` (priority 1500, `PlayerAnimationAccess.REGISTER_ANIMATION_EVENT`), `TpCastLayer` (1600), and BC's own (2000). The registration call sites are `ReiatsuTestClient.java:31` and `FpCast.java:93`.
- One animation asset exists: `assets/reiatsu_test/player_animations/senbon_slash_rl.json` (emote format: `emote.moves[]` with `tick`, `easing`, `leftArm/rightArm/head ... {pitch,yaw,roll,x,y,z}`, `isLoop`, `beginTick/endTick/stopTick/returnTick`, `degrees:false` = radians). It was exported from Blockbench ("Generated from Blockbench animation"). Note that nothing in the Java code loads it by name (grep for `player_animations` returns no code hit); it is picked up by BC through the weapon attack animation id, not by this mod's code. So there is NO existing loader/player of mod-owned animations in this repo; the first one would be the new work.
- The arm layer API is already the right level: `Vec3f get3DTransform(modelName, TransformType, tickDelta, value0)` with part names `rightArm` / `leftArm` and ROTATION + POSITION. The TP solver output (pitch, yaw, roll, dx, dy, dz in radians and px) maps 1:1 onto what an emote JSON stores per keyframe (rotation in radians, x/y/z in px).
- The item bone names `rightItem` / `leftItem` are already probed (`BcItemPose.java:173`), which shows the layer stack carries item-bone data in this setup.

How it could be played (two options):
1. Keep `TpPoseLayer` as the single owner. Replace the number source: `TpPose.solve` (or a small `BlockPoseData` provider) returns arm values sampled from a data file (JSON keyframe or the emote JSON) with the existing eased `st.b` as the time axis (0..1 progress), blended through `armTarget` (`TpPoseDriver.java:270-286`) exactly like today. Zero change to registration, `finishPass`, priority, or the BC interplay. Low risk.
2. Use playerAnimator's own playback: `PlayerAnimationAccess.getPlayerAssociatedData(player)` -> `ModifierLayer<IAnimation>` added at a priority below BC (2000) and play a `KeyframeAnimation` loaded from `player_animations/*.json` (`PlayerAnimationRegistry.getAnimation(Identifier)`) on block start; remove with fade on release. Pros: Blockbench authoring, native easing, fade-in/out built in. Cons: playerAnimator time is tick based (not render-time smoothed like `st.b`), start/stop is event driven rather than a continuous weight (fix: play on `isBlocking` edge, `replaceAnimationWithFade`); it does not know the sword hip/IK, the scabbard and the held-item correction `G`.

What option 2 cannot do by itself, because they come from `TpPose.solve` today:
- the SEALED case (scabbard + sword move together and the left hand tracks the scabbard);
- the held-item correction `G` (`gT`, `gQ`, `BcItemPose.java:203-215`) that puts the sword on the exact frame. An animation would need to drive `rightItem` (rotation + position) and the sword offset would have to be re-authored for the item bone. Today `BcItemPose` explicitly says the item bone is left untouched (`TpCastLayer.java:11-12`, "tp_pose.md").
- the slim/classic arm difference (`thin`) and handedness: playerAnimator mirrors for left-handed players only if the animation is flagged; the solver mirrors analytically.

Hook points that already exist for a data-driven replacement:
- `TpPoseDriver.PARAMS` (`TpPose.Params`): everything TP (`guard.*`, `grip.*`, `hold.*`) is already a flat key space; replacing the lookup with a loader of a resource-pack JSON (reloadable, not dev-only) is the smallest change and touches no solver code.
- `FpMotion.PARAMS` (`FpParams`): same for FP; `FpParams.DEFAULTS` is used by tests. A resource reload listener could produce an `FpParams` implementation instead of `HeldPose.overrideObject`.
- `HeldPose` (`HeldPose.java:259-325`) already hot-reloads `<gameDir>/pose_override.json`; its limitation is the `isDevelopmentEnvironment()` gate.

Recommendation for the cleanest place (see also section 5):
- One data file per pose set (suggested `assets/reiatsu_test/poses/block.json` or inside the existing manifest `assets/reiatsu_test/zanpakuto/<sword>.json`, which already has `stow`, `held`, `hip`), loaded through a `SimpleSynchronousResourceReloadListener`, exposed through the SAME two interfaces `FpParams` and `TpPose.Params`. Keep `GuardMotion` / `TwoHandMotion` / `TpPose` as the evaluators. That gives shipped, per-sword, reloadable (F3+T) data with no new code path in the render hooks.
- If real keyframed motion is wanted (not just two end poses and a ramp), add a pure `core` `PoseTrack` (keyframes, easing, sample(t)) with its own unit tests, feed it the weight `b` / `weight`, and let `TpPose.solve` / `GuardMotion.stance` read it. Author in Blockbench, convert with the same exporter used for `senbon_slash_rl.json`.

---

## 5. Risks

1. Two independent implementations. FP and TP are separate systems (`GuardMotion`/`TwoHandMotion` vs `TpPose`) with different parameter spaces (hand-frame blocks and degrees vs body px, IK). A data file must define both, or be authored once and converted. Block pose in FP does not "follow" TP numbers; the tp comment "same source" refers to draw progress only.
2. Dev override vs shipped defaults diverge. `run/pose_override.json` has `fp_two.guard.pos.y = 0.22` while the code is 0.10; anyone tuning in dev sees different values than production. Overrides are dev-gated (`HeldPose.java:263`), so any data-driven replacement must also remove or bypass that gate for shipping.
3. UNVERIFIED numbers. Both `GuardMotion` and `TwoHandMotion` and `TpPose` docs say the poses were laid out by python projection models and are UNVERIFIED in game (`GuardMotion.java:61`, `TwoHandMotion.java:233`). `TpPoseDriver.blockingOf` fallbacks are also marked UNVERIFIED (:98).
4. Vanilla/other layers fighting the pose. The round6 "tp_block" history shows the block arm pose was suspected to be overridden. The `PlayerEntityModelMixin.finishPass` safety net exists because of that; if playerAnimator plays a separate animation, the net would restore the old solver values over it (finishPass compares against `TpPoseLayer.Trace`, `TpPoseDriver.java:220-264`). Remove or gate `finishPass` when switching sources.
5. BC pose switch is global, not conditional. `BcPoseMixin` suppresses the BC pose for any zanpakuto in the main hand whatever the state (`BcAttackGate.poseSuppressed` returns `holdsZanpakuto`). Pseudo mixin into BC internals (`PlayerAttackHelper.poseForPlayer`, remap=false, signature `(PlayerEntity)Pose`; `Pose(String mainHand, String offHand)` record): a BC update that renames it silently turns the BC katana stance back on (Pseudo mixins fail soft). Consider a dev-time assertion.
6. Blocking is tick state, TP weight is frame state. `ZanpakutoItem.isBlocking` is true only after the server round trip for remote players (`blockingOf` comments, :92-99); the TP pose follows it, so other players see the block one ping late; this is inherent and the same would apply to a playerAnimator animation started from the same predicate.
7. FP block is not reach-clamped. `FpClamp` / `FpCentre` are only applied to ability gestures (`limitGesture`, `FpMotion.java:430-476`). The r8 comments note the block pose was hand-tuned (y 0.22 -> 0.10) to clear the central keep-out rectangle (`TwoHandMotion.java:245-247`). A data-driven pose from an artist could block the middle of the screen. Run `FpClamp.margin` / `FpCentre.right` over the data pose in a unit test.
8. Left-handers: FP mirrors via `FpPose.mirrored()`, TP mirrors in `TpPose.solve` (:342-350). Data authored right-handed must stay right-hander layout or the mirror breaks.
9. SEALED (sheathed) block is a distinct third pose set (FP `sealedGuard`, TP via `saya` blend). It cannot be a plain arm animation because the scabbard and sword move as one body and the left hand follows the scabbard.
10. Server/other-client consistency. Other players' block pose is computed locally on each client from the tracked using-item flag and the same data; if per-sword data files differ between client and server resource packs nothing breaks (server has no pose), but different clients can see different poses.
11. BC strike during block is gated off server-side (`BcCombatGate`), so a block animation never has to fight a BC strike layer on the same player. If the "no attack while blocking" rule (`BlockRules.allowsAttack`) is ever relaxed, the BC layer (2000) would sit above the new layer and `BcItemPose` cross-fade (`1 - w`) would start to matter.
12. Tests. Any change to `TpPose`, `GuardMotion`, `TwoHandMotion` constants will break `TpPoseTest`, `SealedGuardTest`, `TwoHandMotionTest`, `FpMotionTest` (they pin numbers/geometry); update them with the data file instead of deleting them.

---

## Appendix: file index

Core (pure): `main/java/dev/minebleach/reiatsutest/core/combat/{GuardMotion,TwoHandMotion,TpPose,TpMat4,FpClamp,FpParams,FpRig,FpPose,FpEase,FpGeometry,FpCentre,BlockRules,BcAttackGate,CombatConfig}.java`
Server/mixins (main): `registry/ZanpakutoItem.java`, `server/{BlockParry,BcCombatGate}.java`, `mixin/{BcPoseMixin,LivingEntityMixin}.java`, `resources/reiatsu_test.mixins.json`
Client: `client/model/{TpPoseDriver,TpPoseLayer,TpCastLayer,BcItemPose,FpMotion,FirstPersonHand,HeldPose,ScabbardRenderer,DrawTracker,HandMath}.java`, `client/mixin/{HeldItemRendererMixin,PlayerEntityModelMixin,BcFirstPersonModeMixin}.java`, `resources/reiatsu_test.client.mixins.json`, `client/ReiatsuTestClient.java:31-32`
Data: `main/resources/data/reiatsu_test/weapon_attributes/{sode_no_shirayuki,senbonzakura}.json` (parent only), `main/resources/assets/reiatsu_test/zanpakuto/{sode_no_shirayuki,senbonzakura}.json` (`stow`, `held`, `hip`), `main/resources/assets/reiatsu_test/player_animations/senbon_slash_rl.json`, dev only `D:\MineBleach\mod\run\pose_override.json`
Design notes that describe these: `D:\MineBleach\design\results\{fp_combat,fp_two_handed,pose_round3,fp_round5,fp_swing_round6}.md`
