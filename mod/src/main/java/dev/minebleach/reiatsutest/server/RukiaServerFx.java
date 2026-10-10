package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.FieldAnchorPolicy;
import dev.minebleach.reiatsutest.core.fx.RukiaFxParams;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.entity.FxAnchorEntity;
import dev.minebleach.reiatsutest.entity.FxAnchorKind;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server side of the Rukia effects (phase 6 steps 6 and 7): the extra {@code effect_event} params of her abilities
 * ({@link RukiaFxParams}) and the standing frost field anchor of the bankai (FIELD anchor with
 * {@code p0 = FieldAnchorPolicy.FIELD_RUKIA_BANKAI}, static at the release point, ends with the bankai). Pure rules live in
 * {@code core/fx}; this class only talks to Minecraft.
 */
public final class RukiaServerFx {
	private record Tracked(FxAnchorEntity anchor, UUID owner, int spawnedAt) {
	}

	private static final List<Tracked> TRACKED = new ArrayList<>();

	private RukiaServerFx() {
	}

	/** Registered once from the mod initialiser. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RukiaServerFx::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> TRACKED.clear());
	}

	/** Extends the params of an ability event (aim, size, then the Rukia extras). */
	static float[] params(int effectId, float[] params, CastContext ctx) {
		if (ctx == null) {
			return params;
		}
		return RukiaFxParams.extend(RukiaFxParams.abilityOf(effectId), params, ctx.eye.distanceTo(ctx.aim));
	}

	/** Called for every broadcast effect: creates the standing field of a Rukia bankai. */
	static void onEffect(ServerPlayerEntity caster, int effectId, int seed) {
		if (!FieldAnchorPolicy.spawnsOn(effectId)) {
			return;
		}
		discardOf(caster.getUuid());
		FxAnchorEntity a = FxAnchorEntity.create(caster.getServerWorld());
		a.refreshPositionAndAngles(caster.getX(), caster.getY(), caster.getZ(), caster.getYaw(), 0f);
		a.configure(FxAnchorKind.FIELD, seed, caster.getId(), -1, FieldAnchorPolicy.FIELD_RUKIA_BANKAI, (float) caster.getY(), caster.getYaw(), 0f, 0);
		caster.getServerWorld().spawnEntity(a);
		TRACKED.add(new Tracked(a, caster.getUuid(), caster.getServer().getTicks()));
		ReiatsuTest.LOGGER.info("[fx] rukia bankai field anchor for {} at ({}, {}, {})", caster.getName().getString(), caster.getX(), caster.getY(), caster.getZ());
	}

	private static void discardOf(UUID owner) {
		for (Iterator<Tracked> it = TRACKED.iterator(); it.hasNext();) {
			Tracked t = it.next();
			if (t.owner().equals(owner)) {
				t.anchor().discard();
				it.remove();
			}
		}
	}

	private static void tick(MinecraftServer srv) {
		if (TRACKED.isEmpty()) {
			return;
		}
		int now = srv.getTicks();
		for (Iterator<Tracked> it = TRACKED.iterator(); it.hasNext();) {
			Tracked t = it.next();
			if (t.anchor().isRemoved()) {
				it.remove();
				continue;
			}
			if (!FieldAnchorPolicy.due(now - t.spawnedAt())) {
				continue;
			}
			ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(t.owner());
			StateMachine sm = owner == null ? null : ZanpakutoManager.machine(owner);
			boolean keep = owner != null && sm != null && FieldAnchorPolicy.keep(owner.isAlive(), true, sm.character(), sm.state());
			if (!keep) {
				t.anchor().discard();
				it.remove();
			}
		}
	}
}
