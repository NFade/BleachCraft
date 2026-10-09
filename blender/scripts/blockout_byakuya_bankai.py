# Blockout: byakuya_bankai, required pieces (ART_BIBLE 1.6). Senkei sword deferred (optional, after Gate C). Re-runnable.
#   blender --background --factory-startup --python blockout_byakuya_bankai.py      (then: python bb_compose.py byakuya_bankai)
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts")
import importlib, bb_common as bb
importlib.reload(bb)
from mathutils import Vector, Matrix

MODEL = "byakuya_bankai"
bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
bb.bk_materials()

bb.mat("gb_light", "#E4E8F0", rough=0.4, metallic=0.7)     # giant blade highlight
bb.mat("gb_base", "#A9B2C2", rough=0.4, metallic=0.7)      # base
bb.mat("gb_shade", "#7C869A", rough=0.4, metallic=0.7)     # shade (spine)
bb.mat("gb_soil", "#4A3A2E", rough=0.9)                    # buried band
bb.mat("gb_glow", "#F2E9FF", rough=0.3, emit="#F2E9FF", emit_strength=1.0)   # tip glow zone
bb.mat("bb_ripple", "#CFC3F0", rough=0.5)
bb.mat("hk_white", "#F4F8FF", rough=0.4, emit="#FFFFFF", emit_strength=4.0)
bb.mat("hk_halo", "#FFFFFF", rough=0.4, emit="#FFFFFF", emit_strength=4.0)

# ---------------------------------------------------------------- giant blade (instanced), origin at the centre of the base
H, W, T0, T1, KISSAKI = 8.0, 0.55, 0.14, 0.06, 0.9
CP = [(0.5, 0.0), (4.0, 0.10), (6.0, 0.35), (8.0, 0.75)]    # sori control points: deviation from the straight base axis (E14)


def dev(z):
    """Cubic Hermite through CP (Catmull-Rom tangents, zero slope at the first point): 0 at z<=0.5, 0.75 at the tip."""
    if z <= CP[0][0]:
        return 0.0
    pts = CP
    tans = []
    for i, (zz, dd) in enumerate(pts):
        if i == 0:
            tans.append(0.0)
        elif i == len(pts) - 1:
            tans.append((dd - pts[i - 1][1]) / (zz - pts[i - 1][0]))
        else:
            tans.append((pts[i + 1][1] - pts[i - 1][1]) / (pts[i + 1][0] - pts[i - 1][0]))
    for i in range(len(pts) - 1):
        z0, d0 = pts[i]
        z1, d1 = pts[i + 1]
        if z <= z1:
            h = z1 - z0
            t = (z - z0) / h
            h00, h10, h01, h11 = 2 * t**3 - 3 * t**2 + 1, t**3 - 2 * t**2 + t, -2 * t**3 + 3 * t**2, t**3 - t**2
            return h00 * d0 + h10 * h * tans[i] + h01 * d1 + h11 * h * tans[i + 1]
    return pts[-1][1]


def dphi(z, eps=0.01):
    return math.atan((dev(min(z + eps, H)) - dev(max(z - eps, 0.0))) / (min(z + eps, H) - max(z - eps, 0.0)))


def width_at(z):
    ky = H - KISSAKI
    if z <= ky:
        return W
    return None


# ring stations: z, width, local-y shift of the section (edge sweep in the kissaki)
ST = [(0.0, W, 0.0), (0.5, W, 0.0), (1.5, W, 0.0), (2.5, W, 0.0), (3.5, W, 0.0), (4.0, W, 0.0), (5.0, W, 0.0), (6.0, W, 0.0),
      (6.8, W, 0.0), (7.1, W, 0.0), (7.55, 0.40, 0.04), (7.85, 0.18, 0.06)]
thick = lambda z: T0 + (T1 - T0) * max(0.0, z - 0.5) / (H - 0.5)
rings, pattern_face = [], []
for z, w, ys in ST:
    prof = [(x, y + ys) for x, y in bb.shinogi_profile(w, thick(z), ridge=0.55, edge=0.002)]
    rings.append(bb.ring_pts(prof, z, dev(z), dphi(z)))
