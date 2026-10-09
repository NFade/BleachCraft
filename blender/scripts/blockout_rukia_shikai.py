# Blockout: rukia_shikai (ART_BIBLE 1.2). Re-runnable; builds from scratch.
#   blender --background --factory-startup --python blockout_rukia_shikai.py      (then: python bb_compose.py rukia_shikai)
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts")
import importlib, bb_common as bb
importlib.reload(bb)
from mathutils import Vector

MODEL = "rukia_shikai"
bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")

bb.mat("rk_blade", "#EAF2FB", rough=0.25)          # anime cool white (bible: shipped base)
bb.mat("rk_shade", "#C9D6EA", rough=0.4)
bb.mat("rk_wrap", "#F2F4FA", rough=0.7)
bb.mat("rk_ribbon", "#F4F8FF", rough=0.6)

# ---- rukia_shikai_blade: hilt, snowflake tsuba, habaki, blade (one mesh)
parts = bb.hilt_parts("k", "rk_wrap", "rk_blade", "rk_blade")
# Gate B B8: no 4 mm ribbon hole; a 14 x 8 x 10 mm knot block hangs below the kashira (ribbon_root = its bottom)
parts.append(bb.box("k_knot", (-0.007, -0.004, -0.010), (0.007, 0.004, 0.000), "rk_ribbon"))
parts.append(bb.tsuba_snowflake("k", 0.250, "rk_blade"))
parts.append(bb.habaki("k", 0.256, "rk_blade"))
bl, tip = bb.blade("k_blade", 0.284, 1.036 - 0.284, 0.028, 0.020, 0.009, 0.004, 0.070, 0.008, "rk_blade")
parts.append(bl)
sword = bb.join(parts, "rukia_shikai_blade")
bb.link_only(sword, exp)

# ---- ribbon: 10 segments, hinge at the proximal end, mesh runs along -Z (rest pose hangs/trails behind the pommel)
SEG, OVER, W0, W1, T, ROOT = 0.25, 0.0, 0.040, 0.028, 0.002, -0.010   # Gate B B6: OVER = 0 (no overlap)
width = lambda d: W0 + (W1 - W0) * d / (10 * SEG)
ribbons = []
for n in range(1, 11):
    d0 = (n - 1) * SEG
    d1 = d0 + SEG + OVER
    pts = [(-width(d0) / 2, 0.0), (width(d0) / 2, 0.0), (width(d1) / 2, -(SEG + OVER)), (-width(d1) / 2, -(SEG + OVER))]
    # Gate B B7: segment 10 is the same 12-tri trapezoid; the swallow tail is an alpha cut-out in the texture
    o = bb.prism_y("rukia_shikai_ribbon_%02d" % n, pts, -T / 2, T / 2, "rk_ribbon")
    o.location = (0.0, 0.0, ROOT - (n - 1) * SEG)
    bb.link_only(o, exp)
    ribbons.append(o)

empties = [("grip_hand", (0, 0, 0.19)), ("ribbon_root", (0, 0, ROOT)), ("tip", tuple(tip))]
for n, v in empties:
    bb.link_only(bb.empty(n, v, exp, 0.03), exp)

chk = bb.tsuba_snowflake("chk", 0.250, "rk_blade")
chk.name = "tsuba_check"
allobj = [sword] + ribbons
notes = ["Blade 0.752 m from the habaki top (0.78 m from the habaki start): width 28 to 20 mm, thickness 9 to 4 mm, kissaki 70 mm, sori 8 mm chord deviation (tip offset 32 mm after Gate B B1); overall 1.036 m. Habaki is the BASE 32 x 10 mm box.",
         "Snowflake tsuba (Gate B B5): 88 mm round plate (48-segment outline), rim 7 mm, hub 36 mm, six spokes widening 7 to 12 mm, six windows 19 mm deep (boolean cut).",
         "Kashira: plain oval cap, 22 x 30 mm (Gate B B2). Gate B B8: a 14 x 8 x 10 mm knot block (`rk_ribbon` material) is joined into the blade mesh below the kashira; `ribbon_root` (0, 0, -0.010) is its bottom face.",
         "Ribbon (Gate B B6, B7): 10 hinged segments, exactly 0.25 m long, pitch 0.25 m, no overlap, 40 to 28 mm wide, 2 mm thick, identical trapezoid prisms of 12 tris (the swallow tail of segment 10 is a texture alpha cut-out). Origins = hinge points at z = -0.010 - 0.25 (n-1).",
         "Objects are not parented; ribbon objects keep location = hinge, rotation 0, scale 1."]
deviations = [
    "Ribbon mesh runs along local -Z (the ribbon trails behind the pommel at z < 0, and rotation must stay 0 for export), so the 'local +Z points along the ribbon' wording of the bible cannot hold literally (Gate B: kept, code bends about local X).",
    "Sori 8 mm is the chord deviation; tip offset = 4 x 8 = 32 mm (Gate B B1, bb.SORI_K = 4).",
    "Tsuka runs z 0.014 to 0.236 between kashira and fuchi (no overlapping solids); wrap-diamond gaps `#C9D6EA` are texture-only and not modelled; emissive zones are not set up in the blockout.",
    "Tsuba blade hole not cut (enclosed pocket under the habaki).",
]
questions = []
bb.finish(MODEL, allobj, [("sword", [sword], 0.10), ("ribbon_and_sword", allobj, 0.25, dict(H=860)),
                          ("tsuba_detail", [chk], 0.01, dict(views=[bb.VIEW_TOP, bb.VIEWS[2]], W=520, H=520))],
          empties, notes, deviations, questions, temp_objs=[chk])
