package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.MeleeHitRules;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Minecraft glue of the melee hit reiatsu (r8 item 5): every melee hit that a player lands with a zanpakuto in the main hand
 * restores {@code BalanceConfig.meleeHitReiatsu} (1.0 point = 10 tenths). The rule is {@link MeleeHitRules} (pure, tested).
 *
 * <p>It listens to the same events as the Rage gain for "dealt" ({@code RageManager}): {@code AFTER_DAMAGE} (the amount that really
 * landed, so a parried, barrier-eaten, missed or Creative-immune hit gives nothing) and {@code AFTER_DEATH} for the killing blow, which
 * Fabric does not report through AFTER_DAMAGE. Only {@code PLAYER_ATTACK} damage counts: Better Combat strikes call the vanilla attack and
 * arrive as that type, abilities and kido arrive as FREEZE / INDIRECT_MAGIC and never match. One reward per damaged target (a Better
 * Combat swing through two mobs pays twice).
 */
public final class MeleeReiatsu {
	private MeleeReiatsu() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> onHit(entity, source, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onHit(entity, source, Float.MAX_VALUE)); // a lethal hit did damage
	}

	private static void onHit(LivingEntity victim, DamageSource source, float damageTaken) {
		if (!(source.getAttacker() instanceof ServerPlayerEntity a) || a == victim || !source.isOf(DamageTypes.PLAYER_ATTACK)) {
			return;
		}
		boolean melee = source.getSource() == a;
		boolean holds = a.getMainHandStack().getItem() instanceof ZanpakutoItem;
		int perHit = ZanpakutoManager.configOf(a).meleeHitTenths();
		int tenths = MeleeHitRules.reiatsuTenths(melee, holds, damageTaken, perHit);
		if (tenths > 0) {
			ZanpakutoManager.grantMeleeReiatsu(a, tenths);
		}
	}
}
