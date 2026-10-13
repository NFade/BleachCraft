# Build the Gin (gin_shinso, gin_shinso_saya, gin_shinso_sheathed, gin_shinso_ray) and Uryu (uryu_cross, uryu_bow, uryu_bow_string,
# uryu_arrow, uryu_seele) meshes in Blender. Conventions as the other models (ART_BIBLE 0.5): 1 unit = 1 m, Z up, origin at the pommel (kashira),
# blade axis +Z, cutting edge -Y, spine +Y, sori toward +Y, objects keep rotation 0 / scale 1 / origin at (0,0,0).
#   exec(open(<worktree>/blender/scripts/gu_build.py).read(), {"__file__": ".../gu_build.py", "ROOT": "<worktree>/blender"})
import bpy, bmesh, math, os, sys
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
import importlib, gu_layout as L
importlib.reload(L)

N = L.ATLAS
LIGHT = Vector((0.35, -0.55, 0.75)).normalized()
TAU = math.tau


# ---------------------------------------------------------------- mesh builder
def uv_rect(rect, u, v):
    x, y, w, h = rect
    px = x + 0.5 + u * (w - 1)
    py = y + 0.5 + v * (h - 1)
    return (px / N, 1.0 - py / N)


def cell_uv(rect):
    x, y, w, h = rect
    return ((x + w / 2.0) / N, 1.0 - (y + h / 2.0) / N)


def pick_cell(normal, cells):
    d = normal.dot(LIGHT)
    i = 0 if d < -0.15 else 1 if d < 0.30 else 2 if d < 0.72 else 3
    return cells[i]


class MB:
    def __init__(self, name):
        self.name = name
        self.co = []
        self.f = []      # (idx, uvs|None, cells|None, smooth)

    def vert(self, p):
        self.co.append(Vector(p))
        return len(self.co) - 1

    def face(self, idx, uvs=None, cells=None, smooth=False):
        self.f.append((list(idx), uvs, cells, smooth))

    def to_object(self, material, collection):
        me = bpy.data.meshes.new(self.name)
        me.from_pydata([tuple(c) for c in self.co], [], [f[0] for f in self.f])
        me.update()
        uvl = me.uv_layers.new(name="UVMap")
        for p, (idx, uvs, cells, smooth) in zip(me.polygons, self.f):
            p.use_smooth = smooth
            if uvs is None:
                c = cell_uv(pick_cell(p.normal, cells))
                for li in p.loop_indices:
                    uvl.data[li].uv = c
            else:
                for li, uv in zip(p.loop_indices, uvs):
                    uvl.data[li].uv = uv
        me.materials.append(material)
        ob = bpy.data.objects.new(self.name, me)
        collection.objects.link(ob)
        return ob


def ring_oval(n, z, rx, ry, cx=0.0, cy=0.0):
    return [Vector((cx + rx * math.cos(TAU * j / n), cy + ry * math.sin(TAU * j / n), z)) for j in range(n)]


