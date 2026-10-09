# Blockout: rukia_bankai, required pieces only (ART_BIBLE 1.5). Costume set deferred (see notes). Re-runnable.
#   blender --background --factory-startup --python blockout_rukia_bankai.py      (then: python bb_compose.py rukia_bankai)
import sys, os, math, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts")
import importlib, bb_common as bb
importlib.reload(bb)
from mathutils import Vector

MODEL = "rukia_bankai"
bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")

bb.mat("rb_edge", "#CFEFFF", rough=0.15)
bb.mat("rb_core", "#7FB8DF", rough=0.15)
bb.mat("rb_deep", "#2E6FA8", rough=0.15)
bb.mat("rb_wrap", "#DCE8F5", rough=0.7)
bb.mat("rb_tsuba", "#DCE8F5", rough=0.35)
bb.mat("rb_ribbon", "#F0F6FA", rough=0.6)
bb.mat("rb_crystal", "#8EC9EE", rough=0.15)
bb.mat("rb_crystal_tip", "#D9E8F5", rough=0.15)
bb.mat("rb_shell", "#8EC9EE", rough=0.15, alpha=0.85)

# ---- rukia_bankai_sword: tsuka 0.28, bar tsuba z 0.280-0.286, habaki 0.286-0.314, blade 0.78 m from the habaki start (to 1.066)
parts = bb.hilt_parts("b", "rb_wrap", "rb_tsuba", "rb_tsuba", tsuka_len=0.28)
parts.append(bb.tsuba_bankai_bar("b", 0.280, "rb_tsuba"))
parts.append(bb.habaki("b", 0.286, "rb_tsuba", w=0.032, t=0.014))
bl, tip = bb.blade("b_blade", 0.314, 1.066 - 0.314, 0.028, 0.020, 0.012, 0.005, 0.070, 0.008, "rb_core",
                   mat_names=["rb_edge", "rb_core", "rb_deep"], pattern=[0, 1, 2, 1, 0, 0])
parts.append(bl)
sword = bb.join(parts, "rukia_bankai_sword")
bb.link_only(sword, exp)


# ---- ribbons: 0.35 x 0.07 m segment and a tip piece, hinge at the origin, mesh along -Z, 2 mm thick
def ribbon(name, w0, w1, loc):
    o = bb.prism_y(name, [(-w0 / 2, 0.0), (w0 / 2, 0.0), (w1 / 2, -0.35), (-w1 / 2, -0.35)], -0.001, 0.001, "rb_ribbon")
    o.location = loc
    bb.link_only(o, exp)
    return o


seg = ribbon("rukia_bankai_ribbon_seg", 0.07, 0.07, (0.30, 0.0, 0.40))
rtip = ribbon("rukia_bankai_ribbon_tip", 0.07, 0.008, (0.45, 0.0, 0.40))


# ---- crystals: hexagonal prisms with a pointed tip, base radius = height / 6, bases at z = 0
def crystal(name, h, nrings, bend, loc):
    r = h / 6.0
    zs = [0.0, 0.45 * h, 0.78 * h] if nrings == 3 else [0.0, 0.30 * h, 0.60 * h, 0.82 * h]
    ks = [1.0, 0.95, 0.78] if nrings == 3 else [1.0, 0.97, 0.88, 0.72]
    rings = []
    for z, k in zip(zs, ks):
        sh = bend * h * (z / h) ** 2
        rings.append([Vector((k * r * math.cos(math.pi / 3 * i), k * r * math.sin(math.pi / 3 * i) + sh, z)) for i in range(6)])
    apex = Vector((0.0, bend * h, h))
    ngap = len(rings) - 1
    fm = [0] * (ngap * 6) + [0] + [1] * 6
    o = bb.loft(name, rings, "rb_crystal", apex=apex, face_mats=fm, mat_names=["rb_crystal", "rb_crystal_tip"])
    o.location = loc
    bb.link_only(o, exp)
    return o


cr = [crystal("rukia_bankai_crystal_a", 0.15, 4, 0.0, (0.80, 0.0, 0.0)),
      crystal("rukia_bankai_crystal_b", 0.30, 4, 0.0, (1.00, 0.0, 0.0)),
      crystal("rukia_bankai_crystal_c", 0.60, 4, 0.10, (1.30, 0.0, 0.0)),   # the slightly bent variant
      crystal("rukia_bankai_crystal_d", 1.20, 3, 0.0, (1.80, 0.0, 0.0))]


# ---- shards: flat splinters, 3 mm thick triangular prisms (8 tris)
def shard(name, ln, wd, loc):
    tri = [(0.12 * wd, ln / 2), (-0.5 * wd, -ln / 2 + 0.01), (0.5 * wd, -ln / 2)]
    cy, cz = sum(p[0] for p in tri) / 3, sum(p[1] for p in tri) / 3
    tri = [(y - cy, z - cz) for y, z in tri]
    sv = [Vector((-0.0015, y, z)) for y, z in tri] + [Vector((0.0015, y, z)) for y, z in tri]
    sf = [(0, 1, 2), (3, 4, 5)] + [(k, (k + 1) % 3, 3 + (k + 1) % 3, 3 + k) for k in range(3)]
    o = bb.mesh_obj(name, sv, sf, ["rb_edge"])
    o.location = loc
    bb.link_only(o, exp)
    return o


