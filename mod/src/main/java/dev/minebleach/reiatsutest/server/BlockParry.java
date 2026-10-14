package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.BlockOutcome;
import dev.minebleach.reiatsutest.core.combat.BlockRules;
import dev.minebleach.reiatsutest.core.combat.CombatConfig;
import dev.minebleach.reiatsutest.core.combat.DamageKind;
import dev.minebleach.reiatsutest.core.combat.ParryBus;
import dev.minebleach.reiatsutest.core.combat.ParryEvent;
import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

/**
 * Minecraft glue of the block and the parry (design/PARRY_PLAN.md sections 3 to 5). The rules live in
 * {@link BlockRules} (pure, tested); this class reads the game state, applies the outcome (knockback, stagger, sound, sparks)
 * and publishes {@link ParryEvent}s on {@link ParryBus} for the future Rage.
 *
 * <p>The block itself is the vanilla item use hold of {@link ZanpakutoItem} (right mouse button), so there is no separate block
 * state to keep in step: "is blocking" and "ticks since it began" are read live from the player when a hit arrives.
 */
public final class BlockParry {
	/** The numbers in use (window, reduction, stagger). Constant for now; tuning is a code change. */
	public static final CombatConfig CONFIG = CombatConfig.defaults();

	private BlockParry() {
	}

	public static void init() {
		ParryBus.setErrorHandler(t -> ReiatsuTest.LOGGER.error("[parry] listener failed", t));
		// blocking stops attacking: vanilla already ignores the attack key while an item is in use, this is the server side net
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				BlockRules.allowsAttack(ZanpakutoItem.isBlocking(player)) ? ActionResult.PASS : ActionResult.FAIL);
	}

	/** Maps a Minecraft damage source to the pure damage kind: only a direct melee hit of a living attacker counts as MELEE. */
	static DamageKind kindOf(DamageSource source) {
		boolean meleeType = source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK)
				|| source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO);
		return meleeType && source.getAttacker() instanceof LivingEntity ? DamageKind.MELEE : DamageKind.OTHER;
	}

	/**
	 * Called from the damage barrier ({@code ALLOW_DAMAGE}) for a hit on {@code defender}. Returns the outcome: NONE (carry on
	 * with the original damage), BLOCKED (the caller deals {@code outcome.damage()} instead) or PARRIED (the caller cancels the
	 * hit; knockback, stagger, feedback and the {@link ParryEvent} were done here).
	 */
	static BlockOutcome onHit(ServerPlayerEntity defender, DamageSource source, float amount) {
		int elapsed = ZanpakutoItem.blockTicks(defender);
		BlockOutcome out = BlockRules.resolve(elapsed >= 0, elapsed, kindOf(source), amount, CONFIG);
		if (out.kind() == BlockOutcome.Kind.NONE) {
			return out;
		}
		ServerWorld world = defender.getServerWorld();
		Vec3d mid = defender.getPos().add(0.0, defender.getHeight() * 0.6, 0.0);
		if (out.parried()) {
			LivingEntity attacker = (LivingEntity) source.getAttacker();
			Targeting.knockback(attacker, defender.getPos(), CONFIG.parryKnockback());
			if (CONFIG.staggerTicks() > 0) {
				attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, CONFIG.staggerTicks(),
						CONFIG.staggerAmplifier(), false, false, true), defender);
			}
			ReiatsuTest.LOGGER.info("[parry] {} parried {} at {} ticks, {} damage cancelled", defender.getName().getString(),
					attacker.getName().getString(), elapsed, amount);
			ParryBus.fire(new ParryEvent(defender.getUuid(), attacker.getUuid(), defender.getServer().getTicks(), amount));
			// r8 fx2: the sparks, rings and sounds are drawn by the clients from the feedback event (client/fx/BlockFx); the plain server
			// sound below only plays when the rate limit refused the event (a flood of hits), so a parry is never silent
			if (!ServerFx.blockFeedback(defender, attacker, true, amount)) {
				ModSound.play(world, SoundIds.GENERAL_PARRY, mid.x, mid.y, mid.z, "item.trident.hit", 0.9F, 1.7F);
				ModSound.play(world, SoundIds.GENERAL_PARRY, mid.x, mid.y, mid.z, "entity.player.attack.knockback", 0.7F, 1.4F);
			}
		} else {
			boolean sent = source.getAttacker() instanceof LivingEntity attacker && ServerFx.blockFeedback(defender, attacker, false, amount);
			if (!sent) {
				ModSound.play(world, SoundIds.GENERAL_BLOCK_HIT, mid.x, mid.y, mid.z, "item.shield.block", 0.8F, 1.1F);
			}
		}
		return out;
	}
}
