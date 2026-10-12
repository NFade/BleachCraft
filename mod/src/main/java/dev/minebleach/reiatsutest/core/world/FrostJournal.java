package dev.minebleach.reiatsutest.core.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rollback journal of the territory freeze. Every frozen cell keeps the block state it had before the freeze (an opaque
 * token, the glue converts it), the dimension and the id of the field that froze it. A cell is journaled once: while it is
 * frozen no other field can take it, so the original state can never be overwritten by a frost block. Restoring takes
 * entries out (per field in batches, outside in, or everything at once), and the whole journal converts to flat arrays
 * ({@link Snapshot}) for the persistent world state, so a crash can always be rolled back at the next start. Pure Java.
 */
public final class FrostJournal {
	/** One frozen cell. */
	public record Entry(String dimension, int x, int y, int z, String previous, int field) {
		public long packed() {
			return pack(x, y, z);
		}
	}

	/** Flat persistent form: parallel arrays over the entries plus two small palettes (dimensions, block state tokens). */
	public record Snapshot(String[] dimensions, String[] palette, long[] positions, int[] dimIndex, int[] paletteIndex) {
		public int size() {
			return positions.length;
		}
	}

	private static final class FieldSet {
		final List<Entry> list = new ArrayList<>();
		boolean sorted = true;
		double cx;
		double cz;
	}

	private final Map<String, Map<Long, Entry>> byDim = new LinkedHashMap<>();
	private final Map<Integer, FieldSet> fields = new HashMap<>();
	private int size;

	// ------------------------------------------------------------------ packing

