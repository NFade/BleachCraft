# GATE A: Art bible review

Reviewer: Gate A (opus). Input: `design/ART_BIBLE.md` (draft), all six contact sheets, individual frames (zoomed crops of rukia_sealed_1/_7, rukia_shikai_3/_10, rukia_bankai_2/_3, byakuya_sealed_1/_3/_5/_6/_9, byakuya_shikai_7, byakuya_bankai_3/_4/_10/_11), the four research files, and MASTER_PROMPT sections 1, 2, 5, 10.

## 1. Verdict

**APPROVED WITH EDITS.** The bible is well structured, original text (longest quote is 9 words, so it passes the copyright check), and mostly feasible. A sonnet agent applies the 27 required edits below. No second Gate A round is needed: the orchestrator checks that every edit was applied, then phase 2 and phase 3 can start. Before blockouts of models 3, 4 and 5, fill the reference gaps in section 5.

Most serious findings:
- The Rukia bankai sword has the wrong guard. Manga ch. 570 and anime ep. 385 both show a narrow oblong bar guard and a longer grip, not the shikai snowflake ring.
- The Byakuya sealed tsuba is drawn too square. It is also geometrically impossible: a 6 mm cross bar cannot hold the 32 x 10 mm blade hole.
- The numbers in the Byakuya bankai storyboard contradict each other. The rows hold only 80 blades but the counts say 200 to 1000. The rise and petal timings do not add up to the stated times. The LOD rules contradict each other. Far blades are scaled down for fake perspective.

---

## 2. Required edits

Apply them in order. "Replace X with Y" means a literal text replacement. Section numbers refer to ART_BIBLE.md.

**E1. Section 1.0, heading.** The heading contains the stray phrase "item space of 0.5". Replace the heading line with:
`### 1.0 Shared katana base ("BASE", used by the sealed and shikai models, metres)`

**E2. Section 0.5, grip point.** The frames show a one-hand grip directly below the tsuba ([img rukia_shikai_3], [img byakuya_sealed_6]), not the middle of the grip. In section 0.5, replace `an empty \`grip_hand\` at (0, 0, 0.10)` with `an empty \`grip_hand\` at (0, 0, 0.19) (for rukia_bankai_sword: (0, 0, 0.22), because its tsuka is longer, see E11)`. In section 1.2 item 3, replace `\`grip_hand\` (0, 0, 0.10)` with `\`grip_hand\` (0, 0, 0.19)`.

**E3. Section 0.5, export hygiene.** Append this bullet: `- Export one OBJ per object listed under **Objects**, file name = object name (\`blender/export/<model>/<object>.obj\`). All OBJs of a model use the same \`<model>_diffuse.png\` and \`<model>_emissive.png\`. Empties are not exported; write them to \`blender/export/<model>/<model>_empties.json\` as \`{"<empty name>": [x, y, z]}\` in Blender coordinates.`

**E4. Section 1.1, palette and manga/anime note.** The current note says anime ep. 1 shows a tan guard. It does not: ep. 1 shows a pale grey-khaki guard and a dark grey saya. Make these changes:
- In the palette table, replace the Saya lacquer row with `| Saya lacquer | \`#2A2433\` | highlight streak \`#4E4560\`; dark violet-black as on Rukia's sword in [img byakuya_sealed_3] (Rukia at right, anime ep. 34) |`.
- Replace the whole "**Manga vs anime**" paragraph of 1.1 with: `**Manga vs anime**: manga colour ([img rukia_sealed_1], ch. 135) shows a tan-gold guard and red diamond wrap with olive-cream windows. Anime ep. 1 ([img rukia_sealed_7]) draws the guard and kashira pale grey-khaki and the saya dark grey; anime ep. 34 (Rukia in [img byakuya_sealed_3]) draws a tan-gold guard and a dark violet-black saya. We ship the tan-gold guard \`#AF9668\`, the red wrap \`#822933\` and the violet-black saya. No runtime variant.`

