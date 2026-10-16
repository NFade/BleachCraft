"""Item icon of the sealed Quincy cross (R15): a pentacle in a ring on a short chain, 32 x 32, Pillow only.

Output: mod/src/main/resources/assets/reiatsu_test/textures/item/uryu_cross_sealed_icon.png
The chain comes from the top and ends in the ring, like the model (tools/gen_uryu_cross.py).
usage: python -I tools/gen_uryu_cross_icon.py [preview.png]
"""
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "mod/src/main/resources/assets/reiatsu_test/textures/item/uryu_cross_sealed_icon.png"

S = 32
OUTLINE = (30, 38, 66)
DISC = (44, 62, 112)      # the dark blue field inside the ring
RING_D = (92, 112, 160)   # ring: shaded side
RING_M = (160, 180, 214)
RING_L = (226, 236, 250)  # ring: lit side
STAR = (206, 228, 255)    # pentacle, a little bluer than the ring
STAR_L = (255, 255, 255)
CHAIN_D = (96, 112, 150)
CHAIN_L = (196, 210, 232)

CX, CY, R_OUT, R_IN = 15.5, 21.0, 9.4, 7.0


def build():
    px = {}

    def put(x, y, c):
        if 0 <= x < S and 0 <= y < S:
            px[(x, y)] = c

    # disc and ring
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - CX, y - CY)
            if d <= R_IN:
                put(x, y, DISC)
            elif d <= R_OUT:
                # light from the upper left
                a = math.atan2(y - CY, x - CX)  # 0 = right, pi/2 = down
                lit = math.cos(a - math.radians(225))  # 1 at the upper left
                put(x, y, RING_L if lit > 0.55 else RING_M if lit > -0.25 else RING_D)

    # pentacle: five points on a circle, point up, every second one joined
    pts = []
    r = 5.6
    for i in range(5):
        a = math.radians(-90 + 72 * i)
        pts.append((CX + r * math.cos(a), CY + 0.5 + r * math.sin(a)))

    def line(p0, p1, c):
        n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1])) * 2) + 1
        for k in range(n + 1):
            t = k / n
            put(int(round(p0[0] + (p1[0] - p0[0]) * t)), int(round(p0[1] + (p1[1] - p0[1]) * t)), c)

    for i in range(5):
        line(pts[i], pts[(i + 2) % 5], STAR)
    put(int(round(pts[0][0])), int(round(pts[0][1])), STAR_L)
    for p in pts[1:]:
        put(int(round(p[0])), int(round(p[1])), STAR_L)

    # chain: a V of single-pixel links from the top corners down to the eyelet on the ring (a necklace pendant)
    top = int(round(CY - R_OUT))
    for sx, ex in ((4, 15), (27, 16)):
        n = abs(ex - sx)
        for k in range(n + 1):
            x = sx + (ex - sx) * k // n
            y = (top - 2) * k // n
            put(x, y, CHAIN_L if k % 2 == 0 else CHAIN_D)
    put(15, top - 1, CHAIN_L)
    put(16, top - 1, CHAIN_D)

    # outline around every filled pixel
    out = dict(px)
    for (x, y) in list(px):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in px and 0 <= q[0] < S and 0 <= q[1] < S:
                out[q] = OUTLINE
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for (x, y), c in out.items():
        im.putpixel((x, y), c + (255,))
    return im


def main():
    im = build()
    im.save(OUT)
    print("wrote", OUT)
    if len(sys.argv) > 1:
        bg = Image.new("RGBA", (S * 8 * 2 + 24, S * 8 + 16), (70, 70, 80, 255))
        bg.alpha_composite(im.resize((S * 8, S * 8), Image.NEAREST), (8, 8))
        bg.alpha_composite(im, (S * 8 + 16, 8))
        bg.save(sys.argv[1])


if __name__ == "__main__":
    main()
