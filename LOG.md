# LOG

Short log of decisions and phase results.

## 2026-10-08: setup
- Working dir `D:\MineBleach`, mappings Yarn, MC 1.21.1 / Fabric.
- Blender 5.2.2 MCP (`mcp__blender__*`) responds: default scene (Camera, Cube, Light).
- Created folder structure (section 4), `.gitignore` (refs/ excluded), `LICENSES.md`, `refs/INDEX.md`, git init.

## 2026-10-08: Phase 0, environment

| Item | Status (have/missing) | Version / path | Needed action (what to install, if any) |
|---|---|---|---|
| Blender MCP (`mcp__blender__*`) | have | Blender 5.2.2; scene Scene (Camera, Cube, Light), unsaved | none. Read-only snippet errored (see Notes) |
| Java 21 (JDK) | missing | Only Java 8 (`C:\Program Files\Java\jre-1.8`, javapath 1.8.0_401) | Install Temurin JDK 21 (x64 MSI) |
| JAVA_HOME | missing | empty | Set to the Temurin 21 install dir after installing JDK 21 |
| Gradle (global) | missing | not on PATH | Optional; not needed (use `gradlew`) |
| git | have | 2.52.0.windows.1 | none |
| Node.js / npm | have | node v24.15.0 / npm 11.12.1 | none |
| Python (`python`) | have | 3.10.11 | none |
| Python (`py`) | have | 3.12.8 | none |
| Pillow (PIL) | have | 12.3.0 (via `python`) | none |
| `%APPDATA%\.minecraft` | have | `C:\Users\efeki\AppData\Roaming\.minecraft` | none |
| MC 1.21.1 / Fabric in versions | missing | versions present: 26.1.2, 26.2-snapshot-7, SkyFactory 5 5.0.8 | Install Fabric 1.21.1 via launcher (Fabric loader + MC 1.21.1 profile) |
| Official Minecraft Launcher | have (probable) | MS Store package data `Microsoft.4297127D64EC6_8wekyb3d8bbwe` present; classic path absent | Verify the launcher opens; no install needed |
| Prism / MultiMC / CurseForge / Modrinth App | missing | none of the checked paths exist | Optional; not required |
| XboxGames folder | have | `C:\XboxGames` | none |
| Blockbench | missing | not in `%LOCALAPPDATA%\Programs\Blockbench`, `C:\Program Files\Blockbench`, or Start Menu | Install Blockbench (desktop) |
| Free disk C: | have | 27.6 GB free of 222.6 GB | Low margin; keep an eye on it (Minecraft/Gradle caches are large) |
| Free disk D: | have | 199.5 GB free of 931.5 GB | none |
| Google Chrome | have | `C:\Program Files\Google\Chrome\Application\chrome.exe` | none (Web Speech API works) |
| Microsoft Edge | have | `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` | none |

Notes:
- JAVA_HOME is empty and `java` on PATH resolves to Java 8 (`javapath`). Minecraft 1.21.1 + Fabric needs Java 21, so the JDK 21 install is blocking for building and for running.
- Blender `execute_blender_code` with the Phase 0 snippet failed on Blender 5.2.2: `AttributeError: type object 'HydraRenderEngine' has no attribute 'bl_idname'`. The line `[e.bl_idname for e in bpy.types.RenderEngine.__subclasses__()]` is the cause. Drop or guard that field (e.g. `getattr(e, 'bl_idname', None)`) in the next read-only run. The scene was not modified.
- Scene is the default unsaved one; `filepath` is empty, so `is_saved` is false.
- The Store MC package folder exists, but `Get-AppxPackage` returned nothing. Launcher presence is therefore inferred, not confirmed.
- No Fabric or 1.21.1 profile exists yet in `.minecraft\versions`.
- Orchestrator re-check: Blender python 3.13.13, numpy yes, PIL **no** inside Blender (use system `python` 3.10 + Pillow 12.3 for contact sheets). Render engines: CYCLES, HydraRenderEngine (+ built-in EEVEE/Workbench).
- Fabric 1.21.1 launcher profile is NOT needed for development: `gradlew runClient` (Loom) downloads MC 1.21.1 itself. Only JDK 21 is required. Blockbench is only needed for fallback path A.
- Checkpoint 1 shown to user: JDK 21 missing (blocks phase 2b/4), Blockbench optional.

## 2026-10-08: Phase 1 (research, sheets, art bible, Gate A)
- 1a: 4 research files in `research/` (Fandom via `tools/fandom.py`: wiki HTML 403 to scripts, API OK, image CDN needs Referer).
- Refs (private, gitignored): rukia sealed 9 / shikai 11 / bankai 10; byakuya sealed 12 / shikai 12 / bankai 12. Index `refs/INDEX.md`.
- 1b: contact sheets `refs/sheets/*.png` (`tools/contact_sheet.py`, system Python + Pillow).
- 1c: `design/ART_BIBLE.md` v1 (sonnet).
- Gate A (opus call 1/5): APPROVED WITH EDITS, 27 edits, 15 open questions settled (`design/GATE_A.md`). v2 applied by sonnet, plus 8 consistency fixes.
- Ref gaps after Gate A: byakuya sealed tsuba/hilt close-ups and shikai hilt-after-scatter filled (manga ch. 116/301/302/379). Still missing: rukia bankai back view and head ornament (not on Fandom); byakuya saya in daylight.
- Opus budget used: 1 of 5 (Gate A). Remaining: ADR, Gate B, bankai VFX design, Gate D (Gate C would be a 6th: to be decided).

## 2026-10-08: Checkpoint 2 decisions
- User: install everything needed; opus limit raised to 6.
- Installed Temurin JDK 21.0.12 via winget (JAVA_HOME machine-wide, PATH before Oracle javapath 1.8). `GRADLE_USER_HOME=D:\gradle-home` (user env). Blockbench not installed (only for fallback A; install if the spike fails).
- Note for agents: shells started before the install keep old env; set `JAVA_HOME` and `GRADLE_USER_HOME` explicitly in Gradle commands.
- VOICE_PHRASES.md (sonnet): 14 commands, 160 predicted ASR rows, 89 neutral phrases; prototype JW matcher 0 FP, 72% fuzzy recall (rest via aliases). Open: "bank eye" listed as neutral by orchestrator but is a likely ASR form of "bankai": resolve in phase 5.
