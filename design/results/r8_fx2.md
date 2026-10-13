# r8 fx2: block / parry feedback, Byakuya petal cage readable, Rukia Shirafune re-timed (2026-10-13)

Everything about look and sound is UNVERIFIED in the game (the game was not started). Every number is tunable at runtime through `FxTune`
keys in `mod/run/fx_override.json` (listed below); defaults come from maths (spark budgets, spec numbers), not from looking.

## 1. Block and parry feedback (Sekiro style)

Server (one event id 41, `EffectEventS2C`, reused, no new payload):
- `ServerFx.blockFeedback(defender, attacker, parried, damage)` (melee block / parry, called by `BlockParry.onHit`) and the new
  `ServerFx.blockFeedbackAbility(defender, caster, damageCut, bladeSource)` (called by `Targeting.hurt` when the 30 percent ability block
  really cut the hit; damage numbers are not touched). Both go through `blockEvent`: x y z = CONTACT POINT (chest height, 0.6 block from the
  defender towards the source), d = horizontal vector to the source, target = the source, params = `{variant, damage, blade}`;
  variant 0 BLOCK, 1 PARRY, 2 ABILITY (`core/fx/BlockFxMath`). The event goes to everybody who sees the defender (before: only the defender
  reacted); the world sparks and the sound are now drawn by every client at the contact point.
- Rate limit `core/combat/FeedbackLimiter`: per defender 4 events per second, a parry may bring it to 6 (so a flood of ability ticks never swallows a parry).
  When the limiter refuses, `BlockParry` plays the old plain server sound (parry: trident hit + knockback, block: shield block), so the block is never silent.
  The old server-side spark particles of the parry and the server sound in the normal case are gone (the client event replaces them).
- Payload: 1 + 1 + 4 + 3 x 8 + 4 + 1 + 4 + 3 x 4 about 80 bytes, at most 4 to 6 per second per defender.

Client `client/fx/BlockFx` (all through `FxGlowBatch` streak sprites, `FxSound.play`, `ScreenFx`):
- BLOCK: sparks = `24 + 3 x damage` (damage capped at 8, so 24 to 48), in 3 waves 0.045 s apart, each wave 0.28 block further along the blade axis
  (the scrape slides); weights 1 / 0.7 / 0.49. Blade axis = across the defender's front, tilted 35 degrees, side random per event. Spark = thin streak that
  follows its velocity (3.5 to 11 blocks/s, drag 0.88/tick, gravity 9), white / cream / amber / orange, plus a lingering dot every 4th. A white 4-point glint,
  a soft amber glow and a bright 1.5 block scrape line along the blade axis. Sound layers (pitch scatter +-10 percent): item.shield.block, block.anvil.land,
  entity.iron_golem.damage, block.chain.hit (+0.03 s), block.grindstone.use (scrape, +0.02 s), item.trident.hit (+0.05 s). Defender: shake 0.6 deg / 0.16 s + the existing FpMotion recoil.
- PARRY (clearly different): sparks = `70 + 4 x damage` (70 to 102); 70 percent are a white-gold burst over an 80 degree cone towards the attacker (6 to 19 blocks/s),
  30 percent the sliding scrape; two expanding rings standing in front of the defender (thin pale, radius 0.2 to 2.2 in 0.25 s; soft gold, to 3.4 in 0.32 s),
  a core flash, a 4.6 block flare, a big star, 6 radial crack lines, 16 slow gold embers 0.06 s later. Sound: amethyst chime pitch 2.0 + a second chime at 1.5 (+0.035 s),
  bell 2.0, trident hit 1.9, trident hit_ground 1.7, player crit (the deflect crack) 1.6, shield block 1.7, iron golem +0.03 s. Defender screen: gold flash 0.28,
  dark vignette punch 0.35 (0.03 s in, lets go after 0.08 s), `ScreenFx.impactFrame` (the existing hit-stop-like frame), shake 1.1 deg / 0.18 s, FOV kick 1.6 deg,
  sparks in front of the face (10). The parried attacker gets a 0.5 deg / 0.14 s shake. Reduce motion / photosensitive safe are respected because ONLY `ScreenFx` is used
  (flash capped to 0.40 and the 3-big-flashes-per-second limiter, shake and FOV off, vignette capped to 0.15, the impact frame becomes a short flash).
