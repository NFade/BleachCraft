# Build the Quincy effect meshes of the set `uryu_fx` in Blender (storyboard U-FX 1, design/URYU_VFX_STORYBOARD.md section 4.1), check them and export them.
# Conventions as the other models (ART_BIBLE 0.5, gu_build.py): 1 unit = 1 m, Z up, objects keep location 0 / rotation 0 / scale 1 (origin = (0,0,0) of each mesh),
# +Z along the length of long things, OBJ export forward -Z / up Y (so OBJ (x, y, z) = Blender (x, z, -y): Blender +Y is BEHIND, -Y is FORWARD), quads kept.
#   exec(open(<worktree>/blender/scripts/uq_build.py).read(), {"__file__": ".../uq_build.py", "ROOT": "<worktree>/blender"})
#   -> builds the scene (collection `export`, material `uryu_fx_atlas`, empties) and returns the triangle table in `result`
#   then:  import uq_build; uq_build.export_all(ROOT)   (OBJ + meta json + export_check.json into <ROOT>/export/uryu_fx)
import bpy, bmesh, math, os, sys, json
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
import importlib, uq_layout as L
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


def uv_px(px, py):
    return (px / N, 1.0 - py / N)


def cell_uv(rect):
    x, y, w, h = rect
    return ((x + w / 2.0) / N, 1.0 - (y + h / 2.0) / N)


def pick_cell(normal, cells):
    d = normal.dot(LIGHT)
    i = 0 if d < -0.15 else 1 if d < 0.30 else 2 if d < 0.72 else 3
    return cells[i]


def newell(pts):
    n = Vector((0, 0, 0))
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        n.x += (a.y - b.y) * (a.z + b.z)
        n.y += (a.z - b.z) * (a.x + b.x)
        n.z += (a.x - b.x) * (a.y + b.y)
    return n


class MB:
    def __init__(self, name):
        self.name = name
        self.co = []
        self.f = []      # (idx, uvs|None, cells|None)

    def vert(self, p):
        self.co.append(Vector(p))
        return len(self.co) - 1

    def face(self, idx, uvs=None, cells=None, ref=None):
        """Add a face. ref: a point (or a callable face-centre -> point) the normal must point AWAY from; the face is flipped when it does not."""
        idx = list(idx)
        uvs = list(uvs) if uvs is not None else None
        if ref is not None:
            pts = [self.co[i] for i in idx]
            c = sum(pts, Vector((0, 0, 0))) / len(pts)
            r = ref(c) if callable(ref) else Vector(ref)
            if newell(pts).dot(c - r) < 0:
                idx.reverse()
                if uvs is not None:
                    uvs.reverse()
        self.f.append((idx, uvs, cells))

    def face2(self, pts, uvs=None, cells=None, ref=None):
        """Add a face from points (own vertices)."""
        self.face([self.vert(p) for p in pts], uvs, cells, ref)

    def double(self, pts, uvs=None, cells=None):
        """Flat double sided polygon with separate vertices per side (the OBJ importer would merge faces that share all vertices)."""
        a = [self.vert(p) for p in pts]
        b = [self.vert(p) for p in pts]
        self.face(a, uvs, cells)
        self.face(b[::-1], uvs[::-1] if uvs else None, cells)

    def to_object(self, material, collection):
        me = bpy.data.meshes.new(self.name)
        me.from_pydata([tuple(c) for c in self.co], [], [f[0] for f in self.f])
        me.update()
        uvl = me.uv_layers.new(name="UVMap")
        for p, (idx, uvs, cells) in zip(me.polygons, self.f):
            p.use_smooth = False
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


def tri_fan(mb, ring, apex, cells, ref):
    for j in range(len(ring)):
        mb.face([ring[j], ring[(j + 1) % len(ring)], apex], None, cells, ref)


