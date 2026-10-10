# Phase 3 detailing helpers (Blender 5.2.2, bpy): meshes are generated with explicit UVs into a single 256x256 atlas
# (islands from atlas_layouts.py), shared by build_rukia_sealed.py / build_rukia_shikai.py.
#   sys.path.insert(0, r"D:\MineBleach\blender\scripts"); import detail_common as dc
import bpy, bmesh, math, os, json
from mathutils import Vector, geometry
import bb_common as bb
from atlas_layouts import Isl, ATLAS

ROOT = bb.ROOT
EXPORT = os.path.join(ROOT, "export")
SCENES = os.path.join(ROOT, "scenes")
RENDERS = os.path.join(ROOT, "renders")


# ------------------------------------------------------------------ mesh builder with per-corner UVs
class MB:
    """bmesh builder. Build every part as a CLOSED shell (so recalc_face_normals is reliable), tag faces hidden by a
    touching part (hide=True); finish() recalculates the normals outward and then deletes the tagged faces."""

    def __init__(self):
        self.bm = bmesh.new()
        self.uvl = self.bm.loops.layers.uv.new("UVMap")
        self.hide = []

    def v(self, co):
        return self.bm.verts.new(co)

    def face(self, verts, uvs, smooth=False, hide=False):
        f = self.bm.faces.new(verts)
        for lp, uv in zip(f.loops, uvs):
            lp[self.uvl].uv = uv
        f.smooth = smooth
        if hide:
            self.hide.append(f)
        return f

    def finish(self, name, mat, collection=None, recalc=True):
        """recalc=False keeps the winding given at construction (single-sided open sheets: wings, halo, ripple)."""
        bm = self.bm
        if recalc:
            bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        hid = [f for f in self.hide if f.is_valid]
        if hid:
            bmesh.ops.delete(bm, geom=hid, context='FACES')
        loose = [v for v in bm.verts if not v.link_faces]
        if loose:
            bmesh.ops.delete(bm, geom=loose, context='VERTS')
        me = bpy.data.meshes.new(name)
        bm.to_mesh(me)
        bm.free()
        me.materials.append(mat)
        o = bpy.data.objects.new(name, me)
        (collection or bpy.context.scene.collection).objects.link(o)
        return o


def arc_fracs(pts, k0):
    """cumulative normalised arc length of a closed ring starting at vertex k0; entry j <-> vertex (k0 + j) % n, j = 0..n."""
    n = len(pts)
    cum = [0.0]
    for j in range(1, n + 1):
        a, b = pts[(k0 + j - 1) % n], pts[(k0 + j) % n]
        cum.append(cum[-1] + (Vector(b) - Vector(a)).length)
    return [c / cum[-1] for c in cum]


def tube(mb, rings, k0, isl, fy, smooth=False, cap0=None, cap1=None):
    """Quad tube between rings (lists of Vector, equal count). u = arc length fraction from vertex k0 (the seam), v = fy[i]
    (0..1 bottom to top inside `isl`). cap0 / cap1: None (no face) or (island, px_per_m, hide) -> planar n-gon cap."""
    n = len(rings[0])
    vs = [[mb.v(p) for p in r] for r in rings]
    fr = [arc_fracs(r, k0) for r in rings]
    for i in range(len(rings) - 1):
        for k in range(n):
            j = (k - k0) % n
            uvs = [isl.uv(fr[i][j], fy[i]), isl.uv(fr[i][j + 1], fy[i]), isl.uv(fr[i + 1][j + 1], fy[i + 1]), isl.uv(fr[i + 1][j], fy[i + 1])]
            mb.face([vs[i][k], vs[i][(k + 1) % n], vs[i + 1][(k + 1) % n], vs[i + 1][k]], uvs, smooth)
    for ring_i, cap in ((0, cap0), (len(rings) - 1, cap1)):
        if cap is not None:
            ci, s, hide = cap
            if ci is None:
                continue
            cx = sum(p.x for p in rings[ring_i]) / n
            cy = sum(p.y for p in rings[ring_i]) / n
            uvs = [planar_uv(ci, s, p.x, p.y, cx=cx, cy=cy) for p in rings[ring_i]]
            mb.face(vs[ring_i], uvs, False, hide)
    return vs


