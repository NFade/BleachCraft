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
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.ShunpoFx;
import dev.minebleach.reiatsutest.client.fx.UryuFxStats;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

/**
 * T6, the Quincy shunpo (storyboard 2.15). Run with {@code gradlew runPhase7 -Phold=uryu_shunpo[,uryu_shunpo_rm]}. Effect 40 (character URYU) is injected on the
 * client for a 9 block dash along the view; the FX clock is frozen {@code t} ms later. Screenshots {@code run/screenshots/p7_11_shunpo_*.png}: {@code t0 t100 t250 t400},
 * {@code rm_t250} (Reduce Motion). Checks: dashes and discs spawned. Not run by the author.
 */
final class Phase7Shunpo {
	private Phase7Shunpo() {
	}

	static void steps() {
		step("shunpo: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			FxTimelines.clear();
			ScreenFx.clear();
		});
		if (wants("uryu_shunpo")) {
			for (int ms : new int[] {0, 100, 250, 400}) {
				shot("", false, ms);
			}
		}
		if (wants("uryu_shunpo_rm")) {
			shot("rm", true, 250);
		}
		step("shunpo: cleanup", 4, () -> {
			ShunpoFx.clear();
			UryuFxStats.clearShunpo();
			FxConfig.reduceMotion = false;
			FxClock.unfreeze();
			FxTimelines.clear();
			ScreenFx.clear();
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
		});
	}

	private static void shot(String tag, boolean rm, int ms) {
		String name = "11_shunpo" + (tag.isEmpty() ? "" : "_" + tag) + "_t" + ms;
		final int[] before = new int[2];
		step(name + " (scene)", 24, () -> {
			FxClock.unfreeze();
			FxTimelines.clear();
			ScreenFx.clear();
			ShunpoFx.clear();
			UryuFxStats.clearShunpo();
			FxConfig.reduceMotion = rm;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 0.5 0 5", "time set noon");
		});
		step(name + " (play)", 1, () -> {
			before[0] = UryuFxStats.shunpoDashes();
			before[1] = UryuFxStats.shunpoDiscs();
			FxClock.unfreeze();
			FxClock.freezeIn(ms / 1000.0);
			Vec3d feet = mc.player.getPos();
			Vec3d d = mc.player.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0).normalize().multiply(9.0);
			ShunpoFx.play(mc, new EffectEventS2C(EffectIds.SHUNPO, mc.player.getId(), 1, feet.x, feet.y, feet.z, (float) d.x, (float) d.y, (float) d.z, -1, 0,
					new float[] {(float) d.length(), CharacterId.URYU.code}));
		});
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] SHUNPO {} t {} ms: dashes +{} discs +{} images {}", name, ms, UryuFxStats.shunpoDashes() - before[0], UryuFxStats.shunpoDiscs() - before[1],
					ShunpoFx.liveImages());
			cCheck(name + ": the dashes", () -> UryuFxStats.shunpoDashes() > before[0] ? null : "no dashes");
			cCheck(name + ": the platform discs", () -> UryuFxStats.shunpoDiscs() > before[1] ? null : "no discs");
			FxClock.unfreeze();
			FxConfig.reduceMotion = false;
		});
	}
}
