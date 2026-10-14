# Paint the atlas of the Quincy effect set `uryu_fx` (system python: numpy + Pillow). Deterministic (fixed seeds), re-runnable.
#   python -I blender/scripts/uq_paint.py <out_dir> [<preview_dir>]
# Writes uryu_fx_diffuse.png / uryu_fx_emissive.png (256 x 256 RGBA; emissive = white RGB, alpha = glow intensity, same as the other models) into <out_dir>
# and, when given, a 3x nearest preview of both (uryu_fx_atlas.png) into <preview_dir>. Palette: storyboard section 1.1 (uq_layout.PAL).
# The wing sheet is painted from the SAME plate polygons that uq_build.py extrudes (uq_layout.wing_polys), so the edge lines sit on the plate edges.
import os, sys, math
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import uq_layout as L

N = L.ATLAS
P = L.PAL


def rgb(tok, a=255):
    h = P.get(tok, tok).lstrip('#')
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], np.float64)


def lerp(c0, c1, t):
    return c0 + (c1 - c0) * t


class Atlas:
    def __init__(self):
        self.d = np.zeros((N, N, 4), np.uint8)
        self.e = np.zeros((N, N, 4), np.uint8)
        self.e[..., :3] = 255           # white rgb, alpha = glow intensity
        self.mask = np.zeros((N, N), bool)

    def v(self, rect):
        x, y, w, h = rect
        self.mask[y:y + h, x:x + w] = True
        return self.d[y:y + h, x:x + w], self.e[y:y + h, x:x + w]

    def cells(self):
        for g, rects in L.CELLS.items():
            for r, tok, em in zip(rects, L.CELL_TONES[g], L.EMIT_CELLS[g]):
                d, e = self.v(r)
                d[:, :] = rgb(tok).astype(np.uint8)
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


def put(d, e, mask, col, em):
    """write colour col (RGBA float) and glow em (0..1) where mask"""
    d[mask] = np.clip(col, 0, 255).astype(np.uint8)
    e[..., 3][mask] = int(round(em * 255))


