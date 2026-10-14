# Rukia ice wall crystals (R11): pure python (no bpy, no PIL) shared by
#   build_rukia_ice_wall.py (Blender: builds the piece meshes, exports the OBJs, assembles the check wall),
#   tools/gen_ice_crystal_textures.py (system python: paints the atlas into the cells defined here) and
#   the Java port core/world/IceCrystalField (the game places the pieces per block; a golden file written by this module is compared in a test).
#
# Idea: every wall block is one 1 x 1 x 1 cell of a crystal column. The column of a wall (x, z) is a hexagonal crystal made of stacked cells, each cell is
# one piece (shaft / tip / crown) whose bottom and top rings are the same hexagon as the neighbour's, so the cells fuse into one tall spire. All variation
# (width of the column, lean of the axis, yaw, height of the tip, satellites) is a pure function of the block position and of "a wall block under / over
# me", so the same wall looks the same for everybody and for everybody who joins later. Satellite crystals (sat_*) lean out of the shaft, feet (feet_*)
# sit round the lowest cell. Nothing looks at blocks more than one cell away (a chunk section rebuild only reaches one block across its border).
#
# Coordinates: Blender frame (Z up). Cell centre = (0, 0), the cell spans z 0..1, the footprint |x|,|y| <= 0.5. MC block (X, Y, Z) maps to the Blender
# frame as (X + 0.5, -(Z + 0.5), Y) for the cell origin.
import math

# ---------------------------------------------------------------------------------------------------------------- atlas (64 x 64)
ATLAS = 64
# name: (x, y, w, h) in pixels, origin top left, a 2 px gutter between the cells (bled by the painter).
# UV: u = (x + fx * w) / 64, v = 1 - (y + (1 - fy) * h) / 64, fy 0 = bottom (V up), with a half pixel inset.
CELLS = {
    "side": (0, 0, 30, 30),    # facet of a prism: vertical streaks, bevel lines at both edges, tileable top to bottom (no band per cell)
    "tip": (32, 0, 30, 30),    # pyramid facet: triangle (0,0) (1,0) (0.5,1); bright towards the apex
    "cap": (0, 32, 14, 14),    # flat top of a shoulder
    "core": (16, 32, 14, 30),  # inner glow: bright pale blue, vertical
}
INSET = 0.5


def uv(cell, fx, fy):
    x, y, w, h = CELLS[cell]
    px = x + INSET + fx * (w - 2 * INSET)
    py = y + INSET + (1.0 - fy) * (h - 2 * INSET)
    return (px / ATLAS, 1.0 - py / ATLAS)


# ---------------------------------------------------------------------------------------------------------------- geometry constants
HEX_JIT = (0.0, 5.0, -6.0, 4.0, -4.0, 3.0)  # degrees added to 60 * i: one fixed irregular hexagon for every ring (rings of neighbours must match)
NOMINAL_R = 0.5          # radius of the ring at a cell boundary as modelled
COL_R_MIN = 0.62         # per column width scale: thin needles .. fat prisms (real ring radius 0.31 .. 0.56)
COL_R_MAX = 1.12
RING_JITTER = 0.08       # +-4 percent per boundary
FLARE = 1.18             # the lowest ring of a column is wider (capped at FLARE_CAP)
FLARE_CAP = 0.56
LEAN_AMP = 0.15          # lean of the axis: A * sin(W * y + phase) with the ABSOLUTE height y, so every cell of a column agrees without looking at neighbours
LEAN_W = 0.55
JITTER_AMP = 0.02        # extra +-0.02 per boundary
PIVOT = 0.14             # tip / crown pieces: the height above this is stretched by the tip scale
TIP_SCALE_MIN = 0.80
TIP_SCALE_MAX = 1.15
CORE_RATIO = 0.50

