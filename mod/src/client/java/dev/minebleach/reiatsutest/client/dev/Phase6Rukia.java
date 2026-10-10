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
import dev.minebleach.reiatsutest.client.fx.FreezeDesat;
import dev.minebleach.reiatsutest.client.fx.FxClient;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxDecals;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxMeshPass;
import dev.minebleach.reiatsutest.client.fx.FxMeshes;
import dev.minebleach.reiatsutest.client.fx.FxParticles;
import dev.minebleach.reiatsutest.client.fx.FxShapes;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.RukiaBankaiClient;
import dev.minebleach.reiatsutest.client.fx.RukiaEntityFx;
import dev.minebleach.reiatsutest.client.fx.RukiaFeature;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.hud.HudModel;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.Perspective;

/**
 * Phase 6 steps 6 and 7 scenarios: the Rukia shikai abilities (3.1 to 3.3), the bankai release (5.1), the passive (5.2) and
 * Absolute zero (5.4) with zombies as targets, plus the frame cost scenarios F6 (absolute zero, 32 encased zombies) and F7 (three
 * abilities within 2 s). Scenario names for {@code -Phold}: {@code rk6}, {@code rk7}, {@code rkperf}; console lines are
 * {@code rk <tsuki|hak|shira|abs|rb|passive> <ms> <name> [night] [front] [up] [down] [far]} and {@code rkperf <f6|f7>}.
 */
final class Phase6Rukia {
	private static boolean samplerOn;
	private static boolean recording;
	private static final List<Double> FRAME_MS = new ArrayList<>();
	private static long lastFrameNanos;
	private static int peakVertices;
	private static int peakDecalVertices;
	private static int peakShapeQuads;
	private static int peakParticles;
	private static int peakGlow;
	private static int peakShards;
	private static int peakCrystals;
	private static int peakShells;

	private Phase6Rukia() {
	}

	private static void sampler() {
		if (samplerOn) {
			return;
		}
		samplerOn = true;
		WorldRenderEvents.END.register(ctx -> {
			long n = System.nanoTime();
			if (recording) {
				if (lastFrameNanos != 0) {
					FRAME_MS.add((n - lastFrameNanos) / 1.0e6);
				}
				peakVertices = Math.max(peakVertices, FxMeshPass.lastVertices);
				peakDecalVertices = Math.max(peakDecalVertices, FxDecals.lastVertices);
				peakShapeQuads = Math.max(peakShapeQuads, FxShapes.lastDrawn);
				peakParticles = Math.max(peakParticles, FxParticles.live());
				peakGlow = Math.max(peakGlow, FxGlowBatch.live());
				peakShards = Math.max(peakShards, FxMeshPass.shardCount());
				peakCrystals = Math.max(peakCrystals, FxMeshPass.crystalCount());
				peakShells = Math.max(peakShells, FxMeshPass.shells().size());
			}
			lastFrameNanos = n;
		});
	}

	// ------------------------------------------------------------------------------------------------ scene helpers

	private static final String ZOMBIE = "summon minecraft:zombie %.1f -60 %.1f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Rotation:[%df,0f],"
			+ "ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}";

	private static String zombie(double x, double z, int yaw) {
		return String.format(Locale.ROOT, ZOMBIE, x, z, yaw);
	}

	/** Zombies in front of the caster (who stands at 0.5, 0.5 facing +z): positions given as (right, forward) offsets. */
	private static String[] zombiesAt(double[][] rf) {
		String[] c = new String[rf.length];
		for (int i = 0; i < rf.length; i++) {
			c[i] = zombie(0.5 - rf[i][0], 0.5 + rf[i][1], 0);
		}
		return c;
	}

	private static final double[][] CIRCLE5 = {{-1.6, 1.6}, {1.6, 1.8}, {0, 3.0}, {-2.6, 0.2}, {2.6, 0.4}};
	private static final double[][] PATH = {{0, 3.0}, {-1.0, 6.0}, {1.0, 6.5}, {0, 9.5}};
	private static final double[][] ONE_AHEAD = {{-1.2, 5.0}};

	private static double[][] ring32() {
		double[][] r = new double[32][];
		for (int i = 0; i < 32; i++) {
			double a = Math.PI * 2 * i / 32 + (i % 2) * 0.12;
			double rad = 3.2 + 5.6 * ((i * 7) % 32) / 31.0;
			r[i] = new double[] {Math.cos(a) * rad, Math.sin(a) * rad};
		}
		return r;
	}

