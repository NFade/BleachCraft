# QA checklist (manual, in game, 20 to 30 minutes)

Follows `MASTER_PROMPT.md` section 5, Phase 7, scenarios 1 to 8, brought up to date for the drawn base form (BASE), the
scabbard, the 45 second bankai timer and shunpo. Tick the boxes with `[x]`, write numbers into the blanks, and put anything odd
into the "Notes" line of the section. Time boxes add up to about 28 minutes; skip a section only if you write why.

How to read it:
- **[auto]** means a harness already checks it (`gradlew runPhase4/5/6`, `python -I tools/server_smoke.py`); you only confirm it by
  eye. **[hand]** is for you.
- Items marked **(if built)** belong to features that were still in progress when this list was written (shunpo, the bankai
  timer ending in shikai, the Byakuya swarm and the bankai blade rows, the Rukia shikai and bankai effects). If a feature is not in
  your build, write "n/a" and move on; do not count it as a failure.
- Numbers (costs, cooldowns, radii) live in `BalanceConfig`; if they were retuned, trust the HUD and `design/STATE_MACHINE.md`
  more than this page.

## 0. Setup (2 min)

- [ ] Java 21, Minecraft 1.21.1 profile with Fabric Loader 0.19.5+, Fabric API 0.116.17+1.21.1 and `reiatsu_test-*.jar`
      (`docs/RELEASE.md` for how the jar is made). The log has no `ERROR` line from `reiatsu_test`.
- [ ] New creative world, flat or default, cheats on, daytime. Graphics: Fancy (Fabulous is checked in section 7).
- [ ] `/give @s reiatsu_test:sode_no_shirayuki` and `/give @s reiatsu_test:senbonzakura` (also in the Combat creative tab).
- [ ] Options, Controls, category **Reiatsu Test** lists: Draw / sheathe (J), Release (R), Bankai (G), Seal (V), Ability 1 to 3
      (Z, H, B) and, if built, shunpo. No key clashes with a vanilla key (the game marks clashes in red).
- [ ] Keep `/reiatsu info` handy: it prints state, character, mode, reiatsu, temp blocks and cooldowns.
- Build/commit under test: ______________________  Tester: ______________  Date: ____________

## 1. Take the sword: model, orientation, glow (3 min)

Do it for both swords. Main hand, first person, then F5 (third person) and the item in the inventory screen.

- [ ] The Blender model is shown (not a cube, not a vanilla sword), correct way up: the edge toward the camera, the tip up and
      away, the guard (tsuba) visible above the fist, the hand lowered, nothing clipping the screen edge badly.
- [ ] **Scabbard (SEALED).** Holding the sword sheathed shows the scabbard in the hand. The left hand holds the scabbard, the
      right hand rests on the grip. In third person (F5) the scabbard sits on the left hip.
- [ ] Glow: emissive parts shine in a dark cave (`/time set night`, stand in a covered spot): the tsuba and edge accents, not the
      whole model. Compare day and night.
- [ ] Inventory and hotbar icons exist for all three forms (check later in the HUD plate too).
- [ ] Third person: other things in hand (a stick, a vanilla sword) look normal; the mod does not change them.
- Notes: ______________________________________________

## 2. Draw, release (shikai): state change, model swap, release effect (3 min)

Rukia first, then Byakuya.

- [ ] **Draw**: press `J` (and again later with a right click). The blade is drawn from the scabbard along a curve in about half a
      second, the scabbard then leaves the first person view. HUD: state "Drawn" (BASE), hint "[R] Release (shikai)". Press `J`
      again: the sword goes back into the scabbard, state Sealed.
- [ ] Third person: the scabbard is on the hip, the drawn sword in the right hand. Known defect: at the end of the draw the sword
      jumps from the hip to the hand (not a failure).
- [ ] Pressing `R` while still sheathed answers "Draw the sword first" and a low note, nothing else happens.
- [ ] **Release by key**: `R` from the drawn form. The model changes (Rukia: white blade and ribbon; Byakuya: hilt without a
      blade), release effect plays (flash, ring on the ground, column of ice shards or petals, a title card when
      `titleCards` is on), aura starts and stays (cyan snow and frost for Rukia, lilac petals for Byakuya). `/reiatsu info` says SHIKAI.
