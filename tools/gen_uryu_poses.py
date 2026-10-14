#!/usr/bin/env python3
"""Uryu key poses P1 to P6 and the ability gestures of design/URYU_VFX_STORYBOARD.md 3 (third person), authored in code.

No Blockbench GUI is needed: this script is the "slash_solver" of the Uryu animations (same pipeline as the sword
animations, see tools/slash_solver.py, tools/bbmodel_inject.py, tools/bbmodel_to_playeranim.py):

  1. the animations below (key tables, deterministic) are evaluated on the FLAT game rig (parts are not parented in the
     game, so a torso twist does not carry the arms: the arm keys are chest relative and are composed with the torso per
     tick, the shoulder pivots follow the torso by position keys),
  2. the keyframes are written into design/anim/uryu_poses.bbmodel (a copy of player_game_rig.bbmodel, animations
     replaced, `sword_prop` replaced by the preview aid `bow_prop`),
  3. the repository's exporter tools/bbmodel_to_playeranim.py turns every animation into
     mod/src/main/resources/assets/reiatsu_test/player_animations/uryu_<name>.json,
  4. checks (hands on the string anchor, no pops, torso yaw < 60, first and last pose = idle ...) and, with --sheets,
     contact sheets (matplotlib stick figures, no game or Blender needed) in blender/renders/uryu_poses/.

Usage:  python -I tools/gen_uryu_poses.py [--sheets] [--no-write] [--check]
Run from the repository root (paths are relative to this file). Everything is deterministic (no random numbers, uuid5).

Conventions (Blockbench space, degrees, the same as bbmodel_to_playeranim.py): right = +X, up = +Y, front = -Z.
Arms: rx 0 = hanging, +90 = horizontal forward, +180 = straight up; ry > 0 moves the tip of the RIGHT arm to the body centre
(the left arm: ry < 0); rz > 0 swings the arm outwards (right arm). rot() = Rz Ry Rx.
STORYBOARD SIGN FIX: the storyboard wrote torso/head `rx` as lean-forward-positive, the exporter maps pitch = -rx, so
Blockbench rx > 0 leans BACK / looks UP. Here the torso is authored as `lean` (forward positive) and the head as `nod`
(down positive), the numbers of the storyboard kept, and converted with rx = -lean / rx = -nod. Legs and arms keep rx.
Head: only the pitch is keyed. In the game it is ADDED to the camera pitch (UryuTpLayer), not replacing it.
"""
import argparse
import copy
import json
import math
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import bbmodel_to_playeranim as exporter  # noqa: E402
import slash_solver as s  # noqa: E402

TEMPLATE = os.path.join(ROOT, "design", "anim", "player_game_rig.bbmodel")
OUT_BBMODEL = os.path.join(ROOT, "design", "anim", "uryu_poses.bbmodel")
OUT_DIR = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test", "player_animations")
SHEET_DIR = os.path.join(ROOT, "blender", "renders", "uryu_poses")

TORSO_PIVOT = (0.0, 24.0, 0.0)
SHOULDER = {"r": (5.0, 22.0, 0.0), "l": (-5.0, 22.0, 0.0)}
HIP = {"r": (1.9, 12.0, 0.0), "l": (-1.9, 12.0, 0.0)}
ARM_REACH = 9.5          # shoulder pivot to the fist centre along a straight arm (same as slash_solver)
MAX_SHIFT = 3.5          # px a shoulder pivot may leave the shoulder (same limit as slash_solver)
MAX_TORSO_YAW = 60.0     # anim_rules 17, storyboard 3.3: stays under 60
TICK = 0.05

ARM_PART = {"r": "rightArm", "l": "leftArm"}
LEG_PART = {"r": "rightLeg", "l": "leftLeg"}

# ------------------------------------------------------------------------------------------------ easing
# playerAnimator easing (segment that ENDS at the key), the same functions as tools/slash_solver.py plus the ones used here.
_c1 = 1.70158
_c3 = _c1 + 1.0


def _inout_pow(u, n):
    return 0.5 * (2 * u) ** n if u < 0.5 else 1 - 0.5 * (2 * (1 - u)) ** n


EASE = dict(s.EASE)
EASE.update({
    "OUTSINE": lambda u: math.sin(u * math.pi / 2),
    "OUTBACK": lambda u: 1 + _c3 * (u - 1) ** 3 + _c1 * (u - 1) ** 2,
    "INBACK": lambda u: _c3 * u ** 3 - _c1 * u ** 2,
    "CONSTANT": lambda u: 0.0 if u < 1.0 else 1.0,
})


def ease_u(name, u):
    return EASE[name](max(0.0, min(1.0, u)))


def lerp(a, b, f):
    if isinstance(a, (int, float)):
        return a + (b - a) * f
    return tuple(x + (y - x) * f for x, y in zip(a, b))


def eval_knots(knots, tick):
    """Sparse track [(tick, value, ease)] at a tick: value of the first key before it, the segment ending at a key uses that key's ease."""
    if tick <= knots[0][0]:
        return knots[0][1]
    for (t0, v0, _e0), (t1, v1, e1) in zip(knots, knots[1:]):
        if t0 <= tick <= t1:
            return lerp(v0, v1, ease_u(e1, (tick - t0) / float(t1 - t0)))
    return knots[-1][1]


# ------------------------------------------------------------------------------------------------ rig kinematics
def torso_matrix(lean, yaw):
    """Torso rotation about the neck pivot; lean > 0 is forward (Blockbench rx = -lean), yaw > 0 turns the chest to the model's left."""
    return s.rot((-lean, yaw, 0.0))


def shoulder_world(side, lean, yaw):
    """Shoulder pivot after the torso turned (the arms are not children of the torso in the game: shoulders follow by position keys)."""
    R = torso_matrix(lean, yaw)
    return s.add(TORSO_PIVOT, s.mv(R, s.sub(SHOULDER[side], TORSO_PIVOT)))


