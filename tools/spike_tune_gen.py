# Generates a tune file for `gradlew runSpike -Ptune=<file>` (spike 2b in-hand transform tuning).
# usage: python -I spike_tune_gen.py <out.json> <set-name>
import json
import sys

BASE = {
    "thirdperson_righthand": {"rotation": [-10, 0, 0], "translation": [0, -3, 1.75], "scale": [0.85] * 3},
    "thirdperson_lefthand": {"rotation": [-10, 0, 0], "translation": [0, -3, 1.75], "scale": [0.85] * 3},
    "firstperson_righthand": {"rotation": [20, 0, 0], "translation": [1.13, -2.2, -0.8], "scale": [0.70] * 3},
    "firstperson_lefthand": {"rotation": [20, 0, 0], "translation": [1.13, -2.2, -0.8], "scale": [0.70] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55] * 3},
    "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.60] * 3},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.60] * 3},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0] * 3},
}


def disp(**over):
    d = json.loads(json.dumps(BASE))
    for k, (r, t, s) in over.items():
        d[k] = {"rotation": r, "translation": t, "scale": [s] * 3}
    return d


def fp(name, r, t, s, view="fp"):
    over = {"firstperson_righthand": (r, t, s), "firstperson_lefthand": (r, t, s)}
    return {"name": name, "view": view, "display": disp(**over)}


def tp(name, r, t, s, view):
    over = {"thirdperson_righthand": (r, t, s), "thirdperson_lefthand": (r, t, s)}
    return {"name": name, "view": view, "display": disp(**over)}


sets = {}
# set 1: first sweep
c = []
for rx in (-35, -55):
    for s in (0.9, 1.2):
        c.append(fp("fp_rx%d_s%.1f" % (rx, s), [rx, 180, 0], [0, 3, 0], s))
c.append(fp("fp_adr", [20, 0, 0], [1.13, -2.2, -0.8], 0.7))
c.append(fp("fp_adr_alt", [20, 180, 0], [1.13, -2.2, -0.8], 0.7))
for n, r in (("adr", [-10, 0, 0]), ("adr_alt", [-10, 180, 0])):
    c.append(tp("tp_%s_side" % n, r, [0, -3, 1.75], 0.85, "side_r"))
    c.append(tp("tp_%s_front" % n, r, [0, -3, 1.75], 0.85, "front"))
c.append({"name": "ground", "view": "ground", "display": disp()})
c.append({"name": "frame", "view": "frame", "display": disp()})
sets["s1"] = c

# set 2
c = []
for n, r, t, sc in (("A", [-45, 180, 0], [-2, 4, -1], 0.9), ("B", [-45, 180, 0], [-3, 5, -2], 0.9),
                    ("C", [-45, 180, -12], [-3, 5, -2], 0.9), ("D", [-45, 180, 12], [-3, 5, -2], 0.9),
                    ("E", [-50, 180, 0], [-2, 5, -1], 1.0), ("F", [-40, 180, 0], [-4, 6, -3], 0.8)):
    c.append(fp("fp2_" + n, r, t, sc))
for n, r, sc in (("m10", [-10, 0, 0], 0.85), ("m35", [-35, 0, 0], 0.85), ("m55", [-55, 0, 0], 0.85), ("p20", [20, 0, 0], 0.85),
                 ("m35s1", [-35, 0, 0], 1.0)):
    c.append(tp("tp2_%s_side" % n, r, [0, -3, 1.75], sc, "side_r"))
c.append({"name": "ground", "view": "ground", "display": disp()})
c.append({"name": "frame", "view": "frame", "display": disp()})
sets["s2"] = c

# set 3: candidate final values
FINAL = {
    "thirdperson_righthand": ([25, 0, 0], [0, -3, 1.75], 0.85),
    "thirdperson_lefthand": ([25, 0, 0], [0, -3, 1.75], 0.85),
    "firstperson_righthand": ([-45, 180, 0], [-3, 5, -2], 0.9),
    "firstperson_lefthand": ([-45, 180, 0], [-3, 5, -2], 0.9),
    "ground": ([0, 0, 0], [0, 2, 0], 0.65),
    "fixed": ([0, 0, -45], [-2.2, -2.2, 0], 0.9),
}
c = []
for v in ("fp", "fp_left", "side_r", "side_l", "front", "ground", "frame"):
    c.append({"name": "final_" + v, "view": v, "display": disp(**FINAL)})
