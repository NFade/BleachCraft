package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.graphics;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.wants;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.GlowSprite;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;

/**
 * Dev-only harness of the Uryu effects (URYU_VFX_STORYBOARD section 6), a thin layer on the phase 6 engine: {@code gradlew runPhase7
 * [-Phold=<scenario>]} starts {@link Phase6Harness} with {@code -Dreiatsu.phase7=true}, whose script then also contains the scenarios of this
 * class. The engine ({@code step}, {@code cmd}, {@code cCheck}, {@code shotAt} ...) is the phase 6 one; the screenshots are written as
 * {@code run/screenshots/p7_<name>.png} ({@link #shot7}). Scenario names (comma list in {@code -Phold}, default {@code uryu_atlas}):
 * {@code uryu_atlas} (U-FX 0). The scenarios of U-FX 2 to 10 are added here by their steps (storyboard 6 lists them).
 */
final class Phase7Uryu {
	private static final String[] TINTS = {"#FFFFFF", "#9DEBFF", "#4FC8FF", "#E4FAFF"};

	private Phase7Uryu() {
	}

	/** True when the harness was started with {@code -Dreiatsu.phase7=true} (the {@code runPhase7} task). */
	static boolean enabled() {
		return Boolean.getBoolean("reiatsu.phase7");
	}

	/** Called by {@link Phase6Harness} at the end of its script when {@link #enabled()}. */
	static void steps() {
		ReiatsuTest.LOGGER.info("[phase7] scenarios of the Uryu harness (hold = {})", System.getProperty("reiatsu.fx.hold", "all"));
		if (wants("uryu_atlas")) {
			atlasSteps();
		}
	}

