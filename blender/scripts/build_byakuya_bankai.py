# Phase 3 steps 2-3: detailed byakuya_bankai (geometry + UVs into the 512x512 atlas). Re-runnable; builds from scratch.
# Objects (ART_BIBLE 1.6, Gate B B2 + B12..B16 + detailing list): byakuya_bankai_blade (giant blade, instanced, 8.0 m), byakuya_bankai_blade_lod,
# byakuya_bankai_hilt_ground (byakuya_shikai hilt + 0.235 m stub, rotated 180 degrees about Y), byakuya_bankai_ripple, hakuteiken_blade_body /
# _tip, hakuteiken_wing_l / _r (one continuous sheet each, plus one barb per lobe), hakuteiken_halo. Senkei sword NOT built (deferred).
# Scene layout offsets (B3): blade at the origin, LOD x 1.5, ground hilt x 3, ripple x 5, Hakuteiken blade x 8 (tip on it at z 1.0), wings
# (14, 0, 1), halo (14, 0.3, 4.2).
import sys, os, math, importlib
SD = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bb_common as bb, atlas_layouts as al, detail_common as dc
for _m in (bb, al, dc):
    importlib.reload(_m)
from mathutils import Vector, Matrix

MODEL = "byakuya_bankai"
L = al.BYAKUYA_BANKAI
OUT = os.path.join(dc.EXPORT, MODEL)
os.makedirs(OUT, exist_ok=True)

bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
mat = dc.atlas_material(MODEL, os.path.join(OUT, MODEL + "_diffuse.png"), os.path.join(OUT, MODEL + "_emissive.png"),
                        tint="#F2E9FF", strength=1.0, cutout=True)      # preview: dithered alpha (wing cut-outs; BLENDED sorts the opaque blade badly)

# ---------------------------------------------------------------------------------------------------- giant blade (+ LOD)
KIND = {("P0", "S+"): "A", ("S+", "P1"): "A", ("P0", "P1"): "A", ("P1", "P2"): "A", ("P2", "P3"): "S", ("P3", "P4"): "B",
        ("P4", "S-"): "B", ("S-", "P5"): "B", ("P4", "P5"): "B", ("P5", "P0"): "E",
        ("P0", "P2"): "A", ("P3", "P5"): "B"}                      # the last two are the LOD (4-vertex rings)


def blade_uv(kind, role, s):
    fr = al.GB_FR[role]
    if kind == "A":
        return L["blade_a"].uv(fr, s)
    if kind == "B":
        return L["blade_b"].uv(fr, s)
    if kind == "S":
        return L["blade_s"].uv(0.0 if role == "P2" else 1.0, s)
    return L["blade_e"].uv(0.0 if role == "P5" else 1.0, s)


def gb_ring(mb, z, w, ys, roles, prof=None):
    t = al.gb_thick(z)
    if prof is None:
        prof = al.gb_profile(w, t, len(roles))
    pts = bb.ring_pts([(x, y + ys) for x, y in prof], z, al.gb_dev(z), al.gb_dphi(z))
    return [mb.v(p) for p in pts]


def seg_faces(mb, lo, hi, roles_lo, roles_hi, s0, s1):
    """Faces between two rings with the same roles (quads) or a 6 -> 8 transition (face P0-P1 -> 3 tris, face P4-P5 -> 3 tris)."""
    if len(roles_lo) == len(roles_hi):
        n = len(roles_lo)
        for k in range(n):
            k1 = (k + 1) % n
            kd = KIND[(roles_lo[k], roles_lo[k1])]
            uvs = [blade_uv(kd, roles_lo[k], s0), blade_uv(kd, roles_lo[k1], s0), blade_uv(kd, roles_hi[k1], s1), blade_uv(kd, roles_hi[k], s1)]
            mb.face([lo[k], lo[k1], hi[k1], hi[k]], uvs)
        return
    # 6 -> 8: lower ring roles P0 P1 P2 P3 P4 P5, upper ring P0 S+ P1 P2 P3 P4 S- P5
    ul = {r: v for r, v in zip(roles_lo, lo)}
    uh = {r: v for r, v in zip(roles_hi, hi)}

    def uv(kd, role, s):
        return blade_uv(kd, role, s)

    for r0, r1 in (("P1", "P2"), ("P2", "P3"), ("P3", "P4"), ("P5", "P0")):
        kd = KIND[(r0, r1)]
        mb.face([ul[r0], ul[r1], uh[r1], uh[r0]], [uv(kd, r0, s0), uv(kd, r1, s0), uv(kd, r1, s1), uv(kd, r0, s1)])
    for a, m, b, kd in (("P0", "S+", "P1", "A"), ("P4", "S-", "P5", "B")):
        # pentagon (lo a, lo b, hi b, hi m, hi a) as 3 explicit tris fanned from hi m
        la, lb, ha, hm, hb_ = ul[a], ul[b], uh[a], uh[m], uh[b]
        ua0, ub0, ua1, um1, ub1 = uv(kd, a, s0), uv(kd, b, s0), uv(kd, a, s1), uv(kd, m, s1), uv(kd, b, s1)
        mb.face([hm, ha, la], [um1, ua1, ua0])
        mb.face([hm, la, lb], [um1, ua0, ub0])
        mb.face([hm, lb, hb_], [um1, ub0, ub1])


