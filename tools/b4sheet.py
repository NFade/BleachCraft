# usage: python -I b4sheet.py <out.png> <cols> <tile_w> <png> [<png> ...] ; labelled contact sheet of screenshots (B4 polish)
import os
import sys
from PIL import Image, ImageDraw

out, cols, tw = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
files = sys.argv[4:]
ims = [Image.open(f).convert("RGB") for f in files]
th = int(ims[0].height * tw / ims[0].width)
rows = (len(ims) + cols - 1) // cols
sheet = Image.new("RGB", (cols * tw, rows * th), (0, 0, 0))
d = ImageDraw.Draw(sheet)
for i, (f, im) in enumerate(zip(files, ims)):
    x, y = (i % cols) * tw, (i // cols) * th
    sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
    d.rectangle([x, y, x + tw, y + 12], fill=(0, 0, 0))
    d.text((x + 3, y + 1), os.path.splitext(os.path.basename(f))[0].replace("spike_tune_", ""), fill=(255, 255, 0))
sheet.save(out)
