package dev.minebleach.reiatsutest.core.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * Per victim immunity to ONE ability of ONE caster (r8 item 2, Byakuya attack mode). After a damage tick on a victim from the
 * ability, that victim takes no further damage tick of the SAME ability for {@code intervalTicks} (BalanceConfig
 * {@code byakuyaAttackTickInterval}, 4 ticks = 0.2 s). It is a plain per ability table on purpose: it does not touch vanilla
 * {@code hurtTime} / {@code timeUntilRegen}, so the victim stays open to every other source (other players, mobs, other abilities).
 *
 * <p>One instance lives with one cast (caster and ability are implied); the landing burst phases and the flight sweep of the same
 * cast share it, so they cannot stack their ticks inside the window. Pure Java, no Minecraft types.
 */
public final class AbilityHitGate {
	private final int intervalTicks;
	private final Map<Integer, Long> last = new HashMap<>();

	public AbilityHitGate(int intervalTicks) {
		this.intervalTicks = Math.max(1, intervalTicks);
	}

	public int intervalTicks() {
		return intervalTicks;
	}

	/** True when the victim is out of its immunity window at {@code tick} (a never hit victim is always ready). */
	public boolean ready(int victimId, long tick) {
		Long t = last.get(victimId);
		return t == null || tick - t >= intervalTicks;
	}

	/** Records a damage tick on the victim at {@code tick}: it is immune to this ability until {@code tick + interval}. */
	public void record(int victimId, long tick) {
		last.put(victimId, tick);
	}

	/** {@link #ready} then {@link #record}: true when the tick may land. */
	public boolean tryHit(int victimId, long tick) {
		if (!ready(victimId, tick)) {
			return false;
		}
		record(victimId, tick);
		return true;
	}

	public int victims() {
		return last.size();
	}
}
