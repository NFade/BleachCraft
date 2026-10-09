# LOG

Short log of decisions and phase results.

## 2026-10-08: setup
- Working dir `D:\MineBleach`, mappings Yarn, MC 1.21.1 / Fabric.
- Blender 5.2.2 MCP (`mcp__blender__*`) responds: default scene (Camera, Cube, Light).
- Created folder structure (section 4), `.gitignore` (refs/ excluded), `LICENSES.md`, `refs/INDEX.md`, git init.

## 2026-10-08: Phase 0, environment

| Item | Status (have/missing) | Version / path | Needed action (what to install, if any) |
|---|---|---|---|
| Blender MCP (`mcp__blender__*`) | have | Blender 5.2.2; scene Scene (Camera, Cube, Light), unsaved | none. Read-only snippet errored (see Notes) |
| Java 21 (JDK) | missing | Only Java 8 (`C:\Program Files\Java\jre-1.8`, javapath 1.8.0_401) | Install Temurin JDK 21 (x64 MSI) |
| JAVA_HOME | missing | empty | Set to the Temurin 21 install dir after installing JDK 21 |
| Gradle (global) | missing | not on PATH | Optional; not needed (use `gradlew`) |
| git | have | 2.52.0.windows.1 | none |
| Node.js / npm | have | node v24.15.0 / npm 11.12.1 | none |
| Python (`python`) | have | 3.10.11 | none |
| Python (`py`) | have | 3.12.8 | none |
| Pillow (PIL) | have | 12.3.0 (via `python`) | none |
| `%APPDATA%\.minecraft` | have | `C:\Users\efeki\AppData\Roaming\.minecraft` | none |
| MC 1.21.1 / Fabric in versions | missing | versions present: 26.1.2, 26.2-snapshot-7, SkyFactory 5 5.0.8 | Install Fabric 1.21.1 via launcher (Fabric loader + MC 1.21.1 profile) |
| Official Minecraft Launcher | have (probable) | MS Store package data `Microsoft.4297127D64EC6_8wekyb3d8bbwe` present; classic path absent | Verify the launcher opens; no install needed |
| Prism / MultiMC / CurseForge / Modrinth App | missing | none of the checked paths exist | Optional; not required |
| XboxGames folder | have | `C:\XboxGames` | none |
| Blockbench | missing | not in `%LOCALAPPDATA%\Programs\Blockbench`, `C:\Program Files\Blockbench`, or Start Menu | Install Blockbench (desktop) |
| Free disk C: | have | 27.6 GB free of 222.6 GB | Low margin; keep an eye on it (Minecraft/Gradle caches are large) |
| Free disk D: | have | 199.5 GB free of 931.5 GB | none |
| Google Chrome | have | `C:\Program Files\Google\Chrome\Application\chrome.exe` | none (Web Speech API works) |
| Microsoft Edge | have | `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` | none |

Notes:
- JAVA_HOME is empty and `java` on PATH resolves to Java 8 (`javapath`). Minecraft 1.21.1 + Fabric needs Java 21, so the JDK 21 install is blocking for building and for running.
- Blender `execute_blender_code` with the Phase 0 snippet failed on Blender 5.2.2: `AttributeError: type object 'HydraRenderEngine' has no attribute 'bl_idname'`. The line `[e.bl_idname for e in bpy.types.RenderEngine.__subclasses__()]` is the cause. Drop or guard that field (e.g. `getattr(e, 'bl_idname', None)`) in the next read-only run. The scene was not modified.
- Scene is the default unsaved one; `filepath` is empty, so `is_saved` is false.
- The Store MC package folder exists, but `Get-AppxPackage` returned nothing. Launcher presence is therefore inferred, not confirmed.
- No Fabric or 1.21.1 profile exists yet in `.minecraft\versions`.
- Orchestrator re-check: Blender python 3.13.13, numpy yes, PIL **no** inside Blender (use system `python` 3.10 + Pillow 12.3 for contact sheets). Render engines: CYCLES, HydraRenderEngine (+ built-in EEVEE/Workbench).
- Fabric 1.21.1 launcher profile is NOT needed for development: `gradlew runClient` (Loom) downloads MC 1.21.1 itself. Only JDK 21 is required. Blockbench is only needed for fallback path A.
- Checkpoint 1 shown to user: JDK 21 missing (blocks phase 2b/4), Blockbench optional.

## 2026-10-08: Phase 1 (research, sheets, art bible, Gate A)
- 1a: 4 research files in `research/` (Fandom via `tools/fandom.py`: wiki HTML 403 to scripts, API OK, image CDN needs Referer).
- Refs (private, gitignored): rukia sealed 9 / shikai 11 / bankai 10; byakuya sealed 12 / shikai 12 / bankai 12. Index `refs/INDEX.md`.
- 1b: contact sheets `refs/sheets/*.png` (`tools/contact_sheet.py`, system Python + Pillow).
- 1c: `design/ART_BIBLE.md` v1 (sonnet).
- Gate A (opus call 1/5): APPROVED WITH EDITS, 27 edits, 15 open questions settled (`design/GATE_A.md`). v2 applied by sonnet, plus 8 consistency fixes.
- Ref gaps after Gate A: byakuya sealed tsuba/hilt close-ups and shikai hilt-after-scatter filled (manga ch. 116/301/302/379). Still missing: rukia bankai back view and head ornament (not on Fandom); byakuya saya in daylight.
- Opus budget used: 1 of 5 (Gate A). Remaining: ADR, Gate B, bankai VFX design, Gate D (Gate C would be a 6th: to be decided).

## 2026-10-08: Checkpoint 2 decisions
- User: install everything needed; opus limit raised to 6.
- Installed Temurin JDK 21.0.12 via winget (JAVA_HOME machine-wide, PATH before Oracle javapath 1.8). `GRADLE_USER_HOME=D:\gradle-home` (user env). Blockbench not installed (only for fallback A; install if the spike fails).
- Note for agents: shells started before the install keep old env; set `JAVA_HOME` and `GRADLE_USER_HOME` explicitly in Gradle commands.
- VOICE_PHRASES.md (sonnet): 14 commands, 160 predicted ASR rows, 89 neutral phrases; prototype JW matcher 0 FP, 72% fuzzy recall (rest via aliases). Open: "bank eye" listed as neutral by orchestrator but is a likely ASR form of "bankai": resolve in phase 5.
- ADR (opus call 2/6): `design/ADR.md`. B1 = own OBJ parser + Fabric model-loading/renderer API, item per character + `release_state` component, emissive second pass; B2 fallback = dynamic item renderer; A (GeckoLib) last. Versions: Yarn 1.21.1+build.3, Loader 0.19.5, Fabric API 0.116.17+1.21.1. Unverified items go to the spike.

