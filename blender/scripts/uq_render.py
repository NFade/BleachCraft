# Preview renders of the Quincy effect set `uryu_fx` (EEVEE, emissive look on a dark night with a compositor bloom), two halves in one file:
#   Blender side  (exec inside Blender after uq_build.py):   import uq_render as R; R.render_jobs(ROOT, names=None)   -> frames into TMP
#   system python (Pillow):   python -I blender/scripts/uq_render.py sheets <ROOT> <TMP>               -> composes blender/renders/uryu_fx/*.png
# The preview scene (mannequin = a block Steve at the 0.9375 model scale, a night world, lights, camera) is built and removed per call; the exported meshes
# are only translated for the costume jobs and put back.
import os, sys, math

try:
    import bpy
    from mathutils import Vector
except ImportError:        # system python: only the sheet composer is used
    bpy = None

HERE = os.path.dirname(os.path.abspath(__file__))
TMP = os.environ.get("UQ_TMP", r"C:\Users\efeki\AppData\Local\Temp\claude\D--MineBleach\9d6863af-1732-404d-84ac-196afa535bd7\scratchpad\uq")

WINGS = ["uryu_wing_l", "uryu_wing_r"]
ROOT_OFF = (0.0, 0.15, 1.35)          # shoulder root in the Blender frame of the mannequin (x sideways, y behind, z height)
ALL = ["uryu_wing_l", "uryu_wing_r", "uryu_flechette", "uryu_needle", "uryu_star_head", "uryu_bracer", "uryu_boot", "uryu_bow",
       "uryu_bow_flex_1", "uryu_bow_flex_2", "uryu_bow_flex_3", "uryu_arrow", "uryu_seele_long", "uryu_pentagon_edge"]

