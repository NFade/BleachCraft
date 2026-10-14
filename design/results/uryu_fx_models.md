# Uryu VFX meshes `uryu_fx` (Blender), step U-FX 1, branch `gin-uryu`

Contract: `design/URYU_VFX_STORYBOARD.md` 4.1 (list, budgets), 2.10/2.11 (wings), palette 1.1. Source scene: `blender/uryu_fx_models.blend` (collection `export`, material `uryu_fx_atlas`, empties included). Scripts (all deterministic, re-runnable):

| script | runs in | what |
|---|---|---|
| `blender/scripts/uq_layout.py` | pure python | atlas rects, palette, wing plate polygons, bow rings, budgets (shared) |
| `blender/scripts/uq_paint.py` | system python (numpy + Pillow) | `python -I blender/scripts/uq_paint.py blender/export/uryu_fx blender/renders/uryu_fx` writes `uryu_fx_diffuse.png`, `uryu_fx_emissive.png` (+ 3x preview) |
| `blender/scripts/uq_build.py` | Blender (exec) | builds the meshes, empties, material; `export_all(ROOT)` writes OBJ + `uryu_fx_meta.json` + `export_check.json` |
| `blender/scripts/uq_render.py` | Blender + system python | `render_jobs(ROOT)` (EEVEE emissive look, compositor bloom, block mannequin); `python -I uq_render.py sheets <ROOT> <TMP>` composes the sheets |

Run order: paint, `exec(uq_build.py, {"ROOT": ...})`, then in a NEW call `uq_build.export_all(ROOT)` (a same-call export after the exec hit a stale view layer once), `python -I tools/place_model_assets.py uryu_fx`.

Conventions as `gin_uryu_models.md`: 1 unit = 1 m, Blender Z up, every object origin (0,0,0) with rotation 0 / scale 1, OBJ `forward -Z, up Y` (OBJ (x,y,z) = Blender (x,z,-y); so Blender **+Y is BEHIND** the player, -Y forward), quads kept, no `.mtl`, one OBJ per object, shared 256 x 256 RGBA atlas (nearest, 2 px gutters; emissive = white RGB, alpha = glow intensity). Files in `blender/export/uryu_fx/`, copied by the tool to `mod/src/main/resources/assets/reiatsu_test/models/obj/uryu_fx/` and `textures/item/uryu_fx_{diffuse,emissive}.png`. The old `uryu/uryu_bow.obj` and `uryu_arrow.obj` are untouched; "replacing" them means pointing the manifest at `model: "uryu_fx"`.

## Triangles, bounding boxes (Blender space, m)

| object | tris | budget (limit) | bbox min | bbox max |
|---|---|---|---|---|
| `uryu_wing_l` | 104 | 120 (150) | (0.051, 0.049, -0.329) | (1.705, 0.532, 1.173) |
| `uryu_wing_r` | 104 | 120 (150) | (-1.705, 0.049, -0.329) | (-0.051, 0.532, 1.173) |
| `uryu_flechette` | 8 | 8 | (-0.06, 0, -0.2) | (0.06, 0, 0.2) |
| `uryu_needle` | 12 | 12 | (-0.0375, -0.0325, 0) | (0.0375, 0.0325, 1.2) |
| `uryu_star_head` | 24 | 24 | (-0.8, -0.25, -0.8) | (0.8, 0.25, 0.8) |
| `uryu_bracer` | 24 | 24 | (-0.135, -0.135, -0.1) | (0.135, 0.135, 0.1) |
| `uryu_boot` | 36 | 36 | (-0.150, -0.283, 0.015) | (0.150, 0.150, 0.45) |
| `uryu_bow` | 150 | 160 (180) | (-0.052, -0.112, -0.55) | (0.052, 0.105, 0.55) |
| `uryu_bow_flex_1/2/3` | 150 each | 160 (180) | z +-0.544 / 0.536 / 0.528, y max 0.131 / 0.156 / 0.180 | |
| `uryu_arrow` | 40 | 40 | (-0.04, -0.035, 0.02) | (0.04, 0.035, 0.5) |
| `uryu_seele_long` | 16 | 16 | (-0.0025, -0.03, 0) | (0.0025, 0.03, 0.9) |
| `uryu_pentagon_edge` | 4 | 4 | (-0.06, -0.07, 0) | (1.0, 0.07, 0.003) |

All within budget. Sum of the base set (without the optional flex variants) = 104 + 104 + 8 + 12 + 24 + 24 + 36 + 150 + 40 + 16 + 4 = **522** (limit 900). The three flex bows (3 x 150) are alternatives to `uryu_bow`, never drawn together; counted all together the set would be 972, over 900 (decide in U-FX 2 whether to keep them). `export_check.json`: all 14 objects re-imported, vertex and face counts and bounding boxes equal (error below 1e-6 m), `match: true`; wings: 0 flipped faces, closed, manifold (checked with `verify_model.normals_check`).

## Wings (`uryu_wing_l`, `uryu_wing_r`)

