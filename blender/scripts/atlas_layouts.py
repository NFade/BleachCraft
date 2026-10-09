# Atlas layouts (pure python, no bpy/PIL) shared by the Blender build scripts (UV generation) and the system-python
# painters (texture painting), so UV islands and paint always agree.
# Pixel coordinates: origin top-left, x right, y down. UV: u = px / 256, v = 1 - py / 256 (V up).
ATLAS = 256


class Isl:
    """Rectangular UV island. uv(fx, fy): fx 0..1 left to right, fy 0..1 BOTTOM to TOP (V up)."""

    def __init__(self, name, x, y, w, h, atlas=ATLAS):
        self.name, self.x, self.y, self.w, self.h, self.atlas = name, x, y, w, h, atlas

    def uv(self, fx, fy):
        return ((self.x + fx * self.w) / self.atlas, 1.0 - (self.y + (1.0 - fy) * self.h) / self.atlas)

    def px(self, fx, fy):
        return (self.x + fx * self.w, self.y + (1.0 - fy) * self.h)

    def rect(self):
        return (self.x, self.y, self.x + self.w, self.y + self.h)

    def __repr__(self):
        return "Isl(%s,%d,%d,%d,%d)" % (self.name, self.x, self.y, self.w, self.h)


def _mk(d, atlas=ATLAS):
    return {k: Isl(k, *v, atlas=atlas) for k, v in d.items()}


# ---- shared hilt constants (Gate B detailing guidance)
WRAP_RINGS = 20          # rings of the tsuka-ito, 12 sides, odd rings 1..17 are the 9 raised diamonds
WRAP_SIDES = 12
WRAP_RIDGE = 0.0015      # extra radius of a ridge ring (m)
WRAP_PX_PER_GAP = 8      # island height = 19 gaps * 8 px
WRAP_DIAMOND_W = 15      # px, 4 diamonds around (60 px)
BLADE_RINGS_BODY = 10    # body rings + yokote ring (n_mid = 10 -> 11 rings) + 1 kissaki ring + apex

# ---- rukia_sealed (256 x 256)
RUKIA_SEALED = _mk({
    "wrap":        (2, 2, 60, 152),
    "blade_a":     (66, 2, 8, 140),     # +X side: hira + shinogi-ji, u = edge -> spine
    "blade_b":     (76, 2, 8, 140),     # -X side
    "blade_s":     (86, 2, 4, 140),     # spine (mune)
    "blade_e":     (92, 2, 2, 140),     # edge flat
    "saya":        (98, 2, 32, 214),
    "tsuba_front": (134, 2, 36, 42),
    "tsuba_back":  (174, 2, 36, 42),
    "tsuba_rim":   (134, 48, 100, 8),   # three bands: bottom chamfer / wall / top chamfer
    "tsuba_slit":  (134, 58, 12, 4),    # slit inner walls (flat shade)
    "kashira_side": (134, 66, 42, 8),
    "kashira_cap": (180, 66, 12, 16),
    "fuchi_side":  (134, 78, 48, 8),
    "habaki_side": (134, 90, 34, 12),
    "habaki_top":  (172, 90, 14, 6),
    "koiguchi":    (134, 106, 32, 5),
    "kojiri":      (134, 114, 32, 6),
    "kojiri_cap":  (172, 106, 8, 10),
})
SEALED_BLADE_PX_PER_M = 200.0
SEALED_TSUBA_PX_PER_M = 560.0   # planar scale of the tsuba faces
SEALED_RIM_PX = 100             # rim strip length in px (perimeter is scaled to it)

# ---- rukia_shikai (256 x 256)
RUKIA_SHIKAI = _mk({
    "wrap":        (2, 2, 60, 152),
    "blade_a":     (66, 2, 7, 150),
    "blade_b":     (76, 2, 7, 150),
    "blade_s":     (86, 2, 3, 150),
    "blade_e":     (92, 2, 2, 150),
    "tsuba_front": (98, 2, 58, 58),
    "tsuba_back":  (160, 2, 58, 58),
    "tsuba_rim":   (98, 64, 112, 8),
    "tsuba_win":   (98, 74, 12, 4),     # window inner walls (flat shade)
    "kashira_side": (98, 82, 42, 8),
    "kashira_cap": (144, 82, 12, 16),
    "fuchi_side":  (98, 94, 48, 8),
    "habaki_side": (98, 106, 34, 12),
    "habaki_top":  (136, 106, 14, 6),
    "knot_side":   (98, 122, 40, 8),
    "knot_bottom": (142, 122, 12, 8),
})
SHIKAI_BLADE_PX_PER_M = 200.0
SHIKAI_TSUBA_PX_PER_M = 636.0
SHIKAI_RIM_PX = 112
RIBBON_CELL_W, RIBBON_CELL_H = 25, 8
RIBBON_X0, RIBBON_Y0 = 2, 240


