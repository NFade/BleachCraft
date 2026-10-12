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
SP = os.environ.get("FP_SP", HERE)


def spec_for(char, state, grip_ndc, depth, blade, roll, right_s, left_s, pivot_s=-0.10, left_on=True, extra=None):
    g = ndc_to_cam(grip_ndc[0], grip_ndc[1], depth)
    pose = fa.solve(char, g, unit(*blade), roll, pivot_s=pivot_s, grip_s=(extra or {}).get("grip_s", 0.0), sealed=(state == "sealed"))
    spec = {"char": char, "state": state, "pose": pose, "pivot_s": pivot_s,
            "arms": {"mode": "ik", "right": {"on": True, "s": right_s}, "left": {"on": left_on, "s": left_s}}}
    if extra:
        spec.update({k: v for k, v in extra.items() if k != "grip_s"})
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


def overlay(path, out=None):
    """Hotbar and the HUD plate footprint (GUI scale 3 at 1280x720) on a render, for the readability check."""
    from PIL import Image, ImageDraw
    im = Image.open(path).convert("RGB")
    d = ImageDraw.Draw(im, "RGBA")
    w, h = im.size
    hb_w, hb_h = 182 * 3, 22 * 3
    d.rectangle([w // 2 - hb_w // 2, h - hb_h, w // 2 + hb_w // 2, h], outline=(255, 255, 255, 140), fill=(0, 0, 0, 60))
    d.rectangle([6 * 3, 6 * 3, 6 * 3 + 160 * 3, 6 * 3 + 40 * 3], outline=(255, 255, 255, 120))
    d.line([w // 2 - 8, h // 2, w // 2 + 8, h // 2], fill=(255, 255, 255, 200), width=2)
    d.line([w // 2, h // 2 - 8, w // 2, h // 2 + 8], fill=(255, 255, 255, 200), width=2)
    im.save(out or path)
