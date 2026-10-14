# Uryū Ishida: staged plan and design revision 2 (2026-10-14)

The staged plan below mirrors how Rukia and Byakuya were built (MASTER_PROMPT.md section 5: research -> art -> skeleton -> mechanics -> effects with acceptance checks per step -> integration/QA; design/VFX_STORYBOARD.md section 10 for the step-with-acceptance format; PLAN_2026-10-11.md for the user feedback loop). Gin is paused by the user (2026-10-14); his code and models stay in the branch untouched.

## 1. Revision 2: what the user changed (binding over design/URYU.md where they differ)
1. **The bow shoots like the vanilla bow:** hold right click to draw (full draw 20 ticks, power curve of the vanilla bow), release to fire. **Drawing does not slow the player** (no 0.2 movement factor, sprint is kept). Works whenever the bow is drawn (BASE, SHIKAI, BANKAI).
2. **Passive: every arrow is homing** (basic shot, ability arrows). Homing strength is a per-arrow parameter: weak for the basic shot, strong for the Z ability.
3. **Shikai Z = a homing arrow whose flight curves toward the nearest player** (the Focus bonus still applies). "Nearest" = the nearest valid target (HitRules, no caster/team/creative) to the arrow in front of it; players are preferred over mobs.
4. **Licht Regen is replaced:** it is too close to a shikai ability. The bankai needs something larger in scale (see section 3, options; the user picks).
5. Visible arrows with a model, hit effects and a drawn bow pose: the current placeholders (lines) are not acceptable ("no sprites, nothing").

## 2. Staged plan
| Stage | Work | Acceptance |
|---|---|---|
| U0 research | Fandom facts (done): design/URYU.md section 1 | done |
| U1 design | URYU.md + this revision, user decisions in section 5 | user OK on the open points |
| U2 models | bow, arrow, Seele Schneider, cross, string (done: `blender/export/uryu/`); still missing: wings (bankai), reishi flechette, Sprenger pentagon ring, pulled-string bow variants or a draw animation | turntable renders, triangle budgets as the other models |
| U3 skeleton | registration, items, lang, voice phrases, tests (done) | build green |
| **U4 bow and projectiles (now)** | vanilla-like draw and release, no slowdown, server arrow sim with homing for every arrow, visible arrow model with trail and hit spark, bow in hand (first and third person), Focus on the HUD | unit tests; in game: a shot flies with the arrow model, curves toward a player, drawing while running does not slow |
| **U5 shikai** | Z homing arrow, Sprenger (pentagon: bind, explosion, early detonation), Hirenkyaku (air dash and air steps) with real fx | each ability: damage numbers in the log, fx screenshots, dummy-hit checks like Rukia |
| U6 bankai | Vollständig state (wings, 45 s), the large-scale ability (section 3), Sternenstaub | as U5 plus FPS check (wings, swarm effects) |
| U7 first/third person animation | bow draw, release, Hirenkyaku dash, bankai pose (as the fp2/tp3 work of the sword characters) | screenshots from both views, no clipping |
| U8 HUD, voice, lang | Focus pips, cooldown strip icons, voice fixtures (positives for every Uryu command, false positives 0), ru/en lang | fixture tests, HUD screenshots at the standard scales |
| U9 balance and QA | numbers vs Rukia/Byakuya in duels, `docs/QA_CHECKLIST.md` entry, two-client test | the checklist passed by the user |

**Revision 3 (user, 2026-10-14, built in U4/U5):** (a) Focus also builds while Uryu WALKS ON SNEAK (horizontal speed below 0.09 blocks per tick); it is reset only by real movement faster than that, a jump or a fall, sprinting, and being pushed (knockback of a hit or of any ability of another character); the shunpo does not reset it. (b) There is no separate Hirenkyaku: Uryu uses the mod's normal shunpo with +2 blocks of distance and a Quincy look (blue Reishi trail, Reishi platform disc under the feet). The air steps idea is dropped. (c) No ability drains the reiatsu or the Rage of other players (the Sklaverei dome is dropped).

