package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;

import com.mojang.authlib.GameProfile;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxEvents;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuQuincyFeature;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.fx.UryuBlutTiming;
import dev.minebleach.reiatsutest.core.fx.UryuFxTiers;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;

/**
 * Harness scenario {@code uryu_blut} (U-FX 6, URYU_VFX_STORYBOARD 2.5 and 6): {@code gradlew runPhase7 -Phold=uryu_blut}. Screenshots
 * ({@code run/screenshots/p7_06_blut_*.png}):
 * <ul>
 * <li>{@code p7_06_blut_t{0,200,400,550,900}.png}: the real flow, third person from the front: the local player in BASE presses R, the FX clock
 * freezes {@code t} ms after the event; plus {@code p7_06_blut_state.png} (3.6 s: the state level) and the night versions
 * {@code p7_06_blut_night_t*};</li>
 * <li>{@code p7_06_blut_skins_{noon,midnight}_t{300,450,900,state}.png}: first person looking at TWO other players (one slim, one classic skin,
 * client side stand-ins forced into the SHIKAI look), released by a synthetic effect event 70 each, so the three bodies share one clock; the
 * first person arms of the local player carry the overlay too;</li>
 * <li>{@code p7_06_blut_rm_t{200,550,900}.png}: the same with Reduce Motion (constant 0.7 pulse, no flash above 0.25, no shake);</li>
 * <li>{@code p7_06_blut_low.png}: tier Low (veins on the arms only).</li>
 * </ul>
 * Checks (client side): the stage and level at each frozen moment follow {@link UryuBlutTiming}; the feature drew; Reduce Motion level is the
 * constant. Nothing here was run (the game must not be launched by the author of the step).
 */
final class Phase7Blut {
	private static final List<OtherClientPlayerEntity> FAKES = new ArrayList<>();
	private static int fpDrawsBefore;

	private Phase7Blut() {
	}

