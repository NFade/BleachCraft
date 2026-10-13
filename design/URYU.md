# Uryū Ishida (Quincy) - design draft v1 (2026-10-14)

Branch `gin-uryu`. Status: DRAFT. Numbers are first guesses; tune after the numbers of Rukia and Byakuya are read from code. Canon source: Bleach Fandom pages "Uryū Ishida" (sections 14-17), "Sprenger", "Licht Regen", "Blut", "Seele Schneider".

## 1. Canon facts used
- No zanpakutō. Weapon: **Heilig Bogen** (a bow of Reishi, drawn from the air) firing **Heilig Pfeil**. Reishi is gathered from the surroundings, so it is easier where Reishi is dense.
- **Trajectory control:** he can curve arrows after firing (up to 3 at once). **Projectile negation:** an arrow charged enough cancels an incoming projectile on impact.
- **Hirenkyaku** (Quincy flash step, also a Reishi platform under the feet). **Blut Vene** (defensive, durability) and **Blut Arterie** (offensive, damage to a bankai-wielder). **Seele Schneider** (Reishi blade/dagger, used as a sword).
- **Sprenger:** five Seele Schneider make a pentagon seal; the fifth binds the legs of whoever stands inside; after the charging time a massive explosion inside the borders. Impractical in a fight without a partner to stall.
- **Quincy: Vollständig** (two sharp wings, flight). **Flechettes:** a swarm of flechette-shaped Heilig Pfeil under his control (like Senbonzakura), usable to hit, to bind, to wrap in a cage and detonate. **Composite Heilig Pfeil / Sternenstaub** (a giant arrow made of flechettes, clashed with a Getsuga). **Federzwinger:** five rods fall around the target forming a pentagonal cage that drains Reiatsu; a shinigami inside cannot use shikai or bankai. **Licht Regen:** a near-instant barrage of Heilig Pfeil. **Reishi Trap:** markings that fire arrows when the enemy passes. **Antithesis** (his Schrift) is NOT used (too large and too abstract).

## 2. Identity and balance rule
Marksman: the highest per-hit damage at range, but only if he stands still. No self-heal. Weak defense (the only defence is distance and a short Blut Vene window). The price of power is exposure: the strongest shots need Focus, which the movement erases.
- Peak burst must not exceed Byakuya's hakuteiken (24 HP).
- Where Gin roots himself for a moment, Uryū roots himself for a long time before the shot (Focus), so the two differ in rhythm.

## 3. Own mechanic: Focus
- Stacks 0..3. +1 stack every 14 ticks (0.7 s) while the player is on the ground, not sprinting, and moved less than a tiny distance (`speed < 0.02`). Any movement beyond that, a jump, a hit that causes knockback, or Hirenkyaku sets it to 0. In SHIKAI (Blut Arterie) the interval is 10 ticks.
- Focus is a server-side counter, synced to the owner for 3 pips on the HUD under the reiatsu bar.
- The next Heilig Pfeil (basic shot or Z) consumes all stacks: 0 = normal, 1 = +25 percent damage and speed, 2 = +60 percent, 3 = +100 percent, pierces one target and ignores the arrow gravity curve.

## 4. States
Graph SEALED -> BASE -> SHIKAI -> BANKAI as for the others, same keys. Canon-based adaptation:
- **SEALED:** the Quincy cross pendant (item `uryu_cross`), no weapon.
- **BASE:** Heilig Bogen drawn (the pendant becomes a bow in the hand). Right click holds to charge a basic Heilig Pfeil (1 s draw, 4 HP base, no reiatsu cost, uses Focus). **BASE has no Z/H/B abilities** (the mod rule), but the basic shot works.
- **SHIKAI = Blut Arterie** (offensive Blut): Focus builds faster, basic shot +20 percent, abilities Z/H/B unlocked. Release phrase: "Blut Arterie".
- **BANKAI = Quincy: Vollständig** (wings, 45 s timer back to SHIKAI, no reiatsu cost, same Rage gate). Wings are a visual; no free flight (a short glide: holding jump in the air slows the fall).

