package dev.minebleach.reiatsutest.core.gin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who is rooted by a Gin cast and until which tick. A later root never shortens an earlier one. */
public final class RootBook {
	private final Map<UUID, Long> until = new LinkedHashMap<>();

	/** Roots {@code who} for {@code ticks} from {@code now}; returns the tick the root ends at (the later of the old and the new one). */
	public long root(UUID who, long now, int ticks) {
		long end = Math.max(now + Math.max(0, ticks), until.getOrDefault(who, 0L));
		until.put(who, end);
		return end;
	}

	public boolean active(UUID who, long now) {
		Long end = until.get(who);
		return end != null && now < end;
	}

	public long endOf(UUID who) {
		return until.getOrDefault(who, 0L);
	}

	/** Ends the root at once; true when there was one. */
	public boolean release(UUID who) {
		return until.remove(who) != null;
	}

	/** Removes and returns everybody whose root is over at {@code now}. */
	public List<UUID> expired(long now) {
		List<UUID> out = new ArrayList<>();
		for (Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> e = it.next();
			if (now >= e.getValue()) {
				out.add(e.getKey());
				it.remove();
			}
		}
		return out;
	}

	public List<UUID> all() {
		return new ArrayList<>(until.keySet());
	}

	public int size() {
		return until.size();
	}

	public void clear() {
		until.clear();
	}
}
