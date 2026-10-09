# Texture painting helpers (system python: numpy + Pillow). Hand-painted flat / pixel-art look: limited palette, no noise,
# nearest-neighbour friendly. Islands come from atlas_layouts.py (same file the Blender builders use).
import math, os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import atlas_layouts as al


def rgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3] if len(c) > 3 else 255)


class Canvas:
    def __init__(self, size=al.ATLAS):
        self.n = size
        self.img = np.zeros((size, size, 4), np.uint8)
        self.mask = np.zeros((size, size), bool)     # painted texels (islands)

    def view(self, isl):
        x, y, w, h = isl.x, isl.y, isl.w, isl.h
        self.mask[y:y + h, x:x + w] = True
        return self.img[y:y + h, x:x + w]

    def dilate(self, steps=2):
        """Repeat island edge colours into the gutter (bible 0.3: 2 px gutter)."""
        img, mask = self.img, self.mask.copy()
        for _ in range(steps):
            new = mask.copy()
            out = img.copy()
            for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                sm = np.roll(mask, (dy, dx), axis=(0, 1))
                sim = np.roll(img, (dy, dx), axis=(0, 1))
                take = sm & ~new
                out[take] = sim[take]
                new |= take
            img, mask = out, new
        self.img = img
        return img

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        Image.fromarray(self.img, "RGBA").save(path)


def fill(a, c):
    a[:, :] = c


def poly_sdist(px, py, poly):
    """Distance (>=0) from points (arrays) to a closed polygon outline."""
    d = np.full(px.shape, 1e9)
    n = len(poly)
    for i in range(n):
        ax, ay = poly[i]
        bx, by = poly[(i + 1) % n]
        dx, dy = bx - ax, by - ay
        t = np.clip(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy + 1e-18), 0, 1)
        d = np.minimum(d, np.hypot(px - (ax + t * dx), py - (ay + t * dy)))
    return d


def point_in_poly(px, py, poly):
    inside = np.zeros(px.shape, bool)
    n = len(poly)
    for i in range(n):
        ax, ay = poly[i]
        bx, by = poly[(i + 1) % n]
        cond = ((ay > py) != (by > py)) & (px < (bx - ax) * (py - ay) / (by - ay + 1e-18) + ax)
        inside ^= cond
    return inside


def planar_grid(isl, px_per_m, flip_x=False):
    """World (x, y) in metres of every texel centre of a planar-mapped island (centre of the island = origin)."""
    cols = (np.arange(isl.w) + 0.5 - isl.w / 2.0) / px_per_m
    rows = (isl.h / 2.0 - (np.arange(isl.h) + 0.5)) / px_per_m
    X, Y = np.meshgrid(cols, rows)
    return (-X if flip_x else X), Y


def streaks(a, cols_colors):
    """Vertical streaks: list of (u0, u1, colour) fractions across the island width."""
    h, w = a.shape[:2]
    for u0, u1, c in cols_colors:
        c0, c1 = int(round(u0 * w)), max(int(round(u0 * w)) + 1, int(round(u1 * w)))
        a[:, c0:c1] = c


def save_emissive_rgb(arr_rgba_white_alpha, path):
    """Emissive map as RGBA: RGB white, A = intensity (ADR 1). `arr` is float intensity 0..1 (H, W)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    a = np.clip(arr_rgba_white_alpha, 0, 1)
    out = np.empty(a.shape + (4,), np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.round(a * 255).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path)


# ---- wrap (hishimaki) shared by sealed and shikai
def paint_wrap(a, lace, lace_hi, lace_lo, window, window_hi, window_lo, rings=al.WRAP_RINGS, gap_px=al.WRAP_PX_PER_GAP,
               diamond_w=al.WRAP_DIAMOND_W, crease=None):
    """a: island view (h x w x 4). Rows run top = fuchi end (ring rings-1) to bottom = kashira end (ring 0). Raised diamonds
    (windows) are centred on the odd rings 1, 3 .. 17; lacing crosses diagonally between them."""
    h, w = a.shape[:2]
    crease = crease or lace_lo
    for r in range(h):
        t = (h - (r + 0.5)) / gap_px          # continuous ring coordinate
        j = 2 * round((t - 1) / 2.0) + 1
        j = max(1, min(rings - 3, j))
        cy = (j - t) * -1.0                   # window centre row offset in rings
        dy = (t - j) * gap_px                 # px, positive = toward the fuchi (up the island)
        for c in range(w):
            cx = diamond_w * (round((c + 0.5 - diamond_w / 2.0) / diamond_w)) + diamond_w / 2.0
            dx = (c + 0.5) - cx
            d = abs(dx) / (diamond_w / 2.0) + abs(dy) / gap_px
            if d < 0.80:
                s = dx / (diamond_w / 2.0) - dy / gap_px      # + = lower right (dy up is +)
                col = window_hi if s < -0.25 else (window_lo if s > 0.35 else window)
            elif d < 0.95:
                s = dx / (diamond_w / 2.0) - dy / gap_px
                col = lace_hi if s < 0 else lace_lo
            else:
                # staggered lacing cell: cords cross as an X through its centre
                sx = (c + 0.5) - (diamond_w * round((c + 0.5) / diamond_w))
                ty = (t - 2 * round(t / 2.0)) * gap_px
                dd = abs(abs(sx) / (diamond_w / 2.0) - abs(ty) / gap_px)
                col = crease if dd < 0.14 else lace
            a[r, c] = col
