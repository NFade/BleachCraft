# Katana cut animation rules (16 ticks, Minecraft player rig)

Scope: one horizontal/diagonal two-handed katana cut, 16 ticks (0.8 s at 20 tps), on a rig with head, torso, rightArm, leftArm, rightLeg, leftLeg (straight limbs, no pelvis) plus the held item.

Labels used below: **[S]** = taken from a source (paraphrased), **[S*]** = source gives a related number I converted or adapted, **[I]** = my inference or engineering judgement (not a measured fact).

Honest caveat: I found NO published measured millisecond lags between hips, torso, arm and hand for a sword cut (baseball/pitching sources only give order and peak speeds). All frame-lag numbers in section 2 are inference, tuned to the 50 ms tick size. No kendo/iaido source gave hip/torso angles either; torso and head angles are inference.

## Sources

| # | URL | One-line paraphrase |
|---|-----|---------------------|
| 1 | https://github.com/ZsoltMolnarrr/BetterCombat/blob/1.20.4/animations/README.md | Official Better Combat animation guide: aim for 20-tick attacks; keyframes at 50% (ease in-out) and 60% (ease in, the "tipping point"), 60-70% hit end (ease out), 70-100% return (ease in-out); tipping point should match the attack's upswing value; do not touch tick 0 or remove/move locked parts. |
| 2 | https://mocaponline.com/blogs/mocap-news/sword-melee-animation-guide | Game sword guide: light coil 0.1-0.2 s, peak blade speed at 60-70% of the arc, horizontal follow-through 30-60 deg past centre, trunk rotation drives two-handed swings, hit stop 1-4 frames at 30 fps, recovery must match gameplay recovery, common mistakes list. |
| 3 | https://kendo.readthedocs.io/en/latest/basic/suburi.html | Kendo suburi: cut is elliptical not a chop; raise to about 45 deg (shoulder's natural limit) or you must regrip; drive hips; wrist flips the shinai out at the strike; tip must not drop below horizontal; relax arms right after strike so the blade rebounds. |
| 4 | https://www.kendo-guide.com/ki-ken-tai.html | Kendo ki-ken-tai: front-foot landing and the strike should be simultaneous (split sound = out of sync); right hand stays relaxed until contact then tightens; do not pull the blade back after the cut. |
| 5 | https://slynyrd.com/blog/2018/9/8/pixelblog-9-melee-attacks | Pixel-art melee breakdown: anticipation / action / impact / follow-through / recovery, held follow-through frame reads as weight, optional impact hold, smear frames few and fast, small overshoot after recovery (pendulum settling). |
| 6 | https://aztez.com/blog/2010/12/19/characters-adhering-to-input/ | Bayonetta example: about 4-6 frames (60 fps) of player anticipation; even about 10 frames is long enough to feel like lag/cancel problems. |
| 7 | https://www.gameanim.com/?p=115 | Game Anim: enemy anticipation long (readable), player anticipation short (responsive); set a baseline anticipation early and judge by feel, not polish. |
| 8 | https://uhiyama-lab.com/en/notes/unity/unity-game-feel-hit-feedback/ | Hit stop tiers: weak 0-30 ms, strong 50-80 ms, critical 100-150 ms; apply only to first/last hit of multi-hit moves; camera shake only on finishers. |
| 9 | https://critpoints.net/2017/05/17/hitstophitfreezehitlaghitpausehitshit/ | Hitstop makes a hit look like it needed force to push through; Dark Souls 2 lacking it makes hits feel weak; SF2 used about 10 frames. |
| 10 | https://www.topvelocity.net/?p=247833 and https://rocklandpeakperformance.com/3-questions-to-ask-when-analyzing-a-kinematic-sequence | Kinematic sequence: pelvis, then torso, then lead arm, then hands; each earlier segment decelerates while the next keeps accelerating (whip analogy). Gives order only, no ms. |
| 11 | https://www.scitepress.org/Papers/2024/124614/124614.pdf (found via search; abstract-level) and swing-sequencing abstract (ACSM 2024, via search result) | Batting-swing peak angular speeds rise along the chain: hip about 720, torso about 930, arm about 1160, hand about 1520 deg/s; ideal order seen in only about 39% of players. Used for the "each link faster than the last" rule. |
| 12 | https://www.academieduello.com/?p=4434 (search snippet only; page body did not load) | Cut lands when the front foot lands; move blade first, let the foot catch up, step at the last moment; power from hip/torso turn, shoulders/arms follow; feet/hips/core/hands must act as one motion. |
| 13 | https://www.martialtalk.com/threads/going-up-on-toes-when-swinging-katana.135299 (search snippet, anecdotal) | Heels rise as the sword goes up and drop as it comes down; sliding (suri-ashi) steps keep the centre of gravity steady. Anecdotal, treated as hypothesis. |
| 14 | https://polycount.com/discussion/comment/2507079 (search snippet; page returned 403) | Animation critique: follow-through sells weight when anticipation is short; viewers watch the blade-tip arc, so push the tip arc; film a bat swing as reference; strike should land as the foot lands; vary fast moves with slower follow-through. |
| 15 | https://playbooks.com/skills/dylantarre/animation-principles/follow-through-overlapping and https://blog.cg-wire.com/arc-animation-principle (search snippets) | Uniform spacing reads as mechanical; use eased spacing and curved arcs; root (hips/torso) moves and stops first, extremities lag and overshoot. No fixed frame counts given. |
| 16 | https://blog.cgcookie.com/?p=21210 and https://blenderartists.org/t/animation-on-twos/686600 (search snippets) | Animating on twos halves drawings; fast action is often put back on ones; bad arcs and awkward holds are more visible at low rates. |
| 17 | https://epicfight-docs.readthedocs.io/Guides/page1/ | Epic Fight animates in Blender with plain keyframes; no combat-timing numbers (checked, none found). |