**E5. Section 1.2, snowflake tsuba.** The frames show a clean circular outline without notches, and spokes that widen toward the rim. Replace the whole "- Tsuba:" bullet of 1.2 with: `- Tsuba: "hollow snowflake-like circle" [RS 2b]. Round ring, outer diameter 80 mm, 6 mm thick, rim width 8 mm (inner rim diameter 64 mm), central hub diameter 26 mm holding the blade hole, six spokes that widen from 7 mm at the hub to 12 mm at the rim, giving six rounded-triangle windows. The outer edge is a clean circle (no notches). Frames: [img rukia_shikai_3] (manga ch. 266), [img rukia_shikai_10] (anime ep. 149).`

**E6. Section 1.2, ribbon.** A 1.6 m ribbon is far too short. [img rukia_shikai_1, rukia_shikai_2] show a single ribbon long enough to circle her whole body. Make these changes:
- Objects item 2: replace `\`rukia_shikai_ribbon_01\` ... \`rukia_shikai_ribbon_08\`: eight separate objects` with `\`rukia_shikai_ribbon_01\` ... \`rukia_shikai_ribbon_10\`: ten separate objects`.
- Replace the "- Ribbon:" bullet with: `- Ribbon: one long white ribbon from the pommel, singular per the wiki [RS 2b] and frames [img rukia_shikai_1, rukia_shikai_2, rukia_shikai_3]. Total 2.5 m = 10 segments x 0.25 m; width 40 mm at segment 01 tapering to 28 mm at segment 10; each segment is a 2 x 1 quad strip with 2 mm thickness (12 tris max); adjacent segments overlap 10 mm at the hinge so no gap opens when bent. Swallow-tail cut on segment 10. Code note: in first-person view only segments 01 to 04 are drawn.`
- Triangle budget line: replace `ribbon segments 12 tris each (96 total)` with `ribbon segments 12 tris each (120 total)`.
- Texture line: replace `bottom band ribbon (one continuous 8-segment strip, 8 x 20 px cells so texture flows along the chain)` with `bottom band ribbon (one continuous strip 250 x 8 px, 10 cells of 25 x 8 px, so the texture flows along the chain)`.

**E7. Section 1.3, Byakuya tsuba geometry.** The guard is a tall rectangle with stepped windows ([img byakuya_sealed_1], [img byakuya_sealed_9], [img byakuya_sealed_6]), not an 85 x 75 near-square. The 6 mm cross bar cannot hold a 32 x 10 mm blade hole. Replace the "- Tsuba:" bullet of 1.3 with: `- Tsuba: bronze **open window frame**, "like a four-pane window" [BS 2a]; frames [img byakuya_sealed_1] (tall frame, stepped windows), [img byakuya_sealed_6], [img byakuya_sealed_9]. Outer 92 mm (along Y) x 56 mm (along X), 7 mm thick, corner radius 5 mm. Outer frame bar 8 mm; longitudinal centre bar (along Y) 10 mm wide; transverse bar (along X) 8 mm wide; solid central hub plate 40 mm (Y) x 18 mm (X) that holds the 32 x 10 mm blade hole. Result: four windows of about 30 x 15 mm, each with a 4 mm step where it meets the hub. Clearly larger and taller than Rukia's guard; this is the signature of the sealed state.`

**E8. Section 1.3, saya evidence and the white variant.** [img byakuya_sealed_3] shows Rukia's saya (right figure), not Byakuya's, and [img byakuya_sealed_2] does not show a saya. In the "- Saya:" bullet, replace `every frame I opened that shows the sheath ([img byakuya_sealed_3, byakuya_sealed_5, byakuya_sealed_2]) shows it dark` with `the frames that show his sheath ([img byakuya_sealed_5], backlit but violet; [img byakuya_sealed_4], night) show it dark`. Replace `A white saya (\`#F2F2EE\`) is listed as an alternative texture variant; see open question 3.` with `No white variant is made (Gate A decision Q1).` In the palette row "Saya", replace `highlight streak \`#554A73\`; alt white \`#F2F2EE\`` with `highlight streak \`#554A73\``.

**E9. Section 1.4, petal evidence.** [img byakuya_shikai_1] shows cross-guard swords stuck in the ground (Senkei-type, bankai-era [BB 8 q9]), not shikai petals. [img byakuya_shikai_2] does not show a petal shape. Make these changes:
- In "Petal-blade proportions", replace `Evidence for the leaf-blade look: [img byakuya_shikai_2, byakuya_shikai_3].` with `Evidence for the leaf-blade look: [img byakuya_bankai_2, byakuya_bankai_5] (manga, slender leaf blades), colour from [img byakuya_shikai_3]. Note: [img byakuya_shikai_1] shows Senkei-type cross-guard swords, not petals; do not use it for the petal.`
- In "Emissive zones" of 1.4, replace `the anime frames ([img byakuya_shikai_1]) show strong pink glow` with `the anime frames ([img byakuya_shikai_3, byakuya_shikai_7]) show strong pink glow`.

