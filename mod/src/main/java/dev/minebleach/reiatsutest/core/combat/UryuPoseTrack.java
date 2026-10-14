package dev.minebleach.reiatsutest.core.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One Uryu pose animation read from the emote JSON that {@code tools/bbmodel_to_playeranim.py} writes (assets/reiatsu_test/player_animations/uryu_*.json,
 * authored by {@code tools/gen_uryu_poses.py}). Pure data and maths, no playerAnimator: the animation is sampled by the draw progress or by the time since an
 * ability started ({@link UryuBowFlow}), which a stock playerAnimator player cannot do (the draw is held for as long as the key is held).
 *
 * <p>Values are in GAME axes, exactly as stored (radians, the vanilla part values): {@code pitch, yaw, roll} of a part, and for the positions the absolute
 * part pivot in pixels ({@code x, y, z}; {@link #REST} is the vanilla pivot, a position is used as a DELTA from it). The head only has {@code pitch}.
 * Interpolation is the one of playerAnimator ({@code easeBeforeKeyframe}): the easing of a key shapes the segment that ENDS at that key; before the
 * first key and after the last the value is held. Axes without keys are not animated ({@link #NOT_KEYED}).
 */
public final class UryuPoseTrack {
	/** An axis without keys. */
	public static final double NOT_KEYED = Double.NaN;
	public static final String[] PARTS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"};
	private static final String[] ROT_AXES = {"pitch", "yaw", "roll"};
	private static final String[] POS_AXES = {"x", "y", "z"};

	/** Vanilla pivot of the parts in game axes (the exporter's REST_GAME), pixels. */
	public static double[] rest(String part) {
		return switch (part) {
			case "rightArm" -> new double[] {-5.0, 2.0, 0.0};
			case "leftArm" -> new double[] {5.0, 2.0, 0.0};
			case "rightLeg" -> new double[] {-1.9, 12.0, 0.0};
			case "leftLeg" -> new double[] {1.9, 12.0, 0.0};
			default -> new double[3];
		};
	}

	private record Key(int tick, double value, String easing) {
	}

	private final String name;
	private final int endTick;
	private final int stopTick;
	/** "part.axis" to the keys, sorted by tick. */
	private final Map<String, List<Key>> tracks;

	private UryuPoseTrack(String name, int endTick, int stopTick, Map<String, List<Key>> tracks) {
		this.name = name;
		this.endTick = endTick;
		this.stopTick = stopTick;
		this.tracks = tracks;
	}

	public static UryuPoseTrack parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonObject e = root.getAsJsonObject("emote");
		Map<String, List<Key>> tracks = new HashMap<>();
		for (JsonElement m : e.getAsJsonArray("moves")) {
			JsonObject mv = m.getAsJsonObject();
			int tick = mv.get("tick").getAsInt();
			String easing = mv.has("easing") ? mv.get("easing").getAsString().toUpperCase() : "LINEAR";
			for (String part : PARTS) {
				if (!mv.has(part)) {
					continue;
				}
				JsonObject axes = mv.getAsJsonObject(part);
				for (Map.Entry<String, JsonElement> en : axes.entrySet()) {
					tracks.computeIfAbsent(part + "." + en.getKey(), k -> new ArrayList<>()).add(new Key(tick, en.getValue().getAsDouble(), easing));
				}
			}
		}
		for (List<Key> l : tracks.values()) {
			l.sort((a, b) -> Integer.compare(a.tick, b.tick));
		}
		return new UryuPoseTrack(root.has("name") ? root.get("name").getAsString() : "", e.get("endTick").getAsInt(), e.get("stopTick").getAsInt(),
				Collections.unmodifiableMap(tracks));
	}

	public String name() {
		return name;
	}

	/** Tick of the last key of the animation (20 ticks per second). */
	public int endTick() {
		return endTick;
	}

	public int stopTick() {
		return stopTick;
	}

	/** True when any rotation axis of the part is keyed. */
	public boolean keysRotation(String part) {
		for (String a : ROT_AXES) {
			if (tracks.containsKey(part + "." + a)) {
				return true;
			}
		}
		return false;
	}

	/** True when any position axis of the part is keyed. */
	public boolean keysPosition(String part) {
		for (String a : POS_AXES) {
			if (tracks.containsKey(part + "." + a)) {
				return true;
			}
		}
		return false;
	}

	/** {@code pitch, yaw, roll} of the part at {@code tick} (fractional), {@link #NOT_KEYED} for an axis without keys. */
	public double[] rotation(String part, double tick) {
		double[] r = new double[3];
		for (int i = 0; i < 3; i++) {
			r[i] = value(part + "." + ROT_AXES[i], tick);
		}
		return r;
	}

	/** Shift of the part pivot from the vanilla pivot at {@code tick} in pixels (game axes), {@link #NOT_KEYED} for an axis without keys. */
	public double[] position(String part, double tick) {
		double[] rest = rest(part);
		double[] r = new double[3];
		for (int i = 0; i < 3; i++) {
			double v = value(part + "." + POS_AXES[i], tick);
			r[i] = Double.isNaN(v) ? NOT_KEYED : v - rest[i];
		}
		return r;
	}

	private double value(String track, double tick) {
		List<Key> keys = tracks.get(track);
		if (keys == null || keys.isEmpty()) {
			return NOT_KEYED;
		}
		if (tick <= keys.get(0).tick) {
			return keys.get(0).value;
		}
		for (int i = 1; i < keys.size(); i++) {
			Key b = keys.get(i);
			if (tick <= b.tick) {
				Key a = keys.get(i - 1);
				if (b.tick == a.tick) {
					return b.value;
				}
				double u = (tick - a.tick) / (b.tick - a.tick);
				return a.value + (b.value - a.value) * ease(b.easing, u);
			}
		}
		return keys.get(keys.size() - 1).value;
	}

	// ------------------------------------------------------------------ easing (playerAnimator names, easings.net formulas)

	private static final double C1 = 1.70158;
	private static final double C2 = C1 * 1.525;
	private static final double C3 = C1 + 1.0;

	/** Easing by name of the exporter ({@code LINEAR}, {@code INOUTSINE}, {@code OUTBACK} ...), input clamped to 0..1; unknown names are linear. */
	public static double ease(String name, double u) {
		double x = u < 0.0 ? 0.0 : Math.min(u, 1.0);
		String n = name.startsWith("EASE") ? name.substring(4) : name;
		return switch (n) {
			case "CONSTANT", "STEP" -> x < 1.0 ? 0.0 : 1.0;
			case "INSINE" -> 1.0 - Math.cos(x * Math.PI / 2.0);
			case "OUTSINE" -> Math.sin(x * Math.PI / 2.0);
			case "INOUTSINE", "CATMULLROM" -> -(Math.cos(Math.PI * x) - 1.0) / 2.0;
			case "INQUAD" -> x * x;
			case "OUTQUAD" -> 1.0 - (1.0 - x) * (1.0 - x);
			case "INOUTQUAD" -> x < 0.5 ? 2.0 * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 2) / 2.0;
			case "INCUBIC" -> x * x * x;
			case "OUTCUBIC" -> 1.0 - Math.pow(1.0 - x, 3);
			case "INOUTCUBIC" -> x < 0.5 ? 4.0 * x * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 3) / 2.0;
			case "INQUART" -> Math.pow(x, 4);
			case "OUTQUART" -> 1.0 - Math.pow(1.0 - x, 4);
			case "INOUTQUART" -> x < 0.5 ? 8.0 * Math.pow(x, 4) : 1.0 - Math.pow(-2.0 * x + 2.0, 4) / 2.0;
			case "INQUINT" -> Math.pow(x, 5);
			case "OUTQUINT" -> 1.0 - Math.pow(1.0 - x, 5);
			case "INOUTQUINT" -> x < 0.5 ? 16.0 * Math.pow(x, 5) : 1.0 - Math.pow(-2.0 * x + 2.0, 5) / 2.0;
			case "INEXPO" -> x == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * x - 10.0);
			case "OUTEXPO" -> x == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * x);
			case "INOUTEXPO" -> x == 0.0 ? 0.0 : x == 1.0 ? 1.0 : x < 0.5 ? Math.pow(2.0, 20.0 * x - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * x + 10.0)) / 2.0;
			case "INCIRC" -> 1.0 - Math.sqrt(1.0 - x * x);
			case "OUTCIRC" -> Math.sqrt(1.0 - Math.pow(x - 1.0, 2));
			case "INOUTCIRC" -> x < 0.5 ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * x, 2))) / 2.0 : (Math.sqrt(1.0 - Math.pow(-2.0 * x + 2.0, 2)) + 1.0) / 2.0;
			case "INBACK" -> C3 * x * x * x - C1 * x * x;
			case "OUTBACK" -> 1.0 + C3 * Math.pow(x - 1.0, 3) + C1 * Math.pow(x - 1.0, 2);
			case "INOUTBACK" -> x < 0.5 ? (Math.pow(2.0 * x, 2) * ((C2 + 1.0) * 2.0 * x - C2)) / 2.0
					: (Math.pow(2.0 * x - 2.0, 2) * ((C2 + 1.0) * (x * 2.0 - 2.0) + C2) + 2.0) / 2.0;
			default -> x;
		};
	}
}
