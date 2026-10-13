# Paint the Gin / Uryu atlases (system python: numpy + Pillow). Hand-painted flat pixel-art look, limited palette, no noise.
#   python blender/scripts/gu_paint.py <out_dir_gin_shinso> <out_dir_uryu>
# Writes gin_shinso_diffuse.png / gin_shinso_emissive.png and uryu_diffuse.png / uryu_emissive.png (128 x 128 RGBA; emissive = white RGB, alpha = glow intensity).
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gu_layout as L


def rgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


class Atlas:
    def __init__(self):
        self.d = np.zeros((L.ATLAS, L.ATLAS, 4), np.uint8)
        self.e = np.zeros((L.ATLAS, L.ATLAS, 4), np.uint8)
        self.e[..., :3] = 255          # white rgb, alpha 0 = no glow
        self.mask = np.zeros((L.ATLAS, L.ATLAS), bool)

    def v(self, rect):
        x, y, w, h = rect
        self.mask[y:y + h, x:x + w] = True
        return self.d[y:y + h, x:x + w], self.e[y:y + h, x:x + w]

    def cells(self, rects, hexes, emit):
        for r, c, em in zip(rects, hexes, emit):
            d, e = self.v(r)
            d[:, :] = rgb(c)
            e[..., 3] = int(round(em * 255))

    def dilate(self, steps=2):
        img, em, mask = self.d, self.e, self.mask.copy()
        for _ in range(steps):
            new = mask.copy()
            out, oute = img.copy(), em.copy()
            for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                sm = np.roll(mask, (dy, dx), axis=(0, 1))
                take = sm & ~new
                out[take] = np.roll(img, (dy, dx), axis=(0, 1))[take]
                oute[take] = np.roll(em, (dy, dx), axis=(0, 1))[take]
                new |= take
            img, em, mask = out, oute, new
        self.d, self.e = img, em

    def save(self, outdir, name):
        os.makedirs(outdir, exist_ok=True)
        Image.fromarray(self.d, "RGBA").save(os.path.join(outdir, name + "_diffuse.png"))
        Image.fromarray(self.e, "RGBA").save(os.path.join(outdir, name + "_emissive.png"))


P = L.PAL


def paint_gin(out):
    at = Atlas()
    # --- tsuka-ito: light blue lacing, raised cream-blue diamonds (hishimaki), 12 sides x 3 px, 80 px tall
    d, e = at.v(L.GIN["wrap"])
    h, w = d.shape[:2]
    lace, hi, lo = rgb(P["wrap_lace"]), rgb(P["wrap_hi"]), rgb(P["wrap_lo"])
    win, winlo = rgb(P["wrap_win"]), rgb(P["wrap_win_lo"])
    for y in range(h):
        for x in range(w):
            # diamond lattice: 12 px per diamond across (4 diamonds around), 10 px tall
            fx = (x % 12) - 5.5
            fy = (y % 10) - 4.5
            dd = abs(fx) / 6.0 + abs(fy) / 5.0
            if dd < 0.55:
                c = win if (fx + fy * 0.3) < 1.5 else winlo
            elif dd < 1.0:
                c = lace if ((x + y) % 4) else hi
            else:
                c = lo
            d[y, x] = c
    # --- blade: edge row 0 (bright edge line), bevel rows 1-3, ridge row 4, flat rows 5-7 (spine side), wavy temper line in row 2
    d, e = at.v(L.GIN["blade"])
    h, w = d.shape[:2]
    for x in range(w):
        d[0, x] = rgb(P["steel_edge"])
        for y in (1, 2, 3):
            d[y, x] = rgb(P["steel_hi"])
        d[2, x] = rgb(P["steel_temper"]) if ((x // 3) % 2 == 0) else rgb(P["steel_hi"])
        d[4, x] = rgb(P["steel_lo"])
        for y in (5, 6):
            d[y, x] = rgb(P["steel"])
        d[7, x] = rgb(P["steel_lo"])
    d, e = at.v(L.GIN["spine"])
    d[:, :] = rgb(P["spine"])
    d[0, :] = rgb(P["steel"])
    # --- saya: plain dark lacquer, one highlight streak
    d, e = at.v(L.GIN["saya"])
    h, w = d.shape[:2]
    d[:, :] = rgb(P["saya"])
    d[0:2, :] = rgb(P["saya_lo"])
    d[6:8, :] = rgb(P["saya_lo"])
    d[2:4, :] = rgb(P["saya_hi"])
    # --- ray: white core, pale blue edge rows, fully emissive
    d, e = at.v(L.GIN["ray"])
    d[:, :] = rgb(P["ray"])
    d[0:2, :] = rgb(P["ray_edge"])
    d[-2:, :] = rgb(P["ray_edge"])
    d[3:5, :] = rgb(P["ray_core"])
    e[..., 3] = 255
    at.cells(L.GIN_CELLS["metal"], P["metal"], L.EMIT_CELLS["metal"])
    at.dilate(2)
    at.save(out, "gin_shinso")


def paint_uryu(out):
    at = Atlas()
    # --- bow: pale blue reishi ribbon, white core line, soft glow
    d, e = at.v(L.URYU["bow"])
    h, w = d.shape[:2]
    for y in range(h):
        t = abs((y + 0.5) / h - 0.5) * 2          # 0 centre .. 1 edge
        c = rgb(P["bow_core"]) if t < 0.2 else rgb(P["bow_hi"]) if t < 0.5 else rgb(P["bow"]) if t < 0.85 else rgb(P["bow_lo"])
        d[y, :] = c
        e[y, :, 3] = int((0.95 if t < 0.2 else 0.75 if t < 0.5 else 0.55) * 255)
    # --- arrow shaft: bright core with a lighter stripe
    d, e = at.v(L.URYU["shaft"])
    d[:, :] = rgb(P["shaft"])
    d[0:2, :] = rgb(P["shaft_hi"])
    d[-2:, :] = rgb(P["shaft_lo"])
    e[..., 3] = 255
    # --- string: bright blue line
    d, e = at.v(L.URYU["string"])
    d[:, :] = rgb(P["string"])
    d[1:3, :] = rgb(P["reishi"][2])
    e[..., 3] = 255
    at.cells(L.URYU_CELLS["reishi"], P["reishi"], L.EMIT_CELLS["reishi"])
    at.cells(L.URYU_CELLS["silver"], P["silver"], L.EMIT_CELLS["silver"])
    at.dilate(2)
    at.save(out, "uryu")


if __name__ == "__main__":
    paint_gin(sys.argv[1])
    paint_uryu(sys.argv[2])
    print("painted")
