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
 * U-FX 9, Licht Regen (storyboard 2.13). Run with {@code gradlew runPhase7 -Phold=uryu_rain[,uryu_rain_rm]}. The real flow: BANKAI state, flat ground, key H
 * (event 78 at the key press lights the outline, event 76 0.6 s later starts the rain); the FX clock is frozen {@code t} ms after the key. Screenshots
 * {@code run/screenshots/p7_09_rain_*.png}: {@code t300} (outline drawn, before the release), {@code t1200} and {@code t2500} (third person from the back,
 * rain falling), {@code overview_t2500} (from above and behind), {@code night_t2500}, {@code rm_t2500} (Reduce Motion). Checks: outline quads, sky card,
 * landings and splashes. Not run by the author.
 */
final class Phase7Rain {
	private Phase7Rain() {
	}

	static void steps() {
		step("rain: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			FxTimelines.clear();
			ScreenFx.clear();
		});
		if (wants("uryu_rain")) {
			for (int ms : new int[] {300, 1200, 2500}) {
				shot("", Perspective.THIRD_PERSON_BACK, false, false, ms);
			}
			shot("overview", Perspective.THIRD_PERSON_BACK, false, false, 2500, 15f); // positive pitch = looking down; a negative one aims into the sky (no floor within 6 blocks: the square hangs in the air and nothing lands)
			shot("night", Perspective.THIRD_PERSON_BACK, true, false, 2500, 8f);
		}
		if (wants("uryu_rain_rm")) {
			shot("rm", Perspective.THIRD_PERSON_BACK, false, true, 2500, 8f);
		}
		step("rain: cleanup", 4, () -> {
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
		shot(tag, view, night, rm, ms, 8f);
	}

	private static void shot(String tag, Perspective view, boolean night, boolean rm, int ms, float pitch) {
		String name = "09_rain" + (tag.isEmpty() ? "" : "_" + tag) + "_t" + ms;
		final int[] before = new int[5];
		step(name + " (scene)", 36, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			UryuQuincyFeature.clear();
			FxConfig.reduceMotion = rm;
			mc.options.setPerspective(view);
			cmd("kill @e[type=zombie,distance=..60]", "tp @s 0.5 -60 0.5 0 " + pitch, "time set " + (night ? "midnight" : "noon"), "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			cmd("summon zombie 0.5 -60 8.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:200f,Attributes:[{Name:\"generic.max_health\",Base:200}]}");
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state bankai uryu", "reiatsu full");
		});
		step(name + " (cast H)", 1, () -> {
			before[0] = UryuFxStats.rainOutlineQuads();
			before[1] = UryuFxStats.rainCardQuads();
			before[2] = UryuFxStats.rainLandings();
			before[3] = UryuFxStats.rainSplashes();
			before[4] = UryuFxStats.rainSpecks();
			FxClock.freezeIn(ms / 1000.0);
			Phase6Harness.press(ReiatsuKeys.BANKAI_SLOTS[1]);
		});
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] RAIN {} t {} ms: outline +{} card +{} landings +{} splashes +{} specks +{} glow {}", name, ms, UryuFxStats.rainOutlineQuads() - before[0],
					UryuFxStats.rainCardQuads() - before[1], UryuFxStats.rainLandings() - before[2], UryuFxStats.rainSplashes() - before[3], UryuFxStats.rainSpecks() - before[4],
					FxGlowBatch.live());
			cCheck(name + ": the outline is lit (" + (UryuFxStats.rainOutlineQuads() - before[0]) + ")", () -> UryuFxStats.rainOutlineQuads() > before[0] ? null : "no outline");
			if (ms >= 1200) {
				cCheck(name + ": the sky card stands", () -> UryuFxStats.rainCardQuads() > before[1] ? null : "no sky card");
			}
			if (ms >= 2000) {
				cCheck(name + ": needles landed (" + (UryuFxStats.rainLandings() - before[2]) + ", splashes " + (UryuFxStats.rainSplashes() - before[3]) + ")",
						() -> UryuFxStats.rainSplashes() > before[3] ? null : "no splash");
			}
			FxClock.unfreeze();
			FxConfig.reduceMotion = false;
		});
	}
}