def tube(mb, rings, rect=None, ucols=None, vcols=None, cells=None, apex=None, apex_u=1.0, cap0=False, swap=False):
    """rings: lists of equal length (points). Faces between consecutive rings (u along the sweep over ucols, v around over vcols), normals pushed
    away from the ring centres. apex: a closing point after the last ring."""
    n = len(rings[0])
    A = [[mb.vert(p) for p in r] for r in rings]
    cen = [sum(r, Vector((0, 0, 0))) / n for r in rings]
    for r in range(len(rings) - 1):
        ref = (cen[r] + cen[r + 1]) / 2
        for j in range(n):
            j1 = (j + 1) % n
            idx = [A[r][j], A[r][j1], A[r + 1][j1], A[r + 1][j]]
            uvs = None
            if rect is not None:
                u0, u1 = ucols[r], ucols[r + 1]
                v0, v1 = vcols[j], vcols[j + 1]
                uvs = [uv_rect(rect, u0, v0), uv_rect(rect, u0, v1), uv_rect(rect, u1, v1), uv_rect(rect, u1, v0)]
                if swap:      # texture x runs around the ring, texture y along the sweep (bracer, boot)
                    uvs = [uv_rect(rect, b_, a_) for a_, b_ in ((u0, v0), (u0, v1), (u1, v1), (u1, v0))]
            mb.face(idx, uvs, cells if rect is None else None, ref)
    if apex is not None:
        ap = mb.vert(apex)
        for j in range(n):
            j1 = (j + 1) % n
            uvs = None
            if rect is not None:
                uvs = [uv_rect(rect, ucols[-1], vcols[j]), uv_rect(rect, ucols[-1], vcols[j + 1]), uv_rect(rect, apex_u, (vcols[j] + vcols[j + 1]) / 2)]
            mb.face([A[-1][j], A[-1][j1], ap], uvs, cells if rect is None else None, cen[-1])
    if cap0:
        mb.face(A[0], None, cells, lambda c: c + (cen[0] - cen[1]))
    return A


def solid(mb, verts, faces, cells, centre=None):
    """Convex solid from points and index faces; every face oriented away from the centroid (or `centre`)."""
    ids = [mb.vert(p) for p in verts]
    cen = Vector(centre) if centre is not None else sum((Vector(p) for p in verts), Vector((0, 0, 0))) / len(verts)
    for f in faces:
        mb.face([ids[i] for i in f], None, cells, cen)
    return ids


# ---------------------------------------------------------------- wings
CELL_R = L.CELLS["reishi"]
CELL_W = L.CELLS["white"]
CELL_RIM = L.CELLS["rim"]


def wing_slab(mb, poly, layer, sx):
    n = Vector(L.wing_normal())
    n.x *= sx
    t = L.WING_THICK / 2.0
    base = [Vector(L.wing_xyz(x, z, layer)) for x, z in poly]
    for v in base:
        v.x *= sx
    back = [mb.vert(v + n * t) for v in base]
    front = [mb.vert(v - n * t) for v in base]
    cen = sum(base, Vector((0, 0, 0))) / 4.0

    def uvs_of(poly_):
        return [uv_px(*L.wing_px(x, z)) for x, z in poly_]
    mb.face(back, uvs_of(poly), None, cen)
    mb.face(front, uvs_of(poly), None, cen)
    for i in range(4):
        j = (i + 1) % 4
        mb.face([back[i], back[j], front[j], front[i]], None, CELL_RIM, cen)
    return base


def wing_spike(mb, i, sx):
    tip, m, hw, d, plate = L.spike_geom(i)
    layer = plate
    n = Vector(L.wing_normal())
    n.x *= sx
    P = lambda x, z: Vector((sx * L.wing_xyz(x, z, layer)[0], L.wing_xyz(x, z, layer)[1], L.wing_xyz(x, z, layer)[2]))
    perp = (-d[1], d[0])
    a = P(m[0] + hw * perp[0], m[1] + hw * perp[1])
    c = P(m[0] - hw * perp[0], m[1] - hw * perp[1])
    mc = P(*m)
    b = mc + n * 0.0125
    e = mc - n * 0.0125
    apex = P(*tip)
    # rhombus base (a, b, c, e) and four sides: 6 tris
    solid(mb, [apex, a, b, c, e], [[0, 1, 2], [0, 2, 3], [0, 3, 4], [0, 4, 1], [1, 4, 3, 2]], CELL_W)


def wing_chip(mb, c3, s, sx):
    k = s / math.sqrt(3.0)
    pts = [Vector((k * 1.5, k * 0.4, k)), Vector((k * 1.5, -k * 0.4, -k)), Vector((-k * 1.1, k * 0.6, -k)), Vector((-k * 0.6, -k * 0.5, k * 1.3))]
    pts = [Vector((sx * p.x, p.y, p.z)) + c3 for p in pts]
    solid(mb, pts, [[0, 1, 2], [0, 2, 3], [0, 3, 1], [1, 3, 2]], CELL_W)


