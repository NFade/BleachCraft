# Blockout: byakuya_sealed (ART_BIBLE 1.3, BASE 1.0). Re-runnable; builds from scratch.
#   blender --background --factory-startup --python blockout_byakuya_sealed.py      (then: python bb_compose.py byakuya_sealed)
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts")
import importlib, bb_common as bb
importlib.reload(bb)

MODEL = "byakuya_sealed"
bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")
bb.bk_materials()

# ---- drawn: hilt + tsuba + habaki + blade (BASE blade, z 0.284 to 0.980)
dp = bb.bk_hilt("d")
dp.append(bb.habaki("d", 0.257, "bk_bronze"))
bl, tip = bb.blade("d_blade", 0.285, 0.980 - 0.285, 0.032, 0.022, 0.010, 0.005, 0.070, 0.020, "bk_steel")
dp.append(bl)
drawn = bb.join(dp, "byakuya_sealed_drawn")

# ---- sheathed: hilt + tsuba + saya (+ koiguchi, kojiri)
sp = bb.bk_hilt("s")
Z0, L, TIP_OFF = 0.257, 0.723, bb.SORI_K * 0.023
SA, SB = 0.013, 0.020
rings = []
for i in range(9):
    z = 0.271 + (0.982 - 0.271) * i / 8
    rings.append(bb.ring_pts(bb.oval(SA, SB, 12), z, bb.sori_off(z, Z0, L, TIP_OFF), bb.sori_phi(z, Z0, L, TIP_OFF)))
sp.append(bb.loft("s_saya", rings, "bk_saya", smooth=True))
sp.append(bb.loft("s_koiguchi", [bb.ring_pts(bb.oval(SA + 0.001, SB + 0.001, 12), 0.257),
                                  bb.ring_pts(bb.oval(SA + 0.001, SB + 0.001, 12), 0.271)], "bk_bronze"))
krings = [bb.ring_pts(bb.oval(SA * k + 0.0005, SB * k + 0.0005, 12), z, bb.sori_off(z, Z0, L, TIP_OFF), bb.sori_phi(z, Z0, L, TIP_OFF))
          for z, k in ((0.982, 1.0), (0.994, 0.88), (1.000, 0.45))]
sp.append(bb.loft("s_kojiri", krings, "bk_bronze"))
sheathed = bb.join(sp, "byakuya_sealed_sheathed")

for o in (drawn, sheathed):
    bb.link_only(o, exp)
empties = [("grip_hand", (0, 0, 0.19)), ("tip", tuple(tip))]
for n, v in empties:
    bb.link_only(bb.empty(n, v, exp, 0.03), exp)

chk = bb.tsuba_byakuya_sealed("chk", 0.250, "bk_bronze")
chk.name = "tsuba_check"
notes = ["Tsuba: 56 (X) x 92 (Y) x 7 mm window frame, corner radius 5 mm, frame bar 8 mm, centre bar (along Y) 10 mm, transverse bar 8 mm, hub plate 40 (Y) x 18 (X) mm; four stepped L-shaped windows (boolean cut). Because the tsuba is 7 mm thick it occupies z 0.250 to 0.257, so habaki starts at 0.257, the blade at 0.285 and the saya at 0.257 (everything above the guard is shifted +1 mm against the BASE table; total drawn length 0.980 m).",
         "Kashira: cream cylinder (oval 30 x 22 mm, 14 mm high, small chamfer ring). Fuchi, tsuba, habaki and the saya fittings share the bronze colour; saya is dark violet-black `#2E2840`, no white variant.",
         "Both objects are at the origin and overlap by design; `byakuya_sealed_sheathed` is hidden in the viewport in the .blend.",
         "`byakuya_sealed_drawn` and `_sheathed` hilts are separate (unlinked) mesh data, so `byakuya_shikai_hilt` and `byakuya_bankai_hilt_ground` reuse the same builder `bb.bk_hilt()`."]
deviations = [
    "Sori: tip deflection from the base axis, 20 mm (see rukia_sealed note, `bb.SORI_K`).",
    "Saya cross-section 26 (X) x 40 (Y) mm instead of 40 x 26 (cannot contain the 32 mm wide blade otherwise).",
    "Tsuka runs z 0.014 to 0.236 between kashira and fuchi (no overlapping solids). Wrap diamonds and cream windows `#E8DDB5` are texture-only and not modelled.",
    "Tsuba is 7 mm thick (bible 1.3) vs 6 mm in the BASE table; the parts above it start 1 mm higher (see notes), so the drawn blade is 1 mm shorter in nagasa to keep the tip at z 0.980.",
    "Blade hole not cut in the tsuba (would be an enclosed pocket).",
]
questions = [
    "Tsuba thickness 7 mm (section 1.3) vs BASE 6 mm: keep the 1 mm upward shift of habaki, blade and saya, or move the tsuba to z 0.249 to 0.256 so the blade geometry matches the BASE table?",
    "Sori reading (20 mm tip offset vs 80 mm chord deviation), same as rukia_sealed.",
    "Tsuba windows are modelled as hard-edged L shapes; is the 4 mm hub step readable enough or should the frame get a bevel in the detail pass?",
]
bb.finish(MODEL, [drawn, sheathed], [("drawn", [drawn], 0.10), ("sheathed", [sheathed], 0.10),
                                     ("tsuba_detail", [chk], 0.01, dict(views=[bb.VIEW_TOP, bb.VIEWS[2]], W=520, H=520))],
          empties, notes, deviations, questions, hidden=("byakuya_sealed_sheathed",), temp_objs=[chk])
