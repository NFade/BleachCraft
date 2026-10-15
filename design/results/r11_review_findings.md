# R11 review (a2223a9 against 964218e): findings

Read-only review. Nothing was run. In addition to the code I read the existing client+integrated-server log of the 14 Oct session
(`mod/run/logs/latest.log`, 12:25-12:40, the same commit): it has no WARN/ERROR from the mod, the `[momentum]`, `[icewall]`, `[terrain]`,
`[frost]`, `[config]` lines are there and look right. So the mixin, the passive, the wall (in the Nether), the craters and the migration
did run without a crash. The findings below are what that run could not show.

Paths are relative to `D:/MineBleach/mod/src/main/java/dev/minebleach/reiatsutest/` (main) or `.../mod/src/client/java/dev/minebleach/reiatsutest/client/` (client).

## Findings, most serious first

### 1. The ice wall floats or has holes in grass inside Rukia's own frost field (gameplay, likely)
- `server/IceWall.java:169-178` (`WorldProbe.passable/support`), `core/world/IceWallLayout.java:67-85` (`floorY`), `server/TempBlocks.java:106` (`wallReplaceable`).
- Bankai always runs the frost field (radius 30), and the field replaces grass, ferns and flowers with `FROST_SPRIG` (no collision, not `replaceable()`).
  `floorY` walks down from the aimed point; the first cell under it is a sprig: not "passable" and not "support" (empty collision), so the
  `!pr.passable(x, y-1, z)` branch returns `NO_GROUND`. Then `grounded=false` and `baseY = startY`, the height of the ray end (about eye level
  when looking level): the 9x5x2 wall hangs in the air about a block above the ground. Columns where a sprig stands are also holes in layer 0
  (`placeWall` refuses the cell).
- Why the test did not show it: the session ran in the Nether (no plants); the log line `(no ground, at the aimed height)` never appeared.
- Reproduce: Overworld, grass with flowers, bankai, cast the wall at the ground. Expected log: `wall at ... (no ground, at the aimed height)`.
- Fix: in `WorldProbe` count `FROST_SPRIG` as "free" for the floor search only (`passable` true when `state.isOf(ModBlocks.FROST_SPRIG)`, and let
  `placeWall` skip such cells instead of replacing them, so the frost journal is not broken). Add a test with a probe that has a sprig layer.

### 2. Rukia's frost-field pulses pay the Rage "ability reward" (balance, certain by the code)
- `server/BankaiField.java:88` calls `Targeting.hurtPlain(..., INDIRECT_MAGIC)` with the caster as attacker; `server/RageManager.java:194` (`isAbilityDamage`)
  and the branch in `afterDamage` treat every FREEZE/INDIRECT_MAGIC hit with a player attacker as an ability hit.
- So the first field pulse after any cast (also the wall, which deals no damage, or a missed ability) consumes the armed cast and pays 2.0 Rage
  (0.5 on mobs), and the log says `ability`. `hurtPlain` was meant to be neutral for the passive but not for Rage.
- Fix: give `Targeting.hurtPlain` a flag that `DmgLog`/the damage source can carry (or use a different damage type for the field, e.g. FREEZE with a
  marker set in a thread-local read by `RageManager`) and skip `onAbilityHit` when it is set. Keep kido paying (intended).

### 3. `/reiatsu reload` does not reload the kido numbers that live in the state machine (tuning confusion)
- `server/KidoManager.java:65` replaces `config`, but cost, cooldown, gcd and `enabled` are read by `core/state/StateMachine.java:312-332` from `kidoCfg`,
  which is set only in `server/ZanpakutoManager.java:178` (`createSession`). Damage/radius change at once, cost/cooldown only after a relog.
  The command answers "config reloaded" and the log prints nothing wrong.
- Fix: in the kido reload callback loop over the sessions and call `sm.setKidoConfig(config)` (the way `ZanpakutoManager.refreshRage` does).

### 4. The HUD takes the Rage costs and the shikai hold time from the code defaults, not from the server config (balance testing)
- `client/hud/HudModel.java:275` (ready gem), `:657` (toast numbers), `:732` (`shikaiHoldLeft`). `rage.bankaiCostTenths`, `rage.shikaiCostTenths`,
  `rage.bankaiMinShikaiTicks` are in the server config and in `/reiatsu reload`. Nothing is sent to the client.
- Result: with `bankaiMinShikaiTicks` 0 the gem still waits 20 s; with 1200 the gem lights after 20 s and the press is refused, the toast says
  "needs 1 more s" (the `Math.max(1, ceil(0))` case); with a changed cost the gem and the "x/y" text are wrong. The server decides, so no cheating or desync, only
  a misleading screen. `stateChangeAt` is also the client receive time, so the gem leads/lags by the latency.
- Fix: sync a small record (shikaiCost, bankaiCost, bankaiMinShikaiTicks) as an attachment or in the existing rage attachment; until then say so in the
  tuning notes.

