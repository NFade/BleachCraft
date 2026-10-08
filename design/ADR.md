# ADR: Reiatsu Test mod architecture (phase 2a)

Status: ACCEPTED for spike 2b (2026-10-08). Author: opus (ADR call). Overrides `ART_BIBLE.md` 0.5 where noted (0.5 was marked "PROPOSAL, final in ADR").
Scope: Fabric, Minecraft Java 1.21.1, Yarn, mod id `reiatsu_test`, Java 21. No mod code here.

## 0. Verification sources and conventions

Every API name below carries a source tag or `UNVERIFIED`. Tags:
- **[FAPI]** = Fabric API source, branch `1.21.1` (repo renamed `FabricMC/fabric` -> `FabricMC/fabric-api`): `https://raw.githubusercontent.com/FabricMC/fabric-api/1.21.1/<module>/src/...`; module path given after the tag. That branch is written in Yarn names, so names there are Yarn names.
- **[YARN]** = Yarn javadoc `https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/<class path>.html`.
- **[EX]** = `https://github.com/FabricMC/fabric-example-mod/tree/1.21.1` (official 1.21.1 template).
- **[MCMETA]** = vanilla 1.21.1 assets `https://raw.githubusercontent.com/misode/mcmeta/1.21.1-assets/assets/minecraft/...`.
- **[BPY]** = `https://docs.blender.org/api/current/bpy.ops.wm.html#bpy.ops.wm.obj_export` (version "current", not pinned to 5.2.2) plus the bundled manual `blmcp/data/manual/files/import_export/obj.rst`.
- `UNVERIFIED`: the named spike/phase agent must check before relying on it. Fix the name, never guess.

Units: 1 Blender unit = 1 m = 1 block. "Model units" in item JSON `display` = 1/16 block.

---

## 1. Item format and rendering

### Decision
**Path B1 (primary):** Blender OBJ -> own OBJ parser (pure Java) -> custom `UnbakedModel` returned by a `ModelResolver` -> baked into Fabric Renderer API `Mesh`es -> a `BakedModel` implementing `FabricBakedModel#emitItemQuads` that picks the mesh by item state and display context. Textures are stitched into the vanilla block atlas.
**Path B2 (first fallback, still Blender meshes):** the same parsed geometry drawn by a `BuiltinItemRendererRegistry.DynamicItemRenderer` into a `VertexConsumer` with our own texture and `RenderLayer`s (the vanilla trident pattern). B2 code is needed anyway for effects (section 2), so switching is cheap.
**Path A (Blockbench + GeckoLib):** last resort only, per section 7.

**Items: one item per character, state in a data component.** Items `reiatsu_test:sode_no_shirayuki` and `reiatsu_test:senbonzakura`. Component `reiatsu_test:release_state` (enum `SEALED | SHIKAI | BANKAI`, codec + packet codec). The server writes it on transitions (section 3). Reason: `emitItemQuads` receives only the `ItemStack`, not the holder, so the stack itself must carry the render state. Swapping items would lose stack identity, needs 6 registrations and complicates the "item in hand" rule.

**Mesh choice per state and display context** (inside `emitItemQuads`, using `RenderContext#itemTransformationMode()`):

| Item / state | Hand modes (1st/3rd person) | GROUND, FIXED, HEAD | GUI |
|---|---|---|---|
| shirayuki SEALED | `rukia_sealed_drawn` | `rukia_sealed_sheathed` | icon |
| shirayuki SHIKAI | `rukia_shikai_blade` + dynamic ribbon | same | icon |
| shirayuki BANKAI | `rukia_bankai_sword` | same | icon |
| senbonzakura SEALED | `byakuya_sealed_drawn` | `byakuya_sealed_sheathed` | icon |
| senbonzakura SHIKAI | `byakuya_shikai_hilt` | same | icon |
| senbonzakura BANKAI | nothing (the hilt is in the ground, drawn by the bankai effect) | `byakuya_sealed_sheathed` | icon |

**GUI icons (new small asset, amends the art bible):** a 32 mm katana blade is under 1 px wide in a 16 px slot, so the GUI draws a flat icon (two quads, front and back) from `textures/item/<item>_<state>_icon.png` (32x32). The Blender agent renders it orthographically from the finished model, then cleans the pixels. 6 icons.

**OBJ subset parsed** (UTF-8, line based, `#` comments):
- `v x y z` (an optional `w` is ignored), `vt u v` (optional `w` ignored), `vn x y z`.
- `f` with **3 or 4** corners, each `v/vt/vn`. Negative (relative) indices are accepted. `vt` is required (missing -> load error with file:line). If `vn` is missing, the flat face normal is used. More than 4 corners -> load error.
- `o` / `g`: the name is recorded; one object per file is expected. `usemtl`, `mtllib`, `s`, `l` are ignored silently. Any other keyword -> one warning per file.
- Quads stay quads. Triangles become degenerate quads (corner 3 = corner 2). Minecraft draws `QUADS`, so a quad costs 4 vertices instead of 6.

**Resource layout** (in `mod/src/main/resources/assets/reiatsu_test/`):
- `models/obj/<model>/<object>.obj`, plus `models/obj/<model>/<model>_meta.json` (section 5).
- `textures/item/<model>_diffuse.png`, `textures/item/<model>_emissive.png`, `textures/item/<item>_<state>_icon.png`. Everything under `textures/item/` is stitched into the block atlas by the vanilla `blocks.json` `directory` source `item` ([MCMETA] `atlases/blocks.json`). Effect renderers bind the same files directly by full path (`reiatsu_test:textures/item/<model>_diffuse.png`), so one PNG serves both paths.
- `zanpakuto/<item>.json`: a manifest mapping (state, context) to OBJ objects, the texture model and the emissive tint ARGB. Read by the `PreparableModelLoadingPlugin` data loader.
- `models/item/<item>_display.json`: an ordinary vanilla JSON holding only `gui_light` and `display`, plus `textures.particle`. Our unbaked model depends on it and takes its transforms through `JsonUnbakedModel#getTransformations()`.

