# Gin and Uryu models (Blender), branch `gin-uryu`

Source scene: `blender/gin_uryu_models.blend` (collection `export`, material `gin_shinso_atlas` and `uryu_atlas`, empties included). Scripts: `blender/scripts/gu_build.py` (geometry, UV), `gu_paint.py` (textures, system python), `gu_layout.py` (atlas rects and palette, shared), `gu_render.py` + `gu_sheet.py` (previews). Previews: `blender/renders/gin_uryu/` (`*_overview.png`, `*_tsuba.png`, `uryu_*.png`, `*_atlas.png` = 4x nearest preview of the textures).

Conventions copied from the existing models (ART_BIBLE 0.5, `blender/scripts/export_obj.py`): 1 unit = 1 m, Blender Z up, object origin (0, 0, 0) with rotation 0 and scale 1 on every object, OBJ exported with `forward_axis=NEGATIVE_Z, up_axis=Y` (so OBJ (x, y, z) = Blender (x, z, -y)), `export_uv`, `export_normals`, no materials (like all existing exports there is NO .mtl file; textures are bound by the manifest `diffuse` / `emissive` keys), quads kept, n-gons triangulated, one OBJ per object, shared `<model>_diffuse.png` and `<model>_emissive.png` (128 x 128 RGBA, nearest neighbour; emissive = white RGB, alpha = glow intensity, same as the existing emissive maps). Per model there is `<model>_meta.json` (empties and object origins, Blender space) and `export_check.json` (OBJ re-imported into Blender, all objects `match: true`).

Triangle counts below are after triangulation (an OBJ quad = 2 tris).

## Gin Ichimaru: `blender/export/gin_shinso/`

Files: `gin_shinso.obj`, `gin_shinso_saya.obj`, `gin_shinso_sheathed.obj`, `gin_shinso_ray.obj`, `gin_shinso_diffuse.png`, `gin_shinso_emissive.png`, `gin_shinso_meta.json`, `export_check.json`.

The same model is used for sealed, shikai and bankai (canon: the bankai looks identical).

| object (= OBJ name) | tris | bbox min (m, Blender) | bbox max (m, Blender) | content |
|---|---|---|---|---|
| `gin_shinso` | 414 | (-0.024, -0.0336, 0) | (0.024, 0.0336, 0.656) | drawn wakizashi: kashira, light-blue wrapped tsuka, fuchi, two-swirl tsuba, habaki, blade (no saya) |
| `gin_shinso_saya` | 204 | (-0.0115, -0.0195, 0.206) | (0.0115, 0.0394, 0.668) | scabbard alone, plain dark lacquer, open mouth with a dark inner liner (normals toward the axis), so the blade can slide out for a draw animation |
| `gin_shinso_sheathed` | 488 | (-0.024, -0.0336, 0) | (0.024, 0.0394, 0.668) | hilt + tsuba + closed scabbard (blade not modelled), same pattern as `rukia_sealed_sheathed` |
| `gin_shinso_ray` | 10 | (0, -0.02, 0) | (0.0035, 0.02, 1.0) | extended-blade segment, see below |

Origin of `gin_shinso`, `gin_shinso_saya` and `gin_shinso_sheathed`: Blender (0, 0, 0) = centre of the pommel end (kashira), blade axis +Z, cutting edge faces -Y, spine +Y, flat faces look along +-X, sori toward +Y (identical to the sealed Rukia and Byakuya models, so the same hold and draw code applies). The three objects overlap by design (all at the origin).

Dimensions: tsuka z 0.012 to 0.186 (24 x 30 mm oval, 3 mm swell in the middle third, 12 sides, light blue lacing `#8EC3E8` with raised diamonds), kashira z 0 to 0.012, fuchi z 0.186 to 0.200, tsuba plane z 0.200 to 0.206, habaki 30 (Y) x 9 (X) x 30 mm z 0.200 to 0.230, blade 0.45 m from the habaki start (z 0.206) to the tip z 0.656, width 28 mm at the root to 20 mm at the yokote z 0.611, thickness 8 to 4 mm, kissaki 45 mm, 5-vertex shinogi section, sori 6 mm chord deviation = 24 mm tip offset (4 x chord, B1). Overall drawn length 0.656 m. Tsuba: two slim 6 mm metal swirls (arm section 6 x 6 mm), each one starts inside the habaki at a blade edge, bulges around an open oval 42 x 74 mm and hooks inward at the end, the second arm is the first rotated 180 degrees about Z, which reads as an "S" from above (see `gin_shinso_tsuba.png`). Scabbard: 8-sided oval 23 x 39 mm, silver koiguchi collar and kojiri, mouth at z 0.206, end at z 0.668, sori 27 mm tip offset (3 mm more than the blade so the blade lies inside).