def nearest_euler(prev, cand):
    """The Euler triple equivalent to `cand` that is closest to `prev` (keeps interpolation continuous across the ZYX branches)."""
    rx, ry, rz = cand
    options = [(rx, ry, rz), (rx + 180.0, 180.0 - ry, rz + 180.0), (rx - 180.0, 180.0 - ry, rz - 180.0),
               (rx + 180.0, -180.0 - ry, rz + 180.0), (rx - 180.0, -180.0 - ry, rz - 180.0)]
    best = None
    for o in options:
        for k in range(-1, 2):
            for m in range(-1, 2):
                for n in range(-1, 2):
                    t = (o[0] + 360.0 * k, o[1] + 360.0 * m, o[2] + 360.0 * n)
                    if abs(t[1]) > 180.0:
                        continue
                    d = max(abs(t[i] - prev[i]) for i in range(3)) if prev is not None else max(abs(t[0]), abs(t[1]), abs(t[2]))
                    if best is None or d < best[0] - 1e-9:
                        best = (d, t)
    return best[1]


def arm_key(kind, value, side, lean, yaw, prev_rel=None):
    """One arm key as (rel Euler, extra shoulder shift in the chest frame).

    kind 'rel'  : chest relative Blockbench Euler (the storyboard numbers), no shift
         'dir'  : (azimuth to the left of straight ahead, elevation) of the straight arm in the entity (root) frame
         'hand' : fist target in the root frame; the straight arm points at it and the shoulder pivot slides so that it lands exactly
    """
    Rt = torso_matrix(lean, yaw)
    if kind == "rel":
        return tuple(float(v) for v in value), (0.0, 0.0, 0.0)
    sh = shoulder_world(side, lean, yaw)
    if kind == "dir":
        d = s.dir_from_az_el(*value)
        extra_root = (0.0, 0.0, 0.0)
    elif kind == "hand":
        v = s.sub(value, sh)
        n = s.norm(v)
        d = s.unit(v)
        extra_root = s.mul(d, n - ARM_REACH)
    else:
        raise ValueError(kind)
    root = s.rot(s.arm_euler(d))
    rel = s.euler_zyx(s.mm(s.mt(Rt), root))
    rel = nearest_euler(prev_rel, rel)
    extra_chest = s.mv(s.mt(Rt), extra_root)
    return rel, extra_chest


class Anim:
    """One animation: sparse body tracks and arm key lists, evaluated per tick."""

    def __init__(self, name, end, torso, head=None, legs=None, arms=None, lift=None, loop="once", length=None, note=""):
        self.name = name
        self.end = end
        self.torso = torso                       # [(tick, (lean, yaw), ease)]
        self.head = head                         # [(tick, (nod,), ease)] or None
        self.legs = legs or {}                   # {'r': [(tick, (rx, rz), ease)], 'l': ...}
        self.arms_spec = arms or {}              # {'r': [(tick, kind, value, ease)], 'l': ...}
        self.lift = lift                         # [(tick, (px,), ease)] or None (all parts moved up: a float)
        self.loop = loop
        self.length = length if length is not None else end * TICK
        self.note = note
        self.arm_keys = {}
        self._solve_arms()

    def _solve_arms(self):
        for side, spec in self.arms_spec.items():
            keys = []
            prev = None
            for tick, kind, value, ease in spec:
                lean, yaw = eval_knots(self.torso, tick)
                rel, extra = arm_key(kind, value, side, lean, yaw, prev)
                prev = rel
                keys.append((tick, rel, extra, ease))
            self.arm_keys[side] = keys

    def arm_rel(self, side, tick):
        keys = self.arm_keys[side]
        if tick <= keys[0][0]:
            return keys[0][1], keys[0][2]
        for (t0, r0, x0, _e0), (t1, r1, x1, e1) in zip(keys, keys[1:]):
            if t0 <= tick <= t1:
                f = ease_u(e1, (tick - t0) / float(t1 - t0))
                return lerp(r0, r1, f), lerp(x0, x1, f)
        return keys[-1][1], keys[-1][2]

    def pose(self, tick):
        """Everything at a tick: dict with torso, head, legs, lift and per keyed arm root Euler, shoulder offset and fist."""
        lean, yaw = eval_knots(self.torso, tick)
        out = {"lean": lean, "yaw": yaw, "tick": tick}
        out["nod"] = eval_knots(self.head, tick)[0] if self.head else None
        out["legs"] = {k: eval_knots(v, tick) for k, v in self.legs.items()}
        out["lift"] = eval_knots(self.lift, tick)[0] if self.lift else 0.0
        Rt = torso_matrix(lean, yaw)
        for side in self.arm_keys:
            rel, extra = self.arm_rel(side, tick)
            R = s.mm(Rt, s.rot(rel))
            sh = shoulder_world(side, lean, yaw)
            pivot = s.add(sh, s.mv(Rt, extra))
            d = s.mv(R, (0.0, -1.0, 0.0))
            out[side] = {"rel": rel, "root": s.euler_zyx(R), "pivot": pivot, "offset": s.sub(pivot, SHOULDER[side]),
                         "dir": d, "fist": s.add(pivot, s.mul(d, ARM_REACH)), "shift": s.norm(extra), "R": R}
        return out


# ------------------------------------------------------------------------------------------------ the animations
IO, OUTC, INC, OUTB, LIN, INOUTC = "INOUTSINE", "OUTCUBIC", "INCUBIC", "OUTBACK", "LINEAR", "INOUTCUBIC"

IDLE_R = (18.0, 4.0, 6.0)          # P1 bow arm (storyboard 3.2)
IDLE_L = (6.0, 0.0, -4.0)
IDLE_TORSO = (0.0, 6.0)            # (lean, yaw)

# full draw anchors (3.3): the string hand on the right cheek, 5 px in front of the face, on the arrow line of the bow hand
BOW_ELEV = 8.0                      # elevation of the straight bow arm at full draw (storyboard 3.3 rx 90 to 98)
FULL_TORSO = (-2.0, 55.0)           # storyboard: [+2 lean back, 55]
ANCHOR = (1.8, 24.2, -5.0)          # string hand at full draw (root frame)
FLICK = (4.5, 25.2, 1.0)            # string hand 1 tick after the release (storyboard: [+88,-92,+16] flicks back)
FOLLOW = (5.2, 24.6, 2.2)           # follow-through (storyboard: [+80,-98,+20] hold)


def anim_idle():
    return Anim("idle", 20, torso=[(0, IDLE_TORSO, LIN), (20, IDLE_TORSO, LIN)],
                arms={"r": [(0, "rel", IDLE_R, LIN), (20, "rel", IDLE_R, LIN)]}, loop="loop",
                note="P1, the bow arm only (the left arm keeps the vanilla walk swing), breathing is added in code")


