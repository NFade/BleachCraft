# Paint rukia_sealed_diffuse.png / rukia_sealed_emissive.png (system python). Palette: ART_BIBLE 1.1.
#   python paint_rukia_sealed.py
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paint_common import *
import atlas_layouts as al

MODEL = "rukia_sealed"
L = al.RUKIA_SEALED
OUT = os.path.join(r"D:\MineBleach\blender\export", MODEL)

# palette (art bible 1.1)
LACE, LACE_HI, LACE_LO = rgb("#822933"), rgb("#A83A45"), rgb("#5C1B24")
UNDER, UNDER_HI, UNDER_LO = rgb("#D8C9A0"), rgb("#E8DDB5"), rgb("#B09F74")
TSU, TSU_HI, TSU_LO = rgb("#AF9668"), rgb("#CDB88A"), rgb("#7D6B49")
FIT, FIT_HI, FIT_LO = rgb("#8C7650"), rgb("#A38B5E"), rgb("#6B5A3E")
SAYA, SAYA_HI, SAYA_LO, SAYA_MID = rgb("#2A2433"), rgb("#4E4560"), rgb("#1E1A26"), rgb("#3A3347")
STEEL, STEEL_LIT, STEEL_EDGE, STEEL_TEMPER = rgb("#B9C3D2"), rgb("#D1D9E8"), rgb("#EEF3FA"), rgb("#E4EAF4")
STEEL_SH, STEEL_DK = rgb("#A4B0C3"), rgb("#8D99AD")

cv = Canvas()


def blade_strip(a, side):
    """a: strip island view (h x w x 4); u = edge (0) -> spine (1), v = root (bottom) -> tip (top)."""
    h, w = a.shape[:2]
    base, lit, shd = (STEEL, STEEL_LIT, STEEL_SH) if side == "a" else (shade(STEEL, 0.93), shade(STEEL_LIT, 0.93), shade(STEEL_SH, 0.93))
    ridge_u = 0.55
    for r in range(h):
        s = 1.0 - (r + 0.5) / h                     # 0 root .. 1 tip
        hamon = 0.20 + 0.035 * math.sin(r * 0.33) + 0.02 * math.sin(r * 0.91 + 1.0)
        # the hamon runs out toward the tip (kissaki): line follows the edge there
        hamon = hamon * (1.0 - 0.6 * max(0.0, (s - 0.9) / 0.1))
        for c in range(w):
            u = (c + 0.5) / w
            if u < hamon:
                col = lit
            else:
                col = base if u < ridge_u else shd
            a[r, c] = col
        ch = min(w - 1, int(hamon * w))
        a[r, ch] = STEEL_TEMPER                        # 1 px wavy temper line
        a[r, 0] = STEEL_EDGE                           # edge highlight
        rc = min(w - 1, int(ridge_u * w))
        a[r, rc] = STEEL_EDGE if side == "a" else lit  # shinogi ridge streak
        a[r, w - 1] = STEEL_DK                         # spine shadow
    # yokote: the kissaki starts at 0.9 of the length (yokote line, 1 px)
    ry = int(round((1.0 - 0.8994) * h))
    a[ry, :] = shade(STEEL_EDGE, 0.97)
    a[ry + 1, :] = STEEL_TEMPER if side == "a" else lit


# ---- wrap
a = cv.view(L["wrap"])
paint_wrap(a, LACE, LACE_HI, LACE_LO, UNDER, UNDER_HI, UNDER_LO)

# ---- blade strips
a = cv.view(L["blade_a"]); blade_strip(a, "a")
a = cv.view(L["blade_b"]); blade_strip(a, "b")
a = cv.view(L["blade_s"]); fill(a, STEEL_DK); a[:, 1] = STEEL_SH
a = cv.view(L["blade_e"]); fill(a, STEEL_EDGE)

# ---- saya (u = 0 at the spine +Y, .25 -X, .5 edge -Y, .75 +X)
a = cv.view(L["saya"])
fill(a, SAYA)
streaks(a, [(0.0, 0.06, SAYA_LO), (0.94, 1.0, SAYA_LO), (0.34, 0.40, SAYA_HI), (0.40, 0.44, SAYA_MID), (0.60, 0.64, SAYA_MID),
            (0.47, 0.53, SAYA_MID)])
