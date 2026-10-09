# BLOCKOUTS (Phase 3 step 1)

Blender 5.2.2 blockouts, Workbench renders, flat Principled colours from ART_BIBLE v2. Generated per model by `blender/scripts/blockout_<model>.py`.

Run headless (the Blender MCP socket was not available, no GUI window was opened): `blender.exe --background --factory-startup --python blender/scripts/blockout_<model>.py`, then `python blender/scripts/bb_compose.py <model>`. Binary: `D:\SteamLibrary\steamapps\common\Blender\blender.exe` (5.2.2 LTS).

## rukia_sealed

Scene: `blender/scenes/rukia_sealed.blend`. Render: `blender/renders/rukia_sealed_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `rukia_sealed_drawn` | 638 | (-0.031, -0.036, 0.0) | (0.031, 0.036, 0.9796) | (0.062, 0.072, 0.9796) | (0.0, 0.0, 0.0) |
| `rukia_sealed_sheathed` | 868 | (-0.031, -0.036, 0.0) | (0.031, 0.0436, 1.0006) | (0.062, 0.0796, 1.0006) | (0.0, 0.0, 0.0) |

Empties: `grip_hand` (0, 0, 0.19); `tip` (0, 0.0266, 0.9796)

Notes:
- Both objects are at the world origin (kashira) and overlap by design; in the .blend `rukia_sealed_sheathed` is hidden in the viewport (eye icon).
- Tsuba: 62 (X) x 72 (Y) x 6 mm, concave corners (r 8 mm), two curved sukashi slits (14 x 4 mm) at y = +-26 mm. The 32 x 10 mm blade hole is not cut (it would be an enclosed pocket between fuchi top and habaki).
- Blade: 6-vertex shinogi section (edge, ridge at 55 percent, spine), kissaki 70 mm, tapering 32 to 22 mm and 10 to 5 mm; centreline is parabolic.
- Saya: koiguchi collar 14 mm, kojiri cap 18 mm, both in the fittings colour; body in the violet-black lacquer.

Deviations from the art bible:
- Sori: read as the tip deflection from the base axis (20 mm, parabolic centreline, tilt applied to each ring), the same convention the bible uses for the giant blade (E14). `bb.SORI_K` (1.0) switches to the chord-deviation reading (K = 4 gives an 80 mm tip offset).
- Saya cross-section is 26 mm (X) x 40 mm (Y), not 40 (X) x 26 (Y): the blade is 32 mm wide along Y and 10 mm thick along X, so a 26 mm Y extent cannot contain it.
- Tsuka runs z 0.014 to 0.236 (between kashira and fuchi) instead of z 0 to 0.250, so the solids do not overlap (no hidden interior faces); visible result and total length are unchanged.
- Habaki is a plain 32 x 10 x 28 mm box and the blade starts on its top face (z 0.284) so both are flush, no coincident faces.
- Wrap diamonds (hishimaki) and the cream underlay colour are not modelled (texture-only detail); the tsuka carries the red wrap colour.

Open questions for Gate B:
- Sori: 20 mm tip offset reads almost straight at item scale. Keep it, or switch to the chord-deviation reading (80 mm tip offset, clearly curved, `bb.SORI_K = 4`)?
- Tsuka oval orientation: the bible gives 30 (X) x 24 (Y). Real grips are longer along the edge-spine axis (Y); keep the bible orientation?
- Does the saya need to follow a visible curve in the Minecraft item, or is a straighter saya preferable for the holster/ground pose?

## rukia_shikai

Scene: `blender/scenes/rukia_shikai.blend`. Render: `blender/renders/rukia_shikai_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `rukia_shikai_blade` | 918 | (-0.04, -0.04, 0.0) | (0.04, 0.04, 1.0359) | (0.08, 0.08, 1.0359) | (0.0, 0.0, 0.0) |
| `rukia_shikai_ribbon_01` | 12 | (-0.02, -0.001, -0.27) | (0.02, 0.001, -0.01) | (0.04, 0.002, 0.26) | (0.0, 0.0, -0.01) |
| `rukia_shikai_ribbon_02` | 12 | (-0.0194, -0.001, -0.52) | (0.0194, 0.001, -0.26) | (0.0388, 0.002, 0.26) | (0.0, 0.0, -0.26) |
| `rukia_shikai_ribbon_03` | 12 | (-0.0188, -0.001, -0.77) | (0.0188, 0.001, -0.51) | (0.0376, 0.002, 0.26) | (0.0, 0.0, -0.51) |
| `rukia_shikai_ribbon_04` | 12 | (-0.0182, -0.001, -1.02) | (0.0182, 0.001, -0.76) | (0.0364, 0.002, 0.26) | (0.0, 0.0, -0.76) |
| `rukia_shikai_ribbon_05` | 12 | (-0.0176, -0.001, -1.27) | (0.0176, 0.001, -1.01) | (0.0352, 0.002, 0.26) | (0.0, 0.0, -1.01) |
| `rukia_shikai_ribbon_06` | 12 | (-0.017, -0.001, -1.52) | (0.017, 0.001, -1.26) | (0.034, 0.002, 0.26) | (0.0, 0.0, -1.26) |
| `rukia_shikai_ribbon_07` | 12 | (-0.0164, -0.001, -1.77) | (0.0164, 0.001, -1.51) | (0.0328, 0.002, 0.26) | (0.0, 0.0, -1.51) |
| `rukia_shikai_ribbon_08` | 12 | (-0.0158, -0.001, -2.02) | (0.0158, 0.001, -1.76) | (0.0316, 0.002, 0.26) | (0.0, 0.0, -1.76) |
| `rukia_shikai_ribbon_09` | 12 | (-0.0152, -0.001, -2.27) | (0.0152, 0.001, -2.01) | (0.0304, 0.002, 0.26) | (0.0, 0.0, -2.01) |
| `rukia_shikai_ribbon_10` | 16 | (-0.0146, -0.001, -2.52) | (0.0146, 0.001, -2.26) | (0.0292, 0.002, 0.26) | (0.0, 0.0, -2.26) |

