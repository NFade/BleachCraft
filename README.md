# BleachCraft (reiatsu_test)

A Fabric mod for Minecraft Java 1.21.1 based on Bleach. Two zanpakuto, each with a sealed (sheathed), drawn, shikai and bankai form:

- **Kuchiki Rukia: Sode no Shirayuki** (ice, white blade and ribbon): Tsukishiro, Hakuren, Shirafune, bankai Hakka no Togame (Absolute Zero).
- **Kuchiki Byakuya: Senbonzakura** (blade petals): attack and barrier modes, bankai Senbonzakura Kageyoshi (scatter, Hakuteiken).

Every sword is a model built in Blender and loaded as an OBJ item model, with a reiatsu bar, server-side abilities, a HUD, custom
effects and optional voice control through a browser page. This is a test prototype to judge whether a full mod is worth building,
not a finished game. Current build state: `STATUS.md`; decisions and measurements: `LOG.md`; designs: `design/`.

## Requirements

| Part | Version |
|---|---|
| Minecraft Java Edition | 1.21.1 |
| Java | 21 (Temurin 21 is used for development) |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.116.17+1.21.1 (the version it is built against; `fabric.mod.json` asks for any Fabric API) |
| Mappings (development only) | Yarn 1.21.1+build.3, Fabric Loom 1.17.21 |

The mod has to be installed on the client **and** on a dedicated server (the server owns the state, the client draws the models
and effects). A browser with speech recognition (Chrome or Edge) is only needed for voice control.

## Build and install

```
cd mod
gradlew build            # Windows; on Linux and macOS: ./gradlew build
```

Set `JAVA_HOME` to a JDK 21 first. `build` compiles, runs the unit tests (no Minecraft needed) and writes the jar to
`mod/build/libs/reiatsu_test-0.0.1-spike.jar` (the remapped jar to install; the `-sources.jar` next to it is only the source).
`gradlew releaseJar` builds it and prints the path, the size and a content check; `docs/RELEASE.md` explains the checks and how to
test the jar in the official launcher.

Install: put the jar and Fabric API into the `mods` folder of a Fabric 1.21.1 profile (Fabric Loader 0.19.5 or newer). Dedicated
server: the same two jars in the server `mods` folder. The first start writes `config/reiatsu_test.json` and
`config/reiatsu_test_client.json`.

Get the swords: Creative inventory, Combat tab (Sode no Shirayuki, Senbonzakura), or `/give @s reiatsu_test:sode_no_shirayuki`
and `/give @s reiatsu_test:senbonzakura` (there is no crafting recipe). The operator command `/reiatsu` (info, full, set, state,
cooldowns clear, pvp, voice) sets state and bar for testing. A dev item `spike_item` exists for the render spike.

## Run in development

```
cd mod
gradlew runClient                 # normal dev client (run dir mod/run)
gradlew runServer                 # dedicated dev server (run dir mod/run-server, accept the EULA there first)
gradlew build test                # build and unit tests
python -I ../tools/server_smoke.py   # dedicated server smoke test, no game window, about 2.5 minutes, prints PASS or FAIL
```

Dev harnesses (each opens a game window by itself, one game at a time; they drive the game, take screenshots and print
`CHECK PASS` or `FAIL`): `gradlew runSpike` (OBJ item rendering spike), `gradlew runPhase4` (state machine, abilities, HUD, sync),
`gradlew runPhase5` (voice bridge in game), `gradlew runPhase6 [-Phold=<scenario>] [-PfreezeAt=<ms>] [-Pfx=key=value,...]`
(effects and HUD screenshots). The windows open on the monitor that contains the point `dev_monitor` in `mod/gradle.properties`
(override it in `%GRADLE_USER_HOME%\gradle.properties`).

## Controls

Keys are rebindable in Options, Controls, category **Reiatsu Test**. The mod ignores them while a screen is open.

