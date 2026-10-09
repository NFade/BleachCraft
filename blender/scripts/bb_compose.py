# Compose the per-view Workbench renders of a blockout into blender/renders/<model>_blockout.png (system python + Pillow).
# usage: python bb_compose.py <model>
import json, os, sys
from PIL import Image, ImageDraw, ImageFont

TMP = os.environ.get("BB_TMP", r"C:\Users\efeki\AppData\Local\Temp\claude\D--MineBleach\3ed047c0-7628-4215-89c7-999cf89b2df5\scratchpad")
OUT = r"D:\MineBleach\blender\renders"
model = sys.argv[1]
man = json.load(open(os.path.join(TMP, model + "_manifest.json")))
try:
    font = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 17)
    fontb = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 22)
except Exception:
    font = fontb = ImageFont.load_default()
rows = []
for g in man:
    ims = [Image.open(p).convert("RGB") for _, _, p in g["files"]]
    rows.append((g, ims))
HEAD, FOOT = 34, 26
W = max(sum(im.size[0] for im in ims) for _, ims in rows)
Hh = sum(HEAD + ims[0].size[1] + FOOT for _, ims in rows)
sheet = Image.new("RGB", (W, Hh), (24, 24, 26))
d = ImageDraw.Draw(sheet)
y = 0
for g, ims in rows:
    d.text((10, y + 5), "%s  |  %s  |  %.0f px/m  |  ruler segments = %g m (red base bar = 1 segment)" % (model, g["group"], g["px_per_m"], g["unit_m"]), font=fontb, fill=(235, 235, 235))
    x = 0
    ch = ims[0].size[1]
    for im, (vn, label, _) in zip(ims, g["files"]):
        cw = im.size[0]
        sheet.paste(im, (x, y + HEAD))
        d.rectangle([x, y + HEAD, x + cw - 1, y + HEAD + ch - 1], outline=(70, 70, 74))
        d.text((x + 8, y + HEAD + ch + 3), label, font=font, fill=(190, 190, 195))
        x += cw
    y += HEAD + ch + FOOT
os.makedirs(OUT, exist_ok=True)
dst = os.path.join(OUT, model + "_blockout.png")
sheet.save(dst, optimize=True)
print(dst, sheet.size)