**Model pipeline:**
1. `PreparableModelLoadingPlugin.register(loader, plugin)`: the loader reads the manifests and the OBJ/meta files from the `ResourceManager` asynchronously and parses them with the pure-Java parser.
2. In `onInitializeModelLoader(data, ctx)`: `ctx.resolveModel().register(...)` answers ids `reiatsu_test:item/<item>` (the 1.21.1 inventory item model id with the `item/` prefix; the spike logs the actual ids) with `ObjItemUnbakedModel`.
3. `ObjItemUnbakedModel implements UnbakedModel`: `getModelDependencies()` returns the `_display` id; `setParents(...)`; `bake(Baker, Function<SpriteIdentifier,Sprite>, ModelBakeSettings)` resolves the sprites `new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, id)`, then builds one `Mesh` per (state, context, pass) through `RendererAccess.INSTANCE.getRenderer().meshBuilder()` / `QuadEmitter`, using `pos`, `normal`, `uv`, `spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED)`, `color` (tint) and `material`.
4. `ObjItemBakedModel implements BakedModel`: `isVanillaAdapter()` is false and `emitItemQuads` calls `mesh.outputTo(context.getEmitter())`. `getQuads` returns an empty list, `getOverrides()` returns `ModelOverrideList.EMPTY`, `getTransformation()` returns the `_display` transforms, and `isSideLit()` is false (`gui_light: front`, for the flat icon).

**Emissive (two-pass overlay):**
- Pass 1: diffuse sprite, `BlendMode.CUTOUT` material (Indigo maps non-translucent item quads to `TexturedRenderLayers.getEntityCutout()`), lit normally.
- Pass 2: the same geometry with the emissive sprite and material `emissive(true)`, `disableDiffuse(true)`, `blendMode(BlendMode.TRANSLUCENT)`. Indigo's item context replaces the lightmap with `LightmapTextureManager.MAX_LIGHT_COORDINATE` for emissive quads (verified in `ItemRenderContext#shadeQuad`).
- Pass 2 contains only quads whose UV region has at least one emissive texel with alpha > 0. The loader computes this at bake time from the PNG, so non-glowing parts cost nothing.
- **Emissive PNG format (overrides ART_BIBLE 0.3):** RGBA with RGB = white and A = intensity. A post-bake script converts Blender's RGB bake using A = max(R,G,B). Tint comes from vertex colour (manifest ARGB × `emissiveMultiplier` config). The same file works in both B1 (translucent emissive material) and the effect renderers (`RenderLayer.getEntityTranslucentEmissive`).
- Translucent ice (Rukia bankai, diffuse alpha < 255): pass 1 uses `BlendMode.TRANSLUCENT` for those objects only (manifest flag).

**Code animation:**
- *Shikai ribbon (item):* 10 segment meshes baked separately. Every frame, `emitItemQuads` (Indigo calls it per render, verified) copies each segment quad (`emitter.copyFrom(quad)`), rotates it about its hinge by a procedural damped sine (time from the client clock), and emits it. In first person (`itemTransformationMode().isFirstPerson()`) only segments 01-04 are drawn. Limit: the item has no knowledge of world "down" or holder velocity, so the ribbon waves in item space. Upgrade (stretch): a player `FeatureRenderer` verlet ribbon (Gate A suggestion).
- *Rukia bankai back ribbons and costume:* a player feature renderer (section 2), not the item.
- *Bankai blades, petals, crystals, Hakuteiken:* effect renderers (section 2); never items.

