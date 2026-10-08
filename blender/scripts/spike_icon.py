# Spike-only GUI icons (32x32). Final models render these orthographically from Blender (ADR section 1);
# for the spike a hand-drawn stand-in is enough to prove the flat-icon path. System python + Pillow.
import sys
from PIL import Image, ImageDraw

out_dir = sys.argv[1]


def icon(cube_bg, cube_fg, bar_edge, bar_back, tip):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # bar: diagonal from (10,22) to (27,5), 2 px wide, tip emissive-coloured
    for i in range(0, 18):
        x, y = 10 + i, 22 - i
        col = tip if i >= 12 else bar_edge
        d.rectangle([x, y, x + 1, y + 1], fill=col)
        d.point((x + 2, y), fill=bar_back)
    # cube: 9x9 at bottom-left with an inner letter-ish mark
    d.rectangle([3, 19, 12, 28], fill=cube_bg, outline=(20, 20, 20, 255))
    d.rectangle([6, 22, 9, 25], fill=cube_fg)
    return im


icon((240, 220, 40, 255), (0, 0, 0, 255), (230, 20, 20, 255), (120, 160, 255, 255), (255, 170, 40, 255)).save(
    out_dir + "/spike_item_sealed_icon.png")
icon((15, 35, 215, 255), (255, 255, 255, 255), (25, 235, 235, 255), (135, 95, 0, 255), (255, 170, 40, 255)).save(
    out_dir + "/spike_item_shikai_icon.png")
print("icons written to", out_dir)
