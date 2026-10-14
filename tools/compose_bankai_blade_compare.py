"""R11 T13: one comparison sheet of the Byakuya bankai giant blade: anime reference frame, old blade (v1) and the new blade (v2).

usage: python -I tools/compose_bankai_blade_compare.py [renders_dir]
Inputs: the Workbench renders written by blender/scripts/render_bankai_blade_compare.py (default %TEMP%/bankai_cmp)
        and the reference frame refs/byakuya_bankai_3.png (already in the repository).
Output: design/results/bankai_blade_new_compare.png
"""
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(os.environ.get("TEMP", ".")) / "bankai_cmp"
OUT = ROOT / "design/results/bankai_blade_new_compare.png"
REF = ROOT / "refs/byakuya_bankai_3.png"
H = 640
BG = (22, 22, 28)


def fit(path, height=H):
    im = Image.open(path).convert("RGB")
    w = round(im.width * height / im.height)
    return im.resize((w, height), Image.LANCZOS)


def label(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, im.width, 18), fill=(0, 0, 0))
    d.text((4, 3), text, fill=(235, 235, 235))
    return im


tiles = []
if REF.exists():
    tiles.append(label(fit(REF), "anime frame (reference)"))
for tag, name in (("old", "v1 (8.0 m, curved)"), ("new", "v2 (5.5 m, user model)")):
    for view, vn in (("side", "from +X"), ("front", "edge on"), ("tip", "tip +X"), ("tip_front", "tip 3/4")):
        p = SRC / f"{tag}_{view}.png"
        if p.exists():
            tiles.append(label(fit(p), f"{name}: {vn}"))
W = sum(t.width for t in tiles) + 8 * (len(tiles) + 1)
sheet = Image.new("RGB", (W, H + 16), BG)
x = 8
for t in tiles:
    sheet.paste(t, (x, 8))
    x += t.width + 8
OUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUT)
print("wrote", OUT, sheet.size)