mb = dc.MB()
rings, roles_l, svals = [], [], []
for z, w, ys, nv in al.GB_STATIONS:
    roles = al.GB_ROLES6 if nv == 6 else al.GB_ROLES8
    rings.append(gb_ring(mb, z, w, ys, roles))
    roles_l.append(roles)
    svals.append(al.gb_arc(z))
for i in range(len(rings) - 1):
    seg_faces(mb, rings[i], rings[i + 1], roles_l[i], roles_l[i + 1], svals[i], svals[i + 1])
apex_p = bb.ring_pts([(0.0, 0.08)], al.GB_H, al.gb_dev(al.GB_H), al.gb_dphi(al.GB_H))[0]
apex_p.z = al.GB_H                                                  # Gate B B13: tip apex at world z = 8.000, y kept
apex = mb.v(apex_p)
top, rt = rings[-1], roles_l[-1]
for k in range(len(rt)):
    k1 = (k + 1) % len(rt)
    kd = KIND[(rt[k], rt[k1])]
    a, b = blade_uv(kd, rt[k], svals[-1]), blade_uv(kd, rt[k1], svals[-1])
    mb.face([top[k], top[k1], apex], [a, b, ((a[0] + b[0]) / 2.0, blade_uv(kd, rt[k], 1.0)[1])])
mb.face(rings[0], [L["blade_a"].uv(0.5, 0.015)] * 6)                # buried bottom cap, collapsed on a soil texel
giant = mb.finish("byakuya_bankai_blade", mat, exp)

# LOD: 3 rings x 4 vertices (z 0, 4.5, 7.1) + the same apex, flat slab, same island layout
mb = dc.MB()
lroles = ["P0", "P2", "P3", "P5"]
lrings, ls = [], []
for z in al.GB_LOD_Z:
    t = al.gb_thick(z)
    prof = [(t / 2, -al.GB_W / 2), (t / 2, al.GB_W / 2), (-t / 2, al.GB_W / 2), (-t / 2, -al.GB_W / 2)]
    lrings.append(gb_ring(mb, z, al.GB_W, 0.0, lroles, prof))
    ls.append(al.gb_arc(z))
for i in range(2):
    seg_faces(mb, lrings[i], lrings[i + 1], lroles, lroles, ls[i], ls[i + 1])
lap = mb.v(apex_p)
for k in range(4):
    k1 = (k + 1) % 4
    kd = KIND[(lroles[k], lroles[k1])]
    a, b = blade_uv(kd, lroles[k], ls[-1]), blade_uv(kd, lroles[k1], ls[-1])
    mb.face([lrings[-1][k], lrings[-1][k1], lap], [a, b, ((a[0] + b[0]) / 2.0, blade_uv(kd, lroles[k], 1.0)[1])])
mb.face(lrings[0], [L["blade_a"].uv(0.5, 0.015)] * 4)
lod = mb.finish("byakuya_bankai_blade_lod", mat, exp)
lod.location = (1.5, 0.0, 0.0)

