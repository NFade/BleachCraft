# VFX STORYBOARD: frame-by-frame effects contract (phase 6a)

Status: contract for the phase 6b VFX agents. Mod `reiatsu_test` (Fabric 1.21.1, Yarn `1.21.1+build.3`). Sections 5 and 6 (the two BANKAI sections) are **DRAFT for bankai design review**; a separate designer finalises them, 6b implements them only after that review.
Binding order (later wins): `ART_BIBLE.md` v3 section 2 (starting point), 0.4, 2.6 < `ADR.md` section 2 < `STATE_MACHINE.md` < `LOG.md` "Phase 4" (what the server really sends). Research reference scenes are cited as [RS 6], [RB 6], [BS 6], [BB 6] (`research/*.md` section 6) and art bible frames as [img name]. This file does not change gameplay numbers.

Abbreviations: **FM** `frost_mote`, **IS** `ice_shard`, **SR** `snow_ring`, **LP** `light_pillar`, **PT** `petal`, **RW** `reiatsu_wisp`. **Q** = `effectQuality`. **[E]** = full-bright (emissive), **[L]** = lit by world light. Colours are HEX from the art bible palette. Sizes in blocks (1 m). Counts are for Q = 1.

---

## 1. Technique contract

### 1.1 What the server really sends (Phase 4, verified in code)
- One `effect_event` per cast or transition, sent to the caster and `PlayerLookup.tracking(caster)` at **t = 0** (accept moment): `effectId, casterId, seed, x,y,z (caster feet), dx,dy,dz (look vector), targetId (-1), startTick (MinecraftServer ticks), params`.
- `params` = `[aimX, aimY, aimZ, size]` for abilities (aim = block ray point, range Shirafune 8, mode_attack 24, scatter 40, Hakuteiken 20; for other abilities aim = caster feet); **empty** for release/seal events.
- Effect ids: 1 Rukia shikai, 2 Rukia bankai, 3 Byakuya shikai, 4 Byakuya bankai, 10 seal, 11 bankai end (cap or reiatsu 0), 20 Tsukishiro (size 4), 21 Hakuren (12), 22 Shirafune (8), 23 absolute zero (10), 30 mode_attack (1.5), 31 mode_barrier (0), 32 scatter (5), 33 Hakuteiken (20), 34 Senkei (disabled). No event for the Rukia bankai passive: the client draws it from the synced attachment `reiatsu_test:zanpakuto` (`character, state, stateSinceTick, shikaiMode, bankaiEndTick`), which is visible to all tracking players. `shikaiMode` (IDLE/ATTACK/BARRIER) also arrives through that attachment.
- `entity_fx` (kind FROZEN/ENCASED/SLOWED, `untilTick`, up to 64 ids) is sent at the server phase that applies the effect: Tsukishiro t = 1.0 (ENCASED, until +3 s), Hakuren t = 1.0 to 1.4 per wave step (SLOWED), Shirafune t = 0.6 (FROZEN, +4 s), absolute zero t = 2.0 (ENCASED, until +1.3 s).
- Server phase times (ticks 20/s): Tsukishiro 20 and 40; Hakuren 20,22,24,26,28 (2.4 blocks per step = 24 blocks/s); Shirafune 12; absolute zero 40 and 66; mode_attack 10,12,14,20; scatter 16,26,50; Hakuteiken 30,36. Storyboard times below are aligned to these.
- **Clock rule:** the client starts every timeline at t = 0 when the packet is received (`startTick` is a server tick counter and is never compared with client time). Anchor entities store `startTime = world.getTime()` (gametime, not affected by `/time set`) for late joiners. Ping shifts visuals against damage by one-way latency; accepted.

### 1.2 Mechanism per effect (ADR section 2)
| Effect | ids | Custom particles | Anchor entity `reiatsu_test:fx_anchor` (kind) | HUD overlay / post | Meshes (`blender/export/<model>/<object>.obj`) |
|---|---|---|---|---|---|
| Release flash, ring, burst | 1-4 | FM, PT | FIELD (ring quad) | flash | none |
| Aura | state | RW, FM, PT | FIELD (haze quad, per state) | none | none |
| Tsukishiro / Hakuren / Shirafune | 20-22 | FM, IS | FIELD (SR, LP, strip, shells) | flash, frost_edge, shake | `rukia_bankai_ice_shell` (shared) |
| Rukia bankai release, passive, absolute zero | 2, -, 23 | FM, IS | FIELD (LP, SR stack, crystals, shells) | flash, frost_edge, `freeze_desat` post, shake | `rukia_bankai_crystal_a-d`, `_ice_shell`, costume set (feature renderer), ribbons |
| Byakuya swarm (release, attack, barrier) | 3, 30, 31 | PT (debris only) | SWARM (`PetalSwarmRenderer`) | flash, shake | `byakuya_shikai_petal` (+ fallback quad) |
| Byakuya bankai rows, storm | 4, 32 | PT, dust | ROWS (`BankaiBladeRenderer`) + SWARM | flash, vignette_dark, darken, shake | `byakuya_bankai_blade`, `_blade_lod`, `_hilt_ground`, `_ripple`, `byakuya_shikai_petal` |
| Hakuteiken | 33 | PT white | WINGS (`WingHaloRenderer`) + SWARM | flash, vignette_dark, shake | `hakuteiken_blade_body`, `_blade_tip`, `_wing_l/r`, `_halo` |

Anchor rules (new server work for 6b): `ServerFx`/`ZanpakutoManager` spawn the anchors next to the existing `effect_event`. Tracked data: `kind, seed, startTime, ownerId, targetId, p0..p3` (aim and size). FIELD: one per cast, lives for the duration in the budget tables (<= 3 per player, the oldest ends early with a 0.2 s fade). SWARM: one per Byakuya player from id 3 until id 10/11/death/logout; ROWS: from id 4 until id 10/11; WINGS: id 33 only. Persistent anchors follow the owner (server sets position each tick, tracking interval 20); renderers read the interpolated owner pose on the client. Mesh fallbacks so 6b never blocks on Blender: petal = one billboard quad, blade = box slab 0.55 x 0.14 x 8 with the kinked tip skipped, crystal = hexagonal prism, shell = 10-sided capsule, wing = 9 quad feathers, halo = 24-gon ring.
Render layers: diffuse `RenderLayer.getEntityCutoutNoCull(tex)`; glow `getEntityTranslucentEmissive(tex)`. **Verified in the decompiled `RenderLayer`:** `ENTITY_TRANSLUCENT_EMISSIVE` and `ENTITY_CUTOUT_NO_CULL` both use `DISABLE_CULLING`, so the single-sided Hakuteiken wings, halo and ripple (art bible B16) need no duplicated faces. Additive rings, pillars and strips use `RenderLayer.getEyes(tex)` (additive, full-bright, **culling stays on**: emit both windings).

