package dev.minebleach.reiatsutest.client;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * R11 T11, client half. Better Combat decides in {@code pre_doAttack} whether a click swings the weapon or digs the block under the crosshair
 * ({@code isTargetingMineableBlock}, byte code read in R11): grass counts as swing-through only when {@code isSwingThruGrassEnabled} is on
 * AND, in the "smart" mode, an enemy is already in reach. Without an enemy the smart mode lets the click break the grass and no strike starts.
 * While a zanpakuto is in the main hand and no screen is open this class switches the smart mode off (and the swing-through on) in the live
 * Better Combat client config; as soon as the sword leaves the hand, a screen opens, or the game disconnects, the player's own values come back.
 * Only public config fields are touched, nothing of Better Combat is copied, and a missing / changed Better Combat only logs once.
 */
public final class BcGrassSwing {
	private static boolean applied;
	private static boolean savedSmart;
	private static boolean savedEnabled;
	private static boolean broken;

	private BcGrassSwing() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BcGrassSwing::tick);
	}

	private static void tick(MinecraftClient mc) {
		if (broken || !FabricLoader.getInstance().isModLoaded("bettercombat")) {
			return;
		}
		boolean want = mc.player != null && mc.currentScreen == null && mc.player.getMainHandStack().getItem() instanceof ZanpakutoItem;
		if (want == applied) {
			return;
		}
		try {
			if (want) {
				apply();
			} else {
				restore();
			}
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			ReiatsuTest.LOGGER.warn("[grass] Better Combat config is not reachable ({}), the sword will break grass under smart swing-through", e.toString());
		}
	}

	private static void apply() {
		var cfg = net.bettercombat.client.BetterCombatClientMod.config;
		if (cfg == null) {
			return;
		}
		savedSmart = cfg.isSwingThruGrassSmart;
		savedEnabled = cfg.isSwingThruGrassEnabled;
		cfg.isSwingThruGrassSmart = false;
		cfg.isSwingThruGrassEnabled = true;
		applied = true;
	}

	private static void restore() {
		var cfg = net.bettercombat.client.BetterCombatClientMod.config;
		if (cfg != null) {
			cfg.isSwingThruGrassSmart = savedSmart;
			cfg.isSwingThruGrassEnabled = savedEnabled;
		}
		applied = false;
	}
}
