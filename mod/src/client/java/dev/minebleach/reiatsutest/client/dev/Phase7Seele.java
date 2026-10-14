package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.wants;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxDelayed;
import dev.minebleach.reiatsutest.client.fx.FxGlowBatch;
import dev.minebleach.reiatsutest.client.fx.FxMesh;
import dev.minebleach.reiatsutest.client.fx.FxShapes;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuFx;
import dev.minebleach.reiatsutest.client.fx.UryuSeeleFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.core.fx.SeeleFxMath;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.Locale;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

/**
 * Scenarios {@code uryu_seele} and {@code uryu_seele_live} of the Uryu harness (URYU_VFX_STORYBOARD 2.9, step U-FX 5), started from
 * {@link Phase7Uryu#steps()}: {@code gradlew runPhase7 -Phold=uryu_seele} (or {@code uryu_seele,uryu_seele_live}).
 *
 * <p>{@code uryu_seele} feeds the client with the same messages the server sends (the lunge effect event 74, then SLASH packets at 0.15, 0.40
 * and 0.65 s and an IMPACT packet of kind 99 per zombie), scheduled on the FX clock ({@link FxDelayed}) so that the screenshots at
 * {@code FxTimelines.setFreezeAt(ms)} are exact and do not depend on the server ticks. Screenshots {@code p7_05_seele_t<ms>.png} at 0, 150, 300,
 * 400, 650 and 850 ms (the acceptance row), plus the mid stroke moments 210, 460 and 710 ms, front view ({@code p7_05_seele_front_t*}), first
 * person ({@code p7_05_seele_fp_t*}) and night ({@code p7_05_seele_night_t*}). {@code uryu_seele_live} casts the real ability (state shikai, key
 * slot 2) and takes two free running shots ({@code p7_05_seele_live_a/b}) and checks that the client got 3 SLASH packets and the impacts.
 */
final class Phase7Seele {
	/** Zombies (x, z) in the cone ahead of the player at (0.5, 0.5) facing +z. */
	private static final double[][] TARGETS = {{0.5, 3.2}, {-0.9, 2.8}, {1.9, 3.0}};
	private static final String ZOMBIE = "summon minecraft:zombie %.1f -60 %.1f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],"
			+ "Health:200f,Attributes:[{id:\"minecraft:generic.max_health\",base:200}],ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}";
	private static final int[] BACK_MS = {0, 150, 210, 300, 400, 460, 650, 710, 850};
	private static final int[] MID_MS = {210, 300, 460, 710};

	private Phase7Seele() {
	}

	static void steps() {
		if (wants("uryu_seele")) {
			emulated();
		}
		if (wants("uryu_seele_live")) {
			live();
		}
	}

	// ============================================================================================================ scene and shots