# job: name, views = [(label, yaw_deg, elev_deg)], scale (ortho height in m) or dist (perspective), centre (cx, cy, cz), size (w, h), layout ("plain" | "body")
JOBS = [
    # the wings on the mannequin, as the contract asks: front / back / top / side + a close-up of one wing
    dict(name="wing_back", layout="body", objs=WINGS, views=[("v", 180, 4)], scale=4.2, c=(0, 0.1, 1.0), size=(1100, 800)),
    dict(name="wing_front", layout="body", objs=WINGS, views=[("v", 0, 4)], scale=4.2, c=(0, 0.1, 1.0), size=(1100, 800)),
    dict(name="wing_top", layout="body", objs=WINGS, views=[("v", 180, 90)], scale=4.4, c=(0, 0.1, 1.2), size=(1100, 700)),
    dict(name="wing_side", layout="body", objs=WINGS, views=[("v", 90, 4)], scale=3.6, c=(0, 0.1, 1.0), size=(700, 800)),
    dict(name="wing_one_back", layout="plain_wing", objs=["uryu_wing_l"], views=[("v", 180, 0)], scale=2.4, c=(0.9, 0.2, 0.55), size=(1100, 700)),
    dict(name="wing_one_front", layout="plain_wing", objs=["uryu_wing_l"], views=[("v", 0, 0)], scale=2.4, c=(0.9, 0.2, 0.55), size=(1100, 700)),
    dict(name="wing_one_iso", layout="plain_wing", objs=["uryu_wing_l"], views=[("v", 150, 28)], scale=2.6, c=(0.9, 0.2, 0.5), size=(1100, 800)),
    # the whole costume: wings, bands, boots, flakes
    dict(name="costume", layout="costume", objs=WINGS + ["uryu_bracer", "uryu_boot", "uryu_flechette"], persp=True, dist=9.5,
         views=[("front", 0, 6), ("back", 180, 6), ("q3", 140, 12)], c=(0, 0.1, 1.0), size=(900, 900)),
    # the other meshes
    dict(name="bow", layout="plain", objs=["uryu_bow"], persp=False, scale=1.5, c=(0, 0.0, 0.0), size=(560, 760),
         views=[("side", 90, 0), ("front", 0, 0), ("q3", 40, 14), ("rest", 90, 0)]),
    dict(name="bow_rest_close", layout="plain", objs=["uryu_bow"], scale=0.32, c=(0, -0.04, 0.03), size=(640, 640), views=[("side", 90, 0), ("q3", 50, 25), ("top", 0, 80)]),
    dict(name="bow_flex", layout="plain", objs=["uryu_bow"], scale=1.5, c=(0, 0.04, 0.0), size=(420, 760), views=[("0", 90, 0, ["uryu_bow"]), ("1", 90, 0, ["uryu_bow_flex_1"]),
         ("2", 90, 0, ["uryu_bow_flex_2"]), ("3", 90, 0, ["uryu_bow_flex_3"])]),
    dict(name="arrow", layout="plain", objs=["uryu_arrow"], scale=0.58, c=(0, 0, 0.26), size=(900, 380), views=[("side", 90, 0), ("top", 0, 90)]),
    dict(name="arrow_head", layout="plain", objs=["uryu_arrow"], scale=0.16, c=(0, 0, 0.45), size=(640, 640), views=[("side", 90, 0), ("q3", 40, 20), ("front", 0, 60)]),
    dict(name="arrow_tail", layout="plain", objs=["uryu_arrow"], scale=0.2, c=(0, 0, 0.09), size=(640, 640), views=[("side", 90, 0), ("q3", 40, 20), ("back", 180, 70)]),
    dict(name="star_head", layout="plain", objs=["uryu_star_head"], persp=False, scale=2.0, c=(0, 0, 0), size=(640, 640),
         views=[("side", 90, 0), ("top", 0, 90), ("q3", 40, 25), ("front", 0, 0)]),
    dict(name="needle", layout="plain", objs=["uryu_needle"], scale=1.5, c=(0, 0, 0.6), size=(900, 380), views=[("side", 90, 0), ("front", 0, 0), ("q3", 40, 30)]),
    dict(name="flechette", layout="plain", objs=["uryu_flechette"], scale=0.55, c=(0, 0, 0), size=(640, 640), views=[("front", 0, 0), ("q3", 40, 30), ("side", 90, 0)]),
    dict(name="bracer", layout="plain", objs=["uryu_bracer"], scale=0.5, c=(0, 0, 0), size=(640, 640), views=[("front", 0, 0), ("q3", 40, 25), ("top", 0, 85)]),
    dict(name="boot", layout="plain", objs=["uryu_boot"], scale=0.95, c=(0, -0.08, 0.24), size=(640, 760), views=[("front", 0, 0), ("side", 90, 0), ("q3", 40, 20)]),
    dict(name="seele_long", layout="plain", objs=["uryu_seele_long"], scale=1.15, c=(0, 0, 0.45), size=(900, 380), views=[("side", 90, 0), ("front", 0, 0), ("q3", 40, 30)]),
    dict(name="pentagon_edge", layout="plain", objs=["uryu_pentagon_edge"], scale=1.3, c=(0.5, 0, 0), size=(900, 320), views=[("top", 0, 90), ("q3", 20, 35)]),
]
# turntable sheets: one row of yaw angles per mesh (name -> (scale, centre, elev))
TURN = {
    "uryu_wing_l": (2.4, (0.9, 0.2, 0.55), 10), "uryu_flechette": (0.55, (0, 0, 0), 35), "uryu_needle": (1.5, (0, 0, 0.6), 20),
    "uryu_star_head": (2.0, (0, 0, 0), 20), "uryu_bracer": (0.5, (0, 0, 0), 20), "uryu_boot": (0.95, (0, -0.08, 0.24), 15), "uryu_bow": (1.5, (0, 0, 0), 10),
    "uryu_arrow": (0.58, (0, 0, 0.26), 15), "uryu_seele_long": (1.15, (0, 0, 0.45), 20), "uryu_pentagon_edge": (1.3, (0.5, 0, 0), 40),
}
TURN_YAWS = [0, 45, 90, 135, 180, 225, 270, 315]