# ---------------------------------------------------------------------------------------------------- ground hilt
# byakuya_shikai hilt (24 rings x 12 sides, 11 raised diamonds, window tsuba, habaki with the 1 mm collar) built kashira-down at z 0..0.285,
# a 32 x 10 mm blade stub z 0.285..0.520 with a flat cut end, then rotated 180 degrees about Y (rotated, not mirrored) and moved up 0.52:
# kashira up, stub bottom at z 0, edge stays -Y (B14). Wrap, tsuba etc. use the same texel layout as byakuya_shikai.
Z_TSUBA0, Z_TSUBA1 = 0.250, 0.257
Z_HAB0, Z_HAB1 = al.SHIKAI_HAB0, al.SHIKAI_HAB1
mb = dc.MB()
dc.build_hilt(mb, L, n_rings=al.BYAKUYA_WRAP_RINGS)
outline, holes = al.byakuya_tsuba_shapes()
dc.plate(mb, outline, holes, Z_TSUBA0, Z_TSUBA1, 0.0008, L, L["tsuba_front"], L["tsuba_back"], L["tsuba_rim"], L["tsuba_win"],
         al.BYAKUYA_TSUBA_PX_PER_M)
dc.cprism(mb, 0.005, 0.016, 0.001, Z_HAB0, Z_HAB1, 0.001, L["habaki_side"], L["habaki_top"], 430.0, k0=3)
# stub: 2 shinogi rings, root cap hidden by the habaki, end cap visible (planar on its own small island)
Z_END = 0.520
sprof = bb.shinogi_profile(0.032, 0.010, edge=0.0005)
sr = [[mb.v(p) for p in bb.ring_pts(sprof, z)] for z in (Z_HAB1, Z_END)]
sfr = [(y + 0.016) / 0.032 for _, y in sprof]
sk = ["stub_a", "stub_a", "stub_s", "stub_b", "stub_b", "stub_e"]
for k in range(6):
    k1 = (k + 1) % 6
    isl = L[sk[k]]
    if sk[k] in ("stub_a", "stub_b"):
        cu = (sfr[k], sfr[k1])
    elif sk[k] == "stub_s":
        cu = (0.0, 1.0)
    else:
        cu = (0.0, 1.0)
    if k == 5:
        cu = (0.0, 1.0)
    mb.face([sr[0][k], sr[0][k1], sr[1][k1], sr[1][k]], [isl.uv(cu[0], 0.0), isl.uv(cu[1], 0.0), isl.uv(cu[1], 1.0), isl.uv(cu[0], 1.0)])
mb.face(sr[0], [L["stub_a"].uv(0.5, 0.0)] * 6, False, True)
mb.face(sr[1], [dc.planar_uv(L["stub_cap"], 200.0, p.co.x, p.co.y) for p in sr[1]])
hilt = mb.finish("byakuya_bankai_hilt_ground", mat, exp)
hilt.data.transform(Matrix.Translation((0, 0, 0.52)) @ Matrix.Rotation(math.pi, 4, 'Y'))
hilt.data.update()
hilt.location = (3.0, 0.0, 0.0)

# ---------------------------------------------------------------------------------------------------- ripple (single sided +Z)
nseg = 16
mb = dc.MB()
RO, RI, RZ = 1.00, 0.88, 0.02
vo = [mb.v(Vector((RO * math.cos(2 * math.pi * k / nseg), RO * math.sin(2 * math.pi * k / nseg), RZ))) for k in range(nseg)]
vi = [mb.v(Vector((RI * math.cos(2 * math.pi * k / nseg), RI * math.sin(2 * math.pi * k / nseg), RZ))) for k in range(nseg)]
R_ = L["ripple"]
for k in range(nseg):
    k1 = (k + 1) % nseg
    mb.face([vo[k], vo[k1], vi[k1], vi[k]], [R_.uv(k / nseg, 1.0), R_.uv((k + 1) / nseg, 1.0), R_.uv((k + 1) / nseg, 0.0), R_.uv(k / nseg, 0.0)])
ripple = mb.finish("byakuya_bankai_ripple", mat, exp, recalc=False)
ripple.location = (5.0, 0.0, 0.0)