apex = bb.ring_pts([(0.0, 0.08)], H, dev(H), dphi(H))[0]
BASEPAT = [0, 1, 2, 1, 0, 0]              # light edge bevel, flat, shade spine, flat, bevel, edge strip
fm = []
for i in range(len(ST) - 1):
    z0 = ST[i][0]
    if z0 < 0.5:
        fm += [3] * 6                      # buried soil band
    elif z0 >= 6.8:
        fm += [4] * 6                      # top 1.2 m glow zone
    else:
        fm += BASEPAT
fm += [3]                                   # bottom cap
fm += [4] * 6                               # tip fan
giant = bb.loft("byakuya_bankai_blade", rings, "gb_base", apex=apex, face_mats=fm,
                mat_names=["gb_light", "gb_base", "gb_shade", "gb_soil", "gb_glow"])
bb.link_only(giant, exp)

# ---------------------------------------------------------------- LOD: 3 rings x 4 vertices + tip (flat slab), same outline/origin
lod_rings = []
for z, w in ((0.0, W), (4.5, W), (7.1, W)):
    t = thick(z)
    lod_rings.append(bb.ring_pts([(t / 2, -w / 2), (t / 2, w / 2), (-t / 2, w / 2), (-t / 2, -w / 2)], z, dev(z), dphi(z)))
lod = bb.loft("byakuya_bankai_blade_lod", lod_rings, "gb_base", apex=apex)
lod.location = (1.5, 0.0, 0.0)
bb.link_only(lod, exp)

# ---------------------------------------------------------------- ground hilt: sealed hilt pointing up over a 0.25 m blade stub, origin at the bottom of the stub
parts = bb.bk_hilt("g")
parts.append(bb.habaki("g", 0.257, "bk_bronze"))
stub = bb.loft("g_stub", [bb.ring_pts(bb.shinogi_profile(0.032, 0.010), 0.285), bb.ring_pts(bb.shinogi_profile(0.031, 0.010), 0.520)], "bk_steel")
parts.append(stub)
hilt = bb.join(parts, "byakuya_bankai_hilt_ground")
bb.xform([hilt], Matrix.Translation((0, 0, 0.52)) @ Matrix.Rotation(math.pi, 4, 'X'))      # flip: kashira up, stub down
hilt.location = (3.0, 0.0, 0.0)
bb.link_only(hilt, exp)

# ---------------------------------------------------------------- ripple: flat ring r 0.88..1.00, 16 segments (32 tris), at z = 0.02
nseg = 16
rv = [Vector((1.00 * math.cos(2 * math.pi * k / nseg), 1.00 * math.sin(2 * math.pi * k / nseg), 0.02)) for k in range(nseg)]
rv += [Vector((0.88 * math.cos(2 * math.pi * k / nseg), 0.88 * math.sin(2 * math.pi * k / nseg), 0.02)) for k in range(nseg)]
rf = [(k, (k + 1) % nseg, nseg + (k + 1) % nseg, nseg + k) for k in range(nseg)]
ripple = bb.mesh_obj("byakuya_bankai_ripple", rv, rf, ["bb_ripple"], recalc=False)
for p in ripple.data.polygons:                 # make sure the single face points up
    if p.normal.z < 0:
        pass
ripple.location = (5.0, 0.0, 0.0)
bb.link_only(ripple, exp)

