#!/usr/bin/env python
"""Composes the tiles rendered by blender/scripts/build_rukia_ice_wall.py (step "render") into one check sheet (Pillow only).

usage: python -I tools/compose_ice_wall_sheet.py <tiles dir> [out png]     (default out: design/results/r11_ice_wall_sheet.png)
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
W = 1536
BG = (20, 26, 36)
ROWS = [
    [("gallery", "pieces as modelled: shaft a b c | tip a b c | crown a b c | sat a b c d | feet a b")],
    [("col_front", "column 1x5, front"), ("col_side", "profile"), ("col_back", "back"), ("col_three", "three quarter")],
    [("wall_front", "wall 9x5x2, front"), ("wall_three", "three quarter"), ("wall_end", "end on (depth 2)")],
    [("wall_top", "from above"), ("wall_night", "dark sky, inner glow"), ("wall_close", "close up")],
]


def main():
    tiles = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "design", "results", "r11_ice_wall_sheet.png")
    rows = []
    for row in ROWS:
        imgs = [(Image.open(os.path.join(tiles, n + ".png")).convert("RGB"), label) for n, label in row]
        rows.append(imgs)
    h = sum(max(i.height for i, _ in r) + 4 for r in rows)
    sheet = Image.new("RGB", (W, h), BG)
    d = ImageDraw.Draw(sheet)
    y = 0
    for r in rows:
        x = 0
        rh = max(i.height for i, _ in r)
        gap = (W - sum(i.width for i, _ in r)) // max(1, len(r) - 1) if len(r) > 1 else 0
        for img, label in r:
            sheet.paste(img, (x, y))
            d.rectangle((x, y, x + 8 + 7 * len(label), y + 14), fill=(0, 0, 0))
            d.text((x + 4, y + 2), label, fill=(235, 245, 255))
            x += img.width + gap
        y += rh + 4
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
