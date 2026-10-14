package dev.minebleach.reiatsutest.core.combat;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;

/**
 * R11 T6, variant P1 "the cold gathers": the passive of Rukia. Every landed hit of Rukia (sword or ability, on a living target) adds a
 * stack; each stack raises her damage by a percentage (shikai 5 percent up to 4 stacks = +20 percent, bankai 8 percent up to 5 stacks = +40
 * percent). All stacks burn out when no hit landed for a while (shikai 80 ticks, bankai 100); a hit that lands on Rukia removes ONE stack;
 * sealing and dying reset it (the glue calls {@link #reset}).
 *
 * <p>One instance per player, server thread, clock-free (every call gets the server tick). No Minecraft types. Two rules keep an area
 * attack fair: a stack is gained at most once per tick (a sweep or a blast that hits five targets in one tick is one hit), and every hit of that
 * tick is scaled by the stacks she had BEFORE it (the first victim must not make the others hurt more).
 */
public final class FrostMomentum {
	/** The form that rules the numbers; NONE (sealed or drawn) has no momentum at all. */
	public enum Form {
		NONE, SHIKAI, BANKAI
	}

	/** Numbers of one form. */
	public record Tier(double bonusPerStack, int maxStacks, int expireTicks) {
		public Tier {
			bonusPerStack = Double.isNaN(bonusPerStack) ? 0.0 : Math.max(0.0, Math.min(1.0, bonusPerStack));
			maxStacks = Math.max(0, Math.min(20, maxStacks));
			expireTicks = Math.max(1, Math.min(72_000, expireTicks));
		}

		/** The highest multiplier of this tier. */
		public double maxMultiplier() {
			return 1.0 + bonusPerStack * maxStacks;
		}
	}

	/** The {@code rukia.passive} section. */
	public record Config(boolean enabled, Tier shikai, Tier bankai) {
		public static final String SECTION = "rukia";
		public static final String SUB = "passive";

		public static Config defaults() {
			return new Config(true, new Tier(0.05, 4, 80), new Tier(0.08, 5, 100));
		}

		public Tier tier(Form f) {
			return switch (f) {
				case SHIKAI -> shikai;
				case BANKAI -> bankai;
				case NONE -> new Tier(0.0, 0, 1);
			};
		}

		/** Reads {@code rukia.passive} of the root config (missing keys = defaults, bad values = defaults, numbers clamped; never throws). */
		public static Config fromRoot(JsonObject root) {
			Config d = defaults();
			JsonObject o = Cfg.obj(Cfg.obj(root, SECTION), SUB);
			return new Config(Cfg.bool(o, "enabled", d.enabled), tierFrom(Cfg.obj(o, "shikai"), d.shikai), tierFrom(Cfg.obj(o, "bankai"), d.bankai));
		}

		private static Tier tierFrom(JsonObject o, Tier d) {
			return new Tier(Cfg.num(o, "bonusPerStack", d.bonusPerStack, 0.0, 1.0), Cfg.integer(o, "maxStacks", d.maxStacks, 0, 20),
					Cfg.integer(o, "expireTicks", d.expireTicks, 1, 72_000));
		}

		/** The {@code rukia.passive} object, for writing the numbers in force into a file. */
		public JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", enabled);
			o.add("shikai", tierJson(shikai));
			o.add("bankai", tierJson(bankai));
			return o;
		}

		private static JsonObject tierJson(Tier t) {
			JsonObject o = new JsonObject();
			o.addProperty("bonusPerStack", t.bonusPerStack());
			o.addProperty("maxStacks", t.maxStacks());
			o.addProperty("expireTicks", t.expireTicks());
			return o;
		}
	}

	private Config cfg;
	private int stacks;
	private int lastHitTick = Integer.MIN_VALUE / 2;
	/** Tick of the last stack gain and the stacks before it, so one tick is one hit and all its victims see the same multiplier. */
	private int lastGainTick = Integer.MIN_VALUE / 2;
	private int stacksBeforeGain;

	public FrostMomentum(Config cfg) {
		this.cfg = cfg;
	}

	public Config config() {
		return cfg;
	}

	/** Live reload: the stacks stay, the new numbers apply from now on. */
	public void setConfig(Config cfg) {
		this.cfg = cfg;
	}

	/** Stacks in force at {@code tick} for {@code form}: 0 after the burn-out, never above the form's maximum. */
	public int stacks(Form form, int tick) {
		if (!cfg.enabled() || form == Form.NONE) {
			return 0;
		}
		Tier t = cfg.tier(form);
		if (tick >= lastHitTick && tick - lastHitTick >= t.expireTicks()) {
			return 0;
		}
		return Math.min(stacks, t.maxStacks());
	}

	/** Damage multiplier for a hit at {@code tick}: 1 + stacks * bonus, with the stacks Rukia had before this tick's own hit. */
	public double multiplier(Form form, int tick) {
		if (!cfg.enabled() || form == Form.NONE) {
			return 1.0;
		}
		int s = tick == lastGainTick ? Math.min(stacksBeforeGain, cfg.tier(form).maxStacks()) : stacks(form, tick);
		return 1.0 + s * cfg.tier(form).bonusPerStack();
	}

	/**
	 * A hit of Rukia landed (damage really dealt to a living target) at {@code tick}. Adds a stack, at most one per tick, up to the form's
	 * maximum, and restarts the burn-out timer. Returns the stacks after the hit.
	 */
	public int onHit(Form form, int tick) {
		if (!cfg.enabled() || form == Form.NONE) {
			return 0;
		}
		Tier t = cfg.tier(form);
		int now = stacks(form, tick);
		if (tick != lastGainTick) {
			stacksBeforeGain = now;
			lastGainTick = tick;
			stacks = Math.min(t.maxStacks(), now + 1);
		}
		lastHitTick = tick;
		return stacks;
	}

	/** A hit landed on Rukia herself: one stack less (not all). Returns the stacks after. */
	public int onHurt(Form form, int tick) {
		int now = stacks(form, tick);
		stacks = Math.max(0, now - 1);
		if (stacks == 0) {
			lastHitTick = Integer.MIN_VALUE / 2;
		}
		return stacks;
	}

	/** Sealing, drawing back, death, logout: everything gone. */
	public void reset() {
		stacks = 0;
		lastHitTick = Integer.MIN_VALUE / 2;
		lastGainTick = Integer.MIN_VALUE / 2;
		stacksBeforeGain = 0;
	}
}
