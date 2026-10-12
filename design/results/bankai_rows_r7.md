# Bankai blade rows r7 (Byakuya, Senbonzakura Kageyoshi)

Complaint: "blades are very small and very far from each other". Game NOT run (coordinator order); everything about the look is UNVERIFIED.

## Diagnosis
- Old numbers: bladeScale 0.55 on the 8 x 0.55 x 0.14 m mesh = 4.4 tall, 0.30 wide, 0.08 thick; 5 per side, pitch 2.5 (gap 2.4 = 8 blade widths).
- Orientation (checked in the mesh and in `basis()`): local z = width (0.55 m, the sabre curve bends it) maps to ACROSS the row, local x = thickness maps ALONG the row. So the broad faces already look along the lane, like pages of a book, which is what ep52 / ep58 show (curved silhouette seen from the lane, tips bent toward the axis). The brief's premise (width along the row) did not match the code; no flip was made. The blades were simply tiny and far apart.
- The dark flat rectangle with a notch in the screenshot: block-aligned notch, grainy block texture, a separate thin slab under it. It is Minecraft block geometry of the user's world (a built wall or structure), not our mesh: the mod places only ice/snow (TempBlocks, FrostField, light/white frost textures), and no renderer draws a plate of that size (hilt 0.04 x 0.52 m, ripple is a flat disc, LOD is a 13 vertex sliver). UNVERIFIED (not seen in the game; conclusion from the picture and the code).

## New numbers (code defaults in `BladeRowLayout.DEFAULT_*`, hot reload keys in fx_override.json)
| key | old | new | note |
|---|---|---|---|
| bankai.bladeScale | 0.55 | 1.0 | height 8 blocks |
| bankai.bladeWidth (new) | 0.30 | 1.5 blocks | local z scale kz = 1.5 / (0.55 x scale) = 2.73 |
| bankai.bladeThick (new) | 1 (0.08) | 2.0 | 0.28 blocks, so the slab edge reads from the side |
| rows.perSide | 5 | 10 | tiers 200/100/50 -> 10/5/3 per side |
| rows.spacing | 2.5 | 1.0 | gap = 1.0 - 0.28 = 0.72 = 0.48 of the width; Medium/Low pitch x2 (`tierPitch`) |
| rows.halfWidth | 6.5 | 7.0 | blade foot spans 6.25..7.75 from the axis; lane 12.5 wide at the feet |
| rows.startAhead | 5 | 6 | lane from +6 ahead to -3 behind the feet (length 9) |

Curve: mesh tip is 0.826 m inward, stretched with the width: 2.25 blocks, plus the 3 degree lean (0.45): tip at about 4.3 from the axis (lane 8.5 wide at the top). If that reads too much like a hook, lower bankai.bladeWidth or raise rows.halfWidth. Gap/width could be raised to ~0.9 (pitch 1.6) if ep52 (sparser) is preferred over ep58 (overlapping louvers).

## Code
- `core/fx/BladeRowLayout`: constants, `laneDefault`, `tierPitch`, `widthScale`, `gap` (no Minecraft imports).
- `client/fx/RowsFx`: `bladeWidthSetting`, `bladeThickSetting`, State.kx/kz/bladeWidth, makeLayout with the new defaults and tier pitch; crack sprite size follows the width.
- `client/fx/BankaiBladeRenderer.basis`: anisotropic columns (x by sc*kx, y by sc, z by sc*kz) plus a normal matrix `NM`; frustum box radius uses max(kz, kx); scar decal size follows the width; shiver now moves along the row (the face normal) so it is visible on slabs. Tip position, glow, lean, rise/sink, cutY (local y, unaffected), LOD and the static buffer use the same basis, so they follow automatically.
- `client/fx/FxMesh.writeBlade`: takes the normal matrix instead of `invScale`, normalises per vertex.
- Tests (`BladeRowLayoutTest`, +4): 7-9 tall, 1.2-1.8 wide, gap 30-50 percent of the width, even pitch, lane open at feet and tips, widthScale, tier pitch.

## UNVERIFIED (nothing rendered)
Look of the slabs against the references, tip glow size on the wide blade, whether the 2.25 block curve is too strong, dissolve/cutY and rise on the stretched mesh, the shiver direction, the dark rectangle being a world structure, performance (20 blades, far fewer vertices than before at N=200 legacy). Suggested first check in game: `run/fx_override.json` with bankai.bladeWidth / rows.spacing / rows.halfWidth, then `-Phold=console` with `rel byakuya 3000 name` (see Phase6Release.consoleCommand), views back/front/top.