## Proposed 16-tick timeline (summary)

Percentages follow the Better Combat split (source 1) adapted to 16 ticks; tick numbers are inference.

| Phase | Ticks | Duration | Share | Notes |
|-------|-------|----------|-------|-------|
| Anticipation (wind-up) | 0-4 | 4 t = 0.20 s | 25% | Coil: weight back, torso wound, blade raised |
| Strike (acceleration through the arc) | 4-9 | 5 t = 0.25 s | 31% | Ease-in long, fast at the end |
| Contact / hold | 9-10 | 1 t = 0.05 s | 6% | Peak pose, 1 tick freeze (hit pause) |
| Follow-through | 10-13 | 3 t = 0.15 s | 19% | Blade overshoots, decelerates fast |
| Recovery | 13-16 | 3 t = 0.15 s | 19% | Ease in-out back to idle, last tick = idle pose |

Check against Better Combat percentages: tick 9.6 = 60% (tipping point), 11.2 = 70% (hit end). So contact at tick 9-10 sits on the tipping point. [S*/I]

## Numbered rules

### Phases and durations

1. **Anticipation is short for a player attack: 3-4 ticks (0.15-0.20 s), at most 5.** Sources: light coil 0.1-0.2 s (2); Bayonetta about 4-6 frames at 60 fps, and about 10 frames already feels laggy (6); player anticipation short, enemy long (7). Because the cut is "only" 0.8 s total, 4 ticks (25%) is the upper end; use 3 if it feels sluggish. [S* for range; the 4 ticks is I]

2. **Strike is 4-5 ticks (0.20-0.25 s), contact at about 55-60% of the clip (tick 9-10 of 16).** Better Combat puts its tipping point at 60% of the clip and wants it to match the attack's upswing value (1). Mocap guide: light active phase 0.2-0.4 s (2). Gameplay hit detection should be fired at the same tick as the visual contact (a visible blade that hits late or early feels wrong). [S]

3. **Contact hold is 1 tick (50 ms), 2 only for a finisher.** Hit stop 1-4 frames at 30 fps (2) = 33-133 ms; tier table weak 0-30 / strong 50-80 / critical 100-150 ms (8). One tick = 50 ms is the "strong" tier and the smallest unit you can do on a 20 tps clock. Freeze only the attacker's pose (and the target), do not hold the whole game. [S* for range, I for 1 tick]

4. **Follow-through 3 ticks, recovery 3 ticks (about 19% each); total follow-through plus recovery about 35-40% of the clip.** Better Combat: hit end 60-70% eased out, recovery 70-100% ease in-out (1). Mocap guide: stopping the clip at impact with no follow-through is the classic mistake (2). Pixel breakdown: hold the follow-through pose a bit longer than the others to sell weight (5). [S* / I]

5. **Easing per phase (Better Combat-style):** wind-up ease in-out; strike ease-in (accelerating, no ease-out before contact); contact-to-hit-end ease-out; recovery ease in-out. [S: 1]

