#!/usr/bin/env python
"""Phase 6 FX textures (design/VFX_STORYBOARD.md section 1.3). Deterministic (fixed seeds), Pillow + numpy only.

Run:  python -I tools/gen_fx_textures.py            (writes into mod/src/main/resources/assets/reiatsu_test)
      python -I tools/gen_fx_textures.py --sheet X  (also writes a contact sheet PNG to X, for review only)

Rules (1.3): additive textures are RGB premultiplied on black (alpha ignored by ONE/ONE blending, fade = vertex colour);
alpha textures are RGBA white or palette, tinted by vertex colour. Pixel-art sprites (particles) are nearest filtered and
alpha-quantised to 8 levels; glow sprites are smooth.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "reiatsu_test")


# ----------------------------------------------------------------------------------------------- helpers
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


def fbm(w, h, cell, octaves, rng):
    tot = np.zeros((h, w), np.float32)
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        tot += amp * value_noise(w, h, max(1.0, cell / (2 ** o)), rng)
        norm += amp
        amp *= 0.5
    return tot / norm


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


# ----------------------------------------------------------------------------------------------- particle sprites
def rays_alpha(w, h, sf):
    x, y = grid(w, h)
    r = np.hypot(x, y)
    core = clamp(1 - r / 3.0) ** 1.5
    ray_h = gauss(y, 0.7) * clamp(1 - np.abs(x) / (7 * sf))
    ray_v = gauss(x, 0.7) * clamp(1 - np.abs(y) / (7 * sf))
    ray = np.maximum(ray_h, ray_v)
    return core, np.maximum(core, 0.8 * ray)


def gen_frost_mote():
    frames = []
    for sf in (1.0, 0.8, 0.55, 0.8):
        core, a = rays_alpha(16, 16, sf)
        rgb = lerp(hexrgb("#9ED3F0")[None, None, :], hexrgb("#FFFFFF")[None, None, :], core[..., None])
        frames.append(to_rgba_u8(rgb, quant8(a)))
    return frames


def gen_snowflake():
    frames = []
    for variant, (rot, bl) in enumerate(((0, 1.0), (15, 0.8), (30, 1.2))):
        line = np.zeros((16, 16), np.float32)
        c = 7.5
        pts = []
        for k in range(6):
            ang = math.radians(rot + 60 * k - 90)
            ca, sa = math.cos(ang), math.sin(ang)
            stamp_line(line, c, c, c + ca * 6.6, c + sa * 6.6)
            for frac, ln in ((0.52, 2.0 * bl), (0.78, 1.3 * bl)):
                bx, by = c + ca * 6.5 * frac, c + sa * 6.5 * frac
                for sgn in (-1, 1):
                    ba = ang + sgn * math.radians(60)
                    stamp_line(line, bx, by, bx + math.cos(ba) * ln, by + math.sin(ba) * ln)
        # 1 px halo #CFEFFF alpha 110 around the lines
        halo = np.zeros_like(line)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            halo = np.maximum(halo, np.roll(np.roll(line, dy, 0), dx, 1))
        halo = np.where(line > 0, 0, halo)
        out = np.zeros((16, 16, 4), np.uint8)
        out[halo > 0] = (0xCF, 0xEF, 0xFF, 110)
        out[line > 0] = (255, 255, 255, 255)
        frames.append(out)
    return frames


def gen_ice_shard():
    frames = []
    for v in range(4):
        rng = np.random.default_rng(1000 + v)
        n = int(rng.integers(5, 8))
        ang = np.sort(rng.random(n)) * 2 * math.pi
        ang = (np.arange(n) + rng.uniform(-0.3, 0.3, n)) * 2 * math.pi / n
        a_ax, b_ax = 6.6, 6.6 / 2.5
        pts = np.stack([a_ax * np.cos(ang), b_ax * np.sin(ang)], 1)
        rot = math.radians(30)
        cr, sr = math.cos(rot), math.sin(rot)
        rp = np.stack([pts[:, 0] * cr - pts[:, 1] * sr, pts[:, 0] * sr + pts[:, 1] * cr], 1) + 7.5
        x, y = grid(16, 16)
        px = x + 7.5
        py = y + 7.5
        inside = np.ones((16, 16), bool)
        ex, ey = rp[1] - rp[0], rp[2] - rp[1]
        orient = ex[0] * ey[1] - ex[1] * ey[0]
        for i in range(n):
            x0, y0 = rp[i]
            x1, y1 = rp[(i + 1) % n]
            cross = (x1 - x0) * (py - y0) - (y1 - y0) * (px - x0)
            inside &= (cross >= 0) if orient >= 0 else (cross <= 0)
        # split line along the major axis (tip to base)
        ux, uy = cr, sr
        side = (px - 7.5) * (-uy) + (py - 7.5) * ux  # signed distance across the major axis
        out = np.zeros((16, 16, 4), np.uint8)
        light, dark, mid = hexrgb("#CFEFFF"), hexrgb("#7FB8DF"), hexrgb("#8EC9EE")
        for yy in range(16):
            for xx in range(16):
                if not inside[yy, xx]:
                    continue
                col = light if side[yy, xx] < 0 else dark
                col = lerp(mid, col, 0.85)
                out[yy, xx, :3] = np.round(col)
                out[yy, xx, 3] = 210
        ins = out[..., 3] > 0
        up = np.roll(np.roll(ins, 1, 0), 1, 1)  # up-left neighbour
        l = np.roll(ins, 1, 1)
        t = np.roll(ins, 1, 0)
        dn = np.roll(np.roll(ins, -1, 0), -1, 1)
        r = np.roll(ins, -1, 1)
        b = np.roll(ins, -1, 0)
        rim_tl = ins & (~l | ~t | ~up)
        rim_br = ins & (~r | ~b | ~dn) & ~rim_tl
        out[rim_tl] = (255, 255, 255, 235)
        out[rim_br] = (0x2E, 0x6F, 0xA8, 235)
        frames.append(out)
    return frames


def leaf_mask(w, h, squash, scale=1.0, rot_deg=35.0, oversample=1):
    """Crescent leaf 13 x 5 px (design) at rot_deg; returns (inside float, edge-distance) on a w*oversample grid."""
    x, y = grid(w * oversample, h * oversample)
    x = x / oversample
    y = y / oversample
    ang = math.radians(rot_deg)
    ca, sa = math.cos(ang), math.sin(ang)
    u = (x * ca + y * sa) / scale
    v = (-x * sa + y * ca) / scale
    t = clamp(u / 6.5, -1, 1)
    half = 2.5 * squash * np.power(clamp(1 - t * t), 0.8)
    center = 0.9 * (1 - t * t)  # crescent bend
    d = np.abs(v - center) - half
    inside = (np.abs(u) <= 6.5) & (d <= 0)
    return inside, v - center, half


def gen_petal():
    frames = []
    for squash in (1.0, 0.65, 0.25, 0.65):
        inside, vv, half = leaf_mask(16, 16, squash)
        body, high, edge = hexrgb("#E5A4DC"), hexrgb("#F9C8F6"), hexrgb("#FFE9FB")
        out = np.zeros((16, 16, 4), np.uint8)
        out[inside, :3] = np.round(body).astype(np.uint8)
        out[inside & (vv < 0), :3] = np.round(high).astype(np.uint8)
        ins = inside
        nb = np.zeros_like(ins)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb |= ~np.roll(np.roll(ins, dy, 0), dx, 1)
        rim = ins & nb
        out[rim, :3] = np.round(edge).astype(np.uint8)
        if squash < 0.3:  # edge-on: a thin line in the edge colour
            out[ins, :3] = np.round(edge).astype(np.uint8)
        out[ins, 3] = 255
        frames.append(out)
    return frames


def gen_wisp():
    frames = []
    ys, xs = np.mgrid[0:32, 0:8].astype(np.float32)
    for f in range(4):
        a = gauss(xs - 3.5 - 0.8 * np.sin(0.3 * ys + f * math.pi / 2), 1.6) * smoothstep(0, 6, ys) * smoothstep(32, 18, ys)
        rgb = np.full((32, 8, 3), 255.0, np.float32)
        frames.append(to_rgba_u8(rgb, quant8(a)))
    return frames


def gen_dust():
    rng = np.random.default_rng(77)
    frames = []
    base = np.array(hexrgb("#BFB6A8"))
    for rf, am in zip((5.0, 6.0, 7.0, 7.5), (1.0, 0.85, 0.6, 0.35)):
        x, y = grid(16, 16)
        r = np.hypot(x, y)
        n = value_noise(16, 16, 4, rng)
        a = clamp(1 - r / rf) ** 1.2 * (0.75 + 0.25 * n) * am
        rgb = np.broadcast_to(base, (16, 16, 3)).copy()
        frames.append(to_rgba_u8(rgb, quant8(a)))
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


# ----------------------------------------------------------------------------------------------- glow atlas
def sp_glow_soft():
    x, y = grid(64, 64)
    return np.exp(-(np.hypot(x, y) / 11.5) ** 2)


def sp_glow_core():
    x, y = grid(32, 32)
    r = np.hypot(x, y)
    return clamp(clamp(1 - r / 16) ** 4 + 0.6 * np.exp(-(r / 3) ** 2))


def _ray(x, y, ang, sigma, length):
    ca, sa = math.cos(ang), math.sin(ang)
    along = x * ca + y * sa
    perp = -x * sa + y * ca
    prof = np.exp(-(perp / sigma) ** 2) * clamp(1 - np.abs(along) / length) ** 2
    return prof


def sp_star4():
    x, y = grid(32, 32)
    r = np.hypot(x, y)
    i = np.exp(-(r / 2.0) ** 2)
    i = np.maximum(i, _ray(x, y, 0, 0.9, 16))
    i = np.maximum(i, _ray(x, y, math.pi / 2, 0.9, 16))
    i = np.maximum(i, 0.35 * np.maximum(_ray(x, y, math.pi / 4, 0.9, 16), _ray(x, y, -math.pi / 4, 0.9, 16)))
    return clamp(i)


def sp_star6():
    x, y = grid(32, 32)
    r = np.hypot(x, y)
    i = np.exp(-(r / 1.6) ** 2)
    for k in range(3):
        i = np.maximum(i, _ray(x, y, math.radians(60 * k), 0.8, 14))
    return clamp(i)


def sp_petal_glow():
    big, _, _ = leaf_mask(32, 32, 1.0, scale=24.0 / 16.0, oversample=4)
    a = big.astype(np.float32).reshape(32, 4, 32, 4).mean((1, 3))
    return clamp(blur(a, 2.5) * 0.8 / 0.55)


def _feather(bent):
    h, w = 64, 32
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    cx = 16.0 + (4.0 * (ys / 64.0) ** 2 if bent else 0.0)
    dx = xs - cx
    ad = np.abs(dx)
    t = clamp((ys - 4.0) / 56.0)
    width = 9.0 * np.power(np.maximum(np.sin(math.pi * np.power(t, 0.8)), 0), 0.75) * ((ys >= 3) & (ys <= 61))
    notch = 1.4 * ((ys % 6.0) / 6.0)  # saw teeth on the outer edge
    wd = np.maximum(width - notch, 0)
    vane = np.where(ad <= wd, 0.75 * np.power(clamp(1 - ad / np.maximum(wd, 1e-3)), 0.5) *
                    (0.8 + 0.2 * np.sin(1.6 * ys + ad)) + 0.18 * (ad <= wd), 0.0)
    shaft = ((ad < 0.7) & (ys >= 4) & (ys <= 60)).astype(np.float32)
    return clamp(blur(np.maximum(vane, shaft), 1.0) * 1.15)


def sp_speck():
    x, y = grid(16, 16)
    return np.exp(-(np.hypot(x, y) / 1.2) ** 2)


def edge_window(w, h, mx, my):
    """0 at the cell border rising smoothly to 1 at mx / my pixels inside (additive cells must end at zero, else a box shows)."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    wx = smoothstep(0.0, mx, np.minimum(xs + 0.5, w - xs - 0.5)) if mx > 0 else 1.0
    wy = smoothstep(0.0, my, np.minimum(ys + 0.5, h - ys - 0.5)) if my > 0 else 1.0
    return wx * wy


