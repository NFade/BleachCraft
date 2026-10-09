# Reusable model verification (Blender side). Usage inside Blender:
#   sys.path.insert(0, scripts); import verify_model as vm; res = vm.verify(MODEL, spec)
# spec = dict(
#   objects = {"obj_name": dict(budget=3000, target=1800, bbox_min=(..), bbox_max=(..), tol=0.002, origin=(0,0,0))},
#   layout  = atlas_layouts.RUKIA_SEALED (dict name -> Isl)  (for UV island reporting) or None,
#   empties = {"grip_hand": (0,0,0.19), "tip": (0, y, z)},  tol_empty = 0.002,
#   material = "<model>_atlas")
# Checks per object: triangles (evaluated, triangulated, after the export Triangulate modifier) vs budget, bounding box, rotation
# 0 / scale 1 / origin, normals (flipped faces vs a hole-filled recalculated copy + signed volume of every closed component),
# UV (inside 0..1, overlapped texels at 1024 resolution, degenerate faces, texel density per island), loose geometry,
# single material, empties.
import bpy, bmesh, math, json, os
import numpy as np
from mathutils import Vector

TMP = os.environ.get("BB_TMP", r"C:\Users\efeki\AppData\Local\Temp\claude\D--MineBleach\3ed047c0-7628-4215-89c7-999cf89b2df5\scratchpad")
RES = 1024


def eval_mesh(o):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = o.evaluated_get(dg)
    me = ev.to_mesh()
    return ev, me


def tri_count(o):
    ev, me = eval_mesh(o)
    t = sum(len(p.vertices) - 2 for p in me.polygons)
    ev.to_mesh_clear()
    return t


def normals_check(o):
    """Flipped faces: compare each face normal with the normal after recalc on a copy whose open holes are filled."""
    bm = bmesh.new()
    bm.from_mesh(o.data)
    bm.normal_update()
    bm.faces.ensure_lookup_table()
    n0 = len(bm.faces)
    for i, f in enumerate(bm.faces):
        f.index = i
    orig = [f.normal.copy() for f in bm.faces]
    bnd = [e for e in bm.edges if e.is_boundary]
    nb = len(bnd)
    nm = len([e for e in bm.edges if not e.is_manifold and not e.is_boundary])
    cp = bm.copy()
    cb = [e for e in cp.edges if e.is_boundary]
    if cb:
        bmesh.ops.holes_fill(cp, edges=cb, sides=0)
    cp.faces.ensure_lookup_table()
    # signed volume per connected component BEFORE recalculation (holes_fill follows the neighbours' winding): > 0 = outward
    seen = set()
    vols = []
    cp.verts.ensure_lookup_table()
    for v0 in cp.verts:
        if v0.index in seen:
            continue
        comp, stack = [], [v0]
        seen.add(v0.index)
        while stack:
            v = stack.pop()
            comp.append(v)
            for e in v.link_edges:
                w = e.other_vert(v)
                if w.index not in seen:
                    seen.add(w.index)
                    stack.append(w)
        fs = {f for v in comp for f in v.link_faces}
        vol = 0.0
        for f in fs:
            vs = [v.co for v in f.verts]
            for k in range(1, len(vs) - 1):
                vol += vs[0].dot(vs[k].cross(vs[k + 1])) / 6.0
        vols.append(vol)
    bmesh.ops.recalc_face_normals(cp, faces=cp.faces[:])
    flipped = 0
    bad = []
    for i in range(n0):
        if orig[i].dot(cp.faces[i].normal) < 0.0:
            flipped += 1
            if len(bad) < 5:
                bad.append(i)
    bm.free()
    cp.free()
    return {"faces": n0, "flipped": flipped, "flipped_examples": bad, "boundary_edges": nb, "non_manifold_edges": nm,
            "components": len(vols), "min_volume_mm3": round(min(vols) * 1e9, 1) if vols else None}


def raster_tri(buf, uvs, cnt_img=None):
    xs = [p[0] * RES for p in uvs]
    ys = [(1.0 - p[1]) * RES for p in uvs]
    x0, x1 = int(max(0, math.floor(min(xs)))), int(min(RES - 1, math.ceil(max(xs))))
    y0, y1 = int(max(0, math.floor(min(ys)))), int(min(RES - 1, math.ceil(max(ys))))
    (ax, ay), (bx, by), (cx, cy) = zip(xs, ys)
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-12:
        return 0
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    w1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
    w2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
    w3 = 1 - w1 - w2
    m = (w1 > 1e-9) & (w2 > 1e-9) & (w3 > 1e-9)
    buf[y0:y1 + 1, x0:x1 + 1] += m
    return int(m.sum())


