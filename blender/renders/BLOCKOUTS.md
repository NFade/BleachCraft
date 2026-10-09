# BLOCKOUTS (Phase 3 step 1)

Blender 5.2.2 blockouts, Workbench renders, flat Principled colours from ART_BIBLE v2. Generated per model by `blender/scripts/blockout_<model>.py`.

Run headless (the Blender MCP socket was not available, no GUI window was opened): `blender.exe --background --factory-startup --python blender/scripts/blockout_<model>.py`, then `python blender/scripts/bb_compose.py <model>`. Binary: `D:\SteamLibrary\steamapps\common\Blender\blender.exe` (5.2.2 LTS).

## rukia_sealed

Scene: `blender/scenes/rukia_sealed.blend`. Render: `blender/renders/rukia_sealed_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `rukia_sealed_drawn` | 670 | (-0.031, -0.036, 0.0) | (0.031, 0.0864, 0.9785) | (0.062, 0.1224, 0.9785) | (0.0, 0.0, 0.0) |
| `rukia_sealed_sheathed` | 900 | (-0.031, -0.036, 0.0) | (0.031, 0.1131, 1.0024) | (0.062, 0.1491, 1.0024) | (0.0, 0.0, 0.0) |

Empties: `grip_hand` (0, 0, 0.19); `tip` (0, 0.0864, 0.9785)

Notes:
- Both objects are at the world origin (kashira) and overlap by design; in the .blend `rukia_sealed_sheathed` is hidden in the viewport (eye icon).
- Tsuba (Gate B B4): 62 (X) x 72 (Y) x 6 mm, concave corners (r 10 mm), two curved sukashi slits (20 x 6 mm, bow 3 mm) at y = +-25 mm. The 32 x 10 mm blade hole is not cut (it would be an enclosed pocket between fuchi top and habaki).
- Hilt ovals (Gate B B2): kashira 22 x 30 mm, tsuka 24 x 30 mm, fuchi 26 x 32 mm (X x Y).
- Blade: 6-vertex shinogi section (edge, ridge at 55 percent, spine), kissaki 70 mm, tapering 32 to 22 mm and 10 to 5 mm; centreline is parabolic, sori 20 mm chord deviation = 80 mm tip offset (Gate B B1).
- Saya: koiguchi collar 14 mm, kojiri cap 18 mm, both in the fittings colour; body in the violet-black lacquer; sori 23 mm chord deviation (92 mm tip offset).

Deviations from the art bible:
- Sori follows the chord reading (Gate B B1): bb.SORI_K = 4, tip offset 80 mm (blade) and 92 mm (saya).
- Saya cross-section is 26 mm (X) x 40 mm (Y), not 40 (X) x 26 (Y): the blade is 32 mm wide along Y and 10 mm thick along X, so a 26 mm Y extent cannot contain it.
- Tsuka runs z 0.014 to 0.236 (between kashira and fuchi) instead of z 0 to 0.250, so the solids do not overlap (no hidden interior faces); visible result and total length are unchanged.
- Habaki is a plain 32 x 10 x 28 mm box and the blade starts on its top face (z 0.284) so both are flush, no coincident faces.
- Wrap diamonds (hishimaki) and the cream underlay colour are not modelled (texture-only detail); the tsuka carries the red wrap colour.

Gate B edits applied (see design/GATE_B.md).

## rukia_shikai

Scene: `blender/scenes/rukia_shikai.blend`. Render: `blender/renders/rukia_shikai_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `rukia_shikai_blade` | 938 | (-0.044, -0.044, -0.01) | (0.044, 0.044, 1.0355) | (0.088, 0.088, 1.0455) | (0.0, 0.0, 0.0) |
| `rukia_shikai_ribbon_01` | 12 | (-0.02, -0.001, -0.26) | (0.02, 0.001, -0.01) | (0.04, 0.002, 0.25) | (0.0, 0.0, -0.01) |
| `rukia_shikai_ribbon_02` | 12 | (-0.0194, -0.001, -0.51) | (0.0194, 0.001, -0.26) | (0.0388, 0.002, 0.25) | (0.0, 0.0, -0.26) |
| `rukia_shikai_ribbon_03` | 12 | (-0.0188, -0.001, -0.76) | (0.0188, 0.001, -0.51) | (0.0376, 0.002, 0.25) | (0.0, 0.0, -0.51) |
| `rukia_shikai_ribbon_04` | 12 | (-0.0182, -0.001, -1.01) | (0.0182, 0.001, -0.76) | (0.0364, 0.002, 0.25) | (0.0, 0.0, -0.76) |
| `rukia_shikai_ribbon_05` | 12 | (-0.0176, -0.001, -1.26) | (0.0176, 0.001, -1.01) | (0.0352, 0.002, 0.25) | (0.0, 0.0, -1.01) |
| `rukia_shikai_ribbon_06` | 12 | (-0.017, -0.001, -1.51) | (0.017, 0.001, -1.26) | (0.034, 0.002, 0.25) | (0.0, 0.0, -1.26) |
| `rukia_shikai_ribbon_07` | 12 | (-0.0164, -0.001, -1.76) | (0.0164, 0.001, -1.51) | (0.0328, 0.002, 0.25) | (0.0, 0.0, -1.51) |
| `rukia_shikai_ribbon_08` | 12 | (-0.0158, -0.001, -2.01) | (0.0158, 0.001, -1.76) | (0.0316, 0.002, 0.25) | (0.0, 0.0, -1.76) |
| `rukia_shikai_ribbon_09` | 12 | (-0.0152, -0.001, -2.26) | (0.0152, 0.001, -2.01) | (0.0304, 0.002, 0.25) | (0.0, 0.0, -2.01) |
| `rukia_shikai_ribbon_10` | 12 | (-0.0146, -0.001, -2.51) | (0.0146, 0.001, -2.26) | (0.0292, 0.002, 0.25) | (0.0, 0.0, -2.26) |

