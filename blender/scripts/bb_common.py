# Shared helpers for the Phase 3 step 1 blockouts (Blender 5.2.2, bpy). Pure helpers, no scene side effects on import.
# Usage inside Blender (background or MCP):  exec(open(r"D:\MineBleach\blender\scripts\bb_common.py").read())
# or:  sys.path.insert(0, r"D:\MineBleach\blender\scripts"); import bb_common as bb
import bpy, bmesh, math, json, os, re
from mathutils import Vector, Matrix

ROOT = r"D:\MineBleach\blender"
SCENES = os.path.join(ROOT, "scenes")
RENDERS = os.path.join(ROOT, "renders")
TMP = os.environ.get("BB_TMP", r"C:\Users\efeki\AppData\Local\Temp\claude\D--MineBleach\3ed047c0-7628-4215-89c7-999cf89b2df5\scratchpad")
MM = 0.001

# ------------------------------------------------------------------ colours / materials
def srgb2lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def hex2lin(h):
    h = h.lstrip('#')
    return tuple(srgb2lin(int(h[i:i + 2], 16) / 255.0) for i in (0, 2, 4))


def mat(name, hexcol, rough=0.5, metallic=0.0, emit=None, emit_strength=0.0, alpha=1.0):
    """Flat Principled material; Base Color + viewport colour set from the art-bible hex."""
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    lin = hex2lin(hexcol)
    b.inputs['Base Color'].default_value = (*lin, 1.0)
    b.inputs['Roughness'].default_value = rough
    b.inputs['Metallic'].default_value = metallic
    if emit:
        b.inputs['Emission Color'].default_value = (*hex2lin(emit), 1.0)
        b.inputs['Emission Strength'].default_value = emit_strength
    m.diffuse_color = (*lin, alpha)
    return m


# ------------------------------------------------------------------ scene management
def clean_scene():
    if bpy.context.object and bpy.context.object.mode != 'OBJECT':
        bpy.ops.object.mode_set(mode='OBJECT')
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.cameras, bpy.data.lights, bpy.data.images,
                 bpy.data.collections, bpy.data.worlds):
        for d in list(coll):
            coll.remove(d)
    sc = bpy.context.scene
    sc.unit_settings.system = 'METRIC'
    sc.unit_settings.scale_length = 1.0
    return sc


def new_collection(name):
    c = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(c)
    return c


def link_only(o, coll):
    for c in list(o.users_collection):
        c.objects.unlink(o)
    coll.objects.link(o)


