// Tests for the hold-to-talk key helpers embedded in voice-bridge/index.html (PTTKEY block, pure functions, no browser).
// Run: node --test tests/voice/ptt_key.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const html = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "..", "voice-bridge", "index.html"), "utf8");
const m = html.match(/\/\* =====\s*PTTKEY-BEGIN[\s\S]*?\*\/([\s\S]*?)\/\* =====\s*PTTKEY-END/);
assert.ok(m, "markers PTTKEY present");
const { PTT_DEFAULT, pttKeyValid, pttKeyLoad, pttKeyLabel, pttFocusBlocks } =
  new Function(m[1] + "\nreturn {PTT_DEFAULT, pttKeyValid, pttKeyLoad, pttKeyLabel, pttFocusBlocks};")();

test("default is Space", () => assert.equal(PTT_DEFAULT, "Space"));

test("valid codes: letters, digits, F keys, Space, single modifiers, numpad", () => {
  for (const c of ["KeyA", "KeyZ", "Digit0", "F1", "F12", "Space", "ShiftLeft", "ControlRight", "AltLeft", "Numpad5", "ArrowUp", "Backquote"]) {
    assert.equal(pttKeyValid(c), true, c);
  }
});

test("rejected codes: Enter, Tab, Escape, OS keys, junk and non-strings", () => {
  for (const c of ["Enter", "NumpadEnter", "Tab", "Escape", "MetaLeft", "OSRight", "ContextMenu", "Unidentified", "", "a", "Key A", "Key-A", "x".repeat(40), null, undefined, 5, {}]) {
    assert.equal(pttKeyValid(c), false, String(c));
  }
});

test("load falls back to Space on missing or bad stored value", () => {
  assert.equal(pttKeyLoad("KeyF"), "KeyF");
  for (const v of [null, undefined, "", "Enter", "garbage value", 7]) assert.equal(pttKeyLoad(v), "Space");
});

test("labels are short and human readable", () => {
  assert.equal(pttKeyLabel("KeyF"), "F");
  assert.equal(pttKeyLabel("Digit3"), "3");
  assert.equal(pttKeyLabel("Space"), "Space");
  assert.equal(pttKeyLabel("F9"), "F9");
  assert.equal(pttKeyLabel("ShiftLeft"), "Left Shift");
  assert.equal(pttKeyLabel("ControlRight"), "Right Ctrl");
  assert.equal(pttKeyLabel("AltLeft"), "Left Alt");
  assert.equal(pttKeyLabel("Numpad1"), "Num 1");
  assert.equal(pttKeyLabel("ArrowLeft"), "Left arrow");
  assert.equal(pttKeyLabel("Enter"), "Space", "invalid code shows the default");
});

test("focus rules: text fields swallow every key, buttons only Space", () => {
  assert.equal(pttFocusBlocks("KeyF", "INPUT", "text", false), true);
  assert.equal(pttFocusBlocks("ShiftLeft", "INPUT", "text", false), true);
  assert.equal(pttFocusBlocks("F2", "TEXTAREA", "", false), true);
  assert.equal(pttFocusBlocks("KeyF", "DIV", "", true), true, "contenteditable");
  assert.equal(pttFocusBlocks("Space", "BUTTON", "", false), true);
  assert.equal(pttFocusBlocks("KeyF", "BUTTON", "", false), false);
  assert.equal(pttFocusBlocks("Space", "INPUT", "checkbox", false), true);
  assert.equal(pttFocusBlocks("KeyF", "INPUT", "checkbox", false), false);
  assert.equal(pttFocusBlocks("KeyF", "BODY", "", false), false);
  assert.equal(pttFocusBlocks("Space", "BODY", "", false), false);
  assert.equal(pttFocusBlocks("Space", undefined, undefined, undefined), false);
});

test("page wiring: key stored as reiatsu.pttKey via store(), no Space hard-coding left in the handlers", () => {
  assert.ok(html.includes('store("pttKey"'));
  assert.ok(!/e\.code\s*[!=]==\s*"Space"/.test(html), "handlers compare against the saved code");
  assert.ok(html.includes('id="pttKey"'));
});
