# VOICE PHRASES: command table for the voice matcher

Phase 5 contract. Consumers: (1) the Java fuzzy matcher (`voice` package of the mod), (2) the test fixture generator (haiku task in MASTER_PROMPT phase 5), (3) the unit tests of the matcher.

**Status: PROPOSAL. `design/ADR.md` is final** wherever the two disagree (matcher location, bridge protocol, thresholds). Section 5 (normaliser, thresholds, interim handling, debounce) is a proposal; its sanity numbers come from a throw-away Python prototype of the normaliser and matcher (not part of the repo). The normaliser of 5.1 is nevertheless the reference for this contract: the keys printed in sections 1, 2 and 4 are **normative**, and the Java normaliser must reproduce every one of them exactly (use them as a golden test).

Sources: `MASTER_PROMPT.md` section 5 (phase 5 command table) and section 10; `research/*.md` section 1 of each file (names, phrases, kanji, romaji, translations, Cyrillic estimates); `design/ART_BIBLE.md` section 2 (abilities) and section 3 (Gate A decisions 8 and 9). All text below is original; no wiki prose is copied.

## 0. Conventions

**Origin tags** (column "Origin" in every variant table):

| Tag | Meaning |
|---|---|
| `C` | canon. Documented in `research/` as a spoken phrase, name or kanji (confidence high or med in the source file). |
| `T` | wiki/VIZ translation. An English translation of a canon name taken from the wiki or the VIZ release (research tables "English sub"). Not necessarily spoken on screen. |
| `G` | game-sourced, not canon. Appears only in a game (Brave Souls line), per `research/rukia_bankai.md`. Gate A decision 9: accepted as the trigger, flagged as not canon. |
| `M` | mod-defined. Invented by the mod because no canon phrase exists (modes, absolute zero, seal, petal storm aliases). Free to change by editing this file. |
| `X` | own transcription. Cyrillic transcription written by the research agents or by me. No external source exists; these are estimates for ru-RU speech. |
| `A` | ASR alias. Recorded ASR output that the fuzzy matcher cannot reach on its own (section 2, tier A). Must be added to the variant table verbatim. |

**English dub:** `research/*.md` marks every dub name and phrase as "not verified". No dub row below is filled with an invented line; each is marked `unverified`. The only English words used that are close to a dub reading ("Scatter", "Dance") are wiki translations and are tagged `T` under English sub.

**Key form** = output of the normaliser defined in section 5.1 (lowercase Latin, no punctuation, no macrons, folded consonants). It is what the matcher compares. Spaces between tokens are kept in the table; the matcher also uses the compact form (spaces removed).

**Gating columns:** `state` = state the player must be in (SEALED, SHIKAI, BANKAI; machine in `design/STATE_MACHINE.md`); `item` = which zanpakuto must be in the main hand (the character is derived from the held item, any visual state of it). The matcher only considers commands whose state and item match; everything else is ignored, not scored. `reiatsu` conditions (full bar for bankai) and cooldowns are checked on the server, not in the matcher: the matcher emits an intent, the server accepts or rejects it.

## 1. Command list

**14 commands** (13 required, 1 optional: `byakuya.bankai.senkei`).

| id | state | item in hand | canonical phrase | weak | notes |
|---|---|---|---|---|---|
| `rukia.shikai.release` | SEALED | Rukia's zanpakuto | Mae, Sode no Shirayuki | no | SEALED to SHIKAI. Canon. |
| `rukia.shikai.tsukishiro` | SHIKAI | Rukia's zanpakuto | Some no mai, Tsukishiro | no | Dance 1 (ice ring + light pillar). Name canon, "dance" prefix optional. |
| `rukia.shikai.hakuren` | SHIKAI | Rukia's zanpakuto | Tsugi no mai, Hakuren | no | Dance 2 (ice wave). Same prefix rules. |
| `rukia.shikai.shirafune` | SHIKAI | Rukia's zanpakuto | San no mai, Shirafune | no | Dance 3 (ice blade). Same prefix rules. |
| `rukia.bankai.release` | SHIKAI | Rukia's zanpakuto | Bankai, Hakka no Togame | no | SHIKAI to BANKAI. Server requires full reiatsu. Phrase is game-sourced (Gate A 9). |
| `rukia.bankai.absolute_zero` | BANKAI | Rukia's zanpakuto | (mod-defined) Absolute zero | no | Active area freeze. No canon phrase exists: mod-defined. |
| `byakuya.shikai.release` | SEALED | Byakuya's zanpakuto | Chire, Senbonzakura | no | SEALED to SHIKAI. Canon. Lone "Chire" and "Scatter" are weak (see 3). |
| `byakuya.shikai.mode_attack` | SHIKAI | Byakuya's zanpakuto | (mod-defined) Attack mode | no | Swarm follows the aim. Mod-defined phrase. |
| `byakuya.shikai.mode_barrier` | SHIKAI | Byakuya's zanpakuto | (mod-defined) Barrier mode | no | Dome mode. Mod-defined phrase. |
| `byakuya.bankai.release` | SHIKAI | Byakuya's zanpakuto | Bankai, Senbonzakura Kageyoshi | no | SHIKAI to BANKAI. Server requires full reiatsu. "Chire" never triggers it (Gate A 8). |
| `byakuya.bankai.scatter` | BANKAI | Byakuya's zanpakuto | Chire (same word as shikai) / mod: Petal storm | no | Giant blades to petal storm. Canon word "Chire" (same word as shikai, different state) + mod aliases. |
| `byakuya.bankai.hakuteiken` | BANKAI | Byakuya's zanpakuto | Shukei: Hakuteiken | no | Final strike. Canon name. |
| `byakuya.bankai.senkei` | BANKAI | Byakuya's zanpakuto | Senkei | no | OPTIONAL (ART_BIBLE stretch). Build only if Senkei is built. |
| `common.seal` | SHIKAI / BANKAI | either zanpakuto | (mod-defined) Seal | yes | Back to SEALED from SHIKAI or BANKAI. Mod-defined. Whole-utterance only. |

"weak" = the command is made of short or very common words and is accepted only when the **whole utterance** is the phrase (section 3.1). A few strong commands also carry weak lone-word variants ("Chire", "Scatter"); these are marked in their tables.

### 1.1 `rukia.shikai.release`

- **Required state:** SEALED. **Item:** Rukia's zanpakuto in the main hand.
- **Canonical phrase:** Mae, Sode no Shirayuki
- **Notes:** Release phrase 舞え (mae, "dance") + zanpakuto name. Name alone is accepted; "Mae" alone is not.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 舞え、袖白雪 | `C` | `mae sode no sirayuki` |
|  | 舞え 袖の白雪 | `C` | `mae sode no sirayuki` |
|  | まえ そでのしらゆき | `C` | `mae sodenosirayuki` |
|  | 袖白雪 | `C` | `sode no sirayuki` |
| Romaji | Mae, Sode no Shirayuki | `C` | `mae sode no sirayuki` |
|  | Sode no Shirayuki | `C` | `sode no sirayuki` |
|  | Shirayuki | `C` | `sirayuki` |
| English sub | Dance, Sode no Shirayuki | `T` | `dance sode no sirayuki` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Мае, Содэ но Сираюки | `X` | `mae sode no sirayuki` |
|  | Маэ, Сёдэ но Ширайюки | `X` | `mae sode no sirayuki` |
|  | Мае, Содэ но Сирасаюки | `M` | `mae sode no sirasayuki` |
|  | Содэ но Сираюки | `X` | `sode no sirayuki` |
|  | Ширайюки | `X` | `sirayuki` |
|  | Сираюки | `X` | `sirayuki` |

### 1.2 `rukia.shikai.tsukishiro`

- **Required state:** SHIKAI. **Item:** Rukia's zanpakuto in the main hand.
- **Canonical phrase:** Some no mai, Tsukishiro
- **Notes:** Kanji 初 is read "some" here, not "hatsu" (research). The wiki names the dances "First Dance, Moon White"; VIZ "Dance Number One, White Moon". The English ordinal forms are accepted only together with the name translation (never "first dance" alone).

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 初の舞・月白 | `C` | `some no mai cukisiro` |
|  | そめのまい つきしろ | `C` | `somenomai cukisiro` |
|  | 月白 | `C` | `cukisiro` |
| Romaji | Some no mai, Tsukishiro | `C` | `some no mai cukisiro` |
|  | Tsukishiro | `C` | `cukisiro` |
| English sub | First Dance, Moon White | `T` | `hirst dance mon white` |
|  | Dance Number One, White Moon | `T` | `dance nunber one white mon` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Сомэ но май, Цукиширо | `X` | `some no mai cukisiro` |
|  | Цукисиро | `X` | `cukisiro` |
|  | Цукиширо | `X` | `cukisiro` |

### 1.3 `rukia.shikai.hakuren`

- **Required state:** SHIKAI. **Item:** Rukia's zanpakuto in the main hand.
- **Canonical phrase:** Tsugi no mai, Hakuren
- **Notes:** Wiki: "Next Dance, White Ripple"; VIZ: "Next Dance, White Wave".

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 次の舞・白漣 | `C` | `cugi no mai hakuren` |
|  | つぎのまい はくれん | `C` | `cuginomai hakuren` |
|  | はくれん | `C` | `hakuren` |
| Romaji | Tsugi no mai, Hakuren | `C` | `cugi no mai hakuren` |
|  | Hakuren | `C` | `hakuren` |
| English sub | Next Dance, White Ripple | `T` | `next dance white ripre` |
|  | Next Dance, White Wave | `T` | `next dance white wave` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Цуги но май, Хакурэн | `X` | `cugi no mai hakuren` |
|  | Хакурэн | `X` | `hakuren` |
|  | Хакурен | `X` | `hakuren` |