def build_wing(mb, sx):
    for name, poly in L.wing_polys():
        wing_slab(mb, poly, name, sx)
    for i in range(len(L.SPIKES)):
        wing_spike(mb, i, sx)
    for (x, z, s) in L.CHIPS:
        x_, y_, z_ = L.wing_xyz(x, z, "low3")
        wing_chip(mb, Vector((sx * x_, y_, z_)), s, sx)


# ---------------------------------------------------------------- flechette (the flake)
def build_flechette(mb):
    R = L.RECT["flake"]
    pts = [(0.0, 0.20), (0.06, 0.03), (0.05, -0.20), (0.0, -0.14), (-0.05, -0.20), (-0.06, 0.03)]     # (x, z): tip, shoulder R, tail R, notch, tail L, shoulder L

    def uv(x, z):
        return uv_rect(R, (z + 0.20) / 0.40, (x + 0.06) / 0.12)
    for (i, j, k) in ((0, 1, 2), (0, 2, 3), (0, 3, 4), (0, 4, 5)):
        tri = [pts[i], pts[j], pts[k]]
        mb.double([(p[0], 0.0, p[1]) for p in tri], [uv(*p) for p in tri], None)


# ---------------------------------------------------------------- needle: three crossed double sided planes, silhouette in the texture alpha
def build_needle(mb):
    R = L.RECT["needle"]
    hw = 0.0375
    for k in range(3):
        a = math.pi * k / 3.0
        dx, dy = math.cos(a), math.sin(a)
        P = [(-hw * dx, -hw * dy, 0.0), (hw * dx, hw * dy, 0.0), (hw * dx, hw * dy, 1.2), (-hw * dx, -hw * dy, 1.2)]
        uvs = [uv_rect(R, 0, 0), uv_rect(R, 0, 1), uv_rect(R, 1, 1), uv_rect(R, 1, 0)]
        mb.double(P, uvs)


# ---------------------------------------------------------------- star head: two crossed octahedra (16 tris) and a diamond ring band (8 tris)
def build_star(mb):
    R = L.RECT["star"]
    h, w = 0.8, 0.25
    eq_z = [Vector((w, 0, 0)), Vector((0, w, 0)), Vector((-w, 0, 0)), Vector((0, -w, 0))]          # equator of the octahedron along Z
    eq_x = [Vector((0, w, 0)), Vector((0, 0, w)), Vector((0, -w, 0)), Vector((0, 0, -w))]          # equator of the octahedron along X
    for axis, eq in ((Vector((0, 0, 1)), eq_z), (Vector((1, 0, 0)), eq_x)):
        for sg in (1, -1):
            apex = mb.vert(axis * h * sg)
            ring = [mb.vert(p) for p in eq]
            for j in range(4):
                j1 = (j + 1) % 4
                uvs = [uv_rect(R, 0.0, 0.0), uv_rect(R, 0.0, 1.0), uv_rect(R, 1.0, 0.5)]
                mb.face([ring[j], ring[j1], apex], uvs, None, Vector((0, 0, 0)))
    # ZK ring: a 4 sided band around the shaft behind the star (diamond section), outward faces
    rr, z0, z1 = 0.22, -0.46, -0.40
    r0 = [Vector((rr * math.cos(TAU * j / 4), rr * math.sin(TAU * j / 4), z0)) for j in range(4)]
    r1 = [Vector((rr * math.cos(TAU * j / 4), rr * math.sin(TAU * j / 4), z1)) for j in range(4)]
    A0 = [mb.vert(p) for p in r0]
    A1 = [mb.vert(p) for p in r1]
    for j in range(4):
        j1 = (j + 1) % 4
        mb.face([A0[j], A0[j1], A1[j1], A1[j]], None, CELL_W, Vector((0, 0, (z0 + z1) / 2)))


# ---------------------------------------------------------------- bracer and boot
def ring12(z, r, a0=math.radians(15.0)):
    return [Vector((r * math.cos(a0 + TAU * j / 12), r * math.sin(a0 + TAU * j / 12), z)) for j in range(12)]


