# Export one model (ADR section 1 exact obj_export parameters, one OBJ per object, `_meta.json` with empties and object origins),
# then re-import every OBJ into a scratch scene and compare vertex/face counts and bounding box with the source.
# Parameter names re-checked on Blender 5.2.2 (bpy.ops.wm.obj_export.get_rna_type().properties.keys(); all 21 exist),
# Triangulate modifier: min_vertices, quad_method, ngon_method, keep_custom_normals.
#   import export_obj as eo; res = eo.export_model("rukia_sealed", spec)
import bpy, os, json, re
from mathutils import Vector

ROOT = os.environ.get("BB_ROOT", r"D:\MineBleach\blender")
USED = ["filepath", "export_selected_objects", "forward_axis", "up_axis", "global_scale", "apply_modifiers", "apply_transform",
        "export_eval_mode", "export_uv", "export_normals", "export_colors", "export_materials", "export_pbr_extensions", "path_mode",
        "export_triangulated_mesh", "export_curves_as_nurbs", "export_object_groups", "export_material_groups",
        "export_vertex_groups", "export_smooth_groups", "export_animation"]


def ensure_triangulate(o):
    """Triangulate modifier as the LAST modifier, splitting only n-gons (min_vertices = 5); quads stay quads."""
    for m in [m for m in o.modifiers if m.type == 'TRIANGULATE']:
        o.modifiers.remove(m)
    t = o.modifiers.new("Triangulate", 'TRIANGULATE')
    t.min_vertices = 5
    t.quad_method = 'BEAUTY'
    t.ngon_method = 'BEAUTY'
    t.keep_custom_normals = True


def export_model(model, spec, out_dir=None):
    out = out_dir or os.path.join(ROOT, "export", model)
    os.makedirs(out, exist_ok=True)
    keys = set(bpy.ops.wm.obj_export.get_rna_type().properties.keys())
    missing = [k for k in USED if k not in keys]
    assert not missing, "obj_export params missing: %s" % missing
    names = spec["export_objects"]
    for n in names:
        o = bpy.data.objects[n]
        assert all(abs(a) < 1e-9 for a in o.rotation_euler), (n, "rotation not applied")
        assert all(abs(s - 1) < 1e-9 for s in o.scale), (n, "scale not applied")
        ensure_triangulate(o)
    for n in names:
        o = bpy.data.objects[n]
        for x in bpy.context.view_layer.objects:
            x.select_set(False)
        was_hidden = o.hide_get()
        o.hide_set(False)
        o.select_set(True)
        bpy.context.view_layer.objects.active = o
        bpy.ops.wm.obj_export(
            filepath=os.path.join(out, n + ".obj"),
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
        o.select_set(False)
        o.hide_set(was_hidden)
    meta = {"format": 1, "units": "m", "space": "blender_zup",
            "empties": {e: [round(c, 6) for c in bpy.data.objects[e].matrix_world.translation] for e in spec["export_empties"]},
            "objects": {n: [round(c, 6) for c in bpy.data.objects[n].matrix_world.translation] for n in names}}
    with open(os.path.join(out, model + "_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
    return reimport_check(model, names, out, meta)


def obj_stats(path):
    st = {"v": 0, "vt": 0, "vn": 0, "f": 0, "tris": 0, "quads": 0, "other_f": 0, "bad_keywords": set(), "faces_without_vt": 0}
    for line in open(path, encoding="utf-8"):
        if not line.strip() or line.startswith("#"):
            continue
        k = line.split(None, 1)[0]
        if k in ("v", "vt", "vn"):
            st[k] += 1
        elif k == "f":
            st["f"] += 1
            c = line.split()[1:]
            st["tris" if len(c) == 3 else "quads" if len(c) == 4 else "other_f"] += 1
            if any(len(x.split("/")) < 3 or x.split("/")[1] == "" for x in c):
                st["faces_without_vt"] += 1
        elif k not in ("o", "g", "s"):
            st["bad_keywords"].add(k)
    st["bad_keywords"] = sorted(st["bad_keywords"])
    return st


def reimport_check(model, names, out, meta):
    """Re-import each OBJ (same axis settings) into a scratch collection; compare vertex/face counts with the evaluated source mesh
    and the bounding box with the source (local space; the imported object has the OBJ origin = world origin of its local space)."""
    res = {}
    dg = bpy.context.evaluated_depsgraph_get()
    for n in names:
        o = bpy.data.objects[n]
        ev = o.evaluated_get(dg)
        me = ev.to_mesh()
        sv, sf = len(me.vertices), len(me.polygons)
        sq = sum(1 for p in me.polygons if len(p.vertices) == 4)
        slo = [min(v.co[i] for v in me.vertices) for i in range(3)]
        shi = [max(v.co[i] for v in me.vertices) for i in range(3)]
        ev.to_mesh_clear()
        before = set(bpy.data.objects.keys())
        bpy.ops.wm.obj_import(filepath=os.path.join(out, n + ".obj"), forward_axis='NEGATIVE_Z', up_axis='Y')
        new = [bpy.data.objects[k] for k in bpy.data.objects.keys() if k not in before]
        imp = new[0]
        iw = [imp.matrix_world @ v.co for v in imp.data.vertices]
        ilo = [min(w[i] for w in iw) for i in range(3)]
        ihi = [max(w[i] for w in iw) for i in range(3)]
        err = max(abs(a - b) for a, b in zip(slo + shi, ilo + ihi))
        st = obj_stats(os.path.join(out, n + ".obj"))
        res[n] = {"src_verts": sv, "src_faces": sf, "src_quads": sq, "imp_verts": len(imp.data.vertices), "imp_faces": len(imp.data.polygons),
                  "obj_lines": st, "bbox_err_m": err, "src_bbox": [[round(x, 4) for x in slo], [round(x, 4) for x in shi]],
                  "meta_origin": meta["objects"][n],
                  "match": (sv == len(imp.data.vertices) and sf == len(imp.data.polygons) and err < 1e-5 and st["f"] == sf and
                            st["other_f"] == 0 and st["faces_without_vt"] == 0 and not st["bad_keywords"])}
        for x in new:
            m = x.data
            bpy.data.objects.remove(x, do_unlink=True)
            if m and m.users == 0:
                bpy.data.meshes.remove(m)
    with open(os.path.join(out, "export_check.json"), "w") as f:
        json.dump(res, f, indent=1, default=list)
    return res