def sp_mist(rng):
    x, y = grid(48, 32)
    base = np.exp(-((x / 18.0) ** 2 + (y / 10.0) ** 2))
    return clamp(base * (0.6 + 0.4 * fbm(48, 32, 12, 3, rng))) * edge_window(48, 32, 10, 8)


def sp_flare():
    x, y = grid(64, 32)
    r = np.hypot(x, y)
    return clamp(np.exp(-(y / 2.2) ** 2) * np.exp(-(x / 20.0) ** 2) + 0.5 * np.exp(-(r / 4.0) ** 2)) * edge_window(64, 32, 12, 3)


def sp_streak():
    ys, xs = np.mgrid[0:16, 0:128].astype(np.float32)
    dy = ys - 7.5
    return np.exp(-(dy / 2.0) ** 2) * (xs / 127.0) ** 0.7


def sp_line():
    ys, xs = np.mgrid[0:16, 0:128].astype(np.float32)
    dy = ys - 7.5
    return clamp(np.exp(-(dy / 1.6) ** 2) + 0.35 * np.exp(-(dy / 5.0) ** 2))


def sp_ring_thin():
    x, y = grid(64, 64)
    r = np.hypot(x, y)
    # radial window: the halo of the ring must be zero at the cell border (a square edge showed at saturation)
    return clamp(np.exp(-((r - 28) / 1.2) ** 2) + 0.35 * np.exp(-((r - 28) / 4.0) ** 2)) * (1 - smoothstep(29.5, 32.0, r))


