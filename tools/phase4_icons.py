"""Generates the placeholder GUI icons of the two zanpakuto items (phase 4).

Output: mod/src/main/resources/assets/reiatsu_test/textures/item/<item>_<state>_icon.png (32x32, RGBA).
Real icons come from Blender renders later (ADR section 1); keep the file names, replace the pixels.
"""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "mod/src/main/resources/assets/reiatsu_test/textures/item"
ITEMS = {
    "sode_no_shirayuki": (159, 223, 255),
    "senbonzakura": (255, 158, 196),
}


def icon(color, state):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r, g, b = color
    # blade from the lower left hilt to the upper right tip
    if state == "sealed":
        d.line([(8, 24), (26, 6)], fill=(40, 40, 52, 255), width=4)  # dark sheath
        d.line([(8, 24), (26, 6)], fill=(200, 200, 215, 255), width=1)
    elif state == "shikai":
        d.line([(8, 24), (28, 4)], fill=(r, g, b, 255), width=3)
        d.line([(8, 24), (28, 4)], fill=(255, 255, 255, 255), width=1)
        for (x, y) in [(18, 6), (24, 12), (12, 14)]:
            d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(r, g, b, 255))
    else:  # bankai: bright blade inside a halo
        d.ellipse([3, 3, 29, 29], outline=(r, g, b, 255), width=2)
        d.line([(8, 24), (26, 6)], fill=(r, g, b, 255), width=4)
        d.line([(8, 24), (26, 6)], fill=(255, 255, 255, 255), width=2)
    # hilt: square at the lower left
    d.rectangle([4, 22, 10, 28], fill=(70, 50, 30, 255), outline=(230, 190, 60, 255))
    return img


# Owned by tools/rukia_icons.py since step B (real Rukia models): do not overwrite.
SKIP = {("sode_no_shirayuki", "sealed"), ("sode_no_shirayuki", "shikai")}

for name, color in ITEMS.items():
    for state in ("sealed", "shikai", "bankai"):
        if (name, state) in SKIP:
            continue
        path = OUT / f"{name}_{state}_icon.png"
        icon(color, state).save(path)
        print("wrote", path)
