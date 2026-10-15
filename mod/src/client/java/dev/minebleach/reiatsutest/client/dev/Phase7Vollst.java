package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.wants;

import com.mojang.authlib.GameProfile;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.BankaiEnd;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxEvents;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuQuincyFeature;
import dev.minebleach.reiatsutest.client.fx.UryuWingRenderer;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.fx.UryuVollstTiming;
import dev.minebleach.reiatsutest.core.fx.UryuWingMath;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Harness scenarios of the Quincy: Vollstaendig release and the bankai state (U-FX 7, URYU_VFX_STORYBOARD 2.10, 2.11, 2.16 and 6),
 * {@code gradlew runPhase7 -Phold=<scenario>[,...]}. Screenshots {@code run/screenshots/p7_07_vollst_*.png}:
 * <ul>
 * <li>{@code uryu_vollst}: the real flow (SHIKAI, key G = the bankai release) frozen {@code t} ms after the event: {@code p7_07_vollst_t{0,300,500,650,900,1250,2200}}
 * and {@code _t5000} (the state) from the front, {@code p7_07_vollst_back_t{900,1250,5000}} from behind, {@code p7_07_vollst_q3_t{650,900,1250}} from the
 * three-quarter view, {@code p7_07_vollst_night_t{650,1250,5000}} at midnight, {@code p7_07_vollst_fp_t{900,1250,5000}} in first person (no wing in
 * the camera: the renderer must not have drawn the owner; the bracer of the first person arm from 1250 ms on);</li>
 * <li>{@code uryu_wings}: the state: {@code p7_07_vollst_wings_{idle,walk,air,sneak}} (the flap amplitude of 4 degrees, the sneak fold, the air lift), and the
 * stardust / star counts;</li>
 * <li>{@code uryu_vollst_rm}: {@code p7_07_vollst_rm_t{650,900}}: Reduce Motion (flash 0.40, no shake, grade kept, calm flakes);</li>
 * <li>{@code uryu_vollst_end}: the bankai ends (back to SHIKAI): {@code p7_07_vollst_end_t{0,150,300}} and the leftovers audit (no wing, no layer left);</li>
 * <li>{@code uryu_vollst_f13}: three Uryu in BANKAI side by side (the local player and two stand-ins of the two skin models): the frame statistics of F13
 * (400 frames after 90 warm-up) and {@code p7_07_vollst_f13}.</li>
 * </ul>
 * Nothing here was run (the author of the step must not launch the game): the checks read the client state at each frozen moment.
 */
final class Phase7Vollst {
	private static final List<OtherClientPlayerEntity> FAKES = new ArrayList<>();
	private static final List<Double> FRAME_MS = new ArrayList<>();
	private static boolean hooked;
	private static boolean recording;
	private static long lastFrameNanos;
	private static long perfFrames0;
	private static double rollMin;
	private static double rollMax;
	private static boolean sampling;

	private Phase7Vollst() {
	}

