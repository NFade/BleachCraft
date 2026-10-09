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