# =================================================================================== Blender side
def _srgb(h):
    h = h.lstrip('#')
    c = [int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    return [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c]


def _box(name, lo, hi, mat, coll):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    f = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(v, [], f)
    me.update()
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    return ob


def preview_material(root):
    exp = os.path.join(root, "export", "uryu_fx")
    mat = bpy.data.materials.get("pv_uryu_fx") or bpy.data.materials.new("pv_uryu_fx")
    mat.use_nodes = True
    mat.surface_render_method = 'BLENDED'
    mat.use_backface_culling = False
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")

    def img(png):
        old = bpy.data.images.get(os.path.basename(png))
        if old:
            bpy.data.images.remove(old)
        im = bpy.data.images.load(png)
        im.alpha_mode = 'STRAIGHT'
        n = nt.nodes.new("ShaderNodeTexImage")
        n.image = im
        n.interpolation = 'Closest'
        return n
    d = img(os.path.join(exp, "uryu_fx_diffuse.png"))
    e = img(os.path.join(exp, "uryu_fx_emissive.png"))
    emi = nt.nodes.new("ShaderNodeEmission")
    mul = nt.nodes.new("ShaderNodeMath")
    mul.operation = 'MULTIPLY'
    mul.inputs[1].default_value = 1.7
    nt.links.new(d.outputs["Color"], emi.inputs["Color"])
    nt.links.new(e.outputs["Alpha"], mul.inputs[0])
    nt.links.new(mul.outputs["Value"], emi.inputs["Strength"])
    bs = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(d.outputs["Color"], bs.inputs["Base Color"])
    bs.inputs["Roughness"].default_value = 0.7
    add = nt.nodes.new("ShaderNodeAddShader")
    nt.links.new(bs.outputs["BSDF"], add.inputs[0])
    nt.links.new(emi.outputs["Emission"], add.inputs[1])
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(d.outputs["Alpha"], mix.inputs["Fac"])
    nt.links.new(tr.outputs["BSDF"], mix.inputs[1])
    nt.links.new(add.outputs["Shader"], mix.inputs[2])
    nt.links.new(mix.outputs["Shader"], out.inputs["Surface"])
    return mat


def setup_compositor(sc, strength=0.7):
    ng = bpy.data.node_groups.get("pv_comp") or bpy.data.node_groups.new("pv_comp", "CompositorNodeTree")
    for n in list(ng.nodes):
        ng.nodes.remove(n)
    for it in list(ng.interface.items_tree):
        ng.interface.remove(it)
    ng.interface.new_socket("Image", in_out='OUTPUT', socket_type='NodeSocketColor')
    rl = ng.nodes.new("CompositorNodeRLayers")
    gl = ng.nodes.new("CompositorNodeGlare")
    gl.inputs["Type"].default_value = 'Bloom'
    gl.inputs["Threshold"].default_value = 0.8
    gl.inputs["Strength"].default_value = strength
    gl.inputs["Size"].default_value = 0.55
    go = ng.nodes.new("NodeGroupOutput")
    ng.links.new(rl.outputs["Image"], gl.inputs["Image"])
    ng.links.new(gl.outputs["Image"], go.inputs[0])
    sc.compositing_node_group = ng
    return ng


class Stage:
    """preview scene: mannequin, world, lights, camera; removed again by close()"""

    def __init__(self, root, bg="#050F2E"):
        self.sc = bpy.context.scene
        self.root = root
        self.coll = bpy.data.collections.new("pv")
        self.sc.collection.children.link(self.coll)
        self.pv = []
        self.old = (self.sc.render.engine, self.sc.render.resolution_x, self.sc.render.resolution_y, self.sc.camera, self.sc.world,
                    self.sc.render.filepath, self.sc.compositing_node_group)
        self.saved = {o.name: (o.location.copy(), o.hide_render, o.hide_viewport, o.hide_get()) for o in bpy.data.objects if o.type == 'MESH' and o.name.startswith("uryu_")}
        self.mat = preview_material(root)
        for o in self.saved:
            ob = bpy.data.objects[o]
            ob.data.materials.clear()
            ob.data.materials.append(self.mat)
        sc = self.sc
        sc.render.engine = 'BLENDER_EEVEE'
        sc.render.image_settings.file_format = 'PNG'
        sc.render.image_settings.color_mode = 'RGB'
        sc.render.film_transparent = False
        sc.render.resolution_percentage = 100
        try:
            sc.view_settings.view_transform = 'Standard'
            sc.eevee.taa_render_samples = 40
        except Exception:
            pass
        wd = bpy.data.worlds.get("pv_world") or bpy.data.worlds.new("pv_world")
        wd.use_nodes = True
        bgn = next(n for n in wd.node_tree.nodes if n.type == 'BACKGROUND')
        bgn.inputs["Color"].default_value = _srgb(bg) + [1.0]
        bgn.inputs["Strength"].default_value = 1.0
        sc.world = wd
        setup_compositor(sc)
        self.mats = {}
        for nm, col, rough in (("coat", "#B9C3D2", 0.8), ("skin", "#C9A58C", 0.8), ("hair", "#1B1B2A", 0.9), ("trou", "#9FAAB8", 0.8)):
            m = bpy.data.materials.get("pv_" + nm) or bpy.data.materials.new("pv_" + nm)
            m.use_nodes = True
            b = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
            b.inputs["Base Color"].default_value = _srgb(col) + [1.0]
            b.inputs["Roughness"].default_value = rough
            self.mats[nm] = m
        self.body = []

    def mannequin(self):
        if self.body:
            return
        M = self.mats
        b = self.body
        b.append(_box("pv_leg_l", (0.0, -0.117, 0.0), (0.234, 0.117, 0.70), M["trou"], self.coll))
        b.append(_box("pv_leg_r", (-0.234, -0.117, 0.0), (0.0, 0.117, 0.70), M["trou"], self.coll))
        b.append(_box("pv_torso", (-0.234, -0.117, 0.70), (0.234, 0.117, 1.40), M["coat"], self.coll))
        b.append(_box("pv_coat", (-0.25, -0.13, 0.42), (0.25, 0.13, 0.98), M["coat"], self.coll))
        b.append(_box("pv_arm_l", (0.234, -0.117, 0.70), (0.468, 0.117, 1.40), M["coat"], self.coll))
        b.append(_box("pv_arm_r", (-0.468, -0.117, 0.70), (-0.234, 0.117, 1.40), M["coat"], self.coll))
        b.append(_box("pv_head", (-0.2345, -0.2345, 1.40), (0.2345, 0.2345, 1.869), M["skin"], self.coll))
        b.append(_box("pv_hair", (-0.24, -0.15, 1.52), (0.24, 0.24, 1.88), M["hair"], self.coll))

    def rig(self, centre, persp, scale, dist):
        sc = self.sc
        piv = bpy.data.objects.new("pv_pivot", None)
        self.coll.objects.link(piv)
        piv.location = Vector(centre)
        cd = bpy.data.cameras.new("pv_cam")
        cd.clip_start, cd.clip_end = 0.05, 60.0
        cd.sensor_fit = 'VERTICAL'
        if persp:
            cd.type = 'PERSP'
            cd.angle = math.radians(28.0)
        else:
            cd.type = 'ORTHO'
            cd.ortho_scale = scale
        cam = bpy.data.objects.new("pv_cam", cd)
        self.coll.objects.link(cam)
        cam.parent = piv
        cam.location = (0, -(dist if persp else 20.0), 0)
        cam.rotation_euler = (math.pi / 2, 0, 0)
        sc.camera = cam
        lights = []
        for nm, rot, e, col in (("key", (math.radians(55), 0, math.radians(-35)), 2.6, (0.85, 0.92, 1.0)),
                                ("fill", (math.radians(70), 0, math.radians(140)), 1.2, (0.7, 0.8, 1.0)),
                                ("rim", (math.radians(110), 0, math.radians(20)), 1.6, (0.8, 0.9, 1.0))):
            ld = bpy.data.lights.new("pv_" + nm, 'SUN')
            ld.energy = e
            ld.color = col
            lo = bpy.data.objects.new("pv_" + nm, ld)
            self.coll.objects.link(lo)
            lo.parent = piv
            lo.rotation_euler = rot
            lights.append(lo)
        return piv, cam, lights

    def close(self):
        sc = self.sc
        datas = []
        for o in list(self.coll.objects):
            if o.data is not None:
                datas.append((o.data.name, o.data.rna_type.identifier))
            bpy.data.objects.remove(o, do_unlink=True)
        for nm, kind in datas:
            lib = {"Mesh": bpy.data.meshes, "Camera": bpy.data.cameras, "Light": bpy.data.lights}.get(kind)
            if lib is not None and nm in lib and lib[nm].users == 0:
                lib.remove(lib[nm])
        bpy.data.collections.remove(self.coll)
        for n, (loc, hr, hv, hs) in self.saved.items():
            ob = bpy.data.objects[n]
            ob.location = loc
            ob.hide_render, ob.hide_viewport = hr, hv
            ob.hide_set(hs)
        sc.render.engine, sc.render.resolution_x, sc.render.resolution_y, sc.camera, sc.world, sc.render.filepath, sc.compositing_node_group = self.old


def _layout(stage, job):
    """translate the exported meshes into their preview places (put back by Stage.close); returns the extra objects to show"""
    lay = job["layout"]
    show = list(job["objs"])
    extra = []
    ob = bpy.data.objects
    if lay in ("body", "costume"):
        stage.mannequin()
        extra += [o.name for o in stage.body]
        for n in WINGS:
            ob[n].location = ROOT_OFF
    if lay == "plain_wing":
        pass
    if lay == "costume":
        # bracers on the forearms, boots on the legs, flakes drifting around the body (linked duplicates)
        me = ob["uryu_bracer"].data
        for k, (x, z) in enumerate(((0.351, 0.86), (-0.351, 0.86))):
            o = bpy.data.objects.new("pv_bracer_%d" % k, me)
            stage.coll.objects.link(o)
            o.location = (x, 0.0, z)
            extra.append(o.name)
        me = ob["uryu_boot"].data
        for k, x in enumerate((0.117, -0.117)):
            o = bpy.data.objects.new("pv_boot_%d" % k, me)
            stage.coll.objects.link(o)
            o.location = (x, 0.0, 0.0)
            extra.append(o.name)
        me = ob["uryu_flechette"].data
        spots = [(0.9, -0.5, 1.9, 20, 40, 10), (-1.0, -0.2, 0.9, -30, 70, 0), (0.7, 0.5, 0.5, 60, 20, 30), (-0.8, 0.6, 1.6, 10, -40, 80), (1.1, 0.2, 1.2, -20, 10, 50),
                 (-0.5, -0.7, 0.4, 70, 80, 20), (0.3, -0.9, 1.5, 35, 15, -30)]
        for k, (x, y, z, rx, ry, rz) in enumerate(spots):
            o = bpy.data.objects.new("pv_flake_%d" % k, me)
            stage.coll.objects.link(o)
            o.location = (x, y, z)
            o.rotation_euler = (math.radians(rx), math.radians(ry), math.radians(rz))
            extra.append(o.name)
        show = [n for n in show if n not in ("uryu_bracer", "uryu_boot", "uryu_flechette")]
    return show, extra


def render_jobs(root, names=None, turn=True, tmp=None):
    """render the frames of the listed jobs (all when None) into tmp (default TMP); returns the file list"""
    tmp = tmp or TMP
    os.makedirs(tmp, exist_ok=True)
    st = Stage(root)
    files = []
    try:
        sc = st.sc
        jobs = [j for j in JOBS if names is None or j["name"] in names]
        if turn and (names is None or "turn" in names):
            for mesh, (scale, c, elev) in TURN.items():
                jobs.append(dict(name="turn_" + mesh, layout="plain", objs=[mesh], scale=scale, c=c, size=(300, 300), views=[("%03d" % y, y, elev) for y in TURN_YAWS]))
        for job in jobs:
            for o in st.saved:
                bpy.data.objects[o].location = st.saved[o][0]
            for x in list(st.body):
                pass
            show, extra = _layout(st, job)
            for k in list(extra):
                if k.startswith("pv_") and bpy.data.objects.get(k) is None:
                    extra.remove(k)
            piv, cam, lights = st.rig(job["c"], job.get("persp", False), job.get("scale", 2.0), job.get("dist", 6.0))
            sc.render.resolution_x, sc.render.resolution_y = job["size"]
            for view in job["views"]:
                label, yaw, elev = view[0], view[1], view[2]
                objs = view[3] if len(view) > 3 else show
                for o in st.saved:
                    bpy.data.objects[o].hide_render = o not in objs
                    bpy.data.objects[o].hide_viewport = False
                for o in bpy.data.objects:
                    if o.name.startswith("pv_") and o.type == 'MESH':
                        o.hide_render = o.name not in extra
                piv.rotation_euler = (-math.radians(elev), 0, math.radians(yaw))
                bpy.context.view_layer.update()
                path = os.path.join(tmp, "uq_%s_%s.png" % (job["name"], label))
                sc.render.filepath = path
                bpy.ops.render.render(write_still=True)
                files.append(path)
            # remove the per job extras (duplicates of meshes for the costume) but keep the mannequin
            for n in list(extra):
                if n.startswith(("pv_bracer_", "pv_boot_", "pv_flake_")):
                    bpy.data.objects.remove(bpy.data.objects[n], do_unlink=True)
            for o in (cam, *lights, piv):
                dn = (o.data.name, o.data.rna_type.identifier) if o.data is not None else None
                bpy.data.objects.remove(o, do_unlink=True)
                if dn is not None:
                    lib = bpy.data.cameras if dn[1] == 'Camera' else bpy.data.lights
                    if dn[0] in lib and lib[dn[0]].users == 0:
                        lib.remove(lib[dn[0]])
    finally:
        st.close()
    return files


# =================================================================================== system python: sheets
def sheets(root, tmp):
    from PIL import Image, ImageDraw
    out = os.path.join(root, "renders", "uryu_fx")
    os.makedirs(out, exist_ok=True)

    def load(job, label):
        return Image.open(os.path.join(tmp, "uq_%s_%s.png" % (job, label)))

    def row(job, labels, title=None, gap=6):
        ims = [load(job, l) for l in labels]
        W = sum(i.width for i in ims) + gap * (len(ims) - 1)
        H = max(i.height for i in ims) + (18 if title else 0)
        sh = Image.new("RGB", (W, H), (5, 15, 46))
        d = ImageDraw.Draw(sh)
        x = 0
        for l, im in zip(labels, ims):
            sh.paste(im, (x, 18 if title else 0))
            if title:
                d.text((x + 4, 3), "%s %s" % (title, l), fill=(190, 225, 245))
            x += im.width + gap
        return sh
    # single frame jobs are copied as they are
    for job in ("wing_back", "wing_front", "wing_top", "wing_side", "wing_one_back", "wing_one_front", "wing_one_iso"):
        p = os.path.join(tmp, "uq_%s_v.png" % job)
        if os.path.exists(p):
            Image.open(p).save(os.path.join(out, job + ".png"))
    for job in JOBS:
        n = job["name"]
        labels = [v[0] for v in job["views"]]
        if len(labels) > 1 and all(os.path.exists(os.path.join(tmp, "uq_%s_%s.png" % (n, l))) for l in labels):
            row(n, labels, n).save(os.path.join(out, n + ".png"))
    # one turntable sheet for all meshes
    rows = []
    for mesh in TURN:
        labels = ["%03d" % y for y in TURN_YAWS]
        if all(os.path.exists(os.path.join(tmp, "uq_turn_%s_%s.png" % (mesh, l))) for l in labels):
            rows.append(row("turn_" + mesh, labels, mesh))
    if rows:
        W = max(r.width for r in rows)
        sh = Image.new("RGB", (W, sum(r.height + 4 for r in rows)), (5, 15, 46))
        y = 0
        for r in rows:
            sh.paste(r, (0, y))
            y += r.height + 4
        sh.save(os.path.join(out, "turntables.png"))
    print("sheets written to", out)


if __name__ == "__main__" and bpy is None:
    if len(sys.argv) >= 4 and sys.argv[1] == "sheets":
        sheets(sys.argv[2], sys.argv[3])