sets["s3"] = c

# set 4: letter orientation check (frame view, big)
c = []
for n, r in (("E", [0, 0, 0]), ("R", [0, -90, 0]), ("B", [0, 180, 0]), ("L", [0, 90, 0]), ("T", [-90, 0, 0])):
    d = disp(fixed=(r, [0, -3.5, 0], 1.6))
    c.append({"name": "face_" + n, "view": "frame", "display": d})
sets["s4"] = c

# ---- step B (Rukia real models): runSpike -Ppreset... with -Dreiatsu.spike.item=sode_no_shirayuki (see LOG.md)
VIEWS = ("fp", "fp_left", "side_r", "side_l", "front", "ground", "frame")


def rukia_set(prefix, final, states=("sealed", "shikai"), views=VIEWS):
    out = []
    for st in states:
        for v in views:
            out.append({"name": "%s_%s_%s" % (prefix, st, v), "view": v, "state": st, "display": disp(**final)})
    return out


# r1: the spike's final transforms applied unchanged to the real models
sets["r1"] = rukia_set("r1", FINAL)

# r2: first person sweep for the real sealed model (flat of the blade vs the camera, tilt, lean)
def fp_over(r, t, sc):
    d = dict(FINAL)
    d["firstperson_righthand"] = (r, t, sc)
    d["firstperson_lefthand"] = (r, t, sc)
    return d


c = []
for n, r in (("A", [-40, 140, 0]), ("B", [-40, 220, 0]), ("C", [-40, 140, 15]), ("D", [-40, 140, -15]),
             ("E", [-40, 220, 15]), ("F", [-40, 220, -15]), ("G", [-20, 160, 0]), ("H", [-20, 200, 0]),
             ("I", [-60, 140, 0]), ("J", [-60, 220, 0])):
    c.append({"name": "r2_" + n, "view": "fp", "state": "sealed", "display": disp(**fp_over(r, [-3, 5, -2], 0.8))})
sets["r2"] = c

# r3: first person refinement (lean left, larger) + baseline of every other view for both states
c = []
FP3 = (("T1", [-40, 220, -22], [-3, 5, -2], 0.8), ("T2", [-40, 220, -22], [-6, 5, -2], 1.0),
       ("T3", [-50, 220, -22], [-6, 3, -2], 1.0), ("T4", [-40, 220, -30], [-8, 4, -4], 1.1),
       ("T5", [-30, 220, -22], [-5, 4, -3], 0.95), ("T6", [-40, 235, -22], [-6, 5, -2], 1.0))
for n, r, t, sc in FP3:
    c.append({"name": "r3_fp_" + n, "view": "fp", "state": "sealed", "display": disp(**fp_over(r, t, sc))})
base3 = fp_over([-40, 220, -22], [-6, 5, -2], 1.0)
for st in ("sealed", "shikai"):
    for v in ("fp", "side_r", "side_l", "front", "ground", "frame", "gui"):
        c.append({"name": "r3_%s_%s" % (st, v), "view": v, "state": st, "display": disp(**base3)})
sets["r3"] = c

# r4: user feedback round: bigger, more upright fp; believable tp grip at katana scale; frame and ground fitted
def over(**kw):
    d = dict(FINAL)
    for k, (r, t, sc) in kw.items():
        d[k] = (r, t, sc)
    return d


c = []
FP4 = (("a", [-35, 220, -6], [-4, 4, -3], 1.05), ("b", [-35, 220, 0], [-4, 4, -3], 1.05),
       ("c", [-30, 220, -6], [-3, 3, -3], 1.15), ("d", [-45, 220, -6], [-4, 4, -3], 1.05),
       ("e", [-35, 200, -6], [-4, 4, -3], 1.05))
