# Spike 2b asset builder. Run in Blender 5.2.2 through the MCP (execute_blender_code).
# Builds D:\MineBleach\blender\scenes\spike.blend: spike_cube, spike_cube_alt, spike_strip_01/02,
# empties grip_hand / tip / ribbon_root, 64x64 diffuse + emissive textures (ADR section 7).
import bpy, bmesh, numpy as np, os
from mathutils import Vector

ROOT = r"D:\MineBleach\blender"
RAW = os.path.join(ROOT, "scenes", "spike_raw")
os.makedirs(RAW, exist_ok=True)
os.makedirs(os.path.join(ROOT, "export", "spike"), exist_ok=True)

# ---------- clean scene (no factory reset: keeps the MCP add-on connection alive) ----------
if bpy.context.object and bpy.context.object.mode != 'OBJECT':
    bpy.ops.object.mode_set(mode='OBJECT')
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.cameras, bpy.data.lights, bpy.data.images,
             bpy.data.collections):
    for d in list(coll):
        coll.remove(d)
bpy.context.scene.name = "SpikeScene"

# ---------- texture painting (top-down pixel coords, row 0 = top of the PNG) ----------
W = H = 64
FONT = {
    'R': ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    'L': ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    'B': ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    'E': ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    'T': ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
}


def fill(img, x0, y0, x1, y1, col):
    img[y0:y1, x0:x1, :3] = col
    img[y0:y1, x0:x1, 3] = 255


def letter(img, x0, y0, ch, col):
    for ry, row in enumerate(FONT[ch]):
        for rx, c in enumerate(row):
            if c == '#':
                px = x0 + 3 + rx * 2
                py = y0 + 1 + ry * 2
                fill(img, px, py, px + 2, py + 2, col)


# Regions (x0, y0, x1, y1) in top-down pixels
REG = {
    'px': (0, 0, 16, 16),      # +X "R"
    'nx': (16, 0, 32, 16),     # -X "L"
    'py': (32, 0, 48, 16),     # +Y "B"
    'ny': (48, 0, 64, 16),     # -Y "E" (edge)
    'pz': (0, 16, 16, 32),     # +Z "T"
    'nz': (16, 16, 32, 32),    # -Z bottom (plain)
    'b_ny': (32, 16, 36, 52),  # bar -Y red (edge marker)
    'b_py': (36, 16, 40, 52),  # bar +Y
    'b_px': (40, 16, 44, 52),  # bar +X
    'b_nx': (44, 16, 48, 52),  # bar -X
    'b_cap': (48, 16, 52, 20),  # bar +Z cap (2 triangles)
    's1': (52, 16, 60, 24),    # strip segment 1
    's2': (52, 24, 60, 32),    # strip segment 2
}
EMIT_ROWS = (16, 22)  # top 6 px of the bar strips = top 0.10 m of 0.60 m


def paint_diffuse(invert=False):
    img = np.zeros((H, W, 4), dtype=np.uint8)
    img[..., 3] = 255

    def c(rgb):
        return tuple(255 - v for v in rgb) if invert else rgb
    for key, bg, fg, ch in (('px', (30, 70, 210), (255, 255, 255), 'R'),
                            ('nx', (30, 150, 60), (255, 255, 255), 'L'),
                            ('py', (230, 130, 20), (255, 255, 255), 'B'),
                            ('ny', (240, 220, 40), (0, 0, 0), 'E'),
                            ('pz', (20, 160, 170), (255, 255, 255), 'T')):
        x0, y0, x1, y1 = REG[key]
        fill(img, x0, y0, x1, y1, c(bg))
        letter(img, x0, y0, ch, fg)
    fill(img, *REG['nz'], c((70, 70, 70)))
    fill(img, *REG['b_ny'], c((230, 20, 20)))
    fill(img, *REG['b_py'], c((120, 160, 255)))
    fill(img, *REG['b_px'], c((150, 150, 150)))
    fill(img, *REG['b_nx'], c((100, 100, 100)))
    fill(img, *REG['b_cap'], c((255, 255, 255)))
    fill(img, *REG['s1'], c((220, 40, 200)))
    fill(img, *REG['s2'], c((40, 220, 230)))
    return img


