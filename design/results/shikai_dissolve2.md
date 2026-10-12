# Byakuya shikai: glow and dissolve, round 2 (stronger, Senbonzakura look)

Status: code written, `compileClientJava` and `test` green (DissolveMathTest, new DissolveSwirlTest 9/9). The game was not run: every visual point below is UNVERIFIED. Not committed.

User note after the first test: "no beautiful anime glow when the sword dissolves, and the dissolve itself is weak". Round 1 is in `byakuya_glow.md`.

## What it does now (one release, defaults)

1. Pink hold (`release.pinkTime`, 0.4 s, unchanged): the blade tint goes silver to pink as before. New: a stack of 3 translucent emissive shells (bloom) swells around the blade, and the world glow runs along the sword with a slow pulse (about 2.2 Hz, 15 percent; none in the calm modes).
2. Dissolve (`release.dissolveTime`, now 0.85 s; 0.6 to 1.0 is the intended range): the front runs from the tip down to the hilt (smoothstep). The blade is cut into about 500 small fragments (the 86 blade quads, each cut 3 x 2). Each fragment breaks off when the front reaches it. The glow level swells to about 1.5 around p = 0.28 and falls to 0 over the last 18 percent; the blade colour goes pink to white-pink and back; one short flash at the break (50 ms rise, no repeat); a hot flare rides the front.
3. Fragment flight (`DissolveSwirl`, pure math): every fragment spirals up and sideways about the blade axis (opening radius, upward speed, tumble). In the hand (model space, so it follows the sword in first and third person) it flies for `release.shardLife` (0.28 s), shrinking and fading, white hot to pink. At the same place and moment the world petal of the same fragment index takes over (same trajectory function): a lit petal card with the swarm's own texture and card shape.
4. Petals live `release.petalLife` (0.9 s, +- 25 percent), with a glowing streak trail and a soft pink halo; sparks (STAR4/SPECK glow sprites) and a faint pink haze fall off the front.
5. Handover to the swarm: in the last part of its life (u 0.45 to 1) each petal blends towards an orbit slot of the owner's `SwarmFx` state (`SwarmMath.orbit`, read only) and fades out (u 0.7 to 1). The swarm itself is untouched: its release stream starts as before at the end of the pink hold; movement and damage logic not changed. Optional `release.swarmDelay` delays the swarm start into the dissolve (0 by default, see risk 5).
6. The hilt keeps the old behaviour (pink, back to its own colour), plus a soft light on the guard and grip that outlives the blade by 0.55 s.

## Where things are

- `core/fx/DissolveMath.java`: added `invEase`, `detachProgress` (when the front reaches blade position s), `glowLevel`, `breakFlash`, `hiltAfterglow`.
- `core/fx/DissolveSwirl.java` (new, pure): per fragment parameters hashed from (seed, index), `offset(prm, tau)` (blade lengths), size/alpha/heat envelopes, `petalCount(quality, motion, cap)`: about 110 at effectQuality 0.25, 200 at 0.5, 300 at 1 (x0.6 with reduceMotion), capped by `maxFxParticles / 4` and 400.
- `client/fx/ShikaiDissolve.java` (rewritten): runs per caster, sword pose, petals (drawn at `AFTER_ENTITIES`), additive glow source (halo, beam, hilt, front flash, petal halos and trails), sparks and haze spawned into `FxGlowBatch` (stops at 70 percent of `maxGlowSprites`), `look()` for the item model, public `releasePoint(id, out)` (world middle of the blade, for a swarm start if the swarm agent wants it).
- `client/model/ObjItemUnbakedModel.java`: `buildShards` (fragments, radial vectors, lit material per fragment), `BakedStates.shards`.
- `client/model/ObjItemBakedModel.java`: `emitBloom` (hold), `emitShardDissolve` (hilt, remaining blade fragments, bloom shells, flying fragments), `captureDissolvePose`. The round 1 clip path (`emitDissolve`) stays as the fallback when `release.shards` is 0.
- `client/mixin/HeldItemRendererMixin.java`: 7 lines at the end of the HEAD hook: while a release effect is alive, the model-to-view matrix of the item goes to `ShikaiDissolve.capturePose`.
- `client/fx/ReleaseHold.java`: its guessed glow position is skipped when a real pose exists (`release.glowFromPose` 0 brings the old one back). `FxClient`: `ShikaiDissolve.init()`. `FxEvents`: `release.swarmDelay`. `FxScene`: no late swarm while a delayed start is pending.

How the world part knows where the sword is: the item render hands over the matrix stack after the display transform and the centre shift. Third person: world offset = camera rotation x (matrix x model point), plus the camera position (the stack carries the inverse camera rotation). First person: `GameRenderer#renderHand` pre-multiplies the camera rotation into the stack, so the matrix gives the world offset directly. Read in the 1.21.1 sources, not seen in the game. In first person the pose is one frame old (the hand renders after the glow pass).