for n, r, t, sc in FP4:
    d = over(firstperson_righthand=(r, t, sc), firstperson_lefthand=(r, t, sc))
    c.append({"name": "r4_fp_" + n, "view": "fp", "state": "sealed", "display": disp(**d)})
    c.append({"name": "r4_fpl_" + n, "view": "fp_left", "state": "sealed", "display": disp(**d)})
TP4 = (("P1", [25, 0, 0], [0, -3, 1.75], 1.2), ("P2", [45, 0, 0], [0, -3, 1.75], 1.2),
       ("P3", [60, 0, 0], [0, -3, 1.75], 1.2), ("P4", [45, 0, 0], [0, -2, 1.75], 1.5),
       ("P5", [60, 0, 0], [0, -2, 1.75], 1.5), ("P6", [45, 180, 0], [0, -3, 1.75], 1.2))
for n, r, t, sc in TP4:
    d = over(thirdperson_righthand=(r, t, sc), thirdperson_lefthand=(r, t, sc))
    for v in ("side_r", "front"):
        c.append({"name": "r4_tp_%s_%s" % (n, v), "view": v, "state": "sealed", "display": disp(**d)})
for n, r, t, sc in (("F1", [0, 0, -45], [-5.6, -5.6, 0], 1.6), ("F2", [0, 0, -45], [-4.5, -4.5, 0], 1.3)):
    c.append({"name": "r4_fix_" + n, "view": "frame", "state": "sealed", "display": disp(**over(fixed=(r, t, sc)))})
for n, r, t, sc in (("G2", [90, 0, 0], [0, 3, -4], 0.8), ("G3", [90, 0, 0], [0, 3, -5], 1.0)):
    c.append({"name": "r4_gnd_" + n, "view": "ground", "state": "sealed", "display": disp(**over(ground=(r, t, sc)))})
sets["r4"] = c

# r5: candidate final values for the real Rukia item (all views, both states, dark room), see LOG.md step B
RUKIA_FINAL = {
    "thirdperson_righthand": ([45, 180, 0], [0, -2, 1.75], 1.5),
    "thirdperson_lefthand": ([45, 180, 0], [0, -2, 1.75], 1.5),
    "firstperson_righthand": ([-30, 220, -6], [-3, 3, -3], 1.15),
    "firstperson_lefthand": ([-30, 220, -6], [-3, 3, -3], 1.15),
    "ground": ([90, 0, 0], [0, 3, -4.5], 0.9),
    "fixed": ([0, 0, 45], [5.6, -5.6, 0], 1.6),
}
c = []
for st in ("sealed", "shikai"):
    for v in ("fp", "fp_left", "side_r", "side_l", "front", "ground", "frame", "gui", "dark"):
        c.append({"name": "r5_%s_%s" % (st, v), "view": v, "state": st, "display": disp(**RUKIA_FINAL)})
alt = dict(RUKIA_FINAL)
alt["fixed"] = ([0, 0, -45], [-5.6, -5.6, 0], 1.6)
c.append({"name": "r5_sealed_frame_alt", "view": "frame", "state": "sealed", "display": disp(**alt)})
sets["r5"] = c

# r6: third person roll of 180 about the blade axis (ry applies before rx), so the sori bends toward the body
R6 = dict(RUKIA_FINAL)
R6["thirdperson_righthand"] = ([45, 180, 0], [0, -2, 1.75], 1.5)
R6["thirdperson_lefthand"] = ([45, 180, 0], [0, -2, 1.75], 1.5)
c = []
for st in ("sealed", "shikai"):
    for v in ("side_r", "side_l", "front"):
        c.append({"name": "r6_%s_%s" % (st, v), "view": v, "state": st, "display": disp(**R6)})
sets["r6"] = c

# r7: first person arm axis sweep (item model frame; blade +Y, flats +-X, edge +Z); needs the real item
def arm(axis, roll=0, grip=(0, -0.065, 0), anchor=(0, 0, 0), scale=1.0):
    return {"axis": list(axis), "roll": roll, "grip": list(grip), "anchor_px": list(anchor), "scale": scale}


