# Turntable renders (Blender side). Renders N frames (default 8, 45 degree steps) per row with an orthographic camera that orbits
# the model (the model is never rotated, no transforms are changed) and writes the frames to TMP;
# compose_turntable.py (system python + Pillow) then builds blender/renders/<model>_turntable.png.
#   import turntable as tt; tt.run("rukia_sealed", rows)
#   rows: [dict(label, objs=[names], z0, z1, w, h, cx=0.0, cy=0.0)]  (z0..z1 = visible height in metres, w x h = frame px)
import bpy, math, os, json
from mathutils import Vector, Matrix
import bb_common as bb

TMP = bb.TMP


def _rig(centre, dist=3.0):
    sc = bpy.context.scene
    piv = bpy.data.objects.new("tt_pivot", None)
    sc.collection.objects.link(piv)
    piv.location = centre
    cd = bpy.data.cameras.new("tt_cam")
    cd.type = 'ORTHO'
    cd.sensor_fit = 'VERTICAL'
    cd.clip_start, cd.clip_end = 0.05, 20.0
    cam = bpy.data.objects.new("tt_cam", cd)
    sc.collection.objects.link(cam)
    cam.parent = piv
    cam.location = (0, -dist, 0)
    cam.rotation_euler = (math.pi / 2, 0, 0)      # looks along +Y of the pivot, up = +Z
    sc.camera = cam
    lights = []
    for nm, rot, e, col in (("key", (math.radians(55), 0, math.radians(-35)), 3.2, (1.0, 0.97, 0.92)),
                            ("fill", (math.radians(70), 0, math.radians(140)), 1.4, (0.85, 0.9, 1.0)),
                            ("rim", (math.radians(110), 0, math.radians(20)), 1.2, (1, 1, 1))):
        ld = bpy.data.lights.new("tt_" + nm, 'SUN')
        ld.energy = e
        ld.color = col
        lo = bpy.data.objects.new("tt_" + nm, ld)
        sc.collection.objects.link(lo)
        lo.parent = piv
        lo.rotation_euler = rot
        lights.append(lo)
    return piv, cam, lights


def _cleanup(piv, cam, lights):
    for o in [cam] + lights + [piv]:
        d = o.data
        bpy.data.objects.remove(o, do_unlink=True)
        if d is not None:
            (bpy.data.cameras if d.rna_type.identifier == 'Camera' else bpy.data.lights).remove(d)


def run(model, rows, frames=8, engine='BLENDER_EEVEE'):
    sc = bpy.context.scene
    old = (sc.render.engine, sc.render.resolution_x, sc.render.resolution_y, sc.camera, sc.world, sc.render.filepath)
    sc.render.engine = engine
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGB'
    sc.render.film_transparent = False
    sc.render.resolution_percentage = 100
    try:
        sc.view_settings.view_transform = 'Standard'
    except Exception:
        pass
    wd = bpy.data.worlds.get("tt_world") or bpy.data.worlds.new("tt_world")
    wd.use_nodes = True
    bgn = next(n for n in wd.node_tree.nodes if n.type == 'BACKGROUND')
    bgn.inputs["Color"].default_value = (0.20, 0.21, 0.23, 1.0)
    bgn.inputs["Strength"].default_value = 0.8
    sc.world = wd
    mesh_objs = [o for o in bpy.data.objects if o.type == 'MESH']
    vis_before = {o.name: (o.hide_render, o.hide_viewport, o.hide_get()) for o in mesh_objs}
    man = []
    os.makedirs(TMP, exist_ok=True)
    for ri, row in enumerate(rows):
        for o in mesh_objs:
            o.hide_set(False)
            o.hide_viewport = False
            o.hide_render = o.name not in row["objs"]
        cx, cy = row.get("cx", 0.0), row.get("cy", 0.0)
        z0, z1 = row["z0"], row["z1"]
        piv, cam, lights = _rig(Vector((cx, cy, (z0 + z1) / 2)))
        cam.data.ortho_scale = (z1 - z0) * 1.04
        sc.render.resolution_x, sc.render.resolution_y = row["w"], row["h"]
        files = []
        for i in range(frames):
            piv.rotation_euler = (0, 0, math.radians(360.0 * i / frames))
            bpy.context.view_layer.update()
            path = os.path.join(TMP, "tt_%s_r%d_%d.png" % (model, ri, i))
            sc.render.filepath = path
            bpy.ops.render.render(write_still=True)
            files.append(path)
        _cleanup(piv, cam, lights)
        man.append({"label": row["label"], "w": row["w"], "h": row["h"], "files": files})
    for o in mesh_objs:
        hr, hv, hs = vis_before[o.name]
        o.hide_render, o.hide_viewport = hr, hv
        o.hide_set(hs)
    sc.render.engine, sc.render.resolution_x, sc.render.resolution_y, sc.camera, sc.world, sc.render.filepath = old
    with open(os.path.join(TMP, "tt_%s_manifest.json" % model), "w") as f:
        json.dump({"model": model, "frames": frames, "rows": man}, f, indent=1)
    return man
