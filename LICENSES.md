# LICENSES

All models, textures and effects in the mod are original work made for this project.
Sounds: vanilla Minecraft sounds (referenced by id, not redistributed) and original sounds.
Any external sample must be CC0 (or explicitly licensed) and listed below.

| Asset | Source URL | License | Used in |
|---|---|---|---|

## Credits

| Component | Source | License | Used in |
|---|---|---|---|
| Fabric Loader, Fabric API | https://fabricmc.net | Apache License 2.0 | runtime dependency (not bundled in the jar) |
| Yarn mappings 1.21.1+build.3 | https://github.com/FabricMC/yarn | CC0 1.0 | development only (the released jar is remapped) |
| Minecraft 1.21.1 | Mojang AB | Minecraft EULA | not redistributed; vanilla sounds are referenced by id, the kanji title plate uses the game's own glyphs |
| Noto Serif JP | https://github.com/notofonts/noto-cjk | SIL Open Font License 1.1 (copyright the Noto Project Authors) | NOT shipped. `design/VFX_STORYBOARD.md` names it for a pre-rendered bankai title plate; the code draws the kanji with the game's glyphs instead. If a plate rendered from this font is added to `assets/`, add the OFL text and the copyright line to the jar (`META-INF` or `assets/reiatsu_test/licenses/`) and keep the font itself out of the repository unless needed |
| Models, textures, HUD sprites, effects, voice phrase tables | made for this project (Blender, `blender/scripts/`, `tools/*.py`) | declared CC0-1.0 in `fabric.mod.json` | the whole mod |

Sounds: vanilla Minecraft sounds only (no audio file is bundled). No ripped game, anime or wiki asset is in the mod; the private
reference images in `refs/` are git-ignored and never shipped.

