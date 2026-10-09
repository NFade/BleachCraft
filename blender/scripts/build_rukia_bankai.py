# Phase 3 steps 2-3: detailed rukia_bankai required pieces (geometry + UVs into the 512x512 atlas; the left 256 px column is reserved
# for the deferred costume set). Re-runnable; builds from scratch.
# Objects: rukia_bankai_sword (hilt, bar tsuba, habaki, ice blade; one mesh), rukia_bankai_ribbon_seg / _tip, rukia_bankai_crystal_a..d,
# rukia_bankai_shard_a/_b, rukia_bankai_ice_shell. Gate B: B1, B2, B11 (+ detailing list). Costume set NOT built (deferred by the bible).
import sys, os, math, random, importlib
SD = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bb_common as bb, atlas_layouts as al, detail_common as dc
for _m in (bb, al, dc):
    importlib.reload(_m)
from mathutils import Vector

MODEL = "rukia_bankai"
L = al.RUKIA_BANKAI
OUT = os.path.join(dc.EXPORT, MODEL)
os.makedirs(OUT, exist_ok=True)

bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
mat = dc.atlas_material(MODEL, os.path.join(OUT, MODEL + "_diffuse.png"), os.path.join(OUT, MODEL + "_emissive.png"),
                        tint="#CFEFFF", strength=1.0, blend=True)

# ---- rukia_bankai_sword: tsuka 0.28 (z 0..0.28), bar tsuba z 0.280..0.286, habaki 0.286..0.314, ice blade 0.752 m to z 1.066
mb = dc.MB()
dc.build_hilt(mb, L, tsuka_len=0.28, n_rings=al.BANKAI_WRAP_RINGS)          # B2 ovals are the build_hilt defaults
outline, holes = al.bankai_tsuba_shapes()                                    # B11
dc.plate(mb, outline, holes, 0.280, 0.286, 0.0008, L, L["tsuba_front"], L["tsuba_back"], L["tsuba_rim"], L["tsuba_win"],
         al.BANKAI_TSUBA_PX_PER_M)
dc.cprism(mb, 0.007, 0.016, 0.001, 0.286, 0.314, 0.0, L["habaki_side"], L["habaki_top"], al.BANKAI_HABAKI_PX_PER_M, k0=3)
apex = dc.blade_mesh(mb, L, 0.314, 1.066 - 0.314, 0.028, 0.020, 0.012, 0.005, 0.070, 0.008, al.BLADE_RINGS_BODY, 300.0)   # B1 via SORI_K
sword = mb.finish("rukia_bankai_sword", mat, exp)

# ---- ribbons: hinge at the origin, mesh along -Z, 2 mm thick, front and back share the cell (double sided strip)
SEG, W, T = 0.35, 0.070, 0.002