def ribbon_cell(n):
    """Island of ribbon segment n (1..10): 25 x 8 px cell of the 250 x 8 strip."""
    return Isl("ribbon_%02d" % n, RIBBON_X0 + RIBBON_CELL_W * (n - 1), RIBBON_Y0, RIBBON_CELL_W, RIBBON_CELL_H)


# ---- byakuya_sealed (256 x 256): same structure as rukia_sealed; 11-diamond wrap (24 rings x 6 px), window-frame tsuba
BYAKUYA_SEALED = _mk({
    "wrap":        (2, 2, 60, 138),     # 23 gaps * 6 px, 5 diamonds around (12 px each)
    "blade_a":     (66, 2, 8, 140),
    "blade_b":     (76, 2, 8, 140),
    "blade_s":     (86, 2, 4, 140),
    "blade_e":     (92, 2, 2, 140),
    "saya":        (98, 2, 32, 214),
    "tsuba_front": (134, 2, 50, 74),
    "tsuba_back":  (188, 2, 50, 74),
    "tsuba_rim":   (134, 78, 120, 8),   # three bands: bottom chamfer / wall / top chamfer
    "tsuba_win":   (134, 88, 12, 4),    # window inner walls (flat shade)
    "kashira_side": (134, 96, 42, 8),
    "kashira_cap": (180, 96, 12, 16),
    "fuchi_side":  (134, 108, 48, 8),
    "habaki_side": (134, 120, 34, 12),
    "habaki_top":  (172, 120, 14, 6),
    "koiguchi":    (134, 136, 32, 5),
    "kojiri":      (134, 144, 32, 6),
    "kojiri_cap":  (172, 136, 8, 10),
})
BYAKUYA_WRAP_RINGS = 24
BYAKUYA_WRAP_PX_PER_GAP = 6
BYAKUYA_WRAP_DIAMOND_W = 12
BYAKUYA_BLADE_PX_PER_M = 200.0
BYAKUYA_TSUBA_PX_PER_M = 660.0     # Gate C C2: 76 x 112 mm -> 50 x 74 px islands


LAYOUTS = {"rukia_sealed": RUKIA_SEALED, "rukia_shikai": RUKIA_SHIKAI, "byakuya_sealed": BYAKUYA_SEALED}


# ---- shared pure-python shapes (used by the Blender builders and the painters)
import math


def concave_rect(hx, hy, r, n=4):
    """Rectangle with concave quarter-circle corners (radius r centred on the corner), CCW."""
    pts = []
    spec = ((1, 1, 0, 90), (-1, 1, 90, 0), (-1, -1, 0, 90), (1, -1, 90, 0))
    for sx, sy, u0, u1 in spec:
        for k in range(n + 1):
            u = math.radians(u0 + (u1 - u0) * k / n)
            pts.append((sx * (hx - r * math.sin(u)), sy * (hy - r * math.cos(u))))
    return pts


def sealed_tsuba_shapes():
    """Gate B B4: outline (concave corners r 10 mm) and the two bowed slits (20 x 6 mm at y = +-25 mm, bow 3 mm, n = 8)."""
    outline = concave_rect(0.031, 0.036, 0.010, 4)
    holes = []
    n = 8
    for sy in (-1, 1):
        top, bot = [], []
        for k in range(n + 1):
            x = -0.010 + 0.020 * k / n
            c = -sy * 0.003 * (x / 0.010) ** 2
            w = 0.003 * (1 - (x / 0.010) ** 2) ** 0.5
            top.append((x, sy * 0.025 + c + w + 0.0003))
            bot.append((x, sy * 0.025 + c - w - 0.0003))
        pts = top + bot[::-1]
        dd = []
        for p in pts:
            if not dd or (abs(p[0] - dd[-1][0]) > 1e-9 or abs(p[1] - dd[-1][1]) > 1e-9):
                dd.append(p)
        if abs(dd[0][0] - dd[-1][0]) < 1e-9 and abs(dd[0][1] - dd[-1][1]) < 1e-9:
            dd.pop()
        holes.append(dd)
    return outline, holes


def rounded_rect(hx, hy, r, n=4):
    pts = []
    for cx, cy, a0 in ((hx - r, hy - r, 0), (-hx + r, hy - r, 90), (-hx + r, -hy + r, 180), (hx - r, -hy + r, 270)):
        for k in range(n + 1):
            t = math.radians(a0 + 90.0 * k / n)
            pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    return pts


