# Byakuya: steerable blade swarm and damage in flight (2026-10-12)

Status: compiled, unit tests green (`./gradlew test`, `compileClientJava`). The game was NOT run: everything about feel, visuals and
network behaviour in a real session is UNVERIFIED. Nothing committed.

## What the player gets
- Cast mode_attack (Byakuya shikai, key Z, also inside bankai). While the petals are out, HOLD the same key (Z): the swarm follows
  the look point (block under the crosshair, or the first valid mob in front of it). Release: the swarm finishes its current
  trajectory, hangs until tick 30 after the cast and returns, exactly like an unsteered cast.
- Holding past tick 30 keeps the swarm out (up to 70 ticks = 3.5 s from the cast), then it returns. Cooldown of the ability is 3 s.
- Blades hurt on the way out, while hanging/moving at the target and on the way back (not only at the landing point).
- No hand gesture (animations come later). Voice casts have no key, so they fly unsteered.

## Data flow
1. Client: `ReiatsuKeys.castSlot` remembers the key (`BladeSteer.arm`), the server answers OK, `ClientNet.onResult` calls
   `BladeSteer.onAccepted`. While the key is down `BladeSteer.tick` sends `SteerC2S(holding, look dir)` every 2 ticks (10 Hz,
   `fx_override.json` key `steer.sendTicks`), and one `holding=false` on release / screen open / 100 ticks.