def build_bracer(mb):
    R = L.RECT["band"]
    rr = 0.14                                    # circumradius of the 12 gon (apothem 0.135): hugs the vanilla arm (0.234 wide, half 0.117); the corners of the arm box poke 0.03 through
    rings = [ring12(0.10, rr), ring12(-0.10, rr)]
    tube(mb, rings, rect=R, ucols=[0.0, 1.0], vcols=[j / 12.0 for j in range(13)], swap=True)


def build_boot(mb):
    R = L.RECT["boot"]
    z0, z1 = 0.02, 0.45
    r0, r1 = 0.140, 0.155                        # leg is 0.234 wide (half 0.117, two legs touch): the shell hugs it, slight flare at the top like a boot shaft
    rings = [ring12(z1, r1), ring12(z0, r0)]
    tube(mb, rings, rect=R, ucols=[0.0, 1.0], vcols=[j / 12.0 for j in range(13)], swap=True)
    # pointed toe cap: a square pyramid on the front (-Y) of the foot end of the shell
    yb = -r0 * math.cos(math.radians(15.0)) + 0.002
    apex = Vector((0.0, yb - 0.15, 0.03))
    base = [Vector((-0.09, yb, 0.015)), Vector((0.09, yb, 0.015)), Vector((0.09, yb, 0.085)), Vector((-0.09, yb, 0.085))]
    solid(mb, [apex] + base, [[0, 1, 2], [0, 2, 3], [0, 3, 4], [0, 4, 1], [1, 4, 3, 2]], CELL_W, centre=Vector((0, yb - 0.05, 0.05)))
    # three straps: flat quads standing 3 mm off the front of the shaft
    for zc in (0.13, 0.25, 0.37):
        f = (zc - z0) / (z1 - z0)
        rad = (r0 + (r1 - r0) * f) * math.cos(math.radians(15.0)) + 0.003
        q = [Vector((-0.075, -rad, zc - 0.018)), Vector((0.075, -rad, zc - 0.018)), Vector((0.075, -rad, zc + 0.018)), Vector((-0.075, -rad, zc + 0.018))]
        mb.face2(q, None, CELL_RIM, Vector((0, 0.5, zc)))


# ---------------------------------------------------------------- bow
def bow_y(z):
    a = abs(z)
    return 0.0 if a <= 0.06 else L.BOW_TIP_Y * ((a - 0.06) / 0.49) ** 1.7


def hexring(z, w, t, yoff=0.0):
    return [Vector((w * math.cos(TAU * j / 6), yoff + t * math.sin(TAU * j / 6), z)) for j in range(6)]


