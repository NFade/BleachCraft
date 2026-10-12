# fp2: first person animations, audit and rebuild (2026-10-12)

Branch `fp2` (worktree `.claude/worktrees/fp2`, from main 80b855d). Owner of `client/model/*`, `core/combat/*` poses, `pose_override.json`, the pose harness.
Everything below marked CHECKED was seen in a real game frame (harness screenshots), the rest is stated as UNVERIFIED.

## 1. Audit (game frames, before any change)

Method: `gradlew runPhase6 -Phold=console` plus the new console commands `fp ...` (`client/dev/Phase6Fp.java`, driven by `tools/fp_console.py`).
The first person clock of the animations (`client/model/FpClock`) can be switched to a fixed step clock, so a pose is captured at an exact time
of an animation (contact sheets, same layout every run). 1280x720, GUI scale 3, FOV 70, flat world, noon, HUD on. Sheets: `blender/renders/fp2/audit_*.png`.

Finding 0, the root cause (not a number problem): the arm is not placed by where the fist has to be. It is the vanilla arm chain turned about odd pivots
(`arm.shift/rot`, `roll`, `turnSealedLeftArm`, per call-site constants). In every pose that leaves the vanilla hold, the forearm becomes a plank lying across the
screen or a cube floating in the air, and the sword pivot (grip) keeps the blade 2.35 blocks long pointing out of the picture.

| State | What the frames show (CHECKED) | Verdict |
|---|---|---|
| SEALED idle (both) | scabbard held horizontally in both hands, black scabbard leaves the picture on the left (long pole), right fist is a big cube, hilt visible | readable, crude |
| SEALED block (RMB) | scabbard rises as a huge diagonal pole from the top left corner, only one fist box in the lower middle, left hand not seen | BROKEN (the screenshot of the user) |
| SEALED draw | tip pulled out to the left along the sori arc, hands visible, then the sword swings upright | good motion, final frames inherit the bad stance |
| BASE/SHIKAI/BANKAI idle (stance, `fp_two`) | blade upright leaving the top edge, right arm rotated into a horizontal plank across the lower right (`arm.rot` -15 30 35), tsuba hidden behind it, left hand barely visible | BROKEN |
| drawn block (RMB) | blade a thin diagonal line from the lower right to the upper left, one cube at the bottom, left hand missing, big pop when the block ends (arm box flips) | BROKEN |
| block ease in / out | `block_a` .. `unblock_b`: the arm box changes orientation in 2 frames (Euler interpolation of absolute poses, forearm turn applied apart from the sword) | pops |
| strike (Better Combat swing, live sampler) | blade leaves the picture or only a streak is visible, arm plank flips, returns to the plank stance | BROKEN |
| release / shikai ability gestures | forearm planks flail, left fist leaves the picture or hangs as a cube in mid air (Rukia Tsukishiro, Byakuya barrier), effect is fine | crude |
| bankai, Byakuya free hands | two forearm planks from below, symmetric, no hand shape | acceptable, crude |
| kido | left fist cube raised to the middle of the screen, right plank | crude |
| shunpo | no pose change at all (stance), only the FX | fine, nothing to fix |
| sheathe | reverse of the draw, good | ok |
| third person block (tp_block) | not re-checked in game here (see section 5) | UNVERIFIED |
| HUD text clipped at the top left of the user's screenshot | not an anchor bug: the hotbar in that screenshot is not centred (centre at 928 of 1961 px), so the image is cropped on the left by about 100 px; `ReiatsuHud.plateOrigin` anchors at x 6 | no change |

Also found by the audit: the harness hides the hands when `hudHidden` is set (vanilla hides the hand pass with F1), so the harness keeps the HUD on.
