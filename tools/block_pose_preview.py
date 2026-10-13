#!/usr/bin/env python3
"""Static two-handed block poses on the flat rig, using slash_solver's geometry, as an animation named
`block_poses` (one pose per second) so they can be looked at in Blockbench.

Poses come from design/results/block_pose_refs.md (hand points are absolute, Blockbench space):
  A  hasso guard      R (1.9, 24.1, -8.1)  blade az -8,  el 76, torso yaw -7.6
  B  raised chudan    R (1.8, 20.3, -8.2)  blade az -20, el 55, torso yaw -7.2

  python tools/block_pose_preview.py    -> design/anim/block_poses_keyframes.json
  python tools/bbmodel_inject.py design/anim/player_game_rig.bbmodel design/anim/block_poses_keyframes.json --animation block_poses --length 1.0
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import slash_solver as s  # noqa: E402

POSES = [
    ("A_hasso", -7.6, (1.9, 24.1, -8.1), (-8.0, 76.0)),
    ("B_chudan", -7.2, (1.8, 20.3, -8.2), (-20.0, 55.0)),
]


def pose(yaw, hand_world, blade_az_el):
    """Same solve() as the slash, with the hand given in world space (undo the torso turn first)."""
    R = s.rot((0.0, yaw, 0.0))
    local = s.add(s.TORSO_PIVOT, s.mv(s.mt(R), s.sub(hand_world, s.TORSO_PIVOT)))
    saved = s.sample_controls
    s.sample_controls = lambda tick: (yaw, 0.0, local, blade_az_el)
    try:
        return s.solve(0)
    finally:
        s.sample_controls = saved


def main():
    kfs = []
    for i, (name, yaw, hand, blade) in enumerate(POSES):
        r = pose(yaw, hand, blade)
        t = float(i)
        print("%s: yaw %.1f | R arm %s | L arm %s | shift R %.2f L %.2f | item %s" % (
            name, yaw, tuple(round(v, 1) for v in r["rightArm"]["rot"]),
            tuple(round(v, 1) for v in r["leftArm"]["rot"]), r["shift_r"], r["shift_l"],
            tuple(round(v, 1) for v in r["rightItem"]["rot"])))
        for bone, ch, v in (("torso", "rotation", r["torso"]),
                            ("rightArm", "rotation", r["rightArm"]["rot"]), ("rightArm", "position", r["rightArm"]["pos"]),
                            ("leftArm", "rotation", r["leftArm"]["rot"]), ("leftArm", "position", r["leftArm"]["pos"]),
                            ("rightItem", "rotation", r["rightItem"]["rot"]), ("rightItem", "position", r["rightItem"]["pos"]),
                            ("sword_prop", "rotation", r["prop"]["rot"]), ("sword_prop", "position", r["prop"]["pos"])):
            kfs.append({"bone": bone, "channel": ch, "time": t, "value": [round(x, 3) for x in v], "interpolation": "step"})
    out = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design", "anim",
                                        "block_poses_keyframes.json"))
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(kfs, f)
    print("wrote", out)


if __name__ == "__main__":
    sys.exit(main())
