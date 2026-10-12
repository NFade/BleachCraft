# Spellbooks (B3), finished 2026-10-12

## State found (commit 7d3e74f, WIP)
Done: items (`registry/SpellbookItem`, `ModBooks`), `core/book` (SpellbookId, SpellbookCatalog, BookPage, BookArg, BookFacts, BookPager), a basic `client/book/SpellbookScreen`, models, 16x16 textures, three shapeless recipes, lang keys for all 3 books in en_us and ru_ru (page title and body, 8 / 9 / 4 pages), tooltip. All 17 ids of phrases.json were already covered. Missing: any test of the book code, a creative tab of the mod (books were only in the vanilla Combat tab), page availability dimming, a layout that survives small windows / high GUI scale, wheel / Home / End / inventory-key handling, no state note on a page. Two lang defects found by the new tests: the Sai page passed a damage argument that its text does not show, and 5 ru pages lacked the Japanese writing of the phrase.

## Books
| Item | Pages |
|---|---|
| `book_sode_no_shirayuki` | intro (keys), shikai release, Tsukishiro, Hakuren, Shirafune, bankai release, Absolute zero, Seal |
| `book_senbonzakura` | intro, shikai release, Attack mode, Barrier mode, bankai release, Scatter, Hakuteiken, Senkei (switched off), Seal |
| `book_kido` | intro (keys K / N / M), Byakurai, Shakkaho, Sai |

Every command page: description, phrase in romaji and Japanese writing (ru adds a Cyrillic reading), key (shown as the player's current binding), reiatsu cost, cooldown, kido range and damage (from `BalanceConfig` / `KidoConfig` defaults; the client does not get the server config, so a server with edited numbers still shows the defaults, the tooltip says so). The screen adds a small line "Works in: Shikai, Bankai" (`any state` for kido).

## Screen (changes in this pass)
- Fixed 192 x 222 virtual layout (vanilla book.png frame, vanilla page-turn arrows at the vanilla positions, Done button), scaled down as a whole when the window is smaller (`min(1, height/222, width/200)`), mouse coordinates mapped back (click, release, drag).
- Title in bold dark red with a rule, page number as in vanilla, body picks the largest of scale 1.0 / 0.85 / 0.75 / 0.65 that fits above the arrows.
- Page availability from `ClientState` (`SpellbookCatalog.availability`): a command page is muted (grey text, grey key names) with a red note when the player is in the wrong state ("Not available now (you are: Drawn)") or has the other zanpakuto released. Intro and kido pages are never muted. The screen does not pause the game, so the dimming follows the live state.
- Input: wheel, Left/Right, Up/Down, PageUp/PageDown, Home/End, arrows by click, Esc and the inventory key close; page-turn sound on every turn.
- Opens at the page of the current state (sealed: intro, drawn: release, shikai: first dance / mode, bankai: first bankai ability); the other zanpakuto's book opens at page 1.

## How to get the books
- Creative tab "Reiatsu Test" (new, holds both zanpakuto and the 3 books) and the vanilla Combat tab.
- `/give @s reiatsu_test:book_kido` (also `book_sode_no_shirayuki`, `book_senbonzakura`).
- Craft (shapeless, 3 ingredients): book + ink sac + redstone = kido; book + ink sac + snowball = Sode no Shirayuki; book + ink sac + pink petals = Senbonzakura.
- Grant on first zanpakuto: not done, the mod has no point where a zanpakuto is handed out (items come from creative / `/give`).

## Tests
New `core/SpellbookTest` (10 tests): phrases.json ids and book ids are the same set; page states equal the `states` of phrases.json; every page and title key exists in both languages with no orphan page keys; placeholders match the arguments (none missing, none unused); every numeric argument resolves and every key name has a lang entry; every command page shows the `say` phrase and a Japanese writing in en and ru; pager clamp and walk through every book; opening page per state; availability. `CorePurityTest` stays green (no Minecraft imports in core/book). `./gradlew compileClientJava test` exit 0.

## To check in game (UNVERIFIED, game not run)
1. Creative tab "Reiatsu Test" appears with its icon, the 3 books have 16x16 icons, tooltip lines, right click opens the screen.
2. Frame, arrows (visible only when a next / previous page exists), Done, wheel, arrows, Esc, E; page-turn sound.
3. GUI scale 4 on a small window: the whole book shrinks, buttons still click at the right spot (mouse mapping).
4. Muting: open the Rukia book sealed / drawn / shikai / bankai and with Byakuya's sword; the page text fits at every scale (longest bodies: intro pages, kido page 3), Japanese glyphs render in the book font.
5. Key names follow the rebinding in Controls.
6. Crafting recipes show up in the recipe book and work.
