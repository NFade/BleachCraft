package dev.minebleach.reiatsutest.core.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;
import dev.minebleach.reiatsutest.core.reiatsu.Rate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * R14: the {@code balance} section of {@code config/reiatsu_test.json}. The balance numbers of {@link BalanceConfig} live in code
 * ({@link BalanceConfig#defaults()}); this section holds only what the player CHANGED ({@code /reiatsu dev set balance.tsukishiro.cost 20}),
 * so a later change of a code default still reaches everybody who never touched that key. {@link #apply} lays the section over a base
 * config, {@link #flatten} lists every key with the value in force (for {@code /reiatsu dev get} and the tab completion). Pure Java: Gson only.
 *
 * <p>Keys (after the {@code balance.} prefix), units are the ones a person reads, not the machine's:
 * <ul>
 * <li>{@code <ability>.cost} points of reiatsu, {@code <ability>.cooldown} seconds, {@code <ability>.enabled} 0/1, {@code <ability>.<param>} every
 * number of {@link AbilityParams} the ability has (blocks, HP, ticks); {@code <ability>} is the lower case enum name, e.g. {@code tsukishiro}</li>
 * <li>{@code regen.<state>.perBatchTenths} / {@code regen.<state>.upkeepTenths}: reiatsu tenths per regeneration batch (5 ticks = 4 batches a second,
 * so 10 = +4.0 a second); state is sealed, base, shikai or bankai</li>
 * <li>{@code shikaiReleaseCost}, {@code bankaiCapSeconds}, {@code shikaiIdleSeconds}, {@code gcdTicks}, {@code barrierSeconds}, {@code barrierReductionPercent},
 * {@code bankaiReentrySeconds}</li>
 * <li>{@code shunpo.cost}, {@code shunpo.cooldown}, {@code shunpo.distance}, {@code shunpo.minDistance}, {@code shunpo.uryuDistanceBonus},
 * {@code shunpo.uryuCooldownBonus} (seconds)</li>
 * </ul>
 */
public final class BalanceOverrides {
	public static final String SECTION = "balance";

	private BalanceOverrides() {
	}

	/** The key of an ability in the section: the lower case enum name. */
	public static String key(AbilityId id) {
		return id.name().toLowerCase(Locale.ROOT);
	}

	/** Every tunable key (without the section prefix) with the value {@code c} holds, sorted. */
	public static Map<String, Double> flatten(BalanceConfig c) {
		Map<String, Double> m = new TreeMap<>();
		for (ZanpakutoState s : ZanpakutoState.values()) {
			Rate r = c.rates().get(s);
			if (r != null) {
				String k = "regen." + s.name().toLowerCase(Locale.ROOT);
				m.put(k + ".perBatchTenths", (double) r.regenPerBatch());
				m.put(k + ".upkeepTenths", (double) r.drainPerBatch());
			}
		}
		m.put("shikaiReleaseCost", c.shikaiReleaseCost() / 10.0);
		m.put("bankaiCapSeconds", c.bankaiCapTicks() / 20.0);
		m.put("shikaiIdleSeconds", c.shikaiIdleTicks() / 20.0);
		m.put("gcdTicks", (double) c.gcdTicks());
		m.put("barrierSeconds", c.barrierTicks() / 20.0);
		m.put("barrierReductionPercent", (double) c.barrierReductionPercent());
		m.put("bankaiReentrySeconds", c.bankaiReentryTicks() / 20.0);
		ShunpoSpec sp = c.shunpo();
		m.put("shunpo.cost", sp.costTenths() / 10.0);
		m.put("shunpo.cooldown", sp.cooldownTicks() / 20.0);
		m.put("shunpo.distance", sp.distance());
		m.put("shunpo.minDistance", sp.minDistance());
		m.put("shunpo.uryuDistanceBonus", sp.uryuDistanceBonus());
		m.put("shunpo.uryuCooldownBonus", sp.uryuCooldownBonus() / 20.0);
		for (AbilitySpec a : c.abilities().values()) {
			String k = key(a.id());
			m.put(k + ".cost", a.costTenths() / 10.0);
			m.put(k + ".cooldown", a.cooldownTicks() / 20.0);
			m.put(k + ".enabled", a.enabled() ? 1.0 : 0.0);
			for (Map.Entry<String, Double> p : a.params().entrySet()) {
				m.put(k + "." + p.getKey(), p.getValue());
			}
		}
		return m;
	}

	/**
	 * {@code base} with the {@code balance} section of {@code root} laid over it. A missing section, a missing or malformed key gives the base value;
	 * numbers are clamped to a sane range, never throws.
	 */
	public static BalanceConfig apply(BalanceConfig base, JsonObject root) {
		JsonObject o = Cfg.obj(root, SECTION);
		if (o.size() == 0) {
			return base;
		}
		EnumMap<ZanpakutoState, Rate> rates = new EnumMap<>(ZanpakutoState.class);
		rates.putAll(base.rates());
		JsonObject regen = Cfg.obj(o, "regen");
		for (ZanpakutoState s : ZanpakutoState.values()) {
			Rate r = base.rates().get(s);
			if (r == null) {
				continue;
			}
			JsonObject so = Cfg.obj(regen, s.name().toLowerCase(Locale.ROOT));
			rates.put(s, new Rate(Cfg.integer(so, "perBatchTenths", r.regenPerBatch(), 0, 1000), Cfg.integer(so, "upkeepTenths", r.drainPerBatch(), 0, 1000)));
		}
		ShunpoSpec sp = base.shunpo();
		JsonObject so = Cfg.obj(o, "shunpo");
		ShunpoSpec shunpo = new ShunpoSpec(
				tenths(so, "cost", sp.costTenths()),
				ticks(so, "cooldown", sp.cooldownTicks()),
				Cfg.num(so, "distance", sp.distance(), 1.0, 64.0),
				Cfg.num(so, "minDistance", sp.minDistance(), 0.0, 64.0),
				sp.step(), sp.maxDrop(),
				Cfg.num(so, "uryuDistanceBonus", sp.uryuDistanceBonus(), -8.0, 32.0),
				Math.max(0, ticks(so, "uryuCooldownBonus", sp.uryuCooldownBonus())));
		Map<AbilityId, AbilitySpec> abilities = new HashMap<>();
		for (AbilitySpec a : base.abilities().values()) {
			JsonObject ao = Cfg.obj(o, key(a.id()));
			Map<String, Double> params = new HashMap<>(a.params());
			for (Map.Entry<String, Double> p : a.params().entrySet()) {
				params.put(p.getKey(), Cfg.num(ao, p.getKey(), p.getValue(), 0.0, 10_000.0));
			}
			abilities.put(a.id(), new AbilitySpec(a.id(), tenths(ao, "cost", a.costTenths()), ticks(ao, "cooldown", a.cooldownTicks()), a.effectId(),
					enabled(ao, a.enabled()), a.phaseOffsets(), params));
		}
		return new BalanceConfig(base.maxTenths(), base.regenBatchTicks(), rates,
				tenths(o, "shikaiReleaseCost", base.shikaiReleaseCost()), base.bankaiCost(),
				ticks(o, "bankaiCapSeconds", base.bankaiCapTicks()), ticks(o, "shikaiIdleSeconds", base.shikaiIdleTicks()), base.handGraceTicks(),
				base.transitionLockTicks(), Cfg.integer(o, "gcdTicks", base.gcdTicks(), 0, 200), base.settleTicks(), base.sealLockTicks(),
				base.sheatheLockTicks(), ticks(o, "bankaiReentrySeconds", base.bankaiReentryTicks()), base.rateLimitPerSecond(), base.respawnTenths(),
				base.attackModeTicks(), ticks(o, "barrierSeconds", base.barrierTicks()), base.barrierPoolTenths(),
				Cfg.integer(o, "barrierReductionPercent", base.barrierReductionPercent(), 0, 100), base.barrierControlImmune(), base.bankaiField(),
				shunpo, abilities, base.mech());
	}

	/** The {@code enabled} key may be spelled 0/1 or false/true; anything else keeps {@code def}. */
	private static boolean enabled(JsonObject o, boolean def) {
		JsonElement e = o.get("enabled");
		if (e != null && e.isJsonPrimitive()) {
			if (e.getAsJsonPrimitive().isBoolean()) {
				return e.getAsBoolean();
			}
			if (e.getAsJsonPrimitive().isNumber()) {
				return e.getAsDouble() >= 0.5;
			}
		}
		return def;
	}

	/** Points in the file, tenths in the machine. */
	private static int tenths(JsonObject o, String key, int def) {
		return (int) Math.round(Cfg.num(o, key, def / 10.0, 0.0, 100.0) * 10.0);
	}

	/** Seconds in the file, ticks in the machine. */
	private static int ticks(JsonObject o, String key, int def) {
		return (int) Math.round(Cfg.num(o, key, def / 20.0, 0.0, 3600.0) * 20.0);
	}

	/** Sets {@code path} (dotted, without prefix) to {@code value} in the {@code balance} section of {@code root}, creating the objects on the way. */
	public static void set(JsonObject root, String path, double value) {
		JsonObject o = root.has(SECTION) && root.get(SECTION).isJsonObject() ? root.getAsJsonObject(SECTION) : new JsonObject();
		root.add(SECTION, o);
		String[] parts = path.split("\\.");
		for (int i = 0; i < parts.length - 1; i++) {
			JsonObject next = o.has(parts[i]) && o.get(parts[i]).isJsonObject() ? o.getAsJsonObject(parts[i]) : new JsonObject();
			o.add(parts[i], next);
			o = next;
		}
		o.addProperty(parts[parts.length - 1], value);
	}

	/** Removes a key from the section (back to the code default); true when it was there. Empty objects on the way are dropped. */
	public static boolean unset(JsonObject root, String path) {
		if (!root.has(SECTION) || !root.get(SECTION).isJsonObject()) {
			return false;
		}
		String[] parts = path.split("\\.");
		JsonObject[] chain = new JsonObject[parts.length];
		JsonObject o = root.getAsJsonObject(SECTION);
		for (int i = 0; i < parts.length - 1; i++) {
			if (!o.has(parts[i]) || !o.get(parts[i]).isJsonObject()) {
				return false;
			}
			chain[i] = o;
			o = o.getAsJsonObject(parts[i]);
		}
		chain[parts.length - 1] = o;
		boolean had = o.remove(parts[parts.length - 1]) != null;
		for (int i = parts.length - 1; i > 0; i--) {
			if (chain[i].size() == 0) {
				chain[i - 1].remove(parts[i - 1]);
			}
		}
		return had;
	}
}