Empties: `grip_hand` (0, 0, 0.19); `ribbon_root` (0, 0, -0.01); `tip` (0, 0.038, 1.0355)

Notes:
- Blade 0.752 m from the habaki top (0.78 m from the habaki start): width 28 to 20 mm, thickness 9 to 4 mm, kissaki 70 mm, sori 8 mm chord deviation (tip offset 32 mm after Gate B B1); overall 1.036 m. Habaki is the BASE 32 x 10 mm box.
- Snowflake tsuba (Gate B B5): 88 mm round plate (48-segment outline), rim 7 mm, hub 36 mm, six spokes widening 7 to 12 mm, six windows 19 mm deep (boolean cut).
- Kashira: plain oval cap, 22 x 30 mm (Gate B B2). Gate B B8: a 14 x 8 x 10 mm knot block (`rk_ribbon` material) is joined into the blade mesh below the kashira; `ribbon_root` (0, 0, -0.010) is its bottom face.
- Ribbon (Gate B B6, B7): 10 hinged segments, exactly 0.25 m long, pitch 0.25 m, no overlap, 40 to 28 mm wide, 2 mm thick, identical trapezoid prisms of 12 tris (the swallow tail of segment 10 is a texture alpha cut-out). Origins = hinge points at z = -0.010 - 0.25 (n-1).
- Objects are not parented; ribbon objects keep location = hinge, rotation 0, scale 1.

Deviations from the art bible:
- Ribbon mesh runs along local -Z (the ribbon trails behind the pommel at z < 0, and rotation must stay 0 for export), so the 'local +Z points along the ribbon' wording of the bible cannot hold literally (Gate B: kept, code bends about local X).
- Sori 8 mm is the chord deviation; tip offset = 4 x 8 = 32 mm (Gate B B1, bb.SORI_K = 4).
- Tsuka runs z 0.014 to 0.236 between kashira and fuchi (no overlapping solids); wrap-diamond gaps `#C9D6EA` are texture-only and not modelled; emissive zones are not set up in the blockout.
- Tsuba blade hole not cut (enclosed pocket under the habaki).

Gate B edits applied (see design/GATE_B.md).

## byakuya_sealed