	/** Like {@code Phase6Harness.shot} with the {@code p7_} prefix. */
	static void shot7(String name) {
		String file = "p7_" + name + ".png";
		ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), msg -> { });
		ReiatsuTest.LOGGER.info("[phase7] screenshot {}", file);
	}

	private static void clearFx() {
		FxTimelines.clear();
		FxClock.unfreeze();
	}

	/** Sprites of atlas 1 in a grid on a vertical plane facing the camera (south looking, +z); {@code size} is the larger side in blocks. */
	private static void grid(GlowSprite[] sprites, int cols, double z, double size, double spacing, double yTop) {
		double x0 = -(cols - 1) * spacing * 0.5 + 0.5;
		for (int i = 0; i < sprites.length; i++) {
			int c = i % cols;
			int r = i / cols;
			double x = x0 + (cols - 1 - c) * spacing; // +x is to the left when looking south: keep the table order left to right
			double y = yTop - r * spacing;
			double w = size / Math.max(1.0, sprites[i].aspect); // tall sprites (lens, flake, needle) are scaled to the same height
			FxGlowBatch.sprite(sprites[i]).at(x, y, z).size(w).life(600).curve(0.01, 0.0).peak(0.7).color(TINTS[i % TINTS.length]).spawn();
		}
	}

	private static void atlasSet(int variant) {
		clearFx();
		GlowSprite[] all = GlowSprite.ofAtlas(1);
		switch (variant) {
			case 0 -> grid(all, 8, 15, 2.0, 3.0, -52.5);
			case 1 -> grid(all, 6, 5, 0.5, 0.75, -57.0);
			case 3 -> {
				// depth test: a stone wall in the middle (z 9); behind it (z 14) a zeichen_star straddling the wall edge, a web wheel and a lens
				// completely hidden; in front of the wall (z 6): a burst and a diamond star that must stay visible over the stone
				FxGlowBatch.sprite(GlowSprite.ZEICHEN_STAR).at(3.0, -56.0, 14).size(7).life(600).curve(0.01, 0.0).color("#9DEBFF").spawn();
				FxGlowBatch.sprite(GlowSprite.WEB_WHEEL).at(0.5, -55.0, 14).size(6).life(600).curve(0.01, 0.0).color("#FFFFFF").spawn();
				FxGlowBatch.sprite(GlowSprite.LENS).at(-2.0, -57.5, 14).size(3).life(600).curve(0.01, 0.0).color("#4FC8FF").spawn();
				FxGlowBatch.sprite(GlowSprite.BURST_RAYS).at(0.5, -57.0, 6).size(3).life(600).curve(0.01, 0.0).color("#E4FAFF").spawn();
				FxGlowBatch.sprite(GlowSprite.DIAMOND_STAR).at(-1.8, -55.0, 6).size(3).life(600).curve(0.01, 0.0).color("#FFFFFF").spawn();
			}
			default -> {
				GlowSprite[] big = {GlowSprite.ZEICHEN_STAR, GlowSprite.WEB_WHEEL, GlowSprite.BURST_RAYS, GlowSprite.PLATFORM_DISC, GlowSprite.LENS,
						GlowSprite.CHEVRONS};
				grid(big, 3, 36, 8.0, 11.0, -47.0);
			}
		}
	}

	/** U-FX 0: every Quincy sprite at 2, 0.5 and 8 blocks, noon and midnight, Fancy and Fabulous, and the depth test against a wall. */
	static void atlasSteps() {
		step("uryu atlas: view", 20, () -> {
			cmd("tp @s 0.5 -60 0.5 0 -2");
			mc.options.hudHidden = true;
		});
		int sprites = GlowSprite.ofAtlas(1).length;
		String[] names = {"00_atlas_uryu_noon", "00_atlas_uryu_midnight", "00_atlas_uryu_fabulous", "00_atlas_uryu_fabulous_midnight"};
		boolean[] night = {false, true, false, true};
		GraphicsMode[] gm = {GraphicsMode.FANCY, GraphicsMode.FANCY, GraphicsMode.FABULOUS, GraphicsMode.FABULOUS};
		for (int i = 0; i < 4; i++) {
			final int k = i;
			step("uryu atlas: " + names[i] + " setup", 70, () -> {
				graphics(gm[k]);
				cmd(night[k] ? "time set midnight" : "time set noon");
				atlasSet(0);
			});
			step("uryu atlas: " + names[i] + " shot", 4, () -> {
				shot7(names[k]);
				int drawn = FxGlowBatch.lastDrawn;
				cCheck("uryu atlas " + names[k] + " draws all " + sprites + " sprites (drawn=" + drawn + ", live=" + FxGlowBatch.live() + ")",
						() -> drawn >= sprites ? null : "only " + drawn + " sprites drawn");
			});
		}
		step("uryu atlas: size 0.5 set", 50, () -> {
			graphics(GraphicsMode.FANCY);
			cmd("time set noon");
			atlasSet(1);
		});
		step("uryu atlas: size 0.5 shot", 3, () -> shot7("00_atlas_uryu_s05"));
		step("uryu atlas: size 8 set", 50, () -> atlasSet(2));
		step("uryu atlas: size 8 shot", 3, () -> shot7("00_atlas_uryu_s8"));
		step("uryu atlas: size 8 night set", 50, () -> cmd("time set midnight"));
		step("uryu atlas: size 8 night shot", 3, () -> shot7("00_atlas_uryu_s8_midnight"));
		step("uryu atlas: depth wall", 60, () -> {
			clearFx();
			cmd("fill -3 -60 9 3 -52 9 minecraft:stone", "time set noon");
			graphics(GraphicsMode.FANCY);
			atlasSet(3);
		});
		step("uryu atlas: depth shot fancy", 3, () -> {
			dev.minebleach.reiatsutest.client.fx.FxProbe.armed = true;
			shot7("00_atlas_uryu_depth");
		});
		step("uryu atlas: probe wait fancy", 3, () -> { });
		step("uryu atlas: depth fabulous", 70, () -> {
			graphics(GraphicsMode.FABULOUS);
			atlasSet(3);
		});
		step("uryu atlas: depth shot fabulous", 3, () -> {
			dev.minebleach.reiatsutest.client.fx.FxProbe.armed = true;
			shot7("00_atlas_uryu_depth_fabulous");
		});
		step("uryu atlas: probe wait", 3, () -> { });
		step("uryu atlas: cleanup", 4, () -> {
			clearFx();
			cmd("fill -3 -60 9 3 -52 9 minecraft:air", "time set noon");
			mc.options.hudHidden = false;
			graphics(GraphicsMode.FANCY);
		});
	}
}