- ABILITY (30 percent cut): `10 + 1.5 x damage` sparks (10 to 22), cool white-blue when the source is not a blade (no scrape: 1 wave, no grindstone / chain / anvil),
  small glint, item.shield.block 1.35 at 0.55 plus block.amethyst_block.hit; a blade source (`DamageTypes.PLAYER_ATTACK` / `MOB_ATTACK`; today no ability uses them) adds anvil + golem and 2 waves.
  Defender shake 0.3 deg.
- Distance cut of the sparks: 1 up to 24 blocks, 0.5 up to 48, 0.25 up to 64, none beyond; hard cap 120 sparks per event; `FxSound` caps (same id 0.05 s, 24 live) apply.

FxTune keys (defaults): `block.sparks` 1.0 (count scale, 0 = off), `block.glow` 1.0, `block.sound` 1.0, `block.tilt` 35 (deg), `block.speedMin` 3.5, `block.speedMax` 11,
`block.gravity` 9, `block.streakTime` 0.035, `blockhit.shake` 0.6, `blockhit.shakeTime` 0.16; `parry.burst` 1.0, `parry.ringRadius` 2.2, `parry.glow` 1.0, `parry.sound` 1.0,
`parry.pitch` 1.0, `parry.flash` 0.28, `parry.flashColor` #FFF0C8, `parry.vignette` 0.35 (0 = off), `parry.impact` 1 (0 = off), `parry.shake` 1.1, `parry.shakeTime` 0.18,
`parry.fov` 1.6, `parry.fovTime` 0.18, `parry.sparks` 10 (face sparks), `parry.attackerShake` 0.5; `ability.sparks` 1.0, `ability.shake` 0.3.
Old defaults changed: `parry.shake` 0.5 to 1.1, `parry.fov` 1.0 to 1.6, `parry.flash` 0.10 to 0.28, `blockhit.shake` 0.3 to 0.6.

## 2. Byakuya shikai ability 3: petal cage (`byakuya.shikai.cage`, key B, effect 35)

