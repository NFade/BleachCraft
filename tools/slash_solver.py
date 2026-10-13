#!/usr/bin/env python3
"""Two-handed sword slash for the flat vanilla player rig (parts are NOT parented in game).

Why this exists: in playerAnimator head/torso/arms/legs are independent parts, so a torso twist does not
carry the arms. The arms must therefore be keyed in the entity frame: the shoulder pivots follow the torso
by position keys, both straight arms must reach one grip, and the sword is steered with the rightItem
channel (read from playerAnimator HeldItemMixin: translate in 1/16 units, then rotation roll, yaw, pitch,
all in the vanilla ITEM frame).

Space: Blockbench space (right = +X, up = +Y, front = -Z, degrees), origin on the floor between the feet.
Rest pivots: torso (0,24,0), right shoulder (5,22,0), left shoulder (-5,22,0).

Item facts (assets/reiatsu_test/models/item/senbonzakura_display.json, models/obj/*_meta.json):
  - the model origin is the grip (grip_hand), +Y is the blade axis;
  - thirdperson_righthand display: rotation [45,180,0], translation [0,-2,1.75] px, scale 1.6.
  JOML rotationXYZ(45,180,0) takes the blade axis (0,1,0) to (0, 0.707, 0.707) in the item frame; vanilla's item
  frame is the arm frame turned by Rx(-90) Ry(180), so the DEFAULT blade stands 45 degrees up from "forward" with
  the arm hanging (tilt towards the shoulder = ITEM_TILT 45). UNVERIFIED in game.
  - rotating the item about the frame origin would carry the grip (at the display translation) away from the
    hand, so rightItem also gets a position key that keeps the grip fixed: t = G - R G.
rightItem keys are stored in GAME item-frame values (degrees pitch/yaw/roll as x/y/z, px for position).
Euler order is ZYX, the same as vanilla ModelPart and Blockbench.

Usage: python tools/slash_solver.py  -> prints the per-tick table, writes design/anim/slash_rl_keyframes.json
"""
import json
import math
import os
import sys

TORSO_PIVOT = (0.0, 24.0, 0.0)
SHOULDER = {"rightArm": (5.0, 22.0, 0.0), "leftArm": (-5.0, 22.0, 0.0)}
ARM_REACH = 9.5        # shoulder pivot -> grip point along a straight arm
HAND_GAP = 3.0         # right hand to left hand along the hilt
ITEM_TILT = 45.0       # default blade tilt towards the shoulder, degrees (derived from the display transform)
GRIP_ITEM = (0.0, -2.0, 1.75)   # grip in the item frame, px (display translation)
MAX_SHIFT = 3.5        # px a pivot may leave the shoulder (reported)

# Timeline (written for 16 ticks, stretched to 20 by TIME_SCALE; 20 ticks per second), following design/results/anim_rules.md (Better Combat's own split):
#   0-4 wind-up | 4-9 strike, accelerating | 9-10 contact, one tick of near-freeze (hit pause) | 10-13 follow-through,
#   overshoot and settle | 13-16 recovery back to the READY pose (the last tick equals the first, so it blends with the
#   idle stance and the next combo hit). Better Combat wants its tipping point at 60 percent = tick 9.6, so
#   weapon_attributes `upswing` is 0.6.
# Per-channel knots (tick, value) with their own times, so the body can lead and the blade can settle last
# (overlapping action: torso arrives 1 tick before the hands, the blade lags them by half a tick to a tick).
# Values: torso yaw>0 turns the chest to the model's left (Blockbench +Y); torso lean>0 tips it backward (+X);
# hand = right hand grip point in the chest frame; blade = (azimuth to the left of straight ahead, elevation) in the
# WORLD; edge = roll of the cutting edge about the blade, 0 = edge horizontal pointing left, -90 = edge down;
# legs = rotation about the hip, + swings the leg forward.
TIME_SCALE = 1.25      # the knots below are written for 16 ticks; Better Combat advises 20-tick attacks
CONTACT_TICK = 12      # 60 percent of 20: Better Combat's tipping point, also weapon_attributes upswing 0.6
END_TICK = 20
# Fraction of the strike arc covered per tick over the 5 strike ticks (cumulative): slow start, fastest between the
# 3rd and the 5th tick, the peak speed sits at 60-70 percent of the arc (times are stretched by TIME_SCALE below).
STRIKE_FRACS = [0.0, 0.03, 0.11, 0.27, 0.58, 1.0]


