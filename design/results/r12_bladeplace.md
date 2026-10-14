# R12 task 1: bankai blade rows placement

Source: `refs/byakuya_bankai_3.png` (anime ep.52). Script `tools/measure_bankai_rows.py` (blade bases by column runs, vanishing point
by least squares of base/top lines, ground-plane pinhole, scale from Byakuya = 1.8 blocks). Pure fit in `core/fx/BladeRowFit`, tests `BladeRowFitTest`.

| quantity | measured | before (r7) |
|---|---|---|
| eye height (check: Byakuya's eyes) | 1.73 | - |
| lane half width (axis to blade bases) | 3.25 (L 3.45, R 3.0) | 7.0 |
| nearest blade vs feet | 0.85 behind | 2.0 behind |
| blade height | 4.95 = 2.75 x Byakuya (mesh 5.5, scale 1.0 kept) | 5.5 |
| pitch along the row | 2.1 at 50 deg hfov (1.4 at 70, 3.2 at 35): focal length not measurable from one frame | 1.0 |

The old lane was twice as wide as the anime (14 blocks between the rows against about 6.5), which is the "too far from each other and from the character".

Config key `bankai.layout` (fx_override.json in the game dir, none present now): `dense` (pitch 1.0, 16/side), `book` (1.5, 14/side, DEFAULT), `anime` (2.1, 12/side),
`r7` (old), `r9`. All share half width 3.25 and start -0.85. `rows.perSide / halfWidth / spacing / startAhead` still override any preset. bladeScale unchanged (1.0).
Not verified in game.

## Pass 2 (agent)

Second look at all 12 frames (`refs/INDEX.md`) plus a reprojection check (`tools/overlay_bankai_rows.py`, run `python -I tools/overlay_bankai_rows.py --preset book --out <png>`).

Frames that show Kageyoshi placement:
- _3 (ep52): two straight rows, about 18 to 28 distinct blades per side to the dark end. Broad faces look along the row (pages of a book), gaps between slabs. Shafts VERTICAL (left edge of the nearest blade 97 to 93 px over 440 px, 0.5 degrees; the point is bent by the blade shape, not a lean). The rows do not bend.
- _4 (ep58): low camera looking up, a tunnel of tightly packed blades, bands touching. The tip lines of both rows are straight within about 10 px over 560 px and meet at a point under Byakuya; the arch look is perspective of a tilted camera, NOT a curved wall. Rough width check from the band size at Byakuya's depth: the nearest blades stand about 2.4 to 3 blocks from the axis (_3 gives 3.25).
- _7 (Senkei, pink): several concentric curved tiers of small blades with hilts, a different technique (Senkei), so no curved variant was added for Kageyoshi.
- _2/_5 (manga petal storm): two vertical streams either side of Byakuya, same lane idea, no scale. _1, _6, _8..12: no row placement (pose, Goukei sphere, barrage, wings).

Reprojection of the layout on _3 (camera of the fit: vp (640, 499), eye 1.73, 50 degrees, plus a 0.25 block lateral camera shift, the two rows are 3.45 / 3.0 out):
| preset | mean base error, nearest 8 per side, at 50 deg | at its best lens |
|---|---|---|
| anime (2.1) | 17 px | 9.9 px at 45 deg |
| book (1.5) | 24 px | 11.4 px at 57 deg |
| dense (1.0) | 63 px | 17.3 px at 75 deg |
The nearest blades are 150 px per block, so 10 px is 0.07 blocks. Half width 3.25, start 0.85 behind the feet and the blade height (tops line up on the overlay) are confirmed; the pitch is only a lens choice (book = a 57 degree lens, a normal anime lens, so it stays the default). The measured spacing is not even: with the horizon from the base lines (y 509) the pitch grows from 1.6 near to 2.5 at the 8th blade, with a horizon at 410 to 450 it is uniform. Do not read more than the lens range into the pitch.

Changed:
- `LEAN_DEG` 3.0 -> 0.0 (vertical shafts). Pin changes: `BladeRowLayoutTest.leanArrivesAfterCompletionAndStaysSmall` now expects `LEAN_DEG` (0) +- 0.6 instead of 3.0 +- 0.6; the tip test with `LEAN_DEG + 0.5` still holds.
- Blades per side dense 16 -> 24, book 14 -> 20, anime 12 -> 15 (rows 23 / 28.5 / 29.4 blocks long) because the frame shows 18 to 28 per side before the dark. New test `BladeRowFitTest.framePinsOfPass2` pins lean 0, at least 15 per side and length 20..36 blocks for the three presets.
- NOT changed: blade scale (measured 5.0 +- 0.2 = 0.91 of the 5.5 mesh, inside the error of the Byakuya yardstick and the earlier R11 measurement said 5.5; try `bankai.bladeScale` 0.9 in game if the blades look too tall), half width, start, pitch, no curved layout (no frame supports it).

UNVERIFIED in game: how the 20 blades per side read from the player's camera (Minecraft FOV is much wider than the 45 to 57 degrees of the frame, so the same pitch looks sparser), the 3.25 half width versus the 2.4 to 3 hint of _4, the longer rows against the quality tiers and fog.
