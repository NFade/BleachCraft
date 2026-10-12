# FX round 3: shunpo afterimage size, Rukia bankai terrain bugs

Date: 2026-10-12. Game not run (clients are open). Everything visual is UNVERIFIED in game; compile and unit tests only.

## 4. Shunpo afterimages "half size": real cause
- Not the scale constant. `ShunpoFx` builds its own `PlayerEntityModel` that never goes through `LivingEntityRenderer`. `EntityModel.child` is initialised to **true** in the constructor (javap of the 1.21.1 Yarn jar: `iconst_1; putfield child`) and the vanilla renderer resets it every frame from `isBaby()`. `AnimalModel.render` with `child == true` draws the body at x0.5 and the head at x0.75 (BipedEntityModel: head scale 1.5/2, body 1/2, offsets 16 and 24 px). That is the "two little men with big heads" of IMG9.
- Matrix height, adult: model point y (blocks, +y down) maps to `s * (1.501 - y)`; feet y=1.5 -> 0.001*s, hat top y=-0.5 -> 2.001*s. s=0.9375 gives 1.876 blocks (live player, hitbox 1.8); s=1.0 gives 2.0.
- Matrix height, as it was (child=true): body/legs 0.5x, head 0.75x: about 1.0-1.1 blocks at s=1.0, i.e. 0.5-0.55 of the live player. Matches the screenshot estimate.
- Fix (`ShunpoFx.pose`): `m.child = false; m.riding = false;`. Default `shunpo.afterimageScale` set back to **0.9375** (exact vanilla player scale, so copy height == live player 1.876 blocks). Still tunable in `fx_override.json`. Number was: 1.0 scale but child model (~1.05 blocks effective); now: 0.9375 adult (1.876).
- Note: the running pose (lean 18-24 deg, legs 0.9 rad, head pitch) makes the silhouette about 10-15% shorter than standing; that is the pose, not the scale.

## 5. Rukia bankai: causes and fixes
Causes found in code (the screenshot shows the passive: frost decal, release/rim crystals, ribbons):
1. `FxGround.sample` scans from feet+3 down and returns the top of the FIRST solid block, so under trees it returns leaf and trunk tops. The frost decal grid (12x32 vertices) took those heights: sheets jumped onto foliage (vertical planes through leaves) and slopes cut through blocks. Crystals (6 release + 8 rim, and the 8 Absolute zero ones) were put on canopies, inside trunks, and their 3-5 block bodies pierced the canopy above.
2. Flat additive rings (release, Absolute zero) were at `gy + 0.06` of the centre only: buried by any higher ground within the radius.
3. Decal had no depth bias (plain `getEntityTranslucent`, 2.5 cm over the block top): z-fight/flicker at distance and grazing angles.
4. Ribbon chains (3 x 10 segments, up to 3.5 blocks behind her) ignored the world: they pass through trunks, leaves, walls.
Other causes ruled out: render order and culling are fine (decal is drawn before the mesh pass, same translucent entity consumers); the glow batch has its own depth copy for Fabulous (FxDepth), not changed.

Fixes (new `client/fx/FxPlace.java`, `FxMeshes.Mesh.radius` added):
- `FxPlace.ground(w, x, z, hint, up, down)`: floor within `up` blocks above the hint (a block reaching higher = occupied column -> NaN) and `down` below.
- Decal (`FxDecals`): vertices from `ground` relative to the floor under the centre (step `decal.step` 1.0); NaN vertices and quads with height spread > `decal.maxStep` (0.6) are not drawn (holes around trunks, no ramps at step edges); extra lift `decal.biasPerBlock` (0.0015 per block of eye distance) on top of `decal.lift` (0.025); own layer `reiatsu_decal_*` = entity translucent + `POLYGON_OFFSET_LAYERING` (switch `decal.polyOffset` 0 = old layer).
- Crystals (`FxPlace.fit`, used by `RukiaBankaiClient.makeField` and the Absolute zero crystals through `placeCrystal`): footprint (centre + 4 points) all need a floor within `place.step` 1.0 and spread <= `place.slope` 0.45, base at the highest; the body is probed along its axis (7 stations x 5 lateral points) against collision shapes; if blocked the crystal is shrunk to the free part, rejected below `place.minFit` 0.45; then up to `rbankai.placeTries` (6; `az.placeTries`) neighbouring angle/radius candidates are tried, else the crystal is not made (logged: "found no free spot"). Random draws per crystal do not depend on terrain, so all clients choose the same first candidate.
- Rings: `FxPlace.ringY` = 80th percentile of floors on 3 circles, within `place.ringLiftMax` 0.5 above the centre floor (release rings, Absolute zero rings).
- Ribbons (`RukiaFeature`): per segment 4 probes x 3 (centre + strip edges `rbankai.ribbonClipWidth` 0.6 of the strip half width) against block collision; the segment is shortened to the free part and the chain ends there. `rbankai.ribbonClip` 0 turns it off.

## Not done / UNVERIFIED
- Not run in game: check the arena under a tree (fewer crystals, shorter, none inside the trunk), on a slope, next to a wall; decal holes around trunks; ribbons with the back to a wall; shunpo copies standing next to the real player.
- Release pillar/walls (`FxShapes.wall`), Rukia shikai crystals (`RukiaShikaiFx`) and other `FxGround.sample` users keep the old behaviour; the same `FxPlace.fitMesh` can be applied there.
- Particles and glow sprites (not meshes) are not collision-clipped.
- Full `gradlew test`: 503 tests, 4 failures, all in `TpPoseTest` (3) and `TwoHandMotionTest` (1): another agent's pose work in the tree, not touched here. `compileClientJava` green.

## fx_override.json keys (all new)
shunpo.afterimageScale (now 0.9375), decal.step 1.0, decal.maxStep 0.6, decal.lift 0.025, decal.biasPerBlock 0.0015, decal.polyOffset 1, place.step 1.0, place.slope 0.45, place.minFit 0.45, place.footprint 1.0, place.ringLiftMax 0.5, rbankai.placeTries 6, az.placeTries 6, rbankai.ribbonClip 1, rbankai.ribbonClipWidth 0.6.
