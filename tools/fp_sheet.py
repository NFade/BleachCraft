#!/usr/bin/env python3
"""Contact sheet of first person captures: python -I tools/fp_sheet.py out.png cols cellw label=path [label=path ...]"""
import sys
from PIL import Image, ImageDraw, ImageFont

out, cols, cw = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
items = [a.split("=", 1) for a in sys.argv[4:]]
imgs = []
for label, path in items:
    try:
        im = Image.open(path).convert("RGB")
    except OSError:
        im = Image.new("RGB", (1280, 720), (90, 0, 0))
    ch = int(cw * im.height / im.width)
    imgs.append((label, im.resize((cw, ch), Image.LANCZOS)))
ch = imgs[0][1].height
rows = (len(imgs) + cols - 1) // cols
lab = 18
sheet = Image.new("RGB", (cols * (cw + 4) + 4, rows * (ch + lab + 4) + 4), (32, 32, 32))
d = ImageDraw.Draw(sheet)
try:
    font = ImageFont.truetype("arial.ttf", 13)
except OSError:
    font = ImageFont.load_default()
for i, (label, im) in enumerate(imgs):
    x = 4 + (i % cols) * (cw + 4)
    y = 4 + (i // cols) * (ch + lab + 4)
    sheet.paste(im, (x, y + lab))
    d.text((x + 3, y + 2), label, fill=(255, 255, 255), font=font)
sheet.save(out)
print(out, sheet.size)