Scene: `blender/scenes/byakuya_sealed.blend`. Render: `blender/renders/byakuya_sealed_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `byakuya_sealed_drawn` | 630 | (-0.028, -0.046, 0.0) | (0.028, 0.046, 0.9796) | (0.056, 0.092, 0.9796) | (0.0, 0.0, 0.0) |
| `byakuya_sealed_sheathed` | 860 | (-0.028, -0.046, 0.0) | (0.028, 0.046, 1.0006) | (0.056, 0.092, 1.0006) | (0.0, 0.0, 0.0) |

Empties: `grip_hand` (0, 0, 0.19); `tip` (0, 0.0266, 0.9796)

Notes:
- Tsuba: 56 (X) x 92 (Y) x 7 mm window frame, corner radius 5 mm, frame bar 8 mm, centre bar (along Y) 10 mm, transverse bar 8 mm, hub plate 40 (Y) x 18 (X) mm; four stepped L-shaped windows (boolean cut). Because the tsuba is 7 mm thick it occupies z 0.250 to 0.257, so habaki starts at 0.257, the blade at 0.285 and the saya at 0.257 (everything above the guard is shifted +1 mm against the BASE table; total drawn length 0.980 m).
- Kashira: cream cylinder (oval 30 x 22 mm, 14 mm high, small chamfer ring). Fuchi, tsuba, habaki and the saya fittings share the bronze colour; saya is dark violet-black `#2E2840`, no white variant.
- Both objects are at the origin and overlap by design; `byakuya_sealed_sheathed` is hidden in the viewport in the .blend.
- `byakuya_sealed_drawn` and `_sheathed` hilts are separate (unlinked) mesh data, so `byakuya_shikai_hilt` and `byakuya_bankai_hilt_ground` reuse the same builder `bb.bk_hilt()`.

Deviations from the art bible:
- Sori: tip deflection from the base axis, 20 mm (see rukia_sealed note, `bb.SORI_K`).
- Saya cross-section 26 (X) x 40 (Y) mm instead of 40 x 26 (cannot contain the 32 mm wide blade otherwise).
- Tsuka runs z 0.014 to 0.236 between kashira and fuchi (no overlapping solids). Wrap diamonds and cream windows `#E8DDB5` are texture-only and not modelled.
- Tsuba is 7 mm thick (bible 1.3) vs 6 mm in the BASE table; the parts above it start 1 mm higher (see notes), so the drawn blade is 1 mm shorter in nagasa to keep the tip at z 0.980.
- Blade hole not cut in the tsuba (would be an enclosed pocket).

Open questions for Gate B:
- Tsuba thickness 7 mm (section 1.3) vs BASE 6 mm: keep the 1 mm upward shift of habaki, blade and saya, or move the tsuba to z 0.249 to 0.256 so the blade geometry matches the BASE table?
- Sori reading (20 mm tip offset vs 80 mm chord deviation), same as rukia_sealed.
- Tsuba windows are modelled as hard-edged L shapes; is the 4 mm hub step readable enough or should the frame get a bevel in the detail pass?

## byakuya_shikai

Scene: `blender/scenes/byakuya_shikai.blend`. Render: `blender/renders/byakuya_shikai_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `byakuya_shikai_hilt` | 560 | (-0.028, -0.046, 0.0) | (0.028, 0.046, 0.315) | (0.056, 0.092, 0.315) | (0.0, 0.0, 0.0) |
| `byakuya_shikai_petal` | 16 | (0.198, -0.0122, 0.0) | (0.202, 0.0138, 0.12) | (0.004, 0.026, 0.12) | (0.2, 0.0, 0.0) |
| `byakuya_shikai_shard` | 8 | (0.2985, -0.011, 0.0303) | (0.3015, 0.009, 0.0833) | (0.003, 0.02, 0.053) | (0.3, 0.0, 0.05) |

Empties: `grip_hand` (0, 0, 0.19); `tang_tip` (0, 0, 0.315)

Notes:
- Hilt is built with the same `bb.bk_hilt()` as byakuya_sealed (kashira, tsuka, fuchi, window-frame tsuba, habaki), so dimensions and palette are identical; the blade is replaced by a 30 mm steel tang stub (5 x 10 mm, `#8E96A3`) above the habaki (z 0.285 to 0.315).
- Petal: leaf blade 120 mm long, 26 mm wide at 40 percent, 4 mm thick flattened diamond section, 6 mm tang, 5 mm sori toward +Y, 16 tris (limit 20). Origin at the tail end (z 0), +Z along the blade, edge -Y; stored at (0.20, 0, 0) in the scene so it does not overlap the hilt.
- Shard: 3-sided prism 50 x 20 x 3 mm, 8 tris (limit 12), origin at the centroid, stored at (0.30, 0, 0.05).
- Petal and shard are shown at their real size; the render rows use a 1 cm ruler for them.

