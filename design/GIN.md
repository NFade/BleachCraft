# Gin Ichimaru (Shinsō, Kamishini no Yari) - design draft v1 (2026-10-14)

Branch `gin-uryu`. Status: DRAFT. Numbers are first guesses in the units of `BalanceConfig` after the rebalance a364d34; tune once the numbers of Rukia and Byakuya are read from code. Canon source: Bleach Fandom (`tools/fandom.py text "Gin Ichimaru" --section 11/12`).

## 1. Canon facts used
- **Shinsō** (God Spear): looks like an ordinary wakizashi, light-blue hilt, S-shaped tsuba. Release phrase **"Ikorose" (Shoot to kill)**. Shikai: the blade glows white and extends at high speed, hits with great force, can be held extended and swung in wide arcs, bends when retracting, max length 100 times its own (nickname Hyapponzashi).
- **Kamishini no Yari** (God-Killing Spear): the bankai does NOT change appearance (subterfuge). Same ability as the shikai but vastly faster, longer, stronger.
  - **Butō** (two hands, hilt on the chest): extension and contraction almost invisible. **Butō: Renjin**: the same repeated many times, a mass of blades.
  - **"Korose, Kamishini no Yari"**: the blade turns to dust for a moment while extending and retracting, a poison dissolves cells; Gin can leave a sliver inside the target and dissolve it at any time by saying the name with a hand raised.
- Also: kido **Hakufuku** (hides a reiatsu from perception), shunpo expert, one-handed fighter, no real defense.

## 2. Identity and balance rule
Glass cannon: range, precision, deception. No barrier, no control immunity, no freeze. Every strong cast roots Gin for a moment (the price of the extension), so a good opponent punishes a miss. His highest burst must not exceed Byakuya's hakuteiken (24 HP, the damage ceiling of the roster). The sliver dissolve ignores Byakuya's barrier cut (it is poison, not a hit); the thrusts do not.

## 3. States
Standard graph SEALED -> BASE -> SHIKAI -> BANKAI (45 s timer back to SHIKAI), same keys: J draw, R release, G bankai, V seal, Z/H/B abilities. Bankai costs no reiatsu; the Rage gate is the same as for the others (open question 2).
- **Visual rule (canon):** BANKAI looks exactly like SHIKAI (same wakizashi), only a thin white dust trail on the blade as a cheap tell. Saves a model.
- Item: Shinsō wakizashi (`gin_shinso`), scabbard in the left hand like the others.

## 4. Abilities
| Slot | State | Name | Behaviour | Cost | CD |
|---|---|---|---|---|---|
| Z | SHIKAI | **Ikorose** | Instant segment, length 14, width 0.6. First target: 5 HP + knockback 1.5. Gin rooted (Slowness IV) 0.4 s after. A sword block or parry cancels it (canon: Haineko held it). | 10 | 4 s |
| H | SHIKAI | **Hyapponzashi** | The blade stays out and sweeps a 120 degree arc, radius 8, over 0.8 s: 3 HP per target (max 8), each once. 1.0 s wind-up with root, 0.5 s recovery. | 18 | 9 s |
| B | SHIKAI | **Snake Hook** | Segment up to 16: on a block or a mob the blade sticks and pulls Gin toward it (server-side velocity, no teleport, stops 1.5 blocks short); on a player it also deals 2 HP. Mobility, not damage. | 12 | 8 s |
| Z | BANKAI | **Butō: Renjin** | Gin rooted 1.2 s (two-hand pose). 8 thrusts over 1.0 s along the look vector, length 24, each 1.5 HP to the first target on the line (armor ignored, i-frames reset per thrust, as the mod's other multi-hit abilities). Total up to 12 HP. | 28 | 14 s |
| H | BANKAI | **Kamishini no Yari / Korose** (RECAST) | Press 1: instant segment, length 48, 9 HP, leaves the **sliver** on the target for 12 s (a glow visible to Gin only). Press 2 (same key) within 12 s on a marked target within 64 blocks: Gin raises a hand, 1.5 s channel (rooted), then the target dissolves: **18 HP**, armor ignored, not reduced by the barrier. The recast is free. | 25 (press 1) | 12 s after press 1, 30 s after the dissolve |
| B | BANKAI | **Hakufuku** | Gin is hidden (no glow, no particles, Invisibility 3 s). His next thrust within 4 s shows no white flash. Broken by taking damage. | 15 | 18 s |

Merged-pair rule: "mark" and "kill" are ONE ability with a recast, not two slots. This frees slot B for the deception ability. Hakufuku gives no damage bonus (open question 1 would add one).

### Cleanse and counterplay
- The sliver is removed by: a milk bucket, the target's death, the 12 s timer, the target farther than 64 blocks, Gin dying or leaving BANKAI.
- Channel interrupt: a single hit on Gin of 4 HP or more, knockback, any control effect, or his own state change. After an interrupt the cooldown is 15 s instead of 30 s.
- A totem of undying pops against the dissolve like against any damage.
- Combo budget: press 1 + recast = 27 HP, 25 reiatsu, about 3 s. The cooldowns allow the full combo about 1.5 times in a 45 s bankai. The peak is close to Byakuya's hakuteiken (24 HP); if tests show it is too high, lower the dissolve to 15.

## 5. Interactions
- **Renjin refreshes the sliver:** a marked target hit by Renjin gets the sliver timer reset to 12 s. Keeps the mark alive in a long fight.
- **Barrier (Byakuya):** the thrust and Renjin are cut by 80 percent; the sliver is placed only if the thrust deals at least 1 HP after the cut; the dissolve ignores the barrier.
- **Frost field (Rukia bankai):** Gin fights at range, the slow hurts him little; Snake Hook is his escape.
- **With Uryū:** a target trapped by Sprenger is rooted, ideal for the channel. Federzwinger silences casting, so it also stops a Korose recast (see URYU.md).

## 6. Technical notes
- Hitscan: reuse `Targeting.segment` and `firstOnRay` (as Shirafune), no projectile entity.
- New small mechanisms, added without editing Rukia or Byakuya: (1) **recast** (a second phase on the same key, per-player state next to the cooldown attachment), (2) **mark** on a living entity (`GinSliver`: target uuid and expire tick, server map, synced to Gin only for the glow), (3) **cast root** (Slowness plus an attribute modifier removed by tick), (4) **self-pull** velocity.
- Client: the blade is a stretched OBJ segment along the look vector for about 0.1 s (like the existing beam drawing); only the wakizashi model is new.
- Voice phrases (canon): "Ikorose, Shinsō" (release), "Korose, Kamishini no Yari" (recast), "Kamishini no Yari" (bankai).
- Tests: pure-core tests for the recast state, the sliver lifetime, the channel interrupt rules and the damage numbers; a harness scenario later.

## 7. Open questions for the user
1. Add a Hakufuku damage bonus (flat +3 HP) or keep it purely deceptive?
2. Keep the Rage 80 gate for the bankai as for the others?
3. White dust tell on the bankai blade, or fully identical to the shikai?
