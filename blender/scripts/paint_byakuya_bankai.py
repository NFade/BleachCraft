# Paint byakuya_bankai_diffuse.png / byakuya_bankai_emissive.png, 512 x 512 (system python: numpy + Pillow).
#   python -I paint_byakuya_bankai.py
# Palette: ART_BIBLE 1.6 (giant blade #E4E8F0 / #A9B2C2 / #7C869A, edge line #F4F6FA, soil band #4A3A2E, tip glow #F2E9FF, Hakuteiken #F4F8FF /
# #FFFFFF, halo #FFFFFF, ripple #CFC3F0) and 1.3 (hilt, same as byakuya_shikai). Emissive RGBA (white, A = intensity): giant blade top 1.2 m gradient
# 0 -> 100 percent (z 6.8 -> tip) + 40 percent edge line over the whole length, wings / halo / Hakuteiken blade 100 percent (barb lines 82 percent).
# Runtime tint of the blade glow #F2E9FF (pink Senkei tint #F25FB8 is the deferred Senkei sword's).
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paint_common import *
import atlas_layouts as al

MODEL = "byakuya_bankai"
L = al.BYAKUYA_BANKAI
OUT = os.path.join(r"D:\MineBleach\blender\export", MODEL)
N = al.BB_ATLAS

# giant blade palette
GB_HI, GB_BASE, GB_SHADE, GB_EDGE = rgb("#E4E8F0"), rgb("#A9B2C2"), rgb("#7C869A"), rgb("#F4F6FA")
GB_GLOW = rgb("#F2E9FF")
SOIL, SOIL_DK, SOIL_LT = rgb("#4A3A2E"), rgb("#392C23"), rgb("#5E4B3B")
# hilt palette (byakuya_shikai / byakuya_sealed)
LACE, LACE_HI, LACE_LO = rgb("#BCB2D3"), rgb("#DAD3EA"), rgb("#8F84B0")
UNDER, UNDER_HI, UNDER_LO = rgb("#E8DDB5"), rgb("#F4EDD2"), rgb("#C2B58A")
BRZ, BRZ_HI, BRZ_LO = rgb("#A58D5F"), rgb("#CBB584"), rgb("#7A6743")
BRZ_DK = rgb("#5E4F33")
KAS, KAS_HI, KAS_LO = rgb("#E9E2C0"), rgb("#F6F1DA"), rgb("#BDB690")
STEEL, STEEL_LIT, STEEL_EDGE = rgb("#C9CED6"), rgb("#DDE1E8"), rgb("#F0F4FA")
STEEL_SH, STEEL_DK = rgb("#B2B8C3"), rgb("#9AA1AE")
HK_WHITE, HK_CORE, HK_SHADE = rgb("#F4F8FF"), rgb("#FFFFFF"), rgb("#DDE6F6")
RIPPLE, RIPPLE_HI = rgb("#CFC3F0"), rgb("#EDE8FB")

cv = Canvas(N)
em = np.zeros((N, N))
rng = np.random.RandomState(20261009)


def lerp_c(a, b, t):
    return tuple(int(round(int(a[i]) + (int(b[i]) - int(a[i])) * t)) for i in range(3)) + (255,)


def rows_z(isl):
    """z (m) of every row of a blade island (arc fraction v -> z through the ring stations)."""
    return np.array([al.gb_z_of_v(1.0 - (r + 0.5) / isl.h) for r in range(isl.h)])


# ------------------------------------------------------------------------------------------------ giant blade
zrow = rows_z(L["blade_a"])
glow_t = np.clip((zrow - al.GB_GLOW_Z0) / (al.GB_H - al.GB_GLOW_Z0), 0.0, 1.0)      # 0 at z 6.8, 1 at the tip
soil = zrow < 0.5


def streaks_for(h, w, c0, c1, n, lens, seed):
    """vertical dashes (col, row0, length) inside columns c0..c1"""
    r = np.random.RandomState(seed)
    out = []
    for _ in range(n):
        out.append((int(r.randint(c0, c1 + 1)), int(r.randint(0, h)), int(r.randint(lens[0], lens[1]))))
    return out