def build_bow(mb, flex_deg=0.0):
    R = L.RECT["bow"]
    th = math.radians(flex_deg)

    def flex(p, sg):
        """rigid bend of one limb about the grip end (0, 0, sg * 0.06), toward +Y"""
        if th == 0.0:
            return p
        dy = p.y
        dz = p.z - sg * 0.06
        s_, c_ = math.sin(th), math.cos(th)
        ny = dy * c_ + sg * dz * s_
        nz = dz * c_ - sg * dy * s_
        return Vector((p.x, ny, sg * 0.06 + nz))

    rings_up = [[flex(p, 1) for p in hexring(z, w, t, bow_y(z))] for (z, w, t) in L.BOW_RINGS]
    rings_dn = [[flex(Vector((p.x, p.y, -p.z)), -1) for p in hexring(z, w, t, bow_y(z))] for (z, w, t) in L.BOW_RINGS]
    zs = [r[0] for r in L.BOW_RINGS]

    def uc(z):
        return (z + 0.55) / 1.1
    vc = [j / 6.0 for j in range(7)]
    # upper limb (grip ring z 0.06 up to the tip), lower limb mirrored, grip between the two z = +-0.06 rings
    tip_up = flex(Vector((0, bow_y(0.55), 0.55)), 1)
    tip_dn = flex(Vector((0, bow_y(-0.55), -0.55)), -1)
    tube(mb, rings_up, rect=R, ucols=[uc(z) for z in zs], vcols=vc, apex=tip_up)
    tube(mb, rings_dn, rect=R, ucols=[uc(-z) for z in zs], vcols=vc, apex=tip_dn, apex_u=0.0)
    g = [hexring(-0.06, 0.0115, 0.0115), hexring(0.06, 0.0115, 0.0115)]
    tube(mb, g, rect=R, ucols=[uc(-0.06), uc(0.06)], vcols=vc)
    # two lightning prongs at the arrow rest: three sided, forward (-Y), zig-zag outward
    for sx in (1, -1):
        def tri_ring(y, x, z, s):
            return [Vector((x + s, y, z)), Vector((x - s * 0.5, y, z + s * 0.87)), Vector((x - s * 0.5, y, z - s * 0.87))]
        r0 = tri_ring(-0.009, sx * 0.016, 0.030, 0.0042)
        r1 = tri_ring(-0.058, sx * 0.027, 0.036, 0.0036)
        ap = Vector((sx * 0.018, -0.112, 0.029))
        tube(mb, [r0, r1], cells=CELL_W, apex=ap)
    # spiky guard: four small three sided spikes crossing the ends of the grip
    for sz in (1, -1):
        for sx in (1, -1):
            base = [Vector((sx * 0.0105, -0.004, sz * 0.0575)), Vector((sx * 0.0105, 0.004, sz * 0.0575)), Vector((sx * 0.0105, 0.0, sz * 0.0425))]
            apex = Vector((sx * 0.052, 0.0, sz * 0.070))
            ids = [mb.vert(p) for p in base]
            ap = mb.vert(apex)
            cen = Vector((0, 0, sz * 0.05))
            for a_, b_ in ((0, 1), (1, 2), (2, 0)):
                mb.face([ids[a_], ids[b_], ap], None, CELL_W, cen)
    return tip_up, tip_dn


# ---------------------------------------------------------------- arrow (tail origin, +Z, 0.5 m)
def build_arrow(mb):
    Sh = L.RECT["shaft"]
    dia = lambda z, r: [Vector((r, 0, z)), Vector((0, r, z)), Vector((-r, 0, z)), Vector((0, -r, z))]
    rings = [dia(0.02, 0.004), dia(0.41, 0.004), dia(0.425, 0.011)]
    tube(mb, rings, rect=Sh, ucols=[0.04, 0.82, 0.86], vcols=[0, 0.25, 0.5, 0.75, 1.0])
    ap = mb.vert((0, 0, 0.5))
    r2 = [mb.vert(p) for p in dia(0.425, 0.011)]
    for j in range(4):
        mb.face([r2[j], r2[(j + 1) % 4], ap], None, CELL_W, Vector((0, 0, 0.45)))
    # four swept back barbs on the head: flat double sided triangles at 45 degrees
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        d = Vector((math.cos(a), math.sin(a), 0.0))
        pts = [d * 0.011 + Vector((0, 0, 0.452)), d * 0.011 + Vector((0, 0, 0.425)), d * 0.027 + Vector((0, 0, 0.402))]
        mb.double(pts, None, CELL_W)
    # chevron fletching: three double sided darts (V shaped, opening toward the tail), 120 degrees apart
    for k in range(3):
        a = k * math.pi / 3.0
        d = Vector((math.cos(a), math.sin(a), 0.0))
        F, Rr, Nn, Ll = d * 0.0 + Vector((0, 0, 0.14)), d * 0.040 + Vector((0, 0, 0.020)), Vector((0, 0, 0.075)), d * -0.040 + Vector((0, 0, 0.020))
        mb.double([F, Ll, Nn, Rr], None, CELL_W)


# ---------------------------------------------------------------- seele long
def build_seele_long(mb):
    R = L.RECT["blade"]
    hx, hy = 0.0025, 0.012
    ztip, zcut = 0.90, 0.875
    P = [(-hx, -hy, 0.0), (hx, -hy, 0.0), (hx, hy, 0.0), (-hx, hy, 0.0),
         (-hx, -hy, zcut), (hx, -hy, zcut), (hx, hy, ztip), (-hx, hy, ztip)]
    ids = [mb.vert(p) for p in P]
    cen = Vector((0, 0, 0.45))

    def uvz(i):
        z = P[i][2]
        return uv_rect(R, z / ztip, (P[i][1] + hy) / (2 * hy))
    for f in ([0, 1, 2, 3], [4, 5, 6, 7], [0, 1, 5, 4], [1, 2, 6, 5], [2, 3, 7, 6], [3, 0, 4, 7]):
        mb.face([ids[i] for i in f], [uvz(i) for i in f], None, cen)
    g = [(0.0, 0.0, 0.040), (0.0, 0.030, 0.062), (0.0, 0.0, 0.085), (0.0, -0.030, 0.062)]
    mb.double(g, None, CELL_W)


