# Uryu U-FX 4: results note

Stage U-FX 4 of URYU_VFX_STORYBOARD (2.7 Sprenger pentagon, 2.8 Sprenger bind visual, section 6 harness rows p7_04_sprenger_*).

Branch `uryu-fx-wip`. The stage is one commit: `7f337a9` (range `32bb0bd..7f337a9` contains only this commit). Numbers and file:line refs are read from `7f337a9`. Nothing here was run in the game, and no gradle or test run was made for this note (see sections 4 and 5).

---

## 1. What was built

One line per file, all in `mod/src/`.

| File | Status in 7f337a9 | What it does |
|---|---|---|
| client/fx/UryuSprengerFx.java | changed (63 lines in the stat, hunks in section 3) | Pentagon figure: five daggers, hub and spokes, edges, floor, fence, charge, anticipation, explosion (column, Zeichen flare, rays, shock ring), early look, cancel, break, aftermath. New: `reset()` (harness), white edge blast, dagger blast, early core and diamond star. |
| client/fx/UryuBindFx.java | changed (2 lines) | Bind visual on the rooted entities (2.8): foot sigil now `ZEICHEN_CROSS`, rings, chains, ends by reason. |
| client/fx/UryuSeeleRenderer.java | not edited | Draws the five daggers (drop, landing, lift). Read only: `lift()`, `Seele.dropHeight`, `lastDrawn`, `clear()`. |
| client/fx/FxDecals.java | not edited | Not touched. The scar decal call (`PENT_SCAR`, UryuSprengerFx.java:611) is older code, outside this commit's hunks. |
| client/fx/UryuFxStats.java | changed (+50 lines) | Harness accessors for the Sprenger and bind counters (traps, bursts, cancels, daggers, curtains, roots, chains). |
| client/dev/Phase7Sprenger.java | new (397 lines) | Harness scenarios `uryu_sprenger`, `uryu_sprenger_live`, `uryu_sprenger_f16` with the shots and `cCheck`s (section 4). |
| client/dev/Phase7Uryu.java | changed (+3 lines) | Dispatches to `Phase7Sprenger.steps()`. |
| core/fx/SprengerFxPlan.java | changed (32 lines in the stat) | All Sprenger numbers and pure functions: timings, geometry, bind wave, edge blast, explosion table, sounds. |
| core/fx/BindFxPlan.java | changed (javadoc only) | Class comment now says Quincy cross sigil, not Zeichen. No number changed. |
| test: core/fx/SprengerFxPlanTest.java | changed (1 renamed, 2 new tests) | Pins the bind flash, bind wave and edge blast. |

---

## 2. Timeline in force

Times are seconds from the TRAP packet (`t = 0`, server ticks / 20). Constants are in `SprengerFxPlan` unless noted. Radius r = 3 (`UryuBalance.SPRENGER_RADIUS`).

