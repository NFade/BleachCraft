package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cCheck;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.press;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.selectSlot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shotAt;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.hud.HudModel;
import dev.minebleach.reiatsutest.client.hud.ReiatsuHud;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.client.net.ClientNet;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.voice.VoiceHudState;
import net.minecraft.client.option.Perspective;

/** Phase 6 step 1 scenarios: the HUD at the five GUI layouts, all states, animations at exact times, feedback, voice. */
final class Phase6Hud {
	private record Layout(String name, int w, int h, int gui) {
	}

	private static final Layout[] LAYOUTS = {
			new Layout("320x240", 1280, 960, 4), new Layout("427x240", 1280, 720, 3), new Layout("480x270", 1920, 1080, 4),
			new Layout("640x360", 1280, 720, 2), new Layout("960x540", 1920, 1080, 2)};

	private Phase6Hud() {
	}

	private static void state(String state, String character, int slot, String item) {
		cmd("item replace entity @s hotbar." + slot + " with " + item, "reiatsu cooldowns clear");
		selectSlot(slot);
		cmd("reiatsu state " + state + " " + character);
	}

	private static void clearHud() {
		HudModel.COOLDOWN_OVERRIDE[AbilityId.TSUKISHIRO.ordinal()] = null;
		HudModel.COOLDOWN_OVERRIDE[AbilityId.HAKUREN.ordinal()] = null;
		HudModel.COOLDOWN_OVERRIDE[AbilityId.SHIRAFUNE.ordinal()] = null;
		FxClock.unfreeze();
		HudModel.clearTransient();
	}

	private static void layout(Layout l) {
		step("hud: window " + l.name(), 24, () -> {
			Phase6Harness.windowSize(l.w(), l.h(), l.gui());
		});
		step("hud: check window " + l.name(), 4, () -> {
			int sw = mc.getWindow().getScaledWidth();
			int sh = mc.getWindow().getScaledHeight();
			ReiatsuTest.LOGGER.info("[phase6] layout {}: framebuffer {}x{} scaled {}x{} gui {}", l.name(), mc.getWindow().getFramebufferWidth(),
					mc.getWindow().getFramebufferHeight(), sw, sh, mc.options.getGuiScale().getValue());
			cCheck("layout " + l.name() + " scaled size " + sw + "x" + sh, () -> (sw + "x" + sh).equals(l.name()) ? null : "got " + sw + "x" + sh);
		});
	}

	static void steps(boolean layouts, boolean details) {
		step("hud: setup survival, noon, plate visible", 30, () -> {
			cmd("gamemode survival @s", "time set noon", "effect give @s minecraft:saturation infinite 0 true");
			mc.options.hudHidden = false;
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			ScreenFx.clear();
			clearHud();
			HudModel.resetAll();
		});
		for (Layout l : layouts ? LAYOUTS : new Layout[0]) {
			layout(l);
			for (String ch : new String[] {"rukia", "byakuya"}) {
				String item = ch.equals("rukia") ? Phase6Harness.RUKIA : Phase6Harness.BYAKUYA;
				int slot = ch.equals("rukia") ? 0 : 1;
				step("hud: " + l.name() + " " + ch + " sealed", 30, () -> {
					state("sealed", ch, slot, item);
					cmd("reiatsu set 78.8");
				});
				step("hud: shot sealed", 3, () -> shot("01_hud_" + l.name() + "_" + ch + "_sealed"));
				step("hud: " + l.name() + " " + ch + " shikai", 50, () -> state("shikai", ch, slot, item));
				step("hud: shot shikai", 3, () -> shot("01_hud_" + l.name() + "_" + ch + "_shikai"));
				step("hud: " + l.name() + " " + ch + " bankai", 70, () -> state("bankai", ch, slot, item));
				step("hud: shot bankai", 3, () -> shot("01_hud_" + l.name() + "_" + ch + "_bankai"));
			}
			step("hud: back to sealed", 20, () -> cmd("reiatsu state sealed"));
		}
		if (details) {
			detailSteps();
		}
		step("hud: restore window", 20, () -> {
			Phase6Harness.windowSize(1280, 720, 3);
			clearHud();
		});
	}