def uv_check(o, layout):
    ev, me = eval_mesh(o)
    uvl = me.uv_layers.active
    res = {"has_uv": uvl is not None}
    if uvl is None:
        ev.to_mesh_clear()
        return res
    buf = np.zeros((RES, RES), np.int16)
    out_of_range = 0
    degenerate = 0
    dens = []        # (px/m, island or None)
    mw = o.matrix_world
    names = list(layout.keys()) if layout else []
    for p in me.polygons:
        uvs = [tuple(uvl.data[li].uv) for li in p.loop_indices]
        if any(u < -1e-6 or u > 1 + 1e-6 or v < -1e-6 or v > 1 + 1e-6 for u, v in uvs):
            out_of_range += 1
        uva = 0.0
        for k in range(1, len(uvs) - 1):
            a, b, c = uvs[0], uvs[k], uvs[k + 1]
            uva += abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])) / 2.0
            raster_tri(buf, [a, b, c])
        if uva < 1e-9:
            degenerate += 1
            continue
        area = p.area
        cx = sum(u for u, v in uvs) / len(uvs) * 256
        cy = (1 - sum(v for u, v in uvs) / len(uvs)) * 256
        isl = None
        for nm in names:
            i = layout[nm]
            if i.x - 0.5 <= cx <= i.x + i.w + 0.5 and i.y - 0.5 <= cy <= i.y + i.h + 0.5:
                isl = nm
                break
        if area > 1e-10:
            dens.append((math.sqrt(uva * 256 * 256 / area), isl))
    ev.to_mesh_clear()
    over = buf > 1
    res.update(out_of_range_faces=out_of_range, degenerate_uv_faces=degenerate, covered_texels_1024=int((buf > 0).sum()),
               overlapped_texels_1024=int(over.sum()))
    # which islands overlap
    ys, xs = np.nonzero(over)
    oi = {}
    for y, x in zip(ys[::7], xs[::7]):
        px, py = x / RES * 256, y / RES * 256
        for nm in names:
            i = layout[nm]
            if i.x <= px < i.x + i.w and i.y <= py < i.y + i.h:
                oi[nm] = oi.get(nm, 0) + 1
                break
    res["overlap_islands"] = oi
    per = {}
    for d, nm in dens:
        per.setdefault(nm or "?", []).append(d)
    res["texel_density_px_per_m"] = {k: [round(float(np.percentile(v, 5))), round(float(np.median(v))), round(float(np.percentile(v, 95)))]
                                     for k, v in sorted(per.items())}
    allv = [d for d, _ in dens]
    if allv:
        res["texel_density_overall"] = [round(min(allv)), round(float(np.median(allv))), round(max(allv))]
    return res


def verify(model, spec):
    out = {"model": model, "objects": {}, "ok": True, "problems": []}
    layout = spec.get("layout")
    for name, ex in spec["objects"].items():
        o = bpy.data.objects.get(name)
        if o is None:
            out["problems"].append("missing object " + name)
            out["ok"] = False
            continue
        r = {}
        r["tris"] = tri_count(o)
        r["budget"] = ex.get("budget")
        r["target"] = ex.get("target")
        if ex.get("budget") and r["tris"] > ex["budget"]:
            out["problems"].append("%s over budget %d > %d" % (name, r["tris"], ex["budget"]))
        mw = o.matrix_world
        ev, me = eval_mesh(o)
        ws = [mw @ v.co for v in me.vertices]
        lo = [min(w[i] for w in ws) for i in range(3)]
        hi = [max(w[i] for w in ws) for i in range(3)]
        r["verts"] = len(me.vertices)
        r["polys_before_triangulation_eval"] = len(me.polygons)
        ev.to_mesh_clear()
        r["bbox_min"] = [round(x, 4) for x in lo]
        r["bbox_max"] = [round(x, 4) for x in hi]
        r["size"] = [round(hi[i] - lo[i], 4) for i in range(3)]
        tol = ex.get("tol", 0.002)
        for key, vals in (("bbox_min", lo), ("bbox_max", hi)):
            if ex.get(key):
                if max(abs(a - b) for a, b in zip(ex[key], vals)) > tol:
                    out["problems"].append("%s %s %s != expected %s" % (name, key, [round(x, 4) for x in vals], ex[key]))
        r["origin"] = [round(c, 5) for c in mw.translation]
        r["rotation0"] = all(abs(a) < 1e-9 for a in o.rotation_euler)
        r["scale1"] = all(abs(s - 1) < 1e-9 for s in o.scale)
        if not (r["rotation0"] and r["scale1"]):
            out["problems"].append(name + " rotation/scale not applied")
        if ex.get("origin") and max(abs(a - b) for a, b in zip(ex["origin"], r["origin"])) > 1e-4:
            out["problems"].append("%s origin %s != %s" % (name, r["origin"], ex["origin"]))
        r["materials"] = [m.name for m in o.data.materials if m]
        if spec.get("material") and r["materials"] != [spec["material"]]:
            out["problems"].append("%s materials %s" % (name, r["materials"]))
        r["modifiers"] = [m.type for m in o.modifiers]
        r["normals"] = normals_check(o)
        if r["normals"]["flipped"] or (r["normals"]["min_volume_mm3"] is not None and r["normals"]["min_volume_mm3"] <= 0):
            out["problems"].append("%s normals: %s" % (name, r["normals"]))
        r["uv"] = uv_check(o, layout)
        if r["uv"].get("out_of_range_faces"):
            out["problems"].append("%s UV outside 0..1 on %d faces" % (name, r["uv"]["out_of_range_faces"]))
        bm = bmesh.new()
        bm.from_mesh(o.data)
        r["loose_verts"] = len([v for v in bm.verts if not v.link_edges])
        r["loose_edges"] = len([e for e in bm.edges if not e.link_faces])
        r["ngons"] = len([f for f in bm.faces if len(f.verts) > 4])
        bm.free()
        out["objects"][name] = r
    out["empties"] = {}
    for n, loc in spec.get("empties", {}).items():
        e = bpy.data.objects.get(n)
        if e is None:
            out["problems"].append("missing empty " + n)
            continue
        got = [round(c, 5) for c in e.matrix_world.translation]
        out["empties"][n] = got
        if max(abs(a - b) for a, b in zip(loc, got)) > spec.get("tol_empty", 0.002):
            out["problems"].append("empty %s at %s expected %s" % (n, got, loc))
    out["ok"] = not out["problems"]
    with open(os.path.join(TMP, "verify_%s.json" % model), "w") as f:
        json.dump(out, f, indent=1)
    return out
