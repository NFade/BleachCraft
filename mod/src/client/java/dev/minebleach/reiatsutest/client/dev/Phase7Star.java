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
import dev.minebleach.reiatsutest.client.fx.UryuFx;
import dev.minebleach.reiatsutest.client.fx.UryuFxStats;
import dev.minebleach.reiatsutest.client.fx.UryuQuincyFeature;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

/**
 * U-FX 10, Sternenstaub (storyboard 2.14). Run with {@code gradlew runPhase7 -Phold=uryu_star[,uryu_star_rm]}. The server packets are injected on the client
 * (no Focus needed): effect 78 (params[3] = 77) at the key press, effect 77 and the LINE packet 0.8 s later; the FX clock is frozen {@code t} ms after the
 * key press. Screenshots {@code run/screenshots/p7_10_star_*.png}: {@code t0 t300 t450 t700} (front view of the charge), {@code t800 t830 t1100 t2000}
 * (from behind, along the line), {@code front_t1100}, {@code night_t1100}, {@code rm_t1100} (Reduce Motion). Checks: flakes drawn, beam strips, rings, end burst.
 * Not run by the author.
 */
final class Phase7Star {
	private Phase7Star() {
	}

	static void steps() {
		step("star: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			FxTimelines.clear();
			ScreenFx.clear();
		});
		if (wants("uryu_star")) {
			for (int ms : new int[] {0, 300, 450, 700}) {
				shot("", Perspective.THIRD_PERSON_FRONT, false, false, ms);
			}
			for (int ms : new int[] {800, 830, 1100, 2000}) {
				shot("", Perspective.THIRD_PERSON_BACK, false, false, ms);
			}
			shot("front", Perspective.THIRD_PERSON_FRONT, false, false, 1100);
			shot("night", Perspective.THIRD_PERSON_BACK, true, false, 1100);
		}
		if (wants("uryu_star_rm")) {
			shot("rm", Perspective.THIRD_PERSON_BACK, false, true, 1100);
		}
		step("star: cleanup", 4, () -> {
			cmd("kill @e[type=zombie,distance=..60]");
			UryuQuincyFeature.clear();
			UryuFxStats.clearStar();
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

	private static void inject(int effectId, float[] params) {
		Vec3d feet = mc.player.getPos();
		Vec3d look = mc.player.getRotationVec(1.0f);
		UryuFx.play(mc, new EffectEventS2C(effectId, mc.player.getId(), 1, feet.x, feet.y, feet.z, (float) look.x, (float) look.y, (float) look.z, -1, 0, params));
	}

	private static void shot(String tag, Perspective view, boolean night, boolean rm, int ms) {
		String name = "10_star" + (tag.isEmpty() ? "" : "_" + tag) + "_t" + ms;
		final int[] before = new int[6];
		step(name + " (scene)", 36, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			UryuQuincyFeature.clear();
			UryuFxStats.clearStar();
			FxConfig.reduceMotion = rm;
			mc.options.setPerspective(view);
			cmd("kill @e[type=zombie,distance=..60]", "tp @s 0.5 -60 0.5 0 8", "time set " + (night ? "midnight" : "noon"), "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			cmd("summon zombie 0.5 -60 12.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:200f,Attributes:[{Name:\"generic.max_health\",Base:200}]}");
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state bankai uryu", "reiatsu full");
		});
		step(name + " (cast B)", ms >= 800 ? 16 : 1, () -> {
			before[0] = UryuFxStats.starFlakeQuads();
			before[1] = UryuFxStats.starBeamQuads();
			before[2] = UryuFxStats.starRings();
			before[3] = UryuFxStats.starSpecks();
			before[4] = UryuFxStats.starBursts();
			before[5] = UryuFxStats.starLaunches();
			FxClock.unfreeze();
			if (ms < 800) {
				FxClock.freezeIn(ms / 1000.0);
			}
			Vec3d p = mc.player.getEyePos().add(mc.player.getRotationVec(1.0f).multiply(20.0));
			inject(78, new float[] {(float) p.x, (float) p.y, (float) p.z, 77f, 0.8f});
		});
		if (ms >= 800) {
			step(name + " (release)", 1, () -> {
				Vec3d eye = mc.player.getEyePos();
				Vec3d end = eye.add(mc.player.getRotationVec(1.0f).multiply(40.0));
				inject(77, new float[] {(float) end.x, (float) end.y, (float) end.z, 1f, 0.8f});
				UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.LINE, 0, new int[0], new float[] {(float) eye.x, (float) eye.y, (float) eye.z, (float) end.x, (float) end.y, (float) end.z, 1f}));
				FxClock.freezeIn((ms - 800) / 1000.0);
			});
		}
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] STAR {} t {} ms: flakes +{} beam +{} rings +{} specks +{} bursts +{} launches +{} glow {}", name, ms, UryuFxStats.starFlakeQuads() - before[0],
					UryuFxStats.starBeamQuads() - before[1], UryuFxStats.starRings() - before[2], UryuFxStats.starSpecks() - before[3], UryuFxStats.starBursts() - before[4],
					UryuFxStats.starLaunches() - before[5], FxGlowBatch.live());
			if (ms >= 300 && ms <= 700) {
				cCheck(name + ": the flakes are drawn", () -> UryuFxStats.starFlakeQuads() > before[0] ? null : "no flakes");
			}
			if (ms >= 830) {
				cCheck(name + ": the beam is drawn", () -> UryuFxStats.starBeamQuads() > before[1] ? null : "no beam");
				cCheck(name + ": the shock rings", () -> UryuFxStats.starRings() > before[2] ? null : "no rings");
			}
			if (ms >= 1100) {
				cCheck(name + ": the end burst", () -> UryuFxStats.starBursts() > before[4] ? null : "no end burst");
			}
			FxClock.unfreeze();
			FxConfig.reduceMotion = false;
		});
	}
}