c = []
for n, ax in (("xm", (-1, 0, 0)), ("xp", (1, 0, 0)), ("zm", (0, 0, -1)), ("zp", (0, 0, 1)),
              ("a", (-0.7, 0, -0.7)), ("b", (0.7, 0, -0.7)), ("c", (-0.7, 0, 0.7)), ("d", (0.7, 0, 0.7))):
    c.append({"name": "r7_" + n, "view": "fp", "state": "sealed", "display": disp(**RUKIA_FINAL), "arm": arm(ax)})
sets["r7"] = c

# r8: arm refinement after the axis sweep (d and zp looked right)
c = []
for n, kw in (("a", dict(axis=(0.7, 0.4, 0.7))), ("b", dict(axis=(0.7, 0.4, 0.7), scale=0.9)),
              ("c", dict(axis=(0.6, 0.5, 0.6), scale=0.9, grip=(0, -0.08, 0))),
              ("d", dict(axis=(0.5, 0.3, 0.8), scale=0.9)),
              ("e", dict(axis=(0.7, 0.4, 0.7), scale=0.9, roll=30)), ("f", dict(axis=(0.7, 0.4, 0.7), scale=0.9, roll=-30)),
              ("g", dict(axis=(0.7, 0.4, 0.7), scale=0.9, grip=(0, -0.03, 0))),
              ("h", dict(axis=(0.7, 0.2, 0.7), scale=0.8, grip=(0, -0.05, 0)))):
    ax = kw.pop("axis")
    c.append({"name": "r8_" + n, "view": "fp", "state": "sealed", "display": disp(**RUKIA_FINAL), "arm": arm(ax, **kw)})
sets["r8"] = c

# r9: final check of the arm pose in the manifest: both hands, both states, a swing, and the third person unchanged
c = []
for st in ("sealed", "shikai"):
    for v in ("fp", "fp_left"):
        c.append({"name": "r9_%s_%s" % (st, v), "view": v, "state": st, "display": disp(**RUKIA_FINAL)})
c.append({"name": "r9_sealed_swing", "view": "fp_swing", "state": "sealed", "display": disp(**RUKIA_FINAL)})
c.append({"name": "r9_shikai_swing", "view": "fp_swing", "state": "shikai", "display": disp(**RUKIA_FINAL)})
sets["r9"] = c

c = []
for t in (1, 2, 4, 5):
    c.append({"name": "r10_swing_%d" % t, "view": "fp_swing", "state": "sealed", "swing_ticks": t, "display": disp(**RUKIA_FINAL)})
sets["r10"] = c

# b2: step B2 (Gate C scales: first person 1.20, third person 1.00); runs for either item, all three states
B2 = {
    "thirdperson_righthand": ([45, 180, 0], [0, -2, 1.75], 1.5),
    "thirdperson_lefthand": ([45, 180, 0], [0, -2, 1.75], 1.5),
    "firstperson_righthand": ([-8.0, -1.0, 6.9], [2.14, -0.29, -5], 2.4),  # B4 step 1 (B3: [-90, 78.1, 78.4])
    "firstperson_lefthand": ([-8.0, -1.0, 6.9], [2.14, -0.29, -5], 2.4),
    "ground": ([90, 0, 0], [0, 3, -4.5], 0.9),
    "fixed": ([0, 0, 45], [5.6, -5.6, 0], 1.6),
}
c = []
for st in ("sealed", "shikai", "bankai"):
    for v in ("fp", "fp_left", "side_r", "side_l", "front", "ground", "frame", "dark"):
        c.append({"name": "b2_%s_%s" % (st, v), "view": v, "state": st, "display": disp(**B2)})
sets["b2"] = c

# b3: dark third person (ribbon and tsuba glow), per state, plus Byakuya shikai fp with the per-state arm
c = []
for st in ("sealed", "shikai", "bankai"):
    c.append({"name": "b3_%s_dark_tp" % st, "view": "dark_tp", "state": st, "display": disp(**B2)})
for st in ("shikai",):
    for v in ("fp", "fp_left"):
        c.append({"name": "b3_%s_%s" % (st, v), "view": v, "state": st, "display": disp(**B2)})