def lerp(a, b, f):
    if isinstance(a, (int, float)):
        return a + (b - a) * f
    return tuple(lerp(x, y, f) for x, y in zip(a, b))


def strike(t0, a, b):
    """Knots over the 5 strike ticks from value a to value b with the STRIKE_FRACS spacing, starting at tick t0."""
    return [(t0 + i, lerp(a, b, f)) for i, f in enumerate(STRIKE_FRACS)]


READY_HAND = (1.0, 18.0, -6.5)       # the mod's own idle hold: upright sword in front of the chest
READY_BLADE = (0.0, 76.0)
READY_EDGE = -25.0
WIND_HAND = (6.0, 24.0, -9.0)
HIT_HAND = (1.0, 19.0, -9.0)

CHANNELS = {
    "hand":  [(0, READY_HAND), (4, WIND_HAND)] + strike(4, WIND_HAND, HIT_HAND)[1:]
             + [(10, (0.6, 18.7, -9.0)), (12, (-3.0, 16.0, -7.5)), (16, READY_HAND)],
    # the blade lags the hands by half a tick, overshoots about 10 degrees after the contact and settles
    "blade": [(0, READY_BLADE), (4.5, (-100.0, 55.0))] + strike(4.5, (-100.0, 55.0), (28.0, 6.0))[1:]
             + [(10.5, (40.0, 4.0)), (11.5, (68.0, -2.0)), (12.5, (80.0, -6.0)), (16, READY_BLADE)],
    "edge":  [(0, READY_EDGE), (4.5, -40.0)] + strike(4.5, -40.0, -15.0)[1:] + [(10.5, -14.0), (13, -10.0), (16, READY_EDGE)],
}
CHANNELS = {name: [(t * TIME_SCALE, v) for t, v in knots] for name, knots in CHANNELS.items()}
BLADE_LEN = 20.0       # px, only used to find the tip path for the edge check


# ------------------------------------------------------------------ eased sparse tracks (the body, not IK-bound)
# playerAnimator easing, applied to the segment that ENDS at the key (easeBeforeKeyframe = true, verified with
# tools/pa_sample/PaSample.java against the real playerAnimator classes). Better Combat's own rule: wind-up in-out,
# strike ease-in, after the hit ease-out, recovery in-out.
def _inout_pow(u, n):
    return 0.5 * (2 * u) ** n if u < 0.5 else 1 - 0.5 * (2 * (1 - u)) ** n


EASE = {
    "LINEAR": lambda u: u,
    "INSINE": lambda u: 1 - math.cos(u * math.pi / 2),
    "OUTSINE": lambda u: math.sin(u * math.pi / 2),
    "INOUTSINE": lambda u: -(math.cos(math.pi * u) - 1) / 2,
    "INQUAD": lambda u: u * u,
    "OUTQUAD": lambda u: 1 - (1 - u) ** 2,
    "INOUTQUAD": lambda u: _inout_pow(u, 2),
    "INCUBIC": lambda u: u ** 3,
    "OUTCUBIC": lambda u: 1 - (1 - u) ** 3,
    "INOUTCUBIC": lambda u: _inout_pow(u, 3),
}

# bone -> [(tick, value, easing of the segment ending here)]; rotation values in Blockbench degrees.
# torso value = (lean rx, yaw ry): yaw>0 turns the chest to the model's left, lean>0 tips it backward.
# The torso arrives one tick before the hands (chain: body first, blade last) and eases into the contact while the
# arms are still fast; the front (right) foot is planted exactly at the contact, the rear foot follows.
TRACKS = {
    "torso": [(0, (2.0, -2.0), None), (4, (6.0, -16.0), "INOUTSINE"), (10, (-11.0, 2.0), "INOUTQUAD"),
              (12, (-10.5, 6.0), "OUTQUAD"), (17, (-6.0, 17.0), "OUTQUAD"), (20, (2.0, -2.0), "INOUTSINE")],
    "rightLeg": [(0, (0.0,), None), (3, (-6.0,), "INOUTSINE"), (5, (-6.0,), "LINEAR"), (12, (24.0,), "INOUTQUAD"),
                 (17, (18.0,), "OUTQUAD"), (20, (0.0,), "INOUTSINE")],
    "leftLeg": [(0, (0.0,), None), (3, (5.0,), "INOUTSINE"), (5, (5.0,), "LINEAR"), (12, (-17.0,), "INOUTQUAD"),
                (14, (-18.0,), "OUTQUAD"), (17, (-12.0,), "OUTQUAD"), (20, (0.0,), "INOUTSINE")],
    # Only the nod: in game the head is not a child of the torso and a keyed yaw would replace the camera look, so yaw
    # and roll stay unkeyed (the converter writes pitch only for the head).
    "head": [(0, (0.0,), None), (5, (2.0,), "INOUTSINE"), (12, (-5.0,), "INQUAD"), (15, (-3.0,), "OUTQUAD"),
             (20, (0.0,), "INOUTSINE")],
}