def sp_ring_soft():
    x, y = grid(64, 64)
    r = np.hypot(x, y)
    return np.exp(-((r - 24) / 6.0) ** 2) * (1 - smoothstep(27.0, 32.0, r))


def sp_frost_sigil():
    n = 128
    x, y = grid(n, n)
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    base = np.exp(-((r - 60) / 0.8) ** 2)
    base = np.maximum(base, np.exp(-((r - 52) / 0.6) ** 2))
    # dotted ring r 40, one dot every 10 degrees
    step = math.radians(10)
    k = np.round(th / step)
    dth = th - k * step
    d = np.hypot(r * np.sin(dth), r * np.cos(dth) - 40)
    base = np.maximum(base, np.exp(-(d / 0.9) ** 2))
    # six-fold snowflake: arms of length 48 with branches at 40 and 65 percent (+-60 deg, 10 and 6 px)
    for a in range(6):
        ang = math.radians(60 * a)
        ca, sa = math.cos(ang), math.sin(ang)
        base = np.maximum(base, np.exp(-(seg_dist(x, y, 0, 0, ca * 48, sa * 48) / 0.75) ** 2))
        for frac, ln in ((0.40, 10), (0.65, 6)):
            bx, by = ca * 48 * frac, sa * 48 * frac
            for sg in (-1, 1):
                ba = ang + sg * math.radians(60)
                base = np.maximum(base, np.exp(-(seg_dist(x, y, bx, by, bx + math.cos(ba) * ln, by + math.sin(ba) * ln) / 0.75) ** 2))
    # 12 diamonds 5 x 3 px between r 52 and 60 at 30 degree steps
    for k2 in range(12):
        ang = math.radians(30 * k2 + 15)
        ca, sa = math.cos(ang), math.sin(ang)
        u = x * ca + y * sa - 56  # radial
        v = -x * sa + y * ca
        dia = 1.0 - (np.abs(u) / 2.5 + np.abs(v) / 1.5)
        base = np.maximum(base, clamp(dia * 2.0))
    i = clamp(0.9 * base + 0.4 * blur(base, 3.0))
    return i