Empties (in `gin_shinso_meta.json`): `grip_hand` (0, 0, 0.12), `tip` (0, 0.024, 0.656), `saya_mouth` (0, 0, 0.206). `blade_axis` is given the same way as for the sealed models (origin = saya mouth, draw direction -Z, `sori_arc` centre (0, 4.2188, 0.206), radius 4.2188 m, rotate about +X for the exact draw).

`gin_shinso_ray`: thin white glow blade segment, 1 m long (z 0 to 1), 0.04 m wide (Y, -0.02 to 0.02), flat (section is a flat triangle 3.5 mm thick: back plane at x = 0, ridge at x = +0.0035), pointed tip over the last 0.06 m (apex at (0.0012, 0, 1.0)), origin at its base (0, 0, 0), +Z along the length, 10 tris (limit 12), fully emissive (diffuse white/pale blue, emissive alpha 255 over its whole strip). Safe to stretch non-uniformly along Z (the UV strip is `u` along the length, a plain gradient-free stripe); the tip is NOT separate, so a long stretch also lengthens the tip (add a separate tip later if it looks wrong at 50 m).

Part names for the manifest (suggested): `hand: ["gin_shinso"]`, `other: ["gin_shinso_sheathed"]`, `saya: "gin_shinso_saya"`, `blade: "gin_shinso"`, dynamic / effect: `gin_shinso_ray`. All the same for `sealed`, `base`, `shikai`, `bankai`; only the ray appears while the ability is active. Suggested manifest object entries: `{"model": "gin_shinso", "diffuse": "gin_shinso_diffuse", "emissive": "gin_shinso_emissive"}`; the ray additionally `"translucent": false` (glow comes from the emissive map). `emissive_tint` suggestion: `FFFFFFFF`.

## Uryu Ishida: `blender/export/uryu/`

Files: `uryu_cross.obj`, `uryu_bow.obj`, `uryu_bow_string.obj`, `uryu_arrow.obj`, `uryu_seele.obj`, `uryu_diffuse.png`, `uryu_emissive.png`, `uryu_meta.json`, `export_check.json`.

| object (= OBJ name) | tris | limit | bbox min (m, Blender) | bbox max (m, Blender) |
|---|---|---|---|---|
| `uryu_cross` | 36 | 40 | (-0.008, -0.04, -0.06) | (0.008, 0.04, 0.06) |
| `uryu_bow` | 96 | 120 | (-0.018, -0.01, -0.55) | (0.018, 0.105, 0.55) |
| `uryu_bow_string` | 4 | 4 | (-0.0025, 0.1025, -0.55) | (0.0025, 0.1075, 0.55) |
| `uryu_arrow` | 30 | 40 | (-0.04, -0.04, 0) | (0.04, 0.04, 0.5) |
| `uryu_seele` | 20 | 24 | (0, -0.028, 0) | (0.0055, 0.028, 0.3) |

- `uryu_cross` (Quincy cross pendant, silver `#B9C3D2` family): 0.12 m tall, 0.08 m wide, flared faceted arms (longer lower arm), a small triangular bail on top. Origin = the crossing point (0, 0, 0), vertical axis +Z, the flat faces look along +-X, bail top at z 0.06 (empty `cross_hang`). The bail is a zero-thickness double-sided ring (own vertices per side). Not emissive.
- `uryu_bow` (Heilig Bogen): 1.1 m tall (z -0.55 to +0.55), origin at the grip centre (0, 0, 0). The long axis is +Z. The limbs bend toward +Y (the string and archer side, tips at y = 0.105); the belly / target side is -Y, so an arrow shot from the bow flies toward -Y of the bow. Grip 23 mm round hexagon (z -0.06 to 0.06), the limbs widen to 36 mm (X) x 17 mm (Y) at z +-0.18 and taper to points at z +-0.55 (6-sided section, flat limbs facing +-Y). Pale blue reishi `#A9D8F5` with a white core line, emissive 55 to 95 percent. No string in this mesh.
- `uryu_bow_string`: the string as a separate 4-tri object (two crossed 5 mm wide quads, one in the XZ plane, one in the YZ plane, each single-sided with its normal facing +Y / +X), straight from tip to tip at y = 0.105, z -0.55 to 0.55, origin (0, 0, 0) like the bow. Bright blue `#7FD0FF`, fully emissive. Because the quads are single-sided, draw it with culling off or accept that it vanishes from some angles (the crossing makes at least one visible from any side but the back). Code can move the middle of it toward the hand for the draw pose (empties `bow_string_top` (0, 0.105, 0.55) and `bow_string_bottom` (0, 0.105, -0.55)).
- `uryu_arrow` (Heilig Pfeil): origin at the tail (0, 0, 0), +Z along the shaft, tip at z 0.5 (empty `arrow_tip`). Total length 0.5 m = 0.02 m tail gap + 0.39 m diamond shaft (8 mm across) + 0.09 m head (26 mm wide barbed shoulder at z 0.41, point at 0.5). Back end: four swept-back double-sided fins (along +X, +Y, -X, -Y, root z 0.04 to 0.13, tips at z 0 and 40 mm out), seen from behind they form a Quincy cross with the arms bent back. Reishi pale blue, emissive 100 percent on the shaft.
- `uryu_seele` (Seele Schneider): 0.3 m, origin at the hilt end (0, 0, 0), +Z along the blade, tip at (0.001, 0, 0.30) (empty `seele_tip`). Triangular 11 mm grip (z 0 to 0.06), flat blade (22 mm wide, 5 mm thick at z 0.09, point at 0.30), a single cross guard 56 mm wide, double-sided kite in the YZ plane at z 0.052 to 0.080. Pale reishi, emissive 45 to 100 percent by tone. For the Sprenger pentagon code rotates it as needed.

