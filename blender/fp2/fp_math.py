"""First person rig maths (numpy): an exact replica of the Minecraft 1.21.1 first person hand chain used by the mod, so key poses can be
authored in Blender and read back as the numbers of pose_override.json / core/combat.

Conventions = Minecraft camera space = Blender camera local space: +X right, +Y up, +Z towards the viewer (forward is -Z).
Rotations: Rxyz(a,b,c) = Rx(a) Ry(b) Rz(c) (JOML rotationXYZ, vanilla display transform). Lengths in blocks.
"""
import json
import math
import os

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test")
EXPORT = os.path.join(ROOT, "blender", "export")


def T(x, y=None, z=None):
    if y is None:
        x, y, z = x
    m = np.eye(4)
    m[:3, 3] = [x, y, z]
    return m


def S(k):
    m = np.eye(4)
    m[0, 0] = m[1, 1] = m[2, 2] = k
    return m


def Rx(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[1, 1] = c
    m[1, 2] = -s
    m[2, 1] = s
    m[2, 2] = c
    return m


def Ry(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0] = c
    m[0, 2] = s
    m[2, 0] = -s
    m[2, 2] = c
    return m


def Rz(d):
    a = math.radians(d)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0] = c
    m[0, 1] = -s
    m[1, 0] = s
    m[1, 1] = c
    return m


def Rxyz(x, y, z):
    return Rx(x) @ Ry(y) @ Rz(z)


def axis_angle(axis, deg):
    axis = np.asarray(axis, float)
    n = np.linalg.norm(axis)
    if n < 1e-9:
        return np.eye(4)
    x, y, z = axis / n
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    C = 1 - c
    m = np.eye(4)
    m[:3, :3] = [[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                 [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                 [z * x * C - y * s, z * y * C + x * s, c + z * z * C]]
    return m


def rot_to(a, b):
    """Minimal rotation (4x4) taking unit vector a to unit vector b."""
    a = np.asarray(a, float) / np.linalg.norm(a)
    b = np.asarray(b, float) / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if c < -0.999999:
        # opposite: any perpendicular axis
        p = np.cross(a, [1, 0, 0])
        if np.linalg.norm(p) < 1e-6:
            p = np.cross(a, [0, 1, 0])
        return axis_angle(p, 180)
    k = 1.0 / (1.0 + c)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    m = np.eye(4)
    m[:3, :3] = np.eye(3) + vx + vx @ vx * k
    return m


def pt(m, p):
    return (m @ np.array([p[0], p[1], p[2], 1.0]))[:3]


def dirv(m, d):
    return m[:3, :3] @ np.asarray(d, float)


# ---------------------------------------------------------------- display transform of the item model

def load_display(char):
    name = "sode_no_shirayuki" if char == "rukia" else "senbonzakura"
    d = json.load(open(os.path.join(RES, "models", "item", name + "_display.json")))["display"]["firstperson_righthand"]
    return d


def model_to_hand(disp, left=False):
    """Item model space (0..1 block) to the hand frame: HandMath.modelToHand."""
    i = -1.0 if left else 1.0
    rx, ry, rz = disp["rotation"]
    if left:
        ry, rz = -ry, -rz
    t = [v / 16.0 for v in disp["translation"]]
    s = disp["scale"]
    return T(i * t[0], t[1], t[2]) @ Rxyz(rx, ry, rz) @ np.diag([s[0], s[1], s[2], 1.0]) @ T(-0.5, -0.5, -0.5)


# the held sword is lifted in the hand frame (manifest draw.held.lift) and the fist sits (0,-0.12,0) lower (held.arm)
LIFT = np.array([0.0, 0.06, 0.0])
HELD_ARM = np.array([0.0, -0.12, 0.0])


def hand_origin(left=False, equip=1.0):
    i = -1.0 if left else 1.0
    return T(i * 0.56, -0.52 - 0.6 * (1.0 - equip), -0.72)


# ---------------------------------------------------------------- player arm
WIDE = {"pivot": (-5, 2, 0), "box": (-3, -2, -2, 4, 12, 4)}
SLIM = {"pivot": (-5, 2.5, 0), "box": (-2, -2, -2, 3, 12, 4)}


def arm_vanilla_chain(left=False):
    """HeldItemRenderer arm chain as the mod draws it (FirstPersonHand.renderVanillaArm), in the hand frame of the held item."""
    f = -1.0 if left else 1.0
    return (T(f * 0.08, -0.08, 0) @ Ry(f * 45) @ T(f * -1.0, 3.6, 3.5) @ Rz(f * 120) @ Rx(200) @ Ry(f * -135) @ T(f * 5.6, 0, 0))


def arm_geom(left=False, slim=False):
    """Arm cuboid (min, max corners in blocks, arm model space after the pivot translate) and the fist centre point."""
    g = SLIM if slim else WIDE
    px, py, pz = g["pivot"]
    bx, by, bz, bw, bh, bd = g["box"]
    if left:
        px = -px
        # left arm cuboid x: (-1, -2, -2, 3|4 ...) mirrored
        bx = -(bx + bw)
    lo = np.array([px + bx, py + by, pz + bz]) / 16.0
    hi = lo + np.array([bw, bh, bd]) / 16.0
    fist = np.array([(lo[0] + hi[0]) / 2, hi[1] - 2.0 / 16.0 - (0.0 if not slim else 0.0), 0.0])
    return lo, hi, fist


# ---------------------------------------------------------------- arm inverse kinematics (the new solver)
# shoulders in camera space: the point the arm box points to (behind and below the camera); the rest direction of the vanilla arm is kept
VANILLA_DIR = None  # filled below


def shoulder(left=False, scale=1.0):
    d = arm_dir_vanilla(left)
    rest = hand_origin(left) @ np.array([0, 0, 0, 1.0])
    fist = pt(hand_origin(left) @ arm_vanilla_chain(left), arm_geom(left)[2])
    return fist + d * 1.0 * scale


def arm_dir_vanilla(left=False):
    return dirv(arm_vanilla_chain(left), [0, -1, 0])


def arm_place(left, fist, shoulder_pt, blade_dir=None, roll=0.0, slim=False):
    """Model matrix (arm model space -> camera space) that puts the fist centre at `fist` and points the arm axis to `shoulder_pt`."""
    lo, hi, fm = arm_geom(left, slim)
    RV = arm_vanilla_chain(left).copy()
    RV[:3, 3] = 0
    a_v = RV[:3, :3] @ np.array([0, -1.0, 0])
    d = np.asarray(shoulder_pt, float) - np.asarray(fist, float)
    d = d / np.linalg.norm(d)
    Q = rot_to(a_v, d)
    R = Q @ RV
    if blade_dir is not None:
        # square the cube to the blade: the model x (or z) axis lines up with the blade projected on the plane across the arm
        b = np.asarray(blade_dir, float)
        bp = b - d * np.dot(b, d)
        if np.linalg.norm(bp) > 1e-3:
            bp /= np.linalg.norm(bp)
            best = None
            for ax in ([1, 0, 0], [0, 0, 1]):
                for sg in (1, -1):
                    v = R[:3, :3] @ (np.array(ax) * sg)
                    ang = math.degrees(math.atan2(np.dot(np.cross(v, bp), d), np.dot(v, bp)))
                    if best is None or abs(ang) < abs(best):
                        best = ang
            R = axis_angle(d, best) @ R
    if roll:
        R = axis_angle(d, roll) @ R
    M = T(fist) @ R @ T(-fm[0], -fm[1], -fm[2])
    return M