	/** 26 bits x, 26 bits z, 12 bits y (same layout as a block position long; y in -2048..2047). */
	public static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
	}

	public static int unpackX(long p) {
		return (int) (p >> 38);
	}

	public static int unpackY(long p) {
		return (int) (p << 52 >> 52);
	}

	public static int unpackZ(long p) {
		return (int) (p << 26 >> 38);
	}

	// ------------------------------------------------------------------ queries

	public int size() {
		return size;
	}

	public int size(int field) {
		FieldSet f = fields.get(field);
		return f == null ? 0 : f.list.size();
	}

	public boolean contains(String dimension, int x, int y, int z) {
		Map<Long, Entry> m = byDim.get(dimension);
		return m != null && m.containsKey(pack(x, y, z));
	}

	public Entry get(String dimension, int x, int y, int z) {
		Map<Long, Entry> m = byDim.get(dimension);
		return m == null ? null : m.get(pack(x, y, z));
	}

	// ------------------------------------------------------------------ recording

	/**
	 * Records a cell before it is changed. Returns false (and records nothing) when the cell is already journaled: the original
	 * state is already safe and must not be replaced by a frost block's own state.
	 */
	public boolean add(String dimension, int x, int y, int z, String previous, int field) {
		return add(new Entry(dimension, x, y, z, previous, field));
	}

	public boolean add(Entry e) {
		Map<Long, Entry> m = byDim.computeIfAbsent(e.dimension(), k -> new HashMap<>());
		if (m.putIfAbsent(e.packed(), e) != null) {
			return false;
		}
		FieldSet f = fields.computeIfAbsent(e.field(), k -> new FieldSet());
		f.list.add(e);
		f.sorted = false;
		size++;
		return true;
	}

	/** Centre of a field (blocks), used by {@link #takeBatch} to order restoring by distance. */
	public void setCenter(int field, double cx, double cz) {
		FieldSet f = fields.computeIfAbsent(field, k -> new FieldSet());
		f.cx = cx;
		f.cz = cz;
		f.sorted = false;
	}

	/** Puts entries back (a restore that had to wait, for example for a chunk that was not loaded). */
	public void putBack(Collection<Entry> back) {
		for (Entry e : back) {
			add(e);
		}
	}

	// ------------------------------------------------------------------ taking entries out

	/**
	 * Removes and returns up to {@code budget} entries of one field, outermost first when {@code outsideIn} (the thaw runs
	 * inward), else innermost first.
	 */
	public List<Entry> takeBatch(int field, int budget, boolean outsideIn) {
		return takeBatch(field, budget, outsideIn, 0.0);
	}

	/**
	 * As {@link #takeBatch(int, int, boolean)} but outside in only through entries at least {@code minDistance} blocks from the
	 * field centre (the inward running thaw ring: the glue lowers {@code minDistance} every tick). 0 takes everything.
	 */
	public List<Entry> takeBatch(int field, int budget, boolean outsideIn, double minDistance) {
		FieldSet f = fields.get(field);
		if (f == null || f.list.isEmpty() || budget <= 0) {
			return new ArrayList<>();
		}
		if (!f.sorted) {
			final double cx = f.cx;
			final double cz = f.cz;
			Comparator<Entry> byDistance = Comparator.comparingDouble(e -> (e.x() + 0.5 - cx) * (e.x() + 0.5 - cx) + (e.z() + 0.5 - cz) * (e.z() + 0.5 - cz));
			f.list.sort(byDistance);
			f.sorted = true;
		}
		int n;
		if (outsideIn && minDistance > 0.0) {
			double min2 = minDistance * minDistance;
			n = 0;
			for (int i = f.list.size() - 1; i >= 0 && n < budget; i--) {
				Entry e = f.list.get(i);
				double dx = e.x() + 0.5 - f.cx;
				double dz = e.z() + 0.5 - f.cz;
				if (dx * dx + dz * dz < min2) {
					break;
				}
				n++;
			}
		} else {
			n = Math.min(budget, f.list.size());
		}
		List<Entry> out = new ArrayList<>(n);
		if (n == 0) {
			return out;
		}
		if (outsideIn) {
			int from = f.list.size() - n;
			out.addAll(f.list.subList(from, f.list.size()));
			f.list.subList(from, f.list.size()).clear();
		} else {
			out.addAll(f.list.subList(0, n));
			f.list.subList(0, n).clear();
		}
		for (Entry e : out) {
			Map<Long, Entry> m = byDim.get(e.dimension());
			if (m != null) {
				m.remove(e.packed());
			}
		}
		size -= out.size();
		if (f.list.isEmpty()) {
			fields.remove(field);
		}
		return out;
	}

	/** Removes and returns everything (server stop, startup recovery). */
	public List<Entry> takeAll() {
		List<Entry> out = new ArrayList<>(size);
		for (Map<Long, Entry> m : byDim.values()) {
			out.addAll(m.values());
		}
		byDim.clear();
		fields.clear();
		size = 0;
		return out;
	}

	/** Ids of the fields that still have entries. */
	public List<Integer> fieldIds() {
		return new ArrayList<>(fields.keySet());
	}

	// ------------------------------------------------------------------ persistence

	/** Flat copy for the persistent state. */
	public Snapshot snapshot() {
		List<String> dims = new ArrayList<>();
		Map<String, Integer> palette = new LinkedHashMap<>();
		long[] pos = new long[size];
		int[] di = new int[size];
		int[] pi = new int[size];
		int i = 0;
		for (Map.Entry<String, Map<Long, Entry>> d : byDim.entrySet()) {
			int dIndex = dims.size();
			dims.add(d.getKey());
			for (Entry e : d.getValue().values()) {
				pos[i] = e.packed();
				di[i] = dIndex;
				pi[i] = palette.computeIfAbsent(e.previous(), k -> palette.size());
				i++;
			}
		}
		return new Snapshot(dims.toArray(new String[0]), palette.keySet().toArray(new String[0]), pos, di, pi);
	}

	/**
	 * Rebuilds a journal from a snapshot (all entries get field 0: after a restart nothing owns them any more and they are
	 * rolled back at once). Entries with an out of range index are dropped, never thrown on, so a damaged file cannot stop
	 * the server.
	 */
	public static FrostJournal fromSnapshot(Snapshot s) {
		FrostJournal j = new FrostJournal();
		int n = Math.min(s.positions().length, Math.min(s.dimIndex().length, s.paletteIndex().length));
		for (int i = 0; i < n; i++) {
			int d = s.dimIndex()[i];
			int p = s.paletteIndex()[i];
			if (d < 0 || d >= s.dimensions().length || p < 0 || p >= s.palette().length) {
				continue;
			}
			long pos = s.positions()[i];
			j.add(s.dimensions()[d], unpackX(pos), unpackY(pos), unpackZ(pos), s.palette()[p], 0);
		}
		return j;
	}
}