### 1.3 Assets (new, authored by 6b; 16 x 16 and 64 x 64 per art bible 2.6, hand-painted, nearest filtering)
| File (`assets/reiatsu_test/...`) | Size px | Content |
|---|---|---|
| `textures/particle/frost_mote.png` | 16 x 16 | soft 4-point star, white-blue (`#DDF3FF` centre, `#9ED3F0` rim), tinted by code |
| `textures/particle/ice_shard_0..2.png` | 3 x 16 x 16 | angular shards `#CFEFFF` / `#7FB8DF` / `#2E6FA8` shade |
| `textures/particle/petal_0..1.png` | 2 x 16 x 16 | leaf `#E5A4DC` + highlight `#F9C8F6`; variant 1 = white `#F4F8FF` (manga tint, Hakuteiken feathers) |
| `textures/particle/reiatsu_wisp.png` | 16 x 16 | vertical soft streak, white, tinted by code |
| `textures/fx/snow_ring.png` | 64 x 64 | thin ring with a soft outer glow, white on transparent |
| `textures/fx/light_pillar.png` | 64 x 64 | vertical gradient, bright core, soft side falloff, tileable vertically |
| `textures/fx/strip_core.png` | 64 x 16 | Shirafune and Hakuteiken line (glow strip, tileable along U) |
| `textures/gui/fx/vignette_dark.png` | 256 x 256 | radial alpha (0 centre, 1 corners) |
| `textures/gui/fx/frost_edge.png` | 256 x 256 | frost crystals at the borders, alpha |
| `particles/<name>.json` | - | one per particle type (`textures` list) |
| `shaders/post/freeze_desat.json` + `shaders/program/freeze_desat.{json,fsh}` | - | desaturation 0 to 0.3 (fallback: grey HUD quad, ADR R2.3) |

Meshes come from `blender/export/<model>/` (phase 3 exports; only `rukia_sealed` PNGs exist today). Particle code spawns through `ParticleManager.addParticle(effect, x, y, z, vx, vy, vz)` which returns the `Particle`; then `setColor`, `scale`, `setMaxAge` (all verified). Custom glow particles override `getBrightness` to the max lightmap and use `PARTICLE_SHEET_TRANSLUCENT`. A helper `FxParticles.spawn(type, pos, vel, hex, size, lifeTicks, drag, gravity, glow)` is the only spawn path (applies Q, distance LOD and the live cap).

### 1.4 Screen effect curves (HUD quads; `freeze_desat` is the only post pass)
| Name | Definition | Reduce motion |
|---|---|---|
| FLASH(P, tint) | additive full-screen quad; alpha ramps 0 to P in 0.05 s, holds 0.03 s, fades to 0 in 0.14 s (0.22 s). Bible flashes quoted as "P, D s" with D < 0.1 s use rise 0.02, hold 0.02, fade 0.06 (>= 6 frames at 60 FPS). | P <= 0.40, total 0.07 s (rise 0.02, hold 0.01, fade 0.04) |
| VIGNETTE(kind, L, ramp, fade) | `vignette_dark` (tint `#1C2540`) or `frost_edge` (tint `#CFEFFF`) texture at alpha L; linear ramp, hold until the stated time, linear fade | pulses removed: constant alpha 0.15 while the effect is active |
| DARKEN(L) | flat `#1C2540` quad at alpha L (stands in for the sky tint; true sky recolour and fog change were deferred in the art bible, optional stretch: fog colour lerp 35 percent via a `BackgroundRenderer` mixin, UNVERIFIED) | constant 0.10 |
| SHAKE(A, T) | camera yaw offset `A sin(2 pi 14 t)(1 - t/T)^2`, pitch the same with phase pi/2 and 0.7 A; summed over live shakes, total clamp 1.5 deg; mixin after `GameRenderer#tiltViewWhenHurt` (UNVERIFIED) | 0 |
| desat(p) | `freeze_desat` 0 to p, ramp 0.5 s | p capped at 0.15 |
| Distance rule | caster's own client: factor 1. Other players' casts: s(d) = 0.5 x clamp((24 - d)/16, 0, 1) applied to flash P, vignette L and shake A; no HUD effects beyond 24 blocks. | same |

All screen values are multiplied by `screenFxScale` (new config, default 1.0) and shake by `shakeScale`. Particle counts are never changed by reduce motion (art bible 2.0).

### 1.5 Sound policy and register
Sounds play with `ClientWorld.playSound(x, y, z, event, SoundCategory.PLAYERS, vol, pitch, false)` at the effect position, times `fxSoundVolume` (new, 1.0). Default volume 0.7 (shikai), 1.0 (bankai); "quiet" = 0.25; "loop" = re-triggered every 0.6 s by the timeline (vanilla events are one-shots). At most 24 FX sounds live, the same id within 0.05 s is dropped, `pointed_dripstone.land` capped at 8 per second.
**Verification:** every id below was found as a registration string in `SoundEvents.java` of `D:\MineBleach\mod\.gradle\loom-cache\minecraftMaven\...\minecraft-common-...-sources.jar` (1602 ids extracted). **Verified (exist in 1.21.1):** `block.beacon.activate`, `block.beacon.deactivate`, `block.beacon.ambient`, `block.beacon.power_select`, `block.end_portal.spawn`, `entity.generic.explode`, `entity.player.attack.sweep`, `entity.player.attack.crit`, `entity.player.hurt_freeze`, `block.amethyst_block.chime`, `block.amethyst_block.resonate`, `block.amethyst_cluster.break`, `block.amethyst_cluster.place`, `block.powder_snow.step`, `block.powder_snow.break`, `block.glass.break`, `item.trident.thunder`, `item.trident.throw`, `item.trident.hit_ground`, `entity.breeze.wind_burst`, `entity.warden.sonic_boom`, `entity.warden.heartbeat`, `block.pink_petals.break`, `block.pink_petals.step`, `entity.illusioner.cast_spell`, `entity.phantom.flap`, `item.shield.block`, `block.respawn_anchor.charge`, `ambient.cave`, `block.pointed_dripstone.land`, `entity.ender_dragon.growl`, `entity.ender_dragon.flap`, `item.elytra.flying`, `block.bell.use`, `block.bell.resonate`, `item.mace.smash_ground`. **Not in 1.21.1 (do not use):** `block.ice.break`, `block.ice.step` (ice uses `block.glass.*`). **Unverified:** none. Pitch values below are starting points tuned in the harness.