a[:4, :] = SAYA_LO                                     # tint near the kojiri end is the top; koiguchi end = bottom rows
a[-3:, :] = SAYA_LO

# ---- tsuba faces (planar, 560 px/m)
outline, holes = al.sealed_tsuba_shapes()
for key, flip in (("tsuba_front", False), ("tsuba_back", True)):
    isl = L[key]
    a = cv.view(isl)
    X, Y = planar_grid(isl, al.SEALED_TSUBA_PX_PER_M, flip)
    fill(a, TSU)
    d_out = poly_sdist(X, Y, outline)
    inside = point_in_poly(X, Y, outline)
    # flame embossing on the two long sides (|x| > 17 mm): two-tone tongues rising along Y (texture only)
    side = np.abs(X) > 0.0165
    phase = (Y * 430.0 + np.abs(X) * 260.0)
    tongue = np.sin(phase) + 0.55 * np.sin(phase * 2.1 + 0.8)
    a[side & (tongue > 0.55)] = TSU_HI
    a[side & (tongue < -0.85)] = TSU_LO
    # rim: 1 px band just inside the outline, light on the upper-left, dark on the lower-right
    rim = inside & (d_out < 1.5 / al.SEALED_TSUBA_PX_PER_M)
    lighter = (X * 0.6 - Y * 0.8) < 0
    a[rim & lighter] = TSU_HI
    a[rim & ~lighter] = TSU_LO
    # slits: dark ring around each hole
    for h in holes:
        dh = poly_sdist(X, Y, h)
        inh = point_in_poly(X, Y, h)
        a[(~inh) & (dh < 1.2 / al.SEALED_TSUBA_PX_PER_M)] = TSU_LO
    # blade-hole seat outline (32 x 10 mm), covered by the habaki in game
    seat = (np.abs(X) < 0.0055) & (np.abs(Y) < 0.0165) & ((np.abs(X) > 0.0035) | (np.abs(Y) > 0.0145))
    a[seat] = TSU_LO
    a[~inside & ~point_in_poly(X, Y, [(2 * x, 2 * y) for x, y in outline])] = TSU_LO  # outside the plate (never visible)

# ---- tsuba rim bands + slit walls
a = cv.view(L["tsuba_rim"])
fill(a, TSU)
a[:3, :] = TSU_HI
a[3:6, :] = TSU
a[6:, :] = TSU_LO
a = cv.view(L["tsuba_slit"])
fill(a, TSU_LO)
a[0, :] = shade(TSU_LO, 1.1)


def fitting(isl, hi_u=0.30):
    a = cv.view(isl)
    fill(a, FIT)
    h, w = a.shape[:2]
    streaks(a, [(hi_u, hi_u + max(0.04, 1.5 / w), FIT_HI), (0.72, 0.72 + max(0.05, 2.0 / w), FIT_LO)])
    a[-max(1, h // 5):, :] = FIT_LO if h > 5 else a[-1:, :]
    a[0, :] = FIT_HI
    return a


fitting(L["kashira_side"])
a = cv.view(L["kashira_cap"]); fill(a, FIT_LO); a[1:-1, 1:-1] = shade(FIT, 0.85)
fitting(L["fuchi_side"], 0.28)
a = cv.view(L["habaki_side"]); fill(a, TSU); streaks(a, [(0.20, 0.26, TSU_HI), (0.74, 0.82, TSU_LO)]); a[0, :] = TSU_HI
a = cv.view(L["habaki_top"]); fill(a, TSU_HI)
fitting(L["koiguchi"], 0.30)
fitting(L["kojiri"], 0.30)
a = cv.view(L["kojiri_cap"]); fill(a, FIT_LO)

img = cv.dilate(2)
cv.img = img
os.makedirs(OUT, exist_ok=True)
cv.save(os.path.join(OUT, MODEL + "_diffuse.png"))
# emissive: none (art bible 1.1) -> fully transparent white map, still exported so the item pipeline is uniform
save_emissive_rgb(np.zeros((al.ATLAS, al.ATLAS)), os.path.join(OUT, MODEL + "_emissive.png"))
print("painted", MODEL, os.listdir(OUT))
