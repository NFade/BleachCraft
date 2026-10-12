# Dev stand: server + two clients (2026-10-11)

Goal: test parry and PvP on one PC. No code of the mod touched; the game and the server were NOT started.

## Commands (from mod/)
```
gradlew runServer     # first, wait for "Done"
gradlew runClient     # player 1, Multiplayer -> localhost:25565
gradlew runClient2    # player 2 "TestB", same address
```

## Created / changed
- `mod/build.gradle`: new Loom run config `client2` (task `runClient2`): `client()`, `runDir "run2"`, `programArgs --username TestB`,
  same `-Dreiatsu.devMonitor` vmArg as `client`, no IDE config. `afterEvaluate` hook `doFirst` on `runClient2` copies
  `run/options.txt` and `run/config/` into `run2/` only if missing (saves are never copied).
- `runServer` already existed (Loom `runs.server`, `runDir "run-server"`, `-Xmx1536M`); not changed.
- `mod/run-server/server.properties` (online-mode=false, port 25565, level-name=world_pvp, pvp=true, spawn-protection=0, enforce-secure-profile=false),
  `mod/run-server/eula.txt` (eula=true, local dev test only).
- `mod/run2/` seeded now: `options.txt` + `config/` from `mod/run/` (no saves).
- `.gitignore`: `mod/run2/` added (`mod/run-server/` was already there). README: section "Two clients on one PC".

## Checked
- `gradlew tasks --all`: `runServer`, `runClient2` present.
- Task graph (init script, dry run): `runClient2` has its own argFile `build/loom-cache/argFiles/runClient2`, program args `--username TestB`,
  its own run dir; `runClient` unchanged (dir `run`). Separate game dirs mean separate logs/options/saves and no shared lock.
- Fallback `runClient --args="..."` is accepted by Gradle and Loom keeps its own arguments (they come from argument providers, `--args` is appended).
- `gradlew build -x test` passes.

## Limits
- Not verified in real play (windows, joining, parry between two players): UNVERIFIED.
- Both windows open at the same `dev_monitor` point, overlapping; move one by hand.
- The voice bridge (HTTP 127.0.0.1:47831) tries the next ports when busy, but the browser page stays tied to one client; voice only for one of the two.
- The fallback `--gameDir` is relative to the `runClient` working dir (`mod/run`), so it must be `../run2`; `runClient2` is the proper way.
- `online-mode=false` allows any name to join and skips auth: local only.