| t (ms) | Tick | What is drawn |
|---|---|---|
| 0 | 0 | TRAP packet and effect 73 start the figure. Five daggers start to fall from `DAGGER_HEIGHT` = 6.0 blocks above their vertices (set at UryuSprengerFx.java:266). |
| 0 to 420 | 0 to 8 | Dagger `i` falls from `dropAt(i)` = 50 i ms, drop time 220 ms, so it is down at `landAt(i)` = 220 + 50 i ms (220, 270, 320, 370, 420). Each has a 2.5-block streak above it and a glow card at the hilt. |
| 220 to 420 | 4 to 8 | On each landing (UryuSprengerFx.java:326-336): a star4 flash (size 0.7, 0.12 s), a glow_soft, six flechette flakes at the floor (`cy + 0.2`), landing sound. |
| 300 | 6 | Spokes start (`SPOKE_AT`), a rotating six-point star (STAR6) and a glow core at the hub fade in over 0.1 s, `spokeLength` grows at 40 blocks per second. Spoke sound at 300 ms. |
| 375 | 7.5 | Spokes reach the daggers (`spokesDoneAt(3)`), beads (glow core) light at the daggers over 80 ms. |
| 550 to 800 | 11 to 16 | Edges draw in order 0-1, 1-2, 2-3, 3-4, 4-0, 50 ms each (`EDGE_AT`, `EDGE_STEP`): a bright head runs along each. Edge sounds 60 ms apart. |
| 550 to 850 | 11 to 17 | Floor disc (Q8, glow_soft 7.0) fills from 0 to 0.25 over 300 ms (`FLOOR_AT`, `FLOOR_TIME`). |
| 800 | 16 | **Bind** (`BIND_AT`). The five edges flash to full (`bindFlash`, 100 ms, plus 0.6 on the edge intensity). Hub flare: glow core and six-point star (STAR6) at `y + 0.3`, size 2.6 k, life 400 ms, peak 0.7 falling quadratically to 0 at 1200 ms. Ring wave: two rings (RING_THIN, RING_SOFT) from the hub out to 1.15 r = 3.45 blocks at 1050 ms (easeOutCubic), peak 0.8 fading out. Fence: five curtains rise 0 to 3.0 blocks in 200 ms (`oc`), alpha 0.12, ramps in over 80 ms. Per bound entity: foot sigil, rings, chains (section 2b). Sounds `block.beacon.activate` 1.4 and `block.amethyst_block.resonate` 0.8. |
| 800 to 1750 | 16 to 35 | **Charge**: centre column grows, radius 0.15 to 0.6 and height 0 to 4 over 950 ms (`iq`). Dust ring: 28 puffs every 400 ms. Glitter 8 per second. Hum pitch 1.0 to 2.0 in 4 steps 250 ms apart. Pentagon pulses (existing formula). |
| 1750 | 35 | **Anticipation**: everything dims to 0.2 (ramp 40 ms, `ANTICIPATION_RAMP`), held until 1900 ms, then relights to 0.5 at the explosion. Column becomes a thin needle (radius 0.08). Other Uryu sounds of the caster are ducked from 1750 to 2000 ms. |
| 2000 | 40 | **Explosion** (`EXPLODE_AT`). END(EXPLOSION) for every bound entity, then BURST. Column: core radius 0.87 r and outer r, height 0 to 40 in 120 ms (`oe`), width down to 0.3 over 500 ms, life 750 ms. Zeichen flare (`ZEICHEN_STAR`, the only place it is drawn in this stage, UryuSprengerFx.java:536): size 4 r = 12, peak 0.9, life 600 ms, 0.4 turns per second. Edges and spokes flash white (`edgeBlast`, 150 ms, x1.3 if early). Each dagger corner bursts (star4 and glow_soft, 150 ms). Four flechette flakes per dagger from hilt height minus 0.4 (UryuSprengerFx.java:457). Daggers are no longer drawn. |
| 2150 | 43 | Fence shatters: 20 flakes (`SHATTER_FLAKES`) at 0.3 to 2.9 blocks above the floor, glass sound. Edges fade over 300 ms (to 2450 ms). Scar decal (existing, lives 6 s). |
| 2300 to 3000 | 46 to 60 | Aftermath: glitter 20 per second and dust 6 per second, decaying (`AFTERMATH_FROM` 0.30 to `AFTERMATH_TO` 1.0 after the burst). |

### 2a. Early detonation

| Item | Value (read) |
|---|---|
| Server trigger | A fully focused arrow (Focus 3) touching the pentagon (`SprengerTrap.detonates`, UryuManager.java:391-392). |
| Damage | 13.5 instead of 9 (`SPRENGER_EARLY_FACTOR` 1.5, UryuManager.java:566). |
| Look | Flare x1.2 (`FLARE_EARLY_SCALE`), core white (`EARLY_CORE_INTENSITY` 1.0 instead of 0.9), a diamond star (DIAMOND_STAR, size 1.8 r x 0.5 r at `cy + 1.4`, life 300 ms), a thin shock ring (RING_THIN, 1.15 x the shock ring) at ground level, edge blast x1.3, screen flash peak 0.65 (`FLASH_EARLY`, UryuSprengerFx.java:582). |
| Harness | `early` scenario fires BURST with `early = true` at 1200 ms (Phase7Sprenger.java). The storyboard says "same from t 1.75" for the early look. |

