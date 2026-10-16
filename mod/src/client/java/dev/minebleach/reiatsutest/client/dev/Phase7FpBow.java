package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuQuincyFeature;
import dev.minebleach.reiatsutest.client.fx.UryuWingRenderer;
import dev.minebleach.reiatsutest.core.fx.UryuDrawLight;
import net.minecraft.client.option.Perspective;

/**
 * First person bow hold with the HUD shown (vanilla draws no first person hands with {@code hudHidden}). Run with {@code gradlew runPhase7 -Phold=uryu_fpbow}.
 * Screenshots {@code run/screenshots/p7_12_fpbow_*.png}: {@code idle}, {@code p50}, {@code p100} in the base state, {@code bankai_idle} and {@code bankai_p100} in
 * the bankai state (the bracer of the bankai state must be drawn on the arm).
 */
final class Phase7FpBow {
	private Phase7FpBow() {
	}

	static void steps() {
		scene("12_fpbow_idle", "base");
		shot("12_fpbow_idle", 0.0, false);
		shot("12_fpbow_p25", 0.25, false);
		shot("12_fpbow_p50", 0.5, false);
		shot("12_fpbow_p75", 0.75, false);
		shot("12_fpbow_p100", 1.0, false);
		variants();
		scene("12_fpbow_bankai_idle", "bankai");
		shot("12_fpbow_bankai_idle", 0.0, true);
		shot("12_fpbow_bankai_p100", 1.0, true);
		step("fpbow: cleanup", 4, () -> {
			mc.options.useKey.setPressed(false);
			UryuQuincyFeature.clear();
			FxConfig.reduceMotion = false;
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			cmd("kill @e[type=minecraft:zombie]", "time set noon", "reiatsu dev ragefree false", "reiatsu state sealed uryu");
		});
	}

	private static void scene(String name, String state) {
		step(name + " (scene)", 40, () -> {
			mc.options.useKey.setPressed(false);
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			UryuQuincyFeature.clear();
			mc.options.hudHidden = false;
			mc.options.getFov().setValue(70);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			cmd("tp @s 0.5 -60 0.5 0 5", "time set noon", "kill @e[type=minecraft:zombie]", "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state " + state + " uryu", "reiatsu dev ragefree true", "reiatsu full");
		});
		step(name + " (settle)", 24, () -> { });
	}

	/** Dev: variants of the first person tunables at full draw ({@code run/screenshots/p7_12_fpbow_var*.png}); each row is key, value pairs applied on top of the defaults. */
	private static final String[][] VARS = {
			{"uryu.fp.arrowUp", "0.09", "uryu.fp.stringDx", "-0.75", "uryu.fp.stringDy", "-0.1"},
			{"uryu.fp.arrowX", "0.14", "uryu.fp.arrowUp", "0.05", "uryu.fp.stringDx", "-0.75", "uryu.fp.stringDy", "-0.1"},
			{"uryu.fp.arrowX", "-0.14", "uryu.fp.arrowUp", "0.05", "uryu.fp.stringDx", "-0.75", "uryu.fp.stringDy", "-0.1"},
			{"uryu.fp.holdRy", "30", "uryu.fp.holdX", "-0.06", "uryu.fp.arrowUp", "0.09", "uryu.fp.stringDx", "-0.75", "uryu.fp.stringDy", "-0.1"},
			{"uryu.fp.holdRy", "30", "uryu.fp.holdX", "-0.06", "uryu.fp.arrowX", "0.14", "uryu.fp.arrowUp", "0.05", "uryu.fp.stringDx", "-0.75", "uryu.fp.stringDy", "-0.1"},
			{"uryu.fp.holdRy", "30", "uryu.fp.holdX", "-0.06", "uryu.fp.arrowX", "-0.14", "uryu.fp.arrowUp", "0.05", "uryu.fp.stringDx", "-0.75", "uryu.fp.stringDy", "-0.1"},
	};

	private static void variants() {
		scene("12_fpbow_var", "base");
		step("fpbow var (hold)", 1, () -> mc.options.useKey.setPressed(true));
		final int ticks = (int) Math.ceil(UryuDrawLight.ticksForPower(1.0)) + 6;
		stepUntil("fpbow var (drawn)", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= ticks);
		for (int i = 0; i < VARS.length; i++) {
			final int n = i;
			step("fpbow var" + n + " (apply)", 6, () -> {
				for (int k = 0; k < VARS[n].length; k += 2) {
					dev.minebleach.reiatsutest.client.fx.FxTune.override(VARS[n][k], Double.parseDouble(VARS[n][k + 1]));
				}
			});
			step("fpbow var" + n + " (shot)", 3, () -> {
				Phase7Uryu.shot7("12_fpbow_var" + n);
				for (int k = 0; k < VARS[n].length; k += 2) {
					dev.minebleach.reiatsutest.client.fx.FxTune.unset(VARS[n][k]);
				}
			});
		}
		step("fpbow var (release)", 4, () -> mc.options.useKey.setPressed(false));
		step("fpbow var (let go)", 24, () -> { });
	}

	/** {@code p == 0}: idle shot. Otherwise the use key is held until the draw reaches {@code p}, then the shot. */
	private static void shot(String name, double p, boolean bankai) {
		final int ticks = Math.max(1, (int) Math.ceil(UryuDrawLight.ticksForPower(p)));
		final int[] bracers = new int[1];
		if (p > 0.0) {
			step(name + " (hold)", 1, () -> mc.options.useKey.setPressed(true));
			stepUntil(name + " (drawn)", 1, 20 * 6, () -> { }, () -> mc.player.isUsingItem() && mc.player.getItemUseTime() >= ticks + (p >= 1.0 ? 6 : 0));
		}
		step(name + " (pre)", 1, () -> bracers[0] = UryuWingRenderer.firstPersonBracers);
		step(name + " (shot)", 4, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] FPBOW {} use time {} fp bracers +{}", name, mc.player.getItemUseTime(), UryuWingRenderer.firstPersonBracers - bracers[0]);
			if (bankai) {
				cCheck(name + ": first person: the bracer of the bankai state is on the arm", () -> UryuWingRenderer.firstPersonBracers > bracers[0] ? null : "no bracer drawn");
			}
			mc.options.useKey.setPressed(false);
		});
		step(name + " (let go)", 24, () -> { });
	}
}