## 2026-10-09: Phase 2b spike (path B, OBJ item rendering)

**Result: B1 PASSES (12/12). Path B1 approved; B2 and A not needed.** Everything below was run with `gradlew runSpike` (dev harness). Screenshots: `blender/renders/spike/` (originals in `mod/run/screenshots/`, git-ignored).

### Versions actually used
Minecraft 1.21.1, Yarn `1.21.1+build.3`, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Indigo (bundled), **Loom `net.fabricmc.fabric-loom-remap` 1.17.21** (not 1.18.3, see deviations), Gradle 9.7.1, Temurin 21.0.12, JUnit 5.14.4, Blender 5.2.2 LTS. Test machine: GTX 1650 SUPER, Ryzen 5 2600, 1280x720 window, render distance 6, vsync off, uncapped fps.

### Checklist (ADR section 7)
| # | Result | Evidence |
|---|---|---|
| 1 | PASS | `gradlew build` ok; client starts with `runClient` (no harness lines in its log) and `runSpike`. Section 6 versions and Yarn, except Loom 1.17.21 (fallback). |
| 2 | PASS | `blender/scripts/spike_build.py`, `spike_export.py`, `spike_emissive_convert.py`. All 21 `obj_export` params exist on 5.2.2. Re-import bbox error 1.5e-8 for all 4 objects. Meta JSON written. Emissive PNG converted to RGBA (RGB white, A=max(R,G,B), 112 glow texels). |
| 3 | PASS | `gradlew test`: 19 tests (ObjParserTest 8, AxisMapperTest 7, CorePurityTest 1, EmissiveMaskTest 3). `spike_cube.obj`: v16 vt24 vn6 f12 (10 quads, 2 tris); bounds after mapping (0.4,0.31,0.4)-(0.6,1.11,0.6). Blender tip (0,0,1) -> OBJ (0,1,0); `grip_hand` -> model (0.5,0.5,0.5). |
| 4 | PASS | Log: `resolver sees id reiatsu_test:item/spike_item` (+ `..._display`), `baked ... quads=82 sprites=[spike_diffuse, spike_emissive, spike_item_sealed_icon, spike_alt_diffuse, spike_item_shikai_icon] missing=[]`. No checkerboard anywhere. |
| 5 | PASS | `01`,`02` first person right/left; `03`,`04` player third person back/front; `03b-d` armor-stand profiles (right, left, front); `05` dropped; `06` item frames; `07a/07b` GUI hotbar + inventory (flat icons, state specific). |
| 6 | PASS | Face-by-face check (frame view, enlarged): E, R, B, L read upright and unmirrored; bar points along the blade; red edge face underneath (edge forward/down). V flip correct. |
| 7 | PASS (with note) | `09a-09f`. Dark room (light 0, midnight, gamma 0): bar tip full colour (252,168,50 vs tint 255,170,51), rest near black (background ~5/255); the vanilla diamond sword reference is fully black. Fancy and Fabulous identical. R1.1 measured: with real face normals one glow face was 99%, the face turned away 50%. Mitigation implemented (glow quads get the model +Y normal): all glow faces 92%. R1.2: no z-fighting visible with the 0.0005 offset. |
| 8 | PASS | `item replace ... with reiatsu_test:spike_item[reiatsu_test:release_state="shikai"]` and `/give @s reiatsu_test:spike_item[reiatsu_test:release_state="shikai"]` both work (log: "Gave 1 [Spike Item (reiatsu_test)]"); shows `spike_cube_alt` (inverted colours, no strips): `08a`, `08b`, `05`, `06`. In `summon`: `{Item:{id:"reiatsu_test:spike_item",count:1,components:{"reiatsu_test:release_state":"shikai"}}}`. |
| 9 | PASS | Two hinged segments sway every frame (`04`/`04b`/`04c`, `01`/`01b`, `06`/`06b`: 880 to 6400 changed pixels between captures with a static camera). Code: `pushTransform` + `Mesh#outputTo` inside `emitItemQuads`. |
| 10 | PASS | `10a` after reload ok; `_display.json` edited in the build output (fp scale 0.9 -> 1.8) -> reload -> `10b` shows the change; restored -> `10c`. Reload log: parse 16-55 ms, bake 1-2.7 ms, no errors. |
| 11 | PASS | Numbers below. Holding the item costs 0.00 ms mean (within noise, +/-0.1 ms); own `emitItemQuads` time 0.015 ms/frame (3.7 us/call). |
| 12 | PASS | `jdk.httpserver present=true`, `java.desktop present=true` in the dev runtime (Temurin 21.0.12). Production launcher runtime still UNVERIFIED (R4.3 stays; keep the ServerSocket fallback). |

### Performance (final run, mean frame time over 400 frames after 90 warm-up frames)
| Scenario | mean ms | fps | p95 ms | emit us/call |
|---|---|---|---|---|
| P0 empty hand, no frames (3 hotbar icons drawn) | 1.334 | 749 | 2.35 | 1.8 |
| P1 spike item in hand | 1.323 | 756 | 2.22 | 3.7 |
| P1s shikai in hand | 1.322 | 757 | 2.14 | 2.6 |
| P3 empty hand, 64 item frames (spike) | 2.549 | 392 | 3.80 | 5.8 (0.39 ms/frame total) |
| P2 item in hand + 64 item frames | 2.279 | 439 | 3.43 | 5.3 (0.36 ms/frame) |
| P4 control: 64 vanilla diamond-sword frames | 2.905 | 344 | 4.19 | |
| PF Fabulous, item in hand | 1.710 | 585 | 2.73 | 3.7 |

Earlier runs agreed (P1 minus P0 between -0.05 and +0.12 ms). 64 spike frames are cheaper than 64 vanilla sword frames. Parse (cold JVM) 209-298 ms, warm 15-55 ms; bake 16 ms cold, 1-3 ms warm.

### Chosen in-hand transforms (`assets/reiatsu_test/models/item/spike_item_display.json`)
Tuned by sweeps with the tune harness (`gradlew runSpike -Ptune=<json>`, generator `tools/spike_tune_gen.py`, contact sheets `tools/spike_sheet.py`).

