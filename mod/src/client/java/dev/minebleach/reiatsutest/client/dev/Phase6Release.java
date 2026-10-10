package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.press;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.selectSlot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.AuraFx;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxConfig;
import dev.minebleach.reiatsutest.client.fx.FxEvents;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxSound;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.hud.HudModel;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Phase 6 step 2 scenarios: release flash / ring / burst (2.1), auras (2.2), seal and bankai end (2.3), draw events. */
final class Phase6Release {
	private static boolean samplerOn;
	private static final List<double[]> FLASH_SAMPLES = new ArrayList<>();
	private static int peakParticles;
	private static int peakGlow;

	private Phase6Release() {
	}

	private static void sampler() {
		if (samplerOn) {
			return;
		}
		samplerOn = true;
		WorldRenderEvents.END.register(ctx -> {
			synchronized (FLASH_SAMPLES) {
				FLASH_SAMPLES.add(new double[] {FxClock.now, ScreenFx.lastFlashAlpha});
				if (FLASH_SAMPLES.size() > 4000) {
					FLASH_SAMPLES.remove(0);
				}
			}
			peakParticles = Math.max(peakParticles, FxParticles.live());
			peakGlow = Math.max(peakGlow, FxGlowBatch.live());
		});
	}

	private static String ch(boolean rukia) {
		return rukia ? "rukia" : "byakuya";
	}

	private static void toBase(boolean rukia) {
		cmd("item replace entity @s hotbar." + (rukia ? 0 : 1) + " with " + (rukia ? Phase6Harness.RUKIA : Phase6Harness.BYAKUYA),
				"reiatsu cooldowns clear", "reiatsu state base " + ch(rukia), "reiatsu full");
		selectSlot(rukia ? 0 : 1);
	}