def anim_draw():
    """P2, 20 ticks: the bow arm leads (tick 8), the draw arm arrives at 14, the torso last (anim_rules 7 to 9)."""
    torso = [(0, IDLE_TORSO, LIN), (4, (0.0, 30.0), IO), (10, (0.0, 50.0), IO), (20, FULL_TORSO, IO)]
    legs = {"r": [(0, (0.0, 0.0), LIN), (4, (4.0, 4.0), IO), (10, (8.0, 6.0), IO), (20, (10.0, 8.0), IO)],
            "l": [(0, (0.0, 0.0), LIN), (4, (-4.0, -4.0), IO), (10, (-8.0, -6.0), IO), (20, (-10.0, -8.0), IO)]}
    arms = {"r": [(0, "rel", IDLE_R, LIN), (4, "rel", (75.0, -22.0, 2.0), IO), (8, "dir", (0.0, BOW_ELEV), IO), (20, "dir", (0.0, BOW_ELEV), LIN)],
            "l": [(0, "rel", IDLE_L, LIN), (4, "rel", (55.0, -35.0, -4.0), IO), (10, "rel", (95.0, -62.0, 6.0), IO),
                  (14, "hand", ANCHOR, IO), (20, "hand", ANCHOR, LIN)]}
    return Anim("draw", 20, torso=torso, legs=legs, arms=arms, note="P2 draw, hold at tick 20 (tremor in code)")


def anim_release():
    """P3, 14 ticks: tick 0 = full draw, 1 the string hand flicks back (OUTCUBIC), 3 follow-through, 8 blend to idle over 6 ticks."""
    torso = [(0, FULL_TORSO, LIN), (1, (-2.0, 49.0), OUTC), (8, (-2.0, 49.0), LIN), (14, IDLE_TORSO, IO)]
    legs = {"r": [(0, (10.0, 8.0), LIN), (8, (10.0, 8.0), LIN), (14, (0.0, 0.0), IO)],
            "l": [(0, (-10.0, -8.0), LIN), (8, (-10.0, -8.0), LIN), (14, (0.0, 0.0), IO)]}
    arms = {"r": [(0, "dir", (0.0, BOW_ELEV), LIN), (1, "dir", (0.0, BOW_ELEV + 4.0), OUTC), (3, "dir", (0.0, BOW_ELEV + 1.0), IO),
                  (8, "dir", (0.0, BOW_ELEV + 1.0), LIN), (14, "rel", IDLE_R, IO)],
            "l": [(0, "hand", ANCHOR, LIN), (1, "hand", FLICK, OUTC), (3, "hand", FOLLOW, IO), (8, "hand", FOLLOW, LIN),
                  (14, "rel", IDLE_L, IO)]}
    return Anim("release", 14, torso=torso, legs=legs, arms=arms, note="P3 release (basic shot), 8 ticks + 6 ticks blend")


def anim_release_z():
    """Z ability (storyboard 3.5): P3 compressed to 5 ticks, the draw starts at tick 0 at about 60 percent, 10 ticks in all (0.5 s)."""
    torso = [(0, (0.0, 24.0), LIN), (2, (0.0, 50.0), IO), (3, FULL_TORSO, IO), (4, (-2.0, 49.0), OUTC), (6, (-2.0, 49.0), LIN),
             (10, IDLE_TORSO, IO)]
    legs = {"r": [(0, (3.0, 3.0), LIN), (3, (10.0, 8.0), IO), (6, (10.0, 8.0), LIN), (10, (0.0, 0.0), IO)],
            "l": [(0, (-3.0, -3.0), LIN), (3, (-10.0, -8.0), IO), (6, (-10.0, -8.0), LIN), (10, (0.0, 0.0), IO)]}
    arms = {"r": [(0, "rel", (60.0, -12.0, 3.0), LIN), (2, "dir", (0.0, BOW_ELEV), IO), (3, "dir", (0.0, BOW_ELEV), LIN),
                  (4, "dir", (0.0, BOW_ELEV + 4.0), OUTC), (6, "dir", (0.0, BOW_ELEV + 1.0), IO), (10, "rel", IDLE_R, IO)],
            "l": [(0, "rel", (45.0, -28.0, -4.0), LIN), (2, "rel", (95.0, -62.0, 6.0), IO), (3, "hand", ANCHOR, IO),
                  (4, "hand", FLICK, OUTC), (6, "hand", FOLLOW, IO), (10, "rel", IDLE_L, IO)]}
    return Anim("release_z", 10, torso=torso, legs=legs, arms=arms, note="Z homing arrow: quick draw + release, 0.5 s")


def anim_seele():
    """P4 Seele Schneider lunge (3.6): the off (left) arm carries the blade, slashes at ticks 3, 8, 13, hold 18, idle at 26."""
    torso = [(0, IDLE_TORSO, LIN), (1, (8.0, -20.0), OUTC), (3, (10.0, 30.0), IO), (8, (8.0, -22.0), IO), (13, (16.0, 0.0), INC),
             (18, (16.0, 0.0), LIN), (26, IDLE_TORSO, IO)]
    head = [(0, (0.0,), LIN), (1, (6.0,), IO), (18, (6.0,), LIN), (26, (0.0,), IO)]
    legs = {"r": [(0, (0.0, 0.0), LIN), (1, (-14.0, 0.0), IO), (3, (-14.0, 0.0), LIN), (13, (-18.0, 0.0), INC), (18, (-18.0, 0.0), LIN), (26, (0.0, 0.0), IO)],
            "l": [(0, (0.0, 0.0), LIN), (1, (16.0, 0.0), IO), (3, (16.0, 0.0), LIN), (13, (20.0, 0.0), INC), (18, (20.0, 0.0), LIN), (26, (0.0, 0.0), IO)]}
    # the blade arm is keyed by its direction in the entity frame (azimuth to the left of straight ahead, elevation): the chest turns
    # +30 / -22 / 0 under it, the storyboard's chest relative numbers [105,-35,10] / [95,+20,-25] / [170,0,0] -> [30,0,0] are kept for the
    # sweep shape (the left hand cuts right to left at 3, back-hand left to right at 8, overhead chop at 13), solved in the entity frame
    arms = {"r": [(0, "rel", IDLE_R, LIN), (1, "rel", (5.0, 8.0, 10.0), IO), (18, "rel", (5.0, 8.0, 10.0), LIN), (26, "rel", IDLE_R, IO)],
            "l": [(0, "rel", IDLE_L, LIN), (1, "dir", (-42.0, 22.0), OUTC), (3, "dir", (38.0, 6.0), INC),
                  (8, "dir", (-38.0, 6.0), IO), (11, "dir", (0.0, 82.0), IO), (13, "dir", (0.0, -48.0), INC),
                  (18, "dir", (0.0, -48.0), LIN), (26, "rel", IDLE_L, IO)]}
    return Anim("seele", 26, torso=torso, head=head, legs=legs, arms=arms, note="P4 melee lunge, slashes at 3, 8, 13")