## 5. Abilities
| Slot | State | Name | Behaviour | Cost | CD |
|---|---|---|---|---|---|
| Z | SHIKAI | **Heilig Pfeil: Focus shot** | A projectile with real flight (speed 2.2 blocks per tick, gravity small), 7 HP base. Consumes Focus as in section 3. Can be dodged. Hold the key to aim, release to fire (client sends the aim at release). | 12 | 3 s |
| H | SHIKAI | **Sprenger** | Aimed ground point within 20: five Seele Schneider land in a pentagon, radius 3. At 0.8 s the legs of everyone inside are bound (Slowness V, no jump) for 2 s. At 2.0 s the pentagon explodes: 9 HP in the whole area. **A fully focused arrow (3 stacks) that hits the pentagon detonates it at once, with +50 percent.** Gin-style counter: leaving before 0.8 s. | 25 | 14 s |
| B | SHIKAI | **Hirenkyaku** | A fast dash 8 blocks in the look direction (server velocity, not a teleport), stays above any pit for 0.6 s (a Reishi platform: blocks are NOT placed, the fall is cancelled). Sets Focus to 0. | 10 | 6 s |
| Z | BANKAI | **Licht Regen** | Aimed point within 28: 12 arrows fall over 1.2 s inside radius 4, each 1.2 HP, armor ignored, the caster is safe. Can be cast while moving, so it is the answer to no Focus. | 28 | 16 s |
| H | BANKAI | **Federzwinger** | A projectile (the rods) at an aimed enemy: on a hit, a 3 s pentagonal cage around the target: **drains 30 Reiatsu and silences the target: no ability cast, no release, no shunpo for 3 s** (they can still swing the sword and move). Does not work through a Byakuya barrier. Cooldown applies at half if it misses. | 40 | 35 s |
| B | BANKAI | **Sternenstaub** | A giant arrow (needs Focus 3, consumes it). Line, length 40, width 2, pierces all: 20 HP to the first, 12 HP to the following. Clashes with and cancels an enemy beam (Hakuteiken) of lower damage on contact. | 50 | 40 s |

Merged-pair rule: Sprenger is one slot (trap + detonation), not two. Federzwinger already merges "drain" and "silence".

## 6. Interactions
- **Sprenger + focused arrow:** the main skill combo (see above). 3 stacks need 2.1 s of standing still next to your own trap, which is the exposed moment.
- **Federzwinger + Gin Korose recast:** a silenced Gin cannot recast Korose; a silenced Rukia cannot cast ability or release.
- **Federzwinger vs Byakuya:** the barrier absorbs the projectile (no cage); Sprenger and Licht Regen are cut 80 percent while it is up.
- **Projectile negation (stretch goal):** a fully focused arrow that hits an enemy projectile entity cancels it. Only meaningful if the mod has projectile entities; Rukia/Byakuya are mostly hitscan or area, so it is not in v1.

## 7. Technical notes
- The mod has no ability projectile yet (verify in the survey): add a minimal **server-simulated projectile** (a list of (pos, vel, owner, damage, expire) stepped per tick, no new entity type), drawn by the client from effect packets. Reuse `Targeting` for the hit tests.
- New mechanisms, added without editing Rukia or Byakuya: Focus counter (per player, server), pentagon area task, silence status (a tick-based map checked at cast time for any character), reiatsu drain on a target player.
- Item and models: `uryu_cross` (SEALED), `uryu_bow` (BASE, SHIKAI, BANKAI), Seele Schneider mini-model, wings (existing wing code of Hakuteiken first person may be reused), arrow tip. Blender models later.
- Voice phrases: "Heilig Bogen" (draw), "Blut Arterie" (release), "Quincy: Vollständig" (bankai), "Licht Regen", "Sprenger", "Federzwinger", "Sternenstaub".
- Tests: pure-core: Focus rules, the projectile stepping, the silence rules, damage numbers.

## 8. Open questions for the user
1. Is Focus (stand still to build power) the wanted unique mechanic, or charges/ammo?
2. Silence of 3 s plus 30 Reiatsu drain (Federzwinger): wanted on players, or too strong for the first version?
3. Short glide in bankai instead of full flight: OK?