SHAFTS = ("shaft_a", "shaft_b", "shaft_c")
TIPS = ("tip_a", "tip_b", "tip_c")
CROWNS = ("crown_a", "crown_b", "crown_c")
TOP_WEIGHTS = (("tip_a", 0.22), ("tip_b", 0.16), ("tip_c", 0.12), ("crown_a", 0.10), ("crown_b", 0.08), ("crown_c", 0.32))
SATS = ("sat_a", "sat_b", "sat_c", "sat_d")
FEET = ("feet_a", "feet_b")
PIECE_NAMES = SHAFTS + TIPS + CROWNS + SATS + FEET
CORE_PIECES = SHAFTS + TIPS + CROWNS   # these have a "<name>_core" object
MODEL = "rukia_ice_wall"


def obj_name(piece, core=False):
    return "%s_%s%s" % (MODEL, piece, "_core" if core else "")


# ---------------------------------------------------------------------------------------------------------------- integer hash (same bits as the Java port)
M32 = 0xFFFFFFFF


def mix(a, b, c, salt):
    h = (salt * 0x9E3779B1) & M32
    h ^= (a * 0x85EBCA6B) & M32
    h ^= (b * 0xC2B2AE35) & M32
    h ^= (c * 0x27D4EB2F) & M32
    h ^= h >> 16
    h = (h * 0x7FEB352D) & M32
    h ^= h >> 15
    h = (h * 0x846CA68B) & M32
    h ^= h >> 16
    return h


def unit(h):
    """0 <= value < 1, 24 bits."""
    return (h >> 8) / 16777216.0


def lerp(a, b, t):
    return a + (b - a) * t


# ---------------------------------------------------------------------------------------------------------------- piece geometry
class Mesh:
    def __init__(self):
        self.verts = []
        self.faces = []  # (tuple of vertex indices, cell name, [uv, ...])

    def add_ring(self, r, z, cx=0.0, cy=0.0):
        base = len(self.verts)
        for i in range(6):
            a = math.radians(60.0 * i + HEX_JIT[i])
            self.verts.append((cx + r * math.cos(a), cy + r * math.sin(a), z))
        return list(range(base, base + 6))

    def add_vert(self, x, y, z):
        self.verts.append((x, y, z))
        return len(self.verts) - 1

    def band(self, lo, up, cell="side"):
        for i in range(6):
            j = (i + 1) % 6
            self.faces.append(((lo[i], lo[j], up[j], up[i]), cell, [uv(cell, 0, 0), uv(cell, 1, 0), uv(cell, 1, 1), uv(cell, 0, 1)]))

    def pyramid(self, ring, apex, cell="tip"):
        for i in range(6):
            j = (i + 1) % 6
            self.faces.append(((ring[i], ring[j], apex), cell, [uv(cell, 0, 0), uv(cell, 1, 0), uv(cell, 0.5, 1)]))

    def cap(self, ring, cell="cap"):
        pts = []
        for i in ring:
            x, y, _ = self.verts[i]
            pts.append((x, y))
        cx = sum(p[0] for p in pts) / 6.0
        cy = sum(p[1] for p in pts) / 6.0
        rr = max(math.hypot(p[0] - cx, p[1] - cy) for p in pts)
        self.faces.append((tuple(ring), cell, [uv(cell, 0.5 + 0.5 * (p[0] - cx) / rr, 0.5 + 0.5 * (p[1] - cy) / rr) for p in pts]))

    def tris(self):
        return sum(1 if len(f[0]) == 3 else (2 if len(f[0]) == 4 else len(f[0]) - 2) for f in self.faces)

    def bbox(self):
        xs, ys, zs = zip(*self.verts)
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def _spire(m, cx, cy, r, z0, z1, apex_z, taper=1.0, apex_off=(0.0, 0.0), tilt=0.0, yaw=0.0, at=(0.0, 0.0, 0.0), cell_side="side"):
    """A hexagonal prism (rings at z0 and z1) with a pointed top; tilted about Y by `tilt` degrees (towards +X), yawed about Z, moved by `at`."""
    r0 = m.add_ring(r, z0, cx, cy)
    r1 = m.add_ring(r * taper, z1, cx, cy)
    ap = m.add_vert(cx + apex_off[0], cy + apex_off[1], apex_z)
    m.band(r0, r1, cell_side)
    m.pyramid(r1, ap)
    if tilt or yaw or any(at):
        t = math.radians(tilt)
        y = math.radians(yaw)
        first = r0[0]
        for k in range(first, len(m.verts)):
            x, yy, z = m.verts[k]
            x2 = x * math.cos(t) + z * math.sin(t)
            z2 = -x * math.sin(t) + z * math.cos(t)
            x3 = x2 * math.cos(y) - yy * math.sin(y)
            y3 = x2 * math.sin(y) + yy * math.cos(y)
            m.verts[k] = (x3 + at[0], y3 + at[1], z2 + at[2])