### 1.4 `rukia.shikai.shirafune`

- **Required state:** SHIKAI. **Item:** Rukia's zanpakuto in the main hand.
- **Canonical phrase:** San no mai, Shirafune
- **Notes:** Wiki: "Third Dance, White Sword"; VIZ: "Dance Number Three, White Sword".

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 参の舞・白刀 | `C` | `san no mai sirahune` |
|  | さんのまい しらふね | `C` | `sanomai sirahune` |
|  | 白刀 | `C` | `sirahune` |
| Romaji | San no mai, Shirafune | `C` | `san no mai sirahune` |
|  | Shirafune | `C` | `sirahune` |
| English sub | Third Dance, White Sword | `T` | `third dance white sword` |
|  | Dance Number Three, White Sword | `T` | `dance nunber thre white sword` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Сан но май, Сирафунэ | `X` | `san no mai sirahune` |
|  | Сирафунэ | `X` | `sirahune` |
|  | Ширафуне | `X` | `sirahune` |

### 1.5 `rukia.bankai.release`

- **Required state:** SHIKAI. **Item:** Rukia's zanpakuto in the main hand. **Server also requires full reiatsu.**
- **Canonical phrase:** Bankai, Hakka no Togame
- **Notes:** Research found no spoken phrase in canon; the only documented line is the game line "Bankai! Hakka no Togame!". Gate A decision 9 makes it the trigger. Wiki literal translation "Censure of the White Haze"; VIZ "White Haze Punishment". Official-looking kanji 白霞罸 is rare; ja-JP ASR will almost certainly write 白霞の咎め (table in 5.1 maps it).

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 卍解 白霞罸 | `G` | `bankai haka no togame` |
|  | 卍解 白霞の咎め | `G` | `bankai haka no togame` |
|  | ばんかい はっかのとがめ | `G` | `bankai hakanotogame` |
|  | 卍解 | `G` | `bankai` |
| Romaji | Bankai, Hakka no Togame | `G` | `bankai haka no togame` |
|  | Bankai | `G` | `bankai` |
|  | Hakka no Togame | `G` | `haka no togame` |
| English sub | Bankai, Censure of the White Haze | `T` | `bankai censure oh the white hase` |
|  | Censure of the White Haze | `T` | `censure oh the white hase` |
|  | White Haze Punishment | `T` | `white hase punisment` |
|  | Final Release, Censure of the White Haze | `G` | `hinar rerease censure oh the white hase` |
|  | Bankai, White Haze Punishment | `T` | `bankai white hase punisment` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Банкай, Хакка но Тогамэ | `X` | `bankai haka no togame` |
|  | Банкай, Хакка но Тогаме | `X` | `bankai haka no togame` |
|  | Банкай | `X` | `bankai` |
|  | Хакка но Тогамэ | `X` | `haka no togame` |

### 1.6 `rukia.bankai.absolute_zero`

- **Required state:** BANKAI. **Item:** Rukia's zanpakuto in the main hand.
- **Canonical phrase:** (mod-defined) Absolute zero
- **Notes:** Research (`rukia_bankai.md` section 4): the wiki describes the bankai ability as widening the area where she can drop temperature to absolute zero; there is no separate spoken name. The mod needs a second bankai command for the active freeze, so it uses the English physics term and its Japanese and Russian equivalents.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 絶対零度 | `M` | `setai redo` |
|  | ぜったいれいど | `M` | `setairedo` |
| Romaji | Zettai reido | `M` | `setai redo` |
| English sub | Absolute zero | `M` | `absorute sero` |
| English dub | n/a (mod-defined, no dub exists) | - | - |
| Russian (Cyrillic) | Абсолютный ноль | `M` | `absoryutni nor` |
|  | Абсолютный нуль | `M` | `absoryutni nur` |

### 1.7 `byakuya.shikai.release`

- **Required state:** SEALED. **Item:** Byakuya's zanpakuto in the main hand.
- **Canonical phrase:** Chire, Senbonzakura
- **Notes:** Release word 散れ (chire, "scatter"). `ART_BIBLE` storyboard calls the shikai release "Chire". Name alone ("Senbonzakura") is **not** a trigger.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 散れ、千本桜 | `C` | `cire senbonsakura` |
|  | ちれ せんぼんざくら | `C` | `cire senbonsakura` |
|  | 散れ (weak: whole utterance) | `C` | `cire` |
|  | ちれ (weak: whole utterance) | `C` | `cire` |
| Romaji | Chire, Senbonzakura | `C` | `cire senbonsakura` |
|  | Chire (weak: whole utterance) | `C` | `cire` |
| English sub | Scatter, Senbonzakura | `T` | `scater senbonsakura` |
|  | Scatter (weak: whole utterance) | `T` | `scater` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Чире, Сэнбонзакура | `X` | `cire senbonsakura` |
|  | Чирэ, Сенбонзакура | `X` | `cire senbonsakura` |
|  | Чире (weak: whole utterance) | `X` | `cire` |
|  | Чирэ (weak: whole utterance) | `X` | `cire` |

### 1.8 `byakuya.shikai.mode_attack`

- **Required state:** SHIKAI. **Item:** Byakuya's zanpakuto in the main hand.
- **Canonical phrase:** (mod-defined) Attack mode
- **Notes:** No canon voice command exists for modes (MASTER_PROMPT only says "команды режимов (атака/барьер)"). Phrases are two-word on purpose so that "attack" / "атака" alone never fires.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 攻撃モード | `M` | `kogeki modo` |
|  | こうげきモード | `M` | `kogekimodo` |
| Romaji | Kogeki modo | `M` | `kogeki modo` |
| English sub | Attack mode | `M` | `atack mode` |
|  | Senbonzakura attack mode | `M` | `senbonsakura atack mode` |
| English dub | n/a (mod-defined, no dub exists) | - | - |
| Russian (Cyrillic) | Режим атаки | `M` | `resim ataki` |
|  | Сенбонзакура режим атаки | `M` | `senbonsakura resim ataki` |

### 1.9 `byakuya.shikai.mode_barrier`

- **Required state:** SHIKAI. **Item:** Byakuya's zanpakuto in the main hand.
- **Canonical phrase:** (mod-defined) Barrier mode
- **Notes:** ART_BIBLE: barrier/dome mode, radius 3. Two-word phrases only; "barrier" and "барьер" alone never fire.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 防御モード | `M` | `bogyo modo` |
|  | ぼうぎょモード | `M` | `bogyomodo` |
|  | 結界モード | `M` | `kekai modo` |
| Romaji | Bogyo modo | `M` | `bogyo modo` |
| English sub | Barrier mode | `M` | `barier mode` |
|  | Dome mode | `M` | `dome mode` |
|  | Senbonzakura barrier mode | `M` | `senbonsakura barier mode` |
| English dub | n/a (mod-defined, no dub exists) | - | - |
| Russian (Cyrillic) | Режим барьера | `M` | `resim barera` |
|  | Режим купола | `M` | `resim kupora` |
|  | Сенбонзакура режим барьера | `M` | `senbonsakura resim barera` |

### 1.10 `byakuya.bankai.release`

- **Required state:** SHIKAI. **Item:** Byakuya's zanpakuto in the main hand. **Server also requires full reiatsu.**
- **Canonical phrase:** Bankai, Senbonzakura Kageyoshi
- **Notes:** Gate A decision 8: trigger is "Bankai" optionally followed by "Senbonzakura Kageyoshi"; "Chire" is never a bankai trigger. 景厳 is read Kageyoshi (Fandom, Wikipedia); ja.wikipedia romanises it "Kagemitsu", treated as an error in research. English literal names ("Vibrant Display of a Thousand Cherry Blossoms") are intentionally not accepted: nobody says them and "cherry blossoms" is ordinary speech.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 卍解 千本桜景厳 | `C` | `bankai senbonsakura kageosi` |
|  | ばんかい せんぼんざくら かげよし | `C` | `bankai senbonsakura kageosi` |
|  | 卍解 | `C` | `bankai` |
|  | 景厳 | `C` | `kageosi` |
| Romaji | Bankai, Senbonzakura Kageyoshi | `C` | `bankai senbonsakura kageosi` |
|  | Bankai | `C` | `bankai` |
|  | Senbonzakura Kageyoshi | `C` | `senbonsakura kageosi` |
|  | Kageyoshi | `C` | `kageosi` |
| English sub | (none) | - | - |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Банкай, Сэнбонзакура Кагэёси | `X` | `bankai senbonsakura kageosi` |
|  | Банкай, Сенбонзакура Кагеёши | `X` | `bankai senbonsakura kageosi` |
|  | Банкай | `X` | `bankai` |
|  | Кагэёси | `X` | `kageosi` |

### 1.11 `byakuya.bankai.scatter`