### 1.6 Budget rules and config
- ADR: batched vertices <= 200k per frame, CPU <= 4 ms/frame (profiler section `reiatsu_fx`), 60 FPS with 1000 bankai blades + 3000 petals at Q = 1. Estimated worst case 165k (ADR table). Fallback order if exceeded: cache settled rows in a `VertexBuffer` (UNVERIFIED), lower `bladeLodDistance`/`nearPetalCount`, lower counts, never drop the effect.
- **Distance LOD for custom particles** (applied at spawn from the event or anchor position): d <= 24 blocks x1.0; <= 48 x0.5; <= 96 x0.25; beyond 96 none (anchor meshes still draw). Petals: 100 percent <= 16, 50 percent <= 32, 25 percent <= 64, culled beyond 64 (art bible 2.6); blades: full mesh <= `bladeLodDistance` (32), LOD mesh to 160, culled beyond; emissive blade pass <= 64.
- Existing keys: `effectQuality` (0.25/0.5/1.0, scales every count including particles), `maxPetals` 3000 (global per client), `bankaiBladeCount` 200 (max 1000), `bladeLodDistance` 32, `nearPetalCount` 300, `emissiveMultiplier`, `reduceMotion`, `wingScale`. **New keys proposed here:** `maxFxParticles` 1500 (live custom particles; oldest dropped), `maxIceShells` 48, `maxCrystals` 40, `bladeCullDistance` 160, `screenFxScale` 1.0, `shakeScale` 1.0, `fxSoundVolume` 1.0, `maxConcurrentFx` 8 (timelines; when exceeded the oldest loses its screen effects first).
- Petal allocation: `maxPetals` is shared; the local player's swarm gets its share first, other swarms by distance.

---

## 2. Common effects (all four release events and the seal)

### 2.1 Release flash, shockwave ring, burst (ids 1-4) [RS 6 ep. 117, BS 6 Gemelos Sonido]
| t (s) | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.00 | Flash. Rukia tint `#DDF3FF`, Byakuya `#F3E4FF`. P: Rukia shikai 0.60, Byakuya shikai 0.40 (bible 2.4 wins over 2.1), bankai 1.00 | none | shikai `block.beacon.activate`; bankai `block.end_portal.spawn` | FLASH(P); bankai adds a 0.15 s radial blur lump (post optional, off in reduce motion) |
| 0.05 | Ground ring: SR flat quad at feet + 0.05, additive [E], 0 to 5 (shikai) or 0 to 10 (bankai) radius in 0.4 s (ease-out), alpha 1 to 0, colour = flash tint | 1 quad | `entity.generic.explode` pitch 1.6, vol 0.5 | none |
| 0.10 | Burst from chest height | Rukia 40 FM `#DDF3FF` [E], Byakuya 40 PT `#E5A4DC` [L]; speed 6 blocks/s outward (bankai 8, count x2), 15 degrees upward, drag 0.9 per tick, life 20 ticks, size 0.2 | `entity.player.attack.sweep` | SHAKE(0.4, 0.3) |
| 0.20 | Rukia only: vertical light ring (SR on a vertical plane at the blade, facing the camera yaw), white [E], radius 0.8 to 1.4 in 0.3 s, follows the right hand (approximate offset right 0.35, up 1.1, forward 0.45 from body yaw) [img rukia_shikai_2] | 1 quad | `block.amethyst_block.chime` | none |
Budget: 80 particles max, 2 quads (8 vertices), FIELD life 0.6 s. LOD: particles per 1.6; ring always. Multiplayer: bystanders get everything except the flash is scaled by s(d).

### 2.2 Reiatsu aura (continuous while the attachment shows a released state; any tracked player)
Spawner per player, 5-tick cadence, rate x Q x LOD. Cap 120 live aura particles per player and 400 per client.
| State | Particle and colour | Motion / size | Rate | Sound |
|---|---|---|---|---|
| Rukia shikai | RW [E] `#DDF3FF` fading to `#9ED3F0` over life | spawn at radius <= 0.6, y 0 to 1.0, rise 1.2 blocks/s (reaches 2.2), life 36 ticks, size 0.35 | 16/s | `block.powder_snow.step` quiet, every 2 s, pitch 1.2 |
| Byakuya shikai | RW [E] `#D9C8F0` | same | 14/s | none |
| Rukia bankai (DRAFT) | FM [E] drifting down from y + 3, speed 0.4, plus ground haze SR `#EAF8FF` alpha 0.3 radius 2.5 (merged with the passive aura of 5.2) | radius 3 | 30/s | `block.amethyst_block.resonate` quiet every 3 s, pitch 0.8 |
| Byakuya bankai (DRAFT) | RW `#CFC3F0` slower (rise 0.6) plus 10 PT drifting on a 2.5 radius ring (6.2) | radius 2.5 | 20/s | `entity.warden.heartbeat` quiet every 1.4 s |

### 2.3 Seal and bankai end (ids 10, 11)
| t | Visual | Counts | Sound | Screen |
|---|---|---|---|---|
| 0.0 | All anchors of the caster enter "ending": swarm falls and fades 0.5 s, blade rows sink 0.5 s (reverse rise), costume overlay fades 0.4 s, haze fades 1 s, darken/vignette fade 1 s | 24 FM or PT, radius 1.2 | id 10 `block.beacon.deactivate`; id 11 the same pitch 0.6 plus `block.bell.resonate` vol 0.5 (warning) | none |

---

## 3. Rukia shikai: Sode no Shirayuki