def byakuya_tsuba_shapes():
    """Gate C C2 (was ART_BIBLE 1.3 56 x 92 mm): 76 (X) x 112 (Y) mm window frame, corner radius 6 mm (n = 4 per corner); frame bar 8, centre bar (along Y) 10,
    transverse bar 8, hub plate 18 (X) x 40 (Y). Four stepped L windows (4 mm step at the hub); windows are clockwise holes."""
    outline = rounded_rect(0.038, 0.056, 0.006, 4)
    holes = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            pts = [(9, 4), (30, 4), (30, 48), (5, 48), (5, 20), (9, 20)]
            pts = [(sx * x * 0.001, sy * y * 0.001) for x, y in pts]
            if sx * sy > 0:
                pts = pts[::-1]
            holes.append(pts)
    return outline, holes


def circle(r, n):
    return [(r * math.cos(2 * math.pi * k / n), r * math.sin(2 * math.pi * k / n)) for k in range(n)]


def snowflake_shapes(r_out=0.044, r_rim=0.037, r_hub=0.018, outline_n=48, corner_r=0.0025, corner_segs=3, arc_in=5, arc_out=7):
    """Gate B B5 snowflake tsuba: outline (48 segments) and six windows between the spokes (spoke half width 3.5 mm at the hub
    to 6 mm at the rim), four corners rounded with a quadratic Bezier of `corner_segs` segments."""
    def h(r):
        return 0.0035 + (r - r_hub) / (r_rim - r_hub) * 0.0025
    wins = []
    for k in range(6):
        t0, t1 = math.radians(60 * k), math.radians(60 * (k + 1))
        u = lambda t: (math.cos(t), math.sin(t))
        up = lambda t: (-math.sin(t), math.cos(t))
        d_in, d_out = math.asin(h(r_hub) / r_hub), math.asin(h(r_rim) / r_rim)
        # four sharp corners, CCW: inner-start, inner-end, outer-end, outer-start
        def sp(t, r, side):   # point on the spoke edge at axis angle t, radius r; side +1 = ccw side of the axis
            ux, uy = u(t)
            vx, vy = up(t)
            return (r * ux + side * h(r) * vx, r * uy + side * h(r) * vy)
        c_is = sp(t0, r_hub, +1)
        c_ie = sp(t1, r_hub, -1)
        c_oe = sp(t1, r_rim, -1)
        c_os = sp(t0, r_rim, +1)
        ang = lambda p: math.atan2(p[1], p[0])
        # sample polygon: inner arc (hub) from c_is to c_ie, straight edge to c_oe, outer arc to c_os, straight edge back
        def arc(r, a0, a1, n):
            return [(r * math.cos(a0 + (a1 - a0) * i / n), r * math.sin(a0 + (a1 - a0) * i / n)) for i in range(n + 1)]
        a_is, a_ie = ang(c_is), ang(c_ie)
        if a_ie < a_is:
            a_ie += 2 * math.pi
        a_os, a_oe = ang(c_os), ang(c_oe)
        if a_oe < a_os:
            a_oe += 2 * math.pi
        inner = arc(r_hub, a_is, a_ie, arc_in)
        outer = arc(r_rim, a_oe, a_os, arc_out)
        edges = [inner, [c_ie, c_oe], outer, [c_os, c_is]]
        # build the loop with rounded corners: for each corner replace the shared sharp corner by a Bezier
        corners = [inner[-1], outer[0], outer[-1], inner[0]]
        loop = []
        seq = [inner, outer, ]
        pts = inner[:]                      # corner c_ie is inner[-1]
        pts += outer                        # c_oe .. c_os (straight edge c_ie->c_oe implied)
        # pts is the closed loop (edge c_os -> c_is closes it)
        n = len(pts)
        corner_idx = [len(inner) - 1, len(inner), n - 1, 0]
        out = []
        for i in range(n):
            if i in corner_idx:
                p = pts[i]
                pp, pn = pts[i - 1], pts[(i + 1) % n]
                def toward(a, b, d):
                    dx, dy = b[0] - a[0], b[1] - a[1]
                    L = math.hypot(dx, dy)
                    return (a[0] + dx / L * min(d, 0.45 * L), a[1] + dy / L * min(d, 0.45 * L))
                p_in = toward(p, pp, corner_r)
                p_out = toward(p, pn, corner_r)
                for j in range(corner_segs + 1):
                    t = j / corner_segs
                    a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t * t
                    out.append((a * p_in[0] + b * p[0] + c * p_out[0], a * p_in[1] + b * p[1] + c * p_out[1]))
            else:
                out.append(pts[i])
        wins.append(out)
    return circle(r_out, outline_n), wins


