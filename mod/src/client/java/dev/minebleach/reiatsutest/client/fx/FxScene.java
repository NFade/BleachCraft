package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.entity.FxAnchorEntity;
import dev.minebleach.reiatsutest.entity.FxAnchorKind;
import java.util.Iterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Ties the client side swarm and row states to the tracked anchor entities (VFX_STORYBOARD 1.2 and 9). The effect events create and
 * time the states; the anchors keep them alive (a state whose anchor is not seen for 2 s is dropped) and give late joiners a settled
 * swarm or rows from the tracked fields (kind, seed, startTime, owner, yaw and feet y of the rows).
 */
public final class FxScene {
	/** Dev: false lets harness events run without a server anchor. */
	public static volatile boolean requireAnchor = true;
	public static int swarmAnchors;
	public static int rowsAnchors;
	public static int lateJoins;

	private FxScene() {
	}

	/** Once per frame at WorldRenderEvents.START. */
	public static void frame(MinecraftClient mc) {
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		double now = FxClock.now;
		int swarms = 0;
		int rows = 0;
		for (Entity en : w.getEntities()) {
			if (!(en instanceof FxAnchorEntity a)) {
				continue;
			}
			int owner = a.ownerId();
			if (a.kind() == FxAnchorKind.SWARM) {
				swarms++;
				SwarmFx.State st = SwarmFx.of(owner);
				if (st == null && w.getEntityById(owner) != null && FxClock.now > 0) {
					st = SwarmFx.lateState(owner, a.seed(), w.getEntityById(owner).getPos());
					lateJoins++;
				}
				if (st != null) {
					st.anchorSeen = true;
					st.lastSeen = now;
				}
			} else if (a.kind() == FxAnchorKind.ROWS) {
				rows++;
				RowsFx.State st = RowsFx.of(owner);
				if (st == null) {
					double age = Math.max(0, (w.getTime() - a.startTime()) / 20.0);
					st = RowsFx.lateState(owner, a.seed() * 1L, a.p0(), a.getX(), a.p1() != 0 ? a.p1() : a.getY(), a.getZ(), age, w);
					lateJoins++;
				}
				st.anchorSeen = true;
				st.lastSeen = now;
			}
		}
		swarmAnchors = swarms;
		rowsAnchors = rows;
		if (!requireAnchor) {
			return;
		}
		for (Iterator<SwarmFx.State> it = SwarmFx.states().values().iterator(); it.hasNext();) {
			SwarmFx.State st = it.next();
			if (st.endAt < 0 && now - st.lastSeen > 2.0) {
				it.remove();
			}
		}
		for (Iterator<RowsFx.State> it = RowsFx.states().values().iterator(); it.hasNext();) {
			RowsFx.State st = it.next();
			if (st.endAt < 0 && now - st.lastSeen > 2.0) {
				if (st.vbo != null) {
					st.vbo.close();
				}
				it.remove();
			}
		}
	}

	/** Position helper for effects that need the anchor point of an owner. */
	public static Vec3d ownerFeet(MinecraftClient mc, int ownerId, Vec3d fallback) {
		Entity e = mc.world != null ? mc.world.getEntityById(ownerId) : null;
		return e != null ? e.getPos() : fallback;
	}
}