### 3.1 Some no Mai: Tsukishiro (id 20, size 4; server freeze t = 1.0, shatter t = 2.0) [RS 6 ep. 117, img rukia_shikai_7]
Technique: FIELD anchor (ring SR, 8-sided light tube LP, shells), FM/IS particles, HUD frost_edge. Total anchor life 3.0 s.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Wind-up circle: FM trail around a radius-4 ring at ground + 0.1 at 360 degrees per 0.2 s from the caster's yaw, white `#F0F8FF` [E], size 0.18 | 20 FM, life 12 ticks | `entity.player.attack.sweep` | none |
| 0.2 | Ring complete: SR radius 4, `#BFE4FF`, additive [E], alpha 0 to 1 in 0.1 s | 1 quad | `block.amethyst_block.chime` | none |
| 0.5 | Pillar: LP tube (8 sides) radius 3.5, height 0 to 14 in 0.35 s (ease-out), `#EAF8FF`, additive [E], scrolls texture up 1 block/s | 120 FM rising 4 blocks/s inside the tube, life 20 | `item.trident.thunder` vol 0.3 | FLASH(0.30, `#EAF8FF`) |
| 1.0 | Freeze (server entity_fx ENCASED): each affected mob gets the frost shell (see 5.3, alpha 0.5) | ice crust: 60 IS standing on the rim (radius 3.6 to 4, upright, size 0.25-0.4, life 60) | `entity.player.hurt_freeze` | VIGNETTE(frost_edge, 0.20, ramp 0.3 s) |
| 1.5 | Hold, frost creeps up | 15 FM (30/s over 0.5 s) | none | none |
| 2.0 | Shatter: pillar collapses (height 14 to 0 in 0.25 s), shells burst | 140 IS speed 5 outward + up, gravity 0.04, 80 FM speed 3 | `block.glass.break` x3 (0, 0.07, 0.14 s; pitches 1.0, 1.2, 0.8) | SHAKE(0.6, 0.35); frost_edge fades |
| 2.5 | Fade: ring and haze alpha to 0 over 0.5 s | 20 FM | none | none |
Budget: spawn total 455, max live 300, 1 + 1 quads + tube 32 vertices, shells <= 16 (240 vertices each). Config limits: `maxFxParticles`, `maxIceShells`. LOD: particles per 1.6; tube drawn to 160 blocks.

### 3.2 Tsugi no Mai: Hakuren (id 21, size 12, flat direction = look yaw; wave steps 24 blocks/s from t = 1.0) [RS 6 ep. 149, img rukia_shikai_9/10]
Technique: FIELD anchor, FM/IS particles. Anchor life 3.5 s.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Ground puncture under her: SR radius 2, `#C0D3E7`, alpha 1 to 0 in 0.5 s | 30 FM up 3 blocks/s | `block.glass.break` | none |
| 0.3 | 4 punctures on a semicircle radius 2.5 in front (angles -60, -20, 20, 60 degrees): SR radius 0.8 each + column of rising FM 4 blocks | 4 x 20 FM | `block.amethyst_cluster.break` x4 at 0.3, 0.36, 0.42, 0.48; pitch 1.0 + 0.1 per step | none |
| 0.7 | Gather at the blade tip: soft sphere (FM cloud) radius 0.5 `#EAF8FF` [E] shrinking to 0.2 | 30 FM converging, life 6 | `block.beacon.power_select` | none |
| 1.0 | Wave: 3 LP slices (4 wide x 3 high, spaced 1.2 blocks) travel along flat look at 24 blocks/s for 12 blocks (0.5 s), colour `#C0D3E7` to `#EAF8FF`, additive [E]; trail of frost along the path | 220 FM + 40 IS over 0.5 s emitted along the slices | `entity.breeze.wind_burst` pitch 1.2 + `block.powder_snow.break` | SHAKE(0.5, 0.4) |
| 1.5 | Impact frost on the ground path: IS stand up where server frost blocks appear (the block layer is real, particles add only sparkle) | 60 IS | `entity.player.hurt_freeze` | none |
| 3.0 | Frost fades with the temporary blocks | 40 FM | `block.glass.break` vol 0.25 | none |
Budget: spawn total 500, max live 350, quads <= 24 (96 vertices). Mobs hit (entity_fx SLOWED): 4 FM/s each while the flag is active (<= 12 mobs x 4 = 48/s).

### 3.3 San no Mai: Shirafune (id 22, aim = ray point, reach 8; server hit t = 0.6) [RS 6 ep. 160, img rukia_shikai_8]
Technique: FIELD anchor with a code-generated strip (two crossed quads, `strip_core.png`, `getEyes`), FM/IS particles. Anchor life 2.2 s.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Mote swirl converging on the blade tip, radius 1 | 30 FM, life 8 | `block.amethyst_block.resonate` pitch 1.4 | none |
| 0.3 | Strip grows from tip along the look vector: width 0.25, length 1 to 8 in 0.2 s, colour `#CFEFFF` with core `#7FB8DF` [E]; 40 IS shed along it | 40 IS, size 0.2 | `item.trident.throw` | none |
| 0.6 | Full thrust to the aim point (or 8 blocks); contact burst | 60 IS, 40 FM at the contact | `entity.player.attack.crit` + `block.glass.break` | SHAKE(0.4, 0.2) |
| 1.0 | Frost spreads over the target (entity_fx FROZEN: small shell alpha 0.35 at 1.05 x its box, 4 s) | 20 FM/s for 1 s | `entity.player.hurt_freeze` | none |
| 2.0 | Strip retracts (0.2 s), shards fall | 30 IS gravity 0.06 | `block.glass.break` vol 0.3 | none |
Budget: spawn 220, max live 120, 8 vertices strip.

---

## 4. Byakuya shikai: Senbonzakura (Chire)

Swarm technique (SWARM anchor, `PetalSwarmRenderer`): all petals are batched instances of `byakuya_shikai_petal` (16 tris, ~32 vertices) near the camera (<= `nearPetalCount` 300 nearest within 16 blocks) and one billboard quad (4 vertices) otherwise; light via `getLightmapCoordinates` refreshed every 8 frames, minimum block light 8 so the swarm never reads black; emissive pass 25 percent `#F9C8F6` per bible. Each instance `i` is closed-form from `(seed, i, t)` with `SplittableRandom(seed ^ i * 0x9E3779B97F4A7C15)`: no per-petal state. Allocation: shikai swarm uses N = min(1000, maxPetals) x Q petals. Colours: body `#E5A4DC`, highlight `#F9C8F6`, edge `#FFE9FB` [BS 6 Gemelos, ep. 364].

### 4.1 Release / Chire (id 3; item swaps to `byakuya_shikai_hilt`)
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Blade separates; stream from the habaki top (`tang_tip` ~ hand + 0.3 along the hilt) in a 25 degree cone, 3 blocks long toward body forward. Petal i launches at t_i = 0.4 i/N, speed 8 blocks/s decaying (x0.93 per tick) | 200 of the swarm petals visible in the stream, the rest launch up to t = 0.4 | `block.pink_petals.break` x3 (0, 0.1, 0.2 s) + `entity.illusioner.cast_spell` | FLASH(0.40, `#F3E4FF`) |
| 0.2 | Cloud forms: each petal blends from its launch path to its orbit slot with smoothstep over [t_i + 0.2, t_i + 0.5] | up to 1000 active | `entity.phantom.flap` quiet, loop | none |
| 0.5 | Idle orbit (also the `shikaiMode = IDLE` state): angle `theta_i + 0.6 x 2 pi t`, radius 1.4 + 1.0 u_i (ring radius 2, u random), height 0.2 + 1.3 v_i, plus noise amplitude 0.12 at 0.8 Hz and upward drift 0.15 blocks/s wrapping over 1.5 | 1000 | `block.pink_petals.step` random every 0.5 s, vol 0.3 | none |
Budget: petals 1000 (Q), vertices ~15k (300 x 40 + 700 x 4), PT debris particles <= 300 live. Config: `maxPetals`, `nearPetalCount`, `effectQuality`. Multiplayer: SWARM anchor is tracked, so late joiners see the orbit; the release flash is per 1.4.

