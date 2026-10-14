package dev.minebleach.reiatsutest.core.rage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R11 T3: the balance simulator of the Rage economy. A deterministic fight (no random numbers: a hit chance is spread evenly over the swings)
 * is fed into a real {@link RageMeter} tick by tick, the player releases shikai as soon as the bar and the rules allow, then bankai as soon as
 * the bar, the cost and the minimum time in shikai allow. The table of times for several play styles is written to
 * {@code build/reports/rage_sim_table.txt} (and copied into design/results/r11_T3.md by hand).
 *
 * <p>Goals of the plan (T3 item 7, "a duel of good players: a hit every 1.5 s of 6 hp at 70 percent, a parry every 10 s"): shikai after 35 to 60 s of
 * active fighting, bankai 80 to 140 s after shikai (20 s of that is the minimum time in shikai). The numbers are UNVERIFIED by play.
 */
class RageSimulatorTest {
	/** One play style. All periods are in ticks, 0 = never. */
	record Profile(String name, int swingEvery, int hitPercent, float hitHp, int parryEvery, int takenEvery, int takenPercent, float takenHp,
			int abilityEvery, boolean mob, int hitEveryOnTarget) {
	}

	/** Seconds at which shikai / bankai became possible and were taken; -1 = not within the time limit. */
	record Result(double shikaiSec, double bankaiSec, int rageAtEnd) {
		double shikaiToBankaiSec() {
			return shikaiSec < 0 || bankaiSec < 0 ? -1 : bankaiSec - shikaiSec;
		}
	}

	static final int LIMIT_TICKS = 20 * 60 * 30; // 30 minutes

	static Result simulate(RageConfig cfg, Profile p) {
		RageMeter m = new RageMeter(cfg);
		int shikaiAt = -1;
		int bankaiAt = -1;
		double swingAcc = 0.0;
		double takenAcc = 0.0;
		for (int t = 1; t <= LIMIT_TICKS && bankaiAt < 0; t++) {
			if (p.swingEvery() > 0 && t % p.swingEvery() == 0) {
				swingAcc += p.hitPercent() / 100.0;
				if (swingAcc >= 1.0 - 1e-9) {
					swingAcc -= 1.0;
					m.onDamageDealt(p.hitHp(), 1, p.mob(), t);
				}
			}
			if (p.takenEvery() > 0 && t % p.takenEvery() == 0) {
				takenAcc += p.takenPercent() / 100.0;
				if (takenAcc >= 1.0 - 1e-9) {
					takenAcc -= 1.0;
					m.onDamageTaken(p.takenHp(), t);
				}
			}
			if (p.parryEvery() > 0 && t % p.parryEvery() == 0) {
				m.onParry(t);
			}
			if (p.abilityEvery() > 0 && shikaiAt >= 0 && t % p.abilityEvery() == 0) {
				m.armAbility(t);
				m.onAbilityHit(p.mob(), t); // one target or ten, the cast pays once
				m.onAbilityHit(p.mob(), t);
			}
			m.tick(t);
			if (shikaiAt < 0 && m.canSpend(cfg.shikaiCostTenths())) {
				m.spend(cfg.shikaiCostTenths());
				shikaiAt = t;
			} else if (shikaiAt >= 0 && t - shikaiAt >= cfg.bankaiMinShikaiTicks() && m.canSpend(cfg.bankaiCostTenths())) {
				m.spend(cfg.bankaiCostTenths());
				bankaiAt = t;
			}
		}
		return new Result(shikaiAt < 0 ? -1 : shikaiAt / 20.0, bankaiAt < 0 ? -1 : bankaiAt / 20.0, m.value());
	}

	/** The reference duel of the plan: swing every 1.5 s, 70 percent hit, 6 hp, a parry every 10 s; the enemy hits back (matters for the old numbers). */
	static final Profile REFERENCE = new Profile("duel, good players (reference)", 30, 70, 6.0F, 200, 30, 50, 6.0F, 0, false, 0);

	static final List<Profile> PROFILES = List.of(
			REFERENCE,
			new Profile("duel + one ability per 8 s in shikai", 30, 70, 6.0F, 200, 30, 50, 6.0F, 160, false, 0),
			new Profile("aggressive: swing 1.0 s, 80%, 7 hp, parry 15 s", 20, 80, 7.0F, 300, 20, 50, 6.0F, 0, false, 0),
			new Profile("defensive: swing 2.0 s, 50%, parry every 6 s", 40, 50, 6.0F, 120, 40, 50, 6.0F, 0, false, 0),
			new Profile("casual: swing 2.5 s, 50%, no parry", 50, 50, 6.0F, 0, 50, 50, 6.0F, 0, false, 0),
			new Profile("parry spam only, every 0.5 s (gap rule)", 0, 0, 0.0F, 10, 0, 0, 0.0F, 0, false, 0),
			new Profile("one dummy player, 100% hits every 0.5 s", 10, 100, 6.0F, 0, 0, 0, 0.0F, 0, false, 0),
			new Profile("mob farm: swing 1.0 s, 100%, 6 hp", 20, 100, 6.0F, 0, 0, 0, 0.0F, 0, true, 0));

