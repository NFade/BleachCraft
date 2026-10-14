# HUD: kido corner and shunpo side cell (branch gin-uryu)

Request: show the KIDO spells in the bottom LEFT corner so their cooldowns can be seen, and SHUNPO (Uryu's flash step too) at the LEFT CENTER of the screen.

## What the code did before

- The strip (`StripHud`, right of the hotbar) draws only the three character ability slots (`AbilityId.forSlot`). It never drew kido or shunpo, so nothing had to be removed from it.
- Kido (K / N / M, `KidoId` codes 101 to 103) work in EVERY state, with any sword or empty hands (`KidoManager`, `StateMachine`). Their cooldowns were already synced (`CooldownData`, `ClientState.cooldownRemaining(KidoId)`) but not shown anywhere.
- Shunpo (Y) also works in ANY state with any sword or none (`StateMachine#shunpo`, round 5; the older note "SHIKAI and BANKAI only" is out of date). Its cooldown (`shunpoEnd`, 50 ticks, Uryu +2 blocks) was NOT synced to the client, so a new sync entry was needed.

## What was built

- `core/hud/SideHudLayout` (pure): placement. Kido row of three 22 x 22 cells, 6 px from the left and 6 px above the bottom (compact: 4 px, gap 1), left of the hotbar and its off hand slot; when the row does not fit (scaled width below about 400, e.g. 1280x720 at GUI 4) the three cells stack in a column, K on top. Shunpo cell at x = margin, vertically centred, pushed down under `topReserve` (plate, voice row, denied toast) and never onto the kido cells.
- `core/hud/SideCellMath` (pure): total with fallback to the largest remaining value seen (the server config may differ from the client defaults), radial sweep frame, vertical wipe height, seconds label (whole seconds rounded up, "0.x" under one second), key letter.
- `client/hud/SideCell`: one cell drawn like a strip slot (slot frame, 16 x 16 icon, sweep, seconds, key tab with the letter, ready ping, red flash on DENIED_REIATSU, shake plus gold seconds on COOLDOWN, grey flash on other refusals, "poor" look when reiatsu is below the cost).
- `client/hud/KidoCornerHud`: the three kido cells (radial sweep like the strip). Costs and cooldowns from `KidoConfig.defaults()`.
- `client/hud/ShunpoSideHud`: the shunpo cell with a vertical wipe draining top to bottom. Frame tint by character: Rukia and Byakuya by their accent while released, Uryu light blue `#7FB2FF` plus a blue icon, Gin and sealed steel.
- `client/hud/SideHudIcons`: pixel icons built from character grids drawn with `HudGfx.fill` (lightning bolt, red fireball, gold binding ring with a knot, three chevrons). No new binary textures; the atlas and its generator are untouched.
- `client/hud/SideHud`: public entry (draw, legacy line, denied feedback bookkeeping); the whole draw is guarded so a bad value can never crash the render loop (logs once).
- Visibility rule = the plate rule: shown when a zanpakuto item is in the main hand or the state is not SEALED (`HudModel.plateWanted`, faded with `plateAlpha`). Hidden while the chat input is open (its field covers the bottom left), with F1 and in the dev harness hide (same as the rest of the HUD).
- `reduceMotion`: no shake, no growing ready glow, plate alpha jumps instead of fading.
- `hud=false` (legacy): one plain text line at the bottom left, `K 2.5  N -  M -  Y 1` (seconds or `-` when ready), drawn only if it fits left of the hotbar.

## Shunpo cooldown sync

- `CooldownData.SHUNPO_CODE = 110` (abilities 1 to 23, kido 101 to 103), new overload `CooldownData.of(abilities, kido, shunpoTicks)`, `shunpoRemaining()`.
- `ZanpakutoManager.flush` passes `s.sm.shunpoCooldownRemaining()`; it already syncs whenever the key set changes, which happens on every shunpo and when it expires.
- `ClientState.shunpoCooldownRemaining()` counts down from the sync like the other cooldowns.

## Hook lines in shared files

- `client/hud/ReiatsuHud.java` after `StripHud.draw(...)`: `SideHud.draw(GFX, mc, font, w, h, o[1] + 72); // kido corner ...`
- `client/hud/ReiatsuHud.java` legacy branch after `LegacyHud.render(ctx, tick);`: `SideHud.renderLegacy(ctx, mc);`
- `client/net/ClientNet.java`: `SideHud.noteKido(seq, kido);` in `castKido`, `SideHud.noteShunpo(seq);` in `requestShunpo`, `SideHud.onResult(r.clientSeq(), code);` in `onResult` (after the `REQUESTS.remove`).
- `server/ZanpakutoManager.java` (flush): `CooldownData.of(..., ..., s.sm.shunpoCooldownRemaining())` replaces the two argument call.
- `client/ClientState.java`: new method `shunpoCooldownRemaining()` before `ticksInState()`.
- `registry/data/CooldownData.java`: additive (constant, overload, getter).
- No lang keys, no PlateHud / HudModel / StripHud changes (merge with the Uryu Focus pips row stays trivial).

## Tests

`SideHudLayoutTest` (10): bottom left position, no overlap with the hotbar plus off hand slot at GUI scales 1 to 4 on 1280x720, 1366x768, 1920x1080, 2560x1440 and at the five sizes of storyboard 7.9, on screen, below the chat lines whenever it is a row, no overlap between cells, shunpo centred vertically at the left edge and pushed only by `topReserve`, narrow window stacking, compact rule. `SideCellMathTest` (5). `CooldownDataShunpoTest` (3). `./gradlew build` passes.

## UNVERIFIED in game

- Everything visual: icon readability at GUI scale 1 to 4, the look of the frame tints, alignment of key letters and seconds.
- That the kido corner never touches the chat lines in a real window (computed: cells top = h - 28, chat lines end at h - 40). In the stacked column mode (scaled width below about 400) the column can overlap unfocused chat lines.
- Boss bars pushing the plate down: `topReserve` = plate y0 + 72 is a computed bound for the voice row and denied toast.
- Offhand slot and the attack indicator option HOTBAR: the layout always reserves the 29 px off hand space; nothing is drawn in it.
- Denied feedback for voice-cast kido (negative sequence numbers) is not attached to a cell; only the sweep shows.
- Server configs with other kido or shunpo cooldowns: the sweep adapts by the largest remaining value seen; the first cast after joining may start with a short sweep.
- Whether Uryu's shunpo sync (cooldown 50 ticks, +2 blocks) reads correctly in the cell.

## Manual checklist

1. Join with a zanpakuto in hand (any of the four): three kido cells bottom left (K N M), shunpo cell left centre (Y). Without sword and SEALED nothing shows.
2. Press K: the Byakurai cell sweeps for 5 s with whole seconds, the ready ping when it ends. N: 10 s. M: 5 s. Press during cooldown: the cell shakes, seconds flash gold.
3. Drain reiatsu and press N: the frame flashes red, the cell shows the poor look (red line) when reiatsu is below the cost.
4. Press Y: the shunpo cell wipes top to bottom for 2.5 s. Try into a wall (BLOCKED): grey flash, no cooldown.
5. Uryu: the shunpo cell has a blue frame and blue chevrons.
6. Open chat: the cells hide. Open inventory: they stay.
7. GUI scale 1 to 4 on a 1280x720 window: row at 1 to 3, column at 4; no overlap with the hotbar or off hand item.
8. `reduceMotion` true: no shake, no glow growth. `hud=false`: one text line at the bottom left.
9. Rukia and Byakuya: the shunpo frame takes their released accent.
10. Check the voice toast and the denied toast at the top left do not touch the shunpo cell on a small window (GUI 4).