# ---- byakuya_shikai (256 x 256): the hilt islands keep the byakuya_sealed positions (same hilt texture, seamless swap); the blade and
# saya islands are dropped; petal 40 x 24 cell, shard 16 x 16 cell (8 x 16 face + 6 x 16 side strips) in the freed area.
BYAKUYA_SHIKAI = _mk({
    "wrap":        (2, 2, 60, 138),
    "tsuba_front": (134, 2, 50, 74),
    "tsuba_back":  (188, 2, 50, 74),
    "tsuba_rim":   (134, 78, 120, 8),
    "tsuba_win":   (134, 88, 12, 4),
    "kashira_side": (134, 96, 42, 8),
    "kashira_cap": (180, 96, 12, 16),
    "fuchi_side":  (134, 108, 48, 8),
    "habaki_side": (134, 120, 34, 12),
    "habaki_top":  (172, 120, 6, 14),    # cap is 10 (X) x 32 (Y) mm at 430 px/m = 4.3 x 13.8 px: Y is the long axis (was 14 x 6, spilled ~4 px)
    "petal":       (66, 2, 40, 24),     # leaf outline, length along u; +X and -X faces share the texels (mirrored UV)
    "shard_face":  (66, 30, 8, 16),     # the two triangular end faces (shared texels)
    "shard_side":  (76, 30, 6, 16),     # three 2 px wide strips, one per rectangular side face
})
LAYOUTS["byakuya_shikai"] = BYAKUYA_SHIKAI
SHIKAI_HAB0, SHIKAI_HAB1 = 0.257, 0.285

# petal (ART_BIBLE 1.4, Gate B B10): rings at z 0.042 / 0.084 as (edge y, flat y, spine y, flat half thickness x), tail apex (0,0,0), tip apex (0, 0.012, 0.120)
PETAL_LEN = 0.120
PETAL_RINGS = [(0.042, -0.009, 0.004, 0.017, 0.0020), (0.084, -0.003, 0.006, 0.014, 0.0015)]
PETAL_TIP = (0.012, 0.120)
PETAL_PX_PER_M = 317.0
PETAL_YC = 0.004


def petal_verts():
    """10 vertices (x, y, z): 0 tail apex, 1-4 ring A (edge, +x flat, spine, -x flat), 5-8 ring B, 9 tip apex."""
    v = [(0.0, 0.0, 0.0)]
    for z, ye, yf, ys, tx in PETAL_RINGS:
        v += [(0.0, ye, z), (tx, yf, z), (0.0, ys, z), (-tx, yf, z)]
    v.append((0.0, PETAL_TIP[0], PETAL_TIP[1]))
    return v


def petal_profile(z):
    """(edge y, flat y, spine y) of the leaf outline projected on the (z, y) plane, piecewise linear along z."""
    pts = [(0.0, 0.0, 0.0, 0.0)] + [(r[0], r[1], r[2], r[3]) for r in PETAL_RINGS] + [(PETAL_TIP[1], PETAL_TIP[0], PETAL_TIP[0], PETAL_TIP[0])]
    z = min(max(z, 0.0), PETAL_LEN)
    for a, b in zip(pts, pts[1:]):
        if a[0] <= z <= b[0]:
            t = (z - a[0]) / (b[0] - a[0])
            return tuple(a[i] + (b[i] - a[i]) * t for i in (1, 2, 3))
    return pts[-1][1:]


def petal_uv_xy(isl, z, y):
    return isl.uv((1.0 + z * PETAL_PX_PER_M) / isl.w, 0.5 + (y - PETAL_YC) * PETAL_PX_PER_M / isl.h)


# shard: 3-sided prism 3 mm thick; triangle in (y, z) around its centroid (bible: 50 x 20 x 3 mm splinter)
SHARD_TRI_RAW = [(0.003, 0.025), (-0.010, -0.022), (0.010, -0.028)]
SHARD_T = 0.003
SHARD_PX_PER_M = 290.0


def shard_tri():
    cy = sum(p[0] for p in SHARD_TRI_RAW) / 3.0
    cz = sum(p[1] for p in SHARD_TRI_RAW) / 3.0
    return [(y - cy, z - cz) for y, z in SHARD_TRI_RAW]


def shard_face_uv(isl, y, z):
    t = shard_tri()
    yc = (min(p[0] for p in t) + max(p[0] for p in t)) / 2.0
    zc = (min(p[1] for p in t) + max(p[1] for p in t)) / 2.0
    return isl.uv(0.5 + (y - yc) * SHARD_PX_PER_M / isl.w, 0.5 + (z - zc) * SHARD_PX_PER_M / isl.h)


