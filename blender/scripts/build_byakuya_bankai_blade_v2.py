# R11 T13: byakuya_bankai giant blade v2, built from the user's own mesh `Plane` (blender/scenes/byakuya_bankai.blend, left untouched).
# Runs INSIDE the open file (it does not clean the scene):
#   exec(open(r"D:\MineBleach\blender\scripts\build_byakuya_bankai_blade_v2.py").read())
# What it does
#   * reads the silhouette of `Plane` (world space): 8 z levels, 6 vertices per level (spine flat, shoulders, edge point), 1 apex;
#   * normalises it: blade axis +Z, base at z = 0, thickness along X centred on 0, width along Y centred on 0 (edge -Y, spine +Y, like v1),
#     metres, uniform scale so the blade length is BLADE_LENGTH (the ONE size number, also written to the meta file);
#   * rebuilds a CLEAN closed shell (the user's mesh has an open tip and flipped normals): 8-vertex rings with the roles of the v1 atlas
#     islands blade_a / blade_b / blade_s / blade_e (edge strip at u 0.10, bevel up to the shoulder u 0.55, flat wall to the spine), UVs into the
#     existing atlas so the painted PNGs fit unchanged, flat shading, apex fan, buried bottom cap on a soil texel;
#   * LOD: 3 rings x 4 vertices + apex, same silhouette and island layout (13 faces);
#   * v1 objects are renamed *_old, moved to collection `old_blade` and hidden; `Plane` is moved to `user_original` and hidden, never modified.
import sys, os, math, importlib
SD = r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bpy, bmesh
import bb_common as bb, atlas_layouts as al, detail_common as dc
for _m in (bb, al, dc):
    importlib.reload(_m)
from mathutils import Vector

BLADE_LENGTH = float(globals().get("BLADE_LENGTH", 5.5))     # metres; anime measurement of R9 (3 body heights)
EDGE_HALF = 0.002                                            # half width of the 4 mm edge flat (island blade_e), same as v1
S_EDGE = al.GB_S_EDGE                                        # u of the edge-strip split (0.10)
FR = {"P0": 0.0, "S+": S_EDGE, "P1": 0.55, "P2": 1.0, "P3": 1.0, "P4": 0.55, "S-": S_EDGE, "P5": 0.0}
ROLES8 = ["P0", "S+", "P1", "P2", "P3", "P4", "S-", "P5"]
KIND = {("P0", "S+"): "A", ("S+", "P1"): "A", ("P1", "P2"): "A", ("P2", "P3"): "S", ("P3", "P4"): "B",
        ("P4", "S-"): "B", ("S-", "P5"): "B", ("P5", "P0"): "E", ("P0", "P2"): "A", ("P2", "P3_"): "S",
        ("P3", "P5"): "B"}
L = al.BYAKUYA_BANKAI
MODEL = "byakuya_bankai"
EXPORT_OUT = os.path.join(dc.EXPORT, MODEL)

sc = bpy.context.scene
plane = bpy.data.objects["Plane"]
assert plane.type == 'MESH'

# ---------------------------------------------------------------------------------------------- 1. read the user's silhouette
mw = plane.matrix_world
world = [mw @ v.co for v in plane.data.vertices]
levels = []
for p in sorted(world, key=lambda q: q.z):                       # cluster by z: levels are > 1 cm apart, vertices of one level differ by < 1 mm
    if levels and abs(levels[-1][0][2] - p.z) < 0.005:
        levels[-1].append((p.x, p.y, p.z))
    else:
        levels.append([(p.x, p.y, p.z)])
apex_pts = [pts for pts in levels if len(pts) == 1]
assert len(apex_pts) == 1 and levels[-1] is apex_pts[0], "expected a single apex vertex at the top"
ring_levels = levels[:-1]
cx = (min(p.x for p in world) + max(p.x for p in world)) / 2.0
y_lo, y_hi = min(p.y for p in world), max(p.y for p in world)
yc = (y_lo + y_hi) / 2.0
z0, z1 = min(p.z for p in world), max(p.z for p in world)
k = BLADE_LENGTH / (z1 - z0)


