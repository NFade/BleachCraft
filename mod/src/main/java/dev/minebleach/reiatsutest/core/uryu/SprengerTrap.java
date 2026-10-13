package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.UUID;

/**
 * One Sprenger pentagon (design/URYU.md section 5): five Seele Schneider on a circle of radius 3 around {@code center}.
 * 0.8 s after the cast the legs of everyone inside are bound, 2.0 s after it the pentagon explodes for 9 HP. A fully focused arrow
 * of the same owner that touches the pentagon detonates it at once for +50 percent. Whoever leaves before the bind is free of it
 * (the glue asks {@link #contains} at the bind tick). The area test uses the circle that the five points lie on.
 */
public final class SprengerTrap {
	public enum Step {
		NONE, BIND, EXPLODE
	}

	private final UUID owner;
	private final V3 center;
	private final int bornTick;
	private boolean bound;
	private boolean finished;
	private boolean early;

	public SprengerTrap(UUID owner, V3 center, int bornTick) {
		this.owner = owner;
		this.center = center;
		this.bornTick = bornTick;
	}

	public UUID owner() {
		return owner;
	}

	public V3 center() {
		return center;
	}

	public int bornTick() {
		return bornTick;
	}

	public boolean bound() {
		return bound;
	}

	public boolean finished() {
		return finished;
	}

	public boolean early() {
		return early;
	}

	/** What happens at server tick {@code now}: the bind first (0.8 s), then the explosion (2.0 s); each is reported once. */
	public Step advance(int now) {
		if (finished) {
			return Step.NONE;
		}
		int age = now - bornTick;
		if (!bound && age >= UryuBalance.SPRENGER_BIND_AT) {
			bound = true;
			return Step.BIND;
		}
		if (age >= UryuBalance.SPRENGER_EXPLODE_AT) {
			finished = true;
			return Step.EXPLODE;
		}
		return Step.NONE;
	}

	/** Detonates now (a focused arrow). False when the pentagon is already gone. */
	public boolean detonateEarly() {
		if (finished) {
			return false;
		}
		finished = true;
		early = true;
		return true;
	}

	/** HP of the explosion: 9, or 13.5 after an early detonation. */
	public float damage() {
		return (float) (UryuBalance.SPRENGER_DAMAGE * (early ? UryuBalance.SPRENGER_EARLY_FACTOR : 1.0));
	}

	/** Only an arrow shot with all 3 stacks detonates. */
	public static boolean detonates(int focusOfArrow) {
		return focusOfArrow >= UryuBalance.FOCUS_FULL;
	}

	/** True when the point is inside the pentagon area: within the radius on the ground plane and in the height band. */
	public boolean contains(double x, double y, double z) {
		double dx = x - center.x();
		double dz = z - center.z();
		double dy = y - center.y();
		return dx * dx + dz * dz <= UryuBalance.SPRENGER_RADIUS * UryuBalance.SPRENGER_RADIUS && dy >= -UryuBalance.SPRENGER_BELOW
				&& dy <= UryuBalance.SPRENGER_HEIGHT;
	}

	/** Does the flight of an arrow from {@code a} to {@code b} (sampled every 0.25 block) touch the pentagon? */
	public boolean touchedBy(V3 a, V3 b) {
		double len = a.distanceTo(b);
		int n = Math.max(1, (int) Math.ceil(len / 0.25));
		for (int i = 0; i <= n; i++) {
			V3 p = a.lerp(b, (double) i / n);
			if (contains(p.x(), p.y(), p.z())) {
				return true;
			}
		}
		return false;
	}

	/** Position of the i-th Seele Schneider (0..4) on the circle; the first one points to -Z. */
	public V3 vertex(int i) {
		double ang = -Math.PI / 2.0 + i * 2.0 * Math.PI / 5.0;
		return new V3(center.x() + Math.cos(ang) * UryuBalance.SPRENGER_RADIUS, center.y(), center.z() + Math.sin(ang) * UryuBalance.SPRENGER_RADIUS);
	}
}
