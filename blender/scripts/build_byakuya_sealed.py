# Phase 3 steps 2-3: detailed byakuya_sealed (geometry + UVs into the 256x256 atlas). Re-runnable; builds from scratch.
#   exec inside Blender (MCP) or: blender --background --factory-startup --python build_byakuya_sealed.py
# Textures are painted by paint_byakuya_sealed.py (system python) into blender/export/byakuya_sealed/*.png; if the PNGs exist the
# preview material shows them. Mirrors build_rukia_sealed.py. Gate B: B1 (sori), B2 (grip ovals) only, plus detailing list.
import sys, os, math, importlib
SD = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts"
if SD not in sys.path:
    sys.path.insert(0, SD)
import bb_common as bb, atlas_layouts as al, detail_common as dc
for _m in (bb, al, dc):
    importlib.reload(_m)
from mathutils import Vector

MODEL = "byakuya_sealed"
L = al.BYAKUYA_SEALED
OUT = os.path.join(dc.EXPORT, MODEL)
os.makedirs(OUT, exist_ok=True)

bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
mat = dc.atlas_material(MODEL, os.path.join(OUT, MODEL + "_diffuse.png"), os.path.join(OUT, MODEL + "_emissive.png"))

Z_TSUBA0, Z_TSUBA1 = 0.250, 0.257     # 7 mm window frame; habaki, blade and saya shifted +1 mm (tip stays at 0.980)
Z_HAB0, Z_HAB1 = 0.257, 0.285


def tsuba(mb):
    """56 (X) x 92 (Y) x 7 mm window frame, r 5 mm corners, four stepped L windows (hard walls), rim bevel 0.8 mm (outer rim only)."""
    outline, holes = al.byakuya_tsuba_shapes()
    dc.plate(mb, outline, holes, Z_TSUBA0, Z_TSUBA1, 0.0008, L, L["tsuba_front"], L["tsuba_back"], L["tsuba_rim"], L["tsuba_win"],
             al.BYAKUYA_TSUBA_PX_PER_M)


def hilt_and_tsuba(mb):
    # B2: kashira 22 x 30 x 14, tsuka 24 x 30, fuchi 26 x 32 (defaults of build_hilt); 24 rings = 11 raised diamonds (odd rings 1..21)
    dc.build_hilt(mb, L, n_rings=al.BYAKUYA_WRAP_RINGS)
    tsuba(mb)


# ---- byakuya_sealed_drawn: hilt + tsuba + habaki + blade
mb = dc.MB()
hilt_and_tsuba(mb)
dc.cprism(mb, 0.005, 0.016, 0.001, Z_HAB0, Z_HAB1, 0.0, L["habaki_side"], L["habaki_top"], 430.0, k0=3)
apex = dc.blade_mesh(mb, L, Z_HAB1, 0.980 - Z_HAB1, 0.032, 0.022, 0.010, 0.005, 0.070, 0.020, al.BLADE_RINGS_BODY, al.BYAKUYA_BLADE_PX_PER_M)
drawn = mb.finish("byakuya_sealed_drawn", mat, exp)

# ---- byakuya_sealed_sheathed: hilt + tsuba + koiguchi + saya + kojiri (blade inside is not modelled)
mb = dc.MB()
hilt_and_tsuba(mb)
Z0, LEN, TIP_OFF = 0.257, 0.723, bb.SORI_K * 0.023
SA, SB = 0.013, 0.020
def sori_ring(a, b, z, sides=16):
    return bb.ring_pts(bb.oval(a, b, sides), z, bb.sori_off(z, Z0, LEN, TIP_OFF), bb.sori_phi(z, Z0, LEN, TIP_OFF))
NR = 12
z_s0, z_s1 = 0.271, 0.982
saya = [sori_ring(SA, SB, z_s0 + (z_s1 - z_s0) * i / (NR - 1)) for i in range(NR)]
dc.tube(mb, saya, 4, L["saya"], [i / (NR - 1) for i in range(NR)], True, cap0=(L["saya"], 1e-9, True), cap1=(L["saya"], 1e-9, True))
dc.tube(mb, [sori_ring(SA + 0.001, SB + 0.001, 0.257), sori_ring(SA + 0.001, SB + 0.001, 0.271)], 4, L["koiguchi"], [0.0, 1.0], False,
        cap0=(L["koiguchi"], 1e-9, True), cap1=(L["koiguchi"], 1e-9, False))
kz = [(0.982, 1.0), (0.994, 0.88), (1.000, 0.45)]
dc.tube(mb, [sori_ring(SA * k + 0.0005, SB * k + 0.0005, z) for z, k in kz], 4, L["kojiri"], [0.0, 0.7, 1.0], False,
        cap0=(L["kojiri"], 1e-9, True), cap1=(L["kojiri_cap"], 450.0, False))
sheathed = mb.finish("byakuya_sealed_sheathed", mat, exp)

# ---- byakuya_sealed_saya: the scabbard alone for the draw animation (same origin / outer rings / UVs as the saya part of `sheathed`,
# one closed shell with an open mouth at z = Z0, dark inner liner; no tsuka, tsuba, habaki, kashira or blade). Added after the two
# objects above, so their geometry is untouched.
mb = dc.MB()
saya_info = dc.build_saya(mb, L, Z0, z_s0, z_s1, LEN, TIP_OFF, SA, SB, NR)
saya_o = mb.finish("byakuya_sealed_saya", mat, exp)

for o in (drawn, sheathed, saya_o):
    o.data.update()
emp = dc.set_empties(exp, [("grip_hand", (0, 0, 0.19)), ("tip", tuple(apex)), ("saya_mouth", (0.0, 0.0, Z0))])
sheathed.hide_set(True)
saya_o.hide_set(True)
result = {"apex": tuple(apex), "tris": {o.name: sum(len(p.vertices) - 2 for p in o.data.polygons) for o in (drawn, sheathed, saya_o)}, "saya": saya_info}
