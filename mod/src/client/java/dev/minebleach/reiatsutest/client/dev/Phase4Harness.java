package dev.minebleach.reiatsutest.client.dev;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientState;
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
			sCheck("A8 reiatsu 80.0", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 760 && v <= 800 ? null : "reiatsu " + v;
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
		step("press Z (absolute zero)", 42, () -> press(ReiatsuKeys.SLOTS[0]));
		step("A11 checks: absolute zero ice", 3, () -> {
			shot("10_absolute_zero_ice");
			sCheck("A11 temp ice placed", p -> TempBlocks.count(p.getUuid()) > 0 ? null : "no temp blocks");
			sCheck("A11 cows immobilised", p -> {
				for (CowEntity c : cows(p)) {
					var s = c.getStatusEffect(StatusEffects.SLOWNESS);
					if (s == null || s.getAmplifier() < 6) {
						return "cow slowness " + s;
					}
				}
				return cows(p).size() == 3 ? null : "cows " + cows(p).size();
			});
		});
		step("wait shatter", 30, () -> { });
		step("A11 checks: shatter", 3, () -> {
			sCheck("A11 cows dead after 14 HP shatter", p -> cows(p).isEmpty() ? null : "cows alive " + cows(p).size());
		});
		step("wait ice rollback", 110, () -> { });
		step("A11 checks: ice rolled back", 3, () -> {
			sCheck("A11 all temp ice gone", p -> TempBlocks.size() == 0 ? null : "still " + TempBlocks.size());
		});
		step("press V (seal)", 8, () -> press(ReiatsuKeys.SEAL));
		step("A12 checks: sealed", 3, () -> {
			sCheck("A12 server SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("A12 client SEALED", () -> clientExpect(ZanpakutoState.SEALED, CharacterId.NONE));
			cCheck("A12 held stack SEALED", () -> clientStack(ReleaseState.SEALED));
		});
		step("press R during release lock", 8, () -> press(ReiatsuKeys.RELEASE));
		step("A13 checks: release lock", 3, () -> {
			cCheck("A13 R during the 2 s release lock is COOLDOWN", () -> lastResultIs("COOLDOWN") ? null : "last result " + lastResult());
		});

		// ---------------- B. Byakuya
		step("cleanup, select byakuya", 60, () -> {
			cmd("kill @e[type=minecraft:zombie]", "reiatsu full");
			selectSlot(1);
			view(0.5, -60, 0.5, 0, 15);
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
		step("creative again, full reiatsu, wait", 150, () -> cmd("gamemode creative @s", "reiatsu full"));
		step("press G (byakuya bankai)", 14, () -> press(ReiatsuKeys.BANKAI));
		step("B4 checks: byakuya bankai", 3, () -> {
			sCheck("B4 server BANKAI/BYAKUYA", p -> expectState(p, ZanpakutoState.BANKAI, CharacterId.BYAKUYA));
			cCheck("B4 client BANKAI", () -> clientExpect(ZanpakutoState.BANKAI, CharacterId.BYAKUYA));
			shot("12_byakuya_bankai_hand_empty");
		});
		stepUntil("B5 idle bankai until the 45 s cap", 10, 20 * 60, () -> { }, () -> ClientState.zanpakuto().zanpakutoState() == ZanpakutoState.SEALED);
		step("B5 checks: cap revert", 4, () -> {
			sCheck("B5 server SEALED after the cap", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
			sCheck("B5 8.0 reiatsu left at the cap (80 - 72)", p -> {
				int v = sm(p).reiatsu().value();
				return v >= 80 && v <= 100 ? null : "reiatsu " + v;
			});
			cCheck("B5 held stack SEALED", () -> clientStack(ReleaseState.SEALED));
			shot("13_after_bankai_cap");
		});
		step("wait recovery lock 8 s, full reiatsu, release", 175, () -> cmd("reiatsu full"));
		step("press R", 14, () -> press(ReiatsuKeys.RELEASE));
		step("wait for full bar, bankai", 5, () -> cmd("reiatsu full"));
		step("press G", 14, () -> press(ReiatsuKeys.BANKAI));
		step("cows for scatter / hakuteiken", 50, () -> {
			spawnCows(7.0, 9.0, 12.0);
			view(0.5, -60, 0.5, 0, 12);
		});
		step("press Z (scatter)", 50, () -> press(ReiatsuKeys.SLOTS[0]));
		step("B6 checks: scatter", 3, () -> {
			shot("14_scatter");
			sCheck("B6 cows near the aim point lost HP", p -> {
				float min = 99;
				for (CowEntity c : cows(p)) {
					min = Math.min(min, c.getHealth());
				}
				return cows(p).size() < 3 || min < 10 ? null : "no cow was hurt";
			});
		});
		step("wait gcd, hakuteiken", 24, () -> cmd("reiatsu full"));
		step("press H (hakuteiken)", 40, () -> press(ReiatsuKeys.SLOTS[1]));
		step("B7 checks: hakuteiken", 3, () -> {
			shot("15_hakuteiken");
			sCheck("B7 effect events delivered", p -> ClientNet.EFFECT_EVENTS.get() >= 6 ? null : "events " + ClientNet.EFFECT_EVENTS.get());
			sCheck("B7 no temporary blocks from byakuya", p -> TempBlocks.size() == 0 ? null : "temp " + TempBlocks.size());
		});
		step("reiatsu to 0.3 (zero revert)", 3, () -> cmd("reiatsu set 0.3"));
		stepUntil("B8 zero reiatsu ends the bankai", 5, 200, () -> { }, () -> ClientState.zanpakuto().zanpakutoState() == ZanpakutoState.SEALED);
		step("B8 checks", 4, () -> {
			sCheck("B8 server SEALED", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
		});

		// ---------------- C. hand, drop, death, dimension
		step("C1 release rukia, switch hand away", 175, () -> {
			selectSlot(0);
			cmd("reiatsu full");
		});
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
		step("press R", 14, () -> press(ReiatsuKeys.RELEASE));
		step("C2 remove the item (drop)", 6, () -> cmd("item replace entity @s hotbar.0 with minecraft:air"));
		step("C2 checks: immediate seal on drop", 3, () -> {
			sCheck("C2 SEALED right after the item left the inventory", p -> expectState(p, ZanpakutoState.SEALED, CharacterId.NONE));
		});
		step("C3 give back, release, die", 20, () -> {
			cmd("item replace entity @s hotbar.0 with " + RUKIA, "reiatsu full");
		});
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
