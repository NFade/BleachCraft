# Configurable push-to-talk key (bridge page, 2026-10-11)

## What changed
- `voice-bridge/index.html`: new button `#pttKey` ("Key: Space") next to `#ptt` (both shown only in Hold to talk mode).
  The `#ptt` caption follows the choice ("Hold to talk (F)"), so does the "Ready" detail line. UI text of the page is English only, so no ru strings.
- Pure helpers in a `PTTKEY-BEGIN/END` block (the page is a single file, CSP forbids `script src`, so the block is cut out by tests like SESSION-CORE/STATS):
  `pttKeyValid`, `pttKeyLoad`, `pttKeyLabel`, `pttFocusBlocks`.
- Tests: `tests/voice/ptt_key.test.mjs` (7); `VoicePageTest` also checks the PTTKEY markers. README line updated.

## Use
Click "Key: ..." -> "Press a key... (Esc cancels)" -> press the key. Esc or leaving the button cancels. Accepted: any KeyboardEvent.code
that is a plain name (KeyA, Digit1, F1-F12, Space, Shift/Control/Alt Left/Right alone, Numpad*, arrows, Backquote...).
Refused (keeps waiting): Enter, NumpadEnter, Tab, Escape (cancel), Meta/OS keys, ContextMenu. Mouse buttons not supported.

## Storage
`localStorage["reiatsu.pttKey"]` = KeyboardEvent.code, via existing `store()` (try/catch inside). Bad or missing value -> Space.

## Handler rules
keydown/keyup (capture phase) compare `e.code` with the saved code; `e.repeat` ignored. Focus in a text field/textarea/select/contenteditable
blocks every key (manualText included); focus on a button or checkbox blocks only Space (it would click), so after rebinding to F the key
works even with the button focused. Changing the key mid-hold releases PTT; the capture keydown never presses PTT.

## Check in the browser (not run here)
Open http://localhost:47821/?mode=ptt, press Start, click "Key", press F: both labels say F; reload keeps it. Hold F: button turns accent
and "Hearing you" appears; release stops. Click into the phrase field and type F: no PTT. Space no longer triggers PTT (unless re-chosen).

## Verification
`node --test tests/voice/*.test.mjs` 44/44; page script syntax ok; `gradlew test` exit 0, no FAILED/Exception. Game not run.