def anim_blut():
    """P5 Blut Arterie release (3.7): arms crossed at tick 6, snap open at 12 (OUTBACK), relax at 24, idle at 34."""
    torso = [(0, IDLE_TORSO, LIN), (6, (10.0, 0.0), IO), (8, (10.0, 0.0), LIN), (12, (-8.0, 0.0), OUTB), (14, (-8.0, 0.0), LIN),
             (24, (0.0, 0.0), IO), (34, IDLE_TORSO, IO)]
    head = [(0, (0.0,), LIN), (6, (8.0,), IO), (8, (8.0,), LIN), (12, (-10.0,), OUTB), (14, (-10.0,), LIN), (24, (0.0,), IO), (34, (0.0,), LIN)]
    legs = {"r": [(0, (0.0, 0.0), LIN), (6, (3.0, 0.0), IO), (8, (3.0, 0.0), LIN), (12, (0.0, 0.0), IO), (34, (0.0, 0.0), LIN)],
            "l": [(0, (0.0, 0.0), LIN), (6, (3.0, 0.0), IO), (8, (3.0, 0.0), LIN), (12, (0.0, 0.0), IO), (34, (0.0, 0.0), LIN)]}
    # the wrists cross in front of the chest: the right arm a little lower than the left (78 / 98 instead of 85 / 85) so the boxes stack
    arms = {"r": [(0, "rel", IDLE_R, LIN), (6, "rel", (78.0, 50.0, -8.0), IO), (8, "rel", (78.0, 50.0, -8.0), LIN),
                  (12, "rel", (15.0, -10.0, 58.0), OUTB), (14, "rel", (15.0, -10.0, 58.0), LIN), (24, "rel", (4.0, 0.0, 14.0), IO),
                  (34, "rel", IDLE_R, IO)],
            "l": [(0, "rel", IDLE_L, LIN), (6, "rel", (98.0, -50.0, 8.0), IO), (8, "rel", (98.0, -50.0, 8.0), LIN),
                  (12, "rel", (15.0, 10.0, -58.0), OUTB), (14, "rel", (15.0, 10.0, -58.0), LIN), (24, "rel", (4.0, 0.0, -14.0), IO),
                  (34, "rel", IDLE_L, IO)]}
    return Anim("blut", 34, torso=torso, head=head, legs=legs, arms=arms, note="P5 Blut Arterie, burst at tick 12 (0.55 s + the effect)")


def anim_vollstandig():
    """P6 Quincy: Vollstaendig release (3.8): crouch gather 4, burst at 13 (OUTBACK, arms wide and up), hold 20, ease down 30, idle 40."""
    torso = [(0, IDLE_TORSO, LIN), (4, (12.0, 0.0), IO), (10, (12.0, 0.0), LIN), (13, (-12.0, 0.0), OUTB), (20, (-12.0, 0.0), LIN),
             (30, (-2.0, 0.0), IO), (40, (0.0, 6.0), IO)]
    head = [(0, (0.0,), LIN), (4, (10.0,), IO), (10, (10.0,), LIN), (13, (-14.0,), OUTB), (20, (-14.0,), LIN), (30, (-2.0,), IO), (40, (0.0,), IO)]
    legs = {"r": [(0, (0.0, 0.0), LIN), (4, (14.0, 4.0), IO), (10, (14.0, 4.0), LIN), (13, (0.0, 0.0), OUTB), (40, (0.0, 0.0), LIN)],
            "l": [(0, (0.0, 0.0), LIN), (4, (14.0, -4.0), IO), (10, (14.0, -4.0), LIN), (13, (0.0, 0.0), OUTB), (40, (0.0, 0.0), LIN)]}
    lift = [(0, (0.0,), LIN), (10, (0.0,), LIN), (13, (2.0,), OUTB), (30, (2.0,), LIN), (40, (0.0,), IO)]
    arms = {"r": [(0, "rel", IDLE_R, LIN), (4, "rel", (70.0, 45.0, -6.0), IO), (10, "rel", (70.0, 45.0, -6.0), LIN),
                  (13, "rel", (25.0, -5.0, 95.0), OUTB), (20, "rel", (25.0, -5.0, 95.0), LIN), (30, "rel", (8.0, 0.0, 30.0), IO),
                  (40, "rel", (6.0, 0.0, 8.0), IO)],
            "l": [(0, "rel", IDLE_L, LIN), (4, "rel", (84.0, -45.0, 6.0), IO), (10, "rel", (84.0, -45.0, 6.0), LIN),
                  (13, "rel", (25.0, 5.0, -95.0), OUTB), (20, "rel", (25.0, 5.0, -95.0), LIN), (30, "rel", (8.0, 0.0, -30.0), IO),
                  (40, "rel", (6.0, 0.0, -8.0), IO)]}
    return Anim("vollstandig", 40, torso=torso, head=head, legs=legs, arms=arms, lift=lift,
                note="P6 Quincy: Vollstaendig, burst at tick 13 (0.65 s)")


SKY_ELEV = 62.0
SKY_TORSO = (-8.0, 20.0)
SKY_ANCHOR = (1.8, 24.6, -4.6)
SKY_FLICK = (4.8, 25.6, 0.8)