# --------------------------------------------------------------------------------------------- wings
def paint_wing(at):
    x0, y0, w, h = L.RECT["wing"]
    d, e = at.v(L.RECT["wing"])
    rng = np.random.RandomState(4711)
    ys, xs = np.mgrid[0:h, 0:w]
    px = x0 + xs + 0.5
    py = y0 + ys + 0.5
    hi, lo, q1, q2, q0, ql = rgb("wing_hi"), rgb("wing_lo"), rgb("Q1"), rgb("Q2"), rgb("Q0"), rgb("QL")
    ea = L.EMIT["wing"]
    for name, poly in L.wing_polys():
        Pq = [L.wing_px(*p) for p in poly]
        area = sum(Pq[i][0] * Pq[(i + 1) % 4][1] - Pq[(i + 1) % 4][0] * Pq[i][1] for i in range(4))
        sg = 1.0 if area > 0 else -1.0
        dist = np.full(px.shape, 1e9)
        for i in range(4):
            a, b = Pq[i], Pq[(i + 1) % 4]
            ex, ey = b[0] - a[0], b[1] - a[1]
            ln = math.hypot(ex, ey)
            cr = sg * (ex * (py - a[1]) - ey * (px - a[0])) / ln
            dist = np.minimum(dist, cr)
        inside = dist >= -0.6                                   # grown by 0.6 px so the nearest texel at the polygon edge is never empty
        A, D_ = Pq[0], Pq[3]
        ux, uy = D_[0] - A[0], D_[1] - A[1]
        ul = math.hypot(ux, uy)
        vp = np.clip(((px - A[0]) * ux + (py - A[1]) * uy) / (ul * ul), 0.0, 1.0)      # 0 upper edge -> 1 lower edge of the plate
        tv = ys / float(h)
        t = np.clip(0.55 * tv + 0.45 * vp, 0, 1)
        shade = {"spar": 0.0, "plate": 0.06, "step": -0.18, "low1": 0.10, "low2": 0.14, "low3": 0.18, "hook": -0.10}[name]
        t = np.clip(t + shade, 0, 1)
        base = lerp(hi[None, None, :], lo[None, None, :], t[..., None])
        base[..., 3] = L.WING_ALPHA
        em = np.full(px.shape, ea)
        # a soft pale streak along the middle of the plate (crystal sheen) and an inner edge line 3 px inside the rim
        streak = np.clip(1.0 - np.abs(vp - 0.40) * 4.0, 0, 1) * 0.22
        base[..., :3] = base[..., :3] + (q1[:3] - base[..., :3]) * streak[..., None]
        inner = (dist >= 3.0) & (dist < 4.2)
        base[inner, :3] = base[inner, :3] + (q2[:3] - base[inner, :3]) * 0.55
        # stardust: 90 specks per 64 x 64 px (2.2 percent), value 0.6..1.0, 8 percent lilac, alpha 255
        spk = (rng.random_sample(px.shape) < 90.0 / 4096.0) & (dist >= 1.2)
        val = rng.uniform(0.6, 1.0, px.shape)
        lil = rng.random_sample(px.shape) < 0.08
        sc = lerp(q2[None, None, :], q0[None, None, :], val[..., None])
        sc[lil] = lerp(ql, q1, 0.35 * val[lil][:, None])
        sc[..., 3] = 255
        base = np.where((spk)[..., None], sc, base)
        em = np.where(spk, 1.0, em)
        # rim line (the 1 px outline of the plate, Q1)
        rim = dist < 1.2
        base[rim] = q1
        em[rim] = 1.0
        # a few 4 point glints
        cand = np.argwhere(inside & (dist > 5.0))
        pick = rng.choice(len(cand), size=min(len(cand), 4 if name in ("spar", "plate") else 2), replace=False)
        for k in pick:
            gy, gx = cand[k]
            for dy, dx, v_ in ((0, 0, 1.0), (1, 0, 0.8), (-1, 0, 0.8), (0, 1, 0.8), (0, -1, 0.8), (2, 0, 0.5), (-2, 0, 0.5), (0, 2, 0.5), (0, -2, 0.5)):
                yy, xx = gy + dy, gx + dx
                if 0 <= yy < h and 0 <= xx < w and inside[yy, xx]:
                    base[yy, xx] = lerp(base[yy, xx], q0, v_)
                    base[yy, xx, 3] = 255
                    em[yy, xx] = 1.0
        d[inside] = np.clip(base[inside], 0, 255).astype(np.uint8)
        e[..., 3][inside] = np.round(em[inside] * 255).astype(np.uint8)
    # outside the plates: transparent, colour = the nearest pale cyan so that mip / filter bleed stays light (nothing dark leaks into the edge)
    outside = d[..., 3] == 0
    d[outside, :3] = (140, 227, 255)
    e[outside, 3] = 0


# --------------------------------------------------------------------------------------------- small parts
def ramp(u, stops):
    """stops = [(u, token), ...] -> float RGBA at u"""
    for (ua, ta), (ub, tb) in zip(stops, stops[1:]):
        if u <= ub:
            f = 0.0 if ub == ua else (u - ua) / (ub - ua)
            return lerp(rgb(ta), rgb(tb), min(max(f, 0.0), 1.0))
    return rgb(stops[-1][1])


def paint_flake(at):
    d, e = at.v(L.RECT["flake"])
    h, w = d.shape[:2]
    for y in range(h):
        for x in range(w):
            u = x / (w - 1.0)
            v = y / (h - 1.0)
            c = lerp(rgb("Q3"), rgb("Q2"), u * 0.8)
            a = 235
            em = L.EMIT["flake"]
            if abs(v - 0.5) < 0.05:                       # spine
                c, em = rgb("Q0"), 1.0
            elif (abs(((v - 0.5) * 24 * 0.5) - (u * 64 - 30) * 0.18) % 3.0) < 0.55 and u > 0.30:   # herringbone vein
                c = rgb("Q1")
            if y in (0, h - 1) or x == w - 1:             # rim
                c, em = rgb("Q1"), 1.0
            if u < 0.34 and ((y * 1.0 + x * 0.65) % 4.8) < 1.7:      # the comb: diagonal slits cut into the tail (alpha 0)
                a = 0
            d[y, x] = np.clip(c, 0, 255).astype(np.uint8)
            d[y, x, 3] = a
            e[y, x, 3] = int(round(em * 255)) if a else 0