- **Required state:** BANKAI. **Item:** Byakuya's zanpakuto in the main hand.
- **Canonical phrase:** Chire (same word as shikai) / mod: Petal storm
- **Notes:** Research: the bankai has no separate release word; the shikai word "Chire" is also chanted in bankai (ja.wikipedia). ART_BIBLE 2.5: "Command (spoken Chire in lore)". The state (BANKAI) is what separates it from the shikai release. Mod aliases for the petal storm are added because "Chire" is hard to say reliably.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 散れ、千本桜 | `C` | `cire senbonsakura` |
|  | 桜吹雪 | `M` | `sakura hubuki` |
|  | さくらふぶき | `M` | `sakurahubuki` |
|  | 散れ (weak: whole utterance) | `C` | `cire` |
|  | ちれ (weak: whole utterance) | `C` | `cire` |
| Romaji | Chire, Senbonzakura | `C` | `cire senbonsakura` |
|  | Sakura fubuki | `M` | `sakura hubuki` |
|  | Chire (weak: whole utterance) | `C` | `cire` |
| English sub | Scatter, Senbonzakura | `T` | `scater senbonsakura` |
|  | Petal storm | `M` | `petar storm` |
|  | Scatter (weak: whole utterance) | `T` | `scater` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Чире, Сэнбонзакура | `X` | `cire senbonsakura` |
|  | Шторм лепестков | `M` | `storm repestkov` |
|  | Лепестковый шторм | `M` | `repestkovi storm` |
|  | Буря лепестков | `M` | `burya repestkov` |
|  | Чире (weak: whole utterance) | `X` | `cire` |
|  | Чирэ (weak: whole utterance) | `X` | `cire` |

### 1.12 `byakuya.bankai.hakuteiken`

- **Required state:** BANKAI. **Item:** Byakuya's zanpakuto in the main hand.
- **Canonical phrase:** Shukei: Hakuteiken
- **Notes:** Wiki translation "Endscape: White Emperor Sword"; Wikipedia "Final Scape"; VIZ "Last Sight: White Emperor Sword". "Hakuteiken" alone is canon-accepted (research: the name part is also used alone).

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 終景・白帝剣 | `C` | `suke hakuteken` |
|  | しゅうけい はくていけん | `C` | `suke hakuteken` |
|  | 白帝剣 | `C` | `hakuteken` |
| Romaji | Shukei, Hakuteiken | `C` | `suke hakuteken` |
|  | Hakuteiken | `C` | `hakuteken` |
| English sub | Endscape, White Emperor Sword | `T` | `endscape white enperor sword` |
|  | Last Sight, White Emperor Sword | `T` | `rast sight white enperor sword` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Сюкэй, Хакутэйкэн | `X` | `suke hakuteken` |
|  | Сюкэй Хакутэкэн | `X` | `suke hakuteken` |
|  | Хакутэйкэн | `X` | `hakuteken` |

### 1.13 `byakuya.bankai.senkei`

- **Required state:** BANKAI. **Item:** Byakuya's zanpakuto in the main hand. **Optional command.**
- **Canonical phrase:** Senkei
- **Notes:** Wiki: "Slaughterscape". Optional command. Key folds to 5 letters ("senke"), so it is exact-match only.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 殲景 | `C` | `senke` |
|  | せんけい | `C` | `senke` |
| Romaji | Senkei | `C` | `senke` |
| English sub | Slaughterscape | `T` | `sraughterscape` |
| English dub | `unverified` (no source in research; not invented) | - | - |
| Russian (Cyrillic) | Сэнкэй | `X` | `senke` |
|  | Сенкей | `X` | `senke` |

### 1.14 `common.seal`

- **Required state:** SHIKAI or BANKAI. **Item:** either zanpakuto in the main hand.
- **Canonical phrase:** (mod-defined) Seal
- **Notes:** MASTER_PROMPT: "запечатать" (seal). No canon phrase. 納刀 (nōtō, sheathing) and 戻れ (modore, "return") are natural Japanese equivalents chosen by the mod.

| Group | Phrase | Origin | Key form |
|---|---|---|---|
| Japanese script (ja-JP ASR form) | 封印 | `M` | `huin` |
|  | ふういん | `M` | `huin` |
|  | 納刀 | `M` | `noto` |
|  | 戻れ | `M` | `modore` |
| Romaji | Fuin | `M` | `huin` |
| English sub | Seal | `M` | `sear` |
|  | Seal it | `M` | `sear it` |
|  | Seal sword | `M` | `sear sword` |
| English dub | n/a (mod-defined, no dub exists) | - | - |
| Russian (Cyrillic) | Запечатать | `M` | `sapecatat` |
|  | Запечатай | `M` | `sapecatai` |
|  | Печать | `M` | `pecat` |
|  | Запечатать меч | `M` | `sapecatat mec` |

## 2. Typical ASR mis-recognitions

These rows seed the fixture generator: each is a realistic raw string the Web Speech API could return for that command. The lists are my best expectation of how each language model behaves, **not recordings**; they must be replaced or extended by real captures from the bridge page (see 5.7).

Tier column: `F` = the fuzzy matcher of section 5 reaches this string using only canon/mod/transcription variants (no alias needed); `A` = the string is too far for the fuzzy rules and must be added to the variant table as an alias (origin `A`). The Java variant table = section 1 rows + every tier-A row below. Every row below is a **positive** test case (expected: the command fires in the right state).

### 2.1 `rukia.shikai.release`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | my sode no shirayuki | `my sode no sirayuki` | F |
| en-US | may so day no sheer a you key | `may so day no ser a yo ke` | A |
| en-US | mine sode no shira yuki | `mine sode no sira yuki` | F |
| en-US | mae sodenoshirayuki | `mae sodenosirayuki` | F |
| en-US | my so the no sheer ayuki | `my so the no ser ayuki` | F |
| ru-RU | маэ содэ но сираюки | `mae sode no sirayuki` | F |
| ru-RU | мае сода но сирайки | `mae soda no siraiki` | A |
| ru-RU | май сода но сира юки | `mai soda no sira yuki` | F |
| ru-RU | мэй сёдэ но ширайюки | `me sode no sirayuki` | F |
| ja-JP | 前袖白雪 | `sode no sirayuki` | F |
| ja-JP | 舞え袖の白雪 | `mae sode no sirayuki` | F |

### 2.2 `rukia.shikai.tsukishiro`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | some no my tsukishiro | `some no my cukisiro` | F |
| en-US | sumo no my tsukishiro | `sumo no my cukisiro` | F |
| en-US | so me no my sue key she row | `so me no my sue ke se row` | A |
| en-US | sue key she row | `sue ke se row` | A |
| en-US | ski shiro | `ski siro` | A |
| en-US | tsu ki shiro | `cu ki siro` | A |
| ru-RU | сомэ но май цукиширо | `some no mai cukisiro` | F |
| ru-RU | сома но май цукисиро | `soma no mai cukisiro` | F |
| ru-RU | цуки сиро | `cuki siro` | F |
| ja-JP | 染めの舞月白 | `some no mai cukisiro` | F |
| ja-JP | 初の舞 月白 | `some no mai cukisiro` | F |

### 2.3 `rukia.shikai.hakuren`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | tsugi no my hakuren | `cugi no my hakuren` | F |
| en-US | sue gee no my hakuren | `sue ge no my hakuren` | F |
| en-US | tsu gi no my hack you wren | `cu gi no my hack yo wren` | A |
| en-US | haku wren | `haku wren` | F |
| en-US | haku ren | `haku ren` | F |
| en-US | hockey wren | `hocke wren` | A |
| ru-RU | цуги но май хакурэн | `cugi no mai hakuren` | F |
| ru-RU | цугино май хакурен | `cugino mai hakuren` | F |
| ru-RU | цуги но май хакурен | `cugi no mai hakuren` | F |
| ja-JP | 次の舞白蓮 | `cugi no mai hakuren` | F |
| ja-JP | 次の舞 白漣 | `cugi no mai hakuren` | F |

### 2.4 `rukia.shikai.shirafune`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | san no my shirafune | `san no my sirahune` | F |
| en-US | son no my sheer a foo nay | `son no my ser a ho nay` | A |
| en-US | sun no my shira fune | `sun no my sira hune` | F |
| en-US | shira fune | `sira hune` | F |
| en-US | sheer of newn | `ser oh newn` | A |
| ru-RU | сан но май сирафунэ | `san no mai sirahune` | F |
| ru-RU | сан но май ширафуне | `san no mai sirahune` | F |
| ru-RU | сан но май сира фунэ | `san no mai sira hune` | F |
| ja-JP | 三の舞 白刀 | `san no mai sirahune` | F |
| ja-JP | 参の舞白刀 | `san no mai sirahune` | F |

### 2.5 `rukia.bankai.release`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | bunkai hakka no togame | `bunkai haka no togame` | F |
| en-US | bonkai hakka no togame | `bonkai haka no togame` | F |
| en-US | bank eye hakka no toga may | `bank e haka no toga may` | F |
| en-US | bankai hacker no togame | `bankai hacker no togame` | F |
| en-US | bankai hawk a no toe gah may | `bankai hawk a no toe gah may` | F |
| en-US | bunkai | `bunkai` | A |
| en-US | bonkai | `bonkai` | A |
| ru-RU | банкай хакка но тогамэ | `bankai haka no togame` | F |
| ru-RU | бункай хака но тогаме | `bunkai haka no togame` | F |
| ru-RU | банкай хакка но тогомэ | `bankai haka no togome` | F |
| ru-RU | бонкай | `bonkai` | A |
| ja-JP | 万海 白霞の咎め | `bankai haka no togame` | F |
| ja-JP | 挽回 白霞の咎め | `bankai haka no togame` | F |