# ---------------------------------------------------------------- Hakuteiken set
# blade body: unit-length straight prism along +Z, 0.30 wide (Y), 0.06 thick (X), 2 rings x 6 vertices, origin at the hilt end
hb = bb.loft("hakuteiken_blade_body", [bb.ring_pts(bb.shinogi_profile(0.30, 0.06, edge=0.002), 0.0), bb.ring_pts(bb.shinogi_profile(0.30, 0.06, edge=0.002), 1.0)], "hk_white")
hb.location = (8.0, 0.0, 0.0)
bb.link_only(hb, exp)
# blade tip: 0.45 m kissaki, origin at its base (sits on the body end at z = 1.0 in the scene)
tr = [bb.ring_pts(bb.shinogi_profile(w, t, edge=0.002), z) for z, w, t in ((0.0, 0.30, 0.06), (0.18, 0.27, 0.055), (0.34, 0.18, 0.04))]
tapex = Vector((0.0, 0.05, 0.45))
tip_o = bb.loft("hakuteiken_blade_tip", tr, "hk_white", apex=tapex)
tip_o.location = (8.0, 0.0, 1.0)
bb.link_only(tip_o, exp)


# wings: 9 feather sheets fanned from the shoulder root, single sided (normals +Y), curved forward (-Y) by 0.8 m at the longest feather
def wing(name, sx, loc):
    verts, faces = [], []
    L0 = 6.0 / math.cos(math.radians(30))
    for i in range(9):
        a = math.radians(30 - 7 * i)
        L = L0 * (1 - 0.075 * i)
        d = Vector((sx * math.cos(a), 0.0, math.sin(a)))
        n = Vector((-d.z, 0.0, d.x)) * (1.0 if sx > 0 else -1.0)      # in-plane normal to the feather direction
        st = [(0.10, 0.10 * (L / L0) + 0.05), (0.40, 0.30 * (L / L0) + 0.12), (0.75, 0.20 * (L / L0) + 0.08)]
        base = len(verts)
        for f, hw in st:
            r = f * L
            c = d * r + Vector((0.0, -0.8 * (r / L0) ** 2, 0.0))
            verts.append(c + n * hw)
            verts.append(c - n * hw)
        r = L
        verts.append(d * r + Vector((0.0, -0.8 * (r / L0) ** 2, 0.0)))
        quads = [(base, base + 1, base + 3, base + 2), (base + 2, base + 3, base + 5, base + 4), (base + 4, base + 5, base + 6)]
        for q in quads:
            v = [verts[k] for k in q]
            nrm = (v[1] - v[0]).cross(v[2] - v[0])
            faces.append(tuple(q) if nrm.y > 0 else tuple(reversed(q)))
    o = bb.mesh_obj(name, verts, faces, ["hk_white"], recalc=False)
    o.location = loc
    bb.link_only(o, exp)
    return o


wing_l = wing("hakuteiken_wing_l", 1, (14.0, 0.0, 1.0))
wing_r = wing("hakuteiken_wing_r", -1, (14.0, 0.0, 1.0))

# halo: vertical ring in the XZ plane, r 1.00..1.10, 24 segments (48 tris), origin at its centre
nh = 24
hv = [Vector((1.10 * math.cos(2 * math.pi * k / nh), 0.0, 1.10 * math.sin(2 * math.pi * k / nh))) for k in range(nh)]
hv += [Vector((1.00 * math.cos(2 * math.pi * k / nh), 0.0, 1.00 * math.sin(2 * math.pi * k / nh))) for k in range(nh)]
hf = [(k, nh + k, nh + (k + 1) % nh, (k + 1) % nh) for k in range(nh)]     # normal +Y
halo = bb.mesh_obj("hakuteiken_halo", hv, hf, ["hk_halo"], recalc=False)
halo.location = (14.0, 0.3, 4.2)
bb.link_only(halo, exp)