| Mode | rotation | translation | scale |
|---|---|---|---|
| firstperson_right/left | [-45, **180**, 0] | [-3, 5, -2] | 0.90 |
| thirdperson_right/left | [25, 0, 0] | [0, -3, 1.75] | 0.85 |
| ground | [0, 0, 0] | [0, 2, 0] | 0.65 |
| fixed | [0, 0, -45] | [-2.2, -2.2, 0] | 0.90 |
| gui | flat icon, [0,0,0] | 0 | 1.0 |

Rules learned:
- (a) FIRST person needs ry=180 and THIRD person ry=0 (the two hand frames differ by a flip about Y) to get spine up and edge down in both. The ADR starting angles were right in kind but not in value: fp [20,0,0] / scale 0.70 put the item below the screen edge, tp rx=-10 pointed the blade slightly down (positive rx raises the blade).
- (b) Left-hand JSON entries must EQUAL the right-hand ones: `Transformation#apply(leftHanded)` already mirrors rotation y/z and translation x (verified in source and screenshots 02, 03c).
- (c) Fixed: the object centre is 0.21 m above the grip, so the translation recentres it in the frame.

### Decision
**B1** (Fabric `ModelLoadingPlugin` + own OBJ loader + `Mesh` baked model + `emitItemQuads` + two-pass emissive). No B2 needed: R1.1 and R1.2 are solved inside B1. Path A not needed.

### UNVERIFIED items resolved (ADR section 8)
1. Yarn `1.21.1+build.3` + Loom: works with `net.fabricmc.fabric-loom-remap` **1.17.21** + Gradle 9.7.1 (`mappings "net.fabricmc:yarn:1.21.1+build.3:v2"`). 1.18.3 is refused: its module metadata requires a Java 25 build JVM ("Dependency requires at least JVM runtime version 25"); 1.14.x-1.17.x declare JVM 21. `genSourcesWithVineflower` works (4 min); decompiled sources are in `mod/.gradle/loom-cache/minecraftMaven/.../*-sources.jar`; Yarn-named Fabric API sources are in `mod/build/loom-cache/remapped_working/*-sources.jar` (the plain sources jars in the Gradle cache are intermediary-named). A Loom run config named `spike` is run with `gradlew runSpike`.
2. Resolver id: **`reiatsu_test:item/spike_item`** (vanilla `ModelLoader#loadInventoryVariantItemModel` uses `id.withPrefixedPath("item/")`; the dependency `reiatsu_test:item/spike_item_display` also goes through the resolver, returning null lets vanilla load the JSON). `ItemRenderer#renderItem` applies the display `Transformation#apply` (translate, rotate XYZ, scale) and THEN `translate(-0.5,-0.5,-0.5)`: confirmed. Display values: table above.
3. Emissive: see step 7. Unmodified Indigo: lit face 99%, turned-away face 50% (minimum light factor 0.4). Fix: glow-pass vertex normal = model +Y (`-Dreiatsu.glowNormal=none` disables). Offset 0.0005 along the face normal removes z-fighting.
4. Blender 5.2.2 `obj_export`: all names in ADR section 1 are valid (`forward_axis` enum X/Y/Z/NEGATIVE_X/NEGATIVE_Y/NEGATIVE_Z, `export_eval_mode` DAG_EVAL_RENDER/DAG_EVAL_VIEWPORT, `path_mode` AUTO/ABSOLUTE/RELATIVE/MATCH/STRIP/COPY). Triangulate modifier: `min_vertices`, `quad_method`, `ngon_method`, `keep_custom_normals`. With `export_materials=False` the OBJ has no `usemtl`/`mtllib`; `s 0` and `o <name>` are written.
5. `/give` and `item replace` syntax: `reiatsu_test:spike_item[reiatsu_test:release_state="shikai"]` (string value, quotes needed).
6. `jdk.httpserver` present in the dev runtime. `ClientLifecycleEvents.CLIENT_STARTED` / `CLIENT_STOPPING` exist (read in the `fabric-lifecycle-events-v1` sources).

Other facts verified from source or run:
- `PreparableModelLoadingPlugin.register(DataLoader, plugin)` + `ctx.resolveModel().register(ModelResolver)` + `UnbakedModel` (`getModelDependencies`, `setParents(Function)`, `bake(Baker, Function<SpriteIdentifier,Sprite>, ModelBakeSettings)`); `JsonUnbakedModel#getTransformations()` works for the dependency model.
- Vanilla `atlases/blocks.json` has `directory source item prefix item/`, so `textures/item/*.png` become sprites `reiatsu_test:item/<name>`. Use `PlayerScreenHandler.BLOCK_ATLAS_TEXTURE` (`SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE` is `@Deprecated`).
- `RenderContext#itemTransformationMode()` returns GUI / GROUND / FIXED / hand modes correctly; `emitItemQuads` is called every render; `Mesh#outputTo` and `pushTransform(QuadTransform)` with `MutableQuadView#copyPos/pos` work for dynamic segments.
- `ItemStack#getOrDefault(component, default)` reads `release_state`; `ComponentType.builder().codec().packetCodec().build()` with `StringIdentifiable.createCodec` + `PacketCodecs.indexed` works.
- Harness facts for later phases: a flat creative world is created with `IntegratedServerLoader#createAndStart(name, LevelInfo, GeneratorOptions, registries -> WORLD_PRESET FLAT .createDimensionsRegistryHolder(), screen)`. After `getGraphicsMode().setValue(FABULOUS)` you must call `worldRenderer.reload()` (the vanilla cycling callback does it), otherwise an NPE in `RenderPhase` (entity framebuffer null) crashes the client. After `reloadResources()` wait for `getOverlay() == null` before screenshots. Commands run through `server.getCommandManager().executeWithPrefix(server.getCommandSource().withEntity(player)...)` on the server thread; feedback goes to the log.

### Deviations from ADR (reason)
1. Loom 1.17.21 instead of 1.18.3: 1.18.x needs a Java 25 build JVM and only JDK 21 is installed (nearest working release, one variable changed). Gradle stays 9.7.1.
2. Display transforms differ from the ADR starting values (that was the purpose of the spike).
3. Added the glow-pass normal override (+Y) to fix R1.1 inside B1; the ADR expected B2 might be needed.
4. `PlayerScreenHandler.BLOCK_ATLAS_TEXTURE` instead of `SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE` (deprecated).
5. `gradle.properties`: `org.gradle.configuration-cache=false` (the example had true; not tested with Loom 1.17, left off for safety).
6. The manifest gained `display_model`, `dynamic` (animated segments, chained in listed order) and a per-state `icon`; hand/other lists as in the ADR table. Left-hand display entries equal the right-hand ones.
7. Spike GUI icons are hand-drawn stand-ins (`blender/scripts/spike_icon.py`), not Blender renders; the real models must render them from Blender.