Mechanics are untouched (radius 5, aim range 22, 6 pulses at ticks 12, 22 ... 62, `Targeting.sphere` against the closest point of the victim's box, 0.8 HP per pulse, Slowness III 3 s).

Found and fixed a real client bug: `CageFx` took the area centre from the EVENT POSITION, which is the CASTER (`ServerFx.effect` sends `caster.getX/Y/Z`), so the shell and the pulse
flashes were drawn around the caster and not at the aim point / target. The server puts the centre in `params[0..2]`; `core/fx/CageVisual.parse` now reads it there.

New picture (`client/fx/CageFx`, numbers from `core/fx/CageVisual`, which reads the event params the server builds from the spec; `CageVisualTest` proves the radius and every pulse time equal the spec's):
- 0.0 s telegraph: a ground ring at the exact reach on the ground `sqrt(R^2 - dy^2) + 0.3` (dy = centre height above a 1.8 block body; dim projection of R when the sphere floats), 24 rim ticks,
  three rings outlining the sphere (equator and +-0.7 R), a marker at the centre; they stay until the last pulse. A stream of petals from the hand, and 3 waves of petals from the rim (1.3 R) that fly
  into the centre at a speed chosen so they ARRIVE at the first pulse (`convergeSpeed`).
- 0.15 s before every pulse: a rising warning tick (amethyst cluster hit, pitch 0.9 + 0.1 k) and a ground ring shrinking onto the area ring; before the last one a charge note too.
- Every pulse (the damage tick): flash, core flash, THREE orthogonal shockwave rings growing 0.5 to R (exact radius) in 0.3 s, a ground ring to the area radius, 24 petals flung out, shake 0.28 deg,
  boom (entity.generic.explode + firework blast) + chime (pitch +0.1 per step) + petals. Pulse 0 is 1.2 times, the LAST pulse 1.8 times and is the final burst (60 extra petals, big glow,
  gold-white screen flash 0.18 via `x.flash`, firework large blast + bell); the shell opens 0.12 s after it.
- Book text (en + ru page 5 of the Senbonzakura book) was vague ("pulses of damage and slowness for about 5 s"); now: aim, 0.6 s delay, radius 5, 6 pulses 0.5 s apart, hurts everyone inside and renews Slowness III, ground ring shows the area.
  (Numbers are written into the text; they are not book args, so a rebalance has to edit the text.)

FxTune keys: `cage.ringSlack` 0.3, `cage.telegraph` 0.9, `cage.warnLead` 0.15, `cage.boom` 1.0, `cage.converge` 24, `cage.shake` 0.28, `cage.finalFlash` 0.18, `cage.speed` 2.6, plus `rk.count`, `rk.glow`, `rk.size`.

## 3. Rukia Shirafune timeline (`rukia.shikai.shirafune`, effect 22)

Server timing (r8_mech.md section 3): key press, cast time 12 ticks (0.6 s), at the release phase the aim is read again, the effect event is sent and the hit happens, both at the same tick.
Client (`RukiaShikaiFx.shirafune`, table in `core/fx/ShirafuneTimeline`): the clock of the event now starts AT the hit. Before: swirl 0.0, blade 0.3, tip crystal 0.46, contact 0.6, frost sound 1.0, retract 2.0
(all relative to a press-time event). Now: blade 0.0, tip crystal 0.16, contact 0.1 (half the 0.2 s growth: easeOutExpo has covered 97 percent), frost sound 0.4, retract 1.7, end 2.1; the
swirl is now a 0.15 s close-in at the release. Blade-to-retract shape is unchanged (1.7 s). Keys: `shira.lead` (0, delays the whole table), `shira.growTime` 0.2.

Cast telegraph (`client/fx/ShirafuneCast`, local player only): started by `ClientNet.onResult` on `ResultCode.OK` of a SHIRAFUNE request (key or voice), so a refused cast shows nothing (a "local trigger at the key"
would show it for denied casts too). For the cast time (spec: 0.6 s; `shira.castTime` overrides) a pale-blue glow (#BFE4FF) along the sword, brightness `ShirafuneTimeline.gather` (0.15 at the press to 1.0 at
the release, a flicker of 8 percent in the last third), a star on the tip after half the cast, frost motes closing on the tip (1 to 4 per 0.05 s), a faint frost vignette (0.10) closing in, two soft sounds.
The release event ends it (`ShirafuneCast.onRelease`), a cast that never releases lets go after cast time + 0.2 s. The sword position is a pose guess like `ReleaseHold`'s pink glow (first person from the camera:
the earlier guess floated beside the sword, so check it first; `shira.castFpForward/Right/Up/Length` move it, `shira.castGlow` 0.6 dims it, `shira.cast` 0 switches it off).

## Tests (new)
`core/fx/BlockFxMathTest` (budgets, caps, waves add up, axis, contact point, velocity tables, cone, ring), `core/combat/FeedbackLimiterTest`, `core/fx/CageVisualTest` (spec equality of radius and pulse times,
ground ring, warnings, convergence, shockwave), `core/fx/ShirafuneTimelineTest` (cast time = release phase 0.6 s, the event clock starts at the hit, the removed 0.3 / 0.5 s offsets, gather curve, mote budget).
No existing test needed a change.

## Files
New: `core/fx/{BlockFxMath,CageVisual,ShirafuneTimeline}.java`, `core/combat/FeedbackLimiter.java`, `client/fx/ShirafuneCast.java`, the four tests above, this file.
Changed: `server/{ServerFx,BlockParry,Targeting}.java`, `client/fx/{BlockFx,CageFx,RukiaShikaiFx}.java`, `client/net/ClientNet.java` (3 lines), `lang/{en_us,ru_ru}.json` (cage page).

## What to check in the game
1. Block with right mouse, get hit by a mob / player: sparks slide along a diagonal blade line, metallic clash sound, small shake. Other players see and hear it.
2. Parry (hit within 0.2 s of starting the block): gold-white burst, rings, crack, high ting, flash + vignette + impact frame. With reduce motion on: no shake, short flash only.
3. Block a Tsukishiro / petal / kido hit: the light version (cool sparks, soft shield sound), no more than 4 events per second.
4. Cage (B, Byakuya shikai) on a mob and on the ground: ground ring at the right size, pulses on the ring centre (not the caster), explosion and damage tick together, final burst on the sixth.
5. Shirafune: pale-blue gathering for 0.6 s, then blade and contact exactly with the damage; compare with `[dmg] rukia.shikai.shirafune#0` in the server log.
