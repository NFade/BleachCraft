# GATE C: Final model review (all six models)

Reviewer: Gate C (opus call 5/6). Inputs: `design/ART_BIBLE.md` v3, `design/GATE_B.md`, `LOG.md` (phase 3 sections of all six models), `blender/renders/<model>_turntable.png` and `<model>_atlas.png` for all six, reference sheets `refs/sheets/*.png` and close-ups of byakuya_sealed_1/_6/_9, byakuya_bankai_4/_10/_11, rukia_bankai_2/_3/_12 (rukia sealed/shikai refs are not on this machine: judged from `research/rukia_sealed_shikai.md` and the bible), plus the in-game captures of the real Rukia items made by the orchestrator (session scratchpad `final_b.png`, `r5_b.png`: first person, armor stand, ground, item frame, dark room).

Scope rule: apply the edits in the build/paint scripts, re-run `run_model.py` for the affected models (verify, export, turntable), update the stats in LOG.md. No second Gate C round; the orchestrator checks the numbers and the new renders. Settled Gate B points (sori, ovals, petal, wings B12, giant blade B13, Hakuteiken B15) are not reopened.

---

## 1. Verdicts

| Model | Verdict | Edits |
|---|---|---|
| rukia_sealed | **APPROVED** | none (display only, section 3) |
| rukia_shikai | **APPROVED WITH EDITS** | C1, C3 |
| byakuya_sealed | **APPROVED WITH EDITS** | C2 |
| byakuya_shikai | **APPROVED WITH EDITS** | C2 (shared hilt) |
| rukia_bankai | **APPROVED** (required pieces) | none; costume set still deferred |
| byakuya_bankai | **APPROVED WITH EDITS** | C2 (ground hilt), C4 |

What works: every model reads correctly by silhouette and palette; no flipped faces, missing parts or z-fighting visible; all within budget; scale is consistent across states (sealed 0.98 m, shikai 1.036 m, bankai sword 1.066 m; the Byakuya hilt is identical in sealed, shikai and the ground hilt). Identity cues are present: Rukia sealed red/cream diamond wrap, tan concave guard, violet-black saya; Rukia shikai white snowflake wheel (reads as a clear ellipse ring in first person in `r5_b`), white wrap, swallow-tail ribbon; Rukia bankai pale-blue translucent ice blade with oblong slotted bar guard (matches rukia_bankai_2/_3), crystals and shell; Byakuya bronze open window guard, lavender wrap with cream windows, ivory kashira, dark saya; crescent pink petal; giant blade hook and top glow match byakuya_bankai_3/_4; Hakuteiken wings/halo silhouette matches _10/_11 within the B12 construction.

---

## 2. Required edits (ranked)

**C1. Rukia shikai glow too weak: the item vanishes in the dark (rukia_shikai, `paint_rukia_shikai.py`).** The dark-room capture (`r5_shikai_dark`) shows only a 1-texel line and a faint tsuba: the 35 percent emission sits on one texel column of a 7 px strip, so the bible intent ("stays visible in dark caves") is not met. New values:
- line 16: `EMIT_BLADE, EMIT_TSUBA, EMIT_RIM, EMIT_RIB = 0.60, 0.55, 0.40, 0.45`.
- `blade_strip()`: set `e[r, :] = 0.18` for every row before the edge assignment (whole flat faint body glow), then `e[r, 0] = e[r, 1] = EMIT_BLADE` (2 px edge line instead of 1).
- blade spine strip `blade_s`: put_em 0.15 over the strip.
- ribbon cells: body 0.15, edge rows `EMIT_RIB` (as now, new value).
- wrap, kashira, knot: stay 0.
Diffuse unchanged. This overrides the bible 1.2 emissive numbers (35/25 percent); fold into bible v4.

