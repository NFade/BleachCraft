# ART BIBLE: Reiatsu Test mod (Rukia + Byakuya prototype)

**Revision: v2 (Gate A applied, 2026-10-08)**

Changelog (edit id, section, summary; edits are defined in `design/GATE_A.md` section 2):
- E1, 1.0: heading cleaned (stray "item space of 0.5" removed).
- E2, 0.5 and 1.2: `grip_hand` moved to z 0.19 (bankai sword 0.22).
- E3, 0.5: export rule added (one OBJ per object, shared textures, empties in a JSON file).
- E4, 1.1: saya lacquer `#2A2433`; manga vs anime note rewritten.
- E5, 1.2: snowflake tsuba redrawn (clean circle, widening spokes).
- E6, 1.2: ribbon 2.5 m, 10 segments of 0.25 m; budget 120 tris; texture strip 250 x 8 px.
- E7, 1.3: Byakuya tsuba is a tall 92 x 56 mm stepped window frame with a hub plate.
- E8, 1.3: saya evidence corrected; white variant dropped.
- E9, 1.4: petal evidence frames corrected; byakuya_shikai_1 excluded.
- E10, 1.5: Rukia costume pieces in scope, modelled last, player feature-layer overlay.
- E11, 1.5: bankai sword gets an oblong bar tsuba and a 0.28 m tsuka; palette and emissive updated.
- E12, 1.5: crown is a 6-spike fan with a fringe; chest flower gets a fringe.
- E13, 1.5 and 0.6: new object `rukia_bankai_ice_shell` (120 tris).
- E14, 1.6: giant blade sori 0.75 m, concentrated in the upper half.
- E15, 1.6 and 0.6: new object `byakuya_bankai_blade_lod` (24 tris).
- E16, 1.6 and 2.5: halo radius 1.10 m (inner 1.00 m), placed above the head.
- E17, 2.0 to 2.4: frame counts replaced by seconds.
- E18, 2.0: `freeze_desat` post effect added; batching wording changed.
- E19, 2.2: Shirafune uses a code-generated strip.
- E20, 2.3: release costume overlay fades in over 0.6 s; 6 crystals at her feet.
- E21, 2.3: absolute-zero pause sound and screen cells changed.
- E22, 2.5: rows rebuilt as ranks; 200 default and 1000 maximum blades fit.
- E23, 2.5: rise timing 0.5 s per blade, 0.012 s per block of delay; dust cap 400.
- E24, 2.5: petals per blade P = min(15, floor(maxPetals / N)).
- E25, 2.6: LOD rules rewritten (petals 16/32/64, blades 32/160).
- E26, 2.4: petal glow reference frames added.
- E27, 3 and header: decisions section from Gate A; status line updated.
- Suggestions: none applied (all add scope); listed in section 4.

Consistency fixes made while applying (not in GATE_A.md):
- C1, 1.2: shikai tsuba hub 26 mm could not hold the 32 x 10 mm blade hole; hub now 40 mm.
- C2, 1.5: bankai guard bridge 16 mm could not hold the blade hole; bridge now 44 mm, windows about 16 x 14 mm.
- C3, 1.5: "priority B" wording removed from the costume bullet (E10 scope).
- C4, 1.5: required-piece list now includes shards and the ice shell.
- C5, 1.5 and 0.5: `grip_hand` of the bankai sword listed as (0, 0, 0.22); tsuka length note added to 0.5.
- C6, 2.3: "ice shell mesh" renamed to `rukia_bankai_ice_shell`.
- C7, 2.0: `BankaiBladeRenderer` mentions `byakuya_bankai_blade_lod`.
- C8, 2.0: the frost_edge sentence ended with a full stop, not a semicolon; `freeze_desat` was inserted after it with the stop moved to the end.

Phase 1c deliverable. Contract for the Blender modelling agents (phase 3) and the VFX agents (phase 6).
Status: APPROVED WITH EDITS at Gate A (see design/GATE_A.md); edits applied. English. All text is original; reference images in `refs/` are private modelling references and must never be copied into the mod.

Source tags used below: **[RS]** research/rukia_sealed_shikai.md, **[RB]** research/rukia_bankai.md, **[BS]** research/byakuya_sealed_shikai.md, **[BB]** research/byakuya_bankai.md, followed by a section number; **[img name]** = `refs/<name>` (I looked at these frames myself). Where a number is my own choice because sources are silent it is marked **DECISION** (and **DECISION (low-confidence source)** when the research flags the underlying fact as uncertain).

---

## 0. Global style

### 0.1 Look
- Stylised, chunky, readable at Minecraft scale. Silhouette first: every item must be identifiable by silhouette alone from 3 m away in third person.
- Hand-painted / flat-shaded textures. Per-part palette of at most 6 to 8 colours; shading is 3 tones (shadow, base, light) plus one highlight pixel-streak. No photographic noise, no baked ambient occlusion, no normal maps, no PBR maps. Minecraft lighting is vertex-colour based, so metal "shine" must be painted in as a light streak.
- Textures are sampled with nearest-neighbour filtering. Author them as clean pixel art (no anti-aliased gradients) except in emissive maps and ice, where soft gradients are allowed.
- Geometry stays low and faceted. Bevels of 1 segment on hero edges (tsuba rim, kashira) only. Shade flat on blades, smooth only on tsuka and saya.
- Default look is the **anime** one (the mod is visual and the anime has colour and glow). Manga differences are listed per model and are exposed as a runtime tint, not as separate meshes.

### 0.2 Scale and in-hand factor
- 1 Blender unit = 1 m = 1 Minecraft block. A katana is about 1.0 m total (0.98 m in the shared base below).
- Model at 1:1 true scale; apply the in-hand reduction in the item display transforms (code), never in the .blend.
- **In-hand factor recommendation (starting values, final values come from the phase 2b spike): first person 0.70, third person 0.85, GUI and ground 0.55, item frame 0.60.** Reasons: (1) the first-person camera is a few centimetres from the model and FOV 70 exaggerates near objects; (2) the Minecraft arm is 0.25 m thick, so a 1 m blade already dominates the screen; (3) vanilla swords are shown at roughly 0.85 of block height, so a 1:1 katana would look oversized next to vanilla items; (4) reach feel: a 1.0 m blade at 1.0 scale visually reaches further than the 3-block interaction range suggests.
- Slight stylised thickening is allowed so thin parts survive downscaling: blade thickness at the spine is 10 mm (real katana about 7 mm), blade width at the root is 32 mm (real about 30 mm). Do not go thicker than this.

### 0.3 Texel density and atlases
- Hero parts (tsuka wrap, tsuba, kashira, ice crown): about 400 to 500 px per metre. Body parts (blade, saya, ribbons): 160 to 250 px per metre. Giant bankai blade: 32 px per metre (chunky on purpose, matches the 16 px/m of vanilla blocks).
- One atlas per .blend, one material per atlas, all objects of a model share it. 256x256 for the four small models (sealed and shikai), 512x512 for the two bankai models. Justification: a katana is mostly thin strips (a 0.72 m blade at 200 px/m is 144 px long and 6 px wide), so 256 is plenty and keeps pixels visually consistent with vanilla; the bankai files carry 8 to 15 extra objects (costume pieces, wings, halo, crystals), so they get 512.
- Leave a 2 px gutter between UV islands, and repeat the island edge colour into the gutter to avoid bleeding.
- Output per model: `<model>_diffuse.png` (RGBA; alpha only used for alpha-cut or translucent ice) and `<model>_emissive.png` (RGB, black = no glow). The emissive map is authored as white or light-grey intensity. Runtime multiplies it by a tint colour so the anime/manga variants, and the pink Senkei look, need no extra texture.

### 0.4 Emissive usage
- Emission is only for things that are meant to glow in the source: ice edges and ribbons, bankai blade tips, Hakuteiken wings and halo, Senkei blades. Everything else is zero (steel, wraps, lacquer do not glow).
- Blender preview: Principled BSDF `Emission Color` = the glow colour, `Emission Strength` per table; bake emission to the emissive PNG at 1.0 = fully bright. In game the renderer draws the emissive layer with full-bright lightmap (`LightmapTextureManager.MAX_LIGHT_COORDINATE`-style, to be verified in the spike).
- Principled BSDF in Blender is for turntable checks only. The baked diffuse is what ships. Never ship a look that depends on Metallic or Transmission.

