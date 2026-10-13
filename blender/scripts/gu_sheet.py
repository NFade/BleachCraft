# Compose preview frames into one labelled sheet (system python + Pillow).
#   python gu_sheet.py <out.png> <tmp_dir> name1:label1,label2 name2:label ...   (frame files are gu_<name>_<label>.png)
import sys, os
from PIL import Image, ImageDraw

out, tmp = sys.argv[1], sys.argv[2]
rows = []
for spec in sys.argv[3:]:
    name, labels = spec.split(":")
    rows.append([(name, l, Image.open(os.path.join(tmp, "gu_%s_%s.png" % (name, l)))) for l in labels.split(",")])
W = max(sum(i.width + 6 for _, _, i in r) for r in rows)
H = sum(max(i.height for _, _, i in r) + 22 for r in rows)
sheet = Image.new("RGB", (W, H), (36, 38, 42))
d = ImageDraw.Draw(sheet)
y = 0
for r in rows:
    x = 0
    for name, label, im in r:
        d.text((x + 4, y + 4), "%s %s" % (name, label), fill=(230, 230, 230))
        sheet.paste(im, (x, y + 20))
        x += im.width + 6
    y += max(i.height for _, _, i in r) + 22
os.makedirs(os.path.dirname(out), exist_ok=True)
sheet.save(out)
print(out, sheet.size)
