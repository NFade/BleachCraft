# Preview renders for the Gin / Uryu models (Blender side, orthographic, EEVEE). Writes frames to TMP; gu_sheet.py (system python) composes the sheets.
#   run(jobs): jobs = [dict(name, objs=[...], views=[(label, yaw_deg, elev_deg)], cx, cy, cz, scale, w, h)]
import bpy, math, os
from mathutils import Vector

TMP = os.environ.get("GU_TMP", r"C:\Users\efeki\AppData\Local\Temp\claude\D--MineBleach\9d6863af-1732-404d-84ac-196afa535bd7\scratchpad")


def _rig(centre, dist=3.0):
    sc = bpy.context.scene
    piv = bpy.data.objects.new("gu_pivot", None)
    sc.collection.objects.link(piv)
    piv.location = centre
    cd = bpy.data.cameras.new("gu_cam")
    cd.type = 'ORTHO'
    cd.sensor_fit = 'VERTICAL'
    cd.clip_start, cd.clip_end = 0.05, 20.0
    cam = bpy.data.objects.new("gu_cam", cd)
    sc.collection.objects.link(cam)
    cam.parent = piv
    cam.location = (0, -dist, 0)
    cam.rotation_euler = (math.pi / 2, 0, 0)
    sc.camera = cam
    lights = []
    for nm, rot, e, col in (("key", (math.radians(55), 0, math.radians(-35)), 3.2, (1.0, 0.97, 0.92)),
                            ("fill", (math.radians(70), 0, math.radians(140)), 1.4, (0.85, 0.9, 1.0)),
                            ("rim", (math.radians(110), 0, math.radians(20)), 1.2, (1, 1, 1))):
        ld = bpy.data.lights.new("gu_" + nm, 'SUN')
        ld.energy = e
        ld.color = col
        lo = bpy.data.objects.new("gu_" + nm, ld)
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


def run(jobs, engine='BLENDER_EEVEE'):
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
    wd = bpy.data.worlds.get("gu_world") or bpy.data.worlds.new("gu_world")
    wd.use_nodes = True
    bgn = next(n for n in wd.node_tree.nodes if n.type == 'BACKGROUND')
    bgn.inputs["Color"].default_value = (0.20, 0.21, 0.23, 1.0)
    bgn.inputs["Strength"].default_value = 0.8
    sc.world = wd
    meshes = [o for o in bpy.data.objects if o.type == 'MESH']
    before = {o.name: (o.hide_render, o.hide_viewport, o.hide_get()) for o in meshes}
    os.makedirs(TMP, exist_ok=True)
    out = []
    for job in jobs:
        for o in meshes:
            o.hide_set(False)
            o.hide_viewport = False
            o.hide_render = o.name not in job["objs"]
        piv, cam, lights = _rig(Vector((job.get("cx", 0.0), job.get("cy", 0.0), job.get("cz", 0.0))))
        cam.data.ortho_scale = job["scale"]
        sc.render.resolution_x, sc.render.resolution_y = job["w"], job["h"]
        files = []
        for label, yaw, elev in job["views"]:
            piv.rotation_euler = (-math.radians(elev), 0, math.radians(yaw))
            bpy.context.view_layer.update()
            path = os.path.join(TMP, "gu_%s_%s.png" % (job["name"], label))
            sc.render.filepath = path
            bpy.ops.render.render(write_still=True)
            files.append(path)
        _cleanup(piv, cam, lights)
        out.append({"name": job["name"], "files": files})
    for o in meshes:
        hr, hv, hs = before[o.name]
        o.hide_render, o.hide_viewport = hr, hv
        o.hide_set(hs)
    sc.render.engine, sc.render.resolution_x, sc.render.resolution_y, sc.camera, sc.world, sc.render.filepath = old
    return out
