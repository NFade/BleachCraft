package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.AnchorPlan;
import dev.minebleach.reiatsutest.entity.FxAnchorEntity;
import dev.minebleach.reiatsutest.entity.FxAnchorKind;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Server side of the effect anchors (VFX_STORYBOARD 1.2 and S1 to S3): executes the {@link AnchorPlan} commands with real
 * {@link FxAnchorEntity} instances. One SWARM per Byakuya player (follows the owner), one ROWS (static at the release point:
 * {@code p0} = flat look yaw in degrees, {@code p1} = feet y, S2). Vanilla entity tracking carries them to the viewers, so late
 * joiners see the swarm and the rows; the client derives the visuals from the tracked fields.
 */
final class AnchorManager {
	private record Anchors(UUID swarm, UUID rows) {
	}

	private static final Map<UUID, Anchors> BY_OWNER = new HashMap<>();
	/** Safety net: rows vanish by themselves after this many ticks (a bankai lasts 900 ticks at most). */
	private static final int ROWS_LIFE = 20 * 70;

	private AnchorManager() {
	}

	/** Called next to {@code ServerFx.effect} for every effect event of a player. */
	static void onEffect(ServerPlayerEntity p, int effectId, int seed) {
		for (AnchorPlan.Command c : AnchorPlan.forEffect(effectId)) {
			switch (c.op()) {
				case END_SWARM -> end(p, true);
				case END_ROWS -> end(p, false);
				case SPAWN_SWARM -> spawn(p, FxAnchorKind.SWARM, seed);
				case SPAWN_ROWS -> spawn(p, FxAnchorKind.ROWS, seed);
				case ROWS_PHASE -> {
					Anchors a = BY_OWNER.get(p.getUuid());
					if (a != null && a.rows() != null && find(p.getServer(), a.rows()) instanceof FxAnchorEntity e) {
						e.setPhase(c.phaseKind());
					}
				}
			}
		}
	}

	/** Death, logout, dimension change, seal without an event: everything of that owner ends. */
	static void reset(ServerPlayerEntity p) {
		for (AnchorPlan.Command c : AnchorPlan.forReset()) {
			end(p, c.op() == AnchorPlan.Op.END_SWARM);
		}
		BY_OWNER.remove(p.getUuid());
	}

	static void clearAll() {
		BY_OWNER.clear();
	}

	private static void spawn(ServerPlayerEntity p, byte kind, int seed) {
		ServerWorld world = p.getServerWorld();
		FxAnchorEntity e = FxAnchorEntity.create(world);
		Vec3d feet = p.getPos();
		Vec3d look = p.getRotationVec(1.0F);
		if (kind == FxAnchorKind.ROWS) {
			e.configure(kind, seed, p.getId(), -1, AnchorPlan.flatYawDeg(look.x, look.z), (float) feet.y, 0f, 0f, ROWS_LIFE);
		} else {
			e.configure(kind, seed, p.getId(), -1, 0f, 0f, 0f, 0f, 0);
		}
		e.refreshPositionAndAngles(feet.x, feet.y, feet.z, 0f, 0f);
		world.spawnEntity(e);
		Anchors old = BY_OWNER.get(p.getUuid());
		UUID swarm = kind == FxAnchorKind.SWARM ? e.getUuid() : old == null ? null : old.swarm();
		UUID rows = kind == FxAnchorKind.ROWS ? e.getUuid() : old == null ? null : old.rows();
		BY_OWNER.put(p.getUuid(), new Anchors(swarm, rows));
		ReiatsuTest.LOGGER.info("[anchor] {} spawned for {} (seed {})", kind == FxAnchorKind.ROWS ? "ROWS" : "SWARM", p.getName().getString(), seed);
	}

	private static void end(ServerPlayerEntity p, boolean swarm) {
		Anchors a = BY_OWNER.get(p.getUuid());
		if (a == null) {
			return;
		}
		UUID id = swarm ? a.swarm() : a.rows();
		if (id != null) {
			Entity e = find(p.getServer(), id);
			if (e != null) {
				e.discard();
			}
		}
		BY_OWNER.put(p.getUuid(), new Anchors(swarm ? null : a.swarm(), swarm ? a.rows() : null));
	}

	private static Entity find(MinecraftServer srv, UUID id) {
		if (srv == null) {
			return null;
		}
		for (ServerWorld w : srv.getWorlds()) {
			Entity e = w.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	/** Number of anchors alive for a player (harness checks). */
	static int count(ServerPlayerEntity p) {
		Anchors a = BY_OWNER.get(p.getUuid());
		if (a == null) {
			return 0;
		}
		return (a.swarm() != null && find(p.getServer(), a.swarm()) != null ? 1 : 0) + (a.rows() != null && find(p.getServer(), a.rows()) != null ? 1 : 0);
	}
}
