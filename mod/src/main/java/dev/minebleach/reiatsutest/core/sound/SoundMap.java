package dev.minebleach.reiatsutest.core.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.minebleach.reiatsutest.core.config.Cfg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * R11 T12: the map "event -> what to play" behind {@code config/reiatsu_sounds.json}. Pure Java (Gson only): the game asks {@link #decide} with an
 * {@link Availability} that tells whether a clip is loaded, and gets back a clip to play, a vanilla fallback, silence or "too soon".
 *
 * <pre>
 * { "configVersion": 1,
 *   "events": { "byakuya.shikai.release_flash": {
 *       "files": ["reiatsu_test:byakuya.shikai.release_flash"],  // sound events whose clips may play (default: the event itself)
 *       "fallback": "",                                          // vanilla id to use instead of the code's layers when no clip is loaded ("" = keep the old sound)
 *       "volume": 1.0, "pitch": [1.0, 1.0], "minIntervalMs": 80, "range": 32, "category": "players" } } }
 * </pre>
 *
 * Reads are tolerant like {@link Cfg}: a missing or malformed key gives the shipped default, a number is clamped, unknown events are ignored, an event that
 * is missing from the file is the default. Without a loaded clip the event plays the vanilla fallback exactly as the code asked before this task.
 */
public final class SoundMap {
	public static final String SECTION = "events";
	public static final int VERSION = 1;

	/** Whether the sound event (a clip set) of this id is loaded by the client. */
	@FunctionalInterface
	public interface Availability {
		boolean loaded(String soundEventId);
	}

	public enum Kind {
		/** Play {@link Decision#soundId()} (a clip of the mod, or any sound event named in {@code files}). */
		FILE,
		/** Play the vanilla sound {@link Decision#soundId()} with the volume and pitch the code asked for (volume times {@link Decision#volume()}). */
		FALLBACK,
		/** No clip and no fallback: nothing to play. */
		SILENT,
		/** A clip is loaded but the same event played less than {@code minIntervalMs} ago. */
		LIMITED
	}

	/**
	 * @param volume  FILE: the volume of the clip; FALLBACK: the multiplier of the volume the code asked for
	 * @param pitch   FILE: the pitch of the clip; FALLBACK and others: NaN (the caller keeps its own pitch)
	 * @param range   FILE: audible range in blocks (0 = no limit)
	 */
	public record Decision(Kind kind, String soundId, double volume, double pitch, String category, double range) {
		static final Decision SILENT = new Decision(Kind.SILENT, "", 0.0, Double.NaN, SoundCatalog.DEFAULT_CATEGORY, 0.0);
		static final Decision LIMITED = new Decision(Kind.LIMITED, "", 0.0, Double.NaN, SoundCatalog.DEFAULT_CATEGORY, 0.0);

		public boolean plays() {
			return kind == Kind.FILE || kind == Kind.FALLBACK;
		}
	}

	private final Map<String, SoundEntry> entries;
	private final Map<String, Long> lastPlayedMs = new HashMap<>();
	private final Map<String, String> lastVariant = new HashMap<>();

	public SoundMap(Map<String, SoundEntry> entries) {
		this.entries = new LinkedHashMap<>(entries);
	}

	/** The shipped defaults (no config file). */
	public static SoundMap defaults() {
		return new SoundMap(SoundCatalog.defaults());
	}

	public SoundEntry entry(String id) {
		return entries.get(id);
	}

	public Map<String, SoundEntry> entries() {
		return java.util.Collections.unmodifiableMap(entries);
	}

	// ------------------------------------------------------------------------------------------ decision

	/**
	 * What to play for {@code event}.
	 *
	 * @param callFallback the vanilla sound id the code plays for this moment ({@code null} or empty: none, the event is silent without a clip)
	 * @param av           which sound events have a loaded clip
	 * @param nowMs        a monotonic clock in milliseconds
	 */
	public synchronized Decision decide(String event, String callFallback, Availability av, long nowMs, Random rnd) {
		String call = callFallback == null ? "" : callFallback;
		SoundEntry e = entries.get(event);
		if (e == null) { // not in the catalog: behave as the code did before this layer existed
			return call.isEmpty() ? Decision.SILENT : new Decision(Kind.FALLBACK, call, 1.0, Double.NaN, SoundCatalog.DEFAULT_CATEGORY, 0.0);
		}
		List<String> loaded = new ArrayList<>(e.files().size());
		for (String f : e.files()) {
			if (av.loaded(f)) {
				loaded.add(f);
			}
		}
		if (!loaded.isEmpty()) {
			Long last = lastPlayedMs.get(event);
			if (e.minIntervalMs() > 0 && last != null && nowMs >= last && nowMs - last < e.minIntervalMs()) {
				return Decision.LIMITED;
			}
			lastPlayedMs.put(event, nowMs);
			String pick = pickVariant(loaded, lastVariant.get(event), rnd);
			lastVariant.put(event, pick);
			double pitch = e.pitchMin() + (e.pitchMax() - e.pitchMin()) * rnd.nextDouble();
			return new Decision(Kind.FILE, pick, e.volume(), pitch, e.category(), e.range());
		}
		String fb = !e.fallback().isEmpty() ? e.fallback() : call;
		if (fb.isEmpty()) {
			return Decision.SILENT;
		}
		return new Decision(Kind.FALLBACK, fb, e.volume(), Double.NaN, e.category(), 0.0);
	}

	/** One of the loaded variants, never the one played last time when there is another. */
	public static String pickVariant(List<String> loaded, String last, Random rnd) {
		if (loaded.size() == 1) {
			return loaded.get(0);
		}
		List<String> pool = new ArrayList<>(loaded);
		if (last != null && pool.size() > 1) {
			pool.remove(last);
		}
		return pool.get(rnd.nextInt(pool.size()));
	}

	// ------------------------------------------------------------------------------------------ JSON

	/** Reads the config root; missing parts give the defaults of {@link SoundCatalog}. */
	public static SoundMap fromJson(JsonObject root) {
		JsonObject events = Cfg.obj(root, SECTION);
		Map<String, SoundEntry> out = new LinkedHashMap<>();
		for (Map.Entry<String, SoundEntry> d : SoundCatalog.defaults().entrySet()) {
			out.put(d.getKey(), readEntry(Cfg.obj(events, d.getKey()), d.getValue()));
		}
		return new SoundMap(out);
	}

	private static SoundEntry readEntry(JsonObject o, SoundEntry def) {
		List<String> files = def.files();
		JsonElement fe = o.get("files");
		if (fe instanceof JsonArray a) {
			List<String> raw = new ArrayList<>();
			for (JsonElement el : a) {
				if (el instanceof JsonPrimitive p && p.isString()) {
					raw.add(p.getAsString());
				}
			}
			files = SoundEntry.cleanFiles(raw); // an empty list is allowed: no clip, play the fallback
		} else if (fe instanceof JsonPrimitive p && p.isString()) {
			files = SoundEntry.cleanFiles(List.of(p.getAsString()));
		}
		String fallback = def.fallback();
		if (o.get("fallback") instanceof JsonPrimitive p && p.isString()) {
			String f = p.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
			fallback = f.isEmpty() || SoundEntry.validSoundId(f) ? f : def.fallback();
		}
		double pMin = def.pitchMin();
		double pMax = def.pitchMax();
		JsonElement pe = o.get("pitch");
		if (pe instanceof JsonPrimitive p && p.isNumber()) {
			double v = Cfg.num(o, "pitch", def.pitchMin(), SoundEntry.MIN_PITCH, SoundEntry.MAX_PITCH);
			pMin = v;
			pMax = v;
		} else if (pe instanceof JsonArray a && a.size() >= 2 && a.get(0) instanceof JsonPrimitive p0 && p0.isNumber() && a.get(1) instanceof JsonPrimitive p1
				&& p1.isNumber()) {
			double lo = p0.getAsDouble();
			double hi = p1.getAsDouble();
			if (!Double.isNaN(lo) && !Double.isInfinite(lo) && !Double.isNaN(hi) && !Double.isInfinite(hi)) {
				pMin = lo;
				pMax = hi;
			}
		}
		String category = def.category();
		if (o.get("category") instanceof JsonPrimitive p && p.isString()) {
			category = SoundEntry.normalizeCategory(p.getAsString());
		}
		return new SoundEntry(def.id(), files, fallback, Cfg.num(o, "volume", def.volume(), 0.0, SoundEntry.MAX_VOLUME), pMin, pMax,
				Cfg.integer(o, "minIntervalMs", def.minIntervalMs(), 0, SoundEntry.MAX_INTERVAL_MS), Cfg.num(o, "range", def.range(), SoundEntry.MIN_RANGE,
						SoundEntry.MAX_RANGE),
				category);
	}

	/** The whole config as JSON (every event of the catalog with its current values). */
	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("configVersion", VERSION);
		JsonObject events = new JsonObject();
		for (SoundEntry e : entries.values()) {
			JsonObject o = new JsonObject();
			JsonArray files = new JsonArray();
			for (String f : e.files()) {
				files.add(f);
			}
			o.add("files", files);
			o.addProperty("fallback", e.fallback());
			o.addProperty("volume", e.volume());
			JsonArray pitch = new JsonArray();
			pitch.add(e.pitchMin());
			pitch.add(e.pitchMax());
			o.add("pitch", pitch);
			o.addProperty("minIntervalMs", e.minIntervalMs());
			o.addProperty("range", e.range());
			o.addProperty("category", e.category());
			events.add(e.id(), o);
		}
		root.add(SECTION, events);
		return root;
	}
}