### Open issues / notes for phase 3-4
- Player third person view is small; the armor-stand profile views are the reliable check for the third-person transform (the real player arm pose differs a little; recheck with real models).
- The shader log warning `rendertype_entity_translucent_emissive could not find sampler named Sampler2` appears on every start (vanilla, before our model is used).
- `mod/spike_run*.log`, `mod/spike_tune*.log`, `mod/gensrc.log`, `mod/runclient.log` are local logs (not committed).
- Phase 4 should keep `SpikeHarness` (gated by `-Dreiatsu.spike=true`, task `runSpike`) as the visual test bed; the tune workflow gives the real in-hand transforms for the final models.
- The Fabric API testmod classes (OctagonalColumn..., PillarBakedModel) are not in the sources jars; the model classes were written from the Yarn sources instead.

## 2026-10-09: Orchestrator check of spike 2b
- Re-ran `gradlew test`: 19/19 pass. Reviewed screenshots (`blender/renders/spike/_sheet.png`): mesh visible in hands, armor stand, item frames; tip glows in dark room (Fancy + Fabulous); letters unmirrored.
- Checkpoint 3 shown to user. Opus used: 2 of 6 (Gate A, ADR).
- Dev runs (`runClient`, `runSpike`) open the window on the monitor containing `dev_monitor` (mod/gradle.properties, default 1260,-540 = second monitor). Verified: window at (825,-811).

## 2026-10-09: Phase 3 step 1, blockouts (sonnet)
- All 6 blockouts: `blender/scenes/<model>.blend`, `blender/scripts/blockout_<model>.py` (+ `bb_common.py`), renders `blender/renders/<model>_blockout.png`, stats `blender/renders/BLOCKOUTS.md`.
- Deviation: the agent reported the MCP socket 127.0.0.1:9876 down and used headless `blender.exe --background --factory-startup` (Workbench renders). Orchestrator re-checked right after: MCP `get_objects_summary` OK. Headless runs are acceptable (scripts are re-runnable, still one Blender job at a time); GUI Blender via MCP preferred for `look` checks.
- Deferred by the bible: Rukia costume set, Senkei sword (after Gate B/C).
- Gate B (opus call 3/6): all 6 APPROVED WITH EDITS, 16 edits B1–B16 (`design/GATE_B.md`). Key: SORI_K=4 (80 mm tip offset), enlarge guard details for in-hand readability, ribbon fixes, Hakuteiken wing as one 48-tri sheet, giant-blade tip hook.
## 2026-10-09: Phase 4 (mod skeleton: state machine, items, server abilities, HUD)

Branch `worktree-agent-a87215125d9f9a3d9`. `gradlew build` and `gradlew test` green: **87 tests** (19 from the spike + 68 new: TransitionTest 21 = L1-L12, I1-I12; ReiatsuTest 9 = R1-R9; AbilityTest 13 = C1-C11 + barrier + serial; AutoRevertTest 9 = A1-A8 + spectator; DebounceTest 8 = D1-D7; BalanceConfigTest 2 = M2, M3 (reads design/VOICE_PHRASES.md); TempBlockJournalTest 4; LangTest 2). M1 CorePurityTest still passes (`core/state`, `core/reiatsu`, `core/world` have no Minecraft imports). `gradlew runPhase4` (dev harness, `-Dreiatsu.phase4=true`, window on the second monitor) runs 64 server and client checks: **64/64 pass**; screenshots in `mod/run/screenshots/p4_*.png` (git-ignored). `gradlew runSpike` still runs to the end.

### What works (verified in game by the harness, real key bindings via KeyBinding.onKeyPressed)
- Items `reiatsu_test:sode_no_shirayuki`, `reiatsu_test:senbonzakura` (class `ZanpakutoItem`, one per character) with `release_state`; the spike item stays registered for the spike harness. Placeholder models: manifests `zanpakuto/<item>.json` point at the spike OBJ set (`"model": "spike"`), display JSON `models/item/<item>_display.json`, generated GUI icons `textures/item/<item>_<state>_icon.png` (`tools/phase4_icons.py`). To swap in real models: change `model` and the object lists in the two manifests and replace the PNGs; nothing in code changes. Visible swap: sealed = cube, shikai = alt cube (Rukia plus ribbon), bankai Rukia = cube + ribbon, bankai Byakuya = empty hand (ADR table).
- Attachments `zanpakuto` (sync to all), `reiatsu` (persistent, copyOnDeath, owner), `cooldowns` (owner). **ADR R3.2 resolved: attachment sync works on join, state change, respawn (new entity) and dimension change** (checks C3, C4); no `state_sync` fallback needed.
- Payloads (codecs via `PayloadTypeRegistry`): `request_transition`, `cast_ability` (C2S), `action_result`, `effect_event`, `entity_fx` (S2C). Server is the only authority; the client sends requests and renders attachments.
- Server glue `ZanpakutoManager`: one `StateMachine` per player (clock = `MinecraftServer#getTicks`), hand grace, drop detection, 20 tick invariant check of the component mirror, spectator, death/respawn (50%), logout, dimension change, Rukia bankai passive, Byakuya barrier (80% reduction, 20 HP pool) through `ServerLivingEntityEvents.ALLOW_DAMAGE`. Abilities per STATE_MACHINE section 4 (damage with caster attribution, Slowness + vanilla frozen ticks, knockback, caps 12/16/24/32 targets), phases through `PhaseScheduler` with the cast serial.
- Temporary blocks: `TempBlocks` + pure `TempBlockJournal` (original state kept on repeated placement, 128 per player), persisted by `TempBlockState` (PersistentState in the overworld data folder) and rolled back on timeout, seal, death, logout, dimension change, server stop and server start (crash safety). Verified: hakuren frost and absolute zero ice appear and are gone after 3 s / 4-7 s, world restored.
- Client: keys R G V (release, bankai, seal) and Z H B (slots), category `key.categories.reiatsu_test`; HUD (bar, state, bankai countdown, three ability boxes with cooldown fill and seconds, red flash and action bar message on denial, shake on cooldown); effect events are logged and spawn vanilla particles (`EffectPlaceholders`, replaced in phase 6).
- Lang en_us and ru_ru (same key set, enforced by LangTest). `/reiatsu info | full | set <0-100> | state <sealed|shikai|bankai> [rukia|byakuya] | cooldowns clear | pvp <bool>` (op level 2).

