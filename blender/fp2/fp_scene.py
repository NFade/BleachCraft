"""Pose spec -> list of drawables in camera space (numpy, no Blender needed). See fp_math for the conventions."""
import numpy as np

import fp_math as fm
from fp_math import T, Rxyz, pt, dirv

# stow pose of both sealed models (manifest draw.stow, hand frame numbers) and the held lift / arm
STOW_ROT = (0.0, 0.0, 104.0)
STOW_MOVE = (-0.4, 0.24, 0.0)


def stow_model(disp):
    """HandMath.stowToModel: the stow pose as a rigid transform of the item model space (right hand)."""
    d = Rxyz(*disp["rotation"])
    qh = fm.Rz(STOW_ROT[2]) @ fm.Ry(STOW_ROT[1]) @ fm.Rx(STOW_ROT[0])
    qm = np.linalg.inv(d) @ qh @ d
    move = np.linalg.inv(d)[:3, :3] @ np.array(STOW_MOVE) / disp["scale"][0]
    o = np.array([0.5, 0.5, 0.5])
    qo = qm[:3, :3] @ o
    t = o - qo + move
    m = qm.copy()
    m[:3, 3] = t
    return m


def pose_matrix(pos, rot, pivot):
    """FpMotion.push: translate(pos) translate(pv) rotateXYZ translate(-pv)."""
    return T(pos) @ T(pivot) @ Rxyz(*rot) @ T(-np.asarray(pivot, float))


def build(spec):
    char = spec.get("char", "rukia")
    left_handed = spec.get("left_handed", False)
    slim = spec.get("slim", False)
    i = -1.0 if left_handed else 1.0
    disp = fm.load_display(char)
    m2h = fm.model_to_hand(disp, left_handed)
    equip = spec.get("equip", 1.0)
    H0 = fm.hand_origin(left_handed, equip)
    lift = np.array(spec.get("lift", fm.LIFT)) * np.array([i, 1, 1])
    pos = spec.get("pose", {}).get("pos", [0, 0, 0])
    rot = spec.get("pose", {}).get("rot", [0, 0, 0])
    if left_handed:
        pos = [-pos[0], pos[1], pos[2]]
        rot = [rot[0], -rot[1], -rot[2]]
    grip_h = pt(m2h, [0.5, 0.5, 0.5]) + lift
    ps = spec.get("pivot_s", 0.0)
    along = pt(m2h, [0.5, 0.5 + ps, 0.5]) - pt(m2h, [0.5, 0.5, 0.5])
    pivot = grip_h + along
    P = pose_matrix(pos, rot, pivot)
    sword_chain = H0 @ P @ T(lift) @ m2h
    state = spec.get("state", "drawn")
    rig = np.eye(4)
    if state == "sealed":
        rig = stow_model(disp)
    sword_m = sword_chain @ rig
    out = {"items": []}
    out["items"].append({"kind": "sword", "mesh": "sheathed" if state == "sealed" else "drawn", "m": sword_m})
    if state == "sealed":
        out["items"].append({"kind": "saya", "m": sword_m})
    out["sword_m"] = sword_m
    # right fist of the vanilla pose: vanilla fist + held arm offset, as a fixed model space offset from the grip (FirstPersonHand.gripOffset)
    vfist = pt(H0 @ fm.arm_vanilla_chain(left_handed), fm.arm_geom(left_handed, slim)[2])
    armHeld = vfist + fm.HELD_ARM * np.array([i, 1, 1])
    heldGrip = pt(H0 @ T(lift) @ m2h, [0.5, 0.5, 0.5])
    cm = np.linalg.inv(m2h)[:3, :3] @ (armHeld - heldGrip)
    out["cm"] = cm
    arms = spec.get("arms", {})
    sh_r = fm.shoulder(left_handed)
    sh_l = fm.shoulder(not left_handed)
    mode = arms.get("mode", "ik")
    R = arms.get("right", {})
    if R.get("on", True):
        s = R.get("s", float(cm[1]))
        off = np.array(R.get("off", [0, 0, 0]), float)
        base = np.array([0.5 + (float(cm[0]) if R.get("follow", False) else 0.0), 0.5 + s, 0.5 + (float(cm[2]) if R.get("follow", False) else 0.0)])
        fist = pt(sword_m, base + off)
        bdir = dirv(sword_m, [0, 1, 0])
        if mode == "vanilla":
            Mv = H0 @ T(fm.HELD_ARM * np.array([i, 1, 1])) @ fm.arm_vanilla_chain(left_handed)
            out["items"].append({"kind": "arm", "left": left_handed, "m": Mv})
        else:
            Ma = fm.arm_place(left_handed, fist, sh_r, bdir if R.get("square", True) else None, R.get("roll", 0.0), slim)
            out["items"].append({"kind": "arm", "left": left_handed, "m": Ma})
        out["fist_r"] = fist
    L = arms.get("left", {})
    if L.get("on", False):
        s = L.get("s", -0.115)
        off = np.array(L.get("off", [0, 0, 0]), float)
        fist = pt(sword_m, np.array([0.5, 0.5 + s, 0.5]) + off)
        bdir = dirv(sword_m, [0, 1, 0])
        Ma = fm.arm_place(not left_handed, fist, sh_l, bdir if L.get("square", True) else None, L.get("roll", 0.0), slim)
        out["items"].append({"kind": "arm", "left": not left_handed, "m": Ma})
        out["fist_l"] = fist
    return out
