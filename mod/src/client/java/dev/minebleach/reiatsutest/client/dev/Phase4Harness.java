package dev.minebleach.reiatsutest.client.dev;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxSound;
import dev.minebleach.reiatsutest.client.fx.ShunpoFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.client.net.ClientNet;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ShikaiMode;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import dev.minebleach.reiatsutest.registry.data.ReiatsuData;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import dev.minebleach.reiatsutest.server.TempBlocks;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Dev-only verification harness for phase 4. Enabled ONLY by {@code -Dreiatsu.phase4=true} (Gradle task
 * {@code runPhase4}). Creates a flat creative world, drives the real key bindings and server logic, asserts server and
 * client state, takes screenshots into {@code run/screenshots} and stops the client itself.
 */
public final class Phase4Harness {
	private static final String WORLD = "reiatsu_phase4";
	private static final String P = "[phase4] ";
	private static final String RUKIA = "reiatsu_test:sode_no_shirayuki";
	private static final String BYAKUYA = "reiatsu_test:senbonzakura";

	private static MinecraftClient mc;
	private static final List<Step> STEPS = new ArrayList<>();
	private static int stepIndex = -1;
	private static int ticksInStep;
	private static boolean stepStarted;
	private static long totalTicks;
	private static final List<String> FAILS = new CopyOnWriteArrayList<>();
	private static int passes;

	private record Step(String name, int minTicks, int timeout, Runnable action, BooleanSupplier until) {
	}

	private Phase4Harness() {
	}

	public static void init() {
		ReiatsuTest.LOGGER.info(P + "ENABLED (-Dreiatsu.phase4=true)");
		ClientTickEvents.END_CLIENT_TICK.register(Phase4Harness::tick);
		buildSteps();
	}

	// ---------------------------------------------------------------- engine

	private static void step(String name, int minTicks, Runnable action) {
		STEPS.add(new Step(name, minTicks, 0, action, null));
	}

	private static void stepUntil(String name, int minTicks, int timeout, Runnable action, BooleanSupplier until) {
		STEPS.add(new Step(name, minTicks, timeout, action, until));
	}

	private static void tick(MinecraftClient client) {
		mc = client;
		totalTicks++;
		if (totalTicks > 20 * 60 * 12) {
			ReiatsuTest.LOGGER.error(P + "global watchdog fired, stopping");
			finish();
			client.scheduleStop();
			return;
		}
		if (stepIndex < 0) {
			if (client.getOverlay() == null && client.currentScreen != null
					&& (client.currentScreen instanceof TitleScreen || totalTicks > 600)) {
				stepIndex = 0;
			}
			return;
		}
		if (stepIndex >= STEPS.size()) {
			return;
		}
		Step s = STEPS.get(stepIndex);
		if (!stepStarted) {
			ReiatsuTest.LOGGER.info(P + "step {}/{}: {}", stepIndex + 1, STEPS.size(), s.name());
			try {
				s.action().run();
			} catch (Throwable t) {
				ReiatsuTest.LOGGER.error(P + "step '{}' threw", s.name(), t);
				FAILS.add("step threw: " + s.name() + " " + t);
			}
			stepStarted = true;
			ticksInStep = 0;
			return;
		}
		ticksInStep++;
		boolean timeUp = ticksInStep >= s.minTicks();
		boolean cond = s.until() == null || safe(s.until());
		boolean timedOut = s.until() != null && s.timeout() > 0 && ticksInStep >= s.timeout();
		if (timedOut && !cond) {
			ReiatsuTest.LOGGER.error(P + "step '{}' timed out waiting for its condition", s.name());
			FAILS.add("timeout: " + s.name());
		}
		if ((timeUp && cond) || timedOut) {
			stepIndex++;
			stepStarted = false;
		}
	}

	private static boolean safe(BooleanSupplier b) {
		try {
			return b.getAsBoolean();
		} catch (Throwable t) {
			return false;
		}
	}

	// ---------------------------------------------------------------- helpers

	private static MinecraftServer server() {
		return mc.getServer();
	}

	private static void cmd(String... commands) {
		MinecraftServer srv = server();
		srv.execute(() -> {
			List<ServerPlayerEntity> players = srv.getPlayerManager().getPlayerList();
			if (players.isEmpty()) {
				return;
			}
			ServerPlayerEntity p = players.get(0);
			ServerCommandSource src = srv.getCommandSource().withEntity(p).withPosition(p.getPos())
					.withRotation(p.getRotationClient()).withWorld(p.getServerWorld());
			for (String c : commands) {
				try {
					srv.getCommandManager().executeWithPrefix(src, c);
				} catch (Throwable t) {
					ReiatsuTest.LOGGER.error(P + "command '{}' failed", c, t);
				}
			}
		});
	}

