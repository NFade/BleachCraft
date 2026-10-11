# Kido (B5) - 2026-10-11

Scope: three spells that anyone can cast by voice or by key in ANY state (SEALED, BASE, SHIKAI, BANKAI), with any sword or with bare hands. No Rage link (the extension point `RageManager.spend` is not used).

## The set (NEEDS YOUR CONFIRMATION)
`PLAN_2026-10-11.md` B5 only says "for example Hado 1 Sho, Hado 31 Shakkaho, Byakurai or Bakudo 1 Sai", and question F4 ("which three first?") is still open. No `research/` file about kido exists. So I took the canonical trio of the task text:

| # | Spell | Id | Key | Why |
|---|---|---|---|---|
| 1 | Hado no 4, Byakurai (white lightning) | `kido.byakurai` | K | direct beam, cheap, always hits what you look at |
| 2 | Hado no 31, Shakkaho (red fire cannon) | `kido.shakkaho` | N | medium range ball with a burst, the expensive one |
| 3 | Bakudo no 1, Sai (restraint) | `kido.sai` | M | short range binding, no damage |

Names and numbers are from memory of the canon (Hado 4 = Byakurai, Hado 31 = Shakkaho, Bakudo 1 = Sai). The English "Way of Destruction 4 / White Lightning", "Way of Destruction 31 / Red Flame Cannon", "Way of Binding 1 / Restrain" are used as origin `T` phrases and are UNVERIFIED against a wiki (no web check was done). Swapping a spell (for example Hado 1 Sho) means a new `KidoId` entry, a spec, a phrase section and an effect; nothing else is spell specific.

## Numbers (all in `KidoConfig.defaults()`, user-editable)
Reiatsu bar is 100.0 and regenerates 4.0/s when sealed or drawn. UNVERIFIED by play.

| | Byakurai | Shakkaho | Sai |
|---|---|---|---|
| cost (reiatsu) | 10.0 | 20.0 | 6.0 |
| cooldown | 3 s (60 t) | 7 s (140 t) | 5 s (100 t) |
| range | 30 blocks | 24 blocks | 8 blocks |
| damage | 6 hp (3 hearts), first target only | 10 hp in the centre, linear falloff to 40 percent at radius 3.5 | 0 |
| area | beam half width 0.35 | burst radius 3.5, targets burn 3 s, knockback 0.5, NO block damage | first target, half width 0.4 |
| speed | instant (hitscan) | 1.2 blocks/tick (24 b/s), 20 t at full range, burst at the end of the flight | instant |
| status | none | fire 60 t | slowness 7 (speed 0) for 3 s (60 t) |

Common: gap of 10 ticks (0.5 s) between any two kido casts (`gcdTicks`). A cast refuses when the bar would drop below 1.0 (rule R2). A miss still costs (like Shirafune). Shakkaho damage is applied when the ball arrives, around the point where it was going to land (decided at cast time); if the caster dies, leaves or changes dimension meanwhile, the ball fizzles.

Config: section `"kido"` of `config/reiatsu_test.json`, written with the defaults on first start if missing (other sections untouched; an unreadable file is left alone). Per spell keys: `enabled, costTenths, cooldownTicks, range, damage, radius, statusTicks, fireTicks, speed, width`; top level `enabled`, `gcdTicks`. Bad values fall back to the default, numbers are clamped. Read once when the server starts (restart the world after editing).

## Casting rules
- State machine: `StateMachine.castKido` (pure). No state/item/lock checks; order of checks in `design/STATE_MACHINE.md` 10.3.
- Block: refused while the block (right mouse) is held, same rule and same place for key and voice (`ZanpakutoManager.performKido`, `BlockRules.allowsAbility`).
- Damage type `INDIRECT_MAGIC` attributed to the caster: `BlockParry.kindOf` only counts a direct melee hit as MELEE, so a target's block or parry never reduces or cancels kido damage. Targets: `Targeting.valid` (never the caster, immune tag, pets; players only with `Tuning.affectPlayers` and PvP).
- Key and voice share `performKido`. Voice dispatch is in `VoiceControl.dispatch`; the action bar shows "Voice: Hado 4: Byakurai".

