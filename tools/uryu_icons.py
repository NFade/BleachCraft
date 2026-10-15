"""Uryu ability icons for the HUD strip: 6 cells of 16 x 16 in one 96 x 16 sheet, Pillow only.

Output: mod/src/main/resources/assets/reiatsu_test/textures/gui/hud/uryu_ability_icons.png
Cell order (StripHud.uryuIconIndex): 0 heilig_pfeil, 1 sprenger, 2 seele_schneider, 3 wing_barrage, 4 licht_regen, 5 sternenstaub.
Same style as the Rukia/Byakuya cells of the atlas (tools/gen_hud_textures.py): 16 x 16 pixel art, four tones white / ice / blue /
deep blue plus a deep blue outline added around every shape. Quincy palette, no pink.
usage: python -I tools/uryu_icons.py [contact_sheet.png]
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "mod/src/main/resources/assets/reiatsu_test/textures/gui/hud/uryu_ability_icons.png"

PAL = {1: (255, 255, 255), 2: (207, 239, 255), 3: (95, 170, 240), 4: (38, 92, 190)}  # W C M D
OUTLINE = (14, 30, 84)
S = 16


class Cell:
    def __init__(self):
        self.im = Image.new("L", (S, S), 0)
        self.d = ImageDraw.Draw(self.im)

    def line(self, x0, y0, x1, y1, c, w=1):
        self.d.line([(x0, y0), (x1, y1)], fill=c, width=w)

    def poly(self, pts, c):
        self.d.polygon(pts, fill=c)

    def dot(self, x, y, c):
        if 0 <= x < S and 0 <= y < S:
            self.im.putpixel((x, y), c)

    def rect(self, x0, y0, x1, y1, c):
        self.d.rectangle([x0, y0, x1, y1], fill=c)

    def finish(self):
        px = self.im.load()
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        po = out.load()
        for y in range(S):
            for x in range(S):
                if px[x, y]:
                    po[x, y] = PAL[px[x, y]] + (255,)
        for y in range(S):
            for x in range(S):
                if not px[x, y] and any(0 <= x + dx < S and 0 <= y + dy < S and px[x + dx, y + dy]
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    po[x, y] = OUTLINE + (255,)
        return out


def arrow(c, x0, y0, x1, y1, head=3):
    """Arrow with a bright core from the tail (x0, y0) to the tip (x1, y1)."""
    c.line(x0, y0, x1, y1, 3, 2)
    c.line(x0 + 1, y0, x1 + 1, y1, 2, 1)
    c.poly([(x1, y1), (x1 - 2 * head, y1 + 1), (x1 - 1, y1 + 2 * head)], 2)
    c.dot(x1, y1, 1)


def heilig_pfeil():
    c = Cell()
    # trailing glow: two fainter streaks behind the shaft
    c.line(1, 11, 5, 7, 4, 1)
    c.line(3, 14, 7, 10, 4, 1)
    c.line(2, 14, 3, 13, 3, 1)
    c.dot(1, 13, 2)
    c.dot(4, 15, 3)
    arrow(c, 4, 12, 13, 3, 3)
    c.line(3, 10, 5, 12, 2, 1)  # fletching
    c.line(4, 11, 6, 13, 2, 1)
    c.dot(13, 2, 1)
    return c.finish()


def sprenger():
    c = Cell()
    # small arrow flying in from the left, bursting at the target
    c.line(1, 8, 5, 8, 3, 1)
    c.poly([(7, 8), (3, 5), (3, 11)], 2)
    c.dot(1, 7, 2)
    c.dot(1, 9, 2)
    cx, cy = 10, 8
    for dx, dy, ln in ((1, 0, 5), (-1, 0, 4), (0, 1, 5), (0, -1, 5), (1, 1, 3), (-1, -1, 3), (1, -1, 3), (-1, 1, 3)):
        for k in range(1, ln + 1):
            c.dot(cx + dx * k, cy + dy * k, 3 if k > ln - 2 else 2)
    c.rect(cx - 2, cy - 2, cx + 2, cy + 2, 2)
    c.rect(cx - 1, cy - 1, cx + 1, cy + 1, 1)
    c.dot(cx + 4, cy - 4, 1)
    return c.finish()


def seele_schneider():
    c = Cell()
    c.poly([(14, 1), (13, 4), (4, 12), (3, 11)], 2)  # blade
    c.line(13, 2, 5, 10, 1, 1)  # edge light
    c.line(4, 8, 8, 12, 3, 2)  # crossguard
    c.line(1, 14, 4, 11, 4, 2)  # handle
    c.dot(1, 14, 3)
    c.dot(13, 3, 3)  # blade tip shade
    c.line(10, 14, 14, 10, 3, 1)  # slash crescent
    c.dot(15, 8, 2)
    c.dot(11, 15, 2)
    return c.finish()


def wing_barrage():
    c = Cell()
    ox, oy = 2, 14
    # five swept feathers: long blades with a pale edge, widest in the middle, a gap between neighbours
    tips = ((1, 4, 2), (4, 0, 3), (9, 0, 2), (14, 3, 3), (15, 8, 2))
    for tx, ty, col in tips:
        mx, my = (ox + tx) // 2 + (1 if tx > 8 else 0), (oy + ty) // 2
        c.poly([(ox, oy), (mx - 1, my + 1), (tx, ty), (mx + 1, my)], col)
        c.dot(tx, ty, 1)
    c.rect(1, 13, 3, 15, 4)
    c.dot(2, 14, 1)
    return c.finish()


def licht_regen():
    c = Cell()
    # target square on the ground, needles falling onto it
    c.rect(1, 11, 14, 14, 4)
    c.rect(2, 12, 13, 13, 3)
    c.rect(3, 12, 12, 12, 2)
    for x, top, bot in ((3, 1, 9), (7, 0, 8), (11, 2, 10), (14, 5, 10), (5, 5, 10)):
        c.line(x, top, x, bot - 3, 3, 1)
        c.line(x, bot - 3, x, bot, 2, 1)
        c.dot(x, bot + 1, 1)
    return c.finish()


def sternenstaub():
    c = Cell()
    c.line(0, 15, 6, 9, 4, 1)  # line of dust into the star
    c.dot(2, 12, 3)
    c.dot(4, 14, 2)
    cx, cy = 10, 6
    c.poly([(cx, cy - 6), (cx + 1, cy - 1), (cx + 6, cy), (cx + 1, cy + 1), (cx, cy + 6), (cx - 1, cy + 1), (cx - 6, cy), (cx - 1, cy - 1)], 2)
    c.poly([(cx, cy - 3), (cx + 1, cy - 1), (cx + 3, cy), (cx + 1, cy + 1), (cx, cy + 3), (cx - 1, cy + 1), (cx - 3, cy), (cx - 1, cy - 1)], 1)
    for dx, dy in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
        c.dot(cx + dx, cy + dy, 3)
    c.dot(cx - 6, cy - 5, 2)
    c.dot(14, 12, 3)
    c.dot(13, 14, 2)
    return c.finish()


def main():
    cells = [heilig_pfeil(), sprenger(), seele_schneider(), wing_barrage(), licht_regen(), sternenstaub()]
    sheet = Image.new("RGBA", (S * len(cells), S), (0, 0, 0, 0))
    for i, im in enumerate(cells):
        sheet.paste(im, (i * S, 0))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    if len(sys.argv) > 1:
        k = 8
        bg = Image.new("RGBA", (sheet.width * k // 1 + 0, S * k), (30, 34, 48, 255))
        big = sheet.resize((sheet.width * k, S * k), Image.NEAREST)
        bg.alpha_composite(big)
        bg.save(sys.argv[1])
    print("wrote", OUT)


if __name__ == "__main__":
    main()