Deviations from the art bible:
- `tang_tip` empty at z 0.315 instead of 0.31: the 7 mm tsuba pushes habaki to z 0.257 to 0.285 (same shift as byakuya_sealed), so a 30 mm stub ends at 0.315.
- Petal is 16 tris as a three-station spindle (tail apex, tail ring, widest ring at 40 percent, tip apex); the tip taper is a straight pyramid, no extra ring.
- Shard is 8 tris (two triangles plus three quads), the bible's 6 would need an open mesh.
- Tsuka z 0.014 to 0.236 (no overlapping solids), wrap diamonds not modelled; emissive petal zones not set up.

Open questions for Gate B:
- Petal spindle with only one widest ring reads as a diamond; accept 16 tris or spend the remaining 4 tris on a second taper ring for a leaf-like belly?
- Should the shard also carry a tiny tang so it matches the petal outline (as a broken piece of one)?

## rukia_bankai

Scene: `blender/scenes/rukia_bankai.blend`. Render: `blender/renders/rukia_bankai_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `rukia_bankai_sword` | 586 | (-0.0163, -0.042, 0.0) | (0.0163, 0.042, 1.0659) | (0.0326, 0.084, 1.0659) | (0.0, 0.0, 0.0) |
| `rukia_bankai_ribbon_seg` | 12 | (0.265, -0.001, 0.05) | (0.335, 0.001, 0.4) | (0.07, 0.002, 0.35) | (0.3, 0.0, 0.4) |
| `rukia_bankai_ribbon_tip` | 12 | (0.415, -0.001, 0.05) | (0.485, 0.001, 0.4) | (0.07, 0.002, 0.35) | (0.45, 0.0, 0.4) |
| `rukia_bankai_crystal_a` | 46 | (0.775, -0.0217, 0.0) | (0.825, 0.0217, 0.15) | (0.05, 0.0434, 0.15) | (0.8, 0.0, 0.0) |
| `rukia_bankai_crystal_b` | 46 | (0.95, -0.0433, 0.0) | (1.05, 0.0433, 0.3) | (0.1, 0.0866, 0.3) | (1.0, 0.0, 0.0) |
| `rukia_bankai_crystal_c` | 46 | (1.2, -0.0866, 0.0) | (1.4, 0.1027, 0.6) | (0.2, 0.1893, 0.6) | (1.3, 0.0, 0.0) |
| `rukia_bankai_crystal_d` | 34 | (1.6, -0.1732, 0.0) | (2.0, 0.1732, 1.2) | (0.4, 0.3464, 1.2) | (1.8, 0.0, 0.0) |
| `rukia_bankai_shard_a` | 8 | (2.1985, -0.0162, 0.0167) | (2.2015, 0.0138, 0.1367) | (0.003, 0.03, 0.12) | (2.2, 0.0, 0.06) |
| `rukia_bankai_shard_b` | 8 | (2.3485, -0.0135, 0.01) | (2.3515, 0.0115, 0.09) | (0.003, 0.025, 0.08) | (2.35, 0.0, 0.04) |
| `rukia_bankai_ice_shell` | 96 | (2.5252, -0.4845, 0.0) | (3.4875, 0.4878, 2.0) | (0.9623, 0.9723, 2.0) | (3.0, 0.0, 0.0) |

Empties: `grip_hand` (0, 0, 0.22); `tip` (0, 0.014, 1.0659); `ribbon_root` (0, 0.12, 0.9)

Notes:
- Required pieces only: sword, ribbon segment/tip, crystals a-d, shards a-b, ice shell. Layout in the scene (objects keep their own origins): sword at the origin, ribbon seg/tip at x 0.30 / 0.45 (hinge z 0.40), crystals at x 0.80 / 1.00 / 1.30 / 1.80, shards at x 2.20 / 2.35, ice shell at x 3.00.
- Sword: tsuka 0.28 m (z 0 to 0.28), oblong stadium bar tsuba 84 (Y) x 22 (X) x 6 mm (rim 4 mm, centre bridge 44 mm, two stadium windows about 16 x 14 mm), habaki z 0.286 to 0.314, blade 0.752 m to z 1.066 (0.78 m from the habaki start), 12 mm spine, edge bevel `#CFEFFF`, core `#7FB8DF`, spine `#2E6FA8` (face-material split so the ice layers read).
- Ribbon segment 0.35 x 0.07 m, 2 mm thick, 12 tris; tip piece tapers from 70 mm to 8 mm over 0.35 m (12 tris). Hinge at the origin, mesh runs along -Z (same convention as rukia_shikai ribbons).
- Crystals: 6-sided pyramid-tipped prisms, base radius = height/6, 46 tris (a, b, c) and 34 tris (d); crystal_c leans 0.10 x height toward +Y (the bent variant); tips use `#D9E8F5`.
- Shards: 3 mm triangular prisms, 120 x 30 mm and 80 x 25 mm, 8 tris each. Ice shell: 10-sided, 3 body rings + 2 end rings with seeded irregular radii, 96 tris, X/Y about 1.0 m, Z 2.0 m, translucent preview colour.
- `ribbon_root` is a player-space point (feet at the player origin, back = +Y, obi height about 0.9 m), kept at (0, 0.12, 0.90) because the player/costume is not modelled yet.

