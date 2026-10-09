# GATE B: Blockout review (all six models)

Reviewer: Gate B (opus). Inputs: the six blockout sheets in `blender/renders/`, `blender/renders/BLOCKOUTS.md`, `blender/scripts/blockout_*.py` and `bb_common.py`, `design/ART_BIBLE.md` v2, `design/GATE_A.md`, `design/ADR.md` sections 1 and 5, and the references (contact sheets plus close-ups of rukia_sealed_1, rukia_shikai_3/_10, byakuya_sealed_10/_11/_12, byakuya_shikai_11, rukia_bankai_2/_3 (hilt crops), byakuya_bankai_2/_3/_4/_5/_10/_11).

Scope rule for the modeller: apply the edits below **in the build scripts** (they are re-runnable), re-run each script and `bb_compose.py`, and regenerate `BLOCKOUTS.md`. No second Gate B round: the orchestrator checks that every edit is applied (numbers in the stats table, new renders), then detailing starts. Where an edit changes a number in the art bible it is listed in section 6 so the orchestrator can fold it into bible v3; until then this document wins over the bible for these points (the ADR still wins over both).

---

## 1. Verdicts

| Model | Verdict | Required edits |
|---|---|---|
| rukia_sealed | **APPROVED WITH EDITS** | B1, B2 (shared) + B4 |
| rukia_shikai | **APPROVED WITH EDITS** | B1, B2 (shared) + B5 to B8 |
| byakuya_sealed | **APPROVED WITH EDITS** | B1, B2 (shared) only |
| byakuya_shikai | **APPROVED WITH EDITS** | B2 (shared) + B9, B10 |
| rukia_bankai | **APPROVED WITH EDITS** | B1, B2 (shared) + B11 |
| byakuya_bankai | **APPROVED WITH EDITS** | B2 (shared, ground hilt) + B12 to B16 |

No model needs a redo. Silhouettes, part breakdown, object names, origins and empties match the bible and ADR section 5; all objects are far under budget; the renders show no flipped normals (the dark strip on the byakuya_shikai shard front view is its down-facing base face, not a flipped face). Most important findings:
- **Sori is read wrongly.** The BASE table says "20 mm maximum deviation from the chord", which is literally the chord reading (tip offset 4 x 20 = 80 mm). At 20 mm tip offset the katanas read straight, and the curve is the main katana cue at item scale. Switch to the chord reading (B1).
- **The Hakuteiken wings are too flat and their feathers overlap coplanar** (z-fighting and double blending in the translucent emissive pass). Rebuild each wing as one continuous sheet with the fan raised to 48 degrees (B12).
- **Three tsuba details vanish at item scale** (Rukia sealed slits, snowflake windows, bankai guard windows). Enlarge them (B4, B5, B11).

---

## 2. Required edits

Coordinates are Blender (Z-up, metres unless mm is written). "Script" means `blender/scripts/blockout_<model>.py`.

### Shared (bb_common.py, affects several models)

**B1. Sori = chord deviation (rukia_sealed, rukia_shikai, byakuya_sealed, rukia_bankai).** In `bb_common.py` set `SORI_K = 4.0` (was 1.0). Do not change the sori arguments in the scripts (0.020 sealed, 0.008 shikai and bankai, saya 0.023). Result: tip offset from the base axis 80 mm (sealed), 32 mm (shikai, bankai), saya 92 mm. The `tip` empties are taken from the blade apex, so they update by themselves; check that `tip` y is now about 0.085 (sealed) and about 0.034 (shikai, bankai). The giant bankai blade has its own `dev()` and is not affected.

**B2. Grip oval orientation (all hilts, including byakuya_shikai_hilt and byakuya_bankai_hilt_ground).** Real tsuka, fuchi and kashira are longer along the edge-spine axis (Y). In `hilt_parts()` change the defaults to `kashira_dims=(0.011, 0.015, 0.014)`, `tsuka_dims=(0.012, 0.015)`, `fuchi_dims=(0.013, 0.016)`, i.e. kashira 22 (X) x 30 (Y) x 14 mm, tsuka 24 (X) x 30 (Y) mm, fuchi 26 (X) x 32 (Y) mm. In `bk_hilt()` change the `kashira_dims` argument the same way to `(0.011, 0.015, 0.014)`. Swell, ring count and lengths stay.