	private static void detailSteps() {
		Layout big = LAYOUTS[2]; // 480x270 at GUI scale 4: the details are readable
		step("hud detail: window " + big.name(), 24, () -> Phase6Harness.windowSize(big.w(), big.h(), big.gui()));
		step("hud detail: rukia shikai", 60, () -> {
			state("shikai", "rukia", 0, Phase6Harness.RUKIA);
			cmd("reiatsu set 78.8");
			clearHud();
		});
		for (int pct : new int[] {25, 50, 75}) {
			step("hud detail: cooldown " + pct, 6, () -> {
				int total = 240;
				int rem = total * pct / 100;
				HudModel.COOLDOWN_OVERRIDE[AbilityId.TSUKISHIRO.ordinal()] = new int[] {rem, total};
				HudModel.COOLDOWN_OVERRIDE[AbilityId.HAKUREN.ordinal()] = new int[] {rem * 2 / 3, 160};
				HudModel.COOLDOWN_OVERRIDE[AbilityId.SHIRAFUNE.ordinal()] = new int[] {rem / 2, 100};
			});
			step("hud detail: shot cooldown " + pct, 3, () -> shot("01_cooldown_" + pct));
		}
		for (int ms : new int[] {50, 150, 250}) {
			step("hud detail: ping arm " + ms, 8, () -> {
				clearHud();
				HudModel.COOLDOWN_OVERRIDE[AbilityId.TSUKISHIRO.ordinal()] = new int[] {1, 240};
			});
			shotAt("01_ping_t" + ms, ms / 1000.0, () -> HudModel.COOLDOWN_OVERRIDE[AbilityId.TSUKISHIRO.ordinal()] = null);
		}
		step("hud detail: poor (0.5 reiatsu)", 20, () -> {
			clearHud();
			cmd("reiatsu set 0.5");
		});
		step("hud detail: shot poor", 3, () -> shot("01_poor"));
		step("hud detail: almost full", 10, () -> cmd("reiatsu set 99"));
		shotAt("01_gem_spark", 0.12, () -> cmd("reiatsu full"));
		step("hud detail: gem ready idle", 20, () -> { });
		step("hud detail: shot gem ready", 3, () -> shot("01_gem_ready"));
		shotAt("01_chip_t100", 0.10, () -> cmd("reiatsu set 40"));
		step("hud detail: back to full", 10, () -> cmd("reiatsu set 90"));
		shotAt("01_chip_t500", 0.55, () -> cmd("reiatsu set 40"));

		step("hud detail: reiatsu 40 for the denied bankai", 10, () -> {
			cmd("reiatsu set 40");
			clearHud();
		});
		shotAt("01_denied_reiatsu", 0.20, () -> press(ReiatsuKeys.BANKAI));
		step("hud detail: results", 2, () -> cCheck("denied reiatsu: a result arrived", () -> ClientNet.RESULTS.isEmpty() ? "no result" : null));
		step("hud detail: cooldown denied: cast Z", 20, () -> cmd("reiatsu set 100", "reiatsu cooldowns clear"));
		step("hud detail: Z", 16, () -> press(ReiatsuKeys.SLOTS[0]));
		shotAt("01_denied_cooldown", 0.12, () -> press(ReiatsuKeys.SLOTS[0]));
		step("hud detail: wrong state: base", 20, () -> cmd("reiatsu state base rukia"));
		shotAt("01_denied_state", 0.25, () -> press(ReiatsuKeys.SLOTS[0]));
		shotAt("01_denied_item", 0.15, () -> HudModel.onResult(ResultCode.DENIED_ITEM, null));

		// transitions, lock ring, bankai end, boss bar offset
		step("hud trans: sealed", 40, () -> {
			HudModel.clearTransient();
			state("sealed", "rukia", 0, Phase6Harness.RUKIA);
			cmd("reiatsu set 100");
		});
		shotAt("01_flip_t150", 0.15, () -> cmd("reiatsu state shikai rukia"));
		step("hud trans: shikai settle", 40, () -> { });
		shotAt("01_bankai_in_t150", 0.15, () -> cmd("reiatsu state bankai rukia"));
		step("hud trans: bankai settle", 40, () -> { });
		shotAt("01_bankai_end_t100", 0.10, () -> {
			HudModel.onEffectEvent(11, true, CharacterId.RUKIA);
			cmd("reiatsu state sealed");
		});
		step("hud trans: after end", 4, () -> { });
		shotAt("01_bankai_end_t1000", 1.0, () -> { });
		step("hud trans: shikai again", 40, () -> {
			state("shikai", "rukia", 0, Phase6Harness.RUKIA);
		});
		shotAt("01_seal_lock_t500", 0.5, () -> press(ReiatsuKeys.SEAL));
		step("hud trans: bossbar", 30, () -> {
			cmd("bossbar add reiatsu_test:probe \"Probe\"", "bossbar set reiatsu_test:probe players @s", "bossbar set reiatsu_test:probe visible true");
		});
		step("hud trans: bossbar shot", 20, () -> shot("01_bossbar"));
		step("hud trans: bossbar remove", 10, () -> cmd("bossbar remove reiatsu_test:probe"));

		step("hud title: shikai (rukia) state", 40, () -> {
			HudModel.clearTransient();
			state("shikai", "rukia", 0, Phase6Harness.RUKIA);
			cmd("reiatsu set 80");
		});
		for (int ms : new int[] {150, 600, 1500}) {
			shotAt("01_title_rukia_shikai_t" + ms, ms / 1000.0, () -> {
				HudModel.clearTransient();
				HudModel.onEffectEvent(1, true, CharacterId.RUKIA);
			});
		}
		for (int ms : new int[] {300, 800, 1600, 2400}) {
			shotAt("01_title_rukia_bankai_t" + ms, ms / 1000.0, () -> {
				HudModel.clearTransient();
				HudModel.onEffectEvent(2, true, CharacterId.RUKIA);
			});
		}
		step("hud title: byakuya state", 40, () -> {
			HudModel.clearTransient();
			state("shikai", "byakuya", 1, Phase6Harness.BYAKUYA);
		});
		for (int ms : new int[] {150, 600, 1500}) {
			shotAt("01_title_byakuya_shikai_t" + ms, ms / 1000.0, () -> {
				HudModel.clearTransient();
				HudModel.onEffectEvent(3, true, CharacterId.BYAKUYA);
			});
		}
		for (int ms : new int[] {300, 1600}) {
			shotAt("01_title_byakuya_bankai_t" + ms, ms / 1000.0, () -> {
				HudModel.clearTransient();
				HudModel.onEffectEvent(4, true, CharacterId.BYAKUYA);
			});
		}
		shotAt("01_technique_toast", 0.5, () -> {
			HudModel.clearTransient();
			HudModel.onEffectEvent(33, true, CharacterId.BYAKUYA);
		});
		step("hud voice: wait", 70, () -> HudModel.clearTransient());
		shotAt("01_voice_interim", 0.2, () -> {
			long now = System.currentTimeMillis();
			VoiceHudState.set(new VoiceHudState(VoiceHudState.Mic.HEARING, now, "chire senbon", "", null, VoiceHudState.Result.NONE, now, 11));
		});
		shotAt("01_voice_accepted", 0.3, () -> {
			long now = System.currentTimeMillis();
			VoiceHudState.set(new VoiceHudState(VoiceHudState.Mic.LISTENING, now, "", "chire senbonzakura", "byakuya.shikai.release",
					VoiceHudState.Result.ACCEPTED, now, 12));
		});
		shotAt("01_voice_denied", 0.3, () -> {
			long now = System.currentTimeMillis();
			VoiceHudState.set(new VoiceHudState(VoiceHudState.Mic.LISTENING, now, "", "bankai senbonzakura kageyoshi", "byakuya.bankai.release",
					VoiceHudState.Result.DENIED_REIATSU, now, 13));
		});
		shotAt("01_voice_nomatch", 0.3, () -> {
			long now = System.currentTimeMillis();
			VoiceHudState.set(new VoiceHudState(VoiceHudState.Mic.LISTENING, now, "", "pass me the salt", null, VoiceHudState.Result.NO_MATCH, now, 14));
		});
		step("hud voice: cleanup", 10, () -> {
			clearHud();
			VoiceHudState.reset();
			cmd("reiatsu state sealed");
		});
		perfSteps();
	}

