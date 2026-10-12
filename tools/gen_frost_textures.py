#!/usr/bin/env python
"""Block textures of the Rukia bankai territory freeze (round 6, design/results/rukia_bankai_round6.md). Deterministic (fixed seeds),
Pillow + numpy only, original pixel art (nothing is copied from another mod or from vanilla).

Run:  python -I tools/gen_frost_textures.py            (writes 16 x 16 PNGs into assets/reiatsu_test/textures/block)
      python -I tools/gen_frost_textures.py --sheet X  (also writes an enlarged contact sheet PNG to X, review only)

Palette (blue and white, VFX_STORYBOARD section 0): mist #EAF8FF, ice light #CFEFFF, sky ice #9ED3F0, mid #8EC9EE, core #7FB8DF, deep #2E6FA8,
shadow #1E4C86. Blocks:
  frost_crust_top / _side / _bottom  ground (grass, dirt, sand, gravel ...): bright frost on top, an icicle fringe over frozen earth on the sides
  frost_stone                        rock (stone, cobblestone, deepslate ...): cold grey blue with white rime in the cracks
  frost_leaves                       leaves: cut out (alpha 0 or 255) frosted clusters
  frost_film                         thin ice over water: translucent, cracked
  frost_sprig                        plants (grass, ferns, flowers): a cross of thin ice blades
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test", "textures", "block")
SIZE = 16


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


MIST = hexrgb("#EAF8FF")
ICE_LIGHT = hexrgb("#CFEFFF")
SKY = hexrgb("#9ED3F0")
MID = hexrgb("#8EC9EE")
CORE = hexrgb("#7FB8DF")
DEEP = hexrgb("#2E6FA8")
SHADOW = hexrgb("#1E4C86")
WHITE = hexrgb("#FFFFFF")


def lerp(a, b, t):
    return a + (b - a) * t


def value_noise(rng, cell):
    """Tileable value noise on the 16 x 16 grid with lattice spacing `cell` pixels (cell divides 16)."""
    n = SIZE // cell
    lat = rng.random((n, n)).astype(np.float32)
    out = np.zeros((SIZE, SIZE), dtype=np.float32)
    for y in range(SIZE):
        for x in range(SIZE):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx) % n, int(fy) % n
            x1, y1 = (x0 + 1) % n, (y0 + 1) % n
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            out[y, x] = lerp(lerp(lat[y0, x0], lat[y0, x1], tx), lerp(lat[y1, x0], lat[y1, x1], tx), ty)
    return out


def voronoi_edges(rng, seeds):
    """Edge mask (1 on the border between cells) of a tileable Voronoi diagram: crack lines."""
    pts = rng.random((seeds, 2)) * SIZE
    best = np.full((SIZE, SIZE), 1e9, dtype=np.float32)
    second = np.full((SIZE, SIZE), 1e9, dtype=np.float32)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
    for px, py in pts:
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        dx = np.minimum(dx, SIZE - dx)
        dy = np.minimum(dy, SIZE - dy)
        d = np.sqrt(dx * dx + dy * dy)
        upd = d < best
        second = np.where(upd, best, np.where(d < second, d, second))
        best = np.where(upd, d, best)
    return ((second - best) < 0.9).astype(np.float32)


def sparkles(rng, count, rows=None):
    m = np.zeros((SIZE, SIZE), dtype=np.float32)
    for _ in range(count):
        y = int(rng.integers(0, SIZE)) if rows is None else int(rng.integers(rows[0], rows[1]))
        m[y, int(rng.integers(0, SIZE))] = 1.0
    return m


def to_image(rgb, alpha=None):
    a = np.full((SIZE, SIZE), 255.0, dtype=np.float32) if alpha is None else alpha
    arr = np.dstack([np.clip(rgb, 0, 255), np.clip(a, 0, 255)]).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def shade(rgb, v):
    return rgb * v[..., None]


def crust_top():
    rng = np.random.default_rng(6101)
    n = value_noise(rng, 4) * 0.6 + value_noise(rng, 2) * 0.4
    base = lerp(ICE_LIGHT, MIST, n[..., None])
    cracks = voronoi_edges(rng, 5)
    rgb = lerp(base, SKY, (cracks * 0.55)[..., None])
    rgb = lerp(rgb, WHITE, (sparkles(rng, 9) * 0.9)[..., None])
    return to_image(rgb)


def crust_side():
    rng = np.random.default_rng(6102)
    n = value_noise(rng, 2)
    earth = lerp(hexrgb("#7C9AB3"), hexrgb("#4F6D87"), n[..., None])
    rgb = earth.copy()
    # icicle fringe: frost covers the top rows, the lower edge hangs down by 1 to 5 pixels per column
    hang = [3, 4, 3, 5, 4, 3, 2, 4, 5, 3, 4, 2, 3, 5, 4, 3]
    for x in range(SIZE):
        for y in range(SIZE):
            if y < hang[x]:
                t = y / max(1, hang[x])
                rgb[y, x] = lerp(MIST, ICE_LIGHT, t * 0.8)
        if hang[x] >= 4:  # a bright spike tip
            rgb[hang[x] - 1, x] = SKY
    rgb = lerp(rgb, WHITE, (sparkles(rng, 6, (4, SIZE)) * 0.75)[..., None])
    return to_image(rgb)


def crust_bottom():
    rng = np.random.default_rng(6103)
    n = value_noise(rng, 2) * 0.5 + value_noise(rng, 4) * 0.5
    rgb = lerp(hexrgb("#3E586F"), hexrgb("#5E7D96"), n[..., None])
    rgb = lerp(rgb, SKY, (sparkles(rng, 5) * 0.5)[..., None])
    return to_image(rgb)


def stone():
    rng = np.random.default_rng(6104)
    n = value_noise(rng, 4) * 0.55 + value_noise(rng, 2) * 0.45
    base = lerp(hexrgb("#7E95AA"), hexrgb("#B2C6D6"), n[..., None])
    cracks = voronoi_edges(rng, 4)
    rgb = lerp(base, MIST, (cracks * 0.8)[..., None])
    dark = voronoi_edges(np.random.default_rng(6105), 6)
    rgb = lerp(rgb, hexrgb("#5A7388"), (dark * (1 - cracks) * 0.35)[..., None])
    rgb = lerp(rgb, WHITE, (sparkles(rng, 8) * 0.9)[..., None])
    return to_image(rgb)


def leaves():
    rng = np.random.default_rng(6106)
    n = value_noise(rng, 2)
    solid = (value_noise(np.random.default_rng(6107), 2) * 0.6 + n * 0.4) > 0.24
    rgb = lerp(hexrgb("#9ED3F0"), hexrgb("#EAF8FF"), value_noise(rng, 4)[..., None])
    edge = np.zeros((SIZE, SIZE), dtype=np.float32)
    for y in range(SIZE):
        for x in range(SIZE):
            if solid[y, x] and not (solid[(y + 1) % SIZE, x] and solid[y, (x + 1) % SIZE]):
                edge[y, x] = 1.0
    rgb = lerp(rgb, hexrgb("#4F8DBF"), (edge * 0.55)[..., None])
    rgb = lerp(rgb, WHITE, (sparkles(rng, 9) * 0.9)[..., None])
    alpha = np.where(solid, 255.0, 0.0).astype(np.float32)
    return to_image(rgb, alpha)


def film():
    rng = np.random.default_rng(6108)
    n = value_noise(rng, 4)
    rgb = lerp(ICE_LIGHT, SKY, n[..., None] * 0.6)
    cracks = voronoi_edges(rng, 4)
    rgb = lerp(rgb, WHITE, (cracks * 0.9)[..., None])
    alpha = 150.0 + n * 40.0 + cracks * 70.0
    spark = sparkles(rng, 6)
    rgb = lerp(rgb, WHITE, (spark)[..., None])
    alpha = np.where(spark > 0, 255.0, alpha)
    return to_image(rgb, alpha)


def sprig():
    img = np.zeros((SIZE, SIZE, 4), dtype=np.float32)
    blades = [(3, 9, ICE_LIGHT), (5, 13, MIST), (7, 11, SKY), (8, 15, WHITE), (10, 12, ICE_LIGHT), (12, 8, SKY), (14, 6, MIST)]
    for i, (x, h, col) in enumerate(blades):
        lean = (-1, 0, 1, 0, -1, 1, 0)[i]
        for k in range(h):
            y = SIZE - 1 - k
            xx = x + (lean * k) // 5
            if 0 <= xx < SIZE:
                tone = col if k < h - 2 else WHITE
                img[y, xx, :3] = tone
                img[y, xx, 3] = 255
                if k < h // 2 and 0 <= xx + 1 < SIZE and i % 2 == 0:
                    img[y, xx + 1, :3] = lerp(col, CORE, 0.5)
                    img[y, xx + 1, 3] = 230
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


TEXTURES = {
    "frost_crust_top": crust_top,
    "frost_crust_side": crust_side,
    "frost_crust_bottom": crust_bottom,
    "frost_stone": stone,
    "frost_leaves": leaves,
    "frost_film": film,
    "frost_sprig": sprig,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {}
    for name, fn in TEXTURES.items():
        img = fn()
        img.save(os.path.join(OUT, name + ".png"))
        made[name] = img
    if "--sheet" in sys.argv:
        path = sys.argv[sys.argv.index("--sheet") + 1]
        scale = 12
        sheet = Image.new("RGBA", (len(made) * (SIZE * scale + 8) + 8, SIZE * scale + 16), (40, 48, 60, 255))
        for i, (name, img) in enumerate(made.items()):
            big = img.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            sheet.alpha_composite(big, (8 + i * (SIZE * scale + 8), 8))
        sheet.save(path)
    print("frost textures written to", OUT)


if __name__ == "__main__":
    main()