**B3. Not an edit, a rule for all models:** keep every part a closed shell, keep the joined-shell construction, and keep the scene layout offsets of effect objects (petal, shard, crystals, shell, ribbons, LOD, Hakuteiken parts). ADR 5: effect meshes keep their own origin; the `objects` table in `<model>_meta.json` records the layout offset and the Java loader ignores it for effect meshes.

### rukia_sealed

**B4. Tsuba sukashi and corners (readability).** In `tsuba_rukia_sealed()`: concave corner radius 10 mm (was 8: `concave_rect(0.031, 0.036, 0.010, 4)`); each slit 20 mm long along X (x from -0.010 to +0.010, was +-0.007), 6 mm maximum width (half-width term 0.003, was 0.002), centred at y = +-0.025 (was +-0.026), bow 3 mm (curvature term 0.003 instead of 0.0025, same direction: the slit ends bend toward the blade hole). Use n = 8 points per side. Outer size 62 (X) x 72 (Y) x 6 mm unchanged.

### rukia_shikai

**B5. Snowflake tsuba enlarged (readability, frames show large windows and a small hub).** Call `tsuba_snowflake(..., r_out=0.044, r_rim=0.037, r_hub=0.018)`: outer diameter 88 mm, rim 7 mm (inner 74 mm), hub 36 mm (still contains the 32 x 10 mm habaki: corner radius 16.8 mm). Spoke widths stay 7 mm at the hub to 12 mm at the rim. Outer outline 48 segments (was 36). Window radial depth becomes 19 mm (was 12).

**B6. Ribbon overlap removed.** Set `OVER = 0.0`: each segment mesh is exactly 0.25 m long, pitch 0.25 m, hinges unchanged at z = -0.010 - 0.25 (n-1). Reason: the 10 mm overlap puts the 2 mm sheets of two segments coplanar on top of each other (z-fighting in the idle pose), while the gap it was meant to hide is under 1 mm (hinge on the centre line of a 2 mm strip).

**B7. Segment 10 back to 12 tris.** Build `rukia_shikai_ribbon_10` as the same trapezoid prism as the others (width 29.2 to 28.0 mm, 12 tris). The swallow tail becomes an alpha cut-out in the ribbon strip texture (V notch 50 mm deep in the last 25 x 8 px cell); ADR pass 1 is CUTOUT, so this costs nothing.

**B8. Ribbon attachment.** Remove the 4 mm boolean hole in the kashira (invisible at item scale). Add a knot block to `rukia_shikai_blade`: box 14 (X) x 8 (Y) x 10 mm, x -0.007 to 0.007, y -0.004 to 0.004, z -0.010 to 0.000, material `rk_ribbon`, joined into the blade mesh. `ribbon_root` stays at (0, 0, -0.010), which is now the bottom of the knot, so the ribbon hangs from the pommel without the current 10 mm gap.

### byakuya_sealed

Only B1 and B2. Tsuba, saya, blade and the +1 mm shift above the 7 mm tsuba are accepted as built.

### byakuya_shikai

**B9. No tang stub.** The new frame byakuya_shikai_11 (manga ch. 116, hilt after release) shows the habaki block with a flat top and no steel stub. Delete the `h_tang` box. The habaki (32 Y x 10 X x 28 mm, z 0.257 to 0.285) is the end of the hilt. Move the empty `tang_tip` to (0, 0, 0.285) (the petal stream emitter at the habaki top). Remove material `bk_tang` from the object.