def sweep(mb, rings, ucols=None, vcols=None, rect=None, rect_fn=None, along_x=True, cells=None, smooth=False,
          cap0=False, cap1=False, apex=None, apex0=None, cap_cells=None, fixed=None, rev=False):
    """rings: list of equal-length rings (CCW looking against the sweep direction, so quads get outward normals).
    Textured by rect (u along the sweep over ucols, v around over vcols; rect_fn(j) -> (rect, v0, v1) per ring edge) or by tone cells."""
    n = len(rings[0])
    A = [[mb.vert(p) for p in r] for r in rings]
    if rev:
        # inward-facing shell (liner): reverse the winding of every face
        pass

    def mk(idx, uvs):
        if rev:
            idx = idx[::-1]
            uvs = uvs[::-1] if uvs else uvs
        mb.face(idx, uvs, cells if uvs is None else None, smooth)

    def fixed_uvs(k):
        return [uv_rect(fixed[0], fixed[1], fixed[2])] * k

    def mapuv(rc, u, v):
        return uv_rect(rc, u, v) if along_x else uv_rect(rc, v, u)

    for r in range(len(rings) - 1):
        for j in range(n):
            j1 = (j + 1) % n
            idx = [A[r][j], A[r][j1], A[r + 1][j1], A[r + 1][j]]
            if fixed:
                uvs = fixed_uvs(4)
            elif cells is not None:
                uvs = None
            else:
                rc, v0, v1 = rect_fn(j) if rect_fn else (rect, vcols[j], vcols[j + 1])
                u0, u1 = ucols[r], ucols[r + 1]
                uvs = [mapuv(rc, u0, v0), mapuv(rc, u0, v1), mapuv(rc, u1, v1), mapuv(rc, u1, v0)]
            mk(idx, uvs)
    if apex is not None:
        ap = mb.vert(apex)
        for j in range(n):
            j1 = (j + 1) % n
            idx = [A[-1][j], A[-1][j1], ap]
            if fixed:
                uvs = fixed_uvs(3)
            elif cells is not None:
                uvs = None
            else:
                rc, v0, v1 = rect_fn(j) if rect_fn else (rect, vcols[j], vcols[j + 1])
                uvs = [mapuv(rc, ucols[-1], v0), mapuv(rc, ucols[-1], v1), mapuv(rc, 1.0, (v0 + v1) / 2)]
            mk(idx, uvs)
    if apex0 is not None:
        ap = mb.vert(apex0)
        for j in range(n):
            j1 = (j + 1) % n
            idx = [A[0][j1], A[0][j], ap]
            if fixed:
                uvs = fixed_uvs(3)
            elif cells is not None:
                uvs = None
            else:
                rc, v0, v1 = rect_fn(j) if rect_fn else (rect, vcols[j], vcols[j + 1])
                uvs = [mapuv(rc, ucols[0], v1), mapuv(rc, ucols[0], v0), mapuv(rc, 0.0, (v0 + v1) / 2)]
            mk(idx, uvs)
    cc = cap_cells or cells
    if cap0:
        mb.face(A[0][::-1] if not rev else A[0], None if not fixed else fixed_uvs(n), cc, False)
    if cap1:
        mb.face(A[-1] if not rev else A[-1][::-1], None if not fixed else fixed_uvs(n), cc, False)
    return A


def box(mb, x0, x1, y0, y1, z0, z1, cells, skip=()):
    q = {
        "+z": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
        "-z": [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)],
        "+x": [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)],
        "-x": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
        "+y": [(x1, y1, z0), (x0, y1, z0), (x0, y1, z1), (x1, y1, z1)],
        "-y": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
    }
    for k, pts in q.items():
        if k in skip:
            continue
        mb.face([mb.vert(p) for p in pts], None, cells, False)


# ---------------------------------------------------------------- Gin
G = L.GIN
M_METAL = L.GIN_CELLS["metal"]

Z_TSUBA0, Z_TSUBA1 = 0.200, 0.206         # tsuba plane (6 mm), fuchi top = 0.200
Z_HAB0, Z_HAB1 = 0.200, 0.230             # habaki 30 mm tall, passes through the tsuba plane
Z_BL0, Z_TIP = 0.225, 0.656               # blade starts inside the habaki; nagasa 0.45 m from the habaki start 0.206
Z_YOKOTE = 0.611                          # kissaki 45 mm
SORI_T_BLADE = 0.024                      # tip offset (chord deviation 6 mm x 4)
SORI_T_SAYA = 0.027                       # saya tip offset (chord deviation +1 mm... the saya is 3 mm of tip offset wider)
Z_MOUTH = Z_TSUBA1
K_B = SORI_T_BLADE / (Z_TIP - Z_MOUTH) ** 2
K_S = SORI_T_SAYA / (0.668 - Z_MOUTH) ** 2


def yb(z):
    return K_B * max(0.0, z - Z_MOUTH) ** 2


def ys(z):
    return K_S * max(0.0, z - Z_MOUTH) ** 2


