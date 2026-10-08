# Spike 2b exporter (Blender 5.2.2). ADR section 1 exact parameters, one object at a time.
# Writes blender/export/spike/<object>.obj and spike_meta.json, then re-imports each OBJ in a scratch
# collection and compares the bounding box with the source (applying the meta origin).
import bpy, os, json
from mathutils import Vector

ROOT = r"D:\MineBleach\blender"
OUT = os.path.join(ROOT, "export", "spike")
MODEL = "spike"
OBJECTS = ["spike_cube", "spike_cube_alt", "spike_strip_01", "spike_strip_02"]
EMPTIES = ["grip_hand", "tip", "ribbon_root"]
os.makedirs(OUT, exist_ok=True)

# Parameter names verified on 5.2.2: bpy.ops.wm.obj_export.get_rna_type().properties.keys()
# Triangulate modifier property verified on 5.2.2: min_vertices, ngon_method, quad_method, keep_custom_normals.
rna_keys = set(bpy.ops.wm.obj_export.get_rna_type().properties.keys())
used = ["filepath", "export_selected_objects", "forward_axis", "up_axis", "global_scale", "apply_modifiers",
        "apply_transform", "export_eval_mode", "export_uv", "export_normals", "export_colors", "export_materials",
        "export_pbr_extensions", "path_mode", "export_triangulated_mesh", "export_curves_as_nurbs",
        "export_object_groups", "export_material_groups", "export_vertex_groups", "export_smooth_groups",
        "export_animation"]
missing = [k for k in used if k not in rna_keys]
assert not missing, "obj_export params missing on this Blender: %s" % missing

# sanity: outward normals for the cube part, rotation 0 / scale 1
report = {}
for name in OBJECTS:
    o = bpy.data.objects[name]
    assert all(abs(a) < 1e-9 for a in o.rotation_euler), (name, "rotation not applied")
    assert all(abs(s - 1) < 1e-9 for s in o.scale), (name, "scale not applied")
    # last modifier: Triangulate that splits only n-gons
    for m in [m for m in o.modifiers if m.type == 'TRIANGULATE']:
        o.modifiers.remove(m)
    t = o.modifiers.new("Triangulate", 'TRIANGULATE')
    t.min_vertices = 5
    t.quad_method = 'BEAUTY'
    t.ngon_method = 'BEAUTY'

for name in OBJECTS:
    bpy.ops.object.select_all(action='DESELECT')
    o = bpy.data.objects[name]
    o.select_set(True)
    bpy.context.view_layer.objects.active = o
    bpy.ops.wm.obj_export(
        filepath=os.path.join(OUT, name + ".obj"),
        export_selected_objects=True,
        forward_axis='NEGATIVE_Z', up_axis='Y',
        global_scale=1.0,
        apply_modifiers=True,
        apply_transform=False,
        export_eval_mode='DAG_EVAL_VIEWPORT',
        export_uv=True, export_normals=True, export_colors=False,
        export_materials=False,
        export_pbr_extensions=False, path_mode='STRIP',
        export_triangulated_mesh=False,
        export_curves_as_nurbs=False,
        export_object_groups=False, export_material_groups=False,
        export_vertex_groups=False, export_smooth_groups=False,
        export_animation=False)

meta = {
    "format": 1, "units": "m", "space": "blender_zup",
    "empties": {e: [round(c, 6) for c in bpy.data.objects[e].matrix_world.translation] for e in EMPTIES},
    "objects": {n: [round(c, 6) for c in bpy.data.objects[n].matrix_world.translation] for n in OBJECTS},
}
with open(os.path.join(OUT, MODEL + "_meta.json"), "w", encoding="utf-8") as f:
    json.dump(meta, f, indent=2)

# ---- re-import check: bbox of re-imported OBJ (Y-up) mapped back must equal the source bbox (local space) ----
def src_bbox(o, world=False):
    vs = [(o.matrix_world @ v.co) if world else v.co for v in o.data.vertices]
    return [min(v[i] for v in vs) for i in range(3)], [max(v[i] for v in vs) for i in range(3)]

checks = {}
for name in OBJECTS:
    before = set(bpy.data.objects.keys())
    bpy.ops.wm.obj_import(filepath=os.path.join(OUT, name + ".obj"), forward_axis='NEGATIVE_Z', up_axis='Y')
    new = [bpy.data.objects[k] for k in bpy.data.objects.keys() if k not in before]
    imp = new[0]
    lo_i, hi_i = src_bbox(imp, world=True)
    lo_s, hi_s = src_bbox(bpy.data.objects[name])
    err = max(abs(a - b) for a, b in zip(lo_i + hi_i, lo_s + hi_s))
    checks[name] = {"bbox_err": err, "src_lo": [round(x, 4) for x in lo_s], "src_hi": [round(x, 4) for x in hi_s],
                    "verts": len(imp.data.vertices), "polys": len(imp.data.polygons)}
    for n in new:
        bpy.data.objects.remove(n, do_unlink=True)

bpy.ops.wm.save_mainfile()
result = {"meta": meta, "checks": checks}