	private static void shot(String name) {
		String file = "p4_" + name + ".png";
		ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), msg -> { });
		ReiatsuTest.LOGGER.info(P + "screenshot {}", file);
	}

	private static void selectSlot(int slot) {
		mc.player.getInventory().selectedSlot = slot;
		mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
	}

	private static void view(double x, double y, double z, float yaw, float pitch) {
		cmd(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void press(KeyBinding k) {
		ReiatsuKeys.press(k);
	}

	private static boolean worldReady() {
		return mc.world != null && mc.player != null && mc.currentScreen == null && mc.getServer() != null
				&& !server().getPlayerManager().getPlayerList().isEmpty();
	}

	private static void record(String name, String failDetail) {
		if (failDetail == null) {
			passes++;
			ReiatsuTest.LOGGER.info(P + "CHECK PASS {}", name);
		} else {
			FAILS.add(name + ": " + failDetail);
			ReiatsuTest.LOGGER.error(P + "CHECK FAIL {}: {}", name, failDetail);
		}
	}

	/** Client-thread check; returns null for pass. */
	private static void cCheck(String name, Supplier<String> fn) {
		String fail;
		try {
			fail = fn.get();
		} catch (Throwable t) {
			fail = t.toString();
		}
		record(name, fail);
	}

	/** Server-thread check (runs on the server thread, result is logged when it runs). */
	private static void sCheck(String name, Function<ServerPlayerEntity, String> fn) {
		MinecraftServer srv = server();
		srv.execute(() -> {
			String fail;
			try {
				fail = fn.apply(srv.getPlayerManager().getPlayerList().get(0));
			} catch (Throwable t) {
				fail = t.toString();
			}
			record(name, fail);
		});
	}

	private static StateMachine sm(ServerPlayerEntity p) {
		return ZanpakutoManager.machine(p);
	}

	private static String expectState(ServerPlayerEntity p, ZanpakutoState st, CharacterId c) {
		StateMachine m = sm(p);
		if (m.state() != st || m.character() != c) {
			return "server state " + m.state() + "/" + m.character() + " expected " + st + "/" + c;
		}
		return null;
	}

	private static String clientExpect(ZanpakutoState st, CharacterId c) {
		var z = ClientState.zanpakuto();
		if (z.zanpakutoState() != st || z.characterId() != c) {
			return "client attachment " + z.zanpakutoState() + "/" + z.characterId() + " expected " + st + "/" + c;
		}
		return null;
	}

	private static String clientStack(ReleaseState want) {
		ReleaseState have = mc.player.getMainHandStack().get(ModComponents.RELEASE_STATE);
		return have == want ? null : "main-hand component " + have + " expected " + want;
	}

	private static List<CowEntity> cows(ServerPlayerEntity p) {
		return p.getServerWorld().getEntitiesByClass(CowEntity.class, Box.of(p.getPos(), 80, 20, 80), e -> e.isAlive());
	}

	private static String lastResult() {
		synchronized (ClientNet.RESULTS) {
			return ClientNet.RESULTS.isEmpty() ? "(none)" : ClientNet.RESULTS.get(ClientNet.RESULTS.size() - 1);
		}
	}

	private static boolean lastResultIs(String code) {
		return lastResult().endsWith(":" + code);
	}

	private static void spawnCows(double... zs) {
		List<String> c = new ArrayList<>();
		c.add("kill @e[type=minecraft:cow]");
		double[] xs = {0.5, -1.0, 2.0, 0.5, 1.5};
		for (int i = 0; i < zs.length; i++) {
			c.add(String.format(java.util.Locale.ROOT, "summon minecraft:cow %.1f -60 %.1f {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}",
					xs[i % xs.length], zs[i]));
		}
		cmd(c.toArray(new String[0]));
	}

	/** Cows tagged "far" (no AI) at the given "x y z" positions, for the radius checks; kept in addition to the normal cows. */
	private static void spawnFar(String... positions) {
		List<String> c = new ArrayList<>();
		for (String pos : positions) {
			c.add("summon minecraft:cow " + pos + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"far\"]}");
		}
		cmd(c.toArray(new String[0]));
	}

	private static void finish() {
		ReiatsuTest.LOGGER.info(P + "SUMMARY passes={} fails={}", passes, FAILS.size());
		for (String f : FAILS) {
			ReiatsuTest.LOGGER.error(P + "FAILED {}", f);
		}
		ReiatsuTest.LOGGER.info(P + "RESULT {}", FAILS.isEmpty() ? "ALL PASS" : "FAILURES");
	}

	// ---------------------------------------------------------------- the script

	private static void buildSteps() {
		step("bootstrap world", 2, Phase4Harness::bootstrapWorld);
		stepUntil("wait for world", 40, 20 * 120, () -> { }, Phase4Harness::worldReady);
		step("setup", 30, () -> {
			cmd("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
					"gamerule doImmediateRespawn false", "time set noon", "weather clear", "difficulty easy", "gamemode creative @s",
					"item replace entity @s hotbar.0 with " + RUKIA,
					"item replace entity @s hotbar.1 with " + BYAKUYA,
					"tp @s 0.5 -60 0.5 0 20");
			ClientNet.RESULTS.clear();
		});
		step("select rukia, first person", 25, () -> {
			selectSlot(0);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			mc.inGameHud.getChatHud().clear(true);
		});

		// ---------------- A. attachments, HUD, release, sync
		step("A1 checks: attachments synced while sealed", 5, () -> {
			cCheck("A1 client sees reiatsu attachment (sync works)", () -> {
				ReiatsuData r = ClientState.reiatsu();
				return r != null && r.value() == 1000 ? null : "reiatsu attachment " + r;
			});
			cCheck("A1 client sees SEALED", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
			sCheck("A1 server SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
		});
		step("shot sealed HUD", 5, () -> shot("01_rukia_sealed_hud"));
		// B4 step 3: shikai is entered from the drawn base form only
		step("A1b press R with the sword in the scabbard", 12, () -> press(ReiatsuKeys.RELEASE));
		step("A1b checks: SEALED to SHIKAI is rejected with its own feedback", 3, () -> {
			cCheck("A1b R while sealed is DENIED_NOT_DRAWN", () -> lastResultIs("DENIED_NOT_DRAWN") ? null : "last result " + lastResult());
			sCheck("A1b server still SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			sCheck("A1b reiatsu untouched", p -> sm(p).reiatsu().value() == 1000 ? null : "reiatsu " + sm(p).reiatsu().value());
			cCheck("A1b feedback text is the draw-first message", () -> {
				String key = "message.reiatsu_test.denied.not_drawn";
				return net.minecraft.client.resource.language.I18n.hasTranslation(key) ? null : "no translation for " + key;
			});
			shot("01b_not_drawn_feedback");
		});
		step("A1c press J (draw)", 12, () -> press(ReiatsuKeys.DRAW));
		step("A1c checks: base form", 3, () -> {
			sCheck("A1c server BASE/RUKIA", p -> expectState(p, ZanpakutoState.BASE, CharacterId.RUKIA));
			cCheck("A1c client BASE/RUKIA (attachment sync)", () -> clientExpect(ZanpakutoState.BASE, CharacterId.RUKIA));
			cCheck("A1c held stack component BASE (render mirror)", () -> clientStack(ReleaseState.BASE));
			cCheck("A1c action_result OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			sCheck("A1c the draw cost nothing", p -> sm(p).reiatsu().value() == 1000 ? null : "reiatsu " + sm(p).reiatsu().value());
			shot("01c_rukia_base_hud");
		});
		step("A1d press J again (sheathe)", 12, () -> press(ReiatsuKeys.DRAW));
		step("A1d checks: back in the scabbard", 3, () -> {
			sCheck("A1d server SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("A1d client SEALED", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("A1d held stack SEALED", () -> clientStack(ReleaseState.SEALED));
		});
		step("A1e right click with the sword (draw)", 12, () -> {
			mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND);
		});
		step("A1e checks: right click draws", 3, () -> {
			sCheck("A1e server BASE/RUKIA after the right click", p -> expectState(p, ZanpakutoState.BASE, CharacterId.RUKIA));
			cCheck("A1e client BASE", () -> clientExpect(ZanpakutoState.BASE, CharacterId.RUKIA));
		});
		step("A1f press V (seal from the base form)", 12, () -> press(ReiatsuKeys.SEAL));
		step("A1f checks: seal from BASE", 3, () -> {
			sCheck("A1f server SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			sCheck("A1f no release lock after sheathing the base form", p -> sm(p).releaseLockEndTick() <= p.getServer().getTicks() ? null : "release lock still active");
		});
		step("A1g press J (draw again)", 12, () -> press(ReiatsuKeys.DRAW));
		step("A1g checks: base again", 3, () -> {
			sCheck("A1g server BASE", p -> expectState(p, ZanpakutoState.BASE, CharacterId.RUKIA));
			cCheck("A1g Z in the base form is refused locally (no abilities before shikai)", () -> {
				var z = ClientState.zanpakuto();
				return dev.minebleach.reiatsutest.core.state.AbilityId.forSlot(z.characterId(), z.zanpakutoState(), 0) == null ? null : "ability in BASE";
			});
		});
		step("press R (release)", 12, () -> press(ReiatsuKeys.RELEASE));
		step("A2 checks: shikai", 3, () -> {
			sCheck("A2 server SHIKAI/RUKIA", p -> expectState(p, ZanpakutoState.SHIKAI, CharacterId.RUKIA));
			sCheck("A2 reiatsu charged 15.0", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 850 && v <= 870 ? null : "reiatsu " + v;
			});
			cCheck("A2 client SHIKAI/RUKIA (attachment sync)", () -> clientExpect(ZanpakutoState.SHIKAI, CharacterId.RUKIA));
			cCheck("A2 held stack component SHIKAI (render mirror synced)", () -> clientStack(ReleaseState.SHIKAI));
			cCheck("A2 action_result OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			cCheck("A2 effect_event received", () -> ClientNet.EFFECT_EVENTS.get() >= 1 ? null : "no effect events");
		});
		step("shot shikai", 12, () -> { });
		step("shot shikai (now)", 4, () -> shot("02_rukia_shikai_hud"));

		// Tsukishiro
		step("spawn cows near, then Z", 30, () -> {
			spawnCows(3.0, 2.5, 2.0);
		});
		step("press Z (tsukishiro)", 24, () -> press(ReiatsuKeys.SLOTS[0]));
		step("shot freeze phase", 3, () -> {
			shot("03_tsukishiro_freeze");
			sCheck("A3 cows slowed after phase 0", p -> {
				for (CowEntity c : cows(p)) {
					if (!c.hasStatusEffect(StatusEffects.SLOWNESS)) {
						return "cow without slowness";
					}
				}
				return cows(p).size() == 3 ? null : "cows " + cows(p).size();
			});
		});
		step("wait for shatter", 26, () -> { });
		step("A3 checks: tsukishiro damage", 3, () -> {
			shot("04_tsukishiro_shatter");
			sCheck("A3 each cow lost >= 6 HP (shatter)", p -> {
				for (CowEntity c : cows(p)) {
					if (c.getHealth() > 4.01F) {
						return "cow health " + c.getHealth();
					}
				}
				return cows(p).size() == 3 ? null : "cows " + cows(p).size();
			});
			cCheck("A3 client cooldown icon running", () -> ClientState.cooldownRemaining(AbilityId.TSUKISHIRO) > 100
					? null : "cooldown " + ClientState.cooldownRemaining(AbilityId.TSUKISHIRO));
			cCheck("A3 action_result OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
		});
		step("press Z again (cooldown)", 12, () -> press(ReiatsuKeys.SLOTS[0]));
		step("A4 checks: denied by cooldown", 3, () -> {
			cCheck("A4 second Z is COOLDOWN", () -> lastResultIs("COOLDOWN") ? null : "last result " + lastResult());
			shot("05_cooldown_hud");
		});

		// Hakuren with frost layer
		step("fresh cows, look at ground", 30, () -> {
			spawnCows(4.0, 6.0, 8.0);
			view(0.5, -60, 0.5, 0, 30);
		});
		step("press H (hakuren)", 36, () -> press(ReiatsuKeys.SLOTS[1]));
		step("A5 checks: hakuren frost", 3, () -> {
			shot("06_hakuren_frost");
			sCheck("A5 temporary blocks placed", p -> TempBlocks.count(p.getUuid()) > 0 ? null : "no temp blocks");
			sCheck("A5 cows hurt 5 HP", p -> {
				int hit = 0;
				for (CowEntity c : cows(p)) {
					if (c.getHealth() <= 5.01F) {
						hit++;
					}
				}
				return hit == 3 ? null : "cows hit " + hit;
			});
		});
		step("wait frost rollback", 70, () -> { });
		step("A5 checks: rollback", 3, () -> {
			sCheck("A5 temp blocks rolled back after 3 s", p -> TempBlocks.size() == 0 ? null : "still " + TempBlocks.size());
			sCheck("A5 world restored (no snow at 0,-60,3)", p -> {
				var b = p.getServerWorld().getBlockState(new BlockPos(0, -60, 3));
				return b.isAir() ? null : "block " + b;
			});
			shot("07_hakuren_after_rollback");
		});

		// Shirafune
		step("cow ahead for shirafune", 25, () -> {
			spawnCows(5.0);
			view(0.5, -60, 0.5, 0, 6);
			cmd("reiatsu full");
		});
		step("press B (shirafune)", 30, () -> press(ReiatsuKeys.SLOTS[2]));
		step("A6 checks: shirafune", 3, () -> {
			sCheck("A6 cow lost >= 8 HP", p -> {
				List<CowEntity> c = cows(p);
				return c.size() == 1 && c.get(0).getHealth() <= 2.01F ? null : "cows " + c.size() + (c.isEmpty() ? "" : " health " + c.get(0).getHealth());
			});
		});

		// Denied by reiatsu
		step("drain reiatsu, press G", 3, () -> {
			cmd("reiatsu set 50");
		});
		step("press G (bankai, not full)", 8, () -> press(ReiatsuKeys.BANKAI));
		step("A7 checks: denied reiatsu", 3, () -> {
			cCheck("A7 bankai at 50.0 is DENIED_REIATSU", () -> lastResultIs("DENIED_REIATSU") ? null : "last result " + lastResult());
			shot("08_denied_reiatsu_flash");
		});

		// Bankai (Rukia)
		step("full reiatsu, wait", 14, () -> cmd("reiatsu full"));
		step("press G (bankai)", 12, () -> press(ReiatsuKeys.BANKAI));
		step("A8 checks: rukia bankai", 3, () -> {
			sCheck("A8 server BANKAI/RUKIA", p -> expectState(p, ZanpakutoState.BANKAI, CharacterId.RUKIA));
			sCheck("A8 bankai cost no reiatsu (bar still full)", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 990 ? null : "reiatsu " + v;
			});
			sCheck("A8 bankai timer 45 s armed (bankaiEndTick = now + 900)", p -> {
				long end = sm(p).snapshot().bankaiEndTick();
				long left = end - p.getServer().getTicks();
				return left > 850 && left <= 900 ? null : "ticks left " + left;
			});
			cCheck("A8 client BANKAI", () -> clientExpect(ZanpakutoState.BANKAI, CharacterId.RUKIA));
			cCheck("A8 held stack BANKAI", () -> clientStack(ReleaseState.BANKAI));
			shot("09_rukia_bankai_hud");
		});
		step("press Z inside settle lock", 6, () -> press(ReiatsuKeys.SLOTS[0]));
		step("A9 checks: settle lock", 3, () -> {
			cCheck("A9 Z during settle is COOLDOWN", () -> lastResultIs("COOLDOWN") ? null : "last result " + lastResult());
		});
		step("cows for absolute zero + hostile for passive", 50, () -> {
			spawnCows(3.0, 5.0, 7.0);
			cmd("summon minecraft:zombie 1.5 -60 1.5 {NoAI:1b,PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:leather_helmet\",count:1}]}");
			// B4 step 4 acceptance: a group more than 10 blocks from the caster (10.5 to 11.6 blocks, all around)
			spawnFar("11.0 -60 0.5", "-11.0 -60 1.0", "2.0 -60 -11.0", "-7.0 -60 -8.0");
			view(0.5, -60, 0.5, 0, 25);
		});
		step("A10 checks: bankai passive slows hostile mobs", 12, () -> {
			sCheck("A10 zombie slowed by the passive", p -> {
				for (var z : p.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.ZombieEntity.class, Box.of(p.getPos(), 20, 10, 20), e -> true)) {
					return z.hasStatusEffect(StatusEffects.SLOWNESS) ? null : "zombie without slowness";
				}
				return "no zombie";
			});
		});
		step("press U (absolute zero, bankai row)", 42, () -> press(ReiatsuKeys.BANKAI_SLOTS[0]));
		step("A11 checks: absolute zero ice", 3, () -> {
			shot("10_absolute_zero_ice");
			sCheck("A11 temp ice placed", p -> TempBlocks.count(p.getUuid()) > 0 ? null : "no temp blocks");
			sCheck("A11 temp ice within the TempBlocks per-player limit (128)", p -> TempBlocks.count(p.getUuid()) <= 128 ? null : "count " + TempBlocks.count(p.getUuid()));
			sCheck("A11 bankai ability cost no reiatsu", p -> sm(p).reiatsu().value() >= 990 ? null : "reiatsu " + sm(p).reiatsu().value());
			sCheck("A11 all 7 cows immobilised (Slowness VII)", p -> {
				for (CowEntity c : cows(p)) {
					var s = c.getStatusEffect(StatusEffects.SLOWNESS);
					if (s == null || s.getAmplifier() < 6) {
						return "cow slowness " + s;
					}
				}
				return cows(p).size() == 7 ? null : "cows " + cows(p).size();
			});
			sCheck("A11 ACCEPTANCE: the 4 cows 10+ blocks away are in the frozen group", p -> {
				int far = 0;
				for (CowEntity c : cows(p)) {
					if (c.getCommandTags().contains("far")) {
						double d = Math.hypot(c.getX() - p.getX(), c.getZ() - p.getZ());
						if (d < 10.0) {
							return "far cow only " + d + " blocks away";
						}
						if (c.hasStatusEffect(StatusEffects.SLOWNESS)) {
							far++;
						}
					}
				}
				return far == 4 ? null : "far cows frozen " + far;
			});
			sCheck("A11 sanity: the radius of the frozen group is 10 to 16 blocks", p -> {
				double max = 0;
				for (CowEntity c : cows(p)) {
					max = Math.max(max, Math.hypot(c.getX() - p.getX(), c.getZ() - p.getZ()));
				}
				return max >= 10.0 && max <= 16.0 ? null : "farthest cow " + max;
			});
		});
		step("wait shatter", 30, () -> { });
		step("A11 checks: shatter", 3, () -> {
			sCheck("A11 all cows dead after the 18 HP shatter (near and far)", p -> cows(p).isEmpty() ? null : "cows alive " + cows(p).size());
		});
		step("wait ice rollback", 110, () -> { });
		step("A11 checks: ice rolled back", 3, () -> {
			sCheck("A11 all temp ice gone", p -> TempBlocks.size() == 0 ? null : "still " + TempBlocks.size());
		});
		// B4 step 4: shikai abilities stay available inside bankai, with their normal cost and cooldown
		step("A11b cow near, full reiatsu", 20, () -> {
			spawnCows(2.5);
			cmd("reiatsu full");
		});
		step("A11b press Z (tsukishiro inside bankai)", 24, () -> press(ReiatsuKeys.SLOTS[0]));
		step("A11b checks", 3, () -> {
			cCheck("A11b action_result OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			sCheck("A11b still BANKAI", p -> expectState(p, ZanpakutoState.BANKAI, CharacterId.RUKIA));
			sCheck("A11b the shikai ability cost its 25.0 (bar 700..800)", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 700 && v <= 800 ? null : "reiatsu " + v;
			});
			sCheck("A11b cow slowed", p -> {
				for (CowEntity c : cows(p)) {
					return c.hasStatusEffect(StatusEffects.SLOWNESS) ? null : "cow not slowed";
				}
				return "no cow";
			});
			cCheck("A11b shikai cooldown running on the client", () -> ClientState.cooldownRemaining(AbilityId.TSUKISHIRO) > 100
					? null : "cooldown " + ClientState.cooldownRemaining(AbilityId.TSUKISHIRO));
			cCheck("A11b bankai ability cooldown running on the client", () -> ClientState.cooldownRemaining(AbilityId.ABSOLUTE_ZERO) > 100
					? null : "cooldown " + ClientState.cooldownRemaining(AbilityId.ABSOLUTE_ZERO));
			shot("10b_bankai_strip_both_rows");
		});
		step("A11c press U again (cooldown, no spam)", 12, () -> press(ReiatsuKeys.BANKAI_SLOTS[0]));
		step("A11c checks", 3, () -> {
			cCheck("A11c second absolute zero is COOLDOWN", () -> lastResultIs("COOLDOWN") ? null : "last result " + lastResult());
			sCheck("A11c nothing was spent", p -> sm(p).reiatsu().value() >= 700 ? null : "reiatsu " + sm(p).reiatsu().value());
		});
		step("press V (seal)", 8, () -> press(ReiatsuKeys.SEAL));
		step("A12 checks: sealed", 3, () -> {
			sCheck("A12 server SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("A12 client SEALED", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("A12 held stack SEALED", () -> clientStack(ReleaseState.SEALED));
		});
		step("press J during release lock", 8, () -> press(ReiatsuKeys.DRAW));
		step("A13 checks: release lock", 3, () -> {
			cCheck("A13 J during the 2 s release lock is COOLDOWN", () -> lastResultIs("COOLDOWN") ? null : "last result " + lastResult());
		});

		// ---------------- B. Byakuya
		step("cleanup, select byakuya", 60, () -> {
			cmd("kill @e[type=minecraft:zombie]", "reiatsu full");
			selectSlot(1);
			view(0.5, -60, 0.5, 0, 15);
		});
		step("B1 press J (byakuya draw)", 14, () -> press(ReiatsuKeys.DRAW));
		step("B1 base checks", 3, () -> {
			sCheck("B1 server BASE/BYAKUYA", p -> expectState(p, ZanpakutoState.BASE, CharacterId.BYAKUYA));
			cCheck("B1 held stack BASE", () -> clientStack(ReleaseState.BASE));
		});
		step("B1 press R (byakuya shikai)", 14, () -> press(ReiatsuKeys.RELEASE));
		step("B1 checks", 3, () -> {
			sCheck("B1 server SHIKAI/BYAKUYA", p -> expectState(p, ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
			cCheck("B1 client SHIKAI/BYAKUYA", () -> clientExpect(ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
			cCheck("B1 held stack SHIKAI", () -> clientStack(ReleaseState.SHIKAI));
			shot("11_byakuya_shikai_hud");
		});
		step("cow at the aimed point", 30, () -> spawnCows(6.5));
		step("press Z (mode attack)", 30, () -> press(ReiatsuKeys.SLOTS[0]));
		step("B2 checks: mode attack hits", 3, () -> {
			sCheck("B2 cow lost 8 HP in four 2 HP hits", p -> {
				List<CowEntity> c = cows(p);
				return c.size() == 1 && c.get(0).getHealth() <= 2.01F ? null : "cows " + c.size() + (c.isEmpty() ? "" : " health " + c.get(0).getHealth());
			});
		});
		step("survival, press H (barrier)", 40, () -> {
			cmd("gamemode survival @s", "reiatsu full");
		});
		step("press H", 8, () -> press(ReiatsuKeys.SLOTS[1]));
		step("B3 checks: barrier", 3, () -> {
			cCheck("B3 client sees shikaiMode BARRIER (attachment sync)", () ->
					ClientState.zanpakuto().mode() == ShikaiMode.BARRIER ? null : "mode " + ClientState.zanpakuto().mode());
			sCheck("B3 server mode BARRIER", p -> sm(p).shikaiMode() == ShikaiMode.BARRIER ? null : "mode " + sm(p).shikaiMode());
		});
		step("damage the player 10 HP", 6, () -> cmd("damage @s 10 minecraft:generic"));
		step("B3 checks: damage reduced", 6, () -> {
			sCheck("B3 player lost 2 HP (80% of 10 absorbed)", p -> {
				float h = p.getHealth();
				return Math.abs(h - 18.0F) < 0.2F ? null : "health " + h;
			});
			sCheck("B3 absorption pool 12.0 left", p -> sm(p).barrierPoolTenths() == 120 ? null : "pool " + sm(p).barrierPoolTenths());
		});
		step("creative again, full reiatsu, wait", 150, () -> cmd("gamemode creative @s", "reiatsu full", "reiatsu cooldowns clear"));
		step("press G (byakuya bankai)", 14, () -> press(ReiatsuKeys.BANKAI));
		step("B4 checks: byakuya bankai", 3, () -> {
			sCheck("B4 server BANKAI/BYAKUYA", p -> expectState(p, ZanpakutoState.BANKAI, CharacterId.BYAKUYA));
			cCheck("B4 client BANKAI", () -> clientExpect(ZanpakutoState.BANKAI, CharacterId.BYAKUYA));
			shot("12_byakuya_bankai_hand_empty");
		});
		stepUntil("B5 idle bankai until the 45 s timer ends", 10, 20 * 60, () -> { }, () -> ClientState.zanpakuto().zanpakutoState() == ZanpakutoState.SHIKAI);
		step("B5 checks: the timer ends bankai into shikai", 4, () -> {
			sCheck("B5 server SHIKAI/BYAKUYA after the timer (not SEALED)", p -> expectState(p, ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
			sCheck("B5 no reiatsu was drained during the 45 s", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 990 ? null : "reiatsu " + v;
			});
			sCheck("B5 bankaiEndTick cleared", p -> sm(p).snapshot().bankaiEndTick() == 0 ? null : "bankaiEndTick " + sm(p).snapshot().bankaiEndTick());
			cCheck("B5 client SHIKAI/BYAKUYA", () -> clientExpect(ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
			cCheck("B5 held stack SHIKAI", () -> clientStack(ReleaseState.SHIKAI));
			shot("13_after_bankai_timer_back_in_shikai");
		});
		step("B5b press G right after the timer (re-entry lock)", 14, () -> press(ReiatsuKeys.BANKAI));
		step("B5b checks", 3, () -> {
			cCheck("B5b bankai is locked after the timer even with a full bar (COOLDOWN)", () -> lastResultIs("COOLDOWN") ? null : "last result " + lastResult());
			sCheck("B5b still SHIKAI", p -> expectState(p, ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
		});
		step("B5c shikai ability still works after the timer: Z (mode attack)", 6, () -> {
			spawnCows(6.5);
			view(0.5, -60, 0.5, 0, 15);
		});
		step("B5c press Z", 24, () -> press(ReiatsuKeys.SLOTS[0]));
		step("B5c checks", 3, () -> cCheck("B5c action_result OK", () -> lastResultIs("OK") ? null : "last result " + lastResult()));
		step("clear the lock (dev command), full bar, bankai", 5, () -> cmd("reiatsu cooldowns clear", "reiatsu full"));
		step("press G", 14, () -> press(ReiatsuKeys.BANKAI));
		step("cows for scatter / hakuteiken", 50, () -> {
			spawnCows(7.0, 9.0, 12.0);
			// B4 step 4: a cow 10.9 blocks from the caster, inside the 12 block storm but 3+ blocks outside the old 5 block radius
			spawnFar("-10.5 -60 3.0");
			view(0.5, -60, 0.5, 0, 12);
		});
		step("press U (scatter, bankai row)", 50, () -> press(ReiatsuKeys.BANKAI_SLOTS[0]));
		step("B6 checks: scatter", 3, () -> {
			shot("14_scatter");
			sCheck("B6 ACCEPTANCE: the whole group (7, 9, 12 blocks and the cow 10.9 blocks aside) is dead after the 16 HP impact + storm", p -> {
				return cows(p).isEmpty() ? null : "cows alive " + cows(p).size();
			});
			sCheck("B6 bankai ability cost no reiatsu", p -> sm(p).reiatsu().value() >= 990 ? null : "reiatsu " + sm(p).reiatsu().value());
		});
		step("wait gcd, hakuteiken", 24, () -> cmd("reiatsu full"));
		step("press I (hakuteiken, bankai row)", 40, () -> press(ReiatsuKeys.BANKAI_SLOTS[1]));
		step("B7 checks: hakuteiken", 3, () -> {
			shot("15_hakuteiken");
			sCheck("B7 effect events delivered", p -> ClientNet.EFFECT_EVENTS.get() >= 6 ? null : "events " + ClientNet.EFFECT_EVENTS.get());
			sCheck("B7 no temporary blocks from byakuya", p -> TempBlocks.size() == 0 ? null : "temp " + TempBlocks.size());
		});
		step("B7b cow ahead, full reiatsu: shikai ability inside bankai", 24, () -> {
			spawnCows(6.5);
			cmd("reiatsu full");
			view(0.5, -60, 0.5, 0, 15);
		});
		step("B7b press Z (mode attack inside bankai)", 30, () -> press(ReiatsuKeys.SLOTS[0]));
		step("B7b checks", 3, () -> {
			cCheck("B7b action_result OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			sCheck("B7b still BANKAI", p -> expectState(p, ZanpakutoState.BANKAI, CharacterId.BYAKUYA));
			sCheck("B7b charged its 12.0 (bar 860..930, not 1000)", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 860 && v <= 930 ? null : "reiatsu " + v;
			});
			sCheck("B7b cow lost HP", p -> {
				List<CowEntity> c = cows(p);
				return c.size() == 1 && c.get(0).getHealth() <= 2.01F ? null : "cows " + c.size();
			});
		});
		step("reiatsu to 0.3 inside bankai", 3, () -> cmd("reiatsu set 0.3"));
		step("B8 wait", 45, () -> { });
		step("B8 checks: an empty bar no longer ends bankai", 4, () -> {
			sCheck("B8 server still BANKAI at 0.3 reiatsu (the timer alone ends it)", p -> expectState(p, ZanpakutoState.BANKAI, CharacterId.BYAKUYA));
		});
		step("B8 press V (seal)", 10, () -> press(ReiatsuKeys.SEAL));
		step("B8 checks: sealed", 3, () -> {
			sCheck("B8 server SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
		});

		// ---------------- D. shunpo (B4 step 5) and the keys
		buildShunpoSteps();

		// ---------------- C. hand, drop, death, dimension
		step("C1 release rukia, switch hand away", 175, () -> {
			selectSlot(0);
			cmd("reiatsu full");
		});
		step("press J", 14, () -> press(ReiatsuKeys.DRAW));
		step("press R", 14, () -> press(ReiatsuKeys.RELEASE));
		step("select empty slot", 10, () -> selectSlot(5));
		step("C1 checks: inside the 1 s hand grace", 3, () -> {
			sCheck("C1 still SHIKAI after 0.5 s without the item", p -> expectState(p, ZanpakutoState.SHIKAI, CharacterId.RUKIA));
		});
		step("wait out the grace", 22, () -> { });
		step("C1 checks: grace expired", 3, () -> {
			sCheck("C1 SEALED after the grace", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("C1 client SEALED", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
		});
		step("C2 re-equip and release", 14, () -> {
			selectSlot(0);
			cmd("reiatsu full");
		});
		step("press J", 14, () -> press(ReiatsuKeys.DRAW));
		step("press R", 14, () -> press(ReiatsuKeys.RELEASE));
		step("C2 remove the item (drop)", 6, () -> cmd("item replace entity @s hotbar.0 with minecraft:air"));
		step("C2 checks: immediate seal on drop", 3, () -> {
			sCheck("C2 SEALED right after the item left the inventory", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
		});
		step("C2b give back, only draw, then drop", 20, () -> {
			cmd("item replace entity @s hotbar.0 with " + RUKIA, "reiatsu full");
		});
		step("C2b press J", 14, () -> press(ReiatsuKeys.DRAW));
		step("C2b checks: drawn", 2, () -> sCheck("C2b server BASE", p -> expectState(p, ZanpakutoState.BASE, CharacterId.RUKIA)));
		step("C2b remove the item", 6, () -> cmd("item replace entity @s hotbar.0 with minecraft:air"));
		step("C2b checks: immediate seal on drop of the drawn sword", 3, () -> {
			sCheck("C2b SEALED right after the item left the inventory", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
		});
		step("C2c give back, draw, switch hand away (hand grace)", 20, () -> {
			cmd("item replace entity @s hotbar.0 with " + RUKIA);
		});
		step("C2c press J", 14, () -> press(ReiatsuKeys.DRAW));
		step("C2c select empty slot", 10, () -> selectSlot(5));
		step("C2c checks: inside the grace", 3, () -> sCheck("C2c still BASE after 0.5 s without the item", p -> expectState(p, ZanpakutoState.BASE, CharacterId.RUKIA)));
		step("C2c wait out the grace", 22, () -> { });
		step("C2c checks: grace expired", 3, () -> {
			sCheck("C2c SEALED after the grace", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("C2c client SEALED", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
		});
		step("C3 give back, release, die", 20, () -> {
			cmd("item replace entity @s hotbar.0 with " + RUKIA, "reiatsu full");
			selectSlot(0);
		});
		step("press J", 14, () -> press(ReiatsuKeys.DRAW));
		step("press R", 14, () -> press(ReiatsuKeys.RELEASE));
		step("kill the player", 30, () -> cmd("kill @s"));
		stepUntil("respawn", 5, 400, () -> {
			if (mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen) {
				mc.player.requestRespawn();
			}
		}, () -> mc.player != null && mc.player.isAlive() && mc.currentScreen == null);
		step("C3 checks: after respawn", 30, () -> { });
		step("C3 checks", 3, () -> {
			sCheck("C3 server SEALED after respawn", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			sCheck("C3 reiatsu back at 50%", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 500 && v <= 700 ? null : "reiatsu " + v;
			});
			cCheck("C3 client SEALED after respawn (sync on the new entity)", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("C3 client reiatsu synced after respawn", () -> {
				ReiatsuData r = ClientState.reiatsu();
				return r != null && r.value() >= 500 && r.value() <= 700 ? null : "client reiatsu " + r;
			});
		});
		step("C4 release again, then change dimension", 14, () -> {
			cmd("item replace entity @s hotbar.0 with " + RUKIA, "reiatsu full", "tp @s 0.5 -60 0.5 0 20");
			selectSlot(0);
		});
		step("press J", 14, () -> press(ReiatsuKeys.DRAW));
		step("press R", 14, () -> press(ReiatsuKeys.RELEASE));
		step("C4 checks: released before the trip", 2, () -> {
			sCheck("C4 SHIKAI before dimension change", p -> expectState(p, ZanpakutoState.SHIKAI, CharacterId.RUKIA));
		});
		step("go to the nether", 10, () -> cmd("execute in minecraft:the_nether run tp @s 0 100 0"));
		stepUntil("wait for dimension change", 20, 20 * 60, () -> { }, () -> mc.world != null && mc.player != null
				&& mc.world.getRegistryKey().getValue().toString().equals("minecraft:the_nether") && mc.currentScreen == null);
		step("settle", 40, () -> { });
		step("C4 checks: after dimension change", 3, () -> {
			sCheck("C4 server SEALED after the trip", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("C4 client SEALED (attachment sync across dimensions)", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("C4 client reiatsu still synced", () -> {
				ReiatsuData r = ClientState.reiatsu();
				return r != null && r.value() > 0 ? null : "client reiatsu " + r;
			});
			cCheck("C4 held stack SEALED", () -> clientStack(ReleaseState.SEALED));
		});
		step("back to the overworld", 5, () -> cmd("execute in minecraft:overworld run tp @s 0.5 -60 0.5 0 20"));
		stepUntil("wait for overworld", 20, 20 * 60, () -> { }, () -> mc.world != null && mc.player != null
				&& mc.world.getRegistryKey().getValue().toString().equals("minecraft:overworld") && mc.currentScreen == null);

		step("finish", 10, () -> {
			finish();
			mc.scheduleStop();
		});
	}

	// ---------------------------------------------------------------- D. shunpo (B4 step 5)

	private static volatile net.minecraft.util.math.Vec3d shStart = net.minecraft.util.math.Vec3d.ZERO;
	private static volatile int shReiatsuBefore;
	private static int shPlayedBefore;

	private static void shRecord() {
		server().execute(() -> {
			ServerPlayerEntity p = server().getPlayerManager().getPlayerList().get(0);
			shStart = p.getPos();
			shReiatsuBefore = sm(p).reiatsu().value();
		});
	}

	/** Server-side distance moved since {@link #shRecord} and the movement along z. */
	private static String shMoved(ServerPlayerEntity p, double minDist, double maxDist, boolean needZ) {
		double d = p.getPos().distanceTo(shStart);
		if (d < minDist || d > maxDist) {
			return "moved " + String.format(java.util.Locale.ROOT, "%.2f", d) + " blocks, expected " + minDist + ".." + maxDist;
		}
		if (needZ && p.getZ() - shStart.z < minDist - 0.1) {
			return "not along +z: dz " + (p.getZ() - shStart.z);
		}
		return null;
	}

	private static void buildShunpoSteps() {
		// ---- the key and the cost of the keys
		step("D0 key checks", 2, () -> {
			cCheck("D0 shunpo key is Y and rebindable (a KeyBinding in the reiatsu category)", () ->
					ReiatsuKeys.SHUNPO.getDefaultKey().getCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_Y
							&& ReiatsuKeys.SHUNPO.getCategory().equals(ReiatsuKeys.CATEGORY) ? null : "key " + ReiatsuKeys.SHUNPO.getDefaultKey());
			cCheck("D0 no other key binding in the game (vanilla or ours) shares Y, U, I or O", () -> {
				List<KeyBinding> mine = new ArrayList<>(List.of(ReiatsuKeys.SHUNPO));
				mine.addAll(List.of(ReiatsuKeys.BANKAI_SLOTS));
				for (KeyBinding k : mc.options.allKeys) {
					for (KeyBinding m : mine) {
						if (k != m && k.getBoundKeyTranslationKey().equals(m.getBoundKeyTranslationKey())) {
							return k.getTranslationKey() + " clashes with " + m.getTranslationKey();
						}
					}
				}
				return null;
			});
			cCheck("D0 the four new keys have names in the current language", () -> {
				for (KeyBinding k : new KeyBinding[] {ReiatsuKeys.SHUNPO, ReiatsuKeys.BANKAI_SLOTS[0], ReiatsuKeys.BANKAI_SLOTS[1], ReiatsuKeys.BANKAI_SLOTS[2]}) {
					if (!net.minecraft.client.resource.language.I18n.hasTranslation(k.getTranslationKey())) {
						return "no translation for " + k.getTranslationKey();
					}
				}
				return null;
			});
		});

		// ---- states that must refuse
		step("D1 setup: Byakuya in hand, flat ground, sealed", 40, () -> {
			cmd("kill @e[type=minecraft:cow]", "reiatsu full", "reiatsu cooldowns clear", "tp @s 0.5 -60 0.5 0 0");
			selectSlot(1);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			ClientNet.RESULTS.clear();
		});
		step("D1 press Y while sealed", 12, () -> press(ReiatsuKeys.SHUNPO));
		step("D1 checks: refused in SEALED", 3, () -> {
			cCheck("D1 shunpo in SEALED is DENIED_STATE", () -> lastResultIs("DENIED_STATE") ? null : "last result " + lastResult());
			sCheck("D1 nothing moved", p -> Math.abs(p.getZ() - 0.5) < 0.01 ? null : "z " + p.getZ());
		});
		step("D1 draw (J), press Y in the base form", 14, () -> press(ReiatsuKeys.DRAW));
		step("D1 press Y", 12, () -> press(ReiatsuKeys.SHUNPO));
		step("D1 checks: refused in BASE", 3, () -> {
			cCheck("D1 shunpo in BASE is DENIED_STATE", () -> lastResultIs("DENIED_STATE") ? null : "last result " + lastResult());
			sCheck("D1 nothing moved", p -> Math.abs(p.getZ() - 0.5) < 0.01 ? null : "z " + p.getZ());
		});
		step("D2 release (R), wait the transition lock", 20, () -> press(ReiatsuKeys.RELEASE));

		// ---- 9 blocks along the look direction, afterimages, cooldown, cost (Byakuya, third person)
		step("D2 third person front, record, press Y", 6, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
			cmd("reiatsu full");
			shRecord();
		});
		step("D2 press Y", 2, () -> {
			shPlayedBefore = ShunpoFx.played;
			FxSound.LOG.clear();
			ShunpoFx.renderNanos = 0;
			ShunpoFx.renderNanosMax = 0;
			ShunpoFx.renderFrames = 0;
			ShunpoFx.maxDrawnAlpha = 0;
			press(ReiatsuKeys.SHUNPO);
		});
		stepUntil("D2 wait for the effect", 1, 60, () -> { },
				() -> ShunpoFx.played > shPlayedBefore);
		step("D2 freeze", 1, () -> FxClock.freezeIn(0.04));
		stepUntil("D2 wait for the freeze", 1, 60, () -> { }, () -> FxClock.frozen);
		step("D2 server checks before the side view", 4, () -> {
			sCheck("D2 server: moved about 9 blocks along +z (the look direction)", p -> shMoved(p, 8.6, 9.2, true));
			sCheck("D2 server: reiatsu cost about 5.0", p -> {
				int spent = shReiatsuBefore - sm(p).reiatsu().value();
				return spent >= 20 && spent <= 55 ? null : "spent " + spent;
			});
			sCheck("D2 server: still SHIKAI, shunpo does not change the state", p -> expectState(p, ZanpakutoState.SHIKAI, CharacterId.BYAKUYA));
		});
		step("D2 side view: turn the player to the side (rotation only, the images stay in the world), camera behind", 8, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 4.5 90 8");
		});
		step("D2 shot byakuya afterimages", 4, () -> {
			shot("16_shunpo_byakuya_frozen");
			cCheck("D2 4 to 6 afterimages alive in the frozen frame", () -> ShunpoFx.liveImages() >= 4 && ShunpoFx.liveImages() <= 6 ? null : "live " + ShunpoFx.liveImages());
			cCheck("D2 all drawn in the frozen frame (translucent copies of the player model)", () -> ShunpoFx.drawnLastFrame >= 4 ? null : "drawn " + ShunpoFx.drawnLastFrame);
			cCheck("D2 peak opacity between 0.25 and 0.6", () -> ShunpoFx.maxDrawnAlpha > 0.25 && ShunpoFx.maxDrawnAlpha <= ShunpoFx.ALPHA0 + 1e-3 ? null : "alpha " + ShunpoFx.maxDrawnAlpha);
			cCheck("D2 image life 0.4..0.6 s", () -> ShunpoFx.LIFE >= 0.4 && ShunpoFx.LIFE <= 0.6 ? null : "life " + ShunpoFx.LIFE);
			cCheck("D2 trail particles spawned", () -> ShunpoFx.lastTrailParticles >= 20 ? null : "trail " + ShunpoFx.lastTrailParticles);
			cCheck("D2 sound played", () -> FxSound.LOG.contains("entity.player.attack.sweep") ? null : "sounds " + FxSound.LOG);
			cCheck("D2 effect distance reported by the server is about 9", () -> Math.abs(ShunpoFx.lastDistance - 9.0) < 0.3 ? null : "distance " + ShunpoFx.lastDistance);
			cCheck("D2 action_result OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
		});
		step("D2 unfreeze, press Y at once (cooldown)", 10, () -> {
			FxClock.unfreeze();
			press(ReiatsuKeys.SHUNPO);
		});
		step("D2 checks: cooldown", 3, () -> {
			cCheck("D2 second shunpo is COOLDOWN", () -> lastResultIs("COOLDOWN") ? null : "last result " + lastResult());
			cCheck("D2 afterimages are gone 0.6 s after the shunpo", () -> ShunpoFx.liveImages() == 0 ? null : "live " + ShunpoFx.liveImages());
			cCheck("D2 render cost of the afterimages under 1 ms per frame on average", () -> {
				ReiatsuTest.LOGGER.info(P + "shunpo render: {} frames with images, mean {} ms, max {} ms", ShunpoFx.renderFrames,
						ShunpoFx.renderFrames == 0 ? 0 : ShunpoFx.renderNanos / 1.0e6 / ShunpoFx.renderFrames, ShunpoFx.renderNanosMax / 1.0e6);
				return ShunpoFx.renderNanosMax / 1.0e6 < 5.0 ? null : "max " + ShunpoFx.renderNanosMax / 1.0e6 + " ms";
			});
		});

		// ---- wall right ahead: refused, no cost, no cooldown
		step("D3 back to the start, wall one block ahead", 60, () -> {
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			cmd("tp @s 0.5 -60 0.5 0 0", "fill -3 -60 1 4 -57 1 minecraft:stone");
		});
		step("D3 record, press Y at the wall", 6, Phase4Harness::shRecord);
		step("D3 press Y", 12, () -> press(ReiatsuKeys.SHUNPO));
		step("D3 checks: BLOCKED, free", 3, () -> {
			cCheck("D3 shunpo with a wall right ahead is BLOCKED", () -> lastResultIs("BLOCKED") ? null : "last result " + lastResult());
			sCheck("D3 did not move", p -> shMoved(p, 0.0, 0.05, false));
			sCheck("D3 no reiatsu spent", p -> sm(p).reiatsu().value() >= shReiatsuBefore ? null : "reiatsu " + sm(p).reiatsu().value() + " < " + shReiatsuBefore);
			sCheck("D3 no cooldown started", p -> sm(p).shunpoCooldownRemaining() == 0 ? null : "cooldown " + sm(p).shunpoCooldownRemaining());
			shot("17_shunpo_blocked");
		});
		step("D3 wall away, press Y at once (no cooldown was started)", 12, () -> {
			cmd("fill -3 -60 1 4 -57 1 minecraft:air");
		});
		step("D3 press Y", 12, () -> press(ReiatsuKeys.SHUNPO));
		step("D3 checks: free path works right away", 3, () -> {
			cCheck("D3 OK after the blocked try", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			sCheck("D3 moved 9 blocks", p -> shMoved(p, 8.6, 9.2, true));
		});

		// ---- a wall 4 blocks ahead: stops in front of it
		step("D4 back to the start, wall at z = 4, wait the cooldown", 60, () -> {
			cmd("tp @s 0.5 -60 0.5 0 0", "fill -3 -60 4 4 -57 4 minecraft:stone", "reiatsu full");
		});
		step("D4 record", 6, Phase4Harness::shRecord);
		step("D4 press Y", 12, () -> press(ReiatsuKeys.SHUNPO));
		step("D4 checks: stops before the wall", 3, () -> {
			cCheck("D4 OK (a shorter dash)", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			sCheck("D4 server: stopped in front of the wall, not through it", p -> {
				double z = p.getZ();
				return z >= 3.2 && z <= 3.71 ? null : "z " + z;
			});
			sCheck("D4 server: the wall block is untouched", p -> p.getServerWorld().getBlockState(new BlockPos(0, -60, 4)).isOf(net.minecraft.block.Blocks.STONE) ? null : "wall changed");
			shot("18_shunpo_stopped_by_wall");
		});

		// ---- looking up from the ground: a shorter dash that is put down on the floor, never left in mid-air or in a block
		step("D5 clean the wall, look up 30 degrees", 60, () -> {
			cmd("fill -3 -60 4 4 -57 4 minecraft:air", "tp @s 0.5 -60 0.5 0 -30", "reiatsu full");
		});
		step("D5 record, press Y", 6, Phase4Harness::shRecord);
		step("D5 press Y", 12, () -> press(ReiatsuKeys.SHUNPO));
		step("D5 checks: lands on the floor", 3, () -> {
			sCheck("D5 server: the player is not stuck in a block", p -> p.getServerWorld().isSpaceEmpty(p, p.getBoundingBox()) ? null : "inside a block");
			cCheck("D5 upward dash OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			sCheck("D5 server: put down on the floor (y -60), shorter than 9 blocks", p -> {
				double dz = p.getZ() - shStart.z;
				return Math.abs(p.getY() + 60.0) < 0.1 && dz > 3.0 && dz < 8.9 ? null : "y " + p.getY() + " dz " + dz;
			});
		});

		// ---- bankai: shunpo works in BANKAI too
		step("D6 bankai (lock cleared), full bar", 20, () -> {
			cmd("reiatsu cooldowns clear", "reiatsu full", "tp @s 0.5 -60 0.5 0 0");
		});
		step("D6 press G", 60, () -> press(ReiatsuKeys.BANKAI));
		step("D6 record", 4, Phase4Harness::shRecord);
		step("D6 press Y in bankai", 12, () -> press(ReiatsuKeys.SHUNPO));
		step("D6 checks", 3, () -> {
			sCheck("D6 server BANKAI/BYAKUYA", p -> expectState(p, ZanpakutoState.BANKAI, CharacterId.BYAKUYA));
			cCheck("D6 shunpo in BANKAI is OK", () -> lastResultIs("OK") ? null : "last result " + lastResult());
			sCheck("D6 moved 9 blocks", p -> shMoved(p, 8.6, 9.2, true));
		});
		step("D6 seal (V), wait the release lock", 60, () -> {
			press(ReiatsuKeys.SEAL);
			cmd("tp @s 0.5 -60 0.5 0 0");
		});

		// ---- Rukia (shared ability, ice trail, other tint)
		step("D7 Rukia: select, draw", 14, () -> {
			selectSlot(0);
			cmd("reiatsu full", "reiatsu cooldowns clear");
			mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
		});
		step("D7 press J", 14, () -> press(ReiatsuKeys.DRAW));
		step("D7 press R", 24, () -> press(ReiatsuKeys.RELEASE));
		step("D7 record", 4, Phase4Harness::shRecord);
		step("D7 press Y", 2, () -> {
			shPlayedBefore = ShunpoFx.played;
			press(ReiatsuKeys.SHUNPO);
		});
		stepUntil("D7 wait for the effect", 1, 60, () -> { }, () -> ShunpoFx.played > shPlayedBefore);
		step("D7 freeze 0.1 s", 1, () -> FxClock.freezeIn(0.04));
		stepUntil("D7 wait for the freeze", 1, 60, () -> { }, () -> FxClock.frozen);
		step("D7 server check", 4, () -> sCheck("D7 server: Rukia moved 9 blocks too (shared ability)", p -> shMoved(p, 8.6, 9.2, true)));
		step("D7 side view", 8, () -> {
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			cmd("tp @s 0.5 -60 4.5 90 8");
		});
		step("D7 shot rukia afterimages", 4, () -> {
			shot("19_shunpo_rukia_frozen");
			cCheck("D7 4 to 6 afterimages", () -> ShunpoFx.liveImages() >= 4 && ShunpoFx.liveImages() <= 6 ? null : "live " + ShunpoFx.liveImages());
		});
		step("D7 unfreeze, look at the trail", 4, FxClock::unfreeze);
		step("D7 shot trail a moment later", 8, () -> shot("20_shunpo_rukia_trail"));
		step("D7 back to first person, seal", 30, () -> {
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			press(ReiatsuKeys.SEAL);
			cmd("tp @s 0.5 -60 0.5 0 20");
		});
	}

	private static void bootstrapWorld() {
		var o = mc.options;
		o.pauseOnLostFocus = false;
		o.getViewDistance().setValue(6);
		o.getSimulationDistance().setValue(5);
		o.getGamma().setValue(0.0);
		o.getMaxFps().setValue(120);
		o.getEnableVsync().setValue(false);
		o.getShowAutosaveIndicator().setValue(false);
		o.getBobView().setValue(false);
		o.getChatVisibility().setValue(net.minecraft.network.message.ChatVisibility.HIDDEN);
		o.getGraphicsMode().setValue(GraphicsMode.FANCY);
		o.getGuiScale().setValue(3);
		deleteWorld();
		mc.createIntegratedServerLoader().createAndStart(
				WORLD,
				new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.EASY, true, new GameRules(),
						DataConfiguration.SAFE_MODE),
				new GeneratorOptions(12345L, false, false),
				registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value()
						.createDimensionsRegistryHolder(),
				mc.currentScreen);
	}

	private static void deleteWorld() {
		File dir = new File(mc.runDirectory, "saves/" + WORLD);
		if (!dir.exists()) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir.toPath())) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException e) {
			ReiatsuTest.LOGGER.warn(P + "could not delete old world: {}", e.toString());
		}
	}
}