	static void steps() {
		step("vollst: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(70);
			FxTimelines.clear();
			ScreenFx.clear();
			hookFrames();
		});
		if (wants("uryu_vollst")) {
			vollst();
		}
		if (wants("uryu_vollst_rm")) {
			rm();
		}
		if (wants("uryu_wings")) {
			wings();
		}
		if (wants("uryu_vollst_end")) {
			end();
		}
		if (wants("uryu_vollst_f13")) {
			f13();
		}
		step("vollst: cleanup", 4, Phase7Vollst::cleanup);
	}

	private static void cleanup() {
		removeFakes();
		UryuQuincyFeature.clear();
		FxConfig.reduceMotion = false;
		FxClock.unfreeze();
		FxTimelines.setFreezeAt(-1);
		FxTimelines.clear();
		ScreenFx.clear();
		sampling = false;
		recording = false;
		mc.options.forwardKey.setPressed(false);
		mc.options.jumpKey.setPressed(false);
		mc.options.sneakKey.setPressed(false);
		mc.options.setPerspective(Perspective.FIRST_PERSON);
		mc.options.hudHidden = false;
		cmd("time set noon", "reiatsu dev ragefree false", "reiatsu state sealed uryu");
	}

	private static void hookFrames() {
		if (hooked) {
			return;
		}
		hooked = true;
		WorldRenderEvents.START.register(ctx -> {
			if (sampling && mc.player != null) {
				double[] p = UryuWingRenderer.debugPose(mc.player.getId());
				if (p != null) {
					rollMin = Math.min(rollMin, p[3]);
					rollMax = Math.max(rollMax, p[3]);
				}
			}
			if (!recording) {
				return;
			}
			long now = System.nanoTime();
			if (lastFrameNanos != 0) {
				FRAME_MS.add((now - lastFrameNanos) / 1.0e6);
			}
			lastFrameNanos = now;
		});
	}

	// ------------------------------------------------------------------------------------------ scenes

	private static void scene(String name, String state, boolean night, Perspective view, float yaw, int settle) {
		step(name + " (scene)", settle, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			UryuQuincyFeature.clear();
			mc.options.setPerspective(view);
			cmd(String.format(Locale.ROOT, "tp @s 0.5 -60 0.5 %.1f 8", yaw), "time set " + (night ? "midnight" : "noon"), "item replace entity @s hotbar.0 with reiatsu_test:uryu_cross");
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state " + state + " uryu", "reiatsu dev ragefree true", "reiatsu full"); // ragefree: the real G key needs Rage otherwise (DENIED_RAGE)
		});
	}

	private static String shotName(String view, boolean night, boolean rm, int ms) {
		return "07_vollst" + (night ? "_night" : "") + (rm ? "_rm" : "") + (view.isEmpty() ? "" : "_" + view) + "_t" + ms;
	}

	/** The real flow: SHIKAI, key G, the FX clock frozen {@code ms} after the release event, a shot and the checks. */
	private static void releaseShot(String view, Perspective persp, float yaw, int ms, boolean night, boolean rm) {
		String name = shotName(view, night, rm, ms);
		scene(name, "shikai", night, persp, yaw, 36);
		if (persp == Perspective.FIRST_PERSON) {
			step(name + " (hud)", 1, () -> mc.options.hudHidden = false); // vanilla draws no first person hands with the HUD hidden
		}
		final boolean late = ms >= 3000;
		final int[] before = new int[3];
		step(name + " (arm)", 2, () -> {
			FxConfig.reduceMotion = rm;
			// the release timeline is over before ~3 s: a later moment freezes by the absolute FX clock instead (see the steps below)
			FxTimelines.setFreezeAt(late ? -1 : ms);
			before[0] = UryuWingRenderer.passes;
			before[1] = UryuWingRenderer.firstPersonBracers;
			before[2] = FxParticles.live();
		});
		step(name + " (bankai G)", 1, () -> Phase6Harness.press(ReiatsuKeys.BANKAI));
		if (late) {
			// wait for the release event (wing clock >= 0), then freeze the FX clock where the wing clock reads ms
			stepUntil(name + " (release seen)", 1, 20 * 6, () -> { }, () -> {
				double c = UryuWingRenderer.wingClock(mc.player.getId());
				return c >= 0 && c < ms / 1000.0 - 0.3;
			});
			step(name + " (freeze in)", 1, () -> FxClock.freezeIn(ms / 1000.0 - UryuWingRenderer.wingClock(mc.player.getId())));
		}
		stepUntil(name + " (frozen)", 2, 20 * 14, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			int id = mc.player.getId();
			double clock = UryuWingRenderer.wingClock(id);
			double[] pose = UryuWingRenderer.debugPose(id);
			ReiatsuTest.LOGGER.info("[phase7] VOLLST {} t {} ms: wing clock {} state {} passes {} (+{}) wing quads {} band quads {} flake quads {} fp bracers {} glow {} particles {} flash {}", name, ms,
					String.format(Locale.ROOT, "%.3f", clock), UryuQuincyFeature.state(id), UryuWingRenderer.passes, UryuWingRenderer.passes - before[0], UryuWingRenderer.wingQuads,
					UryuWingRenderer.bandQuads, UryuWingRenderer.flakeQuads, UryuWingRenderer.firstPersonBracers, FxGlowBatch.live(), FxParticles.live(),
					String.format(Locale.ROOT, "%.2f", ScreenFx.lastFlashAlpha));
			checkClock(name, id, ms / 1000.0);
			if (persp == Perspective.FIRST_PERSON) {
				// the guard "no wing in the camera": the feature never draws the camera owner in first person, so the wing renderer was not called for him
				cCheck(name + ": first person: no wing pass for the owner (passes +" + (UryuWingRenderer.passes - before[0]) + ")", () -> UryuWingRenderer.passes == before[0] ? null
						: "the owner was drawn in first person");
				if (ms >= 1250) {
					cCheck(name + ": first person: the bracer of the bankai state is on the arm", () -> UryuWingRenderer.firstPersonBracers > before[1] ? null : "no bracer drawn");
				}
			} else if (ms >= 700) {
				cCheck(name + ": the wing pass drew (wing quads " + UryuWingRenderer.wingQuads + ")", () -> UryuWingRenderer.wingQuads > 0 && pose != null && pose[4] > 0.5 ? null : "no wings drawn");
			} else if (ms <= 600 && pose != null) {
				cCheck(name + ": the wings are not shown before the unfold", () -> pose[4] < 0.5 ? null : "wings shown at " + ms + " ms");
			}
			if (rm) {
				cCheck(name + ": Reduce Motion flash <= 0.40 (" + ScreenFx.lastFlashAlpha + ")", () -> ScreenFx.lastFlashAlpha <= UryuVollstTiming.FLASH_RM + 1e-6 ? null : "flash " + ScreenFx.lastFlashAlpha);
			}
		});
		step(name + " (release clock)", 2, () -> {
			mc.options.hudHidden = true;
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxConfig.reduceMotion = false;
		});
	}

	/** The wing clock at the frozen moment (the frozen time is {@code t} plus up to a frame). */
	private static void checkClock(String name, int id, double t) {
		cCheck(name + ": wing clock at " + t + " s", () -> {
			double c = UryuWingRenderer.wingClock(id);
			return c >= t - 0.03 && c <= t + 0.15 ? null : "clock " + c;
		});
	}

	// ------------------------------------------------------------------------------------------ the scenarios

	private static void vollst() {
		for (int ms : new int[] {0, 300, 500, 650, 900, 1250, 2200, 5000}) {
			releaseShot("", Perspective.THIRD_PERSON_FRONT, 0f, ms, false, false);
		}
		for (int ms : new int[] {900, 1250, 5000}) {
			releaseShot("back", Perspective.THIRD_PERSON_BACK, 0f, ms, false, false);
		}
		for (int ms : new int[] {650, 900, 1250}) {
			// three-quarter: the body turned 45 degrees against the front camera
			releaseShot("q3", Perspective.THIRD_PERSON_FRONT, 45f, ms, false, false);
		}
		for (int ms : new int[] {650, 1250, 5000}) {
			releaseShot("", Perspective.THIRD_PERSON_FRONT, 0f, ms, true, false);
		}
		for (int ms : new int[] {900, 1250, 5000}) {
			releaseShot("fp", Perspective.FIRST_PERSON, 0f, ms, false, false);
		}
	}

	private static void rm() {
		for (int ms : new int[] {650, 900}) {
			releaseShot("", Perspective.THIRD_PERSON_FRONT, 0f, ms, false, true);
		}
	}

	/** The state: idle (the flap), walking, in the air, sneaking. The attachment comes without an event: the wings unfold by themselves after the wait. */
	private static void wings() {
		scene("wings", "bankai", false, Perspective.THIRD_PERSON_BACK, 0f, 20 * 4);
		step("wings: idle sampling", 20 * 5, () -> {
			rollMin = 99;
			rollMax = -99;
			sampling = true;
		});
		step("wings: idle shot (freeze)", 1, () -> {
			sampling = false;
			FxClock.freezeIn(0.0);
		});
		stepUntil("wings: idle frozen", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step("wings: idle shot", 3, () -> {
			Phase7Uryu.shot7("07_vollst_wings_idle");
			int id = mc.player.getId();
			ReiatsuTest.LOGGER.info("[phase7] WINGS idle: roll min {} max {} pose {} clock {} specks {} tip stars {}", String.format(Locale.ROOT, "%.2f", rollMin), String.format(Locale.ROOT, "%.2f", rollMax),
					java.util.Arrays.toString(UryuWingRenderer.debugPose(id)), String.format(Locale.ROOT, "%.2f", UryuWingRenderer.wingClock(id)), dev.minebleach.reiatsutest.client.fx.UryuFxStats.wingSpecks(),
					dev.minebleach.reiatsutest.client.fx.UryuFxStats.wingTipStars());
			cCheck("wings idle: the flap is +-" + UryuWingMath.FLAP_DEG + " degrees (" + rollMin + " .. " + rollMax + ")",
					() -> rollMax > UryuWingMath.FLAP_DEG - 1.0 && rollMax < UryuWingMath.FLAP_DEG + 1.0 && rollMin < -UryuWingMath.FLAP_DEG + 1.0 && rollMin > -UryuWingMath.FLAP_DEG - 1.0 ? null
							: "roll " + rollMin + " .. " + rollMax);
			cCheck("wings idle: the four tips are published", () -> {
				Vec3d[] tips = UryuWingRenderer.wingTips(id, 1.0f);
				if (tips == null || tips.length != 4) {
					return "no tips";
				}
				// slot 0 is the upper right tip, 1 the upper left: they stand at their WING_TIPS offsets from the feet (the flap moves them a little)
				Vec3d feet = mc.player.getLerpedPos(1.0f);
				for (int s = 0; s < 4; s++) {
					double[] want = UryuBalance.WING_TIPS[s];
					double dh = tips[s].y - feet.y - want[1];
					double side = Math.hypot(tips[s].x - feet.x, tips[s].z - feet.z);
					if (Math.abs(dh) > 0.45 || Math.abs(side - Math.hypot(want[0], want[2])) > 0.45) {
						return "tip " + s + " dh " + dh + " horizontal " + side;
					}
				}
				return null;
			});
		});
		step("wings: idle release", 1, FxClock::unfreeze);
		// walking
		step("wings: walk", 30, () -> mc.options.forwardKey.setPressed(true));
		step("wings: walk (freeze)", 1, () -> FxClock.freezeIn(0.0));
		stepUntil("wings: walk frozen", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step("wings: walk shot", 3, () -> {
			mc.options.forwardKey.setPressed(false);
			Phase7Uryu.shot7("07_vollst_wings_walk");
			ReiatsuTest.LOGGER.info("[phase7] WINGS walk: pose {}", java.util.Arrays.toString(UryuWingRenderer.debugPose(mc.player.getId())));
			FxClock.unfreeze();
		});
		// in the air: a jump, the shot near the top
		step("wings: settle", 30, () -> { });
		step("wings: jump", 6, () -> mc.options.jumpKey.setPressed(true));
		step("wings: air (freeze)", 1, () -> {
			mc.options.jumpKey.setPressed(false);
			FxClock.freezeIn(0.0);
		});
		stepUntil("wings: air frozen", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step("wings: air shot", 3, () -> {
			Phase7Uryu.shot7("07_vollst_wings_air");
			double[] p = UryuWingRenderer.debugPose(mc.player.getId());
			ReiatsuTest.LOGGER.info("[phase7] WINGS air: pose {}", java.util.Arrays.toString(p));
			cCheck("wings air: the air value rises while the player is off the ground", () -> p != null && (p[1] > 0.3 || mc.player.isOnGround()) ? null : "air " + (p == null ? "none" : p[1]));
			FxClock.unfreeze();
		});
		// sneaking
		step("wings: land", 30, () -> mc.options.sneakKey.setPressed(true));
		step("wings: sneak (freeze)", 15, () -> FxClock.freezeIn(0.0));
		stepUntil("wings: sneak frozen", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step("wings: sneak shot", 3, () -> {
			Phase7Uryu.shot7("07_vollst_wings_sneak");
			double[] p = UryuWingRenderer.debugPose(mc.player.getId());
			ReiatsuTest.LOGGER.info("[phase7] WINGS sneak: pose {}", java.util.Arrays.toString(p));
			cCheck("wings sneak: the wings fold (sneak " + (p == null ? "none" : p[0]) + ")", () -> p != null && p[0] > 0.8 ? null : "sneak " + (p == null ? "none" : p[0]));
			mc.options.sneakKey.setPressed(false);
			FxClock.unfreeze();
		});
	}

	/** The end: back to SHIKAI after the bankai ran: the wings break, the bands fade, nothing is left behind. */
	private static void end() {
		for (int ms : new int[] {0, 150, 300}) {
			String name = "07_vollst_end_t" + ms;
			scene(name, "bankai", false, Perspective.THIRD_PERSON_FRONT, 0f, 20 * 4);
			final int[] before = new int[1];
			step(name + " (end)", 1, () -> {
				before[0] = dev.minebleach.reiatsutest.client.fx.UryuFxStats.wingEnds();
				cmd("reiatsu state shikai uryu");
				FxClock.freezeIn(ms / 1000.0);
			});
			stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
			step(name + " (shot)", 3, () -> {
				Phase7Uryu.shot7(name);
				ReiatsuTest.LOGGER.info("[phase7] VOLLST END {} t {} ms: ends {} particles {} glow {}", name, ms, dev.minebleach.reiatsutest.client.fx.UryuFxStats.wingEnds(), FxParticles.live(),
						FxGlowBatch.live());
				cCheck(name + ": the end was handled once (ends +" + (dev.minebleach.reiatsutest.client.fx.UryuFxStats.wingEnds() - before[0]) + ")",
						() -> dev.minebleach.reiatsutest.client.fx.UryuFxStats.wingEnds() - before[0] == 1 ? null : "ends " + (dev.minebleach.reiatsutest.client.fx.UryuFxStats.wingEnds() - before[0]));
				FxClock.unfreeze();
			});
		}
		step("end: audit wait", 20 * 7, () -> { });
		step("end: audit", 2, () -> {
			cCheck("end: no bankai effect left on the owner (" + BankaiEnd.lastAudit + ")", () -> BankaiEnd.dirtyAudits == 0 ? null : "dirty audits " + BankaiEnd.dirtyAudits);
			cCheck("end: the player is out of the BANKAI look", () -> !UryuQuincyFeature.state(mc.player.getId()).bankai() ? null : "still BANKAI");
		});
	}

	// ------------------------------------------------------------------------------------------ F13 and the stand-ins

	private static UUID uuidWith(SkinTextures.Model model) {
		for (int k = 0; k < 500; k++) {
			UUID id = UUID.nameUUIDFromBytes(("uryu-vollst-" + k).getBytes());
			if (DefaultSkinHelper.getSkinTextures(id).model() == model) {
				return id;
			}
		}
		return UUID.randomUUID();
	}

	private static OtherClientPlayerEntity spawn(SkinTextures.Model model, String name, double x, double z) {
		OtherClientPlayerEntity e = new OtherClientPlayerEntity(mc.world, new GameProfile(uuidWith(model), name));
		e.refreshPositionAndAngles(x + 0.5, -60, z, 0f, 0f);
		e.bodyYaw = 0f;
		e.prevBodyYaw = 0f;
		e.headYaw = 0f;
		e.prevHeadYaw = 0f;
		mc.world.addEntity(e);
		return e;
	}

	private static void removeFakes() {
		for (OtherClientPlayerEntity e : FAKES) {
			UryuQuincyFeature.forceBankai(e.getId(), false);
			if (mc.world != null) {
				mc.world.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		FAKES.clear();
	}

	/** F13: three Uryu in BANKAI in a row, the camera in front; 400 frames after 90 warm-up. */
	private static void f13() {
		scene("f13", "bankai", false, Perspective.THIRD_PERSON_FRONT, 0f, 20 * 4);
		step("f13: stand-ins", 6, () -> {
			removeFakes();
			FAKES.add(spawn(SkinTextures.Model.SLIM, "UryuSlim", 3.0, 0.0));
			FAKES.add(spawn(SkinTextures.Model.WIDE, "UryuClassic", -3.0, 0.0));
			for (OtherClientPlayerEntity f : FAKES) {
				UryuQuincyFeature.forceBankai(f.getId(), true);
				EffectEventS2C e = new EffectEventS2C(71, f.getId(), 7100 + f.getId(), f.getX(), f.getY(), f.getZ(), 0f, 0f, 1f, -1, 0, new float[0]);
				FxEvents.onEffectEvent(mc, e);
			}
		});
		step("f13: warm up", 90, () -> { });
		step("f13: measure", 1, () -> {
			FRAME_MS.clear();
			FxClient.Stats.reset();
			perfFrames0 = FxClock.frame;
			lastFrameNanos = 0;
			recording = true;
		});
		stepUntil("f13: wait", 5, 20 * 40, () -> { }, () -> FxClock.frame - perfFrames0 >= 400);
		step("f13: result", 3, () -> {
			recording = false;
			Phase7Uryu.shot7("07_vollst_f13");
			List<Double> f = new ArrayList<>(FRAME_MS);
			if (f.size() < 30) {
				cCheck("F13: enough frames", () -> "only " + f.size());
				return;
			}
			double mean = f.stream().mapToDouble(Double::doubleValue).average().orElse(0);
			double[] sorted = f.stream().mapToDouble(Double::doubleValue).sorted().toArray();
			double p99 = sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))];
			ReiatsuTest.LOGGER.info(String.format(Locale.ROOT,
					"[phase7] PERF F13 (three Uryu in BANKAI): %d frames, mean %.2f ms = %.0f FPS, 1%% low %.2f ms = %.0f FPS; reiatsu_fx mean %.3f ms max %.3f ms; glow drawn %d, particles %d; wing quads %d band quads %d flake quads %d",
					f.size(), mean, 1000 / mean, p99, 1000 / p99, FxClient.Stats.meanMs(), FxClient.Stats.maxMs, FxGlowBatch.lastDrawn, FxParticles.live(), UryuWingRenderer.wingQuads,
					UryuWingRenderer.bandQuads, UryuWingRenderer.flakeQuads));
			cCheck("F13: reiatsu_fx mean " + String.format(Locale.ROOT, "%.3f", FxClient.Stats.meanMs()) + " ms <= 4 ms", () -> FxClient.Stats.meanMs() <= 4.0 ? null : "mean " + FxClient.Stats.meanMs());
			cCheck("F13: mean " + String.format(Locale.ROOT, "%.0f", 1000 / mean) + " FPS >= 60", () -> 1000 / mean >= 60 ? null : "mean fps " + 1000 / mean);
			cCheck("F13: 1% low " + String.format(Locale.ROOT, "%.0f", 1000 / p99) + " FPS >= 45", () -> 1000 / p99 >= 45 ? null : "1% low " + 1000 / p99);
		});
		step("f13: cleanup", 2, Phase7Vollst::removeFakes);
	}
}
