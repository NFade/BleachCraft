# Paint byakuya_shikai_diffuse.png / byakuya_shikai_emissive.png (system python). Palette: ART_BIBLE 1.3 (hilt) and 1.4 (petal, shard).
#   python -I paint_byakuya_shikai.py
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paint_common import *
import atlas_layouts as al

MODEL = "byakuya_shikai"
L = al.BYAKUYA_SHIKAI
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


# ---- wrap: 11 diamonds (odd rings 1..21), cream windows smaller than the lacing so the cord dominates
a = cv.view(L["wrap"])
paint_wrap(a, LACE, LACE_HI, LACE_LO, UNDER, UNDER_HI, UNDER_LO, rings=al.BYAKUYA_WRAP_RINGS, gap_px=al.BYAKUYA_WRAP_PX_PER_GAP,
           diamond_w=al.BYAKUYA_WRAP_DIAMOND_W, win=0.70, rimw=0.84)

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

# ---- habaki top: the hilt end (flat top with a 1 mm inset collar ring on the side island)
a = cv.view(L["habaki_top"]); fill(a, BRZ_HI); a[0, :] = BRZ; a[-1, :] = BRZ_LO; a[:, 0] = BRZ; a[:, -1] = BRZ_LO

# ---- petal (ART_BIBLE 1.4): leaf outline projected on the (z, y) plane; edge line, centre ridge, tang painted; emissive 25 percent over the
# blade and 60 percent on the edge line (runtime tint #F9C8F6)
PET, PET_HI, PET_EDGE, PET_LO, PET_TANG = rgb("#E5A4DC"), rgb("#F9C8F6"), rgb("#FFE9FB"), rgb("#C98AC1"), rgb("#B993B6")
em = np.zeros((al.ATLAS, al.ATLAS))
isl = L["petal"]
a = cv.view(isl)
fill(a, PET)
PXM = al.PETAL_PX_PER_M
e_pet = np.zeros((isl.h, isl.w))
for r in range(isl.h):
    y = al.PETAL_YC + (isl.h / 2.0 - (r + 0.5)) / PXM
    for c in range(isl.w):
        z = (c + 0.5 - 1.0) / PXM
        ye, yf, ys = al.petal_profile(z)
        tol = 0.55 / PXM
        if z < -tol or z > al.PETAL_LEN + tol or y < ye - tol or y > ys + tol:
            continue                                         # outside the leaf: never sampled (gutter fills it later)
        col = PET
        if y >= yf:
            col = PET_HI if (y - yf) < 1.3 / PXM else PET   # light band beside the centre ridge on the spine half
        else:
            col = PET
        if abs(y - yf) < 0.5 / PXM:
            col = PET_HI                                     # centre ridge line
        if y - ye < 1.0 / PXM + 1e-9:
            col = PET_EDGE                                   # 1 px edge line
        elif ys - y < 1.0 / PXM:
            col = PET_LO                                     # spine rim, one shade down
        if z < 0.006:
            col = PET_TANG                                   # painted 6 mm tang
        a[r, c] = col
        e_pet[r, c] = 0.60 if col == PET_EDGE else 0.25
em[isl.y:isl.y + isl.h, isl.x:isl.x + isl.w] = e_pet

# ---- shard: steel splinter (end faces + 3 side strips)
isl = L["shard_face"]
a = cv.view(isl)
fill(a, STEEL)
tri = al.shard_tri()
yc = (min(p[0] for p in tri) + max(p[0] for p in tri)) / 2.0
zc = (min(p[1] for p in tri) + max(p[1] for p in tri)) / 2.0
cols = yc + (np.arange(isl.w) + 0.5 - isl.w / 2.0) / al.SHARD_PX_PER_M
rows = zc + (isl.h / 2.0 - (np.arange(isl.h) + 0.5)) / al.SHARD_PX_PER_M
Y, Z = np.meshgrid(cols, rows)
poly = [(y, z) for y, z in tri]
inside = point_in_poly(Y, Z, poly)
d = poly_sdist(Y, Z, poly)
a[inside & (d < 1.1 / al.SHARD_PX_PER_M)] = STEEL_DK
a[inside & (d >= 1.1 / al.SHARD_PX_PER_M) & (Y < yc)] = STEEL_LIT
a[~inside & (d > 1.0 / al.SHARD_PX_PER_M)] = STEEL_SH
isl = L["shard_side"]
a = cv.view(isl)
fill(a, STEEL_DK)
a[:, 0:2] = STEEL_SH
a[:, 2:4] = STEEL_DK
a[:, 4:6] = STEEL_LIT
a[0, :] = STEEL_EDGE

img = cv.dilate(2)
cv.img = img
os.makedirs(OUT, exist_ok=True)
cv.save(os.path.join(OUT, MODEL + "_diffuse.png"))
# emissive RGBA (white, A = intensity); the 2 px gutters take the neighbouring island intensity
em_img = em.copy()
isl_mask = np.zeros_like(cv.mask)
for i in L.values():
    isl_mask[i.y:i.y + i.h, i.x:i.x + i.w] = True
for _ in range(2):
    new = isl_mask.copy()
    out = em_img.copy()
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        sm = np.roll(isl_mask, (dy, dx), axis=(0, 1))
        sv = np.roll(em_img, (dy, dx), axis=(0, 1))
        take = sm & ~new
        out[take] = sv[take]
        new |= take
    em_img, isl_mask = out, new
save_emissive_rgb(em_img, os.path.join(OUT, MODEL + "_emissive.png"))
print("painted", MODEL, "emissive texels > 0:", int((em_img > 0).sum()), os.listdir(OUT))