Origin = the shoulder root, `_l` extends +X, `_r` is the exact mirror (own winding). Seven SHINGLED slabs (0.025 m thick, 12 tris each; every plate lies in its own layer 0.03 m apart in Y so the fan may overlap near the root without z fighting): main spar (1.9 m, 0.17 m wide, 30 deg), parallel plate (1.5 m, 26 deg), step slab on the back of that plate end (the notch step), three lower plates fanned 12.6, 6.5, -5.1 deg (1.24, 1.07, 0.89 m, 0.115/0.105/0.09 m wide, tapering), a root hook blade; every plate end cut at 30 deg; 2 flat pyramid spikes (6 tris each) continue two plate ends; 2 loose crystal chips (tetrahedra, 4 tris each). Emissive 85 percent, diffuse alpha 200 with 255 for rim lines and stardust specks (see textures). Deviations from 4.1: spar rises 30 deg (not 38) and is 1.9 m (not 2.1) so that its acute tip lies exactly on `WING_TIPS[0]`; the lowest plate tip lies exactly on `WING_TIPS[2]`.

Empties (object space, = scene space; in `uryu_fx_meta.json` under `empties`, plus a `wing` block with the check):

| empty | position (x, y behind, z up) | = `UryuBalance.WING_TIPS` slot |
|---|---|---|
| `wing_l_tip_upper` | (1.70, 0.30, 1.00) | slot 1 (upper left) |
| `wing_l_tip_lower` | (1.25, 0.45, -0.15) | slot 3 (lower left) |
| `wing_r_tip_upper` | (-1.70, 0.30, 1.00) | slot 0 (upper right) |
| `wing_r_tip_lower` | (-1.25, 0.45, -0.15) | slot 2 (lower right) |

Root in the WING_TIPS body frame (sideways, height, backwards) = (0, 1.35, 0.15): `root + (|x|, z, y)` reproduces `{1.7, 2.35, 0.45}` and `{1.25, 1.2, 0.6}` exactly (`WING_TIPS_check` in the meta, all `match true`). Java sideways is "right positive", the mesh `_l` is +X: so mesh `_l` = Java negative sideways (slots 1, 3). The slot-to-side mapping assumes model +X is the player's left (as the Hakuteiken wing convention says, `_l` extends +X); confirm in U-FX 7.

Load: instanced twice (`_l`, `_r`), translate to the shoulder root of the body (feet + (0, 1.35, 0.15 behind the spine)), rotate for the flap about the root (the origin), translucent emissive pass + a 6 percent larger additive pass about the same origin.

## Other meshes

| object | origin, axes | empties | notes and how to load |
|---|---|---|---|
| `uryu_flechette` | centre of the leaf, +Z along the length (tip +0.20), flat in the XZ plane (normal +-Y), double sided | `flechette_tip` (0,0,0.2) | 6 vertex leaf x 2 sides. The comb of barbs is cut into the TAIL by texture alpha 0 (rect `flake`): use a translucent / cutout pass. Instance for Sternenstaub (120) and the state flakes. |
| `uryu_needle` | tail (0,0,0), +Z, 1.2 m | `needle_tip` (0,0,1.2) | 3 crossed double sided planes (0.075 m wide), silhouette (shaft, 4 barbs, two chevron tail fins) is texture alpha (rect `needle`): needs alpha cutout / translucent. NOT the 12 mm diamond tube of 4.1 (impossible in 12 tris). |
| `uryu_star_head` | centre, long axis +Z (flight), tip +0.8 | `star_head_tip` (0,0,0.8), `star_head_neck` (0,0,-0.43) | two crossed octahedra (long axes Z and X, 1.6 long, 0.5 wide, white core to cyan tips) + a 4 sided ring band (diamond section, r 0.22) behind it as the ZK ring (8 tris, not 12, to stay in 24). Scale freely. |
| `uryu_bracer` | centre of the band, axis +Z (OBJ +Y), z -0.1..0.1 | | 12 sided single sided ring, **circumradius 0.14** (spec 0.075 would sit inside the vanilla arm: arm box 0.234 wide). Texture: 3 diagonal bars (period 32 px around 96 px), scroll the U coordinate (`band` rect) at 0.2 UV/s; the bars are texture only (a raised bar mesh does not fit 24 tris). Instance x2 at the forearms. Draw with culling off. |
| `uryu_boot` | sole centre under the leg, shell z 0.02..0.45, toe forward (-Y) | `boot_toe` (0,-0.31,0.03) | 12 sided shell radius 0.14 (bottom) to 0.155 (top) (spec 0.10..0.075, too small for the 0.234 leg), pointed toe pyramid (6 tris), 3 front straps (flat quads, 6 tris). Instance x2 at (+-0.117, 0, 0). |
| `uryu_bow` | grip centre, +Z up, limbs bend to +Y (string side), arrow flies to -Y, 1.1 m | `bow_grip_hand` (0,0,0), `bow_string_top` (0,0.105,0.55), `bow_string_bottom` (0,0.105,-0.55), `arrow_rest` (0,-0.009,0.030) | angular polyline limbs (3 straight segments each), flat bar section 40 x 14 mm (6 sided), two step notches per limb (z 0.20 and 0.385, painted darker), tips as points, two lightning prongs flanking the arrow rest (x +-0.016, reach -0.11, zig-zag outward), four small guard spikes across the grip ends. No string mesh. Same contract as the old bow. |
| `uryu_bow_flex_1..3` | same | strings in `uryu_fx_meta.json` `bow_flex_variants` (`bow_string_top/bottom` per variant) | same bow, each limb rigidly bent a further 3 / 6 / 9 deg toward the string about its grip end. Optional. |
| `uryu_arrow` | tail (0,0,0), +Z, tip z 0.5 (as the old arrow) | `arrow_tip` (0,0,0.5) | 4 sided 8 mm shaft, flared head, 4 flat swept barbs (double sided), three double sided chevron vanes (V darts opening to the tail) at 60 deg. The tail end of the shaft is open (hidden by the vanes). |
| `uryu_seele_long` | hilt end (0,0,0), +Z along the blade, 0.9 m | `seele_long_tip` (0,0.012,0.9), `seele_long_hilt` (0,0,0) | flat blade 24 x 5 mm, tip end cut square but slanted 25 mm, cross guard kite 60 mm at z 0.04..0.085 (double sided). |
| `uryu_pentagon_edge` | bead end (0,0,0), lies flat, length along +X to 1.0, normal +Z | `pentagon_edge_end` (1,0,0) | 0.14 m strip + a bead diamond at the start, single sided. Optional (Q:strip does the job). |