- [ ] **Release by voice** (section 6 explains the page): say the release phrase while the sword is drawn. Same result. The page
      shows what it heard and the game's answer.
- [ ] The reiatsu bar dropped by 15 and the HUD plate shows the form and the sword names.
- [ ] Swap to a vanilla item while drawn: `R` is refused ("Hold your zanpakuto"), and after about one second without the sword the state returns to SEALED.
- [ ] Seal with `V`: model back to the sheathed sword, aura and effects end, no stuck particles.
- Notes: ______________________________________________

## 3. All shikai abilities, by key and by voice (5 min)

Use `/reiatsu full` and `/reiatsu cooldowns clear` between tries. Stand in front of a few mobs (`/summon zombie` in a row, or a
flat arena). Say each phrase once and press the key once.

| Ability | Key | Voice phrase | Cost / cooldown | Key works | Voice works | Effect and damage look right | Cooldown icon / refusal |
|---|---|---|---|---|---|---|---|
| Rukia, Tsukishiro | Z | "Tsukishiro" | 25 / 12 s | [ ] | [ ] | [ ] ring of ice, pillar, mobs frozen then shattered | [ ] |
| Rukia, Hakuren | H | "Hakuren" | 20 / 8 s | [ ] | [ ] | [ ] wave along the look line, frost on the ground | [ ] |
| Rukia, Shirafune | B | "Shirafune" | 15 / 5 s | [ ] | [ ] | [ ] strike on the first target | [ ] |
| Byakuya, attack mode | Z | "Attack mode" | 12 / 3 s | [ ] | [ ] | [ ] petals fly to the aimed point (if built) | [ ] |
| Byakuya, barrier mode | H | "Barrier mode" | 18 / 10 s | [ ] | [ ] | [ ] dome of petals, damage reduced (if built) | [ ] |

- [ ] A second press during the cooldown does nothing harmful: the HUD shows the cooldown (shake, "Cooldown Ns" for voice), no
      reiatsu is lost.
- [ ] With too little reiatsu (`/reiatsu set 5`) the ability is refused with the red bar flash and "Not enough reiatsu".
- [ ] Mobs of a villager or wandering trader type take no damage; yourself, a tamed pet and other players do not either (unless
      `/reiatsu pvp true`).
- [ ] Temporary ice or snow appears only on air or snow and disappears by itself (within about 7 s); `/reiatsu info` ends with
      `tempBlocks=0` after sealing. No block anywhere stays changed (look at the arena after `V`).
- [ ] A wrong voice phrase for the form is ignored (say a bankai ability while in shikai): "Not available in this state".
- Notes: ______________________________________________

## 4. Bankai: condition, transition, effect, HUD, timer (3 min)

- [ ] In shikai with the bar below full (`/reiatsu set 99`) `G` and the bankai phrase are refused ("Need full reiatsu", red flash).
- [ ] `/reiatsu full`, then `G` (or say "Bankai" with the matching sword): transition to BANKAI. Cost 20 is taken.
      Effect: letterbox bars and the kanji plate 卍解 with the bankai name (Hakka no Togame / Senbonzakura Kageyoshi) and its
      English translation, screen grade, the big bankai effect and the aura of the form (if built). The model changes to the
      bankai model (Rukia: the long white ribbon blade; Byakuya: the sword with the blade rows in the ground, if built).
- [ ] HUD shows the bankai timer ("Ns left", counts down from 45) and the bar. **(timer rule)** In the current build bankai also
      drains the bar and ends at 45 s or at 0 reiatsu; the planned rule (`design/FIXES_B4.md` step 4) is a pure 45 s timer with no
      bar cost for bankai abilities and a return to shikai. Write down which one you saw: ______________________