def planar_uv(isl, px_per_m, x, y, flip_x=False, cx=0.0, cy=0.0):
    fx = 0.5 + ((-(x - cx) if flip_x else (x - cx)) * px_per_m) / isl.w
    fy = 0.5 + ((y - cy) * px_per_m) / isl.h
    return isl.uv(fx, fy)


def scaled(pts, sx, sy):
    return [(x * sx, y * sy) for x, y in pts]


def ring_at(pts2, z, **kw):
    return bb.ring_pts(pts2, z, **kw)


# ------------------------------------------------------------------ hilt (kashira, tsuka-ito, fuchi)
def build_hilt(mb, L, kashira_dims=(0.011, 0.015, 0.014), tsuka_dims=(0.012, 0.015), fuchi_dims=(0.013, 0.016),
               fuchi_len=0.014, tsuka_len=0.25, swell=0.003, ridge=0.0015, n_rings=20, wrap_sides=12, fit_sides=16):
    """Gate B detailing: tsuka 20 rings x 12 sides, odd rings 1..17 are 9 raised diamonds (+1.5 mm), 3 mm swell in the middle
    third; kashira and fuchi 16 sides with one chamfer. Hidden faces: kashira top cap, tsuka top cap, fuchi bottom/top caps.
    The tsuka bottom cap stays (the tsuka is 2 mm wider than the kashira along X); its UV is collapsed on a wrap texel."""
    ka, kb, kh = kashira_dims
    # kashira: bottom chamfer ring, full ring, top ring (cap hidden by the tsuka)
    kr = [[Vector((x * 0.88, y * 0.88, 0.0)) for x, y in bb.oval(ka, kb, fit_sides)],
          [Vector((x, y, 0.0018)) for x, y in bb.oval(ka, kb, fit_sides)],
          [Vector((x, y, kh)) for x, y in bb.oval(ka, kb, fit_sides)]]
    tube(mb, kr, fit_sides // 4, L["kashira_side"], [0.0, 0.2, 1.0], False,
         cap0=(L["kashira_cap"], 540.0, False), cap1=(L["kashira_cap"], 540.0, True))
    # tsuka
    z0, z1 = kh, tsuka_len - fuchi_len
    a, b = tsuka_dims
    rings = []
    for i in range(n_rings):
        u = i / (n_rings - 1)
        z = bb.lerp(z0, z1, u)
        mid = math.sin(math.pi * (u - 1 / 3) * 3) if 1 / 3 <= u <= 2 / 3 else 0.0
        sw = swell / 2.0 * mid + (ridge if (i % 2 == 1 and i <= n_rings - 3) else 0.0)
        rings.append([Vector((x, y, z)) for x, y in bb.oval(a + sw, b + sw, wrap_sides)])
    fy = [i / (n_rings - 1) for i in range(n_rings)]
    wrap = L["wrap"]
    # bottom cap collapsed on a wrap texel (annulus 1 mm wide at +-X)
    collapse = (wrap, 1e-9, False)
    vs = tube(mb, rings, wrap_sides // 4, wrap, fy, True, cap0=None, cap1=(wrap, 1e-9, True))
    uv0 = wrap.uv(0.5, 0.0)
    mb.face(vs[0], [uv0] * wrap_sides, False, False)
    # fuchi: starts at the tsuka end size, steps out over 1.5 mm (chamfer), then straight to the tsuba
    fa, fb = fuchi_dims
    ta, tb = a, b
    fr = [[Vector((x, y, z1)) for x, y in bb.oval(ta, tb, fit_sides)],
          [Vector((x, y, z1 + 0.0015)) for x, y in bb.oval(fa, fb, fit_sides)],
          [Vector((x, y, tsuka_len)) for x, y in bb.oval(fa, fb, fit_sides)]]
    tube(mb, fr, fit_sides // 4, L["fuchi_side"], [0.0, 0.18, 1.0], False,
         cap0=(L["fuchi_side"], 1e-9, True), cap1=(L["fuchi_side"], 1e-9, True))


# ------------------------------------------------------------------ chamfered prism (habaki, knot)
def octagon(hx, hy, c):
    return [(hx - c, -hy), (hx, -hy + c), (hx, hy - c), (hx - c, hy), (-hx + c, hy), (-hx, hy - c), (-hx, -hy + c), (-hx + c, -hy)]


def inset_poly(pts, d):
    """Inset a CCW polygon by d (miter)."""
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1, p2 = Vector(pts[i - 1]), Vector(pts[i]), Vector(pts[(i + 1) % n])
        e1, e2 = (p1 - p0).normalized(), (p2 - p1).normalized()
        n1, n2 = Vector((-e1.y, e1.x)), Vector((-e2.y, e2.x))
        m = (n1 + n2)
        den = 1.0 + n1.dot(n2)
        off = m * (d / max(den, 0.5))
        out.append((p1.x + off.x, p1.y + off.y))
    return out


def cprism(mb, hx, hy, c, z_hidden, z_free, bev, isl_side, isl_cap, cap_px_per_m, k0=0, hide_cap=True, hide_top=False):
    """Octagonal prism from z_hidden (cap deleted) to z_free (visible cap; with bev > 0 an inset bevel ring first).
    u along the perimeter, v along the height."""
    pl = octagon(hx, hy, c)
    sgn = 1.0 if z_free > z_hidden else -1.0
    span = abs(z_free - z_hidden)
    rings = [[Vector((x, y, z_hidden)) for x, y in pl]]
    fy = [0.0]
    if bev:
        rings.append([Vector((x, y, z_free - sgn * bev)) for x, y in pl])
        fy.append((span - bev) / span)
        rings.append([Vector((x, y, z_free)) for x, y in inset_poly(pl, bev)])
    else:
        rings.append([Vector((x, y, z_free)) for x, y in pl])
    fy.append(1.0)
    tube(mb, rings, k0, isl_side, fy, False, cap0=(isl_cap, 1e-9, hide_cap), cap1=(isl_cap, cap_px_per_m, hide_top))


# ------------------------------------------------------------------ blade
def blade_mesh(mb, L, z0, length, root_w, yok_w, root_t, tip_t, kissaki, sori_chord, n_mid, px_per_m, root_cap_hidden=True):
    """Shinogi-zukuri blade, flat shaded: n_mid + 1 body rings (the last is the yokote ring) + 1 kissaki ring + apex.
    UV: strips blade_a (+X faces 0, 1), blade_b (-X faces 3, 4), blade_s (spine), blade_e (edge flat); u = fraction across the
    ring width (edge 0 -> spine 1), v = distance along the blade. Returns the apex position."""
    A, B, S, E = L["blade_a"], L["blade_b"], L["blade_s"], L["blade_e"]
    tip_off = bb.SORI_K * sori_chord
    z_tip = z0 + length
    z_yk = z_tip - kissaki
    data = []   # (s, ring Vectors, profile)

    def add(z, prof):
        data.append(((z - z0) / length, bb.ring_pts(prof, z, bb.sori_off(z, z0, length, tip_off), bb.sori_phi(z, z0, length, tip_off)), prof))

    for i in range(n_mid + 1):
        z = bb.lerp(z0, z_yk, i / n_mid)
        u = (z - z0) / (z_yk - z0)
        add(z, bb.shinogi_profile(bb.lerp(root_w, yok_w, u), bb.lerp(root_t, tip_t, u)))
    zk = z_yk + 0.5 * kissaki
    wk, tk = yok_w * 0.62, tip_t * 0.8
    add(zk, [(x, y + 0.10 * yok_w) for x, y in bb.shinogi_profile(wk, tk)])
    apex = bb.ring_pts([(0.0, 0.30 * yok_w)], z_tip, bb.sori_off(z_tip, z0, length, tip_off), bb.sori_phi(z_tip, z0, length, tip_off))[0]
    va = mb.v(apex)
    vs = [[mb.v(p) for p in r] for _, r, _ in data]

    def fx_list(prof):
        ys = [y for _, y in prof]
        lo, hi = min(ys), max(ys)
        fr = [(y - lo) / (hi - lo) for _, y in prof]
        tx = prof[2][0] * 2.0   # thickness at the spine
        # (isl, fx) for each of the 6 profile points, per face k (corner a = k, corner b = k+1)
        return fr

    def corner_uv(k, i_pt, fr, prof, s):
        """UV of profile point i_pt as a corner of face k."""
        if k in (0, 1):
            return A.uv(fr[i_pt], s)
        if k in (3, 4):
            return B.uv(fr[i_pt], s)
        if k == 2:
            return S.uv(0.0 if i_pt == 2 else 1.0, s)
        return E.uv(0.0 if i_pt == 5 else 1.0, s)

    for i in range(len(data) - 1):
        s0, s1 = data[i][0], data[i + 1][0]
        fr0, fr1 = fx_list(data[i][2]), fx_list(data[i + 1][2])
        for k in range(6):
            k1 = (k + 1) % 6
            uvs = [corner_uv(k, k, fr0, data[i][2], s0), corner_uv(k, k1, fr0, data[i][2], s0),
                   corner_uv(k, k1, fr1, data[i + 1][2], s1), corner_uv(k, k, fr1, data[i + 1][2], s1)]
            mb.face([vs[i][k], vs[i][k1], vs[i + 1][k1], vs[i + 1][k]], uvs, False)
    last = len(data) - 1
    frl = fx_list(data[last][2])
    for k in range(6):
        k1 = (k + 1) % 6
        ua, ub = corner_uv(k, k, frl, data[last][2], data[last][0]), corner_uv(k, k1, frl, data[last][2], data[last][0])
        isl = {0: A, 1: A, 2: S, 3: B, 4: B, 5: E}[k]
        # apex u = mean of the two ring corners in the island's own u (inverse uv)
        um = 0.5 * (ua[0] + ub[0])
        uapex = (um, isl.uv(0, 1.0)[1])
        mb.face([vs[last][k], vs[last][k1], va], [ua, ub, uapex], False)
    # root cap, hidden by the habaki
    mb.face(vs[0], [A.uv(0.5, 0.0)] * 6, False, root_cap_hidden)
    return apex


# ------------------------------------------------------------------ tsuba plate with holes, rim bevel, planar UV
def poly_len_fracs(pts):
    n = len(pts)
    cum = [0.0]
    for k in range(n):
        a, b = Vector(pts[k]), Vector(pts[(k + 1) % n])
        cum.append(cum[-1] + (b - a).length)
    return [c / cum[-1] for c in cum]


def plate(mb, outline, holes, z0, z1, bev, L, front, back, rim, hole_isl, px_per_m, smooth=False):
    """Flat plate z0..z1 with through holes. Outer rim: 1 segment bevel (inset `bev` and `bev` tall) on both faces; hole walls
    are hard edged. Faces: planar UV on front (+Z) and back (-Z, mirrored in x), rim bands (bottom chamfer / wall / top chamfer),
    hole walls into hole_isl (u = perimeter, v = height)."""
    inn = inset_poly(outline, bev)
    n = len(outline)
    fr = poly_len_fracs(outline)
    P = {
        "in0": [mb.v(Vector((x, y, z0))) for x, y in inn], "o0": [mb.v(Vector((x, y, z0 + bev))) for x, y in outline],
        "o1": [mb.v(Vector((x, y, z1 - bev))) for x, y in outline], "in1": [mb.v(Vector((x, y, z1))) for x, y in inn]}
    bands = [("in0", "o0", 0.0, 0.3), ("o0", "o1", 0.3, 0.7), ("o1", "in1", 0.7, 1.0)]
    for a, b, f0, f1 in bands:
        for k in range(n):
            k1 = (k + 1) % n
            mb.face([P[a][k], P[a][k1], P[b][k1], P[b][k]],
                    [rim.uv(fr[k], f0), rim.uv(fr[k + 1], f0), rim.uv(fr[k + 1], f1), rim.uv(fr[k], f1)], smooth)
    # planar fills with holes
    poly3 = [[Vector((x, y, 0.0)) for x, y in outline_pts] for outline_pts in [inn] + list(holes)]
    tris = geometry.tessellate_polygon(poly3)
    hv0 = [[mb.v(Vector((x, y, z0))) for x, y in h] for h in holes]
    hv1 = [[mb.v(Vector((x, y, z1))) for x, y in h] for h in holes]
    flat0 = P["in0"] + [v for h in hv0 for v in h]
    flat1 = P["in1"] + [v for h in hv1 for v in h]
    flatpts = list(inn) + [p for h in holes for p in h]
    for t in tris:
        pts = [flatpts[i] for i in t]
        mb.face([flat1[i] for i in t], [planar_uv(front, px_per_m, x, y) for x, y in pts], smooth)
        mb.face([flat0[i] for i in t], [planar_uv(back, px_per_m, x, y, flip_x=True) for x, y in pts], smooth)
    for h, a, b in zip(holes, hv0, hv1):
        hn = len(h)
        hf = poly_len_fracs(h)
        for k in range(hn):
            k1 = (k + 1) % hn
            mb.face([a[k], a[k1], b[k1], b[k]],
                    [hole_isl.uv(hf[k], 0.0), hole_isl.uv(hf[k + 1], 0.0), hole_isl.uv(hf[k + 1], 1.0), hole_isl.uv(hf[k], 1.0)], smooth)


def bezier_corner(p_in, p_corner, p_out, segs):
    """quadratic Bezier from p_in to p_out with control p_corner, segs segments -> segs + 1 points."""
    pts = []
    for i in range(segs + 1):
        t = i / segs
        a = (1 - t) ** 2
        b = 2 * (1 - t) * t
        c = t * t
        pts.append((a * p_in[0] + b * p_corner[0] + c * p_out[0], a * p_in[1] + b * p_corner[1] + c * p_out[1]))
    return pts


# ------------------------------------------------------------------ materials (preview) and scene helpers
def atlas_material(model, diffuse=None, emissive=None, tint="#FFFFFF", strength=1.0, cutout=False, blend=False):
    """One material `<model>_atlas`. Principled BSDF preview: diffuse PNG (Closest) as base colour; emissive PNG alpha x tint as
    emission. The baked PNGs are what ships; this is only for the turntable and visual checks."""
    name = model + "_atlas"
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    b = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(b.outputs["BSDF"], out.inputs["Surface"])
    b.inputs["Roughness"].default_value = 0.65
    b.inputs["Metallic"].default_value = 0.0
    if diffuse and os.path.exists(diffuse):
        im = bpy.data.images.load(diffuse, check_existing=False)
        im.reload()
        im.alpha_mode = 'STRAIGHT'
        t = nt.nodes.new("ShaderNodeTexImage")
        t.image = im
        t.interpolation = 'Closest'
        nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
        nt.links.new(t.outputs["Alpha"], b.inputs["Alpha"])
    if emissive and os.path.exists(emissive):
        im = bpy.data.images.load(emissive, check_existing=False)
        im.reload()
        im.alpha_mode = 'STRAIGHT'
        t = nt.nodes.new("ShaderNodeTexImage")
        t.image = im
        t.interpolation = 'Closest'
        mul = nt.nodes.new("ShaderNodeMath")
        mul.operation = 'MULTIPLY'
        mul.inputs[1].default_value = strength
        nt.links.new(t.outputs["Alpha"], mul.inputs[0])
        nt.links.new(mul.outputs["Value"], b.inputs["Emission Strength"])
        b.inputs["Emission Color"].default_value = (*bb.hex2lin(tint), 1.0)
    if cutout:
        try:
            m.surface_render_method = 'DITHERED'
        except Exception:
            pass
    if blend:    # translucent ice preview (diffuse alpha < 255); the shipped look is decided by the PNG alpha, not this flag
        try:
            m.surface_render_method = 'BLENDED'
            m.use_backface_culling = False
        except Exception:
            pass
    return m


def save_scene(model):
    os.makedirs(SCENES, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(SCENES, model + ".blend"))


def set_empties(coll, items):
    out = {}
    for n, loc in items:
        e = bb.empty(n, loc, coll, 0.03)
        bb.link_only(e, coll)
        out[n] = tuple(loc)
    return out


# ------------------------------------------------------------------ saya alone (draw animation)
LINER_TEXEL = (99.0, 110.0)    # atlas px (99 = border between the two dark edge columns of the island `saya`, rows 109/110 dark)


def build_saya(mb, L, z_mouth, z_s0, z_s1, length, tip_off, sa=0.013, sb=0.020, n_rings=12, liner_inset=0.002, liner_rings=6,
               widen_y=0.0016, widen_z=(0.70, 0.91)):
    """Scabbard alone, same outer rings / UVs as the saya + koiguchi + kojiri of `<model>_sheathed`, but ONE closed shell with an open mouth:
    outer koiguchi (z_mouth .. z_s0, +1 mm) -> 1 mm step -> saya (z_s0 .. z_s1) -> 0.5 mm step -> kojiri + cap; a mouth lip at z_mouth joins the
    koiguchi to a dark inner liner (normals toward the axis) that runs to z_s1 and is capped there. Liner UVs are collapsed on a dark texel of the
    `saya` island (no new texture). Shared vertices everywhere, so finish() recalculates all normals outward from the solid wall.
    widen_y: the drawn blade (sori tip offset 80 mm) lies against the -Y wall of the saya (sori tip offset 92 mm) and pokes 1.2 mm through it at z 0.91 (yokote),
    so the Y semi axis of every ring grows linearly by widen_y between widen_z[0] and widen_z[1] (0 below, widen_y above); 1.6 mm = 0.8 px at 500 px/m."""
    def ex(z):
        u = min(1.0, max(0.0, (z - widen_z[0]) / (widen_z[1] - widen_z[0])))
        return widen_y * u

    def sr(a, b, z):
        return bb.ring_pts(bb.oval(a, b + ex(z), 16), z, bb.sori_off(z, z_mouth, length, tip_off), bb.sori_phi(z, z_mouth, length, tip_off))

    def ring_quads(va, vb, uv):
        n = len(va)
        for k in range(n):
            mb.face([va[k], va[(k + 1) % n], vb[(k + 1) % n], vb[k]], [uv] * 4, False)

    saya = [sr(sa, sb, z_s0 + (z_s1 - z_s0) * i / (n_rings - 1)) for i in range(n_rings)]
    v_saya = tube(mb, saya, 4, L["saya"], [i / (n_rings - 1) for i in range(n_rings)], True)
    v_koi = tube(mb, [sr(sa + 0.001, sb + 0.001, z_mouth), sr(sa + 0.001, sb + 0.001, z_s0)], 4, L["koiguchi"], [0.0, 1.0], False)
    ring_quads(v_koi[1], v_saya[0], L["koiguchi"].uv(0.5, 0.5))          # 1 mm step koiguchi -> saya, flat, collapsed on one koiguchi texel
    kz = [(z_s1, 1.0), (z_s1 + 0.012, 0.88), (z_s1 + 0.018, 0.45)]
    v_koj = tube(mb, [sr(sa * k + 0.0005, sb * k + 0.0005, z) for z, k in kz], 4, L["kojiri"], [0.0, 0.7, 1.0], False,
                 cap1=(L["kojiri_cap"], 450.0, False))
    ring_quads(v_saya[-1], v_koj[0], L["kojiri"].uv(0.5, 0.5))           # 0.5 mm step saya -> kojiri
    # inner liner: the same sori centre line, inset by liner_inset on both axes; rings spread evenly between the mouth and z_s1
    ls = [z_mouth + (z_s1 - z_mouth) * i / (liner_rings - 1) for i in range(liner_rings)]
    lu, lv = LINER_TEXEL
    dark = ((lu) / L["saya"].atlas, 1.0 - lv / L["saya"].atlas)
    v_lin = [[mb.v(p) for p in sr(sa - liner_inset, sb - liner_inset, z)] for z in ls]
    ring_quads(v_koi[0], v_lin[0], L["koiguchi"].uv(0.5, 0.5))           # mouth lip (wall thickness), faces the mouth
    for i in range(liner_rings - 1):
        ring_quads(v_lin[i], v_lin[i + 1], dark)
    mb.face(v_lin[-1], [dark] * 16, False)                               # liner end cap
    return {"liner_inset": liner_inset, "liner_z": ls}
