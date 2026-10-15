# Uryu poses (U-FX 11): results note

Status: built and committed on branch `uryu-poses`. **Nothing was run in the game.** No gradle, no client, no Python test run for this note. Everything below is read from the repository (HEAD b4a379d; the generator, JSON and sheets come from cdef879).

Ticks: 1 tick = 0.05 s. Angles in degrees.

## 1 What was built

Commits: `cdef879` step 1 (generator, bbmodel, JSON, sheets), `cf908b2` step 2 (pure core, CastGesture rows, shared hooks), `6989071` step 3 (client wiring), `7a2d8d2` (ability and string hand logic moved out of UryuBowMotion, with tests), `b4a379d` (coverage test).

Pure core, `mod/src/main/java/dev/minebleach/reiatsutest/core/combat/`:

| File | What it does |
|---|---|
| `UryuAnims.java` | Table of the 11 animations: file name, the CastGestures id that starts it, look/axis flags, start tick and event tick |
| `UryuPoseTrack.java` | Reads an `uryu_*.json` emote and samples it per tick with playerAnimator easing |
| `UryuBowFlow.java` | Pull/release/ability state machine (OFF, IDLE, DRAW, RELEASE, ABILITY), 0.10 s crossfade, 0.12 s fade in, 0.15 s fade out |
| `UryuTpPose.java` | Third person values per frame for head, torso, arms, legs: blend, camera pitch, mirror, breathing |
| `UryuPoseMath.java` | Euler and bow correction maths, breath row, mirror |
| `UryuGestures.java` | First person `CastGesture` rows: `uryu.draw`, `uryu.release`, and the 8 ability gestures |
| `UryuStringHand.java` | Presence and placement of the string hand (left arm) in first person |
| `CastGestures.java` | Maps effects 70 and 71 to the two release ids (shared, section 4) |
| `NewCharacterGestures.java` | Uryu placeholder thrusts replaced by `UryuGestures.addTo` (shared, section 4) |

Client, `mod/src/client/java/dev/minebleach/reiatsutest/client/model/` unless noted:

| File | What it does |
|---|---|
| `UryuBowMotion.java` | Driver: per-player state, third person sampling, first person mix, string hand render |
| `UryuTpLayer.java` | playerAnimator layer at priority 1550 that hands the `UryuTpPose` values to the model parts |
| `UryuBowPose.java` | Bow correction taken from UryuBowMotion while the pose layer drives the arm; old constant as fallback |
| `FpCast.java`, `FpMotion.java` | Hooks (section 4) |
| `client/ClientOptions.java` | `uryu.bowHand` option (section 4) |

Data and tools (cdef879):

| File | What it is |
|---|---|
| `tools/gen_uryu_poses.py` | Solver and writer: key tables, checks, .bbmodel, JSON export, contact sheets |
| `tools/test_gen_uryu_poses.py` | 25 Python tests (classes Rig, Export, Math) |
| `tools/test_bbmodel_to_playeranim.py` | One added test, `test_bow_prop_is_ignored_like_the_sword_prop` |
| `design/anim/uryu_poses.bbmodel` | Generated Blockbench model |
| `mod/src/main/resources/assets/reiatsu_test/player_animations/uryu_*.json` | 11 files: blut, draw, idle, licht_regen, release, release_z, seele, sprenger, sternenstaub, vollstandig, wings (the brief said 10; there are 11) |
| `blender/renders/uryu_poses/uryu_*_sheet.png` | 11 matplotlib contact sheets, one per animation (stick figures, no game) |

## 2 Pose table

