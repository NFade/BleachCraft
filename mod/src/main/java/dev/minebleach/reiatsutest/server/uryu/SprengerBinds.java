package dev.minebleach.reiatsutest.server.uryu;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.uryu.BindBook;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Server side of the Sprenger bind (round 2, rules in {@link BindBook}): everything inside the pentagon at the bind moment gets a
 * movement-speed and a jump-strength modifier of -100 percent (two unique ids, temporary: never saved to disk, so a crash cannot leave
 * anybody stuck) and a player may not shunpo ({@link #shunpoBanned}). It is lifted when the time is up, when the bound one takes any
 * damage (this class listens to {@code AFTER_DAMAGE}; the killing blow is {@code AFTER_DEATH}), on death, logout, a change of dimension
 * and the server stopping; {@code UryuManager} releases the bound ones of a pentagon before its explosion and when the pentagon is
 * cancelled (caster dead, logged out, state changed). Touched from the server thread only.
 */
public final class SprengerBinds {
	/** The unique ids of the two modifiers. */
	public static final Identifier SPEED_ID = ReiatsuTest.id("uryu_sprenger_root");
	public static final Identifier JUMP_ID = ReiatsuTest.id("uryu_sprenger_nojump");

	private static final BindBook BOOK = new BindBook();
	/** World of every bound entity, to find it again (and to notice a change of dimension). */
	private static final Map<UUID, RegistryKey<World>> WORLDS = new HashMap<>();

	private SprengerBinds() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && damageTaken > 0f) {
				onDamaged(entity);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> release(entity, "death"));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> release(handler.player, "logout"));
	}

	/** Roots {@code e} from tick {@code now} for {@code ticks}. */
	public static void bind(LivingEntity e, int now, int ticks) {
		if (e.isDead() || !(e.getWorld() instanceof ServerWorld w)) {
			return;
		}
		BOOK.bind(e.getUuid(), now, ticks);
		WORLDS.put(e.getUuid(), w.getRegistryKey());
		modify(e, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, true);
		modify(e, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID, true);
	}

	/** True while the player may not use the shunpo (rooted by a Sprenger and not yet freed). */
	public static boolean shunpoBanned(ServerPlayerEntity p) {
		MinecraftServer srv = p.getServer();
		return srv != null && BOOK.active(p.getUuid(), srv.getTicks());
	}

	public static boolean isBound(UUID id, int now) {
		return BOOK.active(id, now);
	}

	/** Ends the bind of this entity (if any) and takes the modifiers off. True when there was one. */
	public static boolean release(LivingEntity e, String why) {
		boolean was = BOOK.release(e.getUuid());
		WORLDS.remove(e.getUuid());
		off(e);
		if (was) {
			ReiatsuTest.LOGGER.info("[uryu] sprenger bind of {} ends ({})", e.getName().getString(), why);
		}
		return was;
	}

	/** By uuid, for the pentagon cleanup: finds the entity in its world (or among the players) and releases it. */
	public static void release(MinecraftServer srv, UUID id, String why) {
		RegistryKey<World> key = WORLDS.get(id);
		Entity e = null;
		if (key != null && srv.getWorld(key) != null) {
			e = srv.getWorld(key).getEntity(id);
		}
		if (e == null) {
			e = srv.getPlayerManager().getPlayer(id);
		}
		if (e instanceof LivingEntity le) {
			release(le, why);
		} else {
			BOOK.release(id);
			WORLDS.remove(id);
		}
	}

	private static void onDamaged(LivingEntity e) {
		if (BOOK.size() == 0) {
			return;
		}
		if (BOOK.onDamage(e.getUuid())) {
			WORLDS.remove(e.getUuid());
			off(e);
			ReiatsuTest.LOGGER.info("[uryu] sprenger bind of {} broken by damage", e.getName().getString());
		}
	}

	/** Once per server tick: lifts the bind of whoever's time is up, was lost (removed mob) or changed dimension. */
	public static void tick(MinecraftServer srv, int now) {
		if (BOOK.size() == 0) {
			return;
		}
		for (UUID id : BOOK.all()) {
			RegistryKey<World> key = WORLDS.get(id);
			ServerWorld w = key == null ? null : srv.getWorld(key);
			Entity e = w == null ? null : w.getEntity(id);
			boolean over = now >= BOOK.endOf(id);
			if (e instanceof LivingEntity le && !le.isDead() && !le.isRemoved()) {
				if (over) {
					release(le, "time is up");
				}
				continue;
			}
			// gone from its world: dead, unloaded, or a player in another dimension / disconnected
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(id);
			if (p != null) {
				release(p, "dimension change");
			} else {
				BOOK.release(id);
				WORLDS.remove(id);
			}
		}
	}

	/** The server starts or stops: nobody stays rooted. */
	public static void clearAll(MinecraftServer srv) {
		if (srv != null) {
			for (UUID id : BOOK.all()) {
				release(srv, id, "server stop");
			}
		}
		BOOK.clear();
		WORLDS.clear();
	}

	private static void off(LivingEntity e) {
		modify(e, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, false);
		modify(e, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID, false);
	}

	private static void modify(LivingEntity e, RegistryEntry<EntityAttribute> attribute, Identifier id, boolean on) {
		EntityAttributeInstance inst = e.getAttributeInstance(attribute);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		if (on) {
			inst.addTemporaryModifier(new EntityAttributeModifier(id, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