6. **Do not edit tick 0 and make the last tick equal to the idle pose** so the clip blends with the game's idle and the next combo hit. Better Combat explicitly tells animators not to edit the tick-0 keyframe and not to move locked parts (1). The last-tick match is inference. [S for tick 0, I for last tick]

### Kinetic chain: lead and lag

7. **Order of motion: legs/weight, then torso, then shoulders/arms, then hands/blade.** Everything supports this: pelvis, then torso, then arm, then hand (10); hip, torso, arm, hand peak speeds about 720, 930, 1160, 1520 deg/s, so every link ends faster than the previous (11); in sword cutting the hip/torso turn drives the shoulders and arms (12); in kendo, extension goes shoulders, elbows, wrists, hand grip (3). [S]

8. **Lag between links: 1 tick (50 ms) per link, about 3 ticks from first mover to blade.** No source gives ms values. At 20 tps a lag below 1 tick is invisible and above 2 ticks starts to look like a whip-crack. Suggested keys for the strike: legs/torso start at tick 4, rightArm at tick 5, leftArm at tick 5 (same time or +0 to the right arm, since both hold the same grip), item/wrist roll at tick 6, with all of them arriving at the contact pose between ticks 9 and 10 (arrivals differ by 0-1 tick, blade last). [I]

9. **Peaks, not starts, must be staggered.** Each segment should decelerate while the next accelerates (10). So in the strike: torso is slowing in the last 1-2 ticks before contact while the arms and blade are still at maximum speed. In practice: give the torso an ease-out on its last 1.5 ticks and the arm/item no ease-out until the contact pose. [S for principle, I for ticks]

10. **Do not sync everything on the same keyframe tick.** Same-tick keys on all parts is the typical source of a robotic look; the offset (root stops first, extremities drag and overshoot) is what reads as weight (15). [S]

### Legs

11. **Cut lands as the front foot lands.** Kendo: foot landing and strike are simultaneous (4); fencing/sword teaching: blade moves first, foot catches up, both arrive together (12); critique advice: make the strike land as the foot comes down (14). So the front foot (for a right-handed katana grip: right foot forward, left foot back is the normal kendo/iai step) should be airborne/sliding in ticks 5-8 and planted exactly at tick 9-10. [S]

12. **Step size on this rig: front leg about +20 to +30 deg forward pitch, rear leg about -15 to -25 deg back, held through contact.** The leg is 12 px (0.75 block) long, so 25 deg is about 5 px (0.3 block) of reach, which looks like a believable half step without knees. Larger angles look like a lunge/stride and clip through the floor in a rig with no knee bend. [I]

13. **Fake the missing knee bend/hip with a small root drop.** The rig has straight legs and no hips, so lower the whole body 1-2 px (or tilt both legs outward slightly) at tick 4 (loaded) and at tick 9-10 (landing), and return in recovery. Do this only if the animation system lets you offset the root; otherwise skip it. Katana/iai teaching keeps the centre of gravity steady with a sliding step (13). [I, with S support from 13]

14. **Weight shift in the wind-up: rear leg loaded, front leg light, then transfer forward during the strike.** Kendo suburi and sword teaching both say to drive the hips into the cut (3, 12). On this rig show it as: rear leg angle increasing by 5-10 deg in ticks 0-4 and the torso pivoting back; in ticks 4-9 the front leg swings forward and the torso comes with it. [I with S support]

15. **Feet should lead the hands by roughly 0-1 tick at the start, not by a lot.** Very early leg motion looks like walking, late leg motion looks like arms only (stiff upper body). Start the leg motion on the same tick as the torso unwinding (tick 4) or 1 tick before (tick 3). [I]

16. **Recovery: rear foot follows by 1-2 ticks after contact** (kendo: back foot follows the front), then both legs ease back to idle in ticks 13-16. [S*: 3/4 imply the follower foot, I for ticks]

### Torso and head

17. **Torso twist: wind up about 25-40 deg away from the target, release through to about 30-50 deg past centre at follow-through.** Source says the horizontal follow-through goes 30-60 deg past centre and the body keeps rotating a little after the arm stops (2). The wind-up number is inference. Total twist 60-90 deg is realistic; above about 100 deg the single-part torso looks like a spinning top (the rig has only one torso piece). [S* for follow-through, I for wind-up]

