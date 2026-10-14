# Layout, palette and shared geometry data of the Quincy effect set `uryu_fx` (pure python, no bpy: shared by uq_build.py (Blender),
# uq_paint.py (system python, numpy + Pillow) and uq_render.py). Contract: design/URYU_VFX_STORYBOARD.md sections 1 and 4.1.
# Atlas 256 x 256 RGBA, pixel coordinates with the origin top-left, rect = (x, y, w, h). UV convention of uq_build.uv_rect(): u, v in 0..1 over the
# rect, v = 0 at the TOP row of the rect (image space). The wing sheet is mapped PLANAR (see wing_uv()).
import math

ATLAS = 256

# ---- palette (storyboard 1.1) ---------------------------------------------------------------------
PAL = {
    "Q0": "#FFFFFF", "Q1": "#E4FAFF", "Q2": "#9DEBFF", "Q3": "#4FC8FF", "Q4": "#1F8FE0", "Q5": "#0E4FA8", "Q6": "#0A1E52",
    "Q7": "#6FE0FF", "Q8": "#1E5FB0", "Q9": "#DCEBFA", "QL": "#A99BFF",
    "wing_hi": "#8DE3FF",           # top of the wing gradient (4.2)
    "wing_lo": "#1F8FE0",           # bottom of the wing gradient
}

# ---- atlas rects ------------------------------------------------------------------------------------
RECT = {
    "wing":   (0, 0, 192, 136),     # planar wing sheet: 96 px per metre, x 0..2.0 m outward, z -0.38..1.04 m (up); both wings use it
    "flake":  (192, 0, 64, 24),     # flechette leaf: u along the length (tail 0 -> tip 1), v across; alpha cuts the comb of barbs at the tail
    "star":   (192, 24, 48, 16),    # star head: u = 0 at the white core in the middle -> 1 at the cyan spike points, v around
    "shaft":  (192, 40, 64, 8),     # arrow shaft: u tail -> head, v around the 4 sides
    "blade":  (192, 48, 64, 16),    # seele long blade strip: u along the blade (hilt 0 -> point 1), v across the width
    "pent":   (192, 64, 64, 8),     # pentagon edge strip: u along the edge, v across
    "band":   (0, 136, 96, 24),     # bracer: u around the 12 sides (8 px each), v along the height (top row = z + 0.1)
    "boot":   (0, 160, 96, 48),     # boot: u around the 12 sides, v along the height (top row = top of the calf)
    "bow":    (96, 136, 120, 12),   # bow limb strip: u along the bow (z -0.55 .. +0.55), v around the 6 sides
    "needle": (0, 208, 128, 8),     # three crossed planes, u along the length 1.2 m (tail 0 -> head 1), v across the 0.075 m width; alpha cut silhouette
}
# 4 x 4 px tone cells (shadow, base, light, highlight), chosen per face from the face normal (uq_build.pick_cell)
CELLS = {
    "reishi": [(0, 244, 4, 4), (4, 244, 4, 4), (8, 244, 4, 4), (12, 244, 4, 4)],
    "white":  [(16, 244, 4, 4), (20, 244, 4, 4), (24, 244, 4, 4), (28, 244, 4, 4)],
    "rim":    [(32, 244, 4, 4), (36, 244, 4, 4), (40, 244, 4, 4), (44, 244, 4, 4)],
}
CELL_TONES = {                      # palette tokens per tone cell (shadow .. highlight)
    "reishi": ["Q5", "Q4", "Q3", "Q2"],
    "white":  ["Q3", "Q2", "Q1", "Q0"],
    "rim":    ["Q1", "Q1", "Q0", "Q0"],
}
EMIT_CELLS = {"reishi": [0.55, 0.75, 0.90, 1.00], "white": [1.0, 1.0, 1.0, 1.0], "rim": [1.0, 1.0, 1.0, 1.0]}
EMIT = {"wing": 0.85, "bow": 0.70, "bow_core": 1.0, "arrow": 1.0, "band": 0.6, "boot": 0.6, "star": 1.0, "flake": 0.9, "needle": 1.0,
        "blade": 1.0, "pent": 1.0}
WING_ALPHA = 200                    # storyboard 4.2: texture alpha 200 with glitter specks at 255