### Verified API names (Yarn 1.21.1+build.3, Fabric API 0.116.17+1.21.1; compiled and run)
- Attachments: `AttachmentRegistry.create(Identifier, Consumer<Builder>)`, `Builder#initializer/persistent/copyOnDeath/syncWith(PacketCodec<? super RegistryByteBuf,A>, AttachmentSyncPredicate)`, `AttachmentSyncPredicate.all()/targetOnly()`, `getAttached/setAttached` on entities (client too). `getAttached` is null until the first sync/set, so the server sets all three on join and the client treats null as default.
- Networking: `PayloadTypeRegistry.playC2S()/playS2C().register(CustomPayload.Id, PacketCodec<RegistryByteBuf,T>)`, `PacketCodec.of(ValueFirstEncoder(value, buf), decoder)`, `ServerPlayNetworking.registerGlobalReceiver(id, (payload, ctx) -> ...)` with `ctx.player()`, `canSend`, `send`, `PlayerLookup.tracking(entity)`; client `ClientPlayNetworking.registerGlobalReceiver` with `ctx.client()`, `ClientPlayNetworking.send`.
- `HudRenderCallback.EVENT` (`onHudRender(DrawContext, RenderTickCounter)`) exists in 0.116.17 and is not deprecated. `DrawContext#fill/drawBorder/drawText(TextRenderer, Text, x, y, color, shadow)`, `InGameHud#setOverlayMessage(Text, boolean)`.
- Keys: `new KeyBinding(String, InputUtil.Type, int, String category)`, `KeyBindingHelper.registerKeyBinding`, `KeyBinding.onKeyPressed(InputUtil.Key)`. Vanilla 1.21.1 defaults (read from `GameOptions`): W A S D Space E F Q T Tab / P L 1-9 C X F2 F5 F11 Ctrl Shift, mouse buttons; R G V Z H B are free. Labels follow the OS layout (a Cyrillic layout shows Я Р И for Z H B).
- Server: `MinecraftServer#getTicks()` (int, server tick counter, not world time), `getOverworld()`, `getWorld(RegistryKey)`; `DamageTypes.FREEZE / INDIRECT_MAGIC`, `DamageSources#create(key, attacker)`; `LivingEntity#takeKnockback(strength, x, z)` (x, z point toward the attacker), `Entity#setFrozenTicks/getFrozenTicks/getMinFreezeDamageTicks` (140; frozen ticks decay by 2 per tick), `Entity#timeUntilRegen`; `ServerLivingEntityEvents.AFTER_DEATH/ALLOW_DAMAGE`, `ServerPlayerEvents.AFTER_RESPAWN`, `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD`, `ServerPlayConnectionEvents.JOIN/DISCONNECT`, `ServerLifecycleEvents.SERVER_STARTED/SERVER_STOPPING`, `ServerTickEvents.END_SERVER_TICK`; `net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback`; `PersistentState.Type(Supplier, BiFunction<NbtCompound, WrapperLookup, T>, DataFixTypes.LEVEL)`; `getOrCreate(Type, String)`. Entity type tag folder in 1.21 is `data/<ns>/tags/entity_type/`.
- Learned: a null `DataFixTypes` in `PersistentState.Type` makes loading an existing file fail silently (exception swallowed, state lost): use `DataFixTypes.LEVEL`. Entities killed in a tick stay in `getEntitiesByClass` for 20 ticks (filter `isAlive()`).
- Not found: no Fabric hook for "item dropped"; detected by scanning the inventory (and cursor stack) for the released character each tick (immediate seal when the last stack is gone).

### Deviations from the specs
1. `StateEvent.ScheduledPhase` carries `ability` and `serial` (sketch: offset, phaseId only). `RejectReason.STALE_SEQ` added (D4: replayed or lower `clientSeq`, wire byte RATE_LIMIT). `StateMachine` owns its `RateLimiter` (config field `rateLimitPerSecond`), plus `setSpectator`, `onRespawn`, `absorbBarrier`, dev hooks (`devSetState`, `devSetReiatsu`, `devClearCooldowns`) and `cooldownRemainingAll`. `BalanceConfig` gained rate limit, respawn, attack mode and barrier fields. FakeClock lives in the test source set, as the sketch says.
2. Cooldown attachment carries remaining ticks at sync time (the client has no server ticks) and is re-sent only when the set of cooling abilities changes; the client counts down from the receive tick.
3. Hand grace is driven by the machine (`onHandChanged`), which the glue calls every tick. After a hand loss or drop there is no transition lock (release lock 0 as specified).
4. Rukia bankai passive keeps frozen ticks at 100 (below the 140 damage threshold) so it stays damage-free. Abilities that use `chill()` set frozen ticks above 140, so vanilla freeze damage can add a little on top.
5. Absolute zero "no jump" is not implemented (Slowness VII only). Hakuren is 5 wave steps (2 ticks apart, 2.4 blocks each) instead of a continuous wave. All aimed abilities use the cast-time eye position and look vector (the client visuals get the same vector from `effect_event`); only the Byakuya tornado follows the live caster position. Barrier reduction re-applies the reduced damage inside `ALLOW_DAMAGE` (guarded against recursion).
6. Senkei is enabled=false and has no executor.
7. Unit test M3 reads design/VOICE_PHRASES.md (relative `../design`) because `voice/phrases.json` does not exist yet; it is skipped (Assumptions) if the file is unreachable.

### Open issues
- Only the integrated server was exercised; a dedicated server run is untested.
- Dropped item entities keep the SHIKAI component until picked up (cosmetic, fixed by the 20 tick invariant on pickup).
- `affectPlayers` (PvP abilities) and visibility of `effect_event` to tracking players are implemented but not tested with a second client.
- Balance numbers, GCD feel and the 45 s cap need a play test (STATE_MACHINE open points e and c). Relog resets cooldowns (non-persistent attachment).
- The HUD sits above the vanilla bars; with armor and absorption rows it may need a further offset.

## 2026-10-09: Phase 3 steps 2-7, rukia_sealed (final textured asset, Blender 5.2.2 via the MCP socket)

Pipeline (all scripts in `blender/scripts/`, re-runnable): `bb_common.py` (Gate B B1 `SORI_K = 4`, B2 oval swap, B4 tsuba in the blockout helper, B5 snowflake defaults) -> blockout scripts re-run (BLOCKOUTS.md sections for the two models regenerated) -> `build_rukia_sealed.py` (geometry with explicit UVs into the atlas layout in `atlas_layouts.py`, helpers in `detail_common.py`) -> `paint_rukia_sealed.py` (system python: numpy + Pillow, `paint_common.py`) -> `verify_model.py` -> `export_obj.py` (ADR 1 parameters; all 21 `obj_export` names exist on 5.2.2, Triangulate `min_vertices = 5` last modifier) -> `turntable.py` + `compose_turntable.py` -> `blender/scenes/rukia_sealed.blend`. Driver: `run_model.py` (+ `model_specs.py`). Blender GUI via MCP worked throughout; no headless fallback was needed for this model (headless was only used once to smoke-test that the other four blockout scripts still run after the bb_common change).

