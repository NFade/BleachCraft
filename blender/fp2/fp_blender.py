"""Blender (headless) renderer of first person pose specs:
  blender --background --factory-startup --python fp_blender.py -- specs.json outdir
Camera = Minecraft first person camera (vertical FOV 70 by default), objects placed in camera space exactly like the game."""
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import fp_math as fm  # noqa: E402
import fp_scene  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
specs = json.load(open(args[0]))
outdir = args[1]
os.makedirs(outdir, exist_ok=True)


def load_obj(path, grip_y):
    """OBJ (y up) -> vertices in item model space (obj - grip_obj + 0.5), faces, uvs per face corner."""
    verts, uvs, faces = [], [], []
    for line in open(path):
        if line.startswith("v "):
            verts.append([float(x) for x in line.split()[1:4]])
        elif line.startswith("vt "):
            uvs.append([float(x) for x in line.split()[1:3]])
        elif line.startswith("f "):
            idx = []
            for tok in line.split()[1:]:
                p = tok.split("/")
                idx.append((int(p[0]) - 1, int(p[1]) - 1 if len(p) > 1 and p[1] else 0))
            faces.append(idx)
    grip = np.array([0.0, grip_y, 0.0])
    v = np.array(verts) - grip + 0.5
    return v, uvs, faces


def mesh_object(name, v, uvs, faces, mat):
    me = bpy.data.meshes.new(name)
    vi = [[a for a, _ in f] for f in faces]
    me.from_pydata([tuple(x) for x in v], [], vi)
    me.update()
    uv_layer = me.uv_layers.new(name="uv")
    k = 0
    for f in faces:
        for _, t in f:
            uv_layer.data[k].uv = uvs[t] if uvs else (0, 0)
            k += 1
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def tex_material(name, png):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(png)
    tex.interpolation = "Closest"
    em = nt.nodes.new("ShaderNodeEmission")
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(tex.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    return mat


def flat_material(name, rgb):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*rgb, 1)
    return mat


def box_object(name, lo, hi, mat):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    f = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (2, 3, 7, 6), (1, 2, 6, 5), (0, 4, 7, 3)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(v, [], f)
    me.update()
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def setup_world(sc, fov, w, h, bg):
    sc.render.engine = "BLENDER_WORKBENCH"
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = 100
    sc.display.shading.light = "STUDIO"
    sc.display.shading.color_type = "TEXTURE"
    sc.display.shading.show_specular_highlight = False
    sc.world = bpy.data.worlds.new("w")
    sc.world.color = bg
    sc.view_settings.view_transform = "Standard"
    cam = bpy.data.cameras.new("cam")
    cam.sensor_fit = "VERTICAL"
    cam.angle_y = math.radians(fov)
    cam.clip_start = 0.05
    cam.clip_end = 100
    co = bpy.data.objects.new("cam", cam)
    sc.collection.objects.link(co)
    co.matrix_world = Matrix.Identity(4)
    sc.camera = co


def horizon(sc):
    mat = flat_material("ground", (0.20, 0.30, 0.12))
    me = bpy.data.meshes.new("g")
    s = 80
    me.from_pydata([(-s, -1.62, 1), (s, -1.62, 1), (s, -1.62, -s), (-s, -1.62, -s)], [], [(0, 3, 2, 1)])
    me.update()
    me.materials.append(mat)
    ob = bpy.data.objects.new("ground", me)
    sc.collection.objects.link(ob)


def render_spec(spec, path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    setup_world(sc, spec.get("fov", 70), spec.get("w", 1280), spec.get("h", 720), (0.55, 0.70, 1.0))
    horizon(sc)
    char = spec.get("char", "rukia")
    ex = os.path.join(fm.EXPORT, f"{char}_sealed")
    tex = tex_material("sword", os.path.join(ex, f"{char}_sealed_diffuse.png"))
    meshes = {}
    for kind, f in (("drawn", f"{char}_sealed_drawn.obj"), ("sheathed", f"{char}_sealed_sheathed.obj"), ("saya", f"{char}_sealed_saya.obj")):
        meshes[kind] = load_obj(os.path.join(ex, f), 0.19)
    skin = flat_material("skin", (0.88, 0.62, 0.50))
    sleeve = flat_material("sleeve", (0.82, 0.5, 0.2))
    scn = fp_scene.build(spec)
    slim = spec.get("slim", False)
    for it in scn["items"]:
        if it["kind"] in ("sword", "saya"):
            kind = it["mesh"] if it["kind"] == "sword" else "saya"
            v, uvs, faces = meshes[kind]
            ob = mesh_object(kind, v, uvs, faces, tex)
            ob.matrix_world = Matrix(it["m"].tolist())
        elif it["kind"] == "arm":
            lo, hi, fist = fm.arm_geom(it["left"], slim)
            split = hi[1] - 4.0 / 16.0
            a = box_object("arm_s", lo, (hi[0], split, hi[2]), sleeve)
            b = box_object("arm_h", (lo[0], split, lo[2]), hi, skin)
            for ob in (a, b):
                ob.matrix_world = Matrix(it["m"].tolist())
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)


for name, spec in specs.items():
    render_spec(spec, os.path.join(outdir, name + ".png"))
    print("rendered", name)
