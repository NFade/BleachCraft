package dev.minebleach.reiatsutest.core.kido;

/**
 * Numbers of one spell. Units: ticks (20 per second), tenths of a reiatsu point (the bar is 1000 = 100.0), blocks, hit
 * points. A number that a spell does not use is 0.
 *
 * @param range reach in blocks (beam length, flight distance, grab distance)
 * @param damage hit points at the centre of the hit (0 for a binding)
 * @param radius burst radius in blocks (0 = single target)
 * @param statusTicks length of the binding (slowness to a standstill) in ticks
 * @param fireTicks how long a burst sets the targets on fire, in ticks
 * @param speed flight speed in blocks per tick (0 = instant)
 * @param width half width of the hit test around the line of flight, in blocks
 */
public record KidoSpec(KidoId id, boolean enabled, int costTenths, int cooldownTicks, double range, double damage, double radius,
		int statusTicks, int fireTicks, double speed, double width) {

	public KidoSpec withCost(int tenths) {
		return new KidoSpec(id, enabled, tenths, cooldownTicks, range, damage, radius, statusTicks, fireTicks, speed, width);
	}

	public KidoSpec withCooldown(int ticks) {
		return new KidoSpec(id, enabled, costTenths, ticks, range, damage, radius, statusTicks, fireTicks, speed, width);
	}

	public KidoSpec withEnabled(boolean on) {
		return new KidoSpec(id, on, costTenths, cooldownTicks, range, damage, radius, statusTicks, fireTicks, speed, width);
	}

	public int effectId() {
		return id.effectId;
	}
}
