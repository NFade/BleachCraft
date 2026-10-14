package dev.minebleach.reiatsutest.server.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.ArrowSim;
import dev.minebleach.reiatsutest.core.uryu.BindEnd;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Sends the placeholder visuals of Uryu ({@link UryuArrowsS2C}) to the players near them. Server thread only. */
final class UryuNet {
	/** Players within this distance of an arrow or an effect get it. */
	static final double RANGE = 128.0;
	/** Players that got a non empty arrow list last tick (they need one empty list when the arrows are gone). */
	private static final Set<UUID> SEEN = new HashSet<>();

	private UryuNet() {
	}

	static void forget(UUID player) {
		SEEN.remove(player);
	}

	static void clear() {
		SEEN.clear();
	}

	/** The arrows of a world, per receiving player the ones within range. */
	static void sendArrows(ServerWorld world, ArrowSim sim, int tick) {
		List<ArrowSim.Arrow> all = sim.arrows();
		for (ServerPlayerEntity pl : world.getPlayers()) {
			if (!ServerPlayNetworking.canSend(pl, UryuArrowsS2C.ID)) {
				continue;
			}
			int[] ids = new int[Math.min(all.size(), UryuArrowsS2C.MAX_ARROWS)];
			float[] data = new float[ids.length * UryuArrowsS2C.PER_ARROW];
			int n = 0;
			for (ArrowSim.Arrow a : all) {
				if (n >= ids.length) {
					break;
				}
				V3 p = a.pos();
				if (pl.squaredDistanceTo(p.x(), p.y(), p.z()) > RANGE * RANGE) {
					continue;
				}
				V3 v = a.vel();
				int o = n * UryuArrowsS2C.PER_ARROW;
				ids[n] = a.id();
				data[o] = (float) p.x();
				data[o + 1] = (float) p.y();
				data[o + 2] = (float) p.z();
				data[o + 3] = (float) v.x();
				data[o + 4] = (float) v.y();
				data[o + 5] = (float) v.z();
				data[o + 6] = a.focus() + 4 * a.kind().ordinal() + (a.steering() ? 32 : 0);
				n++;
			}
			send(pl, n, ids, data, tick);
		}
	}

	/** After the last arrow of a world is gone: everybody who still sees some gets the empty list. */
	static void sendEmpty(ServerWorld world, int tick) {
		for (ServerPlayerEntity pl : world.getPlayers()) {
			if (SEEN.remove(pl.getUuid()) && ServerPlayNetworking.canSend(pl, UryuArrowsS2C.ID)) {
				ServerPlayNetworking.send(pl, new UryuArrowsS2C(UryuArrowsS2C.ARROWS, tick, new int[0], new float[0]));
			}
		}
	}

	private static void send(ServerPlayerEntity pl, int n, int[] ids, float[] data, int tick) {
		if (n == 0) {
			if (SEEN.remove(pl.getUuid())) {
				ServerPlayNetworking.send(pl, new UryuArrowsS2C(UryuArrowsS2C.ARROWS, tick, new int[0], new float[0]));
			}
			return;
		}
		SEEN.add(pl.getUuid());
		ServerPlayNetworking.send(pl, new UryuArrowsS2C(UryuArrowsS2C.ARROWS, tick, java.util.Arrays.copyOf(ids, n),
				java.util.Arrays.copyOf(data, n * UryuArrowsS2C.PER_ARROW)));
	}

	/**
	 * The Focus stacks of {@code pl} to the owner (HUD pips) and to the players who track him (S-U2: the Focus feedback on his body). The
	 * caller sends only when the count changed. The packet carries the caster id ({@link UryuArrowsS2C#focus}).
	 */
	static void sendFocus(ServerPlayerEntity pl, int stacks, int tick) {
		UryuArrowsS2C payload = UryuArrowsS2C.focus(pl.getId(), stacks, tick);
		Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.tracking(pl));
		viewers.add(pl);
		for (ServerPlayerEntity v : viewers) {
			if (ServerPlayNetworking.canSend(v, UryuArrowsS2C.ID)) {
				ServerPlayNetworking.send(v, payload);
			}
		}
	}

	/** S-U3: the pentagon at {@code c} rooted {@code ids}; the root runs out {@code ticksLeft} ticks after {@code tick}. */
	static void sendBind(ServerWorld world, int tick, int[] ids, V3 c, int ticksLeft) {
		sendTo(world, c.x(), c.y(), c.z(), UryuArrowsS2C.bind(tick, ids, c.x(), c.y(), c.z(), ticksLeft));
	}

	/** S-U3: the root of {@code ids} ended for {@code reason}. */
	static void sendEnd(ServerWorld world, int tick, int[] ids, V3 c, BindEnd reason) {
		sendTo(world, c.x(), c.y(), c.z(), UryuArrowsS2C.end(tick, ids, c.x(), c.y(), c.z(), reason));
	}

	private static void sendTo(ServerWorld world, double x, double y, double z, UryuArrowsS2C payload) {
		for (ServerPlayerEntity pl : world.getPlayers()) {
			if (pl.squaredDistanceTo(x, y, z) <= RANGE * RANGE && ServerPlayNetworking.canSend(pl, UryuArrowsS2C.ID)) {
				ServerPlayNetworking.send(pl, payload);
			}
		}
	}

	/** A hit spark at {@code at} for everybody near. */
	static void sendImpact(ServerWorld world, V3 at, int type, int kind, int tick) {
		sendEffect(world, at.x(), at.y(), at.z(), UryuArrowsS2C.IMPACT, tick, new float[] {(float) at.x(), (float) at.y(), (float) at.z(), type, kind});
	}

	/** An effect packet ({@link UryuArrowsS2C#BURST}, {@link UryuArrowsS2C#LINE}, {@link UryuArrowsS2C#TRAP}) to everybody near {@code at}. */
	static void sendEffect(ServerWorld world, double x, double y, double z, byte kind, int tick, float[] data) {
		UryuArrowsS2C payload = new UryuArrowsS2C(kind, tick, new int[0], data);
		for (ServerPlayerEntity pl : world.getPlayers()) {
			if (pl.squaredDistanceTo(x, y, z) <= RANGE * RANGE && ServerPlayNetworking.canSend(pl, UryuArrowsS2C.ID)) {
				ServerPlayNetworking.send(pl, payload);
			}
		}
	}
}