# ---- the wing (storyboard 2.10, 2.11, 4.1) --------------------------------------------------------
# Axes (Blender, object space of uryu_wing_l): origin = the shoulder root, +X outward (the _l wing extends +X, _r is the mirror), +Z up, +Y = BEHIND the
# player (OBJ export forward -Z: Blender -Y is forward). The wing is a flat sheet in the plane y = WING_PA * x + WING_PB * z (a slight sweep back
# toward the tips, fitted so that both tip empties lie exactly on the plate corners).
WING_T0 = (1.70, 1.00)              # (x, z) of the upper spar tip relative to the root   = WING_TIPS[0] (1.7, 2.35 high, 0.45 back) minus the root (0, 1.35, 0.15)
WING_T2 = (1.25, -0.15)             # (x, z) of the lowest plate tip                      = WING_TIPS[2] (1.25, 1.2, 0.6) minus the root
WING_ROOT_BODY = (0.0, 1.35, 0.15)  # root in the body frame of WING_TIPS: sideways, height, backwards
WING_TIP_BACK = (0.30, 0.45)        # backwards offsets (+Y in Blender) of the two tips relative to the root (0.45 - 0.15, 0.60 - 0.15)
# The plates are SHINGLED: every plate lies in its own layer (a plane y = a x + b z + LAYER_Y[name], parallel to the others, 0.03 m apart) so that
# the fan of plates may overlap near the root without z fighting (the canon wing is a fan of bars that merge at the root). Plane fitted so that the
# centre of the spar tip and of the lowest plate tip lie exactly on the tip empties.
LAYER_Y = {"hook": 0.090, "spar": 0.060, "step": 0.055, "plate": 0.030, "low1": 0.0, "low2": -0.030, "low3": -0.060}
STEP_ON = "plate"                   # the step slab rests on the back of this plate (its layer is LAYER_Y[STEP_ON] + 0.025)
_c0, _c2 = LAYER_Y["spar"], LAYER_Y["low3"]
# 0.30 = a 1.70 + b 1.00 + c0 ; 0.45 = a 1.25 + b (-0.15) + c2
_det = WING_T0[0] * WING_T2[1] - WING_T0[1] * WING_T2[0]
_r0, _r2 = WING_TIP_BACK[0] - _c0, WING_TIP_BACK[1] - _c2
WING_PA = (_r0 * WING_T2[1] - WING_T0[1] * _r2) / _det
WING_PB = (WING_T0[0] * _r2 - _r0 * WING_T2[0]) / _det
WING_THICK = 0.025
CUT_DEG = 30.0                      # every plate end is cut at a sharp angle (acute corner 30 deg on the upper edge)


def _byroot(rx, rz, bx, bz, w0, w1):
    """plate tuple from the root top corner and the acute tip corner"""
    return (rx, rz, math.degrees(math.atan2(bz - rz, bx - rx)), math.hypot(bx - rx, bz - rz), w0, w1)


# plate = (root_top_x, root_top_z, theta_deg, L, w_root, w_tip): the upper edge runs from the root corner for L metres at theta; the acute 30 deg corner
# is its end; the lower edge is shorter by w_tip / tan(30 deg). Lower plates fan about 6 to 14 degrees apart (12.6, 6.5, -5.1).
PLATES = {
    "spar": (0.055, 0.050, 30.0, 1.90, 0.17, 0.17),     # main spar: its acute tip is exactly at WING_T0
    "plate": _byroot(0.17, -0.10, 1.52, 0.56, 0.14, 0.11),   # parallel plate, 4 deg flatter than the spar, with the step notch near its end
    "step": (1.05, 0.33, 26.0, 0.62, 0.055, 0.055),     # the step notch: a short slab on the back of the plate end, sticking out past its tip
    "low1": _byroot(0.25, -0.05, 1.46, 0.22, 0.115, 0.085),
    "low2": _byroot(0.32, -0.10, 1.38, 0.02, 0.105, 0.08),
    "low3": (0.36, -0.07, math.degrees(math.atan2(-0.15 + 0.07, 1.25 - 0.36)), math.hypot(1.25 - 0.36, -0.15 + 0.07), 0.09, 0.06),  # acute tip exactly at WING_T2
}
# root hook: a slender blade hanging from the spar root toward the shoulder blade (the curved piece at the root of [U vollstandig 5]), free quad (x, z)
HOOK = [(0.100, 0.020), (0.200, 0.000), (0.330, -0.300), (0.250, -0.325)]
# two outer spikes (flat rhombus pyramids) continuing the end cuts of these plates: (plate, length, half width)
SPIKES = [("low2", 0.16, 0.030), ("low1", 0.14, 0.030)]
# two loose chips (tetrahedra, layer of the lowest plate): centre (x, z), size
CHIPS = [(1.52, 1.12, 0.07), (1.30, -0.27, 0.055)]