### 0.5 Coordinate convention (PROPOSAL, final in design/ADR.md)
- Blender Z-up, right-handed. Item-space origin = centre of the pommel end (kashira) of the hilt ("start of the hilt"). Blade axis = +Z, so the tsuka occupies z = 0 to 0.25 (0.28 for `rukia_bankai_sword`) and the blade runs to the tip at the +Z end.
- Cutting edge faces **-Y**, the spine (mune) faces +Y. Flat blade faces look along +-X. Sori (curvature): the tip deflects toward **+Y** (toward the spine), as on a real katana.
- Each hand-held model carries an empty `grip_hand` at (0, 0, 0.19) (for rukia_bankai_sword: (0, 0, 0.22), because its tsuka is longer, see E11) (palm centre on the tsuka) so code can re-pivot the in-hand transform. Models with a tip carry an empty `tip` at the blade end.
- Default Blender OBJ export (Y-up, -Z forward) maps (x, y, z) in Blender to (x, z, -y) in OBJ: the blade ends up along +Y in OBJ and the cutting edge faces +Z in OBJ. The ADR decides how this maps onto Minecraft hand display transforms.
- Instanced or stretched meshes (giant bankai blade, Hakuteiken blade) use their own local origin, defined in their section.
- Export hygiene: apply all modifiers and scale, triangulate on export, normals outward, no loose geometry, object names exactly as listed in this document (lowercase snake_case), materials named `<model>_atlas`.
- Export one OBJ per object listed under **Objects**, file name = object name (`blender/export/<model>/<object>.obj`). All OBJs of a model use the same `<model>_diffuse.png` and `<model>_emissive.png`. Empties are not exported; write them to `blender/export/<model>/<model>_empties.json` as `{"<empty name>": [x, y, z]}` in Blender coordinates.

### 0.6 Triangle budget summary (hard limits per object, count after triangulation)
| Model | Object | Max tris |
|---|---|---|
| rukia_sealed | sheathed / drawn | 2500 / 3000 |
| rukia_shikai | blade+hilt / each ribbon segment | 3000 / 12 |
| byakuya_sealed | sheathed / drawn | 2800 / 3200 |
| byakuya_shikai | hilt / petal-blade / shard | 2000 / 20 / 12 |
| rukia_bankai | sword / ribbon segment / each crystal / ice shell / costume set | 3000 / 12 / 60 / 120 / 3500 |
| byakuya_bankai | giant blade (instanced) / blade LOD / ground hilt / Hakuteiken set / Senkei sword | 300 / 24 / 1500 / 900 / 150 |

---

## 1. Models

### 1.0 Shared katana base ("BASE", used by the sealed and shikai models, metres)

```
 +Y (spine)  <---- side view, looking along -X ---->     blade tip deflects toward +Y (sori 2 cm)
                                      _.-'  tip
                                _.-'
                          _.-'        blade (nagasa) 0.724 incl. habaki, z 0.256 to 0.980
                    _.-'
 [kashira]=[ tsuka 0.250 ]=[tsuba]=[habaki]===========>   +Z
 z=0                       z 0.250-0.256
 edge faces -Y (below the line in this view)
```

| Part | Spec |
|---|---|
| Kashira (pommel cap) | z 0 to 0.014, oval 30 x 22 mm |
| Tsuka (grip) | z 0 to 0.250, oval section 30 (X) x 24 (Y) mm, 3 mm swell in the middle third, 12-sided, 10 rings |
| Fuchi (collar) | z 0.236 to 0.250, 32 x 26 mm |
| Tsuba | z 0.250 to 0.256 (6 mm thick), shape per sword |
| Habaki (blade collar) | z 0.256 to 0.284, 32 x 10 mm |
| Blade (nagasa, from habaki start) | 0.724 m; width 32 mm at root tapering to 22 mm at the yokote line (z 0.91), kissaki (tip) 70 mm long; thickness 10 mm at root to 5 mm near the tip; shinogi-zukuri section (6 vertices per ring, ridge at 55 percent of the width from the edge) |
| Sori | 20 mm maximum deviation from the chord, tip toward +Y |
| Saya (scabbard) | z 0.256 to 1.000, oval 40 (X) x 26 (Y) mm, follows the same 20 mm sori plus 3 mm, koiguchi collar 14 mm, kojiri end cap 18 mm |
| Overall (drawn) | 0.980 m. Overall (sheathed) 1.000 m |

Why these numbers: no source gives a length ([RS 2a], [BS 2a], [BB 2]); Rukia is 144 cm and Byakuya 180 cm, both carry a "normal katana", so one standard katana is the honest common denominator. **DECISION (low-confidence source)**: identical base length for both characters; differences come from the tsuba, wrap and colour, which are the details sources and frames do give.

---

### 1.1 rukia_sealed

**Objects in the .blend** (collection `export`):
1. `rukia_sealed_sheathed`: kashira, tsuka with wrap, tsuba, saya with fittings. The blade inside the saya is not modelled.
2. `rukia_sealed_drawn`: same hilt and tsuba, habaki, full blade, no saya.
3. Empties `grip_hand`, `tip` (on the drawn blade).

The hilt, tsuba and habaki are duplicated mesh data (not linked) so each object exports independently.

**Silhouette and proportions** (BASE plus overrides)
- Tsuba: rectangular with concave (inward-curved) corners, 72 mm long (along Y) x 62 mm (along X), 6 mm thick, with two curved slits above and below the blade hole (sukashi, 14 x 4 mm each); flame-like embossing on the two long sides is texture only, not geometry. [RS 2a]; frame [img rukia_sealed_1] shows a tall, flat rectangular guard. Rim bevel 1 segment.
- Tsuka wrap: flat diamond lacing (hishimaki), 9 diamonds visible along the grip, each about 22 mm long, deep red lacing over a cream underlay. [img rukia_sealed_1, rukia_sealed_7]. Wiki says "reddish-brown", the frames say deep red with a diamond pattern; frames win (DECISION (low-confidence source), [RS 8 q2]).
- Kashira: dull gold-bronze cap. Not described in any source and not visible in frames; **DECISION (low-confidence source)**: simple oval cap in the guard colour, darkened.
- Saya: plain dark lacquer, no ribbon or tassel. [img rukia_sealed_7, rukia_sealed_8] show a dark scabbard; sources record only "dark" [RS 2a]. Add a small tan koiguchi and kojiri fitting to tie it to the tsuba colour (**DECISION (low-confidence source)**).
- No ribbon in the sealed state. [RS 2a]
- Note on frame rukia_sealed_6: the blade is drawn absurdly curved and large (anime perspective exaggeration). Do **not** use it for proportions, only for the steel tone.

**Palette**
| Part | HEX | Notes |
|---|---|---|
| Tsuka-ito (wrap) | `#822933` | sampled [RS 2a]; highlight `#A83A45`, shadow `#5C1B24` |
| Wrap underlay (diamond windows) | `#D8C9A0` | from [img rukia_sealed_1], DECISION |
| Tsuba | `#AF9668` | sampled [RS 2a]; shadow `#7D6B49`, highlight `#CDB88A` |
| Kashira / fuchi / saya fittings | `#8C7650` | DECISION |
| Saya lacquer | `#2A2433` | highlight streak `#4E4560`; dark violet-black as on Rukia's sword in [img byakuya_sealed_3] (Rukia at right, anime ep. 34) |
| Blade steel | `#B9C3D2` | base; lit tone `#D1D9E8` [RS 2a]; edge line `#EEF3FA`; temper line a 1 px wavy `#E4EAF4` |
| Habaki | `#AF9668` | same as tsuba |

**Materials (Blender preview only)**: steel Metallic 0.9, Roughness 0.30; lacquer Metallic 0, Roughness 0.25, coat 0.5; silk wrap Roughness 0.7; gold fittings Metallic 0.8, Roughness 0.4.
**Emissive zones**: none. Emissive PNG is fully black (still export it so the item pipeline is uniform).
**Triangle budget**: sheathed 2500, drawn 3000.
**Texture**: 256x256. Layout: top 96 px band = tsuka wrap strip (0.25 m x 0.085 m circumference, 440 px/m), tsuba and fittings on the right of that band; middle band = blade, three strips 144x8 px (left face, right face, edge/spine); bottom band = saya, two 160x14 px strips. Most texel space goes to the wrap and the tsuba.
**Manga vs anime**: manga colour ([img rukia_sealed_1], ch. 135) shows a tan-gold guard and red diamond wrap with olive-cream windows. Anime ep. 1 ([img rukia_sealed_7]) draws the guard and kashira pale grey-khaki and the saya dark grey; anime ep. 34 (Rukia in [img byakuya_sealed_3]) draws a tan-gold guard and a dark violet-black saya. We ship the tan-gold guard `#AF9668`, the red wrap `#822933` and the violet-black saya. No runtime variant.