- [ ] At the end of the timer the form ends with the seal-style effect: Bankai ended message, auras gone, temp blocks rolled back.
      The state afterwards (SEALED in the current build, SHIKAI after step 4) is: ______________
- [ ] After a bankai that ran out, drawing again is held back for a few seconds (about 8 s release lock) but then works.
- Notes: ______________________________________________

## 5. Bankai abilities and the way back to sealed (4 min)

- [ ] **Rukia, Absolute Zero** (`Z`, "Absolute zero"): wide frozen area around you (about 10 blocks in the new numbers), mobs held
      then shattered, ice crystals, ice blocks vanish by themselves. Passive: hostile mobs close to you are slowed.
- [ ] **Byakuya, petal storm / scatter** (`Z`, "Chire" or "Petal storm") (if built): storm of petals around you, impact at the aim point.
- [ ] **Byakuya, Hakuteiken** (`H`, "Hakuteiken") (if built): bright line along the look direction, impact burst at its end.
- [ ] Shikai abilities in bankai (the planned rule makes them usable on top; write what happened): ______________
- [ ] Each of the three ways back, each with a clean world afterwards (no ice, no particles, no stuck aura, `tempBlocks=0`):
  - [ ] `V` or the phrase "Seal" in bankai;
  - [ ] let the timer run out;
  - [ ] die in bankai (`/kill`), respawn: SEALED, bar at 50, no leftover effect.
- [ ] Log out in bankai and join again: SEALED, bar kept, no ice left in the world.
- [ ] Press `/reiatsu state sealed` mid-cast: the scheduled phases of the ability stop (no late damage after sealing).
- Notes: ______________________________________________

## 6. Voice: works, and no false triggers (4 min)

Set up (1 min): single-player world, click the chat link `http://127.0.0.1:47821/`, open it in Chrome or Edge, Start, allow the
microphone, pick the language. Hold the sword in the right state.

- [ ] The page shows interim (grey) and final text, the microphone indicator moves, the "you can say now" chips match the state.
- [ ] Release, one dance, bankai and seal by voice each fired the right command within a moment (about 0.4 s is the budget; the
      bridge itself measured about 1 ms plus 8 ms to the client; judge by feel and note anything slow): ____________
- [ ] Say them in the other two languages too if you can (Japanese, Russian): ____________
- [ ] **No false triggers.** Hold the sword drawn and in shikai, and talk normally for one minute: read a paragraph, say
      "I need to go to the bank", "the seal is broken", "banker", "scatter the papers", the names of other anime. Nothing may
      fire. Count of unwanted triggers: ____ (must be 0). The page's typed-phrase box can be used for exact sentences.
- [ ] Voice obeys the rules like a key: wrong state, no sword, empty bar, cooldown give the same refusal text as a key press.
- [ ] Stop with the browser tab closed: the game keeps working, `/reiatsu voice` prints the link again.
- [ ] (automated reference: `runPhase5`, fixture 202 of 202 phrases matched, 0 of 153 neutral sentences fired; real microphones are
      the part that was never tested.)
- Notes: ______________________________________________

## 7. Performance (3 min, Byakuya is the heavy case)

The budget: 60 FPS on an average PC with 1000 bankai blades and 3000 petals, with a limit setting and distance LOD. Use F3
(FPS) and note the lowest values. `config/reiatsu_test_client.json`: `fxTier` `CUSTOM`, `bankaiBladeCount` 1000, `maxPetals` 3000
(restart the game after editing). Your PC (CPU / GPU / RAM): ______________________________

| Situation | Tier HIGH | MEDIUM | LOW |
|---|---|---|---|
| Idle, sword in hand (baseline) | ____ FPS | ____ | ____ |
| Rukia shikai release and aura | ____ | ____ | ____ |
| Rukia Absolute Zero (peak) | ____ | ____ | ____ |
| Byakuya bankai with 1000 blades standing (if built) | ____ | ____ | ____ |
| Byakuya scatter, 3000 petals (if built) | ____ | ____ | ____ |
| Hakuteiken (if built) | ____ | ____ | ____ |

