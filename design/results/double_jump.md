# Double jump (SHIKAI and BANKAI, all characters)

User request 2026-10-14: a double jump in shikai and bankai for ALL characters (Rukia, Byakuya, Gin, Uryu); not in SEALED or BASE.

## What was built

A pure state machine (`core/movement/DoubleJump`) fed once per client tick, a client glue class that applies a vanilla-style jump when the
machine fires, and a `doubleJump` config section. Nothing server side was changed: the movement is client-authoritative in vanilla.

### Rules (all unit tested)

- One air jump per air time. Landing resets.
- The jump key must be PRESSED anew in the air (rising edge). A key that is held from the ground jump does not fire; release and press again.
- Walking off a ledge without jumping also allows one air jump (press edge in the air).
- Minimum air time 3 client ticks (fires from the 3rd air tick). A press before that is ignored, not buffered.
- Blocked (and treated like the ground, so it resets): in water or lava (also submerged), climbing (ladder, vine, scaffolding), fall flying (elytra), riding, creative flying, spectator, sleeping, dead.
- Allowed only when the SYNCED zanpakuto state of the local player is SHIKAI or BANKAI. If the attachment cannot be read in a frame the state is "not allowed". No screen open: with a screen the key and the permission are both fed as false.
- A key held across a state change (sealed to shikai in mid air) never turns into a press.

### Effect applied

`vy = max((jumpStrength attribute 0.42 + JumpBoost 0.1*(amp+1)) * strength, vy)`, horizontal velocity untouched, no sprint boost. This is
`LivingEntity.getJumpVelocity()/jump()` of 1.21.1 without the block factor (honey) and without the sprint push. The fall distance is NOT touched
(see below).

Client effect: 8 `dust_puff` mod particles (via `FxParticles`) in a ring at the feet, and a quiet vanilla `entity.phantom.flap` (volume 0.3, pitch 1.5-1.7).
CLIENT ONLY: other players neither see nor hear it. All existing effect packets are server to client; showing it to others needs a new
client to server payload plus a server broadcast, which was not cheap or "additive" enough. Other players do see the player move upward (normal position sync).

## Files

New:
- `mod/src/main/java/dev/minebleach/reiatsutest/core/movement/DoubleJump.java` (state machine, `allowedState`)
- `mod/src/main/java/dev/minebleach/reiatsutest/core/movement/DoubleJumpConfig.java`
- `mod/src/client/java/dev/minebleach/reiatsutest/client/input/DoubleJumpClient.java` (glue)
- `mod/src/test/java/dev/minebleach/reiatsutest/core/movement/DoubleJumpTest.java`, `DoubleJumpConfigTest.java`

Shared files, additive only: `ModConfig.java` (`loadDoubleJump()`, one import), `ReiatsuTestClient.java` (one line `DoubleJumpClient.init()`).

## Config

`config/reiatsu_test.json`, section written with defaults when missing:

```json
"doubleJump": { "enabled": true, "strength": 1.0 }
```

- `enabled` (default true): false switches the feature off for the local player.
- `strength` (default 1.0, clamped to 0.1 .. 2.5): multiplier of the vanilla jump velocity. 1.0 is a normal jump.
- Bad or missing values give the defaults. Live reload: `/reiatsu reload` (registered in `ConfigReload`, works on the integrated server, i.e. singleplayer) and the file is re-read on every world join. The client reads its OWN local copy of the file, so on a dedicated server each player sets it on their machine.

## Server rules checked (bytecode of `ServerPlayerEntity`-side `ServerPlayNetworkHandler.onPlayerMove`, Yarn 1.21.1 jar, javap -c)

No decompiled sources exist in the loom cache, so the checks were done on the bytecode (constants read from it):

1. "Floating" kick (`multiplayer.disconnect.flying`): `floating` is set per move packet when the Y delta is >= -0.03125 AND the player is not creative/spectator, server flight is disabled, no `allowFlying`, no Levitation, not fall flying/riptide, and `isEntityOnAir` (no block directly below). `floatingTicks` counts server ticks while it holds and resets as soon as one packet has a lower delta. The kick comes at more than `ceil(80 * max(1, 0.08 / gravity))` = 80 ticks. A jump rises with dy >= -0.03125 for only about 7 ticks at strength 1.0 (about 13 at the max strength 2.5) and then falls faster than 0.03125 per tick, which resets the counter. One air jump per air time, so the counter never gets near 80. Cannot trip.
2. "Moved too quickly": compares the squared distance since the last tick (minus the server-known velocity squared) with 100 (300 elytra) times the number of packets. A jump moves at most about 0.42 to 1.05 blocks per tick. Cannot trip.
3. "Moved wrongly" (distance 0.0625 squared = 0.25 blocks): the server re-simulates the packet's delta with collisions and compares. Vertical differences between -0.5 and 0.5 are ignored, horizontal ones are not affected by the air jump (horizontal velocity untouched). This only warns/rolls back for a client that moved through blocks. Cannot trip.
4. The server's own jump emulation (`player.jump()` when the server player is on ground, the packet says not on ground and dy > 0) only triggers for the first jump; in the air it is not involved.
5. `isMovementInvalid` (NaN / out-of-range values): the velocities are finite and small.

## Fall damage (reported, not touched)

Fall damage is computed server side from the position and onGround packets (`handleFall`): the fall distance only grows by descents. The client's fall distance field is irrelevant for the damage and resetting it would be wrong, so nothing is reset. Result: an air jump ADDS about 1.25 blocks to the total descent (at strength 1.0), so a jump from a ledge that was safe by 1 block may now cost a heart. Landing on the same level after a normal double jump (about 2.5 blocks up, 2.5 down) stays under the 3 block threshold and does no damage.

## UNVERIFIED (nothing played)

- The whole feature has not been run in game; unit tests cover the state machine and the config only.
- That the integrated server really never logs "moved wrongly" or kicks during repeated double jumps (analysis of bytecode only).
- Feel: the 3 tick minimum, strength 1.0 and the sound/puff values are guesses. The `dust_puff` size/colour may look too faint or too large.
- `p.isOnGround()` on the client right after the player tick in `END_CLIENT_TICK`: assumed to reflect the tick's movement (the vanilla order is player tick, then END_CLIENT_TICK).
- Whether the synced state attachment is present for the local player on every character (Gin, Uryu): read through the same `ModAttachments.ZANPAKUTO` as the HUD.
- Interaction with other mods' movement (Rukia frost momentum, shunpo) not tested: shunpo is server driven and sets velocity; an air jump during a shunpo may be eaten by it.

## Manual checklist

1. SEALED and BASE (J, drawn only): jump, press space again in the air: nothing happens, no sound/puff.
2. Release to SHIKAI (R): jump, release space, press space in the air (after a moment): a second jump with a puff and a soft flap. A third press does nothing.
3. Hold space the whole time while jumping: no air jump. Release, press: air jump.
4. Walk off a ledge without jumping, press space in the air: one air jump.
5. Land, jump again: the air jump is available again (landing resets).
6. BANKAI: same as 2. Repeat with Rukia, Byakuya, Gin and Uryu.
7. In water, lava, on a ladder or vines, with an elytra, riding a horse/boat, in creative flight, in spectator: no air jump. Leaving the water upwards and pressing space after 3 ticks in the air gives one.
8. In a screen (inventory, chat) the key does nothing; closing the screen with space held gives no jump.
9. Config: set `"enabled": false` then `/reiatsu reload`: no air jump. `"strength": 2.0`: roughly doubled height. Delete the section: it is rewritten with defaults on the next start.
10. Multiplayer: the other player sees you rise but hears and sees no puff.