# ---- rukia_bankai (512 x 512, ART_BIBLE 1.5): the left 256 px column (x < 256) is RESERVED for the deferred costume set; every
# required piece lives in the right column. Palette/emissive per bible; ice parts carry diffuse alpha 200 (shell 180).
BANKAI_ATLAS = 512
RUKIA_BANKAI = _mk({
    "wrap":         (258, 2, 60, 168),     # 22 rings x 12 sides, 21 gaps x 8 px, 4 diamonds around
    "blade_a":      (322, 2, 10, 226),     # +X side: edge bevel (u 0..0.55) + core flat (0.55..1), u = edge -> spine
    "blade_b":      (334, 2, 10, 226),     # -X side
    "blade_s":      (346, 2, 5, 226),      # spine
    "blade_e":      (353, 2, 3, 226),      # edge flat
    "tsuba_front":  (360, 2, 18, 70),      # planar, 720 px/m: 22 (X) x 96 (Y) mm
    "tsuba_back":   (380, 2, 18, 70),
    "tsuba_rim":    (400, 2, 108, 8),
    "tsuba_win":    (400, 12, 16, 4),
    "kashira_side": (400, 18, 42, 8),
    "kashira_cap":  (446, 18, 12, 16),
    "fuchi_side":   (400, 28, 48, 8),
    "habaki_side":  (400, 38, 34, 12),
    "habaki_top":   (438, 38, 8, 16),
    "shell_body":   (362, 80, 120, 72),    # 10 columns x 12 px, 4 rows x 18 px: one patch per facet
    "shell_top":    (362, 154, 36, 36),
    "shell_bot":    (402, 154, 36, 36),
    "ribbon_seg":   (258, 234, 104, 20),   # along the length x across the width (297 / 286 px/m)
    "ribbon_tip":   (366, 234, 104, 20),
    "crystal_d":    (258, 258, 120, 120),
    "crystal_c":    (382, 258, 72, 72),
    "crystal_b":    (458, 258, 48, 48),
    "crystal_a":    (382, 334, 32, 32),
    "shard_a_face": (418, 334, 12, 40),
    "shard_a_side": (432, 334, 6, 40),
    "shard_b_face": (442, 334, 10, 28),
    "shard_b_side": (454, 334, 6, 28),
}, BANKAI_ATLAS)
LAYOUTS["rukia_bankai"] = RUKIA_BANKAI
BANKAI_TSUBA_PX_PER_M = 720.0
BANKAI_WRAP_RINGS = 22
BANKAI_HABAKI_PX_PER_M = 440.0
# crystals: ring heights as fractions of the height (the apex fan covers the rest up to 1.0), ring radius factors
CRYSTAL_RINGS = {"a": ([0.0, 0.30, 0.60, 0.82], [1.0, 0.97, 0.88, 0.72]), "b": ([0.0, 0.30, 0.60, 0.82], [1.0, 0.97, 0.88, 0.72]),
                 "c": ([0.0, 0.30, 0.60, 0.82], [1.0, 0.97, 0.88, 0.72]), "d": ([0.0, 0.45, 0.78], [1.0, 0.95, 0.78])}
CRYSTAL_H = {"a": 0.15, "b": 0.30, "c": 0.60, "d": 1.20}
CRYSTAL_BEND = {"a": 0.0, "b": 0.0, "c": 0.10, "d": 0.0}
CRYSTAL_SEED = {"a": 11, "b": 12, "c": 13, "d": 14}
SHELL_COLS, SHELL_ROWS = 10, 4
# shards: (length, width) in m, raw triangle in (y, z); 3 mm thick
SHARDS = {"a": (0.12, 0.03), "b": (0.08, 0.025)}
BANKAI_SHARD_PX_PER_M = 300.0


def bankai_shard_tri(key):
    ln, wd = SHARDS[key]
    tri = [(0.12 * wd, ln / 2), (-0.5 * wd, -ln / 2 + 0.01), (0.5 * wd, -ln / 2)]
    cy, cz = sum(p[0] for p in tri) / 3, sum(p[1] for p in tri) / 3
    return [(y - cy, z - cz) for y, z in tri]


def bankai_shard_face_uv(isl, key, y, z):
    t = bankai_shard_tri(key)
    yc = (min(p[0] for p in t) + max(p[0] for p in t)) / 2.0
    zc = (min(p[1] for p in t) + max(p[1] for p in t)) / 2.0
    return isl.uv(0.5 + (y - yc) * BANKAI_SHARD_PX_PER_M / isl.w, 0.5 + (z - zc) * BANKAI_SHARD_PX_PER_M / isl.h)


def stadium(hx, hy, n):
    """Stadium along Y (CCW): half width hx, half length hy, semicircular ends of radius hx with n segments each."""
    pts = []
    for k in range(n + 1):
        t = math.pi * k / n
        pts.append((hx * math.cos(t), hy - hx + hx * math.sin(t)))
    for k in range(n + 1):
        t = math.pi + math.pi * k / n
        pts.append((hx * math.cos(t), -hy + hx + hx * math.sin(t)))
    return pts


