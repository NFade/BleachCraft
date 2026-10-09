# VFX STORYBOARD: frame-by-frame effects and HUD contract (phase 6)

Status: **FINAL v2** (lead VFX/UI design, opus call 5/6, 2026-10-09). Sections 5 and 6 (bankai) are final designs and replace the earlier DRAFT; sections 1 to 4 were upgraded (glow layer, mesh shatter, decals, verified render APIs); section 7 (HUD) is new. Mod `reiatsu_test` (Fabric 1.21.1, Yarn `1.21.1+build.3`, Fabric API 0.116.17).
Binding order (later wins): `ART_BIBLE.md` v3 section 2 (starting point) and Gate C v4 items < `ADR.md` section 2 < `STATE_MACHINE.md` < `LOG.md` "Phase 4" (what the server really sends) < this file for everything visual and audible. Research scenes are cited as [RS 6], [RB 6], [BS 6], [BB 6]; frames as [img name] (private refs, look only). Gameplay numbers are never changed here; where a visual needs new server data it is listed in 1.1 "Server additions".

Abbreviations. Systems: **P:x** = vanilla `ParticleManager` particle of our type x (alpha blended, 1.3.1); **G:x** = `FxGlowBatch` additive sprite x (1.7.1, atlas cell 1.3.2); **M:x** = instanced mesh (`blender/export/...` object x) in an anchor renderer; **Q:x** = additive oriented quad/tube in `FieldRenderer` (glow layer); **D:x** = alpha ground decal (lit); **S:** screen effect (1.4); **A:** audio (1.5). **FM** frost_mote, **SF** snowflake, **IS** ice_shard, **PT** petal, **RW** reiatsu_wisp, **DP** dust_puff. **RM** = reduce motion. Counts are for tier High (`effectQuality` 1.0); tiers in 8.2. Colours HEX; sizes in blocks (1 m); times in seconds from packet receipt; lifetimes in ticks (t, 20/s) unless marked s. Easing: `oc` easeOutCubic 1-(1-x)^3, `ob` easeOutBack (c1 1.70158, c3 = c1+1): 1 + c3(x-1)^3 + c1(x-1)^2, `oe` easeOutExpo 1-2^(-10x), `iq` easeInQuad x^2, `ios` easeInOutSine.

Design rules used everywhere (the "anime-grade" contract):
1. **Three layers per hero moment**: a solid or lit shape (mesh or alpha particle) for silhouette; an additive glow 2 to 3 x larger at low intensity (fake bloom, Minecraft has none); short sparkle accents (G star4/star6, 6 to 14 t).
2. **Anticipation, hit, follow-through**: every big hit has 0.1 to 0.5 s of build-up (gather, darken, silence), a 50 to 100 ms hit-stop at contact (1.4 HITSTOP), then a slow decay (1 to 3 s).
3. **Value contrast**: darken or grade the world before the brightest moment; glows are drawn after the grade so they pop (1.7.1).
4. **Restricted palette per character** (below); additive tints are kept <= 0.6 intensity so they do not clip to white except where white is intended.
5. **Daylight readability**: everything that must read at noon has an alpha (non-additive) component: frost decals, ice meshes, petals; additive alone vanishes on a bright sky.

Palettes. Rukia: white core `#FFFFFF`, mist `#EAF8FF`, ice light `#CFEFFF`, sky ice `#9ED3F0`, mid `#8EC9EE`, core `#7FB8DF`, deep `#2E6FA8`, shadow `#1E4C86`. Byakuya: petal body `#E5A4DC`, highlight `#F9C8F6`, edge `#FFE9FB`, alt bloom `#FEA2D3`, lilac glow `#F3E4FF`, lavender `#CFC3F0`, blade tip `#F2E9FF`, night `#1C2540`, Hakuteiken `#F4F8FF` / `#FFFFFF`. Shared UI: ink `#0B0F1A`, panel `#141826`, text `#E8ECF4`, dim `#8A93A6`, gold `#E8C66A`, warn `#FF6A5A`, ok `#4ADE80`, error `#F87171`.

---

## 1. Technique contract

### 1.1 What the server sends (Phase 4, verified in code) and server additions
- One `effect_event` per cast or transition, to the caster and `PlayerLookup.tracking(caster)` at **t = 0** (accept moment): `effectId, casterId, seed, x,y,z (caster feet), dx,dy,dz (look), targetId (-1), startTick (server ticks), params`.
- `params` = `[aimX, aimY, aimZ, size]` for abilities (aim = block ray point; range Shirafune 8, mode_attack 24, scatter 40, Hakuteiken 20; other abilities aim = caster feet); **empty** for release/seal events.
- Effect ids: 1 Rukia shikai, 2 Rukia bankai, 3 Byakuya shikai, 4 Byakuya bankai, 10 seal, 11 bankai end (cap or reiatsu 0), 20 Tsukishiro (size 4), 21 Hakuren (12), 22 Shirafune (8), 23 absolute zero (10), 30 mode_attack (1.5), 31 mode_barrier (0), 32 scatter (5), 33 Hakuteiken (20), 34 Senkei (disabled). The Rukia bankai passive has no event: the client draws it from the synced attachment `reiatsu_test:zanpakuto` (`character, state, stateSinceTick, shikaiMode, bankaiEndTick`), visible to all tracking players.
- `entity_fx` (kind FROZEN/ENCASED/SLOWED, `untilTick`, up to 64 ids): Tsukishiro t = 1.0 (ENCASED, +3 s), Hakuren t = 1.0 to 1.4 per wave step (SLOWED), Shirafune t = 0.6 (FROZEN, +4 s), absolute zero t = 2.0 (ENCASED, +1.3 s).
- Server phase times (ticks): Tsukishiro 20, 40; Hakuren 20, 22, 24, 26, 28 (2.4 blocks per step = 24 blocks/s); Shirafune 12; absolute zero 40, 66; mode_attack 10, 12, 14, 20; scatter 16 to 30 (tornado, every 10 t), 50 (impact); Hakuteiken 30 (line), 36 (burst). Bankai settle lock 44 t. Storyboard times are aligned to these.
- **Clock rule:** every client timeline starts at t = 0 on packet receipt; `startTick` is never compared with client time. Anchors store `startTime = world.getTime()` (gametime) for late joiners. Ping shifts visuals against damage by one-way latency (accepted).

**Server additions required by this storyboard (6b server work, small):**
| # | What | Fields | Why |
|---|---|---|---|
| S1 | `fx_anchor` tracked data | `kind` byte, `seed` int, `startTime` int (gametime), `ownerId` int, `targetId` int, `p0..p3` float, **`phaseKind` byte, `phaseTime` int** (gametime of the last phase change) | late joiners reconstruct rows that were scattered or consumed |
| S2 | ROWS anchor is **static** at the release point (not following the owner) | `p0` = flat look yaw (deg) at release, `p1` = feet y, position = feet | the corridor stays where it rose [img byakuya_bankai_3]; storm and wings follow the live owner instead |
| S3 | ROWS `phaseKind/phaseTime` | 0 STANDING; 32 = scattered by scatter at `phaseTime`; 33 = consumed by Hakuteiken at `phaseTime` | client derives dissolve and re-form (6.2, 6.3) from these two numbers |
| S4 | `effect_event` 33 params | `[aimX, aimY, aimZ, 20, lineLength]` (lineLength = the server ray length actually used, <= 20) | the white line ends exactly where the server burst happens |
| S5 | new `entity_fx` kind **HIT** (byte 3, `untilTick` = now) | sent at each damage phase with the ids actually damaged (Tsukishiro 40, Hakuren steps, Shirafune 12, absolute zero 66, mode_attack 10/12/14/20, tornado ticks, scatter 50, Hakuteiken 30/36) | hit sparks and slot feedback on real hits only |
| S6 | voice HUD status (integrated server only, same JVM) | `VoiceHudState` volatile immutable record written by `VoiceControl`: `mic` (OFF, IDLE, LISTENING, HEARING, ERROR), `lastContactMs`, `interim`, `finalText`, `commandId`, `result` (ACCEPTED, DENIED_*, COOLDOWN, NO_MATCH, GATED), `atMs` | HUD 7.8; plus one line in `voice-bridge/index.html`: `/status?mic=<state>` on its 2.5 s poll (owner of that file: phase 5 code, not this doc) |

### 1.2 Mechanism per effect (ADR section 2 plus this file)
| Effect | ids | Particles (P) | Glow (G) | Anchor `reiatsu_test:fx_anchor` (kind) and meshes | Screen / post |
|---|---|---|---|---|---|
| Release flash, ring, burst | 1-4 | FM, PT | star4, ring_soft | FIELD | FLASH, title card (HUD) |
| Aura | state | RW, SF, PT | star6, petal_glow | none (per-player spawner) + feature renderer (Rukia bankai sheen, ribbons) | none |
| Tsukishiro / Hakuren / Shirafune | 20-22 | FM, IS, SF | ring, sigil, pillar, line, mist, star | FIELD; `rukia_bankai_ice_shell`, `_crystal_c/d`, `_shard_a/b` | FLASH, frost_edge, SHAKE |
| Rukia bankai release, passive, absolute zero | 2, -, 23 | FM, SF, IS | pillar, ring, sigil, mist, star6, crack | FIELD; crystals a-d, shell, shards; feature renderer: ice sheen + 3 ribbon chains (`rukia_bankai_ribbon_seg/_tip`) | FLASH, frost_edge, GRADE, `freeze_desat`, SHAKE, HITSTOP |
| Byakuya swarm (release, attack, barrier) | 3, 30, 31 | PT (debris) | petal_glow, streak | SWARM (`PetalSwarmRenderer`, `byakuya_shikai_petal`) | FLASH, SHAKE |
| Byakuya bankai rows, storm | 4, 32 | PT, DP, block crumbs | glow_core (tips), streak, ring_soft | ROWS (`BankaiBladeRenderer`: `byakuya_bankai_blade`, `_blade_lod`, `_hilt_ground`, `_ripple`) + SWARM | GRADE, vignette_dark, FLASH, SHAKE, SPEEDLINES, HITSTOP |
| Hakuteiken | 33 | PT (white) | feather, line, ring_thin, glow_soft | WINGS (`WingHaloRenderer`: `hakuteiken_blade_body`, `_blade_tip`, `_wing_l/r`, `_halo`) + SWARM | GRADE, IMPACT_FRAME, FLASH, SHAKE, HITSTOP |

Anchor rules: `ServerFx`/`ZanpakutoManager` spawn anchors next to the existing `effect_event`. FIELD: one per cast, lives for the anchor life of its table (<= 3 per player; the oldest ends early with a 0.2 s fade). SWARM: one per Byakuya player from id 3 until id 10/11/death/logout, follows the owner. ROWS: static (S2) from id 4 until id 10/11. WINGS: id 33 only, follows the owner. Renderers read the interpolated owner pose on the client. Mesh fallbacks (6b never blocks on Blender): petal = billboard quad, blade = box slab 0.55 x 0.14 x 8, crystal = hexagonal prism, shell = 10-sided capsule, wing = 9 quad feathers, halo = 24-gon ring.
Render layers (verified in the decompiled 1.21.1 `RenderLayer`/`RenderPhase`): diffuse `getEntityCutoutNoCull(tex)` and `getEntityTranslucent(tex)` (ice, alpha 180-200); glow overlay `getEntityTranslucentEmissive(tex)` (no culling, so single-sided wings, halo and ripple need no duplicated faces); additive `getEyes(tex)` = ADDITIVE_TRANSPARENCY (ONE, ONE), colour mask only, no depth write, culling **on** (emit both windings). Our own linear-filtered additive layer for the glow atlas: `RenderLayer.of("reiatsu_glow", POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, QUADS, 1536, false, true, MultiPhaseParameters.builder().program(EYES_PROGRAM).texture(new RenderPhase.Texture(id, true, false)).transparency(ADDITIVE_TRANSPARENCY).writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).build(false))` (`RenderLayer.of`, `EYES_PROGRAM`, `RenderPhase.Texture(Identifier, boolean blur, boolean mipmap)` are public in the named 1.21.1 sources; blur = true so 32 to 64 px glow sprites stay smooth when drawn 10 blocks wide).

### 1.3 Assets (generated by `tools/gen_fx_textures.py`, Pillow + numpy, seeded; run with `python -I`)
Rules: additive textures are **RGB premultiplied on black** (alpha ignored by ONE/ONE; fade = vertex colour brightness); alpha textures are RGBA white or palette, tinted by vertex colour. Pixel-art sprites (particles, HUD) are nearest-filtered and alpha-quantised to 8 levels; glow sprites are smooth.

#### 1.3.1 Particle sprites (`textures/particle/<name>_<frame>.png`, `particles/<name>.json` lists frames; `SpriteProvider` picks frame by age or by seed)
| Type (FabricParticleTypes.simple) | Frames x size | Light | Recipe (centre c = (w-1)/2, r = distance to c in px) |
|---|---|---|---|
| `frost_mote` | 4 x 16x16, twinkle (frame = age / 3 mod 4) | full-bright | core a = clamp(1 - r/3)^1.5; rays a = exp(-(d_perp/0.7)^2) x clamp(1 - d_along/(7 s_f)), s_f = 1.0, 0.8, 0.55, 0.8; alpha = max(core, 0.8 ray); RGB lerp(`#9ED3F0`, `#FFFFFF`, core) |
| `snowflake` | 3 x 16x16 (variant by seed) | full-bright | 6 arms (0, 60 .. 300 deg) length 6.5 px, 1 px lines alpha 255 `#FFFFFF`; side branches at 45 and 70 percent of the arm, +-60 deg, length 2.5 / 1.5; variants rotate 0/15/30 deg, branch lengths x 1.0/0.8/1.2; 1 px halo `#CFEFFF` alpha 110 |
| `ice_shard` | 4 x 16x16 (variant by seed) | lit, min block light 10 | seeded convex polygon, 5-7 vertices, aspect 2.5:1, rotated 30 deg; fill `#8EC9EE` alpha 210; split line tip-to-base, left half `#CFEFFF`, right half `#7FB8DF`; 1 px top-left rim `#FFFFFF`, 1 px bottom-right `#2E6FA8` |
| `petal` | 4 x 16x16 tumble (frame = (age/2 + seed) mod 4) | lit, min block light 8, x1.15 brightness | crescent leaf 13 x 5 px at 35 deg; body `#E5A4DC`, upper half `#F9C8F6`, 1 px edge `#FFE9FB`; frames squash width 1.0, 0.65, 0.25 (edge-on 2 px line with `#FFE9FB`), 0.65 |
| `reiatsu_wisp` | 4 x 8x32 (custom `buildGeometry`: vertical-axis billboard 0.15 x 0.6) | full-bright | alpha = exp(-((x - 3.5 - 0.8 sin(0.3y + f pi/2))/1.6)^2) x smoothstep(0, 6, y) x smoothstep(32, 18, y); white |
| `dust_puff` | 4 x 16x16 (frame by age) | lit | alpha = clamp(1 - r/R_f)^1.2 x (0.75 + 0.25 valueNoise(4 px)), R_f = 5, 6, 7, 7.5, alpha x 1, 0.85, 0.6, 0.35; RGB `#BFB6A8` x tint (ground block map colour) |
| (vanilla) `ParticleTypes.BLOCK` | - | lit | ground crumbs under rising blades (block state of the sampled ground) |

