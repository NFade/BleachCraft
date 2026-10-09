# Paint rukia_bankai_diffuse.png / rukia_bankai_emissive.png (system python, 512 x 512). Palette: ART_BIBLE 1.5 (anime glowing ice).
#   python -I paint_rukia_bankai.py
# Ice parts carry diffuse alpha 200 (blade, crystals, shards), the ice shell 180; hilt, tsuba, habaki, ribbons are opaque.
# Emissive (RGBA, white, A = intensity): blade edge strip + core 60 %, tsuba rim 50 %, ribbon edges 40 %, crystal core 35 % / tips 80 %,
# shell 30 %, shards 35 % / 60 % (bible silent: crystal values). The left 256 px column is reserved for the costume set and stays empty.
import math, os, sys, random
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paint_common import *
import atlas_layouts as al

MODEL = "rukia_bankai"
L = al.RUKIA_BANKAI
N = al.BANKAI_ATLAS
OUT = os.path.join(r"D:\MineBleach\blender\export", MODEL)

HI, MID, CORE, DEEP = rgb("#CFEFFF"), rgb("#9ED3F0"), rgb("#7FB8DF"), rgb("#2E6FA8")
CRY, CRYT, CRYD = rgb("#8EC9EE"), rgb("#D9E8F5"), rgb("#6FAEDA")
TS, TSS, TSH = rgb("#DCE8F5"), rgb("#9DB4CC"), rgb("#EEF4FA")
SILVER, WSHADOW = rgb("#C8D2DC"), rgb("#B8CFE6")
WRAP, WRAP_HI, WRAP_LO = rgb("#DCE8F5"), rgb("#F2F8FD"), rgb("#B8CFE6")
DIAM, DIAM_HI, DIAM_LO = rgb("#7FA5C8"), rgb("#9CBBD8"), rgb("#5F86AB")
RIB, RIBE = rgb("#F0F6FA"), rgb("#8EC5EE")
A_ICE, A_SHELL = 200, 180
EM_BLADE, EM_RIM, EM_RIB, EM_CORE, EM_TIP, EM_SHELL, EM_SHARD_F, EM_SHARD_S = 0.60, 0.50, 0.40, 0.35, 0.80, 0.30, 0.35, 0.60


def alpha(c, a):
    return (c[0], c[1], c[2], a)


def mix(c0, c1, t):
    t = max(0.0, min(1.0, t))
    return (int(c0[0] + (c1[0] - c0[0]) * t), int(c0[1] + (c1[1] - c0[1]) * t), int(c0[2] + (c1[2] - c0[2]) * t), int(c0[3] + (c1[3] - c0[3]) * t))


cv = Canvas(N)
em = np.zeros((N, N))     # emissive intensity 0..1


def put_em(isl, arr):
    em[isl.y:isl.y + isl.h, isl.x:isl.x + isl.w] = arr


# ---- wrap: pale ice-white lacing, blue-grey diamonds (22 rings, 10 diamonds)
a = cv.view(L["wrap"])
paint_wrap(a, WRAP, WRAP_HI, WRAP_LO, DIAM, DIAM_HI, DIAM_LO, rings=al.BANKAI_WRAP_RINGS, gap_px=al.WRAP_PX_PER_GAP,
           diamond_w=al.WRAP_DIAMOND_W, crease=rgb("#C4D6EA"))