### 2b. Bind visual per rooted entity (BindFxPlan, 2.8)

| Element | Value (read) |
|---|---|
| Foot sigil | Quincy cross, `GlowSprite.ZEICHEN_CROSS` (UryuBindFx.java:297). Size 0.8 x width + 0.9 (`sigilSize`). Alpha 0.6 to 1.0 flickering at 8 Hz, peak 0.8, 0.5 turns per second. |
| Rings | Three rings at heights 0.12, 0.45 and 0.78 (scaled by entity height), radius 0.4, intensity 0.5, alternating turn direction. |
| Chains | Five, width 0.28, intensity 0.5, sag 0.3, light runs down them at 1.5 turns per second. Only the three nearest entities get rings and chains (`MAX_FULL` = 3). |
| Sound | Two nearest, `block.amethyst_block.resonate` every 0.8 s. |
| Fade in | 150 ms (`RAMP_IN`). |
| Ends | DAMAGE: chains snap in 120 ms, 12 flakes, rings grow to 3x over 250 ms, sigil out in 150 ms, glass sound. EXPLOSION: everything out in 100 ms. EXPIRE: 250 ms. CANCEL: 300 ms. |

---

## 3. Shared-file edits with file:line (commit 7f337a9)

Line numbers are in the 7f337a9 version of each file.

| File | Lines | Edit |
|---|---|---|
| client/dev/Phase7Uryu.java | 48-50 | New dispatch: `wants("uryu_sprenger")`, `uryu_sprenger_live`, `uryu_sprenger_f16` call `Phase7Sprenger.steps()`. |
| client/fx/UryuFxStats.java | 36-85 | 50 lines added: `sprengerReset()` (40), `sprengerTraps/Bursts/Cancels/Live/Curtains`, `daggersDrawn`, `binds`, `roots`, `rootsFull`, `rootChains()` (83). |
| client/fx/UryuBindFx.java | 18 | Class javadoc: sigil is a Quincy cross, not the Zeichen. |
| client/fx/UryuBindFx.java | 297 | `GlowSprite.ZEICHEN_STAR` replaced by `GlowSprite.ZEICHEN_CROSS` for the foot sigil. |
| core/fx/BindFxPlan.java | 6 | Class javadoc only. |
| core/fx/SprengerFxPlan.java | 58-67 | Constant comments and new constants `WAVE_REACH` (63), `WAVE_TIME` (64), `WAVE_PEAK` (65), `EDGE_BLAST_TIME` (67); `HUB_SIZE` comment changed. |
| core/fx/SprengerFxPlan.java | 319-345 | `hubFlare` (323, no longer a Zeichen), new `bindWaveRadius` (329), `bindWave` (334), `edgeBlast` (340). |
| test core/fx/SprengerFxPlanTest.java | 126, 136, 148 | 126 renamed and reworded, 136 and 148 new. |

UryuSprengerFx.java hunks (owned file, 7f337a9 line numbers):

| Lines | Edit |
|---|---|
| 155-166 | `reset()` for the harness: clears pentagons, binds, seeles and counters. |
| 453 | Sign fix: the hilt height now adds `UryuSeeleRenderer.lift` (was subtracted). |
| 529-534 | Early look: diamond star and thin shock ring (only when `early`). |
| 687-702 | Edge blast passed to `figure`; an exploded figure is drawn while its fade or its blast is above 0.004 (was fade only). |
| 713 | `figure` gets a new `blast` parameter. |
| 724-728 | Bind flare: six-point flare and glow core at the hub, only while the daggers stand. |
| 730-733 | Bind ring wave (RING_THIN, RING_SOFT). |
| 779-781 | Blast streak on the spokes. |
| 806-812 | Flat band on each edge (LINE ground, width x2, alpha 0.3) and blast streak. |
| 824-828 | Corner burst: star4 and glow_soft at each corner, 150 ms. |