### 2.6 `rukia.bankai.absolute_zero`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | absolute 0 | `absorute 0` | A |
| en-US | absolutely zero | `absorutery sero` | F |
| en-US | apsolute zero | `apsorute sero` | F |
| en-US | absolute zeal | `absorute sear` | A |
| ru-RU | абсолютный ноль | `absoryutni nor` | F |
| ru-RU | абсолютный нуль | `absoryutni nur` | F |
| ru-RU | абсолютного нуля | `absoryutnogo nurya` | A |
| ru-RU | абсолютный нол | `absoryutni nor` | F |
| ja-JP | 絶対零度 | `setai redo` | F |
| ja-JP | ぜったいれいど | `setairedo` | F |

### 2.7 `byakuya.shikai.release`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | cheery senbonzakura | `cery senbonsakura` | A |
| en-US | chiri senbonzakura | `ciri senbonsakura` | A |
| en-US | chili senbonzakura | `ciri senbonsakura` | A |
| en-US | cherry send bon zakura | `cery send bon sakura` | A |
| en-US | chee ray senbonzakura | `ce ray senbonsakura` | F |
| en-US | she re senbonzakura | `se re senbonsakura` | A |
| ru-RU | чире сэнбонзакура | `cire senbonsakura` | F |
| ru-RU | чири сенбонзакура | `ciri senbonsakura` | A |
| ru-RU | тире сенбонзакура | `tire senbonsakura` | A |
| ru-RU | чирэ сенбон закура | `cire senbon sakura` | F |
| ja-JP | 散れ千本桜 | `cire senbonsakura` | F |
| ja-JP | 知れ千本桜 | `cire senbonsakura` | F |
| ja-JP | チレ センボンザクラ | `cire senbonsakura` | F |

### 2.8 `byakuya.shikai.mode_attack`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | attack mood | `atack mod` | A |
| en-US | a tack mode | `a tack mode` | F |
| en-US | attack mod | `atack mod` | A |
| en-US | attic mode | `atic mode` | A |
| en-US | senbonzakura attack mode | `senbonsakura atack mode` | F |
| ru-RU | режим атаки | `resim ataki` | F |
| ru-RU | режим атака | `resim ataka` | F |
| ru-RU | реджим атаки | `resim ataki` | F |
| ru-RU | режим а так и | `resim a tak i` | F |
| ja-JP | 攻撃モード | `kogeki modo` | F |
| ja-JP | こうげき モード | `kogeki modo` | F |

### 2.9 `byakuya.shikai.mode_barrier`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | barrier mood | `barier mod` | A |
| en-US | barrio mode | `bario mode` | A |
| en-US | berry er mode | `bery er mode` | A |
| en-US | dome mood | `dome mod` | A |
| ru-RU | режим барьер | `resim barer` | F |
| ru-RU | режим барьера | `resim barera` | F |
| ru-RU | режим купала | `resim kupara` | F |
| ru-RU | режим купола | `resim kupora` | F |
| ja-JP | 防御モード | `bogyo modo` | F |
| ja-JP | ぼうぎょ モード | `bogyo modo` | F |

### 2.10 `byakuya.bankai.release`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | bunkai | `bunkai` | A |
| en-US | bonkai | `bonkai` | A |
| en-US | bunkai senbonzakura kageyoshi | `bunkai senbonsakura kageosi` | F |
| en-US | bankai senbonzakura kagayoshi | `bankai senbonsakura kagayosi` | F |
| en-US | bankai senbonzakura kaga yoshi | `bankai senbonsakura kaga yosi` | F |
| en-US | buy kai | `buy kai` | A |
| ru-RU | банкай | `bankai` | F |
| ru-RU | бункай | `bunkai` | A |
| ru-RU | банкай сенбонзакура кагеёши | `bankai senbonsakura kageosi` | F |
| ru-RU | банкай сэнбонзакура кагэёси | `bankai senbonsakura kageosi` | F |
| ja-JP | 卍解 | `bankai` | F |
| ja-JP | 卍解 千本桜景厳 | `bankai senbonsakura kageosi` | F |
| ja-JP | 万海 千本桜影吉 | `bankai senbonsakura kageosi` | F |
| ja-JP | ばんかい | `bankai` | F |

### 2.11 `byakuya.bankai.scatter`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | cheery senbonzakura | `cery senbonsakura` | A |
| en-US | chiri senbonzakura | `ciri senbonsakura` | A |
| en-US | pedal storm | `pedar storm` | F |
| en-US | petal stom | `petar stom` | F |
| en-US | petal storms | `petar storms` | F |
| ru-RU | чире сэнбонзакура | `cire senbonsakura` | F |
| ru-RU | шторм лепестки | `storm repestki` | F |
| ru-RU | лепестковый шторм | `repestkovi storm` | F |
| ru-RU | буря лепестков | `burya repestkov` | F |
| ja-JP | 散れ | `cire` | F |
| ja-JP | 桜吹雪 | `sakura hubuki` | F |
| ja-JP | さくらふぶき | `sakurahubuki` | F |

### 2.12 `byakuya.bankai.hakuteiken`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | shukei hakuteiken | `suke hakuteken` | F |
| en-US | shoe kay hakuteiken | `soe kay hakuteken` | F |
| en-US | hakutei ken | `hakute ken` | F |
| en-US | haku tay ken | `haku tay ken` | A |
| en-US | hockey tay ken | `hocke tay ken` | A |
| en-US | hakutaken | `hakutaken` | F |
| ru-RU | сюкэй хакутэйкэн | `suke hakuteken` | F |
| ru-RU | сюкей хакутейкен | `suke hakuteken` | F |
| ru-RU | шукей хакутейкен | `suke hakuteken` | F |
| ru-RU | хакутэкэн | `hakuteken` | F |
| ja-JP | 終景白帝剣 | `suke hakuteken` | F |
| ja-JP | 白帝剣 | `hakuteken` | F |
| ja-JP | しゅうけい はくていけん | `suke hakuteken` | F |

### 2.13 `byakuya.bankai.senkei`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | sen kay | `sen kay` | A |
| en-US | sank ay | `sank ay` | A |
| en-US | send kay | `send kay` | A |
| en-US | sen kei | `sen ke` | F |
| ru-RU | сэнкэй | `senke` | F |
| ru-RU | сенкей | `senke` | F |
| ru-RU | сенки | `senki` | A |
| ja-JP | 殲景 | `senke` | F |
| ja-JP | せんけい | `senke` | F |

### 2.14 `common.seal`

| lang | ASR output | key form | tier |
|---|---|---|---|
| en-US | seal | `sear` | F |
| en-US | seal the sword | `sear the sword` | A |
| en-US | sill | `sir` | A |
| en-US | zeal | `sear` | F |
| ru-RU | запечатать | `sapecatat` | F |
| ru-RU | запечатай | `sapecatai` | F |
| ru-RU | запечатать меч | `sapecatat mec` | F |
| ru-RU | печать | `pecat` | F |
| ru-RU | запечатать клинок | `sapecatat krinok` | A |
| ja-JP | 封印 | `huin` | F |
| ja-JP | ふういん | `huin` | F |
| ja-JP | 納刀 | `noto` | F |

### 2.99 Why these errors happen (per language model)

- **en-US:** "mae" is heard as my / may / mine / mei (all very common words); "shirayuki" is split into "sheer a you key" or "shira yuki"; "sode" becomes "so day" / "so the" / "sold". Dance names get ordinary-word replacements ("some" to "sumo", "san" to "son" / "sun", "no mai" to "no my"). "Bankai" is the most unstable word: bunkai (also a real karate term), bonkai, "buy kai", "bank I". "Chire" collapses into cheery / cherry / chili / chiri.
- **ru-RU:** The Russian model writes everything in Cyrillic phonetically, so spellings vary mainly in vowels: э/е, о/а in unstressed syllables (содэ/сода, сираюки/сирайки), и/ы, double consonants (хакка/хака), ю/ай (сираюки/сирайки). "Маэ" becomes май / мае / мэй. A trailing "й" is often added (кагэй). Real words intrude: "тире" for чире, "печать" for seal.
- **ja-JP:** Kana names come out as kanji: 袖白雪 (sometimes with の), 舞え as 前 / 舞へ, 初の舞 as 染めの舞, 白漣 as 白蓮, 参の舞 as 三の舞, 白霞罸 as 白霞の咎め, 散れ as 知れ, 景厳 as 景義 / 影吉, ばんかい as 万海 / 挽回 (a real word, "recovery"). Katakana spelling of names (センボンザクラ) is also common. The kanji lookup table in 5.1 covers all of these.

Totals: 160 ASR rows, 115 tier F (fuzzy reaches them), 45 tier A (alias required). With aliases loaded the recognition rate on this seed set is 100 percent by construction; the honest figure for the fuzzy matcher alone is 72 percent. Acceptance criterion in MASTER_PROMPT section 6 (at least 95 percent on the fixture) is therefore only meaningful after real captures are added.

