#!/usr/bin/env python3
"""Write a keyframe list (slash_solver.py output) into an animation of a .bbmodel, replacing its keys.

  python tools/bbmodel_inject.py design/anim/player_game_rig.bbmodel design/anim/slash_rl_keyframes.json \
      --animation senbon_slash_rl [--length 0.8]

The keyframes are [{bone, channel, time, value:[x,y,z], interpolation}]. Bones are matched to groups by name.
Every other part of the .bbmodel is left alone. Open the result in Blockbench (MCP load_project) to look at it.
"""
import argparse
import json
import sys
import uuid


def inject(model, keyframes, animation, length=None):
    anims = [a for a in model.get("animations", []) if a.get("name") == animation]
    if not anims:
        raise SystemExit("animation %r not found in the model" % animation)
    anim = anims[0]
    groups = {g["name"]: g["uuid"] for g in model.get("groups", []) if isinstance(g, dict) and "name" in g}
    animators = {}
    for kf in keyframes:
        bone = kf["bone"]
        if bone not in groups:
            raise SystemExit("bone %r has no group in the model (have %s)" % (bone, sorted(groups)))
        gid = groups[bone]
        an = animators.setdefault(gid, {"name": bone, "type": "bone", "rotation_global": False,
                                        "quaternion_interpolation": False, "keyframes": []})
        x, y, z = kf["value"]
        rec = {
            "channel": kf["channel"],
            "data_points": [{"x": repr(float(x)), "y": repr(float(y)), "z": repr(float(z))}],
            "uuid": str(uuid.uuid4()),
            "time": kf["time"],
            "color": -1,
            "interpolation": kf.get("interpolation", "linear"),
        }
        if kf.get("easing"):
            rec["easing"] = kf["easing"]      # playerAnimator easing name, read by bbmodel_to_playeranim.py
        an["keyframes"].append(rec)
    # keep empty animators for the other groups so Blockbench lists the bones
    for name, gid in groups.items():
        animators.setdefault(gid, {"name": name, "type": "bone", "rotation_global": False,
                                   "quaternion_interpolation": False})
    anim["animators"] = animators
    if length is not None:
        anim["length"] = length
    return model


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("bbmodel")
    ap.add_argument("keyframes")
    ap.add_argument("--animation", required=True)
    ap.add_argument("--length", type=float)
    ap.add_argument("--out", help="default: overwrite the input")
    args = ap.parse_args(argv)
    with open(args.bbmodel, encoding="utf-8") as f:
        model = json.load(f)
    with open(args.keyframes, encoding="utf-8") as f:
        kfs = json.load(f)
    inject(model, kfs, args.animation, args.length)
    with open(args.out or args.bbmodel, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f)
    print("injected %d keyframes into %r" % (len(kfs), args.animation))
    return 0


if __name__ == "__main__":
    sys.exit(main())