Gate B edits applied: B1 (tip y 0.0864, saya 92 mm offset), B2 (kashira 22 x 30, tsuka 24 x 30, fuchi 26 x 32 mm, long axis Y), B3 (closed shells, joined, layout offsets kept), B4 (concave corner r 10 mm, slits 20 x 6 mm at y +-25 mm, bow 3 mm, n = 8), B16 (not applicable). Detailing per GATE_B section 4: tsuka 20 rings x 12 sides with the 9 raised diamonds (+1.5 mm, odd rings 1..17) and the 3 mm middle swell, tsuba with 0.8 mm rim bevel and hard slit walls, blade 11 body rings + yokote ring + kissaki ring + apex (6-vertex shinogi section, flat shaded), kashira and fuchi 16 sides with one chamfer, habaki 32 x 10 x 28 mm with 1 mm chamfered corners, saya 12 rings x 16 sides + koiguchi + kojiri (sori 23 mm chord = 92 mm tip offset). Hidden faces deleted: kashira top cap, tsuka top cap, fuchi top and bottom caps, habaki bottom cap, blade root cap, koiguchi bottom cap, saya end caps, kojiri start cap.

| object | tris (evaluated, triangulated) | target / limit | bbox (m) |
|---|---|---|---|
| `rukia_sealed_drawn` | 1076 | 1800 / 3000 | x +-0.031, y -0.036..0.0864, z 0..0.9785 |
| `rukia_sealed_sheathed` | 1392 | 2000 / 2500 | x +-0.031, y -0.036..0.1131, z 0..1.0024 |

Texture: ONE atlas `rukia_sealed_diffuse.png` + `rukia_sealed_emissive.png`, 256 x 256 (RGBA; the emissive map is RGB white with A = intensity, all zero for this model, written directly by the painter, so `spike_emissive_convert.py` was not needed). Material `rukia_sealed_atlas` on both objects, UV map `UVMap`. Islands (px): wrap 60 x 152, blade strips 8 + 8 + 4 + 2 px x 140, saya 32 x 214, tsuba front and back 36 x 42, rim 100 x 8, fittings. Median texel density (px/m): wrap 678, tsuba faces 560, tsuba rim 902, kashira/fuchi 520-660, habaki 422, blade 247, saya 302. 2 px gutters, island edge colours repeated into them. Palette per bible 1.1 (red `#822933` lacing with 3 tones over cream `#D8C9A0` windows, tsuba `#AF9668`, steel `#B9C3D2` with 1 px wavy temper line, saya `#2A2433` with streak). Previews: `blender/renders/rukia_sealed_atlas.png`, `rukia_sealed_turntable.png` (3 rows x 8 frames, 45 degree steps).

Verification (`verify_model.py`, both objects): within budget; rotation 0 and scale 1, origin (0,0,0) = kashira centre; single material; 0 flipped faces (face normals vs a hole-filled recalculated copy) and all 6-7 components have positive signed volume; 0 loose vertices/edges; 0 non-manifold edges (74 / 124 boundary edges are the intentional open contacts where hidden caps were removed); UVs inside 0..1; empties `grip_hand` (0,0,0.19) and `tip` (0, 0.0864, 0.9785). Turntable checked visually, no flipped faces visible. UV overlap: 769 texels at 1024 resolution, all in `tsuba_slit` (the two slit wall rings share one flat-shade island on purpose); 1 (drawn) / 2 (sheathed) zero-area UV faces = the visible 1 mm tsuka underside annulus and the koiguchi step, collapsed on one texel of their own colour. Export to `blender/export/rukia_sealed/`: `rukia_sealed_drawn.obj`, `rukia_sealed_sheathed.obj`, `_diffuse.png`, `_emissive.png`, `rukia_sealed_meta.json` (empties + object origins), `export_check.json`. Re-import check (scratch import with the same axes): vertex and face counts equal the evaluated source (drawn 577 v / 614 f, sheathed 760 v / 780 f), bbox error 5e-7 m, OBJ has only `v/vt/vn/f` (3 or 4 corners, `vt` on every face, no `usemtl/mtllib`).

Differences from the references and why: (1) tsuba flame embossing on the long sides is texture only (bible), so it reads as two-tone tongues instead of relief; the slits and the concave corners are real geometry. (2) The wrap is a coarse 4-diamonds-around, 9-along pattern: the frames show finer lacing with small cream diamonds, but 12 sides and 5 px per side cannot carry more; the 9 diamonds bump the silhouette as Gate B asks. (3) No menuki, mekugi peg, blade hole or hamon in geometry (Gate B skip list). (4) The blade hole seat is only a painted outline. (5) Saya section 26 (X) x 40 (Y) mm as in the blockout (blade is 32 mm wide). (6) Tsuka runs z 0.014..0.236 (not 0..0.250) so solids do not overlap; its bottom cap is kept (2 mm wider than the kashira along X). (7) Habaki has no top bevel: the blade root is 10 mm thick like the habaki, so a bevel would leave an overhang. (8) The tsuba back is a separate planar island (mirrored in X) painted like the front. Atlas is about one third used; the free area is left for the later icon work, no density was inflated.

## 2026-10-09: Phase 3 steps 2-7, rukia_shikai (final textured asset)

Same pipeline as rukia_sealed (`build_rukia_shikai.py`, `paint_rukia_shikai.py`, `run_model.py`, specs in `model_specs.py`); Blender GUI via MCP, no headless fallback. The rukia_sealed turntable sheet was re-rendered with an extra "tsuba from above" row (turntable `elev` option) at the same time.

