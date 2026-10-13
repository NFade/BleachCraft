package dev.minebleach.reiatsutest.core.gin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The slivers of the Kamishini no Yari that are alive right now (design/GIN.md sections 4 and 5): target uuid -> owner uuid and the tick
 * at which the sliver is gone by itself. Pure bookkeeping on ticks; the server glue asks the world (milk, death, distance) and calls the
 * {@code cleanse} methods with the reason. One sliver per target (a newer one of another owner replaces it), at most one per owner is
 * expected but not enforced here.
 */
public final class SliverBook {
	/** Why a sliver ended. DISSOLVED is the intended end (the recast), the others are the cleanse list of the design. */
	public enum Reason {
		MILK, TARGET_DEATH, TIMER, OUT_OF_RANGE, OWNER_DEATH, OWNER_LEFT_BANKAI, DISSOLVED, REPLACED,
		/** The recast channel was interrupted and the sliver was spent by it (the interrupt rule, not a cleanse of the list). */
		INTERRUPTED
	}

	public record Sliver(UUID target, UUID owner, long expiry) {
	}

	public record Removal(Sliver sliver, Reason reason) {
	}

	private final Map<UUID, Sliver> byTarget = new LinkedHashMap<>();

	/** Puts a sliver on {@code target} until {@code expiry}; returns the sliver it replaced (another owner's or the old one), or null. */
	public Sliver place(UUID target, UUID owner, long expiry) {
		return byTarget.put(target, new Sliver(target, owner, expiry));
	}

	public Sliver get(UUID target) {
		return byTarget.get(target);
	}

	public boolean has(UUID target) {
		return byTarget.containsKey(target);
	}

	/** True when {@code target} carries a sliver of {@code owner} that has not run out at {@code now}. */
	public boolean liveOf(UUID target, UUID owner, long now) {
		Sliver s = byTarget.get(target);
		return s != null && s.owner().equals(owner) && now < s.expiry();
	}

	/** The sliver of {@code owner}, or null. */
	public Sliver ofOwner(UUID owner) {
		for (Sliver s : byTarget.values()) {
			if (s.owner().equals(owner)) {
				return s;
			}
		}
		return null;
	}

	/** Resets the timer of a sliver of {@code owner} on {@code target} to {@code ticks} from {@code now} (Renjin). False when there is none. */
	public boolean refresh(UUID target, UUID owner, long now, int ticks) {
		Sliver s = byTarget.get(target);
		if (s == null || !s.owner().equals(owner) || now >= s.expiry()) {
			return false;
		}
		byTarget.put(target, new Sliver(target, owner, now + ticks));
		return true;
	}

	/** Keeps the sliver alive at least until {@code expiry} (the recast channel must not lose it to the timer). */
	public void extendTo(UUID target, long expiry) {
		Sliver s = byTarget.get(target);
		if (s != null && s.expiry() < expiry) {
			byTarget.put(target, new Sliver(target, s.owner(), expiry));
		}
	}

	/** Removes every sliver whose time is up at {@code now} (reason TIMER). */
	public List<Removal> expire(long now) {
		List<Removal> out = new ArrayList<>();
		for (Iterator<Sliver> it = byTarget.values().iterator(); it.hasNext();) {
			Sliver s = it.next();
			if (now >= s.expiry()) {
				it.remove();
				out.add(new Removal(s, Reason.TIMER));
			}
		}
		return out;
	}

	/** Removes the sliver on {@code target}; null when there is none. */
	public Removal cleanse(UUID target, Reason reason) {
		Sliver s = byTarget.remove(target);
		return s == null ? null : new Removal(s, reason);
	}

	/** Removes every sliver of {@code owner} (Gin died, logged out or left BANKAI). */
	public List<Removal> cleanseOwner(UUID owner, Reason reason) {
		List<Removal> out = new ArrayList<>();
		for (Iterator<Sliver> it = byTarget.values().iterator(); it.hasNext();) {
			Sliver s = it.next();
			if (s.owner().equals(owner)) {
				it.remove();
				out.add(new Removal(s, reason));
			}
		}
		return out;
	}

	public List<Sliver> all() {
		return new ArrayList<>(byTarget.values());
	}

	public int size() {
		return byTarget.size();
	}

	public void clear() {
		byTarget.clear();
	}
}