**C2. Byakuya guard enlarged to reference proportion (byakuya_sealed, byakuya_shikai hilt, byakuya_bankai ground hilt; `atlas_layouts.byakuya_tsuba_shapes()`).** In byakuya_sealed_6 (manga colour) and _9 (anime) the open guard is about 3.5 to 4 grip widths across with large panes; ours is 92 x 56 mm (3 x and 1.9 x the grip) with 15 x 34 mm windows, which shrink to 1 to 2 px in hand. This is the signature of the sealed state, so it gets a model-side fix:
- outline `rounded_rect(0.038, 0.056, 0.006, 4)` = **76 (X) x 112 (Y) mm**, corner radius 6 mm; thickness and z 0.250 to 0.257 unchanged (no shift of habaki, blade, saya, tip).
- windows: `pts = [(9, 4), (30, 4), (30, 48), (5, 48), (5, 20), (9, 20)]` (mm): frame bar 8, centre bar 10, transverse bar 8 and the 18 x 40 mm hub plate with its 4 mm step unchanged; windows become about 25 x 44 mm.
- The function is shared, so `build_/paint_byakuya_sealed.py`, `build_/paint_byakuya_shikai.py` and `build_/paint_byakuya_bankai.py` pick it up; re-run all three. Tris unchanged.
- UV: grow `tsuba_front`/`tsuba_back` islands (all three layouts) from 44 x 72 to 50 x 74 px if the free space allows (about 660 px/m); otherwise keep 44 x 72 (about 580 px/m, still above the 400 px/m hero floor). `tsuba_rim` length grows with the perimeter (about 360 mm).
- `model_specs.py` bboxes: x +-0.038, y min -0.056 for `byakuya_sealed_drawn`, `byakuya_sealed_sheathed`, `byakuya_shikai_hilt` (y +-0.056) and `byakuya_bankai_hilt_ground` (x 2.962..3.038, y +-0.056).
- Bible 1.3 changes to 76 x 112 mm (v4).

**C3. Rukia shikai ribbon wider (rukia_shikai, `build_rukia_shikai.py` line 38).** At 40 mm the ribbon is a string next to the 28 mm blade and disappears in third person; the frames show a cloth band clearly wider than the blade. Set `W0, W1 = 0.060, 0.044` (60 mm at segment 01 tapering to 44 mm at segment 10). Thickness 2 mm, length, hinges, 12 tris per segment and UV cells unchanged (the strip stretches across; the swallow-tail alpha notch stays in cell 10). Update the ribbon bboxes in `model_specs.py`. Bible 1.2 widths change accordingly.

**C4. Hakuteiken halo band thicker (byakuya_bankai, halo builder in `build_byakuya_bankai.py`).** The 0.10 m band is 1 to 2 px at third-person distance and Minecraft has no bloom to fatten it as the anime does (byakuya_bankai_10). Inner radius 1.00 -> **0.94 m** (band 0.16 m; line 233, `1.00` -> `0.94` in both terms), outer 1.10 m, still 48 tris, single-sided +Y. Halo strip texture and 100 percent emissive unchanged.

No other edits. Considered and rejected as nitpicks or settled: Rukia bankai wrap reading dark from the back (render lighting; matches the dark wrap in rukia_bankai_3), petal paleness (bible palette, runtime tint), wing fan angle (B12), giant blade width (B13), Hakuteiken blade width (B15), Rukia sealed guard size (no reference shows it oversized; the red wrap carries the identity in hand).

---

## 3. Hand scale: display transforms, not model changes

The in-game captures show the problem is thinness, not missing parts: a true-scale 30 mm grip and 28 to 32 mm blade at first-person scale 0.90 read as a thin line next to the vanilla sword. A uniform model thickening would break the shared proportions and every UV layout, so the fix is in `models/item/<item>_display.json` (retune with `gradlew runSpike -Ptune=...`):
- firstperson_right/left: scale **0.90 -> 1.20**; retune translation so the tsuba sits at about 0.30 to 0.35 of screen height and the tip leaves the screen near the top-right edge (as the vanilla sword does). Keep rotation [-45, 180, 0].
- thirdperson_right/left: scale **0.85 -> 1.00** (1 m katana on a 1.8 m player).
- ground 0.65, fixed 0.90, gui unchanged.
The only model-side size change is C2 (Byakuya guard), because there the reference itself shows an oversized guard; Rukia guards stay at their current size (the snowflake ring and the red wrap already read in `r5_b`).

---

## 4. Bible v4 items (orchestrator)
1. 1.2 emissive: blade body 18 percent, edge 60 percent (2 px), spine 15, tsuba spokes 55, rim 40, ribbon body 15, edges 45 (C1).
2. 1.2 ribbon width 60 mm tapering to 44 mm (C3).
3. 1.3 tsuba 76 (X) x 112 (Y) mm, corner radius 6 mm, windows about 25 x 44 mm (C2); 1.4 and 1.6 ground hilt follow.
4. 1.6 halo inner radius 0.94 m (C4).
5. 0.2 in-hand factors: first person 1.20, third person 1.00 (section 3).
