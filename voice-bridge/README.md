# Voice bridge

`index.html` is a single page (no build step, no external scripts, fonts or CDNs) that listens to your microphone with
the browser's Web Speech API (`webkitSpeechRecognition`) and posts what it hears to the game on this computer:

    POST http://127.0.0.1:<port>/voice      {"text": "...", "final": true, "lang": "en-US", "utt": 12, "t": 1760000000000}
    GET  http://127.0.0.1:<port>/status     state, held sword, the phrases that work right now

The game side is the embedded server of the mod (`dev.minebleach.reiatsutest.voice`, loopback only). It matches the text
against `design/VOICE_PHRASES.md` (`mod/src/main/resources/assets/reiatsu_test/voice/phrases.json`) and, when a command
fires, goes through the same server path as the key bindings.

## Open it

1. Start the game and open a single-player world (the bridge runs with the integrated server). The chat shows a link,
   by default `http://127.0.0.1:47821/`. `/reiatsu voice` prints it again.
2. Open that link in **Chrome or Edge** (Firefox has no speech recognition). The mod serves the page itself, so the
   microphone permission is remembered and there is no cross-origin trouble.
3. Press **Start**, allow the microphone, pick the language (日本語 / English / Русский) and speak.

Opening `voice-bridge/index.html` from disk also works (`file://`); then the page talks to `127.0.0.1:47821`, or to the port
in the address: `index.html?port=47822`. The browser asks for the microphone permission on every visit in that case.
`?lang=ru-RU` picks the language, `?autostart=1` starts listening at once.

## Notes

- Chrome and Edge send the audio to their vendor's cloud for recognition: you need internet access and should be aware of
  that. The page itself only ever talks to `127.0.0.1`.
- The page restarts the recogniser by itself when the browser ends a session, as long as the button says **Stop**.
- Final results trigger commands. Interim (grey, italic) text is only shown; the mod ignores it unless
  `voice.interimEnabled` is switched on in `config/reiatsu_test.json`.
- The "Type a phrase" box sends text as if it had been spoken, for testing without a microphone.
- Speech recognition needs the right language: Japanese phrases under `en-US` are recognised poorly, so switch the
  language to match what you say. All three languages' spellings are understood whatever the setting.

## Config (`config/reiatsu_test.json`, section `voice`)

`enabled`, `port` (47821, the next 4 ports are tried when it is busy), `threshold` (0.86), `margin` (0.03),
`interimEnabled` (false), `interimThreshold` (0.97), `debounceMs` (1500), `globalGapMs` (300), `consumedTextMs` (1500),
`allowNullOrigin` (true: accept a page opened from `file://`), `maxBodyBytes` (4096), `maxRequestsPerSecond` (20),
`languages`, `defaultLanguage`, `servePage` (true). The file is written with defaults the first time the game starts.