def _shaft(rings):
    m = Mesh()
    prev = None
    for r, z in rings:
        cur = m.add_ring(r, z)
        if prev:
            m.band(prev, cur)
        prev = cur
    return m


def _shaft_core(ratio=CORE_RATIO):
    m = Mesh()
    lo = m.add_ring(NOMINAL_R * ratio, 0.0)
    up = m.add_ring(NOMINAL_R * ratio, 1.0)
    m.band(lo, up, "core")
    return m


def _tip_core(top_z, center=(0.0, 0.0), r_top=0.15, z_top=0.45):
    """Inner glow of a top piece: starts as the shaft core ring, narrows to a small ring at z_top and ends in an apex."""
    m = Mesh()
    lo = m.add_ring(NOMINAL_R * CORE_RATIO, 0.0)
    up = m.add_ring(r_top, z_top, center[0], center[1])
    ap = m.add_vert(center[0], center[1], top_z)
    m.band(lo, up, "core")
    m.pyramid(up, ap, "core")
    return m


def _stepped(rings, spire_ring, apex, apex_off=(0.0, 0.0)):
    """Rings of the base (bottom ring first), then a pyramid from the ring with index `spire_ring`."""
    m = Mesh()
    ids = []
    for r, z in rings:
        ids.append(m.add_ring(r, z))
        if len(ids) > 1:
            m.band(ids[-2], ids[-1])
    ap = m.add_vert(apex_off[0], apex_off[1], apex)
    m.pyramid(ids[spire_ring], ap)
    return m


def _crown(spires, shoulder_r=0.46):
    m = Mesh()
    lo = m.add_ring(NOMINAL_R, 0.0)
    sh = m.add_ring(shoulder_r, PIVOT)
    m.band(lo, sh)
    m.cap(sh)
    for (cx, cy, r, z1, apex) in spires:
        _spire(m, cx, cy, r, PIVOT, z1, apex, taper=0.94)
    return m