sets["b3"] = c

# d1: final B3 part 1 capture: swing, iron sword reference
c = []
c.append({"name": "d1_ref_iron", "view": "fp_iron", "state": "sealed", "display": disp(**B2)})
c.append({"name": "d1_ref_iron_left", "view": "fp_iron_left", "state": "sealed", "display": disp(**B2)})
for st in ("sealed", "shikai"):
    c.append({"name": "d1_%s_swing" % st, "view": "fp_swing", "state": st, "display": disp(**B2)})
sets["d1"] = c
sets["b4"] = sets["b2"] + sets["b3"] + sets["d1"]


def fpd(r, t, sc):
    d = dict(B2)
    d["firstperson_righthand"] = (r, t, sc)
    d["firstperson_lefthand"] = (r, t, sc)
    return d


import math

# Hand frame of first person (x right, y up, z toward the camera). The vanilla empty-hand arm puts the fist centre at
# F = (0.134, -0.018, -0.316) blocks = (2.14, -0.29, -5.06) display units (computed from HeldItemRenderer#renderArmHoldingItem).
F16 = (2.14, -0.29, -5.06)


def _norm(v):
    l = math.sqrt(sum(x * x for x in v))
    return [x / l for x in v]


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def euler_xyz(b, n):
    """Display rotation [rx, ry, rz] degrees (JOML rotationXYZ = Rx Ry Rz) taking model X to the flat normal n (made
    perpendicular to b), model Y to the blade axis b, model Z to n x b."""
    b = _norm(b)
    d = sum(n[i] * b[i] for i in range(3))
    n = _norm([n[i] - d * b[i] for i in range(3)])
    z = _cross(n, b)
    R = [[n[0], b[0], z[0]], [n[1], b[1], z[1]], [n[2], b[2], z[2]]]
    sb = max(-1.0, min(1.0, R[0][2]))
    beta = math.asin(sb)
    alpha = math.atan2(-R[1][2], R[2][2])
    gamma = math.atan2(-R[0][1], R[0][0])
    return [round(math.degrees(x), 1) for x in (alpha, beta, gamma)]


def fp_pose(b, n, scale, shift=(0, 0, 0)):
    """(rotation, translation, scale) for fpd(): pivot (model origin) at the fist F, optional extra shift in display units."""
    return euler_xyz(b, n), [round(F16[i] + shift[i], 2) for i in range(3)], scale


def sealed_view(name, view, state, **fpkw):
    return {"name": name, "view": view, "state": state, "display": disp(**fpd(*fp_pose(**fpkw)))}


sets["f0"] = [{"name": "f0_sealed_fp", "view": "fp", "state": "sealed", "display": disp(**B2)},
              {"name": "f0_shikai_fp", "view": "fp", "state": "shikai", "display": disp(**B2)}]

# g1: sealed held by the saya, blade axis b, flat normal n (toward the camera = +z)
c = []
for nm, b, n in (("A", (-0.30, 0.93, -0.20), (0, 0, 1)), ("B", (-0.30, 0.93, -0.20), (0, 0, -1)),
                 ("C", (-0.45, 0.88, -0.15), (0, 0, 1)), ("D", (-0.20, 0.96, -0.15), (0, 0, 1))):
    c.append(sealed_view("g1_" + nm, "fp", "sealed", b=b, n=n, scale=2.4))
sets["g1"] = c

# g2: tilt variants of g1_B (flat toward the camera, edge to the left), plus the vanilla iron sword for reference
c = [{"name": "g2_iron", "view": "fp_iron", "state": "sealed", "display": disp(**B2)}]
for nm, b, n in (("B2", (-0.20, 0.95, -0.20), (0, 0, -1)), ("B3", (-0.20, 0.90, -0.40), (0, 0, -1)),
                 ("B4", (-0.30, 0.93, -0.20), (0.15, 0, -1)), ("B5", (-0.15, 0.96, -0.25), (0, 0, -1))):
    c.append(sealed_view("g2_" + nm, "fp", "sealed", b=b, n=n, scale=2.4))