Deviations from the art bible:
- Costume set (collar, both pauldrons, crown, chest flower) NOT built: art bible E10 and Gate A decision 6 say it is modelled last, after the required pieces pass Gate B. A reference player and the five pieces can be added by a short script once Gate B passes.
- Habaki is 32 x 14 mm (bible: BASE 32 x 10): the 12 mm spine of the bankai blade does not fit inside a 10 mm collar.
- Sori 8 mm is the tip deflection from the base axis (see rukia_sealed note on `bb.SORI_K`).
- Tsuba blade hole not cut (enclosed pocket); wrap diamonds `#7FA5C8` are texture-only; transparency is only previewed on the shell, everything else is flat colour.
- Ribbon segment/tip run along -Z with rotation 0 (same open question as rukia_shikai); no 10 mm overlap added (the bankai spec gives none).
- Ice shell is 96 tris for 3 body rings + 2 end rings (bible says 3 rings); facet irregularity is a fixed random seed.

Open questions for Gate B:
- Costume pieces are deferred as the bible says; confirm they should be blocked out right after Gate B (and which player model dimensions to use).
- Ribbon local axis (-Z, as built) vs +Z for the chain code, see rukia_shikai.
- Is the 3-way ice material split on the blade (bevel / core / deep spine) the right basis for the later UV layout, or should the blade stay a single ice colour with the layering painted in?
- ribbon_root: its position relative to the sword origin is meaningless until the player rig is chosen; keep it as a player-space point?

## byakuya_bankai

Scene: `blender/scenes/byakuya_bankai.blend`. Render: `blender/renders/byakuya_bankai_blockout.png`.

| object | tris (evaluated, triangulated) | bbox min (m) | bbox max (m) | size (m) | origin (m) |
|---|---|---|---|---|---|
| `byakuya_bankai_blade` | 142 | (-0.07, -0.275, 0.0) | (0.07, 0.8919, 7.9843) | (0.14, 1.1669, 7.9843) | (0.0, 0.0, 0.0) |
| `byakuya_bankai_blade_lod` | 22 | (1.43, -0.275, 0.0) | (1.57, 0.8307, 7.9843) | (0.14, 1.1057, 7.9843) | (1.5, 0.0, 0.0) |
| `byakuya_bankai_hilt_ground` | 568 | (2.972, -0.046, 0.0) | (3.028, 0.046, 0.52) | (0.056, 0.092, 0.52) | (3.0, 0.0, 0.0) |
| `byakuya_bankai_ripple` | 32 | (4.0, -1.0, 0.02) | (6.0, 1.0, 0.02) | (2.0, 2.0, 0.0) | (5.0, 0.0, 0.0) |
| `hakuteiken_blade_body` | 20 | (7.97, -0.15, 0.0) | (8.03, 0.15, 1.0) | (0.06, 0.3, 1.0) | (8.0, 0.0, 0.0) |
| `hakuteiken_blade_tip` | 34 | (7.97, -0.15, 1.0) | (8.03, 0.15, 1.45) | (0.06, 0.3, 0.45) | (8.0, 0.0, 1.0) |
| `hakuteiken_wing_l` | 45 | (14.2096, -0.8, -0.2148) | (20.0, -0.0013, 4.4641) | (5.7904, 0.7987, 4.6789) | (14.0, 0.0, 1.0) |
| `hakuteiken_wing_r` | 45 | (8.0, -0.8, -0.2148) | (13.7904, -0.0013, 4.4641) | (5.7904, 0.7987, 4.6789) | (14.0, 0.0, 1.0) |
| `hakuteiken_halo` | 48 | (12.9, 0.3, 3.1) | (15.1, 0.3, 5.3) | (2.2, 0.0, 2.2) | (14.0, 0.3, 4.2) |

