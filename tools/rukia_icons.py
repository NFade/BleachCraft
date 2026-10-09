"""Pillow GUI icons for Rukia's sealed and shikai states (step B), no Blender needed.

Output: mod/src/main/resources/assets/reiatsu_test/textures/item/sode_no_shirayuki_{sealed,shikai}_icon.png (32x32 RGBA).
Colours are sampled from the exported diffuse atlases (blender/export/rukia_*/rukia_*_diffuse.png). The pictogram is a
sword drawn upright at 8x, outlined, rotated 45 degrees (tip to the upper right) and reduced to 32x32 with a
premultiplied box filter and a binary alpha (crisp pixel-art edges).

Blender-rendered icons (ADR section 1: orthographic render of the finished model) remain a follow-up; keep the file names.
usage: python -I tools/rukia_icons.py [preview.png]
"""
import math
import sys
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "mod/src/main/resources/assets/reiatsu_test/textures/item"
SS = 8              # supersampling
CANVAS = 56         # units; the icon is the central 32 x 32 after rotation
CX = CANVAS / 2


def dominant(model, box, rank=0):
    """rank-th most common opaque colour of an atlas region (x0, y0, x1, y1)."""
    im = Image.open(ROOT / f"blender/export/{model}/{model}_diffuse.png").convert("RGBA").crop(box)
    c = Counter(p[:3] for p in im.getdata() if p[3] > 200)
    return c.most_common(rank + 1)[rank][0]


def pal_sealed():
    wrap = (0, 0, 62, 155)
    saya = (97, 0, 131, 215)
    tsuba = (132, 0, 212, 46)
    return {
        "red": dominant("rukia_sealed", wrap, 0),
        "red_dark": dominant("rukia_sealed", wrap, 1),
        "cream": dominant("rukia_sealed", wrap, 2),
        "saya": dominant("rukia_sealed", saya, 0),
        "saya_dark": dominant("rukia_sealed", saya, 1),
        "saya_lite": dominant("rukia_sealed", saya, 3),
        "gold": dominant("rukia_sealed", tsuba, 0),
        "gold_dark": dominant("rukia_sealed", tsuba, 1),
        "gold_lite": dominant("rukia_sealed", tsuba, 2),
        "outline": (21, 17, 26),
    }


def pal_shikai():
    wrap = (0, 0, 62, 155)
    blade = (66, 0, 92, 150)
    tsuba = (96, 0, 220, 60)
    return {
        "wrap": dominant("rukia_shikai", wrap, 0),
        "wrap_shade": dominant("rukia_shikai", wrap, 2),
        "blade_shade": dominant("rukia_shikai", blade, 0),
        "blade_mid": dominant("rukia_shikai", blade, 1),
        "blade_lite": dominant("rukia_shikai", blade, 4),
        "tsuba": dominant("rukia_shikai", tsuba, 1),
        "tsuba_shade": dominant("rukia_shikai", tsuba, 0),
        "ribbon_edge": dominant("rukia_shikai", (0, 236, 255, 255), 0),
        "ribbon": (243, 247, 254),
        "glow": (0xBF, 0xE4, 0xFF),   # runtime emissive tint of the manifest
        "outline": (74, 92, 128),
    }


class Canvas:
    """Upright drawing space in icon units: x across (axis at CX), d along the sword from the kashira upward."""

    def __init__(self, length, scale=1.0):
        self.length = length  # drawn length in units after scaling d by `scale`
        self.scale = scale
        n = CANVAS * SS
        self.img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        self.dr = ImageDraw.Draw(self.img)

    def X(self, x):
        return (CX + x) * SS

    def Y(self, d):
        return (CANVAS / 2 + self.length / 2 - d * self.scale) * SS

    def rect(self, d0, d1, w, col, x0=0.0):
        self.dr.rectangle([self.X(x0 - w / 2), self.Y(d1), self.X(x0 + w / 2), self.Y(d0)], fill=tuple(col) + (255,))

    def poly(self, pts, col):
        self.dr.polygon([(self.X(x), self.Y(d)) for x, d in pts], fill=tuple(col) + (255,))

    def ellipse(self, d, rx, rd, col):
        self.dr.ellipse([self.X(-rx), self.Y(d + rd), self.X(rx), self.Y(d - rd)], fill=tuple(col) + (255,))

    def diamond(self, d, r, col):
        self.poly([(-r, d), (0, d + r), (r, d), (0, d - r)], col)


def finish(cv, outline):
    """outline, rotate, reduce to 32x32 (premultiplied box filter, binary alpha)."""
    base = cv.img
    alpha = base.getchannel("A")
    halo = alpha.filter(ImageFilter.MaxFilter((SS | 1) * 2 + 1))
    out = Image.new("RGBA", base.size, tuple(outline) + (0,))
    out.putalpha(halo)
    out.alpha_composite(base)
    out = out.rotate(-45, resample=Image.BICUBIC, center=(CANVAS * SS / 2, CANVAS * SS / 2))
    lo = (CANVAS - 32) // 2 * SS
    out = out.crop((lo, lo, lo + 32 * SS, lo + 32 * SS))
    px = out.load()
    res = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    rp = res.load()
    for y in range(32):
        for x in range(32):
            r = g = b = a = 0.0
            for j in range(SS):
                for i in range(SS):
                    p = px[x * SS + i, y * SS + j]
                    r += p[0] * p[3]
                    g += p[1] * p[3]
                    b += p[2] * p[3]
                    a += p[3]
            if a / (SS * SS * 255.0) >= 0.5:
                rp[x, y] = (round(r / a), round(g / a), round(b / a), 255)
    return res


