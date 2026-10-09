# Paint rukia_shikai_diffuse.png / rukia_shikai_emissive.png (system python). Palette: ART_BIBLE 1.2 (anime cool white) + Gate B.
#   python paint_rukia_shikai.py
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from paint_common import *
import atlas_layouts as al

MODEL = "rukia_shikai"
L = al.RUKIA_SHIKAI
OUT = os.path.join(r"D:\MineBleach\blender\export", MODEL)

WHITE, BASE, SHADE, SHADE2, HI = rgb("#FFFFFF"), rgb("#EAF2FB"), rgb("#C9D6EA"), rgb("#B3C3DC"), rgb("#F8FBFF")
WRAP, WRAP_GAP = rgb("#F2F4FA"), rgb("#C9D6EA")
RIB, RIB_EDGE = rgb("#F4F8FF"), rgb("#B9D4F0")
EMIT_BLADE, EMIT_TSUBA, EMIT_RIM, EMIT_RIB = 0.60, 0.55, 0.40, 0.45     # Gate C C1 (was 0.35 / 0.35 / 0.20 / 0.25)
EMIT_BLADE_BODY, EMIT_SPINE, EMIT_RIB_BODY = 0.18, 0.15, 0.15           # Gate C C1: faint flat body glow

cv = Canvas()
em = np.zeros((al.ATLAS, al.ATLAS))     # emissive intensity 0..1


def put_em(isl, arr):
    em[isl.y:isl.y + isl.h, isl.x:isl.x + isl.w] = arr


# ---- wrap: white lacing, diamond gaps shaded #C9D6EA (ridges kept so the pattern reads white on white)
a = cv.view(L["wrap"])
paint_wrap(a, WRAP, WHITE, rgb("#DCE4F2"), WRAP_GAP, rgb("#DDE6F4"), SHADE2, crease=rgb("#E3EAF5"))


# ---- blade strips (u = edge 0 -> spine 1, v = root bottom -> tip top)
def blade_strip(isl, side):
    a = cv.view(isl)
    h, w = a.shape[:2]
    base, lit, shd = (BASE, WHITE, SHADE) if side == "a" else (shade(BASE, 0.97), HI, shade(SHADE, 0.95))
    ridge_u = 0.55
    e = np.zeros((h, w))
    yok = 0.9069
    for r in range(h):
        s = 1.0 - (r + 0.5) / h
        hamon = 0.20 + 0.03 * math.sin(r * 0.30) + 0.02 * math.sin(r * 0.83 + 1.0)
        hamon *= 1.0 - 0.6 * max(0.0, (s - yok) / (1 - yok))
        for c in range(w):
            u = (c + 0.5) / w
            a[r, c] = lit if u < hamon else (base if u < ridge_u else shd)
        a[r, min(w - 1, int(hamon * w))] = HI
        a[r, 0] = WHITE
        a[r, min(w - 1, int(ridge_u * w))] = WHITE if side == "a" else HI
        a[r, w - 1] = SHADE2
        e[r, :] = EMIT_BLADE_BODY                       # Gate C C1: whole flat faint body glow
        e[r, 0] = e[r, 1] = EMIT_BLADE                  # emissive blade edge line, 2 px
    ry = int(round((1.0 - yok) * h))
    a[ry, :] = WHITE
    a[ry + 1, :] = HI
    put_em(isl, e)


blade_strip(L["blade_a"], "a")
blade_strip(L["blade_b"], "b")
a = cv.view(L["blade_s"]); fill(a, SHADE); a[:, 1] = BASE
put_em(L["blade_s"], np.full((L["blade_s"].h, L["blade_s"].w), EMIT_SPINE))
a = cv.view(L["blade_e"]); fill(a, WHITE); put_em(L["blade_e"], np.full((L["blade_e"].h, L["blade_e"].w), EMIT_BLADE))