## 3. Partial-phrase and ambiguity rules

### 3.1 General rules

1. **Window match, not full-string match.** The matcher looks for the variant inside the utterance (extra words before or after are allowed) for every **strong** variant. "Okay now Sode no Shirayuki please" fires.
2. **Weak variants need a whole-utterance match.** The utterance, after removing the filler words `please, now, пожалуйста, давай, ну, сейчас, お願い`, must equal the variant. Applies to every variant of `common.seal` and to the lone-word variants "Chire", "Scatter", "Чире", "散れ", "ちれ". Reason: these are short or common words ("seal", "scatter", "печать").
3. **Names may be said without the prefix.** The ability name alone is enough: `Hakuren`, `Tsukishiro`, `Shirafune`, `Hakuteiken`, `Hakka no Togame`, `Kageyoshi`. The prefix alone ("Tsugi no mai", "Some no mai", "Mae", "Shukei", "Sode", "Senbonzakura") is **never** enough.
4. **State/item gating happens before scoring**; a phrase for another state is not an error, it is simply ignored. This is what separates "Chire" (shikai release in SEALED, petal storm in BANKAI, ignored in SHIKAI) and "Bankai" (release in SHIKAI, ignored in SEALED and BANKAI).
5. **Cross-language tolerance.** All variants of all languages are matched regardless of the `lang` field (people mix: an en-US session will hear Cyrillic-like romaji). `lang` is only logged.
6. **Margin rule.** If two different commands of the gated set both pass, the higher wins only if it leads by at least 0.03; otherwise nothing fires (log "ambiguous"). With the current table this never happens inside one state (checked), so it is a safety net for future edits.
7. **Same-utterance chaining is blocked** (consumed-text rule, 5.5): "Bankai, Hakka no Togame" fires bankai release once; the trailing name must not then fire anything in the new BANKAI state.

### 3.2 Minimum acceptable phrase per command

| id | minimum phrase that fires | must NOT fire |
|---|---|---|
| `rukia.shikai.release` | Any of: `Sode no Shirayuki`, `Shirayuki`, `Mae Sode no Shirayuki`, 袖白雪, Cyrillic equivalents. | "Mae" alone, "Sode" alone, "Dance" alone. |
| `rukia.shikai.tsukishiro` | `Tsukishiro` (alone) or `Some no mai Tsukishiro`; English `First dance, moon white`. | "Some no mai" alone, "first dance" alone. |
| `rukia.shikai.hakuren` | `Hakuren` (alone) or `Tsugi no mai Hakuren`; English `Next dance, white ripple`. | "Tsugi no mai" alone, "next dance" alone. |
| `rukia.shikai.shirafune` | `Shirafune` (alone) or `San no mai Shirafune`; English `Third dance, white sword`. | "San no mai" alone, "third dance" alone. |
| `rukia.bankai.release` | `Bankai` (alone) or `Hakka no Togame` (alone) or both. English `Censure of the White Haze` accepted. | "Hakka" alone, "togame" alone. |
| `rukia.bankai.absolute_zero` | `Absolute zero` / `Абсолютный ноль` / 絶対零度 (full phrase). | "zero" alone, "absolute" alone, "ноль" alone. `Hakka no Togame` and `Bankai` are **ignored** in BANKAI state. |
| `byakuya.shikai.release` | `Chire Senbonzakura` (any ASR spelling) **or** the lone word `Chire` / `Scatter` / `Чире` / 散れ as a whole utterance. | "Senbonzakura" alone, "Bankai", "cherry blossoms". |
| `byakuya.shikai.mode_attack` | `Attack mode` / `Режим атаки` / 攻撃モード (two words). | "attack" alone, "атака" alone. |
| `byakuya.shikai.mode_barrier` | `Barrier mode` / `Dome mode` / `Режим барьера` / `Режим купола` / 防御モード. | "barrier" alone, "dome" alone, "барьер" alone. |
| `byakuya.bankai.release` | `Bankai` alone, `Kageyoshi` alone, or `Senbonzakura Kageyoshi` (with or without "Bankai"). In SHIKAI state only. | **"Senbonzakura" alone must NOT trigger.** "Chire" must NOT trigger (Gate A 8). |
| `byakuya.bankai.scatter` | `Chire` (whole utterance), `Chire Senbonzakura`, `Petal storm` / `Шторм лепестков` / 桜吹雪. | "Senbonzakura" alone, "Bankai" (ignored in BANKAI). |
| `byakuya.bankai.hakuteiken` | `Hakuteiken` alone, or `Shukei Hakuteiken`. | "Shukei" alone (and "Senkei" must not be mistaken for it), "emperor", "sword". |
| `byakuya.bankai.senkei` | `Senkei` / `Сэнкэй` / 殲景 (exact key `senke`, plus aliases). | "sen" alone, "senbonzakura" alone. |
| `common.seal` | Whole utterance: `Seal`, `Seal it`, `Seal sword`, `Запечатать`, `Запечатай`, `Печать`, `Запечатать меч`, 封印, 納刀, 戻れ. | Any longer sentence containing the word ("the seal is broken", "сними печать с письма"). |

### 3.3 Gating and ambiguity matrix (expected results, usable as test cases)

Computed with the prototype: `text` is fed to the matcher with the given state and held zanpakuto; "none" = nothing fires. Rows marked with `*` are the key ambiguity rules.

| text | state | item in hand | expected |
|---|---|---|---|
| senbonzakura | SHIKAI | byakuya | none * |
| まえそでのしらゆき | SEALED | rukia | `rukia.shikai.release` |
| そめのまいつきしろ | SHIKAI | rukia | `rukia.shikai.tsukishiro` |
| ばんかいせんぼんざくらかげよし | SHIKAI | byakuya | `byakuya.bankai.release` |
| senbonzakura | SEALED | byakuya | none * |
| senbonzakura | BANKAI | byakuya | none |
| сенбонзакура | SHIKAI | byakuya | none |
| 千本桜 | SHIKAI | byakuya | none |
| bankai | SHIKAI | byakuya | `byakuya.bankai.release` |
| bankai senbonzakura | SHIKAI | byakuya | `byakuya.bankai.release` |
| senbonzakura kageyoshi | SHIKAI | byakuya | `byakuya.bankai.release` |
| kageyoshi | SHIKAI | byakuya | `byakuya.bankai.release` |
| bankai | SEALED | byakuya | none * |
| bankai | BANKAI | byakuya | none * |
| chire | SHIKAI | byakuya | none * |
| chire | SEALED | byakuya | `byakuya.shikai.release` * |
| chire senbonzakura | BANKAI | byakuya | `byakuya.bankai.scatter` |
| chire | BANKAI | byakuya | `byakuya.bankai.scatter` * |
| scatter | SEALED | byakuya | `byakuya.shikai.release` |
| scatter | BANKAI | byakuya | `byakuya.bankai.scatter` |
| please scatter | SEALED | byakuya | `byakuya.shikai.release` |
| scatter the seeds | SEALED | byakuya | none * |
| shirayuki | SEALED | rukia | `rukia.shikai.release` |
| mae | SEALED | rukia | none |
| sode | SEALED | rukia | none |
| shirayuki | SHIKAI | rukia | none |
| hakka no togame | SHIKAI | rukia | `rukia.bankai.release` * |
| bankai hakka no togame | SHIKAI | rukia | `rukia.bankai.release` |
| bankai hakka no togame | BANKAI | rukia | none * |
| bankai | BANKAI | rukia | none |
| tsukishiro | SEALED | rukia | none |
| tsukishiro | SHIKAI | rukia | `rukia.shikai.tsukishiro` |
| some no mai | SHIKAI | rukia | none |
| hakuren | SHIKAI | rukia | `rukia.shikai.hakuren` |
| shirafune | SHIKAI | rukia | `rukia.shikai.shirafune` |
| absolute zero | BANKAI | rukia | `rukia.bankai.absolute_zero` |
| absolute zero | SHIKAI | rukia | none |
| chire senbonzakura | SEALED | rukia | none |
| bankai | SHIKAI | rukia | `rukia.bankai.release` |
| hakuteiken | SHIKAI | byakuya | none |
| hakuteiken | BANKAI | byakuya | `byakuya.bankai.hakuteiken` |
| shukei | BANKAI | byakuya | none |
| senkei | BANKAI | byakuya | `byakuya.bankai.senkei` |
| senkei | SHIKAI | byakuya | none |
| seal | SHIKAI | rukia | `common.seal` |
| seal | SEALED | rukia | none |
| seal the sword | BANKAI | byakuya | `common.seal` |
| i will seal the deal | SHIKAI | byakuya | none |
| печать | SHIKAI | rukia | `common.seal` |
| запечатать | BANKAI | byakuya | `common.seal` |
| сними печать с письма | SHIKAI | rukia | none |
| attack | SHIKAI | byakuya | none |
| attack mode | SHIKAI | byakuya | `byakuya.shikai.mode_attack` |
| режим атаки | SHIKAI | byakuya | `byakuya.shikai.mode_attack` |
| барьер | SHIKAI | byakuya | none |
| режим купола | SHIKAI | byakuya | `byakuya.shikai.mode_barrier` |
| barrier | SHIKAI | byakuya | none |
| bank eye | SHIKAI | byakuya | none * |
| bank eye | SHIKAI | rukia | none |
| банка | SHIKAI | rukia | none * |
| banzai | SHIKAI | byakuya | none |
| bonsai | SHIKAI | byakuya | none |
| bunkai | SHIKAI | byakuya | `byakuya.bankai.release` |
| cherry | SEALED | byakuya | none |
| cherry blossoms are pretty | SEALED | byakuya | none |
| cheery senbonzakura | SEALED | byakuya | `byakuya.shikai.release` |
| first dance moon white | SHIKAI | rukia | `rukia.shikai.tsukishiro` |
| first dance | SHIKAI | rukia | none |
| next dance | SHIKAI | rukia | none |
| dance sode no shirayuki | SEALED | rukia | `rukia.shikai.release` |
| sode no shirayuki | SHIKAI | rukia | none |
| okay now sode no shirayuki please | SEALED | rukia | `rukia.shikai.release` |
| mae sode no shirayuki | SEALED | rukia | `rukia.shikai.release` |
| dance | SEALED | rukia | none |
| some no mai tsukishiro | SHIKAI | rukia | `rukia.shikai.tsukishiro` |
| tsugi no mai | SHIKAI | rukia | none |
| tsugi no mai hakuren | SHIKAI | rukia | `rukia.shikai.hakuren` |
| san no mai | SHIKAI | rukia | none |
| san no mai shirafune | SHIKAI | rukia | `rukia.shikai.shirafune` |
| hakka | SHIKAI | rukia | none |
| togame | SHIKAI | rukia | none |
| censure of the white haze | SHIKAI | rukia | `rukia.bankai.release` |
| hakka no togame | BANKAI | rukia | none |
| zero | BANKAI | rukia | none |
| absolute | BANKAI | rukia | none |
| ноль | BANKAI | rukia | none |
| абсолютный ноль | BANKAI | rukia | `rukia.bankai.absolute_zero` |
| zettai reido | BANKAI | rukia | `rukia.bankai.absolute_zero` |
| chire | SHIKAI | rukia | none |
| атака | SHIKAI | byakuya | none |
| dome | SHIKAI | byakuya | none |
| dome mode | SHIKAI | byakuya | `byakuya.shikai.mode_barrier` |
| 防御モード | SHIKAI | byakuya | `byakuya.shikai.mode_barrier` |
| 攻撃モード | SHIKAI | byakuya | `byakuya.shikai.mode_attack` |
| attack mode | SHIKAI | rukia | none |
| шторм лепестков | BANKAI | byakuya | `byakuya.bankai.scatter` |
| petal storm | BANKAI | byakuya | `byakuya.bankai.scatter` |
| petal storm | SHIKAI | byakuya | none |
| shukei hakuteiken | BANKAI | byakuya | `byakuya.bankai.hakuteiken` |
| сюкэй хакутэйкэн | BANKAI | byakuya | `byakuya.bankai.hakuteiken` |
| emperor | BANKAI | byakuya | none |
| sword | BANKAI | byakuya | none |
| sen | BANKAI | byakuya | none |
| seal it | SHIKAI | byakuya | `common.seal` |
| 封印 | BANKAI | rukia | `common.seal` |
| запечатать меч | SHIKAI | byakuya | `common.seal` |
| chire | SEALED | byakuya | `byakuya.shikai.release` * |
| 千本桜景厳 | SHIKAI | byakuya | `byakuya.bankai.release` |

