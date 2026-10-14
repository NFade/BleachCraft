package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.List;
import java.util.function.Predicate;

/**
 * Target choice of a homing Heilig Pfeil (design/URYU_PLAN.md revision 2): the nearest valid target in front of the arrow, within the
 * radius, players preferred over mobs, never one behind a block. The steering itself (a turn rate limit per tick) is
 * {@link ArrowSim#turnToward}. Pure: the glue supplies the candidates (already filtered by the usual target rules: no caster, no
 * creative or spectating player) and the line of sight test.
 */
public final class Homing {
	private Homing() {
	}

	/** A possible target: entity id, aim point, and whether it is a player. */
	public record Candidate(int id, V3 pos, boolean player) {
	}

	/**
	 * @param turnRad maximal turn per tick in radians (0 = no homing)
	 * @param radius blocks within which a target is looked for
	 * @param coneRad half opening angle in radians around the flight direction in which a NEW target must lie
	 */
	public record Params(double turnRad, double radius, double coneRad) {
		public static Params degrees(double degPerTick, double radius, double coneHalfDeg) {
			return new Params(Math.toRadians(degPerTick), radius, Math.toRadians(coneHalfDeg));
		}

		public boolean active() {
			return turnRad > 0.0 && radius > 0.0;
		}
	}

	/**
	 * The target for this tick, or null. A locked target ({@code lockedId}, -1 for none) is kept while it is still a candidate within
	 * {@code radius * keepFactor} with a free line, unless it is a mob and a player qualifies as a NEW target (players first). A new
	 * target must lie inside the cone and the radius with a free line; the nearest player wins, else the nearest mob.
	 *
	 * @param visible true when no block lies between the arrow and the candidate
	 */
	public static Candidate pick(V3 pos, V3 dir, List<Candidate> candidates, Params p, int lockedId, double keepFactor, Predicate<Candidate> visible) {
		if (!p.active() || candidates.isEmpty()) {
			return null;
		}
		V3 d = dir.normalizedOr(new V3(0, 0, 1));
		Candidate locked = null;
		Candidate bestPlayer = null;
		Candidate bestMob = null;
		double bestPlayerD = Double.MAX_VALUE;
		double bestMobD = Double.MAX_VALUE;
		for (Candidate c : candidates) {
			V3 to = c.pos().sub(pos);
			double dist = to.length();
			if (c.id() == lockedId && dist <= p.radius() * keepFactor && visible.test(c)) {
				locked = c;
			}
			if (dist > p.radius()) {
				continue;
			}
			if (dist > 1.0E-6 && ArrowSim.angleBetween(d, to) > p.coneRad()) {
				continue;
			}
			if (!visible.test(c)) {
				continue;
			}
			if (c.player()) {
				if (dist < bestPlayerD || (dist == bestPlayerD && bestPlayer != null && c.id() < bestPlayer.id())) {
					bestPlayer = c;
					bestPlayerD = dist;
				}
			} else if (dist < bestMobD || (dist == bestMobD && bestMob != null && c.id() < bestMob.id())) {
				bestMob = c;
				bestMobD = dist;
			}
		}
		if (locked != null && (locked.player() || bestPlayer == null)) {
			return locked;
		}
		return bestPlayer != null ? bestPlayer : bestMob;
	}
}