| Key | Action |
|---|---|
| `J` (or right click with the sword) | draw the sword from the scabbard, press again to sheathe it |
| `R` | release: shikai (only from the drawn form) |
| `G` | bankai (only from shikai, needs a full reiatsu bar) |
| `V` | seal: back to the sheathed sword from any drawn form |
| `Z`, `H`, `B` | ability 1, 2, 3 of the current character and form |
| shunpo | see in-game **Controls** (Options, Controls, Reiatsu Test); the key is listed there once the ability is in your build |

Abilities (key): Rukia shikai: Tsukishiro `Z`, Hakuren `H`, Shirafune `B`; Rukia bankai: Absolute Zero `Z`; Byakuya shikai: attack
mode `Z`, barrier mode `H`; Byakuya bankai: scatter (petal storm) `Z`, Hakuteiken `H`. Senkei exists but is disabled. Costs,
cooldowns, damage and radii are numbers in `BalanceConfig` (`core/state`) and are listed in `design/STATE_MACHINE.md`.

## State flow

```
SEALED --J or right click--> BASE --R--> SHIKAI --G (full bar)--> BANKAI
   ^                          |            |                         |
   +----- J or V -------------+---- V -----+---- V, timeout, bar 0 --+
```

SEALED is the sheathed sword, BASE is the drawn sword without release.

- The sword has to stay in the main hand. Drop it, hold something else for more than a second, die, change dimension or log out
  and the state goes back to SEALED. The state belongs to the player and is synchronised to everybody (auras); the reiatsu bar is
  private to its owner and saved with the player.
- Shikai costs 15 reiatsu. Bankai needs the full bar (100) and costs 20; each form regenerates or drains the bar at its own rate,
  and bankai lasts at most 45 seconds and then returns to SEALED. What the server refuses (state, item, bar, cooldown) is shown as a
  short HUD message. Balance is still being tuned (`design/FIXES_B4.md`: bankai timer, bigger radii, shunpo).
- Abilities change the world only with temporary ice or snow that is rolled back on timeout, seal, death, logout or server stop
  (at most 128 blocks per player). They never hurt the caster, villagers, wandering traders or, by default, other players
  (`/reiatsu pvp true` allows player damage for testing).

## Voice

Say the phrase out loud and the game acts as if you had pressed the key. Commands still obey every rule (state, sword in
hand, reiatsu, cooldowns): a spoken command that the server refuses gets the same feedback as a refused key press.

**How to open the page**

1. Start the game, open a single-player world. The chat shows `Voice bridge ready: http://127.0.0.1:47821/`; click it, or run
   `/reiatsu voice` to see the link again. The bridge listens on the loopback address 127.0.0.1 only (default port 47821, the next
   four ports are tried when it is busy).
2. Open the link in Chrome or Edge, press **Start**, allow the microphone, choose Japanese, English or Russian.
3. Hold a zanpakuto in the main hand and speak. The page shows what it heard, what the game answered, and the phrases that
   work in your current state.

Chrome and Edge do the recognition in their vendor's cloud (internet needed, audio leaves your PC); Firefox has no speech
recognition. Details, query parameters and the config keys: `voice-bridge/README.md`. Dedicated servers do not start the
bridge (single player only for now).

**Phrases per state.** Every phrase also works in its Japanese and Russian spelling, and common misheard forms are accepted
(full lists: `design/VOICE_PHRASES.md`). A phrase for another state or the other sword is ignored. Drawing the sword (BASE) has no
voice command: use `J` or a right click.

