# R12: Uryu bow, first person archer view

Right handed player (main arm RIGHT, empty off hand, `UryuBowPose.bowInLeftHand`) with the drawn Heilig Bogen (`UryuCrossItem.isBow`).
Left handed players, a filled off hand and the sealed pendant keep the old path (plain display transform), exactly as before.

## What was wrong
The display transform `firstperson_righthand` shows the bow edge-on (arrow axis along the view direction): a pale hairline at the right edge,
no readable arrow, no string hand. Vanilla's UseAction.BOW branch of `renderFirstPersonItem` additionally moves the item toward the camera.

## What changed
- `HeldItemRendererBowMixin` (new): at the head of `HeldItemRenderer#renderFirstPersonItem`, only for this case, cancels the vanilla pass and
  calls `FpBow.render` (equip lift kept, swing not kept).
- `FpBow` (new, client/model): draws the left arm on the grip, the right arm on the nock point of the model, then the bow itself via
  `HeldItemRenderer#renderItem` (FIRST_PERSON_RIGHT_HAND). The right display transform is divided out of the matrix first, so the pose is the
  complete model placement and `uryu_cross_display.json` is NOT changed (no effect on the pendant or left handers).
- `BowFpPose` (new, core/uryu): all numbers, named defaults, dev override. `BowHandMotion` (new, core/uryu): clock of the draw hand
  (comes to the string, follows the draw, holds, snaps forward on the release, relaxes). `BowDraw`: `NOCK_START/NOCK_TRAVEL/STRING_Z`,
  `nockBack`, `nockZ` (the numbers that were private in `ObjItemBakedModel`; the model now uses them, behaviour identical).
- Guards: `FirstPersonHand.render` and `FpMotion.push` do nothing while `FpBow.active()` (the generic zanpakuto arm and sword motion).
  `FpMotion.params()` exposes the override reader.
- Tests: `BowFpPoseTest` (5), `BowHandMotionTest` (6). Full build green, 1359 tests, 0 failures.

## How it looks (design)
Frame = camera frame of the hand pass (x right, y up, -z forward, blocks), hand origin of the vanilla hold.
- Bow: grip (model centre) at (-0.42, -0.42, -1.30), scale 1.5 blocks per model unit (bow 1.65 long), yaw 80 (arrow tip turned left, almost
  the side view so the arc, string and arrow read), pitch 4, roll 0. The lower limb leaves the bottom of the screen, the upper limb stands in the
  left third.
- Left fist on the grip (offset z -0.10, so the bow is drawn in front of the fist).
- Right fist: at rest the place of a vanilla empty hand (lower right); while drawing it is lerped (ready.in 0.10 s) to the nock point
  `(0.5, 0.5, BowDraw.nockZ(pull))` of the model, plus offset (0.03, 0, -0.10) so the arrow is drawn in front of the fingers, forearm turned 20 degrees about the fist.
  Pull = `BowDraw.powerAt(useTime + tickDelta)`, the same curve as the string and arrow in `emitBowDraw`. Release: snap forward 0.09 s (ease out), then relax 0.20 s.
- The arrow is part of the model (`uryu_arrow`, 0.08 thick, 0.5 long, 1.5x scale here) and is emitted only while the bow is drawn; it was
  not hidden or changed. Idle bow: no arrow (unchanged).

Numbers were chosen with an offline wireframe projection of the real OBJ meshes and the vanilla arm matrix chain (calibrated against the
user's screenshot 4: bow tip and base landed within 0.02 NDC), NOT in the game.

## Tuning without a rebuild (dev only, `pose_override.json`, hot reload like the other `fp_*` sections)
```json
"fp_bow": {
  "enabled": 1,
  "grip": [-0.42, -0.42, -1.30],
  "scale": 1.5,
  "rot": [80, 4, 0],
  "left":  { "off": [0, 0, -0.10], "rot": [0, 0, 0] },
  "right": { "off": [0.03, 0, -0.10], "rot": [0, 0, 20] },
  "ready": { "in": 0.10, "out": 0.20 },
  "snap": 0.09
}
```
- `rot` = [yaw, pitch, roll] degrees: yaw turns the arrow tip to the LEFT (0 = away from the camera, edge-on; 90 = across the screen), pitch raises the tip, roll turns the bow counter-clockwise.
- `grip` moves the whole bow (hands follow). `scale` sizes it. Bigger grip y = higher on screen; more negative z = farther and smaller.
- `left/right.off` shift the fist from the grip / nock (camera frame). Negative z = behind the bow/arrow. `left/right.rot` = Euler X,Y,Z turn of the
  forearm about the fist: X about 55 flattens an arm that points at the camera into the screen plane, Z fans it to the side.
- `enabled: 0` = old vanilla path (for an A/B screenshot).

## UNVERIFIED in game (nothing was run)
- The mixin target `renderFirstPersonItem` (private, yarn) is checked by compile/javap only; the mixin apply happens at game start.
- Real fov / aspect of the user's window differ from the mock (fov 70, 16:9): the grip x/y and scale probably need one tuning round.
- The vanilla arm box is large (0.25 x 0.75 blocks); the left fist may hide part of the arrow tip, tune `left.off` / yaw.
- Slim skins: the fist constants are those of the classic arm (same as `FpMotion.renderLeftGuard`), error about 0.5 px.
- Swing (left click) is not shown while this pose is active; the equip lift is.
- Better Combat's own first person animation mode (`fp_bc_mode`) was not exercised with this item.