Empties: none

Notes:
- Required pieces only; `byakuya_bankai_senkei_sword` is not built (optional, only after Gate C passes). Petal/shard are reused from byakuya_shikai.
- Scene layout (each object keeps its own origin): giant blade at the origin, LOD at x 1.5, ground hilt at x 3, ripple at x 5, Hakuteiken blade body at x 8 (z 0 to 1) with the tip sitting on it at z 1.0, wings at x 14 (shoulder root z 1.0, L extends +X, R extends -X), halo centre at (14, 0.3, 4.2).
- Giant blade: 8.0 m, 0.55 m wide, spine thickness 0.14 m tapering to 0.06 m, sori control points (0.5, 0), (4, 0.10), (6, 0.35), (8, 0.75) smoothed with a cubic Hermite spline (tip 0.75 m toward +Y), kissaki last 0.9 m, 12 rings x 6 vertices + tip. Face materials: soil band z 0 to 0.5, light edge bevel / base flats / shade spine, glow zone z >= 6.8 (`#F2E9FF`).
- LOD: 3 rings x 4 vertices (z 0, 4.5, 7.1) + tip, 22 tris, same origin and tip as the full blade.
- Ground hilt: the sealed-sword hilt (shared builder) mirrored to point up over a 0.263 m habaki + blade stub, total 0.52 m, origin at the bottom of the stub (a flat cut end).
- Ripple: 16-segment flat ring r 0.88 to 1.00 m at z 0.02 (32 tris). Hakuteiken blade body: 2 shinogi rings (20 tris incl. caps), tip: 0.45 m kissaki (34 tris). Halo: 24-segment annulus in the XZ plane (48 tris).
- Wings: 9 feather sheets per wing (3 quads + tip triangle fan, single sided, normals +Y), longest feather 6.93 m at 30 degrees above X (horizontal reach 6.0 m), lengths decreasing 7.5 percent per feather, tips curve forward (-Y) up to 0.8 m.
- No empties are specified for this model; none added.

Deviations from the art bible:
- Senkei sword deferred (optional per the bible and Gate A decision 7).
- Giant blade bbox top is z 7.984 rather than 8.000: the kissaki tip sits 0.08 m toward the spine inside the tilted last section; the tip height can be pushed to exactly 8.0 in the detail pass.
- Wing shape is an interpretation: the bible gives span 6 m, 9 lobes, 150 tris and 0.8 m forward curvature but no feather layout; the fan angles (30 down to -26 degrees) and lengths are guesses to be checked against the Hakuteiken frames at Gate B.
- Giant blade has 142 tris (target 170, limit 300); the ring z-stations are chosen so the spline control points get rings.
- Hakuteiken blade tip uses 3 rings (34 tris) instead of the full 40; body 20 tris.
- Ground hilt blade stub is a straight, flat-cut 32 x 10 mm blade section (the buried end).
- Palette: the giant blade face-material split (light edge bevel, base flats, shade spine) is a blockout stand-in for the painted texture.

Open questions for Gate B:
- Wing layout: confirm fan direction (longest feather up-and-out at 30 degrees) and that +Y is 'back' with the 0.8 m curve toward -Y (forward).
- Halo position: bible says centre 0.35 m above the head and 0.3 m behind the back; placed here at (14, 0.3, 4.2) over a wing root at z 1.0, which is only a layout convenience. Should the halo origin be defined relative to the wing root instead?
- Giant-blade tilt: with sori 0.75 m toward +Y the tip leans over the buried axis; the in-game yaw rule (spine toward the player) is unchanged, but should the buried 0.5 m also be vertical (as built)?
- Ripple is a single face (normal +Z); is double-sided rendering assumed in code?