def bankai_tsuba_shapes():
    """Gate B B11: outline stadium 22 (X) x 96 (Y) mm, 16 segments per end; two slot windows 14 x 26 mm centred at y = +-31 mm
    (|y| 18..44), 8 segments per end, clockwise holes. Centre bridge 36 mm, end and side rims 4 mm."""
    outline = stadium(0.011, 0.048, 16)
    holes = []
    for sy in (-1, 1):
        h = [(x, y + sy * 0.031) for x, y in stadium(0.007, 0.013, 8)]
        holes.append(h[::-1])
    return outline, holes


# ---- byakuya_bankai (512 x 512, ART_BIBLE 1.6 + Gate B B12..B15). Left column = giant blade (32 px/m, 252 px tall for ~8.1 m of arc);
# top right = hilt (copy of the byakuya_shikai hilt islands) + stub; middle right 192 x 192 = wing alpha sheet (shared by both wings,
# wing_r mirrors in X); bottom right = halo strip + Hakuteiken blade strips; ripple strip below the blade column. The 64 x 64 area at
# (2, 446) is reserved for the deferred Senkei sword (not built).
BB_ATLAS = 512
BYAKUYA_BANKAI = _mk({
    "blade_a":      (2, 2, 20, 252),       # +X side: edge strip (u 0..0.10), bevel (..0.55), flat (..1), v = arc length fraction (0 base, 1 tip)
    "blade_b":      (24, 2, 20, 252),      # -X side
    "blade_s":      (46, 2, 6, 252),       # spine
    "blade_e":      (54, 2, 2, 252),       # edge flat (4 mm)
    "ripple":       (2, 262, 128, 16),     # u = around (16 segments x 8 px), v = across (inner -> outer edge)
    "wrap":         (164, 2, 60, 138),
    "tsuba_front":  (228, 2, 50, 74),
    "tsuba_back":   (282, 2, 50, 74),
    "tsuba_rim":    (228, 78, 120, 8),
    "tsuba_win":    (228, 88, 12, 4),
    "kashira_side": (228, 96, 42, 8),
    "kashira_cap":  (274, 96, 12, 16),
    "fuchi_side":   (228, 108, 48, 8),
    "habaki_side":  (228, 120, 34, 12),
    "habaki_top":   (266, 120, 6, 14),
    "stub_a":       (292, 96, 8, 48),      # blade stub of the ground hilt, 200 px/m
    "stub_b":       (302, 96, 8, 48),
    "stub_s":       (312, 96, 4, 48),
    "stub_e":       (318, 96, 2, 48),
    "stub_cap":     (322, 96, 4, 8),
    "wing":         (164, 148, 192, 192),  # planar (|x|, z) at WING_PX_PER_M, root at the left edge
    "halo":         (164, 352, 96, 8),
    "hk_body":      (164, 364, 48, 8),
    "hk_tip":       (164, 376, 48, 16),
}, BB_ATLAS)
LAYOUTS["byakuya_bankai"] = BYAKUYA_BANKAI

# giant blade geometry (pure python; the Blender builder and the painter share it)
GB_H, GB_W, GB_T0, GB_T1, GB_KISSAKI = 8.0, 0.55, 0.14, 0.06, 0.9
GB_CP = [(0.5, 0.0), (4.0, 0.10), (6.0, 0.35), (8.0, 0.75)]      # sori control points (deviation from the straight base axis)
GB_SLOPES = [0.0, 0.07, 0.17, 0.33]                                # Gate B B13: fixed slopes dy/dz at the control points
GB_S_EDGE = 0.10                                                   # u of the edge-bevel split vertex (2 of 20 px)
# ring stations: (z, width, local y shift of the section, vertices per ring); 8 vertices = with the edge bevel strip (z >= 6.85)
GB_STATIONS = [(0.0, GB_W, 0.0, 6), (0.5, GB_W, 0.0, 6), (2.0, GB_W, 0.0, 6), (3.5, GB_W, 0.0, 6), (4.5, GB_W, 0.0, 6),
               (5.3, GB_W, 0.0, 6), (6.0, GB_W, 0.0, 6), (6.5, GB_W, 0.0, 6), (6.85, GB_W, 0.0, 8), (7.1, GB_W, 0.0, 8),
               (7.55, 0.40, 0.04, 8), (7.7, 0.29, 0.05, 8), (7.85, 0.18, 0.06, 8)]
GB_LOD_Z = [0.0, 4.5, 7.1]
GB_GLOW_Z0 = 6.8


