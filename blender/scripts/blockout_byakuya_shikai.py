# Blockout: byakuya_shikai (ART_BIBLE 1.4). Re-runnable; builds from scratch.
#   blender --background --factory-startup --python blockout_byakuya_shikai.py      (then: python bb_compose.py byakuya_shikai)
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts")
import importlib, bb_common as bb
importlib.reload(bb)
from mathutils import Vector

MODEL = "byakuya_shikai"
bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
bb.bk_materials()
bb.mat("bk_petal", "#E5A4DC", rough=0.4)
bb.mat("bk_shard", "#C9CED6", rough=0.3, metallic=0.9)

# ---- byakuya_shikai_hilt: same hilt as byakuya_sealed + habaki + 30 mm bare tang stub
parts = bb.bk_hilt("h")
parts.append(bb.habaki("h", 0.257, "bk_bronze"))
parts.append(bb.box("h_tang", (-0.0025, -0.005, 0.285), (0.0025, 0.005, 0.315), "bk_tang"))   # 5 (X) x 10 (Y) mm section
hilt = bb.join(parts, "byakuya_shikai_hilt")
bb.link_only(hilt, exp)

# ---- byakuya_shikai_petal: leaf blade, origin at the tail end, +Z along the blade, edge -Y, sori 5 mm
LEN, WMAX, T, TANG, SORI = 0.120, 0.026, 0.004, 0.006, 0.005
sori = lambda z: SORI * (z / LEN) ** 2
def ring(z, w, t):
    return [Vector((0, -w / 2, z)), Vector((t / 2, 0, z)), Vector((0, w / 2, z)), Vector((-t / 2, 0, z))]
r0 = [v + Vector((0, sori(TANG), 0)) for v in ring(TANG, 0.004, 0.002)]
r1 = [v + Vector((0, sori(0.4 * LEN), 0)) for v in ring(0.4 * LEN, WMAX, T)]
verts = [Vector((0, 0, 0))] + r0 + r1 + [Vector((0, sori(LEN) + 0.001, LEN))]
faces = []
for k in range(4):                       # tail apex -> r0
    faces.append((0, 1 + k, 1 + (k + 1) % 4))
for k in range(4):                       # r0 -> r1
    faces.append((1 + k, 1 + (k + 1) % 4, 5 + (k + 1) % 4, 5 + k))
for k in range(4):                       # r1 -> tip apex
    faces.append((5 + k, 5 + (k + 1) % 4, 9))
petal = bb.mesh_obj("byakuya_shikai_petal", verts, faces, ["bk_petal"])
petal.location = (0.20, 0.0, 0.0)
bb.link_only(petal, exp)

# ---- byakuya_shikai_shard: irregular triangular splinter, 50 x 20 x 3 mm, origin at the centroid
tri = [(0.003, 0.025), (-0.010, -0.022), (0.010, -0.028)]            # (y, z)
cy, cz = sum(p[0] for p in tri) / 3, sum(p[1] for p in tri) / 3
tri = [(y - cy, z - cz) for y, z in tri]
sv = [Vector((-0.0015, y, z)) for y, z in tri] + [Vector((0.0015, y, z)) for y, z in tri]
sf = [(0, 1, 2), (3, 4, 5)] + [(k, (k + 1) % 3, 3 + (k + 1) % 3, 3 + k) for k in range(3)]
shard = bb.mesh_obj("byakuya_shikai_shard", sv, sf, ["bk_shard"])
shard.location = (0.30, 0.0, 0.05)
bb.link_only(shard, exp)

empties = [("grip_hand", (0, 0, 0.19)), ("tang_tip", (0, 0, 0.315))]
for n, v in empties:
    bb.link_only(bb.empty(n, v, exp, 0.03), exp)

notes = ["Hilt is built with the same `bb.bk_hilt()` as byakuya_sealed (kashira, tsuka, fuchi, window-frame tsuba, habaki), so dimensions and palette are identical; the blade is replaced by a 30 mm steel tang stub (5 x 10 mm, `#8E96A3`) above the habaki (z 0.285 to 0.315).",
         "Petal: leaf blade 120 mm long, 26 mm wide at 40 percent, 4 mm thick flattened diamond section, 6 mm tang, 5 mm sori toward +Y, 16 tris (limit 20). Origin at the tail end (z 0), +Z along the blade, edge -Y; stored at (0.20, 0, 0) in the scene so it does not overlap the hilt.",
         "Shard: 3-sided prism 50 x 20 x 3 mm, 8 tris (limit 12), origin at the centroid, stored at (0.30, 0, 0.05).",
         "Petal and shard are shown at their real size; the render rows use a 1 cm ruler for them."]
deviations = [
    "`tang_tip` empty at z 0.315 instead of 0.31: the 7 mm tsuba pushes habaki to z 0.257 to 0.285 (same shift as byakuya_sealed), so a 30 mm stub ends at 0.315.",
    "Petal is 16 tris as a three-station spindle (tail apex, tail ring, widest ring at 40 percent, tip apex); the tip taper is a straight pyramid, no extra ring.",
    "Shard is 8 tris (two triangles plus three quads), the bible's 6 would need an open mesh.",
    "Tsuka z 0.014 to 0.236 (no overlapping solids), wrap diamonds not modelled; emissive petal zones not set up.",
]
questions = [
    "Petal spindle with only one widest ring reads as a diamond; accept 16 tris or spend the remaining 4 tris on a second taper ring for a leaf-like belly?",
    "Should the shard also carry a tiny tang so it matches the petal outline (as a broken piece of one)?",
]
bb.finish(MODEL, [hilt, petal, shard], [("hilt", [hilt], 0.05), ("petal", [petal], 0.01, dict(H=700, W=420)), ("shard", [shard], 0.01, dict(H=420, W=420))],
          empties, notes, deviations, questions)