18. **Lean: back lean 5-10 deg in the wind-up, forward lean 10-15 deg at contact, ease back to upright in recovery.** Anything above about 20 deg looks overacted on a rig this blocky. Pixel/combat sources only say "body coils like a spring" and weight shifts (search snippets, 5, 14); the angles are inference. [I]

19. **The torso is the last thing to stop turning among the root parts, but it stops before the blade** (follow-through order: legs plant, torso slows, arms slow, blade overshoots and settles). Mocap guide: the body keeps rotating slightly after the arm stops (2), kendo: arms relax right after the cut so the blade rebounds (3). Careful: these two sources disagree slightly on which stops last; I resolve it as "torso drift is slow and small, blade overshoot is fast and small". [S* / I]

20. **Head: keep it level and keep looking at the target; counter-rotate against the torso.** No source gives a number. Rule: head yaw = -(torso yaw) for about 60-80% of the torso yaw (so the gaze stays within about 10-15 deg of the target), with head lagging the torso by 1 tick, and a small nod (2-5 deg down) at contact. Do not roll or tilt the head (looks like a headbang). Kendo teaches a steady upright head (shinai-and-head structure) but I found no explicit angles. [I]

### Blade handling

21. **Edge leads the cut; the blade follows an elliptical/curved arc, never a straight chop.** Kendo: cutting motion is elliptical (3). Mocap guide: smooth arc, irregular arc reads as weak (2). Critique advice: viewers watch the tip, so push the tip arc (14). Check the tip path by scrubbing with a temporary marker at the blade tip and make sure the path is a smooth curve with no kinks. [S]

22. **Wind-up pose: blade raised/back to about 45 deg past vertical for an overhead-diagonal, or hip-high/behind the back shoulder for a horizontal cut; do not swing back to touching the back.** Kendo: about 45 deg back matches the shoulder's natural reach, further forces a regrip (two timings instead of one) (3). On this rig the right arm raises about 100-130 deg pitch (arm 0 = hanging) with the left arm following; more than that and the hands separate from the grip. [S* for 45 deg, I for rig angles]

23. **Keep both hands on the same grip all the way: converge the arms.** Rig shoulders are 12 px apart, arms 12 px long, so for the hands to meet near the midline each arm needs about 20-25 deg of inward yaw/roll (asin(5/12) is about 25 deg). The left arm follows the right arm's pitch with a tick-0 to tick-1 offset only; any bigger offset breaks the grip. Mocap guide: keep both hands on the grip for the whole two-handed attack (2). [S for grip rule, I for angles]

24. **Blade roll/wrist: add a roll of the item (or the hand end) of about 15-30 deg through the last 2 ticks before contact, snapping the edge into line at contact.** Kendo: wrist flips the blade at the strike and a slight rotation of the handle raises tip speed (a commenter claims about 30%) (3). The exact roll is inference, and the 30% claim is a single commenter (low confidence). [S* for the idea, I for numbers]

25. **Blade is the last thing to settle: it lags the hands by 1 tick and overshoots 5-15 deg past the contact pose, then settles back over 2 ticks.** Overlap/drag principle (15), pendulum-style overshoot of faster motion (5), arms relax so the blade rebounds (3). If the rig can only rotate the item via the arm, simulate the lag by finishing the arm's pitch 1 tick before the arm's roll/yaw. [S for principle, I for numbers]

26. **Blade should not drop below horizontal in the wind-up or before the strike** (kendo tip rule) and should never point backward-down toward the player's own legs. [S: 3]

### Timing and spacing

27. **Peak speed at about 60-70% of the arc, then a hard but short deceleration (not a slow-out before contact).** Mocap guide says peak blade speed at 60-70% through the arc (2); Better Combat's ease-in into the 60% keyframe agrees (1). Practical spacing for the 5-tick strike (fraction of total strike arc covered, cumulative, ticks 4 to 9): 0, 0.04, 0.15, 0.35, 0.70, 1.00. Increments per tick: 4%, 11%, 20%, 35%, 30%, so max speed is between ticks 7 and 9, i.e. when the blade is 35-70% into the arc. Adjust by eye. [S for 60-70%, I for the table]

28. **Snap into contact, then stop on the hold: the blade should arrive at contact still moving at 70-85% of peak speed and be stopped by the 1-tick hold.** Hit pause makes the strike look like it needed force to get through the target (9). [S* / I]