def paint_emissive_rgb():
    img = np.zeros((H, W, 4), dtype=np.uint8)
    img[..., 3] = 255  # black RGB; converted to white-RGB + A=max(R,G,B) by spike_emissive_convert.py
    for key in ('b_ny', 'b_py', 'b_px', 'b_nx'):
        x0, y0, x1, y1 = REG[key]
        fill(img, x0, EMIT_ROWS[0], x1, EMIT_ROWS[1], (255, 170, 40))
    fill(img, *REG['b_cap'], (255, 170, 40))
    return img


def to_bpy_image(name, arr, path):
    im = bpy.data.images.new(name, W, H, alpha=True)
    im.colorspace_settings.name = 'sRGB'
    flat = np.flipud(arr).astype(np.float32) / 255.0  # bpy row 0 = bottom
    im.pixels.foreach_set(flat.ravel())
    im.filepath_raw = path
    im.file_format = 'PNG'
    im.save()
    return im


img_d = to_bpy_image("spike_diffuse", paint_diffuse(), os.path.join(ROOT, "export", "spike", "spike_diffuse.png"))
img_a = to_bpy_image("spike_alt_diffuse", paint_diffuse(True), os.path.join(ROOT, "export", "spike", "spike_alt_diffuse.png"))
img_e = to_bpy_image("spike_emissive_rgb", paint_emissive_rgb(), os.path.join(RAW, "spike_emissive_rgb.png"))


# ---------- geometry ----------
def uv_of(region, a_sign, b_sign):
    """Region corner for a face-local (right, up) sign pair; image v = 1 - y/H (bpy UV origin bottom-left)."""
    x0, y0, x1, y1 = region
    x = x1 if a_sign > 0 else x0
    y = y0 if b_sign > 0 else y1  # up = smaller y (top of image)
    return (x / W, 1.0 - y / H)


def add_face(bm, uvl, verts, region, up):
    """verts: 4 Vectors, CCW seen from outside. Texture upright toward `up`, unmirrored seen from outside."""
    f = bm.faces.new([bm.verts.new(v) for v in verts])
    n = (verts[1] - verts[0]).cross(verts[2] - verts[1]).normalized()
    r = up.cross(n).normalized()  # screen-right for a viewer looking at the face from outside
    ctr = sum(verts, Vector()) / len(verts)
    for loop, v in zip(f.loops, verts):
        d = v - ctr
        loop[uvl].uv = uv_of(region, 1 if d.dot(r) > 0 else -1, 1 if d.dot(up) > 0 else -1)
    return f