UryuSeeleRenderer.java and FxDecals.java: no lines changed in this commit.

---

## 4. Tests

Unit tests (`mod/src/test/java/.../core/fx/SprengerFxPlanTest.java`, 24 tests in the class):

| Test | Status |
|---|---|
| theHubFlashOnlyFlaresAtTheBindAndIsGoneAfterFourTenths | renamed from theHubStarOnlyFlaresAtTheBindAndIsGoneAfterFourTenths, values unchanged |
| theBindWaveRunsFromTheHubPastTheDaggersInAQuarterSecond | new |
| theEdgesBlastWhiteAtTheExplosionForAFifteenthOfASecond | new |
| 21 other tests | unchanged in this commit (among them bindAndExplosionFollowTheServerTicks, theFigureHappensInTheStoryboardOrder, edgesDrawOneAfterTheOtherFiveHundredthsEach, everythingDimsToTwoTenthsAtTheAnticipation, soundTablesAreVanillaIdsWithSaneValues) |

`BindFxPlanTest` (9 tests: onlyTheNearestThreeGetTheFullVisual, everyEndReasonHasItsOwnLook, endingsFadeAndBurstOverTheirTimes, and others) is unchanged. No test pins the sprite choice (ZEICHEN_CROSS).

Harness scenarios (`client/dev/Phase7Sprenger.java`, run with `gradlew runPhase7 -Phold=uryu_sprenger[,uryu_sprenger_live,uryu_sprenger_f16]`, per the class javadoc):

| Scenario | Shots (`p7_04_sprenger_*.png`) | In-game checks (`cCheck` names) |
|---|---|---|
| uryu_sprenger, ground | t0, t220, t550, t800, t1200, t1750, t2000, t2050, t2300, t3000 | TRAP started one pentagon; five daggers stand; fence of five curtains up; bound zombies have roots (roots, full, chains); explosion played; daggers gone; figure writes glow sprites |
| uryu_sprenger, back | back_t550, back_t1200, back_t2000 | same |
| uryu_sprenger, top | top_t550, top_t1200, top_t2000, top_t2300 | same |
| uryu_sprenger, night | night_t550, t1200, t1750, t2000, t2300 | same |
| uryu_sprenger, Fabulous | fab_t1200, fab_t2050 | same |
| uryu_sprenger, early | early_t1100, t1230, t1300, t1500; early_top_t1300 | same, with the explosion at 1200 ms |
| uryu_sprenger, cancel | cancel_t950, t1050, t1150, t1400 | cancel seen and no explosion |
| uryu_sprenger, break | break_t1050, t1130, t1250, t1600 | roots of the first zombie snap at 1100 ms (DAMAGE), the other stays rooted |
| uryu_sprenger_live | live_a (about 0.3 s), live_b (1.2 s), live_c (2.2 s) | client got the TRAP and BURST; client got the BIND |
| uryu_sprenger_f16 | none (perf) | F16: mean fx time <= 4 ms; mean >= 60 FPS; 1 percent low >= 45 FPS (three bound zombies, explosion loop, 400 frames) |

---

## 5. UNVERIFIED in game

Nothing of U-FX 4 was run in the game. No gradle run was made for this note. The commit message does not record a test run. Check these in game:

1. **Pentagon before the explosion.** The code draws an outline (five edges, flat bands, spokes with flechette trails), a rotating six-point star (STAR6) at the hub from 300 ms, and the six-point bind flare at 800 ms. It does NOT draw the Zeichen (pentagram): `ZEICHEN_STAR` is used only for the explosion flare (UryuSprengerFx.java:536). So there is a star-shaped hub before the explosion, but not the pentagram. Check that the hub does not read as the Zeichen.
2. **Dagger heights vs flakes.** Daggers fall from 6 blocks (`DAGGER_HEIGHT`). Landing flakes spawn at ground level (`cy + 0.2`, UryuSprengerFx.java:333), explosion flakes at hilt minus 0.4 (line 457), shatter flakes at 0.3 to 2.9 blocks (line 603). Check the flakes do not hang in the dagger path or float above the blades.
3. **Foot sigil is the Quincy cross** (`ZEICHEN_CROSS`, UryuBindFx.java:297), not the pentagram. The storyboard row 2.8 still says `zeichen_star`: this commit did not update the storyboard.
4. **Top view.** The scene is at 13 blocks (code comment), the class javadoc says 14, and the storyboard acceptance says 20 blocks. Check which one matches the framing wanted.
5. **Anticipation ramp**: 40 ms in code (`ANTICIPATION_RAMP`), 0.15 s in the storyboard row 1.75.
6. **Landing sound timing**: code plays the landing sound at `landAt(i)` (220 to 420 ms, UryuSprengerFx.java:292), the storyboard row 0.00 has it at 0.0 to 0.20 s with pitch 1.6 (code: 1.4 + 0.08 i, volume 0.35).
7. **Early look**: storyboard "same from t 1.75", harness fires early at 1200 ms, server fires early on a Focus 3 hit. Only the harness timing is tested here.
8. **Bind visuals on mobs**: rings and sigil size for different entity sizes, and the "three nearest full, rest sigil only" rule with more than three bound.
9. **Storyboard acceptance not covered by a check**: "no frame with the star only" (no harness check), the FPS numbers (not measured), vanilla sound ids (checked only by `soundTablesAreVanillaIdsWithSaneValues`).
10. **Focus**: the interval is 14 ticks per Focus stack outside Blut Arterie (`FOCUS_INTERVAL`), so full Focus takes 3 x 14 = 42 ticks = 2.1 s. Inside Blut Arterie it is 10 ticks per stack (`FOCUS_INTERVAL_ARTERIE`), so 30 ticks = 1.5 s. Focus resets if the player moves, sprints or is airborne (Focus.java:54).

---

## 6. Manual checklist for the user

Do these in game. Each line is one thing to look at.

1. Cast Sprenger with key H (the middle of Z/H/B, `SLOTS[1]`) with Blut Arterie on, on a mob (a zombie) 8 blocks ahead on the floor.
   - 0 to 420 ms: five daggers fall from above, one after another, each lands with a flash. None hangs in the air after it lands.
   - 300 to 375 ms: spokes run from the centre to the daggers.
   - 550 to 800 ms: the outline draws edge by edge, the floor fills blue.
2. Bind at 800 ms, on the mob: a Quincy cross (not a pentagram) under the feet, three rings around the legs, five chains from the dagger beads to the chest, the fence rising, one ring wave from the hub.
3. Same bind on another player in the pentagon: the cross under the feet and, if there are three or fewer bound, the rings and chains.
4. About 1200 ms: take a screenshot of the bound target with the visuals.
5. 1750 to 2000 ms: the scene dims, then at 2000 ms the explosion: the column, the Zeichen star (pentagram) on the ground, a white edge flash, flakes. The bound target is free afterwards and the chains are gone.
6. Early detonation: stand still until Focus 3 shows (2.1 s outside Blut Arterie, 1.5 s inside it), then shoot the pentagon outline with the fully focused arrow. Check: white core, extra diamond star, bigger Zeichen flare, the damage is 13.5 (check the health bar of a mob).
7. Damage break: while the target is bound, hit it. Check: chains snap, flakes burst, glass sound, rings shatter, sigil gone within a fraction of a second. A second bound target keeps its visuals until the explosion.
8. Optional: note the frame rate with three bound targets and the explosion, and compare with `uryu_sprenger_f16`.

---

Commit: `Uryu U-FX 4: results note`, with only this file staged.