sh = [shard("rukia_bankai_shard_a", 0.12, 0.03, (2.20, 0.0, 0.06)), shard("rukia_bankai_shard_b", 0.08, 0.025, (2.35, 0.0, 0.04))]

# ---- ice shell: 1.0 x 1.0 x 2.0 m, origin at the bottom centre, 10-sided, 3 body rings + 2 end rings, irregular facets
random.seed(7)
levels = [(0.0, 0.30), (0.30, 0.46), (1.00, 0.50), (1.70, 0.46), (2.0, 0.30)]
rings = []
for z, rr in levels:
    ring = []
    for i in range(10):
        a = 2 * math.pi * i / 10 + 0.1 * random.random()
        rad = rr * (0.94 + 0.12 * random.random())
        ring.append(Vector((rad * math.cos(a), rad * math.sin(a), z)))
    rings.append(ring)
shell = bb.loft("rukia_bankai_ice_shell", rings, "rb_shell")
shell.location = (3.00, 0.0, 0.0)
bb.link_only(shell, exp)

empties = [("grip_hand", (0, 0, 0.22)), ("tip", tuple(tip)), ("ribbon_root", (0, 0.12, 0.90))]
for n, v in empties:
    bb.link_only(bb.empty(n, v, exp, 0.03), exp)

chk = bb.tsuba_bankai_bar("chk", 0.280, "rb_tsuba")
chk.name = "tsuba_check"
allobj = [sword, seg, rtip] + cr + sh + [shell]
notes = ["Required pieces only: sword, ribbon segment/tip, crystals a-d, shards a-b, ice shell. Layout in the scene (objects keep their own origins): sword at the origin, ribbon seg/tip at x 0.30 / 0.45 (hinge z 0.40), crystals at x 0.80 / 1.00 / 1.30 / 1.80, shards at x 2.20 / 2.35, ice shell at x 3.00.",
         "Sword: tsuka 0.28 m (z 0 to 0.28), oblong stadium bar tsuba 84 (Y) x 22 (X) x 6 mm (rim 4 mm, centre bridge 44 mm, two stadium windows about 16 x 14 mm), habaki z 0.286 to 0.314, blade 0.752 m to z 1.066 (0.78 m from the habaki start), 12 mm spine, edge bevel `#CFEFFF`, core `#7FB8DF`, spine `#2E6FA8` (face-material split so the ice layers read).",
         "Ribbon segment 0.35 x 0.07 m, 2 mm thick, 12 tris; tip piece tapers from 70 mm to 8 mm over 0.35 m (12 tris). Hinge at the origin, mesh runs along -Z (same convention as rukia_shikai ribbons).",
         "Crystals: 6-sided pyramid-tipped prisms, base radius = height/6, 46 tris (a, b, c) and 34 tris (d); crystal_c leans 0.10 x height toward +Y (the bent variant); tips use `#D9E8F5`.",
         "Shards: 3 mm triangular prisms, 120 x 30 mm and 80 x 25 mm, 8 tris each. Ice shell: 10-sided, 3 body rings + 2 end rings with seeded irregular radii, 96 tris, X/Y about 1.0 m, Z 2.0 m, translucent preview colour.",
         "`ribbon_root` is a player-space point (feet at the player origin, back = +Y, obi height about 0.9 m), kept at (0, 0.12, 0.90) because the player/costume is not modelled yet."]
deviations = [
    "Costume set (collar, both pauldrons, crown, chest flower) NOT built: art bible E10 and Gate A decision 6 say it is modelled last, after the required pieces pass Gate B. A reference player and the five pieces can be added by a short script once Gate B passes.",
    "Habaki is 32 x 14 mm (bible: BASE 32 x 10): the 12 mm spine of the bankai blade does not fit inside a 10 mm collar.",
    "Sori 8 mm is the tip deflection from the base axis (see rukia_sealed note on `bb.SORI_K`).",
    "Tsuba blade hole not cut (enclosed pocket); wrap diamonds `#7FA5C8` are texture-only; transparency is only previewed on the shell, everything else is flat colour.",
    "Ribbon segment/tip run along -Z with rotation 0 (same open question as rukia_shikai); no 10 mm overlap added (the bankai spec gives none).",
    "Ice shell is 96 tris for 3 body rings + 2 end rings (bible says 3 rings); facet irregularity is a fixed random seed.",
]
questions = [
    "Costume pieces are deferred as the bible says; confirm they should be blocked out right after Gate B (and which player model dimensions to use).",
    "Ribbon local axis (-Z, as built) vs +Z for the chain code, see rukia_shikai.",
    "Is the 3-way ice material split on the blade (bevel / core / deep spine) the right basis for the later UV layout, or should the blade stay a single ice colour with the layering painted in?",
    "ribbon_root: its position relative to the sword origin is meaningless until the player rig is chosen; keep it as a player-space point?",
]
bb.finish(MODEL, allobj, [("sword", [sword], 0.10), ("ribbon_pieces", [seg, rtip], 0.05, dict(H=700, W=460)),
                          ("crystals", cr, 0.10, dict(H=800, W=520)),
                          ("shards", sh, 0.01, dict(H=420, W=420)), ("ice_shell", [shell], 0.25, dict(H=700, W=460)),
                          ("tsuba_detail", [chk], 0.01, dict(views=[bb.VIEW_TOP, bb.VIEWS[2]], W=520, H=520))],
          empties, notes, deviations, questions, temp_objs=[chk])
