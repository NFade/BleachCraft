package dev.minebleach.reiatsutest.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

/**
 * R11 T0: a config file carries {@code "configVersion"}. When the file is older than {@link #SERVER_VERSION} / {@link #CLIENT_VERSION},
 * every {@link Rule} newer than the file replaces a key whose value still EQUALS one of the old defaults (or a listed bad value) with the new
 * default. A value the player changed to anything else stays. A file with no version counts as version 1.
 *
 * <p>The table below is the single list of "old default -> new default"; {@code ConfigMigrationsTest} checks it against the code defaults, so a
 * default that changes without a rule here (or the reverse) fails the build. Pure Java: Gson only.
 */
public final class ConfigMigrations {
	public static final String VERSION_KEY = "configVersion";
	/** {@code config/reiatsu_test.json}. */
	public static final int SERVER_VERSION = 3;
	/** {@code config/reiatsu_test_client.json}. */
	public static final int CLIENT_VERSION = 2;

	/**
	 * @param since  the config version that introduced the new default (a file older than this is migrated)
	 * @param path   dotted path of the numeric key, e.g. {@code kido.shakkaho.damage}
	 * @param olds   values that count as "never changed by the player" (old defaults) or as known-bad (see {@code reason})
	 * @param value  the new default
	 * @param reason one line for the log and the report
	 */
	public record Rule(int since, String path, double[] olds, double value, String reason) {
	}

	/** What a migration did: the keys it rewrote (path, from, to) and whether the version stamp changed. */
	public record Result(List<String> changed, int fromVersion, int toVersion) {
		public boolean touched() {
			return fromVersion != toVersion || !changed.isEmpty();
		}
	}

	private static final List<Rule> SERVER = new ArrayList<>();
	private static final List<Rule> CLIENT = new ArrayList<>();

	static {
		// R11 T4: Shakkaho is half a heart with a short burn. 5.0 / 60 ticks were the dev-config values of the duel test, 2.0 / 60 the code default.
		SERVER.add(new Rule(2, "kido.shakkaho.damage", new double[] {2.0, 4.0, 5.0}, 1.0, "R11 T4: Shakkaho centre damage is half a heart"));
		SERVER.add(new Rule(2, "kido.shakkaho.fireTicks", new double[] {60.0}, 15.0, "R11 T4: Shakkaho burn is short (15 ticks)"));
		// Rukia shikai skills aim at the end of the cast: the passive gives 20 percent less per stack
		SERVER.add(new Rule(3, "rukia.passive.shikai.bonusPerStack", new double[] {0.05}, 0.04, "Rukia passive: 20 percent less bonus per stack (shikai)"));
		SERVER.add(new Rule(3, "rukia.passive.bankai.bonusPerStack", new double[] {0.08}, 0.064, "Rukia passive: 20 percent less bonus per stack (bankai)"));
	}

	private ConfigMigrations() {
	}

	/** The rules for the server file (read-only copy). */
	public static List<Rule> serverRules() {
		return List.copyOf(SERVER);
	}

	public static List<Rule> clientRules() {
		return List.copyOf(CLIENT);
	}

	public static Result migrateServer(JsonObject root) {
		return migrate(root, SERVER, SERVER_VERSION);
	}

	public static Result migrateClient(JsonObject root) {
		return migrate(root, CLIENT, CLIENT_VERSION);
	}

	/** Applies the rules newer than the file's version and stamps {@code current}. A file already at {@code current} (or newer) is not touched. */
	public static Result migrate(JsonObject root, List<Rule> rules, int current) {
		int from = versionOf(root);
		if (from >= current) {
			return new Result(List.of(), from, from);
		}
		List<String> changed = new ArrayList<>();
		for (Rule r : rules) {
			if (r.since() <= from || r.since() > current) {
				continue;
			}
			String old = rewrite(root, r);
			if (old != null) {
				changed.add(r.path() + " " + old + " -> " + fmt(r.value()));
			}
		}
		root.addProperty(VERSION_KEY, current);
		return new Result(changed, from, current);
	}

	/** Version of a parsed file: the stamp, or 1 when missing / not a whole number. */
	public static int versionOf(JsonObject root) {
		JsonElement e = root == null ? null : root.get(VERSION_KEY);
		if (e instanceof JsonPrimitive p && p.isNumber()) {
			double d = p.getAsDouble();
			if (!Double.isNaN(d) && !Double.isInfinite(d) && d >= 1 && d == Math.floor(d)) {
				return (int) d;
			}
		}
		return 1;
	}

	private static String rewrite(JsonObject root, Rule r) {
		String[] parts = r.path().split("\\.");
		JsonObject o = root;
		for (int i = 0; i < parts.length - 1; i++) {
			JsonElement e = o.get(parts[i]);
			if (!(e instanceof JsonObject next)) {
				return null;
			}
			o = next;
		}
		String leaf = parts[parts.length - 1];
		JsonElement e = o.get(leaf);
		if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
			return null;
		}
		double cur = p.getAsDouble();
		for (double old : r.olds()) {
			if (Math.abs(cur - old) < 1e-9) {
				if (r.value() == Math.rint(r.value()) && Math.abs(r.value()) < 1e9) {
					o.addProperty(leaf, (long) r.value());
				} else {
					o.addProperty(leaf, r.value());
				}
				return fmt(cur);
			}
		}
		return null;
	}

	private static String fmt(double v) {
		return v == Math.rint(v) && Math.abs(v) < 1e9 ? Long.toString((long) v) : Double.toString(v);
	}
}
