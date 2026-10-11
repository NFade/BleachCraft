package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.ParryBus;
import dev.minebleach.reiatsutest.core.rage.RageConfig;
import dev.minebleach.reiatsutest.core.rage.RageMeter;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.data.RageData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Minecraft glue of Rage (PLAN_2026-10-11 B4): the third resource next to reiatsu. The rules live in {@link RageMeter} (pure,
 * tested). Gain comes from damage taken ({@code AFTER_DAMAGE}: the amount that really reached the player, so a parried hit
 * gives no damage rage and a blocked hit gives it for the reduced amount) and from successful parries ({@link ParryBus}).
 * The bar drains out of combat, resets on death and logout, and is synced to its owner as the {@link ModAttachments#RAGE}
 * attachment (only when the value changed).
 *
 * <p>Extension point: {@link #canSpend} / {@link #spend}. No ability spends Rage yet, and Rage does not gate any state
 * transition yet (that is the open question F3 of the plan).
 */
public final class RageManager {
	/** The numbers in use. Constant for now; tuning is a code change (see design/results/rage.md). */
	public static final RageConfig CONFIG = RageConfig.defaults();

	private static final Map<UUID, RageMeter> METERS = new HashMap<>();
	private static MinecraftServer server;

	private RageManager() {
	}

	public static void init() {
		ParryBus.register(e -> {
			MinecraftServer srv = server;
			if (srv == null || srv.getPlayerManager().getPlayer(e.defender()) == null) {
				return; // defender went offline in the same tick
			}
			int gained = meterOf(e.defender()).onParry(e.tick());
			ReiatsuTest.LOGGER.info("[rage] parry by {}: +{} tenths", e.defender(), gained);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				onDamaged(entity, source, damageTaken, blocked));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity p) {
				meterOf(p.getUuid()).reset();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
			meterOf(handler.player.getUuid()).reset();
			sync(handler.player, meterOf(handler.player.getUuid()));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> METERS.remove(handler.player.getUuid()));
		ServerTickEvents.END_SERVER_TICK.register(RageManager::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> server = srv);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			METERS.clear();
			server = null;
		});
	}

	private static RageMeter meterOf(UUID id) {
		return METERS.computeIfAbsent(id, k -> new RageMeter(CONFIG));
	}

	/** The meter of an online player (created on first use). Read-only use for HUD/commands; spend through {@link #spend}. */
	public static RageMeter meter(ServerPlayerEntity p) {
		return meterOf(p.getUuid());
	}

	/** Extension point for abilities: true if the player has at least {@code costTenths} of Rage. */
	public static boolean canSpend(ServerPlayerEntity p, int costTenths) {
		return meterOf(p.getUuid()).canSpend(costTenths);
	}

	/** Extension point for abilities: pays {@code costTenths} if possible (syncs right away); false changes nothing. */
	public static boolean spend(ServerPlayerEntity p, int costTenths) {
		RageMeter m = meterOf(p.getUuid());
		boolean ok = m.spend(costTenths);
		if (ok) {
			sync(p, m);
		}
		return ok;
	}

	private static void onDamaged(LivingEntity entity, DamageSource source, float damageTaken, boolean blocked) {
		if (!(entity instanceof ServerPlayerEntity p) || blocked || !(damageTaken > 0.0F)
				|| source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		meterOf(p.getUuid()).onDamageTaken(damageTaken, p.getServer().getTicks());
	}

	private static void tick(MinecraftServer srv) {
		int now = srv.getTicks();
		for (ServerPlayerEntity p : srv.getPlayerManager().getPlayerList()) {
			RageMeter m = meterOf(p.getUuid());
			m.tick(now);
			sync(p, m);
		}
	}

	/** Writes the attachment only when it differs, so a quiet bar costs no packets. */
	private static void sync(ServerPlayerEntity p, RageMeter m) {
		RageData d = RageData.of(m.state());
		if (!d.equals(p.getAttached(ModAttachments.RAGE))) {
			p.setAttached(ModAttachments.RAGE, d);
		}
	}
}