def desc(pts):
    ys = sorted({round(p[1], 4) for p in pts})
    y_sp, y_ed = ys[-1], ys[0]
    xs_sp = [p[0] for p in pts if abs(p[1] - y_sp) < 1e-3]
    xl, xr = min(xs_sp), max(xs_sp)
    mid = [y for y in ys if y_ed + 1e-3 < y < y_sp - 1e-3]
    y_sh = mid[0] if mid else (y_sp + y_ed) / 2.0
    return dict(z=pts[0][2], xl=xl, xr=xr, y_sp=y_sp, y_sh=y_sh, y_ed=y_ed)


D = [desc(p) for p in ring_levels]


def T(x, y, z):
    return Vector(((x - cx) * k, (y - yc) * k, (z - z0) * k))


def ring8(d):
    """8 vertices of one ring in the v1 role order (P0 = +X edge corner ... P5 = -X edge corner)."""
    xl, xr = (d["xl"] - cx) * k, (d["xr"] - cx) * k            # xl < 0 < xr
    ysp, ysh, yed = (d["y_sp"] - yc) * k, (d["y_sh"] - yc) * k, (d["y_ed"] - yc) * k
    z = (d["z"] - z0) * k
    e = min(EDGE_HALF, 0.2 * (xr - xl) / 2.0)
    w = ysp - yed
    ys = yed + S_EDGE * w
    t = (ys - yed) / max(ysh - yed, 1e-9)                       # position of the split on the straight bevel P0 -> P1
    t = min(t, 1.0)
    sxp = e + (xr - e) * t
    sxm = -e + (xl + e) * t
    pts = {"P0": (e, yed), "S+": (sxp, ys), "P1": (xr, ysh), "P2": (xr, ysp), "P3": (xl, ysp), "P4": (xl, ysh), "S-": (sxm, ys), "P5": (-e, yed)}
    return [Vector((pts[r][0], pts[r][1], z)) for r in ROLES8], z


def blade_uv(kind, role, s):
    if kind == "A":
        return L["blade_a"].uv(FR[role], s)
    if kind == "B":
        return L["blade_b"].uv(FR[role], s)
    if kind == "S":
        return L["blade_s"].uv(0.0 if role == "P2" else 1.0, s)
    return L["blade_e"].uv(0.0 if role == "P5" else 1.0, s)


# ---------------------------------------------------------------------------------------------- 2. v1 out of the way, collections
def coll(name, hide=False):
    c = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if c.name not in sc.collection.children:
        sc.collection.children.link(c)
    return c


export_c = bpy.data.collections["export"]
old_c = coll("old_blade")
user_c = coll("user_original")
for n in ("byakuya_bankai_blade", "byakuya_bankai_blade_lod"):
    o = bpy.data.objects.get(n)
    if o is not None and not n.endswith("_old"):
        if bpy.data.objects.get(n + "_old") is None:
            o.name = n + "_old"
            o.data.name = n + "_old"
            for c in list(o.users_collection):
                c.objects.unlink(o)
            old_c.objects.link(o)
            o.hide_set(True)
            o.hide_render = True
        else:
            me = o.data
            bpy.data.objects.remove(o, do_unlink=True)          # a previous v2 run: rebuild it
            if me.users == 0:
                bpy.data.meshes.remove(me)
if plane.name not in user_c.objects:
    for c in list(plane.users_collection):
        c.objects.unlink(plane)
    user_c.objects.link(plane)
plane.hide_set(True)
mat = bpy.data.materials["byakuya_bankai_atlas"]

# ---------------------------------------------------------------------------------------------- 3. giant blade v2
mb = dc.MB()
rings, svals = [], []
for d in D:
    pts, z = ring8(d)
    rings.append([mb.v(p) for p in pts])
    svals.append(z / BLADE_LENGTH)
for i in range(len(rings) - 1):
    for a in range(8):
        a1 = (a + 1) % 8
        kd = KIND[(ROLES8[a], ROLES8[a1])]
        lo, hi = rings[i], rings[i + 1]
        s0, s1 = svals[i], svals[i + 1]
        mb.face([lo[a], lo[a1], hi[a1], hi[a]],
                [blade_uv(kd, ROLES8[a], s0), blade_uv(kd, ROLES8[a1], s0), blade_uv(kd, ROLES8[a1], s1), blade_uv(kd, ROLES8[a], s1)])
