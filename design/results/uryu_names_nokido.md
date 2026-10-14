# Uryu: Quincy names and no kido

Branch gin-uryu. Request: Uryu is a Quincy, so he must not show "shikai" / "bankai" / "sword", and he has no kido (removed for now, maybe something else later).

## 1. Quincy names

BASE = Heilig Bogen (ru Хайлиг Боген), SHIKAI = Blut Arterie (Блут Артерие), BANKAI = Quincy: Vollständig (Квинси: Фолльштендиг). The internal names (ZanpakutoState SHIKAI / BANKAI, ability ids, command ids, item ids) are unchanged.

How: a pure lookup `core/state/CharacterWording` (`key(baseKey, character)`, `stateKey(character, state)`). For Uryu a base key with a variant resolves to `<base>.uryu`; every other character (and keys without a variant) get the shared key, so the strings of Rukia, Byakuya and Gin are untouched. The list of keys with a variant is in `CharacterWording`; the variants are in en_us.json and ru_ru.json next to `hud.reiatsu_test.denied.blocked` (19 `.uryu` keys plus the new `hud.reiatsu_test.bankai_kanji` base key).

Where a player saw the words, and what changed for Uryu:

| Place | Before | Uryu now |
|---|---|---|
| HUD plate name line (PlateHud) | DRAWN / SHIKAI / BANKAI + name | state word of Uryu; shown once when it equals the name (Heilig Bogen, Quincy: Vollständig); SHIKAI shows "BLUT ARTERIE · Heilig Bogen" |
| Plate value line hints (PlateHud) | "[J] Draw", "[R] Release", "Bankai ended" | "[J] Bow", "[R] Blut Arterie", "Quincy: Vollständig ended" |
| Legacy HUD (hud=false) label and hints (LegacyHud) | state word, "draw the sword", "Release (shikai)" | state word of Uryu, "[J]: draw the bow", "[R] Blut Arterie" |
| Denied toasts (HudModel) | "Not in shikai", "Bankai needs shikai", "Bankai needs N more s in shikai", "Draw the sword first", "Hold your zanpakuto" | "Not in Blut Arterie", "Quincy: Vollständig needs Blut Arterie", "... N more s of Blut Arterie", "Draw the bow first", "Hold the Quincy Cross" |
| Action bar messages without the HUD (ClientNet.feedback) | denied.item / not_drawn / shikai_time | the Uryu variants |
| Voice result line on the HUD (HudModel.commandName) | "Shikai: <weapon>" / "Bankai: <weapon>" | "Blut Arterie: Heilig Bogen" / "Quincy: Vollständig" |
| Server action bar (VoiceControl): "Voice: Release / Bankai", "draw the sword first" | | "Voice: Blut Arterie / Quincy: Vollständig", "Draw the bow first" |
| Release flash (OverlayHud) | phrase + "SHIKAI" label | phrase "Blut Arterie"; the label is dropped when it equals the phrase (en) |
| Bankai title card (OverlayHud) | kanji 卍解 | 完聖体 (new lang key `hud.reiatsu_test.bankai_kanji`) |
| /voice status (`/reiatsu voice`) | "state SHIKAI" | the Uryu state word |
| `/reiatsu state ...` feedback | "state forced to SHIKAI (URYU)" | the Uryu state word |
| Key binding names (controls screen, shared by all) | "Release (shikai)", "Bankai", "Bankai ability N", "Draw / sheathe" | "Release (shikai / Blut Arterie)", "Bankai / Quincy: Vollständig", "Bankai / Vollständig ability N", "Draw / sheathe (sword or bow)" (annotated, not replaced; they cannot be per character) |

Also: `hud.reiatsu_test.zanpakuto.uryu` and `.bankai` in ru_ru.json are now the Cyrillic names (they were Latin copies). `hud.reiatsu_test.release.uryu` (the spoken phrase on the flash) stays "Blut Arterie". Voice phrases: the Uryu release phrases were already Blut Arterie and Quincy: Vollstandig (design/VOICE_PHRASES.md 1.28 and 1.32); `tools/gen_voice_phrases.py` was re-run and `phrases.json` came out byte-identical (only the kido notes in the md changed).

