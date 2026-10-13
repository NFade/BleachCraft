# Hakuteiken first-person view fix (branch fix/hakuteiken-fp)

## Verdict on the hypothesis

Partly confirmed, with a correction. The wings do not lag: `HakuteikenFx.pose()` uses `lerpAngleDegrees(td, prevBodyYaw, bodyYaw)`, so they are smooth with the partial tick. But they hang off the **body** yaw, not the camera yaw, and in vanilla the body yaw differs from the head yaw:

- standing still: the body trails the head by up to 75 degrees (`LivingEntity.turnHead` limit);
- moving (strafe, backwards, or any walk where the movement direction differs from the look direction): the body yaw follows the movement direction.

The wing sheets stand in a vertical plane 0.15 m behind the eye (`hak.rootBack` 0.15, roots at x +-0.12, y 1.35; eye at 1.62), are 4.3 m wide and 4.46 m high (OBJ bounds: x 0..4.277, y -0.656..4.459, z 0..0.8, z is forward). When the body is rotated by theta against the camera, that plane tilts into the frustum.

## Numbers (offline ray test over the real OBJ meshes, not a game frame)

Eye at 1.62, pitch 0, 16:9, `hak.*` defaults, fan = 1, geometry only (texture alpha not applied, so an upper bound for the sheet itself):

| body yaw vs camera | wing sheets, share of screen covered (FOV 70) | FOV 110 |
|---|---|---|
| 0, 10, 20, 30 deg | 0 % | 0 % (0, 15) |
| 45 deg | 10.6 % | 18.4 % |
| 60 deg | 22.0 % | - |
| 75 deg | 17.3 % (0.3 m extra back: 31.7 %) | 6.7 % |

The halo covers 6 % at 75 deg. On top of the meshes, `emitGlow` puts additive sprites at the wing roots: a 1.6 m `GLOW_SOFT` at alpha 0.35 centred at (+-0.12, 1.35, -0.15) of the rig. Rotated by 75 deg that point is about 0.08 m IN FRONT of the eye, so the haze fills the whole screen. Also 9 feather streaks per wing (width 0.55) plus 9 soft sprites per wing, the additive overglow pass (alpha 0.35) and the 1.3 m halo bloom ring plane. These add to the sheet coverage and explain the "covers the screen" impression. (Glow part is computed by hand, not rendered: UNVERIFIED.)

## Fix

Only for the owner seen from his own first-person camera (`!camera.isThirdPerson()` and `camera.getFocusedEntity().getId() == state.ownerId`, so spectating the owner also counts). Third person and other players take the old path, byte for byte.

1. Rig yaw: `limitYaw(bodyYaw, cameraYaw, 15 deg)`, continuous in both inputs (smooth with the partial tick), identity while the body is within 15 deg of the camera. Wings still follow the body inside that window.
2. Rig pushed back 0.35 m along its own backward axis (first person only; the body is not visible there).
3. Opacity by view depth, `fade(depth, 0.8, 3.0)` (smoothstep, 0 behind the eye): per vertex for the wing sheets (solid and overglow pass) and the halo, and for the glow sprites (halo bloom ring and soft glow, root glows, feather streaks and soft spots via the point depth). Safety net for what still lies near the eye.
4. Shed and falling feathers of `HakuteikenFx.frame` come off the same guarded rig and are not spawned when the guard hides the spawn point.
5. Reduce motion / photosensitive safe (`FxConfig.reduceMotion`, `photosensitiveSafe`): yaw limit halved (7.5 deg) and the fade band widened x1.3. Everything is a reduction, nothing is added.
6. Tunables in `fx_override.json` (the `FxTune` route): `hak.fpGuard` (0 turns the guard off), `hak.fpYawMax` (deg), `hak.fpBack` (m), `hak.fpClear`, `hak.fpFull` (m). All existing `hak.*` keys are untouched and still scale the guarded rig.

Not changed: the blade, the hand ignition glow (white core 1.2 m, flare 3 m at the hand, 0.6 m ahead of the eye) and the line. They are meant to be in front of the player. If the ignition flare still feels too large in first person, `hak.flareSize` / `hak.coreSize` / `hak.coreGlow` are the knobs (not touched).

After the fix, the same ray test (yaw clamped to 15 deg, back 0.35 m) gives 0.0 % coverage for wings and halo for every body offset (0 to 180 deg), at FOV 70 and FOV 110.

## Changed files (worktree D:\MineBleach\.claude\worktrees\hakuteiken-fp\mod)

- `src/main/java/dev/minebleach/reiatsutest/core/fx/HakuteikenFirstPerson.java` (new, pure math: `limitYaw`, `wrapPi`, `forward`, `depth`, `fade`, `yawLimit`).
- `src/test/java/dev/minebleach/reiatsutest/core/fx/HakuteikenFirstPersonTest.java` (new, 11 tests).
- `src/client/java/dev/minebleach/reiatsutest/client/fx/WingHaloRenderer.java` (inner `FpView`, rig pose for `draw` and `emitGlow`, per-vertex fade, `guardedPose` / `guardHides`).
- `src/client/java/dev/minebleach/reiatsutest/client/fx/HakuteikenFx.java` (shed / dissolve feathers use the guarded pose; the inner `if` bodies are not re-indented).

## Tests and build

- 11 new JUnit tests: angle wrap, yaw clamp (inside limit, both sides, +-180 seam, continuity over a full turn, zero limit), Minecraft forward convention, fade band, reduce-motion halving, plus three model tests on a wing point grid built from the OBJ bounds: unfixed rig at 45 and 75 deg puts wing points in a 55 deg view cone, fixed rig puts none (opacity above 2 %) for every body offset in -180..180 step 5, and the identity inside the limit.
- `./gradlew build` from `mod/`: BUILD SUCCESSFUL (2m55s), all tests pass including `CorePurityTest`. Nothing committed, pushed or launched.

## UNVERIFIED (no game frame was rendered)

- Whether the wing glow sprites and the mesh together really look as described in a real first-person frame; the screen coverage numbers are an offline ray test of the OBJ geometry without texture alpha, and the glow numbers are hand calculations.
- That the visual result still feels right in first person (wings behind and to the side, now mostly out of view by design) and that third person and other players are unchanged in pixels (code paths are identical for them, but not compared in game).
- `Camera.getYaw()` equals the displayed first-person yaw during `ScreenFx` shake or modded cameras; the hand ignition flare may still be large in first person.
- Pitch other than 0 was not covered by the ray test (the depth fade uses the real camera pitch).