29. **Hit pause does not freeze the whole body equally; keep 1-3 px of tiny drift in the follow-through pose** (or freeze everything for 1 tick and rely on the next tick to explode). Either is acceptable; consistent use is better than mixing. [I]

30. **Vary speeds: fast strike, slower follow-through, slower still recovery.** Critique advice: pair fast moves with slower follow-through to avoid monotonous timing (14); mocap guide: heavy attack recovery is longer than the swing (2). [S]

### Smooth and uniform at 20 tps

31. **At 20 tps each tick is 50 ms, so every tick in the strike must be a distinct, intentional pose.** If the game interpolates between ticks (partial-tick rendering, which Better Combat/PlayerAnimator-style systems normally do), you need far fewer keys than ticks: about 8-10 keyframes total for 16 ticks (wind-up: ticks 0, 2, 4; strike: 6, 8, 9; hold 10; follow-through 12, 13; recovery 16), with easing set per segment as in rule 5. If the system does NOT interpolate, key every tick in the strike (4-9) and use ones, since twos would be only 2-3 poses. Bad arcs and awkward holds become more obvious at lower rates (16). [S for the difficulty, I for key counts]

32. **No stop-and-go: do not leave two adjacent ticks with identical poses except the deliberate contact hold, and do not insert an ease-out/ease-in pair on a part in the middle of a swing.** One continuous speed-up per segment, one deceleration after contact. Uniform spacing means a robotic constant speed (15), so spacing should be eased, but "smooth" also means no random wiggles: check each channel's curve is monotonic between keys except for intentional overshoot. [S for uniform spacing, I for the monotonic check]

33. **Avoid "spastic" twitching: do not add small counter-motions of less than 3 deg to parts that already move; do not overshoot more than 15 deg; do not key the head with more than 2 direction changes over the whole clip.** [I]

34. **Arcs not lines.** Check by looking at the hand end and blade tip paths (a temporary marker) and ensure each is a curve; because shoulders are the pivot, arm rotation already gives arcs, but keying only pitch with no yaw produces a flat plane and a stiff swing. Add 10-20 deg of yaw/roll variation across the swing so the path is 3D-curved. [S for arcs principle (15), I for numbers]

### Common mistakes and checks

35. **Mistake: stiff upper body** (only the arm moves). Fix: torso yaw 25-50 deg + lean (rules 17-18) and arm key offsets (rule 8). [I with S support (2: trunk rotation drives two-handed swings)]

36. **Mistake: no leg weight shift / feet glued to the floor.** Fix: front-foot landing at contact, rear foot trailing, small root drop (rules 11-16). [S/I]

37. **Mistake: blade does not follow the hands** (item stays in a fixed pose relative to the arm, or lags by more than 2 ticks). Fix: blade lag of 1 tick with a small overshoot (rule 25). [I]

38. **Mistake: over-rotation of head/torso.** More than about 100 deg total torso twist or more than about 20 deg lean looks like a cartoon spin on a single-piece torso; head should counter-rotate (rules 17-20). [I]

39. **Mistake: floaty follow-through** (the blade keeps drifting at constant speed after contact, no deceleration, no settling). Fix: ease-out from the contact pose, overshoot 5-15 deg, settle within 2 ticks, then a distinct recovery ease. Mocap guide also warns about a clip that stops at impact with no follow-through (2) and about weak/hidden wind-ups on heavy attacks (2). [S/I]

40. **Mistake: gameplay and animation disagree.** Hit detection must be triggered at the same tick as the visible contact; recovery in the animation must equal the gameplay recovery (2); a late-hitting visual makes hits feel "unfair" (search result, Defold thread). [S]

41. **Mistake: wind-up too long.** If playtesting feels laggy, shorten anticipation first (3 ticks), not the strike; judge by feel rather than polish (7). [S]

## Source coverage and gaps

- Strong: Better Combat's own timing structure (1) and a game sword guide with arc, follow-through and hit-stop numbers (2); kendo suburi mechanics for blade path and wrist (3, 4).
- Weak: any measured segment-to-segment lag (only order and peak speeds), any torso/lean/head angles, any leg step numbers. Those are labelled [I] and should be tuned in-game in Blockbench against what the rig looks like.
- Epic Fight docs gave no timing numbers (17).
- Pages that did not load or were only seen through search snippets (marked above): academieduello article, martialtalk thread, polycount thread, several animation-principle blogs.
