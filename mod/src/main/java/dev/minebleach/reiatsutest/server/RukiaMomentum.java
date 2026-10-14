package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.FrostMomentum;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import dev.minebleach.reiatsutest.registry.data.MomentumData;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Minecraft glue of the Rukia passive (R11 T6, variant P1 "the cold gathers"). The rules live in {@link FrostMomentum} (pure, tested).
 * <ul>
 * <li>STACKS are gained by landed hits of a Rukia in shikai or bankai: sword hits ({@code AFTER_DAMAGE} / {@code AFTER_DEATH} with
 * {@code PLAYER_ATTACK}, the same recipe as {@link MeleeReiatsu}) and ability hits ({@link Targeting#hurt}, via {@link #onAbilityHit}).
 * Kido and the frost field pulses do not count ({@link Targeting#hurtPlain}). A hit that lands on Rukia removes one stack; sealing, death
 * and logout reset (the machine's {@code StateChanged} event reaches {@link #onStateChanged}).</li>
 * <li>THE BONUS multiplies the damage: abilities in {@link Targeting#hurt} ({@link #abilityMultiplier}), the sword in
 * {@code LivingEntityMixin} before the damage is processed ({@link #meleeMultiplier}). A victim that holds the zanpakuto block gets
 * the unboosted hit (the bonus must not undo the block), a parried hit is cancelled anyway.</li>
 * <li>RAGE is not multiplied: {@link #unboost} gives {@code RageManager} the damage without the bonus.</li>
 * </ul>
 * The stacks are synced to the owner as the {@link ModAttachments#MOMENTUM} attachment for the HUD (only when they change).
 */
public final class RukiaMomentum {
	private static volatile FrostMomentum.Config config = FrostMomentum.Config.defaults();
	private static final Map<UUID, FrostMomentum> MOMENTUM = new HashMap<>();
	/** Melee multiplier applied to a victim in the current tick (entity id -> {tick, multiplier}), read by {@link #unboost}. Cleared every tick. */
	private static final Map<Integer, float[]> APPLIED = new HashMap<>();

	private RukiaMomentum() {
	}

	public static void init() {
		config = ModConfig.loadRukiaPassive();
		ConfigReload.register(() -> {
			FrostMomentum.Config c = ModConfig.loadRukiaPassive();
			config = c;
			for (FrostMomentum m : MOMENTUM.values()) {
				m.setConfig(c);
			}
			ReiatsuTest.LOGGER.info("[momentum] config reloaded: enabled {}, shikai {}, bankai {}", c.enabled(), c.shikai(), c.bankai());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F) {
				onLanded(entity, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity p) {
				reset(p.getUuid());
			}
			onLanded(entity, source); // a lethal hit landed
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> MOMENTUM.remove(handler.player.getUuid()));
		ServerTickEvents.END_SERVER_TICK.register(RukiaMomentum::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			MOMENTUM.clear();
			APPLIED.clear();
		});
	}

	// ------------------------------------------------------------------ who is Rukia

	/** The form of {@code p} for the passive: SHIKAI / BANKAI only as a released Rukia. */
	static FrostMomentum.Form formOf(ServerPlayerEntity p) {
		StateMachine sm = ZanpakutoManager.machine(p);
		if (sm == null || sm.character() != CharacterId.RUKIA) {
			return FrostMomentum.Form.NONE;
		}
		ZanpakutoState st = sm.state();
		return st == ZanpakutoState.SHIKAI ? FrostMomentum.Form.SHIKAI : st == ZanpakutoState.BANKAI ? FrostMomentum.Form.BANKAI : FrostMomentum.Form.NONE;
	}

	private static int now(ServerPlayerEntity p) {
		MinecraftServer srv = p.getServer();
		return srv == null ? 0 : srv.getTicks();
	}

	private static FrostMomentum of(UUID id) {
		return MOMENTUM.computeIfAbsent(id, k -> new FrostMomentum(config));
	}

	// ------------------------------------------------------------------ multipliers

	/** Multiplier of an ability hit of {@code caster} (1.0 unless a released Rukia with stacks). */
	static float abilityMultiplier(ServerPlayerEntity caster) {
		FrostMomentum m = MOMENTUM.get(caster.getUuid());
		if (m == null) {
			return 1.0F;
		}
		FrostMomentum.Form f = formOf(caster);
		return f == FrostMomentum.Form.NONE ? 1.0F : (float) m.multiplier(f, now(caster));
	}

	/**
	 * Multiplier of a sword hit, asked by {@code LivingEntityMixin} for EVERY damage call on the server, so the first lines are cheap exits. 1.0
	 * unless a player in shikai/bankai as Rukia, holding her sword, strikes in melee ({@code PLAYER_ATTACK} whose direct source is the player) a
	 * victim that is not blocking.
	 */
	public static float meleeMultiplier(LivingEntity victim, DamageSource source) {
		if (MOMENTUM.isEmpty() || !(source.getSource() instanceof ServerPlayerEntity a) || a == victim || !source.isOf(DamageTypes.PLAYER_ATTACK)) {
			return 1.0F;
		}
		if (ModItems.characterOf(a.getMainHandStack()) != CharacterId.RUKIA || ZanpakutoItem.isBlocking(victim)) {
			return 1.0F;
		}
		FrostMomentum m = MOMENTUM.get(a.getUuid());
		FrostMomentum.Form f = m == null ? FrostMomentum.Form.NONE : formOf(a);
		if (f == FrostMomentum.Form.NONE) {
			return 1.0F;
		}
		int t = now(a);
		float mult = (float) m.multiplier(f, t);
		if (mult > 1.0F) {
			APPLIED.put(victim.getId(), new float[] {t, mult});
		}
		return mult;
	}

	/** {@code hp} without the passive bonus that {@link #meleeMultiplier} applied to this victim in this tick (Rage must not be multiplied by stacks). */
	static float unboost(LivingEntity victim, int tick, float hp) {
		float[] ap = APPLIED.get(victim.getId());
		return ap != null && (int) ap[0] == tick && ap[1] > 0.0F ? hp / ap[1] : hp;
	}

	// ------------------------------------------------------------------ stacks

	/** A landed hit on {@code victim} by {@code source}: a stack for a Rukia sword hit, one stack less for a Rukia that was hit. */
	private static void onLanded(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getAttacker();
		if (attacker == null || attacker == victim) {
			return;
		}
		if (victim instanceof ServerPlayerEntity v) {
			FrostMomentum m = MOMENTUM.get(v.getUuid());
			FrostMomentum.Form f = m == null ? FrostMomentum.Form.NONE : formOf(v);
			if (f != FrostMomentum.Form.NONE && m.stacks(f, now(v)) > 0) {
				int after = m.onHurt(f, now(v));
				ReiatsuTest.LOGGER.info("[momentum] {} was hit by {}: {} stacks left", v.getName().getString(), attacker.getName().getString(), after);
			}
		}
		if (attacker instanceof ServerPlayerEntity a && source.getSource() == a && source.isOf(DamageTypes.PLAYER_ATTACK)
				&& ModItems.characterOf(a.getMainHandStack()) == CharacterId.RUKIA && !(victim instanceof ArmorStandEntity)) {
			gain(a, victim, "sword");
		}
	}

	/** A landed ability hit (called by {@link Targeting#hurt} after the damage went through). */
	static void onAbilityHit(ServerPlayerEntity caster, LivingEntity target) {
		if (!(target instanceof ArmorStandEntity)) {
			gain(caster, target, "ability");
		}
	}

	private static void gain(ServerPlayerEntity a, LivingEntity victim, String how) {
		FrostMomentum.Form f = formOf(a);
		if (f == FrostMomentum.Form.NONE || !config.enabled()) {
			return;
		}
		FrostMomentum m = of(a.getUuid());
		int t = now(a);
		int before = m.stacks(f, t);
		int after = m.onHit(f, t);
		if (after != before) {
			ReiatsuTest.LOGGER.info("[momentum] {} {} hit on {}: {} -> {} stacks (x{})", a.getName().getString(), how, victim.getName().getString(), before, after,
					String.format(Locale.ROOT, "%.2f", m.multiplier(f, t + 1)));
		}
	}

	/** The machine went to another state: sealing or drawing back drops the stacks. */
	static void onStateChanged(ServerPlayerEntity p, ZanpakutoState to) {
		if (to == ZanpakutoState.SEALED || to == ZanpakutoState.BASE) {
			reset(p.getUuid());
		}
	}

	private static void reset(UUID id) {
		FrostMomentum m = MOMENTUM.get(id);
		if (m != null) {
			m.reset();
		}
	}

	// ------------------------------------------------------------------ HUD sync

	private static void tick(MinecraftServer srv) {
		APPLIED.clear();
		int now = srv.getTicks();
		for (ServerPlayerEntity p : srv.getPlayerManager().getPlayerList()) {
			FrostMomentum m = MOMENTUM.get(p.getUuid());
			MomentumData d = MomentumData.EMPTY;
			if (m != null) {
				FrostMomentum.Form f = formOf(p);
				if (f != FrostMomentum.Form.NONE) {
					d = new MomentumData(m.stacks(f, now), m.config().tier(f).maxStacks());
				}
			}
			if (!d.equals(p.getAttached(ModAttachments.MOMENTUM))) {
				p.setAttached(ModAttachments.MOMENTUM, d);
			}
		}
	}
}