**B10. Petal as a crescent leaf, 16 tris (references show curved leaf blades, not a diamond).** Rebuild `byakuya_shikai_petal` with these vertices (local, origin at the tail apex; +Z along the blade, edge -Y):
- tail apex `(0, 0, 0)`;
- ring A at z 0.042: edge `(0, -0.009, z)`, flat `(+0.002, 0.004, z)`, spine `(0, 0.017, z)`, flat `(-0.002, 0.004, z)` (26 mm wide, 4 mm thick);
- ring B at z 0.084: edge `(0, -0.003, z)`, flat `(+0.0015, 0.006, z)`, spine `(0, 0.014, z)`, flat `(-0.0015, 0.006, z)` (17 mm wide, 3 mm thick);
- tip apex `(0, 0.012, 0.120)`.
Faces: tail fan apex to ring A (4 tris), ring A to ring B (4 quads), ring B to tip apex (4 tris) = 16 tris, normals outward (recalc). The 6 mm tang is dropped from geometry (paint it in the 40 x 24 px cell). Keep the scene location (0.20, 0, 0) and the shard as built.

### rukia_bankai

**B11. Oblong guard: longer, with slot windows (frames ep. 385 and ch. 570 show long slots, the blockout reads as a spanner with two round holes).** In `tsuba_bankai_bar()`: outline `stadium(0.011, 0.048, 8)` = 96 (Y) x 22 (X) x 6 mm; windows `stadium(0.007, 0.013, 6)` centred at y = +-0.031, i.e. 14 (X) x 26 (Y) mm slots spanning |y| 18 to 44 mm; centre bridge 36 mm (holds the 32 x 14 mm habaki), end rim 4 mm, side rims 4 mm. Thickness and z 0.280 to 0.286 unchanged.

### byakuya_bankai