**Representation (generator).** Keys are written in code as sparse tables per tick and solved per tick, then baked and simplified per axis into Blockbench keyframes. The exporter writes the game JSON.
- Torso: `lean` (forward positive) and `yaw` (chest turns to the model's left). Written as Blockbench `rx = -lean`.
- Head: `nod` (down positive), pitch only, written as `rx = -nod`. In game it is added to the camera pitch.
- Arms, three key kinds: `rel` = chest-relative Blockbench Euler (rx, ry, rz); `dir` = (azimuth, elevation) of the straight arm in the entity frame, written `dir(az, elev)`; `hand` = fist target in the root frame, written `hand(x, y, z)`, where the shoulder pivot slides up to 3.5 px.
- Right arm = bow arm (R). Left arm = string hand, free hand or blade (L).
- Legs: (rx, rz) per side.
- Duration = end tick x 0.05 s.

**2a Timing**

| Pose (file) | Storyboard | Third person: ticks (s) | First person gesture (s) |
|---|---|---|---|
| idle (P1) | P1 | 20 (1.0), loops | none; breath row only (code) |
| draw (P2) | P2 | 20 (1.0), held at tick 20 while the key is held | `uryu.draw` (1.0), sampled by draw time |
| release | P3 | 14 (0.7) | `uryu.release` (0.5) |
| release_z (Z) | 3.5 Heilig Pfeil | 10 (0.5) | `uryu.shikai.heilig_pfeil` (0.5) |
| sprenger (H) | 3.5 Sprenger | 52 (2.6) | `uryu.shikai.sprenger` (2.4) |
| seele (B) | 3.6 Seele Schneider (P4) | 26 (1.3) | `uryu.shikai.seele_schneider` (0.9) |
| blut | 3.7 Blut Arterie (P5) | 34 (1.7) | `uryu.shikai.release` (1.2) |
| vollstandig | 3.8 Vollstaendig (P6) | 40 (2.0) | `uryu.bankai.release` (2.2) |
| wings (Z) | 3.5 Wing barrage | 130 (6.5), loop "hold" | `uryu.bankai.wing_barrage` (6.4) |
| licht_regen (H) | 3.5 Licht Regen | 96 (4.8) | `uryu.bankai.licht_regen` (3.8) |
| sternenstaub (B) | 3.5 Sternenstaub | 60 (3.0) | `uryu.bankai.sternenstaub` (1.6) |

**2b Key angles as written in the generator** (torso as (lean, yaw); R/L as rel (rx, ry, rz) unless marked; `@t` = tick)

| Pose | Torso (lean, yaw) | R, bow arm | L, string / free / blade arm | Head nod | Legs (rx, rz) |
|---|---|---|---|---|---|
| idle | (0, 6) all ticks | rel (18, 4, 6) | not keyed (vanilla walk swing) | none | none |
| draw | (0,6)@0, (0,30)@4, (0,50)@10, (-2,55)@20 | (18,4,6)@0, (75,-22,2)@4, dir(0, 8)@8 to 20 | (6,0,-4)@0, (55,-35,-4)@4, (95,-62,6)@10, hand(1.8, 24.2, -5.0) @14 to 20 | none | R (10,8) and L (-10,-8) @20 |
| release | (-2,55)@0, (-2,49)@1 to 8, (0,6)@14 | dir(0,8)@0, dir(0,12)@1, dir(0,9)@3 to 8, (18,4,6)@14 | hand(1.8,24.2,-5.0)@0, hand(4.5,25.2,1.0)@1, hand(5.2,24.6,2.2)@3 to 8, (6,0,-4)@14 | none | (10,8)@0 to 8, (0,0)@14 |
| release_z (Z) | (0,24)@0, (0,50)@2, (-2,55)@3, (-2,49)@4 to 6, (0,6)@10 | (60,-12,3)@0, dir(0,8)@2 to 3, dir(0,12)@4, dir(0,9)@6, (18,4,6)@10 | (45,-28,-4)@0, (95,-62,6)@2, hand anchor@3, hand flick(4.5,25.2,1.0)@4, hand follow(5.2,24.6,2.2)@6, (6,0,-4)@10 | none | (3,3)@0, (10,8)@3 to 6, (0,0)@10 |
| sprenger (H) | (0,6)@0, (4,12)@2, yaw 12 (right throws) / -2 (left) at ticks 3, 7, 11, 15, 19, (4,6)@24 to 40, (0,6)@52 | five throws R L R L R, 4 ticks each: wind (80,-10,4)@t+1, top (150,0,10)@t+2, follow (60,-6,4)@t+4; guard (35,-30,0)@24 to 40; idle@52 | mirrored throws (wind (80,10,-4), top (150,0,-10), follow (60,6,-4)); the off arm rests at its idle +20 on pitch at t+2; guard (35,-45,0)@24 to 40; idle@52 | none | none |
| seele (B) | (0,6)@0, (8,-20)@1, (10,30)@3, (8,-22)@8, (16,0)@13 to 18, (0,6)@26 | rel (5,8,10)@1 to 18, idle@26 | dir(-42,22)@1, dir(38,6)@3, dir(-38,6)@8, dir(0,82)@11, dir(0,-48)@13 to 18, idle@26 | 0@0, 6@1 to 18, 0@26 | R (-14,0)@1 to 3, (-18,0)@13 to 18; L mirrored |
| blut (shikai release) | (0,6)@0, (10,0)@6 to 8, (-8,0)@12 to 14 (OUTBACK), (0,0)@24, (0,6)@34 | (78,50,-8)@6 to 8, (15,-10,58)@12 to 14, (4,0,14)@24, idle@34 | (98,-50,8)@6 to 8, (15,10,-58)@12 to 14, (4,0,-14)@24, idle@34 | 0@0, 8@6 to 8, -10@12 to 14, 0@24, 0@34 | (3,0)@6 to 8, (0,0)@12 |
| vollstandig (bankai release) | (0,6)@0, (12,0)@4 to 10, (-12,0)@13 to 20 (OUTBACK), (-2,0)@30, (0,6)@40 | (70,45,-6)@4 to 10, (25,-5,95)@13 to 20, (8,0,30)@30, (6,0,8)@40 | (84,-45,6)@4 to 10, (25,5,-95)@13 to 20, (8,0,-30)@30, (6,0,-8)@40 | 0@0, 10@4 to 10, -14@13 to 20, -2@30, 0@40 | R and L (14,±4)@4 to 10, (0,0)@13; body lift +2 px at 13 to 30 |
| wings (Z) | (0,6)@0, (-4,0)@8, volleys from tick 12 every 8 ticks (14 volleys): (-6,0)@t, (-4,0)@t+4, (-4,0)@124, (0,6)@130 | (10,-4,50)@8 to 122, idle@130 | (10,4,-50)@8 to 122, idle@130 | none | none |
| licht_regen (H) | (0,6)@0, (-8,20)@12, (-9,16)@13 to 72, (0,6)@84 | idle@0, (90,-20,0)@6, dir(-6,62)@12, dir(-6,65)@13, dir(-6,63)@16 to 72, idle@84 | idle@0, (80,-45,0)@6, hand(1.8,24.6,-4.6)@8 to 12, hand(4.8,25.6,0.8)@13 to 72, idle@84 | 0@0, -22@12 to 72, 0@84 | R and L (±6)@12 to 72 |
| sternenstaub (B) | (0,6)@0, (-2,55)@12, (-4,60)@16, (-4,46)@17 to 24, (-2,30)@48, (0,6)@60 | idle@0, (75,-22,2)@6, dir(0,8)@12, dir(0,12)@16, dir(0,20)@17, dir(0,14)@20 to 24, idle@60 | idle@0, (55,-35,-4)@6, (95,-62,6)@9, hand anchor(1.8,24.2,-5.0)@12 to 16, hand(6.4,25.6,4.5)@17, hand(6.6,24.6,5.0)@20 to 24, idle@60 | none | R (10,8), L (-10,-8) @12 to 24 |

Notes from the generator: the arm of the release-type poses is solved in the entity frame (`dir`), so the bow stays aimed when the chest turns. Torso yaw is limited to 60 (checked by the generator). The generator's own checks (hands on the string anchor, no pops, torso yaw, first and last key = idle) are in `check()` and `checks()`; they were not re-run for this note.

## 3 Which gesture or animation is triggered by what

Keys as in `ReiatsuKeys` and the balance comments in `BalanceConfig.java`: J = draw key, R = release key, G = bankai, Z/H/B = ability 1/2/3. Effect ids from `EffectIds.java` and `AbilityId.java`.

| Trigger | State or effect | CastGestures id | Third person animation (start tick, event tick) | First person gesture |
|---|---|---|---|---|
| Right click held with the bow drawn | DRAW | none | `draw`, tick = use time in ticks, held at tick 20 | `uryu.draw`, sampled by draw time |
| Right click let go after at least 1 tick (0.05 s) | RELEASE | none | `release` | `uryu.release` |
| Right click let go within 1 tick | back to IDLE, no release | none | `idle` | none |
| Bow in hand, nothing else | IDLE | none | `idle` | breath row |
| Z shikai (effect 72) | ability | `uryu.shikai.heilig_pfeil` | `release_z` (0, event 3) | `uryu.shikai.heilig_pfeil` |
| H shikai (effect 73) | ability | `uryu.shikai.sprenger` | `sprenger` (0) | `uryu.shikai.sprenger` |
| B shikai (effect 74) | ability | `uryu.shikai.seele_schneider` | `seele` (0, event 3) | `uryu.shikai.seele_schneider` |
| Shikai release event (effect 70) | ability | `uryu.shikai.release` | `blut` (0, event 12) | `uryu.shikai.release` |
| Z bankai (effect 75) | ability | `uryu.bankai.wing_barrage` | `wings` (0, event 8) | `uryu.bankai.wing_barrage` |
| H bankai (effect 76), after cast start (effect 78) | ability | `uryu.bankai.licht_regen` | `licht_regen` (start 8, event 12) | `uryu.bankai.licht_regen` |
| B bankai (effect 77), after cast start (effect 78) | ability | `uryu.bankai.sternenstaub` | `sternenstaub` (start 10, event 16) | `uryu.bankai.sternenstaub` |
| Bankai release event (effect 71) | ability | `uryu.bankai.release` | `vollstandig` (0, event 13) | `uryu.bankai.release` |

- Effect 78 (`URYU_CAST_START`) is the key press of H and B bankai. Its `params[3]` names the ability effect. The ability event that follows does not restart the animation within 3 s (`CAST_START_WINDOW`). Without effect 78 the animation starts at its start tick.
- Look and axis flags (`UryuAnims`): look = 1 (arms follow camera pitch) for draw, release, release_z, sternenstaub; axis = 1 (bow axis follows the arm) for draw, release, release_z, licht_regen, sternenstaub.
- Third and first person durations differ: seele 1.3 s vs 0.9 s, licht_regen 4.8 s vs 3.8 s, wings 6.5 s vs 6.4 s, vollstandig 2.0 s vs 2.2 s.

## 4 Shared-file edits (file:line, at HEAD b4a379d)

| File | Lines | Change | Commit |
|---|---|---|---|
| `mod/src/main/java/dev/minebleach/reiatsutest/core/combat/CastGestures.java` | 55-58 | `idForEffect`: 70 maps to `UryuAnims.RELEASE_SHIKAI`, 71 to `UryuAnims.RELEASE_BANKAI` | cf908b2 |
| `mod/src/main/java/dev/minebleach/reiatsutest/core/combat/NewCharacterGestures.java` | 6 (javadoc), 26 | Six `thrust(m, "uryu...")` placeholders removed; `UryuGestures.addTo(m)` added | cf908b2 |
| `mod/src/client/java/dev/minebleach/reiatsutest/client/ClientOptions.java` | 29-34 (field `uryuBowHand`), 87-89 (read `uryu.bowHand`), 101 (default written) | New option `uryu.bowHand` (right default, left logs a hint once) | 6989071 |
| `mod/src/client/java/dev/minebleach/reiatsutest/client/model/FpCast.java` | 94 (`init`), 259 (`onEffectEvent`), 304 (`clear`), 379 (`mixFirstPerson`), 448-452 (`runId`) | Hooks into the Uryu driver | 6989071 |
| `mod/src/client/java/dev/minebleach/reiatsutest/client/model/FpMotion.java` | 618 (inside `renderLeftGuard`, defined at 615) | `renderStringHand` call | 6989071 |
| `mod/src/client/java/dev/minebleach/reiatsutest/client/model/UryuBowPose.java` | 19-21 (javadoc), 48-56 (`push`) | Uses `UryuBowMotion.bowCorrection` when the pose layer drives the arm; old correction is the fallback | 6989071 |

Shared test edits (cf908b2): `CastGestureTest.java` skips the draw gesture in the hold check and skips `startsFromPose` gestures; `FpClampTest.java` skips `UryuGestures` ids (no two handed stance).

## 5 Tests added (names; none were run for this note)

Java, `mod/src/test/java/dev/minebleach/reiatsutest/core/combat/`:
- `UryuAnimsTest`: everyAnimationHasItsFileAndTheEndTickOfTheGenerator, everyAbilityOfUryuStartsItsAnimation, theTwoReleaseEventsStartTheirAnimations, theEventTickIsInsideTheAnimationAndTheStartTickBeforeIt, theAimedAnimationsFollowTheCameraAndTheBow
- `UryuBowFlowTest`: offUntilTheBowIsHeldThenFadesInAndOut, theDrawTickIsTheUseTimeAndHoldsAtFullDraw, releasingPlaysTheReleaseFromTheFullDrawAndReturnsToIdle, aShortDrawIsCancelledWithoutARelease, aShorterDrawReleasesFromThePoseItHad, anAbilityGestureRunsItsAnimationThenGoesBackToIdle, anAbilityThatStartsLateSkipsItsRaise, theKeyHeldDuringAnAbilityDrawsAfterItEnds, triggerIsIgnoredWhileTheBowIsNotHeld, switchingTheItemAwayInTheMiddleOfADrawResetsEverything, theCrossfadeIsASmoothstepOfTheBlendTime
- `UryuBowFlowAbilityTest`: anAbilityWithoutACastStartStartsAtItsStartTick, theCastStartStartsFromTheFirstTickAndTheEventDoesNotRestartIt, aStaleCastStartDoesNotSwallowTheEvent, anotherAbilityEventIsNotSwallowedByTheCastStart, anUnknownAbilityIsIgnored, preEventRunsFromZeroToOneOnlyForAbilitiesWithACastStart
- `UryuGesturesTest`: theGameUsesTheUryuRowsNotTheOldThrust, ginKeepsHisPlaceholderRows, everyRowStaysInReachAndInTheEaseCodes, everyGestureThatEndsReturnsToTheRestHold, theDrawRowsAreTheStoryboardNumbersAndHoldAtFullDraw, theStringHandShowsOnlyWhereTheStoryboardGivesTheFreeHandARow, theReleaseIsTheDrawRowFollowedByTheKickAndTheLeavingHand, theTwoReleasesRiseFromBelowAndBurstAtTheKeyTime, theRainAndTheStarGesturesStartAtTheirEvent, theWingBarrageHoldsForSixSecondsWithAVibration
- `UryuGestureCoverageTest`: everyUryuGestureRowHasAnAnimationOrIsTheDrawStateMachine
- `UryuPoseMathTest`: eulerRoundTripsForTheAnglesOfThePoses, nearestPicksTheBranchNextToThePreviousValue, lerpIsAPlainBlendWhenTheBranchesAgree, anArmPointsAlongItsPitch, theLookPitchTurnsTheAimedArmAboutTheShoulderLine, theAxisFrameTakesTheForwardAxisToTheArm, theCorrectionIsNothingInTheIdleHoldAndKeepsTheBowOrientationElsewhere, anAimedBowIsLevelWithItsArrowAxisAlongTheArm, theFirstSolutionOfTheDrawnBowIsTheLevelBowToo, mirrorsAreTheirOwnInverse
- `UryuPoseTrackTest`: parsesNamesTicksAndMergesTheOneAxisMoves, interpolatesHoldsAndLeavesUnkeyedAxesOpen, positionsAreDeltasFromTheVanillaPivot, easingNamesMatchTheirFormulas, theIdleFileIsTheDrawnBowPoseP1, everyShippedAnimationEndsNearTheIdleBowArm
- `UryuStringHandTest`: theHandIsWantedWhileDrawingAndInTheFirstTicksOfTheRelease, theRaiseBeforeAnEventAndAGestureWithAStringHandWantTheHand, noCandidateOrNoSampleMeansNoHand, presenceComesInFasterThanItLeavesAndStaysInRange, atFullPresenceTheFistSitsAtTheRowPlaceAndComesFromBelowBefore, theLeftHanderIsTheMirrorImage, theHandRisesWithTheEquipAnimation, theDrawAndReleaseRowsAreTheOnesTheStateMachineSamples, theBreathIsASlowSineAtTheStoryboardAmplitude, reduceMotionHalvesTheBreath
- `UryuTpPoseTest`: aFadedOutPoseChangesNothing, keyedPartsTakeTheAnimationValueAndUnkeyedPartsKeepTheIncomingOne, theHeadNodIsAddedToTheCameraPitch, theWeightBlendsTowardsTheIncomingValue, aCrossfadeBlendsTheTwoAnimations, anAimedPoseFollowsTheCameraPitchAndAFixedOneDoesNot, aLeftHanderGetsTheMirrorImage, shouldersFollowTheTurnedChest, theBowAxisFollowsTheArmOnlyInTheAimedPoses, theBowArmOfTheIdleIsTheVanillaHeldItemArm, breathingAndTremorAreSmallAndSwitchable, theRaisedBowOfTheRainShakesOnlyWhileItIsUp

Python (step 1): `tools/test_gen_uryu_poses.py`, classes Rig (12 tests, e.g. test_full_draw_string_hand_is_on_the_cheek_anchor, test_crossed_wrists_are_stacked_not_on_top_of_each_other), Export (10 tests, e.g. test_every_animation_is_exported_with_its_end_tick, test_the_repo_exporter_accepts_every_animation), Math (3 tests, e.g. test_hand_key_reaches_its_target_with_the_shoulder_shift).

## 6 UNVERIFIED in game

Nothing here has been seen in the game. Unit tests cover the pure maths only. Look at:
- **Bow hand**: the bow stays upright and aimed in the moving hand (UryuBowPose correction) in idle, draw, release and every ability pose. Check the left-handed mirror. `uryu.bowHand = left` only logs a hint; the real left-hand mirror comes from the vanilla Main Hand: Left option.
- **String hand anchor**: the left fist should sit on the right cheek at full draw (anchor 1.8, 24.2, -5.0 in the root frame), leave back on release, and fade in and out (`UryuStringHand`, IN 0.18 s, OUT 0.22 s). Check that it is not drawn for other items.
- **Crossed-arm stack**: the wrists in blut (ticks 6 to 14) and vollstandig (ticks 4 to 10) must stack in front of the chest, not intersect. The Python test checks the numbers only, not the render.
- **Third person layer**: `UryuTpLayer` (priority 1550, between TpPoseLayer 1500 and TpCastLayer 1600; Better Combat 2000 stays above). Check the arms, the head nod on top of the camera pitch, the legs, the body lift of vollstandig, and that no other layer overrides the pose.
- **First person rows**: all values in `UryuGestures` are start values (javadoc says UNVERIFIED, tune with `pose_override.json` section `fp_cast`).
- **Cast start (effect 78)**: Licht Regen and Sternenstaub rely on it. Without it (old server) they start at their start tick.
- **Sync of first and third person** durations (section 3).
- Contact sheets: only `uryu_draw_sheet.png` was opened for this note. Its panel titles run from yaw 6 / lean 0 at tick 0 to yaw 55 / lean -2 at tick 20, as the generator's draw keys give. The other 10 sheets were not reviewed.

## 7 Manual checklist for the user

1. Get the item `uryu_cross` (`ModItems.URYU_CROSS`) in a dev world, by your usual method.
2. Press **J** to draw the bow (the draw key).
3. Hold **right click** to pull. Look at the pose in first person, then press **F5** and look at it in third person. Expect: the bow arm leads, the string hand reaches the right cheek at full draw, the torso turns to about 55 degrees yaw.
4. Let go of **right click**. Check the shot pose and the string hand flicking back, then the return to idle.
5. **Z**, **H**, **B** (shikai): Heilig Pfeil, Sprenger, Seele Schneider. Check first and third person.
6. **G** for bankai, then **Z**, **H**, **B** (bankai): Wing Barrage, Licht Regen (raised bow, rain), Sternenstaub (raise and draw). Check the release poses too: Blut Arterie and Vollstaendig come from the release events 70 and 71. Confirm which key sends them (R is assumed, not checked).
7. Option `uryu.bowHand` in `config/reiatsu_test_client.json`: `right` (default) or `left`. For a left hand bow, also set Options > Skin Customization > Main Hand: Left, which is what actually mirrors the pose.
8. Dev switches in `pose_override.json`, section `uryu_pose`: `enabled` (0 turns everything off), `tp`, `fp` (0 or 1), `breath` (amplitude, default 1).