def plate_poly(name):
    """The four corners of a plate in the wing plane (x, z), order: root top, tip top (acute 30 deg), tip bottom, root bottom."""
    rx, rz, th, L, w0, w1 = PLATES[name]
    t = math.radians(th)
    d = (math.cos(t), math.sin(t))
    n = (-math.sin(t), math.cos(t))
    cot = 1.0 / math.tan(math.radians(CUT_DEG))
    A = (rx, rz)
    B = (rx + L * d[0], rz + L * d[1])
    C = (rx + (L - w1 * cot) * d[0] - w1 * n[0], rz + (L - w1 * cot) * d[1] - w1 * n[1])
    D = (rx - w0 * n[0], rz - w0 * n[1])
    return [A, B, C, D]


def wing_polys():
    """All 2D polygons of the wing in painting order, lowest layer first (name, [(x, z), ...])."""
    order = ["low3", "low2", "low1", "plate", "step", "spar"]
    out = [(k, plate_poly(k)) for k in order]
    out.append(("hook", list(HOOK)))
    return out


def spike_geom(i):
    """(tip (x, z), base centre (x, z), half width, direction (x, z), plate name) of spike i."""
    name, ln, hw = SPIKES[i]
    A, B, C, D = plate_poly(name)
    t = math.radians(PLATES[name][2])
    d = (math.cos(t), math.sin(t))
    m = ((B[0] + C[0]) / 2, (B[1] + C[1]) / 2)
    return (m[0] + ln * d[0], m[1] + ln * d[1]), m, hw, d, name


def wing_xyz(x, z, layer):
    return (x, WING_PA * x + WING_PB * z + LAYER_Y[layer], z)


def wing_normal():
    """Unit normal of the wing planes, pointing to the back (+Y)."""
    nx, ny, nz = -WING_PA, 1.0, -WING_PB
    m = math.sqrt(nx * nx + ny * ny + nz * nz)
    return (nx / m, ny / m, nz / m)


# planar mapping of the wing sheet: x 0..2.0 m -> u, z 1.04 .. -0.38 -> v (v = 0 at the top row)
WING_X0, WING_X1 = 0.0, 2.0
WING_Z0, WING_Z1 = -0.38, 1.04      # bottom, top


def wing_px(x, z):
    """Pixel position (float, in atlas pixels) of a wing plane point."""
    rx, ry, rw, rh = RECT["wing"]
    return (rx + (x - WING_X0) / (WING_X1 - WING_X0) * rw, ry + (WING_Z1 - z) / (WING_Z1 - WING_Z0) * rh)


# ---- bow (as the existing uryu_bow: 1.1 m tall, origin = grip centre, limbs bend toward +Y = the string side) -----------
BOW_TIP_Y = 0.105
BOW_HALF = 0.55
# limb rings (z, half width X, half thickness Y); the pairs (0.22, 0.235) and (0.385, 0.40) are the two step notches (a short chamfer = a ledge)
BOW_RINGS = [(0.06, 0.0115, 0.0115), (0.20, 0.0200, 0.0070), (0.215, 0.0130, 0.0060), (0.385, 0.0120, 0.0055), (0.400, 0.0075, 0.0045)]
BOW_NOTCH_Z = [0.20, 0.385]         # the two step notches per limb are the short chamfers 0.20 -> 0.215 and 0.385 -> 0.40
# ---- object list in export order -------------------------------------------------------------------
OBJECTS = ["uryu_wing_l", "uryu_wing_r", "uryu_flechette", "uryu_needle", "uryu_star_head", "uryu_bracer", "uryu_boot", "uryu_bow",
           "uryu_bow_flex_1", "uryu_bow_flex_2", "uryu_bow_flex_3", "uryu_arrow", "uryu_seele_long", "uryu_pentagon_edge"]
BUDGET = {"uryu_wing_l": 150, "uryu_wing_r": 150, "uryu_flechette": 8, "uryu_needle": 12, "uryu_star_head": 24, "uryu_bracer": 24,
          "uryu_boot": 36, "uryu_bow": 180, "uryu_bow_flex_1": 180, "uryu_bow_flex_2": 180, "uryu_bow_flex_3": 180, "uryu_arrow": 40,
          "uryu_seele_long": 16, "uryu_pentagon_edge": 4}
TARGET = {"uryu_wing_l": 120, "uryu_wing_r": 120, "uryu_flechette": 8, "uryu_needle": 12, "uryu_star_head": 24, "uryu_bracer": 24,
          "uryu_boot": 36, "uryu_bow": 160, "uryu_bow_flex_1": 160, "uryu_bow_flex_2": 160, "uryu_bow_flex_3": 160, "uryu_arrow": 40,
          "uryu_seele_long": 16, "uryu_pentagon_edge": 4}
FLEX_DEG = {"uryu_bow_flex_1": 3.0, "uryu_bow_flex_2": 6.0, "uryu_bow_flex_3": 9.0}
