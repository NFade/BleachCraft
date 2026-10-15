package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.wants;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuFxStats;
import dev.minebleach.reiatsutest.client.fx.UryuQuincyFeature;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import net.minecraft.client.option.Perspective;

/**
 * U-FX 8, the wing barrage (storyboard 2.12). Run with {@code gradlew runPhase7 -Phold=uryu_barrage[,uryu_barrage_rm]}. The real flow: BANKAI state, four
 * frozen zombies in front, key Z; the FX clock is frozen {@code t} ms after the first arrow left a tip. Screenshots {@code run/screenshots/p7_08_barrage_*.png}:
 * {@code p7_08_barrage_t{300,1000,3000}} (third person from the back), {@code p7_08_barrage_front_t1000}, {@code p7_08_barrage_night_t1000},
 * {@code p7_08_barrage_rm_t1000} (Reduce Motion: no spin, no lock-on line, no shake). Checks: tip fires, flare dashes, cross sprites. Not run by the author.
 */
final class Phase7Barrage {
	private static final int[][] TARGETS = {{-6, 11}, {-2, 14}, {2, 9}, {6, 13}};

	private Phase7Barrage() {
	}

	static void steps() {
		step("barrage: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			FxTimelines.clear();
			ScreenFx.clear();
		});
		if (wants("uryu_barrage")) {
			for (int ms : new int[] {300, 1000, 3000}) {
				shot("", Perspective.THIRD_PERSON_BACK, false, false, ms);
			}
			shot("front", Perspective.THIRD_PERSON_FRONT, false, false, 1000);
			shot("night", Perspective.THIRD_PERSON_BACK, true, false, 1000);
		}
		if (wants("uryu_barrage_rm")) {
			shot("rm", Perspective.THIRD_PERSON_BACK, false, true, 1000);
		}
		step("barrage: cleanup", 4, () -> {
			cmd("kill @e[type=zombie,distance=..60]");
			UryuQuincyFeature.clear();
			FxConfig.reduceMotion = false;
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			cmd("time set noon", "reiatsu state sealed uryu");
		});
	}

	private static void shot(String tag, Perspective view, boolean night, boolean rm, int ms) {
		String name = "08_barrage" + (tag.isEmpty() ? "" : "_" + tag) + "_t" + ms;
		final int[] before = new int[3];
		step(name + " (scene)", 36, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			UryuQuincyFeature.clear();
			FxConfig.reduceMotion = rm;
			mc.options.setPerspective(view);
			cmd("kill @e[type=zombie,distance=..60]", "tp @s 0.5 -60 0.5 0 8", "time set " + (night ? "midnight" : "noon"), "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			for (int[] t : TARGETS) {
				cmd(String.format("summon zombie %d.5 -60 %d.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:200f,Attributes:[{Name:\"generic.max_health\",Base:200}]}", t[0], t[1]));
			}
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state bankai uryu", "reiatsu full");
		});
		step(name + " (cast Z)", 1, () -> {
			before[0] = UryuFxStats.wingTipFires();
			before[1] = UryuFxStats.wingBarrageDashes();
			before[2] = UryuFxStats.wingCrossSprites();
			Phase6Harness.press(ReiatsuKeys.BANKAI_SLOTS[0]);
		});
		stepUntil(name + " (first arrow)", 1, 20 * 6, () -> { }, () -> UryuFxStats.wingTipFires() > before[0]);
		step(name + " (freeze)", 1, () -> FxClock.freezeIn(ms / 1000.0));
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] BARRAGE {} t {} ms: tip fires +{} dashes +{} cross sprites +{} glow {}", name, ms, UryuFxStats.wingTipFires() - before[0],
					UryuFxStats.wingBarrageDashes() - before[1], UryuFxStats.wingCrossSprites() - before[2], FxGlowBatch.live());
			cCheck(name + ": arrows left the tips (" + (UryuFxStats.wingTipFires() - before[0]) + ")", () -> UryuFxStats.wingTipFires() > before[0] ? null : "no tip fire");
			cCheck(name + ": the cross stands behind the back (" + (UryuFxStats.wingCrossSprites() - before[2]) + ")", () -> UryuFxStats.wingCrossSprites() > before[2] ? null : "no cross");
			if (ms >= 1000) {
				cCheck(name + ": several slots fired (" + (UryuFxStats.wingTipFires() - before[0]) + ")", () -> UryuFxStats.wingTipFires() - before[0] >= 3 ? null : "fewer than 3 tip fires");
			}
			if (!rm) {
				cCheck(name + ": the flare dashes flew (" + (UryuFxStats.wingBarrageDashes() - before[1]) + ")", () -> UryuFxStats.wingBarrageDashes() > before[1] ? null : "no dashes");
			}
			FxClock.unfreeze();
			FxConfig.reduceMotion = false;
		});
	}
}