## Voice (`tools/gen_voice_phrases.py`, `design/VOICE_PHRASES.md` 1.15 to 1.17)
Commands `kido.byakurai`, `kido.shakkaho`, `kido.sai`: states SEALED/BASE/SHIKAI/BANKAI, item `any`, not interim-safe. 17 commands now, 390 variants. Number is optional, the name alone is enough, "Hado" alone never fires (it would be 4 or 31).

- Byakurai: `Byakurai`, `Hado no yon Byakurai`, `Hado 4 [Byakurai]`, `Хадо четыре / чётыре / 4 / но ён [Бякурай]`, 白雷, 破道の四 白雷. ASR aliases: bya kurai, by a cry (weak), be a cry (weak), beer a cry (weak), бьякурай, бекурай, бякурая, бья курай.
- Shakkaho: `Shakkaho`, `Hado no sanjuichi Shakkaho`, `Hado 31 [Shakkaho]`, `Хадо тридцать один / 31 [Сякахо]`, `Сякухо`, `Шакахо`, 赤火砲, しゃっかほう. Key `sakaho` is 6 letters (exact-only), so spellings are explicit aliases: sha ka ho / shaka ho / shack a ho / сяка хо / шака хо (weak), сякаго, сякохо, shockaho.
- Sai: `Bakudo ichi Sai`, `Bakudo 1 [Sai]`, `Бакудо один [Сай]`, 縛道の一 塞. Weak (whole utterance only): lone `Sai`, `Сай`, `塞`, `さい`, lone `Bakudo` / `Бакудо` (it is the only binding spell, so it fires Sai).
- False-positive rules: every ordinary-word spelling is weak; Normalizer got the kanji 破道, 縛道, 白雷, 赤火砲, 塞. Section 3.2 of the md has the "must not fire" column.
- All ASR spellings are expected renderings, NOT recordings (UNVERIFIED with a real microphone), same status as the G3 aliases.

Fixture: new `tests/voice/kido_fixture.json` (71 positives, 68 neutrals: say/sigh/psy/sad/buy/shack/cannon/lightning, Russian and Japanese look-alikes, "Hado" alone, sentences with the weak names). The 202 fixture, the dirty (G3) fixture and `live_fixture_2026_10_11.json` were not touched; `KidoVoiceFixtureTest` re-runs their neutrals against the kido commands (0 fired) and checks that their positives are not stolen (0 stolen).

## Client effects (`client/fx/KidoFx`, effect ids 50, 51, 52, hooked in `FxEvents`)
One `effect_event` per cast: x y z = eye, d = look, target = entity hit, params = {length, travel ticks, burst radius, hit kind (0 none, 1 entity, 2 block), speed, binding ticks}. The client draws from that (no second packet).
- Byakurai: white core strip plus soft glow along the line (0.28 s), a short zigzag of 3 to 14 thin strips, flashes at the hand and at the end, electric sparks, thunder-like sound.
- Shakkaho: charge flash at the hand, a red ball as a train of glow sprites with staggered births over the server's flight time, flame/smoke particles on the way, burst at the end (glow, star, ground ring, flames, smoke, explosion puff, sound).
- Sai: yellow flash at the hand, a thin yellow line, on a hit two rings and a star at the target and sparks.
- Tuning without restart, `run/fx_override.json`: `kido.byakurai.{color,life,coreWidth,glowWidth,glowIntensity,zigzag,handFlash,endFlash,endGlow,sparks,pitch,volume}`, `kido.shakkaho.{color,core,ballSize,burstSize,flames,smoke,castPitch,castVolume,burstPitch,burstVolume}`, `kido.sai.{color,life,width,handFlash,ringSize,pitch,volume}`. Counts follow `effectQuality`; `reduceMotion` drops the zigzag and most sparks.
- HUD: no new strip entry. A refused key press uses the existing feedback (denied reiatsu bar flash, cooldown shake). The cooldowns are synced (`CooldownData` carries codes 101 to 103, `ClientState.cooldownRemaining(KidoId)`) so a HUD icon can be added later.
- Keys K, N, M (`key.reiatsu_test.kido_1..3`, free in vanilla 1.21.1, rebindable); lang keys `kido.reiatsu_test.*` and `key.reiatsu_test.kido_*` in en/ru.

