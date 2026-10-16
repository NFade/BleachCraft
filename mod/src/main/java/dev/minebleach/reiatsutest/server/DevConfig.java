package dev.minebleach.reiatsutest.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.config.ConfigKeys;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.BalanceOverrides;
import dev.minebleach.reiatsutest.core.uryu.BowDraw;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * R14: {@code /reiatsu dev get|set|unset}. Balance without a rebuild: {@code set} writes ONE leaf into {@code config/reiatsu_test.json} and runs the
 * same reload as {@code /reiatsu reload}, so the number is in force at once for everybody online. The key list is what the game holds now: the
 * leaves of the sections of the file ({@link ConfigKeys#SECTIONS}) completed with the live objects of {@code rage}, {@code mech} and {@code uryu}
 * (so a key the file does not hold yet is still listed) and the balance numbers of {@link BalanceOverrides} (the {@code balance} section of the file
 * holds only the keys that were changed). Only an existing key can be set: a typo is refused, not written.
 */
public final class DevConfig {
	private DevConfig() {
	}

	/** Every key that can be set with its value in force, sorted. */
	public static Map<String, JsonPrimitive> snapshot() {
		Map<String, JsonPrimitive> out = new TreeMap<>();
		ConfigKeys.flatten("", ModConfig.readRoot(), out);
		out.keySet().removeIf(k -> !ConfigKeys.allowed(k) || k.startsWith("balance."));
		ConfigKeys.flatten("rage", RageManager.config().toJson(), out);
		ConfigKeys.flatten("mech", ZanpakutoManager.mech().toJson(), out);
		ConfigKeys.flatten("", wrap("uryu", BowDraw.tuning().toJson()), out);
		for (Map.Entry<String, Double> e : BalanceOverrides.flatten(BalanceConfig.live()).entrySet()) {
			out.put("balance." + e.getKey(), new JsonPrimitive(e.getValue()));
		}
		return out;
	}

	private static JsonObject wrap(String section, JsonObject inner) {
		// UryuBowTuning.toJson already is the {bow, rage} object of the "uryu" section
		JsonObject o = new JsonObject();
		o.add(section, inner);
		return o;
	}

	/**
	 * Sets one key and reloads. Returns the line for the chat (the value that is in force after the reload: a number out of range shows the clamped one).
	 *
	 * @throws IllegalArgumentException for an unknown key or a value that does not fit it
	 */
	public static String set(String key, String text) {
		Map<String, JsonPrimitive> now = snapshot();
		JsonPrimitive like = now.get(key);
		if (like == null) {
			throw new IllegalArgumentException("unknown key '" + key + "' (try /reiatsu dev get " + prefixHint(key) + ")");
		}
		JsonPrimitive value = ConfigKeys.parse(text, like);
		try {
			ModConfig.edit(root -> {
				if (key.startsWith("balance.")) {
					BalanceOverrides.set(root, key.substring("balance.".length()), value.isBoolean() ? (value.getAsBoolean() ? 1.0 : 0.0) : value.getAsDouble());
				} else {
					ConfigKeys.setPath(root, key, value);
				}
			});
		} catch (IOException e) {
			throw new IllegalArgumentException("could not write the config: " + e.getMessage());
		}
		ConfigReload.run();
		JsonPrimitive after = snapshot().get(key);
		ReiatsuTest.LOGGER.info("[config] dev set {} = {} (was {}, in force {})", key, ConfigKeys.show(value), ConfigKeys.show(like), after == null ? "?" : ConfigKeys.show(after));
		String got = after == null ? ConfigKeys.show(value) : ConfigKeys.show(after);
		return key + " = " + got + " (was " + ConfigKeys.show(like) + ")" + (got.equals(ConfigKeys.show(value)) ? "" : " [asked " + ConfigKeys.show(value) + ": clamped or rounded]");
	}

	/** Takes a {@code balance.*} key back to the code default. Returns the line for the chat. */
	public static String unset(String key) {
		if (!key.startsWith("balance.")) {
			throw new IllegalArgumentException("only balance.* keys have a code default to go back to; set the other keys to the number you want");
		}
		boolean[] had = new boolean[1];
		try {
			ModConfig.edit(root -> had[0] = BalanceOverrides.unset(root, key.substring("balance.".length())));
		} catch (IOException e) {
			throw new IllegalArgumentException("could not write the config: " + e.getMessage());
		}
		ConfigReload.run();
		JsonPrimitive after = snapshot().get(key);
		return key + (had[0] ? " back to the default " : " was not changed, is ") + (after == null ? "?" : ConfigKeys.show(after));
	}

	private static String prefixHint(String key) {
		int dot = key.indexOf('.');
		return dot > 0 ? key.substring(0, dot) : key;
	}
}