	/** Releases for real (R key), freezes the FX clock at ms of the first running timeline, shoots, releases the clock. */
	private static void releaseShot(String name, boolean rukia, int ms) {
		step(name + " (base)", 30, () -> {
			FxClock.unfreeze();
			FxTimelines.clear();
			ScreenFx.clear();
			HudModel.clearTransient();
			toBase(rukia);
		});
		step(name + " (arm)", 4, () -> FxTimelines.setFreezeAt(ms));
		step(name + " (R)", 1, () -> press(ReiatsuKeys.RELEASE));
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	/** The bankai release events (2, 4) have no server transition yet in the harness: the event is built the way the server builds it. */
	private static void fakeShot(String name, int effectId, int ms) {
		step(name + " (clear)", 24, () -> {
			FxClock.unfreeze();
			FxTimelines.clear();
			ScreenFx.clear();
			HudModel.clearTransient();
		});
		step(name + " (arm)", 2, () -> FxTimelines.setFreezeAt(ms));
		step(name + " (event)", 1, () -> {
			var p = mc.player;
			var look = p.getRotationVec(1.0f);
			var e = new dev.minebleach.reiatsutest.net.EffectEventS2C(effectId, p.getId(), 4242, p.getX(), p.getY(), p.getZ(), (float) look.x, (float) look.y,
					(float) look.z, -1, 0, new float[0]);
			FxEvents.onEffectEvent(mc, e);
		});
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	static void steps() {
		step("rel: world setup (third person back, noon, creative)", 30, () -> {
			sampler();
			cmd("gamemode creative @s", "time set noon", "tp @s 0.5 -60 0.5 0 12");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			mc.options.hudHidden = true;
			FxConfig.reduceMotion = false;
			ScreenFx.clear();
			FxTimelines.clear();
		});
		step("rel: sounds resolve", 2, () -> {
			String[] ids = {"block.beacon.activate", "block.beacon.deactivate", "entity.generic.explode", "entity.player.attack.sweep", "block.amethyst_block.chime",
					"block.bell.use", "block.bell.resonate", "block.respawn_anchor.deplete", "entity.illusioner.cast_spell", "item.trident.return",
					"block.powder_snow.step", "entity.warden.heartbeat", "item.armor.equip_iron", "item.armor.equip_chain", "block.note_block.bass",
					"block.note_block.chime", "block.note_block.bell", "ui.toast.in", "ui.button.click"};
			for (String id : ids) {
				cCheck("sound id exists: " + id, () -> Registries.SOUND_EVENT.containsId(Identifier.ofVanilla(id)) ? null : "unknown");
			}
		});
		boolean all = Phase6Harness.wants("release");
		if (all || Phase6Harness.wants("relshots")) {
			for (boolean rukia : new boolean[] {true, false}) {
				for (int ms : new int[] {50, 100, 200, 500}) {
					releaseShot("02_release_" + ch(rukia) + "_t" + ms, rukia, ms);
				}
			}
		}
		if (all || Phase6Harness.wants("relnight")) {
			nightSteps();
		}
		if (all || Phase6Harness.wants("relflash")) {
			flashSteps();
		}
		if (all || Phase6Harness.wants("aura")) {
			auraSteps();
		}
		if (all || Phase6Harness.wants("seal")) {
			sealSteps();
		}
		if (all || Phase6Harness.wants("draw")) {
			drawSteps();
		}
		if (all || Phase6Harness.wants("relfab")) {
			fabulousSteps();
		}
		if (all || Phase6Harness.wants("relperf")) {
			perfSteps();
		}
		if (Phase6Harness.explicit("console")) {
			Phase6Harness.consoleMode = true;
			consoleStep();
		}
	}

	private static void nightSteps() {
		// night (the glows) and with the HUD (title card), plus a top view for the ring radius
		step("rel: night", 20, () -> cmd("time set midnight"));
		for (boolean rukia : new boolean[] {true, false}) {
			for (int ms : new int[] {100, 300}) {
				releaseShot("02_release_" + ch(rukia) + "_night_t" + ms, rukia, ms);
			}
		}
		step("rel: day, HUD on", 20, () -> {
			cmd("time set noon");
			mc.options.hudHidden = false;
		});
		releaseShot("02_release_rukia_hud_t600", true, 600);
		releaseShot("02_release_byakuya_hud_t600", false, 600);
		step("rel: high view (ring radius)", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 0.5 0 62");
		});
		releaseShot("02_release_rukia_ring_t300", true, 300);
		for (int id : new int[] {2, 4}) {
			fakeShot("02_release_" + (id == 2 ? "rukia" : "byakuya") + "_bankai_ring_t300", id, 300);
		}
		step("rel: back to third person", 20, () -> {
			cmd("tp @s 0.5 -60 0.5 0 12");
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		});
	}

	private static void flashSteps() {
		step("rel: view for the flash timing", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 0.5 0 12", "time set noon");
		});
		// flash timing (frame log) normal and reduce motion
		step("rel: flash timing (normal)", 30, () -> {
			FLASH_SAMPLES.clear();
			peakParticles = 0;
			peakGlow = 0;
			toBase(true);
		});
		step("rel: R", 40, () -> press(ReiatsuKeys.RELEASE));
		step("rel: flash check normal", 2, () -> {
			double[] d = flashSpan();
			ReiatsuTest.LOGGER.info("[phase6] FLASH normal: first {} last {} duration {} s, peak {}", fmt(d[0]), fmt(d[1]), fmt(d[1] - d[0]), fmt(d[2]));
			cCheck("flash alpha peaked near 0.60 (" + fmt(d[2]) + ")", () -> d[2] > 0.5 && d[2] <= 0.61 ? null : "peak " + d[2]);
			cCheck("flash lasted about 0.22 s (" + fmt(d[1] - d[0]) + ")", () -> d[1] - d[0] > 0.12 && d[1] - d[0] < 0.30 ? null : "span " + (d[1] - d[0]));
		});
		step("rel: flash timing (reduce motion)", 30, () -> {
			FxConfig.reduceMotion = true;
			FLASH_SAMPLES.clear();
			cmd("reiatsu state sealed");
		});
		step("rel: base again", 30, () -> toBase(true));
		step("rel: R (RM)", 40, () -> {
			FLASH_SAMPLES.clear();
			press(ReiatsuKeys.RELEASE);
		});
		step("rel: flash check RM", 2, () -> {
			double[] d = flashSpan();
			ReiatsuTest.LOGGER.info("[phase6] FLASH reduce motion: duration {} s, peak {}", fmt(d[1] - d[0]), fmt(d[2]));
			cCheck("reduce motion: flash <= 0.07 s plus one frame (" + fmt(d[1] - d[0]) + ")", () -> d[1] - d[0] <= 0.09 ? null : "span " + (d[1] - d[0]));
			cCheck("reduce motion: flash peak <= 0.40", () -> d[2] <= 0.401 ? null : "peak " + d[2]);
			FxConfig.reduceMotion = false;
		});
		// dev hot reload: a value from run/fx_override.json changes the next release without a restart
		step("rel: hot reload: write override", 4, () -> {
			writeOverride("{\"release.flashRukiaShikai\": 0.5}");
			cmd("reiatsu state sealed");
		});
		step("rel: hot reload: base", 70, () -> toBase(true));
		step("rel: hot reload: R", 40, () -> {
			FLASH_SAMPLES.clear();
			press(ReiatsuKeys.RELEASE);
		});
		step("rel: hot reload: check", 2, () -> {
			double[] d = flashSpan();
			cCheck("hot reload: flash peak follows fx_override.json (" + fmt(d[2]) + ")", () -> Math.abs(d[2] - 0.5) < 0.02 ? null : "peak " + d[2]);
			writeOverride("{}");
		});
		step("rel: hot reload: back", 70, () -> cmd("reiatsu state sealed"));
		step("rel: sounds of the release", 2, () -> {
			String log = String.join(",", FxSound.LOG);
			ReiatsuTest.LOGGER.info("[phase6] sounds so far: {}", log);
			cCheck("release sounds played", () -> log.contains("block.beacon.activate") && log.contains("entity.generic.explode") && log.contains("entity.player.attack.sweep")
					&& log.contains("block.amethyst_block.chime") ? null : "missing: " + log);
			cCheck("peak live particles of a release with its aura " + peakParticles + " <= 170", () -> peakParticles <= 170 ? null : "peak " + peakParticles);
			cCheck("peak live glow sprites " + peakGlow + " <= 90", () -> peakGlow <= 90 ? null : "peak " + peakGlow);
		});
	}

