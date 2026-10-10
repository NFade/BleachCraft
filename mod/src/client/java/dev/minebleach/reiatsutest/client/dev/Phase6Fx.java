package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.graphics;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxMath;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.GlowSprite;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import java.util.Random;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;

/** Phase 6 step 0 scenarios: glow atlas test, GRADE / SHAKE behaviour, glow batch cost. */
final class Phase6Fx {
	private static final String[] TINTS = {"#FFFFFF", "#9ED3F0", "#F9C8F6", "#FFE9FB"};

	private Phase6Fx() {
	}

	private static void clearFx() {
		FxTimelines.clear();
		FxClock.unfreeze();
	}

	/** One grid of sprites on a vertical plane facing the camera (south looking, +z). */
	private static void grid(GlowSprite[] sprites, int cols, double z, double size, double spacing, double yTop, boolean tinted) {
		double x0 = -(cols - 1) * spacing * 0.5 + 0.5;
		for (int i = 0; i < sprites.length; i++) {
			int c = i % cols;
			int r = i / cols;
			double x = x0 + (cols - 1 - c) * spacing; // +x is to the left when looking south: keep the table order left to right
			double y = yTop - r * spacing;
			String tint = tinted ? TINTS[i % TINTS.length] : "#FFFFFF";
			// Ground type sprites are shown as camera facing quads here: the test is about the texture, not the orientation.
			FxGlowBatch.sprite(sprites[i]).at(x, y, z).size(size).life(600).curve(0.01, 0.0).peak(0.62).color(tint).spawn();
		}
	}

	private static void atlasSet(int variant) {
		clearFx();
		GlowSprite[] all = GlowSprite.values();
		switch (variant) {
			case 0 -> grid(all, 9, 15, 2.0, 3.2, -54.0, true); // 17 sprites in 9 columns x 2 rows, size 2 blocks, no wall
			case 1 -> grid(all, 6, 5, 0.5, 0.75, -57.4, true); // size 0.5
			case 3 -> {
				// depth test: a stone wall in the middle (z 9); behind it (z 14): a star6 straddling the wall edge, a ring and a glow
				// completely hidden; in front of the wall (z 6): a glow and a star4 that must stay visible over the stone
				FxGlowBatch.sprite(GlowSprite.STAR6).at(3.0, -56.0, 14).size(7).life(600).curve(0.01, 0.0).color("#9ED3F0").spawn();
				FxGlowBatch.sprite(GlowSprite.RING_THIN).at(0.5, -55.0, 14).size(6).life(600).curve(0.01, 0.0).color("#FFFFFF").spawn();
				FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(-2.0, -57.5, 14).size(6).life(600).curve(0.01, 0.0).color("#F9C8F6").spawn();
				FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(0.5, -57.0, 6).size(3).life(600).curve(0.01, 0.0).color("#FFE9FB").spawn();
				FxGlowBatch.sprite(GlowSprite.STAR4).at(-1.8, -55.0, 6).size(3).life(600).curve(0.01, 0.0).color("#FFFFFF").spawn();
			}
			default -> {
				GlowSprite[] big = {GlowSprite.GLOW_SOFT, GlowSprite.STAR4, GlowSprite.RING_THIN, GlowSprite.RING_SOFT, GlowSprite.FROST_SIGIL,
						GlowSprite.GLOW_CORE};
				grid(big, 3, 36, 8.0, 11.0, -47.0, true);
			}
		}
	}

	static void atlasSteps() {
		step("atlas: view", 20, () -> {
			cmd("tp @s 0.5 -60 0.5 0 -2");
			mc.options.hudHidden = true;
		});
		String[] names = {"p6_00_atlas_test", "p6_00_atlas_midnight", "p6_00_atlas_fabulous", "p6_00_atlas_fabulous_midnight"};
		boolean[] night = {false, true, false, true};
		GraphicsMode[] gm = {GraphicsMode.FANCY, GraphicsMode.FANCY, GraphicsMode.FABULOUS, GraphicsMode.FABULOUS};
		for (int i = 0; i < 4; i++) {
			final int k = i;
			step("atlas: " + names[i] + " setup", 70, () -> {
				graphics(gm[k]);
				cmd(night[k] ? "time set midnight" : "time set noon");
				atlasSet(0);
			});
			step("atlas: " + names[i] + " shot", 4, () -> {
				shot(names[k].substring(3));
				int drawn = FxGlowBatch.lastDrawn;
				cCheck("atlas " + names[k] + " draws sprites (drawn=" + drawn + ", live=" + FxGlowBatch.live() + ")",
						() -> drawn >= 17 ? null : "only " + drawn + " sprites drawn");
			});
		}
		step("atlas: size 0.5 set", 50, () -> {
			graphics(GraphicsMode.FANCY);
			cmd("time set noon");
			atlasSet(1);
		});
		step("atlas: size 0.5 shot", 3, () -> shot("00_atlas_s05"));
		step("atlas: size 8 set", 50, () -> atlasSet(2));
		step("atlas: size 8 shot", 3, () -> shot("00_atlas_s8"));
		step("atlas: size 8 night set", 50, () -> cmd("time set midnight"));
		step("atlas: size 8 night shot", 3, () -> shot("00_atlas_s8_midnight"));
		// depth test against a wall, Fancy and Fabulous
		step("atlas: depth wall", 60, () -> {
			clearFx();
			cmd("fill -3 -60 9 3 -52 9 minecraft:stone", "time set noon");
			graphics(GraphicsMode.FANCY);
			atlasSet(3);
		});
		step("atlas: depth shot fancy", 3, () -> {
			dev.minebleach.reiatsutest.client.fx.FxProbe.armed = true;
			shot("00_atlas_depth");
		});
		step("atlas: probe wait fancy", 3, () -> { });
		step("atlas: depth fabulous", 70, () -> {
			graphics(GraphicsMode.FABULOUS);
			atlasSet(3);
		});
		step("atlas: depth shot fabulous", 3, () -> {
			dev.minebleach.reiatsutest.client.fx.FxProbe.armed = true;
			shot("00_atlas_depth_fabulous");
		});
		step("atlas: probe wait", 3, () -> { });
		step("atlas: cleanup", 4, () -> {
			clearFx();
			cmd("fill -3 -60 9 3 -52 9 minecraft:air", "time set noon");
			mc.options.hudHidden = false;
			graphics(GraphicsMode.FANCY);
		});
	}