**E10. Section 1.5, costume scope (decision Q6).** Replace `the costume pieces (priority B) are authored so a later overlay or armour-layer renderer can attach them.` with `the costume pieces are in scope: they are modelled last in this .blend, after the required pieces pass Gate B, and are rendered as a player feature-layer overlay (head, body, arm parts; mechanism per ADR).` In Objects item 5, replace `Costume set, priority B (player-scale, for a 1.8 m Minecraft player)` with `Costume set (in scope, modelled last; player-scale, for a 1.8 m Minecraft player)`.

**E11. Section 1.5, bankai sword (decision Q4).** Both canon frames ([img rukia_bankai_2] manga ch. 570, [img rukia_bankai_3] anime ep. 385) show a narrow oblong guard and a long grip, not the shikai snowflake. Make these changes:
- Objects item 1: replace `ice katana, hilt, snowflake tsuba, blade.` with `ice katana, hilt, oblong bar tsuba, blade.`
- Replace the whole "- Sword:" bullet with: `- Sword: tsuka 0.28 m (z 0 to 0.28; longer than BASE, the frames show a long two-hand grip), tsuba z 0.28 to 0.286, habaki z 0.286 to 0.314, blade 0.78 m from the habaki start, sori 8 mm, overall 1.066 m. Tsuba: a narrow openwork oblong guard, stadium outline 84 mm (along Y) x 22 mm (along X), 6 mm thick, rim 4 mm, solid centre bridge 16 mm (along Y) that holds the blade hole, so two stadium windows of about 26 x 14 mm. Blade made of "ice": 12 mm thick spine, a clear central core strip, brighter edge bevel. Hilt wrap: pale ice-white lacing with blue-grey diamonds. Frames: [img rukia_bankai_2, rukia_bankai_3]. Kashira: plain pale cap, BASE size.`
- Palette: add the row `| Tsuba | \`#DCE8F5\`, shade \`#9DB4CC\` | pale ice-silver |`.
- Emissive zones: replace `tsuba spokes (50 percent)` with `tsuba rim (50 percent)`.
- Section 0.6 table and the 1.5 triangle budget stay at sword 3000.

**E12. Section 1.5, crown and chest flower.** The frames show a fan of spikes on the right side of the head with a fringe of thin strips, not beads. In the "- Costume" bullet, replace `crown = a small cluster of 5 ice spikes (longest 0.12 m) with a hanging tassel of 3 ice beads` with `crown = a fan of 6 ice spikes (longest 0.12 m) on the right side of the head, sweeping up and back, with a fringe of 5 thin vertical ice strips (0.06 m) hanging below it [img rukia_bankai_2, rukia_bankai_3]`. Replace `chest flower = six-petal snowflake 0.09 m across, relief 15 mm` with `chest flower = six-petal snowflake 0.09 m across, relief 15 mm, with a fringe of 4 thin ice strips (0.05 m) hanging below it`.

**E13. Section 1.5, new object for the absolute-zero freeze.** The storyboard (section 2.3, t = 2.0) uses an "ice shell mesh" that no model provides. Add Objects item `4b. \`rukia_bankai_ice_shell\` (required): faceted ice capsule for encasing mobs, 1.0 x 1.0 x 2.0 m (X, Y, Z), origin at the bottom centre, 10-sided, 3 rings with irregular facets, max 120 tris; code scales it to the mob's bounding box. Palette \`#8EC9EE\` over \`#D9E8F5\`, diffuse alpha 180, emissive 30 percent.` Add `ice shell 120` to the 1.5 triangle budget line and to the rukia_bankai row of the section 0.6 table. In the 1.5 Texture line, append `; one 64x64 cell for the ice shell in the middle-right area below the blade strips.`

