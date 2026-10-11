package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.press;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.selectSlot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxDecals;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxScene;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.client.fx.PetalSwarmRenderer;
import dev.minebleach.reiatsutest.client.fx.ReleaseHold;
import dev.minebleach.reiatsutest.client.fx.RowsFx;
import dev.minebleach.reiatsutest.client.fx.RukiaBankaiClient;
import dev.minebleach.reiatsutest.client.fx.RukiaFeature;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.SwarmFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.fx.BankaiCurves;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.BlockPos;

/**
 * Fix batch of 2026-10-11 (plan A1 to A6), scenario names for {@code -Phold}: {@code fxrk} (Rukia bankai: standing, walking, running, jumping, the ground
 * seal decal made once, ribbons behind her, no ice blocks and no sliding), {@code fxswarm} (Byakuya shikai swarm in the owner's first person view, before
 * and after the clearing), {@code fxbk} (Byakuya bankai sword throw at several moments, first person and third person), {@code fxpink} (Byakuya shikai
 * release: the blade turns pink for 0.4 s, then the petals). {@code fxfix} runs all four. Screenshots {@code run/screenshots/p6_fx_*.png}.
 */
final class Phase6FxFix {
	private static int decalsAtStart;
	private static long slideStartTime;
	private static int slideTicks = -1;
	private static int hold0;
	private static int bursts0;

	private Phase6FxFix() {
	}

	static boolean wants() {
		return Phase6Harness.wants("fxfix") || Phase6Harness.wants("fxrk") || Phase6Harness.wants("fxswarm") || Phase6Harness.wants("fxbk") || Phase6Harness.wants("fxpink");
	}

	private static boolean on(String name) {
		return Phase6Harness.wants("fxfix") || Phase6Harness.wants(name);
	}

	static String f(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private static void resetFx() {
		FxClock.unfreeze();
		FxTimelines.clear();
		FxTimelines.setFreezeAt(-1);
		ScreenFx.clear();
		SwarmFx.clear();
		RowsFx.clear();
		FxDecals.clear();
		RukiaBankaiClient.clear();
	}

	static void steps() {
		step("fxfix: setup", 30, () -> {
			cmd("gamemode creative @s", "time set noon", "gamerule doMobSpawning false", "kill @e[type=pig]", "kill @e[type=zombie]", "tp @s 0.5 -60 0.5 0 12");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(90);
			FxConfig.reduceMotion = false;
			FxScene.requireAnchor = true;
			resetFx();
		});
		if (on("fxrk")) {
			rukia();
		}
		if (on("fxswarm")) {
			swarm();
		}
		if (on("fxpink")) {
			pink();
		}
		if (on("fxbk")) {
			bankai();
		}
	}

	// ------------------------------------------------------------------------------------------------ A1 A2 A3: Rukia bankai

	private static void freezeShot(String name) {
		step(name + " (freeze)", 1, () -> FxClock.freezeIn(0.0));
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release clock)", 2, FxClock::unfreeze);
	}

	private static void keys(boolean forward, boolean sprint, boolean jump) {
		mc.options.forwardKey.setPressed(forward);
		mc.options.sprintKey.setPressed(sprint);
		mc.options.jumpKey.setPressed(jump);
	}