def paint_star(at):
    d, e = at.v(L.RECT["star"])
    h, w = d.shape[:2]
    for y in range(h):
        for x in range(w):
            u = x / (w - 1.0)
            c = ramp(u, [(0.0, "Q0"), (0.30, "Q1"), (0.65, "Q2"), (1.0, "Q3")])
            if y in (0, h - 1):
                c = rgb("Q1") if u > 0.3 else rgb("Q0")
            d[y, x] = c.astype(np.uint8)
            e[y, x, 3] = 255


def paint_shaft(at):
    d, e = at.v(L.RECT["shaft"])
    h, w = d.shape[:2]
    for y in range(h):
        tok = "Q0" if (y // 2) % 2 == 0 else "Q1"
        d[y, :] = rgb(tok).astype(np.uint8)
        e[y, :, 3] = 255
    d[:, w - 1] = rgb("Q2").astype(np.uint8)


def paint_blade(at):
    d, e = at.v(L.RECT["blade"])
    h, w = d.shape[:2]
    for y in range(h):
        t = abs((y + 0.5) / h - 0.5) * 2                  # 0 centre .. 1 edge
        tok = "Q0" if t < 0.45 else "Q1" if t < 0.75 else "Q2"
        d[y, :] = rgb(tok).astype(np.uint8)
        e[y, :, 3] = 255
    d[:, 0:3] = rgb("Q2").astype(np.uint8)                # hilt end a little darker
    d[:, w - 3:] = rgb("Q1").astype(np.uint8)


def paint_pent(at):
    d, e = at.v(L.RECT["pent"])
    h, w = d.shape[:2]
    for y in range(h):
        for x in range(w):
            c = rgb("Q7")
            if 3 <= y <= 4:
                c = rgb("Q0")
            if x < 8:                                      # the bead end
                c = rgb("Q0") if 1 <= y <= 6 else rgb("Q1")
            d[y, x] = c.astype(np.uint8)
            e[y, x, 3] = 255


def paint_band(at, rect, bars, straps, alpha=200, bar_period=32):
    d, e = at.v(rect)
    h, w = d.shape[:2]
    for y in range(h):
        tv = y / (h - 1.0)
        for x in range(w):
            c = lerp(rgb("Q3"), rgb("Q4"), tv * 0.8)
            c[3] = alpha
            em = L.EMIT["band"]
            if bars and ((x + y) % bar_period) < 6:      # the diagonal bars (3 around the 96 px, they scroll in the mod)
                c, em = rgb("Q1"), 1.0
            if y < 2 or y >= h - 2:                        # rim at both ends
                c, em = rgb("Q1"), 1.0
            for s0 in straps:
                if s0 <= y < s0 + 3:
                    c, em = rgb("Q1"), 1.0
            d[y, x] = np.clip(c, 0, 255).astype(np.uint8)
            e[y, x, 3] = int(round(em * 255))


def paint_bow(at):
    d, e = at.v(L.RECT["bow"])
    h, w = d.shape[:2]
    rng = np.random.RandomState(77)
    for y in range(h):
        side = y // 2                                      # 6 sides x 2 px
        tok, em = [("Q4", 0.55), ("Q3", 0.70), ("Q1", 0.85), ("Q0", 1.0), ("Q1", 0.85), ("Q3", 0.70)][side]
        d[y, :] = rgb(tok).astype(np.uint8)
        e[y, :, 3] = int(round(em * 255))
    # stardust in the limbs
    for _ in range(26):
        sx, sy = rng.randint(0, w), rng.randint(0, h)
        d[sy, sx] = rgb("Q0").astype(np.uint8)
        e[sy, sx, 3] = 255
    # the step notches show as a darker cross line (u at the two ledge z of the model)
    for z in L.BOW_NOTCH_Z:
        for sgn in (-1, 1):
            u = (sgn * z + L.BOW_HALF) / (2 * L.BOW_HALF)
            x = int(round(u * (w - 1)))
            d[:, x] = rgb("Q5").astype(np.uint8)
            e[:, x, 3] = int(0.45 * 255)


def paint_needle(at):
    d, e = at.v(L.RECT["needle"])
    h, w = d.shape[:2]
    d[:, :] = (0, 0, 0, 0)
    e[..., 3] = 0

    def px(x, y, tok, a=255):
        if 0 <= x < w and 0 <= y < h:
            d[y, x] = rgb(tok, a).astype(np.uint8)
            e[y, x, 3] = 255
    for x in range(8, w - 14):                              # shaft: 2 rows (about 19 mm), white core
        px(x, 3, "Q0")
        px(x, 4, "Q0")
        if x % 6 < 3:
            px(x, 2, "Q2", 160)
            px(x, 5, "Q2", 160)
    # barbed head: ledge at x 108, four barbs (two up, two down), point at the end
    for k, x in enumerate(range(108, 122)):
        half = max(0.0, 3.6 - (x - 108) * 0.27)
        for y in range(8):
            if abs(y - 3.5) <= half:
                px(x, y, "Q0" if abs(y - 3.5) < 1.0 else "Q1")
    for x, ys in ((106, (0, 7)), (104, (1, 6))):            # barbs swept back from the shoulder
        for y in ys:
            px(x, y, "Q1")
            px(x + 1, y, "Q1")
    # chevron tail fins: two V shapes
    for x0 in (2, 8):
        for k in range(4):
            px(x0 + k, 3 - k // 1 if k < 4 else 0, "Q1")
            px(x0 + k, 4 + k if 4 + k < 8 else 7, "Q1")
        px(x0 + 4, 0, "Q1")
        px(x0 + 4, 7, "Q1")
    for x in range(0, 8):                                   # fin root joins the shaft
        px(x, 3, "Q0")
        px(x, 4, "Q0")


def paint_all(out_dir, preview_dir=None):
    at = Atlas()
    paint_wing(at)
    paint_flake(at)
    paint_star(at)
    paint_shaft(at)
    paint_blade(at)
    paint_pent(at)
    paint_band(at, L.RECT["band"], bars=True, straps=[])
    paint_band(at, L.RECT["boot"], bars=True, straps=[10, 23, 36], bar_period=32)
    paint_bow(at)
    paint_needle(at)
    at.cells()
    for k, r in L.RECT.items():
        if k not in ("wing", "needle", "flake"):
            at.mask[r[1]:r[1] + r[3], r[0]:r[0] + r[2]] = True
    at.dilate(2)
    at.save(out_dir, "uryu_fx")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        bg = np.zeros((N, N, 3), np.uint8)
        bg[:] = (10, 30, 82)
        for tag, arr in (("d", at.d), ("e", at.e)):
            a = arr[..., 3:4].astype(np.float64) / 255.0
            comp = (arr[..., :3] * a + bg * (1 - a)).astype(np.uint8)
            Image.fromarray(comp).resize((N * 3, N * 3), Image.NEAREST).save(os.path.join(preview_dir, "uryu_fx_atlas_%s.png" % tag))
        both = Image.new("RGB", (N * 6 + 6, N * 3), (30, 30, 34))
        both.paste(Image.open(os.path.join(preview_dir, "uryu_fx_atlas_d.png")), (0, 0))
        both.paste(Image.open(os.path.join(preview_dir, "uryu_fx_atlas_e.png")), (N * 3 + 6, 0))
        both.save(os.path.join(preview_dir, "uryu_fx_atlas.png"))
        for tag in ("d", "e"):
            os.remove(os.path.join(preview_dir, "uryu_fx_atlas_%s.png" % tag))


if __name__ == "__main__":
    paint_all(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
    print("painted uryu_fx")