	private static void scene(String name, String view, boolean night, boolean targets) {
		step("seele " + name + ": scene", 44, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			UryuSeeleFx.clear();
			FxShapes.clear();
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(90);
			Phase6Harness.graphics(GraphicsMode.FANCY);
			switch (view) {
				case "fp" -> {
					mc.options.setPerspective(Perspective.FIRST_PERSON);
					cmd("tp @s 0.5 -60 0.5 0 6");
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
			cmd("difficulty easy", "gamerule doMobSpawning false", "gamerule mobGriefing false", "time set " + (night ? "midnight" : "noon"), "kill @e[type=minecraft:zombie]",
					"kill @e[type=minecraft:item]");
			if (targets) {
				String[] z = new String[TARGETS.length];
				for (int i = 0; i < TARGETS.length; i++) {
					z[i] = String.format(Locale.ROOT, ZOMBIE, TARGETS[i][0], TARGETS[i][1]);
				}
				cmd(z);
			}
		});
	}

	private static void shot(String name, int ms, boolean targets) {
		step(name + " (arm)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.clear();
			UryuSeeleFx.clear();
			UryuSeeleFx.resetStats();
			FxTimelines.setFreezeAt(ms);
		});
		step(name + " (cast)", 1, () -> fire(targets));
		stepUntil(name + " (frozen)", 2, 20 * 14, () -> { }, () -> FxClock.frozen);
		step(name + " (shot)", 3, () -> {
			Phase7Uryu.shot7(name);
			ReiatsuTest.LOGGER.info("[phase7] SHOT {} t {} ms: combos {} blades drawn {} blade {} slashes {} hits {} glow {} shapes {} segments {}", name, ms, UryuSeeleFx.liveCombos(),
					UryuSeeleFx.bladesDrawn, String.format(Locale.ROOT, "%.2f", UryuSeeleFx.bladeLength()), UryuSeeleFx.slashesSeen, UryuSeeleFx.hitsSeen, FxGlowBatch.live(),
					FxShapes.lastDrawn, UryuSeeleFx.lastSegments);
			// what must be on the screen at the frozen moment (the storyboard rows)
			int expectSlashes = ms >= 650 ? 3 : ms >= 400 ? 2 : ms >= 150 ? 1 : 0;
			cCheck(name + ": " + expectSlashes + " stroke(s) played at " + ms + " ms (saw " + UryuSeeleFx.slashesSeen + ")", () -> UryuSeeleFx.slashesSeen == expectSlashes
					|| (ms == 150 || ms == 400 || ms == 650) && UryuSeeleFx.slashesSeen == expectSlashes - 1 ? null : "slashes " + UryuSeeleFx.slashesSeen);
			if (ms >= 150 && ms < 850) {
				cCheck(name + ": the blade is out and drawn", () -> UryuSeeleFx.bladesDrawn >= 1 && UryuSeeleFx.bladeLength() > 0.9 ? null : "drawn " + UryuSeeleFx.bladesDrawn + " len " + UryuSeeleFx.bladeLength());
			}
			if (ms == 300 || ms == 210 || ms == 460 || ms == 710) {
				cCheck(name + ": glow sprites and strips are live", () -> FxGlowBatch.lastDrawn > 20 ? null : "glow drawn " + FxGlowBatch.lastDrawn);
			}
			if (targets && ms >= 300) {
				cCheck(name + ": the hits were routed to the combo (" + UryuSeeleFx.hitsSeen + ")", () -> UryuSeeleFx.hitsSeen >= TARGETS.length ? null : "hits " + UryuSeeleFx.hitsSeen);
			}
		});
		step(name + " (release)", 2, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
		});
	}

	/**
	 * The messages of the server for one combo, on the FX clock: effect event 74 now, SLASH at 0.15 / 0.40 / 0.65 s (the eye and look at that
	 * moment, 3.5 reach, 100 degrees, hits) and one IMPACT (type entity, kind 99) per zombie in the cone right after it.
	 */
	private static void fire(boolean targets) {
		Vec3d feet = mc.player.getPos();
		Vec3d look = mc.player.getRotationVec(1.0F);
		int tick = (int) (mc.world.getTime() & 0x7FFFFFFF);
		EffectEventS2C e = new EffectEventS2C(74, mc.player.getId(), 7400, feet.x, feet.y, feet.z, (float) look.x, (float) look.y, (float) look.z, -1, tick,
				new float[] {(float) feet.x, (float) feet.y, (float) feet.z, (float) UryuBalance.SEELE_LUNGE, (float) UryuBalance.SEELE_REACH});
		UryuFx.play(mc, e);
		for (int i = 0; i < UryuBalance.SEELE_SLASHES; i++) {
			final int idx = i;
			// a hair before the exact time, so that a freeze at exactly 150 / 400 / 650 ms already shows the first frame of the stroke
			FxDelayed.after(SeeleFxMath.slashTime(i) - 0.0005, () -> slashPackets(idx, targets, tick));
		}
	}

	private static void slashPackets(int idx, boolean targets, int tick) {
		Vec3d eye = mc.player.getEyePos();
		Vec3d look = mc.player.getRotationVec(1.0F);
		int hits = targets ? TARGETS.length : 0;
		float[] d = {(float) eye.x, (float) eye.y, (float) eye.z, (float) look.x, (float) look.y, (float) look.z, idx, (float) UryuBalance.SEELE_REACH,
				(float) UryuBalance.SEELE_CONE_DEG, hits};
		UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.SLASH, tick, new int[0], d));
		if (targets) {
			for (double[] t : TARGETS) {
				UryuFx.onArrows(new UryuArrowsS2C(UryuArrowsS2C.IMPACT, tick, new int[0],
						new float[] {(float) t[0], -60f + 0.975f, (float) t[1], UryuArrowsS2C.IMPACT_ENTITY, UryuArrowsS2C.KIND_SLASH}));
			}
		}
	}

	private static void emulated() {
		step("seele: setup", 20, () -> {
			mc.options.hudHidden = true;
			mc.options.getFov().setValue(90);
		});
		step("seele: assets", 2, () -> cCheck("uryu_seele_long loads from the uryu_fx set", () -> FxMesh.load("uryu_fx", "uryu_seele_long") != null
				&& FxMesh.load("uryu_fx", "uryu_seele_long").quads >= 8 ? null : "mesh missing"));
		scene("back", "back", false, true);
		for (int ms : BACK_MS) {
			shot("05_seele_t" + ms, ms, true);
		}
		scene("front", "front", false, false);
		for (int ms : MID_MS) {
			shot("05_seele_front_t" + ms, ms, false);
		}
		scene("fp", "fp", false, true);
		for (int ms : MID_MS) {
			shot("05_seele_fp_t" + ms, ms, true);
		}
		scene("night", "back", true, true);
		for (int ms : new int[] {300, 710}) {
			shot("05_seele_night_t" + ms, ms, true);
		}
		step("seele: cleanup", 4, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			UryuSeeleFx.clear();
			cmd("kill @e[type=minecraft:zombie]", "time set noon");
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
		});
	}

	// ============================================================================================================ the real ability

	private static void live() {
		scene("live", "back", false, true);
		step("seele live: state", 6, () -> cmd("item replace entity @s hotbar.0 with reiatsu_test:uryu_cross", "reiatsu cooldowns clear", "reiatsu state shikai uryu", "reiatsu full"));
		step("seele live: arm", 8, () -> {
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			UryuSeeleFx.clear();
			UryuSeeleFx.resetStats();
			Phase6Harness.selectSlot(0);
		});
		step("seele live: cast (key B)", 7, () -> Phase6Harness.press(ReiatsuKeys.SLOTS[2]));
		step("seele live: shot a", 4, () -> Phase7Uryu.shot7("05_seele_live_a"));
		step("seele live: shot b", 6, () -> Phase7Uryu.shot7("05_seele_live_b"));
		step("seele live: done", 30, () -> { });
		step("seele live: checks", 2, () -> {
			ReiatsuTest.LOGGER.info("[phase7] seele live: combos {} slashes {} hits {}", UryuSeeleFx.combosStarted, UryuSeeleFx.slashesSeen, UryuSeeleFx.hitsSeen);
			cCheck("seele live: the client got the lunge event and 3 slash packets (slashes " + UryuSeeleFx.slashesSeen + ", combos " + UryuSeeleFx.combosStarted + ")",
					() -> UryuSeeleFx.combosStarted >= 1 && UryuSeeleFx.slashesSeen == 3 ? null : "combos " + UryuSeeleFx.combosStarted + " slashes " + UryuSeeleFx.slashesSeen);
			cCheck("seele live: the impact packets of the strokes reached the combo (" + UryuSeeleFx.hitsSeen + ")", () -> UryuSeeleFx.hitsSeen >= 3 ? null : "hits " + UryuSeeleFx.hitsSeen);
		});
		step("seele live: cleanup", 4, () -> {
			cmd("kill @e[type=minecraft:zombie]", "reiatsu state sealed uryu", "time set noon");
			mc.options.hudHidden = false;
			mc.options.setPerspective(Perspective.FIRST_PERSON);
		});
	}
}