# ---- blade strips (u = edge 0 -> spine 1, v = root bottom -> tip top). bevel u < 0.55, core flat u > 0.55
def blade_strip(isl, side):
    a = cv.view(isl)
    h, w = a.shape[:2]
    e = np.zeros((h, w))
    yok = 0.9069                                   # yokote: kissaki starts at 70 mm from the tip
    for r in range(h):
        s = 1.0 - (r + 0.5) / h                    # 0 root .. 1 tip
        for c in range(w):
            u = (c + 0.5) / w
            # fracture facets: slanted bands along the blade, different phase on the two sides
            band = int((r + c * (3.0 if side == "a" else -3.0) + (0 if side == "a" else 17)) // 31) % 3
            k = (1.0, 0.93, 1.06)[band]
            if c == 0:
                col = HI
            elif c == 1:
                col = mix(HI, MID, 0.5)
            elif u < 0.55:
                col = mix(MID, CRY, (u - 0.2) / 0.35)
            elif c == 5 or (u >= 0.5 and u < 0.58):
                col = HI                            # shinogi ridge line
            elif u < 0.9:
                col = shade(CORE, k)
            else:
                col = DEEP
            a[r, c] = alpha(col, A_ICE)
            if c <= 1:
                e[r, c] = EM_BLADE
            elif 0.60 < u < 0.88:
                e[r, c] = EM_BLADE
            elif u >= 0.5 and u < 0.58:
                e[r, c] = EM_BLADE * 0.7
        # thin diagonal fracture lines in the core flat
        if r % 37 == (3 if side == "a" else 21):
            for c in range(6, w - 1):
                rr = min(h - 1, r + (c - 6))
                a[rr, c] = alpha(mix(CORE, HI, 0.6), A_ICE)
    ry = int(round((1.0 - yok) * h))
    a[ry, :] = alpha(HI, A_ICE)
    a[ry + 1, :] = alpha(mix(HI, CORE, 0.4), A_ICE)
    e[ry, :] = EM_BLADE
    # tip: brighter and glowing
    for r in range(0, int(h * 0.05)):
        e[r, :w - 2] = np.maximum(e[r, :w - 2], EM_BLADE)
    put_em(isl, e)


blade_strip(L["blade_a"], "a")
blade_strip(L["blade_b"], "b")
a = cv.view(L["blade_s"]); fill(a, alpha(DEEP, A_ICE)); a[:, 2] = alpha(mix(DEEP, CORE, 0.45), A_ICE)
a = cv.view(L["blade_e"]); fill(a, alpha(HI, A_ICE)); put_em(L["blade_e"], np.full((L["blade_e"].h, L["blade_e"].w), EM_BLADE))

# ---- bar tsuba (planar, 720 px/m): pale ice-silver, outline highlight, slot windows (holes, never visible)
outline, holes = al.bankai_tsuba_shapes()
for key, flip in (("tsuba_front", False), ("tsuba_back", True)):
    isl = L[key]
    a = cv.view(isl)
    X, Y = planar_grid(isl, al.BANKAI_TSUBA_PX_PER_M, flip)
    ins = point_in_poly(X, Y, outline)
    d_out = poly_sdist(X, Y, outline)
    fill(a, TS)
    a[ins & (d_out < 0.004)] = shade(TSH, 1.0)                                  # 4 mm rims a little brighter
    a[ins & (d_out < 0.004) & (d_out > 0.004 - 1.0 / al.BANKAI_TSUBA_PX_PER_M)] = TSS   # rim / bridge boundary hint
    inside_h = np.zeros(X.shape, bool)
    d_h = np.full(X.shape, 1e9)
    for h in holes:
        inside_h |= point_in_poly(X, Y, h)
        d_h = np.minimum(d_h, poly_sdist(X, Y, h))
    a[~inside_h & (d_h < 0.9 / al.BANKAI_TSUBA_PX_PER_M)] = TSS                 # slot edge line
    a[inside_h] = TSS
    # bridge pip: small raised diamond on the centre bridge (paint only)
    a[(np.abs(X) / 0.006 + np.abs(Y) / 0.012) < 1.0] = WSHADOW
    a[(np.abs(X) / 0.0035 + np.abs(Y) / 0.0075) < 1.0] = TSH
    a[ins & (d_out < 1.1 / al.BANKAI_TSUBA_PX_PER_M)] = HI                      # outer rim highlight
    a[~ins] = TSS
    e = np.zeros(X.shape)
    e[ins & (d_out < 1.1 / al.BANKAI_TSUBA_PX_PER_M)] = EM_RIM
    put_em(isl, e)

a = cv.view(L["tsuba_rim"]); fill(a, TS); a[:3, :] = HI; a[3:6, :] = TSH; a[6:, :] = TSS
put_em(L["tsuba_rim"], np.full((L["tsuba_rim"].h, L["tsuba_rim"].w), EM_RIM))
a = cv.view(L["tsuba_win"]); fill(a, TSS); a[0, :] = rgb("#8AA3BE")


def fitting(isl, base, hi_u=0.30):
    a = cv.view(isl)
    fill(a, base)
    h, w = a.shape[:2]
    streaks(a, [(hi_u, hi_u + max(0.04, 1.5 / w), HI), (0.72, 0.72 + max(0.05, 2.0 / w), shade(base, 0.86))])
    a[-1:, :] = shade(base, 0.86)
    a[0, :] = HI
    return a


fitting(L["kashira_side"], TS)
a = cv.view(L["kashira_cap"]); fill(a, TSS); a[1:-1, 1:-1] = TS
fitting(L["fuchi_side"], TS, 0.28)
a = cv.view(L["habaki_side"]); fill(a, SILVER); streaks(a, [(0.20, 0.26, HI), (0.74, 0.82, shade(SILVER, 0.82))]); a[0, :] = HI
a = cv.view(L["habaki_top"]); fill(a, HI)

# ---- ribbon seg: 104 x 20 (length x width), edge rows tinted and glowing
a = cv.view(L["ribbon_seg"])
h, w = a.shape[:2]
fill(a, RIB)
a[0:2, :] = RIBE
a[h - 2:h, :] = RIBE
a[2, :] = shade(RIB, 1.01)
a[h // 2 - 1:h // 2 + 1, :] = rgb("#FFFFFF")
for c in range(w):
    if c % 21 == 7:                                # faint cross-fold marks
        a[4:h - 4, c] = rgb("#E2EDF6")
e = np.zeros((h, w))
e[0:2, :] = EM_RIB
e[h - 2:h, :] = EM_RIB
put_em(L["ribbon_seg"], e)

# ---- ribbon tip: triangle (70 mm at the hinge to a point at 0.35 m), same colours, painted edge lines on the slanted sides
isl = L["ribbon_tip"]
a = cv.view(isl)
h, w = a.shape[:2]
e = np.zeros((h, w))
for r in range(h):
    fy = 1.0 - (r + 0.5) / h
    for c in range(w):
        fx = (c + 0.5) / w
        d = min(fy, 1.0 - fy) * 2.0 - fx * 0.0           # 0 on the side lines at fx = 0 ... edge lines run (0,0)->(1,.5) and (0,1)->(1,.5)
        yb = 0.5 * fx                                    # lower line fy = 0.5 fx, upper line fy = 1 - 0.5 fx
        inside = fy >= yb and fy <= 1.0 - yb
        dist = min(fy - yb, (1.0 - yb) - fy) * h         # px to the nearest slanted line (vertical distance)
        if not inside or dist < 1.6:
            a[r, c] = RIBE
            e[r, c] = EM_RIB
        elif abs(fy - 0.5) < 0.5 / h * 1.2:
            a[r, c] = rgb("#FFFFFF")
        else:
            a[r, c] = RIB
put_em(isl, e)


# ---- crystals
def crystal(key):
    isl = L["crystal_" + key]
    a = cv.view(isl)
    h, w = a.shape[:2]
    fr, ks = al.CRYSTAL_RINGS[key]
    ftop = fr[-1]
    rng = random.Random(100 + al.CRYSTAL_SEED[key])
    kf = [rng.uniform(0.80, 1.06) for _ in range(6)]
    e = np.zeros((h, w))
    for r in range(h):
        vv = 1.0 - (r + 0.5) / h
        for c in range(w):
            uu = (c + 0.5) / w * 6.0
            k = min(5, int(uu))
            fx = uu - k
            if vv >= ftop:                                     # pointed tip faces
                t = (vv - ftop) / (1.0 - ftop)
                col = shade(mix(mix(CRYT, CRY, 0.40), HI, 0.30 * t), kf[k] * (0.94 if (r + c) % 11 < 3 else 1.0))
                if fx < 0.12 or fx > 0.88:
                    col = shade(CRYT, 0.9)
                e[r, c] = EM_TIP
            else:
                t = vv / ftop
                col = mix(CRY, CRYT, 0.15 + 0.55 * t * t)
                col = shade(col, kf[k])
                # facet shading across each face: bright left edge, dark right edge, glowing core streak in the middle
                if fx < 0.14:
                    col = mix(col, HI, 0.55)
                elif fx > 0.86:
                    col = mix(col, CRYD, 0.65)
                elif 0.30 < fx < 0.70 and 0.06 < vv < ftop * 0.85:
                    col = mix(col, CORE, 0.45)
                    e[r, c] = EM_CORE
                # slanted fracture bands
                if (r + c * 2 + k * 5) % max(9, h // 5) == 0:
                    col = mix(col, HI, 0.35)
            a[r, c] = alpha(col, A_ICE)
    put_em(isl, e)


for k in "abcd":
    crystal(k)

# ---- shards (triangle face cell + 3 side strips)
for key in "ab":
    isl = L["shard_%s_face" % key]
    a = cv.view(isl)
    h, w = a.shape[:2]
    fill(a, alpha(MID, A_ICE))
    t = al.bankai_shard_tri(key)
    yc = (min(p[0] for p in t) + max(p[0] for p in t)) / 2.0
    zc = (min(p[1] for p in t) + max(p[1] for p in t)) / 2.0
    pts = [(0.5 * w + (y - yc) * al.BANKAI_SHARD_PX_PER_M, 0.5 * h - (z - zc) * al.BANKAI_SHARD_PX_PER_M) for y, z in t]
    gx = (np.arange(w) + 0.5)[None, :] * np.ones((h, 1))
    gy = (np.arange(h) + 0.5)[:, None] * np.ones((1, w))
    inside = point_in_poly(gx, gy, pts)
    d = poly_sdist(gx, gy, pts)
    a[inside] = alpha(CORE, A_ICE)
    a[inside & (gx < pts[0][0] + 0.0)] = alpha(MID, A_ICE)
    a[inside & (d < 1.2)] = alpha(HI, A_ICE)
    e = np.zeros((h, w))
    e[inside] = EM_SHARD_F
    e[inside & (d < 1.2)] = EM_SHARD_S
    put_em(isl, e)
    isl = L["shard_%s_side" % key]
    a = cv.view(isl)
    h, w = a.shape[:2]
    for c in range(w):
        a[:, c] = alpha((HI, MID, CORE)[c // 2], A_ICE)
    put_em(isl, np.full((h, w), EM_SHARD_S))

# ---- ice shell: 10 columns x 4 rows of facet patches (12 x 18 px), alpha 180, emissive 30 %
isl = L["shell_body"]
a = cv.view(isl)
rng = random.Random(21)
for i in range(al.SHELL_ROWS):
    for k in range(al.SHELL_COLS):
        x0, x1 = k * 12, (k + 1) * 12
        y1 = isl.h - i * 18                       # row 0 = bottom of the shell = bottom of the island
        y0 = y1 - 18
        base = mix(CRY, CRYT, 0.10 + 0.22 * i / 3.0 + rng.uniform(-0.08, 0.10))
        base = shade(base, rng.uniform(0.84, 1.08))
        for r in range(y0, y1):
            for c in range(x0, x1):
                dx, dy = (c - x0) / 11.0, (r - y0) / 17.0
                col = base
                if (dx + dy * 0.7) < 0.30:
                    col = mix(base, HI, 0.45)          # lit corner
                elif dx > 0.75 and dy > 0.5:
                    col = mix(base, CRYD, 0.45)        # shadowed corner
                if c in (x0, x1 - 1) or r in (y0, y1 - 1):
                    col = mix(col, CRYT, 0.5)          # crack line between facets
                a[r, c] = alpha(col, A_SHELL)
put_em(isl, np.full((isl.h, isl.w), EM_SHELL))
for key in ("shell_top", "shell_bot"):
    isl = L[key]
    a = cv.view(isl)
    h, w = a.shape[:2]
    rng2 = random.Random(5 if key == "shell_top" else 6)
    wedge = [rng2.uniform(0.92, 1.08) for _ in range(10)]
    for r in range(h):
        for c in range(w):
            ang = math.atan2(h / 2.0 - (r + 0.5), (c + 0.5) - w / 2.0) % (2 * math.pi)
            wi = int(ang / (2 * math.pi) * 10) % 10
            rad = math.hypot(c + 0.5 - w / 2.0, r + 0.5 - h / 2.0) / (w / 2.0)
            col = shade(mix(CRYT, CRY, 0.35 + 0.4 * min(1.0, rad)), wedge[wi])
            a[r, c] = alpha(col, A_SHELL)
    put_em(isl, np.full((h, w), EM_SHELL))

cv.dilate(2)
cv.save(os.path.join(OUT, MODEL + "_diffuse.png"))
# emissive RGBA (white, A = intensity); gutters take the neighbouring island intensity
em_img = em.copy()
isl_mask = np.zeros((N, N), bool)
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
print("painted", MODEL, "emissive texels > 0:", int((em_img > 0).sum()))
