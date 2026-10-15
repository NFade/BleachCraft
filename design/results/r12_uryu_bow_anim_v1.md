# R12 Uryu bow draw animation v1 (`uryu_bow_draw`)

Files: `design/anim/player_game_rig_bow_v1.bbmodel` (new; `senbon_slash_rl` and `block_poses` have the same keyframes as
the v0 backup, the original `D:/blockbench-mcp/plugin/player_game_rig_bow.bbmodel` and `..._v0_sword_placeholder.bbmodel` were not touched,
md5 checked), converter output `design/anim/out/uryu_bow_draw.json` (not in mod/src).

## Side mapping (user correction applied)

Right-handed default: BOW in the LEFT hand, STRING drawn with the RIGHT hand.
`leftArm` = bow arm, straight at the target (-Z). `rightArm` = string arm, straight back along the same line.
`sword_prop` (preview stand-in for the bow, vertical, long axis up) is keyed into the LEFT hand grip.
Torso yaw is negative in Blockbench (ry -72..-77) so the LEFT shoulder points at the target. `rightItem` is NOT keyed (see open items).

## Timeline (20 tps, 40 ticks, loop once, last tick = first tick on every channel)

| time / tick | what | easing of the segment ending there |
|---|---|---|
| 0.00 / 0 | ready pose (all zero) | - |
| 0.00-0.20 / 0-4 | legs first: left leg forward +16, right leg back -14, hips follow the turn | INOUTSINE |
| 0.05-0.25 / 1-5 | torso yaw to -72 (starts 1 tick after the legs) | INOUTSINE |
| 0.10-0.25 / 2-5 | arms start 2 ticks after the legs. bow arm up to rx 90 (pitch -90 in game), string arm up and out (rx 90, ry -25) = nock pose | INOUTSINE |
| 0.25-1.05 / 5-21 | DRAW. string arm sweeps in the horizontal plane out and round to pointing straight back (ry -25 -> -194.7), bow arm holds, torso -72 -> -76, legs lean in (+18/-16), head nod to -4 (game pitch +4 deg) | INOUTSINE |
| 1.05-1.30 / 21-26 | HOLD, almost still: torso -76 -> -77, string arm +1.5 deg creep | INOUTSINE |
| 1.30-1.40 / 26-28 | RELEASE: string arm snaps back and outward (rx 100, ry -160), bow arm recoils (rx 94, shoulder 0.8 px back), torso unwinds to -72, head -2 | OUTQUAD |
| 1.40-2.00 / 28-40 | relax to ready (legs hold until 1.5 then follow), tick 40 equals tick 0 | INOUTSINE |

**Hold / freeze for code: tick 21 (1.05 s) is full draw; the pose is stable until tick 26 (1.30 s).** Freeze at tick 21.
Converter call used: `--end-tick 40 --stop-tick 50` (the script default would give stopTick 50 for a 2.0 s clip anyway; endTick 40 = last key).

Key counts: 75 keys total (torso 7, head 6, arms 14 each, legs 10 each, sword_prop 14). Key times: 0, .05, .1, .2, .25, 1.05, 1.3, 1.4, 1.5, 2.0.
Head: only pitch is keyed (rotation x), the converter writes only head pitch.

## Numbers at full draw (t = 1.05, Blockbench axes, px; FK checked against Blockbench's live meshes, they match to 0.01)

- Bow hand (bottom of leftArm): (-2.29, 22.0, -14.84), arm direction -Z within 0.5 deg: points at the aim line.
- String hand (bottom of rightArm): (-2.29, 22.0, +14.27): same x and y as the bow hand, so on the aim line.
- Both arms are 10.05 px from shoulder pivot to hand end at all times (reach is constant, never stretched).
- Shoulders follow the torso turn through the arm position keys (left (-1.21, 22, -4.85), right (+1.21, 22, +4.85)), so the arms stay attached to the turned torso.
- Hand-to-hand distance: **29.1 px (1.8 blocks), NOT the requested ~12 px**. With straight arms and a side-on torso this is geometry, not a choice:
  shoulders are ~9.7 px apart along the aim line and each arm adds 10 px. 12 px would put the string hand at z about -2.8, inside the head and
  torso volume (the head is camera driven and cannot be turned), and the string arm would have to be shorter than its own length. Real draw length
  (about 12 px) needs the elbow bend (Bendy Lib), which is out of scope. Consequence for code: the string drawn by the bow item model moves only ~1.6 px
  (uryu_bow_string.obj), so the right hand visibly ends far behind the string. Possible fix in code (not done): attach the nocked arrow/string visual to the
  right hand while drawing, or accept the wide pose.

## Analyzer / preview