def build_piece(name):
    """-> (shell Mesh, core Mesh or None). Cell frame: footprint +-0.5, z 0..1 (tips and crowns reach above 1)."""
    if name == "shaft_a":
        return _shaft([(0.50, 0.0), (0.50, 1.0)]), _shaft_core()
    if name == "shaft_b":
        return _shaft([(0.50, 0.0), (0.515, 0.5), (0.50, 1.0)]), _shaft_core()
    if name == "shaft_c":
        return _shaft([(0.50, 0.0), (0.49, 0.45), (0.50, 1.0)]), _shaft_core()
    if name == "tip_a":    # needle: a slender spire stepping out of the shaft
        return _stepped([(0.50, 0.0), (0.47, 0.10), (0.29, PIVOT + 0.02), (0.27, 0.55)], 3, 1.20), _tip_core(0.95, (0.0, 0.0), 0.15, 0.50)
    if name == "tip_b":    # chisel: broad pyramid with the apex pushed to one side
        return _stepped([(0.50, 0.0), (0.47, 0.30), (0.41, 0.45)], 2, 0.98, (0.20, 0.05)), _tip_core(0.72, (0.04, 0.0), 0.12, 0.40)
    if name == "tip_c":    # twin: two spires on one shoulder
        return (_crown([(-0.13, 0.02, 0.25, 0.50, 1.12), (0.20, -0.06, 0.19, 0.38, 0.84)], 0.47), _tip_core(0.90, (-0.13, 0.02), 0.12, 0.40))
    if name == "crown_a":  # three peaks
        return (_crown([(0.04, 0.02, 0.24, 0.70, 1.25), (-0.22, 0.10, 0.15, 0.50, 0.90), (0.15, -0.20, 0.13, 0.45, 0.70)]), _tip_core(1.00, (0.04, 0.02), 0.13, 0.40))
    if name == "crown_b":  # four peaks, the main one slender
        return (_crown([(-0.03, 0.04, 0.20, 0.80, 1.32), (0.20, 0.05, 0.14, 0.55, 0.95), (-0.20, -0.18, 0.14, 0.50, 0.75), (0.12, -0.25, 0.10, 0.45, 0.60)]),
                _tip_core(1.05, (-0.03, 0.04), 0.11, 0.45))
    if name == "crown_c":  # low cluster of short peaks (the column ends lower here)
        return (_crown([(0.0, 0.05, 0.20, 0.36, 0.64), (-0.24, 0.0, 0.14, 0.30, 0.52), (0.22, -0.10, 0.14, 0.32, 0.56), (0.05, -0.26, 0.10, 0.28, 0.44)]), _tip_core(0.50, (0.0, 0.05), 0.10, 0.30))
    if name in SATS:
        r, z1, ap, tilt = {"sat_a": (0.14, 0.55, 1.00, 32.0), "sat_b": (0.09, 0.80, 1.45, 22.0), "sat_c": (0.18, 0.40, 0.80, 42.0),
                           "sat_d": (0.20, 0.70, 1.35, 27.0)}[name]
        m = Mesh()
        _spire(m, 0.0, 0.0, r, -0.08, z1, ap, taper=0.9, tilt=tilt)
        return m, None
    if name in ("feet_a", "feet_b"):
        stubs = {"feet_a": [(20, 0.16, 0.30, 0.70, 56), (110, 0.11, 0.20, 0.48, 62), (200, 0.15, 0.32, 0.78, 50), (290, 0.12, 0.22, 0.52, 64)],
                 "feet_b": [(50, 0.17, 0.32, 0.80, 52), (170, 0.12, 0.24, 0.55, 60), (275, 0.14, 0.28, 0.66, 54)]}[name]
        m = Mesh()
        for (ang, r, z1, ap, tilt) in stubs:
            _spire(m, 0.0, 0.0, r, -0.06, z1, ap, taper=0.9, tilt=tilt, yaw=ang, at=(0.30 * math.cos(math.radians(ang)), 0.30 * math.sin(math.radians(ang)), 0.0))
        return m, None
    raise KeyError(name)


# ---------------------------------------------------------------------------------------------------------------- the field
def _column_scale(x, z):
    return COL_R_MIN + (COL_R_MAX - COL_R_MIN) * unit(mix(x, 0, z, 4))


def _boundary(x, yb, z):
    """Ring factor and axis jitter of the horizontal plane yb (an integer: the bottom of the block with Y = yb) of the column (x, z)."""
    f = _column_scale(x, z) * (1.0 + (unit(mix(x, yb, z, 5)) - 0.5) * RING_JITTER)
    ox = (unit(mix(x, yb, z, 6)) - 0.5) * 2.0 * JITTER_AMP
    oy = (unit(mix(x, yb, z, 7)) - 0.5) * 2.0 * JITTER_AMP
    return f, ox, oy


def _pick(u, names):
    return names[min(len(names) - 1, int(u * len(names)))]


def _pick_weighted(u, table):
    acc = 0.0
    for name, w in table:
        acc += w
        if u < acc:
            return name
    return table[-1][0]


