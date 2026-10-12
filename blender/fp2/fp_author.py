"""Authoring helpers: camera space targets (grip position, blade direction, roll) -> the numbers of FpPose (pos, rot XYZ about the pivot)."""
import math

import numpy as np

import fp_math as fm
import fp_scene
from fp_math import T, pt, dirv


def euler_xyz(R):
    """Angles (degrees) a, b, c with R = Rx(a) Ry(b) Rz(c) (JOML rotationXYZ)."""
    b = math.asin(max(-1.0, min(1.0, R[0, 2])))
    a = math.atan2(-R[1, 2], R[2, 2])
    c = math.atan2(-R[0, 1], R[0, 0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def rest_info(char, left_handed=False, pivot_s=0.0):
    disp = fm.load_display(char)
    m2h = fm.model_to_hand(disp, left_handed)
    i = -1.0 if left_handed else 1.0
    lift = np.array(fm.LIFT) * np.array([i, 1, 1])
    C = T(lift) @ m2h  # model -> hand frame, no pose
    Rrest = C[:3, :3] / disp["scale"][0]
    grip_h = pt(C, [0.5, 0.5, 0.5])
    along = pt(m2h, [0.5, 0.5 + pivot_s, 0.5]) - pt(m2h, [0.5, 0.5, 0.5])
    return C, Rrest, grip_h, grip_h + along


def solve(char, grip_cam, blade_dir, roll_deg=0.0, pivot_s=0.0, grip_s=0.0, left_handed=False):
    """grip_cam: camera space position of the model point (0.5, 0.5+grip_s, 0.5); blade_dir: camera space unit vector of the blade (model +Y).
    Returns dict(pos=[3], rot=[3]) in the right-hander layout of FpPose."""
    C, Rrest, grip_h, pv = rest_info(char, left_handed, pivot_s)
    H0 = fm.hand_origin(left_handed)
    D0 = Rrest @ np.array([0, 1.0, 0])
    D = np.asarray(blade_dir, float)
    D = D / np.linalg.norm(D)
    Q = fm.rot_to(D0, D)[:3, :3]
    Rroll = fm.axis_angle(D, roll_deg)[:3, :3]
    Rdes = Rroll @ Q @ Rrest
    Rp = Rdes @ np.linalg.inv(Rrest)
    # the model point g (0.5, 0.5+grip_s, 0.5) in the hand frame at rest
    g_h = pt(C, [0.5, 0.5 + grip_s, 0.5])
    G_H = np.asarray(grip_cam, float) - H0[:3, 3]
    pos = G_H - (pv + Rp @ (g_h - pv))
    rot = euler_xyz(Rp)
    if left_handed:
        pos = np.array([-pos[0], pos[1], pos[2]])
        rot = [rot[0], -rot[1], -rot[2]]
    return {"pos": [float(x) for x in pos], "rot": [float(x) for x in rot]}


def unit(*v):
    v = np.array(v, float)
    return v / np.linalg.norm(v)


def ndc_to_cam(nx, ny, depth, fov=70.0, aspect=16 / 9):
    t = math.tan(math.radians(fov / 2))
    return np.array([nx * t * aspect * depth, ny * t * depth, -depth])


def project(p, fov=70.0, aspect=16 / 9):
    t = math.tan(math.radians(fov / 2))
    return (p[0] / (-p[2]) / (t * aspect), p[1] / (-p[2]) / t)


def report(spec):
    """Screen positions (ndc) of the key points of a spec: grip, tsuba, tip, fists."""
    scn = fp_scene.build(spec)
    sm = scn["sword_m"]
    out = {}
    out["grip"] = project(pt(sm, [0.5, 0.5, 0.5]))
    out["tsuba"] = project(pt(sm, [0.5, 0.5 + 0.06, 0.5]))
    out["tip"] = project(pt(sm, [0.5, 0.5 + 0.79, 0.5]))
    out["pommel"] = project(pt(sm, [0.5, 0.5 - 0.19, 0.5]))
    if "fist_r" in scn:
        out["fist_r"] = project(scn["fist_r"])
    if "fist_l" in scn:
        out["fist_l"] = project(scn["fist_l"])
    out["tip_depth"] = float(-pt(sm, [0.5, 0.5 + 0.79, 0.5])[2])
    return {k: ([round(float(a), 2) for a in v] if isinstance(v, tuple) else round(v, 2)) for k, v in out.items()}
