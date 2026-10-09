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

if __name__ == "__main__":
    out, name = sys.argv[1], sys.argv[2]
    json.dump({"candidates": sets[name]}, open(out, "w"), indent=1)
    print(out, len(sets[name]), "candidates")