Empties in `uryu_meta.json` (Blender coordinates): `bow_grip_hand` (0, 0, 0), `bow_string_top`, `bow_string_bottom`, `arrow_tip`, `seele_tip`, `cross_hang`. They are all in the one JSON because they belong to one atlas; ignore the ones you do not need. The hand pivot of the bow is its origin (the grip centre).

Suggested manifest entries: each object `{"model": "uryu", "diffuse": "uryu_diffuse", "emissive": "uryu_emissive"}`; `uryu_cross` for SEALED, `uryu_bow` (+ `uryu_bow_string` as a dynamic part) for BASE / SHIKAI / BANKAI, `uryu_arrow` and `uryu_seele` as effect meshes. Alpha of the diffuse is 255 everywhere (no translucency was painted; mark `translucent: true` in the manifest only if you want the reishi to fade).

## Textures

128 x 128 per atlas (the two bankai atlases are bigger, the sealed ones 256; these models are tiny), flat pixel art, no noise, 2 px gutter by edge dilation. Tone cells (4 x 4 px, shadow / base / light / highlight) are picked per face from the face normal against a fixed key light, so facets read without baked lighting (cross, tsuba, fittings, arrowhead, seele, ray tip). Strips: tsuka wrap (diamond lacing, light blue), blade (edge line, bevel with wavy temper line, ridge, flat), spine, saya lacquer with one highlight streak, bow ribbon with a white core line, arrow shaft, string, ray stripe. Emissive: only the ray (100 percent), bow (55 to 95), string and arrow shaft (100), reishi cells (45 to 100); Gin's steel, wrap and lacquer and the silver cross are 0.

## Decisions not covered by the docs (simplest consistent option)

- No `.mtl` is written (the existing exports do not write one; `export_materials=False`), so "OBJ + PNG" only.
- Wakizashi proportions: tsuka 0.2 m instead of the katana 0.25 m, same B2 ovals (22/24/26 x 30/30/32 mm) and the same 6 mm tsuba thickness and 4 x chord sori rule. Blade width 28 mm and habaki 30 x 9 mm (instead of 32 x 10) because the blade is narrower.
- `grip_hand` at z 0.12 (about 60 percent of the grip) because there is no tsuka length reference for a 0.2 m hilt.
- Gin tsuba is modelled open (no plate between the swirls); the fuchi top and the habaki fill the centre.
- Gin `gin_shinso_sheathed` has a closed scabbard mouth (the blade is not modelled), `gin_shinso_saya` has an open mouth + liner. The saya sori leaves 1 to 3 mm clearance for the blade; the liner is inset 2 mm from the outer wall.
- Blade bottom (z 0.225) is a cap hidden inside the habaki (z 0.200 to 0.230).
- Bow orientation (limbs toward +Y = string side, arrow flies toward -Y) and the arrow's tail-origin / +Z are chosen to match the sword convention (edge -Y, spine +Y, sori toward +Y); rotate in the display transform.
- Quincy cross has no chain, only a bail; the pendant is flat along +-X.
- `uryu_bow_string` is a separate 4-tri object, as requested ("no string mesh in the bow").

## Not verified

The game was not run (not allowed here); the in-hand pose, display transforms, back-face culling of the single-sided string and fins and the emissive layer in the real renderer are untested. Texel density of the arrow shaft (0.4 m over 80 px) and the bow (1.1 m over 110 px) is about 200 px/m, same as the existing body parts. OBJ re-import check passed for every object (vertex and face counts and bounding boxes equal), normals were checked by winding (all quads emitted CCW from outside, contiguous orientation on every mesh, bow closed with positive volume) and visually in the EEVEE previews, not by an in-engine lighting test.