def paint_blade_side(key, bevel, flat, ridge_dk, hi_flat, lo_flat, seed):
    isl = L[key]
    a = cv.view(isl)
    h, w = a.shape[:2]
    for c in range(w):
        u = (c + 0.5) / w
        if u < 0.10:
            col = GB_EDGE if c == 0 else lerp_c(GB_EDGE, bevel, 0.5)
        elif u < 0.55:
            col = bevel
        elif c == int(0.55 * w) + 0:
            col = ridge_dk
        else:
            col = flat
        a[:, c] = col
    # painted streaks on the flat
    c_flat0 = int(0.55 * w) + 1
    for col, r0, ln in streaks_for(h, w, c_flat0, w - 1, 22, (6, 38), seed):
        a[r0:r0 + ln, col] = hi_flat
    for col, r0, ln in streaks_for(h, w, c_flat0, w - 1, 12, (8, 30), seed + 1):
        a[r0:r0 + ln, col] = lo_flat
    a[:, c_flat0 + 3] = np.where((np.arange(h) % 29 < 19)[:, None], lerp_c(flat, hi_flat, 0.6), a[:, c_flat0 + 3])   # a long highlight streak
    # bevel streaks (lighter dashes)
    for col, r0, ln in streaks_for(h, w, 3, int(0.55 * w) - 1, 10, (5, 24), seed + 2):
        a[r0:r0 + ln, col] = lerp_c(bevel, GB_EDGE, 0.55)
    # glow zone: blend toward the white-lilac tip colour
    for r in range(h):
        if glow_t[r] > 0:
            for c in range(w):
                a[r, c] = lerp_c(tuple(a[r, c]), GB_GLOW, 0.80 * glow_t[r])
    # buried soil band: dark soil with dither and a ragged upper edge
    for r in range(h):
        z = zrow[r]
        if z < 0.62:
            for c in range(w):
                ragged = 0.5 + 0.12 * (((c * 7 + r * 3) % 5) - 2) / 2.0
                if z < ragged:
                    n = (c * 13 + r * 7) % 6
                    a[r, c] = SOIL_LT if n == 0 else (SOIL_DK if n in (1, 2) else SOIL)
    # emissive: gradient + 40 percent edge line (first column) above the soil band
    e = np.zeros((h, w))
    e[:, :] = glow_t[:, None]
    e[:, 0] = np.maximum(e[:, 0], 0.40)
    e[soil, :] = 0.0
    em[isl.y:isl.y + h, isl.x:isl.x + w] = e


paint_blade_side("blade_a", GB_HI, GB_BASE, rgb("#8C97AB"), rgb("#BAC2D0"), rgb("#9AA4B6"), 11)
paint_blade_side("blade_b", rgb("#CBD1DC"), rgb("#9CA6B8"), rgb("#7C869A"), rgb("#ADB6C5"), rgb("#8A94A8"), 21)

# spine: shade tone, 1 px darker rims, lighter streaks
isl = L["blade_s"]
a = cv.view(isl)
h, w = a.shape[:2]
fill(a, GB_SHADE)
a[:, 0] = shade(GB_SHADE, 0.88)
a[:, -1] = shade(GB_SHADE, 0.88)
a[:, 2] = lerp_c(GB_SHADE, GB_BASE, 0.45)
for col, r0, ln in streaks_for(h, w, 1, 4, 14, (6, 30), 31):
    a[r0:r0 + ln, col] = lerp_c(GB_SHADE, GB_BASE, 0.35)
for r in range(h):
    if glow_t[r] > 0:
        for c in range(w):
            a[r, c] = lerp_c(tuple(a[r, c]), GB_GLOW, 0.80 * glow_t[r])
    if zrow[r] < 0.62:
        for c in range(w):
            if zrow[r] < 0.5 + 0.06 * (((c * 5 + r) % 3) - 1):
                a[r, c] = SOIL_DK if (c + r) % 3 == 0 else SOIL
e = np.zeros((h, w))
e[:, :] = glow_t[:, None]
e[soil, :] = 0.0
em[isl.y:isl.y + h, isl.x:isl.x + w] = e

# edge flat (4 mm): the bright edge line
isl = L["blade_e"]
a = cv.view(isl)
h, w = a.shape[:2]
fill(a, GB_EDGE)
for r in range(h):
    if zrow[r] < 0.5:
        a[r, :] = SOIL
e = np.full((h, w), 0.40)
e[soil, :] = 0.0
e = np.maximum(e, glow_t[:, None])
e[soil, :] = 0.0
em[isl.y:isl.y + h, isl.x:isl.x + w] = e

# ------------------------------------------------------------------------------------------------ hilt (copy of the byakuya_shikai hilt)
a = cv.view(L["wrap"])
paint_wrap(a, LACE, LACE_HI, LACE_LO, UNDER, UNDER_HI, UNDER_LO, rings=al.BYAKUYA_WRAP_RINGS, gap_px=al.BYAKUYA_WRAP_PX_PER_GAP,
           diamond_w=al.BYAKUYA_WRAP_DIAMOND_W, win=0.70, rimw=0.84)