| Sword | State | Say | Effect |
|---|---|---|---|
| Rukia | drawn | "Mae, Sode no Shirayuki" (or "Sode no Shirayuki", "Shirayuki"), 舞え 袖白雪, "Мае, Содэ но Сираюки" | release (shikai) |
| Rukia | shikai | "Some no mai, Tsukishiro" (or "Tsukishiro") | first dance, Tsukishiro |
| Rukia | shikai | "Tsugi no mai, Hakuren" (or "Hakuren") | second dance, Hakuren |
| Rukia | shikai | "San no mai, Shirafune" (or "Shirafune") | third dance, Shirafune |
| Rukia | shikai | "Bankai, Hakka no Togame" (or just "Bankai"), 卍解, "Банкай" | bankai (needs a full reiatsu bar) |
| Rukia | bankai | "Absolute zero", 絶対零度, "Абсолютный ноль" | absolute zero |
| Byakuya | drawn | "Chire, Senbonzakura" (or "Chire", "Scatter"), 散れ千本桜, "Чире" | release (shikai) |
| Byakuya | shikai | "Attack mode", 攻撃モード, "Режим атаки" | swarm attack mode |
| Byakuya | shikai | "Barrier mode" or "Dome mode", 防御モード, "Режим барьера" | barrier dome |
| Byakuya | shikai | "Bankai, Senbonzakura Kageyoshi" (or "Bankai", "Kageyoshi") | bankai (needs a full reiatsu bar) |
| Byakuya | bankai | "Chire" or "Petal storm", 桜吹雪, "Шторм лепестков" | petal storm (scatter) |
| Byakuya | bankai | "Shukei, Hakuteiken" (or "Hakuteiken") | Hakuteiken |
| either | drawn, shikai or bankai | "Seal", 封印, "Запечатать" (whole sentence only) | back to sealed |

Notes: "Chire" is the release when the sword is drawn and the petal storm in bankai, never a bankai trigger. Lone "Seal",
"Chire" and "Scatter" only count as the whole utterance ("the seal is broken" does nothing). "bank eye" (what English
recognisers often hear for "bankai") is accepted as bankai only while you are in shikai, hold the sword and the bar is full.

**Tuning.** `config/reiatsu_test.json`, section `voice` (port, confidence threshold, debounce times, interim results,
languages). Regenerate the phrase table after editing `design/VOICE_PHRASES.md` with `python -I tools/gen_voice_phrases.py`.

## Config

- `config/reiatsu_test.json` (written on the first start): section `voice` with `enabled`, `port` (47821), `portSearch` (4),
  `threshold` (0.86), `margin`, `interimEnabled`, `interimThreshold`, `debounceMs`, `globalGapMs`, `consumedTextMs`,
  `allowNullOrigin`, `maxBodyBytes`, `maxRequestsPerSecond`, `languages`, `defaultLanguage`, `servePage`. Meaning of every key:
  `voice-bridge/README.md`.
- `config/reiatsu_test_client.json` (client only): `show_first_person_hand`, `first_person_scale_multiplier`,
  `draw_animation_seconds`, `fxTier` (`LOW`, `MEDIUM`, `HIGH` or `CUSTOM`; a tier sets the quality dependent keys, `CUSTOM` keeps
  them as written), `effectQuality`, `maxPetals` (3000), `bankaiBladeCount` (up to 1000), `bladeLodDistance`, `nearPetalCount`,
  `maxFxParticles`, `maxGlowSprites`, `maxIceShells`, `maxCrystals`, `maxShardMeshes`, `bladeCullDistance`, `reduceMotion`,
  `photosensitiveSafe` (weaker flashes, no impact frames), `screenFxScale`, `shakeScale`, `fxSoundVolume`, `glowIntensity`,
  `gradeStrength`, `decals`, `maxConcurrentFx`, and the HUD switches `hud`, `hudCompact`, `titleCards`, `crosshairPips`,
  `voiceHud`, `voiceToastSound`, `cooldownReadySound`. A missing key is added with its default; a file that does not parse is
  never overwritten.
- Balance numbers are not in a file yet: they are `BalanceConfig.defaults()` in the code.

## Known issues

From `STATUS.md` ("Known UNVERIFIED" and minor defects) at the time of writing. They have not been proven in a real launcher or
with real people:

- The mixin refmap and the remapped jar were run from the development environment only, not from the official launcher
  (checklist in `docs/RELEASE.md`).
- `jdk.httpserver` (transport of the voice bridge) exists in the dev runtime; in the official launcher runtime it is unknown. A plain
  socket fallback exists and is covered by unit tests only.
- Fabulous graphics were not checked with the translucent ice blade and the glow layers (Fast and Fancy were).
- The camera shake hook and the Byakuya hand poses on a real player model (tuned on an armor stand) are unverified.
- Voice: no real microphone session has been run; Chrome and Edge send the audio to their cloud for recognition; voice works in
  single player (the host player) only, dedicated servers and LAN guests do not start the bridge.
