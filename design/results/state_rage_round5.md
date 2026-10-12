# Round 5 (mechanics): Rage gate, shunpo and kido anywhere, sword away does not end anything - 2026-10-12

Game and server NOT run (both were open at the user's). `gradlew test` and `compileClientJava` green (in-game behaviour UNVERIFIED).

## 8. Rage gates shikai and bankai
- **One place**: `StateMachine.request` (core, pure). Keys and voice both go through `ZanpakutoManager.performTransition`, so one check covers both. New `RejectReason.NOT_ENOUGH_RAGE` -> `ResultCode.DENIED_RAGE` (appended, wire codes of the old ones unchanged). The check comes after the locks and BEFORE the reiatsu checks; a refusal changes and costs nothing. The reiatsu requirements (shikai 15.0, bankai full bar) are kept in addition (plan question F3: "add", not "replace").
- **Seam**: `StateMachine.setRage(RageConfig, IntSupplier)`; the glue (`ZanpakutoManager.createSession`) passes `RageManager.valueOf(uuid)`. Without the seam (unit tests) there is no requirement. Payment: the machine emits `StateEvent.RageSpent(tenths)`, `apply()` calls `RageManager.spend` (log `[rage] -40.0 spent ...`).
- **Costs** (`RageConfig.shikaiCostTenths` 400, `bankaiCostTenths` 800; code constant `RageManager.CONFIG`, no config file yet): shikai needs and pays 40, bankai needs and pays 80.
- **Creative does not bypass.** `/reiatsu dev ragefree on|off` (op level 2, per player machine, default off, log `[rage] ragefree ON for X`) switches the requirement and the payment off.
- **Client feedback** (`HudModel.onResult` case DENIED_RAGE): red toast "Недостаточно Ярости 23/40" / "Not enough Rage 23/40" (keys `hud.reiatsu_test.rage_need`, `message.reiatsu_test.denied.rage`, `hud.reiatsu_test.rage_need_short` in en/ru), bass note, `PlateHud.flashRageDenied`: the missing part of the Rage bar up to the needed mark turns red, the mark blinks, the bar is shown even at 0 (1 s). Voice: second line of the voice toast "Недостаточно Ярости". The needed number is the client's copy of `RageConfig.defaults()` (as `BalanceConfig.defaults()` in the HUD): a changed server constant would show the old number. The "bankai ready" gem now also wants Rage >= 80.
- Legacy HUD (`hud=false`): action bar "Недостаточно Ярости" without numbers.

## 8b. New Rage numbers (`RageConfig.defaults()`, about 2.5x faster)
| | before | now |
|---|---|---|
| damage dealt | 0.4/hp, cap 8 per hit | 1.0/hp, cap 15 per hit |
| damage taken | 0.8/hp, cap 15 per hit | 1.5/hp, cap 25 per hit |
| parry | +20 | +30 |
| passive inflow | - | +1.0/s while a hit dealt/taken/parry happened within the last 6 s |
| drain | after 5 s, 1.0/s | after 8 s without a fight, 0.5/s |
| shikai / bankai | - | 40 / 80 (paid) |
Passive inflow: `passiveTenths 5` every `passiveIntervalTicks 10` for `passiveWindowTicks 120` after the last hit; another hit extends the window and keeps the rhythm; `devSet` starts none. About 7 sword hits of 6 hp = shikai, 14 = bankai. Config constructor grew by 5 ints (tests updated). `RageMeterTest` 29, new `RageGateTest` 11.

## 9. Shunpo and kido in any state
- Kido already was (`StateMachine.castKido`, phrases.json states = SEALED/BASE/SHIKAI/BANKAI for all three, no key gating in `ReiatsuKeys.castKido`); verified, nothing changed there.
- Shunpo: the only limit was in `StateMachine.shunpo` (state SHIKAI/BANKAI + held == character). Both removed: it works in SEALED/BASE/SHIKAI/BANKAI with any sword or none. Still checked: alive/spectator, rate limit, transition lock (0.5 s after a state change), cooldown, reiatsu cost, path/room. `ZanpakutoManager` tints the effect by the released character, else by the sword in hand. The key `Y` and the client had no gating; there is no shunpo/kido icon in the HUD (so nothing to un-grey). There is no shunpo voice phrase. No change to phrases.json / gen_voice_phrases.py.

## 10. Sword away from the hand ends nothing
- Removed: hand grace (`handLostSince`, `HAND_LOST` revert after 20 ticks), `onItemDropped` immediate seal and the `carries()` inventory scan in `ZanpakutoManager.tick`. `onHandChanged` is now a no-op kept for compatibility (`Trigger.HAND_LOST/ITEM_DROPPED` stay in the enum, unused; `BalanceConfig.handGraceTicks` is unused).
- SHIKAI/BANKAI now end only by: seal, death, logout, dimension change/spectator, the bankai cap (45 s -> SHIKAI) and the shikai idle timer (120 s without a cast, kido or shunpo). Running effects (freeze, arena, swarm, barrier, temp blocks) are untouched because no `CancelEffects` is produced.
- A NEW shikai/bankai ability or the release/bankai request still needs the matching sword in the main hand (WRONG_ITEM). SEALING no longer needs a sword (V works with the sword dropped or in the off hand); this changed the old contract "seal with the wrong sword = WRONG_ITEM".
- Visual: `applyMirror` still shows the state only on the main-hand stack (other stacks render SEALED); that is render only. Nothing server-side was found that reset effects on item change besides the above (AbilityExecutor, AnchorManager, KidoManager have no hand checks). Client pose/FX code (other agents) was not touched.
- Open: BASE (drawn, not released) has no idle timer, so with the sword dropped it stays BASE until V.

## Changed files
core: `rage/RageConfig`, `rage/RageMeter`, `state/StateMachine`, `RejectReason`, `ResultCode`, `StateEvent`. server: `RageManager` (valueOf, spend log), `ZanpakutoManager`, `ReiatsuCommand`, `VoiceControl`. voice: `VoiceHudState` (DENIED_RAGE). client: `HudModel`, `PlateHud`, `ClientNet`. lang en/ru. tests: `RageMeterTest`, `RageGateTest` (new), `ShunpoTest`, `AutoRevertTest`, `BankaiTest`, `TransitionTest`.
Dev harnesses (`Phase4/5Harness` "bankai at 50.0 is DENIED_REIATSU") were not run; with the Rage gate they need `/reiatsu dev ragefree on` or enough Rage first.

## To check in game (UNVERIFIED)
1. Fresh player, draw (J), press R: red toast "Недостаточно Ярости 0/40", the bar appears red with a mark at 40%. `/reiatsu dev rage 40` then R works and the bar drops to 0. G needs 80 (and a full reiatsu bar). Same with the voice phrase.
2. `/reiatsu dev ragefree on` (also in Creative) lets R/G through with no Rage change; `off` restores the check.
3. Fight a mob/player: bar rises ~2.5x faster, keeps creeping +1.0/s for 6 s after the last hit, drains 0.5/s after 8 s.
4. Y (shunpo) and K/N/M (kido) in SEALED, BASE, SHIKAI, BANKAI, with a sword and with an empty hand.
5. In shikai: switch the hotbar slot, drop the sword (Q), put it in the off hand: state stays SHIKAI, a running freeze/arena/swarm continues; Z/H/B refuse with WRONG_ITEM until the sword is back; V seals without a sword; 2 minutes idle still seals.