def gb_dev(z):
    """Cubic Hermite through GB_CP with the fixed slopes GB_SLOPES; 0 for z <= 0.5 (vertical buried section)."""
    if z <= GB_CP[0][0]:
        return 0.0
    for i in range(len(GB_CP) - 1):
        z0, d0 = GB_CP[i]
        z1, d1 = GB_CP[i + 1]
        if z <= z1 + 1e-12:
            h = z1 - z0
            t = (z - z0) / h
            h00, h10, h01, h11 = 2 * t**3 - 3 * t**2 + 1, t**3 - 2 * t**2 + t, -2 * t**3 + 3 * t**2, t**3 - t**2
            return h00 * d0 + h10 * h * GB_SLOPES[i] + h01 * d1 + h11 * h * GB_SLOPES[i + 1]
    return GB_CP[-1][1]


def gb_dphi(z, eps=0.01):
    z0, z1 = max(z - eps, 0.0), min(z + eps, GB_H)
    return math.atan((gb_dev(z1) - gb_dev(z0)) / (z1 - z0))


def gb_thick(z):
    return GB_T0 + (GB_T1 - GB_T0) * max(0.0, z - 0.5) / (GB_H - 0.5)


def gb_arc(z, n=800):
    """Normalised arc length (0 at z = 0, 1 at z = 8) of the centreline (dev(z), z)."""
    pts = [(gb_dev(GB_H * i / n), GB_H * i / n) for i in range(n + 1)]
    cum = [0.0]
    for a, b in zip(pts, pts[1:]):
        cum.append(cum[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    k = min(n, max(0, int(z / GB_H * n)))
    f = z / GB_H * n - k
    c = cum[k] + (cum[min(n, k + 1)] - cum[k]) * f
    return c / cum[-1]


def gb_station_v():
    """[(v, z)] of the ring stations plus the apex (v = arc fraction = UV v inside the blade islands)."""
    return [(gb_arc(s[0]), s[0]) for s in GB_STATIONS] + [(1.0, GB_H)]


def gb_z_of_v(v):
    st = gb_station_v()
    for (v0, z0), (v1, z1) in zip(st, st[1:]):
        if v <= v1:
            return z0 + (z1 - z0) * (v - v0) / max(v1 - v0, 1e-9)
    return GB_H


# 8-vertex profile roles in ring order: P0 edge(+X), S+ split, P1 ridge, P2 spine(+X), P3 spine(-X), P4 ridge, S- split, P5 edge(-X)
GB_ROLES6 = ["P0", "P1", "P2", "P3", "P4", "P5"]
GB_ROLES8 = ["P0", "S+", "P1", "P2", "P3", "P4", "S-", "P5"]
GB_FR = {"P0": 0.0, "S+": GB_S_EDGE, "P1": 0.55, "P2": 1.0, "P3": 1.0, "P4": 0.55, "S-": GB_S_EDGE, "P5": 0.0}


def gb_profile(w, t, nv, edge=0.002):
    """Cross-section (x, y) in ring order (nv = 6 or 8); edge faces -Y, ridge at 55 percent of the width, split vertex at u = GB_S_EDGE
    (2-face edge bevel strip: flatter than the straight line P0-P1, i.e. an acute edge)."""
    ry = -w / 2 + 0.55 * w
    pts = {"P0": (edge, -w / 2), "P1": (t / 2, ry), "P2": (t / 2, w / 2), "P3": (-t / 2, w / 2), "P4": (-t / 2, ry), "P5": (-edge, -w / 2)}
    sy = -w / 2 + GB_S_EDGE * w
    sx = edge + (t / 2 - edge) * (GB_S_EDGE / 0.55) * 0.55
    pts["S+"] = (sx, sy)
    pts["S-"] = (-sx, sy)
    return [pts[r] for r in (GB_ROLES6 if nv == 6 else GB_ROLES8)]


# ---- Hakuteiken wings (Gate B B12 + one barb per lobe): one continuous fan sheet per wing
WING_PX_PER_M = 34.0
WING_ZMIN = -0.80
WING_BARB = 0.25


def wing_geometry(sx=1):
    """Returns (verts, faces, info). verts: (x, y, z) tuples (root at the origin, +Y = behind, forward curve toward -Y); faces: index tuples
    (tris), all with a +Y normal. 9 feathers at 48 - 7.5 i degrees, length 6.0 (1 - 0.075 i); root fan 8 + band quads 8 (16 tris) + 3 tris per
    gap (24) = 48 tris, plus the barb vertex on each feather edge Q_i-T_i (pushed up to 0.25 m sideways in the plane, alternating, limited to half the distance to the lobe outline so no faces overlap): +2 tris per
    interior lobe, +1 per outer lobe."""
    def curve(p):
        r = math.hypot(p[0], p[2])
        return (p[0], p[1] - 0.8 * (r / 6.0) ** 2, p[2])

    ang = [48.0 - 7.5 * i for i in range(9)]
    Ln = [6.0 * (1 - 0.075 * i) for i in range(9)]
    dirs = [(sx * math.cos(math.radians(a)), 0.0, math.sin(math.radians(a))) for a in ang]
    sc = lambda d, r: (d[0] * r, 0.0, d[2] * r)
    V = []
    idx = {}

    def add(key, p):
        idx[key] = len(V)
        V.append(curve(p))

    add("R", (0.0, 0.0, 0.0))
    for i in range(9):
        add(("M", i), sc(dirs[i], 0.30 * Ln[i]))
        add(("Q", i), sc(dirs[i], 0.65 * Ln[i]))
        add(("T", i), sc(dirs[i], Ln[i]))
    for i in range(8):
        am = math.radians((ang[i] + ang[i + 1]) / 2.0)
        add(("N", i), sc((sx * math.cos(am), 0.0, math.sin(am)), 0.80 * Ln[i + 1]))
    tris = []
    for i in range(8):
        tris.append((idx["R"], idx[("M", i)], idx[("M", i + 1)]))
    for i in range(8):
        for a, b, c in ((("M", i), ("Q", i), ("Q", i + 1)), (("M", i), ("Q", i + 1), ("M", i + 1))):
            tris.append((idx[a], idx[b], idx[c]))
    for i in range(8):
        tris.append((idx[("Q", i)], idx[("T", i)], idx[("N", i)]))
        tris.append((idx[("Q", i)], idx[("N", i)], idx[("Q", i + 1)]))
        tris.append((idx[("Q", i + 1)], idx[("N", i)], idx[("T", i + 1)]))
    # barbs: B_i halfway on Q_i-T_i, pushed 0.25 m sideways in the plane (+perp = toward the higher feather); split the adjacent gap tris
    barbs = {}
    for i in range(9):
        q, t = V[idx[("Q", i)]], V[idx[("T", i)]]
        a = math.radians(ang[i])
        perp = (-sx * math.sin(a), 0.0, math.cos(a))            # in-plane normal to the feather, toward +angle
        side = 1.0 if i % 2 == 0 else -1.0
        if i == 0:
            side = 1.0            # outer boundary of the top feather: real silhouette barb
        if i == 8:
            side = -1.0
        mx, mz = (q[0] + t[0]) / 2, (q[2] + t[2]) / 2
        push = WING_BARB
        if 0 < i < 8:                       # interior lobe: keep the barb inside its own gap triangles (no overlap), at most 0.25 m
            nb = V[idx[("N", i - 1)]] if side > 0 else V[idx[("N", i)]]
            dx, dz = perp[0] * side, perp[2] * side
            lim = 1e9
            for e0, e1 in ((q, nb), (t, nb)):
                ex, ez = e1[0] - e0[0], e1[2] - e0[2]
                den = dx * ez - dz * ex
                if abs(den) > 1e-12:
                    tt = ((e0[0] - mx) * ez - (e0[2] - mz) * ex) / den
                    uu = ((e0[0] - mx) * dz - (e0[2] - mz) * dx) / den
                    if tt > 0 and -1e-9 <= uu <= 1 + 1e-9:
                        lim = min(lim, tt)
            push = min(WING_BARB, 0.5 * lim)
        barbs[i] = len(V)
        V.append((mx + perp[0] * push * side, (q[1] + t[1]) / 2, mz + perp[2] * push * side))
    out = []
    for tri in tris:
        done = False
        for i in range(9):
            q, t, b = idx[("Q", i)], idx[("T", i)], barbs[i]
            if q in tri and t in tri:
                out.append(tuple(b if v == t else v for v in tri))
                out.append(tuple(b if v == q else v for v in tri))
                done = True
                break
        if not done:
            out.append(tri)
    fin = []                                                     # wind every face to +Y
    for tri in out:
        p = [V[k] for k in tri]
        u = (p[1][0] - p[0][0], p[1][2] - p[0][2])
        v = (p[2][0] - p[0][0], p[2][2] - p[0][2])
        ny = u[1] * v[0] - u[0] * v[1]
        fin.append(tri if ny > 0 else (tri[0], tri[2], tri[1]))
    return V, fin, {"idx": idx, "barbs": barbs, "ang": ang, "len": Ln}


def wing_uv(isl, x, z):
    """Planar UV of a wing vertex: u from |x|, v from z (shared by wing_l and wing_r; the right wing is the mirror image)."""
    return isl.uv((2.0 + abs(x) * WING_PX_PER_M) / isl.w, (2.0 + (z - WING_ZMIN) * WING_PX_PER_M) / isl.h)
