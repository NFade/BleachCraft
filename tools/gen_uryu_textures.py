#!/usr/bin/env python
"""Uryu (Quincy) FX textures, design/URYU_VFX_STORYBOARD.md section 4.2 and 4.3 (U-FX 0). Deterministic (fixed seeds), Pillow + numpy only.

Run:  python -I tools/gen_uryu_textures.py            (writes into mod/src/main/resources/assets/reiatsu_test)
      python -I tools/gen_uryu_textures.py --sheet X  (also writes a contact sheet PNG to X, for review only)

Same helper functions and rules as tools/gen_fx_textures.py (1.3): additive textures are RGB premultiplied on black (alpha ignored by
ONE/ONE blending, fade = vertex colour); alpha textures are RGBA white or palette, tinted by vertex colour. Pixel-art sprites (particles,
scars) are nearest filtered and alpha-quantised to 8 levels; glow sprites are smooth. This script NEVER touches fx_glow.png (the Rukia and
Byakuya atlas, owned by gen_fx_textures.py): the Quincy sprites live in their own 512 x 256 atlas uryu_glow.png.

NOT generated here (they come after the Blender models, U-FX 1): textures/fx/uryu_vein_skin.png and textures/item/uryu_fx_diffuse.png /
uryu_fx_emissive.png.

The rect table URYU_RECTS below is the single source for the Java side too: core/fx/GlowAtlasTable.java repeats it and the unit test
GlowAtlasTableTest parses this file, the generated uryu_glow_atlas.json and the PNG and fails when they differ.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test")

# Palette of the storyboard 1.1
Q0, Q1, Q2, Q3, Q4, Q5 = "#FFFFFF", "#E4FAFF", "#9DEBFF", "#4FC8FF", "#1F8FE0", "#0E4FA8"


# ----------------------------------------------------------------------------------------------- helpers (copied from gen_fx_textures.py)
def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def clamp(x, lo=0.0, hi=1.0):
    return np.minimum(np.maximum(x, lo), hi)


def smoothstep(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


def gauss(x, s):
    return np.exp(-(x / s) ** 2)


def grid(w, h):
    """Pixel-centre coordinates relative to the sprite centre c = (w-1)/2 (design: r = distance to c)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    return xs - (w - 1) / 2.0, ys - (h - 1) / 2.0


def lerp(a, b, t):
    return a + (b - a) * t


def blur(a, sigma):
    """Separable gaussian blur of a float array with zero padding."""
    if sigma <= 0:
        return a
    rad = int(math.ceil(sigma * 3))
    k = np.exp(-0.5 * (np.arange(-rad, rad + 1) / sigma) ** 2)
    k /= k.sum()
    p = np.pad(a, rad, mode="constant")
    out = np.zeros_like(p)
    for i, kv in enumerate(k):
        out += kv * np.roll(p, i - rad, axis=1)
    p2 = out
    out = np.zeros_like(p2)
    for i, kv in enumerate(k):
        out += kv * np.roll(p2, i - rad, axis=0)
    return out[rad:-rad, rad:-rad]


def value_noise(w, h, cell, rng, tile=False):
    gw = int(math.ceil(w / cell)) + 2
    gh = int(math.ceil(h / cell)) + 2
    g = rng.random((gh, gw)).astype(np.float32)
    if tile:
        g[:, -1] = g[:, 0]
        g[-1, :] = g[0, :]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    fx = xs / cell
    fy = ys / cell
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    tx = fx - x0
    ty = fy - y0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    a = g[y0, x0]
    b = g[y0, x0 + 1]
    c = g[y0 + 1, x0]
    d = g[y0 + 1, x0 + 1]
    return lerp(lerp(a, b, tx), lerp(c, d, tx), ty)


def seg_dist(x, y, x0, y0, x1, y1):
    dx = x1 - x0
    dy = y1 - y0
    l2 = dx * dx + dy * dy
    t = clamp(((x - x0) * dx + (y - y0) * dy) / l2) if l2 > 0 else 0.0
    return np.hypot(x - (x0 + t * dx), y - (y0 + t * dy))


def stamp_line(arr, x0, y0, x1, y1, val=1.0, step=0.4):
    """Hard 1 px line (nearest pixels) into a float array, coordinates in pixel indices."""
    n = max(2, int(math.hypot(x1 - x0, y1 - y0) / step))
    for i in range(n + 1):
        t = i / n
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        if 0 <= x < arr.shape[1] and 0 <= y < arr.shape[0]:
            arr[y, x] = max(arr[y, x], val)


def quant8(a):
    return np.round(clamp(a) * 7.0) / 7.0