# ---- snowflake tsuba (planar, 636 px/m): spokes glow, rim a little
R_OUT, R_RIM, R_HUB = 0.044, 0.037, 0.018
h_sp = lambda r: 0.0035 + (r - R_HUB) / (R_RIM - R_HUB) * 0.0025
outline, wins = al.snowflake_shapes()
for key, flip in (("tsuba_front", False), ("tsuba_back", True)):
    isl = L[key]
    a = cv.view(isl)
    X, Y = planar_grid(isl, al.SHIKAI_TSUBA_PX_PER_M, flip)
    R = np.hypot(X, Y)
    fill(a, BASE)
    spoke = np.zeros(X.shape, bool)
    core = np.zeros(X.shape, bool)
    edge = np.zeros(X.shape, bool)
    for k in range(6):
        t = math.radians(60 * k)
        ax, ay = math.cos(t), math.sin(t)
        al_ = X * ax + Y * ay
        pp = -X * ay + Y * ax
        inr = (al_ > R_HUB - 0.003) & (al_ < R_RIM + 0.003)
        hh = h_sp(np.clip(al_, R_HUB, R_RIM))
        spoke |= inr & (np.abs(pp) < hh)
        core |= inr & (np.abs(pp) < hh * 0.35)
        edge |= inr & (np.abs(pp) < hh) & (np.abs(pp) > hh - 1.3 / al.SHIKAI_TSUBA_PX_PER_M)
    inside_win = np.zeros(X.shape, bool)
    for w in wins:
        inside_win |= point_in_poly(X, Y, w)
    rimzone = (R >= R_RIM) & (R <= R_OUT + 0.001)
    hub = R <= R_HUB
    a[hub] = BASE
    a[(R > R_HUB - 1.4 / al.SHIKAI_TSUBA_PX_PER_M) & (R <= R_HUB)] = SHADE         # hub ring line
    a[spoke] = BASE
    a[core] = WHITE
    a[edge] = SHADE
    a[rimzone] = BASE
    a[rimzone & (R < R_RIM + 1.4 / al.SHIKAI_TSUBA_PX_PER_M)] = SHADE              # inner rim line
    a[rimzone & (R > R_OUT - 2.2 / al.SHIKAI_TSUBA_PX_PER_M)] = WHITE              # outer highlight band
    a[rimzone & (R > R_OUT - 0.8 / al.SHIKAI_TSUBA_PX_PER_M)] = SHADE
    # six small snowflake ticks on the hub (blade hole seat 32 x 10 mm is covered by the habaki)
    for k in range(6):
        t = math.radians(60 * k + 30)
        ax, ay = math.cos(t), math.sin(t)
        al_ = X * ax + Y * ay
        pp = -X * ay + Y * ax
        a[(al_ > 0.009) & (al_ < R_HUB - 0.003) & (np.abs(pp) < 0.45 / al.SHIKAI_TSUBA_PX_PER_M)] = SHADE
    a[inside_win] = SHADE                                                           # window area (a hole; never visible)
    e = np.zeros(X.shape)
    e[spoke & ~inside_win] = EMIT_TSUBA
    e[core] = EMIT_TSUBA
    e[rimzone] = EMIT_RIM
    put_em(isl, e)

a = cv.view(L["tsuba_rim"]); fill(a, BASE); a[:3, :] = WHITE; a[6:, :] = SHADE
put_em(L["tsuba_rim"], np.where(np.arange(L["tsuba_rim"].h)[:, None] < 8, EMIT_RIM, 0) * np.ones((L["tsuba_rim"].h, L["tsuba_rim"].w)))
a = cv.view(L["tsuba_win"]); fill(a, SHADE); a[0, :] = SHADE2


def fitting(isl, hi_u=0.30, e_val=0.0):
    a = cv.view(isl)
    fill(a, BASE)
    h, w = a.shape[:2]
    streaks(a, [(hi_u, hi_u + max(0.04, 1.5 / w), WHITE), (0.72, 0.72 + max(0.05, 2.0 / w), SHADE)])
    a[-1:, :] = SHADE
    a[0, :] = WHITE
    return a


fitting(L["kashira_side"])
a = cv.view(L["kashira_cap"]); fill(a, SHADE); a[1:-1, 1:-1] = BASE
fitting(L["fuchi_side"], 0.28)
a = cv.view(L["habaki_side"]); fill(a, BASE); streaks(a, [(0.20, 0.26, WHITE), (0.74, 0.82, SHADE)]); a[0, :] = WHITE
a = cv.view(L["habaki_top"]); fill(a, WHITE)
# knot block: ribbon material (white with the ribbon edge tint at the bottom rows)
a = cv.view(L["knot_side"]); fill(a, RIB); a[-2:, :] = RIB_EDGE; a[0, :] = WHITE
a = cv.view(L["knot_bottom"]); fill(a, RIB_EDGE); a[1:-1, 1:-1] = RIB

# ---- ribbon strip 250 x 8 (10 cells of 25 x 8); edge rows tinted and glowing; segment 10 has the swallow tail cut-out
for n in range(1, 11):
    isl = al.ribbon_cell(n)
    a = cv.view(isl)
    t = (n - 1) / 9.0
    body = shade(RIB, 1.0 - 0.035 * t)
    body = (body[0], body[1], min(255, int(body[2] + 2 * t)), 255)
    fill(a, body)
    a[0, :] = RIB_EDGE
    a[-1, :] = RIB_EDGE
    a[1, :] = shade(body, 1.01)
    a[3, :] = WHITE
    for c in range(isl.w):
        if (c + 12 * (n - 1)) % 19 == 0:                    # faint cross-fold marks along the ribbon
            a[2:6, c] = shade(body, 0.975)
    e = np.full((isl.h, isl.w), EMIT_RIB_BODY)
    e[0, :] = EMIT_RIB
    e[-1, :] = EMIT_RIB
    put_em(isl, e)
    if n == 10:
        for r in range(isl.h):
            depth = 5.0 * (1.0 - abs(r - 3.5) / 3.5)        # V notch 5 px = 50 mm deep, apex on the centre line
            for c in range(isl.w):
                if c >= isl.w - depth:
                    a[r, c, 3] = 0
                    em[isl.y + r, isl.x + c] = 0.0

cv.dilate(2)
# dilation copies the alpha 0 of the notch into the gutter only where it borders the notch; keep the notch transparent
cv.save(os.path.join(OUT, MODEL + "_diffuse.png"))
# emissive RGBA (white, A = intensity); gutters take the neighbouring island intensity
em_img = em.copy()
m = cv.mask.copy()
isl_mask = np.zeros_like(m)
for i in list(L.values()) + [al.ribbon_cell(n) for n in range(1, 11)]:
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