2. Server: `ZanpakutoManager.performCast` starts `BladeFlights.start` after the usual cast (effect event 30, phases and landing
   burst are unchanged). `SteerC2S` -> `BladeFlights.onSteer` -> `core/swarm/SteerGate` (rate limit, vector sanity, cone check
   against the server's own look vector, timeout). Each tick `BladeFlights.run` turns the validated direction into a world target
   (`steerTarget`: block ray, or first valid entity), `core/swarm/BladeFlight.step` moves the head (turn and speed limits),
   a block raycast stops the head at walls, then the covered segment is swept for damage.
3. Server -> clients: `SwarmHeadS2C` (caster id, phase, server tick, head xyz) every 2 ticks and on each phase change, to the
   caster and trackers. `SwarmFx.onHead` records a `core/fx/HeadTrack`; `SwarmMath.attackTracked` draws the ribbon petals along
   it (trailing 0..0.3 s behind the head, band across the travel direction while moving, cutting envelope while hanging, fall into
   orbit when the head is back). Without head packets the old closed-form flight to the aim point is used (fallback).
4. Landing burst (phases 10/12/14/20 ticks, 2.0 HP each, radius 3 at `ctx.aim`) is untouched for unsteered casts; for steered casts its
   centre is the head (`BladeFlights.landingCenter`). Its hits start the flight hit cooldown (`markLanding`).

## Numbers (defaults, `FlightConfig.defaults()`)
| key | default | meaning |
| --- | --- | --- |
| speed / backSpeed | 30 / 30 b/s | outward speed = largest sideways speed; return speed (same as client ribbon 30 b/s) |
| turnDegPerSec | 200 | head direction turn around the caster's eye, at close range; far away the sideways speed cap (30 b/s / r) is stricter |
| startDelayTicks | 4 | blades stay at the hand (no damage) |
| returnTick / maxTicks | 30 / 70 | return time when not held (old envelope length) / hard limit while held |
| hitRadius | 1.6 | damage sphere around the head |
| hitDamage | 1.0 HP | per flight hit (landing hit is 2.0 HP) |
| hitCooldownTicks | 8 | same target, same leg; return leg may hit after 4 |
| maxHitsPerTarget | 6 | flight hits per target per cast (landing hits do not count, they restart the cooldown) |
| maxTargetsPerTick | 8 | |
| maxSweepPerTick | 6.0 | a longer step (caster shunpo) is not swept |
| steerTimeoutTicks | 6 | steering lapses when no packet for this long |
| steerRatePerSecond | 20 (burst 5) | packets above this are dropped |
| steerLookToleranceDeg | 35 | a sent direction further from the server's look is replaced by the server look |
| sendEveryTicks | 2 | head packets to clients |
| enabled / steerEnabled | true / true | kill switches (`enabled=false`: old behaviour only) |

Where: server file `config/reiatsu_test.json`, section `"bladeFlight"` (written with defaults on first start, read at
SERVER_STARTED, so restart the world/server after editing; same loader style as `"kido"`, `ModConfig.loadFlight`). Range of the
flight = ability `AIM_RANGE` (32 b) from `BalanceConfig`. Client visuals, `run/fx_override.json` (hot reload): `swarm.tracked`
(1, 0 = old closed-form flight), `swarm.trackDelay` (0.12 s), `swarm.trackLag` (0.30 s), `steer.sendTicks` (2).

Balance note: unsteered worst case per target = 4 landing hits x 2.0 = 8 HP (as before) plus flight hits only while the head
is moving (stationary head at the aim does not re-hit): typically +1 on the way out, +1 on the way back, so about +2 HP (25 percent).
Steered sweeping of one target is capped at 6 flight hits = 6 HP plus the landing burst.

## Rules honoured
`Targeting.refusal/valid` (players only with `/reiatsu pvp true` and server pvp), `Targeting.hurt` (DamageTypes.INDIRECT_MAGIC with
the caster as attacker: not a melee hit, so the sword parry does not see it; barrier, Creative, team and i-frame handling and the
`[dmg]` log are the shared ones), blocks stop the head (a target behind a wall is still reachable with the sphere radius, like the
other sphere abilities).

## Reading the log
- `[steer] config: ...` at server start (values in use).
- `[steer] <name> flight start target=(x, y, z) range=R`.
- `[steer] <name> hold start tick=N dir=(...)` / `hold end tick=N phase=...` / `hold lapsed (no steer packet ...)`.
- `[steer] <name> sent direction is more than 35 deg from the server look ...` (once per flight; a modified client or lag).
- `[steer] <name> exceeds 20 steer packets per second` / `invalid direction vector` (once per flight).
- `[steer] <name> flight end (returned|cancelled|replaced) ticks=N steered=bool packets ok= dropped= clamped= flightHits= landingTargets=`.
- Damage: `[dmg] byakuya.shikai.mode_attack#flight <caster> -> <target> asked=1.0 lost=... result=DAMAGED|CREATIVE|BARRIER|...`;
  landing hits keep `[dmg] byakuya.shikai.mode_attack#0..3`. Refused players (pvp off): `... refused=PLAYERS_OFF` once per flight.

## Tests
`core/swarm/*` (pure): `SteerAndSweepTest` (turn limit, far-swarm slowdown, cone, swept sphere through thin walls and 6-block steps,
rounded corners, hit cooldown/legs/cap), `BladeFlightTest` (phases, max step per tick, windup, turn limit per tick, hold/release
timetable, teleport, range clamp, gate rate limit/vector/clamp/timeout, config round trip), `core/fx/HeadTrackTest` (track
interpolation, tracked ribbon behind the head, orbit before/after, no teleporting petals, fallback). `CorePurityTest` covers the imports.

## Check in game (all UNVERIFIED)
1. Survival, Byakuya shikai, dummy mobs: Z tap = old look, hits along the line. Z hold + turn the camera = ribbon bends toward
   the crosshair, turn is smooth, not instant. Release = continues, returns at about 1.5 s.
2. Server log lines `[steer]` and `[dmg] ...#flight` (1.0 per hit, about every 8 ticks per mob, again on the return).
3. Two clients (`runClient2`): the other player sees the same path (head packets go to trackers); `/reiatsu pvp true` hurts TestB; Creative
   target takes nothing (`result=CREATIVE`).
4. Look through a wall: the head stops at the block. Shunpo during the flight must not produce a line of damage.
5. Feel: `swarm.trackDelay` (smoothness vs responsiveness), `turnDegPerSec`, `hitDamage`. 10 Hz linear interpolation may show corners on fast turns.
6. Known limits: the first-person owner view sees the ribbon about 0.12 s late (render delay); landing burst timing is still the old
   0.5 to 1.0 s even if a far head has not arrived yet; the sound and glow timeline of effect 30 is unchanged (the aim glow now sits at the head).

## Files
New: `core/swarm/{V3,SteerMath,SweepMath,HitCooldown,BladeFlight,SteerGate,FlightConfig}.java`, `core/fx/HeadTrack.java`,
`net/{SteerC2S,SwarmHeadS2C}.java`, `server/BladeFlights.java`, `client/input/BladeSteer.java`, tests `core/swarm/*`, `core/fx/HeadTrackTest`.
Edited: `SwarmMath` (Mode.track, tracked attack), `SwarmFx` (onHead, expiry, glow at head), `PetalSwarmRenderer` (2 lines: feet for the
world head path), `ReiatsuKeys` (arm + tick), `ClientNet` (receiver, sendSteer, accept hook), `ModNetworking`, `ModConfig.loadFlight`,
`AbilityExecutor.modeAttack` (landing centre + markLanding), `ZanpakutoManager` (start flight, init).