def sp_crack(rng):
    n = 64
    arr = np.zeros((n, n), np.float32)
    c = (n - 1) / 2.0
    for b in range(5):
        ang = rng.uniform(0, 2 * math.pi)
        length = rng.uniform(26, 31)
        x, y = c, c
        path = []
        steps = int(length / 2)
        for s in range(steps):
            ang += math.radians(rng.uniform(-35, 35))
            nx, ny = x + math.cos(ang) * 2, y + math.sin(ang) * 2
            stamp_line(arr, x, y, nx, ny)
            path.append((nx, ny, ang))
            x, y = nx, ny
        for sb in range(2):
            px, py, pa = path[int(rng.integers(len(path) // 3, len(path)))]
            ang2 = pa + math.radians(rng.choice([-1, 1]) * rng.uniform(30, 60))
            x2, y2 = px, py
            for s in range(int(rng.integers(3, 6))):
                ang2 += math.radians(rng.uniform(-30, 30))
                nx, ny = x2 + math.cos(ang2) * 2, y2 + math.sin(ang2) * 2
                stamp_line(arr, x2, y2, nx, ny)
                x2, y2 = nx, ny
    return clamp(arr + 0.5 * blur(arr, 1.5))


def sp_pillar():
    ys, xs = np.mgrid[0:96, 0:32].astype(np.float32)
    i = (0.9 * np.exp(-((xs - 15.5) / 5.0) ** 2) + 0.3 * np.exp(-((xs - 15.5) / 12.0) ** 2)) * (0.85 + 0.15 * np.sin(2 * math.pi * ys / 32))
    return clamp(i) * edge_window(32, 96, 6, 0)


GLOW_RECTS = {
    "glow_soft": (0, 0, 64, 64), "glow_core": (64, 0, 32, 32), "star4": (96, 0, 32, 32), "star6": (128, 0, 32, 32),
    "petal_glow": (160, 0, 32, 32), "feather": (192, 0, 32, 64), "feather_b": (224, 0, 32, 64), "speck": (64, 32, 16, 16),
    "mist": (80, 32, 48, 32), "flare": (128, 32, 64, 32), "streak": (0, 64, 128, 16), "line": (0, 80, 128, 16),
    "ring_thin": (0, 96, 64, 64), "ring_soft": (64, 96, 64, 64), "frost_sigil": (128, 64, 128, 128),
    "crack": (0, 160, 64, 64), "pillar": (64, 160, 32, 96),
}


def gen_glow_atlas():
    rng = np.random.default_rng(4242)
    sprites = {
        "glow_soft": sp_glow_soft(), "glow_core": sp_glow_core(), "star4": sp_star4(), "star6": sp_star6(),
        "petal_glow": sp_petal_glow(), "feather": _feather(False), "feather_b": _feather(True), "speck": sp_speck(),
        "mist": sp_mist(rng), "flare": sp_flare(), "streak": sp_streak(), "line": sp_line(), "ring_thin": sp_ring_thin(),
        "ring_soft": sp_ring_soft(), "frost_sigil": sp_frost_sigil(), "crack": sp_crack(rng), "pillar": sp_pillar(),
    }
    atlas = np.zeros((256, 256), np.float32)
    for name, (x, y, w, h) in GLOW_RECTS.items():
        s = sprites[name]
        assert s.shape == (h, w), (name, s.shape, (h, w))
        region = atlas[y:y + h, x:x + w]
        if region.max() > 0:
            raise SystemExit("glow atlas overlap at " + name)
        atlas[y:y + h, x:x + w] = s
    g = np.round(clamp(atlas) * 255).astype(np.uint8)
    rgb = np.stack([g, g, g], -1)
    save(os.path.join(ASSETS, "textures", "fx", "fx_glow.png"), rgb)
    meta = {k: {"x": v[0], "y": v[1], "w": v[2], "h": v[3]} for k, v in GLOW_RECTS.items()}
    with open(os.path.join(ASSETS, "textures", "fx", "fx_glow_atlas.json"), "w", newline="\n") as fh:
        json.dump({"size": 256, "sprites": meta}, fh, indent=2)
        fh.write("\n")
    return atlas


# ----------------------------------------------------------------------------------------------- other fx textures
def gen_frost_ground():
    rng = np.random.default_rng(31)
    n = 128
    x, y = grid(n, n)
    r = np.hypot(x, y)
    f = fbm(n, n, 32, 4, rng)
    # phase 6 step 6: the spec's floor (0.0) left 83 percent of the texels under the entity shader's 0.1 alpha discard, so the frost was only needles;
    # a floor of 0.34 with the fbm on top makes the sheet itself readable at noon (needles stay at 230)
    a = (0.34 + 0.66 * clamp(f * 1.6 - 0.35)) * 215.0
    needles = np.zeros((n, n), np.float32)
    for k in range(60):
        cx, cy = rng.uniform(8, n - 8, 2)
        ang = math.radians(rng.choice([0, 60, 120]))
        ln = rng.uniform(6, 14)
        stamp_line(needles, cx - math.cos(ang) * ln / 2, cy - math.sin(ang) * ln / 2,
                   cx + math.cos(ang) * ln / 2, cy + math.sin(ang) * ln / 2)
    a = np.maximum(a / 255.0, 0.0) * 255.0 + 0.0
    a = np.maximum(a, needles * 230.0)
    a = a * smoothstep(64, 50, r)
    rgb = np.broadcast_to(hexrgb("#EEF6FF"), (n, n, 3)).copy()
    save(os.path.join(ASSETS, "textures", "fx", "frost_ground.png"), to_rgba_u8(rgb, a / 255.0))


def gen_crack_ground():
    rng = np.random.default_rng(55)
    n = 64
    arr = np.zeros((n, n), np.float32)
    c = (n - 1) / 2.0
    for b in range(5):
        ang = rng.uniform(0, 2 * math.pi)
        x, y = c, c
        for s in range(int(rng.integers(9, 14))):
            ang += math.radians(rng.uniform(-35, 35))
            nx, ny = x + math.cos(ang) * 2.4, y + math.sin(ang) * 2.4
            stamp_line(arr, x, y, nx, ny)
            if rng.random() < 0.35:  # 2 px wide sections
                stamp_line(arr, x + 1, y, nx + 1, ny)
            x, y = nx, ny
    out = np.zeros((n, n, 4), np.uint8)
    out[arr > 0] = (0x2A, 0x24, 0x33, 210)
    for k in range(6):
        cx, cy = rng.uniform(10, n - 12, 2).astype(int)
        out[cy:cy + 2, cx:cx + 2] = (0x7C, 0x86, 0x9A, 255)
    save(os.path.join(ASSETS, "textures", "fx", "crack_ground.png"), out)


def gen_frost_swirl():
    rng = np.random.default_rng(9)
    n = 64
    seeds = rng.random((12, 2)) * n
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32)
    best = np.full((n, n), 1e9, np.float32)
    second = np.full((n, n), 1e9, np.float32)
    for sx, sy in seeds:
        d = np.full((n, n), 1e9, np.float32)
        for ox in (-n, 0, n):
            for oy in (-n, 0, n):
                d = np.minimum(d, np.hypot(xs - (sx + ox), ys - (sy + oy)))
        new_second = np.where(d < best, best, np.minimum(second, d))
        best = np.minimum(best, d)
        second = new_second
    edge = (second - best) < 1.1
    noise = value_noise(n, n, 8, rng, tile=True)
    cells = 0.15 + 0.1 * noise
    i = np.where(edge, 1.0, cells)
    g = np.round(clamp(i) * 255).astype(np.uint8)
    save(os.path.join(ASSETS, "textures", "fx", "frost_swirl.png"), np.stack([g, g, g], -1))


def gen_vignette_dark():
    n = 256
    x, y = grid(n, n)
    u = x / ((n - 1) / 2.0)
    v = y / ((n - 1) / 2.0)
    r = np.hypot(u, v)
    a = smoothstep(0.45, 1.0, r) ** 1.3
    save(os.path.join(ASSETS, "textures", "gui", "fx", "vignette_dark.png"),
         to_rgba_u8(np.zeros((n, n, 3), np.float32), a))


def gen_frost_edge():
    rng = np.random.default_rng(101)
    n = 256
    arr = np.zeros((n, n), np.float32)
    maxlen = int(0.22 * n)

    def grow(px, py, dx, dy, ln, depth):
        x, y = px, py
        i = 0
        nxt = int(rng.integers(3, 6))
        while i < ln:
            # straight inward with a little wobble
            x += dx + rng.uniform(-0.25, 0.25) * abs(dy)
            y += dy + rng.uniform(-0.25, 0.25) * abs(dx)
            ix, iy = int(round(x)), int(round(y))
            if 0 <= ix < n and 0 <= iy < n:
                fade = 1.0 - 0.55 * (i / ln)
                arr[iy, ix] = max(arr[iy, ix], 220 / 255.0 * fade)
            i += 1
            if depth < 2 and i >= nxt and i < ln - 3:
                nxt = i + int(rng.integers(3, 6))
                for sg in (-1, 1):
                    ba = math.atan2(dy, dx) + sg * math.radians(60)
                    grow_branch(x, y, math.cos(ba), math.sin(ba), int((ln - i) * 0.45), depth + 1)

    def grow_branch(px, py, dx, dy, ln, depth):
        x, y = px, py
        for i in range(max(0, ln)):
            x += dx
            y += dy
            ix, iy = int(round(x)), int(round(y))
            if 0 <= ix < n and 0 <= iy < n:
                arr[iy, ix] = max(arr[iy, ix], 200 / 255.0 * (1 - 0.5 * i / max(1, ln)))

    for edge in range(4):
        for s in range(40):
            t = rng.uniform(2, n - 3)
            ln = int(maxlen * rng.uniform(0.25, 1.0))
            if edge == 0:
                grow(t, 0, 0, 1, ln, 0)
            elif edge == 1:
                grow(t, n - 1, 0, -1, ln, 0)
            elif edge == 2:
                grow(0, t, 1, 0, ln, 0)
            else:
                grow(n - 1, t, -1, 0, ln, 0)
    x, y = grid(n, n)
    m = np.maximum(np.abs(x), np.abs(y)) / ((n - 1) / 2.0)
    fog = smoothstep(0.65, 1.0, m) * (120 / 255.0)
    # a soft glow around the dendrites so the pixel lines read at GUI scale
    a = clamp(np.maximum(arr, fog) + 0.35 * blur(arr, 1.2))
    save(os.path.join(ASSETS, "textures", "gui", "fx", "frost_edge.png"),
         to_rgba_u8(np.full((n, n, 3), 255.0, np.float32), a))


def gen_speed_lines():
    rng = np.random.default_rng(66)
    n = 256
    x, y = grid(n, n)
    r = np.hypot(x, y) / ((n - 1) / 2.0)
    th = np.arctan2(y, x)
    a = np.zeros((n, n), np.float32)
    for k in range(90):
        ang = rng.uniform(-math.pi, math.pi)
        w = rng.choice([1.0, 2.0]) * 0.5  # half width in px
        r0 = rng.uniform(0.35, 0.6)
        d = np.abs(np.sin(th - ang)) * np.hypot(x, y)  # distance to the ray line
        along = np.cos(th - ang) > 0
        prof = clamp(1 - d / (w + 0.4)) * along * smoothstep(r0, 1.0, r)
        a = np.maximum(a, prof * (200 / 255.0))
    save(os.path.join(ASSETS, "textures", "gui", "fx", "speed_lines.png"),
         to_rgba_u8(np.full((n, n, 3), 255.0, np.float32), clamp(a)))


# ----------------------------------------------------------------------------------------------- main
def contact_sheet(path, sprites):
    """Review only: particle frames and the glow atlas on a dark and a light background."""
    tiles = []
    for name, frames in sprites.items():
        for f in frames:
            im = Image.fromarray(f, "RGBA").resize((f.shape[1] * 6, f.shape[0] * 6), Image.NEAREST)
            tiles.append(im)
    W = 1500
    sheet = Image.new("RGBA", (W, 300), (24, 30, 46, 255))
    x = 4
    for t in tiles:
        if x + t.width > W:
            break
        sheet.alpha_composite(t, (x, 4))
        x += t.width + 6
    glow = Image.open(os.path.join(ASSETS, "textures", "fx", "fx_glow.png")).convert("RGB")
    full = Image.new("RGB", (W, 300 + 520), (0, 0, 0))
    full.paste(sheet.convert("RGB"), (0, 0))
    full.paste(glow.resize((512, 512), Image.BILINEAR), (4, 308))
    ground = Image.open(os.path.join(ASSETS, "textures", "fx", "frost_ground.png")).convert("RGBA")
    bg = Image.new("RGBA", (256, 256), (90, 120, 70, 255))
    bg.alpha_composite(ground.resize((256, 256), Image.NEAREST))
    full.paste(bg.convert("RGB"), (530, 308))
    sw = Image.open(os.path.join(ASSETS, "textures", "fx", "frost_swirl.png")).convert("RGB").resize((256, 256), Image.NEAREST)
    full.paste(sw, (790, 308))
    cr = Image.open(os.path.join(ASSETS, "textures", "fx", "crack_ground.png")).convert("RGBA")
    bg2 = Image.new("RGBA", (64, 64), (110, 110, 100, 255))
    bg2.alpha_composite(cr)
    full.paste(bg2.convert("RGB").resize((256, 256), Image.NEAREST), (1050, 308))
    for i, nm in enumerate(("vignette_dark", "frost_edge", "speed_lines")):
        im = Image.open(os.path.join(ASSETS, "textures", "gui", "fx", nm + ".png")).convert("RGBA")
        bg3 = Image.new("RGBA", (256, 256), (70, 110, 160, 255) if nm != "vignette_dark" else (200, 200, 200, 255))
        bg3.alpha_composite(im)
        full.paste(bg3.convert("RGB"), (530 + i * 262, 570))
    full.save(path)


def main():
    parts = {
        "frost_mote": gen_frost_mote(), "snowflake": gen_snowflake(), "ice_shard": gen_ice_shard(),
        "petal": gen_petal(), "reiatsu_wisp": gen_wisp(), "dust_puff": gen_dust(),
    }
    for name, frames in parts.items():
        write_particle(name, frames)
    gen_glow_atlas()
    gen_frost_ground()
    gen_crack_ground()
    gen_frost_swirl()
    gen_vignette_dark()
    gen_frost_edge()
    gen_speed_lines()
    if "--sheet" in sys.argv:
        contact_sheet(sys.argv[sys.argv.index("--sheet") + 1], parts)
    print("fx textures written to", ASSETS)


if __name__ == "__main__":
    main()
