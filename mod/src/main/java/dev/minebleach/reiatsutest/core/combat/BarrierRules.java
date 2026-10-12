package dev.minebleach.reiatsutest.core.combat;

import java.util.Set;

/**
 * Pure rules of the Byakuya shikai barrier (mech2 task 1): which control effects it keeps off the caster, and the damage cut.
 * No Minecraft types: the mixins ask {@code ZanpakutoManager} whether a barrier stands and pass the effect id path here.
 *
 * <p>While the barrier stands the caster takes {@code reduction} less damage (the damage cut itself lives in
 * {@code StateMachine.absorbBarrier}) and nothing that takes control away lands on him: slowness, mining fatigue, weakness,
 * nausea, blindness, darkness and levitation (vanilla and our abilities add them through the same call), freezing ticks
 * (Rukia ice, powder snow) and knockback (our Scatter / Hakuteiken pushes and every vanilla hit). Poison, wither, hunger and
 * the like are damage, not control, and stay.
 */
public final class BarrierRules {
	private BarrierRules() {
	}

	/** Effect id paths (namespace {@code minecraft}) that count as taking control away. */
	public static final Set<String> CONTROL_EFFECTS = Set.of(
			"slowness", "mining_fatigue", "weakness", "nausea", "blindness", "darkness", "levitation");

	/** Damage left after the barrier cut, in the unit of {@code damage} (percent 0..100). */
	public static float reduce(float damage, int reductionPercent) {
		int pct = Math.max(0, Math.min(100, reductionPercent));
		return damage * (100 - pct) / 100.0F;
	}

	/** True when the barrier must refuse this status effect. {@code effectPath} is the effect id without the namespace. */
	public static boolean blocksEffect(boolean controlImmune, String effectPath) {
		return controlImmune && effectPath != null && CONTROL_EFFECTS.contains(effectPath);
	}

	/**
	 * True when a change of the freezing ticks must be ignored: only increases are refused, so the counter can still drain
	 * (the ice that was on the target before the barrier melts away as usual).
	 */
	public static boolean blocksFrozenTicks(boolean controlImmune, int current, int requested) {
		return controlImmune && requested > current;
	}

	/** True when knockback must be ignored (every source: our abilities and vanilla hits). */
	public static boolean blocksKnockback(boolean controlImmune) {
		return controlImmune;
	}
}
