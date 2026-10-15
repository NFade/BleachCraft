package dev.minebleach.reiatsutest.core.uryu;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;

/**
 * R13: the tunable numbers of the Heilig Bogen and of the Rage its arrows pay, read from the {@code uryu} section of
 * {@code config/reiatsu_test.json} (sub objects {@code bow} and {@code rage}), reloaded with {@code /reiatsu reload}. Pure data (Gson only).
 *
 * <p>Design (design/results/r13_uryu_fixes.md, set B): a release under {@link #minPower} fires nothing (no tap spam), the draw is slower, a
 * basic arrow hits for less and at most once per {@link #cooldownTicks}; the Rage of an arrow hit follows the damage of the arrow (so a
 * short or weak draw pays little) instead of the flat 3.0 every hit used to pay.
 *
 * @param minPower        smallest bow power 0..1 that still fires (0.5 = a half draw)
 * @param fullDrawTicks   ticks of drawing for full power
 * @param damage          HP of a basic arrow at full power, before Focus and Blut Arterie
 * @param damageCurve     damage = damage * power^curve (1 = linear; above 1 a short draw loses more)
 * @param cooldownTicks   least ticks between two basic shots (release to release)
 * @param rageTenthsPerHp Rage (tenths of a point) a basic arrow hit pays per HP of its damage before armor
 * @param rageMaxPerHitTenths cap of the Rage of one arrow hit
 * @param castRewardForAbilityArrows the arrows of Uryu's abilities (Z, wings, Licht Regen, Sternenstaub) and his Sprenger / Seele hits pay the
 *                        fixed reward of the cast ({@code rage.abilityHitTenths}, once per cast) instead of the flat hit reward per arrow
 */
public record UryuBowTuning(double minPower, int fullDrawTicks, double damage, double damageCurve, int cooldownTicks,
		int rageTenthsPerHp, int rageMaxPerHitTenths, boolean castRewardForAbilityArrows) {
	public static final String SECTION = "uryu";
	public static final String BOW = "bow";
	public static final String RAGE = "rage";

	public UryuBowTuning {
		if (!(minPower >= 0.0 && minPower <= 1.0)) {
			throw new IllegalArgumentException("minPower: " + minPower);
		}
		if (fullDrawTicks < 1 || !(damage >= 0.0) || !(damageCurve > 0.0) || cooldownTicks < 0 || rageTenthsPerHp < 0 || rageMaxPerHitTenths < 0) {
			throw new IllegalArgumentException("bad uryu tuning value");
		}
	}

	/** Set B of the R13 report: half draw to fire, 1.2 s to full power, 3.0 HP, 0.7 s between shots, 0.5 Rage per HP. */
	public static UryuBowTuning defaults() {
		return new UryuBowTuning(UryuBalance.BOW_MIN_POWER, UryuBalance.BOW_FULL_DRAW_TICKS, UryuBalance.BASIC_DAMAGE, 1.0,
				UryuBalance.BASIC_COOLDOWN, 5, 30, true);
	}

	/** Reads {@code uryu.bow} and {@code uryu.rage} of the root config (missing keys = defaults, bad values = defaults, numbers clamped; never throws). */
	public static UryuBowTuning fromRoot(JsonObject root) {
		UryuBowTuning d = defaults();
		JsonObject u = Cfg.obj(root, SECTION);
		JsonObject b = Cfg.obj(u, BOW);
		JsonObject r = Cfg.obj(u, RAGE);
		return new UryuBowTuning(
				Cfg.num(b, "minPower", d.minPower, 0.0, 1.0),
				Cfg.integer(b, "fullDrawTicks", d.fullDrawTicks, 4, 200),
				Cfg.num(b, "damage", d.damage, 0.0, 40.0),
				Cfg.num(b, "damageCurve", d.damageCurve, 0.25, 4.0),
				Cfg.integer(b, "cooldownTicks", d.cooldownTicks, 0, 400),
				Cfg.integer(r, "arrowTenthsPerHp", d.rageTenthsPerHp, 0, 1000),
				Cfg.integer(r, "arrowMaxPerHitTenths", d.rageMaxPerHitTenths, 0, 1000),
				Cfg.bool(r, "abilityArrowsPayCastReward", d.castRewardForAbilityArrows));
	}

	/** The {@code uryu} object, for writing the numbers in force into a file. */
	public JsonObject toJson() {
		JsonObject b = new JsonObject();
		b.addProperty("minPower", minPower);
		b.addProperty("fullDrawTicks", fullDrawTicks);
		b.addProperty("damage", damage);
		b.addProperty("damageCurve", damageCurve);
		b.addProperty("cooldownTicks", cooldownTicks);
		JsonObject r = new JsonObject();
		r.addProperty("arrowTenthsPerHp", rageTenthsPerHp);
		r.addProperty("arrowMaxPerHitTenths", rageMaxPerHitTenths);
		r.addProperty("abilityArrowsPayCastReward", castRewardForAbilityArrows);
		JsonObject u = new JsonObject();
		u.add(BOW, b);
		u.add(RAGE, r);
		return u;
	}

	/** Rage tenths of one basic arrow hit that was shot with {@code rawHp} of damage (before armor): proportional, capped, never negative. */
	public int arrowRageTenths(float rawHp) {
		if (!(rawHp > 0.0F)) {
			return 0;
		}
		long raw = Math.round((double) rawHp * rageTenthsPerHp);
		return (int) Math.max(0L, Math.min(raw, rageMaxPerHitTenths));
	}
}
