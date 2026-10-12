# Voice: manual microphone checklist

For the user. About 15 minutes. Everything the automation could do (synthetic speech through a real Chrome, no person) is already
done and recorded in `LOG.md` ("T1 voice bridge"); what is left here needs a real voice and a real microphone.

## Before you start

- Chrome or Edge (Firefox has no speech recognition). Internet access (the recognition service is the browser vendor's cloud).
- Page: either the game (open a world, click the link in chat, or `/reiatsu voice`) or, without the game,
  `cd mod && gradlew voiceLab` and open `http://127.0.0.1:47831/`. The lab server runs the **real** bridge and matcher
  with every state/item rule switched off, so any phrase of any sword is reported and nothing fires in the game.
- First open: **Settings** (collapsed card) and **Latency** are on the page itself. Nothing leaves the computer except the
  audio that the browser sends to its own recognition service.

## Checklist

| # | Do | Expected | If not |
|---|---|---|---|
| 1 | Open **Lab**, press **Run checks** | SpeechRecognition yes; `start(track) overload` supported (Chrome 155 on the dev machine: yes); on-device packs say `available` or `downloadable` (never installed by the page) | no SpeechRecognition: wrong browser. `start(track)` not supported: pre-warm stays off by itself |
| 2 | Press **Start**, allow the microphone, say any sentence | The mic circle glows with your voice, the bar under it moves (speech about -30 to -10 dBFS). Grey italic text appears while you speak | bar flat and the note after 8 s says no signal: muted or wrong input device in Windows |
| 3 | Lab, **Measure microphone (3 s)**, say a phrase | peak above about -30 dBFS | below -50 dBFS: raise the input volume, move closer |
| 4 | Language **English**. Say "Attack mode", "Barrier mode", "Seal" | Game reply: `byakuya.shikai.mode_attack`, `byakuya.shikai.mode_barrier`, `common.seal` (in the game: also the result code; wrong state means `gated`, that is correct) | text heard is wrong: note it, it is exactly what the ASR table in `design/VOICE_PHRASES.md` section 2 wants |
| 5 | English: "Chire, Senbonzakura", "Bankai", "Mae, Sode no Shirayuki", "Some no mai, Tsukishiro" | recognised, or the engine's own spelling shown in **Heard** | copy what **Heard** shows for each and send it back: those strings are the real test data (md section 5.7) |
| 6 | **Русский**: "Режим барьера", "Режим атаки", "Запечатать", "Чире, Сэнбонзакура" | `byakuya.shikai.mode_barrier`, `byakuya.shikai.mode_attack`, `common.seal`, `byakuya.shikai.release` | same: send the text shown |
| 7 | **日本語**: 「攻撃モード」「散れ、千本桜」「卍解」「封印」 | recognised in kanji or kana, matched by the Japanese columns | ja-JP has no on-device pack in most installs: it uses the cloud service (needs internet) and that is fine |
| 8 | False positives: say "I need to go to the bank", "banka konservov" (ru), a normal chat sentence | Game reply says `no_match` (or `gated_out`); nothing fires | any fire on ordinary speech is a bug: note the exact words |
| 9 | Settings: **Mode = Hold to talk**, tick **Pre-warm**, press **Start** | the mic circle turns amber (`ready`: a muted session is already running) | no amber: see the notice banner (the page tells why it fell back) |
| 10 | Hold **Hold to talk** (or Space; the page must have focus), say "Attack mode", release | text appears while you hold, one reply after the release. **Latency** row "Press to ready" shows 0 ms | a banner saying "heard sound but returned no text, switching to the plain microphone" is the safety net working; send me the browser and the microphone model |
| 11 | Switch the language three times quickly while listening | no frozen state, listening resumes within about a second | note what happened |
| 12 | Turn off Wi-Fi for 10 s with the mic on | red banner "Speech service unreachable ... Retrying"; back to listening after the network returns | stays dead: note it |
| 13 | Tick **On-device recognition**, switch the language | nothing is downloaded; with no pack installed the page keeps using the cloud service | a download or a prompt would be a bug |

## Reading the Latency card (last 30, no words, no audio)

| Row | Meaning | Measured with synthetic speech on the dev machine (Chrome 155) |
|---|---|---|
| Mic start to ready | Start (or session replacement) until the engine reports audio is flowing | plain microphone 235 to 240 ms; muted track 6 to 72 ms (median about 25) |
| Dead time between sessions | gap between one session ending and the next one hearing audio (words said in it are lost) | not observable in the 20 s runs: Chrome kept one continuous session |
| Press to ready | hold to talk: press until the engine is ready; 0 ms means the prepared session was already listening | 0 ms, 5 of 5 |
| Voice onset to first text | first loud sample until the first interim text | 0.6 to 0.95 s |
| Voice end to final text | last loud sample until the **final** text. The recogniser's own wait, not ours | 0.5 to 1.0 s (median about 0.7 s; 0.9 to 1.0 s for the English test phrases) |
| Voice end to full interim text | until the interim text already equals the final one | about 0.3 to 0.4 s |
| Page to game and back | HTTP round trip | 3 to 7 ms |
| Game side | the mod's own time from receiving the post to applying the transition | 0.4 to 2.4 ms (lab: matcher only) |
| Voice end to game reply | sum of the final-text wait and the round trip | about 0.9 s |

The 400 ms budget of the master prompt is "final text to effect" (rows 6 and 7: under 10 ms). The recogniser's end-of-speech wait
(row 5) is outside our control; row 6 shows how much earlier the whole phrase is known as interim text (see
`design/VOICE_BRIDGE_NOTES.md`, "interim path").

**Copy JSON** in the card copies the numbers (also `VoiceStats()` in the browser console); send it back with the answers to
the table above. It contains no words and no audio.