**E14. Section 1.6, giant blade curvature.** [img byakuya_bankai_3, byakuya_bankai_4] show the tips clearly hooking inward, with about 8 to 10 percent tip deflection concentrated near the top. 0.40 m (5 percent, uniform) is too straight. In the "- Height 8.0 m" bullet, replace `sori 0.40 m (tip deflecting toward +Y, spine side)` with `sori 0.75 m toward +Y (spine side), concentrated in the upper half: deviation from the straight base axis 0 at z 0.5, 0.10 m at z 4.0, 0.35 m at z 6.0, 0.75 m at the tip`.

**E15. Section 1.6, LOD blade object.** 1000 blades x 170 tris must be rebuilt every frame in 1.21.1, so the far blades need a cheap version. Add Objects item `1b. \`byakuya_bankai_blade_lod\` (required): same outline, origin and UV region as \`byakuya_bankai_blade\`, 3 rings x 4 vertices plus the tip (flat slab, same 0.75 m sori), max 24 tris; used beyond 32 blocks from the camera.` Add `blade LOD 24` to the 1.6 triangle budget line and to the byakuya_bankai row of the section 0.6 table.

**E16. Section 1.6 and section 2.5, halo size.** The frames show the ring framing the head ([img byakuya_bankai_10, byakuya_bankai_11]); a 3.2 m ring dwarfs the 1.8 m player. Replace the "- Halo:" bullet with `- Halo: vertical ring, outer radius 1.10 m, inner 1.00 m, 48 tris, double-sided, origin at its centre, plane = XZ; code places the centre 0.35 m above the top of the head and 0.3 m behind the back.` In the section 2.5 Shukei intro, replace `halo radius 1.6 m` with `halo radius 1.1 m`.

**E17. Section 2.0 and all of section 2, frame-based timings.** Frame counts depend on FPS. In 2.1, replace `3 frames ramp up, 2 hold, 8 down (about 0.22 s)` with `ramp up 0.05 s, hold 0.03 s, fade 0.14 s (0.22 s total)`. Everywhere else in section 2, replace each "N frames" with N/60 seconds rounded to 0.01 s (for example `flash 30 percent, 3 frames` becomes `flash 30 percent, 0.05 s`; `4 frames` becomes `0.07 s`). This includes the Reduce motion bullet in 2.0.

**E18. Section 2.0, post effects and batching.** In the "Post effect" bullet, after `\`frost_edge\` = blue frost vignette for Rukia's bankai;` insert `\`freeze_desat\` = full-screen desaturation 0 to 30 percent (used by the absolute-zero pause);`. In the "Batched renderers" bullet, replace `(one draw call per effect type)` with `(one buffer per effect type per frame; the ADR chooses a CPU-built buffer or a cached VBO)`.

**E19. Section 2.2, Shirafune.** Replace the 0.3 row's shape text `Stretched \`rukia_bankai_sword\`-style blade mesh or a long glowing quad strip, width 0.25,` with `Code-generated glowing strip (two crossed quads, no Blender mesh), width 0.25,`.

**E20. Section 2.3, release.** In the 1.0 row "Costume forms", replace `Priority B overlay fades in from the feet up: crystals grow on the shoulders (\`rukia_bankai_crystal_a\`), ribbons unfurl` with `Costume overlay (collar, pauldrons, crown, chest flower) fades in from alpha 0 to 1 over 0.6 s, ribbons unfurl, 6 \`rukia_bankai_crystal_a\` grow around her feet`.

**E21. Section 2.3, absolute-zero pause.** In the 2.0 row: in the Sound column, replace `silence (cut ambient to 20 percent)` with `no new sounds until t = 3.0`. In the Screen column, replace `slight desaturation 15 percent` with `\`freeze_desat\` 15 percent`. Sound ducking has no stable API.

