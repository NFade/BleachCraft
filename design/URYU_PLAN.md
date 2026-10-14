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

## 4. Revised ability table (current)
| Slot | State | Ability |
|---|---|---|
| Z | SHIKAI | **Heilig Pfeil: homing arrow** strong homing (10 degrees per tick, radius 20, half cone 100 degrees) to the nearest valid target, players first, 7 HP base, Focus bonus |
| H | SHIKAI | Sprenger (unchanged) |
| B | SHIKAI | **Seele Schneider** (lunge and three slashes), replaces Hirenkyaku |
| Z | BANKAI | **Wing barrage**, replaces Licht Regen |
| H | BANKAI | Homing Volley (kept) |
| B | BANKAI | Sternenstaub (unchanged) |

Note: Hirenkyaku is gone as an ability; the normal shunpo (any state) is Uryu's flash step, +2 blocks, Quincy fx, and it does not reset Focus. The air steps are dropped.

## 5. Open points
1. Homing vs Focus trade and Reishi absorption (section 3, 1 and 2): build or not.
2. Is the homing too strong or too weak in play (basic 2.5 degrees per tick, radius 14, half cone 60; Z 10 degrees, radius 20, half cone 100)? Tune in `UryuBalance`.
3. Left-handed bow hold and a real animated draw pose of the player (stage U7).
