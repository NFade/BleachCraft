#!/usr/bin/env python3
"""Convert a Blockbench .bbmodel animation to a playerAnimator ("emote") JSON.

No dependencies. Usage:
  python tools/bbmodel_to_playeranim.py design/anim/player_game_rig.bbmodel --animation senbon_slash_rl \
      --out mod/src/main/resources/assets/reiatsu_test/player_animations/senbon_slash_rl.json --end-tick 16

Rig contract: the FLAT game rig (groups head, torso, rightArm, leftArm, rightLeg, leftLeg, all children of the
root, because playerAnimator does not parent arms to the torso), made like the vanilla player model:
  torso/head pivot (0,24,0), arms (+-5,22,0), legs (+-1.9,12,0) in Blockbench space
  (model faces -Z, own right is +X, Y up, degrees).
playerAnimator uses vanilla ModelPart values (radians, Y down, model-right = -X), which is Blockbench turned 180
degrees about Z. Rotations keep the ZYX Euler order, so:
      pitch = -rx    yaw = -ry    roll = +rz
Position keys are offsets from the group origin in Blockbench space, playerAnimator wants the part position:
      x = rest_x - dx    y = rest_y - dy    z = rest_z + dz        (rest = vanilla pivot, game axes)
Group `rightItem` (no cubes) carries the held item straight in GAME item-frame values, nothing is flipped:
rotation degrees (x=pitch, y=yaw, z=roll) and position px (the grip-keeping offset). Groups `sword_prop` / `bow_prop` are preview aids and ignored.

HEAD_AXES: only the head pitch is written. The head is not a child of the torso in game, and any keyed head axis
replaces the vanilla value that follows the camera, so yaw and roll stay unkeyed (others keep seeing where you look).

Anything else with keyframes (scale, unmapped groups) is an error, nothing is dropped silently. Elbow/knee bends
need Bendy Lib and are not exported.
"""
import argparse
import json
import math
import sys

TICKS_PER_SECOND = 20

ROTATING = ("head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg")
IGNORED = {"sword_prop", "bow_prop"}   # preview props (the Uryu bow prop of design/anim/uryu_poses.bbmodel)
# vanilla part pivots in game axes
REST_GAME = {
    "head": (0.0, 0.0, 0.0),
    "torso": (0.0, 0.0, 0.0),
    "rightArm": (-5.0, 2.0, 0.0),
    "leftArm": (5.0, 2.0, 0.0),
    "rightLeg": (-1.9, 12.0, 0.0),
    "leftLeg": (1.9, 12.0, 0.0),
}

# playerAnimator easing names accepted in a keyframe's "easing" field (case-insensitive, EASE prefix not needed).
EASE_NAMES = {"LINEAR", "CONSTANT", "INSINE", "OUTSINE", "INOUTSINE", "INQUAD", "OUTQUAD", "INOUTQUAD", "INCUBIC",
              "OUTCUBIC", "INOUTCUBIC", "INQUART", "OUTQUART", "INOUTQUART", "INQUINT", "OUTQUINT", "INOUTQUINT",
              "INEXPO", "OUTEXPO", "INOUTEXPO", "INCIRC", "OUTCIRC", "INOUTCIRC", "INBACK", "OUTBACK", "INOUTBACK",
              "CATMULLROM", "STEP"}

# Blockbench interpolation of the keyframe = easing of the segment that ends there (used when no "easing" is stored).
EASING = {
    "linear": "LINEAR",
    "catmullrom": "INOUTSINE",
    "bezier": "INOUTSINE",
    "step": "CONSTANT",
}


class ConvertError(Exception):
    pass


def bb_rotation_to_pa(rx, ry, rz):
    """Blockbench Euler degrees -> playerAnimator (pitch, yaw, roll) radians."""
    return (-math.radians(rx), -math.radians(ry), math.radians(rz))


def bb_offset_to_pa(part, dx, dy, dz):
    """Blockbench offset from the group origin -> absolute playerAnimator part position."""
    rx, ry, rz = REST_GAME[part]
    return (rx - dx, ry - dy, rz + dz)


def _number(value, where):
    try:
        return float(value)
    except (TypeError, ValueError):
        raise ConvertError("%s: non-numeric keyframe value %r (molang is not supported)" % (where, value))


def _clean(v):
    v = round(v, 6)
    return 0.0 if v == 0 else v