### 4.2 Attack mode (id 30, aim up to 24, size 1.5; server hits at 0.5, 0.6, 0.7, 1.0)
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Hilt swing (player animation, phase 5 art) | none | `entity.player.attack.sweep` | none |
| 0.2 | 60 percent of the swarm detaches into a ribbon 1.2 wide toward the aim at 30 blocks/s (positions along a quadratic Bezier from the hand to the aim, petals spread in a 1.2 wide band, `t_i` offset 0.15 i/N) | 600 | `entity.breeze.wind_burst` | none |
| 0.5 | Impact: envelop the aim point, radius 1.5, 3 rev/s | 600 + 40 PT debris | `entity.player.attack.crit` at 0.5, 0.6, 0.7 | SHAKE(0.3, 0.2) |
| 1.0 | Cutting core: dense `#F9C8F6` ball radius 1 (instances shrink toward centre, alpha 0.8) with sparks | 30 PT/s | `block.pink_petals.break` loop every 0.25 s | none |
| 1.5 | Return stream to the player (reverse Bezier) and rejoin the orbit by 2.1 | 600 | `block.pink_petals.step` | none |
Attachment: `shikaiMode` ATTACK from t = 0 to 1.5 matches.

### 4.3 Barrier / dome (id 31; server 5 s, 80 percent reduction, absorption 20 HP)
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Swarm contracts toward the player | 900 (shared swarm) | `block.beacon.power_select` | none |
| 0.3 | Dome forms: hemisphere radius 3, 4 layers r_k = 2.64 + 0.12 k (k = 0..3), petals spiral up from the ground, layers spin 0.8 rev/s alternating direction | 900 | `item.shield.block` | none |
| 0.6 | Solid: lavender rim ring SR on the ground radius 3, `#CFC3F0`, alpha 0.5 [E] | 1 quad | `block.amethyst_block.resonate` pitch 0.7 vol 0.3 | none |
| per hit | Local spray of 20 PT away from the nearest hostile (random if none) when the owner's `hurtTime` rises while `shikaiMode == BARRIER`; the dome section brightens 0.2 s | 20 PT | `item.shield.block` + `block.pink_petals.break` | SHAKE(0.2, 0.2) (owner only) |
| 5.0 or mode change | Collapse: petals drop with gravity, fade over 0.6 s; rejoin the orbit | 900 | `block.pink_petals.step` | none |
Budget: 900 petals, vertices ~13k.

---

## 5. Rukia bankai: Hakka no Togame (DRAFT for bankai design review)