def anim_licht_regen():
    """Licht Regen (3.5): raise and draw 0..12 (the effect event is at the RELEASE = tick 12), flick, stays raised 3 s, lowers over 12."""
    torso = [(0, IDLE_TORSO, LIN), (12, SKY_TORSO, IO), (13, (-9.0, 16.0), OUTC), (72, (-9.0, 16.0), LIN), (84, IDLE_TORSO, IO), (96, IDLE_TORSO, LIN)]
    head = [(0, (0.0,), LIN), (12, (-22.0,), IO), (72, (-22.0,), LIN), (84, (0.0,), IO), (96, (0.0,), LIN)]
    arms = {"r": [(0, "rel", IDLE_R, LIN), (6, "rel", (90.0, -20.0, 0.0), IO), (12, "dir", (-6.0, SKY_ELEV), IO), (13, "dir", (-6.0, SKY_ELEV + 3.0), OUTC),
                  (16, "dir", (-6.0, SKY_ELEV + 1.0), IO), (72, "dir", (-6.0, SKY_ELEV + 1.0), LIN), (84, "rel", IDLE_R, IO), (96, "rel", IDLE_R, LIN)],
            "l": [(0, "rel", IDLE_L, LIN), (6, "rel", (80.0, -45.0, 0.0), IO), (8, "hand", SKY_ANCHOR, IO), (12, "hand", SKY_ANCHOR, LIN),
                  (13, "hand", SKY_FLICK, OUTC), (16, "hand", SKY_FLICK, IO), (72, "hand", SKY_FLICK, LIN), (84, "rel", IDLE_L, IO),
                  (96, "rel", IDLE_L, LIN)]}
    legs = {"r": [(0, (0.0, 0.0), LIN), (12, (6.0, 6.0), IO), (72, (6.0, 6.0), LIN), (84, (0.0, 0.0), IO), (96, (0.0, 0.0), LIN)],
            "l": [(0, (0.0, 0.0), LIN), (12, (-6.0, -6.0), IO), (72, (-6.0, -6.0), LIN), (84, (0.0, 0.0), IO), (96, (0.0, 0.0), LIN)]}
    return Anim("licht_regen", 96, torso=torso, head=head, legs=legs, arms=arms, note="Licht Regen: release (event) at tick 12")


STAR_TORSO = (-4.0, 60.0)
STAR_ELEV = BOW_ELEV + 4.0
STAR_FLICK = (6.4, 25.6, 4.5)          # 2x the P3 flick, farther back
STAR_FOLLOW = (6.6, 24.6, 5.0)


def anim_sternenstaub():
    """Sternenstaub (3.5): slow full draw (key 20 values by tick 12, torso yaw 60 and bow arm +4 by 16), release at 16 with twice the recoil, hold, idle 60."""
    torso = [(0, IDLE_TORSO, LIN), (12, FULL_TORSO, IO), (16, STAR_TORSO, IO), (17, (-4.0, 46.0), OUTC), (24, (-4.0, 46.0), LIN), (48, (-2.0, 30.0), IO),
             (60, IDLE_TORSO, IO)]
    legs = {"r": [(0, (0.0, 0.0), LIN), (12, (10.0, 8.0), IO), (24, (10.0, 8.0), LIN), (60, (0.0, 0.0), IO)],
            "l": [(0, (0.0, 0.0), LIN), (12, (-10.0, -8.0), IO), (24, (-10.0, -8.0), LIN), (60, (0.0, 0.0), IO)]}
    arms = {"r": [(0, "rel", IDLE_R, LIN), (6, "rel", (75.0, -22.0, 2.0), IO), (12, "dir", (0.0, BOW_ELEV), IO), (16, "dir", (0.0, STAR_ELEV), IO),
                  (17, "dir", (0.0, STAR_ELEV + 8.0), OUTC), (20, "dir", (0.0, STAR_ELEV + 2.0), IO), (24, "dir", (0.0, STAR_ELEV + 2.0), LIN),
                  (60, "rel", IDLE_R, IO)],
            "l": [(0, "rel", IDLE_L, LIN), (6, "rel", (55.0, -35.0, -4.0), IO), (9, "rel", (95.0, -62.0, 6.0), IO), (12, "hand", ANCHOR, IO),
                  (16, "hand", ANCHOR, LIN), (17, "hand", STAR_FLICK, OUTC), (20, "hand", STAR_FOLLOW, IO), (24, "hand", STAR_FOLLOW, LIN),
                  (60, "rel", IDLE_L, IO)]}
    return Anim("sternenstaub", 60, torso=torso, legs=legs, arms=arms, note="Sternenstaub: release at tick 16 (0.8 s)")


def anim_wings():
    """Wing barrage (3.5): arms spread T-ish, chest out, a micro recoil per volley (every 8 ticks), 130 ticks."""
    pulses = []
    for k in range(14):
        t = 12 + 8 * k
        pulses += [(t, (-6.0, 0.0), OUTC), (t + 4, (-4.0, 0.0), IO)]
    torso = [(0, IDLE_TORSO, LIN), (8, (-4.0, 0.0), IO)] + pulses + [(124, (-4.0, 0.0), LIN), (130, IDLE_TORSO, IO)]
    arms = {"r": [(0, "rel", IDLE_R, LIN), (8, "rel", (10.0, -4.0, 50.0), OUTC), (122, "rel", (10.0, -4.0, 50.0), LIN), (130, "rel", IDLE_R, IO)],
            "l": [(0, "rel", IDLE_L, LIN), (8, "rel", (10.0, 4.0, -50.0), OUTC), (122, "rel", (10.0, 4.0, -50.0), LIN), (130, "rel", IDLE_L, IO)]}
    return Anim("wings", 130, torso=torso, arms=arms, loop="hold", note="wing barrage: T pose, volley recoil every 8 ticks")


