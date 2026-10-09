# Phase 3 steps 2-3: detailed rukia_sealed (geometry + UVs into the 256x256 atlas). Re-runnable; builds from scratch.
#   exec inside Blender (MCP) or: blender --background --factory-startup --python build_rukia_sealed.py
# Textures are painted by paint_rukia_sealed.py (system python) into blender/export/rukia_sealed/*.png; if the PNGs exist the
# preview material shows them. Export: export_model.py rukia_sealed.
import sys, os, math, importlib
SD = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bb_common as bb, atlas_layouts as al, detail_common as dc
for _m in (bb, al, dc):
    importlib.reload(_m)
from mathutils import Vector

MODEL = "rukia_sealed"
L = al.RUKIA_SEALED
OUT = os.path.join(dc.EXPORT, MODEL)
os.makedirs(OUT, exist_ok=True)

bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
mat = dc.atlas_material(MODEL, os.path.join(OUT, MODEL + "_diffuse.png"), os.path.join(OUT, MODEL + "_emissive.png"))


def tsuba(mb):
    """Gate B B4: 62 x 72 x 6 mm, concave corners r 10 mm, two bowed slits 20 x 6 mm at y = +-25 mm, rim bevel 0.8 mm."""
    outline, holes = al.sealed_tsuba_shapes()
    dc.plate(mb, outline, holes, 0.250, 0.256, 0.0008, L, L["tsuba_front"], L["tsuba_back"], L["tsuba_rim"], L["tsuba_slit"],
             al.SEALED_TSUBA_PX_PER_M)


def hilt_and_tsuba(mb):
    dc.build_hilt(mb, L)
    tsuba(mb)


# ---- rukia_sealed_drawn: hilt + tsuba + habaki + blade
mb = dc.MB()
hilt_and_tsuba(mb)
dc.cprism(mb, 0.005, 0.016, 0.001, 0.256, 0.284, 0.0, L["habaki_side"], L["habaki_top"], 430.0, k0=3)
apex = dc.blade_mesh(mb, L, 0.284, 0.980 - 0.284, 0.032, 0.022, 0.010, 0.005, 0.070, 0.020, al.BLADE_RINGS_BODY, al.SEALED_BLADE_PX_PER_M)
drawn = mb.finish("rukia_sealed_drawn", mat, exp)

# ---- rukia_sealed_sheathed: hilt + tsuba + koiguchi + saya + kojiri (blade inside is not modelled)
mb = dc.MB()
hilt_and_tsuba(mb)
Z0, LEN, TIP_OFF = 0.256, 0.724, bb.SORI_K * 0.023
SA, SB = 0.013, 0.020
def sori_ring(a, b, z, sides=16):
    return bb.ring_pts(bb.oval(a, b, sides), z, bb.sori_off(z, Z0, LEN, TIP_OFF), bb.sori_phi(z, Z0, LEN, TIP_OFF))
NR = 12
z_s0, z_s1 = 0.270, 0.982
saya = [sori_ring(SA, SB, z_s0 + (z_s1 - z_s0) * i / (NR - 1)) for i in range(NR)]
dc.tube(mb, saya, 4, L["saya"], [i / (NR - 1) for i in range(NR)], True, cap0=(L["saya"], 1e-9, True), cap1=(L["saya"], 1e-9, True))
dc.tube(mb, [sori_ring(SA + 0.001, SB + 0.001, 0.256), sori_ring(SA + 0.001, SB + 0.001, 0.270)], 4, L["koiguchi"], [0.0, 1.0], False,
        cap0=(L["koiguchi"], 1e-9, True), cap1=(L["koiguchi"], 1e-9, False))
kz = [(0.982, 1.0), (0.994, 0.88), (1.000, 0.45)]
dc.tube(mb, [sori_ring(SA * k + 0.0005, SB * k + 0.0005, z) for z, k in kz], 4, L["kojiri"], [0.0, 0.7, 1.0], False,
        cap0=(L["kojiri"], 1e-9, True), cap1=(L["kojiri_cap"], 450.0, False))
sheathed = mb.finish("rukia_sealed_sheathed", mat, exp)

for o in (drawn, sheathed):
    o.data.update()
emp = dc.set_empties(exp, [("grip_hand", (0, 0, 0.19)), ("tip", tuple(apex))])
sheathed.hide_set(True)
result = {"apex": tuple(apex), "tris": {o.name: sum(len(p.vertices) - 2 for p in o.data.polygons) for o in (drawn, sheathed)}}
