package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.wants;

import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.fx.UryuDrawLight;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;

/**
 * R13 harness: the poses of the Heilig Bogen and of the Byakuya sword in first and third person, the screenshots the design report compares
 * before and after. {@code gradlew runPhase7 -Phold=r13_pose} (bow draw, release and ability poses; files {@code run/screenshots/p7_r13_*.png}) and
 * {@code -Phold=r13_byakuya} (the strike of the petal sword in first and third person). Numbers are not asserted here, the pictures are the result.
 */
final class Phase8R13 {
	private Phase8R13() {
	}

	static boolean wantsAny() {
		return wants("r13_pose") || wants("r13_byakuya") || wants("r13_tp") || wants("r13_tpvar") || wants("r13_front") || wants("r13_fpvar") || wants("r13_byaktp") || wants("r13_strafe") || wants("r13_aimpitch");
	}

	static void steps() {
		if (wants("r13_pose")) {
			for (String view : new String[] {"fp", "back", "front"}) {
				drawSet(view);
			}
			for (String view : new String[] {"fp", "back"}) {
				abilitySet(view);
			}
		}
		if (wants("r13_tp")) {
			drawSet("back");
			drawSet("front");
		}
		if (wants("r13_strafe")) {
			for (int i = 0; i < 2; i++) {
				final int align = i; // 0 = the old behaviour (body follows the walk), 1 = the body follows the look
				final String n = "strafe_a" + align;
				scene(n, "back", "base", "uryu", "uryu_cross");
				step(n + " apply", 1, () -> {
					dev.minebleach.reiatsutest.client.fx.FxTune.override("uryu.tp.alignBody", align);
					mc.options.useKey.setPressed(true);
				});
				stepUntil(n + " drawn", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= 26);
				step(n + " strafe", 1, () -> mc.options.leftKey.setPressed(true));
				step(n + " walking", 14, () -> { });
				step(n + " shot", 1, () -> shot(n));
				step(n + " stop", 1, () -> {
					mc.options.leftKey.setPressed(false);
					mc.options.useKey.setPressed(false);
					dev.minebleach.reiatsutest.client.fx.FxTune.unset("uryu.tp.alignBody");
				});
				step(n + " rest", 20, () -> { });
			}
		}
		if (wants("r13_aimpitch")) {
			for (int pitch : new int[] {-35, 35}) {
				final String n = "pitch" + (pitch < 0 ? "up" : "down");
				scene(n, "back", "base", "uryu", "uryu_cross");
				step(n + " turn", 3, () -> cmd("tp @s 0.5 -60 0.5 0 " + pitch));
				step(n + " hold", 1, () -> mc.options.useKey.setPressed(true));
				stepUntil(n + " drawn", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= 28);
				step(n + " shot", 2, () -> shot(n));
				step(n + " release", 1, () -> mc.options.useKey.setPressed(false));
				step(n + " rest", 20, () -> { });
			}
		}
		if (wants("r13_fpvar")) {
			fpVariants();
		}
		if (wants("r13_front")) {
			drawSet("front");
			for (String f : new String[] {"p50", "p100"}) {
				step("copy front " + f, 1, () -> {
					try {
						java.nio.file.Path d = mc.runDirectory.toPath().resolve("screenshots");
						java.nio.file.Files.copy(d.resolve("p7_r13_bow_front_" + f + ".png"), d.resolve("p7_r13_firstset_" + f + ".png"),
								java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					} catch (java.io.IOException e) {
						throw new IllegalStateException(e);
					}
				});
			}
			drawSet("front");
		}
		if (wants("r13_tpvar")) {
			double[][] vars = {{0.0, 1.0}, {25.0, 1.0}, {-25.0, 1.0}, {45.0, 1.0}};
			for (int i = 0; i < vars.length; i++) {
				final double[] v = vars[i];
				final String n = "tpvar" + i;
				scene(n, "front", "base", "uryu", "uryu_cross");
				step(n + " apply", 1, () -> {
					dev.minebleach.reiatsutest.client.fx.FxTune.override("uryu.tp.drawArmYaw", v[0]);
					mc.options.useKey.setPressed(true);
				});
				stepUntil(n + " drawn", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= 30);
				step(n + " shot", 2, () -> shot(n));
				step(n + " release", 1, () -> {
					mc.options.useKey.setPressed(false);
					dev.minebleach.reiatsutest.client.fx.FxTune.unset("uryu.tp.drawArmYaw");
				});
				step(n + " rest", 20, () -> { });
			}
		}
		if (wants("r13_byaktp")) {
			byakuya("back", "shikai");
		}
		if (wants("r13_byakuya")) {
			for (String view : new String[] {"fp", "back", "front"}) {
				byakuya(view, "shikai");
			}
			byakuya("back", "bankai");
		}
		step("r13: cleanup", 4, () -> {
			mc.options.useKey.setPressed(false);
			mc.options.attackKey.setPressed(false);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			cmd("kill @e[type=minecraft:zombie]", "time set noon", "reiatsu state sealed uryu");
		});
	}

	private static void scene(String name, String view, String state, String character, String item) {
		step(name + " (scene)", 40, () -> {
			mc.options.useKey.setPressed(false);
			mc.options.attackKey.setPressed(false);
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			mc.options.getFov().setValue(70);
			boolean fp = view.equals("fp");
			mc.options.hudHidden = !fp; // vanilla draws no first person hands with the HUD hidden
			mc.options.setPerspective(fp ? Perspective.FIRST_PERSON : view.equals("back") ? Perspective.THIRD_PERSON_BACK : Perspective.THIRD_PERSON_FRONT);
			cmd("tp @s 0.5 -60 0.5 0 5", "time set noon", "kill @e[type=minecraft:zombie]", "difficulty easy", "gamerule doMobSpawning false",
					"item replace entity @s hotbar.0 with reiatsu_test:" + item);
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state " + state + " " + character, "reiatsu dev ragefree true", "reiatsu full");
		});
		step(name + " (settle)", 24, () -> { });
	}

	/** Dev: variants of the first person bow numbers written into pose_override.json (section fp_bow, hot reload), p100 and the release of each. */
	private static final String[] FP_VARS = {
			"{}",
	};

	private static void fpVariants() {
		for (int i = 0; i < FP_VARS.length; i++) {
			final int n = i;
			final String nm = "fpvar" + i;
			scene(nm, "fp", "base", "uryu", "uryu_cross");
			step(nm + " write", 12, () -> {
				try {
					java.nio.file.Files.writeString(mc.runDirectory.toPath().resolve("pose_override.json"), FP_VARS[n]);
				} catch (java.io.IOException e) {
					throw new IllegalStateException(e);
				}
			});
			step(nm + " idle", 3, () -> shot(nm + "_idle"));
			step(nm + " hold", 1, () -> mc.options.useKey.setPressed(true));
			stepUntil(nm + " half", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= 8);
			step(nm + " half shot", 1, () -> shot(nm + "_p30"));
			stepUntil(nm + " half2", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= 15);
			step(nm + " half2 shot", 1, () -> shot(nm + "_p60"));
			stepUntil(nm + " drawn", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= 30);
			step(nm + " shot", 2, () -> shot(nm + "_p100"));
			step(nm + " release", 1, () -> mc.options.useKey.setPressed(false));
			step(nm + " rel2", 2, () -> shot(nm + "_rel2"));
			step(nm + " rest", 20, () -> { });
		}
		step("fpvar cleanup", 2, () -> {
			try {
				java.nio.file.Files.deleteIfExists(mc.runDirectory.toPath().resolve("pose_override.json"));
			} catch (java.io.IOException e) {
				// left behind
			}
		});
	}

	private static void shot(String name) {
		Phase7Uryu.shot7("r13_" + name);
	}

	/** Idle, half draw, full draw, and the release (+2 and +6 ticks) of the base bow in one view. */
	private static void drawSet(String view) {
		String n = "bow_" + view;
		scene(n, view, "base", "uryu", "uryu_cross");
		step(n + " idle", 2, () -> shot(n + "_idle"));
		for (double p : new double[] {0.5, 1.0}) {
			final int ticks = Math.max(1, (int) Math.ceil(UryuDrawLight.ticksForPower(p))) + (p >= 1.0 ? 4 : 0);
			final String nm = n + "_p" + (int) Math.round(p * 100);
			step(nm + " hold", 1, () -> mc.options.useKey.setPressed(true));
			stepUntil(nm + " drawn", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= ticks);
			step(nm + " shot", 2, () -> shot(nm));
			if (p < 1.0) {
				step(nm + " let go", 2, () -> mc.options.useKey.setPressed(false));
				step(nm + " settle", 30, () -> { });
			}
		}
		step(n + " release", 1, () -> mc.options.useKey.setPressed(false));
		step(n + " release+2", 2, () -> shot(n + "_rel2"));
		step(n + " release+6", 4, () -> shot(n + "_rel6"));
		step(n + " rest", 20, () -> { });
	}

	/** The ability poses: shikai Z, H, B and bankai Z, H, B (key presses of the real casts), two shots each. */
	private static void abilitySet(String view) {
		String[][] names = {{"shikai", "z"}, {"shikai", "h"}, {"shikai", "b"}, {"bankai", "z"}, {"bankai", "h"}, {"bankai", "b"}};
		for (String[] nm : names) {
			final String state = nm[0];
			final int idx = nm[1].equals("z") ? 0 : nm[1].equals("h") ? 1 : 2;
			final KeyBinding key = (state.equals("bankai") ? ReiatsuKeys.BANKAI_SLOTS : ReiatsuKeys.SLOTS)[idx];
			final String n = "abil_" + view + "_" + state + "_" + nm[1];
			scene(n, view, state, "uryu", "uryu_cross");
			step(n + " cast", 3, () -> Phase6Harness.press(key));
			step(n + " +3", 3, () -> shot(n + "_a"));
			step(n + " +9", 6, () -> shot(n + "_b"));
			step(n + " +20", 12, () -> shot(n + "_c"));
		}
	}

	/** Byakuya, shikai or bankai: three strikes in a row with a screenshot every 3 ticks over the first 24. */
	private static void byakuya(String view, String state) {
		String n = "byak_" + view + "_" + state;
		scene(n, view, state, "byakuya", "senbonzakura");
		step(n + " idle", 2, () -> shot(n + "_idle"));
		step(n + " strike", 1, () -> mc.options.attackKey.setPressed(true));
		for (int i = 0; i < 12; i++) {
			final int k = i;
			step(n + " t" + k, 3, () -> shot(n + "_t" + (k < 10 ? "0" : "") + k));
		}
		step(n + " stop", 1, () -> mc.options.attackKey.setPressed(false));
		step(n + " end", 30, () -> shot(n + "_end"));
	}
}