#### 1.3.2 Glow atlas `textures/fx/fx_glow.png` 256 x 256 (additive, `reiatsu_glow` layer)
| Sprite | Rect x,y,w,h | Recipe (intensity I in 0..1, written as RGB = I x 255) |
|---|---|---|
| glow_soft | 0,0,64,64 | I = exp(-(r/11.5)^2) |
| glow_core | 64,0,32,32 | I = clamp(1 - r/16)^4 + 0.6 exp(-(r/3)^2) |
| star4 | 96,0,32,32 | I = max(exp(-(r/2)^2), ray_h, ray_v, 0.35 ray_diag); ray = exp(-(d_perp/0.9)^2) x (1 - d_along/16)^2 |
| star6 | 128,0,32,32 | six rays (0, 60, 120 deg lines) length 14, d_perp sigma 0.8, plus core sigma 1.6 |
| petal_glow | 160,0,32,32 | petal silhouette (1.3.1 frame 0) scaled to 24 px, gaussian blur sigma 2.5, x 0.8 |
| feather | 192,0,32,64 | shaft 1 px x = 16, y 4..60, I = 1; vanes each side width 9 tapering to 0 at both ends, I = 0.75 (1 - abs(x-16)/9)^0.5 x (0.8 + 0.2 sin(1.6y + abs(x-16))); saw notch every 6 px on the outer edge; blur sigma 1 |
| feather_b | 224,0,32,64 | same, shaft bent: x = 16 + 4 (y/64)^2 |
| speck | 64,32,16,16 | I = exp(-(r/1.2)^2) |
| mist | 80,32,48,32 | I = exp(-((dx/18)^2 + (dy/10)^2)) x (0.6 + 0.4 fbm(3 octaves, 12 px)) |
| flare | 128,32,64,32 | I = exp(-(dy/2.2)^2) exp(-(dx/20)^2) + 0.5 exp(-(r/4)^2) |
| streak | 0,64,128,16 | I = exp(-(dy/2)^2) x (x/127)^0.7 (head at x = 127, tail fades left) |
| line | 0,80,128,16 | I = exp(-(dy/1.6)^2) + 0.35 exp(-(dy/5)^2), constant along x (tile along U) |
| ring_thin | 0,96,64,64 | I = exp(-((r-28)/1.2)^2) + 0.35 exp(-((r-28)/4)^2) |
| ring_soft | 64,96,64,64 | I = exp(-((r-24)/6)^2) |
| frost_sigil | 128,64,128,128 | rings r 60 (sigma 0.8) and 52 (0.6); dotted ring r 40 (dot every 10 deg, sigma 0.7); 6-fold snowflake, arm 48, branches at 40/65 percent (+-60 deg, 10/6 px); 12 diamonds 5 x 3 px between r 52 and 60 at 30 deg steps; all I 0.9, plus blur sigma 3 x 0.4 |
| crack | 0,160,64,64 | 5 random-walk branches from c (length 26-31, step 2 px, turn <= 35 deg), 2 sub-branches each; 1 px I = 1, blur sigma 1.5 x 0.5 |
| pillar | 64,160,32,96 | I = 0.9 exp(-((x-15.5)/5)^2) + 0.3 exp(-((x-15.5)/12)^2), x (0.85 + 0.15 sin(2 pi y/32)) (tile along V, scroll) |

#### 1.3.3 Other FX textures
| File | Size | Recipe / use |
|---|---|---|
| `textures/fx/frost_ground.png` | 128 x 128 RGBA | D: frost decal. alpha = clamp(fbm(4 oct, 32 px) x 1.6 - 0.5) x 200 + 60 needle strokes (length 6-14 px, 3 directions 0/60/120 deg, alpha 230) ; radial falloff alpha x smoothstep(64, 50, r); RGB `#EEF6FF` |
| `textures/fx/crack_ground.png` | 64 x 64 RGBA | D: blade eruption scar. 5 crack branches (as G crack) 1-2 px `#2A2433` alpha 210 + 6 chips 2x2 `#7C869A` |
| `textures/fx/frost_swirl.png` | 64 x 64 RGB | energy-swirl overlay on Rukia (5.2): diagonal crystal facets: Voronoi (12 seeds, tileable) edges I = 1 at 1 px, cells I = 0.15 + 0.1 noise; tileable both axes |
| `textures/gui/fx/vignette_dark.png` | 256 x 256 RGBA | alpha = smoothstep(0.45, 1.0, r_ellipse)^1.3 x 255, RGB black, tinted by code |
| `textures/gui/fx/frost_edge.png` | 256 x 256 RGBA | 40 seeds per edge; dendrites grown inward up to 22 percent of the size (branch every 3-5 px at +-60 deg, 1 px, alpha 220); plus fog alpha = smoothstep(0.65, 1.0, max(abs(u), abs(v))) x 120; RGB white |
| `textures/gui/fx/speed_lines.png` | 256 x 256 RGBA | 90 radial lines, random angle, width 1-2 px, start radius 0.35-0.6 of the half size, alpha 0 to 200 outward; white |
| `shaders/post/freeze_desat.json` + `shaders/program/freeze_desat.{json,fsh}` | - | desaturation 0 to `p` (luma 0.299/0.587/0.114) plus a cool tint mix `#C8DCF0` x p/2; fallback ADR R2.3 (`assets/minecraft/shaders/program/reiatsu_test_*`, final fallback grey HUD quad) |

HUD textures: section 7.10. Meshes: `blender/export/<model>/`, copied by `tools/place_model_assets.py`. MC vertex counts (OBJ faces, triangles as degenerate quads): petal 12 quads = 48 v; giant blade 96 = 384 v (tip glow-zone quads about 40 = 160 v); blade LOD 13 = 52 v; wing 64 = 256 v; halo 24 = 96 v; ripple 16 = 64 v; ground hilt 580 = 2320 v; crystal b 28 = 112 v; shell 56 = 224 v; shard 5 = 20 v.

### 1.4 Screen effect curves (HUD quads unless noted; `freeze_desat` is the only post pass)
| Name | Definition | RM variant |
|---|---|---|
| FLASH(P, tint) | additive full-screen quad: 0 to P in 0.05 s, hold 0.03 s, fade 0.14 s. "Short" flashes: rise 0.02, hold 0.02, fade 0.06 | P <= 0.40, total 0.07 s |
| VIGNETTE(kind, L, ramp, fade) | `vignette_dark` (tint `#0E1428`) or `frost_edge` (tint `#CFEFFF`) at alpha L; frost_edge also scales its UV 1.15 to 1.0 while ramping (frost creeps in) | constant alpha min(L, 0.15), no UV scale |
| GRADE(colour, s, ramp) | **multiply** full-screen quad (`blendFunc(DST_COLOR, ZERO)`) with lerp(`#FFFFFF`, colour, s), drawn at `WorldRenderEvents.LAST` (after Fabulous composite, before the hand, so hand and HUD stay ungraded); the glow batch is drawn after it (1.7.1) | unchanged (not motion) |
| DARKEN(L) | flat `#1C2540` quad alpha L (legacy, used only as GRADE fallback) | constant 0.10 |
| SHAKE(A, T) | yaw offset A sin(2 pi 14 t)(1 - t/T)^2, pitch same, phase pi/2, 0.7 A; summed, clamp 1.5 deg. Injection: `@Inject(at = HEAD)` into private `GameRenderer#tiltViewWhenHurt(MatrixStack, float)` (verified in 1.21.1 sources; called for the world and for the hand, so both shake; TAIL is wrong: the method returns early when not hurt) | 0 |
| FOV_KICK(deg, rise, fall) | `@Inject(at = RETURN)` into private `GameRenderer#getFov(Camera, float, boolean)` (double, verified): + deg over rise (oc), back over fall (ios) | 0 |
| HITSTOP(ms) | freezes the timeline clock of that effect (particle and petal updates of that effect, not the world) | unchanged |
| IMPACT_FRAME | anime impact frame: >= 1 frame (17 ms) `#FFFFFF` alpha 0.85, then >= 2 frames (33 ms) `#0B0F1E` alpha 0.6 | replaced by FLASH(0.40) short; also when `photosensitiveSafe` |
| SPEEDLINES(L, T) | `speed_lines` centred on a projected world point, scale 1.0 to 1.15 over T, alpha L, random rotation per 2 frames | off |
| desat(p, ramp) | `freeze_desat` 0 to p | unchanged |
| Distance rule | caster's own client factor 1; others s(d) = 0.5 x clamp((24 - d)/16, 0, 1) on FLASH P, VIGNETTE L, GRADE s, SHAKE A, desat p; none beyond 24 blocks. Impact effects (scatter, Hakuteiken burst) use d to the impact point, not the caster | same |
| Photosensitivity | max 3 flashes with P > 0.25 per second per client, IMPACT_FRAME at most once per 1.0 s (extra ones are downgraded to FLASH 0.25) | always on |

All screen values x `screenFxScale` (1.0); shake x `shakeScale`. Particle counts never change with RM (art bible 2.0).

### 1.5 Sound policy, verified register, custom sounds
Sounds play with `ClientWorld.playSound(x, y, z, event, SoundCategory.PLAYERS, vol, pitch, false)` at the effect position, x `fxSoundVolume` (1.0). "loop" = re-triggered by the timeline (vanilla events are one-shots). At most 24 FX sounds live; the same id within 0.05 s is dropped; `pointed_dripstone.land` <= 8/s; `block.pink_petals.break` <= 10/s. **Duck**: while an effect marks DUCK, other `reiatsu_test` FX sounds of that caster play at x0.5 (absolute-zero pause: x0).
**Verified ids** (registration strings in `SoundEvents.java` of the 1.21.1 Yarn sources jar under `.claude/worktrees/stepb/mod/.gradle/loom-cache/minecraftMaven/...minecraft-common...-sources.jar`): all ids of the previous list (`block.beacon.activate/deactivate/ambient/power_select`, `block.end_portal.spawn`, `entity.generic.explode`, `entity.player.attack.sweep/crit`, `entity.player.hurt_freeze`, `block.amethyst_block.chime/resonate`, `block.amethyst_cluster.break/place`, `block.powder_snow.step/break`, `block.glass.break`, `item.trident.thunder/throw/hit_ground`, `entity.breeze.wind_burst`, `entity.warden.sonic_boom/heartbeat`, `block.pink_petals.break/step`, `entity.illusioner.cast_spell`, `entity.phantom.flap`, `item.shield.block`, `block.respawn_anchor.charge`, `ambient.cave`, `block.pointed_dripstone.land`, `entity.ender_dragon.growl/flap`, `item.elytra.flying`, `block.bell.use/resonate`, `item.mace.smash_ground`) **plus, checked for this revision:** `block.note_block.bass/chime/bell/pling`, `ui.toast.in/out`, `ui.button.click`, `block.amethyst_block.break/hit/step`, `block.amethyst_cluster.hit`, `block.large_amethyst_bud.break`, `block.small_amethyst_bud.break`, `block.snow.break`, `block.powder_snow.fall`, `item.trident.riptide_1/riptide_3/return`, `entity.illusioner.prepare_mirror`, `entity.evoker.prepare_attack`, `block.conduit.activate/ambient/deactivate`, `block.end_portal_frame.fill`, `entity.ender_eye.death`, `block.respawn_anchor.deplete`, `entity.warden.emerge`, `entity.breeze.charge/whirl`, `item.mace.smash_air/smash_ground_heavy`, `entity.wind_charge.wind_burst`, `entity.allay.item_thrown`, `item.armor.equip_elytra`, `block.bubble_column.whirlpool_inside`, `block.fire.extinguish`, `entity.phantom.swoop`, `block.sand.break`, `block.gravel.break`, `entity.player.attack.strong/knockback`. **Not in 1.21.1:** `block.ice.break`, `block.ice.step`. Pitch/volume values are starting points tuned in the harness.

**Custom sounds worth making** (original recordings or synthesis only, never anime audio; `assets/reiatsu_test/sounds.json`, category players; each has the vanilla layering above as fallback until it exists):
| Id | Length | Content | Replaces / layers over |
|---|---|---|---|
| `fx.rukia.bankai_release` | 3.0 s | reversed cymbal swell 0.15 s into a bright ice crack, sustained airy choir pad falling a fifth, crystalline tail | 5.1 t 0 to 1.2 |
| `fx.rukia.absolute_zero` | 1.5 s | inhale swell, hard cut to silence with a single high sine ring (3.2 kHz) decaying | 5.4 t 2.0 |
| `fx.ice.shatter_big` | 1.2 s | layered glass and ice crash, sparkle tail | 3.1, 5.4 shatter |
| `fx.byakuya.drop` | 1.8 s | metallic water drop "plink" + long dark reverb | 6.1 t 0.18 |
| `fx.byakuya.rows_rise` | 2.0 s | deep stone grind + rising metal shing chord | 6.1 t 1.0 |
| `fx.petal.storm_loop` | 4.0 s seamless | thousands of thin blades: rustle + metallic whisper, band-passed wind | 6.2, 4.2 loops |
| `fx.hakuteiken.strike` | 1.4 s | sharp whoosh-crack, ringing metal tail | 6.3 t 1.5 |
| `ui.release_stinger` | 1.2 s | short taiko hit + plucked string flourish | title card 7.6 |
| `ui.cooldown_ready` | 0.3 s | soft glass chime | 7.4 |
| `ui.denied` | 0.25 s | muted wooden knock | 7.7 |

