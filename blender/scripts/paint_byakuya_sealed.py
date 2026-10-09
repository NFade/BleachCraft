# Paint byakuya_sealed_diffuse.png / byakuya_sealed_emissive.png (system python). Palette: ART_BIBLE 1.3.
#   python -I paint_byakuya_sealed.py
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paint_common import *
import atlas_layouts as al

MODEL = "byakuya_sealed"
L = al.BYAKUYA_SEALED
OUT = os.path.join(r"D:\MineBleach\blender\export", MODEL)

# palette (art bible 1.3)
LACE, LACE_HI, LACE_LO = rgb("#BCB2D3"), rgb("#DAD3EA"), rgb("#8F84B0")
UNDER, UNDER_HI, UNDER_LO = rgb("#E8DDB5"), rgb("#F4EDD2"), rgb("#C2B58A")
BRZ, BRZ_HI, BRZ_LO = rgb("#A58D5F"), rgb("#CBB584"), rgb("#7A6743")
BRZ_DK = rgb("#5E4F33")
KAS, KAS_HI, KAS_LO = rgb("#E9E2C0"), rgb("#F6F1DA"), rgb("#BDB690")
SAYA, SAYA_HI, SAYA_LO, SAYA_MID = rgb("#2E2840"), rgb("#554A73"), rgb("#201C2E"), rgb("#3F3856")
STEEL, STEEL_LIT, STEEL_EDGE, STEEL_TEMPER = rgb("#C9CED6"), rgb("#DDE1E8"), rgb("#F0F4FA"), rgb("#E6EAF1")
STEEL_SH, STEEL_DK = rgb("#B2B8C3"), rgb("#9AA1AE")

cv = Canvas()


def blade_strip(a, side):
    """a: strip island view (h x w x 4); u = edge (0) -> spine (1), v = root (bottom) -> tip (top)."""
    h, w = a.shape[:2]
    base, lit, shd = (STEEL, STEEL_LIT, STEEL_SH) if side == "a" else (shade(STEEL, 0.94), shade(STEEL_LIT, 0.94), shade(STEEL_SH, 0.94))
    ridge_u = 0.55
    for r in range(h):
        s = 1.0 - (r + 0.5) / h
        hamon = 0.20 + 0.03 * math.sin(r * 0.29 + 0.6) + 0.02 * math.sin(r * 0.83)
        hamon = hamon * (1.0 - 0.6 * max(0.0, (s - 0.9) / 0.1))
        for c in range(w):
            u = (c + 0.5) / w
            if u < hamon:
                col = lit
            else:
                col = base if u < ridge_u else shd
            a[r, c] = col
        ch = min(w - 1, int(hamon * w))
        a[r, ch] = STEEL_TEMPER
        a[r, 0] = STEEL_EDGE
        rc = min(w - 1, int(ridge_u * w))
        a[r, rc] = STEEL_EDGE if side == "a" else lit
        a[r, w - 1] = STEEL_DK
    ry = int(round((1.0 - 0.8994) * h))
    a[ry, :] = shade(STEEL_EDGE, 0.97)
    a[ry + 1, :] = STEEL_TEMPER if side == "a" else lit


# ---- wrap: 11 diamonds (odd rings 1..21), cream windows smaller than the lacing so the cord dominates
a = cv.view(L["wrap"])
paint_wrap(a, LACE, LACE_HI, LACE_LO, UNDER, UNDER_HI, UNDER_LO, rings=al.BYAKUYA_WRAP_RINGS, gap_px=al.BYAKUYA_WRAP_PX_PER_GAP,
           diamond_w=al.BYAKUYA_WRAP_DIAMOND_W, win=0.70, rimw=0.84)

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
a[:4, :] = SAYA_LO
a[-3:, :] = SAYA_LO

