package dev.minebleach.reiatsutest.core.state;

/**
 * The passive frost territory of the Rukia bankai (mech2 task 4): while the bankai lasts, every valid enemy (players when player hits are
 * allowed, hostile mobs; never the caster, allies or pets) inside the territory gets a short Slowness that is renewed every
 * {@code refreshTicks}, and every {@code periodTicks} takes {@code tickDamage} of armor-ignoring cold damage. Pure data and timing rules;
 * the server glue is {@code server/BankaiField}. The radius is the frozen-territory radius of the frost config
 * ({@code frost.radius}, 30): the territory is the place the bankai froze, centred on her feet at the release.
 *
 * <p>Config names: {@code bankai.field.slowAmplifier}, {@code bankai.field.tickDamage}, {@code bankai.field.periodTicks}.
 *
 * @param slowAmplifier  Slowness amplifier (0 = Slowness I)
 * @param slowTicks      duration of one Slowness application (short: it runs out on its own a moment after the target leaves)
 * @param refreshTicks   how often the Slowness is renewed on everything inside
 * @param tickDamage     HP of one damage pulse (2.0 = exactly one heart, the damage type bypasses armor)
 * @param periodTicks    ticks between two damage pulses (60 = 3 s)
 * @param maxTargets     cap of the targets handled per pulse
 */
public record BankaiFieldSpec(int slowAmplifier, int slowTicks, int refreshTicks, float tickDamage, int periodTicks, int maxTargets) {
	public BankaiFieldSpec {
		if (slowAmplifier < 0 || slowTicks < 1 || refreshTicks < 1 || tickDamage < 0.0F || periodTicks < 1 || maxTargets < 1) {
			throw new IllegalArgumentException("bad bankai field spec");
		}
	}

	/** Slowness I, 1.5 s per application renewed every second, one heart (2.0 HP, armor ignored) every 3 s. */
	public static BankaiFieldSpec defaults() {
		return new BankaiFieldSpec(0, 30, 20, 2.0F, 60, 64);
	}

	/** True when the Slowness is renewed at this age of the territory (ticks since the bankai began); the first renewal is at age 0. */
	public boolean slowDue(long ageTicks) {
		return ageTicks >= 0 && ageTicks % refreshTicks == 0;
	}

	/** True when a damage pulse falls on this age; the first pulse is one full period after the bankai began (never at age 0). */
	public boolean damageDue(long ageTicks) {
		return ageTicks > 0 && ageTicks % periodTicks == 0;
	}

	/**
	 * Who the territory touches, on top of the usual target filter ({@code HitRules.refusal}: never the caster, the dead, spectators, immune
	 * types, own pets, players while player hits or pvp are off): hostile mobs and players; no animals, villagers or other passive
	 * creatures; never an ally (same scoreboard team); never a player who cannot be hurt (Creative, invulnerable abilities).
	 */
	public static boolean eligible(boolean isPlayer, boolean isMonster, boolean ally, boolean playerInvulnerable) {
		if (ally) {
			return false;
		}
		if (isPlayer) {
			return !playerInvulnerable;
		}
		return isMonster;
	}

	/**
	 * Radius of the territory {@code ageTicks} after the bankai began: the ring runs outward at {@code waveSpeed} blocks per second to the
	 * full {@code radius} (so an enemy is touched only on ground that is already frozen); a disabled frost wave means the full radius at once.
	 */
	public static double radiusAt(long ageTicks, double radius, double waveSpeed, boolean wave) {
		if (!wave) {
			return radius;
		}
		return Math.min(radius, Math.max(0, ageTicks) * waveSpeed / 20.0);
	}

	/** Damage pulses a target standing inside for {@code ticks} ticks takes. */
	public long pulsesIn(long ticks) {
		return ticks <= 0 ? 0 : ticks / periodTicks;
	}
}