def build_hilt(mb):
    n = 12
    # kashira: z 0 .. 0.012, flares from 22 x 30 to 24 x 30 mm; bottom cap
    sweep(mb, [ring_oval(n, 0.0, 0.011, 0.015), ring_oval(n, 0.012, 0.012, 0.015)], cells=M_METAL, smooth=True, cap0=True)
    # tsuka: 24 x 30 mm, 3 mm swell in the middle third, 4 segments; wrap texture
    zs = [0.012, 0.0555, 0.099, 0.1425, 0.186]
    rx = [0.012, 0.0125, 0.0135, 0.0125, 0.012]
    ry = [0.015, 0.0155, 0.0165, 0.0155, 0.015]
    rings = [ring_oval(n, z, a, b) for z, a, b in zip(zs, rx, ry)]
    uc = [(z - 0.012) / (0.186 - 0.012) for z in zs]
    sweep(mb, rings, ucols=uc, vcols=[j / n for j in range(n + 1)], rect=G["wrap"], along_x=False, smooth=True)
    # fuchi: lip under it (faces down), wall 26 x 32 mm, top cap
    sweep(mb, [ring_oval(n, 0.186, 0.012, 0.015), ring_oval(n, 0.186, 0.013, 0.016)], cells=M_METAL)
    sweep(mb, [ring_oval(n, 0.186, 0.013, 0.016), ring_oval(n, 0.200, 0.013, 0.016)], cells=M_METAL, smooth=True, cap1=True)


def swirl_path(sign, samples=10):
    """One S arm of the tsuba: starts inside the habaki at the blade edge, bulges around the oval (a_y 37 mm, b_x 21 mm) and hooks inward."""
    pts = []
    for i in range(samples + 1):
        s = i / samples
        if s < 0.25:
            t = s / 0.25
            g = 0.42 + (1.0 - 0.42) * (t * t * (3 - 2 * t))
        elif s < 0.65:
            g = 1.0
        else:
            t = (s - 0.65) / 0.35
            g = 1.0 - 0.20 * (t * t * (3 - 2 * t))
        psi = math.radians(-90.0 + 185.0 * s)
        x = 0.021 * g * math.cos(psi)
        y = 0.037 * g * math.sin(psi)
        pts.append(Vector((sign * x, sign * y, 0.0)))
    return pts


def build_swirls(mb):
    zc = (Z_TSUBA0 + Z_TSUBA1) / 2.0
    for sign in (1, -1):
        pts = swirl_path(sign)
        rings = []
        for i, p in enumerate(pts):
            a = pts[max(0, i - 1)]
            b = pts[min(len(pts) - 1, i + 1)]
            T = (b - a).normalized()
            zv = Vector((0, 0, 1))
            side = zv.cross(T)                       # a = z x T, b = z so that a x b = T
            wdt = 0.0030 if (0 < i < len(pts) - 1) else 0.0026
            hh = 0.0030
            if i >= len(pts) - 2:
                wdt = 0.0034                          # slightly fatter hook end
            ring = []
            for sx, sy in ((1, 1), (-1, 1), (-1, -1), (1, -1)):
                ring.append(Vector((p.x, p.y, zc)) + side * (sx * wdt) + zv * (sy * hh))
            rings.append(ring)
        sweep(mb, rings, cells=M_METAL, cap0=True, cap1=True)


def build_habaki(mb, z1=Z_HAB1):
    box(mb, -0.0045, 0.0045, -0.015, 0.015, Z_HAB0, z1, M_METAL, skip=("-z",) + (("+z",) if z1 < Z_HAB1 else ()))


def build_blade(mb):
    zs = [Z_BL0, 0.34, 0.45, 0.55, Z_YOKOTE]
    rings = []
    ucs = []
    for z in zs:
        f = (z - Z_BL0) / (Z_YOKOTE - Z_BL0)
        w = 0.028 + (0.020 - 0.028) * f
        t = 0.008 + (0.004 - 0.008) * f
        yc = yb(z)
        rings.append([Vector((0.0, yc - w / 2, z)),
                      Vector((t / 2, yc + 0.05 * w, z)),
                      Vector((t * 0.375, yc + w / 2, z)),
                      Vector((-t * 0.375, yc + w / 2, z)),
                      Vector((-t / 2, yc + 0.05 * w, z))])
        ucs.append((z - Z_BL0) / (Z_TIP - Z_BL0))
    R, S = G["blade"], G["spine"]
    fn = {0: (R, 0.0, 0.55), 1: (R, 0.55, 1.0), 2: (S, 0.2, 0.8), 3: (R, 1.0, 0.55), 4: (R, 0.55, 0.0)}
    sweep(mb, rings, ucols=ucs, rect_fn=lambda j: fn[j], cap0=True, cap_cells=M_METAL, apex=Vector((0.0, yb(Z_TIP), Z_TIP)))