def icon_sealed():
    p = pal_sealed()
    cv = Canvas(38.0)
    cv.rect(0, 1.6, 3.4, p["gold"])                                   # kashira
    cv.rect(1.6, 10.2, 3.4, p["red"])                                  # tsuka wrap
    cv.rect(1.6, 10.2, 0.8, p["red_dark"], -1.3)
    for d in (3.7, 6.0, 8.3):
        cv.diamond(d, 1.1, p["cream"])
    cv.rect(10.4, 11.6, 6.8, p["gold_dark"])                           # tsuba
    cv.rect(10.7, 11.3, 5.0, p["gold_lite"])
    cv.rect(11.6, 13.0, 4.9, p["gold"])                                # koiguchi
    cv.rect(13.0, 38.0, 4.2, p["saya"])                                # saya
    cv.rect(13.0, 38.0, 0.9, p["saya_dark"], -1.6)
    cv.rect(13.0, 37.0, 0.7, p["saya_lite"], 1.0)
    cv.rect(35.8, 38.0, 4.4, p["gold"])                                # kojiri
    return finish(cv, p["outline"])


def icon_shikai():
    p = pal_shikai()
    cv = Canvas(40.0, 40.0 / 46.0)
    # ribbon from the kashira trailing down in a slow wave
    n = 12
    left, right = [], []
    for i in range(n + 1):
        d = 7.0 - 7.0 * i / n
        w = 1.5 - 0.3 * i / n
        off = 1.3 * math.sin(i * 0.9)
        left.append((off - w, d))
        right.append((off + w, d))
    cv.poly(left + right[::-1], p["ribbon_edge"])
    core_l = [((l[0] + r[0]) / 2 - (r[0] - l[0]) * 0.28, l[1]) for l, r in zip(left, right)]
    core_r = [((l[0] + r[0]) / 2 + (r[0] - l[0]) * 0.28, l[1]) for l, r in zip(left, right)]
    cv.poly(core_l + core_r[::-1], p["ribbon"])
    cv.rect(7.0, 8.4, 3.2, p["tsuba"])                                 # kashira
    cv.rect(8.4, 17.6, 3.2, p["wrap"])                                 # tsuka wrap
    cv.rect(8.4, 17.6, 0.7, p["wrap_shade"], -1.2)
    for d in (10.4, 12.5, 14.6, 16.5):
        cv.diamond(d, 0.9, p["wrap_shade"])
    r = 4.3                                                            # snowflake tsuba
    cv.ellipse(19.0, r, r * 0.55, p["tsuba_shade"])
    cv.ellipse(19.0, r - 0.7, (r - 0.7) * 0.55, p["tsuba"])
    for a in (0, 60, 120):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        k = 0.55
        cv.poly([(-r * 0.85 * ca - 0.3 * sa * k, 19.0 - r * 0.85 * sa * k + 0.3 * ca),
                 (-r * 0.85 * ca + 0.3 * sa * k, 19.0 - r * 0.85 * sa * k - 0.3 * ca),
                 (r * 0.85 * ca + 0.3 * sa * k, 19.0 + r * 0.85 * sa * k - 0.3 * ca),
                 (r * 0.85 * ca - 0.3 * sa * k, 19.0 + r * 0.85 * sa * k + 0.3 * ca)], p["blade_lite"])
    cv.rect(19.8, 21.0, 3.4, p["tsuba_shade"])                         # habaki
    # blade: body, shade half toward the spine, glowing edge line, kissaki
    cv.poly([(-1.6, 21.0), (1.6, 21.0), (1.6, 43.0), (0.0, 46.0), (-1.6, 43.0)], p["blade_lite"])
    cv.poly([(-1.6, 21.0), (-0.5, 21.0), (-0.5, 44.0), (0.0, 46.0), (-1.6, 43.0)], p["blade_shade"])
    cv.poly([(0.7, 21.0), (1.6, 21.0), (1.6, 43.0), (0.0, 46.0), (0.7, 44.0)], p["glow"])
    img = finish(cv, p["outline"])
    d = ImageDraw.Draw(img)
    for x, y in ((6, 5), (27, 24), (4, 15)):                           # a few snow sparkles
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if img.getpixel((x + dx, y + dy))[3] == 0:
                d.point((x + dx, y + dy), fill=p["glow"] + (255,))
    return img


if __name__ == "__main__":
    icons = {"sealed": icon_sealed(), "shikai": icon_shikai()}
    for state, im in icons.items():
        path = OUT / f"sode_no_shirayuki_{state}_icon.png"
        im.save(path)
        print("wrote", path)
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (268 * 2, 256), (90, 90, 96, 255))
        for i, im in enumerate(icons.values()):
            sheet.alpha_composite(im.resize((256, 256), Image.NEAREST), (i * 268, 0))
        sheet.save(sys.argv[1])
        print("preview", sys.argv[1])