- Multiplayer: the dedicated server was smoke tested with scripted protocol clients (`tools/server_smoke.py`); a second real game
  client was not (manual list in `docs/QA_CHECKLIST.md`).
- Third person: at the end of the draw animation the sword jumps from the hip to the hand, and there is no sword swing animation
  visible from outside (third person, other players).
- Effects are still being built (`STATUS.md`): release, auras and seal are in, most ability effects are placeholders or not done.
  Rukia's costume set and Senkei are deferred.

## Credits and licences

- All models, textures, HUD sprites and effects were made for this project (Blender and the Pillow generators in `tools/`). No
  ripped game, anime or wiki assets are in the mod; the reference images used while modelling are private and not in the repository.
- Sounds: vanilla Minecraft sounds referenced by id (nothing is redistributed). No external audio sample is used; `LICENSES.md`
  would list any that is added (CC0 or an explicit licence only).
- Fonts: the mod ships no font file. The kanji title plate (卍解) is drawn at run time with Minecraft's own glyphs. The effects
  design (`design/VFX_STORYBOARD.md`) names **Noto Serif JP** (SIL Open Font License 1.1, copyright the Noto Project Authors,
  https://openfontlicense.org) for a pre-rendered title plate. If such a plate is added, ship the OFL notice and the font's
  copyright line with the mod (see `LICENSES.md`, Credits).
- Built on Fabric Loader and Fabric API (Apache 2.0) and Yarn mappings (CC0) by the FabricMC project. Minecraft is a trademark of
  Mojang AB; this is an unofficial fan project, not affiliated with Mojang, Microsoft or the creators of Bleach.
- Bleach and its characters belong to their creators. This project has no commercial purpose.
- The source files of the mod are declared CC0-1.0 in `fabric.mod.json`.

## Русский (кратко)

Fabric-мод для Minecraft Java 1.21.1 по Bleach: два занпакто, Содэ но Сирасаюки (Рукия) и Сенбонзакура (Бякуя). У каждого есть
запечатанная форма в ножнах, обнажённый клинок, шикай и банкай. Есть шкала рейацу, способности на сервере, HUD, свои эффекты и
голосовое управление через страницу в браузере. Это тестовый прототип, а не готовая игра.

- **Требования:** Java 21, Minecraft 1.21.1, Fabric Loader 0.19.5 и новее, Fabric API 0.116.17+1.21.1. Мод нужен и на клиенте, и на
  выделенном сервере.
- **Сборка:** `cd mod`, `gradlew build` (переменная `JAVA_HOME` на JDK 21). Готовый jar: `mod/build/libs/reiatsu_test-0.0.1-spike.jar`,
  его и Fabric API положить в папку `mods`. Меч лежит во вкладке Combat (боевые предметы) творческого инвентаря или выдаётся командой
  `/give @s reiatsu_test:sode_no_shirayuki`.
- **Клавиши:** `J` или правый клик достаёт меч и убирает в ножны, `R` шикай, `G` банкай (нужна полная шкала), `V` запечатать,
  `Z` `H` `B` способности 1-3. Клавишу шунпо смотри в игре: «Настройки, Управление, Reiatsu Test».
- **Состояния:** ножны, меч обнажён, шикай, банкай. Шикай включается только из обнажённого состояния, банкай только из шикая при
  полной шкале рейацу, банкай длится не больше 45 секунд.
- **Голос:** в одиночной игре откройте ссылку из чата (`http://127.0.0.1:47821/`) в Chrome или Edge, нажмите Start, разрешите
  микрофон и выберите язык (японский, английский или русский). Фразы указаны в таблице раздела Voice. Распознавание делает облако
  браузера (нужен интернет); на выделенном сервере голосовой мост не запускается.
- **Лицензии:** модели, текстуры и эффекты свои, звуки только ванильные. Шрифт Noto Serif JP (лицензия SIL OFL) упомянут в
  дизайне, но в мод не вложен. Подробности: `LICENSES.md`, раздел Credits.
- **Известные проблемы** описаны в разделе Known issues, ручная проверка в игре: `docs/QA_CHECKLIST.md`.
