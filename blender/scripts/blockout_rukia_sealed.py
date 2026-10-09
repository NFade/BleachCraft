# Blockout: rukia_sealed (ART_BIBLE 1.1, BASE 1.0). Re-runnable; builds from scratch.
#   blender --background --factory-startup --python blockout_rukia_sealed.py      (then: python bb_compose.py rukia_sealed)
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else r"D:\MineBleach\blender\scripts")
import importlib, bb_common as bb
importlib.reload(bb)
from bb_common import *

MODEL = "rukia_sealed"
bb.clean_scene()
exp = bb.new_collection("export")
bb.new_collection("helpers")

# palette (ART_BIBLE 1.1)
bb.mat("rs_wrap", "#822933", rough=0.7)
bb.mat("rs_underlay", "#D8C9A0", rough=0.7)
bb.mat("rs_tsuba", "#AF9668", rough=0.4, metallic=0.8)
bb.mat("rs_fittings", "#8C7650", rough=0.4, metallic=0.8)
bb.mat("rs_saya", "#2A2433", rough=0.25)
bb.mat("rs_steel", "#B9C3D2", rough=0.3, metallic=0.9)


def hilt(prefix):
    parts = bb.hilt_parts(prefix, "rs_wrap", "rs_fittings", "rs_fittings")
    parts.append(bb.tsuba_rukia_sealed(prefix, 0.250, "rs_tsuba"))
    return parts


# ---- drawn: hilt + tsuba + habaki + blade
dp = hilt("d")
dp.append(bb.habaki("d", 0.256, "rs_tsuba"))
bl, tip = bb.blade("d_blade", 0.284, 0.980 - 0.284, 0.032, 0.022, 0.010, 0.005, 0.070, 0.020, "rs_steel")
dp.append(bl)
drawn = bb.join(dp, "rukia_sealed_drawn")

# ---- sheathed: hilt + tsuba + saya (+ koiguchi, kojiri); blade inside is not modelled
sp = hilt("s")
Z0, L, TIP_OFF = 0.256, 0.724, bb.SORI_K * 0.023  # saya sori = 20 + 3 mm chord deviation (x4 = 92 mm tip offset)
SA, SB = 0.013, 0.020                      # saya semi-axes: 26 mm (X) x 40 mm (Y)
rings = []
nrg = 8
for i in range(nrg + 1):
    z = 0.270 + (0.982 - 0.270) * i / nrg
    rings.append(bb.ring_pts(bb.oval(SA, SB, 12), z, bb.sori_off(z, Z0, L, TIP_OFF), bb.sori_phi(z, Z0, L, TIP_OFF)))
sp.append(bb.loft("s_saya", rings, "rs_saya", smooth=True))
sp.append(bb.loft("s_koiguchi", [bb.ring_pts(bb.oval(SA + 0.001, SB + 0.001, 12), 0.256),
                                  bb.ring_pts(bb.oval(SA + 0.001, SB + 0.001, 12), 0.270)], "rs_fittings"))
kz = [(0.982, 1.0, 0.0), (0.994, 0.88, 0.0), (1.000, 0.45, 0.0)]
krings = [bb.ring_pts(bb.oval(SA * k + 0.0005, SB * k + 0.0005, 12), z, bb.sori_off(z, Z0, L, TIP_OFF), bb.sori_phi(z, Z0, L, TIP_OFF))
          for z, k, _ in kz]
sp.append(bb.loft("s_kojiri", krings, "rs_fittings"))
sheathed = bb.join(sp, "rukia_sealed_sheathed")

for o in (drawn, sheathed):
    bb.link_only(o, exp)
empties = [("grip_hand", (0, 0, 0.19)), ("tip", tuple(tip))]
for n, v in empties:
    bb.link_only(bb.empty(n, v, exp, 0.03), exp)

notes = ["Both objects are at the world origin (kashira) and overlap by design; in the .blend `rukia_sealed_sheathed` is hidden in the viewport (eye icon).",
         "Tsuba (Gate B B4): 62 (X) x 72 (Y) x 6 mm, concave corners (r 10 mm), two curved sukashi slits (20 x 6 mm, bow 3 mm) at y = +-25 mm. The 32 x 10 mm blade hole is not cut (it would be an enclosed pocket between fuchi top and habaki).",
         "Hilt ovals (Gate B B2): kashira 22 x 30 mm, tsuka 24 x 30 mm, fuchi 26 x 32 mm (X x Y).",
         "Blade: 6-vertex shinogi section (edge, ridge at 55 percent, spine), kissaki 70 mm, tapering 32 to 22 mm and 10 to 5 mm; centreline is parabolic, sori 20 mm chord deviation = 80 mm tip offset (Gate B B1).",
         "Saya: koiguchi collar 14 mm, kojiri cap 18 mm, both in the fittings colour; body in the violet-black lacquer; sori 23 mm chord deviation (92 mm tip offset)."]
deviations = [
    "Sori follows the chord reading (Gate B B1): bb.SORI_K = 4, tip offset 80 mm (blade) and 92 mm (saya).",
    "Saya cross-section is 26 mm (X) x 40 mm (Y), not 40 (X) x 26 (Y): the blade is 32 mm wide along Y and 10 mm thick along X, so a 26 mm Y extent cannot contain it.",
    "Tsuka runs z 0.014 to 0.236 (between kashira and fuchi) instead of z 0 to 0.250, so the solids do not overlap (no hidden interior faces); visible result and total length are unchanged.",
    "Habaki is a plain 32 x 10 x 28 mm box and the blade starts on its top face (z 0.284) so both are flush, no coincident faces.",
    "Wrap diamonds (hishimaki) and the cream underlay colour are not modelled (texture-only detail); the tsuka carries the red wrap colour.",
]
questions = []
chk = bb.tsuba_rukia_sealed("chk", 0.250, "rs_tsuba")
chk.name = "tsuba_check"
bb.finish(MODEL, [drawn, sheathed], [("drawn", [drawn], 0.10), ("sheathed", [sheathed], 0.10),
                                     ("tsuba_detail", [chk], 0.01, dict(views=[bb.VIEW_TOP, bb.VIEWS[2]], W=520, H=520))],
          empties, notes, deviations, questions, hidden=("rukia_sealed_sheathed",), temp_objs=[chk])