	private static void rukiaView(String v) {
		switch (v) {
			case "front" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				cmd("tp @s 0.5 -60 0.5 0 12");
			}
			default -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 12");
			}
		}
	}

	private static void rukia() {
		step("fxrk: bankai scene", 30, () -> {
			resetFx();
			decalsAtStart = RukiaBankaiClient.decalsMade;
			rukiaView("back");
			// survival: in creative a second jump press within a few ticks toggles flying and the stopping distance would measure the air drag
			cmd("gamemode survival @s", "item replace entity @s hotbar.0 with " + Phase6Harness.RUKIA, "reiatsu cooldowns clear", "reiatsu state bankai rukia", "reiatsu full");
			selectSlot(0);
		});
		step("fxrk: settle 3.5 s", 80, () -> { });
		freezeShot("fx_01_rk_stand_back");
		step("fxrk: ribbons check (behind her)", 2, () -> {
			cCheck("A2 ribbon tip is BEHIND her (forward displacement " + f(RukiaFeature.lastTipForward) + ", height " + f(RukiaFeature.lastTipHeight) + ")",
					() -> RukiaFeature.lastTipForward < -0.1 && RukiaFeature.lastTipHeight > 0.5 ? null : "forward " + RukiaFeature.lastTipForward);
			cCheck("A1 one seal decal made so far (" + (RukiaBankaiClient.decalsMade - decalsAtStart) + ")", () -> RukiaBankaiClient.decalsMade - decalsAtStart == 1 ? null : "decals made " + (RukiaBankaiClient.decalsMade - decalsAtStart));
		});
		step("fxrk: front view", 30, () -> rukiaView("front"));
		freezeShot("fx_01_rk_stand_front");
		step("fxrk: ribbons check front", 2, () -> cCheck("A2 (front view) ribbon tip behind her (" + f(RukiaFeature.lastTipForward) + ")", () -> RukiaFeature.lastTipForward < -0.1 ? null : "forward " + RukiaFeature.lastTipForward));
		step("fxrk: back view again", 30, () -> {
			rukiaView("back");
			cmd("reiatsu cooldowns clear");
		});
		// walking
		step("fxrk: walk", 1, () -> keys(true, false, false));
		step("fxrk: walking 2 s", 40, () -> { });
		step("fxrk: walk shot", 1, () -> {
			shot("fx_02_rk_walk");
			ReiatsuTest.LOGGER.info("[phase6] SHOT walk: decals {}, glow {}, particles {}, decals made {}", FxDecals.count(), FxGlowBatch.live(), FxParticles.live(), RukiaBankaiClient.decalsMade - decalsAtStart);
		});
		step("fxrk: walk 1 s more", 20, () -> { });
		step("fxrk: sprint", 1, () -> keys(true, true, false));
		step("fxrk: sprinting 1.5 s", 30, () -> { });
		step("fxrk: sprint shot", 1, () -> {
			shot("fx_02_rk_run");
			ReiatsuTest.LOGGER.info("[phase6] SHOT run: decals {}, glow {}, particles {}, decals made {}", FxDecals.count(), FxGlowBatch.live(), FxParticles.live(), RukiaBankaiClient.decalsMade - decalsAtStart);
		});
		step("fxrk: sprint on", 20, () -> { });
		step("fxrk: jump", 1, () -> keys(true, true, true));
		step("fxrk: jump key up", 1, () -> keys(true, true, false));
		step("fxrk: jump apex", 5, () -> { });
		step("fxrk: jump shot", 1, () -> {
			shot("fx_02_rk_jump");
			ReiatsuTest.LOGGER.info("[phase6] SHOT jump: decals {}, glow {}, particles {}", FxDecals.count(), FxGlowBatch.live(), FxParticles.live());
		});
		step("fxrk: sprint on 2", 30, () -> { });
		step("fxrk: counts while moving", 1, () -> {
			cCheck("A1 still one seal decal made after walking, sprinting, jumping (" + (RukiaBankaiClient.decalsMade - decalsAtStart) + ")",
					() -> RukiaBankaiClient.decalsMade - decalsAtStart == 1 ? null : "decals made " + (RukiaBankaiClient.decalsMade - decalsAtStart));
			cCheck("A1 decals alive while moving " + FxDecals.count() + " <= 3", () -> FxDecals.count() <= 3 ? null : "decals " + FxDecals.count());
			cCheck("A1 glow sprites alive while moving " + FxGlowBatch.live() + " <= 90", () -> FxGlowBatch.live() <= 90 ? null : "glow " + FxGlowBatch.live());
			cCheck("A1 particles alive while moving " + FxParticles.live() + " <= 260", () -> FxParticles.live() <= 260 ? null : "particles " + FxParticles.live());
			cCheck("A3 no ice blocks near her (" + iceBlocksNear() + ")", () -> iceBlocksNear() == 0 ? null : "ice blocks " + iceBlocksNear());
			cCheck("A3 floor slipperiness is the normal 0.6", () -> {
				Block b = mc.world.getBlockState(mc.player.getBlockPos().down()).getBlock();
				return Math.abs(b.getSlipperiness() - 0.6f) < 1e-4 ? null : "slipperiness " + b.getSlipperiness();
			});
		});
		// stopping distance: no sliding
		step("fxrk: stop keys", 1, () -> {
			keys(false, false, false);
			slideStartTime = mc.world.getTime();
			slideTicks = -1;
		});
		stepUntil("fxrk: slide until still", 2, 100, () -> { }, () -> {
			double v = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
			if (v < 0.03 && slideTicks < 0) {
				slideTicks = (int) (mc.world.getTime() - slideStartTime);
			}
			return slideTicks >= 0;
		});
		step("fxrk: slide check", 1, () -> cCheck("A3 she stops in " + slideTicks + " ticks after releasing the keys (ice would be 25 or more)", () -> slideTicks >= 0 && slideTicks <= 14 ? null : "ticks " + slideTicks));
		freezeShot("fx_02_rk_stopped");
		step("fxrk: end", 5, () -> {
			keys(false, false, false);
			cmd("gamemode creative @s", "reiatsu state sealed", "tp @s 0.5 -60 0.5 0 12");
			resetFx();
		});
	}

	private static int iceBlocksNear() {
		int n = 0;
		BlockPos c = mc.player.getBlockPos();
		for (int x = -6; x <= 6; x++) {
			for (int y = -3; y <= 1; y++) {
				for (int z = -6; z <= 6; z++) {
					Block b = mc.world.getBlockState(c.add(x, y, z)).getBlock();
					if (b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE || b == Blocks.FROSTED_ICE) {
						n++;
					}
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------------------------------------ A4: swarm in the first person view

	private static void toBase() {
		resetFx();
		cmd("item replace entity @s hotbar.1 with " + Phase6Harness.BYAKUYA, "reiatsu cooldowns clear", "reiatsu state sealed");
		selectSlot(1);
	}

	private static void swarm() {
		step("fxswarm: base, first person", 50, () -> {
			toBase();
			FxTune.unset("swarm.fpClearing");
		});
		step("fxswarm: base state", 30, () -> {
			cmd("reiatsu state base byakuya", "reiatsu full", "tp @s 0.5 -60 0.5 0 6");
			mc.options.setPerspective(Perspective.FIRST_PERSON);
		});
		step("fxswarm: R", 1, () -> press(ReiatsuKeys.RELEASE));
		step("fxswarm: release over", 60, () -> { });
		freezeShot("fx_03_swarm_fp_after_pitch6");
		step("fxswarm: checks", 2, () -> {
			ReiatsuTest.LOGGER.info("[phase6] SHOT swarm fp: petals {}, culled {}, nearest {}", PetalSwarmRenderer.lastPetals, PetalSwarmRenderer.lastFpCulled, f(PetalSwarmRenderer.lastFpNearest));
			cCheck("A4 first person clearing active (" + PetalSwarmRenderer.lastFp + ")", () -> PetalSwarmRenderer.lastFp ? null : "not active");
			cCheck("A4 petals removed near the camera (" + PetalSwarmRenderer.lastFpCulled + ")", () -> PetalSwarmRenderer.lastFpCulled > 30 ? null : "culled " + PetalSwarmRenderer.lastFpCulled);
			cCheck("A4 nearest drawn petal at least 1.5 blocks from the camera (" + f(PetalSwarmRenderer.lastFpNearest) + ")", () -> PetalSwarmRenderer.lastFpNearest >= 1.5 ? null : "nearest " + PetalSwarmRenderer.lastFpNearest);
		});
		step("fxswarm: look down", 20, () -> cmd("tp @s 0.5 -60 0.5 0 35"));
		freezeShot("fx_03_swarm_fp_after_pitch35");
		step("fxswarm: look up", 20, () -> cmd("tp @s 0.5 -60 0.5 0 -25"));
		freezeShot("fx_03_swarm_fp_after_pitchm25");
		// the same views without the clearing (the old look)
		step("fxswarm: clearing off", 20, () -> {
			FxTune.override("swarm.fpClearing", 0.0);
			cmd("tp @s 0.5 -60 0.5 0 6");
		});
		freezeShot("fx_03_swarm_fp_before_pitch6");
		step("fxswarm: clearing off look down", 20, () -> cmd("tp @s 0.5 -60 0.5 0 35"));
		freezeShot("fx_03_swarm_fp_before_pitch35");
		step("fxswarm: clearing on again, third person", 20, () -> {
			FxTune.unset("swarm.fpClearing");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 0.5 0 12");
		});
		freezeShot("fx_03_swarm_tp_idle");
		step("fxswarm: third person check", 2, () -> cCheck("A4 third person view is not cleared (culled " + PetalSwarmRenderer.lastFpCulled + ", fp " + PetalSwarmRenderer.lastFp + ")",
				() -> !PetalSwarmRenderer.lastFp && PetalSwarmRenderer.lastFpCulled == 0 ? null : "fp " + PetalSwarmRenderer.lastFp));
		// barrier dome seen from inside, first person
		step("fxswarm: dome, first person", 20, () -> {
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			cmd("tp @s 0.5 -60 0.5 0 12", "reiatsu cooldowns clear", "reiatsu full");
		});
		step("fxswarm: H (dome)", 1, () -> press(ReiatsuKeys.SLOTS[1]));
		step("fxswarm: dome forms", 50, () -> { });
		freezeShot("fx_03_dome_fp_after");
		step("fxswarm: dome off", 20, () -> FxTune.override("swarm.fpClearing", 0.0));
		freezeShot("fx_03_dome_fp_before");
		step("fxswarm: end", 5, () -> {
			FxTune.unset("swarm.fpClearing");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("reiatsu state sealed", "tp @s 0.5 -60 0.5 0 12");
			resetFx();
		});
	}

	// ------------------------------------------------------------------------------------------------ A6: the pink blade

	private static void pinkSeries(String prefix, double[] secs, String view) {
		step(prefix + " (prep)", 50, Phase6FxFix::toBase);
		step(prefix + " (base)", 30, () -> {
			cmd("reiatsu state base byakuya", "reiatsu full");
			if (view.equals("fp")) {
				mc.options.setPerspective(Perspective.FIRST_PERSON);
				mc.options.hudHidden = false; // F1 hides the hand: the pink blade in the hand must be seen
				cmd("tp @s 0.5 -60 0.5 0 6");
			} else if (view.equals("front")) {
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				cmd("tp @s 0.5 -60 0.5 0 6");
			} else {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 6");
			}
		});
		step(prefix + " (note)", 1, () -> hold0 = ReleaseHold.started);
		step(prefix + " (R)", 1, () -> press(ReiatsuKeys.RELEASE));
		// the freeze is armed in the very tick in which the hold begins (the polling condition runs at the end of that tick), so the times are exact
		stepUntil(prefix + " (event)", 1, 20 * 6, () -> { }, () -> {
			if (ReleaseHold.started > hold0) {
				double elapsed = Math.max(0, ReleaseHold.progress(mc.player.getId())) * FxTune.d("release.pinkTime", 0.4); // frames that ran before this tick's poll
				FxClock.freezeIn(Math.max(0, secs[0] - elapsed));
				return true;
			}
			return false;
		});
		stepUntil(prefix + " (frozen 0)", 2, 20 * 12, () -> { }, () -> FxClock.frozen);
		step(prefix + " (shot 0)", 3, () -> {
			shot(prefix + "_t" + Math.round(secs[0] * 1000));
			pinkCheck(prefix, secs[0]);
		});
		for (int k = 1; k < secs.length; k++) {
			final int kk = k;
			final double dt = secs[k] - secs[k - 1];
			step(prefix + " (advance " + secs[k] + ")", 1, () -> FxClock.freezeIn(dt));
			stepUntil(prefix + " (frozen " + secs[k] + ")", 2, 20 * 12, () -> { }, () -> FxClock.frozen);
			step(prefix + " (shot " + secs[k] + ")", 3, () -> {
				shot(prefix + "_t" + Math.round(secs[kk] * 1000));
				pinkCheck(prefix, secs[kk]);
			});
		}
		step(prefix + " (release clock)", 2, FxClock::unfreeze);
	}

	private static void pinkCheck(String prefix, double sec) {
		int id = mc.player.getId();
		double p = ReleaseHold.progress(id);
		if (sec < 0.38) {
			cCheck(prefix + " t " + f(sec) + ": hold active (progress " + f(p) + "), the swarm is not made yet (" + SwarmFx.states().size() + ")", () -> p >= 0 && SwarmFx.states().isEmpty() ? null : "progress " + p);
		} else if (sec > 0.45) {
			cCheck(prefix + " t " + f(sec) + ": hold over, the swarm exists (" + SwarmFx.states().size() + ")", () -> p < 0 && SwarmFx.states().size() == 1 ? null : "progress " + p + " swarms " + SwarmFx.states().size());
		}
	}

	private static void pink() {
		step("fxpink: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			FxTune.unset("swarm.fpClearing");
		});
		pinkSeries("fx_04_pink_fp", new double[] {0.05, 0.2, 0.32, 0.39, 0.45, 0.7}, "fp");
		step("fxpink: hud off again", 2, () -> mc.options.hudHidden = true);
		pinkSeries("fx_04_pink_front", new double[] {0.05, 0.2, 0.32, 0.39, 0.45, 0.7}, "front");
		step("fxpink: checks", 2, () -> cCheck("A6 pink tint was applied to the item model (" + ReleaseHold.tintedFrames + " frames)", () -> ReleaseHold.tintedFrames > 0 ? null : "never tinted"));
		step("fxpink: end", 5, () -> {
			cmd("reiatsu state sealed", "tp @s 0.5 -60 0.5 0 12");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			resetFx();
		});
	}

	// ------------------------------------------------------------------------------------------------ A5: the sword throw

	private static void bankaiSeries(String prefix, int[] ms, String view) {
		step(prefix + " (prep)", 50, Phase6FxFix::toBase);
		step(prefix + " (base)", 30, () -> {
			cmd("reiatsu state base byakuya", "reiatsu full");
			switch (view) {
				case "fp" -> {
					mc.options.setPerspective(Perspective.FIRST_PERSON);
					cmd("tp @s 0.5 -60 0.5 0 14");
				}
				case "front" -> {
					mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
					cmd("tp @s 0.5 -60 0.5 0 14");
				}
				default -> {
					mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
					cmd("tp @s 0.5 -60 0.5 0 14");
				}
			}
		});
		step(prefix + " (R)", 1, () -> press(ReiatsuKeys.RELEASE));
		step(prefix + " (full)", 70, () -> cmd("reiatsu full", "reiatsu cooldowns clear"));
		step(prefix + " (arm first)", 2, () -> {
			bursts0 = RowsFx.bursts;
			FxTimelines.setFreezeAt(ms[0]);
		});
		step(prefix + " (G)", 1, () -> press(ReiatsuKeys.BANKAI));
		step(prefix + " (G retry)", 5, () -> {
			if (RowsFx.states().isEmpty()) {
				ReiatsuTest.LOGGER.warn("[phase6] bankai key press was not seen, pressing again");
				press(ReiatsuKeys.BANKAI);
			}
		});
		stepUntil(prefix + " (frozen 0)", 2, 20 * 12, () -> { }, () -> FxClock.frozen);
		step(prefix + " (shot 0)", 3, () -> {
			shot(prefix + "_t" + ms[0]);
			swordCheck(prefix, ms[0]);
		});
		for (int k = 1; k < ms.length; k++) {
			final int kk = k;
			final double dt = (ms[k] - ms[k - 1]) / 1000.0;
			step(prefix + " (advance " + ms[k] + ")", 1, () -> {
				FxTimelines.setFreezeAt(-1);
				FxClock.freezeIn(dt);
			});
			stepUntil(prefix + " (frozen " + ms[k] + ")", 2, 20 * 20, () -> { }, () -> FxClock.frozen);
			step(prefix + " (shot " + ms[k] + ")", 3, () -> {
				shot(prefix + "_t" + ms[kk]);
				swordCheck(prefix, ms[kk]);
			});
		}
		step(prefix + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	private static void swordCheck(String prefix, int ms) {
		RowsFx.State st = RowsFx.of(mc.player.getId());
		if (st == null) {
			cCheck(prefix + " t " + ms + ": rows state exists", () -> "no rows state");
			return;
		}
		double sw = st.swordTime();
		double hs = FxTune.d("bankai.hiltScale", 1.8);
		double h = BankaiCurves.swordHeight(sw, RowsFx.swordHang(), RowsFx.swordEndY(hs));
		ReiatsuTest.LOGGER.info("[phase6] SHOT {} t {} ms: sword time {} height {} contact {} rows time {} bursts {}", prefix, ms, f(sw), f(h), f(RowsFx.swordContact(hs)), f(st.time()), RowsFx.bursts - bursts0);
		if (ms >= 400 && ms <= 600) {
			cCheck(prefix + " t " + ms + ": the sword hangs in front (height " + f(h) + " above the ground)", () -> h > 0.7 ? null : "height " + h);
		}
		if (ms <= 1000) {
			cCheck(prefix + " t " + ms + ": no row blade has erupted yet (bursts " + (RowsFx.bursts - bursts0) + ")", () -> RowsFx.bursts - bursts0 == 0 ? null : "bursts " + (RowsFx.bursts - bursts0));
		}
		if (ms >= 2200) {
			cCheck(prefix + " t " + ms + ": the hilt stays in the ground, " + f(h + BankaiCurves.HILT_LENGTH * hs) + " blocks of it above (rows bursts " + (RowsFx.bursts - bursts0) + ")",
					() -> Math.abs(h + BankaiCurves.HILT_LENGTH * hs - FxTune.d("bankai.hiltRemain", 0.38)) < 0.02 && RowsFx.bursts - bursts0 > 0 ? null : "remain " + (h + BankaiCurves.HILT_LENGTH * hs));
		}
	}

	private static void bankai() {
		step("fxbk: setup", 20, () -> {
			FxConfig.bankaiBladeCount = 200;
			FxTune.unset("swarm.fpClearing");
		});
		int[] ms = {100, 250, 450, 700, 900, 1050, 1250, 1500, 2000, 2600};
		bankaiSeries("fx_05_sword_front", ms, "front");
		bankaiSeries("fx_05_sword_back", new int[] {250, 700, 1050, 1500, 2600}, "back");
		bankaiSeries("fx_05_sword_fp", new int[] {150, 450, 900, 1250, 2000}, "fp");
		step("fxbk: end", 20, () -> {
			cmd("reiatsu state sealed", "tp @s 0.5 -60 0.5 0 12");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			resetFx();
		});
	}
}