# ---------------------------------------------------------------- pentagon edge: a flat 1 m strip with a bead at its start (lies in XY, normal +Z)
def build_pentagon_edge(mb):
    R = L.RECT["pent"]
    q = [(0.0, -0.07, 0.0), (1.0, -0.07, 0.0), (1.0, 0.07, 0.0), (0.0, 0.07, 0.0)]
    mb.face2(q, [uv_rect(R, 0.14, 0), uv_rect(R, 1.0, 0), uv_rect(R, 1.0, 1), uv_rect(R, 0.14, 1)], None, Vector((0.5, 0, -1.0)))
    b = [(-0.06, 0.0, 0.003), (0.0, -0.06, 0.003), (0.06, 0.0, 0.003), (0.0, 0.06, 0.003)]
    mb.face2(b, [uv_rect(R, 0.0, 0.5), uv_rect(R, 0.06, 0.0), uv_rect(R, 0.12, 0.5), uv_rect(R, 0.06, 1.0)], None, Vector((0.0, 0, -1.0)))


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


def empties_table():
    emp = {}
    for side, sx in (("l", 1), ("r", -1)):
        u = L.wing_xyz(L.WING_T0[0], L.WING_T0[1], "spar")
        lo = L.wing_xyz(L.WING_T2[0], L.WING_T2[1], "low3")
        emp["wing_%s_tip_upper" % side] = (sx * u[0], u[1], u[2])
        emp["wing_%s_tip_lower" % side] = (sx * lo[0], lo[1], lo[2])
    emp.update({
        "bow_grip_hand": (0.0, 0.0, 0.0), "bow_string_top": (0.0, L.BOW_TIP_Y, 0.55), "bow_string_bottom": (0.0, L.BOW_TIP_Y, -0.55),
        "arrow_rest": (0.0, -0.009, 0.030),
        "arrow_tip": (0.0, 0.0, 0.5), "needle_tip": (0.0, 0.0, 1.2), "flechette_tip": (0.0, 0.0, 0.2),
        "star_head_tip": (0.0, 0.0, 0.8), "star_head_neck": (0.0, 0.0, -0.43),
        "seele_long_tip": (0.0, 0.012, 0.9), "seele_long_hilt": (0.0, 0.0, 0.0),
        "boot_toe": (0.0, -0.31, 0.03), "pentagon_edge_end": (1.0, 0.0, 0.0),
    })
    return emp


def flex_tips():
    """string empties of the bent bows (rigid bend of the limb tips about the grip ends)"""
    out = {}
    for name, deg in L.FLEX_DEG.items():
        th = math.radians(deg)
        s_, c_ = math.sin(th), math.cos(th)
        dy, dz = L.BOW_TIP_Y, 0.55 - 0.06
        ny, nz = dy * c_ + dz * s_, dz * c_ - dy * s_
        out[name] = {"bow_string_top": [0.0, round(ny, 6), round(0.06 + nz, 6)], "bow_string_bottom": [0.0, round(ny, 6), round(-0.06 - nz, 6)]}
    return out


def build_all(root):
    exp = os.path.join(root, "export", "uryu_fx")
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for me in list(bpy.data.meshes):
        bpy.data.meshes.remove(me)
    coll = bpy.data.collections.get("export") or bpy.data.collections.new("export")
    if coll.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(coll)
    mat = make_material("uryu_fx_atlas", os.path.join(exp, "uryu_fx_diffuse.png"))
    res = {}

    def mk(name, fn):
        mb = MB(name)
        fn(mb)
        res[name] = mb.to_object(mat, coll)

    mk("uryu_wing_l", lambda m: build_wing(m, 1))
    mk("uryu_wing_r", lambda m: build_wing(m, -1))
    mk("uryu_flechette", build_flechette)
    mk("uryu_needle", build_needle)
    mk("uryu_star_head", build_star)
    mk("uryu_bracer", build_bracer)
    mk("uryu_boot", build_boot)
    mk("uryu_bow", lambda m: build_bow(m, 0.0))
    for name, deg in L.FLEX_DEG.items():
        mk(name, lambda m, d=deg: build_bow(m, d))
    mk("uryu_arrow", build_arrow)
    mk("uryu_seele_long", build_seele_long)
    mk("uryu_pentagon_edge", build_pentagon_edge)
    for k, v in empties_table().items():
        empty(k, v, coll)
    return res