Gate B edits applied: B1 (tip y 0.0380, chord 8 mm x 4 = 32 mm), B2 (hilt ovals), B3 (closed shells, joined sword mesh, ribbon layout offsets kept), B5 (snowflake 88 mm, rim 7, hub 36, 48-segment outline, windows 19 mm deep, spokes 7..12 mm), B6 (ribbon segments exactly 0.25 m, pitch 0.25 m, no overlap, hinges at z = -0.010 - 0.25 (n-1)), B7 (segment 10 is the same 12-tri trapezoid; swallow tail = alpha cut-out, V notch 5 px = 50 mm in the last 25 x 8 px cell), B8 (no kashira hole; knot block 14 x 8 x 10 mm, z -0.010..0, 1 mm chamfer, material area of the ribbon colour, joined into `rukia_shikai_blade`; `ribbon_root` (0,0,-0.010) = its bottom). Detailing: tsuka wrap as in rukia_sealed (20 rings x 12 sides, 9 raised diamonds), snowflake tsuba with six windows whose four corners are rounded (3 segment Bezier) and 0.8 mm rim bevel, blade 11 body rings + yokote + kissaki ring (sori 8 mm chord, thickness 9 to 4 mm, width 28 to 20 mm), habaki 32 x 10 x 28 mm, kashira and fuchi 16 sides with one chamfer.

| object | tris | target / limit | notes |
|---|---|---|---|
| `rukia_shikai_blade` | 1834 | 2000 / 3000 | bbox x,y +-0.044, z -0.010..1.0355 |
| `rukia_shikai_ribbon_01..10` | 12 each (120) | 12 each | origin = hinge, mesh along local -Z, rotation 0, scale 1; 8 vertices, 6 quads each |

Texture: one 256 x 256 atlas, `rukia_shikai_diffuse.png` (RGBA; alpha 0 only in the swallow-tail notch) + `rukia_shikai_emissive.png` (RGBA white, A = intensity: blade edge strip 35 percent, tsuba spokes 35 percent, tsuba rim 20 percent, ribbon edge rows 25 percent; runtime tint `#BFE4FF`). Islands: wrap 60 x 152, blade strips 7 + 7 + 3 + 2 px x 150, snowflake tsuba front and back 58 x 58 (636 px/m), rim 112 x 8, fittings, knot, ribbon strip 250 x 8 (ten 25 x 8 cells, continuous so the texture flows along the chain). Median texel density (px/m): wrap 678, tsuba 636, tsuba rim 543-932, blade strips 244, spine 316, edge 631, knot 823, ribbon 100 along x 200-285 across. Palette: base `#EAF2FB`, shade `#C9D6EA`, highlight `#FFFFFF`, wrap `#F2F4FA` with gaps `#C9D6EA`, ribbon `#F4F8FF` with edge `#B9D4F0`. Previews: `blender/renders/rukia_shikai_atlas.png`, `rukia_shikai_turntable.png` (4 rows x 8 frames incl. the ribbon and a tsuba-from-above row).

Verification (`verify_model.py`): 11 objects, all within budget, rotation 0, scale 1, origins as ADR 5 (blade (0,0,0), ribbons at their hinges), single material `rukia_shikai_atlas`, 0 flipped faces, positive signed volume on every component, 0 loose geometry, 0 non-manifold edges, UVs inside 0..1, bounding boxes equal the expected values, empties `grip_hand` (0,0,0.19), `ribbon_root` (0,0,-0.010), `tip` (0, 0.0380, 1.0355). UV overlap: `tsuba_win` (the six window wall rings share one flat island, 769 texels at 1024) and, per ribbon, front and back faces on the same cell on purpose (3200 texels at 1024 each); zero-area UV faces are the ribbon side/end walls collapsed on the edge row / last column (so the end wall of segment 10 lands in the transparent notch) and one tsuka underside annulus. Export in `blender/export/rukia_shikai/`: 11 OBJs, `_diffuse.png`, `_emissive.png`, `rukia_shikai_meta.json` (empties + origins of all 11 objects), `export_check.json`. Re-import: counts equal for all (blade 953 v / 1152 f, each ribbon 8 v / 6 f), bbox error 2.4e-7 m, only `v/vt/vn/f` lines. Visual: turntable and a zoomed tsuba render checked; the hard shadow of the blade on the tsuba in the preview is the render's sun, not geometry.

Differences from the references and why: (1) the frames show a white wheel guard with six round-ish windows and a visible rim; ours matches (windows are rounded trapezoids, Gate B); the blade is white with only a faint temper line and edge glow, so it is almost uniformly white as in the frames. (2) The ribbon in the frames is wide and cloth-like with folds; ours is a flat 40 to 28 mm strip with painted faint cross-fold marks (no folds in geometry, Gate B skip). (3) Wrap diamonds are shaded gaps only (white on white), 4 around, 9 along. (4) Emissive intensities are the bible's 35 and 25 percent; the glow around the blade in the frames (light ring, flash) is VFX, not texture. (5) No blade hole, hamon geometry or snow details in geometry. (6) Ribbon cells are 100 px/m along the length (bible: 250 x 8 strip), which is coarse next to the 636 px/m tsuba; a larger strip would not fit the 2 m long chain in 256 px. (7) The tsuka starts at z 0.014 and ends at 0.236 (no overlapping solids, as in the sealed model).

## 2026-10-09: continuation on a new machine (checkpoint 1)
- Installed Temurin JDK 21.0.12 (winget); user env `JAVA_HOME` and `GRADLE_USER_HOME=D:\gradle-home` set. Gradle commands still pass both explicitly.
- Blender: MCP live (5.2.2 LTS, addon 1.8, protocol 13). Local `.mcp.json` points to another user's path and is not used or edited. Headless fallback: `C:\Program Files (x86)\Steam\steamapps\common\Blender\blender.exe` (folders 5.1 and 5.2).
- Monitors: primary 2560x1440, second 1920x1080 at X 2560. `dev_monitor=3500,500`; harness log "dev window moved to monitor at 2560,0" (runPhase4 and runSpike).
- `gradlew build test`: 87/87 tests. `runPhase4`: 64/64 CHECK PASS, RESULT ALL PASS. `runSpike`: runs to the end, BUILD SUCCESSFUL.
- Refs re-downloaded to `refs/` (gitignored) with `tools/fetch_refs.py`: byakuya sealed 9, byakuya shikai 8, rukia bankai 12, byakuya bankai 12; sheets in `refs/sheets/`. Rukia sealed/shikai refs not fetched (models done).

## 2026-10-09: Step B: rukia models in game (branch `step-b-rukia-models`, worktree `.claude/worktrees/stepb`)

Placeholders of `reiatsu_test:sode_no_shirayuki` replaced by the exported models. Bankai still shows the spike cube plus strips (no model yet). Byakuya and `blender/scripts` untouched; no Blender used.

