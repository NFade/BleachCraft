package dev.minebleach.reiatsutest.server.gin;

import dev.minebleach.reiatsutest.core.gin.GinGeometry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Snake Hook (design/GIN.md section 4): the blade sticks and pulls the CASTER toward the anchor by server velocity, one step per tick, no
 * teleport. The step comes from {@link GinGeometry#pull}: never closer than {@code stopShort}, never faster than {@code speed}. An anchor is
 * a fixed point (a block) or a living entity that is followed while it moves. Ends on arrival, on the tick limit, when the anchor entity
 * is gone, and on death, logout or a dimension change of the caster.
 */
public final class SelfPull {
	private record Pull(Vec3d fixed, UUID entity, double stopShort, double speed, long endTick, net.minecraft.world.World world) {
	}

	private static final Map<UUID, Pull> PULLS = new LinkedHashMap<>();
	/** Harness / tests: pulls started and pulls that arrived since the server started. */
	private static int started;
	private static int arrived;

	private SelfPull() {
	}

	/** Starts (or replaces) the pull of {@code p}. {@code entity} null = pull toward {@code point}. */
	public static void start(ServerPlayerEntity p, Vec3d point, UUID entity, double stopShort, double speed, int maxTicks) {
		PULLS.put(p.getUuid(), new Pull(point, entity, stopShort, speed, p.getServer().getTicks() + maxTicks, p.getWorld()));
		started++;
	}

	public static boolean active(UUID id) {
		return PULLS.containsKey(id);
	}

	public static void cancel(UUID id) {
		PULLS.remove(id);
	}

	public static void clearAll() {
		PULLS.clear();
	}

	public static int started() {
		return started;
	}

	public static int arrived() {
		return arrived;
	}

	public static void tick(MinecraftServer srv, long now) {
		if (PULLS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Pull>> it = PULLS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Pull> e = it.next();
			Pull pull = e.getValue();
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(e.getKey());
			if (p == null || p.isDead() || p.getWorld() != pull.world() || now >= pull.endTick()) {
				it.remove();
				continue;
			}
			Vec3d anchor = pull.fixed();
			if (pull.entity() != null) {
				Entity target = ((net.minecraft.server.world.ServerWorld) pull.world()).getEntity(pull.entity());
				if (target == null || !target.isAlive()) {
					it.remove();
					continue;
				}
				anchor = target.getBoundingBox().getCenter();
			}
			Vec3d me = p.getPos().add(0, p.getHeight() * 0.5, 0);
			GinGeometry.PullStep step = GinGeometry.pull(me.x, me.y, me.z, anchor.x, anchor.y, anchor.z, pull.stopShort(), pull.speed());
			if (step.done()) {
				p.setVelocity(p.getVelocity().multiply(0.2, 0.2, 0.2));
				p.velocityModified = true;
				p.fallDistance = 0;
				arrived++;
				it.remove();
				continue;
			}
			p.setVelocity(step.vx(), step.vy(), step.vz());
			p.velocityModified = true;
			p.fallDistance = 0;
		}
	}
}
