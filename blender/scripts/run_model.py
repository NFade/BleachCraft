# Driver (inside Blender): build -> verify -> export -> turntable -> save scene for one model.
#   exec(open(r"D:\MineBleach\blender\scripts\run_model.py").read(), {"MODEL": "rukia_sealed", "__file__": ...})
# Painting (system python) must have run before: paint_<model>.py, then compose_turntable.py / atlas_preview.py afterwards.
import sys, os, importlib, json, time
SD = r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bb_common, atlas_layouts, detail_common, verify_model, export_obj, model_specs, turntable
for m in (bb_common, atlas_layouts, detail_common, verify_model, export_obj, model_specs, turntable):
    importlib.reload(m)

STEPS = globals().get("STEPS", ("build", "verify", "export", "save", "turntable"))
summary = {}
spec = model_specs.SPECS[MODEL]
if "build" in STEPS:
    p = os.path.join(SD, "build_%s.py" % MODEL)
    g = {"__file__": p, "__name__": "__main__"}
    exec(compile(open(p, encoding="utf-8").read(), p, "exec"), g)
    summary["build"] = g.get("result")
if "verify" in STEPS:
    v = verify_model.verify(MODEL, spec)
    summary["verify_ok"] = v["ok"]
    summary["problems"] = v["problems"]
if "export" in STEPS:
    r = export_obj.export_model(MODEL, spec)
    summary["reimport_match"] = {k: x["match"] for k, x in r.items()}
if "save" in STEPS:
    detail_common.save_scene(MODEL)
    summary["saved"] = True
if "turntable" in STEPS:
    t = time.time()
    turntable.run(MODEL, spec["turntable"])
    summary["turntable_s"] = round(time.time() - t, 1)
result = summary