- [ ] No stutter longer than half a second when an effect starts (the first cast after launch may hitch once; note it).
- [ ] `reduceMotion` and `photosensitiveSafe` set to `true`: flashes and shake are weaker, the game stays usable.
- [ ] Graphics **Fabulous**: release, auras, the translucent ice blade and glows still look right (this was never checked).
- [ ] Move 40 blocks away from your own effect: far effects thin out (distance LOD) and no blocky artefacts appear.
- Notes: ______________________________________________

## 8. Multiplayer smoke: a second client (3 min with two clients, 6 min with a server)

Automatic part [auto]: `python -I tools/server_smoke.py` (dedicated server with two scripted protocol clients; 43 checks).
What the bots cannot do is draw, so this part is for people. Dedicated server (`gradlew runServer`, EULA accepted in
`mod/run-server`, `online-mode=false` for a local test) or Open to LAN; two game clients with the same jar.

- [ ] Both clients join without errors; the server log has no `ERROR` from `reiatsu_test`.
- [ ] Client A draws and releases a sword: client B sees the model in A's hand and on A's hip (third person), the aura and the
      release flash ring or petals from 10 to 30 blocks away.
- [ ] A casts each ability: B sees the effect at the right place and the frozen or hit mobs; temp ice shows for B and is gone
      for B too.
- [ ] B has its own bar and cooldowns: B's HUD does not change when A casts.
- [ ] B joins late while A is already in shikai: B sees A's aura at once.
- [ ] B walks out of range and back; A leaves while in bankai (or dies): no stuck particles or floating blades on B, no ice left.
- [ ] A and B cast at the same time (two Rukias, a Rukia and a Byakuya): no crash, cooldowns independent.
- [ ] `/reiatsu pvp true` (operator): A's ability can hurt B; without it, B takes nothing.
- [ ] A client without the mod (or with another version) tries to join: write what it sees (a clear refusal is expected, a crash on either side is a failure): ______________
- [ ] Dedicated server only: voice is not started (log line "single player only"); `/reiatsu voice` says the bridge is off. Remote
      voice for a client is not supported yet; do not test it.
- [ ] Latency feel over a real network (not localhost): the effect starts within a fraction of a second after the key.
- Notes: ______________________________________________

## 9. Shunpo and other recent features (2 min)

- [ ] **Shunpo (if built)**: the key is listed in Controls (Reiatsu Test). Press it in shikai and in bankai: you move 8 to 10
      blocks along the look direction in an instant, 4 to 6 translucent copies of you fade out in about half a second, a short
      trail and a sound. You do not pass through walls (aim at a wall: you stop in front of it). Cooldown of 2 to 3 seconds and a
      small reiatsu cost; a quick second press is refused. Nothing happens in SEALED or BASE: ______________
- [ ] Shunpo seen from the second client (section 8): the copies are visible: ______________
- [ ] Right click with the sword draws it (BASE) and does not place blocks or use the item otherwise.
- [ ] `J` while drawn sheathes; `V` also; both leave no half-played animation if pressed in quick succession.
- [ ] Config switches: `hud: false` hides the HUD, `titleCards: false` hides the bankai plate, `show_first_person_hand: false`
      hides the arm.
- Notes: ______________________________________________

## 10. Sign-off

| Scenario | Pass | Fail | n/a | Remarks / issue numbers |
|---|---|---|---|---|
| 1 Take the sword | | | | |
| 2 Draw and release | | | | |
| 3 Shikai abilities | | | | |
| 4 Bankai transition | | | | |
| 5 Bankai abilities, way back | | | | |
| 6 Voice and false triggers | | | | |
| 7 Performance | | | | |
| 8 Multiplayer | | | | |
| 9 Shunpo and extras | | | | |

Blocking issues (crash, world damage, stuck state, wrong item): ______________________________

Also run once on the production setup (official launcher, Fabric Loader install, jar from `gradlew releaseJar`): the manual steps are in
`docs/RELEASE.md`; the three open questions there (mixin refmap, `jdk.httpserver`, Fabulous) belong to this sign-off.