	static void setupPerf() {
		step("hud perf: survival, window", 30, () -> {
			cmd("gamemode survival @s", "time set noon");
			Phase6Harness.windowSize(1920, 1080, 4);
		});
		perfSteps();
	}

	static void perfSteps() {
		step("hud perf: bankai with all animations", 4, () -> state("bankai", "rukia", 0, Phase6Harness.RUKIA));
		stepUntil("hud perf: wait", 5, 20 * 30, ReiatsuHud::resetStats, () -> ReiatsuHud.frames >= 400);
		step("hud perf: result", 3, () -> {
			double ms = ReiatsuHud.meanMs();
			ReiatsuTest.LOGGER.info("[phase6] PERF hud: mean {} ms over {} frames, draw calls {}; {}; sections plate {} strip {} overlay {} ms",
					String.format(java.util.Locale.ROOT, "%.3f", ms), ReiatsuHud.frames, ReiatsuHud.lastDrawCalls, ReiatsuHud.profile(),
					String.format(java.util.Locale.ROOT, "%.3f", ReiatsuHud.plateMs / ReiatsuHud.frames), String.format(java.util.Locale.ROOT, "%.3f", ReiatsuHud.stripMs / ReiatsuHud.frames),
					String.format(java.util.Locale.ROOT, "%.3f", ReiatsuHud.overlayMs / ReiatsuHud.frames));
			cCheck("HUD draw mean " + String.format(java.util.Locale.ROOT, "%.3f", ms) + " ms < 0.3 ms", () -> ms < 0.3 ? null : "mean " + ms);
		});
	}
}
