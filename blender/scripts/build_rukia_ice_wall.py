# Build the Rukia ice wall crystal pieces (R11): parametric, deterministic (no random module, hash based, see ice_wall_field.py), no hand sculpting.
#
# Run INSIDE Blender (exec with __file__ set), in a scene of its own, e.g.
#   g = {"__file__": r"<worktree>\blender\scripts\build_rukia_ice_wall.py", "__name__": "__main__", "STEPS": ("build", "export", "golden", "wall", "render")}
#   exec(compile(open(g["__file__"], encoding="utf-8").read(), g["__file__"], "exec"), g); print(g["result"])
# It only touches objects whose name starts with "rukia_ice_wall" (it never wipes the scene, a live scene of somebody else survives it), and writes ONLY below <worktree>:
#   blender/export/rukia_ice_wall/*.obj + rukia_ice_wall_meta.json   (existing export_obj.py, ADR section 1 exact parameters)
#   mod/src/test/resources/ice_wall_field_golden.json                (parity data for the Java port core/world/IceCrystalField)
#   <TILES>/*.png                                                    (render tiles for tools/compose_ice_wall_sheet.py), only with the step "render"
# Steps: build (13 pieces + 8 inner cores as objects), verify (budget, planarity, outward winding), export, golden, wall (a 9 x 5 x 2 check wall, a 1 x 5
# column and a ground plane, NOT exported), render (tiles).
import importlib.util
import json
import math
import os
import sys

import bpy
import bmesh
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(globals().get("__file__", "build_rukia_ice_wall.py")))
WORKTREE = os.path.abspath(os.path.join(HERE, "..", ".."))
TEX_DIR = os.path.join(WORKTREE, "mod", "src", "main", "resources", "assets", "reiatsu_test", "textures", "block")
EXPORT_DIR = os.path.join(WORKTREE, "blender", "export", "rukia_ice_wall")
GOLDEN = os.path.join(WORKTREE, "mod", "src", "test", "resources", "ice_wall_field_golden.json")
TILES = globals().get("TILES", os.path.join(WORKTREE, "blender", "renders", "rukia_ice_wall_tiles"))
STEPS = globals().get("STEPS", ("build", "verify", "export", "golden", "wall", "render"))


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


fld = _load("ice_wall_field_build", os.path.join(HERE, "ice_wall_field.py"))
exp = _load("export_obj_ice_build", os.path.join(HERE, "export_obj.py"))
PREFIX = fld.MODEL
BUDGET_OBJECT = 96          # tris per exported object (after triangulation)
BUDGET_PIECE = 120          # shell + core of one piece
BUDGET_CELL = 280           # worst case of one block: piece + 2 satellites + feet
BUDGET_WALL = 12000         # the 9 x 5 x 2 wall

scene = bpy.context.scene
result = {}


def clean_own():
    for o in [o for o in bpy.data.objects if o.name.startswith(PREFIX)]:
        me = o.data
        bpy.data.objects.remove(o, do_unlink=True)
        if isinstance(me, bpy.types.Mesh) and me.users == 0:
            bpy.data.meshes.remove(me)
        elif isinstance(me, bpy.types.Light) and me.users == 0:
            bpy.data.lights.remove(me)
        elif isinstance(me, bpy.types.Camera) and me.users == 0:
            bpy.data.cameras.remove(me)