## First person and comfort

- First person: halo and flash sprite sizes are capped by the distance to the lens (about half of it), intensity x `release.glowFpScale` (0.45); petals nearer than `release.fpMinDist` (0.45) are not drawn, up to `swarm.fpFade` (5) they are smaller (`swarm.fpSize`) and their glow is weaker (`swarm.fpGlowMin`); sparks and haze x0.6.
- `reduceMotion`: no pulse, no star spin, flash x0.6, 60 percent of the petals, spiral speed x0.6, lift x0.7, half the sparks. `photosensitiveSafe`: flash x0.25, no pulse, spin or twinkle. `screenFxScale` scales the flash. `glowIntensity` applies through the glow batch. `effectQuality`: petal count (above), trail stride 1/2/4, spark rate x1/0.6/0.3. Remote casters: every 2nd/4th petal beyond 32/64 blocks.

## fx_override.json keys (all optional; flat keys, hot reloaded)

Old keys kept: `release.dissolveTime` (default now 0.85; 0 turns the dissolve off), `release.dissolveTipFirst` (1), `release.dissolveGlow` (0.45, now the base alpha of the bloom shells), `release.pinkTime`, `release.pinkColor`.

| key | default | meaning |
|---|---|---|
| `release.glowColor` / `release.glowCore` | `#FF7FC4` / `#FFE9F6` | outer and hot core colour of the glow |
| `release.glowHalo` | 0.55 | strength of the additive halo along the blade (0 = off) |
| `release.glowSize` | 0.6 | halo size, in blade lengths |
| `release.glowBloom` | 1.0 | x the bloom shell alpha (with `dissolveGlow`) |
| `release.glowBloomWidth` | 1.0 | x the shell offsets (0.006, 0.015, 0.03 model units) |
| `release.glowPulse` | 1.0 | pulse amplitude factor (0 = none) |
| `release.glowFlash` | 0.8 | break flash and front flare strength |
| `release.glowFpScale` | 0.45 | first person factor of the world glow |
| `release.glowFromPose` | 1 | 0 = old guessed glow position of the hold |
| `release.shards` | 1 | 0 = round 1 clip dissolve instead of fragments |
| `release.shardLife` | 0.28 | seconds a fragment flies in the hand before the petal replaces it |
| `release.shardReach` | 1.0 | x the in-hand fragment displacement |
| `release.petalCount` | 0 | 20 to 400; 0 = by tier |
| `release.petalLife` | 0.9 | petal life, seconds |
| `release.petalSize` | 1.0 | x petal size |
| `release.petalLift` | 1.6 | upward speed, blade lengths per second |
| `release.spiral` | 1.0 | turning speed (1 = about one turn per second) |
| `release.spiralRadius` | 0.7 | outward reach, blade lengths |
| `release.trail` | 1.0 | petal trail strength (0 = off) |
| `release.sparks` / `release.smoke` | 1.0 / 1.0 | spark / haze rate factor |
| `release.fpMinDist` | 0.45 | first person: nearest drawn petal distance |
| `release.dissolveFpWorld` | 1 | 0 = no world part (petals, halo, sparks) in first person |
| `release.swarmDelay` | 0 | seconds after the end of the hold at which the swarm starts |

Example for a faster, denser look: `{"release.dissolveTime": 0.7, "release.petalCount": 400, "release.spiral": 1.3, "release.glowHalo": 0.7}`.

## Risks and what to check in the game (UNVERIFIED)

1. Does the glow follow the blade in third person and first person (pose maths above), also during Better Combat swings, and for another player? If the halo floats beside the sword: set `release.glowFromPose` 0 and report which view.
2. First person: does the glow wash out the screen? Lower `release.glowFpScale`, `release.glowHalo`; check that the break flash is not too strong.
3. The bloom shells (translucent emissive, sword texture tinted): z-fighting at the guard, the texture pattern showing through; tune `release.glowBloomWidth` and `dissolveGlow`.
4. Does the tip really break first (tip is up)? `release.dissolveTipFirst` 0 reverses it.
5. Handover: do the petals meet the swarm orbit without a pop at the end of their life, and is the swarm stream from the hand (start at the end of the hold) too early? Try `release.swarmDelay` 0.2 to 0.3. Risk of a delay above 0: an attack event (id 30) that arrives before the delayed start creates its own swarm state and the delayed release start is then skipped.
6. Cost: about 500 fragments x several emits per frame in the hand for about 1.1 s, up to 400 petal cards and several hundred glow quads in the world; look at the frame time at HIGH.
7. Petal count per tier, `reduceMotion` and `photosensitiveSafe` behaviour.