def eval_track(bone, tick):
    """Value of a sparse track at a (fractional) tick, exactly as playerAnimator would interpolate it."""
    keys = TRACKS[bone]
    if tick <= keys[0][0]:
        return keys[0][1]
    for (t0, v0, _), (t1, v1, ease) in zip(keys, keys[1:]):
        if t0 <= tick <= t1:
            u = (tick - t0) / float(t1 - t0)
            e = EASE[ease](u)
            return tuple(a + (b - a) * e for a, b in zip(v0, v1))
    return keys[-1][1]


def hermite(ticks, values, t):
    """Cubic Hermite through (ticks, values) with Catmull-Rom tangents, zero speed at both ends."""
    n = len(ticks)
    if t <= ticks[0]:
        return values[0]
    if t >= ticks[-1]:
        return values[-1]
    i = 0
    for i in range(n - 1):
        if ticks[i] <= t <= ticks[i + 1]:
            break

    def tangent(j):
        if j == 0 or j == n - 1:
            return 0.0
        return (values[j + 1] - values[j - 1]) / (ticks[j + 1] - ticks[j - 1])
    h = ticks[i + 1] - ticks[i]
    u = (t - ticks[i]) / h
    u2, u3 = u * u, u * u * u
    return ((2 * u3 - 3 * u2 + 1) * values[i] + (u3 - 2 * u2 + u) * h * tangent(i)
            + (-2 * u3 + 3 * u2) * values[i + 1] + (u3 - u2) * h * tangent(i + 1))


def sample_channel(name, tick):
    knots = CHANNELS[name]
    ticks = [k[0] for k in knots]
    first = knots[0][1]
    if isinstance(first, (int, float)):
        return hermite(ticks, [k[1] for k in knots], tick)
    return tuple(hermite(ticks, [k[1][i] for k in knots], tick) for i in range(len(first)))


def sample_controls(tick):
    """(yaw, lean, hand point, blade az/el) at tick."""
    lean, yaw = eval_track("torso", tick)
    return yaw, lean, sample_channel("hand", tick), sample_channel("blade", tick)


def rot(deg):
    rx, ry, rz = (math.radians(v) for v in deg)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mm(Rz, mm(Ry, Rx))


F_FLIP = [[-1.0, 0.0, 0.0], [0.0, -1.0, 0.0], [0.0, 0.0, 1.0]]            # Blockbench <-> game axes (180 deg about Z)
Q_ITEM = [[-1.0, 0.0, 0.0], [0.0, 0.0, -1.0], [0.0, -1.0, 0.0]]            # item-frame vector -> arm-frame vector


def to_item_frame(m_bb):
    """Arm-local rotation in Blockbench axes -> rotation in the vanilla item frame (game axes)."""
    m_game = mm(F_FLIP, mm(m_bb, F_FLIP))
    return mm(mt(Q_ITEM), mm(m_game, Q_ITEM))