# ---------------------------------------------------------------------------------------------------- Hakuteiken blade body + tip
def perim_fracs(prof):
    n = len(prof)
    cum = [0.0]
    for k in range(n):
        a, b = prof[k], prof[(k + 1) % n]
        cum.append(cum[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    return [c / cum[-1] for c in cum]


mb = dc.MB()
bp = bb.shinogi_profile(0.10, 0.03, edge=0.002)
bf = perim_fracs(bp)
HB = L["hk_body"]
r0 = [mb.v(p) for p in bb.ring_pts(bp, 0.0)]
r1 = [mb.v(p) for p in bb.ring_pts(bp, 1.0)]
for k in range(6):
    k1 = (k + 1) % 6
    mb.face([r0[k], r0[k1], r1[k1], r1[k]], [HB.uv(bf[k], 0.0), HB.uv(bf[k + 1], 0.0), HB.uv(bf[k + 1], 1.0), HB.uv(bf[k], 1.0)])
mb.face(r0, [HB.uv(0.5, 0.5)] * 6)
mb.face(r1, [HB.uv(0.5, 0.5)] * 6)
hb = mb.finish("hakuteiken_blade_body", mat, exp)
hb.location = (8.0, 0.0, 0.0)

mb = dc.MB()
HT = L["hk_tip"]
trs = [(0.0, 0.10, 0.03), (0.08, 0.09, 0.027), (0.15, 0.06, 0.02)]
tp = [bb.shinogi_profile(w, t, edge=0.002) for _, w, t in trs]
tf = perim_fracs(tp[0])
tr = [[mb.v(p) for p in bb.ring_pts(tp[i], trs[i][0])] for i in range(3)]
tapex = mb.v(Vector((0.0, 0.017, 0.20)))
for i in range(2):
    for k in range(6):
        k1 = (k + 1) % 6
        z0, z1 = trs[i][0] / 0.20, trs[i + 1][0] / 0.20
        mb.face([tr[i][k], tr[i][k1], tr[i + 1][k1], tr[i + 1][k]], [HT.uv(tf[k], z0), HT.uv(tf[k + 1], z0), HT.uv(tf[k + 1], z1), HT.uv(tf[k], z1)])
for k in range(6):
    k1 = (k + 1) % 6
    z0 = trs[2][0] / 0.20
    mb.face([tr[2][k], tr[2][k1], tapex], [HT.uv(tf[k], z0), HT.uv(tf[k + 1], z0), HT.uv((tf[k] + tf[k + 1]) / 2.0, 1.0)])
mb.face(tr[0], [HT.uv(0.5, 0.0)] * 6, False, True)                 # base cap hidden by the body
tip_o = mb.finish("hakuteiken_blade_tip", mat, exp)
tip_o.location = (8.0, 0.0, 1.0)

# ---------------------------------------------------------------------------------------------------- wings (single sided, +Y) and halo
wings = []
for sx, name in ((1, "hakuteiken_wing_l"), (-1, "hakuteiken_wing_r")):
    mb = dc.MB()
    V, F, info = al.wing_geometry(sx)
    bv = [mb.v(Vector(p)) for p in V]
    for tri in F:
        mb.face([bv[k] for k in tri], [al.wing_uv(L["wing"], V[k][0], V[k][2]) for k in tri])
    w_ = mb.finish(name, mat, exp, recalc=False)
    w_.location = (14.0, 0.0, 1.0)
    wings.append(w_)

mb = dc.MB()
nh = 24
HL = L["halo"]
ho = [mb.v(Vector((1.10 * math.cos(2 * math.pi * k / nh), 0.0, 1.10 * math.sin(2 * math.pi * k / nh)))) for k in range(nh)]
hi_ = [mb.v(Vector((0.94 * math.cos(2 * math.pi * k / nh), 0.0, 0.94 * math.sin(2 * math.pi * k / nh)))) for k in range(nh)]   # Gate C C4: inner radius 1.00 -> 0.94
for k in range(nh):
    k1 = (k + 1) % nh
    mb.face([ho[k], hi_[k], hi_[k1], ho[k1]], [HL.uv(k / nh, 1.0), HL.uv(k / nh, 0.0), HL.uv((k + 1) / nh, 0.0), HL.uv((k + 1) / nh, 1.0)])
halo = mb.finish("hakuteiken_halo", mat, exp, recalc=False)
halo.location = (14.0, 0.3, 4.2)

allobj = [giant, lod, hilt, ripple, hb, tip_o] + wings + [halo]
for o in allobj:
    o.data.update()
emp = dc.set_empties(exp, [("tip", tuple(apex_p))])
result = {"apex": tuple(apex_p), "tris": {o.name: sum(len(p.vertices) - 2 for p in o.data.polygons) for o in allobj}}
