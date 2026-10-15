# R11 merge finish (branch r11-merged)

Branch `r11-merged` = r11-integration + gin-uryu + r11-icecrystal + r11-blade. This note records what was done to finish the merge.

## A. Build of the merged tree
`./gradlew build` on the tree as merged: compileJava, compileClientJava and all tests were green, so the r11-blade merge broke nothing. No fix was needed for A.

## B. Crystal ice wall (r11-icecrystal)
Read and checked, no code change was needed:
- `ReiatsuTestClient.onInitializeClient` already calls `IceWallCrystalPlugin.register()` (after `ObjModelPlugin.register()`) and puts `ModBlocks.ICE_WALL` on the translucent render layer.
- Model path: `PreparableModelLoadingPlugin.register` (Fabric model loading API 2.1.0) -> `ctx.resolveModel().register(...)` answers the model id `reiatsu_test:block/ice_wall`, which is the id `blockstates/ice_wall.json` points to; Fabric's `ModelLoaderMixin` calls the resolver for every `getOrLoadModel(Identifier)`, so the block state variant reaches `IceWallCrystalModel`.
- Error paths: a failed load logs `[ice wall] the crystal model failed to load, the plain cube is drawn instead` and returns null, the resolver is then not registered and the JSON cube `models/block/ice_wall.json` is drawn. A missing sprite in the block atlas logs `[ice wall] MISSING sprite`. The glow texture must be RGBA (checked by a test). All 25 OBJ pieces, the meta, `ice_wall_crystal.png` and `ice_wall_crystal_glow.png` are in `assets/reiatsu_test` (textures in `textures/block`, so they are in the block atlas). In a healthy start the client log shows one line `[ice wall] crystal model: N pieces, M quads in X ms` and no error line.
- Tests `IceWallCrystalResourcesTest` (6), `IceWallCrystalSourceTest` (4), also `IceCrystalFieldTest`, `IceWallLayoutTest`, `IceWallSourceTest`: all green.

## C. Bankai giant blade v2 (r11-blade)
The r11-blade WIP commit had already removed `BladeStraighten`, `TIP_Y`, `TIP_Z_EXPORT` and `BLADE_LENGTH = 8.0` (length, width, thickness and apex come from the `"blade"` block of `byakuya_bankai_meta.json` through `BladeSpec` / `BladeSpecLoader`; `BladeRowLayout.height` and `sinkDepth` use `BladeSpec.length()` = 5.5). What was still left and is now removed:
- `bankai.bladeWidth` and `bankai.bladeThick` (fx_override.json, dev only): removed with `RowsFx.bladeWidthSetting/bladeThickSetting`, `State.kx/kz`, `BladeRowLayout.DEFAULT_BLADE_THICK/defaultBladeWidth/widthScale` and the thickness multiplier of `gap`. The blade is scaled by one number (`bankai.bladeScale`, `bankai.bladeLength`) on all three axes, the normal matrix in `BankaiBladeRenderer.basis` is now the plain rotation. `State.bladeWidth` stays as a plain value (mesh width x bladeScale) for the scar and crack sizes.
- Stale comments about the curved blade fixed.
- Old keys in an existing `fx_override.json` are ignored by `FxTune` (unknown keys are simply never read); no migration was needed because the keys never lived in `reiatsu_test.json` or `reiatsu_test_client.json` (so `ConfigMigrations` / `LangTest` are untouched). Documented as removed in `design/results/r11_config_keys.md`.
- `FpRig.TIP_Y` (first person sword rig) is a different thing and stays.
- Tests: `BladeRowLayoutTest` updated for the removed API (5.5 m), `BankaiBladeMeshTest` (reads the real meta and OBJ: length 5.5, thickness, width, bounding box, tip (0, 5.5, -0.201) OBJ = (0, 0.201, 5.5) Blender) got a new test `rowLayoutFollowsTheMetaLengthAndTip` (layout maths follows the meta length and tip).

## D. Result
`./gradlew build`: BUILD SUCCESSFUL, 1333 tests, 0 failures, 0 errors, 0 skipped (147 test classes).

## UNVERIFIED in game
- The crystal ice wall actually shows up (translucent crystals, glow pass, no chunk-build errors, performance of the per-block emit); that the log shows the `[ice wall] crystal model` line and no error.
- The new 5.5 m straight blade in the bankai rows: size, rise and sink (buried by 6.0), the lean toward the axis, tip glow band (0.8 m), regrowth and dissolve, scar sizes, the culling box, lighting after the normal matrix change.
- That an old `fx_override.json` with `bankai.bladeWidth` / `bankai.bladeThick` loads without a warning.
