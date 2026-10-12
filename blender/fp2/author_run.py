"""Poses of the first person rig in camera space terms. Run: python author_run.py <name>  -> writes specs_<name>.json (and poses_<name>.json with the numbers)."""
import json
import os
import subprocess
import sys

import numpy as np

import fp_author as fa
from fp_author import unit, ndc_to_cam

HERE = os.path.dirname(os.path.abspath(__file__))
BLENDER = "D:/SteamLibrary/steamapps/common/Blender/blender.exe"
SP = open("/tmp/sp.txt").read().strip() if os.path.exists("/tmp/sp.txt") else HERE


def spec_for(char, state, grip_ndc, depth, blade, roll, right_s, left_s, pivot_s=-0.10, left_on=True, extra=None):
    g = ndc_to_cam(grip_ndc[0], grip_ndc[1], depth)
    pose = fa.solve(char, g, unit(*blade), roll, pivot_s=pivot_s)
    spec = {"char": char, "state": state, "pose": pose, "pivot_s": pivot_s,
            "arms": {"mode": "ik", "right": {"on": True, "s": right_s}, "left": {"on": left_on, "s": left_s}}}
    if extra:
        spec.update(extra)
    return spec


def render(specs, outdir):
    path = os.path.join(HERE, "_specs_tmp.json")
    json.dump(specs, open(path, "w"))
    r = subprocess.run([BLENDER, "--background", "--factory-startup", "--python", os.path.join(HERE, "fp_blender.py"), "--", path, outdir],
                       capture_output=True, text=True)
    for line in (r.stdout + r.stderr).splitlines():
        if "Error" in line or "Traceback" in line:
            print(line)
    return r


if __name__ == "__main__":
    pass
