# Uryu effects T1-T6: results note

Six stages, one section each. They map to the storyboard as follows: T1 is U-FX 7 (wings, row S6a), T2 and T3 are U-FX 8 (Z arrow 2.6, wing barrage 2.12, row S6b), T4 is U-FX 9 (Licht Regen 2.13, row S7), T5 is U-FX 10 (Sternenstaub 2.14, row S8), T6 is U-FX 3 (shunpo 2.15 and bankai end 2.16, row S9). Progress rows are in design/results/URYU_PROGRESS.md.

Branch `uryu-fx-wip`. Base commit 51bac04. The wing work starts from WIP commit 3753fea. Everything else in T1-T6 is uncommitted (28 changed or new paths in the worktree at the time of writing).

Status of all six: build green, unit tests pass, game NOT run, nothing committed. Nothing here was checked in game. Line numbers are not given; the file names are.

---

## 1. T1: Vollstaendig wings (U-FX 7, S6a)

Built:
- Release, permanent wings, bracers, boots and emblem, through `UryuQuincyFeature.addBankaiPass`. The wings fold at the end of bankai.
- `UryuWingFx.wingTips`, `UryuWingMath.MESH_PLUS_X_IS_LEFT`.
- First-person guard in `UryuWingRenderer.draw` and `put`, with FxTune keys `uryu.fpYawMax`, `uryu.fpBack`, `uryu.fpClear`, `uryu.fpFull`.
- Harness scenes in `Phase7Vollst`: `uryu_vollst`, `uryu_vollst_rm`, `uryu_vollst_end`, `uryu_vollst_f`.

Tests: `UryuVollstTimingTest` 14; `UryuWingMathTest` 19, plus 2 added later.

Not done: first-person depth fade on the bracers; first-person guard on the emblem.

## 2. T2: Z arrow (U-FX 8, storyboard 2.6)

Built in `UryuArrowLook`, `UryuArrowFx`, `UryuArrowRenderer`: spinning zeichen_cross muzzle, 10 dashes, 2 wing flakes in the tail, hit ring 0.6 to 4.0 over 0.35 s, amethyst sounds. `UryuArrowFx.hit` takes a kind. Harness scene `uryu_z` in `Phase7Bow` (p7_08_z_*).

Tests: `UryuArrowLookTest`.

Not done: homing curve in the scene; FOV +2 and the cast shake.

## 3. T3: Wing barrage (U-FX 8, storyboard 2.12)

Built: `UryuWingFx.barrage` and `tipFire` (24 dashes from the 4 tips, zeichen_cross behind the back, camera shake, sounds). `UryuWingMath.crossLevel` and `crossAngle`. Wing cues in `UryuArrowLook`. Harness scene `Phase7Barrage` (`uryu_barrage`, `uryu_barrage_rm`).

Not done: per-plate brighten near the tip; lock-on line toward the target. The scene assumes the Z slot casts the barrage.

## 4. T4: Licht Regen (U-FX 9, storyboard 2.13)

Built: `UryuRainFx` rewritten; `UryuRainMath` with its test (4 tests); events 78 and 76; needles drawn in `UryuArrowRenderer`. Harness scene `Phase7Rain` (p7_09; `uryu_rain`, `uryu_rain_rm`).

Not done: real particles for the puffs (mist sprites used instead); flechette gather above the caster.

## 5. T5: Sternenstaub (U-FX 10, storyboard 2.14)

Built: `UryuStarFx`; `UryuStarMath` with its test; events 78 and 77 plus a LINE packet. Harness scene `Phase7Star` (p7_10; `uryu_star`, `uryu_star_rm`).

Not done: `uryu_star_head` model (a diamond_star flare is used instead); burst at pierced targets (the server sends no hits); FOV -3 during the charge and HITSTOP; the beam is camera-facing strips, not crossed pairs.

## 6. T6: Shunpo (U-FX 3, storyboard 2.15) and end of bankai (2.16)

Built: `UryuShunpoFx`; `UryuShunpoMath` with its test; `UryuFx.shunpoHirenkyaku`, called from the Uryu branch of `ShunpoFx.play`. Three cyan afterimages (#4FC8FF). The old look stays behind `uryu.shunpoOld`. Harness scene `Phase7Shunpo` (`uryu_shunpo`, `uryu_shunpo_rm`).

2.16 was checked: `BankaiEnd`, `clearWings` and `bankaiEnded` already match the storyboard, so nothing changed there. The seal-time bow collapse into the pendant was not touched.

---

## 7. Shared files

Dispatch of the six scenes: `Phase7Uryu.java` lines 54 to 66 (`uryu_vollst*`, `uryu_barrage*`, `uryu_rain*`, `uryu_star*`, `uryu_shunpo*`). The Z scene is in `Phase7Bow`.

Hooks used in the WIP and the stages: `UryuQuincyFeature.addBankaiPass`, `UryuFx.shunpoHirenkyaku`, the event ids 76, 77 and 78, and the LINE packet.

## 8. UNVERIFIED in game

All six. The "not done" lines above are open. Also open: wing look on slim and classic skins, the rain puffs and the star beam as seen on screen, and the shunpo afterimage spacing.

## 9. Manual checklist

Run the harness once per scene: `gradlew runPhase7 -Phold=<scenario>`, scenario names as listed above.

1. Vollstaendig: wings in third person, first-person view with the wings turned away, fold at bankai end.
2. Z arrow: spin, dashes, tail flakes, hit ring.
3. Barrage: 24 dashes from the 4 tips, shake, sounds; compare `uryu_barrage_rm` for Reduce Motion.
4. Licht Regen: needles, mist puffs, event order.
5. Sternenstaub: charge, beam strips, flare at the head.
6. Shunpo: 3 cyan afterimages, timing against the old look (`uryu.shunpoOld`).

Note anything that looks wrong and send the shots if you took them.