### 5.1 Release (id 2; settle lock 2.2 s) [RB 6 ch. 569, img rukia_bankai_3]
Technique: FIELD anchor (LP pillar, SR stack, crystals), costume as a player feature layer on all tracking clients (fade driven by `stateSinceTick`, 0.6 s), three back ribbons x 10 `rukia_bankai_ribbon_seg` at `ribbon_root` player-space (0, 0.12, 0.90), post flash. Anchor life 3.0 s.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | White pillar LP tube radius 1.2, height 20, `#FFFFFF` [E], fade out from 1.5 s. Common release flash and ring (2.1) | 60 FM burst upward | `block.beacon.activate` (also 2.1's end portal sound at pitch 1.0) | FLASH(1.0), blur lump |
| 0.4 | Mist column: 5 SR rings stacked, radius 1 growing to 3 while rising 0 to 3.5 blocks in 1.0 s, `#EAF8FF` alpha 0.5 | 80 FM | `block.powder_snow.break` | VIGNETTE(frost_edge, 0.25, ramp 0.4 s) |
| 1.0 | Costume forms: collar, pauldrons, crown, chest flower alpha 0 to 1 over 0.6 s (diffuse alpha 200, emissive per art bible 1.5); ribbons unfurl one segment per 0.04 s; 6 `crystal_a` grow around her feet (radius 1.2, scale 0 to 1 in 0.5 s, ease-out) | 20 IS | `block.amethyst_block.chime` x3 (1.0, 1.15, 1.3 s; pitches 1.0, 1.25, 1.5) | none |
| 2.0 | Settle: pillar gone, ground haze radius 3 stays (5.2) | 20 FM | none | frost_edge fades to 0.08 |
Budget: 180 particles, 1 tube + 5 + 1 quads, crystals 6 x 120 = 720 vertices, costume ~3300 tris (~6.6k vertices per player). Limit for others: costume only within 48 blocks.
**Design review questions:** (a) costume as feature layer needs `rukia_bankai_*` exports (phase 3, after Gate C); until then, skip the costume and keep crystals and ribbons. (b) Should the white pillar be visible through terrain (current: yes)?

### 5.2 Passive frost aura (no event; attachment state RUKIA + BANKAI; server radius 3, Slowness I, no damage)
| Continuous | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| Haze | SR flat quad radius 3 on the ground, `#EAF8FF`, alpha 0.3, rotates 0.05 rev/s [E] | 1 quad | none | none |
| Falling frost | FM falls from y + 3, speed 0.4, drift 0.2, life 70 ticks | 30/s | `block.powder_snow.step` every 2 s | none |
| Frost on touch | Hostile mob within 3 blocks (client check every 5 ticks, <= 24 mobs): no mesh, no tint; 4 FM/s breath puff `#CFEFFF` at its head | 4 FM/s per mob | `entity.player.hurt_freeze` once on entry | frost_edge 0.08 while one is inside (owner only) |
| Rim crystals | 6 to 10 `crystal_a/b` appear around the rim over 2 s after release, scale ease-out, persist until id 10/11; 8 FM at each spawn | <= 10 | `block.amethyst_block.chime` vol 0.3 | none |
Budget: 90 live FM per player, crystals <= 10 (1k vertices), `maxCrystals` 40 per client.

### 5.3 Ice shell (entity_fx ENCASED / FROZEN, shared with 3.1, 3.3, 5.4)
`rukia_bankai_ice_shell` scaled to the mob's bounding box (x 1.05), drawn with the emissive translucent layer, base alpha 0.6 (Tsukishiro, absolute zero) or 0.35 at 1.05 x box (FROZEN); `#8EC9EE` over `#D9E8F5`, diffuse alpha 180. Replaces the bible's "client tint" (no entity-renderer mixin). The shell ends at the effect's shatter time or `untilTick`, whichever is first, or when the entity is removed. 240 vertices, `maxIceShells` 48 per client.

### 5.4 Absolute zero (id 23, size 10; server phases t = 2.0 freeze, t = 3.3 shatter; ice blocks real from t = 2.0 for 2-5 s) [RB 6 ch. 567-568]
Technique: FIELD anchor (LP pillar, SR mist, crystals b/c, shells), FM/IS, post `freeze_desat`, frost_edge. Anchor life 7.0 s.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Blade raised: bright FM gather at the tip | 40 FM, life 10 | `block.beacon.power_select` | none |
| 0.4 | Pillar: LP radius 1.5, height 20, white [E]; mist SR expanding 0 to 10 radius over 1.0 s, `#EAF8FF` alpha 0.5 | 120 FM | `entity.warden.sonic_boom` pitch 0.5 vol 0.8 | FLASH(0.50, `#EAF8FF`) with rise 0.02 hold 0.02 fade 0.06 |
| 1.0 | Air crystallises: 80 IS spawn on a sphere-disc radius <= 10, y 0 to 4, hang, drift 0.2 blocks/s; 6 to 10 `crystal_b/c` grow from the ground at random rim-to-centre positions over 0.6 s (seeded) | 100 (80 IS + 20 FM) | `block.amethyst_cluster.place` x3 (1.0, 1.1, 1.2 s) | VIGNETTE(frost_edge, 0.30, ramp 1.0 s) |
| 2.0 | Pause: everything holds (particles stop: age frozen; crystals frozen; no new sounds until 3.0); shells on all entity_fx ENCASED targets | 0 new | none | desat(0.15), VIGNETTE(dark, 0.20, tint `#1C2540`) |
| 3.0 | Crack: white lines flash over crystals and shells for 0.3 s (flicker of overlaying white texture strip, alpha 0 to 0.8 at 12 Hz) | 40 IS flicker | `block.glass.break` vol 1.0 pitch 0.8 | none |
| 3.3 | Shatter: shells and crystals burst (scale 1 to 0 in 0.1 s), IS thrown outward speed 8, gravity 0.05 | 260 IS, 160 FM | `block.glass.break` x5 (0, 0.05, 0.1, 0.17, 0.25 s) + `entity.player.hurt_freeze` | SHAKE(1.0, 0.5); desat and frost_edge fade over 0.7 s |
| 4.0 | Aftermath: ground haze radius 10 fades over 3 s; temporary ice blocks revert (server) | 60 FM | `block.powder_snow.break` | frost_edge fade-out |
Budget: spawn 780, max live 600 (cap by `maxFxParticles`), shells <= 32 (7.7k vertices), crystals 10 (1k), quads ~20; post pass 1 per frame during desat. Multiplayer: others within 24 blocks get desat and vignette x s(d). **Design review questions:** (a) pause time 1.3 s is shorter than the bible's literal "freeze for 4 s" because the server shatters at 3.3 s; keep? (b) shell alpha 0.6 vs bible 0.7.

---

## 6. Byakuya bankai: Senbonzakura Kageyoshi (DRAFT for bankai design review)

### 6.1 Release and rising rows (id 4; settle lock 2.2 s) [BB 6 ch. 142, ep. 52, img byakuya_bankai_3/4]
Technique: ROWS anchor (`BankaiBladeRenderer`: instanced `byakuya_bankai_blade` + `_blade_lod`, emissive tip pass, `_hilt_ground`, `_ripple`), HUD vignette_dark + DARKEN, PT dust. Layout exactly as art bible 2.5: N = `bankaiBladeCount` x Q (default 200), ranks R = max(2, ceil(N/200)) <= 5, blades per rank N/(2R), spacing 1.1, rank k at 6 + 1.2 (k-1) blocks off the look axis, stagger 0.55 (k-1), scale 1 + 0.1 (k-1), rows start 3 blocks ahead and run behind, yaw so +Y faces the axis. Rest origin y = local ground - 0.5 (ground sampled per blade column with `FxGround.sample`, 100 columns/frame, cached). Blade i start delay `0.012 x d_i` (d_i blocks from the player along the row), rise `-8 m to rest` in 0.5 s, ease-out cubic.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Ground hilt (placed 0.6 blocks ahead of the feet, along flat look) descends 0.5 in 0.4 s; dust: block particles of the ground block (`ParticleTypes.BLOCK`) | 10 dust | `item.trident.hit_ground` | common flash and ring (2.1, 100 percent) |
| 0.3 | Ripple: `byakuya_bankai_ripple` scale 0 to 6 over 0.8 s (ease-out), `#CFC3F0` [E] | 1 mesh | `block.respawn_anchor.charge` | none |
| 0.6 | Darkening | none | `ambient.cave` vol 0.6 | VIGNETTE(dark, 0.50, ramp 0.6 s), DARKEN(0.25, ramp 0.6 s) |
| 1.0 | Blades rise (all N); dust 4 per blade, max 400 total, at the blade base on rise start | <= 400 dust; rows complete t ~ 2.2 (default), ~ 2.8 (max length) | `block.pointed_dripstone.land` per blade, pitch 0.6 to 0.9 (random), max 8/s; `entity.ender_dragon.growl` vol 0.25 once | SHAKE ramp 0 to 0.8 deg over 1.2 s, continuous |
| 2.2 | Rows complete: tip emissive on `#F2E9FF` | 0 | `block.beacon.ambient` loop (re-trigger 3 s) | SHAKE(1.2, 0.4) then decay |
| 2.8 | Hold: tips lean 3 degrees inward (ease 0.6 s) until id 10/11 | 0 | none | vignette 0.50 held (reduce: 0.15) |
Budget (ADR): at 1000 blades ~92k (near mesh) + 35k (LOD) + 15k (emissive) = ~142k vertices; at 200 about 50k. CPU target 4 ms for the whole `reiatsu_fx` section. Config: `bankaiBladeCount`, `bladeLodDistance`, `bladeCullDistance`, `effectQuality`. Multiplayer: ROWS anchor is tracked (192 block render distance); beyond the server tracking range the corridor is truncated (ADR R2.4, accepted). Darkening and vignette for others per 1.4 s(d).
Persistent while BANKAI: tip glow loop, `entity.warden.heartbeat` quiet every 1.5 s, aura 2.2, DARKEN 0.25.
**Design review questions:** (a) rows re-rise after the storm ends (current draft, 6.2 at t = 4.0) or stay gone until the next cast? (b) hilt placement and whether the player holds an empty hand (ADR table says empty). (c) sky/fog tint vs flat DARKEN.

### 6.2 Petal storm / scatter (id 32, aim ray 40; server tornado t = 0.8 and 1.3, impact t = 2.5)
Technique: SWARM anchor (storm mode) + ROWS dissolving + `petal` particle sparks. P = min(15, floor(maxPetals/N)) per blade (200 blades: 15; 1000: 3), total <= 3000 x Q.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Tip glow pulses to 100 percent over 0.3 s | 0 | `block.amethyst_block.resonate` pitch 0.9 | none |
| 0.3 | Blades dissolve from the tip down: each blade (start `0.008 x d_i`) sheds its P petals over 0.4 s, petals spawn along the blade at height z = 8 - 8 s (s = progress), the blade mesh shrinks to 0 | 3000 petals (default) | `block.pink_petals.break` pitch 0.6; `entity.phantom.flap` vol 0.3 | FLASH(0.15, `#F3E4FF`) |
| 0.8 | Storm: helix tornado radius from 1.5 (ground) to 5 (height 8) around the player, spin 1.2 rev/s, calm core radius >= 1.5 (> Hurtless 0.85); server tornado damage ticks | 3000 | `item.elytra.flying` vol 0.25 loop | none |
| 1.5 | Directed attack: 2000 petals stream to the aim in a band 3 wide at 40 blocks/s (Bezier), 1000 stay as the tornado | 2000 + 1000 | `entity.breeze.wind_burst` | SHAKE(0.6, 0.3) |
| 2.5 | Impact: burst radius 5, then dissolve; 300 PT particle sparks (white-pink, speed 6, life 20) | 300 PT | `entity.generic.explode` + `block.pink_petals.break` | FLASH(0.25, `#F3E4FF`) |
| 4.0 | Fade: petals fall and alpha to 0 over 0.8 s; rows re-rise (DRAFT, see 6.1 a) 4.0 to 5.2 | 0 | `block.pink_petals.step` | vignette fades |
Budget: 3000 petals (ADR: ~12k near + 11k far vertices), 300 PT particles. Multiplayer: seed + aim from params; storm placement relative to the owner's position.

### 6.3 Shukei: Hakuteiken (id 33, aim ray 20; server line t = 1.5, burst t = 1.8) [BB 6 ch. 166, ep. 59, img byakuya_bankai_10/11]
Technique: WINGS anchor (`WingHaloRenderer`: `hakuteiken_wing_l/r`, `_halo`, `_blade_body` Z-scaled, `_blade_tip`), SWARM converging, PT white particles. Wing root at the shoulder (+-0.0, 1.35, 0.15 behind body), `wingScale` config; halo centre = head top + 0.35 above, 0.45 behind the body axis, plane vertical, facing back. Stub rule: if rows are standing at the cast, 2000 petals come from the dissolving blades (P = min(10, floor(2000/N)), 0.3 s); otherwise they spawn around him at radius 6.
| t | Visual | Counts / motion | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Condense: swarm and petals rush into the sword in his hand, 0.5 s (ease-in) | 2000 petals converging | `block.beacon.activate` | VIGNETTE(dark, 0.60, ramp 0.3 s) |
| 0.5 | Wings unfold: longest feather 0 to 6 m in 0.4 s (rotation about the root, ease-out); halo fades in alpha 0 to 1 in 0.3 s, white `#FFFFFF` [E] | 80 white PT feathers (petal_1, size 0.4, drift down) | `entity.ender_dragon.flap` + `block.bell.use` | FLASH(0.40, `#FFFFFF`) |
| 1.0 | Blade glow: `blade_body` brightens, length grows 1 to 4 m in 0.5 s, glow strip scroll | 40 PT white along it | `block.amethyst_block.resonate` pitch 1.2 | none |
| 1.5 | Strike: blade stretches (Z scale) to min(20, ray distance) in 0.08 s, tip mesh fixed at the end, white line `#F4F8FF` core `#FFFFFF`; strip width 0.1 | 200 white particles along the line | `entity.warden.sonic_boom` + `item.mace.smash_ground` | FLASH(0.80, `#FFFFFF`), SHAKE(1.2, 0.5) |
| 1.8 | Impact: white burst radius 5 at the aim point; SR radius 8 `#FFFFFF` on the ground; line fades | 300 | `entity.generic.explode` + `block.glass.break` | flash decays over 0.4 s |
| 2.5 | Wings and halo dissolve into white particles over 0.5 s; blade shrinks to hand length | 100 | none | vignette fades (reduce: constant 0.15) |
Budget: ~900 tris of meshes (~1.1k vertices), particles 80 + 40 + 200 + 300 + 100 = 720, SWARM 2000 temporarily. Wing/halo single-sided meshes use the verified no-cull layer. Multiplayer: params give the aim; wings follow the owner pose.

### 6.4 Senkei (optional, `enabled=false`, id 34): not scheduled. If built after Gate C: 4 rows x 24 `byakuya_bankai_senkei_sword` (96 instances x 300 vertices = ~29k) in a ring radius 4, tint `#F25FB8`, core `#FFD3EE` [E], rotate 0.3 rev/s, release in 0.5 s at t = 2.0 with 120 pink PT; sounds `block.respawn_anchor.charge`, `block.beacon.ambient`, `entity.player.attack.crit` x8 (verified).

---

## 7. Per-effect budget summary
| Effect | Spawn total | Max live particles | Instances | Vertices (batched) | Anchor life | Limiting config |
|---|---|---|---|---|---|---|
| Release flash 2.1 | 80 | 80 | 2 quads | 8 | 0.6 s | `maxFxParticles` |
| Aura 2.2 | 16-30/s | 120 per player | 1 quad | 4 | state | `maxFxParticles` |
| Tsukishiro | 455 | 300 | 16 shells | ~3.9k | 3.0 s | `maxIceShells` |
| Hakuren | 500 | 350 | 24 quads | 96 | 3.5 s | `maxFxParticles` |
| Shirafune | 220 | 120 | 1 strip | 8 + shell 240 | 2.2 s | none |
| Rukia bankai release | 180 | 160 | 6 crystals, costume | ~1.5k + 6.6k | 3.0 s | `maxCrystals` |
| Passive | 90/s live | 90 | <= 10 crystals | ~1k | state | `maxCrystals` |
| Absolute zero | 780 | 600 | 32 shells, 10 crystals | ~9k | 7.0 s | `maxIceShells`, `maxCrystals` |
| Swarm 4.1-4.3 | PT debris | 300 | 1000 petals | ~15k | state | `maxPetals`, `nearPetalCount` |
| Bankai rows | 400 dust | 400 | 200 (1000 max) blades | ~50k (142k) | state | `bankaiBladeCount` |
| Storm | 300 sparks | 300 | 3000 petals | ~23k | 5.2 s | `maxPetals` |
| Hakuteiken | 720 | 500 | 5 meshes + 2000 petals | ~1.1k (+ petals) | 3.0 s | `wingScale` |
| Worst case (rows 1000 + storm 3000) | - | 700 | 1000 + 3000 | ~165k | - | all |

---

## 8. Multiplayer
- The server sends `effect_event` only to the caster and `PlayerLookup.tracking(caster)`; clients reconstruct everything from `(effectId, seed, pos, dir, params)`. All randomness uses the seed, so two clients see the same layout of petals, crystals and shard throws; per-frame jitter may differ.
- Remote caster rules: particles and sounds at the event position with distance LOD; screen effects scaled by s(d) (1.4); no HUD, no costume if farther than 48 blocks; anchors tracked by vanilla entity tracking so late joiners see SWARM and ROWS but not one-shot particles.
- Entity-bound visuals (shells, breath puffs) come from `entity_fx`, which tracking players receive.
- `maxPetals` and the particle caps apply per client, never per server. Phase 7 verifies with two real clients; until then the harness replays events with a remote-caster flag (`-Dreiatsu.fx.remote=true`).

---

## 9. Implementation order for 6b (cheapest, highest value first)
| Step | Work | Why first |
|---|---|---|
| 0 | Scaffolding: `FxConfig` new keys, six particle types and textures, `FxParticles`, `EffectTimeline` (client scheduler, deterministic from seed), sound helper, `ScreenFx` (flash, vignette, darken, shake with reduce motion), anchor entity skeleton + server spawn, harness `Phase6Harness` with time freeze (`-Dreiatsu.fx.freezeAt`) | everything depends on it; replaces `EffectPlaceholders` |
| 1 | 2.1 release flash/ring/burst, 2.3 seal, 2.2 auras | present in every ability, mostly particles |
| 2 | Rukia shikai 3.3 Shirafune, 3.1 Tsukishiro, 3.2 Hakuren (+ shells, `entity_fx`) | no meshes except the shell, high visual value |
| 3 | Byakuya swarm 4.1 (renderer + fallback quad), 4.2 attack, 4.3 barrier | first batched renderer; the petal LOD logic is reused by the storm |
| 4 | Byakuya bankai 6.1 rows, then 6.2 storm (after the bankai design review) | the performance stress; implement the `VertexBuffer` cache only if the F5 scenario misses the budget |
| 5 | 6.3 Hakuteiken | needs Blender wing/halo meshes |
| 6 | Rukia bankai 5.1, 5.2, 5.4 (after the bankai review); `freeze_desat` last (fallback grey quad) | costume depends on phase 3 exports; post shader has the highest risk (ADR R2.3) |
| 7 | Senkei (optional) | out of scope until Gate C |

## 10. Test plan
Harness `gradlew runPhase6` (extends the Phase 4 pattern: flat creative world, `/reiatsu state ... full`, real key presses, screenshots in `mod/run/screenshots/p6_<effect>_t<ms>.png`). Screenshots are taken at the exact event time using the freeze hook (t counted from packet receipt), camera 6 blocks behind the player, noon and midnight for glow, Fancy and Fabulous. FPS: mean frame time over 400 frames after 90 warm-up frames (same as the spike), 1280x720, render distance 6, vsync off; pass lines: <= 200k batched vertices (counter in `reiatsu_fx`), <= 4 ms CPU, >= 60 FPS.
| Effect | Screenshot points (t s) | Checks |
|---|---|---|
| 2.1 release (both) | 0.05, 0.1, 0.2, 0.5 | flash tint, ring size 5/10, burst colour, reduce-motion flash <= 0.07 s |
| Auras | 3.0 | wisp height 2.2, count caps |
| Tsukishiro | 0.2, 0.5, 1.0, 2.0, 2.3 | ring r = 4, pillar height 14, shells on 5 summoned zombies, shatter, alignment with server freeze/shatter log lines |
| Hakuren | 0.3, 1.0, 1.25, 1.5 | wave moves 24 blocks/s (positions at t 1.0 and 1.25 differ by 6 blocks), frost blocks |
| Shirafune | 0.3, 0.5, 0.6, 1.0 | strip length 1 to 8, shell on target |
| Swarm | 0.1, 0.3, 0.5, 1.5 (attack), 0.6 (barrier), 5.0 | cone, ring, dome radius 3, fallback quad |
| Rukia bankai (DRAFT) | 0.0, 0.5, 1.0, 1.6; abs. zero 0.4, 1.0, 2.0, 3.0, 3.35 | pillar, costume fade, crystals, pause desat, shatter |
| Byakuya bankai (DRAFT) | 0.0, 0.6, 1.5, 2.2, 2.8; storm 0.5, 0.8, 1.5, 2.5; Hakuteiken 0.5, 1.0, 1.5, 1.8 | ripple 6, darkening, rows layout (N = 200, 1000), 3-degree tilt, tornado radius 5, wings 6 m, halo 1.1 |
FPS scenarios (each also at Q 0.5 and 0.25, Fancy and Fabulous): **F0** empty baseline; **F1** swarm 1000; **F2** storm 3000 petals alone; **F3** 200 blades held; **F4** 1000 blades held; **F5 worst case** 1000 blades + 3000 petals together (debug flag holds both at full count; also at t = 0.5 s of the storm with live dissolve); **F6** absolute zero with 32 encased zombies + 600 particles; **F7** three Rukia abilities in 2 s; **F8** a remote caster (flag) with the local swarm active; **F9** 64 item frames + F5. Record vertex count and `reiatsu_fx` ms per scenario in `LOG.md`, plus the item-model cost (spike baseline 1.33 ms mean).

## 11. Open points (owner)
1. Camera-shake injection point and `PostEffectProcessor` invocation are UNVERIFIED (ADR R2.3); fallbacks in 1.4 and the asset list.
2. Server work added by 6b: anchor spawning and the `FxConfig` file keys. Anchor `startTime` uses `world.getTime()` (see 1.1).
3. Costume overlay, wings, halo, blade meshes depend on phase 3 exports; fallbacks are defined in 1.2.
4. Barrier hit detection is client-side (`hurtTime` edge); the server sends no hit event.
5. Bankai sections 5 and 6 carry open questions for the bankai design reviewer.