def anim_sprenger():
    """Sprenger (3.5): five overhand throws of 4 ticks, alternating R L R L R, then the clasped guard (hands before the chest) until the
    explosion (tick 40) and out to idle at 52."""
    torso = [(0, IDLE_TORSO, LIN), (2, (4.0, 12.0), IO)]
    arms = {"r": [(0, "rel", IDLE_R, LIN)], "l": [(0, "rel", IDLE_L, LIN)]}
    for i in range(5):
        t0 = 4 * i
        side = "r" if i % 2 == 0 else "l"
        other = "l" if side == "r" else "r"
        sgn = 1.0 if side == "r" else -1.0
        wind = (80.0, -10.0 * sgn, 4.0 * sgn)
        top = (150.0, 0.0, 10.0 * sgn)
        follow = (60.0, -6.0 * sgn, 4.0 * sgn)
        rest_other = IDLE_L if other == "l" else IDLE_R
        arms[side] += [(t0 + 1, "rel", wind, OUTC), (t0 + 2, "rel", top, IN_), (t0 + 4, "rel", follow, OUTC)]
        arms[other] += [(t0 + 2, "rel", (rest_other[0] + 20.0, rest_other[1], rest_other[2]), IO), (t0 + 4, "rel", rest_other, IO)]
        yaw = 12.0 if side == "r" else -2.0
        torso += [(t0 + 3, (4.0, yaw), IO)]
    arms["r"] += [(24, "rel", (35.0, -30.0, 0.0), IO), (40, "rel", (35.0, -30.0, 0.0), LIN), (52, "rel", IDLE_R, IO)]
    arms["l"] += [(24, "rel", (35.0, -45.0, 0.0), IO), (40, "rel", (35.0, -45.0, 0.0), LIN), (52, "rel", IDLE_L, IO)]
    torso += [(24, (4.0, 6.0), IO), (40, (4.0, 6.0), LIN), (52, IDLE_TORSO, IO)]
    # drop key lists that have two keys at one tick (a throw ends where the next one of the same arm starts)
    for side in arms:
        arms[side] = _dedupe(arms[side])
    torso = _dedupe(torso, key=lambda k: k[0])
    return Anim("sprenger", 52, torso=torso, arms=arms, note="Sprenger: five throws R L R L R, guard until the explosion at tick 40")


IN_ = "INCUBIC"


def _dedupe(keys, key=lambda k: k[0]):
    out = {}
    for k in keys:
        out[key(k)] = k
    return [out[t] for t in sorted(out)]


ANIMS = [anim_idle, anim_draw, anim_release, anim_release_z, anim_seele, anim_blut, anim_vollstandig, anim_licht_regen,
         anim_sternenstaub, anim_wings, anim_sprenger]


# ------------------------------------------------------------------------------------------------ baking to Blockbench keyframes
def _simplify(keys, eps=1e-3):
    """Drop interior keys that lie on the straight line of their neighbours (exact for linear baked tracks)."""
    if len(keys) <= 2:
        return keys
    out = [keys[0]]
    for i in range(1, len(keys) - 1):
        a, b, c = out[-1], keys[i], keys[i + 1]
        f = (b[0] - a[0]) / float(c[0] - a[0])
        if abs(a[1] + (c[1] - a[1]) * f - b[1]) > eps:
            out.append(b)
    out.append(keys[-1])
    return out


def _bake_vec(ticks_vals, eps=1e-3):
    """[(tick, (x,y,z))] -> list of per tick keys, simplified per axis; returns [(tick, (x,y,z))] on the union of kept ticks."""
    kept = set()
    for axis in range(3):
        kept.update(t for t, _v in _simplify([(t, v[axis]) for t, v in ticks_vals], eps))
    return [(t, v) for t, v in ticks_vals if t in kept]


def keyframes(anim):
    """Blockbench keyframes (the format of bbmodel_inject.py) of an animation."""
    kfs = []

    def add(bone, channel, tick, value, interp="linear", ease=None):
        kf = {"bone": bone, "channel": channel, "time": round(tick * TICK, 4), "value": [round(x, 3) for x in value],
              "interpolation": interp}
        if ease:
            kf["easing"] = ease
        kfs.append(kf)

    def sparse(bone, knots, conv, channel="rotation"):
        for tick, val, ease in knots:
            add(bone, channel, tick, conv(val), "linear" if ease == LIN else "catmullrom", None if ease == LIN else ease)

    sparse("torso", anim.torso, lambda v: (-v[0], v[1], 0.0))
    if anim.head:
        sparse("head", anim.head, lambda v: (-v[0], 0.0, 0.0))
    for side, knots in anim.legs.items():
        sparse(LEG_PART[side], knots, lambda v: (v[0], 0.0, v[1]))
    if anim.lift:
        for part in ("head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"):
            if part in ("rightArm", "leftArm"):
                continue                                           # the arms get the lift in their baked position keys below
            sparse(part, anim.lift, lambda v: (0.0, v[0], 0.0), "position")
    # arms: baked per tick (the arm follows the eased torso, see slash_solver.keyframes), then simplified per axis
    for side in sorted(anim.arm_keys):
        rots, poss = [], []
        prev = None
        for tick in range(0, anim.end + 1):
            p = anim.pose(tick)
            e = nearest_euler(prev, p[side]["root"])
            prev = e
            rots.append((tick, e))
            off = p[side]["offset"]
            poss.append((tick, (off[0], off[1] + p["lift"], off[2])))
        for tick, v in _bake_vec(rots):
            add(ARM_PART[side], "rotation", tick, v)
        for tick, v in _bake_vec(poss):
            add(ARM_PART[side], "position", tick, v)
    return kfs


def bow_prop_keys(anim):
    """Preview aid: the bow of the right hand (arrow along the arm direction, limbs vertical), ignored by the exporter."""
    kfs = []
    for tick in range(0, anim.end + 1):
        p = anim.pose(tick)
        if "r" not in p:
            continue
        a, c, _ = s.prop_euler(p["r"]["dir"])
        hand = p["r"]["fist"]
        t = round(tick * TICK, 4)
        kfs.append({"bone": "bow_prop", "channel": "rotation", "time": t, "value": [round(a, 3), round(c, 3), 0.0], "interpolation": "linear"})
        kfs.append({"bone": "bow_prop", "channel": "position", "time": t,
                    "value": [round(x, 3) for x in s.sub(hand, (0.0, 20.0, 0.0))], "interpolation": "linear"})
    return kfs


# ------------------------------------------------------------------------------------------------ the .bbmodel
def _uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "minebleach/uryu_poses/" + name))