def build_saya(mb, open_mouth=True):
    n = 8

    def rg(z, rx, ry):
        return ring_oval(n, z, rx, ry, 0.0, ys(z))
    # koiguchi collar (fitting)
    sweep(mb, [rg(Z_MOUTH, 0.0115, 0.0195), rg(0.222, 0.0115, 0.0195)], cells=M_METAL, smooth=True)
    if open_mouth:
        # lip faces down: inner liner mouth ring -> outer ring
        sweep(mb, [rg(Z_MOUTH, 0.0095, 0.0175), rg(Z_MOUTH, 0.0115, 0.0195)], cells=M_METAL)
    else:
        mb_cap = [mb.vert(p) for p in rg(Z_MOUTH, 0.0115, 0.0195)]
        mb.face(mb_cap[::-1], None, M_METAL, False)
    # collar top lip (faces up) -> body
    sweep(mb, [rg(0.222, 0.0115, 0.0195), rg(0.222, 0.0105, 0.0185)], cells=M_METAL)
    zs = [0.222, 0.35, 0.50, 0.62]
    dims = [(0.0105, 0.0185), (0.0100, 0.0175), (0.0095, 0.0165), (0.0098, 0.0168)]
    rings = [rg(z, a, b) for z, (a, b) in zip(zs, dims)]
    uc = [(z - Z_MOUTH) / (0.668 - Z_MOUTH) for z in zs]
    sweep(mb, rings, ucols=uc, vcols=[j / n for j in range(n + 1)], rect=G["saya"], smooth=True)
    # kojiri (end cap fitting)
    sweep(mb, [rg(0.62, 0.0098, 0.0168), rg(0.645, 0.0092, 0.0150), rg(0.668, 0.0060, 0.0090)], cells=M_METAL, smooth=True, cap1=True)
    if open_mouth:
        # inner liner, dark, faces the axis, closed at the far end
        zs2 = [Z_MOUTH, 0.35, 0.50, 0.62, 0.66]
        dims2 = [(0.0095, 0.0175), (0.0080, 0.0155), (0.0075, 0.0145), (0.0078, 0.0148), (0.0052, 0.0093)]
        rings2 = [rg(z, a, b) for z, (a, b) in zip(zs2, dims2)]
        sweep(mb, rings2, fixed=(G["saya"], 0.5, 0.0), rev=True, cap1=True)


def build_ray(mb):
    R = G["ray"]
    tri = lambda z: [Vector((0.0, -0.02, z)), Vector((0.0035, 0.0, z)), Vector((0.0, 0.02, z))]
    sweep(mb, [tri(0.0), tri(0.94)], ucols=[0.0, 0.94], vcols=[0.0, 0.5, 1.0, 0.0], rect=R, apex=Vector((0.0012, 0.0, 1.0)), cap0=True,
          cap_cells=[L.GIN_CELLS["metal"][3]] * 4)


# ---------------------------------------------------------------- Uryu
U = L.URYU
M_REISHI = L.URYU_CELLS["reishi"]
M_SILVER = L.URYU_CELLS["silver"]