### 3.4 Special cases decided here

- **"Bank eye"** is listed as a neutral near-miss in the brief and is kept neutral. It is, however, one of the most likely en-US renderings of "bankai". With the rules above it does not fire (compact key `banke`: shorter keys must match a variant exactly). If field captures show it is the dominant rendering, add it as a tier-A alias for `*.bankai.release` (the state gate then limits the damage to SHIKAI with the item in hand). Decision needed in ADR.
- **"Banzai", "bonsai", "банка", "банзай"** differ from "bankai" by one letter or one letter missing. Short keys (6 letters or less) are therefore exact-match only; Jaro-Winkler alone would score "банка" at 0.967 and "banzai" at 0.92, which is why a plain threshold is not enough (see 5.2).
- **"Hakka no Togame" in BANKAI state** is ignored on purpose. Otherwise the second half of "Bankai, Hakka no Togame" could re-fire as a different command if the first half fired from an interim result.
- **"Chire" collisions:** English "cherry" (key `cery`) and Russian "чирей" (boil, key `cire`) are the only real words close to the key. "Cherry" does not fire; "чирей" fires as a lone word in SEALED (Byakuya) and BANKAI. Accepted as a known, very rare collision.
- **"Absolute zero"** is also ordinary physics talk. It only matters when Rukia holds her bankai sword in BANKAI state, so no further rule is applied.
- **Rukia vs Byakuya "Bankai":** the same word triggers whichever character the held item belongs to. Nothing to disambiguate.

## 4. Neutral phrases (must never trigger)

89 phrases (41 en-US, 39 ru-RU, 9 ja-JP). The test feeds each one to **every** command with **no** state or item gating (strictest case) and expects zero matches. "Nearest" is the best raw similarity to any variant before thresholds, as a margin diagnostic; it is informational, the pass/fail condition is "nothing fires".