**In-hand transforms** (starting values for `_display.json`; the spike tunes them; the scales are the art bible's):
Model space after loading: blade along +Y, edge toward +Z, flats ±X, `grip_hand` at the block centre (8, 8, 8). The rotations reproduce the vanilla `item/handheld` blade direction for a +Y blade.

| Mode | rotation [x,y,z] | translation | scale |
|---|---|---|---|
| thirdperson_righthand | [-10, 0, 0] (alt. [-10, 180, 0] if the edge faces backward) | [0, -3, 1.75] | 0.85 |
| firstperson_righthand | [20, 0, 0] (alt. [20, 180, 0]) | [1.13, -2.2, -0.8] | 0.70 |
| left-hand modes | mirror of the right-hand modes | | |
| ground | [0, 0, 0] | [0, 2, 0] | 0.55 |
| fixed | [0, 0, -45] | [0, 0, 0] | 0.60 |
| gui | [0, 0, 0] | [0, 0, 0] | 1.0 (flat icon) |

Acceptance: the edge faces the swing direction (forward/down) in both hand views. Vanilla clamps translation to ±80 and scale to ≤4 ([YARN] `Transformation`; values are well inside).

### Rationale
B keeps the Blender detail the user asked for. Fabric meshes give free vanilla display transforms, GUI, ground and item-frame handling, and full-bright through Indigo. Per-frame dynamic quads in `emitItemQuads` are an official use ("static and dynamic portions"). Atlas stitching avoids a custom item render layer.

### Alternatives rejected
- Separate items per state: needs item swapping and loses stack identity.
- `MaterialFinder.emissive` on the whole diffuse quad: glows non-glowing texels on the same face.
- A custom render layer for items: not reachable from Fabric materials; B2 covers it.
- GeckoLib: cuboid-only, lossy for these meshes.

### Risks
- R1.1: in the item context, entity shaders still apply directional diffuse to the emissive pass (the `disableDiffuse` doc says it is "not guaranteed" for items), so a glow face can be up to about 40% darker. If unacceptable, use B2 for that item with `RenderLayer.getEyes` / `getEntityTranslucentEmissive`.
- R1.2: the coplanar overlay may z-fight. Mitigation: push pass 2 out 0.0005 blocks along the vertex normal.
- R1.3: `itemTransformationMode()` may return `NONE` for some callers; fall back to the hand mesh.
- R1.4: the procedural ribbon can clip through the arm.
- R1.5: Sodium/Iris are not targets (Indigo only).

### Verification
- [FAPI] `fabric-model-loading-api-v1/.../ModelLoadingPlugin.java` (`register`, `Context#addModels/resolveModel/modifyModelOnLoad`), `ModelResolver.java` (`resolveModel(Context)`, `Context#id()`), `PreparableModelLoadingPlugin.java` (`register(DataLoader, plugin)`, `DataLoader#load(ResourceManager, Executor)`).
- [FAPI] `fabric-renderer-api-v1/.../FabricBakedModel.java` (`isVanillaAdapter`, `emitItemQuads(ItemStack, Supplier<Random>, RenderContext)`), `render/RenderContext.java` (`getEmitter`, `itemTransformationMode`, `pushTransform`), `material/MaterialFinder.java` (`emissive`, `disableDiffuse`, `ambientOcclusion(TriState)`, `blendMode`, `find`), `material/BlendMode.java` (DEFAULT/SOLID/CUTOUT_MIPPED/CUTOUT/TRANSLUCENT), `mesh/QuadEmitter.java` and `MutableQuadView.java` (`BAKE_NORMALIZED`, `spriteBake`, `copyFrom`, `emit`), `mesh/Mesh.java` (`forEach`, `outputTo`), `Renderer.java`, `RendererAccess.java`.
- [FAPI] `fabric-renderer-indigo/.../render/ItemRenderContext.java` (emissive -> `MAX_LIGHT_COORDINATE`; blend-to-layer mapping; per-render `emitItemQuads` call).
- [FAPI] `fabric-renderer-api-v1/src/testmodClient/.../OctagonalColumnUnbakedModel.java`, `PillarBakedModel.java` (1.21.1 `UnbakedModel` signatures, `SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE`, `ModelOverrideList.EMPTY`, `ModelHelper`).
- [FAPI] `fabric-rendering-v1/.../BuiltinItemRendererRegistry.java` (`register(ItemConvertible, DynamicItemRenderer)`, `render(ItemStack, ModelTransformationMode, MatrixStack, VertexConsumerProvider, int, int)`).
- [YARN] `client/render/model/BakedModel`, `client/render/model/json/JsonUnbakedModel` (`getTransformations`), `client/render/model/json/ModelOverrideList`, `client/render/model/json/Transformation`, `client/render/LightmapTextureManager` (`MAX_LIGHT_COORDINATE`), `client/render/RenderLayer` (`getEntityCutoutNoCull`, `getEntityTranslucentEmissive`, `getEyes`), `component/ComponentType` (`builder().codec().packetCodec().build()`), `item/ItemStack#set`, `item/Item.Settings#component`, `registry/Registries#DATA_COMPONENT_TYPE`.
- [MCMETA] `atlases/blocks.json` (directory source `item`), `models/item/trident_in_hand.json` (`builtin/entity` + display pattern for B2).
- UNVERIFIED (spike): the exact item model id the resolver sees; vanilla `ItemRenderer` applying `translate(-0.5,-0.5,-0.5)` after the display transform; the display values above.

### Blender 5.2 OBJ export settings (exact)
Before export (in the .blend): apply rotation and scale (`bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)`), so every exported object has rotation 0 and scale 1. N-gons are not allowed: add a Triangulate modifier as the last modifier with `min_vertices = 5` (only n-gons are split; UNVERIFIED property name, check with `search_api_docs`). Export one object at a time (only it selected):
```
bpy.ops.wm.obj_export(
    filepath="<...>/blender/export/<model>/<object>.obj",
    export_selected_objects=True,
    forward_axis='NEGATIVE_Z', up_axis='Y',      # default; mapping in section 5
    global_scale=1.0,
    apply_modifiers=True,
    apply_transform=False,                        # local object space; origin = object origin
    export_eval_mode='DAG_EVAL_VIEWPORT',
    export_uv=True, export_normals=True, export_colors=False,
    export_materials=False,                       # no MTL; manifest maps textures
    export_pbr_extensions=False, path_mode='STRIP',
    export_triangulated_mesh=False,               # keep quads (Minecraft draws quads)
    export_curves_as_nurbs=False,
    export_object_groups=False, export_material_groups=False,
    export_vertex_groups=False, export_smooth_groups=False,
    export_animation=False)
```
Parameter names and enums were verified against [BPY] "current" docs. The Blender agent re-checks them on 5.2.2 with `bpy.ops.wm.obj_export.get_rna_type().properties.keys()` and records the result in `blender/scripts/export_obj.py`. Triangle budgets stay counted after triangulation, as in the art bible.

---

## 2. Effects

### Decision
Four mechanisms, chosen by what the effect needs:

| Mechanism | Used for | API |
|---|---|---|
| **Custom particles** (billboards, vanilla `ParticleManager`) | `frost_mote`, `ice_shard`, `reiatsu_wisp`, loose `petal` debris and sparks (at most 300 live), dust | `FabricParticleTypes.simple()` registered in `Registries.PARTICLE_TYPE`; client `ParticleFactoryRegistry.getInstance().register(type, PendingParticleFactory)` with a `SpriteProvider`; `assets/reiatsu_test/particles/<name>.json` + `textures/particle/`. Glowing types extend `SpriteBillboardParticle`, override `getBrightness(float)` -> `MAX_LIGHT_COORDINATE`, sheet `ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT`. |
| **Batched effect renderer** on one **effect anchor entity** per live mass effect | petal swarm (shikai) and storm (bankai), bankai blade rows, ground hilt and ripple, Rukia crystals and ice shells, oriented quads (`snow_ring`, `light_pillar`, Shirafune strip, ground haze), Hakuteiken set, Senkei | Entity type `reiatsu_test:fx_anchor` (`EntityType.Builder.create(factory, SpawnGroup.MISC).dimensions(0.5f,0.5f).maxTrackingRange(10).trackingTickInterval(20).disableSaving().disableSummon().build("fx_anchor")`), `EntityRendererRegistry.register(type, factory)`. The renderer overrides `shouldRender(entity, frustum, x, y, z)` with the effect's own world AABB; the entity overrides `shouldRender(double distance)` (192 blocks). |
| **Player feature renderer** | Rukia bankai costume overlay (collar, pauldrons, crown, chest flower), 3 back ribbons, aura anchor | `LivingEntityFeatureRendererRegistrationCallback.EVENT` (rendering-v1), parts follow `ModelPart#rotate(MatrixStack)` of the player model |
| **Screen effects** | `bankai_flash`, `vignette_dark`, `frost_edge`: textured full-screen quads in `HudRenderCallback` (drawn at the tail of `InGameHud.render`, so above the hotbar; acceptable for short alpha). `freeze_desat` (+ optional radial blur): our own `PostEffectProcessor` | `new PostEffectProcessor(TextureManager, ResourceFactory, Framebuffer, Identifier)`, `setupDimensions`, `render(float)`, `close()`; JSON `assets/reiatsu_test/shaders/post/freeze_desat.json`, programs in `shaders/program/`. |

**Why one anchor entity per effect, not WorldRenderEvents-only:** vanilla entity tracking gives multiplayer visibility, late-join, chunk-unload and despawn for free (phase 7 requires a second client to see the petals). The anchor holds only parameters; all instances are simulated on the client. Tracked data (`initDataTracker(DataTracker.Builder)`): `kind`, `seed` (int), `startTick`, `ownerId`, `targetId`, plus packed float params. Moving swarms read the owner's interpolated position and look vector from the client world. The server discards the anchor when the effect ends.

**Batching and instancing:** per frame, each anchor renderer gets one `VertexConsumer` per render layer from the supplied `VertexConsumerProvider` and writes all instances. The per-instance matrix is computed on the CPU (JOML `Matrix4f`); vertices are transformed and written with `vertex(MatrixStack.Entry, x,y,z).color(..).texture(..).overlay(..).light(..).normal(entry, ..)`. Layers: diffuse `RenderLayer.getEntityCutoutNoCull(tex)`; glow `RenderLayer.getEntityTranslucentEmissive(tex)` with the RGBA-alpha emissive PNG (section 1). Instance meshes come from the same OBJ parser, cached per resource reload (`ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(SimpleSynchronousResourceReloadListener)`). Light per instance is `WorldRenderer.getLightmapCoordinates(world, pos)`, cached and refreshed every 8 frames.

**Client simulation, server parameters.** The server decides gameplay (damage, freeze, area, target) and sends seeds and parameters. Clients simulate visuals deterministically from `(seed, instanceIndex, ticksSince(startTick) + tickDelta)` with `SplittableRandom` and closed-form motion where possible (row layout, rise curves), so late joiners reconstruct the state. Storyboard particles and sounds are emitted by a client `EffectTimeline` from one `effect_event` payload; the server never calls `spawnParticles` for these.

**Budget (target: 60 FPS on a mid PC with 1000 bankai blades + 3000 petals, effectQuality 1.0):**
Measure 1: vertices written to batched renderers per frame ≤ 200k. Measure 2: CPU build ≤ 4 ms/frame, profiler section `reiatsu_fx`.

| Item | Rule | Estimated vertices |
|---|---|---|
| Blades, full mesh (about 85 quads) | within `bladeLodDistance` (default 32, per the art bible) | about 270 near × 340 = 92k |
| Blades, LOD `byakuya_bankai_blade_lod` (about 12 quads) | 32 to 160 blocks; culled beyond 160 | 730 × 48 = 35k |
| Blade glow pass | only the emissive tip quads, within 64 blocks | about 15k |
| Petals, near | the 300 nearest within 16 blocks use the full petal mesh (≤ 10 quads) | 12k |
| Petals, rest | one textured quad with the instance rotation; art bible 2.6 distance thinning (50% at 16-32, 25% at 32-64, culled beyond 64) | ≤ 11k |
| Total | | about 165k |

If the measured budget fails, in this order: (1) cache settled blade rows in a `VertexBuffer` (rows are static after rising; the 3-degree tilt is applied before caching; UNVERIFIED usage, phase 6 checks `net.minecraft.client.gl.VertexBuffer` upload/draw); (2) lower `bladeLodDistance` / near-petal count; (3) lower counts. Never drop the effect itself (art bible 2.6).

**Config** (`config/reiatsu_test.json`, Gson, under `FabricLoader.getInstance().getConfigDir()`): `effectQuality` (0.25/0.5/1.0), `maxPetals` 3000, `bankaiBladeCount` 200 (max 1000), `bladeLodDistance` 32, `nearPetalCount` 300, `emissiveMultiplier` 1.0, `reduceMotion` false, `wingScale` 1.0, `voicePort` 47821, `voiceEnabled` true, `voiceAllowNullOrigin` false.

**Reduce motion:** camera shake off; flashes capped at 40% peak and 0.07 s; vignette pulses become a constant 15%; radial blur off; counts unchanged (art bible 2.0). Camera shake is a client mixin that rotates the view matrix after `GameRenderer#tiltViewWhenHurt` (the method exists, private; the injection point is UNVERIFIED, phase 6).

### Rationale
Vanilla particles are cheap and well suited to billboards, but cannot do oriented meshes or a 3000-instance swarm efficiently. One anchor entity per effect gives networking and lifetime handling with no per-petal entities. HUD overlays avoid shader risk for 3 of the 4 screen effects; only desaturation needs a real post pass.

### Alternatives rejected
- One entity per petal or blade: tracking and tick cost.
- `WorldRenderEvents.AFTER_ENTITIES` with S2C-only lifetime: reinvents tracking and late-join. It stays the fallback if the anchor's culling or translucency ordering misbehaves (`WorldRenderContext#consumers()` gives the same buffer provider).
- GPU instancing with custom core shaders (`CoreShaderRegistrationCallback` exists, [FAPI]): too risky for a prototype.
- A post shader for the flash: unnecessary.

### Risks
- R2.1: translucent emissive draws are unsorted; with Fabulous graphics, entity translucency goes through a separate target. Test both graphics modes.
- R2.2: the CPU vertex cost estimate is unmeasured.
- R2.3: running our `PostEffectProcessor` outside vanilla's slot (from `WorldRenderEvents.END` or a `GameRenderer` mixin) needs a framebuffer rebind (`client.getFramebuffer().beginWrite(false)`). Whether post-pass `program` names may be namespaced in 1.21.1 is UNVERIFIED. Fallback: files under `assets/minecraft/shaders/program/reiatsu_test_*.json`; final fallback: desaturation as a grey HUD overlay.
- R2.4: the anchor entity can be culled at the edge of the tracking range; the 110-block corridor exceeds a small server view distance.

### Verification
- [FAPI] `fabric-particles-v1/.../FabricParticleTypes.java` (`simple()`), `client/particle/v1/ParticleFactoryRegistry.java` (`register(type, PendingParticleFactory)`).
- [FAPI] `fabric-rendering-v1/.../EntityRendererRegistry.java`, `LivingEntityFeatureRendererRegistrationCallback.java`, `HudRenderCallback.java` (`onHudRender(DrawContext, RenderTickCounter)`), `WorldRenderEvents.java`, `WorldRenderContext.java`, `CoreShaderRegistrationCallback.java`; `mixin/client/rendering/InGameHudMixin.java` (`@Inject render TAIL`).
- [FAPI] `fabric-resource-loader-v0/.../ResourceManagerHelper.java`, `SimpleSynchronousResourceReloadListener.java`.
- [YARN] `client/particle/Particle` (`getBrightness(float)`, `buildGeometry`, `getType`), `client/particle/ParticleTextureSheet` (`PARTICLE_SHEET_TRANSLUCENT`), `client/particle/ParticleFactory` (`createParticle`), `client/gl/PostEffectProcessor` (constructor, `render(float)`, `setupDimensions`, `close`), `client/render/GameRenderer` (`loadPostProcessor` is private, so we own our instance; `tiltViewWhenHurt`), `client/render/VertexConsumer`, `client/render/entity/EntityRenderer#shouldRender`, `entity/EntityType.Builder` (`create`, `dimensions`, `maxTrackingRange`, `trackingTickInterval`, `disableSaving`, `disableSummon`, `build(String)`), `entity/Entity` (`initDataTracker(DataTracker.Builder)`, `shouldRender(double)`), `client/render/WorldRenderer#getLightmapCoordinates`, `client/model/ModelPart#rotate(MatrixStack)`.
- [MCMETA] `shaders/post/spider.json` (1.21.1 post JSON format: `targets`, `passes`, `intarget`/`outtarget`, `uniforms`).

---

## 3. Player state

### Decision
**Storage: the Fabric Data Attachment API with built-in sync.** Sync exists on the 1.21.1 branch: `AttachmentRegistry.Builder#syncWith(PacketCodec, AttachmentSyncPredicate)`, added in PR #4049 (2024-11-12) and present in 0.116.17. The API is `@ApiStatus.Experimental` (warning only).

| Attachment | Contents | Persistent | Sync |
|---|---|---|---|
| `reiatsu_test:zanpakuto` | record `{byte character, byte state, int stateSinceTick, byte shikaiMode, int bankaiEndTick}` | no; resets to SEALED on join, death or dimension change | `AttachmentSyncPredicate.all()`: others render auras, the swarm and the costume |
| `reiatsu_test:reiatsu` | record `{int value (tenths), int max}` | yes (`persistent(Codec)`) | `targetOnly()`, HUD only |
| `reiatsu_test:cooldowns` | `Map<abilityId, endTick>` | no | `targetOnly()`, HUD cooldown icons |

Each type is created with `AttachmentRegistry.create(id, builder -> builder.initializer(..).persistent(..).syncWith(..))`. The server writes them with `setAttached` / `modifyAttached` only. Reiatsu regeneration is applied every 5 ticks (at most 4 sync packets/s per player). The **item component `release_state` is a render mirror** of `zanpakuto.state`, written by the server on the main-hand stack. Invariant: a server check every 20 ticks resets any inventory stack whose component says released while the attachment says SEALED (and vice versa for the selected stack).

**Fallback (if attachment sync fails in the phase 4 smoke test):** the same records go in `state_sync` S2C, sent to the player plus `PlayerLookup.tracking(player)` on change and on `EntityTrackingEvents.START_TRACKING`.

**Payloads** (records implementing `CustomPayload`, `CustomPayload.Id` = `reiatsu_test:<name>`, `PacketCodec<RegistryByteBuf, T>`, registered in common init via `PayloadTypeRegistry.playC2S()/playS2C().register(id, codec)`; receivers via `ServerPlayNetworking.registerGlobalReceiver` (runs on the server thread) and `ClientPlayNetworking.registerGlobalReceiver`; send with `ClientPlayNetworking.send(payload)` and `ServerPlayNetworking.send(player, payload)`):

| Name | Direction | Fields |
|---|---|---|
| `request_transition` | C2S | `byte targetState`, `byte source` (KEY/VOICE), `int clientSeq` |
| `cast_ability` | C2S | `byte abilityId` (includes swarm mode switches), `byte source`, `int clientSeq` |
| `action_result` | S2C, caster only | `int clientSeq`, `byte result` (OK, DENIED_STATE, DENIED_ITEM, DENIED_REIATSU, COOLDOWN, RATE_LIMIT) |
| `effect_event` | S2C, caster + `PlayerLookup.tracking(caster)` | `varint effectId`, `varint casterId`, `int seed`, `double x,y,z`, `float dx,dy,dz`, `varint targetId` (-1 = none), `int startTick`, `float[≤8] params` |
| `entity_fx` | S2C, tracking players of the area | `byte kind` (FROZEN, ENCASED, SLOWED), `int untilTick`, `int[] entityIds` |
| `state_sync` | S2C | fallback only (see above) |

Aim is never sent: the server uses the player's own look vector and position. **Authority:** only the server changes state. It validates the item in the main hand (item type matches character), the state graph, reiatsu, cooldowns and a rate limit (≤ 10 C2S/s per player). The client does no prediction. Its role: send requests, render state, play `EffectTimeline` on `effect_event`. The state graph and timings live in `design/STATE_MACHINE.md` (next phase), implemented in pure Java (section 6).

### Rationale
Attachments give persistence, death/respawn handling and tracking-aware sync with no hand-written sync code. Explicit payloads remain for one-shot events.

### Alternatives rejected
- Cardinal Components: an extra dependency.
- Hand-rolled NBT plus manual sync: more code; kept as the fallback.
- Item-only state: other players' items can't hold the owner's timers, and the state must survive hotbar swaps.

### Risks
- R3.1: experimental API changes do not matter (pinned version).
- R3.2: sync on respawn and dimension change must be smoke-tested (fallback ready).
- R3.3: component vs attachment drift (the invariant check handles it).

### Verification
- [FAPI] `fabric-data-attachment-api-v1/.../AttachmentRegistry.java` (`create`, `Builder#persistent/copyOnDeath/initializer/syncWith/buildAndRegister`), `AttachmentSyncPredicate.java` (`all`, `targetOnly`, `allButTarget`), `AttachmentTarget.java` (`getAttached`, `setAttached`, `modifyAttached`, `getAttachedOrCreate`); commit history API for the sync PR on branch 1.21.1.
- [FAPI] `fabric-networking-api-v1/.../PayloadTypeRegistry.java` (`playC2S`, `playS2C`, `register(CustomPayload.Id, PacketCodec)`), `ServerPlayNetworking.java` (`registerGlobalReceiver`, `send(ServerPlayerEntity, CustomPayload)`, handler on the server thread), `client/networking/v1/ClientPlayNetworking.java` (`send`), `PlayerLookup.java` (`tracking(Entity)`, `around`), `EntityTrackingEvents.java`.

---

## 4. Voice

### Decision
**Flow:** browser page (Web Speech API, `webkitSpeechRecognition`, continuous + interim results) -> `POST http://127.0.0.1:<port>/voice` with JSON `{text, final, lang, utt, t}`. `utt` = utterance index; `t` = epoch ms when the browser received the result (latency logging). -> embedded HTTP server **in the client mod** (the mic is local to the player; it works for both integrated and dedicated servers) -> Java phrase matcher -> on the client thread (`MinecraftClient#execute`) -> `request_transition` / `cast_ability` C2S.

**The mod serves the bridge page itself:** `GET /` returns `assets/reiatsu_test/voice/index.html`, copied at build time from `voice-bridge/index.html` by Gradle `processResources`. The page is same-origin with the endpoint, so there is no CORS. `http://127.0.0.1` is a secure context in Chromium, so the mic permission persists. Opening from `file://` (Origin `null`) is refused unless `voiceAllowNullOrigin=true`.

**Server:** `com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)`, one small executor thread.
- Started on `ClientLifecycleEvents.CLIENT_STARTED`, stopped on `CLIENT_STOPPING`.
- Port from config (default 47821). If busy, try +1 to +4. The actual URL is printed as a clickable chat line on world join.
- Bind failure: log + chat warning, voice off; the game is unaffected.
- **Startup guard:** if module `jdk.httpserver` is missing at runtime (`ModuleLayer.boot().findModule("jdk.httpserver")`; the Mojang launcher's bundled Java runtime may be a trimmed image, UNVERIFIED), use a minimal `java.net.ServerSocket` HTTP/1.1 handler behind the same `VoiceEndpoint` interface (only GET / and POST /voice, `Content-Length` bodies ≤ 4 KB).

**Request hygiene:**
- Reject unless `Host` ∈ {`127.0.0.1:<port>`, `localhost:<port>`} (DNS-rebinding guard).
- `Origin` must be absent or equal to one of those origins.
- Header `X-Reiatsu-Token` must match a random 128-bit token generated per game launch and embedded in the served page.
- `Content-Type: application/json`; body ≤ 4 KB; ≤ 20 requests/s.
- Answer `OPTIONS` with 204 and no CORS grant. Replies: 204, 400, 403, 413, 429.

**Matcher:**
- Location: pure Java in `core.voice` (no Minecraft imports). The bridge stays dumb.
- Normalisation: lower case, strip punctuation and macrons; Cyrillic and kana -> one romaji-like Latin form via a fixed table; collapse repeats.
- Scoring: Jaro-Winkler over a sliding token window, against the phrase variants generated from `design/VOICE_PHRASES.md` into `assets/reiatsu_test/voice/phrases.json`. Tests read the same file.
- Firing rules:
  - A final result fires at score ≥ 0.86.
  - An interim result fires only at ≥ 0.93 and stable across 2 consecutive interims of the same `utt`. This cuts latency without false starts.
  - One command per `utt`.
  - Same-command debounce 1500 ms; global minimum gap 300 ms.
  - Context gate: client-side check of the current state + item in hand, before sending. The server re-validates.
- Thresholds are config values, tuned by the phase 5 tests (≥ 95% recall on the fixture, 0 false positives on neutral phrases).

**Latency budget, ≤ 400 ms** (from browser `t` to the first effect frame on the caster's client):

| Step | Budget |
|---|---|
| fetch over loopback | 10 ms |
| matcher | 5 ms |
| wait for client thread | ≤ 50 ms |
| C2S + server tick handling | ≤ 50 ms (+ network RTT on a remote server) |
| S2C + client frame | ≤ 60 ms |
| Total | ≈ 175 ms, margin 225 ms |

The recogniser's own end-of-speech delay is outside the budget and is logged separately. The mod logs `t`, receive time, send time, `action_result` time and first effect frame (same machine clock).

### Rationale
The mic and browser are on the player's machine. Loopback plus token plus Host check closes the drive-by-website hole. Serving the page from the mod removes file:// permission and CORS issues. A Java matcher is unit-testable offline.

### Alternatives rejected
- Matcher in JS: not testable with the mod's tests, and it duplicates the state gate.
- WebSocket: unnecessary for this rate.
- Native speech libraries (Vosk etc.): a system install and large models; out of scope.

### Risks
- R4.1: Chrome/Edge Web Speech sends audio to the vendor's cloud. It needs internet and does not work in Firefox. Document this in the README.
- R4.2: Japanese romaji phrases recognised under `en-US` can be poor; the bridge allows switching `lang`, and the matcher normalises all three.
- R4.3: `jdk.httpserver` may be absent in the production runtime (guard above).
- R4.4: interim firing may cause false positives (thresholds are tested against the neutral set).

### Verification
- JDK 21 `jdk.httpserver` module, `com.sun.net.httpserver.HttpServer#create(InetSocketAddress, int)`: https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html (standard API; runtime presence UNVERIFIED in the launcher runtime).
- [FAPI] `fabric-lifecycle-events-v1/.../ClientLifecycleEvents.java` (exists on the branch; event names `CLIENT_STARTED` / `CLIENT_STOPPING` UNVERIFIED by reading, check in phase 5), `ClientPlayNetworking.java#send`.

---

## 5. Coordinates

### Decision
- **Blender (authoring), unchanged from ART_BIBLE 0.5:** Z-up, right-handed, 1 unit = 1 m = 1 block.
  - Hand-held objects: origin at the kashira centre; blade along +Z; cutting edge -Y; spine +Y; flats ±X; sori deflects toward +Y.
  - Other objects use their own origins (ribbon hinge, blade base, crystal base, halo centre) as listed in the art bible.
  - Empties: `grip_hand` (0, 0, 0.19); `rukia_bankai_sword` (0, 0, 0.22); `tip`, `ribbon_root`, `tang_tip`.
- **OBJ** (export `forward_axis='NEGATIVE_Z', up_axis='Y'`): Blender `(x, y, z)` -> OBJ `(x, z, -y)`. This is a proper rotation (det +1), so winding (CCW front) is preserved. Never use a mirroring export axis pair. Blade +Y_obj, edge +Z_obj, spine -Z_obj, flats ±X_obj. Normals use the same mapping.
- **Loader -> Minecraft model space** (item meshes): `p_mc = (p_obj - grip_obj) + (0.5, 0.5, 0.5)`. Scale 1, because 1 m = 1 block and in-hand size is set only by display `scale`. `grip_obj` is `grip_hand` mapped to OBJ axes. UV: `u_mc = u`, `v_mc = 1 - v` (OBJ V points up, Minecraft V points down), then `spriteBake(..., BAKE_NORMALIZED)`.
- **Effect meshes** keep their own origin (no grip offset); instance matrices place them in the world.
- **In-hand scale factors:** first person 0.70, third person 0.85, ground 0.55, item frame 0.60 (art bible 0.2), set in `_display.json`. The GUI uses the flat icon at 1.0.
- **Metadata file (replaces the E3 format):** `blender/export/<model>/<model>_meta.json`, copied to `models/obj/<model>/`:
```
{ "format": 1, "units": "m", "space": "blender_zup",
  "empties": { "grip_hand": [0,0,0.19], "tip": [0,0,1.036], "ribbon_root": [0,0,-0.01] },
  "objects": { "rukia_shikai_blade": [0,0,0], "rukia_shikai_ribbon_01": [0,0,-0.01], "...": [0,0,0] } }
```
  `objects` = each exported object's location (its origin) in Blender world coordinates, needed because `apply_transform=False` writes vertices in local space. Written by a Blender script from `obj.matrix_world.translation` (rotation and scale are applied, so they are identity). The Java loader converts every entry with the same `(x, z, -y)` mapping.

### Rationale
Default OBJ axes keep the files correct in any viewer. A single fixed rotation in the loader is easy to test (axis-marker asset in section 7). Local-space export plus the origins table makes hinges and instance origins exact.

### Risks
- R5.1: someone exports with `apply_transform=True` or unapplied rotation (the haiku re-import check compares the bounding box to the meta).
- R5.2: the V flip is wrong (the spike's lettered texture catches it).

---

## 6. Project layout and versions

### Decision
Verified current for 1.21.1 on 2026-10-08:

| Component | Version | Source |
|---|---|---|
| Minecraft | 1.21.1 | |
| Yarn | `1.21.1+build.3` (latest of 3 builds) | `https://meta.fabricmc.net/v2/versions/yarn/1.21.1` |
| Fabric Loader | 0.19.5 (latest stable) | `https://meta.fabricmc.net/v2/versions/loader`; [EX] `gradle.properties` |
| Fabric API | `0.116.17+1.21.1` (last 1.21.1 build) | `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`; [EX] |
| Loom plugin | `net.fabricmc.fabric-loom-remap` 1.18.3 ([EX] uses `1.18-SNAPSHOT`; pin the release) | `https://maven.fabricmc.net/net/fabricmc/fabric-loom-remap/net.fabricmc.fabric-loom-remap.gradle.plugin/maven-metadata.xml` |
| Gradle wrapper | 9.7.1 | [EX] `gradle/wrapper/gradle-wrapper.properties` |
| JUnit | `junit-bom` 5.14.4 (Jupiter) + `junit-platform-launcher` (test runtime) | `https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter/maven-metadata.xml` |
| Java | 21 (`options.release = 21`); Temurin 21.0.12 local | MASTER_PROMPT 10 |

`build.gradle` follows [EX]: `loom { splitEnvironmentSourceSets(); mods { "reiatsu_test" { sourceSet sourceSets.main; sourceSet sourceSets.client } } }`. Change from [EX]: `mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"` instead of `loom.officialMojangMappings()`. [EX] now ships Mojmap, so **Yarn + the 1.18.3 remap plugin is UNVERIFIED as a combination** and is spike step 1. If it fails, try Loom 1.11.x with Gradle 8.14 (one variable at a time); Mojmap is not an option (MASTER_PROMPT 10). Every Gradle command sets `JAVA_HOME` and `GRADLE_USER_HOME=D:\gradle-home` explicitly. Test config: `test { useJUnitPlatform() }`.

`fabric.mod.json`: `depends` `fabricloader >=0.19.5`, `minecraft ~1.21.1`, `java >=21`, `fabric-api *`; entrypoints `main` -> `dev.minebleach.reiatsutest.ReiatsuTest`, `client` -> `dev.minebleach.reiatsutest.client.ReiatsuTestClient`. Mixins: one client mixin config, added only when phase 6 needs one (camera shake, post hook).

**Layout** (`D:\MineBleach\mod\`):
```
src/main/java/dev/minebleach/reiatsutest/
  core/            pure Java, NO net.minecraft imports: state/ (StateMachine, transitions, timers),
                   reiatsu/ (ReiatsuMath), voice/ (Normalizer, PhraseMatcher, Debouncer),
                   obj/ (ObjParser, ObjMesh, MetaJson, AxisMapper), fx/ (seeded layouts: blade rows, petal counts)
  registry/        items, data component, attachments, particle types, entity type, sounds
  net/             payload records + codecs, server receivers
  server/          ability logic, validation, temporary-ice rollback, component invariant
  entity/          FxAnchorEntity
src/client/java/dev/minebleach/reiatsutest/client/
  model/           ObjModelPlugin, ObjItemUnbakedModel, ObjItemBakedModel
  render/          FxAnchorRenderer + per-effect renderers, costume FeatureRenderer, MeshCache
  fx/              EffectTimeline, particles (factories), screen overlays, post processor
  hud/             reiatsu bar, cooldowns
  voice/           VoiceEndpoint (HttpServer / fallback), command dispatch
src/main/resources/  fabric.mod.json, assets/reiatsu_test/** (assets live in main; ignored on server)
src/test/java/dev/minebleach/reiatsutest/core/**  JUnit 5: state machine, reiatsu math, matcher
                   (fixtures from phrases.json + neutral set), ObjParser (spike asset), AxisMapper,
                   CorePurityTest (fails if any core/ source imports net.minecraft or net.fabricmc)
```
Pure-Java tests run with `gradlew test` without launching Minecraft.

### Risks
- R6.1: the Yarn/Loom combination (above).
- R6.2: Loom's test classpath includes Minecraft; core tests must not touch it (CorePurityTest).

---

## 7. Spike 2b acceptance criteria

**Test asset** (Blender, `blender/scenes/spike.blend`, exported to `blender/export/spike/`):
- `spike_cube`: a 0.20 m cube centred at (0, 0, 0.10), plus a 0.04 × 0.04 × 0.60 m bar from z 0.20 to 0.80 along +Z. Tris and quads both present.
- 64x64 diffuse: each cube face a distinct colour with a letter (+X "R", -X "L", +Y "B", -Y "E" for edge, +Z "T"); the -Y face of the bar red (edge marker).
- Emissive: only the top 0.10 m of the bar (RGBA, A = 1).
- Empties: `grip_hand` (0, 0, 0.19), `tip` (0, 0, 0.80). A second object `spike_cube_alt` (same shape, scaled 0.7, different colours) for the state-swap test.

**Checklist** (all must pass; record each with a screenshot in `blender/renders/spike/` or `mod/run/screenshots/` and a line in `LOG.md`):
1. `gradlew build` and `gradlew runClient` succeed with exactly the section 6 versions and Yarn. If not, follow the section 6 fallback order.
2. Export with the exact section 1 parameters; the meta JSON is written; re-import in Blender matches the bounding box; the emissive PNG is converted to RGBA.
3. JUnit: `ObjParserTest` parses `spike_cube.obj` (expected v/vt/vn/face counts, bounds after `AxisMapper`, quad/tri split); `AxisMapperTest` maps the Blender `(0,0,1)` tip to OBJ `(0,1,0)` and `grip_hand` to model `(0.5,0.5,0.5)`.
4. Item `reiatsu_test:spike_item` registered with the `release_state` component. The log shows the resolver id, `quads=N`, and the sprites found in the atlas. No missing-texture checkerboard.
5. Visible in: first-person right and left hand, third person (F5) right hand, dropped on the ground, item frame. GUI shows the flat icon.
6. Orientation: the bar points along the blade direction out of the fist; the red "E" face points along the intended edge direction (choose the alternative rotation from section 1 if not); letters read unmirrored (V flip correct).
7. Emissive: `/time set midnight`, inside a sealed dark room (light level 0, no night vision): the bar tip shows full colour while the rest is near black. Test with Fancy **and** Fabulous graphics; note any z-fighting or darkened glow faces (R1.1/R1.2).
8. State swap: `/give @s reiatsu_test:spike_item[reiatsu_test:release_state="shikai"]` (exact syntax UNVERIFIED) shows `spike_cube_alt`.
9. Dynamic quads: a 2-segment test strip at `ribbon_root` waves smoothly every frame (proves per-frame `emitItemQuads`).
10. F3+T resource reload rebuilds the model without error; `_display.json` edits take effect.
11. Performance: record FPS (F3) at a fixed view with no item, with the item in hand, and with 64 item frames holding it; record parse+bake time from the log. Pass: holding the item costs < 1 ms frame time.
12. Log whether module `jdk.httpserver` is present in the dev runtime (one line, informs R4.3).

**Diagnosis order if something fails** (change one variable, re-run, record):
1. Resolver not called -> log every id seen; fix namespace or `item/` prefix.
2. `bake` returns null or throws -> `RendererAccess.INSTANCE.hasRenderer()` (Indigo present?), then the stack trace.
3. Replace OBJ geometry with one hard-coded unit quad in the same model class. Visible -> the parser or `AxisMapper` is at fault; invisible -> pipeline or transforms.
4. Replace our sprite with vanilla `block/white_concrete` -> the atlas stitching or texture path is at fault.
5. Set all display transforms to identity -> the transform values are at fault.
6. Faces missing -> winding (check det +1 mapping) or a degenerate-quad corner order.
7. Emissive: base pass only with `emissive(true)` (whole quad bright?) -> then the translucent overlay alone -> then the 0.0005 offset -> then Fancy vs Fabulous.
8. Still failing -> switch the spike item to **B2** (`builtin/entity` display JSON + `DynamicItemRenderer` drawing the parsed mesh with `getEntityCutoutNoCull` + `getEntityTranslucentEmissive`), re-run steps 5-11.

**Switch to fallback A only if:** B1 **and** B2 both fail to show a correctly oriented, emissive Blender mesh in hand, after the diagnosis order above, 2 sonnet attempts and 1 opus escalation, with the blocking cause written in `LOG.md`. If B1 fails only on emissive brightness (R1.1) or z-fighting, adopt B2 for held items; that is not a reason for A. If B passes rendering but step 11 fails, optimise (mesh caching, fewer passes) before considering A.

**Spike report** (`LOG.md` + ≤ 200-word reply): pass/fail per step, chosen in-hand rotations, B1 or B2 decision, FPS numbers, every UNVERIFIED item it resolved (with the correct name).

---

## 8. Consolidated UNVERIFIED list (owner)
1. Yarn `1.21.1+build.3` with `fabric-loom-remap` 1.18.3 (spike step 1).
2. Item model id seen by the resolver; vanilla `translate(-0.5)` after the display transform; in-hand transform values (spike).
3. Indigo emissive overlay: diffuse darkening and z-fighting (spike step 7).
4. Blender 5.2.2 `obj_export` parameter names (re-check), Triangulate modifier `min_vertices` (Blender agent).
5. `/give` component syntax for `release_state` (spike step 8).
6. `jdk.httpserver` in the Mojang launcher runtime; `ClientLifecycleEvents` event names (phase 5).
7. Our own `PostEffectProcessor` invocation point and namespaced post `program` names; camera-shake injection point after `tiltViewWhenHurt`; `VertexBuffer` caching path (phase 6).
8. Attachment sync across respawn and dimension change (phase 4 smoke test).