c.append(sealed_view("g2_B2_shikai", "fp", "shikai", b=(-0.20, 0.95, -0.20), n=(0, 0, -1), scale=2.4))
c.append(sealed_view("g2_B2_left", "fp_left", "sealed", b=(-0.20, 0.95, -0.20), n=(0, 0, -1), scale=2.4))
sets["g2"] = c

# g3: draw frames at fixed progress (debug override), first person and third person
c = []
for pr in (0.0, 0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1.0):
    c.append({"name": "g3_fp_%02d" % round(pr * 100), "view": "fp", "state": "sealed", "draw_p": pr, "display": disp(**B2)})
for pr in (0.0, 0.3, 0.6, 0.9):
    c.append({"name": "g3_tp_%02d" % round(pr * 100), "view": "tp_front", "state": "sealed", "draw_p": pr, "display": disp(**B2)})
sets["g3"] = c

# g4: third person on the armor stands (side right / front), draw frames
c = []
for v in ("side_r", "front"):
    for pr in (0.0, 0.3, 0.6, 0.9):
        c.append({"name": "g4_%s_%02d" % (v, round(pr * 100)), "view": v, "state": "sealed", "draw_p": pr, "display": disp(**B2)})
sets["g4"] = c

# g5: real time draw (state switch on the server, synced attachment drives the DrawTracker) and sheathe, first person
sets["g5"] = [
    {"name": "g5_draw", "view": "fp", "state": "sealed", "display": disp(**B2), "draw_seq": {"to": "shikai", "frames": 12, "gap": 1}},
    {"name": "g5_sheathe", "view": "fp", "state": "shikai", "display": disp(**B2), "draw_seq": {"to": "sealed", "frames": 12, "gap": 1}},
]

# g6: all three states still, fp right, both items (state shows the final display)
sets["g6"] = [{"name": "g6_%s_%s" % (st, v), "view": v, "state": st, "display": disp(**B2)}
              for st in ("sealed", "shikai", "bankai") for v in ("fp", "fp_left", "tp_front", "tp_back")]

# ---- B4 step 1: edge toward the camera. roll r = angle of the flat normal from the camera axis, rotated about the blade
# axis b toward the edge (r = 0: flat to the camera as in B3, r = 90: edge on). sgn picks which way round.
N0 = (0.0, 0.0, -1.0)  # B3: the flat face normal (model X) toward the camera


def roll_n(b, r, sgn=1.0):
    b = _norm(b)
    d = sum(N0[i] * b[i] for i in range(3))
    n0 = _norm([N0[i] - d * b[i] for i in range(3)])
    e = _cross(n0, b)
    a = math.radians(r)
    return [math.cos(a) * n0[i] + sgn * math.sin(a) * e[i] for i in range(3)]


def roll_view(name, view, state, b, r, sgn, scale=2.4, shift=(0, 0, 0), **extra):
    d = {"name": name, "view": view, "state": state,
         "display": disp(**fpd(*fp_pose(b=b, n=roll_n(b, r, sgn), scale=scale, shift=shift)))}
    d.update(extra)
    return d


B_NEW = (-0.15, 0.97, -0.15)  # about 12 degrees from vertical
# h0: which sign puts the edge toward the camera (flat reference, both signs at 65 degrees, shikai and sealed)
c = []
for st in ("shikai", "sealed"):
    c.append(roll_view("h0_%s_r0" % st, "fp", st, B_NEW, 0, 1))
    c.append(roll_view("h0_%s_r65p" % st, "fp", st, B_NEW, 65, 1))
    c.append(roll_view("h0_%s_r65m" % st, "fp", st, B_NEW, 65, -1))
    c.append(roll_view("h0_%s_r90p" % st, "fp", st, B_NEW, 90, 1))
    c.append(roll_view("h0_%s_r90m" % st, "fp", st, B_NEW, 90, -1))
sets["h0"] = c

# h1: roll candidates (sgn +1 = edge toward the camera; the camera ray hits the blade from the left, so the apparent roll
# against the ray is smaller than the hand-frame roll: see LOG "B4 step 1")
c = []
for r in (78, 86, 94, 102):
    c.append(roll_view("h1_shikai_r%d" % r, "fp", "shikai", B_NEW, r, 1))
    c.append(roll_view("h1_sealed_r%d" % r, "fp", "sealed", B_NEW, r, 1))