### 5. Terrain breaking is permanent, default ON, with no log of what was removed (world data; by design of T9, but it can hit the real world)
- `server/AbilityTerrain.java:52,245` (`setBlockState(..., NOTIFY_LISTENERS|FORCE_STATE)`), `core/world/TerrainRules.java` defaults (`enabled=true`).
- Not journaled (the opposite of the temp-block rule of the rest of the mod). Profile S (hardness <= 0.8) takes dirt, grass, sand, gravel, leaves, glass,
  wool, crops, farmland, torches, TNT (hardness 0) etc. within 3 blocks of every Byakuya attack landing, 60 per cast; L (<= 5) takes iron blocks and
  everything under 5 in a 5-block crater and a tunnel. The only ownership check is `World.canPlayerModifyAt` (vanilla spawn protection and the world
  border), so claim mods (FTB Chunks, Open Parties and Claims, WorldGuard-like) are not asked (`PlayerBlockBreakEvents.BEFORE` is not fired).
- Side effects of no neighbour updates: water/lava next to a crater stays as a static wall until some update touches it; a door half, a wall torch,
  a lever or a rail next to a removed block stays floating (`aftermath` only fixes plants and sand directly ABOVE a hole).
- Fix: test in a throw-away world, or ship `terrain.enabled=false` for the first test with the real world; optionally fire
  `PlayerBlockBreakEvents.BEFORE` for each cell (cheap, honours claim mods) and write the first N positions of a job to the log.

### 6. ice_wall blocks can survive a crash and are unbreakable (data, low probability)
- `server/TempBlocks.java` (journal is a `PersistentState`, saved with the world; chunks are saved separately on unload/autosave). If the process is killed
  while a wall stands and its chunk was saved but the journal was not, `ice_wall` (strength -1) stays forever; survival players cannot break it.
  `FrostField` has an orphan sweep on chunk load for its own blocks; `ice_wall` has none.
- Fix: in the same chunk-load hook (or at `onServerStarted` for spawn chunks) replace any `ice_wall` block not in the journal by air.

### 7. `GrassGuard` is wider than "grass": a sword in the main hand cannot break torches, crops, sugar cane, redstone dust, saplings (survival)
- `server/GrassGuard.java:28` + `core/combat/SwingThruRules.java` rule: no collision and hardness 0. That is also torch, wheat/carrots/potatoes, nether wart, sugar cane, sapling,
  redstone dust, tripwire, sweet berry bush, fire is handled earlier. Not a bug if Better Combat treats exactly these as swing-through (client side: predicted
  break is refused by the server and re-synced, which flickers); I did not verify BC's own rule (its `isTargetingMineableBlock` was read in R11 by
  bytecode). Check: hold the sword, left-click a torch and a wheat plant.
- Fix if unwanted: restrict to `state.isIn(BlockTags.REPLACEABLE_BY_TREES)`/`FLOWERS`/`state.isReplaceable()` (grass, ferns, flowers, dead bush) instead of the hardness test.

### 8. Config: the client file is never migrated or stamped; the server file write is not atomic (low)
- `core/config/ConfigMigrations.java:22,66` defines `CLIENT_VERSION` and `migrateClient`, nothing calls them (`grep` over main/client). The CLIENT rule list is
  empty today, so no harm now; the first client default change will not be migrated and the file has no `configVersion`.
- `ModConfig.java:54,70,113,182` use `Files.writeString` (truncate then write). A crash/kill during the write leaves an empty file: next start
  `JsonParser` gives JsonNull, every loader logs nothing special and uses the defaults, the user's numbers are lost. Several loaders each do read-modify-write of
  the same file (and at `SERVER_STARTED` they run on the server thread while the client may read the file on the render thread). Fix: write
  to `file.tmp` and `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`; make `loadSection` `synchronized` on the same monitor as `migrate`.
- Migration rewrites a value that the user deliberately set to an old default (kido.shakkaho.damage 2/4/5, fireTicks 60) without asking; there is a
  `.bak_v1`, the log line lists the keys. Documented, acceptable.

### 9. Rage `unboost` is approximate and shared per victim (tiny)
- `server/RukiaMomentum.java:134,140`. `APPLIED` is keyed by victim id and tick, not by attacker: another player's hit on the same victim in the same tick is divided
  by Rukia's multiplier too (about -17 percent of that player's Rage gain at 5 stacks). The division is applied to the damage AFTER armor, which is not linear.
  The killing blow (`RageManager.onKilled`, `Pending` amount from `ALLOW_DAMAGE`) is not unboosted at all, if the mixin runs before the Fabric head injector
  (order of two HEAD injectors is not defined by the code; I could not tell). Effect: at most 2.5 Rage per hit (cap), so negligible.
- Fix if wanted: key `APPLIED` by (victim, attacker uuid) and store the unboosted amount instead of dividing.