---

### 1.2 rukia_shikai

**Objects**:
1. `rukia_shikai_blade`: hilt, snowflake tsuba, blade, all one mesh.
2. `rukia_shikai_ribbon_01` ... `rukia_shikai_ribbon_10`: ten separate objects, one per ribbon segment, for code animation. Origin of each = the hinge at its proximal end; the +Z axis of each points along the ribbon so a chain of rotations bends it like a flag.
3. Empties: `grip_hand` (0, 0, 0.19), `ribbon_root` (0, 0, -0.010), `tip`.

**Silhouette and proportions**
- Everything is white. Sources: blade, hilt and tsuba turn white [RS 2b].
- Blade: same BASE blade but straighter and a little longer and slimmer: 0.78 m (from habaki start), width 28 mm at root to 20 mm at the yokote line, thickness 9 mm to 4 mm, sori 8 mm. Overall length 1.036 m. **DECISION (low-confidence source)**: [RS 2b] says length is unknown and "assume unchanged", but [img rukia_shikai_1, rukia_shikai_3] show a clearly longer, straighter, slimmer blade than the sealed one; I follow the frames so the shikai reads as a distinct, more elegant silhouette.
- Tsuba: "hollow snowflake-like circle" [RS 2b]. Round ring, outer diameter 80 mm, 6 mm thick, rim width 8 mm (inner rim diameter 64 mm), central hub diameter 40 mm holding the blade hole, six spokes that widen from 7 mm at the hub to 12 mm at the rim, giving six rounded-triangle windows. The outer edge is a clean circle (no notches). Frames: [img rukia_shikai_3] (manga ch. 266), [img rukia_shikai_10] (anime ep. 149).
- Tsuka: same BASE grip, white lacing, but flatter wrap: 9 diamonds, ridge lines kept so the wrap pattern reads in white-on-white (shade the diamond gaps with `#C9D6EA`). Kashira: small white cap with a 4 mm hole where the ribbon attaches.
- Ribbon: one long white ribbon from the pommel, singular per the wiki [RS 2b] and frames [img rukia_shikai_1, rukia_shikai_2, rukia_shikai_3]. Total 2.5 m = 10 segments x 0.25 m; width 40 mm at segment 01 tapering to 28 mm at segment 10; each segment is a 2 x 1 quad strip with 2 mm thickness (12 tris max); adjacent segments overlap 10 mm at the hinge so no gap opens when bent. Swallow-tail cut on segment 10. Code note: in first-person view only segments 01 to 04 are drawn.
- Side sketch (not to scale):
```
 ribbon  ~~~~~(~~~~~[ kashira ]==tsuka==(  snowflake ring )======blade (white, nearly straight)====>
 z<0                z=0                  z=0.25                 z 0.256 to 1.036
```

**Palette**
| Part | HEX | Notes |
|---|---|---|
| Blade / tsuba base | `#F0E8FD` | sampled from manga ch. 266 [RS 2b]; anime frames read cooler white, so the shipped base is `#EAF2FB` (DECISION: anime) |
| Blade shade | `#C9D6EA` | |
| Blade edge line / highlight | `#FFFFFF` | |
| Tsuka wrap | `#F2F4FA`, gaps `#C9D6EA` | |
| Ribbon | `#F4F8FF`, edge `#B9D4F0` | edge tint from `#F0DCE9` sample is rejected (noisy, low confidence [RS 2b]) |

**Materials (preview)**: blade Roughness 0.25, a little Subsurface/translucent white; ribbon Roughness 0.6, double-sided.
**Emissive zones**: faint cold glow so the item stays visible in dark caves: blade edge strip and tsuba spokes, colour `#BFE4FF`, Emission Strength 0.4 (emissive map value about 35 percent); ribbon edges 25 percent. The release flash and the light ring in [img rukia_shikai_2] are VFX (section 2), not geometry.
**Triangle budget**: blade+hilt 3000 total; ribbon segments 12 tris each (120 total).
**Texture**: 256x256. Layout: top band tsuka wrap + kashira; right 80x80 px square for the snowflake tsuba (hero, 500 px/m); middle blade strips; bottom band ribbon (one continuous strip 250 x 8 px, 10 cells of 25 x 8 px, so the texture flows along the chain).
**Manga vs anime**: same design in both [RS 5]; anime adds a bluish ambient tint. We follow the anime cool white.

---

### 1.3 byakuya_sealed

**Objects**:
1. `byakuya_sealed_sheathed`: hilt, tsuba, saya.
2. `byakuya_sealed_drawn`: hilt, tsuba, habaki, blade (no saya).
3. Empties `grip_hand`, `tip`.

