package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The wing barrage (Z, BANKAI) of design/URYU_PLAN.md (option A): for 6 s the wings fire homing arrows at up to four of the nearest valid
 * enemies within 30 blocks, a distinct target per wing slot, the targets chosen again every 8 ticks, one arrow per target per cycle
 * (0.4 s). Pure rules; the glue finds the candidates, spawns the arrows and does the damage.
 */
public final class WingBarrage {
	private WingBarrage() {
	}

	/** A candidate target: entity id and its aim point. */
	public record Target(int id, V3 pos) {
	}

	/** One arrow of a cycle: from the tip of wing slot {@code slot} at tick {@code fireTick} (from the cast) at {@code target}. */
	public record Shot(int slot, int fireTick, Target target) {
	}

	/** True while the wings are firing (tick from the cast). */
	public static boolean active(int k) {
		return k >= 0 && k < UryuBalance.WING_TICKS;
	}

	/** True at the ticks at which the targets are chosen again (0, 8, 16, ...). */
	public static boolean isCycleStart(int k) {
		return active(k) && k % UryuBalance.WING_CYCLE == 0;
	}

	/**
	 * The shots of the cycle that starts at tick {@code k}: the candidates are already filtered and sorted nearest first; the first
	 * {@link UryuBalance#WING_SLOTS} different ones get one slot each (slot i the i-th nearest), the slots fire staggered by
	 * {@link UryuBalance#WING_STAGGER} ticks.
	 */
	public static List<Shot> cycle(int k, List<Target> nearestFirst) {
		List<Shot> out = new ArrayList<>();
		Set<Integer> used = new HashSet<>();
		for (Target t : nearestFirst) {
			if (out.size() >= UryuBalance.WING_SLOTS) {
				break;
			}
			if (!used.add(t.id())) {
				continue;
			}
			int slot = out.size();
			out.add(new Shot(slot, k + slot * UryuBalance.WING_STAGGER, t));
		}
		return out;
	}

	/**
	 * World position of the tip of a wing slot: the feet position plus the offset of {@link UryuBalance#WING_TIPS} in the body frame
	 * (Minecraft yaw: 0 faces +Z, the right hand side of a player facing +Z is -X).
	 */
	public static V3 tip(int slot, double bodyYawDeg, V3 feet) {
		double[] o = UryuBalance.WING_TIPS[Math.floorMod(slot, UryuBalance.WING_SLOTS)];
		double yaw = Math.toRadians(bodyYawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		// o[0] sideways (right positive), o[1] height, o[2] backwards
		return new V3(feet.x() + rx * o[0] - fx * o[2], feet.y() + o[1], feet.z() + rz * o[0] - fz * o[2]);
	}
}