Empties: `grip_hand` (0, 0, 0.19); `ribbon_root` (0, 0, -0.01); `tip` (0, 0.014, 1.0359)

Notes:
- Blade 0.752 m from the habaki top (0.78 m from the habaki start): width 28 to 20 mm, thickness 9 to 4 mm, kissaki 70 mm, sori 8 mm; overall 1.036 m. Habaki is the BASE 32 x 10 mm box.
- Snowflake tsuba: 80 mm round plate, rim 8 mm, hub 40 mm, six spokes widening 7 to 12 mm, six rounded-trapezoid windows (boolean cut, clean circle outline).
- Kashira: oval cap with a 4 mm cross hole (along X) for the ribbon; `ribbon_root` empty at (0, 0, -0.010).
- Ribbon: 10 hinged segments, 0.25 m pitch (0.26 m mesh, 10 mm overlap), 40 to 28 mm wide, 2 mm thick, trapezoid prisms (12 tris), segment 10 has the swallow tail. Origins = hinge points at z = -0.010 - 0.25 (n-1).
- Objects are not parented; ribbon objects keep location = hinge, rotation 0, scale 1.

Deviations from the art bible:
- Ribbon mesh runs along local -Z (the ribbon trails behind the pommel at z < 0, and rotation must stay 0 for export), so the 'local +Z points along the ribbon' wording of the bible cannot hold literally; the chain axis is -Z. The Java chain code must bend about local X with the sign flipped.
- `rukia_shikai_ribbon_10` has 16 tris (pentagon prism for the swallow tail) against the 12 limit; the other nine are 12. A single-sided 3-tri-per-face strip would meet the limit at detail time.
- Sori 8 mm is the tip deflection from the base axis (see rukia_sealed note on `bb.SORI_K`).
- Tsuka runs z 0.014 to 0.236 between kashira and fuchi (no overlapping solids); wrap-diamond gaps `#C9D6EA` are texture-only and not modelled; emissive zones are not set up in the blockout.
- Tsuba blade hole not cut (enclosed pocket under the habaki).

Open questions for Gate B:
- Ribbon axis: is a local -Z mesh (rotation applied) acceptable for the chain animation, or should the objects be authored with +Z along the ribbon and a 180 degree rotation baked in the loader?
- Ribbon width is along X and thickness along Y (flat in the XZ plane). Should it face the viewer in first person instead (flat in YZ)?
- Is the 16-tri tail on segment 10 acceptable at blockout level?

