package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.HashMap;
import java.util.Map;

/**
 * Licht Regen (round 2, bankai H; replaces the Homing Volley): a rain of Reishi arrows on a SQUARE of 9 x 9 blocks (axis aligned to the
 * world grid, centred on the aimed point) for 3.0 s. Every tick {@link UryuBalance#RAIN_PER_TICK} arrows start at random places 14
 * blocks above the ground level of the centre and fall with a small slant; each is a normal {@link ArrowSim} arrow (no homing, stopped
 * by terrain and roofs). One target can take at most {@link UryuBalance#RAIN_TARGET_CAP} HP from one cast.
 *
 * <p>Pure and deterministic: the places depend only on (seed, tick, index). The landing point of every arrow lies inside the square
 * (the start is the landing point moved back along the fall line), so the slant does not shift the area. One instance per cast.
 */
public final class LichtRegen {
	/** One arrow to start: where, with which velocity, and where it would touch the ground level of the centre. */
	public record Drop(V3 start, V3 vel, V3 landing) {
	}

	private final int casterId;
	private final long seed;
	private final V3 centre;
	private final V3 slant;
	private final Map<Integer, Float> taken = new HashMap<>();

	/**
	 * @param casterEntityId entity id of the caster (never takes damage from his own rain)
	 * @param seed the seed of the cast
	 * @param groundCentre the centre of the square at ground level (y = ground level)
	 */
	public LichtRegen(int casterEntityId, long seed, V3 groundCentre) {
		this.casterId = casterEntityId;
		this.seed = seed;
		this.centre = groundCentre;
		double th = unit(seed, -1, 0) * 2.0 * Math.PI;
		this.slant = new V3(Math.cos(th), 0.0, Math.sin(th));
	}

	public V3 centre() {
		return centre;
	}

	/** True while the rain falls: ticks 0 to 59 after the release. */
	public static boolean active(int ticksSinceRelease) {
		return ticksSinceRelease >= 0 && ticksSinceRelease < UryuBalance.RAIN_TICKS;
	}

	/** Arrows that start on this tick (6 while active, else 0). */
	public static int arrowsAt(int ticksSinceRelease) {
		return active(ticksSinceRelease) ? UryuBalance.RAIN_PER_TICK : 0;
	}

	/** All arrows of one cast. */
	public static int totalArrows() {
		return UryuBalance.RAIN_TICKS * UryuBalance.RAIN_PER_TICK;
	}

	/** True when the ground point (x, z) is inside the square of the centre (edges included). */
	public boolean inSquare(double x, double z) {
		return Math.abs(x - centre.x()) <= UryuBalance.RAIN_HALF_WIDTH + 1.0E-9 && Math.abs(z - centre.z()) <= UryuBalance.RAIN_HALF_WIDTH + 1.0E-9;
	}

	/** The i-th arrow of tick {@code tick}: seeded, always the same for the same inputs. */
	public Drop drop(int tick, int index) {
		double hw = UryuBalance.RAIN_HALF_WIDTH;
		double lx = centre.x() + (unit(seed, tick, index * 2) * 2.0 - 1.0) * hw;
		double lz = centre.z() + (unit(seed, tick, index * 2 + 1) * 2.0 - 1.0) * hw;
		V3 landing = new V3(lx, centre.y(), lz);
		V3 dir = new V3(slant.x() * UryuBalance.RAIN_SLANT, -1.0, slant.z() * UryuBalance.RAIN_SLANT).normalizedOr(new V3(0, -1, 0));
		double t = UryuBalance.RAIN_HEIGHT / -dir.y();
		V3 start = landing.sub(dir.scale(t));
		return new Drop(start, dir.scale(UryuBalance.RAIN_SPEED), landing);
	}

	/**
	 * The HP this cast may still deal to {@code targetId} when an arrow wants to deal {@code want}: the arrow damage, cut by the cap
	 * of {@link UryuBalance#RAIN_TARGET_CAP} per target and cast; 0 for the caster. Records what it hands out.
	 */
	public float allow(int targetId, float want) {
		if (targetId == casterId || want <= 0f) {
			return 0f;
		}
		float done = taken.getOrDefault(targetId, 0f);
		float give = Math.max(0f, Math.min(want, UryuBalance.RAIN_TARGET_CAP - done));
		if (give > 0f) {
			taken.put(targetId, done + give);
		}
		return give;
	}

	/** HP this cast has dealt to the target so far. */
	public float dealt(int targetId) {
		return taken.getOrDefault(targetId, 0f);
	}

	/** A uniform number in [0, 1) from (seed, a, b): SplitMix64 of the mixed inputs. */
	private static double unit(long seed, int a, int b) {
		long z = seed + 0x9E3779B97F4A7C15L * (a + 3L) + 0xBF58476D1CE4E5B9L * (b + 7L);
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}
}