	static void gradeSteps() {
		step("grade: hand item and view", 40, () -> {
			clearFx();
			Phase6Harness.selectSlot(0);
			cmd("tp @s 0.5 -60 0.5 0 5");
			mc.options.hudHidden = false;
			ScreenFx.clear();
		});
		step("grade: reference (no effect)", 4, () -> shot("00_grade_off"));
		step("grade: GRADE night colour s=0.6", 25, () -> ScreenFx.grade(FxMath.hex("#1C2540"), 0.6, 0.2, 1.0));
		step("grade: shot (world dark, hand + HUD not)", 3, () -> shot("00_grade_on"));
		step("grade: reduce grade, clear", 6, () -> {
			ScreenFx.clear();
		});
		step("grade: SHAKE reference at t=0", 12, () -> {
			FxClock.unfreeze();
			ScreenFx.shake(1.5, 1.0, 1.0);
			FxClock.freezeIn(0.0);
		});
		step("grade: SHAKE shot at t=0 (no offset)", 3, () -> shot("00_shake_t0"));
		step("grade: SHAKE start peak", 10, () -> {
			ScreenFx.clear();
			FxClock.unfreeze();
			ScreenFx.shake(1.5, 1.0, 1.0);
			FxClock.freezeIn(1.0 / 56.0); // quarter period of the 14 Hz yaw sine: yaw at its peak
		});
		step("grade: SHAKE peak shot (world and hand moved)", 3, () -> {
			shot("00_shake_peak");
			double[] o = new double[2];
			ScreenFx.shakeOffsets(o);
			cCheck("shake yaw offset at the peak is ~ +1.5 deg (" + o[0] + ")", () -> Math.abs(o[0] - 1.5) < 0.15 ? null : "yaw " + o[0]);
		});
		step("grade: reduce motion zeroes the shake", 4, () -> {
			ScreenFx.clear();
			FxClock.unfreeze();
			FxConfig.reduceMotion = true;
			ScreenFx.shake(1.5, 1.0, 1.0);
			cCheck("reduce motion: no shake registered", () -> ScreenFx.shaking() ? "still shaking" : null);
			FxConfig.reduceMotion = false;
			ScreenFx.clear();
		});
	}

	static void perfSteps() {
		step("perf: 4096 glow sprites", 20, () -> {
			clearFx();
			cmd("tp @s 0.5 -60 0.5 0 -10");
			mc.options.hudHidden = true;
			graphics(GraphicsMode.FANCY);
			FxConfig.maxGlowSprites = 4096;
			Random r = new Random(7);
			GlowSprite[] pool = {GlowSprite.GLOW_SOFT, GlowSprite.STAR4, GlowSprite.STAR6, GlowSprite.SPECK, GlowSprite.PETAL_GLOW,
					GlowSprite.RING_THIN, GlowSprite.MIST, GlowSprite.GLOW_CORE};
			for (int i = 0; i < 4096; i++) {
				double x = (r.nextDouble() - 0.5) * 30;
				double y = -58 + r.nextDouble() * 12;
				double z = 4 + r.nextDouble() * 30;
				FxGlowBatch.sprite(pool[i % pool.length]).at(x, y, z).size(0.4 + r.nextDouble() * 1.2).life(600).curve(0.01, 0.0)
						.peak(0.35).color(TINTS[i % TINTS.length]).twinkle().spawn();
			}
		});
		step("perf: warm up", 90, () -> { });
		step("perf: measure 400 frames", 1, () -> {
			FxClient.Stats.reset();
			FxGlowBatch.maxMs = 0;
			FxGlowBatch.drawnTotal = 0;
			perfFrames0 = FxClock.frame;
			perfStart = System.nanoTime();
			perfGlowSum = 0;
		});
		Phase6Harness.stepUntil("perf: wait", 5, 20 * 40, () -> { }, () -> FxClock.frame - perfFrames0 >= 400);
		step("perf: result", 3, () -> {
			double wall = (System.nanoTime() - perfStart) / 1.0e6 / Math.max(1, FxClock.frame - perfFrames0);
			double fxMs = FxClient.Stats.meanMs();
			shot("00_glow_4096");
			ReiatsuTest.LOGGER.info("[phase6] PERF glow 4096: live={} lastDrawn={} glowBatch.maxMs={} reiatsu_fx mean={} max={} ms, frame wall {} ms",
					FxGlowBatch.live(), FxGlowBatch.lastDrawn, fmt(FxGlowBatch.maxMs), fmt(fxMs), fmt(FxClient.Stats.maxMs), fmt(wall));
			cCheck("4096 sprites: reiatsu_fx mean " + fmt(fxMs) + " ms < 1.0 ms", () -> fxMs < 1.0 ? null : "mean " + fmt(fxMs) + " ms");
			clearFx();
			mc.options.hudHidden = false;
		});
	}

	private static long perfFrames0;
	private static long perfStart;
	private static double perfGlowSum;

	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.3f", v);
	}
}