### 1.6 Budget rules and config
- ADR: <= 200k CPU-written batched vertices per frame, CPU <= 4 ms/frame (profiler section `reiatsu_fx`), 60 FPS with 1000 bankai blades + 3000 petals at High on the reference mid PC (the user's Ryzen 5 5500 machine, CPU-bound). Settled blade rows are drawn from a static GPU buffer (1.7.3), which removes them from the CPU vertex budget.
- **Distance LOD for P and G** (from the event or anchor position at spawn): d <= 24 x1.0; <= 48 x0.5; <= 96 x0.25; beyond 96 none (anchor meshes still draw). Petals: 100 percent <= 16, 50 percent <= 32, 25 percent <= 64, culled beyond 64. Blades: see 1.7.3.
- Existing keys: `effectQuality` (0.25/0.5/1.0), `maxPetals` 3000, `bankaiBladeCount` 200 (max 1000), `bladeLodDistance` 32, `nearPetalCount` 300, `emissiveMultiplier`, `reduceMotion`, `wingScale`. **New keys:** `fxTier` (LOW, MEDIUM, HIGH, CUSTOM; sets the 8.2 values, CUSTOM keeps the individual keys), `maxFxParticles` 1500, `maxGlowSprites` 4096, `maxIceShells` 48, `maxCrystals` 40, `maxShardMeshes` 256, `bladeCullDistance` 160, `screenFxScale` 1.0, `shakeScale` 1.0, `fxSoundVolume` 1.0, `glowIntensity` 1.0, `gradeStrength` 1.0, `decals` true, `photosensitiveSafe` false, `maxConcurrentFx` 8 (timelines; the oldest loses its screen effects first). HUD keys in 7.11.
- Petal allocation: `maxPetals` is shared; the local player's swarm first, other swarms by distance.

### 1.7 Render systems (implementation plan, Fabric/Yarn 1.21.1)

#### 1.7.1 `FxGlowBatch` (all G sprites)
- Pool, struct-of-arrays `float[]` (pos 3, vel 3, age, life, size0, size1, rot, spin, drag, gravity, r, g, b, intensity), `byte[]` sprite id and flags (CAMERA, STREAK, GROUND, AXIS_Y, PLANE with a stored normal), `short[]` owning timeline id (for HITSTOP). Cap `maxGlowSprites` (oldest replaced). Update on the client tick (20 Hz) for age and spawn, positions integrated per frame with tickDelta (closed-form: p = p0 + v (1 - drag^t)/(1 - drag) + 0.5 g t^2, so no per-frame integration error).
- Intensity curve per sprite: `in` (0 to peak over `rise` ticks), hold, `out` fade (default rise 2, out = last 40 percent of life); twinkle flag multiplies by 0.6 + 0.4 sin(2 pi 8 t + seed).
- Geometry: CAMERA = camera-facing quad; STREAK = quad along the screen-projected velocity, length max(size, |v| x streakTime (0.04 s default)), head at the front; GROUND = horizontal quad at y; AXIS_Y = cylindrical billboard (pillars, wisps); PLANE = fixed normal (rings on vertical planes, halo bloom).
- Draw: once per frame in `WorldRenderEvents.LAST` with `MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers()`, layer `reiatsu_glow`, then `draw(layer)`; drawn **after** the GRADE quad. Light `0xF000F0`; vertex colour = tint x intensity x `glowIntensity` (x1.3 while a GRADE with s > 0.3 is active). Cull: behind the camera (dot <= 0), beyond 96 blocks, and sprites smaller than 0.5 px projected. UNVERIFIED: depth test against the main depth buffer after the Fabulous composite at LAST; fallback: a carrier particle with sheet `ParticleTextureSheet.CUSTOM` (in the vanilla sheet list, verified) whose `buildGeometry` draws the batch through the same Immediate (vanilla `ItemPickupParticle` pattern); in that fallback glows are graded too, so raise the bankai glow multiplier to x1.6.

#### 1.7.2 Particles (P)
`FabricParticleTypes.simple()` + `ParticleFactoryRegistry.register(type, SpriteProvider factory)`; classes extend `SpriteBillboardParticle`, sheet `PARTICLE_SHEET_TRANSLUCENT` (verified in the vanilla sheet list). Full-bright types override `getBrightness` to `0xF000F0`; lit types use `max(world light, minBlockLight)`. One spawn path `FxParticles.spawn(type, pos, vel, hex, size, lifeTicks, drag, gravity, flags)` applies Q, distance LOD and the `maxFxParticles` cap (live counter decremented in `markDead`). `reiatsu_wisp` overrides `buildGeometry` for its vertical-axis billboard.

#### 1.7.3 Batched mesh renderers (anchor entity renderers; ADR section 2)
Common: meshes from the OBJ parser cached per resource reload; per-instance matrix on the CPU (JOML), vertices written with `vertex(entry, x,y,z).color().texture().overlay().light().normal()`; light per instance from `WorldRenderer.getLightmapCoordinates` cached and refreshed every 8 frames; frustum test per instance AABB (`Frustum#isVisible(Box)`).
| Renderer | Instancing | LOD / cull | Notes |
|---|---|---|---|
| `PetalSwarmRenderer` | closed-form per petal from `(seed, i, t)`, `SplittableRandom(seed ^ i * 0x9E3779B97F4A7C15L)`; no per-petal state | near: the `nearPetalCount` closest within 16 blocks use the 48 v mesh; rest: one oriented card quad (petal sprite from the shikai atlas cell, instance rotation, not camera-facing, so it flickers like a real petal); distance thinning per 1.6; swarm AABB cull | **glint**: vertex colour = lerp(body, `#FFFFFF`, pow(max(0, dot(n, toCamera)), 16)) per petal: Senbonzakura glitter for free. **Streaks**: petals faster than 12 b/s within 24 blocks add a G streak (`#F9C8F6`, intensity 0.22, length speed x 0.04 s), 1 in 10 at High. Emissive overlay 25 percent `#F9C8F6` near mesh only |
| `BankaiBladeRenderer` | closed-form layout (6.1) | **animating** blades (rise, dissolve, re-form): CPU path, full mesh within `bladeLodDistance` (32; auto 24 when N > 400), LOD beyond, cull > `bladeCullDistance`. **Settled** rows: static `VertexBuffer(VertexBuffer.Usage.STATIC)` built once (all blades full mesh, after the 3-degree lean), `bind()`, `upload(BuiltBuffer)`, `draw(viewMatrix, projMatrix, shader)` between `layer.startDrawing()` and `endDrawing()` (methods verified in 1.21.1 `gl/VertexBuffer`; uniform/fog setup UNVERIFIED, mirror `WorldRenderer#renderLayer`); light baked per blade, rebuilt when a sampled base light changes (check every 40 t) | **tip glow without a pass**: vertices with z >= 6.85 get light `0xF000F0` and vertex colour `#F2E9FF`, so tips glow at every distance in the diffuse pass; the painted emissive overlay pass only within 16 blocks (CPU, 160 v per blade); plus one G glow_core billboard per tip (size 1.6, `#F2E9FF` 0.35) at all distances. Shine sweep (6.1) = per-vertex colour band, CPU path only (cached rows use a G streak running up the tips instead) |
| `GroundHiltRenderer` (in ROWS) | 1 instance | none | 2320 v; ripple meshes 64 v each |
| `CrystalRenderer` | per FIELD anchor list (seeded positions) | diffuse translucent + emissive overlay; emissive cull > 48 | growth scale ob, base pinned; inner pulse via emissive vertex alpha |
| `IceShellRenderer` | per `entity_fx` target | cull > 64 | grows bottom-up 0.25 s (scale Y oc); `maxIceShells` |
| `ShardBurstRenderer` | pooled `rukia_bankai_shard_a/b` instances with closed-form ballistic motion (v0, spin, gravity 0.05/t^2, ground clamp then fade 0.3 s) | cull > 48, `maxShardMeshes` | real 3D shards on every shatter |
| `FieldRenderer` | per FIELD anchor: Q rings, sigils, tubes (16 sides, both windings), strips (two crossed quads), D decals (polar grid 12 rings x 32 segments, each vertex y from `FxGround.sample`, vertex alpha = reveal mask) | decals > 48 culled | `FxGround.sample(x, z)`: scan from feet + 3 down to feet - 6 for the first solid top face (not the heightmap: caves); cached per column |
| `WingHaloRenderer` | 5 meshes | none (always near the owner) | wings: emissive translucent pass + **overglow** pass: same mesh again in `reiatsu_glow`-style additive with `byakuya_bankai_emissive.png`, scale 1.06 about the root, `#F4F8FF` x 0.35; feather ripple: vertex offset 0.08 sin(2 pi 1.2 t - 1.5 r) along the wing normal, r = distance from root / 6 |
| Rukia feature renderer | `LivingEntityFeatureRendererRegistrationCallback` | none (others > 48 blocks culled) | **ice sheen**: `getContextModel().render(matrices, vc, light, overlay, color)` (verified `Model#render(..., int color)`) into `RenderLayer.getEnergySwirl(frost_swirl, u, v)` (verified, additive) with matrices scaled 1.04, UV scroll (0.004 t, 0.011 t), colour `#9ED3F0` x intensity (vanilla charged-creeper pattern, `EnergySwirlOverlayFeatureRenderer`); **ribbons** 3 chains of 9 `rukia_bankai_ribbon_seg` + 1 `_tip` (5.2) |

#### 1.7.4 Global caps and what to cull first (when frame time > 16.6 ms for 30 frames, auto-degrade one notch, never below the tier minimum; logged)
1. Petal streaks and blade shine sweep off. 2. Glow sprite cap x0.5. 3. Blade emissive overlay pass off (tips still glow via vertex light). 4. `nearPetalCount` x0.5. 5. Decal grid 12 to 6 rings. 6. Particle cap x0.5. Never drop an effect (art bible 2.6).

---

## 2. Common effects (all four release events and the seal)

### 2.1 Release flash, shockwave ring, burst (ids 1-4) [RS 6 ep. 117, BS 6]
| t | Visual | Systems and counts | A: sound (id pitch vol) | S: screen |
|---|---|---|---|---|
| 0.00 | Flash. Rukia `#DDF3FF`; Byakuya shikai `#F3E4FF`. Byakuya bankai (id 4) has **no white flash** (his release is a quiet drop, 6.1) | none | shikai `block.beacon.activate` 1.2 0.7; Rukia bankai 5.1 | FLASH(0.60 Rukia shikai / 0.40 Byakuya shikai / 1.00 Rukia bankai) |
| 0.05 | Ground ring at feet + 0.05, radius 0 to 5 (shikai) / 10 (bankai) in 0.4 s oc, intensity 1 to 0, flash tint; second G ring_thin 0.06 s behind at x0.85 radius | G ring_soft + ring_thin (GROUND) | `entity.generic.explode` 1.6 0.5 | none |
| 0.10 | Burst from chest height, speed 6 b/s outward (bankai 8, x2), 15 deg up, drag 0.9/t, life 20 t, size 0.2; plus 12 G star4 (life 10 t, twinkle) | Rukia 40 P:FM `#DDF3FF`; Byakuya 40 P:PT `#E5A4DC` + 12 G petal_glow | `entity.player.attack.sweep` 1.0 0.7 | SHAKE(0.4, 0.3) |
| 0.20 | Rukia only: vertical light ring at the blade (PLANE facing camera yaw), radius 0.8 to 1.4 in 0.3 s, white, follows the right hand (right 0.35, up 1.1, fwd 0.45 from body yaw) [img rukia_shikai_2] | G ring_thin + G glow_soft 0.25 behind it | `block.amethyst_block.chime` 1.3 0.6 | none |
| 0.12-1.80 | Title card (HUD 7.6) for the caster only | HUD | `ui.release_stinger` (custom) or `block.bell.use` 1.5 0.4 | HUD |
Budget: 80 P + 30 G, FIELD 0.6 s. Bystanders: everything, flash x s(d), no title card.

### 2.2 Reiatsu aura (continuous while released; any tracked player; per-player spawner, 5-tick cadence, rate x Q x LOD; cap 120 P per player, 400 per client)
| State | Visual | Systems / rate | Sound |
|---|---|---|---|
| Rukia shikai | P:RW `#DDF3FF` fading to `#9ED3F0`, spawn radius <= 0.6, y 0-1.0, rise 1.2 b/s, life 36 t, size 0.35; plus G star6 glints on the blade tip line, life 8 t | RW 16/s, G 3/s | `block.powder_snow.step` 1.2 0.25 every 2 s |
| Byakuya shikai | P:RW `#D9C8F0`, same motion | 14/s | none (the swarm has its own sounds) |
| Rukia bankai | final design in 5.2 | | |
| Byakuya bankai | P:RW `#CFC3F0` slower (rise 0.6 b/s, life 50 t) + 10 orbiting PT on radius 2.5 (part of the SWARM residue, 6.1) | RW 20/s | `entity.warden.heartbeat` 0.7 0.15 every 1.5 s |

### 2.3 Seal and bankai end (ids 10, 11)
| t | Visual | Systems | Sound | Screen |
|---|---|---|---|---|
| 0.0 | All anchors of the caster enter "ending": swarm falls with gravity and fades 0.5 s; rows sink (reverse rise, iq 0.5 s, delay 0.006 d_i); ground hilt rises out of the ground and flies to the hand (0.4 s, oc); ribbons retract one segment per 0.03 s; ice sheen fades 0.4 s; decals and haze fade 1.0 s; GRADE/vignette fade 1.0 s | 24 P:FM or P:PT radius 1.2 + 10 G star4 | id 10 `block.beacon.deactivate` 1.0 0.7 (+ Byakuya `item.trident.return` 0.8 0.6); id 11 the same at pitch 0.6 + `block.bell.resonate` 0.8 0.5 + `block.respawn_anchor.deplete` 0.9 0.5 | HUD transition 7.5 |

---

## 3. Rukia shikai: Sode no Shirayuki

### 3.1 Some no Mai: Tsukishiro (id 20, size 4; server freeze t 1.0, shatter t 2.0) [RS 6 ep. 117, img rukia_shikai_7]
FIELD anchor, life 3.0 s.
| t | Visual | Systems and counts | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Wind-up circle: mote trail sweeping a radius-4 ring (360 deg per 0.2 s from caster yaw), `#F0F8FF` | 20 P:FM size 0.18 life 12 t + 20 G speck along the path | `entity.player.attack.sweep` 1.1 0.7 | none |
| 0.2 | Ring complete: sigil on the ground radius 4, rotates 0.05 rev/s, `#BFE4FF`, intensity 0 to 0.9 in 0.1 s; D frost_ground radius 0 to 4.2 reveal 0.3 s | G frost_sigil (GROUND, size 8.4) + D | `block.amethyst_block.chime` 1.0 0.7 | none |
| 0.5 | Pillar: core tube radius 3.3 `#EAF8FF` 0.55 + outer tube radius 4.0 `#CFEFFF` 0.25, height 0 to 14 in 0.35 s oc, texture scroll up 2 b/s | Q pillar x2 (16 sides) + 120 P:FM rising 4 b/s life 20 + 40 G star6 rising | `item.trident.thunder` 1.4 0.3 + `block.beacon.power_select` 1.2 0.5 | FLASH(0.30, `#EAF8FF`) short |
| 1.0 | Freeze (ENCASED): shells grow on targets (5.3); ice crust standing on the rim radius 3.6-4 | 60 P:IS upright size 0.25-0.4 life 60 t; 8 G star4 per shell | `entity.player.hurt_freeze` 1.0 0.8 + `block.amethyst_cluster.place` 0.9 0.6 | VIGNETTE(frost_edge, 0.20, ramp 0.3) |
| 1.5 | Hold, frost creeps; sigil intensity pulses 0.9 to 1.1 at 3 Hz (tension) | 15 P:FM + 10 G star6 twinkle | none | none |
| 2.0 | Shatter: pillar collapses (14 to 0 in 0.25 s iq), shells burst into real shard meshes | M shards 8 per shell (`maxShardMeshes`) + 140 P:IS speed 5 out+up gravity 0.04 + 80 P:FM + 40 G star4; HITSTOP 60 ms before the burst | `fx.ice.shatter_big` or `block.glass.break` x3 (0, 0.07, 0.14 s; 1.0, 1.2, 0.8) | SHAKE(0.6, 0.35); frost_edge fades 0.5 s |
| 2.5 | Fade: sigil, decal and haze to 0 in 0.5 s | 20 P:FM | none | none |
Budget: spawn 455 P + ~150 G, max live 300 P; quads: 2 tubes (2 x 128 v) + sigil + decal grid (1536 v); shells <= 16; shards <= 128.

### 3.2 Tsugi no Mai: Hakuren (id 21, size 12, direction = flat look yaw; wave steps 24 b/s from t 1.0) [RS 6 ep. 149, img rukia_shikai_9/10]
FIELD anchor, life 3.5 s. Upgrade: the wave is a **fan of ice spikes** erupting along the path (crystal meshes), not only flat quads.
| t | Visual | Systems and counts | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Ground puncture under her: ring radius 2 `#C0D3E7` 1 to 0 in 0.5 s | G ring_soft + 30 P:FM up 3 b/s | `block.glass.break` 1.2 0.5 | none |
| 0.3 | 4 punctures on a semicircle radius 2.5 (-60, -20, 20, 60 deg): ring radius 0.8 + column of rising motes 4 blocks | 4 G ring_thin + 4 x 20 P:FM | `block.amethyst_cluster.break` x4 at 0.30, 0.36, 0.42, 0.48; pitch 1.0 + 0.1 step | none |
| 0.7 | Gather at the blade tip: G glow_core 0.5 to 0.2 `#EAF8FF` (inverse grow = compress) | 30 P:FM converging life 6 + 1 G glow_core | `block.beacon.power_select` 1.3 0.6 | none |
| 1.0 | Wave: per server step k (0..4, t = 1.0 + 0.1 k, distance 2.4 (k+1)): 6 crystals (`crystal_c` x4, `crystal_d` x2) across a 4-wide fan, tilted 25-35 deg forward, scale 0 to 1 in 0.12 s ob; mist sprites along the path; leading edge: 3 Q line slices 4 wide x 3 high moving at 24 b/s, `#C0D3E7` to `#EAF8FF` | 30 M crystals (`maxCrystals`), 40 G mist (size 3, `#EAF8FF` 0.18, life 40 t), 220 P:FM + 40 P:IS along the slices | `entity.breeze.wind_burst` 1.2 0.8 + `block.powder_snow.break` 0.9 0.8 + per step `block.amethyst_cluster.place` 0.8 + 0.1 k 0.6 | SHAKE(0.5, 0.4) |
| 1.5 | Frost on the real temp blocks (server); glints | 60 P:IS + 20 G star6 | `entity.player.hurt_freeze` 1.0 0.6 | none |
| 3.0 | Crystals shatter (4 shards each) as the temp blocks revert | M shards 4 x 30 = 120 + 40 P:FM | `block.glass.break` 1.0 0.4 x2 | none |
Budget: spawn 500 P + 100 G, 30 crystals (3.4k v), shards <= 120. SLOWED mobs: 4 P:FM/s each (<= 12 mobs).

### 3.3 San no Mai: Shirafune (id 22, aim ray, reach 8; server hit t 0.6) [RS 6 ep. 160, img rukia_shikai_8]
FIELD anchor with a strip; life 2.2 s.
| t | Visual | Systems and counts | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Mote swirl converging on the blade tip, radius 1 | 30 P:FM life 8 + 10 G star6 | `block.amethyst_block.resonate` 1.4 0.6 | none |
| 0.3 | Ice blade grows from the tip along the look: core strip width 0.25 `#CFEFFF` + glow strip width 0.7 `#7FB8DF` 0.35, length 1 to 8 in 0.2 s oe; a `crystal_b` (scale 0.8, pointing forward) rides the tip | Q line x2 (crossed quads each) + M crystal + 40 P:IS shed (size 0.2) | `item.trident.throw` 1.1 0.8 | FOV_KICK(2, 0.08, 0.3) |
| 0.6 | Full thrust to the aim (or 8); contact burst; HIT sparks on the target (S5) | 60 P:IS + 40 P:FM + 24 G star4 + G flare at contact (size 2, 0.15 s) | `entity.player.attack.crit` 1.0 0.9 + `block.glass.break` 1.3 0.7 | SHAKE(0.4, 0.2) |
| 1.0 | Frost spreads over the target (FROZEN shell alpha 0.35 at 1.05 x box, 4 s) | 20 P:FM/s for 1 s + 4 G star6/s | `entity.player.hurt_freeze` 1.1 0.6 | none |
| 2.0 | Strip retracts 0.2 s; tip crystal shatters | M shards 6 + 30 P:IS gravity 0.06 | `block.glass.break` 1.4 0.3 | none |
Budget: spawn 220 P + 60 G, strip 16 v, crystal 112 v, shell 224 v.

---

## 4. Byakuya shikai: Senbonzakura (Chire)

Swarm (SWARM anchor, `PetalSwarmRenderer`, 1.7.3): instances closed-form from `(seed, i, t)`; N = min(1000, maxPetals) x Q; light min block light 8; glint and streaks per 1.7.3; emissive overlay 25 percent `#F9C8F6`. Colours: body `#E5A4DC`, highlight `#F9C8F6`, edge `#FFE9FB`; manga tint `#F5F5F5` [BS 6, ep. 364].

### 4.1 Release / Chire (id 3; item swaps to `byakuya_shikai_hilt`)
| t | Visual | Systems and counts | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Blade separates: stream from `tang_tip` (hand + 0.3 along the hilt) in a 25 deg cone, 3 blocks toward body forward; petal i launches at t_i = 0.4 i/N, 8 b/s decaying x0.93/t; a lilac flare at the habaki | swarm 200 visible in the stream, the rest launch to t 0.4; 60 G petal_glow along the stream (life 12 t); G flare size 1.2 0.2 s | `block.pink_petals.break` x3 (0, 0.1, 0.2) + `entity.illusioner.cast_spell` 1.2 0.6 | FLASH(0.40, `#F3E4FF`) |
| 0.2 | Cloud forms: each petal blends from its launch path to its orbit slot with smoothstep over [t_i + 0.2, t_i + 0.5] | up to 1000 | `entity.phantom.flap` 1.4 0.25, loop 0.8 s until 1.0 | none |
| 0.5 | Idle orbit (= `shikaiMode` IDLE): angle theta_i + 0.6 x 2 pi t, radius 1.4 + 1.0 u_i, height 0.2 + 1.3 v_i, noise 0.12 at 0.8 Hz, drift up 0.15 b/s wrapping over 1.5 | 1000; glints | `block.pink_petals.step` random every 0.5 s, 1.2 0.3 | none |
Budget: 1000 petals ~16.8k v (300 x 48 + 700 x 4), PT debris <= 300 live.

### 4.2 Attack mode (id 30, aim <= 24, size 1.5; server hits 0.5, 0.6, 0.7, 1.0)
| t | Visual | Systems and counts | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Hilt swing (player animation) | none | `entity.player.attack.sweep` 0.9 0.8 | none |
| 0.2 | 60 percent of the swarm becomes a ribbon 1.2 wide to the aim at 30 b/s (quadratic Bezier hand to aim, band gaussian sigma 0.4, t_i offset 0.15 i/N); leading 60 petals carry bright streaks `#FFE9FB` 0.4 | 600 petals + 60 G streak | `entity.breeze.wind_burst` 1.1 0.8 + `entity.phantom.swoop` 1.6 0.4 | none |
| 0.5 | Envelop the aim, radius 1.5, 3 rev/s; HIT sparks per damaged id (S5): 6 G star4 + 4 P:PT each | 600 + 40 P:PT debris | `entity.player.attack.crit` at 0.5, 0.6, 0.7 (pitch 1.0, 1.1, 1.2) | SHAKE(0.3, 0.2) |
| 1.0 | Cutting core: dense ball radius 1 (instances shrink toward the centre, alpha 0.8) + G glow_soft `#F9C8F6` 0.35 size 3 | 30 P:PT/s sparks | `fx.petal.storm_loop` 1.3 0.4 or `block.pink_petals.break` every 0.25 s | none |
| 1.5 | Return stream (reverse Bezier), rejoin the orbit by 2.1 | 600 | `block.pink_petals.step` 1.0 0.4 | none |

### 4.3 Barrier / dome (id 31; server 5 s, 80 percent reduction, 20 HP pool)
| t | Visual | Systems and counts | Sound | Screen |
|---|---|---|---|---|
| 0.0 | Swarm contracts toward the player | 900 | `block.beacon.power_select` 1.1 0.6 | none |
| 0.3 | Dome: hemisphere radius 3, 4 layers r_k = 2.64 + 0.12 k, petals spiral up from the ground, layers spin 0.8 rev/s alternating | 900 | `item.shield.block` 0.8 0.7 | none |
| 0.6 | Solid: lavender rim ring on the ground radius 3 `#CFC3F0` 0.5 + faint dome shimmer: 12 G glow_soft on the dome surface, size 2.5, `#F3E4FF` 0.12, drifting | G ring_soft (GROUND) + 12 G | `block.amethyst_block.resonate` 0.7 0.3 | none |
| per hit | Owner `hurtTime` edge while BARRIER: 20 PT spray away from the nearest hostile; that dome section brightens 0.2 s (G glow_soft 0.5 at the hit direction, radius 3) | 20 P:PT + 1 G | `item.shield.block` 1.1 0.8 + `block.pink_petals.break` 1.0 0.6 | SHAKE(0.2, 0.2) owner |
| 5.0 or mode change | Collapse: petals drop with gravity, fade 0.6 s, rejoin the orbit | 900 | `block.pink_petals.step` 0.9 0.5 | none |

---

## 5. Rukia bankai: Hakka no Togame (FINAL)

Concept: "white silence". A blinding white pillar with a horizontal mist cap [img rukia_bankai_1, _4], then stillness: the ground around her turns to frost, snow falls only around her, her body carries a crystalline sheen and three ribbons float behind her like the ep. 385 frame [img rukia_bankai_3]. Absolute zero is the one moment where the whole screen loses colour and time stops. The costume set is not built (Gate C, deferred); the ice sheen overlay plus ribbons carry the transformation until it exists. If costume meshes arrive later they fade in at 5.1 t 0.9 with the sheen.

### 5.1 Release (id 2; settle lock 2.2 s) [RB 6 ch. 569, img rukia_bankai_1/3/4]
FIELD anchor, life 3.0 s. Pillar centre = caster feet (does not follow).
| t | Visual (shape, colour, size) | Systems and counts | Sound | Screen (RM) |
|---|---|---|---|---|
| 0.00 | **Implosion**: shikai wisps and motes are sucked into her chest (y + 1.2) from radius 2.5 in 0.12 s (iq) | 30 P:FM + 24 G star6 converging, life 3 t | `block.beacon.power_select` 1.6 0.6 + `block.amethyst_block.resonate` 0.5 0.8 | VIGNETTE(frost_edge, 0.25, ramp 0.1) |
| 0.12 | **Detonation**: white pillar = core tube r 0.9 `#FFFFFF` 1.0 + outer tube r 2.2 `#CFEFFF` 0.45, height 0 to 32 in 0.18 s oe, V scroll 6 b/s; base flare 6 x 1.5 at the feet; ground shock ring r 0 to 12 in 0.45 s oc, `#EAF8FF` 0.8 to 0; 80 sparks radial 9 b/s drag 0.86 life 14 t | Q pillar x2 (2 x 128 v), G flare (size 6), G ring_soft + ring_thin (GROUND), 80 G star4 | `fx.rukia.bankai_release` or `block.end_portal.spawn` 1.2 1.0 + `entity.warden.sonic_boom` 1.6 0.5 + `block.glass.break` 0.6 0.8 | FLASH(1.00, `#FFFFFF`) (RM 0.40/0.07 s), SHAKE(1.0, 0.45) (RM 0), FOV_KICK(3, 0.06, 0.5) (RM 0) |
| 0.30 | **Mist cap** (ch. 569): horizontal disc at y + 18, r 0 to 10 in 0.9 s oc, `#FFFFFF` to `#EAF8FF`, 0.7 fading to 0 by t 2.0; **mist column**: 6 stacked rings y 0.5..5.5, r 1 to 3.2, rising 1.2 b/s, rotating +-0.25 rev/s alternating, `#EAF8FF` 0.45; snow spiral around the pillar r 1.4-2.0, 1.5 rev/s, up 5 b/s | G ring_soft + G glow_soft (GROUND, at y + 18, sizes 20 / 14), 6 G ring_soft (GROUND), 60 P:SF life 30 t, 30 G mist (AXIS_Y, size 2.5, 0.2) | `block.powder_snow.break` x3 (0.30, 0.38, 0.46; 0.8, 0.9, 1.0; 0.9) + `item.elytra.flying` 1.4 0.3 | none |
| 0.60 | **Ground freezes**: D frost_ground r 0 to 3.4 reveal 0.5 s oc; frost sigil r 3.2 intensity 0 to 0.35 rotating 0.02 rev/s; **6 crystals** (`crystal_a` x3, `_b` x3) at radius 1.2-1.6, seeded angles, staggered 0.06 s, scale 0 to 1.08 to 1.0 (ob 0.35 s), a glint at each tip on completion | D (1536 v) + G frost_sigil + 6 M crystals + 6 G star4 + 24 P:IS (4 per crystal) | `block.amethyst_cluster.place` per crystal (0.9 + 0.08 i, 0.7) + `block.amethyst_block.chime` x3 (1.0, 1.15, 1.3 s; 1.0, 1.25, 1.5) | none |
| 0.60-1.20 | **Ice sheen** on her body fades in: energy-swirl overlay `#9ED3F0`, intensity 0 to 0.5 then settles to 0.22 by t 2.0 | feature renderer | none | none |
| 0.90 | **Ribbons unfurl**: 3 chains from `ribbon_root` player-space (0, 0.12, 0.90) (blender axes: 0.12 behind, 0.90 up), roots spread x = -0.10, 0, +0.10; segments appear one per 0.05 s (10 in 0.5 s), each new segment flicks out with a spring (overshoot 15 deg, damping 0.6); tips trail G streak `#CFEFFF` 0.3 for 0.4 s | 3 x 10 meshes (feature renderer), 3 G streak | `item.armor.equip_elytra` 1.5 0.5 + `entity.phantom.flap` 1.6 0.3 | none |
| 1.20 | Pillar narrows (radius x1 to x0.2) and its bottom lifts away (bottom y 0 to 20) over 0.8 s ios; snow falls out of it | 120 P:FM drifting down + 40 P:SF | `block.beacon.ambient` 1.5 0.4 | none |
| 2.00 | **Settle**: passive aura state (5.2) owns the decal, sigil, crystals and ribbons from here (handover without a pop: same seed) | | `block.amethyst_block.resonate` 0.6 0.3 | frost_edge 0.25 to 0.06 over 1 s |
| 0.12-2.80 | Bankai title card (HUD 7.6), caster only | HUD | `ui.release_stinger` | HUD |
Budget: P 330, G ~260, quads: 2 tubes + 9 G discs, decal 1536 v, crystals 6 x ~100 v, ribbons 30 x 48 v. Others: everything within 48 blocks; flash/shake x s(d); pillar visible to 160 blocks (anchor render distance 192; pillar drawn through terrain: no, depth-tested like everything else).
**Server sends:** `effect_event` id 2 (existing). Nothing new.

### 5.2 Passive frost aura (no event; attachment RUKIA + BANKAI; server radius 3, Slowness I, no damage)
Drawn by a per-player client controller (no anchor), visible to all tracking clients.
| Element | Visual | Systems / rate | Sound |
|---|---|---|---|
| Frozen ground | D frost_ground radius 3.4 (reads at noon) under G frost_sigil radius 3.0 `#CFEFFF` 0.22, rotating 0.02 rev/s, breathing +-10 percent at 0.25 Hz; follows her feet (decal re-sampled every 4 t when she moves > 0.25 blocks; fade-trail: the previous decal fades over 1.5 s) | D 1536 v + 1 G | none |
| Snowfall | flakes from a radius-3.5 disc at y + 3.5, fall 0.5 b/s, sine drift 0.25 b/s, spin, life 140 t (live ~100); glints | 14 P:SF/s + 6 G star6/s (life 10 t, twinkle, radius 3, y 0.2-2.5) | `block.powder_snow.step` 1.3 0.2 random every 2-4 s |
| Cold breath | every 3 s: 6 motes puff from her head forward 0.6 b/s, `#EAF8FF` alpha 0.4 | 6 P:FM | none |
| Ice sheen | energy-swirl overlay `#9ED3F0` 0.22, UV scroll; flares to 0.5 for 0.3 s on any of her casts | feature renderer | none |
| Ribbons | 3 floating chains (not hanging): segment j angle = base curve (chain k: up-back-outward, curvature 25 deg per segment, sign flips after segment 5, forming the S-loops of [img rukia_bankai_3]) + sway 6 deg sin(2 pi 0.35 t - 0.6 j + k); body motion lag: root velocity x 0.15 s offsets the chain; edge glow via the emissive map (ribbon edges 40 percent) | 30 meshes | none |
| Rim crystals | 8 crystals (`_a`, `_b`) on radius 2.6-3.0 (seeded), appear over 2 s after release (ob 0.35 s each), stay at the release point (not following) for the bankai, emissive pulse 0.6-1.0 at 0.3 Hz offset by i; random tip glint every ~2.5 s | 8 M + G star4 | `block.amethyst_block.chime` 1.2 0.3 per crystal |
| Mobs inside (client check every 5 t, <= 24 mobs) | frost decal radius 0.6 under the mob (D, 2 rings x 16 segments) + 3 P:FM/s at its feet + 2 G star6/s on its body box; breath puff every 2 s at its head | per mob | `entity.player.hurt_freeze` 1.2 0.4 once on entry |
| Cold hum | | | `block.amethyst_block.resonate` 0.6 0.15 every 4 s |
| Owner screen | frost_edge 0.06 constant; 0.12 while a hostile is inside | | |
Budget: ~110 live P, G ~60, decals ~1.7k v, crystals 8 x ~100 v, ribbons 1.4k v per player; `maxCrystals` 40 per client.

### 5.3 Ice shell (entity_fx ENCASED / FROZEN; shared with 3.1, 3.3, 5.4)
`rukia_bankai_ice_shell` scaled to the mob box x1.05, base alpha 0.6 (ENCASED) or 0.35 (FROZEN), translucent diffuse + 30 percent emissive overlay. Grows bottom-up 0.25 s (scale Y oc, base pinned) with `block.amethyst_cluster.place` 1.4 0.5; while active 2 G star6/s on its surface. Ends at the effect's shatter time or `untilTick` (whichever first) or entity removal: **shatter** = 8 shard meshes (ShardBurst, v0 3-6 b/s outward + 2 up, spin 2-8 rad/s) + 12 P:IS + 8 G star4, `block.glass.break` 1.2 0.6. 224 v, `maxIceShells` 48.

### 5.4 Absolute zero (id 23, size 10; server freeze t 2.0, shatter t 3.3; temp ice 2.0 to 7.0) [RB 6 ch. 567-568, img rukia_bankai_5/6]
FIELD anchor centred at the caster feet at cast time; life 7.0 s. Marked DUCK from 2.0 to 3.0 (silence).
| t | Visual | Systems and counts | Sound | Screen (RM) |
|---|---|---|---|---|
| 0.00 | Blade raised: gather at the tip (hand + look x 1.0 + up 0.3); tip glow_core 0.2 to 0.6 size, `#CFEFFF` | 40 P:FM + 16 G star4 converging 0.35 s; 1 G glow_core | `block.beacon.power_select` 0.8 0.7 + `block.amethyst_block.resonate` 0.5 0.8 | VIGNETTE(frost_edge, 0.15, ramp 0.3) |
| 0.40 | **Cold wave**: ground ring r 0 to 10 in 0.6 s oc (`#EAF8FF` 0.9 to 0.2) + ring_thin 0.1 s behind; **frost wall**: 16-sided band at the wave front, height 2.5, pillar texture `#CFEFFF` 0.35 fading upward; **ground freezes behind the front**: D frost_ground r 0 to 10.5 radial reveal synced with the wave (vertex alpha = smoothstep(R(t), R(t) - 1, r)); ground mist sheet | G ring_soft + ring_thin (GROUND); Q band (16 x 2 quads); D (12 x 32 grid, 1536 v); 60 G mist (size 2.5-4, `#EAF8FF` 0.12, life 80 t, y 0.3) ; 120 P:FM | `entity.warden.sonic_boom` 0.5 0.8 + `item.trident.riptide_3` 0.7 0.6 + `block.powder_snow.break` 0.6 0.8 | FLASH(0.50, `#EAF8FF`) short, SHAKE(0.5, 0.3) (RM 0) |
| 1.00 | **Air crystallises**: shards hang in a disc r <= 10, y 0.3-4, drift 0.2 b/s, slow spin; 8 crystals (`_b` x3, `_c` x3, `_d` x2) at seeded points r 3-9 (not within 2.5 of the caster), 0.5 s ob, staggered 0.05 s, each with a ground puff | 80 P:IS + 30 G star6 twinkle + 8 M crystals + 8 x 6 P:IS | `block.amethyst_cluster.place` x8 (pitch 0.8 to 1.2 ascending, 0.6) + `block.large_amethyst_bud.break` 0.7 0.5 | VIGNETTE(frost_edge, 0.35, ramp 1.0); desat 0 to 0.25 over 1.0 s |
| 2.00 | **Absolute stop** (server ENCASED): HITSTOP for the rest of the pause (all motion of this effect frozen: shards hang, mist stops); shells snap on (0.12 s); a time-stop ring r 0 to 14 in 0.15 s, `#FFFFFF` 0.8; world loses colour | shells (5.3) + G ring_thin (GROUND) | `fx.rukia.absolute_zero` or `block.beacon.deactivate` 2.0 0.4 + `block.amethyst_block.resonate` 2.0 0.35 (single frozen hum); then **no sound until 3.0** | desat 0.25 to 0.45 in 0.1 s; GRADE(`#C8DCF0`, 0.5, 0.1 s); VIGNETTE(dark, 0.25) |
| 3.00 | **Cracks**: white crack overlay on every shell and crystal (G crack, PLANE facing camera, size = object height x 1.1), flicker 0 to 0.9 at 12 Hz for 0.3 s; crystal emissive flares to 1.0 | 1 G per shell/crystal + 40 P:IS flicker | `block.glass.break` 0.8 1.0 + `block.amethyst_cluster.break` 0.6 0.7 | none |
| 3.30 | **Shatter** (server damage): HITSTOP 60 ms, then all shells and crystals burst into shard meshes; shards thrown outward 8 b/s gravity 0.05; ground ring r 0 to 12 `#FFFFFF` 0.9; colour floods back | M shards 12 per shell + 8 per crystal (cap 256), 260 P:IS + 160 P:FM + 120 G star4 + G ring_soft | `fx.ice.shatter_big` or `block.glass.break` x5 (0, 0.05, 0.10, 0.17, 0.25; pitch 0.7 to 1.3) + `entity.generic.explode` 1.4 0.5 + `item.trident.thunder` 1.8 0.3 + `block.amethyst_block.break` x3 | FLASH(0.35, `#FFFFFF`), SHAKE(1.0, 0.5) (RM 0); desat 0.45 to 0 and GRADE off in 0.25 s; frost_edge fades 0.7 s |
| 3.60-7.00 | **Aftermath**: falling glitter; frost decal fades from the rim inward over 3 s (reverse reveal); server temp ice reverts 4.0-7.0 | 40 P:IS small + 20 G star6/s decaying to 0 by 5.0 | `block.powder_snow.break` 0.8 0.4 at 4.0; `block.fire.extinguish` 1.8 0.15 at 5.0 (melt hiss) | frost_edge fade-out |
Budget: P spawn ~860 (live cap 600 by `maxFxParticles`), G ~400, shells <= 32 (7.2k v), crystals 8 (~0.9k v), shards <= 256 (5.1k v), decal 1.5k v, post pass 1 per frame from 1.0 to 3.55. Others within 24 blocks: desat, grade and vignette x s(d).
**Server sends:** `effect_event` 23 (existing) + `entity_fx` ENCASED at 2.0 (existing) + HIT at 3.3 (S5). Decided review questions: (a) the pause stays 1.3 s (server shatters at 3.3; the bible's "4 s" is the lore limit); (b) shell alpha 0.6.

---

## 6. Byakuya bankai: Senbonzakura Kageyoshi (FINAL)

Concept: the quiet drop, the world turns night-blue, two converging rows of silver blades erupt in a wave that runs away from him [img byakuya_bankai_3/4], tips glint like moonlight; on command the blades burn away from the tip into a pink glittering storm; Hakuteiken is the opposite: everything condenses into white, wings and halo bloom in a dark world, one line of light.

### 6.1 Release and rising rows (id 4; settle lock 2.2 s) [BB 6 ch. 142, ep. 52/58]
ROWS anchor (static, S2). Layout per art bible 2.5: N = `bankaiBladeCount` x Q (default 200); ranks R = max(2, ceil(N/200)) <= 5; blades per rank N/(2R); spacing 1.1; rank k at 6 + 1.2 (k-1) off the axis, stagger 0.55 (k-1), scale 1 + 0.1 (k-1); rows start 3 blocks ahead and run behind; +Y (spine) faces the axis. Rest y = `FxGround.sample` - 0.5. d_i = distance of blade i from the release point along the row.
| t | Visual | Systems and counts | Sound | Screen (RM) |
|---|---|---|---|---|
| 0.00 | **Drop**: the hand is empty (ADR); `byakuya_bankai_hilt_ground` falls from hand height to the ground point (0.6 ahead along flat look) in 0.18 s (iq), point down | M hilt | none (silence) | none |
| 0.18 | **Sinks as into water**: hilt slides 0.5 down over 0.5 s (oc); ripple 1 (`_ripple` mesh) scale 0 to 6 in 1.0 s oc, alpha 1 to 0, `#CFC3F0`; ripple 2 at 0.40 (0 to 4.5), ripple 3 at 0.62 (0 to 3); each ripple has a G ring_thin twin `#F3E4FF` 0.35 | 3 M ripple + 3 G ring_thin (GROUND) | `fx.byakuya.drop` or `item.trident.hit_ground` 0.8 0.9 + `block.bell.use` 0.5 0.6 + `block.bubble_column.whirlpool_inside` 1.6 0.3 | none |
| 0.30-0.90 | **Night falls**: world grade from white to `#5A6A9C` over 0.6 s ios (deep anime blue: day sky becomes `#2A4699`-like); navy vignette | none | `ambient.cave` 0.8 0.6 + `block.respawn_anchor.charge` 0.6 0.6 + `entity.warden.heartbeat` 0.7 0.5 at 0.6 | GRADE(`#5A6A9C`, 1.0, 0.6) (RM same), VIGNETTE(dark, 0.45, ramp 0.6) (RM 0.15) |
| 0.90 | **Fault lines** (anticipation): two faint lilac lines along rank 1 left and right race from 3 ahead to the row ends at 60 b/s; crack decals appear under each blade slot as the line passes | 2 G line (GROUND, width 0.3, `#F3E4FF` 0 to 0.4) ; D crack_ground 1.6 x 1.6 per slot within 24 blocks of the camera | `entity.warden.emerge` 1.4 0.4 | SHAKE ramp 0 to 0.5 (RM 0) |
| 1.00 | **Eruption**: blade i starts at 1.0 + 0.012 d_i; rises from rest - 8.5 to rest + 0.3 then settles to rest (ob, 0.45 s); per blade start: ground burst (2 dust puffs + 3 block crumbs + 1 upward G streak `#F2E9FF` 0.35, length 3, life 6 t); at the overshoot top a **tip glint** (G star4 size 1.2 `#FFFFFF` 0.8 to 0 in 0.25 s at the tip empty (0, 0.826, 8.0)) | N blades (CPU path); P:DP 2 + BLOCK 3 per blade (cap 400 High / 200 Med / 100 Low); N G streak + N G star4 | `fx.byakuya.rows_rise` or `item.mace.smash_ground_heavy` 0.6 0.7 at 1.0 + `block.pointed_dripstone.land` per blade (0.6-0.9, <= 8/s) + `entity.ender_dragon.growl` 0.5 0.25 | SHAKE 0.6 continuous while rising (RM 0) |
| 2.20 (N 200; 2.8 at N 1000) | **Rows complete**: tips glow `#F2E9FF` (vertex light + tip billboards); **moonlight sweep**: a brightness band (+35 percent, 3 blocks wide) runs along each rank from near to far at 40 b/s, once | tip billboards N x 4 v | `block.beacon.ambient` 0.6 0.35 loop every 3 s + `block.bell.resonate` 0.6 0.4 | SHAKE(1.2, 0.4) then 0 (RM 0) |
| 2.80 | **Hold**: tips lean 3 deg inward (ios 0.6 s); then rows go to the static VBO (1.7.3); petal haze drifts in the corridor | 10 G petal_glow/s (`#F9C8F6` 0.25, life 60 t, size 0.5) | none | vignette 0.45 held (RM 0.15) |
| 0.30-3.00 | Bankai title card (HUD 7.6), caster only | HUD | `ui.release_stinger` | HUD |
**While BANKAI (rows standing):** tip emissive breathing 0.8 to 1.0 at 0.4 Hz; ground hilt at the release point with a small ripple (0 to 1.5, every 4 s); haze 6/s; aura 2.2; GRADE eases from `#5A6A9C` to `#7884B0` after 5 s (playable night), vignette 0.30 (owner); `entity.warden.heartbeat` 0.7 0.15 every 1.5 s. Others: GRADE x s(d) where d = distance to the nearest row point (standing in the corridor = full night).
Budget: N 200: ~60k CPU v during rise, then VBO (77k GPU v); N 1000: CPU path only while rising (near 24 blocks ~220 x 384 + LOD ~780 x 52 = ~125k), VBO 384k GPU v after settling. Dust <= 400. Config: `bankaiBladeCount`, `bladeLodDistance`, `bladeCullDistance`, `effectQuality`.
**Server sends:** `effect_event` 4 (existing) + ROWS anchor static with p0 yaw, p1 feet y (S1, S2). Decided review questions: (a) rows **re-form** after scatter and Hakuteiken (6.2, 6.3); (b) hilt 0.6 ahead along flat look, empty hand; (c) multiply GRADE (verified-cheap HUD-level technique) instead of a sky/fog mixin.

### 6.2 Petal storm / scatter (id 32, aim ray 40; server tornado 0.8 to 1.5, impact 2.5)
SWARM anchor (storm mode) + ROWS phase 32 (S3). Petals per blade P = min(15, floor(maxPetals x Q / N)) (N 200: 15; N 1000: 3); M = P x N <= 3000. Petal j: blade b = floor(j/P), k = j mod P, birth time t_s = 0.3 + 0.008 d_b + 0.4 k/P, birth point B = blade b at height 8 - 8 k/P. All positions closed-form (late-join safe):
- Helix H(t): h = (h0_j + 2.5 t) mod 8; r = 1.5 + 3.5 (h/8)^0.8 + 0.4 n_j; theta = theta0_j + 2 pi (1.2 - 0.4 h/8) t; centre = owner interpolated position. Calm core >= 1.5 (> Hurtless 0.85).
- Corridor flow B to H: quadratic Bezier, control = midpoint + up 3, blended by smoothstep(t_s, t_s + 0.9).
- Stream (2/3 of petals, ranked by angle from the facing front): launch t_l = 1.5 + 0.3 rank/M_s; cubic Bezier P0 = H(t_l), P1 = P0 + look x 4 + up 2, P2 = aim - dirToAim x 6 + up 3, P3 = sphere slot; speed max(40, L/0.6) b/s so every petal arrives by 2.4; lateral wobble 0.6 sin.
- Sphere: aim + R(t) u_j rotated 2 rev/s about one of 3 layer axes; R = 5 to 2.2 (2.0 to 2.44), 0.8 at 2.5.
- Burst: aim + u_j (0.8 + 9 oc((t - 2.5)/0.5)) - 0.5 (t - 2.5)^2 up.
- Return (3.0 to 4.5): quadratic Bezier to the birth slot, control up 6; the tornado third returns from H(3.0).
| t | Visual | Systems and counts | Sound | Screen (RM) |
|---|---|---|---|---|
| 0.00 | Command: tips overdrive to `#FFFFFF` core over 0.25 s; blades shiver +-0.04 at 18 Hz for 0.3 s | rows CPU path again | `block.amethyst_block.resonate` 0.9 0.8 + `entity.illusioner.prepare_mirror` 1.2 0.6 | none |
| 0.30 | **Burning dissolve**: blade b cut from the tip down (cut height 8 to 0 over 0.4 s from 0.3 + 0.008 d_b; vertices above the cut collapse onto it); a glowing edge at the cut; petals emerge at the cut, initial velocity 2 b/s outward + 1.5 up | per blade 1 G line (horizontal, width 0.6, `#FFE9FB` 0.6, moving down); M petals | `block.pink_petals.break` x6 (0.6-0.8, 0.7) + `entity.phantom.flap` 0.8 0.3 + `block.amethyst_block.break` 1.4 0.3 | FLASH(0.15, `#F3E4FF`) |
| 0.80 | **Storm** (server tornado): corridor flow into the helix; 30 percent loose debris at r 5-7; ground ring at the foot | M petals + streaks 1 in 10; G ring_soft (GROUND, r 5.5, `#E5A4DC` 0.25, rotating) ; 10 P:DP/s at the foot | `fx.petal.storm_loop` or `item.elytra.flying` 0.9 0.35 (re-trigger 1.0 s) + `entity.breeze.whirl` 0.8 0.6 at 0.8 and 1.2 | owner SHAKE 0.15 continuous (RM 0); vignette +0.10 |
| 1.50 | **Directed attack** (anime hand control [img byakuya_bankai_12]): the stream peels off the front of the tornado; band 3 wide; leading 200 petals bright streaks | 2000 stream + 1000 tornado; 200 G streak `#FFE9FB` 0.5 | `entity.breeze.wind_burst` 0.7 1.0 + `item.trident.riptide_3` 1.3 0.6 + `entity.wind_charge.wind_burst` 0.8 0.5 | SHAKE(0.6, 0.3), SPEEDLINES(0.25, 0.3) at the aim point (RM off) |
| 2.00 | **Envelop** (Gokei-like): sphere contracts around the aim; core glow grows | G glow_soft at aim, size 6, `#F9C8F6` 0 to 0.6 | `block.pink_petals.break` every 0.12 s (1.0-1.4, 0.5) + `entity.player.attack.sweep` x4 (2.0, 2.1, 2.2, 2.3) | none |
| 2.50 | **Impact** (server 12 HP): HITSTOP 60 ms on the collapsed sphere, then burst; white core over pink; ground ring r 0 to 9 in 0.35 s | G glow_soft size 10 `#FFFFFF` 1.0 to 0 in 0.3 s + G ring_soft (GROUND) + 120 G star4 + 300 P:PT sparks (speed 6, life 20 t) + 30 P:DP | `entity.generic.explode` 1.1 0.9 + `item.mace.smash_ground_heavy` 1.2 0.6 + `block.pink_petals.break` x3 (0.7) | FLASH(0.30, `#F3E4FF`) and SHAKE(0.9, 0.4) by distance to the impact (RM: flash 0.40 cap, shake 0) |
| 3.00-4.50 | **Return**: petals drift (gravity 0.5, noise) then stream back to their blade slots | M petals | `block.pink_petals.step` x4 (0.9, 0.5) | vignette back to 0.30 |
| 4.50-5.70 | **Re-form**: blade b regrows bottom-up (cut height 0 to 8 over 0.6 s from 4.5 + 0.004 d_b) as its petals shrink into it; glowing growth edge; then VBO again | per blade G line | `block.amethyst_block.chime` 0.8 0.4 + `block.beacon.power_select` 0.9 0.4 at 5.2 | none |
Life 6.0 s. If no rows are standing at cast (late join beyond range, rows culled): petals spawn on a ring r 6 around the owner (stub), same phases, return = fade out over 0.8 s.
Budget: 3000 petals (~14k near + ~11k far v) + streaks (~1.2k v) + G ~700 + P 360. Multiplayer: seed + aim from params; storm follows the owner.
**Server sends:** `effect_event` 32 (existing) + ROWS `phaseKind = 32, phaseTime = now` (S3) + HIT on tornado ticks and impact (S5).

### 6.3 Shukei: Hakuteiken (id 33, aim ray 20; server line t 1.5, burst t 1.8) [BB 6 ch. 166, ep. 59, img byakuya_bankai_10/11]
WINGS anchor (follows owner) + SWARM converging + ROWS phase 33. Wing root at the shoulders (0, 1.35, 0.15 behind the body axis), `wingScale`; halo centre = head top + 0.35, 0.45 behind, vertical plane facing back (Gate C halo inner radius 0.94). Line length L = params[4] (S4), fallback min(20, |aim - eye|). Marked DUCK from 1.0 to 1.5.
| t | Visual | Systems and counts | Sound | Screen (RM) |
|---|---|---|---|---|
| 0.00 | **Condense**: rows dissolve fast (P = min(10, floor(2000/N)), 0.3 s, tip-down as 6.2); petals spiral 2 turns into the hand point (hand = right shoulder + look x 0.6); within 1.5 blocks they turn white (lerp `#E5A4DC` to `#FFFFFF` by distance) | 2000 M petals + 100 G streak converging | `block.beacon.activate` 0.7 0.8 + `entity.illusioner.cast_spell` 0.6 0.7 + `block.conduit.activate` 0.8 0.6 | VIGNETTE(dark, 0.60, ramp 0.3) (RM 0.15); GRADE to `#3C4870` in 0.4 s |
| 0.45 | **Ignition**: white core at the hand, glow_core size 0.3 to 1.2 | G glow_core + G flare (size 3) | none | none |
| 0.50 | **Wings and halo bloom**: feathers fan out from folded (all at -12 deg, length x0.15) to the final fan in 0.45 s ob(1.1); feather ripple on; overglow pass; halo scale 0.6 to 1.0 and alpha 0 to 1 in 0.35 s with a halo bloom ring (PLANE, r 1.3, `#FFFFFF` 0.45); shed feathers from the wing edges over 1.5 s, drift down 0.6 b/s with sway | M wings (2 x 256 v + overglow 512 v), halo 96 v; G ring_soft (PLANE); 80 G feather/feather_b (size 0.6, life 40-60 t) | `entity.ender_dragon.flap` 0.9 0.9 + `block.bell.use` 1.2 0.7 + `block.end_portal_frame.fill` 0.6 0.6 | FLASH(0.40, `#FFFFFF`) |
| 1.00 | **Blade forms**: `hakuteiken_blade_body` Z-scaled 1 to 4 along the look, tip mesh at the end; glow strip pulse 0.8-1.2 at 6 Hz; sparks slide along; the world darkens further | M blade + Q line glow (width 0.35, `#F4F8FF` 0.4) + 40 G star4 | `block.amethyst_block.resonate` 1.2 0.7 + `block.beacon.power_select` 1.5 0.6 | GRADE to `#2E3858` in 0.3 s |
| 1.40 | **Held breath**: 0.1 s total stillness (feathers and ripple frozen) | HITSTOP 100 ms | silence | GRADE +0.05 darker |
| 1.50 | **Strike** (server line): blade extends to L in 0.08 s (oe); white line core width 0.12 `#FFFFFF` + glow width 0.6 `#F4F8FF` 0.5 (crossed quads); shock rings every 2 blocks perpendicular to the line (r 0 to 1.2, 0.2 s, staggered 0.01 s per ring); sparks fly off perpendicular | Q line x2; 10 G ring_thin (PLANE); 200 G star4/streak | `fx.hakuteiken.strike` or `entity.warden.sonic_boom` 1.2 1.0 + `item.mace.smash_air` 0.8 0.8 + `item.trident.thunder` 1.5 0.4 | IMPACT_FRAME (RM/photosensitive: FLASH 0.40), SHAKE(1.2, 0.5) (RM 0), FOV_KICK(4, 0.05, 0.4) (RM 0) |
| 1.80 | **Burst** at the line end (server): HITSTOP 50 ms; white sphere glow size 12 `#FFFFFF` 1.0 to 0 in 0.5 s; ground ring r 0 to 8; mixed white feathers and sparks radial | G glow_soft + G ring_soft (GROUND) + 200 G star4 + 100 G feather + 60 P:DP | `entity.generic.explode` 0.9 1.0 + `block.glass.break` 0.6 0.6 + `item.mace.smash_ground_heavy` 0.8 0.8 | FLASH(0.80 to 0 over 0.4 s) by distance to the burst; SHAKE(0.8, 0.4) (RM 0) |
| 2.20 | Line fades 0.3 s; blade shrinks back to 1 m | | none | GRADE back toward `#5A6A9C` over 1.3 s |
| 2.50-3.00 | **Dissolve**: wings fade from the feather tips inward (vertex alpha by radius), halo fades; feathers and white petals fall | 100 G feather + 60 P:PT (white tint `#F4F8FF`) | `entity.phantom.flap` 1.2 0.3 | vignette back to 0.30 |
| 3.00-4.20 | Rows re-form as in 6.2 (from 3.0, delay 0.004 d_b, 0.6 s each) | | `block.amethyst_block.chime` 0.8 0.4 | none |
Life 4.2 s. Budget: meshes ~1.4k v, G ~900, P ~120, petals 2000 temporarily. Wings 6 m: `wingScale` (0.5 recommended indoors; no auto-clip).
**Server sends:** `effect_event` 33 with `lineLength` (S4), ROWS `phaseKind = 33, phaseTime = now` (S3), HIT at 30 and 36 (S5).

### 6.4 Senkei (optional, `enabled=false`, id 34): not scheduled. If built after Gate C: 4 rows x 24 `byakuya_bankai_senkei_sword` (96 instances) in a ring r 4, tint `#F25FB8`, core `#FFD3EE` via vertex light, G glow_core per sword (`#F25FB8` 0.35), rotate 0.3 rev/s, release in 0.5 s at t 2.0 with 120 pink PT; sounds `block.respawn_anchor.charge`, `block.beacon.ambient`, `entity.player.attack.crit` x8.

---

## 7. HUD and UI

Principles: Minecraft-native pixel art (1 texel = 1 GUI pixel, nearest, no fractional font scales), dark ink frames with one accent colour per character and state, motion only on change, never over the crosshair area except the 3 pips. All sizes are GUI-scaled pixels (`ctx.getScaledWindowWidth/Height`). Drawn in `HudRenderCallback` (verified), hidden with F1 or when no zanpakuto is held and SEALED (as today). Textures via `DrawContext#drawTexture(Identifier, x, y, u, v, w, h, texW, texH)` and tint via `DrawContext#setShaderColor` (both verified in 1.21.1 sources); nine-slice panels via `drawGuiTexture(Identifier, x, y, w, h)` with sprites in `textures/gui/sprites/hud/` and `.mcmeta` `{"gui":{"scaling":{"type":"nine_slice","width":16,"height":16,"border":4}}}`.

### 7.1 Colours per character and state
| | Accent (frame, text) | Bar fill gradient (left to right) | Overlay on the bar |
|---|---|---|---|
| any, SEALED | `#C8D2DC` | `#5E6B80` to `#AEB9C9` | none |
| Rukia SHIKAI | `#9ED3F0` | `#3A86C8` to `#CFEFFF` | sheen |
| Rukia BANKAI | `#DDF3FF` | `#9ED3F0` to `#FFFFFF`, 1 px outer glow `#9ED3F0` | frost crystals scrolling 4 px/s |
| Byakuya SHIKAI | `#E5A4DC` | `#A8569E` to `#F9C8F6` | sheen |
| Byakuya BANKAI | `#F3E4FF` | `#7A6BC0` to `#F3E4FF` | petals scrolling 6 px/s, glint pixels |
| warnings | low `#FF6A5A`, poor `#E8C66A`, spent chip `#FFFFFF`, denied hatch `#FF4A4A` | | |

### 7.2 Spirit plate (top-left; anchor x0 = 6, y0 = 6)
```
 (x0,y0)
  /\    SHIKAI · Sode no Shirayuki            <- name line, y0+2
 / * \ ======================------<>          <- bar frame 128x11 at (x0+30, y0+13), gem at the end
 \   /  0:44                      78.8/100    <- value line, y0+26
  \/
  [mic] "mae sode no shirayuki"  ok Shikai    <- voice row, y0+38 (7.8)
```
- **Soft backdrop**: `plate_shadow.png` 192 x 48 at (x0 - 6, y0 - 6), alpha gradient `#05070D` 140 at the left to 0 at the right: readable on a bright sky without a box.
- **Emblem** 30 x 30 diamond at (x0, y0): frame strip `emblem_frame.png` (3 states), character icon 16 x 16 centred (x0 + 7, y0 + 7) from `emblem_icons.png` (Rukia six-arm snowflake `#FFFFFF` on `#1E4C86`; Byakuya five-petal sakura `#F9C8F6` on `#1C2540`). SEALED: icon at 60 percent brightness, steel frame. SHIKAI: full colour, accent frame. BANKAI: bankai frame + timer ring (`timer_ring.png`, 38 x 38 frames, centred, i.e. at (x0 - 4, y0 - 4)) draining clockwise over 45 s; last 10 s the ring turns `#FF6A5A` and pulses 2 Hz; seconds 5..1 tick `block.note_block.bell` 1.6 0.25.
- **Name line** at (x0 + 34, y0 + 2): state word in accent (`SEALED` / `SHIKAI` / `BANKAI`), ` · ` dim, zanpakuto or bankai name `#E8ECF4` (lang keys; trimmed to 124 px with an ellipsis).
- **Reiatsu bar**: frame `bar_frame.png` 128 x 11 at (x0 + 30, y0 + 13) (1 px ink outline alpha 230, 1 px inner bevel); background `#141826` alpha 200; fill area 124 x 7 at (+2, +2), clipped to a **parallelogram** (3 px slant at both ends, "/" cut, via `bar_mask.png`): fill = row of `bar_fill.png` for the state, cropped to round(124 x value/max).
  - Sheen: `bar_sheen.png` 14 x 7, additive, crosses the fill at 52 px/s every 2.4 s (SHIKAI, BANKAI).
  - Front cap: 1 px `#FFFFFF` alpha 200 at the fill end while regenerating.
  - Ticks: every 10 percent a 1 x 3 px ink tick (alpha 90) on the top edge; release cost tick at 15 percent: 1 x 7 `#E8C66A`; ability cost markers: three 1 x 2 px ticks under the bar at cost/max for the current slots (slot colours: accent, accent x 0.8, accent x 0.6).
  - **Bankai-ready gem** at the right end (`gem.png` 7 x 9): grey while not full; when SHIKAI and value == max it lights in accent and pulses (alpha 0.6 to 1.0, 1.2 Hz) with a one-time sparkle (4 frames, 0.3 s) and `block.amethyst_block.chime` 1.6 0.3.
  - **Chip bar**: on any decrease the lost span is drawn `#FFFFFF` alpha 200, holds 0.25 s, then shrinks to the new value over 0.45 s (oc): fighting-game feel for spending.
- **Value line** y0 + 26: left: BANKAI `0:44` (accent, red when <= 10 s), SHIKAI idle-seal warning `Seal in 15s` (`#8A93A6`, only when < 20 s of the 120 s idle remain, estimated from the last accepted cast), SEALED hint `[R] Release` (key from the binding); right-aligned at the bar end: `78.8` `#E8ECF4` + `/100` `#8A93A6`.

### 7.3 Ability strip (bottom, right of the hotbar)
- Slot 22 x 22 (`slot_frame.png` states), icon 16 x 16 at (+3, +3), gap 2, strip width 70. Position: x = w/2 + 91 + 6 (+ 20 when the vanilla attack indicator option is HOTBAR), y = h - 22 (aligned with the hotbar). If x + 70 > w - 4: vertical stack at x = w - 26, y = h - 24 - 24 i (i = 0..2, bottom-up).
- Key tab 9 x 9 (`key_tab.png`) overlapping the bottom-right corner (+15, +15), key letter centred (font 1.0, `#E8ECF4`; Cyrillic layouts show their letter, as today).
- States: READY: accent frame, full icon. COOLING: icon x0.55 brightness, radial sweep (`cooldown_sweep.png` 20 x 20, 24 frames, frame = ceil(24 x remaining/total)), integer seconds centred when remaining >= 1 s (`#FFFFFF`, shadow). POOR (value - cost < 1): icon desaturated (setShaderColor 0.6, 0.6, 0.6), 2 px `#FF5A4A` strip at the bottom inside. DISABLED (Senkei): ink slot + lock glyph. ACTIVE (barrier 5 s, attack 1.5 s): frame breathes (alpha 0.6 to 1.0 at 2 Hz) + 20 x 2 duration bar under the slot draining.
- **Ready ping** when a cooldown ends: white frame overlay alpha 1 to 0 in 0.25 s + `slot_glow.png` 28 x 28 scale 20 to 28 px, alpha 0.6 to 0 in 0.3 s; sound `ui.cooldown_ready` or `block.note_block.chime` 1.8 0.25 (`cooldownReadySound`).
- **Crosshair pips** (`crosshairPips`, default on, SHIKAI/BANKAI only): three 2 x 2 dots at (w/2 - 6, h/2 + 9), (w/2 - 1, ..), (w/2 + 4, ..): READY accent alpha 0.9, COOLING `#8A93A6` 0.4, POOR `#FF5A4A` 0.6.

### 7.4 Ability icons (`ability_icons.png` 64 x 48, 4 x 3 cells of 16 x 16; 1 px ink outline `#0B0F1A`; Rukia palette `#FFFFFF/#CFEFFF/#7FB8DF/#2E6FA8`, Byakuya `#FFE9FB/#F9C8F6/#E5A4DC/#8F5A9E`; generated by `tools/gen_hud_textures.py`, then hand-touched)
| Cell | Ability | Pictogram |
|---|---|---|
| 0 | Tsukishiro | flat ellipse ring (12 x 4) at the bottom with a vertical light column (6 wide, fading up) |
| 1 | Hakuren | three forward chevrons of ice spikes growing left to right, mist dots |
| 2 | Shirafune | diagonal ice blade thrust (bottom-left to top-right) with a crystal point and 3 shard pixels |
| 3 | Absolute zero | six-arm snowflake inside a thin circle, centre white |
| 4 | Attack mode | petal stream curving into an arrowhead (5 petals) |
| 5 | Barrier mode | dome arc of 7 petals over a ground line |
| 6 | Scatter | spiral tornado of petals, wider at the top |
| 7 | Hakuteiken | white vertical blade with two small wings and a halo arc (`#FFFFFF`, `#DDE6F6`, ink) |
| 8 | Senkei | three rows of tiny cross-guard swords, pink |
| 9-11 | release, bankai, seal (voice toast) | sword with a light burst / two blades crossed with a ring / sheathed sword |

### 7.5 Transition animations (driven by the attachment state change)
| Change | Animation |
|---|---|
| SEALED to SHIKAI | emblem flip (x scale cos, 0.3 s; icon swaps at mid); colours lerp 0.4 s; one triple-strength sheen pass; slots slide up from y + 8 with alpha 0 to 1, staggered 0.05 s, 0.25 s oc |
| SHIKAI to BANKAI | timer ring draws itself 0 to full in 0.5 s (oc); bar overlay (frost/petals) fades in 0.4 s; plate kick scale 1.0 to 1.06 to 1.0 in 0.25 s ob (RM: none); bar frame flashes white 0.15 s |
| to SEALED (id 10) | colours drain to steel 0.6 s; slots slide down and fade 0.2 s; grey lock ring on the emblem counts down the release lock (40 t) |
| bankai end (id 11) | timer ring shatters (`ring_shatter.png` 4 frames 40 x 40, 0.25 s) + bar flashes `#FF6A5A` twice; text `Bankai ended · recovering` (lang) for 2 s; lock ring 160 t |
| death / dimension change | instant reset, no animation |

### 7.6 Release title cards (caster only, on effect ids 1-4; `titleCards` config)
- **Shikai (1, 3)**: brush band `title_band.png` 256 x 32, tinted accent alpha 0.85, centred at y = round(0.28 h), width wipes 0 to min(w - 16, 300) in 0.18 s oc (from the left); release phrase (lang, e.g. `Mae, Sode no Shirayuki` / `Chire, Senbonzakura`) font scale 2 (scale 1 when w < 400), `#FFFFFF` with ink shadow, slides in from x + 12 with alpha 0 to 1 over 0.2 s starting 0.08 s; `SHIKAI` letter-spaced (+1 px) accent at scale 1 above it; hold to 1.4 s; exit: band slides right 24 px and fades 0.25 s. Total 1.7 s. RM: no slide, fades only.
- **Bankai (2, 4)**: letterbox bars top and bottom, height round(0.085 h), `#05070D` alpha 0.85, slide in 0.25 s oc; kanji plate `title_bankai.png` 64 x 32 (卍解, rendered from Noto Serif JP Black, OFL) centred at y = round(0.36 h), scale-in 1.6 to 1.0 with alpha 0 to 1, 0.2 s ob from 0.15 s (accent glow `title_glow.png` 96 x 48 behind it, additive, alpha 0.5); bankai name (`HAKKA NO TOGAME` / `SENBONZAKURA KAGEYOSHI`) scale 2, tracking +1 px, revealed letter by letter 0.02 s per char from 0.35 s; translation line (lang) scale 1 `#AEB9C9` at +22; hold to 2.4 s; exit 0.35 s (bars retract, text fades). Total 2.8 s. RM: no scale pop, no letter reveal.
- **Technique toast** (ability casts, caster only): name (`Some no Mai · Tsukishiro`, `Shukei · Hakuteiken`) at y = h/2 + 30, centred, accent, scale 1, brush underline `toast_line.png` 96 x 3 wiping left to right in 0.15 s; in 0.12 s (slide up 4 px + fade), hold, out 0.3 s; total 1.4 s.

### 7.7 Feedback for denied actions (`action_result`; replaces the action-bar text; the action bar stays as fallback when `hud=false`)
| Result | Feedback | Sound |
|---|---|---|
| DENIED_REIATSU | bar shakes +-2 px at 30 Hz for 0.3 s; the missing span (value to cost, or to max for bankai) is drawn as a red hatch (`hatch.png` 4 x 4 tiled, `#FF4A4A` alpha 160) for 0.8 s; slot flashes red; toast `Need 40 reiatsu` (or `Need full reiatsu`) under the plate | `ui.denied` or `block.note_block.bass` 0.6 0.6 |
| COOLDOWN | that slot shakes +-2 px for 0.3 s, its seconds flash `#FFD860` | `ui.button.click` 0.5 0.4 |
| DENIED_STATE | small grey toast near the crosshair (y = h/2 + 30) `Not in shikai` / `Bankai needs shikai`; slots flash grey 0.2 s | `block.note_block.bass` 0.5 0.4 |
| DENIED_ITEM | emblem flashes with a crossed-sword glyph 0.6 s; toast `Hold your zanpakuto` | `block.note_block.bass` 0.5 0.4 |
| RATE_LIMIT | none | none |
Toasts are rate-limited to one per 0.5 s (newer replaces).

### 7.8 Voice indicator (S6; shown only when the bridge has been contacted this session; dedicated servers: hidden)
- **Mic icon** 12 x 12 at (x0 + 9, y0 + 38) from `voice_mic.png` (6 cells): OFF grey slashed mic (`#8A93A6`, bridge silent > 6 s); IDLE white outline (`#C8D2DC`); LISTENING with a green dot `#4ADE80` pulsing 0.55 Hz; HEARING accent mic with 3 level bars animated 3 frames at 6 fps; ERROR `#F87171`.
- **Phrase toast** to its right (x0 + 24, y0 + 36), nine-slice `hud/toast` panel (ink alpha 180), max width min(220, w - 40): line 1 the recognised text in quotes (italic, `#E8ECF4`, trimmed 40 chars); while HEARING the interim text shows live in `#8A93A6` italic with `...`; line 2 the result: `✓ Shikai: Sode no Shirayuki` `#4ADE80`, `✗ Cooldown 3.2s` / `✗ Need full reiatsu` `#F87171`, `- no command` `#8A93A6`. Slides in from the left 0.15 s oc, lives 2.5 s, fades the last 0.4 s; newer replaces. A bankai accepted by voice also tints the toast panel accent for 0.3 s. `ui.toast.in` 1.4 0.15 on accepted only (`voiceToastSound`).

### 7.9 Layout at different GUI scales (all values in scaled pixels)
| Window / GUI scale | Scaled w x h | Plate | Ability strip | Title text scale | Notes |
|---|---|---|---|---|---|
| 1280x960 @4 / min | 320 x 240 | (6, 6); compact: name line hidden, bar 96 px | vertical stack at x 294 | 1 | voice toast max 220 |
| 1280x720 @3 | 427 x 240 | (6, 6) | x 310..380, y 218 | 2 | plate y + 19 per boss bar when the plate's right edge > w/2 - 91 (BossBarHud `bossBars` via accessor mixin, UNVERIFIED field name) |
| 1920x1080 @4 | 480 x 270 | (6, 6) | x 337..407 | 2 | |
| 1280x720 @2 | 640 x 360 | (6, 6) | x 417..487 | 2 | |
| 1920x1080 @2 | 960 x 540 | (6, 6) | x 577..647 | 2 | small on screen: user may raise GUI scale |
Compact mode (`hudCompact` or w < 360): name line hidden, bar 96 px, voice toast one line. Never overlaps the hotbar, health/armor/food rows (the strip sits beside the hotbar at its height), chat (bottom-left) or status effects (top-right).

### 7.10 HUD textures to generate (`textures/gui/hud/`, RGBA, nearest; `tools/gen_hud_textures.py`, procedural then hand-touched)
| File | Size | Content |
|---|---|---|
| `plate_shadow.png` | 192 x 48 | horizontal alpha gradient `#05070D` 140 to 0, rounded left end |
| `emblem_frame.png` | 90 x 30 (3 x 30x30) | diamond frame 2 px: steel `#AEB9C9` / white (tinted accent) / white with 4 corner notches for bankai |
| `emblem_icons.png` | 32 x 16 | Rukia snowflake, Byakuya sakura (16 x 16 each, on transparent; the diamond fill is drawn from the frame inner colour) |
| `timer_ring.png` | 228 x 228 (6 x 6 frames of 38 x 38) | 2 px ring r 17, frame f shows (36 - f)/36 of the ring clockwise from 12 o'clock, white |
| `ring_shatter.png` | 160 x 40 (4 frames) | ring breaking into 8 arcs flying out |
| `bar_frame.png` | 128 x 11 | ink outline with a 1 px bevel highlight `#2A3046` on top |
| `bar_mask.png` | 124 x 7 | parallelogram alpha mask, 3 px slant |
| `bar_fill.png` | 124 x 35 (5 rows x 7) | gradients of 7.1 (sealed, Rukia shikai, Rukia bankai, Byakuya shikai, Byakuya bankai), 1 px lighter top row |
| `bar_overlay.png` | 64 x 14 (2 rows) | tileable frost crystals / petals with glint pixels |
| `bar_sheen.png` | 14 x 7 | soft white band (additive) |
| `gem.png` | 28 x 9 (4 frames of 7 x 9) | diamond gem off / on / sparkle a / sparkle b |
| `hatch.png` | 4 x 4 | diagonal hatch |
| `slot_frame.png` | 88 x 22 (4 x 22) | normal (tinted accent), poor, disabled, active |
| `slot_glow.png` | 28 x 28 | soft square glow |
| `cooldown_sweep.png` | 120 x 80 (6 x 4 frames of 20 x 20) | black alpha 160 wedge covering remaining fraction, clockwise |
| `key_tab.png` | 9 x 9 | ink tab, 1 px accent top edge |
| `ability_icons.png` | 64 x 48 | 7.4 |
| `voice_mic.png` | 72 x 12 (6 x 12) | 7.8 states |
| `title_band.png` | 256 x 32 | brush stroke: alpha = smoothstep along y (soft top/bottom), ragged ends (noise 6 px), white |
| `title_bankai.png`, `title_glow.png` | 64 x 32, 96 x 48 | kanji plate; soft accent glow |
| `toast_line.png` | 96 x 3 | brush underline |
| `textures/gui/sprites/hud/toast.png` + `.mcmeta` | 16 x 16 nine-slice, border 4 | ink panel alpha 180, 1 px `#2A3046` border |

### 7.11 HUD config keys (`config/reiatsu_test_client.json`)
`hud` true, `hudCompact` false, `titleCards` true, `crosshairPips` true, `voiceHud` true, `voiceToastSound` true, `cooldownReadySound` true.

---

## 8. Budgets and quality tiers

### 8.1 Per-effect budget summary (High)
| Effect | P spawn / live | G peak | Meshes | CPU vertices | Anchor life | Limiting config |
|---|---|---|---|---|---|---|
| Release 2.1 | 80 / 80 | 30 | - | 0.2k | 0.6 s | `maxFxParticles` |
| Auras 2.2 | 16-30/s / 120 per player | 10 | - | - | state | `maxFxParticles` |
| Tsukishiro | 455 / 300 | 150 | 16 shells, 128 shards | ~8k | 3.0 s | `maxIceShells`, `maxShardMeshes` |
| Hakuren | 500 / 350 | 100 | 30 crystals, 120 shards | ~7k | 3.5 s | `maxCrystals` |
| Shirafune | 220 / 120 | 60 | strip, crystal, shell, 6 shards | ~0.5k | 2.2 s | none |
| Rukia bankai release | 330 / 300 | 260 | 6 crystals, ribbons | ~4k | 3.0 s | `maxCrystals` |
| Passive | ~110 live | 60 | 8 crystals, ribbons, sheen | ~4k per player | state | `maxCrystals` |
| Absolute zero | 860 / 600 | 400 | 32 shells, 8 crystals, 256 shards | ~16k | 7.0 s | `maxIceShells`, `maxShardMeshes` |
| Swarm 4.x | PT debris / 300 | 60 | 1000 petals | ~17k | state | `maxPetals`, `nearPetalCount` |
| Bankai rows | dust 400 | N tips | 200 (1000) blades | 60k while rising (125k), then VBO | state | `bankaiBladeCount` |
| Storm | 360 / 360 | 700 | 3000 petals | ~27k (+ rows CPU path while dissolving) | 6.0 s | `maxPetals` |
| Hakuteiken | 120 / 120 | 900 | wings, halo, blade, 2000 petals | ~20k | 4.2 s | `wingScale` |
| **Worst case** (1000 blades dissolving + 3000-petal storm + glow cap) | 700 | 4096 | 1000 + 3000 | blades animating near 24: 84k + LOD 41k + tip emissive within 16: 24k + tips 4k; petals 26k; glow 16k = **~195k** | - | all (1.7.4 auto-degrade) |

### 8.2 Quality tiers (`fxTier`; Low = effectQuality 0.25, Medium 0.5, High 1.0)
| Setting | Low | Medium | High |
|---|---|---|---|
| `maxFxParticles` | 400 | 800 | 1500 |
| `maxGlowSprites` | 1024 | 2048 | 4096 |
| Shikai swarm petals | 250 | 500 | 1000 |
| Storm petals (`maxPetals` x Q) | 750 | 1500 | 3000 |
| Bankai blades (default; user may raise on High) | 50 | 100 | 200 (max 1000) |
| `nearPetalCount` | 100 | 200 | 300 |
| Blade full mesh distance / emissive overlay distance | 16 / off | 24 / 12 | 32 (24 if N > 400) / 16 |
| Static row VBO | on | on | on |
| Petal streaks / blade shine sweep | off / off | 1 in 20 / on | 1 in 10 / on |
| Dust per blade (cap) | 1 (100) | 2 (200) | 4 (400) |
| Decal grid (rings x segments) | 6 x 16 | 9 x 24 | 12 x 32 |
| `maxShardMeshes` / `maxCrystals` / `maxIceShells` | 64 / 16 / 16 | 128 / 28 / 32 | 256 / 40 / 48 |
| Wing overglow pass, petal emissive overlay | off | on | on |
| Ice sheen overlay, ribbons | on (ribbons 6 segments) | on | on |
| `freeze_desat` post | on | on | on |
| Target | 60 FPS on integrated graphics at render distance 8 | 60 FPS mid PC at RD 12 | 60 FPS mid PC at RD 12 incl. the 8.1 worst case |

---

## 9. Multiplayer
- `effect_event` goes to the caster and `PlayerLookup.tracking(caster)`; clients reconstruct everything from `(effectId, seed, pos, dir, params)`; all randomness from the seed (same petals, crystals, shard throws on every client; per-frame jitter may differ). `bankaiBladeCount` and tiers are per client: two clients with different N see different row densities (accepted, cosmetic).
- Remote casters: particles and sounds with distance LOD; screen effects x s(d) (1.4, impacts by distance to the impact); no title card, no HUD; costume/sheen/ribbons only within 48 blocks; anchors tracked by vanilla entity tracking, so late joiners see SWARM, ROWS (with S3 phase) and WINGS but not one-shot particles.
- Entity-bound visuals (shells, breath puffs, hit sparks) come from `entity_fx`, which tracking players receive.
- Caps apply per client. Phase 7 verifies with two real clients; until then the harness replays events with a remote-caster flag (`-Dreiatsu.fx.remote=true`).

---

## 10. Implementation order for 6b (sonnet), most visual quality first
Every step ends with `gradlew build test`, the harness run, screenshots in `mod/run/screenshots/p6_<step>_<name>.png`, a `LOG.md` entry with numbers, and no regression of `runPhase4` (64/64) and `runPhase5`.
| Step | Work | Acceptance checks |
|---|---|---|
| 0 | Scaffolding: `FxConfig` + tiers, `tools/gen_fx_textures.py` and `gen_hud_textures.py` (1.3, 7.10; deterministic seed, committed PNGs), 6 particle types, `FxParticles`, `FxGlowBatch` + `reiatsu_glow` layer, `EffectTimeline` (seeded, HITSTOP, DUCK), sound helper, `ScreenFx` (FLASH, VIGNETTE, GRADE at `WorldRenderEvents.LAST`, SHAKE at HEAD of `tiltViewWhenHurt`), anchor entity with S1 fields, `Phase6Harness` with `-Dreiatsu.fx.freezeAt=<ms>` and `-Dreiatsu.fx.hold=<scenario>` | `p6_00_atlas_test.png`: a grid of every G sprite at sizes 0.5/2/8 blocks, noon and midnight, Fancy and Fabulous, smooth (no blocky 32 px steps), depth-correct behind a wall; 4096 sprites cost < 1.0 ms (`reiatsu_fx` profiler); GRADE darkens world but not hand/HUD; SHAKE visible in world and hand; RM zeroes shake |
| 1 | HUD (section 7) incl. denied feedback and voice indicator | screenshots at the 5 scales of 7.9 for SEALED/SHIKAI/BANKAI x both characters (`p6_01_hud_<scale>_<char>_<state>.png`), cooldown sweep at 25/50/75 percent, poor, ready ping (frames), bankai-ready gem, chip bar, both title cards at 3 times, voice toast (accepted, denied, interim); no overlap with hotbar/health/chat at any scale; HUD draw < 0.3 ms |
| 2 | 2.1 release, 2.2 auras, 2.3 seal | `p6_02_release_<char>_t{50,100,200,500}`; flash tints; ring 5/10; RM flash <= 0.07 s (frame log) |
| 3 | Byakuya swarm 4.1-4.3 (renderer, glint, streaks, card quads) | `p6_03_swarm_t{100,300,500}`, attack 0.5/1.0, dome 0.6; glints visible in motion capture (GIF via 10 frames); F1 FPS |
| 4 | Byakuya bankai rows 6.1 + GRADE + static row VBO | `p6_04_rows_t{0,600,1500,2200,2800}` from 6 behind and from a side tower 20 blocks up; N = 200 and 1000; night grade on a noon sky; tips glow at 100 blocks; F3/F4 FPS; VBO on/off frame time recorded |
| 5 | Storm 6.2 (closed-form phases, dissolve/re-form) | `p6_05_storm_t{300,800,1500,2000,2500,4800}`; tornado radius 5 at h 8; impact at the server aim; F2/F5 FPS |
| 6 | Rukia shikai 3.1-3.3 + shells + crystals + ShardBurst + decals | `p6_06_<ability>_t...` (3.x tables); frost decal readable at noon; shatter shows 3D shards; F7 FPS |
| 7 | Rukia bankai 5.1, 5.2 (sheen, ribbons, passive), 5.4 + `freeze_desat` (fallback grey quad) | `p6_07_rbankai_t{0,120,300,600,900,2000}`, passive at noon and midnight, absolute zero t{400,1000,2000,3000,3350,4500}; desat visible; F6 FPS |
| 8 | Hakuteiken 6.3 | `p6_08_hakuteiken_t{0,500,1000,1500,1530,1800,2600}` front and three-quarter; wings 6 m, halo 1.1 m with bloom; impact frame present (and absent with RM) |
| 9 | Polish: FOV_KICK, SPEEDLINES, IMPACT_FRAME, custom sounds (if produced), auto-degrade 1.7.4 | F5 with auto-degrade logged; photosensitivity limiter test (3 flashes/s) |
| 10 | Senkei (optional) | out of scope until requested |

---

## 11. Test plan
Harness `gradlew runPhase6` (Phase 4 pattern: flat creative world, `/reiatsu state ... full`, real key presses; camera 6 behind the player unless noted; noon and midnight; Fancy and Fabulous; screenshots at exact timeline times via the freeze hook, t from packet receipt). FPS: mean frame time over 400 frames after 90 warm-up frames, 1280x720, vsync off, on the reference mid PC; record mean, 1 percent low, `reiatsu_fx` CPU ms and CPU-written vertex count. **Pass lines:** <= 200k CPU vertices, <= 4 ms `reiatsu_fx`, mean >= 60 FPS and 1 percent low >= 45 FPS at High; Medium must pass at render distance 12; Low at RD 8.
| Effect | Screenshot points (ms) | Checks |
|---|---|---|
| 2.1 release (both) | 50, 100, 200, 500 | tints, ring radii 5/10, burst colours, RM flash |
| Auras | 3000 | wisp height 2.2, caps |
| Tsukishiro | 200, 500, 1000, 2000, 2300 | sigil r 4, pillar 14, shells on 5 zombies, shard meshes, alignment with server log lines |
| Hakuren | 300, 1000, 1250, 1500, 3000 | front moves 24 b/s (6 blocks between 1000 and 1250), crystal fan, shatter at 3000 |
| Shirafune | 300, 500, 600, 1000 | strip 1 to 8, tip crystal, shell |
| Swarm | 100, 300, 500; attack 500, 1000; dome 600; 5000 | cone, ring, dome r 3, card quads beyond 16, glints |
| Rukia bankai | 0, 120, 300, 600, 900, 2000; passive; abs. zero 400, 1000, 2000, 3000, 3350, 4500 | pillar 32 high + cap disc at 18, sheen, ribbons S-loops, frost decal at noon, desat 0.45 in the pause, shard burst |
| Byakuya bankai | 0, 180, 600, 1000, 1500, 2200, 2800; storm 300, 800, 1500, 2000, 2500, 4800; Hakuteiken 0, 500, 1000, 1500, 1800, 2600 | ripples, night grade, layout N 200 and 1000, overshoot, tip glints, 3 deg lean, dissolve edge, tornado r 5, impact at aim, re-form, wings 6 m, halo bloom, line length = params[4] |
| HUD | step 1 list | 7.9 layouts, no overlap, all states |
FPS scenarios (each at High, Medium, Low; Fancy and Fabulous): **F0** empty baseline; **F1** swarm 1000; **F2** storm 3000 alone; **F3** 200 blades held (VBO); **F4** 1000 blades held (VBO); **F4b** 1000 blades rising (CPU path); **F5 worst case** 1000 blades dissolving + 3000-petal storm (debug hold at t 0.5 of the storm); **F6** absolute zero, 32 encased zombies, 600 particles, desat pass; **F7** three Rukia abilities within 2 s; **F8** remote caster with the local swarm; **F9** 64 item frames + F5; **F10** glow batch saturated (4096 sprites) + GRADE; **F11** HUD at GUI scale 1 with all animations running. Record every scenario in `LOG.md` (vertices, `reiatsu_fx` ms, mean and 1 percent low FPS, auto-degrade notches taken).

## 12. Open points (owner)
1. UNVERIFIED render details (6b step 0 resolves, record in LOG): depth testing of the glow batch and GRADE at `WorldRenderEvents.LAST` under Fabulous (fallback: CUSTOM-sheet carrier particle); `VertexBuffer` draw state for cached rows (mirror `WorldRenderer#renderLayer`); `PostEffectProcessor` invocation for `freeze_desat` (ADR R2.3); BossBarHud accessor field name.
2. Server work in 6b: S1 to S5 (anchor fields, static ROWS, phase fields, Hakuteiken line length, HIT kind); S6 voice HUD state plus one line in `voice-bridge/index.html` (`/status?mic=`).
3. Rukia costume set (collar, pauldrons, crown, chest flower) is still not modelled: the ice sheen overlay stands in; when it exists it fades in at 5.1 t 0.9.
4. Custom sounds (1.5) need an author; vanilla layering ships until then.
5. Barrier hit detection is client-side (`hurtTime` edge); S5 HIT does not cover damage taken by the owner.
6. The title-card kanji plate uses Noto Serif JP (SIL OFL): include the OFL notice in the mod's credits.