outline, holes = al.byakuya_tsuba_shapes()
PX = al.BYAKUYA_TSUBA_PX_PER_M
for key, flip in (("tsuba_front", False), ("tsuba_back", True)):
    isl = L[key]
    a = cv.view(isl)
    X, Y = planar_grid(isl, PX, flip)
    fill(a, BRZ)
    d_out = poly_sdist(X, Y, outline)
    inside = point_in_poly(X, Y, outline)
    lighter = (X * 0.6 - Y * 0.8) < 0
    hub = (np.abs(X) < 0.009) & (np.abs(Y) < 0.020)
    a[inside & hub] = shade(BRZ, 1.07)
    in_any_hole = np.zeros(X.shape, bool)
    d_hole = np.full(X.shape, 1e9)
    for hl in holes:
        in_any_hole |= point_in_poly(X, Y, hl)
        d_hole = np.minimum(d_hole, poly_sdist(X, Y, hl))
    solid = inside & ~in_any_hole
    ring = solid & (d_hole >= 1.0 / PX) & (d_hole < 2.2 / PX)
    a[ring & lighter] = BRZ_HI
    a[ring & ~lighter] = shade(BRZ, 0.9)
    a[solid & (d_hole < 1.0 / PX)] = BRZ_LO
    rim = inside & (d_out < 1.5 / PX)
    a[rim & lighter] = BRZ_HI
    a[rim & ~lighter] = BRZ_LO
    a[rim & ~lighter & (d_out < 0.7 / PX)] = BRZ_DK
    seat = (np.abs(X) < 0.0055) & (np.abs(Y) < 0.0165) & ((np.abs(X) > 0.0035) | (np.abs(Y) > 0.0145))
    a[seat] = BRZ_LO
    a[~inside & ~point_in_poly(X, Y, [(2 * x, 2 * y) for x, y in outline])] = BRZ_LO
    a[in_any_hole] = BRZ_LO
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
a[2:5, 2:6] = KAS_HI
fitting(L["fuchi_side"], hi_u=0.28)
a = cv.view(L["habaki_side"]); fill(a, BRZ); streaks(a, [(0.20, 0.26, BRZ_HI), (0.74, 0.82, BRZ_LO)]); a[0, :] = BRZ_HI
a = cv.view(L["habaki_top"]); fill(a, BRZ_HI); a[0, :] = BRZ; a[-1, :] = BRZ_LO; a[:, 0] = BRZ; a[:, -1] = BRZ_LO

# ---- blade stub of the ground hilt (steel, flat cut end)
a = cv.view(L["stub_a"]); fill(a, STEEL); a[:, 0] = STEEL_EDGE; a[:, 1] = STEEL_LIT; a[:, 2:5] = STEEL_LIT; a[:, 6] = STEEL_SH
a = cv.view(L["stub_b"]); fill(a, STEEL_SH); a[:, 0] = STEEL_EDGE; a[:, 5:] = STEEL_DK
a = cv.view(L["stub_s"]); fill(a, STEEL_DK); a[:, 1] = STEEL_SH
a = cv.view(L["stub_e"]); fill(a, STEEL_EDGE)
a = cv.view(L["stub_cap"]); fill(a, STEEL_DK); a[1:-1, 1:-1] = STEEL_SH