def build_bbmodel(anims):
    with open(TEMPLATE, encoding="utf-8") as f:
        model = json.load(f)
    model["name"] = "uryu_poses"
    model["model_identifier"] = "uryu_poses"
    # preview aid: the sword prop becomes a bow prop (limbs vertical, arrow along local -Z), ignored by the exporter
    group = [g for g in model["groups"] if g["name"] == "sword_prop"][0]
    group["name"] = "bow_prop"
    group["uuid"] = _uid("bow_prop")
    kept = [e for e in model["elements"] if e["name"] not in ("blade", "hilt", "tsuba")]
    template = [e for e in model["elements"] if e["name"] == "blade"][0]
    new_elems = []
    for name, lo, hi in (("bow_limbs", [-0.4, 8.0, -0.4], [0.4, 32.0, 0.4]), ("bow_arrow", [-0.2, 19.8, -12.0], [0.2, 20.2, 4.0])):
        e = copy.deepcopy(template)
        e["name"] = name
        e["from"] = lo
        e["to"] = hi
        e["origin"] = list(lo)
        e["uuid"] = _uid(name)
        new_elems.append(e)
    model["elements"] = kept + new_elems
    for node in model["outliner"]:
        if isinstance(node, dict) and node.get("children") and node["uuid"] == "a0350282-1063-f289-5c9b-e2f458c7ec53":
            node["uuid"] = group["uuid"]
            node["children"] = [e["uuid"] for e in new_elems]
    groups = {g["name"]: g["uuid"] for g in model["groups"] if isinstance(g, dict) and "name" in g}
    model["animations"] = []
    for a in anims:
        animators = {}
        for kf in keyframes(a) + bow_prop_keys(a):
            bone = kf["bone"]
            an = animators.setdefault(groups[bone], {"name": bone, "type": "bone", "rotation_global": False,
                                                    "quaternion_interpolation": False, "keyframes": []})
            x, y, z = kf["value"]
            rec = {"channel": kf["channel"], "data_points": [{"x": repr(float(x)), "y": repr(float(y)), "z": repr(float(z))}],
                   "uuid": _uid("kf/%s/%s/%s/%s" % (a.name, bone, kf["channel"], kf["time"])), "time": kf["time"], "color": -1,
                   "interpolation": kf.get("interpolation", "linear")}
            if kf.get("easing"):
                rec["easing"] = kf["easing"]
            an["keyframes"].append(rec)
        for name, gid in groups.items():
            animators.setdefault(gid, {"name": name, "type": "bone", "rotation_global": False, "quaternion_interpolation": False})
        model["animations"].append({
            "uuid": _uid("anim/" + a.name), "name": "uryu_" + a.name, "loop": a.loop, "override": False, "length": a.length,
            "snapping": 20, "selected": False, "group_name": "", "scope": 0, "anim_time_update": "", "blend_weight": "",
            "start_delay": "", "loop_delay": "", "animators": animators})
    return model


