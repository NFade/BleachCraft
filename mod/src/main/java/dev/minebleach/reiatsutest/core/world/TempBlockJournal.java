package dev.minebleach.reiatsutest.core.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure model of the rollback journal for temporary blocks (STATE_MACHINE section 4: no permanent world change). Every
 * placed position keeps the state it had before the first placement; restoring writes that state back. Block states
 * are opaque tokens here (the glue converts them), so nothing in this class knows about Minecraft.
 */
public final class TempBlockJournal {
	/** One temporary block. {@code restoreAtTick} is a server tick; owner may be null for unowned entries. */
	public record Entry(UUID owner, String dimension, int x, int y, int z, String previous, String placed,
			long restoreAtTick) {
		String key() {
			return dimension + "|" + x + "|" + y + "|" + z;
		}
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public int size() {
		return entries.size();
	}

	public int count(UUID owner) {
		int n = 0;
		for (Entry e : entries.values()) {
			if (e.owner != null && e.owner.equals(owner)) {
				n++;
			}
		}
		return n;
	}

	/** True when {@code owner} may place one more block under {@code cap} alive blocks (re-using a known cell is free). */
	public boolean canAdd(UUID owner, String dimension, int x, int y, int z, int cap) {
		return entries.containsKey(new Entry(owner, dimension, x, y, z, "", "", 0).key()) || count(owner) < cap;
	}

	/**
	 * Records a placement. When the cell is already journaled, the ORIGINAL previous state is kept and only the
	 * placed token, owner and restore time are updated, so repeated placements can never leave a changed world behind.
	 */
	public void add(Entry e) {
		Entry old = entries.get(e.key());
		if (old != null) {
			e = new Entry(e.owner, e.dimension, e.x, e.y, e.z, old.previous, e.placed, e.restoreAtTick);
		}
		entries.put(e.key(), e);
	}

	public boolean contains(String dimension, int x, int y, int z) {
		return entries.containsKey(new Entry(null, dimension, x, y, z, "", "", 0).key());
	}

	/** Removes and returns the entries due at {@code nowTick}. */
	public List<Entry> takeDue(long nowTick) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries.values()) {
			if (e.restoreAtTick <= nowTick) {
				out.add(e);
			}
		}
		for (Entry e : out) {
			entries.remove(e.key());
		}
		return out;
	}

	/** Removes and returns every entry of one owner. */
	public List<Entry> takeAll(UUID owner) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries.values()) {
			if (e.owner != null && e.owner.equals(owner)) {
				out.add(e);
			}
		}
		for (Entry e : out) {
			entries.remove(e.key());
		}
		return out;
	}

	/** Removes and returns everything (server stop, startup replay). */
	public List<Entry> takeAll() {
		List<Entry> out = new ArrayList<>(entries.values());
		entries.clear();
		return out;
	}

	/** Puts entries back (used when a chunk is not loaded and the restore must be retried). */
	public void putBack(Collection<Entry> back) {
		for (Entry e : back) {
			entries.put(e.key(), e);
		}
	}

	public List<Entry> snapshot() {
		return new ArrayList<>(entries.values());
	}
}