**E22. Section 2.5, row layout and blade count.** The current rows (2 x 80 blocks / 2-block spacing = 80 blades) cannot hold the stated 200 to 1000 blades. The brief says the rows are behind the player. The frames show rows denser than 2-block spacing. Replace the whole "Rows:" paragraph with: `Rows: 2 rows parallel to the player's look direction at release, 6 blocks either side of the player axis. Each row has R ranks: rank 1 is innermost; each further rank is 1.2 blocks further out, staggered 0.55 blocks along the row, and scaled +10 percent. Blade spacing along a rank is 1.1 blocks. Rows start 3 blocks in front of the player and extend behind him. Blade count N = config \`bankaiBladeCount\` x effectQuality (default 200, max 1000); R = max(2, ceil(N / 200)), at most 5; blades per rank = N / (2 x R). Default: 2 rows x 2 ranks x 50 = 200 blades, 55 blocks long. Maximum: 2 x 5 x 100 = 1000 blades, 110 blocks long. All blades are full scale (no perspective taper; perspective is real in 3D). Blades are cosmetic and pass through terrain. Evidence: [img byakuya_bankai_3, byakuya_bankai_4].`

**E23. Section 2.5, rise timing.** At 0.5 m/s² a blade needs 5.7 s to rise 8 m, and a 30 ms x 200 stagger takes 6 s. Both contradict "rows complete at 2.2". Replace the 1.0 row's "Shape" text with `Each blade rises from -8 m to its rest height in 0.5 s (ease-out cubic). Start delay per blade = 0.012 s x its distance in blocks from the player along the row (default rows complete at t ≈ 2.2, maximum-length rows at t ≈ 2.8).` In the same row's Particles column, replace `20 dust each tick` with `4 dust per rising blade, at most 400 total`. In the 2.2 row's Event column, replace `Rows complete` with `Rows complete (default length; 2.8 at maximum length)`.