def ribbon(name, cell, tip, loc):
    m = dc.MB()
    if not tip:
        pts = [(-W / 2, 0.0), (W / 2, 0.0), (W / 2, -SEG), (-W / 2, -SEG)]
        uv = [cell.uv(0.0, 0.0), cell.uv(0.0, 1.0), cell.uv(1.0, 1.0), cell.uv(1.0, 0.0)]
    else:
        pts = [(-W / 2, 0.0), (W / 2, 0.0), (0.0, -SEG)]
        uv = [cell.uv(0.0, 0.0), cell.uv(0.0, 1.0), cell.uv(1.0, 0.5)]
    A = [m.v(Vector((x, T / 2, z))) for x, z in pts]
    B = [m.v(Vector((x, -T / 2, z))) for x, z in pts]
    m.face(A, uv)
    m.face(B, uv)
    if not tip:
        # corner order: 0 (-,start) 1 (+,start) 2 (+,end) 3 (-,end); side walls collapse on the bottom / top row, end walls on the
        # first / last texel column
        m.face([A[0], A[3], B[3], B[0]], [cell.uv(0.0, 0.0), cell.uv(1.0, 0.0), cell.uv(1.0, 0.0), cell.uv(0.0, 0.0)])
        m.face([A[1], A[2], B[2], B[1]], [cell.uv(0.0, 1.0), cell.uv(1.0, 1.0), cell.uv(1.0, 1.0), cell.uv(0.0, 1.0)])
        m.face([A[0], A[1], B[1], B[0]], [cell.uv(0.02, 0.5)] * 4)
        m.face([A[3], A[2], B[2], B[3]], [cell.uv(0.98, 0.5)] * 4)
    else:
        # walls collapse onto the slanted painted edge lines (left (0,0)->(1,.5), right (0,1)->(1,.5)) and the start column
        m.face([A[0], A[2], B[2], B[0]], [cell.uv(0.0, 0.0), cell.uv(1.0, 0.5), cell.uv(1.0, 0.5), cell.uv(0.0, 0.0)])
        m.face([A[1], A[2], B[2], B[1]], [cell.uv(0.0, 1.0), cell.uv(1.0, 0.5), cell.uv(1.0, 0.5), cell.uv(0.0, 1.0)])
        m.face([A[0], A[1], B[1], B[0]], [cell.uv(0.02, 0.5)] * 4)
    o = m.finish(name, mat, exp)
    o.location = loc
    return o


rseg = ribbon("rukia_bankai_ribbon_seg", L["ribbon_seg"], False, (0.30, 0.0, 0.40))
rtip = ribbon("rukia_bankai_ribbon_tip", L["ribbon_tip"], True, (0.45, 0.0, 0.40))


# ---- crystals: hexagonal prisms with a pointed tip, base radius = height / 6, seeded +-8 % vertex jitter, one bent variant (c)
def crystal(key, loc):
    h, bend = al.CRYSTAL_H[key], al.CRYSTAL_BEND[key]
    fr, ks = al.CRYSTAL_RINGS[key]
    cell = L["crystal_" + key]
    rng = random.Random(al.CRYSTAL_SEED[key])
    r = h / 6.0
    colj = [rng.uniform(-0.05, 0.05) for _ in range(6)]
    colang = [rng.uniform(-0.06, 0.06) for _ in range(6)]
    m = dc.MB()
    rings = []
    for z_f, k in zip(fr, ks):
        z = z_f * h
        sh = bend * h * (z_f) ** 2
        ring = []
        for i in range(6):
            jr = 1.0 + colj[i] + (0.0 if z_f == 0.0 else rng.uniform(-0.03, 0.03))
            a = math.pi / 3 * i + colang[i]
            ring.append(m.v(Vector((k * r * jr * math.cos(a), k * r * jr * math.sin(a) + sh, z))))
        rings.append(ring)
    ap = m.v(Vector((rng.uniform(-0.05, 0.05) * r, bend * h + rng.uniform(-0.05, 0.05) * r, h)))
    for i in range(len(rings) - 1):
        for k in range(6):
            k1 = (k + 1) % 6
            f0, f1 = k / 6.0, (k + 1) / 6.0
            m.face([rings[i][k], rings[i][k1], rings[i + 1][k1], rings[i + 1][k]],
                   [cell.uv(f0, fr[i]), cell.uv(f1, fr[i]), cell.uv(f1, fr[i + 1]), cell.uv(f0, fr[i + 1])])
    top = len(rings) - 1
    for k in range(6):
        k1 = (k + 1) % 6
        m.face([rings[top][k], rings[top][k1], ap],
               [cell.uv(k / 6.0, fr[top]), cell.uv((k + 1) / 6.0, fr[top]), cell.uv((k + 0.5) / 6.0, 1.0)])
    m.face(rings[0], [cell.uv(0.5, 0.01)] * 6)         # base cap, always buried: collapsed on one texel
    o = m.finish("rukia_bankai_crystal_" + key, mat, exp)
    o.location = loc
    return o


cr = [crystal("a", (0.80, 0.0, 0.0)), crystal("b", (1.00, 0.0, 0.0)), crystal("c", (1.30, 0.0, 0.0)), crystal("d", (1.80, 0.0, 0.0))]