ax, ay, az = ((apex_pts[0][0][0] - cx) * k, (apex_pts[0][0][1] - yc) * k, BLADE_LENGTH)
apex = mb.v(Vector((ax, ay, az)))
top = rings[-1]
for a in range(8):
    a1 = (a + 1) % 8
    kd = KIND[(ROLES8[a], ROLES8[a1])]
    ua, ub = blade_uv(kd, ROLES8[a], svals[-1]), blade_uv(kd, ROLES8[a1], svals[-1])
    mb.face([top[a], top[a1], apex], [ua, ub, ((ua[0] + ub[0]) / 2.0, blade_uv(kd, ROLES8[a], 1.0)[1])])
mb.face(rings[0], [L["blade_a"].uv(0.5, 0.015)] * 8)              # buried bottom cap, collapsed on a soil texel (as v1)
blade = mb.finish("byakuya_bankai_blade", mat, export_c)

# ---------------------------------------------------------------------------------------------- 4. LOD
mb = dc.MB()
base = D[0]
W = (base["y_sp"] - base["y_ed"]) * k
Tk = (base["xr"] - base["xl"]) * k
lz = [0.0, 0.55 * BLADE_LENGTH, 0.85 * BLADE_LENGTH]
lroles = ["P0", "P2", "P3", "P5"]
lrings, ls = [], []
for z in lz:
    prof = [(Tk / 2, -W / 2), (Tk / 2, W / 2), (-Tk / 2, W / 2), (-Tk / 2, -W / 2)]
    lrings.append([mb.v(Vector((x, y, z))) for x, y in prof])
    ls.append(z / BLADE_LENGTH)
for i in range(2):
    for a in range(4):
        a1 = (a + 1) % 4
        kd = KIND[(lroles[a], lroles[a1])] if (lroles[a], lroles[a1]) in KIND else "E"
        if (lroles[a], lroles[a1]) == ("P5", "P0"):
            kd = "E"
        lo, hi = lrings[i], lrings[i + 1]
        mb.face([lo[a], lo[a1], hi[a1], hi[a]],
                [blade_uv(kd, lroles[a], ls[i]), blade_uv(kd, lroles[a1], ls[i]), blade_uv(kd, lroles[a1], ls[i + 1]), blade_uv(kd, lroles[a], ls[i + 1])])
lap = mb.v(Vector((ax, ay, az)))
for a in range(4):
    a1 = (a + 1) % 4
    kd = KIND.get((lroles[a], lroles[a1]), "E")
    if (lroles[a], lroles[a1]) == ("P5", "P0"):
        kd = "E"
    ua, ub = blade_uv(kd, lroles[a], ls[-1]), blade_uv(kd, lroles[a1], ls[-1])
    mb.face([lrings[-1][a], lrings[-1][a1], lap], [ua, ub, ((ua[0] + ub[0]) / 2.0, blade_uv(kd, lroles[a], 1.0)[1])])
mb.face(lrings[0], [L["blade_a"].uv(0.5, 0.015)] * 4)
lod = mb.finish("byakuya_bankai_blade_lod", mat, export_c)
lod.location = (1.5, 0.0, 0.0)

# ---------------------------------------------------------------------------------------------- 5. tip empty, triangulate modifiers
tip = bpy.data.objects["tip"]
tip.location = (ax, ay, az)
for o in (blade, lod):
    o.data.update()
    for m in [m for m in o.modifiers if m.type == 'TRIANGULATE']:
        o.modifiers.remove(m)
    t = o.modifiers.new("TRIANGULATE", 'TRIANGULATE')
    t.min_vertices = 5

result = {"length": BLADE_LENGTH, "scale": k, "width": W, "thickness": Tk, "apex": (ax, ay, az),
          "blade_faces": len(blade.data.polygons), "lod_faces": len(lod.data.polygons),
          "levels": [(round(d["z"] - z0, 3), round(d["xr"] - d["xl"], 3), round(d["y_sp"] - d["y_ed"], 3)) for d in D]}
print(result)
