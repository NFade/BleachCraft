#!/usr/bin/env python
"""Phase 6 HUD textures (design/VFX_STORYBOARD.md section 7.10). Deterministic (fixed seeds), Pillow + numpy only.

Run:  python -I tools/gen_hud_textures.py            (writes into mod/src/main/resources/assets/reiatsu_test)
      python -I tools/gen_hud_textures.py --sheet X  (also writes a contact sheet PNG to X for review, 6x nearest)

All textures are RGBA, nearest filtered (GUI pixels), tinted by the code where noted (white = tint carrier). Pixel art rules:
hard 1 px shapes, 1 px ink outline #0B0F1A on icons, alpha quantised where soft.
Differences to the table of 7.10 (documented in LOG.md): voice_mic has 7 cells (84 x 12: three HEARING frames), title_band is
300 x 32 (the band is never stretched), emblem_fill.png (30 x 30) is new (the diamond interior, tinted per character),
title_bankai.png is not generated (the kanji are drawn with the game's own unifont glyphs at run time, no font file shipped).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test")
HUD = os.path.join(ASSETS, "textures", "gui", "hud")
SPR = os.path.join(ASSETS, "textures", "gui", "sprites", "hud")

INK = "#0B0F1A"
PANEL = "#141826"


# ----------------------------------------------------------------------------------------------- helpers
def hexf(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


def clamp(x, lo=0.0, hi=1.0):
    return np.minimum(np.maximum(x, lo), hi)


def smoothstep(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


def new(w, h):
    return np.zeros((h, w, 4), np.float32)


def paint(dst, alpha, rgb):
    """Straight alpha 'over': alpha (h, w) or scalar, rgb (3,) or (h, w, 3)."""
    h, w = dst.shape[:2]
    a = np.broadcast_to(np.asarray(alpha, np.float32), (h, w))
    c = np.broadcast_to(np.asarray(rgb, np.float32), (h, w, 3))
    da = dst[..., 3]
    oa = a + da * (1 - a)
    safe = np.where(oa > 1e-6, oa, 1.0)
    dst[..., :3] = (c * a[..., None] + dst[..., :3] * (da * (1 - a))[..., None]) / safe[..., None]
    dst[..., 3] = oa
    return dst


def coords(w, h):
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    return xs + 0.5, ys + 0.5


def quant(a, levels):
    return np.round(clamp(a) * (levels - 1)) / (levels - 1)


def u8(arr):
    return np.clip(np.round(arr * 255.0), 0, 255).astype(np.uint8)


def save(path, arr):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(u8(arr), "RGBA").save(path, optimize=True)


def hstack(frames):
    return np.concatenate(frames, axis=1)


def vstack(frames):
    return np.concatenate(frames, axis=0)


def noise1d(n, cell, rng):
    pts = rng.random(n // cell + 3)
    xs = np.arange(n) / cell
    i = xs.astype(int)
    f = xs - i
    f = f * f * (3 - 2 * f)
    return pts[i] * (1 - f) + pts[i + 1] * f


# ----------------------------------------------------------------------------------------------- plate shadow
def gen_plate_shadow():
    w, h = 192, 48
    x, y = coords(w, h)
    a = (190 / 255.0) * (1 - smoothstep(14, w, x)) ** 1.05
    # soft top and bottom, rounded left end (smooth: drawn with linear filtering)
    a *= smoothstep(0, 13, y) * smoothstep(0, 13, h - y)
    a *= smoothstep(0, 12, x)
    out = new(w, h)
    paint(out, a, hexf("#05070D"))
    return out


# ----------------------------------------------------------------------------------------------- emblem
def gen_emblem_frame():
    frames = []
    for k in range(3):
        x, y = coords(30, 30)
        d = np.abs(x - 15) + np.abs(y - 15)
        f = new(30, 30)
        outer = (d <= 15)
        ink = outer & (d > 14)
        ring = (d <= 14) & (d > 12)
        inner = (d <= 12) & (d > 11)
        base = hexf("#AEB9C9") if k == 0 else hexf("#FFFFFF")
        bevel = np.where(((x - 15) + (y - 15)) < 0, 1.0, 0.70)[..., None]
        if k == 2:
            # bankai: double ring (a thin outer line), 4 notches at the middle of every edge, studs next to the notches
            ring = (d <= 14) & (d > 12.0)
            notch = ((np.abs(np.abs(x - 15) - 7.5) < 1.6) & (np.abs(np.abs(y - 15) - 7.5) < 1.6))
            ring &= ~notch
            ink |= (notch & (d > 11) & (d <= 15))
            paint(f, ink.astype(np.float32) * 0.94, hexf(INK))
            paint(f, ring.astype(np.float32), base * bevel)
            paint(f, inner.astype(np.float32) * 0.6, hexf(INK))
            # studs: 1 px bright at the 4 tips
            for (sx, sy) in ((14, 1), (15, 1), (14, 28), (15, 28), (1, 14), (1, 15), (28, 14), (28, 15)):
                f[sy, sx, :3] = 1.0
                f[sy, sx, 3] = 1.0
        else:
            paint(f, ink.astype(np.float32) * 0.94, hexf(INK))
            paint(f, ring.astype(np.float32), base * bevel)
            paint(f, inner.astype(np.float32) * 0.6, hexf(INK))
        frames.append(f)
    return hstack(frames)


def gen_emblem_fill():
    x, y = coords(30, 30)
    d = np.abs(x - 15) + np.abs(y - 15)
    a = (d <= 12).astype(np.float32)
    shade = 1.0 - 0.30 * ((y - 3) / 24.0).clip(0, 1) + 0.06 * np.sin((x + y) * 0.9)
    out = new(30, 30)
    paint(out, a, np.repeat(shade[..., None], 3, axis=2))
    return out


class Pix:
    """Supersampled drawing for 16 x 16 pixel art: shapes are painted without AA at S x, each output pixel takes the most common
    colour of its S x S block (crisp, colours preserved); finish() adds the 1 px ink outline."""

    S = 8

    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w * self.S, h * self.S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def c(self, col):
        if isinstance(col, str):
            v = hexf(col)
            return tuple(int(round(t * 255)) for t in v) + (255,)
        return col

    def poly(self, pts, col):
        self.d.polygon([(px * self.S, py * self.S) for px, py in pts], fill=self.c(col))

    def line(self, p0, p1, width, col):
        self.d.line([(p0[0] * self.S, p0[1] * self.S), (p1[0] * self.S, p1[1] * self.S)], fill=self.c(col), width=max(1, int(round(width * self.S))))

    def ell(self, box, col, outline=None, width=1.0):
        self.d.ellipse([box[0] * self.S, box[1] * self.S, box[2] * self.S, box[3] * self.S], fill=self.c(col) if col else None,
                       outline=self.c(outline) if outline else None, width=int(width * self.S))

    def arc(self, box, a0, a1, width, col):
        self.d.arc([box[0] * self.S, box[1] * self.S, box[2] * self.S, box[3] * self.S], a0, a1, fill=self.c(col), width=int(width * self.S))

    def rect(self, x0, y0, x1, y1, col):
        self.d.rectangle([x0 * self.S, y0 * self.S, x1 * self.S - 1, y1 * self.S - 1], fill=self.c(col))

    def dot(self, x, y, col):
        self.rect(x, y, x + 1, y + 1, col)

    def finish(self, outline=True):
        a = np.asarray(self.im)
        S = self.S
        out = new(self.w, self.h)
        blocks = a.reshape(self.h, S, self.w, S, 4).transpose(0, 2, 1, 3, 4).reshape(self.h, self.w, S * S, 4)
        for yy in range(self.h):
            for xx in range(self.w):
                b = blocks[yy, xx]
                op = b[:, 3] > 0
                if op.sum() * 2 < S * S:
                    continue
                cols = b[op][:, :3]
                keys = cols[:, 0].astype(np.int64) * 65536 + cols[:, 1].astype(np.int64) * 256 + cols[:, 2]
                vals, counts = np.unique(keys, return_counts=True)
                k = vals[np.argmax(counts)]
                out[yy, xx] = [(k >> 16) / 255.0, ((k >> 8) & 255) / 255.0, (k & 255) / 255.0, 1.0]
        if outline:
            al = out[..., 3] > 0
            padded = np.pad(~al, 1, constant_values=True)
            ph, pw = padded.shape
            vis = np.zeros_like(padded)
            stack = [(0, 0)]
            vis[0, 0] = True
            while stack:
                yy, xx = stack.pop()
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = yy + dy, xx + dx
                    if 0 <= ny < ph and 0 <= nx < pw and padded[ny, nx] and not vis[ny, nx]:
                        vis[ny, nx] = True
                        stack.append((ny, nx))
            outside = vis[1:-1, 1:-1]
            grown = np.zeros_like(al)
            grown[1:, :] |= al[:-1, :]
            grown[:-1, :] |= al[1:, :]
            grown[:, 1:] |= al[:, :-1]
            grown[:, :-1] |= al[:, 1:]
            edge = grown & ~al & outside
            ink = hexf(INK)
            out[edge, :3] = ink
            out[edge, 3] = 1.0
        return out


def snow_pix(p, cx, cy, arm, col, branch_col=None, width=1.0, branches=True, center=True):
    bc = branch_col or col
    for k in range(6):
        a = math.radians(90 + 60 * k)
        ex, ey = cx + math.cos(a) * arm, cy - math.sin(a) * arm
        p.line((cx, cy), (ex, ey), width, col)
        if branches:
            for frac, ln in ((0.55, arm * 0.38), (0.82, arm * 0.2)):
                bx, by = cx + math.cos(a) * arm * frac, cy - math.sin(a) * arm * frac
                for s in (-1, 1):
                    ba = a + s * math.radians(60)
                    p.line((bx, by), (bx + math.cos(ba) * ln, by - math.sin(ba) * ln), width * 0.9, bc)
    if center:
        p.ell((cx - 0.9, cy - 0.9, cx + 0.9, cy + 0.9), "#FFFFFF")


def sakura_pix(p, cx, cy, r, body="#F9C8F6", edge="#FFE9FB", base="#E5A4DC", core="#8F5A9E"):
    for k in range(5):
        a = math.radians(90 + 72 * k)
        # petal = kite with a notched tip
        ux, uy = math.cos(a), -math.sin(a)
        px_, py_ = -uy, ux
        tip = r
        pts = [(cx, cy),
               (cx + ux * tip * 0.55 + px_ * tip * 0.38, cy + uy * tip * 0.55 + py_ * tip * 0.38),
               (cx + ux * tip * 0.98 + px_ * tip * 0.17, cy + uy * tip * 0.98 + py_ * tip * 0.17),
               (cx + ux * tip * 0.84, cy + uy * tip * 0.84),
               (cx + ux * tip * 0.98 - px_ * tip * 0.17, cy + uy * tip * 0.98 - py_ * tip * 0.17),
               (cx + ux * tip * 0.55 - px_ * tip * 0.38, cy + uy * tip * 0.55 - py_ * tip * 0.38)]
        p.poly(pts, body)
        # lighter edge on the leading side, darker base
        p.line((cx + ux * tip * 0.55 + px_ * tip * 0.3, cy + uy * tip * 0.55 + py_ * tip * 0.3),
               (cx + ux * tip * 0.95 + px_ * tip * 0.12, cy + uy * tip * 0.95 + py_ * tip * 0.12), 0.9, edge)
        p.line((cx, cy), (cx + ux * tip * 0.35, cy + uy * tip * 0.35), 1.6, base)
    p.ell((cx - 1.1, cy - 1.1, cx + 1.1, cy + 1.1), core)


def gen_emblem_icons():
    a = Pix()
    snow_pix(a, 8, 8, 6.6, "#FFFFFF", "#CFEFFF", 1.15)
    ri = a.finish(outline=False)
    b = Pix()
    sakura_pix(b, 8, 8.2, 6.6)
    bi = b.finish(outline=False)
    return hstack([ri, bi])


# ----------------------------------------------------------------------------------------------- timer ring
def ring_cov(w, h, cx, cy, r_in, r_out):
    x, y = coords(w, h)
    r = np.hypot(x - cx, y - cy)
    return clamp(r_out - r + 0.5) * clamp(r - r_in + 0.5), x - cx, y - cy


def gen_timer_ring():
    frames = []
    cov, dx, dy = ring_cov(38, 38, 19, 19, 16.1, 18.1)
    theta = np.mod(np.arctan2(dx, -dy), 2 * np.pi)
    for f in range(36):
        frac = (36 - f) / 36.0
        vis = (theta <= frac * 2 * np.pi).astype(np.float32)
        a = quant(cov, 4) * vis
        # the leading end of the arc is brighter (1.0) than the body (0.84): tinted by the code
        lead = clamp(1 - (frac * 2 * np.pi - theta) / 0.22)
        shade = (0.80 + 0.20 * lead * vis)[..., None]
        fr = new(38, 38)
        paint(fr, a, shade * np.ones(3, np.float32))
        frames.append(fr)
    rows = [hstack(frames[i * 6:(i + 1) * 6]) for i in range(6)]
    return vstack(rows)


def gen_ring_shatter():
    frames = []
    for k in range(4):
        fr = new(40, 40)
        x, y = coords(40, 40)
        theta = np.mod(np.arctan2(x - 20, -(y - 20)), 2 * np.pi)
        for seg in range(8):
            mid = (seg + 0.5) * math.pi / 4
            ox = math.sin(mid) * (1.2 + k * 2.3)
            oy = -math.cos(mid) * (1.2 + k * 2.3)
            r = np.hypot(x - 20 - ox, y - 20 - oy)
            th = np.mod(np.arctan2(x - 20 - ox, -(y - 20 - oy)), 2 * np.pi)
            width = 2.0 - 0.38 * k
            cov = clamp(17.0 + width / 2 - r + 0.5) * clamp(r - (17.0 - width / 2) + 0.5)
            span = np.abs(np.angle(np.exp(1j * (th - mid))))
            keep = (span < math.radians(18 - k * 2)).astype(np.float32)
            paint(fr, quant(cov * keep * (1.0 - 0.22 * k), 4), np.ones(3, np.float32))
        frames.append(fr)
    return hstack(frames)


# ----------------------------------------------------------------------------------------------- reiatsu bar
def bar_shift(row):
    return int(round(3 * (6 - row) / 6.0))


def gen_bar_frame():
    w, h = 128, 11
    out = new(w, h)
    ink = hexf(INK)
    # whole rectangle: ink outline alpha 230 and ink body
    rect = np.zeros((h, w), np.float32)
    rect[:, :] = 1.0
    for (cx, cy) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        rect[cy, cx] = 0.0
    paint(out, rect * (230 / 255.0), ink)
    # recess (parallelogram) with the panel colour
    rec = np.zeros((h, w), np.float32)
    hi = np.zeros((h, w), np.float32)
    lo = np.zeros((h, w), np.float32)
    for r in range(7):
        s = bar_shift(r)
        xl, xr = 2 + s, 2 + 121 + s
        rec[2 + r, xl:xr] = 1.0
    for x in range(2 + 3 - 1, 2 + 3 + 121 + 1):
        if 0 <= x < w:
            hi[1, x] = 1.0
    for r in range(7):
        s = bar_shift(r)
        hi[2 + r, 2 + s - 1] = 1.0  # lit left edge of the slant
    for x in range(2 - 1, 2 + 121 + 1):
        lo[9, x] = 1.0
    out[2:9, :, 3] = out[2:9, :, 3]
    paint(out, rec * (200 / 255.0), hexf(PANEL))
    paint(out, hi * 0.95, hexf("#2A3046"))
    paint(out, lo * 0.9, hexf("#06080F"))
    return out


def gen_bar_mask():
    out = new(124, 7)
    for r in range(7):
        s = bar_shift(r)
        out[r, s:s + 121] = [1, 1, 1, 1]
    return out


def gen_bar_fill():
    states = [("#5E6B80", "#AEB9C9"), ("#3A86C8", "#CFEFFF"), ("#9ED3F0", "#FFFFFF"), ("#A8569E", "#F9C8F6"), ("#7A6BC0", "#F3E4FF")]
    out = new(124, 35)
    x = (np.arange(124, dtype=np.float32) + 0.5) / 124.0
    for i, (c0, c1) in enumerate(states):
        a, b = hexf(c0), hexf(c1)
        t = x[:, None] ** 0.9
        row = a[None, :] * (1 - t) + b[None, :] * t  # (124, 3)
        for r in range(7):
            shade = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0][r]
            if r == 0:
                col = row * 0.62 + 0.38  # top highlight
            elif r == 1:
                col = row * 0.88 + 0.12
            elif r in (5,):
                col = row * 0.90
            elif r == 6:
                col = row * 0.74
            else:
                col = row
            # faint diagonal glass stripes
            stripe = (((np.arange(124) + r * 2) % 14) < 2).astype(np.float32)[:, None] * 0.07 * (1 if 1 <= r <= 5 else 0)
            col = np.clip(col + stripe, 0, 1)
            out[i * 7 + r, :, :3] = col
            out[i * 7 + r, :, 3] = 1.0
    return out


def gen_bar_overlay():
    rng = np.random.default_rng(1207)
    out = new(64, 14)
    # row 0 (y 0..6): frost crystals, tileable
    frost = np.zeros((7, 64), np.float32)
    spots = [(6, 3), (19, 2), (31, 4), (44, 3), (56, 2)]
    glints = [(19, 2), (44, 3)]
    for (cx, cy) in spots:
        for (dx, dy, v) in ((0, 0, 0.62), (-1, 0, 0.4), (1, 0, 0.4), (0, -1, 0.4), (0, 1, 0.4), (-2, 0, 0.22), (2, 0, 0.22), (-1, -1, 0.14), (1, 1, 0.14), (1, -1, 0.14), (-1, 1, 0.14)):
            yy = cy + dy
            if 0 <= yy < 7:
                frost[yy, (cx + dx) % 64] = max(frost[yy, (cx + dx) % 64], v)
    for (cx, cy) in glints:
        frost[cy, cx] = 0.95
    # row 1 (y 7..13): petals (3 x 2 tilted)
    pet = np.zeros((7, 64), np.float32)
    for (cx, cy) in ((4, 2), (15, 4), (27, 1), (38, 4), (49, 2), (60, 4)):
        for (dx, dy, v) in ((0, 0, 0.62), (1, 0, 0.5), (2, -1, 0.34), (-1, 1, 0.3)):
            yy = cy + dy
            if 0 <= yy < 7:
                pet[yy, (cx + dx) % 64] = max(pet[yy, (cx + dx) % 64], v)
    pet[2, 15 % 64] = 0.95
    pet[1, 50] = 0.95
    paint(out[0:7], frost, np.ones(3, np.float32))
    paint(out[7:14], pet, np.ones(3, np.float32))
    return out


def gen_bar_sheen():
    w, h = 14, 7
    x, y = coords(w, h)
    xc = 7 + (3.5 - y) * 1.0
    a = np.exp(-((x - xc) / 2.4) ** 2) * (0.55 + 0.45 * np.exp(-((y - 3.5) / 3.0) ** 2))
    out = new(w, h)
    paint(out, quant(a, 8), np.ones(3, np.float32))
    return out


def gen_gem():
    rows = [1, 3, 5, 7, 7, 7, 5, 3, 1]
    cells = []
    for k in range(4):
        c = new(7, 9)
        for yy, wd in enumerate(rows):
            x0 = (7 - wd) // 2
            for xx in range(x0, x0 + wd):
                c[yy, xx, 3] = 1.0
        # outline (ink) around
        al = c[..., 3] > 0
        grown = np.zeros_like(al)
        grown[1:, :] |= al[:-1, :]
        grown[:-1, :] |= al[1:, :]
        grown[:, 1:] |= al[:, :-1]
        grown[:, :-1] |= al[:, 1:]
        # facets: lit left / top, mid, shadow right / bottom
        for yy, wd in enumerate(rows):
            x0 = (7 - wd) // 2
            for xx in range(x0, x0 + wd):
                fx = xx - 3
                if k == 0:  # off: dull steel
                    base = np.array(hexf("#5E6B80"))
                    if fx < 0 and yy < 5:
                        base = hexf("#8A93A6")
                    elif fx > 0 or yy > 5:
                        base = hexf("#3E4658")
                else:  # on: white, tinted by the code
                    base = np.array([1.0, 1.0, 1.0], np.float32)
                    if fx < 0 and yy < 5:
                        base = np.array([1.0, 1.0, 1.0], np.float32)
                    elif fx > 0 and yy < 5:
                        base = np.array([0.86, 0.86, 0.86], np.float32)
                    else:
                        base = np.array([0.66, 0.66, 0.66], np.float32)
                c[yy, xx, :3] = base
        c[1, 3, :3] = 1.0 if k else hexf("#AEB9C9")
        if k >= 2:
            # sparkle: bright pixels at the top left facet
            pts = [(2, 2), (3, 3)] if k == 2 else [(2, 1), (1, 2), (3, 3), (2, 3)]
            for (xx, yy) in pts:
                c[yy, xx, :3] = 1.0
        edge = grown & ~al
        c[edge, :3] = hexf(INK)
        c[edge, 3] = 1.0
        cells.append(c)
    return hstack(cells)


def gen_pixel():
    out = new(4, 4)
    out[:, :] = [1, 1, 1, 1]
    return out


def gen_hatch():
    out = new(4, 4)
    for yy in range(4):
        for xx in range(4):
            if (xx + yy) % 4 in (0, 1):
                out[yy, xx] = [1, 1, 1, 1]
    return out


# ----------------------------------------------------------------------------------------------- slots
def slot_cell(kind):
    w = h = 22
    x, y = coords(w, h)
    out = new(w, h)
    cut = np.ones((h, w), np.float32)
    for (cx, cy) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        cut[cy, cx] = 0.0
    edge = np.zeros((h, w), bool)
    edge[0, :] = edge[-1, :] = True
    edge[:, 0] = edge[:, -1] = True
    edge2 = np.zeros((h, w), bool)
    edge2[1, :] = edge2[-2, :] = True
    edge2[:, 1] = edge2[:, -2] = True
    if kind == 0 or kind == 1:  # normal, poor
        paint(out, cut * (205 / 255.0) * (~edge), hexf(PANEL))
        paint(out, cut * edge, np.ones(3, np.float32) if kind == 0 else hexf("#E8A8A0"))
        # inner bevel: lit top-left, dark bottom-right
        lit = np.zeros((h, w), np.float32)
        lit[1, 1:-2] = 1
        lit[1:-2, 1] = 1
        sh = np.zeros((h, w), np.float32)
        sh[-2, 2:-1] = 1
        sh[2:-1, -2] = 1
        paint(out, lit * 0.55, hexf("#2A3046"))
        paint(out, sh * 0.9, hexf("#06080F"))
    elif kind == 2:  # disabled
        paint(out, cut * (225 / 255.0) * (~edge), hexf("#0B0F1A"))
        paint(out, cut * edge * 0.75, hexf("#5E6B80"))
        paint(out, cut * edge2 * 0.35, hexf("#1B2132"))
    else:  # active: 2 px frame, inner glow
        paint(out, cut * (215 / 255.0) * (~edge), hexf(PANEL))
        paint(out, cut * edge, np.ones(3, np.float32))
        paint(out, cut * edge2 * 0.85, np.ones(3, np.float32) * 0.92)
        glow = clamp(1 - np.maximum(np.abs(x - 11), np.abs(y - 11)) / 10.0) ** 1.6
        paint(out, cut * (~edge) * (~edge2) * glow * 0.28, np.ones(3, np.float32))
    return out


def gen_slot_frame():
    return hstack([slot_cell(k) for k in range(4)])


def gen_slot_glow():
    x, y = coords(28, 28)
    r = (np.abs(x - 14) ** 3 + np.abs(y - 14) ** 3) ** (1 / 3.0)
    a = clamp(1 - r / 14.0) ** 1.6
    out = new(28, 28)
    paint(out, a, np.ones(3, np.float32))
    return out


def gen_cooldown_sweep():
    frames = []
    x, y = coords(20, 20)
    theta = np.mod(np.arctan2(x - 10, -(y - 10)), 2 * np.pi)
    r = np.hypot(x - 10, y - 10)
    for k in range(1, 25):
        rem = k / 24.0
        start = (1 - rem) * 2 * np.pi
        dark = (theta >= start - 1e-6).astype(np.float32)
        fr = new(20, 20)
        paint(fr, dark * (160 / 255.0), np.zeros(3, np.float32))
        if k < 24:
            # leading edge: a thin lighter line along the sweep boundary
            dist = np.abs(theta - start) * np.maximum(r, 1.0)
            line = ((dist < 0.7) & (r < 14.5)).astype(np.float32)
            paint(fr, line * dark * 0.0 + line * 0.42, np.ones(3, np.float32))
        frames.append(fr)
    rows = [hstack(frames[i * 6:(i + 1) * 6]) for i in range(4)]
    return vstack(rows)


def gen_key_tab():
    out = new(9, 9)
    out[:, :, :3] = hexf(INK)
    out[:, :, 3] = 235 / 255.0
    out[0, :, :3] = 1.0
    out[0, :, 3] = 1.0
    for (cx, cy) in ((0, 8), (8, 8)):
        out[cy, cx, 3] = 0.0
    out[0, 0, 3] = 0.0
    out[0, 8, 3] = 0.0
    return out


# ----------------------------------------------------------------------------------------------- ability icons
RUKIA = ("#FFFFFF", "#CFEFFF", "#7FB8DF", "#2E6FA8")
BYAK = ("#FFE9FB", "#F9C8F6", "#E5A4DC", "#8F5A9E")


def petal_poly(cx, cy, ang_deg, ln, wd):
    a = math.radians(ang_deg)
    ux, uy = math.cos(a), -math.sin(a)
    px_, py_ = -uy, ux
    return [(cx, cy),
            (cx + ux * ln * 0.45 + px_ * wd, cy + uy * ln * 0.45 + py_ * wd),
            (cx + ux * ln, cy + uy * ln),
            (cx + ux * ln * 0.45 - px_ * wd, cy + uy * ln * 0.45 - py_ * wd)]


def ascii_icon(rows, pal, outline=True):
    """w x h pixel map: one character per pixel, '.' = transparent."""
    h = len(rows)
    w = max(len(r) for r in rows)
    p = Pix(w, h)
    for yy, row in enumerate(rows):
        for xx, ch in enumerate(row):
            if ch != "." and ch != " ":
                p.dot(xx, yy, pal[ch])
    return p.finish(outline)


def pal_r():
    return {"W": RUKIA[0], "C": RUKIA[1], "M": RUKIA[2], "D": RUKIA[3]}


def pal_b():
    return {"W": BYAK[0], "C": BYAK[1], "M": BYAK[2], "D": BYAK[3]}


def icon_tsukishiro():
    return ascii_icon([
        "................",
        ".......WW.......",
        "......CWWC......",
        "...C..CWWC......",
        "......CWWC......",
        "......CWWC...C..",
        "......CWWC......",
        "......CWWC......",
        "......CWWC......",
        ".....MCWWCM.....",
        "..MMMMCWWCMMMM..",
        ".MCCCCCWWCCCCCM.",
        "DMMCCCCCCCCCCMMD",
        ".DDDMMMMMMMMDDD.",
        "................",
        "................",
    ], pal_r())


def spike(p, cx, base_y, height, half, light, mid, dark):
    top = base_y - height
    for yy in range(top, base_y + 1):
        t = (yy - top) / max(1, height)
        hw = int(round(half * t))
        for xx in range(cx - hw, cx + hw + 1):
            col = light if xx < cx else (mid if xx > cx else RUKIA[0])
            if xx == cx + hw and hw > 0:
                col = dark
            p.dot(xx, yy, col)


def icon_hakuren():
    p = Pix()
    w, c, m, d = RUKIA
    p.rect(1, 13, 15, 14, m)
    spike(p, 3, 12, 4, 1, c, m, d)
    spike(p, 7, 12, 7, 2, c, m, d)
    spike(p, 12, 12, 10, 3, c, m, d)
    p.dot(1, 11, c)
    p.dot(5, 10, w)
    p.dot(9, 14, c)
    return p.finish()


def icon_shirafune():
    w, c, m, d = RUKIA
    p = Pix()
    p.poly([(1.2, 13.4), (3.2, 14.6), (12.6, 5.0), (13.4, 2.0), (10.4, 2.8)], c)
    p.poly([(3.2, 14.6), (12.6, 5.0), (13.4, 2.0), (11.4, 6.4)], m)
    p.poly([(1.8, 13.4), (3.0, 14.2), (12.0, 4.8)], w)
    p.poly([(11.2, 3.6), (14.8, 0.8), (12.6, 6.0)], w)
    for (x, y) in ((7, 1), (14, 8), (11, 12)):
        p.dot(x, y, c)
    p.dot(3, 9, m)
    return p.finish()


def icon_absolute_zero():
    w, c, m, d = RUKIA
    p = Pix()
    p.ell((1.2, 1.2, 14.8, 14.8), None, outline=m, width=1.0)
    snow_pix(p, 8, 8, 5.4, c, w, 1.0, branches=True, center=True)
    p.rect(7, 7, 9, 9, w)
    return p.finish()


def icon_attack():
    w, c, m, d = BYAK
    p = Pix()
    for i, (x, y) in enumerate(((2.4, 12.6), (5.2, 10.0), (7.8, 7.6))):
        p.poly(petal_poly(x - 1.2, y + 1.2, 45, 3.6, 1.15), (m, c, c)[i])
    p.poly([(14.8, 1.2), (8.6, 2.6), (13.4, 7.4)], w)
    p.poly([(14.8, 1.2), (11.0, 2.0), (13.2, 4.6)], c)
    p.dot(1, 9, m)
    p.dot(4, 14, m)
    return p.finish()


def icon_barrier():
    p = Pix()
    w, c, m, d = BYAK
    p.rect(1, 13, 15, 14, m)
    for i in range(9):
        a = math.radians(180 - i * 22.5)
        for rr, col in ((5.2, c if i % 2 == 0 else w), (6.2, c if i % 2 == 0 else w), (7.0, m)):
            cx = 8 + math.cos(a) * rr
            cy = 12.4 - math.sin(a) * rr * 1.25
            p.dot(int(round(cx - 0.5)), int(round(cy - 0.5)), col)
    p.rect(7, 12, 9, 13, c)
    return p.finish()


def icon_scatter():
    w, c, m, d = BYAK
    p = Pix()
    widths = [14, 12, 10, 8, 6, 4, 2]
    for i, wd in enumerate(widths):
        yy = 1 + i * 2
        off = (1 if i % 2 else -1) if wd < 12 else 0
        x0 = 8 - wd // 2 + off
        p.rect(x0, yy, x0 + wd, yy + 1, w if i % 2 == 0 else c)
        p.rect(x0, yy + 1, x0 + wd, yy + 2, m)
    p.dot(1, 3, m)
    p.dot(14, 6, m)
    p.dot(3, 10, c)
    p.dot(12, 12, c)
    return p.finish()


def icon_hakuteiken():
    pal = {"W": "#FFFFFF", "C": "#DDE6F6", "M": "#B9C6E0"}
    return ascii_icon([
        "................",
        ".......WW.......",
        "......WWWW......",
        "..W...WCWC...W..",
        ".WW...WWWC...WW.",
        ".WCW..WCWC..WCW.",
        ".WWCW.WWWC.WCWW.",
        "..WCCWWCWCWCCW..",
        "...WCCWWWCCCW...",
        "....WMMMMMMW....",
        ".....MMCCMM.....",
        "......MCCM......",
        ".......MM.......",
        ".......MM.......",
        "................",
        "................",
    ], pal)


def icon_senkei():
    w, c, m, d = BYAK
    p = Pix()
    for row in range(3):
        for col in range(3 if row != 1 else 2):
            x = 2 + col * 5 + (2 if row == 1 else 0)
            y = 1 + row * 5
            p.rect(x + 1, y, x + 2, y + 3, w)
            p.rect(x, y + 3, x + 3, y + 4, m)
            p.rect(x + 1, y + 4, x + 2, y + 5, c)
    return p.finish()


def icon_release():
    w, c, m, d = ("#FFFFFF", "#DDE6F6", "#B9C6E0", INK)
    p = Pix()
    p.line((2.6, 13.4), (10.6, 5.4), 1.5, c)
    p.line((3.0, 13.0), (10.4, 5.6), 0.8, w)
    p.line((1.6, 11.6), (4.6, 14.4), 1.2, m)
    p.rect(12, 1, 13, 9, w)
    p.rect(9, 4, 16, 5, w)
    p.rect(11, 3, 14, 6, w)
    p.dot(10, 2, c)
    p.dot(14, 2, c)
    p.dot(10, 7, c)
    p.dot(14, 7, c)
    return p.finish()


def icon_bankai():
    w, c, m, d = ("#FFFFFF", "#DDF3FF", "#9ED3F0", INK)
    p = Pix()
    p.line((2.4, 2.4), (13.6, 13.6), 1.6, c)
    p.line((13.6, 2.4), (2.4, 13.6), 1.6, c)
    p.line((2.8, 2.8), (13.2, 13.2), 0.8, w)
    p.line((13.2, 2.8), (2.8, 13.2), 0.8, w)
    p.ell((5.2, 5.2, 10.8, 10.8), None, outline=m, width=1.0)
    p.rect(7, 7, 9, 9, w)
    return p.finish()


def icon_seal():
    w, c, m, d = ("#FFFFFF", "#CFD6E4", "#8A93A6", INK)
    p = Pix()
    p.line((3.6, 12.4), (11.4, 4.6), 2.0, "#2B3350")
    p.line((3.8, 12.2), (11.0, 5.0), 0.8, "#5C6A92")
    p.line((11.8, 4.2), (14.2, 1.8), 1.4, c)
    p.rect(9, 4, 13, 6, m)
    p.line((10.8, 3.4), (13.4, 6.0), 1.2, w)
    p.rect(2, 12, 5, 14, m)
    p.dot(1, 14, c)
    return p.finish()


def gen_ability_icons():
    icons = [icon_tsukishiro(), icon_hakuren(), icon_shirafune(), icon_absolute_zero(), icon_attack(), icon_barrier(),
             icon_scatter(), icon_hakuteiken(), icon_senkei(), icon_release(), icon_bankai(), icon_seal()]
    rows = [hstack(icons[i * 4:(i + 1) * 4]) for i in range(3)]
    return vstack(rows)


# ----------------------------------------------------------------------------------------------- voice mic
MIC_BODY = [
    "....WWWW....",
    "....WWWW....",
    "....WWWW....",
    "....WWWW....",
    "....WWWW....",
    "..W.WWWW.W..",
    "..W.WWWW.W..",
    "..WW....WW..",
    "...WWWWWW...",
    ".....WW.....",
    ".....WW.....",
    "...WWWWWW...",
]


def mic_cell(kind):
    pal = {"W": "#FFFFFF"}
    rows = [list(r) for r in MIC_BODY]
    if kind == 1:  # idle: hollow capsule
        for yy in range(1, 5):
            for xx in range(5, 7):
                rows[yy][xx] = "."
    cell = ascii_icon(["".join(r) for r in rows], pal, outline=False)
    if kind == 0:
        s = Pix(12, 12)
        s.line((1.0, 11.0), (11.0, 1.0), 1.4, "#FFFFFF")
        sl = s.finish(outline=False)
        m = sl[..., 3] > 0
        cell[m] = sl[m]
    if kind in (3, 4, 5):
        pats = {3: (2, 4, 2), 4: (4, 2, 4), 5: (3, 5, 3)}[kind]
        for yy in range(5, 8):
            cell[yy, 9] = [0, 0, 0, 0]
        for i, hgt in enumerate(pats):
            x = 9 + i
            for j in range(hgt):
                cell[7 - j, x] = [1, 1, 1, 1]
    if kind == 6:
        for yy in (1, 2, 3, 4):
            cell[yy, 10] = [1, 1, 1, 1]
        cell[6, 10] = [1, 1, 1, 1]
    return cell


def gen_voice_mic():
    return hstack([mic_cell(k) for k in range(7)])


# ----------------------------------------------------------------------------------------------- titles and toasts
def gen_title_band():
    rng = np.random.default_rng(777)
    w, h = 300, 32
    x, y = coords(w, h)
    top = smoothstep(0, 7, y) ** 0.8
    bot = smoothstep(0, 7, h - y) ** 0.8
    a = top * bot
    # ragged brush ends: the end shifts per row (noise 6 px) and the first pixels are dry-brushed
    nl = noise1d(h, 3, rng)[:h] * 6.0
    nr = noise1d(h, 3, rng)[:h] * 6.0
    left = nl[:, None]
    right = (w - nr)[:, None]
    ends = smoothstep(0, 5, x - left) * smoothstep(0, 5, right - x)
    a = a * ends
    # dry brush streaks: horizontal runs with lower alpha, stronger towards the ends
    streak = np.ones((h, w), np.float32)
    for r in range(h):
        pos = 0
        while pos < w:
            ln = int(rng.integers(8, 40))
            if rng.random() < 0.22:
                near_end = 1.0 - min(pos, w - pos) / 150.0
                streak[r, pos:pos + ln] = 1.0 - 0.55 * clamp(near_end + 0.2) * rng.random()
            pos += ln
    a = a * streak
    out = new(w, h)
    paint(out, quant(a, 16), np.ones(3, np.float32))
    return out


def gen_title_glow():
    x, y = coords(192, 96)
    a = np.exp(-(((x - 96) / 52.0) ** 2 + ((y - 48) / 24.0) ** 2))
    rn = np.hypot((x - 96) / 96.0, (y - 48) / 48.0)  # 1.0 on the border of the ellipse inscribed in the texture
    a = clamp(a * 1.04 - 0.01) * (1 - smoothstep(0.55, 1.0, rn))
    out = new(192, 96)
    paint(out, a, np.ones(3, np.float32))
    return out


def gen_toast_line():
    rng = np.random.default_rng(31)
    w, h = 96, 3
    x, y = coords(w, h)
    along = smoothstep(0, 10, x) * smoothstep(0, 22, w - x) ** 0.7
    prof = np.array([0.55, 1.0, 0.5], np.float32)[:, None]
    a = along[None, :] * prof if along.ndim == 1 else along * prof
    n = rng.random((h, w)) < 0.12
    a = a * np.where(n, 0.45, 1.0)
    out = new(w, h)
    paint(out, quant(a, 8), np.ones(3, np.float32))
    return out


def gen_toast_sprite():
    w = h = 16
    x, y = coords(w, h)
    out = new(w, h)
    cut = np.ones((h, w), np.float32)
    for (cx, cy) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        cut[cy, cx] = 0.0
    edge = np.zeros((h, w), bool)
    edge[0, :] = edge[-1, :] = True
    edge[:, 0] = edge[:, -1] = True
    paint(out, cut * (~edge) * (180 / 255.0), hexf(INK))
    paint(out, cut * edge, hexf("#2A3046"))
    return out


# ----------------------------------------------------------------------------------------------- driver
def contact_sheet(path, items):
    scale = 6
    pad = 8
    imgs = []
    for name, arr in items:
        im = Image.fromarray(u8(arr), "RGBA")
        imgs.append((name, im.resize((im.width * scale, im.height * scale), Image.NEAREST)))
    W = 1800
    x = pad
    y = pad
    rowh = 0
    placed = []
    for name, im in imgs:
        if x + im.width + pad > W:
            x = pad
            y += rowh + pad + 14
            rowh = 0
        placed.append((name, im, x, y))
        x += im.width + pad
        rowh = max(rowh, im.height)
    sheet = Image.new("RGBA", (W, y + rowh + pad + 14), (46, 74, 112, 255))
    d = ImageDraw.Draw(sheet)
    for name, im, px, py in placed:
        sheet.alpha_composite(im, (px, py + 14))
        d.text((px, py), name, fill=(255, 255, 255, 255))
    sheet.convert("RGB").save(path)


ATLAS_SIZE = 512
# sheets packed into hud_atlas.png (one texture, one draw call); the others stay separate files: soft textures need linear
# filtering, tiled ones (hatch, bar overlay) need texture wrapping
NOT_IN_ATLAS = {"plate_shadow", "title_glow", "slot_glow", "hatch", "bar_overlay", "bar_mask"}
JAVA_LAYOUT = os.path.join(ROOT, "mod", "src", "client", "java", "dev", "minebleach", "reiatsutest", "client", "hud", "HudAtlasLayout.java")


def pack_atlas(items):
    sheets = [(n, a) for n, a in items if n not in NOT_IN_ATLAS]
    sheets.sort(key=lambda t: (-t[1].shape[0], -t[1].shape[1], t[0]))
    atlas = new(ATLAS_SIZE, ATLAS_SIZE)
    layout = {}
    x = y = shelf = 0
    for name, arr in sheets:
        h, w = arr.shape[:2]
        if x + w > ATLAS_SIZE:
            x = 0
            y += shelf + 1
            shelf = 0
        if y + h > ATLAS_SIZE:
            raise SystemExit("hud atlas overflow")
        atlas[y:y + h, x:x + w] = arr
        layout[name] = (x, y, w, h)
        x += w + 1
        shelf = max(shelf, h)
    return atlas, layout


def write_layout(layout):
    lines = ["package dev.minebleach.reiatsutest.client.hud;", "", "import java.util.Map;", "",
             "/** GENERATED by tools/gen_hud_textures.py: positions of the sheets inside textures/gui/hud/hud_atlas.png (x, y, w, h). */",
             "final class HudAtlasLayout {", "	static final int SIZE = %d;" % ATLAS_SIZE,
             "	static final Map<String, int[]> SHEETS = Map.ofEntries("]
    entries = ['		Map.entry("%s", new int[] {%d, %d, %d, %d})' % ((n,) + v) for n, v in sorted(layout.items())]
    lines.append(",\n".join(entries).replace("\\n", "\n"))
    lines += ["	);", "", "	private HudAtlasLayout() {", "	}", "}", ""]
    with open(JAVA_LAYOUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))


def main():
    items = [
        ("plate_shadow", gen_plate_shadow()), ("emblem_frame", gen_emblem_frame()), ("emblem_fill", gen_emblem_fill()),
        ("emblem_icons", gen_emblem_icons()), ("timer_ring", gen_timer_ring()), ("ring_shatter", gen_ring_shatter()),
        ("bar_frame", gen_bar_frame()), ("bar_mask", gen_bar_mask()), ("bar_fill", gen_bar_fill()), ("bar_overlay", gen_bar_overlay()),
        ("bar_sheen", gen_bar_sheen()), ("gem", gen_gem()), ("hatch", gen_hatch()), ("pixel", gen_pixel()), ("slot_frame", gen_slot_frame()),
        ("slot_glow", gen_slot_glow()), ("cooldown_sweep", gen_cooldown_sweep()), ("key_tab", gen_key_tab()),
        ("ability_icons", gen_ability_icons()), ("voice_mic", gen_voice_mic()), ("title_band", gen_title_band()),
        ("title_glow", gen_title_glow()), ("toast_line", gen_toast_line()),
    ]
    for name, arr in items:
        if name in NOT_IN_ATLAS:
            save(os.path.join(HUD, name + ".png"), arr)
    atlas, layout = pack_atlas(items)
    save(os.path.join(HUD, "hud_atlas.png"), atlas)
    write_layout(layout)
    save(os.path.join(SPR, "toast.png"), gen_toast_sprite())
    with open(os.path.join(SPR, "toast.png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": 16, "height": 16, "border": 4}}}, f, indent=2)
        f.write("\n")
    if "--sheet" in sys.argv:
        contact_sheet(sys.argv[sys.argv.index("--sheet") + 1], items)
    print("hud textures written to", HUD, "atlas fill", sum(v[2] * v[3] for v in layout.values()), "of", ATLAS_SIZE * ATLAS_SIZE)


if __name__ == "__main__":
    main()
