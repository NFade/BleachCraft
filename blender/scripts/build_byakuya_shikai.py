# Phase 3 steps 2-3: detailed byakuya_shikai (geometry + UVs into the 256x256 atlas). Re-runnable; builds from scratch.
# Objects: byakuya_shikai_hilt (copy of the byakuya_sealed hilt + tsuba + habaki with a 1 mm inset collar; no blade, no tang stub),
# byakuya_shikai_petal (16 tris crescent leaf, origin = tail apex, scene location (0.20, 0, 0)), byakuya_shikai_shard (8 tris splinter,
# origin = centroid, scene location (0.30, 0, 0.05)). Gate B: B2 (hilt ovals, via build_hilt defaults), B9, B10 + detailing list.
import sys, os, math, importlib
SD = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bb_common as bb, atlas_layouts as al, detail_common as dc
for _m in (bb, al, dc):
    importlib.reload(_m)
from mathutils import Vector

MODEL = "byakuya_shikai"
L = al.BYAKUYA_SHIKAI
OUT = os.path.join(dc.EXPORT, MODEL)
os.makedirs(OUT, exist_ok=True)

bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
mat = dc.atlas_material(MODEL, os.path.join(OUT, MODEL + "_diffuse.png"), os.path.join(OUT, MODEL + "_emissive.png"), tint="#F9C8F6", strength=1.0)

Z_TSUBA0, Z_TSUBA1 = 0.250, 0.257
Z_HAB0, Z_HAB1 = al.SHIKAI_HAB0, al.SHIKAI_HAB1

# ---- byakuya_shikai_hilt: byakuya_sealed hilt (24 rings x 12 sides, 11 raised diamonds), window tsuba, habaki as the hilt end
mb = dc.MB()
dc.build_hilt(mb, L, n_rings=al.BYAKUYA_WRAP_RINGS)
outline, holes = al.byakuya_tsuba_shapes()
dc.plate(mb, outline, holes, Z_TSUBA0, Z_TSUBA1, 0.0008, L, L["tsuba_front"], L["tsuba_back"], L["tsuba_rim"], L["tsuba_win"],
         al.BYAKUYA_TSUBA_PX_PER_M)
# B9: habaki 32 (Y) x 10 (X) x 28 mm, z 0.257..0.285, flat top (no tang stub); detailing: one 1 mm inset on the top face = collar
dc.cprism(mb, 0.005, 0.016, 0.001, Z_HAB0, Z_HAB1, 0.001, L["habaki_side"], L["habaki_top"], 430.0, k0=3)
hilt = mb.finish("byakuya_shikai_hilt", mat, exp)

# ---- byakuya_shikai_petal: B10 crescent leaf, 16 tris. Planar UV on the (z, y) plane, so the +X and -X faces share texels (mirrored).
pv = al.petal_verts()
m2 = dc.MB()
V = [m2.v(Vector(p)) for p in pv]
UVp = [al.petal_uv_xy(L["petal"], p[2], p[1]) for p in pv]


def pface(idx):
    m2.face([V[i] for i in idx], [UVp[i] for i in idx])


for k in range(4):
    pface((0, 1 + k, 1 + (k + 1) % 4))                                   # tail fan
for k in range(4):
    pface((1 + k, 1 + (k + 1) % 4, 5 + (k + 1) % 4, 5 + k))              # ring A -> ring B
for k in range(4):
    pface((5 + k, 5 + (k + 1) % 4, 9))                                   # tip fan
petal = m2.finish("byakuya_shikai_petal", mat, exp)
petal.location = (0.20, 0.0, 0.0)

# ---- byakuya_shikai_shard: irregular triangular splinter (50 x 20 x 3 mm), 8 tris, origin = centroid
tri = al.shard_tri()
t = al.SHARD_T / 2.0
m3 = dc.MB()
SV = [m3.v(Vector((-t, y, z))) for y, z in tri] + [m3.v(Vector((t, y, z))) for y, z in tri]
FI, SI = L["shard_face"], L["shard_side"]
fuv = [al.shard_face_uv(FI, y, z) for y, z in tri]
m3.face([SV[0], SV[1], SV[2]], fuv)
m3.face([SV[3], SV[4], SV[5]], fuv)
for k in range(3):
    k1 = (k + 1) % 3
    f0, f1 = k / 3.0, (k + 1) / 3.0
    m3.face([SV[k], SV[k1], SV[3 + k1], SV[3 + k]], [SI.uv(f0, 0.0), SI.uv(f0, 1.0), SI.uv(f1, 1.0), SI.uv(f1, 0.0)])
shard = m3.finish("byakuya_shikai_shard", mat, exp)
shard.location = (0.30, 0.0, 0.05)

for o in (hilt, petal, shard):
    o.data.update()
emp = dc.set_empties(exp, [("grip_hand", (0, 0, 0.19)), ("tang_tip", (0, 0, Z_HAB1))])
result = {"tris": {o.name: sum(len(p.vertices) - 2 for p in o.data.polygons) for o in (hilt, petal, shard)}}