# ---------------------------------------------------------------- checks and export
def tri_count(o):
    return sum(len(p.vertices) - 2 for p in o.data.polygons)


def bbox(o):
    vs = [v.co for v in o.data.vertices]
    return [round(min(v[i] for v in vs), 4) for i in range(3)], [round(max(v[i] for v in vs), 4) for i in range(3)]


def signed_volume(o):
    bm = bmesh.new()
    bm.from_mesh(o.data)
    v = bm.calc_volume(signed=True)
    bm.free()
    return v


def table():
    rows = {}
    for name in L.OBJECTS:
        o = bpy.data.objects[name]
        lo, hi = bbox(o)
        rows[name] = {"tris": tri_count(o), "budget": L.BUDGET[name], "target": L.TARGET[name], "within": tri_count(o) <= L.BUDGET[name],
                      "bbox_min": lo, "bbox_max": hi, "verts": len(o.data.vertices), "faces": len(o.data.polygons)}
    return rows


def export_all(root):
    import export_obj as eo
    importlib.reload(eo)
    out = os.path.join(root, "export", "uryu_fx")
    emp = empties_table()
    spec = {"export_objects": list(L.OBJECTS), "export_empties": list(emp.keys())}
    check = eo.export_model("uryu_fx", spec, out)
    meta_path = os.path.join(out, "uryu_fx_meta.json")
    meta = json.load(open(meta_path, encoding="utf-8"))
    meta["wing"] = {
        "root_body_frame": list(L.WING_ROOT_BODY),
        "note": "origin of uryu_wing_l / uryu_wing_r = the shoulder root. Body frame of UryuBalance.WING_TIPS = (sideways, height, backwards); in Blender object space "
                "x = sideways (the _l wing extends +X), z = height, y = BACKWARDS (+Y). A tip empty plus the root gives the WING_TIPS entry: "
                "(|x|, root_height + z, root_back + y).",
        "slot_map": {"0": "wing_r_tip_upper", "1": "wing_l_tip_upper", "2": "wing_r_tip_lower", "3": "wing_l_tip_lower"},
        "WING_TIPS_check": {}}
    wt = [[1.7, 2.35, 0.45], [-1.7, 2.35, 0.45], [1.25, 1.2, 0.6], [-1.25, 1.2, 0.6]]
    names = ["wing_r_tip_upper", "wing_l_tip_upper", "wing_r_tip_lower", "wing_l_tip_lower"]
    rb = L.WING_ROOT_BODY
    for slot, nm in enumerate(names):
        p = meta["empties"][nm]
        got = [round(abs(p[0]) * (1 if slot % 2 == 0 else -1), 6), round(rb[1] + p[2], 6), round(rb[2] + p[1], 6)]
        meta["wing"]["WING_TIPS_check"][str(slot)] = {"empty": nm, "from_mesh": got, "WING_TIPS": wt[slot],
                                                      "match": all(abs(a - b) < 1e-5 for a, b in zip(got, wt[slot]))}
    meta["bow_flex_variants"] = flex_tips()
    json.dump(meta, open(meta_path, "w", encoding="utf-8"), indent=2)
    tb = table()
    for n, row in tb.items():
        check[n]["tris"] = row["tris"]
        check[n]["budget"] = row["budget"]
        check[n]["within_budget"] = row["within"]
    json.dump(check, open(os.path.join(out, "export_check.json"), "w"), indent=1, default=list)
    return {"check": {k: v["match"] for k, v in check.items()}, "tris": {k: v["tris"] for k, v in tb.items()},
            "wing_tips": {k: v["match"] for k, v in meta["wing"]["WING_TIPS_check"].items()}}


if "ROOT" in globals() and globals().get("RUN_BUILD", True):
    build_all(ROOT)
    result = table()
