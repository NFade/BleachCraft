"""Pillow GUI icons for the Byakuya item (sealed, shikai, bankai) and the Rukia bankai icon, no Blender needed.

Output: mod/src/main/resources/assets/reiatsu_test/textures/item/senbonzakura_{sealed,shikai,bankai}_icon.png and
sode_no_shirayuki_bankai_icon.png (32x32 RGBA). Same drawing helpers as tools/rukia_icons.py (upright sword at 8x,
outline, rotation by 45 degrees, premultiplied box filter, binary alpha). The colours are the palette values of the
exported atlases as recorded in LOG.md (phase 3 sections of byakuya_sealed, byakuya_shikai, byakuya_bankai, rukia_bankai).

Blender-rendered icons (ADR section 1) remain a follow-up; keep the file names.
usage: python -I tools/byakuya_icons.py [preview.png]
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rukia_icons as ri  # noqa: E402

OUT = ri.OUT
H = lambda s: tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))  # noqa: E731

BY = {
    "wrap": H("#BCB2D3"), "wrap_hi": H("#DAD3EA"), "wrap_lo": H("#8F84B0"), "cream": H("#E8DDB5"),
    "bronze": H("#A58D5F"), "bronze_hi": H("#CBB584"), "bronze_lo": H("#7A6743"), "kashira": H("#E9E2C0"),
    "saya": H("#2E2840"), "saya_lite": H("#554A73"), "steel": H("#C9CED6"), "steel_edge": H("#F0F4FA"),
    "petal": H("#E5A4DC"), "petal_hi": H("#F9C8F6"), "petal_edge": H("#FFE9FB"), "petal_lo": H("#C98AC1"),
    "glow": H("#F2E9FF"), "outline": (38, 30, 56),
}
RB = {
    "edge": H("#CFEFFF"), "core": H("#7FB8DF"), "deep": H("#2E6FA8"), "wrap": H("#DCE8F5"), "diamond": H("#7FA5C8"),
    "tsuba": H("#DCE8F5"), "tsuba_lo": H("#9DB4CC"), "ribbon": H("#F0F6FA"), "ribbon_edge": H("#8EC5EE"),
    "crystal": H("#8EC9EE"), "outline": (36, 78, 128),
}


def hilt(cv, p, d0, kashira_col, wrap_col, wrap_lo, diamond_col, tsuba_w=9.0, tsuba_h=2.4, guard="window"):
    """kashira, wrap with diamonds and a wide guard; returns the d where the blade/saya would start."""
    cv.rect(d0, d0 + 1.6, 3.4, kashira_col)
    cv.rect(d0 + 1.6, d0 + 10.2, 3.4, wrap_col)
    cv.rect(d0 + 1.6, d0 + 10.2, 0.8, wrap_lo, -1.3)
    for d in (3.7, 6.0, 8.3):
        cv.diamond(d0 + d, 1.1, diamond_col)
    return d0 + 10.2


def window_guard(cv, d, w, h, body, lo, hi):
    """Byakuya open window guard: wide plate with two window gaps (drawn as the dark outline colour)."""
    cv.rect(d, d + h, w, lo)
    cv.rect(d + 0.35, d + h - 0.35, w - 1.2, body)
    for sx in (-1, 1):
        cv.rect(d + 0.6, d + h - 0.6, w * 0.22, lo, sx * w * 0.24)
    cv.rect(d + 0.5, d + h - 0.5, 1.0, hi, 0)


def leaf(cv, cx, cd, ang, length, width, body, edge):
    """Crescent petal: a pointed leaf centred at (cx, cd) in canvas units, rotated by ang degrees."""
    pts = []
    n = 6
    for i in range(n + 1):
        t = i / n
        pts.append((t * length, math.sin(t * math.pi) * width / 2))
    for i in range(n, -1, -1):
        t = i / n
        pts.append((t * length, -math.sin(t * math.pi) * width * 0.15))
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    out = []
    for (u, v) in pts:
        u -= length / 2
        out.append((cx + u * ca - v * sa, cd + u * sa + v * ca))
    cv.poly(out, edge)
    inner = [(cx + (u - length / 2) * 0.8 * ca - v * 0.7 * sa, cd + (u - length / 2) * 0.8 * sa + v * 0.7 * ca) for u, v in pts]
    cv.poly(inner, body)


def icon_byakuya_sealed():
    p = BY
    cv = ri.Canvas(38.0)
    end = hilt(cv, p, 0.0, p["kashira"], p["wrap"], p["wrap_lo"], p["cream"])
    window_guard(cv, end + 0.2, 8.4, 2.4, p["bronze"], p["bronze_lo"], p["bronze_hi"])
    cv.rect(end + 2.6, end + 3.8, 4.9, p["bronze"])                    # koiguchi
    cv.rect(end + 2.6, 38.0, 4.2, p["saya"])
    cv.rect(end + 2.6, 38.0, 0.9, p["saya"], -1.6)
    cv.rect(end + 2.6, 37.0, 0.7, p["saya_lite"], 1.0)
    cv.rect(35.8, 38.0, 4.4, p["bronze"])                              # kojiri
    return ri.finish(cv, p["outline"])


def icon_byakuya_shikai():
    """Blade-less hilt with a swirl of petals around it."""
    p = BY
    cv = ri.Canvas(38.0)
    cv.rect(2.0, 3.6, 3.4, p["kashira"])
    cv.rect(3.6, 12.2, 3.4, p["wrap"])
    cv.rect(3.6, 12.2, 0.8, p["wrap_lo"], -1.3)
    for d in (5.7, 8.0, 10.3):
        cv.diamond(d, 1.1, p["cream"])
    window_guard(cv, 12.4, 9.6, 3.0, p["bronze"], p["bronze_lo"], p["bronze_hi"])
    cv.rect(15.4, 17.0, 3.2, p["steel"])                               # habaki/tang stub
    # petals spiralling up from the guard
    pts = [(-5.5, 21.5, 60), (5.0, 23.0, 110), (-2.5, 26.5, 30), (5.5, 29.0, 75), (-4.0, 31.5, 100), (1.5, 33.0, 20)]
    for x, d, ang in pts:
        leaf(cv, x, d, ang, 6.0, 3.0, p["petal"], p["petal_edge"])
    return ri.finish(cv, p["outline"])


def icon_byakuya_bankai():
    """Hilt planted in the ground (stub of blade) with a ring of petals / halo around it."""
    p = BY
    cv = ri.Canvas(38.0)
    # ground line
    cv.rect(0.0, 1.2, 14.0, p["bronze_lo"])
    # hilt upright, stub below the grip (planted), pommel up
    cv.rect(1.2, 8.0, 2.6, p["steel"])
    window_guard(cv, 8.0, 9.6, 3.0, p["bronze"], p["bronze_lo"], p["bronze_hi"])
    cv.rect(11.0, 19.6, 3.4, p["wrap"])
    cv.rect(11.0, 19.6, 0.8, p["wrap_lo"], -1.3)
    for d in (13.0, 15.3, 17.6):
        cv.diamond(d, 1.1, p["cream"])
    cv.rect(19.6, 21.2, 3.4, p["kashira"])
    # eight petals circling the hilt top (the swarm of the bankai), tangent orientation
    cx, cd, r = 0.0, 16.0, 12.0
    for i in range(8):
        a = math.radians(i * 45 + 20)
        leaf(cv, cx + math.cos(a) * r, cd + math.sin(a) * r, math.degrees(a) + 90, 5.5, 2.8, p["petal"], p["petal_edge"])
    return ri.finish(cv, p["outline"])


def icon_rukia_bankai():
    p = RB
    cv = ri.Canvas(40.0, 40.0 / 46.0)
    n = 12
    left, right = [], []
    for i in range(n + 1):                                             # ribbon from the kashira
        d = 7.0 - 7.0 * i / n
        w = 1.9 - 0.3 * i / n
        off = 1.4 * math.sin(i * 0.9 + 0.6)
        left.append((off - w, d))
        right.append((off + w, d))
    cv.poly(left + right[::-1], p["ribbon_edge"])
    core_l = [((l[0] + r[0]) / 2 - (r[0] - l[0]) * 0.28, l[1]) for l, r in zip(left, right)]
    core_r = [((l[0] + r[0]) / 2 + (r[0] - l[0]) * 0.28, l[1]) for l, r in zip(left, right)]
    cv.poly(core_l + core_r[::-1], p["ribbon"])
    cv.rect(7.0, 8.4, 3.2, p["tsuba_lo"])
    cv.rect(8.4, 17.6, 3.2, p["wrap"])
    cv.rect(8.4, 17.6, 0.7, p["diamond"], -1.2)
    for d in (10.4, 12.5, 14.6, 16.5):
        cv.diamond(d, 0.9, p["diamond"])
    cv.rect(18.0, 20.6, 10.0, p["tsuba_lo"])                           # slotted bar guard
    cv.rect(18.4, 20.2, 9.2, p["tsuba"])
    for sx in (-1, 1):
        cv.rect(18.6, 20.0, 2.0, p["deep"], sx * 2.6)
    cv.rect(20.6, 21.8, 3.4, p["tsuba_lo"])
    # ice blade: pale body, blue core stripe, deep edge, pointed tip
    cv.poly([(-1.7, 21.8), (1.7, 21.8), (1.7, 43.0), (0.0, 46.0), (-1.7, 43.0)], p["edge"])
    cv.poly([(-0.6, 21.8), (0.5, 21.8), (0.5, 44.0), (0.0, 46.0), (-0.6, 43.0)], p["core"])
    cv.poly([(0.9, 21.8), (1.7, 21.8), (1.7, 43.0), (0.0, 46.0), (0.9, 44.2)], p["deep"])
    img = ri.finish(cv, p["outline"])
    d = ImageDraw.Draw(img)
    for x, y in ((5, 6), (26, 26), (4, 17), (27, 12)):                 # ice crystal sparkles
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if img.getpixel((x + dx, y + dy))[3] == 0:
                d.point((x + dx, y + dy), fill=p["crystal"] + (255,))
    return img


ICONS = {
    "senbonzakura_sealed_icon": icon_byakuya_sealed,
    "senbonzakura_shikai_icon": icon_byakuya_shikai,
    "senbonzakura_bankai_icon": icon_byakuya_bankai,
    "sode_no_shirayuki_bankai_icon": icon_rukia_bankai,
}

if __name__ == "__main__":
    made = {}
    for name, fn in ICONS.items():
        im = fn()
        im.save(OUT / (name + ".png"))
        made[name] = im
        print("wrote", name)
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (268 * len(made), 256), (90, 90, 96, 255))
        for i, im in enumerate(made.values()):
            sheet.alpha_composite(im.resize((256, 256), Image.NEAREST), (i * 268, 0))
        sheet.save(sys.argv[1])
        print("preview", sys.argv[1])