## Files
New: `core/kido/{KidoId,KidoSpec,KidoConfig,KidoMath,KidoRequest}`, `net/CastKidoC2S`, `server/KidoManager`, `client/fx/KidoFx`, tests `core/kido/{KidoConfigTest,KidoMathTest}`, `core/state/KidoCastTest`, `core/voice/KidoVoiceFixtureTest`, `tests/voice/kido_fixture.json`.
Changed: `StateMachine` (castKido, kido cooldown map), `StateEvent` (KidoCast), `ZanpakutoManager` (receiver, `performKido`, event, cooldown sync), `VoiceControl` (dispatch), `ServerFx.kido`, `ModConfig.loadKido`, `ReiatsuTest` (init), `ModNetworking`, `CooldownData`, `Normalizer` (kanji), `phrases.json` (generated), `gen_voice_phrases.py`, `VOICE_PHRASES.md`, `STATE_MACHINE.md` 10.3, lang en/ru, `FxEvents`, `ClientNet`, `ClientState`, `ReiatsuKeys`. Tests adapted for the new command count only: `PhraseBookTest` (17 commands, id pattern), `BalanceConfigTest` M3 (kido has no AbilityId), `MatcherFixtureTest` (the "10 positives per command" check skips `kido.*`, they have their own fixture).

## Tests
`gradlew test compileClientJava`: 437 run, 0 failed (380 before this task and the parallel BC work). New: KidoConfigTest 7, KidoMathTest 5, KidoCastTest 13, KidoVoiceFixtureTest 10. Game not run.

## To check in game (UNVERIFIED)
1. Keys K / N / M in SEALED with an empty hand: each casts, reiatsu drops by 10 / 20 / 6, a second press inside 0.5 s or inside the cooldown does nothing (cooldown shake), with low reiatsu the bar flashes.
2. Same in BASE (sword drawn), SHIKAI and BANKAI of both characters; the zanpakuto state does not change (a cast also counts as activity for the shikai idle timer).
3. Holding right mouse (block): all three are refused (denied message), nothing is spent.
4. Byakurai on a zombie at 20 blocks: 6 hp, white line from the right hand to the body, no damage through a wall. Shakkaho on a mob group: ball arrives after about 1 s at 24 blocks, burst radius 3.5, centre 10 hp, edge 4 hp, mobs burn, no crater. Sai: a zombie within 8 blocks stops walking for 3 s.
5. A mob with a blocking player target (PvP on, `Tuning.affectPlayers`): the kido hit lands through a block and a parry.
6. Voice: "Byakurai", "Хадо четыре Бякурай", "Shakkaho", "Сякухо", "Бакудо один Сай", lone "Sai", in SEALED with empty hands. Watch the log line `[voice] heard ...` for spellings that miss and add them to the generator; normal talk should not cast.
7. Visuals: hand offset (right/down) in first and third person, zigzag amplitude, ball size and flight against the server burst time (burst should coincide with the damage), ring on the target for Sai (it does not follow a moving target, the binding itself has no lasting visual yet).
8. Config: edit `config/reiatsu_test.json` "kido" and restart the world; a broken file keeps working with defaults.

## Open
- Confirm or change the trio (F4). A fourth/fifth spell (Hado 1 Sho, Hado 33 Sokatsui) is a small addition.
- Rage link (kido spends or builds Rage) deliberately not connected.
- No lasting visual for the Sai binding and no HUD cooldown icons.
- Better Combat: kido keys are free of its defaults; not checked in a joint run.
