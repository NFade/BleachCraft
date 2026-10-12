"""The key poses of the fp2 first person rig, authored in camera space terms (grip position as screen position + depth, blade direction, roll).
Run: python author_poses.py [render]  -> poses_fp2.json (numbers for FpPose / pose_override.json), optional Blender contact sheets."""
import json
import os
import sys

import numpy as np

import fp_author as fa
from author_run import HERE, ndc_to_cam, overlay, render, spec_for, unit

# (grip ndc x, y), depth in blocks, blade direction (camera space, -z forward), roll about the blade (degrees), right fist s, left fist s
STANCE = dict(state="drawn", g=(0.22, -0.45), d=1.05, b=(0.15, 0.72, -0.68), roll=55, rs=-0.03, ls=-0.134)
GUARD = dict(state="drawn", g=(0.20, -0.35), d=1.00, b=(-0.65, 0.55, -0.52), roll=40, rs=-0.03, ls=-0.134)
SEALED_IDLE = dict(state="sealed", g=(0.30, -0.50), d=0.95, b=(-0.45, 0.20, -0.87), roll=0, rs=-0.06, ls=0.12)
SEALED_GUARD = dict(state="sealed", g=(0.25, -0.35), d=1.00, b=(-0.75, 0.30, -0.58), roll=0, rs=-0.06, ls=0.12)


def strike(name, wind, hit, follow):
    return {name + ".wind": wind, name + ".hit": hit, name + ".follow": follow}


def K(g, d, b, roll=55):
    return dict(state="drawn", g=g, d=d, b=b, roll=roll, rs=-0.03, ls=-0.134)


def mirror(k):
    g = (-k["g"][0], k["g"][1])
    b = (-k["b"][0], k["b"][1], k["b"][2])
    return dict(k, g=g, b=b, roll=-k["roll"])


H_R = dict(wind=K((0.62, -0.20), 1.00, (0.25, 0.92, -0.30), 60), hit=K((0.20, -0.38), 1.30, (-0.88, 0.10, -0.46), 40),
           follow=K((-0.30, -0.32), 1.15, (-0.92, 0.05, 0.38), 30))
V_R = dict(wind=K((0.30, 0.10), 1.00, (0.15, 0.93, -0.34), 60), hit=K((0.22, -0.30), 1.25, (-0.78, -0.40, -0.48), 45),
           follow=K((-0.05, -0.45), 1.15, (-0.90, -0.40, 0.12), 40))
STAB = dict(wind=K((0.45, -0.50), 0.85, (0.55, 0.32, -0.77), 55), hit=K((0.38, -0.38), 1.45, (-0.22, 0.10, -0.97), 55),
            follow=K((0.36, -0.36), 1.55, (-0.20, 0.10, -0.97), 55))
UPPER = dict(wind=K((-0.05, -0.70), 1.00, (-0.80, -0.30, -0.52), 30), hit=K((0.12, -0.30), 1.25, (-0.20, 0.55, -0.81), 50),
             follow=K((0.30, 0.05), 1.10, (0.35, 0.90, -0.25), 55))
PROFILES = {"h_r": H_R, "h_l": {k: mirror(v) for k, v in H_R.items()}, "v_r": V_R, "v_l": {k: mirror(v) for k, v in V_R.items()},
            "stab": STAB, "upper": UPPER}


def solve_all():
    out = {}
    def one(name, k):
        s = spec_for("rukia", k["state"], k["g"], k["d"], k["b"], k["roll"], k["rs"], k["ls"])
        out[name] = s
    one("stance", STANCE)
    one("guard", GUARD)
    one("sealed_idle", SEALED_IDLE)
    one("sealed_guard", SEALED_GUARD)
    for pn, keys in PROFILES.items():
        for kn, k in keys.items():
            one(f"{pn}.{kn}", k)
    return out


if __name__ == "__main__":
    specs = solve_all()
    nums = {n: {"pos": [round(x, 4) for x in s["pose"]["pos"]], "rot": [round(x, 2) for x in s["pose"]["rot"]]} for n, s in specs.items()}
    json.dump(nums, open(os.path.join(HERE, "poses_fp2.json"), "w"), indent=1)
    for n, v in nums.items():
        print(n, v)
    if len(sys.argv) > 1 and sys.argv[1] == "render":
        sp = os.environ["FP_SP"]
        render(specs, sp + "/bl")
        for n in specs:
            overlay(sp + "/bl/%s.png" % n)