def build_cross(mb):
    P = [(0.0135, 0.044), (-0.0135, 0.044), (-0.0085, 0.0085), (-0.040, 0.0135), (-0.040, -0.0135), (-0.0085, -0.0085),
         (-0.0135, -0.060), (0.0135, -0.060), (0.0085, -0.0085), (0.040, -0.0135), (0.040, 0.0135), (0.0085, 0.0085)]
    ids = [mb.vert((0.0, y, z)) for y, z in P]
    af, ab = mb.vert((0.008, 0.0, 0.0)), mb.vert((-0.008, 0.0, 0.0))
    k = len(P)
    for i in range(k):
        j = (i + 1) % k
        mb.face([ids[i], ids[j], af], None, M_SILVER, False)
        mb.face([ids[j], ids[i], ab], None, M_SILVER, False)
    # bail: flat double-sided triangular ring on top of the upper arm
    outer = [(0.0, 0.060), (-0.0105, 0.0405), (0.0105, 0.0405)]
    cy, cz = 0.0, sum(p[1] for p in outer) / 3.0
    inner = [(cy + 0.5 * (y - cy), cz + 0.5 * (z - cz)) for y, z in outer]
    # order CCW seen from +X: (0,top) -> (-y, low) -> (+y, low)
    o = [mb.vert((0.0, y, z)) for y, z in outer]
    ii = [mb.vert((0.0, y, z)) for y, z in inner]
    # back side has its own vertices (a zero-thickness double-sided ring; the OBJ importer would merge faces sharing all vertices)
    o2 = [mb.vert((0.0, y, z)) for y, z in outer]
    ii2 = [mb.vert((0.0, y, z)) for y, z in inner]
    for i in range(3):
        j = (i + 1) % 3
        mb.face([o[i], o[j], ii[j], ii[i]], None, M_SILVER, False)
        mb.face([ii2[i], ii2[j], o2[j], o2[i]], None, M_SILVER, False)


BOW_Z = [-0.46, -0.33, -0.18, -0.06, 0.06, 0.18, 0.33, 0.46]
BOW_WT = {0.06: (0.0115, 0.0115), 0.18: (0.018, 0.0085), 0.33: (0.0155, 0.0070), 0.46: (0.0095, 0.0045)}
BOW_TIP_Y = 0.105


def bow_y(z):
    a = abs(z)
    return 0.0 if a <= 0.06 else BOW_TIP_Y * ((a - 0.06) / 0.49) ** 1.7


def build_bow(mb):
    rings, ucs = [], []
    for z in BOW_Z:
        w, t = BOW_WT[round(abs(z), 2)]
        rings.append([Vector((w * math.cos(TAU * j / 6), bow_y(z) + t * math.sin(TAU * j / 6), z)) for j in range(6)])
        ucs.append((z + 0.55) / 1.1)
    sweep(mb, rings, ucols=ucs, vcols=[j / 6 for j in range(7)], rect=U["bow"], smooth=False,
          apex=Vector((0.0, bow_y(0.55), 0.55)), apex0=Vector((0.0, bow_y(-0.55), -0.55)))


def build_string(mb):
    S = U["string"]
    y = BOW_TIP_Y
    a = [mb.vert(p) for p in ((-0.0025, y, -0.55), (0.0025, y, -0.55), (0.0025, y, 0.55), (-0.0025, y, 0.55))]
    # normal +Y? (b-a) x (d-a) = x x z = -y, so reverse for +Y facing the archer
    mb.face(a[::-1], [uv_rect(S, 0, 0), uv_rect(S, 0, 1), uv_rect(S, 1, 1), uv_rect(S, 1, 0)], None, False)
    b = [mb.vert(p) for p in ((0.0, y - 0.0025, -0.55), (0.0, y + 0.0025, -0.55), (0.0, y + 0.0025, 0.55), (0.0, y - 0.0025, 0.55))]
    mb.face(b, [uv_rect(S, 0, 0), uv_rect(S, 0, 1), uv_rect(S, 1, 1), uv_rect(S, 1, 0)], None, False)


def build_arrow(mb):
    Sh = U["shaft"]
    dia = lambda z, r: [Vector((r, 0, z)), Vector((0, r, z)), Vector((-r, 0, z)), Vector((0, -r, z))]
    sweep(mb, [dia(0.02, 0.004), dia(0.41, 0.004)], ucols=[0.04, 0.82], vcols=[0, 0.25, 0.5, 0.75, 1.0], rect=Sh, cap0=True, cap_cells=M_REISHI)
    # barbed head: ledge facing back, then the point
    sweep(mb, [dia(0.41, 0.004), dia(0.41, 0.013)], cells=M_REISHI)
    sweep(mb, [dia(0.41, 0.013)], cells=M_REISHI, apex=Vector((0, 0, 0.5)))
    # tail fins (a Quincy cross seen from behind, arms swept back): four double-sided triangles
    for k in range(4):
        d = Vector((math.cos(k * math.pi / 2), math.sin(k * math.pi / 2), 0.0))
        p0, p1, p2 = d * 0.004 + Vector((0, 0, 0.13)), d * 0.004 + Vector((0, 0, 0.04)), d * 0.040
        i0, i1, i2 = mb.vert(p0), mb.vert(p1), mb.vert(p2)
        mb.face([i0, i1, i2], None, M_REISHI, False)
        mb.face([mb.vert(p2), mb.vert(p1), mb.vert(p0)], None, M_REISHI, False)


