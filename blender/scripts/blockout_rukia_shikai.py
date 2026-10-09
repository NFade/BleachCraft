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
kash = parts[0]
bb.boolean_diff(kash, [bb.cyl_x("k_hole", 0.002, -0.03, 0.03, 0.0, 0.007)])  # 4 mm ribbon hole through the kashira (along X)
parts.append(bb.tsuba_snowflake("k", 0.250, "rk_blade"))
parts.append(bb.habaki("k", 0.256, "rk_blade"))
bl, tip = bb.blade("k_blade", 0.284, 1.036 - 0.284, 0.028, 0.020, 0.009, 0.004, 0.070, 0.008, "rk_blade")
parts.append(bl)
sword = bb.join(parts, "rukia_shikai_blade")
bb.link_only(sword, exp)

# ---- ribbon: 10 segments, hinge at the proximal end, mesh runs along -Z (rest pose hangs/trails behind the pommel)
SEG, OVER, W0, W1, T, ROOT = 0.25, 0.010, 0.040, 0.028, 0.002, -0.010
width = lambda d: W0 + (W1 - W0) * d / (10 * SEG)
ribbons = []
for n in range(1, 11):
    d0 = (n - 1) * SEG
    d1 = d0 + SEG + OVER
    pts = [(-width(d0) / 2, 0.0), (width(d0) / 2, 0.0), (width(d1) / 2, -(SEG + OVER)), (-width(d1) / 2, -(SEG + OVER))]
    if n == 10:  # swallow-tail cut
        hw = width(d1) / 2
        pts = [(-width(d0) / 2, 0.0), (width(d0) / 2, 0.0), (hw, -(SEG + OVER)), (0.0, -(SEG + OVER) + 0.05), (-hw, -(SEG + OVER))]
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
notes = ["Blade 0.752 m from the habaki top (0.78 m from the habaki start): width 28 to 20 mm, thickness 9 to 4 mm, kissaki 70 mm, sori 8 mm; overall 1.036 m. Habaki is the BASE 32 x 10 mm box.",
         "Snowflake tsuba: 80 mm round plate, rim 8 mm, hub 40 mm, six spokes widening 7 to 12 mm, six rounded-trapezoid windows (boolean cut, clean circle outline).",
         "Kashira: oval cap with a 4 mm cross hole (along X) for the ribbon; `ribbon_root` empty at (0, 0, -0.010).",
         "Ribbon: 10 hinged segments, 0.25 m pitch (0.26 m mesh, 10 mm overlap), 40 to 28 mm wide, 2 mm thick, trapezoid prisms (12 tris), segment 10 has the swallow tail. Origins = hinge points at z = -0.010 - 0.25 (n-1).",
         "Objects are not parented; ribbon objects keep location = hinge, rotation 0, scale 1."]
deviations = [
    "Ribbon mesh runs along local -Z (the ribbon trails behind the pommel at z < 0, and rotation must stay 0 for export), so the 'local +Z points along the ribbon' wording of the bible cannot hold literally; the chain axis is -Z. The Java chain code must bend about local X with the sign flipped.",
    "`rukia_shikai_ribbon_10` has 16 tris (pentagon prism for the swallow tail) against the 12 limit; the other nine are 12. A single-sided 3-tri-per-face strip would meet the limit at detail time.",
    "Sori 8 mm is the tip deflection from the base axis (see rukia_sealed note on `bb.SORI_K`).",
    "Tsuka runs z 0.014 to 0.236 between kashira and fuchi (no overlapping solids); wrap-diamond gaps `#C9D6EA` are texture-only and not modelled; emissive zones are not set up in the blockout.",
    "Tsuba blade hole not cut (enclosed pocket under the habaki).",
]
questions = [
    "Ribbon axis: is a local -Z mesh (rotation applied) acceptable for the chain animation, or should the objects be authored with +Z along the ribbon and a 180 degree rotation baked in the loader?",
    "Ribbon width is along X and thickness along Y (flat in the XZ plane). Should it face the viewer in first person instead (flat in YZ)?",
    "Is the 16-tri tail on segment 10 acceptable at blockout level?",
]
bb.finish(MODEL, allobj, [("sword", [sword], 0.10), ("ribbon_and_sword", allobj, 0.25, dict(H=860)),
                          ("tsuba_detail", [chk], 0.01, dict(views=[bb.VIEW_TOP, bb.VIEWS[2]], W=520, H=520))],
          empties, notes, deviations, questions, temp_objs=[chk])