Not built (not in 4.1): a chest emblem mesh and separate Zeichen glyph meshes (the emblem is the `chevrons` glow sprite, the glyphs are `uryu_glow` sprites); the pentagon ring pieces are the optional `uryu_pentagon_edge`.

## Texture (`uryu_fx_diffuse.png`, `uryu_fx_emissive.png`, 256 x 256, rect names in `uq_layout.RECT`)

| rect | x,y,w,h | used by |
|---|---|---|
| `wing` | 0,0,192,136 | planar sheet of both wings (96 px/m, x 0..2.0 m, z -0.38..1.04 m): gradient `#8DE3FF` to `#1F8FE0` (a bit by plate), 1 px rim `#E4FAFF`, inner line, pale streak, stardust 90 specks per 64 x 64 px (value 0.6..1.0, 8 percent lilac `#A99BFF`), 4 glints per big plate; alpha 200 (255 on rim and specks), emissive 85 percent. Outside the plates alpha 0. |
| `flake` | 192,0,64,24 | flechette |
| `star` | 192,24,48,16 | star head (u 0 core white to u 1 cyan) |
| `shaft` | 192,40,64,8 | arrow shaft |
| `blade` | 192,48,64,16 | seele long |
| `pent` | 192,64,64,8 | pentagon edge |
| `band` | 0,136,96,24 | bracer (x around, y along) |
| `boot` | 0,160,96,48 | boot (x around, y along) |
| `bow` | 96,136,120,12 | bow (x along z, y around the 6 sides, white core line) |
| `needle` | 0,208,128,8 | needle planes (alpha silhouette) |
| cells `reishi`, `white`, `rim` | y 244, x 0..44, 4 x 4 px each, 4 tones (shadow..highlight), chosen per face from the normal | wing slab edges (`rim`), spikes, chips, arrow head, barbs, prongs, star ring |

Emissive: wings 85, bow 70 (core 100), arrow, needle, star, blade 100, bands and boots 60 (bars and rims 100). No generator in `tools/` is used or touched (`gen_uryu_textures.py` has its own `uryu_fx_diffuse.png` job per 4.2; this atlas is painted by `uq_paint.py` from the same plate polygons as the geometry, so the two must not both write the file: the one in `textures/item` is this one).

## Previews (`blender/renders/uryu_fx/`)

`wing_back`, `wing_front`, `wing_top`, `wing_side` (with the block mannequin at the 0.9375 model scale), `wing_one_back/front/iso`, `costume` (wings, bracers, boots, flakes; front, back, 3/4), `bow`, `bow_rest_close`, `bow_flex`, `arrow`, `arrow_head`, `arrow_tail`, `star_head`, `needle`, `flechette`, `bracer`, `boot`, `seele_long`, `pentagon_edge`, `turntables` (all meshes, 8 yaw angles), `uryu_fx_atlas` (3x preview, diffuse left and emissive right).

## Not verified

Nothing was run in the game. The wing look was compared by eye with [U vollstandig 4, 5]: two long angular bars with parallel stepped plates, sharp 30 degree ends, stardust: it reads as the canon wing, but the lower plates are shorter and fan more than in the frames (forced by the fixed tip empties). The emissive preview (EEVEE with a compositor bloom) is only an approximation of the mod's additive passes. Single sided parts (bracer, boot, bow guard spikes, pentagon edge) need culling off or are seen from outside only. Whether model +X is the player's left (wing slots) and the `translucent` flags of the manifest are for U-FX 7 to confirm. The bracer and boot radii differ from 4.1 on purpose (see above); scale the instances if the final skin layer needs another fit.
