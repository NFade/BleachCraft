#!/usr/bin/env python
"""Atlas of the Rukia ice wall crystals (R11, design/results/r11_ice_crystals.md). Deterministic (fixed seeds), Pillow + numpy only, original pixel art.

Run:  python -I tools/gen_ice_crystal_textures.py              writes textures/block/ice_wall_crystal.png and ice_wall_crystal_glow.png (64 x 64)
      python -I tools/gen_ice_crystal_textures.py --sheet X    also writes an enlarged contact sheet PNG (review only)

The cells are the UV islands of blender/scripts/ice_wall_field.py (CELLS), the Blender build script maps every face into one of them:
  side  prism facet:   deep blue #5B86B5 at the foot -> ice #BCD6EE -> white at the top, bright bevel lines at both edges, a growth line, fibres, sparkles
  tip   pyramid facet: triangle, ice at the foot -> white at the apex, bevel along both slopes
  cap   flat shoulder: pale, lighter rim
  core  inner glow:    pale cold blue, bright in the middle (this is the object "<piece>_core" inside every cell)
The glow texture is the emissive mask in the sense of the OBJ item models (A = intensity, quads whose UV rectangle covers a texel with A > 0 get an
emissive pass): core fully, the tip facets towards the apex, the rest 0.
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.join(ROOT, "blender", "scripts"))
import ice_wall_field as fld  # noqa: E402

OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test", "textures", "block")
N = fld.ATLAS


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


ICE = hexrgb("#BCD6EE")       # ice blue of the brief
DEEP = hexrgb("#5B86B5")      # deep blue of the brief
WHITE = hexrgb("#FFFFFF")
MIST = hexrgb("#EAF8FF")
PALE = hexrgb("#CFEFFF")


def lerp(a, b, t):
    return a + (b - a) * t


def smooth(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def side(w, h):
    """Prism facet. Everything is periodic in y (top row = bottom row) so stacked cells show no band, only vertical streaks."""
    rng = np.random.default_rng(7101)
    rgba = np.zeros((h, w, 4), dtype=np.float32)
    fx = (np.arange(w, dtype=np.float32) + 0.5) / w
    phase = rng.random(w).astype(np.float32) * 2.0 * np.pi
    freq = rng.integers(1, 3, w).astype(np.float32)
    fibre = rng.random(w).astype(np.float32)
    fibre = np.convolve(np.pad(fibre, 1, mode="edge"), [0.25, 0.5, 0.25], mode="valid")
    light = hexrgb("#DCEBF8")
    mid = ICE
    shade = hexrgb("#7FA6CC")
    for y in range(h):
        ty = (y + 0.5) / h
        for x in range(w):
            # facet lighting across the face: bright on the left, deeper blue on the right
            k = smooth(fx[x])
            c = lerp(lerp(light, mid, smooth(k * 2.0)), shade, smooth(k * 2.0 - 1.0))
            wave = 0.5 + 0.5 * np.sin(2.0 * np.pi * freq[x] * ty + phase[x])
            c = c * (0.94 + 0.10 * fibre[x]) + WHITE * (0.10 * wave * fibre[x])
            rgba[y, x, :3] = c
            rgba[y, x, 3] = 118.0 + 26.0 * (fibre[x] - 0.5) + 22.0 * wave * fibre[x]
    # bevel: left edge bright, right edge a deep blue line, two pixels wide
    for b, (col, k, add) in enumerate([(MIST, 0.9, 90.0), (MIST, 0.5, 50.0)]):
        rgba[:, b, :3] = lerp(rgba[:, b, :3], col, k)
        rgba[:, b, 3] = np.minimum(255.0, rgba[:, b, 3] + add)
    for b, (col, k, add) in enumerate([(DEEP, 0.7, 90.0), (DEEP, 0.3, 40.0)]):
        x = w - 1 - b
        rgba[:, x, :3] = lerp(rgba[:, x, :3], col, k)
        rgba[:, x, 3] = np.minimum(255.0, rgba[:, x, 3] + add)
    # inner fracture lines: nearly vertical, wrap in y (they run the full height of the facet)
    for x0 in (int(w * 0.30), int(w * 0.64)):
        for y in range(h):
            xx = x0 + (1 if (y * 3 // h) % 2 else 0)
            rgba[y, xx, :3] = lerp(rgba[y, xx, :3], WHITE, 0.55)
            rgba[y, xx, 3] = min(255.0, rgba[y, xx, 3] + 45.0)
    for _ in range(6):
        y, x = int(rng.integers(0, h)), int(rng.integers(3, w - 3))
        rgba[y, x, :3] = WHITE
        rgba[y, x, 3] = 255.0
    return rgba


def tip(w, h):
    rng = np.random.default_rng(7102)
    rgba = np.zeros((h, w, 4), dtype=np.float32)
    for y in range(h):
        t = 1.0 - (y + 0.5) / h
        for x in range(w):
            fx = (x + 0.5) / w
            inside_half = 0.5 * (1.0 - t)                    # half width of the triangle at this height
            d = inside_half - abs(fx - 0.5)                   # > 0 inside; distance (in fx units) to the slope
            c = lerp(ICE, WHITE, smooth(t / 0.9) * 0.9)
            a = 150.0 + 60.0 * smooth(t)
            if 0.0 <= d < 0.07:                               # bevel along the slopes
                k = 1.0 - d / 0.07
                c = lerp(c, MIST, 0.75 * k)
                a = min(255.0, a + 55.0 * k)
            if d < 0.0:                                       # outside the triangle: bleed colour, never sampled except by mipmaps
                c = lerp(c, ICE, 0.5)
            # a central ridge line
            if abs(fx - 0.5) < 0.5 / w and t > 0.12:
                c = lerp(c, WHITE, 0.55)
            rgba[y, x, :3] = c
            rgba[y, x, 3] = a
    for _ in range(4):
        y, x = int(rng.integers(h // 3, h - 4)), int(rng.integers(w // 3, w - w // 3))
        rgba[y, x, :3] = WHITE
        rgba[y, x, 3] = 255.0
    return rgba


def cap(w, h):
    rgba = np.zeros((h, w, 4), dtype=np.float32)
    for y in range(h):
        for x in range(w):
            r = np.hypot((x + 0.5) / w - 0.5, (y + 0.5) / h - 0.5) / 0.5
            c = lerp(PALE, WHITE, smooth(1.0 - r) * 0.7)
            a = 190.0 + 40.0 * smooth(1.0 - r)
            if r > 0.78:
                c = lerp(c, MIST, 0.7)
                a = 235.0
            rgba[y, x, :3] = c
            rgba[y, x, 3] = a
    return rgba


def core(w, h):
    rng = np.random.default_rng(7103)
    rgba = np.zeros((h, w, 4), dtype=np.float32)
    fibre = rng.random(w).astype(np.float32)
    for y in range(h):
        t = 1.0 - (y + 0.5) / h
        mid = 1.0 - abs(t - 0.5) * 2.0                         # bright in the middle of the cell, so the seams between cells stay soft
        for x in range(w):
            fx = (x + 0.5) / w
            c = lerp(PALE, WHITE, smooth(mid) * 0.8)
            c = lerp(c, ICE, (abs(fx - 0.5) * 2.0) ** 2 * 0.5)
            c = c * (0.96 + 0.08 * fibre[x])
            rgba[y, x, :3] = c
            rgba[y, x, 3] = 185.0 + 70.0 * smooth(mid)
    return rgba


def glow_core(w, h):
    rgba = np.zeros((h, w, 4), dtype=np.float32)
    for y in range(h):
        t = 1.0 - (y + 0.5) / h
        mid = 1.0 - abs(t - 0.5) * 2.0
        for x in range(w):
            fx = (x + 0.5) / w
            edge = 1.0 - (abs(fx - 0.5) * 2.0) ** 2 * 0.4
            rgba[y, x, :3] = lerp(PALE, WHITE, 0.5)
            rgba[y, x, 3] = (110.0 + 120.0 * smooth(mid)) * edge
    return rgba


def glow_tip(w, h):
    rgba = np.zeros((h, w, 4), dtype=np.float32)
    for y in range(h):
        t = 1.0 - (y + 0.5) / h
        for x in range(w):
            if t > 0.50:
                rgba[y, x, :3] = MIST
                rgba[y, x, 3] = 120.0 * smooth((t - 0.50) / 0.5)
    return rgba


def bleed(img, cell):
    """Copies the outer pixel row / column of a cell into a 1 px gutter around it (mipmaps do not bleed other cells in)."""
    x, y, w, h = cell
    for g in (1,):
        if x - g >= 0:
            img[y:y + h, x - g] = img[y:y + h, x]
        if x + w + g - 1 < N:
            img[y:y + h, x + w + g - 1] = img[y:y + h, x + w - 1]
        if y - g >= 0:
            img[y - g, x:x + w] = img[y, x:x + w]
        if y + h + g - 1 < N:
            img[y + h + g - 1, x:x + w] = img[y + h - 1, x:x + w]


def build():
    diffuse = np.zeros((N, N, 4), dtype=np.float32)
    glow = np.zeros((N, N, 4), dtype=np.float32)
    makers = {"side": side, "tip": tip, "cap": cap, "core": core}
    glows = {"core": glow_core, "tip": glow_tip}
    for name, cell in fld.CELLS.items():
        x, y, w, h = cell
        diffuse[y:y + h, x:x + w] = makers[name](w, h)
        if name in glows:
            glow[y:y + h, x:x + w] = glows[name](w, h)
        glow[y:y + h, x:x + w, :3] = np.where(glow[y:y + h, x:x + w, 3:4] > 0, glow[y:y + h, x:x + w, :3], 0.0)
    for cell in fld.CELLS.values():
        bleed(diffuse, cell)
        bleed(glow, cell)
    return diffuse, glow


def to_img(a):
    return Image.fromarray(np.clip(np.rint(a), 0, 255).astype(np.uint8), "RGBA")


def main():
    diffuse, glow = build()
    os.makedirs(OUT, exist_ok=True)
    to_img(diffuse).save(os.path.join(OUT, "ice_wall_crystal.png"))
    to_img(glow).save(os.path.join(OUT, "ice_wall_crystal_glow.png"))
    print("wrote", os.path.join(OUT, "ice_wall_crystal.png"), os.path.join(OUT, "ice_wall_crystal_glow.png"))
    if "--sheet" in sys.argv:
        path = sys.argv[sys.argv.index("--sheet") + 1]
        bg = Image.new("RGBA", (N * 8 * 2 + 8, N * 8), (22, 30, 44, 255))
        for k, a in enumerate((diffuse, glow)):
            im = to_img(a).resize((N * 8, N * 8), Image.NEAREST)
            bg.alpha_composite(im, (k * (N * 8 + 8), 0))
        bg.save(path)
        print("sheet", path)


if __name__ == "__main__":
    main()