| # | lang | phrase | key | nearest command | raw JW |
|---|---|---|---|---|---|
| 1 | en-US | bank eye | `bank e` | `rukia.bankai.release` | 0.93 |
| 2 | en-US | my sofa | `my soha` | `rukia.shikai.tsukishiro` | 0.71 |
| 3 | en-US | cherry blossoms are pretty | `cery brosoms are prety` | `rukia.bankai.absolute_zero` | 0.78 |
| 4 | en-US | hack a tome | `hack a tome` | `rukia.bankai.release` | 0.85 |
| 5 | en-US | bonsai tree | `bonsai tre` | `rukia.bankai.release` | 0.80 |
| 6 | en-US | banzai | `bansai` | `rukia.bankai.release` | 0.92 |
| 7 | en-US | I need to go to the bank | `i ned to go to the bank` | `rukia.bankai.release` | 0.93 |
| 8 | en-US | bank the loot in the chest | `bank the rot in the cest` | `rukia.bankai.release` | 0.93 |
| 9 | en-US | sodium is a metal | `sodium is a metar` | `byakuya.shikai.mode_attack` | 0.81 |
| 10 | en-US | sod it | `sod it` | `rukia.shikai.release` | 0.79 |
| 11 | en-US | the seal is broken | `the sear is broken` | `common.seal` | 1.00 |
| 12 | en-US | seal the deal | `sear the dear` | `common.seal` | 1.00 |
| 13 | en-US | sea lion | `sea rion` | `common.seal` | 0.94 |
| 14 | en-US | absolute power | `absorute power` | `rukia.bankai.absolute_zero` | 0.93 |
| 15 | en-US | it is zero degrees | `it is sero degres` | `common.seal` | 0.87 |
| 16 | en-US | attack that zombie | `atack that sonbie` | `byakuya.shikai.mode_attack` | 0.91 |
| 17 | en-US | switch to creative mode | `swic to creative mode` | `common.seal` | 0.92 |
| 18 | en-US | the barrier block is red | `the barier brock is red` | `byakuya.shikai.mode_barrier` | 0.92 |
| 19 | en-US | build a dome house | `buird a dome hose` | `byakuya.shikai.mode_barrier` | 0.90 |
| 20 | en-US | scatter the seeds | `scater the seds` | `byakuya.shikai.release` | 1.00 |
| 21 | en-US | a cherry tomato | `a cery tomato` | `byakuya.shikai.release` | 0.74 |
| 22 | en-US | sherry | `sery` | `common.seal` | 0.87 |
| 23 | en-US | my senior friend | `my senior hriend` | `common.seal` | 0.80 |
| 24 | en-US | snow white | `snow white` | `rukia.shikai.shirafune` | 0.69 |
| 25 | en-US | white snow is falling | `white snow is haring` | `rukia.bankai.release` | 0.86 |
| 26 | en-US | next dance please | `next dance prease` | `rukia.shikai.hakuren` | 0.89 |
| 27 | en-US | thousand blades | `thosand brades` | `byakuya.bankai.scatter` | 0.76 |
| 28 | en-US | the emperor penguin | `the enperor penguin` | `byakuya.bankai.senkei` | 0.68 |
| 29 | en-US | hakuna matata | `hakuna matata` | `rukia.shikai.hakuren` | 0.91 |
| 30 | en-US | send it to me | `send it to me` | `byakuya.bankai.senkei` | 0.85 |
| 31 | en-US | scatter brain | `scater brain` | `byakuya.shikai.release` | 1.00 |
| 32 | en-US | where is my sword | `where is my sword` | `common.seal` | 0.78 |
| 33 | en-US | petals on the ground | `petars on the grond` | `byakuya.bankai.scatter` | 0.92 |
| 34 | en-US | storm is coming | `storm is coming` | `byakuya.bankai.scatter` | 0.87 |
| 35 | en-US | chirp chirp | `cirp cirp` | `byakuya.shikai.release` | 0.88 |
| 36 | en-US | shira is my friend | `sira is my hriend` | `rukia.shikai.release` | 0.90 |
| 37 | en-US | bunker is ready | `bunker is ready` | `common.seal` | 0.77 |
| 38 | en-US | tsunami warning | `cunami warning` | `rukia.shikai.hakuren` | 0.79 |
| 39 | en-US | third dance of the night | `third dance oh the night` | `rukia.shikai.shirafune` | 0.90 |
| 40 | en-US | first dance at the wedding | `hirst dance at the weding` | `rukia.shikai.tsukishiro` | 0.91 |
| 41 | en-US | mine some stone | `mine some stone` | `rukia.shikai.tsukishiro` | 0.85 |
| 42 | ru-RU | банка | `banka` | `rukia.bankai.release` | 0.97 |
| 43 | ru-RU | сирень | `siren` | `rukia.shikai.shirafune` | 0.84 |
| 44 | ru-RU | банка консервов | `banka konservov` | `rukia.bankai.release` | 0.97 |
| 45 | ru-RU | бонсай | `bonsai` | `rukia.bankai.release` | 0.80 |
| 46 | ru-RU | банзай | `bansai` | `rukia.bankai.release` | 0.92 |
| 47 | ru-RU | банкет | `banket` | `rukia.bankai.release` | 0.87 |
| 48 | ru-RU | банковская карта | `bankovskaya karta` | `rukia.bankai.release` | 0.86 |
| 49 | ru-RU | где мой меч | `gde moi mec` | `byakuya.shikai.mode_barrier` | 0.75 |
| 50 | ru-RU | давай копать | `davai kopat` | `rukia.bankai.release` | 0.70 |
| 51 | ru-RU | печать документов | `pecat dokumentov` | `common.seal` | 1.00 |
| 52 | ru-RU | запечатай письмо | `sapecatai pismo` | `common.seal` | 1.00 |
| 53 | ru-RU | сними печать с письма | `snimi pecat s pisma` | `common.seal` | 1.00 |
| 54 | ru-RU | сода | `soda` | `rukia.shikai.release` | 0.83 |
| 55 | ru-RU | содовая | `sodovaya` | `rukia.shikai.release` | 0.81 |
| 56 | ru-RU | широкая река | `sirokaya reka` | `rukia.shikai.release` | 0.84 |
| 57 | ru-RU | белый снег | `beri sneg` | `common.seal` | 0.75 |
| 58 | ru-RU | сакура цветёт | `sakura cvetyot` | `byakuya.bankai.scatter` | 0.90 |
| 59 | ru-RU | тысяча клинков | `tisaca krinkov` | `common.seal` | 0.70 |
| 60 | ru-RU | ноль градусов | `nor gradusov` | `common.seal` | 0.78 |
| 61 | ru-RU | абсолютно ничего | `absoryutno nicego` | `rukia.bankai.absolute_zero` | 0.95 |
| 62 | ru-RU | режим выживания | `resim visivaniya` | `byakuya.shikai.mode_attack` | 0.90 |
| 63 | ru-RU | купол дома | `kupor doma` | `byakuya.shikai.mode_barrier` | 0.80 |
| 64 | ru-RU | барьерный риф | `barerni rih` | `byakuya.shikai.mode_barrier` | 0.83 |
| 65 | ru-RU | хакер | `haker` | `rukia.shikai.hakuren` | 0.89 |
| 66 | ru-RU | хокку | `hoku` | `rukia.shikai.hakuren` | 0.75 |
| 67 | ru-RU | чирик | `cirik` | `byakuya.shikai.release` | 0.85 |
| 68 | ru-RU | тире | `tire` | `byakuya.shikai.release` | 0.83 |
| 69 | ru-RU | сенсей | `sense` | `byakuya.bankai.senkei` | 0.91 |
| 70 | ru-RU | атака зомби | `ataka sonbi` | `byakuya.shikai.mode_attack` | 0.82 |
| 71 | ru-RU | режим креатива | `resim kreativa` | `byakuya.shikai.mode_attack` | 0.91 |
| 72 | ru-RU | лепестки роз | `repestki ros` | `byakuya.bankai.scatter` | 0.91 |
| 73 | ru-RU | шторм идёт | `storm idyot` | `byakuya.bankai.scatter` | 0.87 |
| 74 | ru-RU | май месяц | `mai mesac` | `rukia.shikai.tsukishiro` | 0.73 |
| 75 | ru-RU | мае | `mae` | `rukia.shikai.release` | 0.81 |
| 76 | ru-RU | сан | `san` | `rukia.shikai.shirafune` | 0.81 |
| 77 | ru-RU | следующий танец | `sreduyusi tanec` | `byakuya.bankai.senkei` | 0.72 |
| 78 | ru-RU | хакуна матата | `hakuna matata` | `rukia.shikai.hakuren` | 0.91 |
| 79 | ru-RU | иди сюда | `idi suda` | `byakuya.bankai.hakuteiken` | 0.73 |
| 80 | ru-RU | эй смотри | `e smotri` | `common.seal` | 0.75 |
| 81 | ja-JP | 今日はいい天気ですね | `hai desune` | `rukia.shikai.hakuren` | 0.75 |
| 82 | ja-JP | 桜が綺麗 | `ga` | `rukia.shikai.hakuren` | 0.71 |
| 83 | ja-JP | 銀行に行く | `ni ku` | `rukia.shikai.hakuren` | 0.76 |
| 84 | ja-JP | 雪が降っている | `ga teru` | `byakuya.shikai.release` | 0.78 |
| 85 | ja-JP | ありがとう | `arigato` | `common.seal` | 0.75 |
| 86 | ja-JP | 盆栽 | `` | `` | 0.00 |
| 87 | ja-JP | 次の舞台 | `cugi no mai` | `rukia.shikai.hakuren` | 0.91 |
| 88 | ja-JP | 攻撃された | `kogeki sareta` | `byakuya.shikai.mode_attack` | 0.92 |
| 89 | ja-JP | 千本ノック | `noku` | `byakuya.bankai.release` | 0.74 |

Notes: neutral rows 1 to 4 are the examples from the brief ("bank eye", "my sofa", "cherry blossoms are pretty", "hack a tome"). "банка" and "сирень" are also from the brief; "сирень" (lilac) is close to the Russian "сираюки" family. "I need to go to the bank" and "bank the loot in the chest" are common Minecraft speech containing the root "bank". Two "dance" sentences ("next dance please", "first dance at the wedding") test that the ordinal-dance English forms never fire without the name translation.

## 5. Matcher parameters (PROPOSAL, ADR.md is final)

### 5.1 Normalisation (normative for keys)

Apply in this order. Every key in sections 1 and 2 was produced by exactly this pipeline.

1. Unicode NFKC, lowercase.
2. **Kanji lookup:** replace known kanji strings (table below, longest match first) by their romaji tokens surrounded by spaces. Then replace every remaining CJK ideograph (U+4E00 to U+9FFF) by a space. The Java side cannot convert arbitrary kanji, so unknown kanji are simply dropped; the lookup table is part of this contract.
3. **Cyrillic to Latin** (letter by letter): а a, б b, в v, г g, д d, е e, ё yo, ж z, з z, и i, й i (dropped when the next letter is ю я ё е), к k, л l, м m, н n, о o, п p, р r, с s, т t, у u, ф f, х h, ц c, ч c, ш s, щ s, ъ and ь (nothing), ы i, э e, ю yu, я ya.
4. **Kana to Hepburn romaji:** katakana is first converted to hiragana. Standard Hepburn (shi, chi, tsu, fu, ji); small っ doubles the next consonant; ー repeats the previous vowel; ん is n; を is o; は is always "ha"; yoon (きゃ, しゅ, ちょ ...) as Hepburn (kya, shu, cho).
5. NFD, drop combining marks (removes macrons: ō ū ā).
6. Every character outside `a-z 0-9 space` becomes a space (punctuation, quotes, 「」, 、。, hyphens, apostrophes).
7. **Digraph folds**, in this order: `tch`→`c`, `ts`→`c`, `ch`→`c`, `sh`→`s`, `zh`→`z`, `dz`→`z`, `kh`→`h`, `ph`→`f`, `j`→`z`, `q`→`k`.
8. **Letter folds:** `l`→`r`, `f`→`h`, `z`→`s`; `m`→`n` before `b` or `p`; drop `y` between `s`/`c` and a vowel (so Russian "сюкэй" and Japanese "しゅうけい" meet); `ou` `oo`→`o`; `uu`→`u`; `ei` `ee` `ey`→`e`; collapse any run of the same letter to one ("hakka" to "haka", "tsukishiro" has none).
9. Collapse whitespace, trim. Tokens = split on space. Compact form = tokens joined without spaces.

Kanji lookup table (contract; extend only by editing this file):