**E24. Section 2.5, petal storm count.** Replace the 0.3 row's Shape text `Each blade breaks into 15 petals starting at the tip, 0.4 s per blade, stagger 15 ms` with `Each blade breaks into P = min(15, floor(maxPetals / N)) petals, where maxPetals = 3000 x effectQuality, over 0.4 s from the tip down; start delay = 0.008 s x the blade's distance in blocks from the player. The storm never exceeds maxPetals.` Keep `3000 petals at quality 1.0`.

**E25. Section 2.6, LOD.** The current bullet culls petals beyond 48 blocks and also reduces them by 75 percent beyond 48 blocks; that is a contradiction. It also culls the 55 to 110 block blade corridor. Replace the first bullet of 2.6 with: `- Mass petals and blades are batched draws (\`PetalSwarmRenderer\`, \`BankaiBladeRenderer\`), never entities. Petals: full count up to 16 blocks from the camera, 50 percent at 16 to 32, 25 percent at 32 to 64, culled beyond 64. Giant blades: full mesh up to 32 blocks, \`byakuya_bankai_blade_lod\` beyond 32, culled beyond 160; blade emissive pass only up to 64 blocks. Ordinary particles use vanilla culling.`

**E26. Section 2.4, petal glow citation.** In the 2.4 intro, after `Petal colour \`#E5A4DC\` (body) / \`#F9C8F6\` (highlight);` insert ` reference frames [img byakuya_shikai_3, byakuya_shikai_7];`.

**E27. Section 3.** Replace the body of section 3 (the 15 open questions) with the heading `## 3. Gate A decisions (final)`, followed by the 15 lines of section 3 of GATE_A.md copied verbatim. Change the document status line at the top from `Status: DRAFT for Gate A review.` to `Status: APPROVED WITH EDITS at Gate A (see design/GATE_A.md); edits applied.`

---

## 3. Decisions on the 15 open questions (final)

1. **Byakuya saya colour:** dark violet-black `#2E2840` confirmed ([img byakuya_sealed_5], [img byakuya_sealed_4]); no white variant is made.
2. **Giant blade size:** 8.0 x 0.55 x 0.14 m accepted, with sori 0.75 m (E14); blades are cosmetic, pass through terrain, and build height is irrelevant.
3. **Giant blade glow:** accepted as a design decision (top 1.2 m gradient plus 40 percent edge line), scaled by the emissive multiplier setting.
4. **Rukia bankai sword:** rejected as drafted; use the oblong bar guard and 0.28 m grip shown in ch. 570 and ep. 385 (E11).
5. **Shikai blade length:** 0.78 m accepted (the frames win over "assume unchanged").
6. **Rukia costume pieces:** kept in scope (collar, both pauldrons, crown, chest flower), modelled last after Gate B, and rendered as a player feature-layer overlay; Gate C judges them only after the required pieces pass.
7. **Senkei:** kept as an optional stretch; `byakuya_bankai_senkei_sword` is modelled only after Gate C passes, and its storyboard stays marked optional.
8. **Byakuya bankai voice phrase:** the trigger is "Bankai" optionally followed by "Senbonzakura Kageyoshi" (MASTER_PROMPT); "Chire" is never a bankai trigger.
9. **Rukia bankai phrase:** "Bankai, Hakka no Togame" is the trigger; VOICE_PHRASES.md notes it is game-sourced, not canon.
10. **Petal size and glow:** the 12 cm leaf-blade with 25 percent emission is accepted as a readability trade-off; the emissive multiplier setting can turn it off.
11. **Dome radius:** 3 blocks gameplay radius; the 0.85 m Hurtless Area is cosmetic lore only.
12. **Ribbon count:** shikai has one ribbon (confirmed by [img rukia_shikai_1, _2, _3]) but 2.5 m long (E6); bankai has 3 ribbons from the back (confirmed by [img rukia_bankai_2, _3]).
13. **Texture sizes:** 256x256 for the four small models and 512x512 for the two bankai models are confirmed.
14. **Coordinate convention:** no objection to Z-up, origin at the kashira, edge -Y, sori toward +Y; only the `grip_hand` height changes (E2) and export becomes one OBJ per object (E3).
15. **Colour accuracy:** no separate sampling pass; the bible HEX values (as edited) are binding starting values, painters check them against the cited frames, and Gate C reviews colour on turntables.

---

## 4. Optional suggestions (not required)

- Ribbons: instead of hinged rigid segments, code could build one dynamic triangle strip along a verlet chain, using the same UV strip. This gives smoother bends. The segment meshes stay as the brief requires and serve as the fallback.
- Byakuya bankai darkening: implement it as `vignette_dark` plus a fog-colour change. A true sky recolour needs a mixin and is not worth the risk in the prototype.
- Rukia sealed blade: the wiki notes curved lines embossed on both faces. A faint 1 px painted line pair on the blade strips would add identity at no geometry cost.
- Hakuteiken wings (6 m each) can clip into walls indoors; expose a `wingScale` config (default 1.0).
- Release flash: use a slightly different flash tint for shikai and bankai (bankai whiter) so the two read as distinct moments.
- Fix `refs/INDEX.md` (orchestrator task, not this gate): relabel `byakuya_shikai_1` as Senkei-type swords (bankai-era) and `rukia_bankai_12` as a duplicate of `rukia_shikai_6`.

---

## 5. Reference gaps to fill

Sufficient as is: rukia_sealed (9), rukia_shikai (11), byakuya_bankai (12). For rukia_sealed, one face-on close-up of the tsuba in anime colour would help but is optional.

**rukia_bankai (12, at the limit): replace 3 weak files.** `rukia_bankai_12` is byte-identical to `rukia_shikai_6` (same MD5). `rukia_bankai_7` shows Äs Nödt, not Rukia's costume. `rukia_bankai_8` is a 190x108 GIF and useless for modelling. Replace them with:
1. Anime ep. 385 (or manga ch. 569-570) close-up of the bankai sword hilt and oblong guard.
2. Anime ep. 385 back or three-quarter back view showing where the ribbons attach and how they loop.
3. Head and shoulders close-up showing the ice hair ornament (crown) and the collar tiers.
Fill these before model 5 (rukia_bankai) blockout.

**byakuya_sealed (9 -> 12): add 3.** Fill before model 3 blockout:
1. Face-on close-up of the bronze window tsuba (anime, guard at least 150 px tall).
2. Full sheathed sword at the hip in neutral daylight, showing saya colour and length (Soul Society arc).
3. Hilt close-up showing the lavender wrap pattern and the cream kashira.

**byakuya_shikai (8 -> 12): add 4.** Only `_2`, `_3` and `_7` (Reigai) are usable shikai frames. Fill item 1 before model 4 blockout:
1. Hilt-only frame after release (blade gone), showing what remains at the tsuba, for example Byakuya vs Ichigo (ep. 54-57) or vs Renji (ep. 51).
2. A clean shikai petal stream from Byakuya himself (anime; not Reigai, not bankai-era).
3. A manga shikai panel with white petals (for the tint variant).
4. A shikai barrier or wall frame (for the dome mode).
