# Byakuya shikai: pink petal glow and blade dissolve

Status: code written, unit tests green (DissolveMathTest 7/7). Not run in the game (rules): every visual point below is UNVERIFIED.

## 1. Petal glow (third and first person)

`client/fx/PetalSwarmRenderer.java`, `emitGlow`:

- Halo sprites (`PETAL_GLOW`, one petal in 8 within 30 blocks) are now pink (`swarm.haloColor` `#FF7FC8`, was a pale pink-white), slightly larger (`swarm.haloSize` 0.7, was 0.5) and a little stronger (`swarm.haloIntensity` 0.36, was 0.30). Glints, streaks and halos all share a per-petal `fade` value.
- First person of the owner: `fade` = `swarm.fpGlowMin` (0.15) next to the lens, rising smoothly to 1 at 1.5 x `swarm.fpFade` (about 7.5 blocks). The sprite size is scaled by `0.5 + 0.5 * fade` as well, so near petals give a small faint point, not a full-screen pink wash. The old hard cut (no glow closer than 4 blocks) is replaced by this falloff.
- Third person: `fade` is 1, nothing changes except the colour and size above.

## 2. Shikai blade dissolve

Before: the drawn blade turned pink for `release.pinkTime` (0.4 s), then the hand showed only the shikai hilt, i.e. the blade vanished at once.

After: the pink hold runs as before; then for `release.dissolveTime` (0.6 s) the blade stays in the hand, glowing, and breaks away along its length.

- `core/fx/DissolveMath.java` (pure, tested in `DissolveMathTest`): smoothstep progress, front position on the blade axis (s = 0 at the saya mouth/tsuba, 1 at the tip, with a 0.05 margin at both ends), keep test, glow shell alpha (full until 70 percent of the dissolve, gone at 100), hilt colour mix.
- `client/fx/ShikaiDissolve.java`: start/progress per caster, started by `FxEvents` for the Byakuya release (effect id 3) at the end of the pink hold (or immediately when the hold is off); cleared with the other timelines.
- `client/model/ObjItemUnbakedModel.java`: builds the blade's glow shell (the sealed drawn blade's quads pushed 0.0005 out along the normal, emissive material) and the blade axis in model space (from `byakuya_sealed_meta.json`: grip, tip and saya mouth, converted with `AxisMapper.blenderToModel`).
- `client/model/ObjItemBakedModel.java`, `emitDissolve`: while the dissolve runs, three parts are drawn in hand modes:
  1. the shikai hilt, tinted pink at the start and returning to its own colour as the dissolve ends (no colour pop at the end);
  2. the drawn blade, pink, with the quads whose centre lies behind the front removed by a Fabric `QuadTransform` (returning false drops the quad; checked in the renderer API sources);
  3. the glow shell over the remaining blade, alpha = `shellAlpha(p)` times `release.dissolveGlow`.
- The hilt part of the drawn blade (s below 0) is never drawn during the dissolve; the shikai hilt replaces it.
- Direction: top to bottom in the world, as in the anime. The front starts at the tip and goes down to the hilt (`release.dissolveTipFirst` 1, the default). Why the tip is the top: the blade's model axis runs from the saya mouth to the tip along +Y (about (0, 0.99, -0.12)). Applying the item display transforms by hand (`senbonzakura_display.json`: third person rotation [45, 180, 0], first person [-172, 1, -173.1]), the tip ends up above the grip under both common Euler orders, so the held sword points tip up. This is a hand calculation, not measured: the arm pose can tilt it, and it has not been seen in game. `release.dissolveTipFirst` 0 gives hilt first.

The petal swarm is still started at the end of the hold (`SwarmFx.onRelease`), not tied to the moving front. The petals leave from the hand and tip points, as before.

## Hot reload keys (`mod/run/fx_override.json`, read through `FxTune`)

| key | default | meaning |
|---|---|---|
| `swarm.haloColor` | `#FF7FC8` | colour of the petal halo |
| `swarm.haloIntensity` | 0.36 | halo strength (0 turns the halo off) |
| `swarm.haloSize` | 0.7 | halo sprite size (blocks) |
| `swarm.fpGlowMin` | 0.15 | first person glow factor next to the lens (1 = no falloff) |
| `release.dissolveTime` | 0.6 | dissolve duration in seconds (0 = off, the blade vanishes at the end of the hold as before) |
| `release.dissolveTipFirst` | 1 | 1 = the front starts at the tip (top of the held blade, down to the hilt); 0 = from the hilt up |
| `release.dissolveGlow` | 0.45 | strength of the pink glow shell over the remaining blade |
| `release.pinkTime` / `release.pinkColor` | 0.4 / `#FF6EC0` | existing pink hold (unchanged) |

## Not checked

- Nothing was rendered. Harness scenarios (`runPhase6` etc.) were not run, per the rules. The existing `fxpink`, `swarm` and `fxfix` checks may move: the halo colour, the first person glow falloff and the blade's presence after the hold have all changed.
- Whether the pink glow shell looks right against the sword texture (it is the sword's own texture tinted pink, emissive, translucent); whether it shows z-fighting with the hilt at the tsuba.
- The first person look of the glow shell at close range.
- The top-to-bottom direction rests on the hand calculation above, not on a render.
- Rukia shares `ObjItemUnbakedModel`: she also gets a glow shell mesh and blade axis (not used, since the dissolve starts only for effect id 3).
- Remote casters: the dissolve is keyed by entity id like the pink hold, but only the local player was looked at.