# ------------------------------------------------------------------------------------------------ ripple: #CFC3F0, alpha gradient across the ring
isl = L["ripple"]
a = cv.view(isl)
h, w = a.shape[:2]
prof = [0, 90, 200, 255, 255, 255, 235, 205, 175, 145, 115, 90, 65, 45, 25, 8]       # row 0 = outer edge, row 15 = inner edge
for r in range(h):
    for c in range(w):
        col = RIPPLE_HI if (3 <= r <= 5 and (c // 4) % 2 == 0) else RIPPLE
        a[r, c] = (col[0], col[1], col[2], prof[r])

# ------------------------------------------------------------------------------------------------ Hakuteiken wings (alpha sheet shared by both wings)
isl = L["wing"]
a = cv.view(isl)
H_, W_ = isl.h, isl.w
PXM, ZMIN = al.WING_PX_PER_M, al.WING_ZMIN
cols = (np.arange(W_) + 0.5 - 2.0) / PXM
rows_z_m = ZMIN + ((H_ - (np.arange(H_) + 0.5)) - 2.0) / PXM
X, Z = np.meshgrid(cols, rows_z_m)
V, F, info = al.wing_geometry(1)
inside = np.zeros(X.shape, bool)
for tri in F:
    p = [V[k] for k in tri]
    (ax, az), (bx, bz), (cx, cz) = (p[0][0], p[0][2]), (p[1][0], p[1][2]), (p[2][0], p[2][2])
    den = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
    if abs(den) < 1e-12:
        continue
    w1 = ((bz - cz) * (X - cx) + (cx - bx) * (Z - cz)) / den
    w2 = ((cz - az) * (X - cx) + (ax - cx) * (Z - cz)) / den
    w3 = 1 - w1 - w2
    tol = 0.35 / PXM / 1.0
    inside |= (w1 >= -1e-3) & (w2 >= -1e-3) & (w3 >= -1e-3)
rr = np.hypot(X, Z)
th = np.degrees(np.arctan2(Z, X))
ang = np.array(info["ang"])
lens = np.array(info["len"])
fi = np.clip(np.round((48.0 - th) / 7.5), 0, 8).astype(int)
dang = th - ang[fi]
dlat = rr * np.sin(np.radians(dang))                   # lateral distance from the feather axis (m)
rho = rr / lens[fi]
hw = rr * math.sin(math.radians(3.75))
phase = (rr - 2.2 * np.abs(dlat)) / 0.30
frac = phase - np.floor(phase)
cut = (rho > 0.50) & (np.abs(dlat) > hw * (0.50 + 0.50 * frac) * 0.96)             # sawtooth barbs along both sides, teeth point to the tip
barb = (rho > 0.18) & (frac < 0.17) & ~cut
spine = np.abs(dlat) < 0.55 / PXM
alpha = np.where(inside & ~cut, 255, 0).astype(np.uint8)
for r in range(H_):
    for c in range(W_):
        if alpha[r, c] == 0:
            a[r, c] = (HK_WHITE[0], HK_WHITE[1], HK_WHITE[2], 0)
        elif spine[r, c]:
            a[r, c] = HK_CORE
        elif barb[r, c]:
            a[r, c] = HK_SHADE
        else:
            a[r, c] = HK_WHITE
e_w = np.where(alpha > 0, 1.0, 0.0)
e_w[barb & (alpha > 0) & ~spine] = 0.82
em[isl.y:isl.y + H_, isl.x:isl.x + W_] = e_w

# ------------------------------------------------------------------------------------------------ halo strip and Hakuteiken blade strips
isl = L["halo"]
a = cv.view(isl)
h, w = a.shape[:2]
hp = [110, 190, 255, 255, 255, 255, 190, 110]
e_h = np.zeros((h, w))
for r in range(h):
    for c in range(w):
        col = HK_CORE if r in (3, 4) else HK_WHITE
        a[r, c] = (col[0], col[1], col[2], hp[r])
        e_h[r, c] = hp[r] / 255.0
em[isl.y:isl.y + h, isl.x:isl.x + w] = e_h


def shinogi_fracs(w=0.10, t=0.03, edge=0.002):
    ry = -w / 2 + 0.55 * w
    pr = [(edge, -w / 2), (t / 2, ry), (t / 2, w / 2), (-t / 2, w / 2), (-t / 2, ry), (-edge, -w / 2)]
    cum = [0.0]
    for k in range(6):
        a_, b_ = pr[k], pr[(k + 1) % 6]
        cum.append(cum[-1] + math.hypot(b_[0] - a_[0], b_[1] - a_[1]))
    return [c / cum[-1] for c in cum]


for key in ("hk_body", "hk_tip"):
    isl = L[key]
    a = cv.view(isl)
    h, w = a.shape[:2]
    fr = shinogi_fracs()
    fill(a, HK_WHITE)
    for c in range(w):
        u = (c + 0.5) / w
        face = max(k for k in range(6) if fr[k] <= u)
        if face == 2 or face == 3:                           # spine faces and the flats beside them: a touch of shade
            a[:, c] = HK_SHADE if face == 2 else HK_WHITE
        # glow core line on the ridge edges
        for ridge_u in (fr[1], fr[4]):
            if abs(u - ridge_u) < 0.5 / w + 1e-9:
                a[:, c] = HK_CORE
    if key == "hk_tip":
        a[:2, :] = HK_CORE
    em[isl.y:isl.y + h, isl.x:isl.x + w] = 1.0

# ------------------------------------------------------------------------------------------------ write
img = cv.dilate(2)
cv.img = img
os.makedirs(OUT, exist_ok=True)
cv.save(os.path.join(OUT, MODEL + "_diffuse.png"))
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
print("painted", MODEL, "emissive texels > 0:", int((em_img > 0).sum()), "wing inside texels:", int((alpha > 0).sum()))