**B12. Hakuteiken wings rebuilt as one continuous sheet per wing (fixes coplanar overlap; matches the steep V of [img byakuya_bankai_10, _11]).** Replace `wing()` with this construction, for `sx = +1` (`hakuteiken_wing_l`, extends +X = the player's left when facing -Y) and `sx = -1` (`_r`):
- Feathers i = 0..8: angle a_i = 48 - 7.5 i degrees above horizontal (48, 40.5, 33, 25.5, 18, 10.5, 3, -4.5, -12); length L_i = 6.0 x (1 - 0.075 i) m (6.00 down to 2.40); direction d_i = (sx cos a_i, 0, sin a_i).
- Forward curve applied to every vertex at planar radius r: add (0, -0.8 (r / 6.0)^2, 0) (forward = -Y, back = +Y).
- Vertices: root R = (0, 0, 0); M_i = d_i x 0.30 L_i; Q_i = d_i x 0.65 L_i; T_i = d_i x L_i (feather tip); for i = 0..7 a notch N_i in the direction of the mean angle (a_i + a_(i+1)) / 2 at radius 0.80 x L_(i+1).
- Faces (no face may overlap another): root fan (R, M_i, M_(i+1)) for i = 0..7 (8 tris); band quads (M_i, Q_i, Q_(i+1), M_(i+1)) for i = 0..7 (8 quads); per gap i = 0..7 three tris (Q_i, T_i, N_i), (Q_i, N_i, Q_(i+1)), (Q_(i+1), N_i, T_(i+1)). Total 48 tris per wing (limit 150).
- Wind every face so its normal has a positive Y component (as now). Origin = R (shoulder root), location stays (14, 0, 1.0) in the scene. Single-sided.

**B13. Giant blade: hooked tip and smoother bend (frames byakuya_bankai_3/_4 show tips hooking inward; the blockout top 2 m is a straight leaning section).** Keep the E14 control points. In `dev()` replace the Catmull-Rom tangents by fixed slopes `[0.0, 0.07, 0.17, 0.33]` at z = 0.5, 4.0, 6.0, 8.0 (segment angles then rise monotonically from 0 to about 17 degrees at the tip). Replace the ring stations with z = 0, 0.5, 2.0, 3.5, 4.5, 5.3, 6.0, 6.5, 6.85, 7.1 (full width 0.55), 7.55 (0.40 wide, y shift 0.04), 7.85 (0.18, y shift 0.06): still 12 rings, 142 tris. Move the tip apex so its world z is exactly 8.000 (keep its y). Glow-zone faces = gaps starting at z >= 6.85. The LOD keeps rings at z 0, 4.5, 7.1 and the same apex (it picks up the new `dev()` automatically).

**B14. Ground hilt keeps edge -Y.** In the script replace `Matrix.Rotation(math.pi, 4, 'X')` with `Matrix.Rotation(math.pi, 4, 'Y')` (same translation 0.52). Kashira up, stub bottom at z 0, edge stays -Y so the yaw convention is the same as every other Byakuya mesh. Note in BLOCKOUTS.md: rotated, not mirrored.

**B15. Hakuteiken blade in proportion to a hand-held sword (frame byakuya_bankai_11 shows a katana-width white blade; 0.30 m reads as a plank in hand).** `hakuteiken_blade_body`: width 0.10 m (Y), thickness 0.03 m (X), z 0 to 1.0, 2 shinogi rings, origin at the hilt end. `hakuteiken_blade_tip`: rings (z, width, thickness) = (0.0, 0.10, 0.03), (0.08, 0.09, 0.027), (0.15, 0.06, 0.02), apex (0, 0.017, 0.20): kissaki 0.20 m long. Scene location of the tip stays (8, 0, 1.0).

**B16. Ripple and halo stay as built** (no geometry change). Rename nothing. Add to BLOCKOUTS.md: ripple single-sided +Z; halo single-sided +Y; wings single-sided +Y; see answers in section 3 for the culling rule.

---

## 3. Answers to the open questions in BLOCKOUTS.md (final)

rukia_sealed
- Sori: switch to the chord reading, `SORI_K = 4` (80 mm tip offset); this is the bible's literal wording (B1).
- Tsuka oval: swap to 24 (X) x 30 (Y) mm, long axis along edge-spine; fuchi and kashira likewise (B2).
- Saya curve: yes, the saya follows the blade sori plus 3 mm (92 mm tip offset); the curve is the main katana cue in GROUND/FIXED/HEAD where the sheathed mesh is shown.

rukia_shikai
- Ribbon axis: keep the mesh along local -Z with rotation 0 (ADR requires applied rotation); code rotates each segment about its local X axis through its origin, and the next hinge is the previous segment's local point (0, 0, -0.25) after rotation. No 180 degree bake.
- Ribbon plane: keep flat in XZ (width X, thickness Y); bending about X is the correct flag-like bend, and with the edge facing forward/down in first person the camera sees the spine side, i.e. the ribbon's broad face.
- 16-tri tail: no; segment 10 becomes a 12-tri trapezoid and the swallow tail is an alpha cut-out (B7).

byakuya_sealed
- Tsuba 7 mm: keep the tsuba at z 0.250 to 0.257 and the 1 mm upward shift of habaki, blade and saya (tip stays at 0.980).
- Sori: chord reading, same as rukia_sealed (B1).
- Tsuba windows: keep hard-edged L windows and the 4 mm hub step; bevel only the outer rim (1 segment, bible 0.1); no bevel inside the windows; the step is reinforced by paint.

byakuya_shikai
- Petal: neither; rebuild as the 16-tri crescent leaf of B10 (two shaped rings, no tail ring).
- Shard tang: no; the shard stays an irregular 8-tri splinter (debris must not look like intact petals).

rukia_bankai
- Costume: yes, blocked out after the B-edits, per section 5, against the vanilla player model in unscaled model space (see section 5 for dimensions).
- Ribbon axis: same as rukia_shikai (-Z, rotation 0, bend about local X).
- Ice split: keep the three face regions (edge bevel / core / deep spine) as the UV-island layout guide; at export all faces carry the single material `rukia_bankai_atlas` and the layering lives in the texture.
- ribbon_root: keep it as a player-space point (0, 0.12, 0.90): player model origin at the feet, facing -Y, back = +Y; it is consumed by the player feature renderer, not the item.

byakuya_bankai
- Wing layout: superseded by B12 (fan 48 down to -12 degrees, one sheet); +Y = behind the player, the 0.8 m curve goes toward -Y (forward): confirmed.
- Halo position: the halo origin stays at its own centre; code places it 0.35 m above the head top and 0.3 m behind the back, independent of the wing root; the scene location is layout only.
- Buried 0.5 m: yes, vertical (deviation 0 for z <= 0.5, as E14 defines).
- Ripple: no double-sided rendering needed (only ever seen from above). Halo and wings are single-sided and must be drawn with back-face culling disabled (the entity translucent emissive layer of the effect renderer; the ADR spike verifies it). If culling cannot be disabled, duplicate their faces with reversed winding (halo 96 tris, wings 96 each, still inside the 900 Hakuteiken budget).

---

## 4. Detailing guidance (next step)

General rules for every model:
- Delete faces that are fully hidden by a touching part (tsuka end caps, fuchi top cap, habaki top and bottom caps, blade root cap, kashira top cap, buried bottom cap of the giant blade may stay). This saves triangles and texture space; each part stays a closed or open shell joined by contact, never a hole into the visible surface.
- One material `<model>_atlas` per model at export; the current per-part preview materials define the UV islands only. N-gons are triangulated at export (ADR 1); check the concave tsuba caps for flipped triangles after triangulation.
- Bevels: 1 segment on hero edges only (tsuba outer rim, kashira, fuchi). Blades flat-shaded, tsuka and saya smooth.
- Emissive zones as per bible; the icon renders (ADR 1, 6 flat GUI icons) come after detailing.

Tri allocations below are targets; hard limits from the bible stay.

**rukia_sealed** (drawn target 1800 / limit 3000; sheathed 2000 / 2500)
1. Tsuka wrap ridges: 20 rings x 12 sides, every second ring 1.5 mm larger radius so 9 diamonds bump the silhouette: about 460 tris.
2. Tsuba (enlarged slits, concave corners, rim bevel): about 350.
3. Blade: 10 body rings + yokote ring + 1 kissaki ring, so the 80 mm sori is smooth: about 160.
4. Kashira and fuchi at 16 sides with one chamfer: about 160; habaki box with 1 bevel: about 40.
5. Saya: 12 rings x 16 sides plus koiguchi and kojiri: about 450 (sheathed only).
Skip: menuki, mekugi peg, hamon in geometry, flame embossing (texture), blade hole.

**rukia_shikai** (target 2000 / 3000; ribbons 12 each)
1. Snowflake tsuba (hero, 500 px/m): 48-segment rim, rim bevel, spokes widening, window corners rounded with 3 segments: about 650.
2. White wrap ridges as rukia_sealed: about 460.
3. Blade: long and slim, 10 rings + kissaki: about 160.
4. Pommel knot block with a 1 mm chamfer: about 30.
5. Ribbon: geometry unchanged; detail is texture (edge tint `#B9D4F0`, emissive edges, swallow-tail alpha).
Skip: light ring and release flash (VFX), ribbon holes or folds.

**byakuya_sealed** (drawn target 1900 / 3200; sheathed 2300 / 2800)
1. Window tsuba (hero): rim bevel, keep hard windows and hub step: about 600.
2. Lavender wrap: 11 diamonds as 24 rings x 12 sides with alternating ridges: about 550.
3. Cream kashira cylinder with chamfer ring, 16 sides: about 100.
4. Blade as rukia_sealed: about 160.
5. Saya: as rukia_sealed: about 450.
Skip: wrap knots, sageo cord (not in frames), blade hole.

**byakuya_shikai** (hilt target 1400 / 2000; petal 16 / 20; shard 8 / 12)
1. Hilt: identical detailing to byakuya_sealed (copy the detailed hilt, then remove the blade above the habaki).
2. Habaki top face: one inset (1 mm) so the flat end reads as a collar, about 10 tris.
3. Petal: no extra geometry; paint the centre ridge line and edge line, mirrored UV.
Skip: tang, petal thickness bevels.

**rukia_bankai** (sword target 1600 / 3000; crystals within 60/48/48/36; shell 96 / 120)
1. Oblong guard: stadium outline 16 segments per end, slot windows 8 segments per end, rim bevel: about 300.
2. Ice blade: keep the bevel / core / spine face split, 10 rings + yokote: about 200.
3. Long pale wrap: 0.28 m, 22 rings x 12 sides with ridges: about 520.
4. Crystals: no extra tris; jitter vertices +-8 percent of radius (fixed seed, different per crystal) so the four do not read as scaled copies; tips keep `#D9E8F5`.
5. Ice shell: unchanged count; add one more seeded jitter pass on the end rings.
Skip: ice sparkles in geometry (texture/particles), transparency tricks beyond diffuse alpha.

**byakuya_bankai** (giant blade target 190 / 300; LOD 22 / 24; ground hilt 1400 / 1500; Hakuteiken set about 300 / 900)
1. Giant blade: one extra kissaki ring at z 7.7 for a cleaner hook (+12 tris) and a 2-face edge bevel strip only in the top 1.2 m (+about 24 tris); 32 px/m texture with painted streaks.
2. Wings: one barb per lobe (an extra vertex halfway between Q_i and T_i, pushed 0.25 m sideways, +2 tris per lobe): about 70 tris per wing; spikiness then lives in the alpha sheet.
3. Ground hilt: copy the detailed byakuya_shikai hilt, keep the flat-cut stub.
4. Halo, ripple, Hakuteiken blade: geometry final; detail is the glow texture.
Skip: Senkei sword, any geometry on the soil band.

---

## 5. Deferred items

- **Rukia bankai costume set** (collar, pauldron_l, pauldron_r, crown, chest_flower): order inside `rukia_bankai.blend` is (1) apply B-edits, (2) detail the required pieces, (3) block out and detail the costume set; no separate Gate B for it. Build it against a helper (not exported) of the vanilla player model in unscaled model space, 1 px = 1/16 m: legs z 0 to 0.75, body 0.50 (X) x 0.25 (Y) x 0.75 at z 0.75 to 1.50, head 0.50 cube at z 1.50 to 2.00, arms 0.25 x 0.25 x 0.75 at |x| 0.25 to 0.50 (classic arms), facing -Y, back +Y; the feature renderer applies the vanilla 0.9375 player scale. Origins: collar and chest_flower at the body pivot (0, 0, 1.50), pauldrons at the vanilla arm pivots (+-0.3125, 0, 1.375), crown at the head pivot (0, 0, 1.50). Gate C reviews the required pieces first and the costume second.
- **Senkei sword** (`byakuya_bankai_senkei_sword`): confirmed deferred; model it only after Gate C passes (Gate A decision 7).
- **GUI icons** (ADR 1): after detailing, not part of this step.

---

## 6. Bible changes for v3 (orchestrator; do not edit the bible from the modelling task)

1. 1.0 BASE: tsuka 24 (X) x 30 (Y), fuchi 26 x 32, kashira 22 x 30 (long axis Y) (B2); Byakuya kashira 22 x 30 (B2). Sori wording unchanged, but add "tip offset from the base tangent = 4 x chord deviation" (B1).
2. 1.1: sukashi slits 20 x 6 mm at y +-25 mm, corner radius 10 mm (B4).
3. 1.2: snowflake tsuba 88 mm, rim 7, hub 36 (B5); ribbon segments 0.25 m with no overlap (B6); swallow tail as alpha cut-out (B7); pommel knot instead of the 4 mm hole (B8).
4. 1.4: no tang stub, `tang_tip` (0, 0, 0.285) (B9); petal shape per B10, tang painted only.
5. 1.5: bankai guard 96 x 22 mm, bridge 36 mm, slot windows 14 x 26 mm (B11).
6. 1.6: wings as B12 (longest feather 6.0 m at 48 degrees; "span 6.0 m" now means the longest feather length); giant blade tangents per B13 (control points unchanged); ground hilt rotated about Y (B14); Hakuteiken blade 0.10 x 0.03 m, tip 0.20 m (B15).