sets["h1"] = c

# h2: roll candidates with the final blade axis; hand-frame roll for an apparent roll (against the camera ray to the fist
# at (0.694, -0.538, -1.03) blocks) of about 55 / 62 / 68 / 75 degrees = 82 / 90 / 96 / 104
B_FIN = (-0.12, 0.98, -0.14)  # 11.5 degrees from vertical
c = []
for r in (82, 90, 96, 104):
    for st in ("shikai", "sealed"):
        c.append(roll_view("h2_%s_r%d" % (st, r), "fp", st, B_FIN, r, 1))
sets["h2"] = c

# h3: the SHIPPED display json of the item under test (item given by -Pitem), all three states, both hands + third person
import os
def shipped(item):
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "resources", "assets",
                     "reiatsu_test", "models", "item", item + "_display.json")
    return json.load(open(p))["display"]


def shipped_set(item):
    d = shipped(item)
    return [{"name": "h3_%s_%s" % (st, v), "view": v, "state": st, "display": d}
            for st in ("sealed", "shikai", "bankai") for v in ("fp", "fp_left", "tp_front")]


sets["h3r"] = shipped_set("sode_no_shirayuki")
sets["h3b"] = shipped_set("senbonzakura")

# ---- B4 step 2: scabbard in the left hand, draw driven by SEALED -> BASE, hip scabbard in third person.
# Everything uses the shipped display json of the item under test; `cfg` overrides keys of the manifest "draw" block
# (stow / hip) so the stow and hip poses can be tuned without editing the manifest.
def k_view(name, view, state, item, p=None, cfg=None, **extra):
    d = {"name": name, "view": view, "state": state, "display": shipped(item)}
    if p is not None:
        d["draw_p"] = p
    if cfg:
        d["draw_cfg"] = cfg
    d.update(extra)
    return d


def k_sets(item, cfg=None, tag="k"):
    out = {}
    out[tag + "1"] = [k_view("%s1_%s_fp" % (tag, st), "fp", st, item, cfg=cfg) for st in ("sealed", "base", "shikai", "bankai")]
    out[tag + "2"] = [k_view("%s2_draw_%02d" % (tag, round(pr * 100)), "fp", "base", item, p=pr, cfg=cfg)
                      for pr in (0.0, 0.1, 0.2, 0.3, 0.45, 0.55, 0.7, 0.85, 1.0)]
    out[tag + "3"] = [k_view("%s3_%s_%s" % (tag, st, v), v, st, item, cfg=cfg)
                      for st in ("sealed", "base") for v in ("side_r", "side_l", "front", "back", "tp_front", "tp_back")]
    out[tag + "4"] = [k_view("%s4_p%02d_%s" % (tag, round(pr * 100), v), v, "sealed", item, p=pr, cfg=cfg)
                      for pr in (0.15, 0.4, 0.8) for v in ("side_r", "front")]
    out[tag + "5"] = [k_view("%s5_fpleft_%s" % (tag, st), "fp_left", st, item, cfg=cfg) for st in ("sealed", "base", "shikai")]
    return out


for _it, _tag in (("sode_no_shirayuki", "k"), ("senbonzakura", "q")):
    sets.update(k_sets(_it, tag=_tag))

# stow pose candidates (hand frame: rot about x, y, z in degrees, move in blocks, pull = left hand share of the travel)
def stow_cfg(rot, move, slide_end=0.55, pull=0.6, retract=0.3):
    return {"stow": {"rot": list(rot), "move": list(move), "slide_end": slide_end, "pull": pull, "retract": retract}}