def cell_params(x, y, z, below, above):
    """Everything the game needs to draw the block (x, y, z) = MC block position. below / above: a wall block stands under / over this one."""
    yaw = unit(mix(x, 0, z, 1)) * 360.0
    cr = _column_scale(x, z)
    amp = 0.04 + 0.20 * (COL_R_MAX - cr)
    col_ox = (unit(mix(x, 0, z, 2)) - 0.5) * 2.0 * amp
    col_oy = (unit(mix(x, 0, z, 3)) - 0.5) * 2.0 * amp
    lean_phase = unit(mix(x, 0, z, 16)) * 2.0 * math.pi
    lean_phase2 = unit(mix(x, 0, z, 17)) * 2.0 * math.pi
    lean_amp = LEAN_AMP * (0.4 + 0.6 * unit(mix(x, 0, z, 18)))
    fb, bxo, byo = _boundary(x, y, z)
    ft, txo, tyo = _boundary(x, y + 1, z)
    flare = (not below)
    if flare:
        fb = min(fb * FLARE, FLARE_CAP / NOMINAL_R)
    top = not above
    if top:
        piece = _pick_weighted(unit(mix(x, y, z, 8)), TOP_WEIGHTS)
        tip_scale = TIP_SCALE_MIN + (TIP_SCALE_MAX - TIP_SCALE_MIN) * unit(mix(x, y, z, 9))
    else:
        piece = _pick(unit(mix(x, y, z, 13)), SHAFTS)
        tip_scale = 1.0
    n_u = unit(mix(x, y, z, 10))
    n_sat = 0 if n_u < 0.20 else (1 if n_u < 0.65 else 2)
    sats = []
    for s in range(n_sat):
        kind = _pick(unit(mix(x, y, z, 20 + s)), SATS)
        az = unit(mix(x, y, z, 30 + s)) * 360.0
        zz = 0.08 + (0.45 if top else 0.65) * unit(mix(x, y, z, 40 + s))
        sats.append({"kind": kind, "az": az, "z": zz})
    feet = _pick(unit(mix(x, y, z, 14)), FEET) if flare else None
    feet_yaw = unit(mix(x, y, z, 15)) * 360.0
    return {"x": x, "y": y, "z": z, "yaw": yaw, "col_r": cr, "col_off": (col_ox, col_oy), "lean": (lean_amp, lean_phase, lean_phase2),
            "f_bottom": fb, "f_top": ft, "off_bottom": (bxo, byo), "off_top": (txo, tyo),
            "piece": piece, "top": top, "tip_scale": tip_scale, "sats": sats, "feet": feet, "feet_yaw": feet_yaw}


def axis_at(p, t):
    """Axis offset of the column at cell height t (0..1, clamped) as (ox, oy)."""
    t = max(0.0, min(1.0, t))
    amp, ph1, ph2 = p["lean"]
    ya = p["y"] + t
    return (lerp(p["off_bottom"][0], p["off_top"][0], t) + p["col_off"][0] + amp * math.sin(LEAN_W * ya + ph1),
            lerp(p["off_bottom"][1], p["off_top"][1], t) + p["col_off"][1] + amp * math.sin(LEAN_W * ya + ph2))


def ring_factor_at(p, t):
    t = max(0.0, min(1.0, t))
    return lerp(p["f_bottom"], p["f_top"], t)


def xform_column(p, v):
    """A vertex of the shaft / tip / crown piece (and its core) in the Blender frame of the cell -> cell frame after the variation."""
    x, y, z = v
    t = z
    if p["top"] and z > PIVOT:
        z = PIVOT + (z - PIVOT) * p["tip_scale"]
    f = ring_factor_at(p, t)
    x *= f
    y *= f
    a = math.radians(p["yaw"])
    ca, sa = math.cos(a), math.sin(a)
    xr = x * ca - y * sa
    yr = x * sa + y * ca
    ox, oy = axis_at(p, t)
    return (xr + ox, yr + oy, z)


