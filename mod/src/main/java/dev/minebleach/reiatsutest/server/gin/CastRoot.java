package dev.minebleach.reiatsutest.server.gin;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.gin.RootBook;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * The cast root of the Gin abilities (design/GIN.md section 6): the price of the extension. A rooted player gets Slowness (hidden) and
 * a movement-speed and a jump-strength modifier of -100 percent with ONE unique id; both are temporary attribute modifiers (never saved
 * to disk, so a crash cannot leave a player stuck) and are taken off by {@link #tick}, by {@link #release} and on death, logout and any
 * state change (the glue calls release). Pure timing lives in {@link RootBook}.
 */
public final class CastRoot {
	/** The unique id of the root modifiers. */
	public static final Identifier MODIFIER_ID = ReiatsuTest.id("gin_cast_root");

	private static final RootBook BOOK = new RootBook();

	private CastRoot() {
	}

	/** Roots {@code p} for {@code ticks} from now (a longer root already running is kept). */
	public static void root(ServerPlayerEntity p, int ticks, int slowAmplifier) {
		if (ticks <= 0 || p.isDead()) {
			return;
		}
		int now = p.getServer().getTicks();
		long end = BOOK.root(p.getUuid(), now, ticks);
		int left = (int) (end - now);
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, left + 1, slowAmplifier, false, false, false));
		modify(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, true);
		modify(p, EntityAttributes.GENERIC_JUMP_STRENGTH, true);
	}

	/** True while the player is rooted by a Gin cast. */
	public static boolean rooted(ServerPlayerEntity p) {
		return BOOK.active(p.getUuid(), p.getServer().getTicks());
	}

	/** Ticks of root left (0 when none). */
	public static int remaining(ServerPlayerEntity p) {
		return (int) Math.max(0L, BOOK.endOf(p.getUuid()) - p.getServer().getTicks());
	}

	/** Takes the root off at once (death, logout, state change, a finished or interrupted cast). */
	public static void release(ServerPlayerEntity p) {
		if (BOOK.release(p.getUuid())) {
			off(p);
		}
	}

	/** Forgets a player who is already gone (logout): nothing to take off any more, the modifiers died with the entity. */
	public static void forget(UUID id) {
		BOOK.release(id);
	}

	/** Once per server tick: lifts the root of everybody whose time is up. */
	public static void tick(MinecraftServer srv, long now) {
		if (BOOK.size() == 0) {
			return;
		}
		for (UUID id : BOOK.expired(now)) {
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(id);
			if (p != null) {
				off(p);
			}
		}
	}

	public static void clearAll(MinecraftServer srv) {
		for (UUID id : BOOK.all()) {
			ServerPlayerEntity p = srv == null ? null : srv.getPlayerManager().getPlayer(id);
			if (p != null) {
				off(p);
			}
		}
		BOOK.clear();
	}

	/** Everybody who is rooted right now. */
	public static java.util.List<UUID> all() {
		return BOOK.all();
	}

	public static int active() {
		return BOOK.size();
	}

	private static void off(ServerPlayerEntity p) {
		modify(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, false);
		modify(p, EntityAttributes.GENERIC_JUMP_STRENGTH, false);
		var slow = p.getStatusEffect(StatusEffects.SLOWNESS);
		// only our own hidden Slowness goes (a foreign one is visible or longer): ours is the amplifier we set and has no icon
		if (slow != null && !slow.shouldShowIcon() && !slow.shouldShowParticles()) {
			p.removeStatusEffect(StatusEffects.SLOWNESS);
		}
	}

	private static void modify(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attribute, boolean on) {
		EntityAttributeInstance inst = p.getAttributeInstance(attribute);
		if (inst == null) {
			return;
		}
		inst.removeModifier(MODIFIER_ID);
		if (on) {
			inst.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