## 3. Making him as interesting as Rukia (ice stacks, frost territory) and Byakuya (petal swarm, barrier, wings)
Existing: **Focus** (stand still, or sneak slowly, for power) and the **homing passive** (every arrow). Decisions of the user:
1. **Homing vs Focus trade:** not decided (not built). Homing is a fixed per-arrow parameter for now.
2. **Reishi absorption:** not decided (not built).
3. **Air steps:** DROPPED (revision 3b).
4. **Seele Schneider melee: BUILT as the shikai slot B ability** (replaces Hirenkyaku, same ability code 20, effect id 74, command `uryu.shikai.seele_schneider`): a 1.5 block lunge by velocity, then three slashes 0.25 s apart in a 3.5 block, 100 degree cone, 2.5 HP each, the third also knocks back 1.0; 10 reiatsu, 5 s; Focus is neither reset nor consumed.
5. **Bankai large-scale ability: option A (wing barrage) BUILT**, replaces Licht Regen (code 21, effect id 75, command `uryu.bankai.wing_barrage`, voice "Heiligen Fliegen" / "Wing barrage"): for 6 s the wings fire homing arrows at up to 4 of the nearest valid enemies within 30 blocks of Uryu (a distinct target per wing slot, re-evaluated every 8 ticks, one arrow per target every 0.4 s, 2 HP each, strong homing); Uryu moves, shoots the bow and uses other abilities meanwhile; 28 reiatsu, 22 s; ends with the bankai or his death. Options B (Sklaverei dome) and C (Reishi trap field) are dropped.
6. **Sternenstaub** stays the bankai finisher (giant Focus-3 arrow).