### What changed
- Assets placed with `tools/place_model_assets.py rukia_sealed rukia_shikai` (copies `blender/export/<model>/*.obj` + `_meta.json` to `assets/reiatsu_test/models/obj/<model>/`, the atlases to `textures/item/rukia_*_{diffuse,emissive}.png`). The sealed and shikai Blender models stay in their own folders, so the files are bit-identical to the export.
- Manifest format extension (back compatible): an object may carry `"model": "<folder>"`; default is the manifest's `model`. `ItemManifest#modelOf`, `ObjModelData#metaOf`, loader reads one meta per used model. Hinges and the grip come from the object's own meta (both models have `grip_hand` 0.19).
- `zanpakuto/sode_no_shirayuki.json`: SEALED hand = `rukia_sealed_drawn`, other (ground, frame, head) = `rukia_sealed_sheathed` (ADR table; swap `hand` to the sheathed object to hold the sheathed sword, one line). SHIKAI = `rukia_shikai_blade` + dynamic chain `rukia_shikai_ribbon_01..10` (hinges from the meta: z -0.010 - 0.25 (n-1)). BANKAI unchanged (spike). Emissive tint `FFBFE4FF`. Display json particle = `rukia_sealed_diffuse`.
- Ribbon animation rewritten for a 10 segment cloth chain: per hinge angle `(0.10 + 0.012 i) sin(2 pi 0.8 t - 0.75 i)` rad about model X, accumulating up the chain (the spike formula whipped by up to 3.5 rad at segment 10). Visible segments: third person hand 10, first person / ground / head 4, fixed (item frame) 0. Dynamic segments now also get the emissive overlay (ribbon edge rows).
- Tune harness: `gradlew runSpike -Pitem=sode_no_shirayuki -Ptune=<json>` (property `reiatsu.spike.item`, default `spike_item` keeps the old behaviour). Candidate fields: `view` fp, fp_left, side_r, side_l, front, ground, frame, gui, dark (sealed stone room at x 96..105, midnight) and `state` sealed | shikai. For real items the state is set with `/reiatsu state` (the server invariant would revert a hand-edited component), shikai copies of the stands, ground item and frame stand 4 blocks to the +x side, the mod HUD is hidden (`ReiatsuHud.devHidden`), non-hand views use an empty hand. `tools/spike_tune_gen.py` sets r1..r5 (r5 = final values).
- GUI icons: `tools/rukia_icons.py` (Pillow, no Blender): sword pictogram drawn upright at 8x from colours sampled from the exported atlases, outline, rotated 45 degrees, premultiplied box filter to 32x32 with binary alpha. `tools/phase4_icons.py` now skips these two files. **Blender-rendered icons (orthographic render of the finished model, ADR section 1) are a follow-up.** Bankai icon is still the phase 4 placeholder.
- New test `RukiaManifestTest` (4): per-object model resolution, every state object exists and parses, chain of 10 with descending hinges (segment 01 hinge 0.20 m below the grip), atlases/icons present.

### Final display transforms (`models/item/sode_no_shirayuki_display.json`)
| Mode | rotation | translation | scale |
|---|---|---|---|
| firstperson_right/left | [-30, 220, -6] | [-3, 3, -3] | 1.15 |
| thirdperson_right/left | [45, 0, 0] | [0, -2, 1.75] | 1.5 |
| ground | [90, 0, 0] (lies flat, spins about the vertical) | [0, 3, -4.5] | 0.9 |
| fixed | [0, 0, 45] | [5.6, -5.6, 0] | 1.6 |
| head | [0, 0, 0] | 0 | 0.6 (not tuned) |
| gui | flat icon | 0 | 1.0 |

### Relative to the spike rules (a) to (c)
- (a) changed. The spike used a symmetric cube, so only "spine up, edge down" could be tuned. A 32 x 9 mm blade needs the FLAT toward the camera: first person ry 180 -> 220 shows the flat and the sori curve (ry 180 looks along the edge: a 3 px sliver), rx -45 -> -30 (blade up and slightly forward), small lean rz -6, scale 0.9 -> 1.15. Third person keeps ry 0 (flat visible from the side, tip up, spine up, edge down: confirmed from the armor stand side and front) but rx 25 -> 45 (blade rises up-forward instead of nearly horizontal) and scale 0.85 -> 1.5, because the 1 m katana at 0.85 looked tiny next to the 1.9 m stand. User feedback on the first r3 sheet (first person slanted off to the side, stand sword a thin sliver) is what drove these values.
- (b) still holds: left entries equal right entries; fp left and the left stand are mirrored by the engine (checked in `02_fp_left_sealed`, `07_stand_side_left_shikai`).
- (c) replaced: the object centre is 0.31 m above the grip for these models and the item frame draws at half size, so fixed uses scale 1.6 with translation (5.6, -5.6) and rz +45 (tip upper right like vanilla swords; rz -45 put it upper left, `r5_sealed_frame_alt`).
- New: ground lies flat (rx 90) instead of standing; the standing 0.65 version looked like a floating stick.

### Measurements
- Bake: `sode_no_shirayuki` quads=4922 (both states, hand + other + chain), parse 86 ms warm (409 ms cold), bake 17 ms, no missing sprites.
- Dark room (light 0, midnight, gamma 0), shikai in hand: background 5/255, glow pixels peak (73, 86, 95), about 37 percent of the tint BFE4FF (art bible 35 percent), 546 pixels above 60. Sealed: no glow, as designed. Glow is faint by design; raise the emissive map alpha in Blender if it should read stronger.
- Frame time not re-measured (same Mesh path as the spike; 4922 quads per item stack).
- `gradlew build test`: 91 tests (87 + 4), 0 failures. `gradlew runPhase4`: 64/64, RESULT ALL PASS. `gradlew runSpike` (default spike item, unchanged harness defaults): runs to the end, 0 errors.
- Screenshots: `blender/renders/rukia_inhand/` (01 fp right, 02 fp left, 03 fp shikai, 04/05 stand side and front, 06 stand shikai with the 10 segment ribbon, 07 stand left hand, 08 ground, 09/10 frame, 11 dark room glow, 12 icon preview).

### Open issues
- Sealed state shows the DRAWN blade in hand (ADR table); if the intended look is the sheathed sword in hand, change `hand` in the manifest.
- The ribbon hangs straight along the blade axis in the item frame of reference (no knowledge of gravity or motion), so in third person it reaches the ground; a player feature renderer (ADR stretch) would fix that. Ribbon thickness is 2 mm: edge-on it vanishes.
- First person shows 4 ribbon segments only and the mod HUD overlaps the right part of the sword in default layout.
- Icons are Pillow pictograms; bankai icon still a placeholder; Blender renders pending.
- Third person on a real player (not only the armor stand) not screenshotted again; the stand with arm pose [-20, 0, 0] is the reference.