def box_faces(lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    V = lambda x, y, z: Vector((x, y, z))
    return {  # CCW from outside
        'px': [V(x1, y0, z0), V(x1, y1, z0), V(x1, y1, z1), V(x1, y0, z1)],
        'nx': [V(x0, y1, z0), V(x0, y0, z0), V(x0, y0, z1), V(x0, y1, z1)],
        'py': [V(x1, y1, z0), V(x0, y1, z0), V(x0, y1, z1), V(x1, y1, z1)],
        'ny': [V(x0, y0, z0), V(x1, y0, z0), V(x1, y0, z1), V(x0, y0, z1)],
        'pz': [V(x0, y0, z1), V(x1, y0, z1), V(x1, y1, z1), V(x0, y1, z1)],
        'nz': [V(x0, y1, z0), V(x1, y1, z0), V(x1, y0, z0), V(x0, y0, z0)],
    }


def build_spike_mesh(name):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    Z = Vector((0, 0, 1))
    Y = Vector((0, 1, 0))
    cube = box_faces((-0.1, -0.1, 0.0), (0.1, 0.1, 0.2))
    for k in ('px', 'nx', 'py', 'ny'):
        add_face(bm, uvl, cube[k], REG[k], Z)
    add_face(bm, uvl, cube['pz'], REG['pz'], Y)
    add_face(bm, uvl, cube['nz'], REG['nz'], Y)
    bar = box_faces((-0.02, -0.02, 0.2), (0.02, 0.02, 0.8))
    for k in ('px', 'nx', 'py', 'ny'):
        add_face(bm, uvl, bar[k], REG['b_' + k], Z)
    # +Z cap as 2 TRIANGLES (tris and quads both present)
    cap = bar['pz']
    ctr = sum(cap, Vector()) / 4.0
    r = Y.cross(Z).normalized()
    for tri in ((cap[0], cap[1], cap[2]), (cap[0], cap[2], cap[3])):
        f = bm.faces.new([bm.verts.new(v) for v in tri])
        for loop, v in zip(f.loops, tri):
            d = v - ctr
            loop[uvl].uv = uv_of(REG['b_cap'], 1 if d.dot(r) > 0 else -1, 1 if d.dot(Y) > 0 else -1)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)  # merge coincident positions (UV loops stay per face)
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = False
    return me


def build_strip_mesh(name, height, region):
    """Double-sided quad in the XZ plane, local to its hinge at the TOP edge (hangs along -Z)."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    V = lambda x, z: Vector((x, 0, z))
    front = [V(-0.025, -height), V(0.025, -height), V(0.025, 0), V(-0.025, 0)]  # normal -Y? verified below
    back = [V(0.025, -height), V(-0.025, -height), V(-0.025, 0), V(0.025, 0)]
    for vs in (front, back):
        add_face(bm, uvl, vs, region, Vector((0, 0, 1)))
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    return me


def mat_for(name, img):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    return m


col = bpy.data.collections.new("spike")
bpy.context.scene.collection.children.link(col)


def add_obj(name, me, loc=(0, 0, 0), mat=None):
    o = bpy.data.objects.new(name, me)
    col.objects.link(o)
    o.location = loc
    if mat:
        o.data.materials.append(mat)
    return o


m_d = mat_for("spike_diffuse_mat", img_d)
m_a = mat_for("spike_alt_mat", img_a)
cube = add_obj("spike_cube", build_spike_mesh("spike_cube"), mat=m_d)
alt = add_obj("spike_cube_alt", build_spike_mesh("spike_cube_alt"), mat=m_a)
alt.scale = (0.7, 0.7, 0.7)
s1 = add_obj("spike_strip_01", build_strip_mesh("spike_strip_01", 0.12, REG['s1']), loc=(0, 0, -0.01), mat=m_d)
s2 = add_obj("spike_strip_02", build_strip_mesh("spike_strip_02", 0.12, REG['s2']), loc=(0, 0, -0.13), mat=m_d)
for name, loc in (("grip_hand", (0, 0, 0.19)), ("tip", (0, 0, 0.80)), ("ribbon_root", (0, 0, -0.01))):
    e = bpy.data.objects.new(name, None)
    e.empty_display_type = 'PLAIN_AXES'
    e.empty_display_size = 0.03
    col.objects.link(e)
    e.location = loc

# apply rotation + scale on every mesh object (ADR 1: rotation 0, scale 1 before export)
bpy.ops.object.select_all(action='DESELECT')
for o in (cube, alt, s1, s2):
    o.select_set(True)
bpy.context.view_layer.objects.active = cube
bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)

os.makedirs(os.path.join(ROOT, "scenes"), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(ROOT, "scenes", "spike.blend"))
result = {"objects": [(o.name, tuple(round(v, 4) for v in o.location)) for o in bpy.data.objects],
          "polys": {o.name: len(o.data.polygons) for o in (cube, alt, s1, s2)}}
