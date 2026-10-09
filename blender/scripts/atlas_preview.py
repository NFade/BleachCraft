# Atlas preview sheet (system python + Pillow): diffuse x3 (nearest) with island outlines, emissive next to it.
#   python atlas_preview.py <model>
import sys, os
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import atlas_layouts as al
model = sys.argv[1]
S = 2 if al.LAYOUTS[model][next(iter(al.LAYOUTS[model]))].atlas > 256 else 3
d = Image.open(r"D:\MineBleach\blender\export\%s\%s_diffuse.png" % (model, model)).convert("RGBA")
e = Image.open(r"D:\MineBleach\blender\export\%s\%s_emissive.png" % (model, model)).convert("RGBA")
bg = Image.new("RGBA", d.size, (40, 40, 44, 255))
bg.alpha_composite(d)
ee = Image.new("RGBA", e.size, (0, 0, 0, 255))
a = e.split()[3]
TINT = (249, 200, 246, 255) if model == "byakuya_shikai" else (207, 239, 255, 255) if model == "rukia_bankai" else (191, 228, 255, 255)
ee.paste(Image.new("RGBA", e.size, TINT), (0, 0), a)
sheet = Image.new("RGB", (d.width * S * 2 + 30, d.height * S + 24), (20, 20, 22))
dd = ImageDraw.Draw(sheet)
dd.text((6, 4), "%s diffuse (x3, nearest) with UV islands" % model, fill=(230, 230, 230))
dd.text((d.width * S + 26, 4), "emissive (alpha = intensity, tinted for display)", fill=(230, 230, 230))
sheet.paste(bg.resize((d.width * S, d.height * S), Image.NEAREST).convert("RGB"), (0, 24))
sheet.paste(ee.resize((d.width * S, d.height * S), Image.NEAREST).convert("RGB"), (d.width * S + 30, 24))
isl = dict(al.LAYOUTS[model])
if model == "rukia_shikai":
    for n in range(1, 11):
        isl["ribbon_%02d" % n] = al.ribbon_cell(n)
for k, i in isl.items():
    x0, y0, x1, y1 = [v * S for v in i.rect()]
    dd.rectangle([x0, y0 + 24, x1, y1 + 24], outline=(255, 80, 80))
    if i.w * S > 40:
        dd.text((x0 + 2, y0 + 26), k, fill=(255, 255, 255))
dst = r"D:\MineBleach\blender\renders\%s_atlas.png" % model
sheet.save(dst)
print(dst, sheet.size)