	/** The numbers before R11 (round 5), the "before" column. */
	static RageConfig oldConfig() {
		return new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, 400, 800);
	}

	static String fmt(double sec) {
		return sec < 0 ? "never (30 min)" : String.format(java.util.Locale.ROOT, "%.0f s", sec);
	}

	static String table(RageConfig now, RageConfig old) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(java.util.Locale.ROOT, "%-52s | %-15s %-15s %-15s | %-15s %-15s %-15s%n", "profile", "shikai NOW", "bankai NOW", "shikai->bankai",
				"shikai OLD", "bankai OLD", "shikai->bankai"));
		for (Profile p : PROFILES) {
			Result a = simulate(now, p);
			Result b = simulate(old, p);
			sb.append(String.format(java.util.Locale.ROOT, "%-52s | %-15s %-15s %-15s | %-15s %-15s %-15s%n", p.name(), fmt(a.shikaiSec()), fmt(a.bankaiSec()),
					fmt(a.shikaiToBankaiSec()), fmt(b.shikaiSec()), fmt(b.bankaiSec()), fmt(b.shikaiToBankaiSec())));
		}
		return sb.toString();
	}

	@Test
	void printsTheTableOfTimesToShikaiAndBankai() throws IOException {
		String t = table(RageR11Test.R11, oldConfig());
		Path out = Path.of("build", "reports", "rage_sim_table.txt");
		Files.createDirectories(out.getParent());
		Files.writeString(out, t, StandardCharsets.UTF_8);
		System.out.println(t);
		assertTrue(t.contains("duel, good players"));
	}

	@Test
	void theReferenceDuelMeetsTheGoalsOfThePlan() {
		Result r = simulate(RageR11Test.R11, REFERENCE);
		assertTrue(r.shikaiSec() >= 35.0 && r.shikaiSec() <= 60.0, "shikai at " + r.shikaiSec() + " s, goal 35..60 s");
		assertTrue(r.shikaiToBankaiSec() >= 80.0 && r.shikaiToBankaiSec() <= 140.0, "shikai to bankai " + r.shikaiToBankaiSec() + " s, goal 80..140 s");
	}

	@Test
	void theOldNumbersWereMuchFasterThanTheGoal() {
		Result r = simulate(oldConfig(), REFERENCE);
		assertTrue(r.shikaiSec() < 20.0, "the old economy opened shikai in " + r.shikaiSec() + " s");
		assertTrue(r.shikaiToBankaiSec() < 40.0, "and bankai in " + r.shikaiToBankaiSec() + " s more");
	}

	@Test
	void takingDamageNeverHelps() {
		Profile onlyTaken = new Profile("only taken", 0, 0, 0.0F, 0, 10, 100, 8.0F, 0, false, 0);
		assertEquals(-1.0, simulate(RageR11Test.R11, onlyTaken).shikaiSec(), "no rage from damage taken: shikai never opens");
	}

	@Test
	void parrySpamAndOneDummyAreSlowedByTheirCaps() {
		Result parry = simulate(RageR11Test.R11, PROFILES.get(5));
		Result dummy = simulate(RageR11Test.R11, PROFILES.get(6));
		Result ref = simulate(RageR11Test.R11, REFERENCE);
		// a parry pays at most once per 20 ticks (4 points): 40 points need ten paying parries, at least 9 s, however often the parries come
		assertTrue(parry.shikaiSec() >= 9.0, "parry spam: shikai in " + parry.shikaiSec() + " s");
		// one target pays at most 8 points per 10 s: 40 points need five windows, i.e. about 40 s however fast it is hit (the first window starts full)
		assertTrue(dummy.shikaiSec() >= 39.0, "one target: shikai in " + dummy.shikaiSec() + " s");
		assertTrue(ref.shikaiSec() > 0);
	}

	@Test
	void mobFarmingIsFarSlowerThanFightingPlayers() {
		Result mob = simulate(RageR11Test.R11, PROFILES.get(7));
		Result duel = simulate(RageR11Test.R11, PROFILES.get(2));
		assertTrue(mob.shikaiSec() > 2.5 * duel.shikaiSec(), "mobs " + mob.shikaiSec() + " s vs players " + duel.shikaiSec() + " s");
	}

	@Test
	void everyProfileIsMonotonic() {
		List<Double> shikai = new ArrayList<>();
		for (Profile p : PROFILES) {
			Result r = simulate(RageR11Test.R11, p);
			if (r.shikaiSec() >= 0 && r.bankaiSec() >= 0) {
				assertTrue(r.bankaiSec() - r.shikaiSec() >= RageR11Test.R11.bankaiMinShikaiTicks() / 20.0, p.name());
			}
			shikai.add(r.shikaiSec());
		}
		assertEquals(PROFILES.size(), shikai.size());
	}
}