def material():
    name = PREFIX + "_atlas"
    mat = bpy.data.materials.get(name)
    if mat is not None:
        bpy.data.materials.remove(mat)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])

    def tex(png):
        n = nt.nodes.new("ShaderNodeTexImage")
        n.image = bpy.data.images.load(os.path.join(TEX_DIR, png), check_existing=True)
        n.image.reload()
        n.interpolation = "Closest"
        return n

    d, g = tex("ice_wall_crystal.png"), tex("ice_wall_crystal_glow.png")
    nt.links.new(d.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(d.outputs["Alpha"], bsdf.inputs["Alpha"])
    nt.links.new(g.outputs["Color"], bsdf.inputs["Emission Color"])
    mul = nt.nodes.new("ShaderNodeMath")
    mul.operation = "MULTIPLY"
    mul.inputs[1].default_value = 3.5
    nt.links.new(g.outputs["Alpha"], mul.inputs[0])
    nt.links.new(mul.outputs["Value"], bsdf.inputs["Emission Strength"])
    bsdf.inputs["Roughness"].default_value = 0.12
    for key, val in (("IOR", 1.31), ("Specular IOR Level", 0.6)):
        if key in bsdf.inputs:
            bsdf.inputs[key].default_value = val
    for attr, val in (("surface_render_method", "DITHERED"), ("use_backface_culling", True)):
        try:
            setattr(mat, attr, val)
        except (AttributeError, TypeError):
            pass
    return mat


def make_object(name, mesh, loc, mat, link=True):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    vs = [bm.verts.new(v) for v in mesh.verts]
    for idx, cell, uvs in mesh.faces:
        f = bm.faces.new([vs[i] for i in idx])
        f.smooth = False
        for loop, uv in zip(f.loops, uvs):
            loop[uvl].uv = uv
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    if link:
        scene.collection.objects.link(ob)
    return ob


def faces_object(name, faces, loc, mat):
    """faces: [(points, cell, uvs)] -> one object (the check wall)."""
    m = fld.Mesh()
    for pts, cell, uvs in faces:
        base = len(m.verts)
        m.verts.extend(pts)
        m.faces.append((tuple(range(base, base + len(pts))), cell, uvs))
    return make_object(name, m, loc, mat)


PIECES = {}
LAYOUT = {}


def layout_location(i):
    return ((i % 7) * 1.7, 0.0, -(i // 7) * 1.9)


def build():
    clean_own()
    mat = material()
    names = []
    for i, n in enumerate(fld.PIECE_NAMES):
        shell, core = fld.build_piece(n)
        PIECES[n] = (shell, core)
        loc = layout_location(i)
        LAYOUT[n] = loc
        make_object(fld.obj_name(n), shell, loc, mat)
        names.append(fld.obj_name(n))
        if core is not None:
            make_object(fld.obj_name(n, True), core, loc, mat)
            names.append(fld.obj_name(n, True))
    bpy.context.view_layer.update()  # the view layer must know the new objects before export_obj walks it
    result["objects"] = names
    return names


def verify():
    problems = []
    stats = {}
    for n, (shell, core) in PIECES.items():
        for label, mesh in (("", shell), ("_core", core)):
            if mesh is None:
                continue
            tris = mesh.tris()
            stats[n + label] = tris
            if tris > BUDGET_OBJECT:
                problems.append("%s%s: %d tris > %d" % (n, label, tris, BUDGET_OBJECT))
            # planarity of the quads and outward winding (column pieces: normal away from the piece axis)
            for idx, cell, uvs in mesh.faces:
                p = [Vector(mesh.verts[i]) for i in idx]
                nrm = (p[1] - p[0]).cross(p[2] - p[0])
                if nrm.length < 1e-9:
                    problems.append("%s%s: degenerate face" % (n, label))
                    continue
                nrm.normalize()
                if len(p) == 4 and abs(nrm.dot(p[3] - p[0])) > 1e-6:
                    problems.append("%s%s: non planar quad" % (n, label))
                if n.startswith(("shaft", "tip_a")) and len(p) != 6:
                    c = sum(p, Vector()) / len(p)
                    if nrm.dot(Vector((c.x, c.y, 0.0))) < -1e-6 and nrm.z < 0.99:
                        problems.append("%s%s: inward face" % (n, label))
        total = (shell.tris() + (core.tris() if core else 0))
        if total > BUDGET_PIECE:
            problems.append("%s: %d tris with core > %d" % (n, total, BUDGET_PIECE))
    worst = 0
    wall_tris = 0
    for p in fld.wall_cells():
        sh, co = fld.cell_geometry(p, {n: PIECES[n] for n in PIECES})
        t = sum(1 if len(f[0]) == 3 else (2 if len(f[0]) == 4 else len(f[0]) - 2) for f in sh + co)
        wall_tris += t
        worst = max(worst, t)
    if worst > BUDGET_CELL:
        problems.append("a block emits %d tris > %d" % (worst, BUDGET_CELL))
    if wall_tris > BUDGET_WALL:
        problems.append("the wall emits %d tris > %d" % (wall_tris, BUDGET_WALL))
    result["verify"] = {"problems": problems, "tris": stats, "worst_cell_tris": worst, "wall_9x5x2_tris": wall_tris}


def export():
    names = result["objects"] if "objects" in result else [o.name for o in bpy.data.objects if o.name.startswith(PREFIX)]
    spec = {"export_objects": names, "export_empties": []}
    res = exp.export_model(PREFIX, spec, out_dir=EXPORT_DIR)
    result["reimport_match"] = {k: v["match"] for k, v in res.items()}
    clean_extra = [o for o in bpy.data.objects if o.name.startswith(PREFIX) and o.name not in names]
    assert not clean_extra, clean_extra


def golden():
    os.makedirs(os.path.dirname(GOLDEN), exist_ok=True)
    with open(GOLDEN, "w", encoding="utf-8", newline="\n") as f:
        json.dump(fld.golden(), f, indent=1)
        f.write("\n")
    result["golden"] = GOLDEN


# ---------------------------------------------------------------------------------------------------------------- check wall and render tiles
def cell_origin(c):
    return (c["x"] + 0.5, -(c["z"] + 0.5), c["y"])


def wall(mat, width=9, height=5, depth=2, x0=0, z0=0, tag="wall"):
    pieces = {n: PIECES[n] for n in PIECES}
    shell, core = [], []
    for c in fld.wall_cells(width, height, depth, x0=x0, z0=z0):
        s, k = fld.cell_geometry(c, pieces)
        ox, oy, oz = cell_origin(c)
        for dst, src in ((shell, s), (core, k)):
            for pts, cell, uvs in src:
                dst.append(([(p[0] + ox, p[1] + oy, p[2] + oz) for p in pts], cell, uvs))
    a = faces_object("%s_check_%s_shell" % (PREFIX, tag), shell, (0, 0, 0), mat)
    b = faces_object("%s_check_%s_core" % (PREFIX, tag), core, (0, 0, 0), mat)
    return a, b


def ground(size=400.0):
    me = bpy.data.meshes.new(PREFIX + "_check_ground")
    bm = bmesh.new()
    vs = [bm.verts.new(v) for v in ((-size, -size, -0.001), (size, -size, -0.001), (size, size, -0.001), (-size, size, -0.001))]
    bm.faces.new(vs)
    bm.to_mesh(me)
    bm.free()
    mat = bpy.data.materials.new(PREFIX + "_check_ground_mat")
    mat.use_nodes = True
    b = next(n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = (0.10, 0.13, 0.18, 1.0)
    b.inputs["Roughness"].default_value = 0.9
    me.materials.append(mat)
    ob = bpy.data.objects.new(PREFIX + "_check_ground", me)
    scene.collection.objects.link(ob)
    return ob


def set_world(color, strength):
    w = bpy.data.worlds.get(PREFIX + "_check_world") or bpy.data.worlds.new(PREFIX + "_check_world")
    w.use_nodes = True
    bg = next(n for n in w.node_tree.nodes if n.type == "BACKGROUND")
    bg.inputs["Color"].default_value = (color[0], color[1], color[2], 1.0)
    bg.inputs["Strength"].default_value = strength
    scene.world = w


def engine():
    ids = [i.identifier for i in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
    pick = next((i for i in ids if "EEVEE" in i), ids[0])
    scene.render.engine = pick
    return pick


def camera(eye, target, ortho=None, lens=50.0):
    cam = bpy.data.objects.get(PREFIX + "_check_cam")
    if cam is None:
        cd = bpy.data.cameras.new(PREFIX + "_check_cam")
        cam = bpy.data.objects.new(PREFIX + "_check_cam", cd)
        scene.collection.objects.link(cam)
    cam.data.type = "ORTHO" if ortho else "PERSP"
    if ortho:
        cam.data.ortho_scale = ortho
    else:
        cam.data.lens = lens
    cam.data.clip_start = 0.05
    cam.data.clip_end = 400.0
    cam.location = Vector(eye)
    cam.rotation_euler = (Vector(target) - Vector(eye)).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    return cam


def sun():
    s = bpy.data.objects.get(PREFIX + "_check_sun")
    if s is None:
        sd = bpy.data.lights.new(PREFIX + "_check_sun", "SUN")
        s = bpy.data.objects.new(PREFIX + "_check_sun", sd)
        scene.collection.objects.link(s)
    s.data.energy = 4.0
    s.rotation_euler = (math.radians(52), math.radians(8), math.radians(38))
    return s


def shot(name, w, h, eye, target, ortho=None, lens=50.0, world=((0.16, 0.22, 0.32), 1.2), hide=(), wire=False):
    os.makedirs(TILES, exist_ok=True)
    set_world(*world)
    camera(eye, target, ortho, lens)
    scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = w, h, 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.filepath = os.path.join(TILES, name + ".png")
    hidden = []
    for o in bpy.data.objects:
        if o.name.startswith(PREFIX) and any(t in o.name for t in hide) and not o.hide_render:
            o.hide_render = True
            hidden.append(o)
    bpy.ops.render.render(write_still=True)
    for o in hidden:
        o.hide_render = False
    return scene.render.filepath


def render_tiles():
    mat = bpy.data.materials[PREFIX + "_atlas"]
    eng = engine()
    scene.render.film_transparent = False
    try:
        scene.eevee.taa_render_samples = 64
    except AttributeError:
        pass
    sun()
    gr = ground()
    cx = 4.5
    tiles = {}
    # 1: gallery of the pieces (no variation), front, no ground
    gr.hide_render = True
    check = [o for o in bpy.data.objects if "_check_" in o.name and o != gr and not o.name.endswith("_cam") and not o.name.endswith("_sun")]
    for o in check:
        o.hide_render = True
    gr.hide_render = True
    tiles["gallery"] = shot("gallery", 1536, 420, (5.1, -18.0, -0.4), (5.1, 0, -0.4), ortho=12.5, world=((0.20, 0.27, 0.38), 1.0))
    # hide the gallery pieces from now on, show the check objects
    for o in bpy.data.objects:
        if o.name.startswith(PREFIX) and "_check_" not in o.name:
            o.hide_render = True
    for o in check:
        o.hide_render = False
    gr.hide_render = False
    # the single column (x = 3, z = 4) is the 1 x 5 x 1 check column
    col_a, col_b = wall(mat, 1, 5, 1, x0=3, z0=4, tag="column")
    colx, coly = 3.5, -4.5
    col_c = (colx, coly, 2.9)
    # keep the 9x5x2 wall away from the column when both exist: hide the column when shooting the wall
    shells = [o for o in bpy.data.objects if o.name.endswith("_check_wall_shell") or o.name.endswith("_check_wall_core")]
    cols = [o for o in bpy.data.objects if "_check_column_" in o.name]
    for o in shells:
        o.hide_render = True
    tiles["col_front"] = shot("col_front", 384, 512, (colx, coly - 14.0, 2.9), col_c, ortho=4.8)
    tiles["col_side"] = shot("col_side", 384, 512, (colx + 14.0, coly, 2.9), col_c, ortho=4.8)
    tiles["col_back"] = shot("col_back", 384, 512, (colx, coly + 14.0, 2.9), col_c, ortho=4.8)
    tiles["col_three"] = shot("col_three", 384, 512, (colx + 6.5, coly - 7.5, 4.0), col_c, lens=60.0)
    for o in cols:
        o.hide_render = True
    for o in shells:
        o.hide_render = False
    wc = (4.5, -1.0, 2.4)
    tiles["wall_front"] = shot("wall_front", 512, 384, (4.5, -30.0, 2.5), wc, ortho=11.8)
    tiles["wall_three"] = shot("wall_three", 512, 384, (13.5, -13.0, 4.6), wc, lens=44.0, world=((0.45, 0.60, 0.80), 1.0))
    tiles["wall_end"] = shot("wall_end", 512, 384, (-16.0, -1.0, 2.8), (4.5, -1.0, 2.8), ortho=8.3)
    tiles["wall_top"] = shot("wall_top", 512, 384, (4.5, -7.0, 34.0), (4.5, -1.0, 0), ortho=11.8)
    tiles["wall_night"] = shot("wall_night", 512, 384, (9.5, -8.5, 2.2), wc, lens=36.0, world=((0.02, 0.03, 0.06), 0.35))
    tiles["wall_close"] = shot("wall_close", 512, 384, (3.2, -4.6, 1.6), (4.0, -0.6, 2.5), lens=40.0)
    result["tiles"] = tiles
    result["engine"] = eng


def main():
    if "build" in STEPS:
        build()
    else:
        # pieces are rebuilt in memory (the objects stay as they are)
        for n in fld.PIECE_NAMES:
            PIECES[n] = fld.build_piece(n)
    if "verify" in STEPS:
        verify()
    if "export" in STEPS:
        export()
    if "golden" in STEPS:
        golden()
    if "wall" in STEPS and "render" not in STEPS:
        wall(bpy.data.materials[PREFIX + "_atlas"])
    if "render" in STEPS:
        wall(bpy.data.materials[PREFIX + "_atlas"])
        render_tiles()


main()