**Silhouette and proportions** (BASE plus overrides)
- Tsuba: bronze **open window frame**, "like a four-pane window" [BS 2a]; frames [img byakuya_sealed_1] (tall frame, stepped windows), [img byakuya_sealed_6], [img byakuya_sealed_9]. Outer 92 mm (along Y) x 56 mm (along X), 7 mm thick, corner radius 5 mm. Outer frame bar 8 mm; longitudinal centre bar (along Y) 10 mm wide; transverse bar (along X) 8 mm wide; solid central hub plate 40 mm (Y) x 18 mm (X) that holds the 32 x 10 mm blade hole. Result: four windows of about 30 x 15 mm, each with a 4 mm step where it meets the hub. Clearly larger and taller than Rukia's guard; this is the signature of the sealed state.
- Tsuka: lavender fabric lacing in a fine diamond pattern; between lacing the frames show cream-yellow diamonds ([img byakuya_sealed_1, byakuya_sealed_3]). 11 diamonds along the grip (smaller than Rukia's, 18 mm), lacing wider than the diamonds so the cord reads as dominant.
- Kashira: cream/ivory cap (cylinder, 30 x 22 mm, 14 mm high) visible at the top of the hilt in [img byakuya_sealed_1, byakuya_sealed_2]. Fuchi and the tsuba share the bronze colour [BS 2a].
- Saya: **conflict**. The wiki says white [BS 2a]; the frames that show his sheath ([img byakuya_sealed_5], backlit but violet; [img byakuya_sealed_4], night) show it dark. **DECISION (low-confidence source)**: dark violet-black saya, `#2E2840`, because it matches the frames and the anime palette and visually separates the item from the white shikai. No white variant is made (Gate A decision Q1).
- No tassels or ribbons on the sword. The white obi is clothing, not part of the item. [BS 2a]

**Palette**
| Part | HEX | Notes |
|---|---|---|
| Tsuba, fuchi, habaki (bronze) | `#A58D5F` | sampled [BS 2a]; shadow `#7A6743`, highlight `#CBB584`; [BB 2] estimate `#A67C3D` is browner, not used |
| Tsuka-ito (lavender) | `#BCB2D3` | sampled [BS 2a]; shadow `#8F84B0`, highlight `#DAD3EA` |
| Wrap windows | `#E8DDB5` | |
| Kashira | `#E9E2C0` | |
| Saya | `#2E2840` | highlight streak `#554A73` |
| Blade steel | `#C9CED6` | [BS 2a] estimate; edge `#F0F4FA` |

**Materials (preview)**: bronze Metallic 0.8, Roughness 0.45; lavender silk Roughness 0.7; lacquer Roughness 0.3; steel Metallic 0.9, Roughness 0.3.
**Emissive zones**: none (the sealed sword does not glow [BS 2a]).
**Triangle budget**: sheathed 2800, drawn 3200 (extra budget goes to the openwork tsuba).
**Texture**: 256x256. Layout: top-left 120x40 tsuka strip; right 96x96 block for the tsuba front and back (hero, 500 px/m); bottom-left saya strips; middle blade strips.
**Manga vs anime**: the manga colour panel [img byakuya_sealed_6] shows the same bronze open guard; the lacing colour is a (cooler) lavender in anime. Follow the anime.

---

### 1.4 byakuya_shikai

**Objects**:
1. `byakuya_shikai_hilt`: kashira, tsuka, fuchi, tsuba, habaki and a 30 mm bare tang stub. The blade is gone: "only the handle and tsuba remain" [BS 4c]. Identical hilt dimensions and palette as `byakuya_sealed` (1.3); build it by copying that hilt so the swap is seamless.
2. `byakuya_shikai_petal`: one petal-blade, instanced by code in the thousands.
3. `byakuya_shikai_shard`: one broken fragment, used for impact debris.
4. Empties `grip_hand`, `tang_tip` (z 0.31).

The tang stub is **DECISION (low-confidence source)**: sources only say the blade separates; a 30 mm steel stub (`#8E96A3`, 10 x 5 mm section) stops the hilt from looking like a cut-off tube.

**Petal-blade proportions**
- Slim leaf-shaped blade, length 0.120 m, maximum width 0.026 m at 40 percent of the length, thickness 0.004 m, tip pointed, tail ending in a 0.006 m tang, sori 0.005 m. Cross-section a flattened diamond. [BS 2b] gives no size ("too small to see", est. under 1 cm real); a 12 cm petal is a deliberate enlargement so the swarm reads at Minecraft scale, flagged **DECISION (low-confidence source)**. Evidence for the leaf-blade look: [img byakuya_bankai_2, byakuya_bankai_5] (manga, slender leaf blades), colour from [img byakuya_shikai_3]. Note: [img byakuya_shikai_1] shows Senkei-type cross-guard swords, not petals; do not use it for the petal.
- Origin at the tail end; +Z along the blade, edge -Y, same as BASE.
- Tris: two rings (3 verts each side) + tip + tail = about 14 tris. Hard limit 20.
- Shard: irregular triangular splinter 0.05 x 0.02 x 0.003 m, 3-sided prism, 6 tris (limit 12), origin at centroid.

**Palette (anime default)**
| Part | HEX | Notes |
|---|---|---|
| Petal body | `#E5A4DC` | sampled anime petal dome [BS 2b] |
| Petal highlight | `#F9C8F6` | [BS 2b] |
| Petal edge line | `#FFE9FB` | 1 px along the edge |
| Alt bloom tone | `#FEA2D3` | from the petal bloom frame [BS 2b], used for the particle variant |
| Manga variant (runtime tint) | `#F5F5F5` | white petals [BS 5]; produced by multiplying the diffuse with a white tint |
| Shard | `#C9CED6` / `#9AA3B4` | steel, matches the sealed blade |

**Materials**: petal Roughness 0.4, slight pearl sheen; never metal-looking, since petals "reflect light" like cherry blossoms [BS 2b].
**Emissive zones**: none on the hilt. Petal: a faint 25 percent emissive of `#F9C8F6` over the whole blade plus 60 percent at the edge line, **DECISION (low-confidence source)**: [BS 2b, 8 q6] says the blades themselves do not glow, but the anime frames ([img byakuya_shikai_3, byakuya_shikai_7]) show strong pink glow and a swarm in a dark cave must stay readable. Tunable by the emissive multiplier setting.
**Triangle budget**: hilt 2000, petal 20, shard 12.
**Texture**: 256x256. Layout: hilt strips same as 1.3 (top 120 px band), petal as a 40x24 px cell (leaf outline with a centre ridge line), shard as a 16x16 px cell, rest empty. Keep the petal UV mirrored so one half-texture is painted.
**Manga vs anime**: manga petals white, anime pink [BS 2b, 5]. Follow the anime pink; manga is the tint variant.

---

### 1.5 rukia_bankai

Research baseline: the sword stays similar to the shikai but is made of transparent ice; she wears a white ice kimono with a high ice collar, shoulder plates and a hair half-crown, a small ice flower on the chest, white hair; large ribbons tied at the back form many loops [RB 2, img rukia_bankai_2, rukia_bankai_3]. Player skin replacement is out of scope, so the **required** pieces are the sword, ribbon segment, ice crystals, shards and ice shell; the costume pieces are in scope: they are modelled last in this .blend, after the required pieces pass Gate B, and are rendered as a player feature-layer overlay (head, body, arm parts; mechanism per ADR).

**Objects**:
1. `rukia_bankai_sword` (required): ice katana, hilt, oblong bar tsuba, blade.
2. `rukia_bankai_ribbon_seg` and `rukia_bankai_ribbon_tip` (required): one segment and one end piece. Code chains them (up to 3 ribbons x 10 segments).
3. `rukia_bankai_crystal_a`, `_b`, `_c`, `_d` (required): ice crystals of increasing height (0.15, 0.30, 0.60, 1.20 m). Separate objects, instanced for the ground freeze and for the effect on mobs.
4. `rukia_bankai_shard_a`, `_b` (required): flat ice splinters for the shatter effect.
4b. `rukia_bankai_ice_shell` (required): faceted ice capsule for encasing mobs, 1.0 x 1.0 x 2.0 m (X, Y, Z), origin at the bottom centre, 10-sided, 3 rings with irregular facets, max 120 tris; code scales it to the mob's bounding box. Palette `#8EC9EE` over `#D9E8F5`, diffuse alpha 180, emissive 30 percent.
5. Costume set (in scope, modelled last; player-scale, for a 1.8 m Minecraft player): `rukia_bankai_collar`, `rukia_bankai_pauldron_l`, `rukia_bankai_pauldron_r`, `rukia_bankai_crown`, `rukia_bankai_chest_flower`.
6. Empties `grip_hand` (0, 0, 0.22), `tip`, `ribbon_root` (back, at obi height).

**Silhouette and proportions**
- Sword: tsuka 0.28 m (z 0 to 0.28; longer than BASE, the frames show a long two-hand grip), tsuba z 0.28 to 0.286, habaki z 0.286 to 0.314, blade 0.78 m from the habaki start, sori 8 mm, overall 1.066 m. Tsuba: a narrow openwork oblong guard, stadium outline 84 mm (along Y) x 22 mm (along X), 6 mm thick, rim 4 mm, solid centre bridge 44 mm (along Y) that holds the blade hole, so two stadium windows of about 16 x 14 mm. Blade made of "ice": 12 mm thick spine, a clear central core strip, brighter edge bevel. Hilt wrap: pale ice-white lacing with blue-grey diamonds. Frames: [img rukia_bankai_2, rukia_bankai_3]. Kashira: plain pale cap, BASE size.
- Ribbon segment: 0.35 m x 0.07 m, 12 tris, tip piece 0.35 m with a pointed end. [RB 2] says "numerous loops" (not counted) and frames show 3 to 4 big loops; so 3 ribbon chains.
- Crystals: hexagonal prisms with pointed tips, base radius = height / 6, one slightly bent variant, 36 to 60 tris each. Their bases sit at z = 0 for ground placement.
- Costume (in scope, modelled last): collar = tall layered stand-up collar, 0.22 m high, 3 stacked flared tiers [img rukia_bankai_2, rukia_bankai_3]; pauldrons = layered plate stack 0.22 x 0.16 x 0.09 m each, 4 plates stepping outward, with an angular upswept edge; crown = a fan of 6 ice spikes (longest 0.12 m) on the right side of the head, sweeping up and back, with a fringe of 5 thin vertical ice strips (0.06 m) hanging below it [img rukia_bankai_2, rukia_bankai_3]; chest flower = six-petal snowflake 0.09 m across, relief 15 mm, with a fringe of 4 thin ice strips (0.05 m) hanging below it.

**Palette**
| Part | HEX | Notes |
|---|---|---|
| Ice, highlight / edge | `#CFEFFF` | [RB 2] |
| Ice core | `#7FB8DF` | [RB 2] |
| Deep ice shade | `#2E6FA8` | [RB 2] |
| White kimono-type surfaces (collar, pauldrons) | `#EEF4FA`, shadow `#B8CFE6` | |
| Silver edges | `#C8D2DC` | pauldron rims |
| Hilt wrap | `#DCE8F5` lacing, diamonds `#7FA5C8` | DECISION |
| Tsuba | `#DCE8F5`, shade `#9DB4CC` | pale ice-silver |
| Ribbons | `#F0F6FA`, edge `#8EC5EE` | [RB 2] |
| Crystal body | `#8EC9EE` over `#D9E8F5` | [RB 2] |

**Materials (preview)**: ice Transmission 0.8, IOR 1.31, Roughness 0.15; keep a baked version in the diffuse (alpha 200 on ice parts, 255 on hilt and tsuba). Do not depend on transmission; the item will use opaque or translucent render layer per ADR.
**Emissive zones**: blade edge strip and core (`#CFEFFF`, strength 1.0, 60 percent map value); tsuba rim (50 percent); chest flower and crown spikes (100 percent); ribbon edges (40 percent); crystals (core 35 percent, tips 80 percent). [RB 2] says glow is an assumption (low), but the anime frame [img rukia_bankai_3] is strongly glowing, so this is anime-driven.
**Triangle budget**: sword 3000; ribbon segment/tip 12 each; crystals 60/48/48/36; shards 8 each; ice shell 120; costume set total 3500 (collar 700, each pauldron 900, crown 500, flower 300; sum 3300).
**Texture**: 512x512. Layout: left 256 px column = costume set (largest, flat panels plus ice highlights); top-right 256x64 = sword hilt wrap strip and tsuba; middle-right = blade strips; bottom-right 256x128 = ribbon (4 cells), crystals (4 cells of 64x64), shards (2 cells); one 64x64 cell for the ice shell in the middle-right area below the blade strips. Most texel space goes to the costume panels and crystals, because they are the visible hero parts.
**Manga vs anime**: manga ch. 570 colour page [img rukia_bankai_2] is white on lavender-grey with a nearly opaque white kimono; anime ep. 385 [img rukia_bankai_3] shows the same costume as glowing translucent ice with sparkles on a deep blue backdrop [RB 5]. Follow the anime (glow, blue ice). The game renders (Jump Force, Fortnite, Brave Souls: [img rukia_bankai_9, _10, _11]) are non-canon and are not used.

---

### 1.6 byakuya_bankai

**Objects**:
1. `byakuya_bankai_blade` (required): one giant blade for instancing.
1b. `byakuya_bankai_blade_lod` (required): same outline, origin and UV region as `byakuya_bankai_blade`, 3 rings x 4 vertices plus the tip (flat slab, same 0.75 m sori), max 24 tris; used beyond 32 blocks from the camera.
2. `byakuya_bankai_hilt_ground` (required): the dropped sword's hilt standing in the ground.
3. `byakuya_bankai_ripple` (required): flat ring mesh for the ground ripple under the hilt.
4. `hakuteiken_blade_body`, `hakuteiken_blade_tip`, `hakuteiken_wing_l`, `hakuteiken_wing_r`, `hakuteiken_halo` (required for Shukei: Hakuteiken): the stretch set.
5. `byakuya_bankai_senkei_sword` (optional, only if Senkei is built): small cross-guard sword for the Senkei rows.
6. Petal and shard: reuse `byakuya_shikai_petal` / `byakuya_shikai_shard` from 1.4; they are not duplicated into this .blend.

**Giant blade (instanced)**
- Origin at the centre of the blade base. +Z up. The lower 0.5 m is the "buried" section (no detail, textured with a dark soil-edge band) so the blade can be sunk 0.5 m into the ground and the intersection is hidden.
- Height 8.0 m (visible 7.5 m), width 0.55 m, thickness 0.14 m at the spine tapering to 0.06 m at the tip, sori 0.75 m toward +Y (spine side), concentrated in the upper half: deviation from the straight base axis 0 at z 0.5, 0.10 m at z 4.0, 0.35 m at z 6.0, 0.75 m at the tip, kissaki the last 0.9 m, shinogi ridge 55 percent. Aspect about 1:14, matching [img byakuya_bankai_3]. Sources: no size given [BB 2, 8 q11]; the 8 m figure is my estimate from [img byakuya_bankai_3, byakuya_bankai_4], where blades are several times Byakuya's height: **DECISION (low-confidence source)** (research suggests 5 to 10 m).
- Edge faces -Y, spine +Y. In the rows the edge points outward and the spine inward, so tips lean toward Byakuya, as in [img byakuya_bankai_3, byakuya_bankai_4]. Code yaws the left-row instances 180 degrees so +Y always points to the central axis.
- Flat-shaded, 12 rings x 6 vertices; tris about 170, hard limit 300. No separate habaki or tang; this is a blade slab, not a sword.

**Ground hilt**
- The released sealed sword drops tip-down and sinks "as if into water" [BB 3]. Object = BASE hilt from 1.3 (kashira, tsuka, tsuba) pointing **up** (+Z) with a 0.25 m blade stub below it, so code can slide it down into the ground; origin at the bottom of the stub. Total height 0.52 m. Tris limit 1500.
- Ripple: flat ring, outer radius 1.0 m, width 0.12 m, 32 tris, scaled by code from 0 to 6 m; lies at z = 0.02.

**Hakuteiken set** (from [img byakuya_bankai_10, byakuya_bankai_11])
- Blade body: unit length, a straight prism along +Z, z 0 to 1.0, width 0.30 m, thickness 0.06 m, 6 tris per ring and only 2 rings, so non-uniform scaling on Z is safe (all detail is in the UVs; stretched UV along Z with a tileable glow stripe). Origin at the hilt-side end. The tip is separate and never scaled: `hakuteiken_blade_tip`, 0.45 m kissaki, 40 tris.
- Wings: two mirrored feather sheets, span 6.0 m each, 9 spiky feather lobes decreasing in size, slightly curved forward (0.8 m), single-sided with alpha cut-out, 150 tris each. Origin at the shoulder root; +Z up, X outward.
- Halo: vertical ring, outer radius 1.10 m, inner 1.00 m, 48 tris, double-sided, origin at its centre, plane = XZ; code places the centre 0.35 m above the top of the head and 0.3 m behind the back.
- All five pieces are fully emissive white. Tris total about 900.

**Senkei sword** (optional): 1.0 m small sword with a cross-shaped tsuba (80 mm), blade 0.70 m, 150 tris, origin at the hilt end, strongly emissive. [BB 4.3, img byakuya_bankai_7] shows hundreds of such glowing cross-guard blades in curved rows. Scope: Senkei is not in the minimum ability list; it is kept as an optional stretch so Hakuteiken's "condensed blades" story can be told. Do not model it before the required objects pass Gate B.

**Palette (anime default)**
| Part | HEX | Notes |
|---|---|---|
| Giant blade, highlight | `#E4E8F0` | [BB 2] used `#DDE2EA`; slightly brighter for readability |
| Giant blade, base | `#A9B2C2` | |
| Giant blade, shade | `#7C869A` | [BB 2] used `#9AA3B4`; darker to reinforce form on blue sky |
| Blade edge line | `#F4F6FA` | |
| Blade tip emissive | `#F2E9FF` (white-lilac) | tint at runtime; pink Senkei tint `#F25FB8` |
| Soil band (buried part) | `#4A3A2E` | |
| Petal | see 1.4 | |
| Hakuteiken white | `#F4F8FF`, glow core `#FFFFFF` | [BB 4.5] |
| Halo | `#FFFFFF` | |
| Ground hilt | see 1.3 | |
| Senkei sword | `#F25FB8` body glow, core `#FFD3EE` | [BB 4.3], low-confidence estimate |

**Materials (preview)**: giant blade Metallic 0.7, Roughness 0.4 (painted streaks matter more); wings and halo pure Emission, Strength 4.0.
**Emissive zones**: the top 1.2 m of the giant blade: a vertical gradient from 0 percent at z = 6.3 m to 100 percent at the tip, plus a 2 cm edge line at 40 percent along the whole edge. Research: the released blades are not described as glowing [BB 2], so the glow is **DECISION (low-confidence source)** and exists to support the petalisation moment and give the storm a lit leading edge. Hakuteiken wings, halo, blade: 100 percent. Senkei sword: 100 percent.
**Triangle budget**: giant blade 300 (target 170), blade LOD 24, ground hilt 1500, ripple 32, Hakuteiken set 900, Senkei sword 150.
**Texture**: 512x512. Layout: left 160x256 = giant blade (mapped so a 1:14 slab takes the full height, 32 px per metre; seams on the spine); top-right 192x128 = hilt strips and tsuba; middle-right 192x192 = wing alpha sheet (mirrored, one feather group painted); bottom-right 128x128 = halo ring + Hakuteiken blade glow strip; Senkei sword 64x64 in the bottom-left. Most texel space goes to the giant blade (it is the signature silhouette).
**Manga vs anime**: manga shows the giant blades as plain white-grey slabs and the petals as white-lavender on navy ([img byakuya_bankai_2, byakuya_bankai_5]); the anime shows silver blades in two converging rows on a blue ground and pink glowing Senkei ([img byakuya_bankai_3, byakuya_bankai_4, byakuya_bankai_7]) [BB 5]. Follow the anime (silver blades, pink glow); the manga look is a tint variant (petals `#E9E7F4`).

---

## 2. Effects storyboard

### 2.0 Common rules and shared assets
- **Particle types (custom, textured, billboard unless noted):** `frost_mote` (soft 4 px star, blue-white), `ice_shard` (angular quad, 3 variants), `snow_ring` (flat quad lying on the ground, ring texture, scalable), `light_pillar` (vertical stretched quad, additive), `petal` (leaf quad, 2 tints), `reiatsu_wisp` (soft streak rising upward).
- **Batched renderers (one buffer per effect type per frame; the ADR chooses a CPU-built buffer or a cached VBO):** `PetalSwarmRenderer` (instanced `byakuya_shikai_petal`, up to 3000, ring buffer), `BankaiBladeRenderer` (instanced `byakuya_bankai_blade`, with `byakuya_bankai_blade_lod` for distant blades, up to 1000 in config, default 200), `CrystalRenderer` (instanced `rukia_bankai_crystal_*`), `WingHaloRenderer` (Hakuteiken set). A configuration multiplier `effectQuality` (0.25, 0.5, 1.0) scales all counts below; the counts are for 1.0.
- **Post effect (Minecraft `PostEffectProcessor`-style chain):** `bankai_flash` = full-screen additive white flash + a short radial blur; `vignette_dark` = dark navy vignette for Byakuya's bankai; `frost_edge` = blue frost vignette for Rukia's bankai; `freeze_desat` = full-screen desaturation 0 to 30 percent (used by the absolute-zero pause). Combined cost target under 1 ms.
- **Reduce motion setting:** when on, camera shake is disabled, flashes drop to 40 percent peak alpha and 0.07 s, vignette pulses become a constant 15 percent overlay, and radial blur is off. Particle counts are not changed by this setting.
- **Camera shake:** a decaying sine on camera yaw and pitch, max 0.6 degrees for small effects, 1.2 degrees for bankai, frequency 14 Hz.
- **Sound ids** are vanilla Minecraft 1.21.1 candidates, to be verified later; pitch and volume are tuned in code.
- Time is wall-clock seconds from the activation tick. Sizes are in blocks (1 block = 1 m). All effects are cosmetic on the client; gameplay logic (freeze, damage, timers) lives on the server per STATE_MACHINE.md.

### 2.1 Common release flash and reiatsu aura

**Release flash (both characters, shikai and bankai variants)** - [RS 3, BS 3, RB 3, BB 3]
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.00 | Flash | Full-screen additive overlay (post `bankai_flash` at 60 percent for shikai, 100 percent for bankai), tint Rukia `#DDF3FF`, Byakuya `#F3E4FF` | none | `block.beacon.activate` (shikai), `block.end_portal.spawn` (bankai) | ramp up 0.05 s, hold 0.03 s, fade 0.14 s (0.22 s total) |
| 0.05 | Ground ring | `snow_ring` flat quad, expands 0 to 5 blocks (shikai) or 0 to 10 (bankai) in 0.4 s, alpha 1 to 0 | 1 quad | `entity.generic.explode` pitched up | none |
| 0.10 | Burst | Radial mote burst from the player, speed 6 blocks/s outward, drag 0.9 | 40 `frost_mote` (Rukia) / 40 `petal` (Byakuya) | `entity.player.attack.sweep` | shake 0.4 degrees, 0.3 s |
| 0.20 | Light ring (Rukia only) | `snow_ring` vertical at the blade, white, radius 0.8 growing to 1.4 over 0.3 s, tracking the blade (cf. [img rukia_shikai_2]) | 1 quad | `block.amethyst_block.chime` | none |

**Reiatsu aura (continuous while in state)**
| Character | Shape / colour | Size | Count (per second) | Sound |
|---|---|---|---|---|
| Rukia shikai | `reiatsu_wisp` soft white-blue streaks rising along the body, colour `#DDF3FF` fading to `#9ED3F0` | height 2.2 blocks, radius 0.6 | 16 | `block.powder_snow.step` quiet, every 2 s |
| Rukia bankai | `frost_mote` drifting down like snow plus a thin ground haze `snow_ring` radius 2.5, colour `#EAF8FF` | radius 3 | 30 | `block.amethyst_block.resonate` at low volume, every 3 s (cold hum) |
| Byakuya shikai | `reiatsu_wisp` lavender streaks, colour `#D9C8F0` | height 2.2, radius 0.6 | 14 | none |
| Byakuya bankai | slower, heavier wisps `#CFC3F0` plus a ring of drifting petals (see 2.4) | radius 2.5 | 20 | `entity.warden.heartbeat` at low volume |

### 2.2 Rukia shikai dances (Sode no Shirayuki)

Source: [RS 4.1 to 4.3]. The ice dances are fixed ability names; call the dance, then the effect starts.

#### Some no Mai: Tsukishiro
Ring of light on the ground, a pillar rises, everything inside freezes then shatters [RS 4.1, img rukia_shikai_7]. Radius 4 blocks, height 14.
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Wind-up: she draws a circle with the tip | `snow_ring` flat quad follows the blade tip path, white `#F0F8FF`, thickness 0.15 | 20 `frost_mote` along the path | `entity.player.attack.sweep` | none |
| 0.2 | Circle completes on the ground | Full `snow_ring` radius 4, glowing `#BFE4FF`, additive | 1 quad | `block.amethyst_block.chime` | none |
| 0.5 | Pillar of light | `light_pillar` radius 3.5, height 0 to 14 in 0.35 s, colour `#EAF8FF`, additive | 120 `frost_mote` rising | `item.trident.thunder` at low volume | flash 30 percent, 0.05 s |
| 1.0 | Freeze | Mobs inside get an ice overlay (client tint `#8EC9EE` at 50 percent), ice crust on the ground | 60 `ice_shard` standing around the rim | `entity.player.hurt_freeze` | frost_edge vignette 20 percent |
| 1.5 | Hold | Pillar steady, frost crawling up mobs | 30 `frost_mote` /s | none | none |
| 2.0 | Shatter | Pillar collapses; ice on mobs bursts | 140 `ice_shard`, 80 `frost_mote`, speed 5 | `block.glass.break` x3 | shake 0.6 degrees, 0.35 s |
| 2.5 | Fade | Ring and haze dissolve | 20 `frost_mote` | none | none |

#### Tsugi no Mai: Hakuren
Ice wave in a line [RS 4.2, img rukia_shikai_9, rukia_shikai_10]. Range 12 blocks, width 4, speed 24 blocks/s.
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Ground puncture, ice circle under her | `snow_ring` radius 2, `#C0D3E7` | 30 `frost_mote` | `block.glass.break` | none |
| 0.3 | Four punctures in a semicircle in front | 4 small `snow_ring` radius 0.8, each with a column of rising motes (4 blocks) | 4 x 20 `frost_mote` | `block.amethyst_cluster.break` x4 staggered 0.06 s | none |
| 0.7 | Gather at the blade tip | Soft glow sphere radius 0.5 at the tip, colour `#EAF8FF` | 30 `frost_mote` converging | `block.beacon.power_select` | none |
| 1.0 | Release wave | Cone of cold: flat `light_pillar` quads 4 wide x 3 high travelling forward, colour `#C0D3E7` fading to `#EAF8FF` | 220 `frost_mote` + 40 `ice_shard` over 0.5 s | `entity.breeze.wind_burst` + `block.powder_snow.break` | shake 0.5 degrees, 0.4 s |
| 1.5 | Impact frost | Blocks along the path get a temporary frost layer (ice blocks, server-side with rollback); mobs hit freeze | 60 `ice_shard` | `entity.player.hurt_freeze` | none |
| 3.0 | Frost fades | Temporary ice blocks revert | 40 `frost_mote` | `block.glass.break` quiet | none |

#### San no Mai: Shirafune
Ice blade extension at the tip [RS 4.3, img rukia_shikai_8]. Reach 8 blocks.
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Moisture gathers at the tip | Mote swirl, radius 1, converging | 30 `frost_mote` | `block.amethyst_block.resonate` | none |
| 0.3 | Ice blade grows | Code-generated glowing strip (two crossed quads, no Blender mesh), width 0.25, length grows 1 to 8 over 0.2 s, colour `#CFEFFF` with core `#7FB8DF` | 40 `ice_shard` shed along it | `item.trident.throw` | none |
| 0.6 | Thrust / pierce | The blade extends fully; target contact | 60 `ice_shard`, 40 `frost_mote` | `entity.player.attack.crit` + `block.glass.break` | shake 0.4 degrees |
| 1.0 | Spread freezing | Frost spreads from the contact point along the target and the surrounding blocks | 20 `frost_mote` /s | `entity.player.hurt_freeze` | none |
| 2.0 | Blade retracts, shards fall | | 30 `ice_shard` | `block.glass.break` | none |

### 2.3 Rukia bankai (Hakka no Togame)

#### Release (see 2.1 for the flash)
Source: [RB 3]. Blinding white pillar, a pillar of cold mist, her clothing changes. Extra sequence on top of 2.1:
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | White pillar | `light_pillar` radius 1.2, height 20, white `#FFFFFF` | 60 `frost_mote` | `block.beacon.activate` | bankai_flash 100 percent |
| 0.4 | Mist column | `snow_ring` stack: 5 rings radius 1 growing to 3, colour `#EAF8FF`, rising | 80 `frost_mote` | `block.powder_snow.break` | frost_edge 25 percent |
| 1.0 | Costume forms | Costume overlay (collar, pauldrons, crown, chest flower) fades in from alpha 0 to 1 over 0.6 s, ribbons unfurl, 6 `rukia_bankai_crystal_a` grow around her feet | 20 `ice_shard` | `block.amethyst_block.chime` x3 | none |
| 2.0 | Settle | Pillar fades, ground haze radius 3 remains | 20 `frost_mote` | none | none |

#### Passive frost aura (continuous)
[RB 4: contact freezing / "everything she touches freezes"]. Radius 3 blocks. Server applies Slowness I and a minor freeze meter to hostile mobs; client visuals:
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| continuous | Frost haze | `snow_ring` flat quad on the ground, radius 3, `#EAF8FF` at 30 percent, slowly rotating | 1 quad | none | none |
| continuous | Falling frost | `frost_mote` fall from 3 blocks above, drift speed 0.4 | 30 /s | `block.powder_snow.step` every 2 s | none |
| continuous | Frost on touch | Mobs inside the ring get a thin white-blue tint (`#CFEFFF`, 20 percent) and breath puffs | 4 `frost_mote` /s per mob | `entity.player.hurt_freeze` once on entry | frost_edge 8 percent while a mob is inside |
| ground | Crystals | 6 to 10 small `rukia_bankai_crystal_a/b` appear around the rim over 2 s | 8 | `block.amethyst_block.chime` low | none |

#### Active "absolute zero" area freeze
Source: [RB 4]: wave of cold toward the target, area where temperature drops to absolute zero; [RB 4: "only about 4 seconds at absolute zero is safe"]. Radius 10 blocks; the effect lasts 4 s of active build-up (the 4 s figure is the source's safe limit and reused as the gameplay tuning anchor).
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Blade raised toward the target | Bright `frost_mote` gather at the tip | 40 | `block.beacon.power_select` | none |
| 0.4 | Pillar and mist | White pillar radius 1.5 height 20, mist ring expanding 0 to 10 over 1 s, `#EAF8FF` | 120 | `entity.warden.sonic_boom` low-pitch | flash 50 percent, 0.07 s |
| 1.0 | Air crystallises | 60 to 100 `ice_shard` hang in the air in the zone, then drift slowly (speed 0.2), plus 6 to 10 `rukia_bankai_crystal_b/c` grow from the ground | 100 | `block.amethyst_cluster.place` | frost_edge 30 percent, ramp 1 s |
| 2.0 | Pause | Everything holds: mobs fully encased (`rukia_bankai_ice_shell` mesh, `#8EC9EE` at 70 percent), particles stop moving, a faint grey desaturation | 0 new | no new sounds until t = 3.0 | `freeze_desat` 15 percent, vignette 20 percent |
| 3.0 | Crack | White crack lines run across crystals and shells, 0.3 s | 40 `ice_shard` flicker | `block.glass.break` (single, loud) | none |
| 3.3 | Shatter | All crystals and shells burst, `ice_shard` flung outward speed 8 | 260 `ice_shard`, 160 `frost_mote` | `block.glass.break` x5 staggered, `entity.player.hurt_freeze` | shake 1.0 degree, 0.5 s |
| 4.0 | Aftermath | Frost haze on the ground radius 10 fades over 3 s; temporary ice blocks revert | 60 `frost_mote` | `block.powder_snow.break` | frost_edge fade-out |

### 2.4 Byakuya shikai (Senbonzakura: Chire)

Source: [BS 3, 4a to 4c]. The blade scatters into a thousand blades that are steered by swinging the hilt; modes attack and barrier/dome. Petal colour `#E5A4DC` (body) / `#F9C8F6` (highlight); reference frames [img byakuya_shikai_3, byakuya_shikai_7]; the swarm is rendered by `PetalSwarmRenderer`, 1000 petals at quality 1.0 (3000 total including the bankai storm).

#### Release (Chire)
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Blade separates | The held item switches to `byakuya_shikai_hilt`; a petal stream sprays from the stub, cone angle 25 degrees, length 3 blocks | 200 `petal` over 0.4 s | `block.pink_petals.break` x3 + `entity.illusioner.cast_spell` | flash 40 percent, lavender tint, 0.05 s |
| 0.2 | Cloud forms | Swarm gathers into a loose ring around the player, radius 2, height 1.5 | up to 1000 petals active | `entity.phantom.flap` quiet loop | none |
| 0.5 | Swarm idle | Orbit at 0.6 rev/s, small noise, slight upward drift | 1000 petals | `block.pink_petals.step` randomly every 0.5 s | none |

#### Attack mode (swarm follows the aim)
Aim steers the swarm; it flies to the crosshair target, up to 24 blocks away.
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Command / hilt swing | Hilt swing animation | none | `entity.player.attack.sweep` | none |
| 0.2 | Swarm elongates toward the aim | Ribbon of petals 1.2 wide stretching to the target at 30 blocks/s | 600 petals (60 percent of the swarm) | `entity.breeze.wind_burst` | none |
| 0.5 | Impact | Petals envelop the target and rotate (radius 1.5, 3 rev/s) | 600 petals plus 40 loose `petal` debris | `entity.player.attack.crit` repeated 0.1 s apart x3 | shake 0.3 degrees, 0.2 s |
| 1.0 | Cutting phase | Dense pink core `#F9C8F6` at 80 percent opacity, radius 1 around the target | 30 `petal` /s sparks | `block.pink_petals.break` loop | none |
| 1.5 | Return | Swarm streams back to the player | 600 petals | `block.pink_petals.step` | none |

#### Barrier / dome mode
Fandom says the blades can fully enclose him, but gives no radius [BS 4b]; the Hurtless Area is 85 cm [BS 4b]. Dome radius 3 blocks (**DECISION (low-confidence source)**); the inner 0.85 m is a safe zone in lore; gameplay uses the dome as a damage-absorbing barrier.
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Command | Swarm contracts | none | `block.beacon.power_select` | none |
| 0.3 | Dome forms | Petals arrange on a hemisphere radius 3, 4 layers, spinning at 0.8 rev/s | 900 petals | `item.shield.block` | none |
| 0.6 | Solid | Subtle lavender rim ring on the ground, `#CFC3F0` | 1 quad | `block.amethyst_block.resonate` low | none |
| per hit | Block reaction | Local petal spray of 20 away from the impact | 20 `petal` | `item.shield.block` + `block.pink_petals.break` | shake 0.2 degrees |
| 5.0 or cancel | Dome collapses | Petals fall to the ground and fade | 900 petals | `block.pink_petals.step` | none |

### 2.5 Byakuya bankai (Senbonzakura Kageyoshi)

Source: [BB 3, 4]. He releases it by dropping the sword; the hilt sinks into the ground with a ripple, the landscape darkens, two rows of giant blades rise, then scatter into petals. All effect timings are my own estimates ([BB 3] only says "within seconds").

#### Release and rising rows
Rows: 2 rows parallel to the player's look direction at release, 6 blocks either side of the player axis. Each row has R ranks: rank 1 is innermost; each further rank is 1.2 blocks further out, staggered 0.55 blocks along the row, and scaled +10 percent. Blade spacing along a rank is 1.1 blocks. Rows start 3 blocks in front of the player and extend behind him. Blade count N = config `bankaiBladeCount` x effectQuality (default 200, max 1000); R = max(2, ceil(N / 200)), at most 5; blades per rank = N / (2 x R). Default: 2 rows x 2 ranks x 50 = 200 blades, 55 blocks long. Maximum: 2 x 5 x 100 = 1000 blades, 110 blocks long. All blades are full scale (no perspective taper; perspective is real in 3D). Blades are cosmetic and pass through terrain. Evidence: [img byakuya_bankai_3, byakuya_bankai_4].
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Sword drop | `byakuya_bankai_hilt_ground` object descends 0.5 m into the ground in 0.4 s | 10 dust | `item.trident.hit_ground` | none |
| 0.3 | Ripple | `byakuya_bankai_ripple` scales 0 to 6 blocks over 0.8 s, `#CFC3F0` | 1 mesh | `block.respawn_anchor.charge` | none |
| 0.6 | Darkening | Sky and ambient tint toward `#1C2540` | none | `ambient.cave` cut-in | `vignette_dark` 50 percent, ramp 0.6 s |
| 1.0 | Blades start to rise | Each blade rises from -8 m to its rest height in 0.5 s (ease-out cubic). Start delay per blade = 0.012 s x its distance in blocks from the player along the row (default rows complete at t ≈ 2.2, maximum-length rows at t ≈ 2.8). | 200 giant blades; 4 dust per rising blade, at most 400 total | `block.pointed_dripstone.land` pitched down per blade (max 8 sounds/s), `entity.ender_dragon.growl` at low volume | shake 0.8 degrees ramping |
| 2.2 | Rows complete (default length; 2.8 at maximum length) | All blades at full height; tip glow on (`#F2E9FF`) | 0 | `block.beacon.ambient` loop | shake 1.2 degrees, 0.4 s then decay |
| 2.8 | Hold | Slow tilt: tips lean 3 degrees inward | 0 | none | vignette_dark 50 percent |

#### Petal storm (scatter)
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Command (spoken Chire in lore) | Tip glow pulses to 100 percent | none | `block.amethyst_block.resonate` | none |
| 0.3 | Blades dissolve from the top | Each blade breaks into P = min(15, floor(maxPetals / N)) petals, where maxPetals = 3000 x effectQuality, over 0.4 s from the tip down; start delay = 0.008 s x the blade's distance in blocks from the player. The storm never exceeds maxPetals. | 3000 petals at quality 1.0 (200 blades x 15) | `block.pink_petals.break` pitched low, `entity.phantom.flap` | flash 15 percent |
| 0.8 | Storm | Petals swirl up the corridor and form a rotating tornado of 5 blocks radius around the player; the player stays in the calm core (Hurtless Area, radius 0.85) | 3000 petals | `item.elytra.flying` quiet loop (as wind) | none |
| 1.5 | Directed attack | Storm streams toward the aimed target in a 3-wide band, 40 blocks/s | 2000 petals | `entity.breeze.wind_burst` | shake 0.6 degrees |
| 2.5 | Impact | Dense pink-white burst at the target, radius 5, then dissolve | 300 `petal` sparks | `entity.generic.explode` + `block.pink_petals.break` | flash 25 percent |
| 4.0 | Fade | Petals fade out; rows reassemble or the effect ends | | `block.pink_petals.step` | vignette fades |

#### Senkei (optional)
Optional stretch; only if time permits after the required effects pass. [BB 4.3, img byakuya_bankai_7]: petals compress into about a thousand glowing swords in rotating rows; in the anime they glow pink.
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Compress | Storm contracts into 4 rows of small swords around the player and target, row radius 4, 4 rows x 24 swords | 96 senkei swords (`byakuya_bankai_senkei_sword`) | `block.respawn_anchor.charge` | vignette_dark 40 percent |
| 0.8 | Hold | Rows rotate slowly (0.3 rev/s), pink glow `#F25FB8` | 0 new | `block.beacon.ambient` | none |
| 2.0 | Release (Ikka Senjinka, optional) | All swords fly to the target within 0.5 s | 96 swords, plus 120 pink `petal` | `entity.player.attack.crit` rapid x8 | shake 0.8 degrees |

#### Shukei: Hakuteiken
[BB 4.5, img byakuya_bankai_10, byakuya_bankai_11]. Condenses everything into one sword; white wings and a vertical halo form on his back. Wing span 6 m each, halo radius 1.1 m, strike distance up to 20 blocks.
| t | Event | Shape / colour / size | Particles | Sound | Screen |
|---|---|---|---|---|---|
| 0.0 | Condense | All petals and swords rush into the sword in his hand | 2000 petals converging | `block.beacon.activate` | vignette_dark 60 percent |
| 0.5 | Wings and halo | `hakuteiken_wing_l/r` unfold 0 to 6 m in 0.4 s; `hakuteiken_halo` fades in behind the head, white `#FFFFFF` | 80 white `frost_mote`-style feathers | `entity.ender_dragon.flap` + `block.bell.use` | flash 40 percent |
| 1.0 | Blade glow | `hakuteiken_blade_body` brightens; length grows 1 to 4 m | 40 | `block.amethyst_block.resonate` | none |
| 1.5 | Strike | Blade stretches to the target distance (Z scale), a white line | 200 white particles along the line | `entity.warden.sonic_boom` + `item.mace.smash_ground` | bankai_flash 80 percent, shake 1.2 degrees, 0.5 s |
| 1.8 | Impact | White burst radius 5, ground ring `snow_ring` radius 8, `#FFFFFF` | 300 | `entity.generic.explode` + `block.glass.break` | flash decays over 0.4 s |
| 2.5 | Fade | Wings and halo dissolve to white particles | 100 | none | vignette fades |

### 2.6 Performance notes for the VFX agents
- Mass petals and blades are batched draws (`PetalSwarmRenderer`, `BankaiBladeRenderer`), never entities. Petals: full count up to 16 blocks from the camera, 50 percent at 16 to 32, 25 percent at 32 to 64, culled beyond 64. Giant blades: full mesh up to 32 blocks, `byakuya_bankai_blade_lod` beyond 32, culled beyond 160; blade emissive pass only up to 64 blocks. Ordinary particles use vanilla culling.
- All counts scale with `effectQuality`. If FPS falls, lower counts first, then drop the emissive pass, and only last the effect itself.
- Particles that need a custom texture (`frost_mote`, `ice_shard`, `snow_ring`, `light_pillar`, `petal`, `reiatsu_wisp`) are authored at 16x16 (mote, shard, petal) or 64x64 (ring, pillar) in the same hand-painted style; they are not part of the six modelling tasks and need no Blender work.

---

## 3. Gate A decisions (final)

Resolved decisions, one line per former open question (same numbering). Source: `design/GATE_A.md` section 3.

1. **Byakuya saya colour:** dark violet-black `#2E2840` confirmed ([img byakuya_sealed_5], [img byakuya_sealed_4]); no white variant is made.
2. **Giant blade size:** 8.0 x 0.55 x 0.14 m accepted, with sori 0.75 m (E14); blades are cosmetic, pass through terrain, and build height is irrelevant.
3. **Giant blade glow:** accepted as a design decision (top 1.2 m gradient plus 40 percent edge line), scaled by the emissive multiplier setting.
4. **Rukia bankai sword:** rejected as drafted; use the oblong bar guard and 0.28 m grip shown in ch. 570 and ep. 385 (E11).
5. **Shikai blade length:** 0.78 m accepted (the frames win over "assume unchanged").
6. **Rukia costume pieces:** kept in scope (collar, both pauldrons, crown, chest flower), modelled last after Gate B, and rendered as a player feature-layer overlay; Gate C judges them only after the required pieces pass.
7. **Senkei:** kept as an optional stretch; `byakuya_bankai_senkei_sword` is modelled only after Gate C passes, and its storyboard stays marked optional.
8. **Byakuya bankai voice phrase:** the trigger is "Bankai" optionally followed by "Senbonzakura Kageyoshi" (MASTER_PROMPT); "Chire" is never a bankai trigger.
9. **Rukia bankai phrase:** "Bankai, Hakka no Togame" is the trigger; VOICE_PHRASES.md notes it is game-sourced, not canon.
10. **Petal size and glow:** the 12 cm leaf-blade with 25 percent emission is accepted as a readability trade-off; the emissive multiplier setting can turn it off.
11. **Dome radius:** 3 blocks gameplay radius; the 0.85 m Hurtless Area is cosmetic lore only.
12. **Ribbon count:** shikai has one ribbon (confirmed by [img rukia_shikai_1, _2, _3]) but 2.5 m long (E6); bankai has 3 ribbons from the back (confirmed by [img rukia_bankai_2, _3]).
13. **Texture sizes:** 256x256 for the four small models and 512x512 for the two bankai models are confirmed.
14. **Coordinate convention:** no objection to Z-up, origin at the kashira, edge -Y, sori toward +Y; only the `grip_hand` height changes (E2) and export becomes one OBJ per object (E3).
15. **Colour accuracy:** no separate sampling pass; the bible HEX values (as edited) are binding starting values, painters check them against the cited frames, and Gate C reviews colour on turntables.

---

## 4. Deferred suggestions

Optional Gate A suggestions (`design/GATE_A.md` section 4). Each adds scope or a design choice, so none was applied in v2:

- Ribbons as a dynamic verlet triangle strip instead of hinged rigid segments (segments stay as fallback).
- Byakuya bankai darkening via `vignette_dark` plus fog-colour change, no true sky recolour.
- Rukia sealed blade: faint 1 px painted line pair on the blade strips (embossed curved lines).
- `wingScale` config (default 1.0) for the Hakuteiken wings to avoid wall clipping.
- Slightly different release-flash tint for shikai and bankai (bankai whiter).
- Fix `refs/INDEX.md` labels (orchestrator task, outside this document).
