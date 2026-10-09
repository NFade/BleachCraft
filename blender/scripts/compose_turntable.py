# Compose the turntable frames into one sheet (system python + Pillow).  usage: python compose_turntable.py <model>
import json, os, sys
from PIL import Image, ImageDraw, ImageFont

TMP = os.environ.get("BB_TMP", r"C:\Users\efeki\AppData\Local\Temp\claude\D--MineBleach\3ed047c0-7628-4215-89c7-999cf89b2df5\scratchpad")
OUT = r"D:\MineBleach\blender\renders"
model = sys.argv[1]
man = json.load(open(os.path.join(TMP, "tt_%s_manifest.json" % model)))
try:
    font = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 15)
    fontb = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 20)
except Exception:
    font = fontb = ImageFont.load_default()
n = man["frames"]
rows = man["rows"]
W = max(r["w"] for r in rows) * n
HEAD, FOOT = 30, 20
H = sum(HEAD + r["h"] + FOOT for r in rows)
sheet = Image.new("RGB", (W, H), (22, 22, 24))
d = ImageDraw.Draw(sheet)
y = 0
for r in rows:
    d.text((8, y + 5), "%s | %s | %d frames, camera yaw 0..%d deg (0 = from -Y, edge toward camera)" % (model, r["label"], n, 360 - 360 // n), font=fontb, fill=(235, 235, 235))
    for i, p in enumerate(r["files"]):
        im = Image.open(p).convert("RGB")
        x = i * r["w"]
        sheet.paste(im, (x, y + HEAD))
        d.rectangle([x, y + HEAD, x + r["w"] - 1, y + HEAD + r["h"] - 1], outline=(60, 60, 64))
        d.text((x + 6, y + HEAD + r["h"] + 2), "%d deg" % (i * 360 // n), font=font, fill=(190, 190, 195))
    y += HEAD + r["h"] + FOOT
os.makedirs(OUT, exist_ok=True)
dst = os.path.join(OUT, model + "_turntable.png")
sheet.save(dst, optimize=True)
print(dst, sheet.size)
