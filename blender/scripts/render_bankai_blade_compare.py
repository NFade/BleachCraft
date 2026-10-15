# R11 T13: Workbench texture renders of the old (v1) and new (v2) giant blade, one PNG per view, for the comparison sheet
# (design/results/bankai_blade_new_compare.png, composed by tools/compose_bankai_blade_compare.py with system python, `python -I`).
#   exec(open(r"D:\MineBleach\blender\scripts\render_bankai_blade_compare.py").read())
import bpy, math, os
from mathutils import Vector

OUT = globals().get("OUT_DIR", r"C:\Users\Haru\AppData\Local\Temp\bankai_cmp")
os.makedirs(OUT, exist_ok=True)
sc = bpy.context.scene
cam_data = bpy.data.cameras.new("cmp_cam")
cam = bpy.data.objects.new("cmp_cam", cam_data)
sc.collection.objects.link(cam)
cam_data.type = 'ORTHO'
sc.camera = cam
sc.render.engine = 'BLENDER_WORKBENCH'
sc.display.shading.light = 'FLAT'
sc.display.shading.color_type = 'TEXTURE'
sc.display.shading.show_object_outline = True
sc.render.film_transparent = False
bpy.context.scene.world = bpy.context.scene.world or bpy.data.worlds.new("w")
sc.world.color = (0.10, 0.10, 0.12)

vis0 = {o.name: (o.hide_get(), o.hide_render) for o in bpy.data.objects}


def only(names):
    for o in bpy.data.objects:
        if o.type == 'MESH':
            on = o.name in names
            o.hide_set(not on)
            o.hide_render = not on


def shot(tag, names, center, height, aspect, direction, size_px=900):
    only(names)
    sc.render.resolution_x = int(size_px * aspect)
    sc.render.resolution_y = size_px
    cam_data.ortho_scale = height
    d = Vector(direction).normalized()
    cam.location = Vector(center) + d * 20.0
    cam.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
    sc.render.filepath = os.path.join(OUT, tag + ".png")
    bpy.ops.render.render(write_still=True)


for nm, h in (("old", 8.4), ("new", 5.8)):
    n = "byakuya_bankai_blade_old" if nm == "old" else "byakuya_bankai_blade"
    for oo in (bpy.data.objects[n],):
        oo.hide_set(False)
    zc = (4.0 if nm == "old" else 2.75)
    shot(nm + "_side", [n], (0, 0.15, zc), h, 0.45, (1, 0, 0))      # seen from +X: width across, edge -Y to the left
    shot(nm + "_front", [n], (0, 0.15, zc), h, 0.45, (0, -1, 0))     # seen from -Y (edge on)
    tipz = 8.0 if nm == "old" else 5.5
    shot(nm + "_tip", [n], (0, 0.15, tipz - 0.8), 1.8, 1.0, (1, 0, 0))   # tip close-up
    shot(nm + "_tip_front", [n], (0, 0.15, tipz - 0.8), 1.8, 1.0, (0.6, -1, 0.15))

for name, (h, hr) in vis0.items():
    o = bpy.data.objects.get(name)
    if o:
        o.hide_set(h)
        o.hide_render = hr
bpy.data.objects.remove(cam, do_unlink=True)
bpy.data.cameras.remove(cam_data)
print("rendered to", OUT, sorted(os.listdir(OUT)))