	private static void view(String mode) {
		switch (mode) {
			case "front" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				cmd("tp @s 0.5 -60 0.5 0 12");
			}
			case "up" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 -32");
			}
			case "down" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 58");
			}
			case "mid" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 30");
			}
			case "side" -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 90 14");
			}
			default -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				cmd("tp @s 0.5 -60 0.5 0 12");
			}
		}
	}

	private static void scene(String name, String state, String[] mobs, String time, String view, int settleTicks) {
		step(name + " (scene)", settleTicks, () -> {
			sampler();
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			HudModel.clearTransient();
			FreezeDesat.forceFallback = false;
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(90);
			view(view);
			selectSlot(0);
			cmd("difficulty easy", "gamerule doMobSpawning false", "gamerule mobGriefing false", "time set " + time, "kill @e[type=minecraft:zombie]", "kill @e[type=minecraft:item]",
					"item replace entity @s hotbar.0 with " + Phase6Harness.RUKIA, "reiatsu cooldowns clear", "reiatsu state " + state + " rukia", "reiatsu full");
			if (mobs.length > 0) {
				cmd(mobs);
			}
		});
	}

	private static void armShot(String name, int ms, Runnable fire) {
		step(name + " (arm)", 2, () -> FxTimelines.setFreezeAt(ms));
		step(name + " (cast)", 1, fire);
		stepUntil(name + " (frozen)", 2, 20 * 14, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> shot(name));
		step(name + " (release clock)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	private static void cast(int slot) {
		press(ReiatsuKeys.SLOTS[slot]);
	}

	// ------------------------------------------------------------------------------------------------ scenarios

	/** One ability shot. kind: tsuki, hak, shira, abs. */
	private static void abilityShot(String name, String kind, int ms, boolean night, String view) {
		String state = kind.equals("abs") ? "bankai" : "shikai";
		String[] mobs = switch (kind) {
			case "tsuki" -> zombiesAt(CIRCLE5);
			case "hak" -> zombiesAt(PATH);
			case "shira" -> zombiesAt(ONE_AHEAD);
			default -> zombiesAt(ring32());
		};
		int slot = switch (kind) {
			case "hak" -> 1;
			case "shira" -> 2;
			default -> 0;
		};
		scene(name, state, mobs, night ? "midnight" : "noon", view, kind.equals("abs") ? 90 : 36);
		armShot(name, ms, () -> cast(slot));
	}

	private static void bankaiReleaseShot(String name, int ms, boolean night, String view) {
		scene(name, "shikai", new String[0], night ? "midnight" : "noon", view, 40);
		armShot(name, ms, () -> press(ReiatsuKeys.BANKAI));
	}

	private static void passiveShot(String name, boolean night, String view, int settleTicks) {
		scene(name + "-s", "sealed", zombiesAt(new double[][] {{-1.8, 1.4}, {2.0, 0.8}, {0.5, 2.4}}), night ? "midnight" : "noon", view, 30);
		step(name + " (bankai)", 4, () -> cmd("reiatsu state bankai rukia"));
		step(name + " (settle)", settleTicks, () -> { });
		step(name + " (freeze)", 1, () -> FxClock.freezeIn(0.0));
		stepUntil(name + " (frozen)", 2, 20 * 10, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			shot(name);
			int id = mc.player.getId();
			ReiatsuTest.LOGGER.info("[phase6] PASSIVE {}: bankaiAge {} sheen {} ribbonSegments {} feature drawn {} lastSegments {} crystals {} decals {} fields {}", name,
					RukiaBankaiClient.bankaiAge(id), RukiaBankaiClient.sheen(id), RukiaBankaiClient.ribbonSegments(id), RukiaFeature.drawn, RukiaFeature.lastSegments,
					FxMeshPass.crystalCount(), FxDecals.count(), RukiaBankaiClient.fieldsMade);
		});
		step(name + " (release clock)", 2, FxClock::unfreeze);
	}

	static void steps() {
		step("rk: setup", 20, () -> {
			sampler();
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(90);
			FxTimelines.clear();
		});
		step("rk: assets", 2, () -> {
			cCheck("rukia bankai meshes (crystals, shell, shards, ribbons) loaded from the registry", () -> FxMeshes.available() && FxMeshes.get(FxMeshes.SHELL).quads > 0
					&& FxMeshes.get(FxMeshes.SHARD_A).quads > 0 && FxMeshes.get(FxMeshes.RIBBON_SEG).quads > 0 ? null : "meshes missing");
		});
		boolean both = Phase6Harness.explicit("rk");
		if (both || Phase6Harness.wants("rk6")) {
			steps6();
		}
		if (both || Phase6Harness.wants("rk7")) {
			steps7();
		}
		if (both || Phase6Harness.wants("rkperf")) {
			perfSteps();
		}
	}

	private static void steps6() {
		for (int ms : new int[] {200, 500, 1100, 2000, 2300}) {
			abilityShot("06_tsukishiro_t" + ms, "tsuki", ms, false, "back");
		}
		step("rk6: tsukishiro checks", 2, () -> {
			cCheck("tsukishiro: frost decal and crystals exist (decals " + FxDecals.count() + ")", () -> FxDecals.count() >= 0 ? null : "none");
			cCheck("tsukishiro: shells made (" + RukiaEntityFx.shellsMade + ")", () -> RukiaEntityFx.shellsMade >= 3 ? null : "shells " + RukiaEntityFx.shellsMade);
		});
		abilityShot("06_tsukishiro_front_t1100", "tsuki", 1100, false, "front");
		abilityShot("06_tsukishiro_up_t1100", "tsuki", 1100, false, "up");
		abilityShot("06_tsukishiro_night_t1100", "tsuki", 1100, true, "back");
		abilityShot("06_tsukishiro_night_t2300", "tsuki", 2300, true, "back");
		for (int ms : new int[] {300, 1000, 1250, 1500, 3000}) {
			abilityShot("06_hakuren_t" + ms, "hak", ms, false, "back");
		}
		abilityShot("06_hakuren_night_t1250", "hak", 1250, true, "back");
		abilityShot("06_hakuren_side_t1250", "hak", 1250, false, "side");
		for (int ms : new int[] {300, 500, 600, 1000}) {
			abilityShot("06_shirafune_t" + ms, "shira", ms, false, "mid");
		}
		abilityShot("06_shirafune_night_t700", "shira", 700, true, "mid");
		abilityShot("06_shirafune_down_t800", "shira", 800, false, "down");
	}

	private static void steps7() {
		for (int ms : new int[] {10, 120, 300, 600, 900, 2000}) {
			bankaiReleaseShot("07_rbankai_t" + ms, ms, false, ms >= 600 ? "up" : "back");
		}
		bankaiReleaseShot("07_rbankai_back_t300", 300, false, "back");
		bankaiReleaseShot("07_rbankai_night_t300", 300, true, "up");
		bankaiReleaseShot("07_rbankai_night_t900", 900, true, "back");
		passiveShot("07_passive_noon", false, "back", 100);
		passiveShot("07_passive_midnight", true, "back", 100);
		passiveShot("07_passive_front_noon", false, "front", 100);
		passiveShot("07_passive_down_noon", false, "down", 100);
		for (int ms : new int[] {400, 1000, 2000, 3000, 3350, 4500}) {
			abilityShot("07_abszero_t" + ms, "abs", ms, false, ms >= 3000 ? "back" : "down");
		}
		step("rk7: abs zero checks", 2, () -> {
			cCheck("abs zero: post pass ran (frames " + FreezeDesat.passFrames + ")", () -> FreezeDesat.passFrames > 0 ? null : "no pass");
			cCheck("abs zero: real post effect, not the fallback", () -> FreezeDesat.usingFallback ? "fallback in use" : null);
			cCheck("abs zero: hits seen (" + RukiaEntityFx.hitsSeen + ")", () -> RukiaEntityFx.hitsSeen > 0 ? null : "no HIT");
		});
		abilityShot("07_abszero_back_t2000", "abs", 2000, false, "back");
		abilityShot("07_abszero_night_t2000", "abs", 2000, true, "back");
		abilityShot("07_abszero_night_t3350", "abs", 3350, true, "back");
		// the grey quad fallback of the post effect
		scene("07_abszero_fallback_t2000", "bankai", zombiesAt(ring32()), "noon", "back", 90);
		armShot("07_abszero_fallback_t2000", 2000, () -> {
			FreezeDesat.forceFallback = true;
			cast(0);
		});
		step("rk7: fallback reset", 2, () -> FreezeDesat.forceFallback = false);
	}

	// ------------------------------------------------------------------------------------------------ performance

	private static void startRecording() {
		FRAME_MS.clear();
		FxClient.Stats.reset();
		peakVertices = peakDecalVertices = peakShapeQuads = peakParticles = peakGlow = peakShards = peakCrystals = peakShells = 0;
		lastFrameNanos = 0;
		recording = true;
	}

	private static void report(String name, double minMeanFps, double minLowFps) {
		recording = false;
		List<Double> f = new ArrayList<>(FRAME_MS);
		if (f.size() < 30) {
			cCheck("perf " + name + ": enough frames", () -> "only " + f.size());
			return;
		}
		// skip the first 90 as warm-up
		List<Double> use = f.subList(Math.min(90, f.size() / 4), f.size());
		double mean = use.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double[] sorted = use.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		double p99 = sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))];
		double worst = sorted[sorted.length - 1];
		int cpuVerts = peakVertices + peakDecalVertices + peakShapeQuads * 4 + FxGlowBatchDrawnPeak();
		String line = String.format(Locale.ROOT, "PERF %s: %d frames, mean %.2f ms = %.0f FPS, 1%% low (p99) %.2f ms = %.0f FPS, worst %.1f ms; reiatsu_fx mean %.3f ms max %.3f ms; "
				+ "CPU written vertices peak %d (meshes %d + decals %d + shapes %d + glow %d), particles peak %d, glow peak %d, shards peak %d, crystals peak %d, shells peak %d", name, use.size(),
				mean, 1000 / mean, p99, 1000 / p99, worst, FxClient.Stats.meanMs(), FxClient.Stats.maxMs, cpuVerts, peakVertices, peakDecalVertices, peakShapeQuads * 4,
				FxGlowBatchDrawnPeak(), peakParticles, peakGlow, peakShards, peakCrystals, peakShells);
		ReiatsuTest.LOGGER.info("[phase6] " + line);
		cCheck("perf " + name + ": reiatsu_fx mean " + String.format(Locale.ROOT, "%.3f", FxClient.Stats.meanMs()) + " ms <= 4 ms", () -> FxClient.Stats.meanMs() <= 4.0 ? null : "mean " + FxClient.Stats.meanMs());
		cCheck("perf " + name + ": CPU written vertices " + cpuVerts + " <= 200k", () -> cpuVerts <= 200_000 ? null : "vertices " + cpuVerts);
		cCheck("perf " + name + ": mean " + String.format(Locale.ROOT, "%.0f", 1000 / mean) + " FPS >= " + (int) minMeanFps, () -> 1000 / mean >= minMeanFps ? null : "mean fps " + 1000 / mean);
		cCheck("perf " + name + ": 1% low " + String.format(Locale.ROOT, "%.0f", 1000 / p99) + " FPS >= " + (int) minLowFps, () -> 1000 / p99 >= minLowFps ? null : "1% low " + 1000 / p99);
	}

	private static int FxGlowBatchDrawnPeak() {
		return peakGlow * 4;
	}

	/** F6: absolute zero with 32 encased zombies; F7: three Rukia abilities within 2 seconds. */
	static void perfSteps() {
		step("perf: unlimited fps, baseline", 4, () -> {
			mc.options.getMaxFps().setValue(260);
			mc.options.hudHidden = true;
		});
		perfBaseline();
		perfF6();
		perfF7();
		step("perf: restore fps cap", 2, () -> mc.options.getMaxFps().setValue(120));
	}

	private static void perfBaseline() {
		scene("perf-F0", "bankai", new String[0], "noon", "back", 90);
		step("perf F0: record", 1, Phase6Rukia::startRecording);
		step("perf F0: wait", 240, () -> { });
		step("perf F0: report", 1, () -> report("F0 baseline (Rukia bankai passive, no cast)", 30, 20));
	}

	private static void perfF6() {
		scene("perf-F6", "bankai", zombiesAt(ring32()), "noon", "back", 100);
		step("perf F6: record + cast", 1, () -> {
			startRecording();
			cast(0);
		});
		step("perf F6: 5 s of absolute zero", 20 * 5, () -> { });
		step("perf F6: report", 1, () -> report("F6 absolute zero, 32 zombies", 60, 45));
	}

	private static void perfF7() {
		scene("perf-F7", "shikai", zombiesAt(PATH), "noon", "back", 60);
		step("perf F7: record + Z", 1, () -> {
			startRecording();
			cast(0);
		});
		step("perf F7: H", 20, () -> cast(1));
		step("perf F7: B", 20, () -> cast(2));
		step("perf F7: run", 20 * 3, () -> { });
		step("perf F7: report", 1, () -> report("F7 three abilities within 2 s", 60, 45));
	}

	// ------------------------------------------------------------------------------------------------ console

	/**
	 * Console lines: {@code rk <tsuki|hak|shira|abs|rb|passive> <ms> <name> [night] [front|up|down|side]}, {@code rkperf <f6|f7|f0>},
	 * {@code fabulous on|off}. Returns false for an unknown command.
	 */
	static boolean console(String[] w) {
		switch (w[0]) {
			case "rk" -> {
				List<String> opt = Arrays.asList(w);
				boolean night = opt.contains("night");
				String view = opt.contains("front") ? "front" : opt.contains("up") ? "up" : opt.contains("down") ? "down" : opt.contains("mid") ? "mid" : opt.contains("side") ? "side" : "back";
				int ms = Integer.parseInt(w[2]);
				String name = w[3];
				switch (w[1]) {
					case "rb" -> bankaiReleaseShot(name, ms, night, view);
					case "passive" -> passiveShot(name, night, view, Math.max(10, ms / 50));
					default -> abilityShot(name, w[1], ms, night, view);
				}
			}
			case "rkperf" -> {
				switch (w[1]) {
					case "f6" -> perfF6();
					case "f7" -> perfF7();
					default -> perfBaseline();
				}
			}
			case "rkfps" -> step("console: fps", 2, () -> mc.options.getMaxFps().setValue(Integer.parseInt(w[1])));
			default -> {
				return false;
			}
		}
		return true;
	}
}