def export_all(model, anims, out_dir, write=True):
    """The repository's exporter, one JSON per animation; returns {name: json}."""
    res = {}
    for a in anims:
        name = "uryu_" + a.name
        out = exporter.convert(model, name, end_tick=a.end, description="Uryu pose %s (tools/gen_uryu_poses.py): %s" % (a.name, a.note))
        res[name] = out
        if write:
            os.makedirs(out_dir, exist_ok=True)
            with open(os.path.join(out_dir, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
                json.dump(out, f, indent=2)
                f.write("\n")
    return res


# ------------------------------------------------------------------------------------------------ checks
class CheckError(Exception):
    pass


# animations whose last key is the P1 idle (the others hold: draw) or the bankai idle (vollstandig, arms loose)
ENDS_IN_IDLE = {"idle", "release", "release_z", "seele", "blut", "licht_regen", "sternenstaub", "wings", "sprenger"}


def check(anim, anims_by_name):
    errs = []
    # 1. the torso stays under the yaw limit; no arm pivot leaves the shoulder by more than MAX_SHIFT
    for t in range(0, anim.end + 1):
        p = anim.pose(t)
        if abs(p["yaw"]) > MAX_TORSO_YAW + 1e-6:
            errs.append("%s tick %d: torso yaw %.1f above %d" % (anim.name, t, p["yaw"], MAX_TORSO_YAW))
        for side in anim.arm_keys:
            if p[side]["shift"] > MAX_SHIFT:
                errs.append("%s tick %d %s: shoulder shift %.2f above %.1f" % (anim.name, t, side, p[side]["shift"], MAX_SHIFT))
    # 2. the first key is near the idle so the layer blends in without a pop, the last key is the idle (anim_rules 6)
    idle = anims_by_name["idle"].pose(0)
    first = anim.pose(0)
    last = anim.pose(anim.end)
    if anim.name not in ("release", "release_z"):
        d0 = max(abs(a - b) for a, b in zip(first["r"]["rel"], idle["r"]["rel"]))
        if d0 > 1e-6 and anim.name not in ("draw",):
            errs.append("%s: first bow arm key is not the idle (%.3f deg)" % (anim.name, d0))
    if anim.name in ENDS_IN_IDLE:
        if abs(last["yaw"] - IDLE_TORSO[1]) > 1e-6 or abs(last["lean"] - IDLE_TORSO[0]) > 1e-6:
            errs.append("%s: last torso key is not the idle" % anim.name)
        d1 = max(abs(a - b) for a, b in zip(last["r"]["rel"], idle["r"]["rel"]))
        if d1 > 1e-6:
            errs.append("%s: last bow arm key is not the idle (%.3f deg)" % (anim.name, d1))
    return errs


def checks(anims):
    by = {a.name: a for a in anims}
    errs = []
    for a in anims:
        errs += check(a, by)
    d = by["draw"]
    p = d.pose(20)
    bow, string = p["r"]["fist"], p["l"]["fist"]
    # the string hand sits at the cheek anchor (within the reach shift) and on the arrow line of the bow hand
    if s.norm(s.sub(string, ANCHOR)) > 0.05:
        errs.append("string hand %s is not on the anchor %s" % (tuple(round(x, 2) for x in string), ANCHOR))
    arrow = s.unit(s.sub(bow, string))
    az = math.degrees(math.atan2(-arrow[0], -arrow[2]))
    el = math.degrees(math.asin(arrow[1]))
    if abs(az) > 12.0 or abs(el) > 8.0:
        errs.append("arrow line is %.1f deg off the line of fire (azimuth) / %.1f deg elevation" % (az, el))
    return errs


def describe(anims):
    lines = []
    for a in anims:
        lines.append("== %s (end tick %d): %s" % (a.name, a.end, a.note))
        for side in sorted(a.arm_keys):
            for tick, rel, extra, ease in a.arm_keys[side]:
                lean, yaw = eval_knots(a.torso, tick)
                lines.append("  %s tick %3d rel (%7.1f %7.1f %7.1f) shift %.2f  torso lean %5.1f yaw %5.1f %s" % (
                    side, tick, rel[0], rel[1], rel[2], s.norm(extra), lean, yaw, ease))
    return "\n".join(lines)


# ------------------------------------------------------------------------------------------------ contact sheets (matplotlib, no game)
def draw_pose(axs, p, mirror=False):
    """Stick figure of one pose in three views: front (x,y), side (z,y), top (x,z)."""
    lean, yaw = p["lean"], p["yaw"]
    Rt = torso_matrix(lean, yaw)

    def tp(v):
        return s.add(TORSO_PIVOT, s.mv(Rt, s.sub(v, TORSO_PIVOT)))

    lift = p["lift"]

    def up(v):
        return (v[0], v[1] + lift, v[2])

    torso_pts = [tp((x, y, z)) for x in (-4, 4) for y in (12, 24) for z in (-2, 2)]
    parts = {"torso": [up(v) for v in torso_pts]}
    hr = 0.0 if p["nod"] is None else -p["nod"]
    Rh = s.rot((hr, 0.0, 0.0))
    head_pts = [s.add(TORSO_PIVOT, s.mv(Rh, (x, y - 24.0, z))) for x in (-4, 4) for y in (24, 32) for z in (-4, 4)]
    parts["head"] = [up(v) for v in head_pts]
    segs = []
    for side in ("r", "l"):
        hip = HIP[side]
        rx, rz = p["legs"].get(side, (0.0, 0.0))
        Rl = s.rot((rx, 0.0, rz))
        foot = s.add(hip, s.mv(Rl, (0.0, -12.0, 0.0)))
        segs.append(("leg" + side, [up(hip), up(foot)]))
        if side in p:
            a = p[side]
            segs.append(("arm" + side, [up(a["pivot"]), up(a["fist"])]))
    shoulders = [up(shoulder_world("r", lean, yaw)), up(shoulder_world("l", lean, yaw))]
    segs.append(("shoulders", shoulders))
    colors = {"armr": "#d62728", "arml": "#1f77b4", "legr": "#aa6666", "legl": "#6688aa", "shoulders": "#555555"}
    views = ((0, 1, "front (x right, y up)"), (2, 1, "side (z, y)"), (0, 2, "top (x, z)"))
    for ax, (u, v, title) in zip(axs, views):
        for name, pts in parts.items():
            xs = [q[u] for q in pts]
            ys = [q[v] for q in pts]
            cx = [min(xs), max(xs), max(xs), min(xs), min(xs)]
            cy = [min(ys), min(ys), max(ys), max(ys), min(ys)]
            ax.plot(cx, cy, color="#333333", lw=0.8)
        for name, pts in segs:
            ax.plot([q[u] for q in pts], [q[v] for q in pts], color=colors[name], lw=2.2 if name.startswith("arm") else 1.4)
            if name.startswith("arm"):
                ax.plot([pts[1][u]], [pts[1][v]], "o", color=colors[name], ms=4)
        if "r" in p:
            d = p["r"]["dir"]
            hand = p["r"]["fist"]
            arrow = d
            tail = s.sub(hand, s.mul(arrow, 14.0))
            tip = s.add(hand, s.mul(arrow, 7.0))
            ax.plot([tail[u], tip[u]], [tail[v], tip[v]], color="#2ca02c", lw=1.0)
            # limbs: perpendicular to the arrow in the vertical plane of the arrow
            up_v = (0.0, 1.0, 0.0)
            limb = s.sub(up_v, s.mul(arrow, s.dot(up_v, arrow)))
            if s.norm(limb) > 1e-6:
                limb = s.unit(limb)
                a0 = s.sub(hand, s.mul(limb, 10.0))
                a1 = s.add(hand, s.mul(limb, 10.0))
                ax.plot([a0[u], a1[u]], [a0[v], a1[v]], color="#2ca02c", lw=1.6)
        ax.set_title(title, fontsize=6)
        ax.set_aspect("equal")
        ax.set_xlim(-16, 16) if u == 0 else ax.set_xlim(-18, 14)
        if v == 1:
            ax.set_ylim(-1, 40)
        else:
            ax.set_ylim(10, -18)
        ax.tick_params(labelsize=5)
        ax.grid(True, lw=0.2)
    return


def sheet(anim, path, ticks=None):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    if ticks is None:
        keyticks = set()
        for knots in [anim.torso] + list(anim.legs.values()):
            keyticks.update(k[0] for k in knots)
        for keys in anim.arm_keys.values():
            keyticks.update(k[0] for k in keys)
        ticks = sorted(keyticks)
        if len(ticks) > 8:
            step = (len(ticks) + 7) // 8
            ticks = ticks[::step] + ([ticks[-1]] if ticks[-1] not in ticks[::step] else [])
    fig, grid = plt.subplots(3, len(ticks), figsize=(1.8 * len(ticks) + 0.4, 6.2), squeeze=False)
    for col, t in enumerate(ticks):
        p = anim.pose(t)
        draw_pose([grid[0][col], grid[1][col], grid[2][col]], p)
        grid[0][col].set_title("tick %d  yaw %.0f lean %.0f" % (t, p["yaw"], p["lean"]), fontsize=6)
    fig.suptitle("uryu_%s: %s   (red = bow arm, blue = draw arm, green = bow and arrow axis)" % (anim.name, anim.note), fontsize=8)
    fig.tight_layout(rect=(0, 0, 1, 0.97))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fig.savefig(path, dpi=110)
    plt.close(fig)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sheets", action="store_true", help="write the matplotlib contact sheets to blender/renders/uryu_poses/")
    ap.add_argument("--no-write", action="store_true", help="evaluate and check only")
    ap.add_argument("--describe", action="store_true", help="print the solved arm keys")
    args = ap.parse_args(argv)
    anims = [f() for f in ANIMS]
    errs = checks(anims)
    if args.describe:
        print(describe(anims))
    if errs:
        for e in errs:
            print("CHECK FAILED: " + e, file=sys.stderr)
        return 1
    model = build_bbmodel(anims)
    if not args.no_write:
        with open(OUT_BBMODEL, "w", encoding="utf-8", newline="\n") as f:
            json.dump(model, f)
    res = export_all(model, anims, OUT_DIR, write=not args.no_write)
    for name, out in res.items():
        print("%s: %d moves, endTick %d" % (name, len(out["emote"]["moves"]), out["emote"]["endTick"]))
    if args.sheets:
        for a in anims:
            sheet(a, os.path.join(SHEET_DIR, "uryu_%s_sheet.png" % a.name))
        print("sheets in", SHEET_DIR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