| kanji | romaji tokens | kanji | romaji tokens |
|---|---|---|---|
| 袖の白雪 | `sode no shirayuki` | 白霞の咎め | `hakka no togame` |
| 袖白雪 | `sode no shirayuki` | 白霞の咎 | `hakka no togame` |
| 舞え | `mae` | 絶対零度 | `zettai reido` |
| 舞へ | `mae` | 散れ | `chire` |
| 初の舞 | `some no mai` | 知れ | `chire` |
| 染めの舞 | `some no mai` | 千本桜景厳 | `senbonzakura kageyoshi` |
| 染の舞 | `some no mai` | 千本桜 | `senbonzakura` |
| 月白 | `tsukishiro` | 景厳 | `kageyoshi` |
| 次の舞 | `tsugi no mai` | 景義 | `kageyoshi` |
| 白漣 | `hakuren` | 影吉 | `kageyoshi` |
| 白蓮 | `hakuren` | 攻撃 | `kogeki` |
| 参の舞 | `san no mai` | 防御 | `bogyo` |
| 三の舞 | `san no mai` | 結界 | `kekkai` |
| 白刀 | `shirafune` | 桜吹雪 | `sakura fubuki` |
| 白船 | `shirafune` | 花吹雪 | `hana fubuki` |
| 白舟 | `shirafune` | 終景 | `shukei` |
| 卍解 | `bankai` | 白帝剣 | `hakuteiken` |
| 万解 | `bankai` | 殲景 | `senkei` |
| 万海 | `bankai` | 封印 | `fuin` |
| 万回 | `bankai` | 納刀 | `noto` |
| 挽回 | `bankai` | 戻れ | `modore` |
| 白霞罸 | `hakka no togame` |  | `` |

Spot checks (golden values): 袖白雪 `sode no sirayuki`; 初の舞 月白 `some no mai cukisiro`; 白霞の咎め `haka no togame`; 千本桜景厳 `senbonsakura kageosi`; Сюкэй: Хакутэйкэн and Shūkei: Hakuteiken both give `suke hakuteken`; ちれ and Чире and Chire all give `cire`.

### 5.2 Similarity and thresholds

Per variant, the utterance (token list) is compared through **windows** of consecutive tokens. For a variant with n tokens, window sizes are n-1 (only if n is 3 or more), n and n+1; this tolerates a dropped "no" and a word split in two by the recogniser. The score of a window is Jaro-Winkler (prefix scale 0.1, prefix length 4) between the compact window and the compact variant. The best window gives the variant score; the best variant gives the command score.

A window passes only if **all** of these hold (L = length of the compact variant key):

| compact key length L | minimum JW | maximum Levenshtein (compact) |
|---|---|---|
| 1 to 6 | exact match required | 0 |
| 7 to 9 | 0.90 | 1 |
| 10 to 13 | 0.88 | 2 |
| 14 or more | 0.88 | 3 |

Extra rule when the window has exactly as many tokens as a multi-token variant: every aligned token pair must also be within its own Levenshtein limit (token length 1 to 4: exact; 5 to 7: 1; 8 or more: 2). This is what stops "absolute sheer" or "absolute hero" from passing as "absolute zero" while "sode no shirayuki" with one wrong letter in "shirayuki" still passes.

**Fused tokens (kana and kanji output has no spaces):** for a variant whose compact key has 9 or more letters, a character window of length L-1, L or L+1 is also slid over every utterance token that is long enough. This is why `まえそでのしらゆき` (key `maesodenosirayuki`) matches `mae sode no sirayuki`. It is not applied to short keys, so no short word is ever found inside a longer one.

Why not a plain 0.88 threshold: Jaro-Winkler alone gives "банка" vs "bankai" 0.967 and "banzai" vs "bankai" 0.92, both far above 0.88, and "absolute power" vs "absolute zero" (a one-token drop) 0.98 in an early prototype. The length-adaptive Levenshtein cap and the per-token rule remove these without lowering recall on the long names, where 0.88 is fine. If ADR prefers a single number, 0.88 JW for L of 10 or more and exact below that is the closest simple form.

Prototype results with these rules (for the sanity check only, not a replacement for the Java tests):

- Neutral set of section 4: 89 phrases, 0 false positives (all commands, no gating).
- Combinatorial stress: 1237 strings (about 280 common English and Russian words, two-word pairs, and each prefix word such as "attack", "absolute", "seal", "dance", "sode", "san" combined with 80 common words); the only strings that fire are the intended ones (seal, zeal, scatter, печать, запечатать, запечатай, чире, чирей, "attack mode" and its ASR forms, "absolute zero", "seal sword").
- Section 2 ASR rows: 115 of 160 reached by fuzzy matching alone, 45 need an alias.
- Gating matrix of 3.3: all rows pass.

### 5.3 State and item gating

- The matcher receives a `GateContext { character, state, heldItemCharacter }` from the server-side player state at the moment the utterance arrives (not at the moment speech started). Candidate commands are filtered by section 1 `state` and `item` **before** scoring.
- `reiatsu` (bankai needs a full bar), cooldowns and zone limits are **not** evaluated here. The matcher emits `CommandIntent(id, score, text, lang, final, receivedAtNanos)`; the server validates and replies. A rejected intent still starts the debounce window.
- Latency budget (MASTER_PROMPT: at most 400 ms from the final recognition result to the start of the effect) is measured from `receivedAtNanos` of the final message to the tick where the server applies the state change; the matcher itself should be under 5 ms for the whole table.

### 5.4 Interim versus final results

Proposal: **trigger on final only by default**; make early triggering a config flag (`voice.interimTrigger`, default `false`).

- Why final-only: interim hypotheses change ("bank" then "bankai" then "bank eye"), and a false bankai or Hakuteiken spends reiatsu and plays a long effect. The 400 ms budget in MASTER_PROMPT is defined from the final result, so final-only already meets the stated requirement.
- If the flag is on (to shave the recogniser's end-of-speech wait, which has not been measured yet; measure in phase 5): an interim result may fire a command only if (a) the command is in the early-OK set, (b) the score is at least 0.97, and (c) the same command wins on two consecutive interim messages at least 150 ms apart. Early-OK set: shikai releases, the three Rukia dances, the two Byakuya modes, `common.seal`. **Never early:** both bankai releases, `byakuya.bankai.scatter`, `byakuya.bankai.hakuteiken`, `byakuya.bankai.senkei`, `rukia.bankai.absolute_zero` (expensive or irreversible).
- Weak (whole-utterance) variants are never fired from an interim result (the utterance is not complete yet).
- The bridge must send both interim and final texts (ADR item 4). The matcher ignores interim messages entirely when the flag is off.

### 5.5 Debounce, chaining and consumed text

- **Per command:** 1500 ms after a command id fires (accepted or rejected by the server), the same id cannot fire again. Reason: the Web Speech API often returns the same final twice (and interim then final).
- **Global after a state change:** 600 ms during which no other command fires after `*.shikai.release`, `*.bankai.release` or `common.seal` fired. Reason: avoid executing the tail of the same sentence in the new state.
- **Consumed text:** after a fire, remember the normalised utterance text `U` and the time. For the next 3000 ms any incoming message whose normalised text starts with `U` is stripped of that prefix before matching; an empty remainder is dropped. A new recognition session (bridge reconnect, language switch) clears this.
- Debounce state is per player. Constants live in the mod config; values above are proposals.

### 5.6 Output contract and fixture shape

Matcher result for a message: `{commandId | null, score, matchedVariantKey, reason}` where `reason` is one of `fired`, `no_match`, `gated_out`, `ambiguous`, `debounced`, `interim_ignored`, `weak_not_whole_utterance`.

Fixture case schema for the generator (JSON lines):

```json
{"id":"rukia.shikai.release#en-US#003","lang":"en-US","text":"my sode no shirayuki","final":true,"state":"SEALED","item":"rukia","expect":"rukia.shikai.release","kind":"asr|variant|neutral|gating","tier":"F|A"}
```

Generator contract: for each command, positive cases = every row of section 1 (all variants, plus a version with a leading "okay " / "ну " and a trailing "please" / "пожалуйста" for strong variants) + every row of section 2; target 10 to 15 distinct positive cases per command (the rows of sections 1 and 2 already give at least 15 for every command; the haiku generator adds filler-wrapped copies, and real captures should replace the predicted ASR rows). Negative cases = section 4 (ungated, `expect: null`) + section 3.3 (gated). Gating cases must be generated for each command: every phrase repeated in each wrong state and with the other character's item, `expect: null`.

### 5.7 Open items for the ADR and for the first real captures

- Record 20 utterances per command per language from the bridge page; replace the section 2 guesses by real data; tune the length table in 5.2 only after that.
- Decide the "bank eye" question (3.4).
- Decide whether "Scatter" alone (weak) should be allowed in SEALED for Byakuya; it is a wiki translation, not a dub line.
- STATE_MACHINE.md must expose the held-item to character mapping; the matcher assumes it.

## 6. Unverified and mod-defined content (summary)

**Unverified (no source in `research/`):**

- All English dub names and lines (every command).
- A spoken canon phrase for Rukia's bankai: only the game line is documented (Gate A 9 accepts it).
- A separate canon release word for Byakuya's bankai: research says it uses the shikai word; Gate A 8 sets "Bankai (+ Senbonzakura Kageyoshi)" as the trigger.
- Whether English speakers will say "Dance, Sode no Shirayuki": built from the wiki translation of 舞え, not heard anywhere.
- All Cyrillic forms (research gives only estimates; rows tagged `X`).
- All ASR forms (section 2): predicted, not recorded.
- The kanji spelling 白霞罸 is rare; ja-JP ASR output for it is predicted (白霞の咎め).

**Mod-defined (free to change):** `rukia.bankai.absolute_zero` (all variants), `byakuya.shikai.mode_attack`, `byakuya.shikai.mode_barrier`, `common.seal`, the petal-storm aliases of `byakuya.bankai.scatter` (Petal storm, Шторм лепестков, 桜吹雪), and the weak lone-word handling.