**Revision 4 (user, 2026-10-14, built in round 2, see design/results/uryu_round2.md):**
(a) **Bankai H = Licht Regen** replaces the Homing Volley (same ability code 22, effect id 76, command `uryu.bankai.licht_regen`, voice "Licht Regen" / "Light rain"). The wing barrage stays on Z. Uryu raises the bow for 0.6 s, then the rain falls on the point he aims at at the END of that time (28 blocks at most, `aimAtRelease` like Shirafune): a SQUARE of 9 x 9 blocks aligned to the world grid and centred on that point, for 3.0 s; 6 arrows per tick (360 per cast) start at seeded random places 14 blocks above the ground level of the centre and fall with a small slant (the landing points stay inside the square), speed 2.6 blocks per tick, each a real `ArrowSim` arrow without homing that stops at terrain and roofs, 1.0 HP, i-frames reset per hit, the caster and his team safe, at most 14 HP per target and cast; 35 reiatsu, 24 s. Expected damage to a standing player is about 8 to 9 HP (arrow radius 0.4), a moving one takes less. Cap of 400 arrows alive per world (the rain alone keeps about 40).
(b) **Sprenger roots.** Everybody inside the pentagon at the bind moment (0.8 s) can neither walk nor jump (movement-speed and jump-strength modifiers of -100 percent) and a player cannot SHUNPO, from the bind to the explosion (1.2 s). The root and the shunpo ban end at once when the rooted one takes ANY damage (also from the owner's other abilities), and at death, logout, change of dimension, state change of the caster, server stop. The pentagon's own explosion (and an early detonation by a Focus 3 arrow) ends it too, and is not counted as a break. The explosion keeps 9 HP (13.5 early). A banned shunpo gets the cooldown answer (the HUD shunpo cell shakes), no cost, and the action bar message `message.reiatsu_test.denied.shunpo_bound`.
(c) **The bow in the hand is bigger:** display scale 1.8 in first person (was 0.7) and 1.4 in third person (was 0.9), see the results file for the reasoning.

## 4. Revised ability table (current)
| Slot | State | Ability |
|---|---|---|
| Z | SHIKAI | **Heilig Pfeil: homing arrow** strong homing (10 degrees per tick, radius 20, half cone 100 degrees) to the nearest valid target, players first, 7 HP base, Focus bonus |
| H | SHIKAI | Sprenger (unchanged) |
| B | SHIKAI | **Seele Schneider** (lunge and three slashes), replaces Hirenkyaku |
| Z | BANKAI | **Wing barrage**, replaces Licht Regen |
| H | BANKAI | **Licht Regen** (arrow rain, 9 x 9 square), replaces the Homing Volley (revision 4) |
| B | BANKAI | Sternenstaub (unchanged) |

Note: Hirenkyaku is gone as an ability; the normal shunpo (any state) is Uryu's flash step, +2 blocks, Quincy fx, and it does not reset Focus. The air steps are dropped.

## 5. Open points
1. Homing vs Focus trade and Reishi absorption (section 3, 1 and 2): build or not.
2. Is the homing too strong or too weak in play (basic 2.5 degrees per tick, radius 14, half cone 60; Z 10 degrees, radius 20, half cone 100)? Tune in `UryuBalance`.
3. Left-handed bow hold and a real animated draw pose of the player (stage U7).

## 6. U-FX pending (effects, later, when the usage limits refresh)
The visual direction, frame-by-frame timelines, poses, model and texture lists, the U-FX 0..12 implementation order and the test plan for everything in this section are now in `design/URYU_VFX_STORYBOARD.md` (canon reference sheets: `refs/sheets/uryu_*.png`, private, git-ignored).

Everything visual below is a PLACEHOLDER today. The user wants LARGE, spectacular effects that are faithful to the canon, built the way the Rukia and Byakuya effects were (FxShapes / FxGlowBatch layers, own meshes, own sounds, a storyboard with acceptance checks per effect, screenshots from the game). Method: reference images found through `tools/fandom.py` (images / get) and stored only as private references under the project rules of MASTER_PROMPT.md section 2 (rule 4: into `refs/` only, at most 12 images per state, file names `<char>_<state>_<n>.<ext>`, source URL in `refs/INDEX.md`, `refs/` in .gitignore, nothing ripped into the mod, texts of the wiki are not copied); all models, textures and effects are made by us. To be done in this order of value:
| Effect | Canon look to aim at | Placeholder today (to replace) |
|---|---|---|
| Heilig Pfeil (basic shot and Z) | a blue-white arrow of condensed Reishi that leaves a light trail, a real Quincy arrow shape, a burst on impact | `UryuArrowRenderer` mesh scale 1.6/2.6, END_ROD sparks, glow streak trail, `IMPACT` spark and chime |
| Reishi gathering | the Reishi in the air streams into the bow, the bow itself forms from light (sealed cross to bow) | none for the gathering; the bow is a normal item mesh with a drawn V string |
| Sprenger | five Seele Schneider fall into a pentagon, a glowing pentagram, a column of blue light and a big explosion | `UryuFx.trap` / `burst` (daggers, flat pentagram, rings) |
| Licht Regen | a huge rain of light arrows from the sky over the whole 9 x 9 area, the bow raised, impact flashes on the ground | a faint square outline on the ground (`UryuFx.rainSquare`), the plain arrows with a lean look, no impact effect |
| Wing barrage / Quincy wings | large angular wings of Reishi (Quincy: Vollstaendig), pulsing with every volley | `UryuFx.wing` glow-streak wings |
| Sternenstaub | a giant line of star-like flechettes | `UryuFx.line` strip |
| Seele Schneider | a Reishi blade with a sweeping crescent | `UryuFx.slash` |
| Shunpo (Hirenkyaku look) | blue Reishi platforms under the feet, a trail | `ShunpoFx` Uryu branch, `UryuFx.shunpoReishi` |
| Sprenger root | a visible binding on the rooted legs | none (the root has no effect at all) |
Also still open: the third person gesture of the Licht Regen starts when the rain starts (the effect event now comes at the release), a real raise-the-bow animation belongs to U7.