def convert(bbmodel, animation_name, end_tick=None, stop_tick=None, author="MineBleach", description=""):
    anims = [a for a in bbmodel.get("animations", []) if a.get("name") == animation_name]
    if not anims:
        names = [a.get("name") for a in bbmodel.get("animations", [])]
        raise ConvertError("animation %r not found, have %s" % (animation_name, names))
    anim = anims[0]

    entries = []  # (tick, part, order, axis, value, easing)
    last_tick = 0
    for animator in anim.get("animators", {}).values():
        keyframes = animator.get("keyframes") or []
        if not keyframes:
            continue
        name = animator.get("name", "")
        if name in IGNORED:
            continue
        if name not in ROTATING and name != "rightItem":
            raise ConvertError("group %r has keyframes but is not mapped (mapped: %s, rightItem)" % (name, list(ROTATING)))
        for kf in keyframes:
            channel = kf.get("channel")
            if channel not in ("rotation", "position"):
                raise ConvertError("%s: channel %r is not supported (rotation and position only)" % (name, channel))
            where = "%s@%s" % (name, kf.get("time"))
            tick = int(round(_number(kf["time"], where) * TICKS_PER_SECOND))
            points = kf.get("data_points") or []
            if len(points) != 1:
                raise ConvertError("%s: expected exactly one data point" % where)
            p = points[0]
            x, y, z = (_number(p.get(a, 0), where) for a in "xyz")
            interp = kf.get("interpolation", "linear")
            if kf.get("easing"):
                easing = kf["easing"].upper()
                if easing not in EASE_NAMES:
                    raise ConvertError("%s: unknown easing %r" % (where, kf["easing"]))
            elif interp in EASING:
                easing = EASING[interp]
            else:
                raise ConvertError("%s: unknown interpolation %r" % (where, interp))
            last_tick = max(last_tick, tick)
            if channel == "rotation":
                if name == "rightItem":
                    vals = (math.radians(x), math.radians(y), math.radians(z))
                else:
                    vals = bb_rotation_to_pa(x, y, z)
                axes = ("pitch", "yaw", "roll")
            else:
                # rightItem position is already in game item-frame px, the parts are Blockbench offsets
                vals = (x, y, z) if name == "rightItem" else bb_offset_to_pa(name, x, y, z)
                axes = ("x", "y", "z")
            for order, (axis, val) in enumerate(zip(axes, vals)):
                if name == "head" and axis != "pitch":
                    continue          # a keyed head yaw/roll would replace the camera look, see HEAD_AXES
                entries.append((tick, name, order, axis, _clean(val), easing))

    if not entries:
        raise ConvertError("animation %r has no keyframes" % animation_name)

    if end_tick is None:
        end_tick = last_tick
    if stop_tick is None:
        # the reference files keep stopTick 10 ticks after endTick (the fade-out window)
        stop_tick = max(end_tick + 10, int(round(float(anim.get("length", 0)) * TICKS_PER_SECOND)))
    if end_tick <= 0:
        raise ConvertError("endTick must be bigger than 0")
    if stop_tick < end_tick:
        raise ConvertError("stopTick %d is before endTick %d" % (stop_tick, end_tick))

    entries.sort(key=lambda e: (e[0], e[1], e[2]))
    moves = []
    for tick, part, _order, axis, val, easing in entries:
        moves.append({"tick": tick, "easing": easing, "turn": 0, part: {axis: val}})

    return {
        "name": animation_name,
        "author": author,
        "description": description or ("Generated from Blockbench animation " + animation_name),
        "emote": {
            "isLoop": "false",
            "returnTick": 0,
            "beginTick": 0,
            "endTick": end_tick,
            "stopTick": stop_tick,
            "degrees": False,
            "easeBeforeKeyframe": True,
            "moves": moves,
        },
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("bbmodel")
    ap.add_argument("--animation", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--end-tick", type=int)
    ap.add_argument("--stop-tick", type=int)
    ap.add_argument("--description", default="")
    args = ap.parse_args(argv)
    with open(args.bbmodel, encoding="utf-8") as f:
        model = json.load(f)
    try:
        out = convert(model, args.animation, args.end_tick, args.stop_tick, description=args.description)
    except ConvertError as e:
        print("error: %s" % e, file=sys.stderr)
        return 1
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, indent=2)
        f.write("\n")
    print("wrote %s (%d moves, endTick %d, stopTick %d)" % (
        args.out, len(out["emote"]["moves"]), out["emote"]["endTick"], out["emote"]["stopTick"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
