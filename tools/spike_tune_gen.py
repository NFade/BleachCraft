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

if __name__ == "__main__":
    out, name = sys.argv[1], sys.argv[2]
    json.dump({"candidates": sets[name]}, open(out, "w"), indent=1)
    print(out, len(sets[name]), "candidates")