# ---- shards: 3 mm triangular prisms, 8 tris, origin = centroid
def shard(key, loc):
    tri = al.bankai_shard_tri(key)
    t = 0.0015
    m = dc.MB()
    SV = [m.v(Vector((-t, y, z))) for y, z in tri] + [m.v(Vector((t, y, z))) for y, z in tri]
    FI, SI = L["shard_%s_face" % key], L["shard_%s_side" % key]
    fuv = [al.bankai_shard_face_uv(FI, key, y, z) for y, z in tri]
    m.face([SV[0], SV[1], SV[2]], fuv)
    m.face([SV[3], SV[4], SV[5]], fuv)
    for k in range(3):
        k1 = (k + 1) % 3
        f0, f1 = k / 3.0, (k + 1) / 3.0
        m.face([SV[k], SV[k1], SV[3 + k1], SV[3 + k]], [SI.uv(f0, 0.0), SI.uv(f0, 1.0), SI.uv(f1, 1.0), SI.uv(f1, 0.0)])
    o = m.finish("rukia_bankai_shard_" + key, mat, exp)
    o.location = loc
    return o


sh = [shard("a", (2.20, 0.0, 0.06)), shard("b", (2.35, 0.0, 0.04))]

# ---- ice shell: 1.0 x 1.0 x 2.0 m, origin at the bottom centre, 10 sides, 3 body rings + 2 end rings, seeded irregular facets
rng = random.Random(7)
levels = [(0.0, 0.30), (0.30, 0.46), (1.00, 0.50), (1.70, 0.46), (2.0, 0.30)]
pts = []
for ri, (z, rr) in enumerate(levels):
    ring = []
    end = ri in (0, len(levels) - 1)
    for i in range(10):
        a = 2 * math.pi * i / 10 + (0.16 if end else 0.10) * (rng.random() - 0.3)
        rad = rr * (1.0 + (0.16 if end else 0.12) * (rng.random() - 0.5))      # second, wider seeded pass on the end rings
        zz = z if end else z + 0.05 * (rng.random() - 0.5)
        ring.append((rad * math.cos(a), rad * math.sin(a), zz))
    pts.append(ring)
xs = [p[0] for r in pts for p in r]
ys = [p[1] for r in pts for p in r]
x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
pts = [[((p[0] - x0) / (x1 - x0) - 0.5, (p[1] - y0) / (y1 - y0) - 0.5, p[2]) for p in r] for r in pts]   # bbox exactly 1.0 x 1.0 (x, y), centred
m = dc.MB()
SB = L["shell_body"]
V = [[m.v(Vector(p)) for p in r] for r in pts]
for i in range(len(pts) - 1):
    for k in range(10):
        k1 = (k + 1) % 10
        f0, f1, g0, g1 = k / 10.0, (k + 1) / 10.0, i / 4.0, (i + 1) / 4.0
        m.face([V[i][k], V[i][k1], V[i + 1][k1], V[i + 1][k]], [SB.uv(f0, g0), SB.uv(f1, g0), SB.uv(f1, g1), SB.uv(f0, g1)])
for ri, key in ((0, "shell_bot"), (len(pts) - 1, "shell_top")):
    isl = L[key]
    m.face(V[ri], [dc.planar_uv(isl, 50.0, p[0], p[1]) for p in pts[ri]])
shell = m.finish("rukia_bankai_ice_shell", mat, exp)
shell.location = (3.00, 0.0, 0.0)

emp = dc.set_empties(exp, [("grip_hand", (0, 0, 0.22)), ("tip", tuple(apex)), ("ribbon_root", (0, 0.12, 0.90))])
allobj = [sword, rseg, rtip] + cr + sh + [shell]
for o in allobj:
    o.data.update()
result = {"apex": tuple(apex), "tris": {o.name: sum(len(p.vertices) - 2 for p in o.data.polygons) for o in allobj}}