def build_seele(mb):
    tri = lambda z, a, b: [Vector((0.0, -a, z)), Vector((b, 0.0, z)), Vector((0.0, a, z))]
    # grip (triangular rod), widening into the flat blade, tapering to the point
    sweep(mb, [tri(0.0, 0.0055, 0.0055), tri(0.06, 0.0055, 0.0055), tri(0.09, 0.011, 0.0025)], cells=M_REISHI, cap0=True, apex=Vector((0.001, 0.0, 0.30)))
    # cross guard: one double-sided kite in the YZ plane
    g = [mb.vert(p) for p in ((0.0, 0.0, 0.052), (0.0, 0.028, 0.065), (0.0, 0.0, 0.080), (0.0, -0.028, 0.065))]
    mb.face(g, None, M_REISHI, False)
    mb.face([mb.vert(mb.co[i]) for i in g[::-1]], None, M_REISHI, False)


# ---------------------------------------------------------------- scene assembly
def make_material(name, png):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    tex = nt.nodes.new("ShaderNodeTexImage")
    img = bpy.data.images.get(os.path.basename(png))
    if img:
        bpy.data.images.remove(img)
    img = bpy.data.images.load(png)
    img.alpha_mode = 'STRAIGHT'
    tex.image = img
    tex.interpolation = 'Closest'
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.6
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    return mat


def empty(name, loc, coll):
    e = bpy.data.objects.new(name, None)
    e.empty_display_type = 'PLAIN_AXES'
    e.empty_display_size = 0.03
    e.location = loc
    coll.objects.link(e)
    return e


def build_all(root):
    exp = os.path.join(root, "export")
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for me in list(bpy.data.meshes):
        bpy.data.meshes.remove(me)
    coll = bpy.data.collections.get("export") or bpy.data.collections.new("export")
    if coll.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(coll)
    gm = make_material("gin_shinso_atlas", os.path.join(exp, "gin_shinso", "gin_shinso_diffuse.png"))
    um = make_material("uryu_atlas", os.path.join(exp, "uryu", "uryu_diffuse.png"))
    res = {}

    def mk(name, fns, mat, **kw):
        mb = MB(name)
        for f in fns:
            f(mb)
        res[name] = mb.to_object(mat, coll)

    mk("gin_shinso", [build_hilt, build_swirls, build_habaki, build_blade], gm)
    mk("gin_shinso_saya", [build_saya], gm)
    mk("gin_shinso_sheathed", [build_hilt, build_swirls, lambda m: build_habaki(m, Z_TSUBA1), lambda m: build_saya(m, False)], gm)
    mk("gin_shinso_ray", [build_ray], gm)
    mk("uryu_cross", [build_cross], um)
    mk("uryu_bow", [build_bow], um)
    mk("uryu_bow_string", [build_string], um)
    mk("uryu_arrow", [build_arrow], um)
    mk("uryu_seele", [build_seele], um)
    emp = {"grip_hand": (0, 0, 0.12), "tip": (0, yb(Z_TIP), Z_TIP), "saya_mouth": (0, 0, Z_MOUTH),
           "bow_grip_hand": (0, 0, 0.0), "bow_string_top": (0, BOW_TIP_Y, 0.55), "bow_string_bottom": (0, BOW_TIP_Y, -0.55),
           "arrow_tip": (0, 0, 0.5), "seele_tip": (0.001, 0, 0.30), "cross_hang": (0, 0, 0.060)}
    for k, v in emp.items():
        empty(k, v, coll)
    return res


if "ROOT" in globals() and globals().get("RUN_BUILD", True):
    result = {k: len(v.data.polygons) for k, v in build_all(ROOT).items()}