def to_rgba_u8(rgb, alpha):
    out = np.zeros(alpha.shape + (4,), np.uint8)
    out[..., :3] = np.clip(np.round(rgb), 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    return out


def save(path, arr):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = "RGBA" if arr.shape[-1] == 4 else "RGB"
    Image.fromarray(arr, mode).save(path, optimize=True)


def edge_window(w, h, mx, my):
    """0 at the cell border rising smoothly to 1 at mx / my pixels inside (additive cells must end at zero, else a box shows)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    wx = smoothstep(0.0, mx, np.minimum(xs + 0.5, w - xs - 0.5)) if mx > 0 else 1.0
    wy = smoothstep(0.0, my, np.minimum(ys + 0.5, h - ys - 0.5)) if my > 0 else 1.0
    return wx * wy


def rwin(r, r0, r1):
    """Radial window: 1 inside r0, 0 outside r1 (glow of round sprites must be zero on the cell border)."""
    return 1.0 - smoothstep(r0, r1, r)


def pent_verts(radius, rot_deg=-90.0):
    return [(radius * math.cos(math.radians(rot_deg + 72 * k)), radius * math.sin(math.radians(rot_deg + 72 * k))) for k in range(5)]


def wrap_dist(a, b, size):
    """Shortest distance on a torus axis (for tileable specks)."""
    d = np.abs(a - b)
    return np.minimum(d, size - d)


# ----------------------------------------------------------------------------------------------- glow atlas 512 x 256 (4.3)
# name -> (x, y, w, h); the same table is core/fx/GlowAtlasTable.java and uryu_glow_atlas.json
URYU_RECTS = {
    "zeichen_star": (0, 0, 128, 128),
    "web_wheel": (128, 0, 128, 128),
    "burst_rays": (256, 0, 128, 128),
    "crescent": (384, 0, 128, 32),
    "platform_disc": (384, 32, 128, 64),
    "spark_cluster": (384, 96, 128, 32),
    "zeichen_cross": (0, 128, 64, 64),
    "pent_line": (64, 128, 64, 64),
    "chevrons": (128, 128, 64, 64),
    "diamond_star": (192, 128, 64, 64),
    "flake": (256, 128, 32, 64),
    "needle": (288, 128, 32, 64),
    "vein_trace": (320, 128, 64, 32),
    "glitter": (320, 160, 64, 32),
    "dash": (384, 128, 64, 16),
    "dash_b": (384, 144, 64, 16),
    "chain": (384, 160, 64, 32),
    "lens": (448, 128, 64, 128),
    "flechette_trail": (0, 192, 128, 16),
    "beam_glitter": (0, 208, 128, 16),
    "rune_band": (0, 224, 128, 32),
    "star_field": (128, 192, 128, 64),
    "fence": (256, 192, 128, 64),
    "spare": (384, 192, 64, 64),
}
ATLAS_W, ATLAS_H = 512, 256


def sp_zeichen_star():
    """The Quincy sign (ZK): pentagram with a ring at every point, a dashed pentagon behind it, two outer rings and five tick marks."""
    n = 128
    x, y = grid(n, n)
    r = np.hypot(x, y)
    base = np.maximum(gauss(r - 60, 0.9), gauss(r - 54, 0.6))
    verts = pent_verts(46)
    for k in range(5):  # pentagram {5/2}
        (x0, y0), (x1, y1) = verts[k], verts[(k + 2) % 5]
        base = np.maximum(base, gauss(seg_dist(x, y, x0, y0, x1, y1), 0.9))
    for vx, vy in verts:  # a ring r 6 at every point
        base = np.maximum(base, gauss(np.hypot(x - vx, y - vy) - 6, 0.8))
    for k in range(5):  # dashed pentagon behind: dash 6 px, gap 4 px, I 0.55
        (x0, y0), (x1, y1) = verts[k], verts[(k + 1) % 5]
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        t = clamp(((x - x0) * dx + (y - y0) * dy) / (length * length))
        s = t * length
        d = np.hypot(x - (x0 + t * dx), y - (y0 + t * dy))
        base = np.maximum(base, 0.55 * gauss(d, 0.7) * (np.mod(s, 10.0) < 6.0))
    for vx, vy in verts:  # tick marks outside, r 54..60, at the angle of every point
        l = math.hypot(vx, vy)
        ux, uy = vx / l, vy / l
        base = np.maximum(base, gauss(seg_dist(x, y, ux * 54, uy * 54, ux * 60, uy * 60), 0.7))
    return clamp(0.9 * base + 0.35 * blur(base, 2.5)) * rwin(r, 61.5, 63.8)


def sp_web_wheel():
    """The spoked wheel that opens the bow: 8 spokes, 4 rings, a web of sagging arcs, a centre ring."""
    n = 128
    x, y = grid(n, n)
    r = np.hypot(x, y)
    base = np.zeros((n, n), np.float32)
    for k in range(8):
        ang = math.pi / 4 * k
        ca, sa = math.cos(ang), math.sin(ang)
        base = np.maximum(base, gauss(seg_dist(x, y, ca * 8, sa * 8, ca * 60, sa * 60), 0.8))
    for rad in (18, 34, 50, 60):
        base = np.maximum(base, gauss(r - rad, 0.7))
    for rad in (34, 50):  # web arcs between the spokes, sagging 4 px towards the centre
        for k in range(8):
            a0 = math.pi / 4 * k
            pts = []
            for i in range(17):
                t = i / 16.0
                ang = a0 + math.pi / 4 * t
                rr = rad - 4.0 * math.sin(math.pi * t)
                pts.append((rr * math.cos(ang), rr * math.sin(ang)))
            for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
                base = np.maximum(base, gauss(seg_dist(x, y, x0, y0, x1, y1), 0.6))
    base = np.maximum(base, gauss(r - 6, 0.8))
    return clamp(0.9 * base + 0.35 * blur(base, 2.0)) * rwin(r, 61.5, 63.8)


def sp_burst_rays():
    """Jagged radial burst: 16 spikes of seeded length 40..62 px, alternately bright and dim, with a hot core."""
    rng = np.random.default_rng(2024)
    n = 128
    x, y = grid(n, n)
    r = np.hypot(x, y)
    lengths = rng.uniform(40, 62, 16)
    jitter = rng.uniform(-0.06, 0.06, 16)
    base = np.zeros((n, n), np.float32)
    for k in range(16):
        ang = 2 * math.pi * k / 16 + jitter[k]
        ca, sa = math.cos(ang), math.sin(ang)
        along = x * ca + y * sa
        perp = -x * sa + y * ca
        length = lengths[k]
        w = np.maximum(6.0 * (1 - clamp(along / length)), 0.3)
        prof = gauss(perp, 0.5 * w + 0.4) * (along > 0) * np.power(clamp(1 - along / length), 0.6)
        base = np.maximum(base, (0.8 if k % 2 == 0 else 0.5) * prof)
    return clamp(np.maximum(base, np.exp(-(r / 10.0) ** 2))) * rwin(r, 61.0, 63.8)


def sp_crescent():
    """Crescent slash arc seen flat: x runs along the arc, the head is at x = 127 (brighter). Circle radius 90 below the cell (a frown)."""
    rng = np.random.default_rng(11)
    w, h = 128, 32
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    radius = 90.0
    y0 = 8.5 + radius
    d = np.abs(np.hypot(xs - 63.5, ys - y0) - radius)
    dxn = np.abs(xs - 63.5) / 63.5
    hw = 7.0 * np.power(clamp(1 - dxn ** 2), 0.8)
    ragged = hw + 1.0 * (value_noise(w, h, 3, rng) - 0.5) * (hw > 0.5)
    body = clamp((ragged - d) / 1.4)
    core = clamp((0.35 * hw - d) / 1.0)
    head = 0.55 + 0.45 * (xs / 127.0) ** 1.5
    i = np.maximum(0.7 * body, core) * head
    speck = (rng.random((h, w)) > 0.965) * (d < hw + 3.5) * 0.15
    i = np.maximum(i, speck)
    return clamp(i) * edge_window(w, h, 5, 1.5)


def sp_platform_disc():
    """Hirenkyaku platform: a flat ellipse, bright rim, white centre, specks outside the rim."""
    rng = np.random.default_rng(23)
    w, h = 128, 64
    x, y = grid(w, h)
    e = np.hypot(x / 60.0, y / 28.0)
    r = np.hypot(x, y)
    i = 0.55 * smoothstep(1.0, 0.5, e)
    i = np.maximum(i, gauss(e - 1.0, 0.03))
    i = np.maximum(i, np.exp(-(r / 8.0) ** 2))
    for k in range(12):
        ang = rng.uniform(0, 2 * math.pi)
        ee = rng.uniform(1.05, 1.11)
        px, py = 60 * ee * math.cos(ang), 28 * ee * math.sin(ang)
        i = np.maximum(i, rng.uniform(0.5, 1.0) * gauss(np.hypot(x - px, y - py), 0.9))
    i = clamp(i) * edge_window(w, h, 3, 3)
    i[0, :] = 0
    i[-1, :] = 0
    i[:, 0] = 0
    i[:, -1] = 0
    return i


def star_glint(x, y, px, py, length, sigma=0.6):
    """A small 4-point glint at (px, py) in the coordinates of x, y."""
    dx, dy = x - px, y - py
    r = np.hypot(dx, dy)
    ray_h = gauss(dy, sigma) * clamp(1 - np.abs(dx) / length) ** 1.5
    ray_v = gauss(dx, sigma) * clamp(1 - np.abs(dy) / length) ** 1.5
    return np.maximum(np.maximum(ray_h, ray_v), np.exp(-(r / 1.1) ** 2))


def sp_spark_cluster():
    rng = np.random.default_rng(77)
    w, h = 128, 32
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    i = np.zeros((h, w), np.float32)
    for k in range(40):
        px, py = rng.uniform(4, w - 4), rng.uniform(4, h - 4)
        sig = rng.uniform(0.6, 1.1)
        i = np.maximum(i, rng.uniform(0.5, 1.0) * gauss(np.hypot(xs - px, ys - py), sig))
    for k in range(6):
        px, py = rng.uniform(8, w - 8), rng.uniform(7, h - 7)
        i = np.maximum(i, rng.uniform(0.7, 1.0) * star_glint(xs, ys, px, py, 4.0))
    return clamp(i) * edge_window(w, h, 2, 2)


def sp_zeichen_cross():
    """The Quincy cross of the pendant: a ring and four arms, bent back by 20 degrees, with a flared end and a ring r 2 at every end."""
    n = 64
    x, y = grid(n, n)
    r = np.hypot(x, y)
    base = gauss(r - 12, 0.8)
    for k in range(4):
        ang = math.radians(90 * k - 90)
        # two segments: 14 px radial, then 10 px swept back by 20 degrees (the pendant's angled arms)
        ca, sa = math.cos(ang), math.sin(ang)
        mx, my = ca * 14, sa * 14
        a2 = ang + math.radians(20)
        ex, ey = mx + math.cos(a2) * 10, my + math.sin(a2) * 10
        base = np.maximum(base, gauss(seg_dist(x, y, 0, 0, mx, my), 0.8))
        d2 = seg_dist(x, y, mx, my, ex, ey)
        t = clamp(np.hypot(x - mx, y - my) / 10.0)
        base = np.maximum(base, gauss(d2, 0.8 + 0.8 * t))  # flares towards the end
        base = np.maximum(base, gauss(np.hypot(x - ex, y - ey) - 2.5, 0.6))
    return clamp(0.95 * base + 0.25 * blur(base, 1.5)) * rwin(r, 29.5, 31.8)


def sp_pent_line():
    n = 64
    x, y = grid(n, n)
    r = np.hypot(x, y)
    verts = pent_verts(28)
    base = np.zeros((n, n), np.float32)
    for k in range(5):
        (x0, y0), (x1, y1) = verts[k], verts[(k + 1) % 5]
        base = np.maximum(base, 0.9 * gauss(seg_dist(x, y, x0, y0, x1, y1), 0.9))
        base = np.maximum(base, 0.4 * gauss(seg_dist(x, y, 0, 0, x0, y0), 0.7))
    for vx, vy in verts:
        base = np.maximum(base, gauss(np.hypot(x - vx, y - vy), 2.5))
    return clamp(base + 0.3 * blur(base, 1.2)) * rwin(r, 30.0, 31.8)


def sp_chevrons():
    """Chest emblem: three stacked V chevrons (point down) inside a shield outline (flat top, pointed bottom)."""
    n = 64
    x, y = grid(n, n)
    px, py = x + 31.5, y + 31.5  # pixel coordinates
    base = np.zeros((n, n), np.float32)
    cx = 31.5
    for k in range(3):
        apex = 24 + 8 * k
        pts = [(cx - 18, apex - 0.55 * 18), (cx, apex), (cx + 18, apex - 0.55 * 18)]
        for off in (-1.5, 1.5):  # 1 px outlines of a 3 px thick V
            for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
                base = np.maximum(base, gauss(seg_dist(px, py, x0, y0 + off, x1, y1 + off), 0.6))
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            base = np.maximum(base, 0.35 * gauss(seg_dist(px, py, x0, y0, x1, y1), 0.9))
    shield = [(cx - 23, 6), (cx + 23, 6), (cx + 23, 40), (cx, 58), (cx - 23, 40), (cx - 23, 6)]
    for (x0, y0), (x1, y1) in zip(shield, shield[1:]):
        base = np.maximum(base, 0.8 * gauss(seg_dist(px, py, x0, y0, x1, y1), 0.7))
    return clamp(0.95 * base + 0.25 * blur(base, 1.0)) * edge_window(n, n, 2, 2)


def sp_diamond_star():
    """Four-point diamond star: two crossed tapered spikes (length 31, half width 5), short diagonals, hot core."""
    n = 64
    x, y = grid(n, n)
    r = np.hypot(x, y)
    ax, ay = np.abs(x), np.abs(y)
    hv = 5.0 * clamp(1 - ay / 31.0) + 0.3
    spike_v = clamp(1 - ax / hv) * np.power(clamp(1 - ay / 31.0), 0.7)
    hh = 5.0 * clamp(1 - ax / 31.0) + 0.3
    spike_h = clamp(1 - ay / hh) * np.power(clamp(1 - ax / 31.0), 0.7)
    i = np.maximum(spike_v, spike_h)
    for sgn in (1, -1):
        for ang in (math.pi / 4, -math.pi / 4):
            ca, sa = math.cos(ang), math.sin(ang)
            along = x * ca + y * sa
            perp = -x * sa + y * ca
            i = np.maximum(i, 0.55 * gauss(perp, 0.8) * clamp(1 - np.abs(along) / 12.0) ** 2)
    i = np.maximum(i, np.exp(-(r / 4.0) ** 2))
    return clamp(i) * rwin(r, 30.0, 31.8)


def flake_shape(w, h, ox, top, tail):
    """Float array w x h of the flake leaf along y: tip at `top`, comb end at `tail`; spine, vanes and five barbs."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = xs - ox
    ad = np.abs(dx)
    leaf_end = top + (tail - top) * 0.78
    t = clamp((ys - top) / (leaf_end - top))
    hw = 5.0 * np.power(np.maximum(np.sin(math.pi * np.power(t, 0.85)), 0), 0.8) * (ys >= top) * (ys <= leaf_end)
    vane = np.where(ad <= hw, 0.55 * np.power(clamp(1 - ad / np.maximum(hw, 1e-3)), 0.5) + 0.15, 0.0)
    spine = gauss(dx, 0.7) * (ys >= top) * (ys <= tail)
    i = np.maximum(vane, spine)
    for k in range(5):  # a comb of five barbs at the tail end
        yb = leaf_end - 2 + k * (tail - leaf_end + 2) / 4.0
        for sgn in (-1, 1):
            i = np.maximum(i, 0.95 * gauss(seg_dist(xs, ys, ox, yb, ox + sgn * 6.5, yb + 4.5), 0.85))
    return i


def sp_flake():
    w, h = 32, 64
    return clamp(blur(flake_shape(w, h, 15.5, 4, 60), 0.6) * 1.3) * edge_window(w, h, 2, 2)


def sp_needle():
    w, h = 32, 64
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = xs - 15.5
    core = gauss(dx, 0.7) * (ys >= 6) * (ys <= 58)
    i = core * (0.55 + 0.45 * clamp((ys - 6) / 10.0)) * 0.85
    i = np.maximum(i, star_glint(xs, ys, 15.5, 8.0, 6.0, 0.6))
    for sgn in (-1, 1):  # chevron fletching at the tail, swept back
        i = np.maximum(i, 0.8 * gauss(seg_dist(xs, ys, 15.5, 52.0, 15.5 + sgn * 3.5, 58.0), 0.6))
    return clamp(i) * edge_window(w, h, 2, 2)


def sp_vein_trace():
    """Horizontal branching trace strip (circuit-like): angular segments of 3..6 px, forks."""
    rng = np.random.default_rng(31)
    w, h = 64, 32
    arr = np.zeros((h, w), np.float32)

    def walk(x, y, steps, depth):
        for s in range(steps):
            dirs = [(1, 0), (1, 1), (1, -1)] if depth == 0 else [(1, 0), (1, 1), (1, -1), (0, 1), (0, -1)]
            dx, dy = dirs[int(rng.integers(0, len(dirs)))]
            seg = int(rng.integers(3, 7))
            nx, ny = x + dx * seg, y + dy * seg
            ny = min(max(ny, 5), h - 6)
            stamp_line(arr, x, y, nx, ny, 1.0)
            if rng.random() < 0.35 and depth < 2:
                walk(nx, ny, int(rng.integers(1, 3)), depth + 1)
            x, y = nx, ny
            if x >= w - 3:
                break

    walk(2, h / 2, 14, 0)
    walk(2, h / 2 + 2, 8, 1)
    i = clamp(arr + 0.5 * blur(arr, 0.9))
    return i * edge_window(w, h, 4, 3)


def sp_glitter():
    """Tileable stardust strip (wraps in x and y)."""
    rng = np.random.default_rng(55)
    w, h = 64, 32
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    i = np.zeros((h, w), np.float32)
    for k in range(70):
        px, py = rng.uniform(0, w), rng.uniform(4, h - 4)
        sig = rng.uniform(0.55, 1.0)
        d = np.hypot(wrap_dist(xs, px, w), ys - py)
        i = np.maximum(i, rng.uniform(0.3, 1.0) * gauss(d, sig))
    return clamp(i) * edge_window(w, h, 0, 3)


def sp_dash():
    w, h = 64, 16
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dy = ys - 7.5
    i = gauss(dy, 0.9) * clamp((xs - 4) / 40.0) * (1 - smoothstep(58, 63, xs))
    head = np.exp(-(np.hypot(xs - 56, dy) / 1.7) ** 2)
    star = np.maximum(gauss(dy, 0.5) * clamp(1 - np.abs(xs - 56) / 4.0), gauss(xs - 56, 0.5) * clamp(1 - np.abs(dy) / 3.0))
    return clamp(np.maximum(np.maximum(i, head), 0.8 * star)) * edge_window(w, h, 1, 2)


def sp_dash_b():
    """Shorter dash with a forked tail."""
    w, h = 64, 16
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dy = ys - 7.5
    fork = clamp((34.0 - xs) / 10.0) * 2.4
    body = np.maximum(gauss(dy - fork, 0.8), gauss(dy + fork, 0.8)) * clamp((xs - 22) / 14.0) * (1 - smoothstep(58, 63, xs))
    body = np.maximum(body, gauss(dy, 0.9) * clamp((xs - 30) / 10.0) * (1 - smoothstep(58, 63, xs)))
    head = np.exp(-(np.hypot(xs - 56, dy) / 1.7) ** 2)
    return clamp(np.maximum(body * 0.95, head)) * edge_window(w, h, 1, 2)


def sp_chain():
    """Three interlocking oval links, 1 px outline, tileable along x (links wrap)."""
    w, h = 64, 32
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dy = ys - 15.5
    i = np.zeros((h, w), np.float32)
    period = w / 3.0
    for k in range(3):
        cxk = 10.0 + k * period
        for off in (-w, 0, w):
            ex = (xs - (cxk + off)) / 13.0
            ey = dy / 6.0
            e = np.hypot(ex, ey)
            ring = gauss((e - 1.0) * 6.0, 0.6) * (0.75 if k % 2 else 1.0)
            i = np.maximum(i, ring)
    return clamp(i) * edge_window(w, h, 0, 3)


def sp_lens():
    """Vertical vesica (the lens aura of the Vollstaendig gather): inside of two overlapping circles, a rim, radial dashes inside the rim."""
    w, h = 64, 128
    x, y = grid(w, h)
    big_r, off = 78.0, 50.0
    d1 = np.hypot(x - off, y) - big_r  # inside the circle centred at +off when negative
    d2 = np.hypot(x + off, y) - big_r
    sd = np.maximum(d1, d2)  # signed distance of the intersection (negative inside)
    inside = smoothstep(0.5, -1.5, sd)
    i = 0.5 * inside
    i = np.maximum(i, 0.9 * gauss(sd, 1.2))
    # 20 short radial dashes inside the rim, from the rim towards the centre
    half_h = math.sqrt(big_r ** 2 - off ** 2)
    for k in range(20):
        ang = 2 * math.pi * k / 20 + 0.1
        # a point on the rim along this direction from the centre
        ca, sa = math.cos(ang), math.sin(ang)
        lo, hi = 0.0, 80.0
        for _ in range(30):
            mid = (lo + hi) / 2
            px, py = ca * mid, sa * mid
            ins = max(math.hypot(px - off, py) - big_r, math.hypot(px + off, py) - big_r) < 0
            lo, hi = (mid, hi) if ins else (lo, mid)
        rim = lo
        i = np.maximum(i, 0.5 * gauss(seg_dist(x, y, ca * (rim - 6.0), sa * (rim - 6.0), ca * (rim - 1.5), sa * (rim - 1.5)), 0.6))
    return clamp(i) * edge_window(w, h, 2, 2)


def sp_flechette_trail():
    w, h = 128, 16
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dy = ys - 7.5
    i = gauss(dy, 1.6) * np.power(xs / 127.0, 0.8)
    for xb in range(20, 128, 20):  # six barbs, swept back towards the tail
        for sgn in (-1, 1):
            i = np.maximum(i, 0.5 * gauss(seg_dist(xs, ys, xb, 7.5, xb - 3.0, 7.5 + sgn * 3.2), 0.6))
    return clamp(i) * edge_window(w, h, 0, 1.5)


def sp_beam_glitter():
    """Tileable along x. The base is faded to zero at the top and bottom of the cell (a flat 0.35 floor would show a box edge)."""
    rng = np.random.default_rng(66)
    w, h = 128, 16
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dy = ys - 7.5
    base = (0.35 + 0.35 * gauss(dy, 4.0)) * smoothstep(0.0, 5.0, np.minimum(ys + 0.5, h - ys - 0.5))
    i = base.astype(np.float32)
    for k in range(40):
        px, py = rng.uniform(0, w), rng.uniform(3, h - 3)
        d = np.hypot(wrap_dist(xs, px, w), ys - py)
        i = np.maximum(i, rng.uniform(0.5, 1.0) * gauss(d, 0.7))
    i = np.maximum(i, 0.9 * gauss(dy, 1.2))
    return clamp(i) * edge_window(w, h, 0, 1.5)


def sp_rune_band():
    """Tileable band (period 32): top and bottom lines, between them a ZK mark, a chevron and three dots."""
    w, h = 128, 32
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    i = np.maximum(gauss(ys - 2.5, 0.8), gauss(ys - 28.5, 0.8)) * 0.9
    star = pent_verts(7.0)
    for p in range(4):
        ox = p * 32.0
        for k in range(5):  # small pentagram
            (x0, y0), (x1, y1) = star[k], star[(k + 2) % 5]
            i = np.maximum(i, 0.8 * gauss(seg_dist(xs, ys, ox + 8 + x0, 15.5 + y0, ox + 8 + x1, 15.5 + y1), 0.65))
        i = np.maximum(i, 0.8 * gauss(np.hypot(xs - (ox + 8), ys - 15.5) - 9.0, 0.6))
        # chevron (point down)
        for (x0, y0), (x1, y1) in (((ox + 16, 12), (ox + 20, 17)), ((ox + 20, 17), (ox + 24, 12))):
            i = np.maximum(i, 0.8 * gauss(seg_dist(xs, ys, x0, y0, x1, y1), 0.65))
        # three dots
        for dx in (26.0, 28.5, 31.0):
            i = np.maximum(i, 0.8 * gauss(np.hypot(xs - (ox + dx), ys - 15.5), 0.8))
    return clamp(i) * edge_window(w, h, 0, 1.5)


def sp_star_field():
    """Tileable stardust field: 220 specks, 14 glints (wraps in both axes)."""
    rng = np.random.default_rng(91)
    w, h = 128, 64
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    i = np.zeros((h, w), np.float32)
    for k in range(220):
        px, py = rng.uniform(0, w), rng.uniform(3, h - 3)
        sig = rng.uniform(0.5, 1.2)
        d = np.hypot(wrap_dist(xs, px, w), ys - py)
        i = np.maximum(i, rng.uniform(0.4, 1.0) * gauss(d, sig))
    for k in range(14):
        px, py = rng.uniform(0, w), rng.uniform(6, h - 6)
        ddx = xs - px
        ddx = ddx - w * np.round(ddx / w)
        i = np.maximum(i, rng.uniform(0.7, 1.0) * star_glint(ddx, ys, 0.0, py, 5.0))
    return clamp(i) * edge_window(w, h, 0, 2)


def sp_fence():
    """Sprenger fence curtain: bright at the foot, six vertical lines, a top line, a faint pentagon pattern; tileable along x."""
    w, h = 128, 64
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    yb = (h - 1) - ys  # distance from the foot
    i = 0.8 * np.exp(-yb / 14.0)
    for k in range(6):
        cx = (k + 0.5) * w / 6.0
        i = np.maximum(i, 0.5 * gauss(wrap_dist(xs, cx, w), 0.7) * (0.35 + 0.65 * np.exp(-yb / 40.0)))
    i = np.maximum(i, gauss(ys - 1.5, 0.8))
    v = pent_verts(8.0)
    for ox in (16.0, 80.0):
        for k in range(5):
            (x0, y0), (x1, y1) = v[k], v[(k + 1) % 5]
            i = np.maximum(i, 0.25 * gauss(seg_dist(xs, ys, ox + x0, 30 + y0, ox + x1, 30 + y1), 0.7))
    return clamp(i)


SPRITES = {
    "zeichen_star": sp_zeichen_star, "web_wheel": sp_web_wheel, "burst_rays": sp_burst_rays, "crescent": sp_crescent,
    "platform_disc": sp_platform_disc, "spark_cluster": sp_spark_cluster, "zeichen_cross": sp_zeichen_cross,
    "pent_line": sp_pent_line, "chevrons": sp_chevrons, "diamond_star": sp_diamond_star, "flake": sp_flake, "needle": sp_needle,
    "vein_trace": sp_vein_trace, "glitter": sp_glitter, "dash": sp_dash, "dash_b": sp_dash_b, "chain": sp_chain, "lens": sp_lens,
    "flechette_trail": sp_flechette_trail, "beam_glitter": sp_beam_glitter, "rune_band": sp_rune_band, "star_field": sp_star_field,
    "fence": sp_fence,
}


def check_rects():
    """No overlap, inside the atlas, every sprite has a rect (and only the reserved one has none)."""
    taken = np.zeros((ATLAS_H, ATLAS_W), np.int32)
    for name, (x, y, w, h) in URYU_RECTS.items():
        if x < 0 or y < 0 or x + w > ATLAS_W or y + h > ATLAS_H:
            raise SystemExit("atlas rect out of bounds: " + name)
        if taken[y:y + h, x:x + w].any():
            raise SystemExit("glow atlas overlap at " + name)
        taken[y:y + h, x:x + w] = 1
    missing = set(SPRITES) - set(URYU_RECTS)
    extra = set(URYU_RECTS) - set(SPRITES) - {"spare"}
    if missing or extra:
        raise SystemExit("sprite / rect mismatch: " + str(sorted(missing | extra)))


def gen_glow_atlas():
    check_rects()
    atlas = np.zeros((ATLAS_H, ATLAS_W), np.float32)
    for name, fn in SPRITES.items():
        x, y, w, h = URYU_RECTS[name]
        s = fn()
        assert s.shape == (h, w), (name, s.shape, (h, w))
        region = atlas[y:y + h, x:x + w]
        if region.max() > 0:
            raise SystemExit("glow atlas overlap at " + name)
        atlas[y:y + h, x:x + w] = s
    g = np.round(clamp(atlas) * 255).astype(np.uint8)
    rgb = np.stack([g, g, g], -1)
    save(os.path.join(ASSETS, "textures", "fx", "uryu_glow.png"), rgb)
    meta = {k: {"x": v[0], "y": v[1], "w": v[2], "h": v[3]} for k, v in URYU_RECTS.items()}
    with open(os.path.join(ASSETS, "textures", "fx", "uryu_glow_atlas.json"), "w", newline="\n") as fh:
        json.dump({"width": ATLAS_W, "height": ATLAS_H, "sprites": meta}, fh, indent=2)
        fh.write("\n")
    return atlas


# ----------------------------------------------------------------------------------------------- particle sprites (pixel art, 8 alpha levels)
def gen_reishi_mote():
    """Teardrop mote: a dot with a short tail along +y (tail 4, 5, 3.5, 5 px), core clamp(1 - r/2.2)^1.4, RGB lerp(Q2, Q0, core)."""
    frames = []
    for tail in (4.0, 5.0, 3.5, 5.0):
        x, y = grid(16, 16)
        y = y + 1.5  # dot a little above the middle so the tail fits
        r = np.hypot(x, y)
        core = clamp(1 - r / 2.2) ** 1.4
        tail_a = gauss(x, 0.75) * clamp(1 - y / tail) * (y > 0)
        a = np.maximum(core, 0.75 * tail_a)
        rgb = lerp(hexrgb(Q2)[None, None, :], hexrgb(Q0)[None, None, :], core[..., None])
        frames.append(to_rgba_u8(rgb, quant8(a)))
    return frames


def gen_flechette():
    """The flake leaf as a particle: spine 1 px (white), two vanes, a comb of barbs at the tail, rim Q1, body Q3 alpha 230; variants 0/20/-20 deg, 3/4/5 barbs."""
    frames = []
    for rot, barbs in ((0.0, 3), (20.0, 4), (-20.0, 5)):
        x, y = grid(16, 16)
        ang = math.radians(rot)
        ca, sa = math.cos(ang), math.sin(ang)
        # u along the flake (tip at -u, tail at +u), v across
        u = x * ca + y * sa
        v = -x * sa + y * ca
        t = clamp((u + 7.0) / 10.5)  # 0 at the tip, 1 at the end of the leaf (u = 3.5)
        hw = 2.4 * np.power(np.maximum(np.sin(math.pi * np.power(t, 0.9)), 0), 0.8)
        leaf = (np.abs(v) <= hw) & (u >= -7.0) & (u <= 3.5)
        comb = np.zeros_like(leaf)
        for k in range(barbs):  # barbs: 1 px notches 1.5 px apart on both sides of the spine at the tail
            ub = 2.0 + k * 1.5
            comb |= (np.abs(u - ub) < 0.6) & (np.abs(v) <= 2.6) & (np.abs(v) >= 0.4)
        spine = (np.abs(v) < 0.55) & (u >= -7.0) & (u <= 7.2)
        body = leaf | comb
        ins = body | spine
        rim = np.zeros_like(ins)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            rim |= ins & ~np.roll(np.roll(ins, dy, 0), dx, 1)
        out = np.zeros((16, 16, 4), np.uint8)
        out[body, :3] = np.round(hexrgb(Q3)).astype(np.uint8)
        out[body, 3] = 230
        out[rim & body, :3] = np.round(hexrgb(Q1)).astype(np.uint8)
        out[spine, :3] = 255
        out[spine, 3] = 255
        frames.append(out)
    return frames


def write_particle(name, frames):
    tex = os.path.join(ASSETS, "textures", "particle")
    for i, f in enumerate(frames):
        save(os.path.join(tex, "%s_%d.png" % (name, i)), f)
    pj = os.path.join(ASSETS, "particles", name + ".json")
    os.makedirs(os.path.dirname(pj), exist_ok=True)
    with open(pj, "w", newline="\n") as fh:
        json.dump({"textures": ["reiatsu_test:%s_%d" % (name, i) for i in range(len(frames))]}, fh, indent=2)
        fh.write("\n")


# ----------------------------------------------------------------------------------------------- scars and glitter
def gen_pentagon_scar():
    """D: pentagon scar with 5 spokes: a 2 px burnt line Q5 alpha 200, glowing cracks, a ZK star imprint at alpha 90, radial falloff smoothstep(64, 54, r)."""
    rng = np.random.default_rng(204)
    n = 128
    c = (n - 1) / 2.0
    burnt = np.zeros((n, n), np.float32)
    verts = [(c + vx, c + vy) for vx, vy in pent_verts(56)]
    for k in range(5):
        (x0, y0), (x1, y1) = verts[k], verts[(k + 1) % 5]
        stamp_line(burnt, x0, y0, x1, y1)
        stamp_line(burnt, x0 + 1, y0, x1 + 1, y1)
        stamp_line(burnt, c, c, x0, y0)
    imprint = np.zeros((n, n), np.float32)
    star = [(c + vx, c + vy) for vx, vy in pent_verts(40)]
    for k in range(5):
        (x0, y0), (x1, y1) = star[k], star[(k + 2) % 5]
        stamp_line(imprint, x0, y0, x1, y1)
    cracks = np.zeros((n, n), np.float32)
    for k in range(26):  # short glowing cracks that leave the burnt lines
        ix, iy = np.nonzero(burnt)
        j = int(rng.integers(0, len(ix)))
        x, y = float(iy[j]), float(ix[j])
        ang = rng.uniform(0, 2 * math.pi)
        for s in range(int(rng.integers(3, 7))):
            ang += math.radians(rng.uniform(-40, 40))
            nx, ny = x + math.cos(ang) * 2, y + math.sin(ang) * 2
            stamp_line(cracks, x, y, nx, ny)
            x, y = nx, ny
    xg, yg = grid(n, n)
    fall = smoothstep(64, 54, np.hypot(xg, yg))
    out = np.zeros((n, n, 4), np.uint8)
    out[imprint > 0] = (*np.round(hexrgb(Q5)).astype(np.uint8), 90)
    out[cracks > 0] = (*np.round(hexrgb(Q3)).astype(np.uint8), 230)
    out[burnt > 0] = (*np.round(hexrgb(Q5)).astype(np.uint8), 200)
    out[..., 3] = np.round(out[..., 3] * fall).astype(np.uint8)
    save(os.path.join(ASSETS, "textures", "fx", "pentagon_scar.png"), out)


def gen_burst_scar():
    """D: small star burst scar, 6 random-walk cracks of 1 px, Q5 alpha 180."""
    rng = np.random.default_rng(8)
    n = 64
    arr = np.zeros((n, n), np.float32)
    c = (n - 1) / 2.0
    for k in range(6):
        ang = 2 * math.pi * k / 6 + rng.uniform(-0.3, 0.3)
        x, y = c, c
        for s in range(int(rng.integers(8, 13))):
            ang += math.radians(rng.uniform(-30, 30))
            nx, ny = x + math.cos(ang) * 2.2, y + math.sin(ang) * 2.2
            stamp_line(arr, x, y, nx, ny)
            x, y = nx, ny
    out = np.zeros((n, n, 4), np.uint8)
    out[arr > 0] = (*np.round(hexrgb(Q5)).astype(np.uint8), 180)
    save(os.path.join(ASSETS, "textures", "fx", "burst_scar.png"), out)


def gen_uryu_glitter():
    """Stardust noise for the wing overglow and the beam body: 140 specks of value 0.3..1.0, 1..2 px, tileable in both axes."""
    rng = np.random.default_rng(140)
    n = 64
    img = np.zeros((n, n), np.float32)
    for k in range(140):
        x, y = int(rng.integers(0, n)), int(rng.integers(0, n))
        v = rng.uniform(0.3, 1.0)
        size = int(rng.integers(1, 3))
        for dy in range(size):
            for dx in range(size):
                img[(y + dy) % n, (x + dx) % n] = max(img[(y + dy) % n, (x + dx) % n], v * (1.0 if dx == 0 and dy == 0 else 0.6))
    g = np.round(clamp(img) * 255).astype(np.uint8)
    save(os.path.join(ASSETS, "textures", "fx", "uryu_glitter.png"), np.stack([g, g, g], -1))


# ----------------------------------------------------------------------------------------------- main
def contact_sheet(path, parts):
    """Review only: the glow atlas (2x), the particle frames, the scars and the glitter."""
    glow = Image.open(os.path.join(ASSETS, "textures", "fx", "uryu_glow.png")).convert("RGB")
    W = 1100
    sheet = Image.new("RGB", (W, 512 + 20 + 280), (0, 0, 0))
    sheet.paste(glow.resize((1024, 512), Image.BILINEAR), (4, 4))
    x = 4
    for name, frames in parts.items():
        for f in frames:
            im = Image.fromarray(f, "RGBA").resize((f.shape[1] * 6, f.shape[0] * 6), Image.NEAREST)
            bg = Image.new("RGBA", im.size, (24, 30, 46, 255))
            bg.alpha_composite(im)
            sheet.paste(bg.convert("RGB"), (x, 530))
            x += im.width + 6
    x = 4
    for nm, size in (("pentagon_scar", 256), ("burst_scar", 128)):
        im = Image.open(os.path.join(ASSETS, "textures", "fx", nm + ".png")).convert("RGBA")
        bg = Image.new("RGBA", im.size, (110, 120, 100, 255))
        bg.alpha_composite(im)
        sheet.paste(bg.convert("RGB").resize((size, size), Image.NEAREST), (x, 640))
        x += size + 8
    gl = Image.open(os.path.join(ASSETS, "textures", "fx", "uryu_glitter.png")).convert("RGB").resize((256, 256), Image.NEAREST)
    sheet.paste(gl, (x, 640))
    sheet.save(path)


def main():
    parts = {"reishi_mote": gen_reishi_mote(), "flechette": gen_flechette()}
    for name, frames in parts.items():
        write_particle(name, frames)
    gen_glow_atlas()
    gen_pentagon_scar()
    gen_burst_scar()
    gen_uryu_glitter()
    if "--sheet" in sys.argv:
        contact_sheet(sys.argv[sys.argv.index("--sheet") + 1], parts)
    print("uryu fx textures written to", ASSETS)


if __name__ == "__main__":
    main()