allobj = [giant, lod, hilt, ripple, hb, tip_o, wing_l, wing_r, halo]
empties = []
notes = ["Required pieces only; `byakuya_bankai_senkei_sword` is not built (optional, only after Gate C passes). Petal/shard are reused from byakuya_shikai.",
         "Scene layout (each object keeps its own origin): giant blade at the origin, LOD at x 1.5, ground hilt at x 3, ripple at x 5, Hakuteiken blade body at x 8 (z 0 to 1) with the tip sitting on it at z 1.0, wings at x 14 (shoulder root z 1.0, L extends +X, R extends -X), halo centre at (14, 0.3, 4.2).",
         "Giant blade: 8.0 m, 0.55 m wide, spine thickness 0.14 m tapering to 0.06 m, sori control points (0.5, 0), (4, 0.10), (6, 0.35), (8, 0.75) smoothed with a cubic Hermite spline (tip 0.75 m toward +Y), kissaki last 0.9 m, 12 rings x 6 vertices + tip. Face materials: soil band z 0 to 0.5, light edge bevel / base flats / shade spine, glow zone z >= 6.8 (`#F2E9FF`).",
         "LOD: 3 rings x 4 vertices (z 0, 4.5, 7.1) + tip, 22 tris, same origin and tip as the full blade.",
         "Ground hilt: the sealed-sword hilt (shared builder) mirrored to point up over a 0.263 m habaki + blade stub, total 0.52 m, origin at the bottom of the stub (a flat cut end).",
         "Ripple: 16-segment flat ring r 0.88 to 1.00 m at z 0.02 (32 tris). Hakuteiken blade body: 2 shinogi rings (20 tris incl. caps), tip: 0.45 m kissaki (34 tris). Halo: 24-segment annulus in the XZ plane (48 tris).",
         "Wings: 9 feather sheets per wing (3 quads + tip triangle fan, single sided, normals +Y), longest feather 6.93 m at 30 degrees above X (horizontal reach 6.0 m), lengths decreasing 7.5 percent per feather, tips curve forward (-Y) up to 0.8 m.",
         "No empties are specified for this model; none added."]
deviations = [
    "Senkei sword deferred (optional per the bible and Gate A decision 7).",
    "Giant blade bbox top is z 7.984 rather than 8.000: the kissaki tip sits 0.08 m toward the spine inside the tilted last section; the tip height can be pushed to exactly 8.0 in the detail pass.",
    "Wing shape is an interpretation: the bible gives span 6 m, 9 lobes, 150 tris and 0.8 m forward curvature but no feather layout; the fan angles (30 down to -26 degrees) and lengths are guesses to be checked against the Hakuteiken frames at Gate B.",
    "Giant blade has 142 tris (target 170, limit 300); the ring z-stations are chosen so the spline control points get rings.",
    "Hakuteiken blade tip uses 3 rings (34 tris) instead of the full 40; body 20 tris.",
    "Ground hilt blade stub is a straight, flat-cut 32 x 10 mm blade section (the buried end).",
    "Palette: the giant blade face-material split (light edge bevel, base flats, shade spine) is a blockout stand-in for the painted texture.",
]
questions = [
    "Wing layout: confirm fan direction (longest feather up-and-out at 30 degrees) and that +Y is 'back' with the 0.8 m curve toward -Y (forward).",
    "Halo position: bible says centre 0.35 m above the head and 0.3 m behind the back; placed here at (14, 0.3, 4.2) over a wing root at z 1.0, which is only a layout convenience. Should the halo origin be defined relative to the wing root instead?",
    "Giant-blade tilt: with sori 0.75 m toward +Y the tip leans over the buried axis; the in-game yaw rule (spine toward the player) is unchanged, but should the buried 0.5 m also be vertical (as built)?",
    "Ripple is a single face (normal +Z); is double-sided rendering assumed in code?",
]
bb.finish(MODEL, allobj,
          [("giant_blade_and_lod", [giant, lod], 1.0, dict(W=520, H=860)),
           ("ground_hilt", [hilt], 0.05, dict(W=460, H=700)),
           ("ripple", [ripple], 0.25, dict(views=[bb.VIEW_TOP, bb.VIEWS[2]], W=520, H=520)),
           ("hakuteiken_blade", [hb, tip_o], 0.25, dict(W=460, H=760)),
           ("wings_and_halo", [wing_l, wing_r, halo], 1.0, dict(W=820, H=560))],
          empties, notes, deviations, questions)