# ------------------------------------------------------------------ raw mesh building
def mesh_obj(name, verts, faces, mat_names=None, face_mats=None, smooth=False):
    """Create an object from verts/faces; normals recalculated outward (closed meshes)."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bv = [bm.verts.new(v) for v in verts]
    for i, f in enumerate(faces):
        try:
            bf = bm.faces.new([bv[k] for k in f])
        except ValueError:
            continue
        if face_mats:
            bf.material_index = face_mats[i]
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    for mn in (mat_names or []):
        me.materials.append(bpy.data.materials[mn])
    if smooth:
        me.shade_smooth()
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    return o


def loft(name, rings, mat_name, cap_start=True, cap_end=True, apex=None, smooth=False, face_mats=None, mat_names=None):
    """rings: list of rings, each a list of Vectors (same count). apex: optional Vector closing the last ring in a fan."""
    n = len(rings[0])
    verts = [v for r in rings for v in r]
    faces = []
    for i in range(len(rings) - 1):
        for k in range(n):
            a, b = i * n + k, i * n + (k + 1) % n
            c, d = (i + 1) * n + (k + 1) % n, (i + 1) * n + k
            faces.append((a, b, c, d))
    ngap = len(faces)
    if cap_start:
        faces.append(tuple(range(n)))
    if apex is not None:
        ai = len(verts)
        verts.append(apex)
        base = (len(rings) - 1) * n
        for k in range(n):
            faces.append((base + k, base + (k + 1) % n, ai))
    elif cap_end:
        faces.append(tuple(range((len(rings) - 1) * n, len(rings) * n)))
    fm = None
    if face_mats is not None:
        fm = list(face_mats) + [face_mats[-1] if face_mats else 0] * (len(faces) - len(face_mats))
    return mesh_obj(name, verts, faces, mat_names or [mat_name], fm, smooth)


def prism(name, pts, z0, z1, mat_name, smooth=False):
    """Extrude a 2D outline (list of (x, y)) along Z."""
    n = len(pts)
    verts = [Vector((x, y, z0)) for x, y in pts] + [Vector((x, y, z1)) for x, y in pts]
    faces = [tuple(range(n)), tuple(range(n, 2 * n))]
    for k in range(n):
        faces.append((k, (k + 1) % n, n + (k + 1) % n, n + k))
    return mesh_obj(name, verts, faces, [mat_name], None, smooth)


def box(name, lo, hi, mat_name):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    return prism(name, [(x0, y0), (x1, y0), (x1, y1), (x0, y1)], z0, z1, mat_name)


def boolean_diff(target, cutters):
    for i, c in enumerate(cutters):
        m = target.modifiers.new("bb_cut%d" % i, 'BOOLEAN')
        m.operation = 'DIFFERENCE'
        m.solver = 'MANIFOLD'
        m.object = c
        with bpy.context.temp_override(object=target, active_object=target, selected_objects=[target]):
            bpy.ops.object.modifier_apply(modifier=m.name)
        bpy.data.objects.remove(c, do_unlink=True)
    return target


def join(parts, name, mesh_name=None):
    parts = [p for p in parts if p]
    act = parts[0]
    with bpy.context.temp_override(object=act, active_object=act, selected_objects=parts, selected_editable_objects=parts):
        bpy.ops.object.join()
    act.name = name
    act.data.name = mesh_name or name
    return act


def xform(objs, m):
    for o in objs:
        o.data.transform(m)
        o.data.update()


def delete(o):
    bpy.data.objects.remove(o, do_unlink=True)


# ------------------------------------------------------------------ 2D outlines (X, Y) in metres
def oval(a, b, n=12):
    return [(a * math.cos(2 * math.pi * k / n), b * math.sin(2 * math.pi * k / n)) for k in range(n)]


def circle_pts(r, n=24):
    return oval(r, r, n)


def rounded_rect(hx, hy, r, n=4):
    pts = []
    for cx, cy, a0 in ((hx - r, hy - r, 0), (-hx + r, hy - r, 90), (-hx + r, -hy + r, 180), (hx - r, -hy + r, 270)):
        for k in range(n + 1):
            t = math.radians(a0 + 90.0 * k / n)
            pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    return pts


def concave_rect(hx, hy, r, n=4):
    """Rectangle whose four corners are cut by concave quarter circles (radius r centred on the corner)."""
    pts = []
    spec = ((1, 1, 0, 90), (-1, 1, 90, 0), (-1, -1, 0, 90), (1, -1, 90, 0))
    for sx, sy, u0, u1 in spec:
        for k in range(n + 1):
            u = math.radians(u0 + (u1 - u0) * k / n)
            pts.append((sx * (hx - r * math.sin(u)), sy * (hy - r * math.cos(u))))
    return pts


def stadium(hx, hy, n=6):
    """Stadium along Y: half-width hx (X), half-length hy (Y), semicircular ends of radius hx."""
    pts = []
    for k in range(n + 1):
        t = math.pi * k / n
        pts.append((hx * math.cos(t), hy - hx + hx * math.sin(t)))
    for k in range(n + 1):
        t = math.pi + math.pi * k / n
        pts.append((hx * math.cos(t), -hy + hx + hx * math.sin(t)))
    return pts


def rect(hx, hy, cx=0.0, cy=0.0):
    return [(cx - hx, cy - hy), (cx + hx, cy - hy), (cx + hx, cy + hy), (cx - hx, cy + hy)]


# ------------------------------------------------------------------ sori / blade
SORI_K = 1.0


def sori_off(z, z0, length, tip_off):
    s = (z - z0) / length
    return tip_off * s * s


def sori_phi(z, z0, length, tip_off):
    return math.atan(2.0 * tip_off * (z - z0) / (length * length))


def ring_pts(profile, z, yc=0.0, phi=0.0, xc=0.0):
    c, s = math.cos(phi), math.sin(phi)
    return [Vector((xc + x, yc + y * c, z - y * s)) for x, y in profile]


def shinogi_profile(w, t, ridge=0.55, edge=0.0005):
    """6 vertices; edge faces -Y (y = -w/2), spine +Y, flats +-X. Ridge at 55 percent of the width from the edge."""
    ry = -w / 2 + ridge * w
    return [(edge, -w / 2), (t / 2, ry), (t / 2, w / 2), (-t / 2, w / 2), (-t / 2, ry), (-edge, -w / 2)]


def lerp(a, b, t):
    return a + (b - a) * t


def blade(name, z0, length, root_w, yok_w, root_t, tip_t, kissaki, sori_chord, mat_name, n_mid=5):
    """Katana blade from z0 (after the habaki) to z0+length. The base tangent runs along +Z and the centreline is a parabola
    whose tip is offset toward +Y by sori * SORI_K (SORI_K = 1: sori = tip deflection from the base axis, the same
    convention as the giant bankai blade; SORI_K = 4 would make sori the chord deviation)."""
    tip_off = SORI_K * sori_chord
    z_tip = z0 + length
    z_yk = z_tip - kissaki
    rings = []
    for i in range(n_mid + 1):
        z = lerp(z0, z_yk, i / n_mid)
        u = (z - z0) / (z_yk - z0)
        w, t = lerp(root_w, yok_w, u), lerp(root_t, tip_t, u)
        rings.append(ring_pts(shinogi_profile(w, t), z, sori_off(z, z0, length, tip_off), sori_phi(z, z0, length, tip_off)))
    zk = z_yk + 0.5 * kissaki
    wk, tk = yok_w * 0.62, tip_t * 0.8
    prof = [(x, y + 0.10 * yok_w) for x, y in shinogi_profile(wk, tk)]
    rings.append(ring_pts(prof, zk, sori_off(zk, z0, length, tip_off), sori_phi(zk, z0, length, tip_off)))
    apex = ring_pts([(0.0, 0.30 * yok_w)], z_tip, sori_off(z_tip, z0, length, tip_off), sori_phi(z_tip, z0, length, tip_off))[0]
    o = loft(name, rings, mat_name, apex=apex)
    o["tip_pos"] = tuple(apex)
    return o, apex


# ------------------------------------------------------------------ hilt parts (BASE, ART_BIBLE 1.0)
def hilt_parts(prefix, wrap, kashira_m, fuchi_m, tsuka_len=0.25, kashira_dims=(0.015, 0.011, 0.014),
               tsuka_dims=(0.015, 0.012), fuchi_dims=(0.016, 0.013), fuchi_len=0.014, swell=0.003, kashira_cyl=False):
    """kashira + tsuka + fuchi as separate objects (joined later). Tsuka runs from the kashira top to the fuchi bottom,
    so volumes do not overlap (deviation from 'tsuka z 0 to 0.250', documented)."""
    ka, kb, kh = kashira_dims
    k_rings = [[Vector((x * 0.9, y * 0.9, 0)) for x, y in oval(ka, kb)] if not kashira_cyl else
               [Vector((x, y, 0)) for x, y in oval(ka, kb)]]
    k_rings.append([Vector((x, y, 0.002)) for x, y in oval(ka, kb)])
    k_rings.append([Vector((x, y, kh)) for x, y in oval(ka, kb)])
    kash = loft(prefix + "_kashira", k_rings, kashira_m, smooth=False)
    z0, z1 = kh, tsuka_len - fuchi_len
    rings = []
    nr = 10
    a, b = tsuka_dims
    for i in range(nr):
        u = i / (nr - 1)
        z = lerp(z0, z1, u)
        mid = max(0.0, math.sin(math.pi * (u - 1 / 3) * 3 / 1.0)) if 1 / 3 <= u <= 2 / 3 else 0.0
        sw = swell / 2.0 * mid
        rings.append([Vector((x, y, z)) for x, y in oval(a + sw, b + sw)])
    tsuka = loft(prefix + "_tsuka", rings, wrap, smooth=True)
    fa, fb = fuchi_dims
    fuchi = loft(prefix + "_fuchi", [[Vector((x, y, z1)) for x, y in oval(fa, fb)],
                                      [Vector((x, y, tsuka_len)) for x, y in oval(fa, fb)]], fuchi_m, smooth=False)
    return [kash, tsuka, fuchi]


def habaki(prefix, z0, mat_name, w=0.032, t=0.010, h=0.028):
    return box(prefix + "_habaki", (-t / 2, -w / 2, z0), (t / 2, w / 2, z0 + h), mat_name)


# ------------------------------------------------------------------ tsuba builders (plate in XY, thickness along Z)
def _cutter(name, pts, z0, z1):
    return prism(name, pts, z0 - 0.002, z1 + 0.002, bpy.data.materials[0].name)


def tsuba_rukia_sealed(prefix, z0, mat_name, thick=0.006):
    z1 = z0 + thick
    o = prism(prefix + "_tsuba", concave_rect(0.031, 0.036, 0.008, 4), z0, z1, mat_name)
    cutters = []
    for sy in (-1, 1):
        pts = []
        n = 6
        for k in range(n + 1):
            x = -0.007 + 0.014 * k / n
            c = -sy * 0.0025 * (x / 0.007) ** 2
            pts.append((x, sy * 0.026 + c + 0.002 * (1 - (x / 0.007) ** 2) ** 0.5 + 0.0003))
        for k in range(n, -1, -1):
            x = -0.007 + 0.014 * k / n
            c = -sy * 0.0025 * (x / 0.007) ** 2
            pts.append((x, sy * 0.026 + c - 0.002 * (1 - (x / 0.007) ** 2) ** 0.5 - 0.0003))
        # drop duplicate end points
        dd = []
        for p in pts:
            if not dd or (abs(p[0] - dd[-1][0]) > 1e-9 or abs(p[1] - dd[-1][1]) > 1e-9):
                dd.append(p)
        cutters.append(_cutter(prefix + "_sukashi", dd, z0, z1))
    return boolean_diff(o, cutters)


def tsuba_snowflake(prefix, z0, mat_name, thick=0.006, r_out=0.040, r_rim=0.032, r_hub=0.020):
    z1 = z0 + thick
    o = prism(prefix + "_tsuba", circle_pts(r_out, 36), z0, z1, mat_name)

    def h(r):
        return (0.0035 + (r - r_hub) / (r_rim - r_hub) * 0.0025)

    cutters = []
    for k in range(6):
        t0, t1 = math.radians(60 * k), math.radians(60 * (k + 1))
        u = lambda t: Vector((math.cos(t), math.sin(t)))
        up = lambda t: Vector((-math.sin(t), math.cos(t)))
        d_in, d_out = math.asin(h(r_hub) / r_hub), math.asin(h(r_rim) / r_rim)
        pts = []
        for i in range(5):  # inner arc, increasing angle
            t = lerp(t0 + d_in, t1 - d_in, i / 4)
            pts.append(tuple(r_hub * u(t)))
        for r in (r_hub, r_rim):  # along spoke 1 edge, outward
            p = r * u(t1) - h(r) * up(t1)
            if r == r_rim:
                pts.append(tuple(p))
        for i in range(5):  # outer arc, decreasing angle
            t = lerp(t1 - d_out, t0 + d_out, i / 4)
            pts.append(tuple(r_rim * u(t)))
        # (spoke 0 edge back to start is the closing edge)
        cutters.append(_cutter(prefix + "_win", pts, z0, z1))
    return boolean_diff(o, cutters)


def tsuba_byakuya_sealed(prefix, z0, mat_name, thick=0.007):
    z1 = z0 + thick
    o = prism(prefix + "_tsuba", rounded_rect(0.028, 0.046, 0.005, 3), z0, z1, mat_name)
    cutters = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            pts = [(9, 4), (20, 4), (20, 38), (5, 38), (5, 20), (9, 20)]
            pts = [(sx * x * MM, sy * y * MM) for x, y in pts]
            if sx * sy < 0:
                pts = pts[::-1]
            cutters.append(_cutter(prefix + "_win", pts, z0, z1))
    return boolean_diff(o, cutters)


def tsuba_bankai_bar(prefix, z0, mat_name, thick=0.006):
    z1 = z0 + thick
    o = prism(prefix + "_tsuba", stadium(0.011, 0.042, 6), z0, z1, mat_name)
    cutters = []
    for sy in (-1, 1):
        pts = [(x, y + sy * 0.030) for x, y in stadium(0.007, 0.008, 4)]
        cutters.append(_cutter(prefix + "_win", pts, z0, z1))
    return boolean_diff(o, cutters)


# ------------------------------------------------------------------ empties
def empty(name, loc, coll, size=0.03, kind='PLAIN_AXES'):
    e = bpy.data.objects.new(name, None)
    e.empty_display_type = kind
    e.empty_display_size = size
    coll.objects.link(e)
    e.location = loc
    return e


# ------------------------------------------------------------------ statistics
def obj_stats(o):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = o.evaluated_get(dg)
    me = ev.to_mesh()
    tris = sum(len(p.vertices) - 2 for p in me.polygons)
    mw = o.matrix_world
    ws = [mw @ v.co for v in me.vertices]
    lo = [round(min(v[i] for v in ws), 4) for i in range(3)]
    hi = [round(max(v[i] for v in ws), 4) for i in range(3)]
    n_loops = len(me.vertices)
    ev.to_mesh_clear()
    return {"name": o.name, "tris": tris, "verts": n_loops, "bbox_min": lo, "bbox_max": hi,
            "size": [round(hi[i] - lo[i], 4) for i in range(3)],
            "origin": [round(c, 4) for c in o.matrix_world.translation],
            "materials": [m.name for m in o.data.materials if m]}


def group_bbox(objs):
    lo = Vector((1e9,) * 3)
    hi = Vector((-1e9,) * 3)
    dg = bpy.context.evaluated_depsgraph_get()
    for o in objs:
        ev = o.evaluated_get(dg)
        me = ev.to_mesh()
        for v in me.vertices:
            w = o.matrix_world @ v.co
            for i in range(3):
                lo[i] = min(lo[i], w[i])
                hi[i] = max(hi[i], w[i])
        ev.to_mesh_clear()
    return lo, hi


# ------------------------------------------------------------------ ruler (10 cm segments by default)
def make_ruler(lo, hi, unit=0.10, coll=None, margin=None):
    """Vertical ruler along Z from lo.z to hi.z (rounded up to whole units), alternating dark/light segments,
    beside the group (+X, +Y of its bbox) so it is visible in side, front and 3/4 views."""
    margin = margin if margin is not None else max(unit * 1.2, 0.03)
    px, py = hi.x + margin, hi.y + margin
    size = max(unit * 0.12, 0.004)
    mat("bb_ruler_dark", "#202020")
    mat("bb_ruler_light", "#F0F0F0")
    mat("bb_ruler_red", "#E02020")
    n = int(math.ceil((hi.z - lo.z) / unit - 1e-6))
    parts = []
    for i in range(n):
        z0 = lo.z + i * unit
        m = "bb_ruler_dark" if i % 2 == 0 else "bb_ruler_light"
        parts.append(box("bb_ruler_seg%d" % i, (px - size, py - size, z0), (px + size, py + size, z0 + unit), m))
    # horizontal 10-unit scale bar at the base
    parts.append(box("bb_ruler_base", (px - size, py - size, lo.z - size), (px + size + unit, py + size, lo.z), "bb_ruler_red"))
    return parts


# ------------------------------------------------------------------ rendering
def setup_world_and_render(w, h):
    sc = bpy.context.scene
    wd = bpy.data.worlds.get("bb_world") or bpy.data.worlds.new("bb_world")
    wd.color = (0.30, 0.31, 0.33)
    sc.world = wd
    sc.render.engine = 'BLENDER_WORKBENCH'
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = 100
    sc.render.film_transparent = False
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGB'
    sd = sc.display
    sd.render_aa = '16'
    sh = sd.shading
    sh.light = 'STUDIO'
    sh.color_type = 'MATERIAL'
    sh.show_object_outline = True
    sh.show_cavity = False
    sh.show_shadows = False
    try:
        sc.view_settings.view_transform = 'Standard'
    except Exception:
        pass
    return sc


def camera_for(sc):
    cd = bpy.data.cameras.new("bb_cam")
    cd.type = 'ORTHO'
    cd.sensor_fit = 'VERTICAL'
    cd.clip_start, cd.clip_end = 0.01, 500.0
    co = bpy.data.objects.new("bb_cam", cd)
    bpy.context.scene.collection.objects.link(co)
    sc.camera = co
    return co


VIEWS = [("side", Vector((-1, 0, 0)), "side (cam +X, +Y right, edge left)"),
         ("front", Vector((0, 1, 0)), "front (cam -Y, +X right, edge toward cam)"),
         ("three_quarter", Vector((-0.62, 0.62, -0.30)), "3/4 (cam +X,-Y, above)")]
VIEW_TOP = ("top", Vector((0, 0, -1)), "top (cam +Z, +X right, +Y up)")


def _basis(d):
    d = d.normalized()
    up = Vector((0, 1, 0)) if abs(d.z) > 0.95 else Vector((0, 0, 1))
    right = d.cross(up).normalized()
    upv = right.cross(d).normalized()
    return right, upv, d


def render_group(model, gname, objs, all_mesh_objs, W=520, H=860, unit=0.10, views=VIEWS, pad=1.10, dist=60.0):
    """Renders the 3 orthographic views of `objs` (others hidden in render). Returns [(label, path)] and px/m."""
    sc = bpy.context.scene
    setup_world_and_render(W, H)
    cam = bpy.data.objects.get("bb_cam") or camera_for(sc)
    lo, hi = group_bbox(objs)
    ruler = make_ruler(lo, hi, unit)
    rlo, rhi = group_bbox(ruler)
    lo = Vector([min(lo[i], rlo[i]) for i in range(3)])
    hi = Vector([max(hi[i], rhi[i]) for i in range(3)])
    corners = [Vector((x, y, z)) for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]
    centre = (lo + hi) / 2
    scale = 0.0
    for _, d, _l in views:
        r, u, dd = _basis(d)
        rs = [c.dot(r) for c in corners]
        us = [c.dot(u) for c in corners]
        wm, hm = (max(rs) - min(rs)) * pad, (max(us) - min(us)) * pad
        scale = max(scale, hm, wm * H / W)
    for o in all_mesh_objs:
        o.hide_render = o not in objs
        o.hide_set(False)
    out = []
    for vname, d, label in views:
        r, u, dd = _basis(d)
        rs = [c.dot(r) for c in corners]
        us = [c.dot(u) for c in corners]
        c2 = r * ((max(rs) + min(rs)) / 2) + u * ((max(us) + min(us)) / 2) + dd * centre.dot(dd)
        cam.location = c2 - dd * dist
        m3 = Matrix((r, u, -dd)).transposed()
        cam.rotation_euler = m3.to_euler()
        cam.data.ortho_scale = scale
        path = os.path.join(TMP, "%s__%s__%s.png" % (model, gname, vname))
        os.makedirs(TMP, exist_ok=True)
        sc.render.filepath = path
        bpy.ops.render.render(write_still=True)
        out.append((vname, label, path))
    for o in ruler:
        delete(o)
    return out, H / scale


# ------------------------------------------------------------------ BLOCKOUTS.md section writer
MODEL_ORDER = ["rukia_sealed", "rukia_shikai", "byakuya_sealed", "byakuya_shikai", "rukia_bankai", "byakuya_bankai"]
MD_HEAD = "# BLOCKOUTS (Phase 3 step 1)\n\nBlender 5.2.2 blockouts, Workbench renders, flat Principled colours from ART_BIBLE v2. Generated per model by `blender/scripts/blockout_<model>.py`.\n\nRun headless (the Blender MCP socket was not available, no GUI window was opened): `blender.exe --background --factory-startup --python blender/scripts/blockout_<model>.py`, then `python blender/scripts/bb_compose.py <model>`. Binary: `D:\\SteamLibrary\\steamapps\\common\\Blender\\blender.exe` (5.2.2 LTS).\n\n"


def update_md(model, text):
    p = os.path.join(RENDERS, "BLOCKOUTS.md")
    secs = {}
    if os.path.exists(p):
        body = open(p, encoding="utf-8").read()
        for chunk in re.split(r"(?m)^(?=## )", body):
            m = re.match(r"## (\S+)", chunk)
            if m:
                secs[m.group(1)] = chunk.rstrip() + "\n\n"
    secs[model] = text.rstrip() + "\n\n"
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(MD_HEAD)
        for k in MODEL_ORDER:
            if k in secs:
                f.write(secs[k])


def md_section(model, stats, empties, notes, deviations, questions, extra=""):
    L = ["## %s\n" % model]
    L.append("Scene: `blender/scenes/%s.blend`. Render: `blender/renders/%s_blockout.png`.\n" % (model, model))
    L.append("| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |")
    L.append("|---|---|---|---|---|---|")
    for s in stats:
        L.append("| `%s` | %d | %s | %s | %s | %s |" % (s["name"], s["tris"], tuple(s["bbox_min"]), tuple(s["bbox_max"]),
                                                    tuple(s["size"]), tuple(s["origin"])))
    L.append("")
    L.append("Empties: " + "; ".join("`%s` (%s)" % (n, ", ".join("%g" % round(c, 4) for c in v)) for n, v in empties) + "\n")
    if extra:
        L.append(extra + "\n")
    if notes:
        L.append("Notes:\n" + "\n".join("- " + n for n in notes) + "\n")
    L.append("Deviations from the art bible:\n" + "\n".join("- " + d for d in deviations) + "\n")
    L.append("Open questions for Gate B:\n" + "\n".join("- " + q for q in questions) + "\n")
    return "\n".join(L)


def save_blend(model):
    os.makedirs(SCENES, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(SCENES, model + ".blend"))


def finish(model, exp_objs, groups, empties, notes, deviations, questions, extra="", hidden=(), W=520, H=860, helpers=None, temp_objs=()):
    """groups: list of (gname, [objs], unit_m[, dict(views=..., W=..., H=..., pad=...)]). Renders every group, writes stats +
    BLOCKOUTS.md section + manifest, saves the .blend. temp_objs are render-only helpers (deleted before saving)."""
    stats = [obj_stats(o) for o in exp_objs]
    man = []
    allm = list(exp_objs) + list(temp_objs)
    for g in groups:
        gname, objs, unit = g[0], g[1], g[2]
        opt = g[3] if len(g) > 3 else {}
        files, pxm = render_group(model, gname, objs, allm, W=opt.get("W", W), H=opt.get("H", H), unit=unit,
                                  views=opt.get("views", VIEWS), pad=opt.get("pad", 1.10))
        man.append({"group": gname, "unit_m": unit, "px_per_m": round(pxm, 1), "files": files})
    for t in temp_objs:
        delete(t)
    with open(os.path.join(TMP, model + "_manifest.json"), "w") as f:
        json.dump(man, f, indent=1)
    # persistent ruler for the first group, camera and world live in 'helpers'
    g0 = groups[0]
    lo, hi = group_bbox(g0[1])
    ruler = make_ruler(lo, hi, g0[2])
    hc = helpers or bpy.data.collections.get("helpers") or new_collection("helpers")
    for r in ruler:
        link_only(r, hc)
    link_only(bpy.data.objects["bb_cam"], hc)
    for o in exp_objs:
        o.hide_render = False
        o.hide_set(o.name in hidden)
    update_md(model, md_section(model, stats, empties, notes, deviations, questions, extra))
    save_blend(model)
    with open(os.path.join(TMP, model + "_stats.json"), "w") as f:
        json.dump({"stats": stats, "empties": [(n, list(v)) for n, v in empties]}, f, indent=1)
    return stats
