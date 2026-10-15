package dev.minebleach.reiatsutest.core.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * R11 T12: one row of {@code config/reiatsu_sounds.json}, the settings of one sound event.
 *
 * @param id            the event id ({@link SoundIds})
 * @param files         the sound events whose clips may play for this event (default: the event itself); the variants that are not loaded are skipped, one of
 *                      the loaded ones is picked, never the same twice in a row
 * @param fallback      vanilla sound id that replaces the layers the code plays when no clip is loaded; empty = the code's own vanilla layers (the old sound)
 * @param volume        multiplier of the volume of a clip (of the fallback too), 0..4
 * @param pitchMin      lowest pitch of a clip, 0.5..2 (random between min and max; the fallback keeps the pitch the code asks for)
 * @param pitchMax      highest pitch of a clip
 * @param minIntervalMs the same event does not play a clip again within this time (ms)
 * @param range         audible range of a clip in blocks, also the radius in which the server sends the event
 * @param category      sound category (the volume slider of the game that scales it)
 */
public record SoundEntry(String id, List<String> files, String fallback, double volume, double pitchMin, double pitchMax, int minIntervalMs, double range,
		String category) {
	public static final double MAX_VOLUME = 4.0;
	public static final double MIN_PITCH = 0.5;
	public static final double MAX_PITCH = 2.0;
	public static final int MAX_INTERVAL_MS = 60_000;
	public static final double MIN_RANGE = 4.0;
	public static final double MAX_RANGE = 256.0;
	public static final int MAX_FILES = 16;
	public static final Set<String> CATEGORIES = Set.of("master", "music", "records", "weather", "blocks", "hostile", "neutral", "players", "ambient", "voice");

	public SoundEntry {
		files = List.copyOf(files);
		fallback = fallback == null ? "" : fallback;
		volume = clamp(volume, 0.0, MAX_VOLUME);
		pitchMin = clamp(pitchMin, MIN_PITCH, MAX_PITCH);
		pitchMax = clamp(pitchMax, MIN_PITCH, MAX_PITCH);
		if (pitchMax < pitchMin) {
			double t = pitchMin;
			pitchMin = pitchMax;
			pitchMax = t;
		}
		minIntervalMs = Math.max(0, Math.min(MAX_INTERVAL_MS, minIntervalMs));
		range = clamp(range, MIN_RANGE, MAX_RANGE);
		category = normalizeCategory(category);
	}

	private static double clamp(double v, double lo, double hi) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			return lo;
		}
		return Math.max(lo, Math.min(hi, v));
	}

	/** Lower case name of a category; "player" and unknown names become the default {@code players}. */
	public static String normalizeCategory(String c) {
		if (c == null) {
			return SoundCatalog.DEFAULT_CATEGORY;
		}
		String n = c.trim().toLowerCase(Locale.ROOT);
		if (n.equals("player")) {
			n = "players";
		} else if (n.equals("block")) {
			n = "blocks";
		} else if (n.equals("record")) {
			n = "records";
		}
		return CATEGORIES.contains(n) ? n : SoundCatalog.DEFAULT_CATEGORY;
	}

	/** A sound id as the config may hold it: {@code [a-z0-9_.-/]} with an optional {@code namespace:}; at most 128 characters. */
	public static boolean validSoundId(String s) {
		if (s == null || s.isEmpty() || s.length() > 128) {
			return false;
		}
		int colons = 0;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == ':') {
				colons++;
				continue;
			}
			boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.' || ch == '/';
			if (!ok) {
				return false;
			}
		}
		return colons <= 1 && !s.startsWith(":") && !s.endsWith(":");
	}

	/** {@code path} becomes {@code reiatsu_test:path}; an id that already has a namespace is kept. */
	public static String withNamespace(String s) {
		return s.indexOf(':') >= 0 ? s : SoundIds.NAMESPACE + ":" + s;
	}

	/** Keeps the valid ids (with the namespace added), drops the rest and the duplicates, at most {@link #MAX_FILES}. */
	public static List<String> cleanFiles(List<String> raw) {
		List<String> out = new ArrayList<>();
		for (String s : raw) {
			if (s == null) {
				continue;
			}
			String t = s.trim().toLowerCase(Locale.ROOT);
			if (!validSoundId(t)) {
				continue;
			}
			t = withNamespace(t);
			if (!out.contains(t) && out.size() < MAX_FILES) {
				out.add(t);
			}
		}
		return out;
	}
}