# ---- tsuba faces (planar, 740 px/m): bronze window frame
outline, holes = al.byakuya_tsuba_shapes()
PX = al.BYAKUYA_TSUBA_PX_PER_M
for key, flip in (("tsuba_front", False), ("tsuba_back", True)):
    isl = L[key]
    a = cv.view(isl)
    X, Y = planar_grid(isl, PX, flip)
    fill(a, BRZ)
    d_out = poly_sdist(X, Y, outline)
    inside = point_in_poly(X, Y, outline)
    lighter = (X * 0.6 - Y * 0.8) < 0                       # light from the upper left
    hub = (np.abs(X) < 0.009) & (np.abs(Y) < 0.020)
    a[inside & hub] = shade(BRZ, 1.07)                       # raised-looking hub plate
    # frame bevel look: light/dark 1 px line 1.5 px inside every hole edge and the outer rim
    in_any_hole = np.zeros(X.shape, bool)
    d_hole = np.full(X.shape, 1e9)
    for h in holes:
        in_any_hole |= point_in_poly(X, Y, h)
        d_hole = np.minimum(d_hole, poly_sdist(X, Y, h))
    solid = inside & ~in_any_hole
    ring = solid & (d_hole >= 1.0 / PX) & (d_hole < 2.2 / PX)
    a[ring & lighter] = BRZ_HI
    a[ring & ~lighter] = shade(BRZ, 0.9)
    a[solid & (d_hole < 1.0 / PX)] = BRZ_LO                  # dark edge ring where the walls start
    rim = inside & (d_out < 1.5 / PX)
    a[rim & lighter] = BRZ_HI
    a[rim & ~lighter] = BRZ_LO
    a[rim & ~lighter & (d_out < 0.7 / PX)] = BRZ_DK
    # blade-hole seat outline (32 x 10 mm), covered by the habaki in game
    seat = (np.abs(X) < 0.0055) & (np.abs(Y) < 0.0165) & ((np.abs(X) > 0.0035) | (np.abs(Y) > 0.0145))
    a[seat] = BRZ_LO
    a[~inside & ~point_in_poly(X, Y, [(2 * x, 2 * y) for x, y in outline])] = BRZ_LO   # outside the plate, never visible
    # texels inside the windows are never sampled; keep them the wall colour so the dilated gutter does not bleed bright bronze
    a[in_any_hole] = BRZ_LO

# ---- tsuba rim bands + window walls
a = cv.view(L["tsuba_rim"])
fill(a, BRZ)
a[:3, :] = BRZ_HI
a[3:6, :] = BRZ
a[6:, :] = BRZ_LO
a = cv.view(L["tsuba_win"])
fill(a, BRZ_LO)
a[0, :] = shade(BRZ_LO, 1.12)
a[-1, :] = BRZ_DK


def fitting(isl, base=BRZ, hi=BRZ_HI, lo=BRZ_LO, hi_u=0.30):
    a = cv.view(isl)
    fill(a, base)
    h, w = a.shape[:2]
    streaks(a, [(hi_u, hi_u + max(0.04, 1.5 / w), hi), (0.72, 0.72 + max(0.05, 2.0 / w), lo)])
    a[-max(1, h // 5):, :] = lo if h > 5 else a[-1:, :]
    a[0, :] = hi
    return a


fitting(L["kashira_side"], KAS, KAS_HI, KAS_LO)
a = cv.view(L["kashira_cap"]); fill(a, KAS_LO); a[1:-1, 1:-1] = KAS
a[2:5, 2:6] = KAS_HI                                        # a little highlight on the top face
fitting(L["fuchi_side"], hi_u=0.28)
a = cv.view(L["habaki_side"]); fill(a, BRZ); streaks(a, [(0.20, 0.26, BRZ_HI), (0.74, 0.82, BRZ_LO)]); a[0, :] = BRZ_HI
a = cv.view(L["habaki_top"]); fill(a, BRZ_HI)
fitting(L["koiguchi"])
fitting(L["kojiri"])
a = cv.view(L["kojiri_cap"]); fill(a, BRZ_LO)

img = cv.dilate(2)
cv.img = img
os.makedirs(OUT, exist_ok=True)
cv.save(os.path.join(OUT, MODEL + "_diffuse.png"))
# emissive: none (art bible 1.3) -> fully transparent white map, still exported so the item pipeline is uniform
save_emissive_rgb(np.zeros((al.ATLAS, al.ATLAS)), os.path.join(OUT, MODEL + "_emissive.png"))
print("painted", MODEL, os.listdir(OUT))