	static void steps() {
		step("blut: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			FxTimelines.clear();
			ScreenFx.clear();
		});
		// the real flow, noon then midnight
		for (boolean night : new boolean[] {false, true}) {
			for (int ms : new int[] {0, 200, 400, 550, 900}) {
				realShot((night ? "06_blut_night_t" : "06_blut_t") + ms, ms, night);
			}
			realState(night ? "06_blut_night_state" : "06_blut_state", night);
		}
		// slim and classic stand-ins, first person
		step("blut skins: stand-ins", 6, Phase7Blut::spawnFakes);
		for (boolean night : new boolean[] {false, true}) {
			String tag = night ? "midnight" : "noon";
			for (int ms : new int[] {300, 450, 900, 4000}) {
				skinShot("06_blut_skins_" + tag + "_t" + ms, ms, night, false);
			}
		}
		// Reduce Motion
		for (int ms : new int[] {200, 550, 900}) {
			skinShot("06_blut_rm_t" + ms, ms, false, true);
		}
		// tier Low: arms only
		step("blut low: tier", 2, () -> FxConfig.uryuVeinOverlay = UryuFxTiers.VeinOverlay.ARMS);
		skinShot("06_blut_low", 4000, false, false);
		step("blut low: tier back", 2, () -> FxConfig.uryuVeinOverlay = UryuFxTiers.VeinOverlay.FULL);
		step("blut: cleanup", 4, () -> {
			removeFakes();
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

	// ------------------------------------------------------------------------------------------ the real flow (key press)

	private static void realScene(String name, boolean night) {
		step(name + " (scene)", 36, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			UryuQuincyFeature.clear();
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			cmd("tp @s 0.5 -60 0.5 0 8", "time set " + (night ? "midnight" : "noon"), "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state base uryu", "reiatsu full");
		});
	}

	private static void realShot(String name, int ms, boolean night) {
		realScene(name, night);
		step(name + " (arm)", 2, () -> FxTimelines.setFreezeAt(ms));
		step(name + " (release R)", 1, () -> Phase6Harness.press(ReiatsuKeys.RELEASE));
		stepUntil(name + " (frozen)", 2, 20 * 14, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			logState(name, mc.player.getId(), ms);
			checkAt(name, mc.player.getId(), ms / 1000.0, false);
		});
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	/** The state level, 3.6 s after the release (after the heartbeat has settled). */
	private static void realState(String name, boolean night) {
		realScene(name, night);
		step(name + " (release R)", 1, () -> Phase6Harness.press(ReiatsuKeys.RELEASE));
		step(name + " (settle)", 20 * 4, () -> { });
		step(name + " (freeze)", 1, () -> FxClock.freezeIn(0.0));
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			logState(name, mc.player.getId(), 3600);
			cCheck(name + ": the state level " + UryuQuincyFeature.state(mc.player.getId()).veinLevel(), () -> {
				var s = UryuQuincyFeature.state(mc.player.getId());
				return s.shikai() && Math.abs(s.veinLevel() - 0.5) < 0.02 && s.veinStage() == UryuBlutTiming.STAGES - 1 ? null : "level " + s.veinLevel() + " stage " + s.veinStage();
			});
		});
		step(name + " (release clock)", 2, FxClock::unfreeze);
	}

	// ------------------------------------------------------------------------------------------ slim and classic stand-ins

	private static UUID uuidWith(SkinTextures.Model model) {
		for (int k = 0; k < 500; k++) {
			UUID id = UUID.nameUUIDFromBytes(("uryu-blut-" + k).getBytes());
			if (DefaultSkinHelper.getSkinTextures(id).model() == model) {
				return id;
			}
		}
		return UUID.randomUUID();
	}

	private static void spawnFakes() {
		removeFakes();
		// facing the camera (yaw 180 = looking north, the player at the origin looks south): the slim one on the left of the picture, the classic one on the right
		FAKES.add(spawn(SkinTextures.Model.SLIM, "UryuSlim", 1.0, 3.2));
		FAKES.add(spawn(SkinTextures.Model.WIDE, "UryuClassic", -1.0, 3.2));
		ReiatsuTest.LOGGER.info("[phase7] blut stand-ins: slim {} classic {}", FAKES.get(0).getSkinTextures().model(), FAKES.get(1).getSkinTextures().model());
	}

	private static OtherClientPlayerEntity spawn(SkinTextures.Model model, String name, double x, double z) {
		OtherClientPlayerEntity e = new OtherClientPlayerEntity(mc.world, new GameProfile(uuidWith(model), name));
		e.refreshPositionAndAngles(x + 0.5, -60, z, 180f, 0f);
		e.bodyYaw = 180f;
		e.prevBodyYaw = 180f;
		e.headYaw = 180f;
		e.prevHeadYaw = 180f;
		mc.world.addEntity(e);
		return e;
	}

	private static void removeFakes() {
		for (OtherClientPlayerEntity e : FAKES) {
			UryuQuincyFeature.forceShikai(e.getId(), false);
			if (mc.world != null) {
				mc.world.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		FAKES.clear();
	}

	/** One shot of the stand-in scene: the three bodies (the two stand-ins and the local player's own arms in first person) are released by a synthetic effect event in the same frame. */
	private static void skinShot(String name, int ms, boolean night, boolean rm) {
		step(name + " (scene)", 24, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			FxConfig.reduceMotion = rm;
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			cmd("tp @s 0.5 -60 0.5 0 6", "time set " + (night ? "midnight" : "noon"), "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state base uryu", "reiatsu full");
			if (FAKES.isEmpty()) {
				spawnFakes();
			}
		});
		step(name + " (arm)", 1, () -> {
			FxClock.unfreeze();
			fpDrawsBefore = UryuQuincyFeature.firstPersonDraws;
			List<AbstractClientPlayerEntity> all = new ArrayList<>(FAKES);
			all.add(mc.player);
			for (AbstractClientPlayerEntity p : all) {
				UryuQuincyFeature.forceShikai(p.getId(), true);
				EffectEventS2C e = new EffectEventS2C(70, p.getId(), 4711 + p.getId(), p.getX(), p.getY(), p.getZ(), (float) -Math.sin(Math.toRadians(p.bodyYaw)), 0f,
						(float) Math.cos(Math.toRadians(p.bodyYaw)), -1, 0, new float[0]);
				FxEvents.onEffectEvent(mc, e);
			}
			FxClock.freezeIn(ms / 1000.0);
		});
		stepUntil(name + " (frozen)", 2, 20 * 20, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			for (OtherClientPlayerEntity f : FAKES) {
				logState(name + " " + f.getSkinTextures().model(), f.getId(), ms);
			}
			if (ms >= 300 && ms <= 4000) {
				cCheck(name + ": the overlay was drawn on the stand-ins and in first person (fp draws " + (UryuQuincyFeature.firstPersonDraws - fpDrawsBefore) + ")",
						() -> UryuQuincyFeature.veinDraws > 0 ? null : "no vein draw");
			}
			for (OtherClientPlayerEntity f : FAKES) {
				checkAt(name + " " + f.getSkinTextures().model(), f.getId(), ms / 1000.0, rm);
			}
		});
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxConfig.reduceMotion = false;
		});
	}

	// ------------------------------------------------------------------------------------------ checks

	private static void logState(String name, int id, int ms) {
		var s = UryuQuincyFeature.state(id);
		ReiatsuTest.LOGGER.info("[phase7] BLUT {} t {} ms: shikai {} level {} stage {} drawn {} veinDraws {} fp {} flash {}", name, ms, s.shikai(),
				String.format(Locale.ROOT, "%.3f", s.veinLevel()), s.veinStage(), UryuQuincyFeature.drawn, UryuQuincyFeature.veinDraws, UryuQuincyFeature.firstPersonDraws,
				String.format(Locale.ROOT, "%.2f", ScreenFx.lastFlashAlpha));
	}

	/**
	 * The table at the frozen moment (the clock of the feature starts with the event frame, the frozen time is {@code t} plus up to a frame):
	 * nothing lit at 0 ms, the first stages at 200 ms, the sweep under way at 400 ms, every stage at 550 ms and later; in Reduce Motion the level is
	 * the constant 0.7 from the ignition to the end of the heartbeat.
	 */
	private static void checkAt(String name, int id, double t, boolean rm) {
		cCheck(name + ": vein state at " + t + " s", () -> {
			var s = UryuQuincyFeature.state(id);
			if (!s.shikai()) {
				return "not in the SHIKAI look";
			}
			if (t < 0.19) {
				return s.veinStage() < 0 || s.veinLevel() == 0.0 ? null : "veins before the ignition: stage " + s.veinStage();
			}
			if (t < 0.3) {
				return s.veinStage() >= 0 && s.veinStage() <= 4 ? null : "stage " + s.veinStage();
			}
			if (t < 0.5) {
				return s.veinStage() >= 2 && s.veinStage() <= UryuBlutTiming.STAGES - 1 ? null : "stage " + s.veinStage();
			}
			if (s.veinStage() != UryuBlutTiming.STAGES - 1) {
				return "stage " + s.veinStage() + " after the sweep";
			}
			if (rm && t < 2.9) {
				return Math.abs(s.veinLevel() - UryuBlutTiming.RM_CONSTANT) < 1e-6 ? null : "Reduce Motion level " + s.veinLevel();
			}
			if (t > 3.5) {
				return Math.abs(s.veinLevel() - 0.5) < 0.02 ? null : "state level " + s.veinLevel();
			}
			return s.veinLevel() >= 0.5 - 1e-6 && s.veinLevel() <= 1.0 + 1e-6 ? null : "level " + s.veinLevel();
		});
	}
}
