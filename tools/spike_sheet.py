# Contact sheet for spike screenshots. usage: python -I spike_sheet.py out.png cols cellW "x0,y0,x1,y1|full" file1 file2 ...
import sys
from PIL import Image, ImageDraw

out, cols, cw, crop = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
files = sys.argv[5:]
cells = []
for f in files:
    im = Image.open(f).convert("RGB")
    if crop != "full":
        x0, y0, x1, y1 = [int(v) for v in crop.split(",")]
        im = im.crop((x0, y0, x1, y1))
    ch = int(cw * im.height / im.width)
    im = im.resize((cw, ch), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    name = f.replace("\\", "/").split("/")[-1].replace("spike_tune_", "").replace(".png", "")
    d.rectangle([0, 0, cw, 14], fill=(0, 0, 0))
    d.text((3, 1), name, fill=(255, 255, 0))
    cells.append(im)
rows = (len(cells) + cols - 1) // cols
ch = max(c.height for c in cells)
sheet = Image.new("RGB", (cols * cw, rows * ch), (40, 40, 40))
for i, c in enumerate(cells):
    sheet.paste(c, ((i % cols) * cw, (i // cols) * ch))
sheet.save(out)
print(out, sheet.size)
