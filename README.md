# BleachCraft (reiatsu_test)

Fabric 1.21.1 prototype mod: Rukia's Sode no Shirayuki and Byakuya's Senbonzakura with sealed, shikai and bankai states,
reiatsu, abilities and voice control. Mod sources in `mod/`, design documents in `design/`, the voice bridge page in
`voice-bridge/`. Install, build and known-problems sections are filled in phase 7; this file currently documents voice.

## Voice

Say the phrase out loud and the game acts as if you had pressed the key. Commands still obey every rule (state, sword in
hand, reiatsu, cooldowns): a spoken command that the server refuses gets the same feedback as a refused key press.

**How to open the page**

1. Start the game, open a single-player world. The chat shows `Voice bridge ready: http://127.0.0.1:47821/`; click it, or run
   `/reiatsu voice` to see the link again.
2. Open the link in Chrome or Edge, press **Start**, allow the microphone, choose Japanese, English or Russian.
3. Hold a zanpakuto in the main hand and speak. The page shows what it heard, what the game answered, and the phrases that
   work in your current state.

Chrome and Edge do the recognition in their vendor's cloud (internet needed, audio leaves your PC); Firefox has no speech
recognition. Details, query parameters and the config keys: `voice-bridge/README.md`. Dedicated servers do not start the
bridge (single player only for now).

**Phrases per state.** Every phrase also works in its Japanese and Russian spelling, and common misheard forms are accepted
(full lists: `design/VOICE_PHRASES.md`). A phrase for another state or the other sword is ignored.

| Sword | State | Say | Effect |
|---|---|---|---|
| Rukia | sealed | "Mae, Sode no Shirayuki" (or "Sode no Shirayuki", "Shirayuki"), 舞え 袖白雪, "Мае, Содэ но Сираюки" | release (shikai) |
| Rukia | shikai | "Some no mai, Tsukishiro" (or "Tsukishiro") | first dance, Tsukishiro |
| Rukia | shikai | "Tsugi no mai, Hakuren" (or "Hakuren") | second dance, Hakuren |
| Rukia | shikai | "San no mai, Shirafune" (or "Shirafune") | third dance, Shirafune |
| Rukia | shikai | "Bankai, Hakka no Togame" (or just "Bankai"), 卍解, "Банкай" | bankai (needs a full reiatsu bar) |
| Rukia | bankai | "Absolute zero", 絶対零度, "Абсолютный ноль" | absolute zero |
| Byakuya | sealed | "Chire, Senbonzakura" (or "Chire", "Scatter"), 散れ千本桜, "Чире" | release (shikai) |
| Byakuya | shikai | "Attack mode", 攻撃モード, "Режим атаки" | swarm attack mode |
| Byakuya | shikai | "Barrier mode" or "Dome mode", 防御モード, "Режим барьера" | barrier dome |
| Byakuya | shikai | "Bankai, Senbonzakura Kageyoshi" (or "Bankai", "Kageyoshi") | bankai (needs a full reiatsu bar) |
| Byakuya | bankai | "Chire" or "Petal storm", 桜吹雪, "Шторм лепестков" | petal storm (scatter) |
| Byakuya | bankai | "Shukei, Hakuteiken" (or "Hakuteiken") | Hakuteiken |
| either | shikai or bankai | "Seal", 封印, "Запечатать" (whole sentence only) | back to sealed |

Notes: "Chire" is the release in sealed state and the petal storm in bankai state, never a bankai trigger. Lone "Seal",
"Chire" and "Scatter" only count as the whole utterance ("the seal is broken" does nothing). "bank eye" (what English
recognisers often hear for "bankai") is accepted as bankai only while you are in shikai, hold the sword and the bar is full.

**Tuning.** `config/reiatsu_test.json`, section `voice` (port, confidence threshold, debounce times, interim results,
languages). Regenerate the phrase table after editing `design/VOICE_PHRASES.md` with `python -I tools/gen_voice_phrases.py`.