### 10. Pose config: any `tp.*` key of the player's own file reaches every pose number unclamped; first run has no `tp` section (cosmetic/robustness)
- `client/model/TpPoseDriver.java:63-77` falls back to `TpConfig.num/vec` for ALL keys, `TpConfig.num` (`client/model/TpConfig.java:100`) clamps nothing except
  `swordScale`. A hand-typed `"hold.tilt": 1e9` in the client file becomes the pose. `TpPose` divides by the item display scale (`handPoint`, `scaleFactor()`);
  a display scale of 0 from a resource pack would give NaN poses (own models are fine). `TpConfig.load()` (`ReiatsuTestClient.java:22`) runs before `FxConfig`
  creates the file on a first run, so the `tp` section is only written at the second start.
- Fix: whitelist the three documented keys in `TpConfig`, or clamp in `num`; call `TpConfig.load()` after `FxClient.init()`.

### 11. Cosmetic
- `client/hud/HudModel.java:723-726`: the new Javadoc of `shikaiHoldLeft` was inserted between the Javadoc of `rageNeedText` and its method.
- `server/RageManager.java` class Javadoc says a parry gives 6.0; the default (and the log: `+4.0 from parry`) is 4.0.
- Client post pass (`client/fx/PressureBlur.java`): a shader error switches the pass off for the whole session (by design, logged); `PostEffectProcessor` is never closed
  (same as `FreezeDesat`).

## Checked and no problem found

- `LivingEntityMixin.reiatsu$rukiaMomentum` (line 44): `LivingEntity#damage(DamageSource, float)` is the only `damage` in Yarn 1.21.1 (javap); one float argument, so
  `argsOnly=true` is unambiguous, handler `(float, DamageSource)` is the usual shape; the log of the 14 Oct run proves it applied (no start-up failure with `defaultRequire=1`).
- No double bonus: `Targeting.hurt` multiplies only abilities (types FREEZE/INDIRECT_MAGIC, never PLAYER_ATTACK; all callers use those two), the mixin only `PLAYER_ATTACK` whose direct
  source is the player. Not applied to a victim in `ZanpakutoItem.isBlocking`. Stacks: one gain per tick, the same multiplier for all victims of a tick (`FrostMomentum`),
  removed on disconnect (`MOMENTUM`), cleared on server stop, `APPLIED` cleared each tick; all on the server thread. Attachment `MOMENTUM` is owner-only, not persistent.
- `TerrainRules`/`AbilityTerrain`: unbreakable (hardness < 0), block entities, fluids, bedrock/barrier/portals/command blocks, frost blocks and the ice wall are never broken;
  adventure/spectator and `mobGriefing=false` stop it; budget 40 blocks/tick with a re-check of every cell; at most 64 jobs; caps 60/400/700 shared over the phases of a cast;
  planning cost is a few thousand cells (radius clamp 16). A dead or moved owner stops the job.
- `IceWall`/`TempBlocks`: cells with living entities or vehicles are skipped, block entities/solids never replaced, rollback on death, logout, dimension change, seal/bankai end,
  server stop (`rollbackAll`, `forgetAll`) and replay at start; `forget(owner)` is called before the restore; a cell changed by someone else is left alone (`restore`);
  the journal cap (160) counts only TempBlocks entries (the frost field has its own journal), so no clash with the field.
- Migration: rule paths match `KidoConfig.toJson` (`kido.shakkaho.damage/fireTicks`), a file at the current version is not touched, a backup `.bak_v1` is made once, an
  unreadable file is left alone; the log shows it worked (2 keys).
- `BcGrassSwing`: touches only two public fields, restores on screen open, sword away, disconnect; `LinkageError`/`RuntimeException` are caught and logged once; the class is only reached
  after `isModLoaded("bettercombat")`.
- `PressureBlur`/shader: same json/program layout as `freeze_desat`; load or draw error is caught and switches the pass off; resize handled; `reduceMotion` removes shake, tremor,
  stripes and tear, `photosensitiveSafe` also the blur (`PressurePlan`); stripe layout fades inside each slot.
- `TpPose` scale paths: `ks = Math.max(0.1, swordScale)`, `swordScale` is clamped 0.5-2.5 and NaN-checked in `TpConfig`; `guard.shift` clamped to +-8 px; a bad `tp` section keeps the last values.
- New JSON resources (lang, phrases, blockstate, models, shader json) all parse; `ICE_SPIKE` is gone from main/client code (byte code 10 kept for `ICE_WALL`); `DENIED_SHIKAI_TIME`
  is appended to `ResultCode` and handled in the HUD, `ClientNet` and the voice state.
- Rage: `RageConfig` constructor checks cannot be hit from `fromRoot` (clamped), `RageMeter` windows and queues are bounded (64 targets, 3 armed casts), tick rollover handled
  (`tick < last` branches); `MeleeRewardLimiter`/`ParryRules` maps are removed on leave.