STOWS = {
    "A": stow_cfg((0, 0, 104), (-0.4, 0.24, 0), 0.55, 0.55, 0.15),
    "B": stow_cfg((0, 0, 100), (-0.45, 0.30, 0), 0.5, 0.55, 0.15),
}
for _it, _tag in (("sode_no_shirayuki", "ks"), ("senbonzakura", "qs")):
    sets[_tag] = [k_view("%s_%s_%s" % (_tag, n, lab), "fp", "base" if pr is not None else "sealed", _it, p=pr, cfg=cfg)
                  for n, cfg in STOWS.items() for lab, pr in (("sealed", None), ("p25", 0.25), ("p50", 0.5), ("p75", 0.75))]

# close third person shots of the stands (hip scabbard): cam = [x, feet y, z, yaw, pitch]
def hip_sets(item, tag):
    out = []
    for st, dx, bx in (("sealed", 0, 0), ("base", 56, 4), ("shikai", 4, 8)):
        for v, x in (("side_r", 20.5 + dx), ("side_l", 30.5 + dx), ("front", 40.5 + dx), ("back", 110.5 + bx)):
            out.append(k_view("%s_%s_%s" % (tag, st, v), v, st, item, cam=[x, -60, 2.35, 0, 26]))
    return out


sets["kh"] = hip_sets("sode_no_shirayuki", "kh")
sets["qh"] = hip_sets("senbonzakura", "qh")

HIPS = {
    "H1": {"hip": {"pos_px": [8.0, 10.5, -0.5], "dir": [0, 0.5, 0.87], "scale": 0.9}},
    "H2": {"hip": {"pos_px": [8.6, 9.5, 0.5], "dir": [0, 0.6, 0.8], "scale": 1.0}},
}
sets["kt"] = [k_view("kt_%s_%s" % (n, v), v, "sealed", "sode_no_shirayuki", cfg=cfg, cam=[x, -60, 2.35, 0, 26])
              for n, cfg in HIPS.items() for v, x in (("side_l", 30.5), ("side_r", 20.5), ("front", 40.5), ("back", 110.5))]

# ---- B4 polish: hot pose sweeps. A candidate with "pose" writes <run>/pose_override.json and needs no resource reload, so a
# whole sweep runs in ONE game launch (keys: lift [x,y,z] sword lift, arm [x,y,z] fist offset from the vanilla fist, roll_stow,
# roll_held; hand frame blocks, y negative = lower on the screen).
def pv(name, state, pose, view="fp", p=None):
    c = {"name": name, "view": view, "state": state, "pose": pose}
    if p is not None:
        c["draw_p"] = p
    return c


def sweep_held(tag, lifts, arms, state="shikai"):
    return [pv("%s_l%02d_a%02d" % (tag, round(l * 100), round(-a * 100)), state, {"lift": [0, l, 0], "arm": [0, a, 0], "roll_stow": 80, "roll_held": 0})
            for l in lifts for a in arms]


def sweep_draw(tag, pose, state="base", frames=(0.0, 0.1, 0.2, 0.3, 0.45, 0.55, 0.7, 0.85, 1.0), view="fp"):
    return [pv("%s_%02d" % (tag, round(p * 100)), state, pose, view, p) for p in frames]


sets["sw1"] = sweep_held("h", (0.0, 0.06, 0.12), (0.0, -0.10, -0.20, -0.30))

POSEA = {"lift": [0, 0.06, 0], "arm": [0, -0.20, 0], "roll_stow": 80, "roll_held": 0}
POSEB = {"lift": [0, 0.08, 0], "arm": [0, -0.15, 0], "roll_stow": 80, "roll_held": 0}
sets["sw2"] = sweep_draw("a", POSEA, frames=(0.0, 0.3, 0.55, 0.7, 0.85, 1.0)) + sweep_draw("b", POSEB, frames=(0.0, 0.3, 0.55, 0.7, 0.85, 1.0)) +     [pv("al_shikai", "shikai", POSEA, "fp_left"), pv("bl_shikai", "shikai", POSEB, "fp_left"),
     pv("al_base", "base", POSEA, "fp_left"), pv("al_sealed", "sealed", POSEA, "fp_left")]

if __name__ == "__main__":
    out, name = sys.argv[1], sys.argv[2]
    json.dump({"candidates": sets[name]}, open(out, "w"), indent=1)
    print(out, len(sets[name]), "candidates")
