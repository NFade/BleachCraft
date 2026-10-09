"""Copies exported Blender models into the mod resources (ADR section 1 resource layout).

usage: python -I tools/place_model_assets.py rukia_sealed rukia_shikai

For every model: blender/export/<model>/*.obj and <model>_meta.json go to
mod/src/main/resources/assets/reiatsu_test/models/obj/<model>/, the atlas PNGs
<model>_diffuse.png / <model>_emissive.png go to textures/item/.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "mod/src/main/resources/assets/reiatsu_test"

for model in sys.argv[1:]:
    src = ROOT / "blender/export" / model
    dst = RES / "models/obj" / model
    dst.mkdir(parents=True, exist_ok=True)
    for f in sorted(src.glob("*.obj")) + [src / f"{model}_meta.json"]:
        shutil.copyfile(f, dst / f.name)
        print("copied", f.name, "->", dst.relative_to(ROOT))
    for kind in ("diffuse", "emissive"):
        f = src / f"{model}_{kind}.png"
        shutil.copyfile(f, RES / "textures/item" / f.name)
        print("copied", f.name, "-> textures/item")