def sat_origin(p, s):
    """Placement of satellite s: (x, y, z, yaw degrees) of its piece origin."""
    sat = p["sats"][s]
    ox, oy = axis_at(p, sat["z"])
    f = ring_factor_at(p, sat["z"])
    a = math.radians(sat["az"])
    rad = 0.34 * f
    return (ox + rad * math.cos(a), oy + rad * math.sin(a), sat["z"], sat["az"])


def feet_origin(p):
    ox, oy = axis_at(p, 0.0)
    return (ox, oy, 0.0, p["feet_yaw"])


def xform_rigid(origin, v):
    x, y, z, yaw = origin
    a = math.radians(yaw)
    return (x + v[0] * math.cos(a) - v[1] * math.sin(a), y + v[0] * math.sin(a) + v[1] * math.cos(a), z + v[2])


def cell_geometry(p, pieces):
    """Transformed (shell faces, core faces) of one block for the check wall: lists of (points, cell, uvs).
    pieces: {name: (shell Mesh, core Mesh or None)}"""
    shell, core = [], []

    def add(dst, mesh, fn):
        for idx, cell, uvs in mesh.faces:
            dst.append(([fn(mesh.verts[i]) for i in idx], cell, uvs))

    sm, cm = pieces[p["piece"]]
    fn = lambda v: xform_column(p, v)
    add(shell, sm, fn)
    if cm is not None:
        add(core, cm, fn)
    for s in range(len(p["sats"])):
        o = sat_origin(p, s)
        add(shell, pieces[p["sats"][s]["kind"]][0], lambda v, o=o: xform_rigid(o, v))
    if p["feet"]:
        o = feet_origin(p)
        add(shell, pieces[p["feet"]][0], lambda v: xform_rigid(o, v))
    return shell, core


# ---------------------------------------------------------------------------------------------------------------- golden data for the Java parity test
GOLDEN_CELLS = [(0, 0, 0, False, False), (0, 0, 0, False, True), (3, 4, -2, True, True), (3, 5, -2, True, False), (-7, 63, 12, False, True), (-7, 64, 12, True, True),
                (-7, 68, 12, True, False), (100, -5, -300, False, False), (-1, -1, -1, True, True), (12, 70, 7, True, False), (5, 2, 5, False, True), (-30, 100, 41, True, True)]
GOLDEN_POINTS = [(0.5, 0.0, 0.0), (0.2, -0.3, 0.7), (-0.1, 0.45, 1.0), (0.0, 0.0, 1.1), (0.25, 0.25, 0.4)]


def golden():
    out = []
    for (x, y, z, b, a) in GOLDEN_CELLS:
        p = cell_params(x, y, z, b, a)
        entry = {"x": x, "y": y, "z": z, "below": b, "above": a, "piece": p["piece"], "top": p["top"], "yaw": p["yaw"], "colR": p["col_r"], "fBottom": p["f_bottom"],
                 "fTop": p["f_top"], "tipScale": p["tip_scale"], "feet": p["feet"], "feetYaw": p["feet_yaw"], "sats": p["sats"],
                 "points": [list(xform_column(p, pt)) for pt in GOLDEN_POINTS],
                 "satOrigins": [list(sat_origin(p, s)) for s in range(len(p["sats"]))]}
        if p["feet"]:
            entry["feetOrigin"] = list(feet_origin(p))
        out.append(entry)
    return {"points": [list(pt) for pt in GOLDEN_POINTS], "cells": out}


def wall_cells(width=9, height=5, depth=2, x0=0, y0=0, z0=0):
    """The cells of an axis aligned test wall; below / above from the wall itself."""
    out = []
    for x in range(x0, x0 + width):
        for z in range(z0, z0 + depth):
            for y in range(y0, y0 + height):
                out.append(cell_params(x, y, z, y > y0, y < y0 + height - 1))
    return out