Not changed on purpose: item name "Quincy Cross"; the Sode no Shirayuki / Senbonzakura / Kido spellbooks (Uryu has none); `/reiatsu info` (a debug dump with enum names); the `/reiatsu state sealed|shikai|bankai` argument names (dev command).

## 2. No kido for Uryu

Pure rule `core/kido/KidoAccess`: `allowed(held, released)` is false when the item in hand is the Quincy cross or the released character is Uryu; `isKidoCommand(commandId)`.

- Server: `ZanpakutoManager.performKido` (shared by the key packet and the voice path) refuses first, before the block check and before the machine: no reiatsu, no cooldown, no effect, no reply, no action bar text (logged at debug). The refusal returns DENIED_ITEM/WRONG_ITEM to the caller. `VoiceControl` does not announce "Voice: Hado ..." for him.
- Client: `ReiatsuKeys.castKido` returns early (no packet, no denied feedback); `ReiatsuKeys.kidoAvailable()` is the check. `SideHud` skips `KidoCornerHud` (cells reset); the shunpo cell is drawn as before. The legacy one line side HUD drops the K N M part.
- Voice: `PhraseBook.Command.validIn` rejects `kido.*` when `ctx.held()` is Uryu (so the matcher does not consider them in any state); the ungated test mode is unaffected.
- Untouched: KidoManager, the kido code and config, the other characters, the Kido spellbook, `phrases.json`.

Tests (new): `KidoAccessTest`, `KidoUryuGateTest` (matcher + `validIn`), `CharacterWordingTest` (variants exist in both languages, shared keys untouched, Uryu texts contain no shikai/bankai/sword words). `LangTest` key parity passes. `./gradlew build` passes.

## UNVERIFIED (no game was launched)

- Plate layout: "[R] Blut Arterie" (en) fits the 128 px bar at default scale (about 75 px of about 87 free); the Russian "[R] Блут Артерие" is wider and may be trimmed by `trimToWidth` (no ellipsis), most likely in compact HUD (96 px bar) for both languages.
- The title card with 完聖体 (3 glyphs at the same scale as 卍解 with 2) was not seen; the glyphs exist in the Minecraft unifont as far as known but were not checked on screen.
- The Russian texts (grammar of "Не в Блут Артерие") were not reviewed by a native speaker.
- Server side `Text.translatable` for the voice action bar relies on the client translating the key (it does; dedicated server not tested).
- Keys K/N/M do nothing for Uryu, but a modified client could still send `cast_kido`; the server refusal for that path is covered only by reading the code, no automated test (the core rule is tested).

## Manual checklist

1. Take the Quincy Cross, switch the game to English. Plate: SEALED shows "[J] Bow"; draw it: "HEILIG BOGEN" and "[R] Blut Arterie"; release: "BLUT ARTERIE · Heilig Bogen" and the flash shows Blut Arterie once; reach bankai: "QUINCY: VOLLSTÄNDIG" once, the title card shows 完聖体.
2. Fail on purpose: bankai too early ("Quincy: Vollständig needs N more s of Blut Arterie"), an ability in the wrong state ("Not in Blut Arterie"), release without drawing ("Draw the bow first"). No toast may say shikai, bankai, sword or zanpakuto.
3. With the HUD off (hud=false in client config) check the bar label and the hint line.
4. Say "Blut Arterie" and "Quincy Vollstandig" with the voice bridge: the action bar and the HUD result line use the Quincy words. Say "Byakurai": nothing happens, no message.
5. Press K, N, M with the cross in hand (sealed, drawn, released): nothing happens, no cooldown, no reiatsu spent, no corner cells; the shunpo cell (Y) is still there and works.
6. Same with Rukia, Byakuya, Gin: wording unchanged ("Shikai", "Bankai", "draw the sword"), kido works, corner cells shown.
7. Switch to Russian and repeat 1 and 2.