	private static void writeOverride(String json) {
		try {
			java.nio.file.Files.writeString(mc.runDirectory.toPath().resolve("fx_override.json"), json);
		} catch (java.io.IOException e) {
			ReiatsuTest.LOGGER.warn("[phase6] cannot write fx_override.json: {}", e.toString());
		}
	}

	private static String fmt(double v) {
		return String.format(java.util.Locale.ROOT, "%.3f", v);
	}

	/** First time, last time and peak of a visible flash in the frame log. */
	private static double[] flashSpan() {
		double first = -1;
		double last = -1;
		double peak = 0;
		synchronized (FLASH_SAMPLES) {
			for (double[] s : FLASH_SAMPLES) {
				if (s[1] > 0.01) {
					if (first < 0) {
						first = s[0];
					}
					last = s[0];
					peak = Math.max(peak, s[1]);
				}
			}
		}
		return new double[] {first, last, peak};
	}

	private static void auraShot(String name, String character, String state, boolean night, double afterSeconds) {
		step(name + " (state)", 10, () -> {
			FxClock.unfreeze();
			FxTimelines.clear();
			cmd("time set " + (night ? "midnight" : "noon"), "item replace entity @s hotbar.0 with " + Phase6Harness.RUKIA,
					"item replace entity @s hotbar.1 with " + Phase6Harness.BYAKUYA, "reiatsu state sealed");
			selectSlot(character.equals("rukia") ? 0 : 1);
		});
		step(name + " (set)", 4, () -> cmd("reiatsu state " + state + " " + character));
		step(name + " (run)", 1, () -> FxClock.freezeIn(afterSeconds));
		stepUntil(name + " (frozen)", 2, 20 * 20, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			shot(name);
			int id = mc.player.getId();
			ReiatsuTest.LOGGER.info("[phase6] aura {}: live wisps of the player {}, particles live {}, glow live {}", name, AuraFx.liveFor(id), FxParticles.live(), FxGlowBatch.live());
			cCheck(name + " aura live " + AuraFx.liveFor(id) + " <= 120", () -> AuraFx.liveFor(id) <= 120 ? null : "live " + AuraFx.liveFor(id));
		});
		step(name + " (release clock)", 2, FxClock::unfreeze);
	}

	private static void auraSteps() {
		step("aura: third person view", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 0.5 180 8");
		});
		auraShot("02_aura_rukia_shikai_noon", "rukia", "shikai", false, 3.0);
		auraShot("02_aura_rukia_shikai_night", "rukia", "shikai", true, 3.0);
		auraShot("02_aura_byakuya_shikai_noon", "byakuya", "shikai", false, 3.0);
		auraShot("02_aura_byakuya_shikai_night", "byakuya", "shikai", true, 3.0);
		auraShot("02_aura_byakuya_bankai_night", "byakuya", "bankai", true, 3.0);
		auraShot("02_aura_rukia_bankai_night", "rukia", "bankai", true, 3.0);
		auraShot("02_aura_rukia_bankai_noon", "rukia", "bankai", false, 4.0);
		auraShot("02_aura_byakuya_bankai_noon", "byakuya", "bankai", false, 3.0);
		step("aura: sound check", 2, () -> {
			String log = String.join(",", FxSound.LOG);
			cCheck("aura sounds (powder snow step / heartbeat) played", () -> log.contains("block.powder_snow.step") && log.contains("entity.warden.heartbeat") ? null : "log " + log);
		});
	}

	private static void sealShot(String name, boolean rukia, String state, int ms) {
		step(name + " (state)", 30, () -> {
			FxClock.unfreeze();
			FxTimelines.clear();
			ScreenFx.clear();
			cmd("item replace entity @s hotbar.0 with " + Phase6Harness.RUKIA, "item replace entity @s hotbar.1 with " + Phase6Harness.BYAKUYA,
					"reiatsu cooldowns clear", "reiatsu state " + state + " " + ch(rukia), "reiatsu full");
			selectSlot(rukia ? 0 : 1);
		});
		step(name + " (arm)", 20, () -> FxTimelines.setFreezeAt(ms));
		step(name + " (V)", 1, () -> press(ReiatsuKeys.SEAL));
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	private static void sealSteps() {
		step("seal: view", 20, () -> cmd("tp @s 0.5 -60 0.5 0 12", "time set midnight"));
		for (boolean rukia : new boolean[] {true, false}) {
			for (int ms : new int[] {100, 300, 600}) {
				sealShot("02_seal_" + ch(rukia) + "_t" + ms, rukia, "shikai", ms);
			}
		}
		sealShot("02_bankai_end_rukia_t200", true, "bankai", 200);
		step("seal: events counted", 2, () -> cCheck("real release and seal events were handled by the new effects (" + FxEvents.handled + ")", () -> FxEvents.handled >= 7 ? null : "handled " + FxEvents.handled));
		step("seal: sounds", 2, () -> {
			String log = String.join(",", FxSound.LOG);
			cCheck("seal sounds played", () -> log.contains("block.beacon.deactivate") ? null : "log " + log);
		});
	}

	private static void drawSteps() {
		step("draw: sealed", 30, () -> {
			FxSound.LOG.clear();
			cmd("time set noon", "reiatsu state sealed", "reiatsu full", "item replace entity @s hotbar.0 with " + Phase6Harness.RUKIA);
			selectSlot(0);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
		});
		step("draw: J", 1, () -> press(ReiatsuKeys.DRAW));
		step("draw: wait 4", 4, () -> { });
		step("draw: shot k4", 1, () -> shot("02_draw_rukia_k4"));
		step("draw: wait 3", 3, () -> { });
		step("draw: shot k7", 1, () -> shot("02_draw_rukia_k7"));
		step("draw: wait 3b", 3, () -> { });
		step("draw: shot k10", 1, () -> shot("02_draw_rukia_k10"));
		step("draw: wait draw animation", 30, () -> { });
		step("draw: sounds of the draw", 2, () -> {
			String log = String.join(",", FxSound.LOG);
			cCheck("draw sounds (scabbard rattle and shing) played", () -> log.contains("item.armor.equip_iron") && log.contains("item.trident.return") ? null : "log " + log);
		});
		step("draw: V (sheathe)", 1, () -> {
			FxSound.LOG.clear();
			press(ReiatsuKeys.SEAL);
		});
		step("draw: wait s6", 6, () -> { });
		step("draw: shot s6", 1, () -> shot("02_sheathe_rukia_k6"));
		step("draw: wait s6b", 6, () -> { });
		step("draw: shot s12", 1, () -> shot("02_sheathe_rukia_k12"));
		step("draw: wait sheathe", 30, () -> { });
		step("draw: sheathe sounds", 2, () -> {
			String log = String.join(",", FxSound.LOG);
			cCheck("sheathe sounds played", () -> log.contains("item.armor.equip_chain") ? null : "log " + log);
			cCheck("client sealed again", () -> ClientState.zanpakuto().zanpakutoState() == ZanpakutoState.SEALED ? null : "state " + ClientState.zanpakuto().zanpakutoState());
		});
	}

	// ------------------------------------------------------------------------------------------ Fabulous graphics

	private static void fabulousSteps() {
		step("fab: Fabulous graphics, third person, noon", 40, () -> {
			Phase6Harness.graphics(GraphicsMode.FABULOUS);
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("time set noon", "tp @s 0.5 -60 0.5 0 12");
		});
		step("fab: settle", 60, () -> { });
		releaseShot("02_fab_release_rukia_t200", true, 200);
		releaseShot("02_fab_release_byakuya_t500", false, 500);
		step("fab: aura view", 20, () -> cmd("tp @s 0.5 -60 0.5 180 8"));
		auraShot("02_fab_aura_rukia_bankai_noon", "rukia", "bankai", false, 4.0);
		auraShot("02_fab_aura_byakuya_shikai_noon", "byakuya", "shikai", false, 3.0);
		step("fab: back to Fancy", 40, () -> {
			Phase6Harness.graphics(GraphicsMode.FANCY);
			cmd("tp @s 0.5 -60 0.5 0 12", "reiatsu state sealed");
		});
	}

	// ------------------------------------------------------------------------------------------ frame cost

	private static long perfFrames0;
	private static long perfStart;

	private static void perfWindow(String name, int frames) {
		step("perf " + name + ": reset", 1, () -> {
			FxClient.Stats.reset();
			perfFrames0 = FxClock.frame;
			perfStart = System.nanoTime();
			peakParticles = 0;
			peakGlow = 0;
		});
		Phase6Harness.stepUntil("perf " + name + ": wait", 5, 20 * 40, () -> { }, () -> FxClock.frame - perfFrames0 >= frames);
		step("perf " + name + ": result", 2, () -> {
			double wall = (System.nanoTime() - perfStart) / 1.0e6 / Math.max(1, FxClock.frame - perfFrames0);
			ReiatsuTest.LOGGER.info("[phase6] PERF {}: reiatsu_fx mean {} ms max {} ms, frame wall mean {} ms, particles live {} (peak {}), glow live {} (peak {})", name,
					fmt(FxClient.Stats.meanMs()), fmt(FxClient.Stats.maxMs), fmt(wall), FxParticles.live(), peakParticles, FxGlowBatch.live(), peakGlow);
			cCheck("perf " + name + ": reiatsu_fx mean " + fmt(FxClient.Stats.meanMs()) + " ms < 1.0 ms", () -> FxClient.Stats.meanMs() < 1.0 ? null : "mean " + FxClient.Stats.meanMs());
		});
	}

	private static void perfAura(String name, String character, String state) {
		step("perf " + name + ": state", 4, () -> {
			cmd("item replace entity @s hotbar.0 with " + Phase6Harness.RUKIA, "item replace entity @s hotbar.1 with " + Phase6Harness.BYAKUYA, "reiatsu state sealed");
			FxTimelines.clear();
		});
		step("perf " + name + ": set", 4, () -> {
			selectSlot(character.equals("rukia") ? 0 : 1);
			cmd("reiatsu state " + state + " " + character);
		});
		step("perf " + name + ": steady state", 160, () -> { });
		perfWindow(name, 240);
	}

	private static void perfSteps() {
		step("perf: view (third person, noon, hud off)", 30, () -> {
			sampler();
			mc.options.hudHidden = true;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("gamemode creative @s", "time set noon", "tp @s 0.5 -60 0.5 180 8", "reiatsu state sealed");
			FxTimelines.clear();
			ScreenFx.clear();
		});
		step("perf: baseline settle", 60, () -> { });
		perfWindow("baseline (sealed, no fx)", 240);
		perfAura("aura rukia shikai", "rukia", "shikai");
		perfAura("aura byakuya shikai", "byakuya", "shikai");
		perfAura("aura rukia bankai", "rukia", "bankai");
		perfAura("aura byakuya bankai", "byakuya", "bankai");
		// release peak: Rukia, from BASE, first 2 seconds
		step("perf release: base", 40, () -> {
			FxTimelines.clear();
			toBase(true);
			cmd("tp @s 0.5 -60 0.5 0 12");
		});
		step("perf release: R", 1, () -> {
			FxClient.Stats.reset();
			perfFrames0 = FxClock.frame;
			perfStart = System.nanoTime();
			peakParticles = 0;
			peakGlow = 0;
			press(ReiatsuKeys.RELEASE);
		});
		Phase6Harness.stepUntil("perf release: wait 2 s", 5, 20 * 40, () -> { }, () -> FxClock.frame - perfFrames0 >= 200);
		step("perf release: result", 2, () -> {
			double wall = (System.nanoTime() - perfStart) / 1.0e6 / Math.max(1, FxClock.frame - perfFrames0);
			ReiatsuTest.LOGGER.info("[phase6] PERF release rukia (2 s from R): reiatsu_fx mean {} max {} ms, wall mean {} ms, peak particles {}, peak glow {}",
					fmt(FxClient.Stats.meanMs()), fmt(FxClient.Stats.maxMs), fmt(wall), peakParticles, peakGlow);
			cCheck("release: reiatsu_fx max " + fmt(FxClient.Stats.maxMs) + " ms < 1.0 ms", () -> FxClient.Stats.maxMs < 1.0 ? null : "max " + FxClient.Stats.maxMs);
			cmd("reiatsu state sealed");
		});
	}

	// ------------------------------------------------------------------------------------------ live console (hot tuning without restarts)

	private static long consoleStamp = -1;

	/**
	 * {@code -Phold=console}: the game waits for {@code run/p6_cmd.txt}; every new write is executed line by line (screenshots go to
	 * {@code p6_<name>.png}) and then {@code run/p6_done.txt} gets the stamp of the command file. Numbers are tuned with
	 * {@code run/fx_override.json} (hot reload), no restart. Lines: {@code rel rukia|byakuya ms name}, {@code fake id ms name},
	 * {@code aura rukia|byakuya shikai|bankai noon|night seconds name}, {@code seal rukia|byakuya shikai|bankai ms name},
	 * {@code time noon|midnight}, {@code view back|aura|top|front}, {@code hud on|off}, {@code gfx fancy|fabulous}, {@code wait ticks}, {@code quit}.
	 */
	private static void consoleStep() {
		Phase6Harness.stepUntil("console: waiting for run/p6_cmd.txt", 0, 0, () -> { }, Phase6Release::pollConsole);
	}

	private static boolean pollConsole() {
		java.nio.file.Path f = mc.runDirectory.toPath().resolve("p6_cmd.txt");
		try {
			if (!java.nio.file.Files.exists(f)) {
				return false;
			}
			long stamp = java.nio.file.Files.getLastModifiedTime(f).toMillis();
			if (stamp == consoleStamp) {
				return false;
			}
			consoleStamp = stamp;
			java.util.List<String> lines = java.nio.file.Files.readAllLines(f);
			boolean quit = false;
			Phase6Harness.beginInsert();
			try {
				for (String line : lines) {
					String[] w = line.trim().split("\\s+");
					if (w.length == 0 || w[0].isEmpty() || w[0].startsWith("#")) {
						continue;
					}
					try {
						quit |= consoleCommand(w);
					} catch (RuntimeException e) {
						ReiatsuTest.LOGGER.warn("[phase6] console: bad line '{}': {}", line, e.toString());
					}
				}
				final long st = stamp;
				step("console: done " + st, 1, () -> {
					try {
						java.nio.file.Files.writeString(mc.runDirectory.toPath().resolve("p6_done.txt"), Long.toString(st));
					} catch (java.io.IOException e) {
						ReiatsuTest.LOGGER.warn("[phase6] console: cannot write p6_done.txt: {}", e.toString());
					}
				});
				if (!quit) {
					consoleStep();
				}
			} finally {
				Phase6Harness.endInsert();
			}
			return true;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	private static boolean consoleCommand(String[] w) {
		switch (w[0]) {
			case "rel" -> releaseShot(w.length > 3 ? w[3] : "tune_rel_" + w[1] + "_" + w[2], w[1].equals("rukia"), Integer.parseInt(w[2]));
			case "fake" -> fakeShot(w.length > 3 ? w[3] : "tune_fake_" + w[1] + "_" + w[2], Integer.parseInt(w[1]), Integer.parseInt(w[2]));
			case "aura" -> auraShot(w.length > 5 ? w[5] : "tune_aura_" + w[1] + "_" + w[2] + "_" + w[3], w[1], w[2], w[3].equals("night"), Double.parseDouble(w[4]));
			case "seal" -> sealShot(w.length > 4 ? w[4] : "tune_seal_" + w[1] + "_" + w[2] + "_" + w[3], w[1].equals("rukia"), w[2], Integer.parseInt(w[3]));
			case "time" -> step("console: time", 6, () -> cmd("time set " + w[1]));
			case "wait" -> step("console: wait", Integer.parseInt(w[1]), () -> { });
			case "hud" -> step("console: hud", 2, () -> mc.options.hudHidden = w[1].equals("off"));
			case "gfx" -> step("console: gfx", 60, () -> Phase6Harness.graphics(w[1].equals("fabulous") ? GraphicsMode.FABULOUS : GraphicsMode.FANCY));
			case "view" -> step("console: view", 20, () -> {
				switch (w[1]) {
					case "aura" -> {
						mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
						cmd("tp @s 0.5 -60 0.5 180 8");
					}
					case "top" -> {
						mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
						cmd("tp @s 0.5 -60 0.5 0 62");
					}
					case "front" -> {
						mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
						cmd("tp @s 0.5 -60 0.5 0 12");
					}
					default -> {
						mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
						cmd("tp @s 0.5 -60 0.5 0 12");
					}
				}
			});
			case "quit" -> {
				return true;
			}
			default -> ReiatsuTest.LOGGER.warn("[phase6] console: unknown command {}", w[0]);
		}
		return false;
	}
}