def euler_zyx(m):
    """Inverse of rot(): matrix -> (rx, ry, rz) degrees."""
    ry = -math.asin(max(-1.0, min(1.0, m[2][0])))
    if abs(math.cos(ry)) > 1e-6:
        rx = math.atan2(m[2][1], m[2][2])
        rz = math.atan2(m[1][0], m[0][0])
    else:  # gimbal: fold the roll into rx
        rx = math.atan2(-m[1][2], m[1][1])
        rz = 0.0
    return tuple(math.degrees(v) for v in (rx, ry, rz))


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mt(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def mv(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def mul(a, s):
    return tuple(x * s for x in a)


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    return math.sqrt(dot(a, a))


def unit(a):
    n = norm(a)
    return tuple(x / n for x in a)


def dir_from_az_el(az, el):
    """az to the left of straight ahead (-Z), el above horizontal."""
    a, e = math.radians(az), math.radians(el)
    return (-math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e))


def arm_euler(d):
    """Hanging arm is the rest pose. Direction d -> (rx, ry, 0) in ZYX order:
    Ry(b)(Rx(a)(0,-1,0)) = (-sin a sin b, -cos a, -sin a cos b)."""
    rx = math.degrees(math.acos(max(-1.0, min(1.0, -d[1]))))
    ry = math.degrees(math.atan2(-d[0], -d[2]))
    return rx, ry, 0.0


def default_edge_local():
    """Cutting edge in the Blockbench arm frame: model +Z, which the display transform turns forward-down."""
    t = math.radians(ITEM_TILT)
    return (0.0, -math.cos(t), -math.sin(t))


def frame_of(b, e):
    """Orthonormal frame with columns blade, edge, blade x edge."""
    c = cross(b, e)
    return [[b[0], e[0], c[0]], [b[1], e[1], c[1]], [b[2], e[2], c[2]]]


def edge_for(blade, roll_deg):
    """Edge direction for a blade direction and a roll: 0 = horizontal pointing left, -90 = pointing down."""
    up = (0.0, 1.0, 0.0)
    l = cross(up, blade)
    if norm(l) < 1e-6:
        l = (-1.0, 0.0, 0.0)
    l = unit(l)
    u = cross(blade, l)
    r = math.radians(roll_deg)
    return unit(add(mul(l, math.cos(r)), mul(u, math.sin(r))))


def default_blade_local():
    """Default blade in the Blockbench arm frame: forward, tilted ITEM_TILT towards the shoulder (up)."""
    t = math.radians(ITEM_TILT)
    return (0.0, math.sin(t), -math.cos(t))


def min_rotation(a, b):
    """Smallest rotation matrix taking unit a to unit b (Rodrigues)."""
    v = cross(a, b)
    c = dot(a, b)
    if c < -0.999999:
        axis = unit(cross(a, (1.0, 0.0, 0.0) if abs(a[0]) < 0.9 else (0.0, 1.0, 0.0)))
        return [[2 * axis[i] * axis[j] - (1 if i == j else 0) for j in range(3)] for i in range(3)]
    k = [[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]]
    k2 = mm(k, k)
    f = 1.0 / (1.0 + c)
    return [[(1 if i == j else 0) + k[i][j] + k2[i][j] * f for j in range(3)] for i in range(3)]


def prop_euler(b):
    """Sword prop modelled along local -Z: direction b -> (a, c, 0), a = asin(by), c = atan2(-bx, -bz)."""
    a = math.degrees(math.asin(max(-1.0, min(1.0, b[1]))))
    c = math.degrees(math.atan2(-b[0], -b[2]))
    return a, c, 0.0


def solve(tick):
    yaw, lean, hand_local, (baz, bel) = sample_controls(tick)
    R = rot((lean, yaw, 0.0))
    hand_r = add(TORSO_PIVOT, mv(R, sub(hand_local, TORSO_PIVOT)))
    pivot_r0 = add(TORSO_PIVOT, mv(R, sub(SHOULDER["rightArm"], TORSO_PIVOT)))
    pivot_l0 = add(TORSO_PIVOT, mv(R, sub(SHOULDER["leftArm"], TORSO_PIVOT)))

    to_r = sub(hand_r, pivot_r0)
    d_r = unit(to_r)
    pivot_r = sub(hand_r, mul(d_r, ARM_REACH))           # straight arm reaches exactly
    blade = unit(dir_from_az_el(baz, bel))               # wanted blade direction in the WORLD frame
    hand_l = sub(hand_r, mul(blade, HAND_GAP))
    to_l = sub(hand_l, pivot_l0)
    d_l = unit(to_l)
    pivot_l = sub(hand_l, mul(d_l, ARM_REACH))

    # rightItem: rotation in the right arm frame that turns the default blade AND edge into the wanted ones
    edge = edge_for(blade, sample_channel("edge", tick))
    R_arm = rot(arm_euler(d_r))
    b_rel = mv(mt(R_arm), blade)
    e_rel = mv(mt(R_arm), edge)
    M_item_bb = mm(frame_of(b_rel, e_rel), mt(frame_of(default_blade_local(), default_edge_local())))
    check_b = mv(R_arm, mv(M_item_bb, default_blade_local()))
    check_e = mv(R_arm, mv(M_item_bb, default_edge_local()))
    assert norm(sub(check_b, blade)) < 1e-6 and norm(sub(check_e, edge)) < 1e-6, (tick, check_b, blade)
    M_item = to_item_frame(M_item_bb)
    item_rot = euler_zyx(M_item)
    # keep the grip in the hand: p' = t + R p must fix G, so t = G - R G
    item_pos = sub(GRIP_ITEM, mv(M_item, GRIP_ITEM))

    out = {"tick": tick, "torso": (lean, yaw, 0.0), "hand_r": hand_r, "hand_l": hand_l, "blade": blade, "edge": edge,
           "rLeg": eval_track("rightLeg", tick)[0], "lLeg": eval_track("leftLeg", tick)[0],
           "shift_r": norm(to_r) - ARM_REACH, "shift_l": norm(to_l) - ARM_REACH, "yaw": yaw}
    out["rightArm"] = {"rot": arm_euler(d_r), "pos": sub(pivot_r, SHOULDER["rightArm"]), "dir": d_r}
    out["leftArm"] = {"rot": arm_euler(d_l), "pos": sub(pivot_l, SHOULDER["leftArm"]), "dir": d_l}
    out["rightItem"] = {"rot": item_rot, "pos": item_pos}
    out["prop"] = {"rot": prop_euler(blade), "pos": sub(hand_r, (0.0, 20.0, 0.0))}
    return out


def table():
    return [solve(t) for t in range(0, END_TICK + 1)]


def keyframes(rows, tick_s=0.05):
    """Blockbench keyframes: sparse eased keys for the body tracks, one key per tick for the IK-bound arms and item
    (the arms must stay on the grip, so their curve is baked from the eased torso)."""
    kfs = []
    for bone, keys in TRACKS.items():
        for tick, value, ease in keys:
            if bone == "torso":
                lean, yaw = value
                v = (lean, yaw, 0.0)
            elif bone == "head":
                v = (value[0], 0.0, 0.0)                    # pitch only, see TRACKS
            else:
                v = (value[0], 0.0, 0.0)
            kf = {"bone": bone, "channel": "rotation", "time": round(tick * tick_s, 4),
                  "value": [round(x, 3) for x in v], "interpolation": "linear" if ease in (None, "LINEAR") else "catmullrom"}
            if ease:
                kf["easing"] = ease
            kfs.append(kf)
    for r in rows:
        t = round(r["tick"] * tick_s, 4)

        def key(bone, channel, v):
            kfs.append({"bone": bone, "channel": channel, "time": t, "value": [round(x, 3) for x in v],
                        "interpolation": "linear"})
        for arm in ("rightArm", "leftArm"):
            key(arm, "rotation", r[arm]["rot"])
            key(arm, "position", r[arm]["pos"])
        key("rightItem", "rotation", r["rightItem"]["rot"])
        key("rightItem", "position", r["rightItem"]["pos"])
        key("sword_prop", "rotation", r["prop"]["rot"])
        key("sword_prop", "position", r["prop"]["pos"])
    return kfs


def main():
    rows = table()
    print("tick   yaw lean | R arm rx    ry | L arm rx    ry | shiftR shiftL | item rx    ry    rz | itemPos | blade")
    worst = 0.0
    for r in rows:
        rr, ll, it = r["rightArm"]["rot"], r["leftArm"]["rot"], r["rightItem"]["rot"]
        ip = r["rightItem"]["pos"]
        worst = max(worst, abs(r["shift_l"]), abs(r["shift_r"]))
        print("%3d %6.1f %4.1f | %6.1f %6.1f | %6.1f %6.1f | %6.2f %6.2f | %6.1f %6.1f %6.1f | %4.1f %4.1f %4.1f | (%5.2f,%5.2f,%5.2f)" % (
            r["tick"], r["yaw"], r["torso"][0], rr[0], rr[1], ll[0], ll[1], r["shift_r"], r["shift_l"],
            it[0], it[1], it[2], ip[0], ip[1], ip[2], *r["blade"]))
    print("worst pivot shift: %.2f px (limit %.1f)" % (worst, MAX_SHIFT))
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.normpath(os.path.join(here, "..", "design", "anim", "slash_rl_keyframes.json"))
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(keyframes(rows), f)
    print("wrote", out)


if __name__ == "__main__":
    sys.exit(main())