`analyze_animation`: 1 issue, `stiff_limbs` (expected, straight limbs are intended). An `all_linear` warning appeared once; fixed by storing the keys as catmullrom
(linear only on the release keys). The Blockbench preview therefore shows catmullrom curves, the exported JSON uses the stored `easing` field (INOUTSINE / OUTQUAD per key).
`preview_animation` at 0, 0.25, 0.65, 1.05, 1.15, 1.4 s in front_right and left: side-on archer, bow arm level at shoulder height, string arm sweeping back, no
visible clipping of arms into the head; during the nock pose (0.25 s) the string arm points forward-out and overlaps the torso edge a little.

## Converter result

`python -I tools/bbmodel_to_playeranim.py design/anim/player_game_rig_bow_v1.bbmodel --animation uryu_bow_draw --out design/anim/out/uryu_bow_draw.json --end-tick 40 --stop-tick 50`
-> wrote 171 moves, endTick 40, stopTick 50, no refusal. `tools/test_bbmodel_to_playeranim.py`: 33 tests OK (4 skipped). Easing field is not written by
Blockbench itself, so it was injected into the .bbmodel by a small python step after saving (same mechanism as tools/bbmodel_inject.py uses); do not re-save the file
from Blockbench without redoing this: the easing fields would be dropped and the converter would map catmullrom to INOUTSINE and the release keys (linear) to LINEAR, so the OUTQUAD release would be lost.

## Also changed in the rig (v1 only)

`leftArm_box` and `rightArm_box` cubes had leftover baked rotations (left arm was rotated -87 deg, right -175) from earlier poses. They were reset to clean vertical
vanilla-style boxes (x 4..8 / -8..-4, y 12..24, z -2..2, rotation 0). Groups and pivots unchanged.

## NOT verified in game

- Item/bow orientation in the hand: `rightItem` is not keyed because the item frame convention is unverified for the bow and the bow is held in the LEFT hand
  (UryuBowPose is for the right hand only, so it needs a decision/new left-hand correction in code). The sword_prop in the preview is only a stand-in.
- Real playerAnimator behaviour of the head: a keyed head pitch replaces the camera pitch for the whole clip (keys exist from tick 0 to 40), so looking up/down
  is overridden while it plays. Drop the head track if that feels wrong.
- Arm clipping with the torso/head on the real skin and with armor layers; leg lean may lift feet about 0.5 px off the floor.
- Timing feel (0.8 s draw, 0.25 s hold, 0.1 s release) and the first-person view (not covered by this clip).

## v2: string arm slides (user feedback on v1)

File `design/anim/player_game_rig_bow_v2.bbmodel` (v1 kept as `player_game_rig_bow_v1.bbmodel` and `player_game_rig_bow_v1_swing.bbmodel`, converter output of v1 as
`design/anim/out/uryu_bow_draw_v1_swing.json`). Only the rightArm keys changed; bow arm, torso, legs, head, timing (stance 5 ticks, draw 5-21, hold 21-26,
release 26-28 OUTQUAD, relax to 40, freeze at tick 21) are the same as v1.

- Orientation of the string arm is constant: rx 90, ry 0 (nock) -> 1 -> 1.5 (small settle). The pull is the position key only, a pure Z translation along the aim line:
  hand tip z -12 (nock, 0.25 s) -> -3 (full draw, 1.05 s) -> -2.7 (hold end) -> +0.5 (release extra slide, OUTQUAD) -> rest at 2.0 s.
- Lateral offset: +4.75 px to the right of the shoulder follow position (constant), so the arm travels along the right flank at x = 4.7..8.7 (centre 6.7).
- Full draw numbers: string hand tip (6.78, 22, -3.0), bow hand tip (-2.29, 22, -14.84): **11.8 px apart along the aim line** (was 29.1). The string hand is at the right cheek,
  1 px behind the face plane z = -4, and 9.1 px to the right of the aim line (it cannot be on the line: any pose on the line passes through the torso or head).
- Clip check by SAT between the arm cube (12 x 4 x 4, FK) and the head box and the rotated torso box, sampled every 1/8 between the keys 0.25 to 1.4 s: smallest gap
  0.61 px, no intersection (needs the 4.75 px lateral shift; the arm floats about 1.8 px away from the torso flank, visible as a slightly raised arm).
  The rise from rest to nock (0.10-0.25 s) was not SAT-checked, only seen in previews.
- Analyzer: only stiff_limbs. Converter: 171 moves, endTick 40, stopTick 50, 33 tests OK. Easing re-injected after save (INOUTSINE, OUTQUAD at 1.4 s).
- rightArm in the JSON at tick 5: pitch -1.5708, x -6.295, z -2.0; at tick 21: pitch -1.5708, yaw -0.0175, x -5.96, z 7.016.
