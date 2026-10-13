package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.Fx;
import org.junit.jupiter.api.Test;

/** r8 item 5: melee hit reiatsu. */
class MeleeHitRulesTest {
	private static final int ONE_POINT = 10; // tenths

	@Test
	void defaultIsOnePointWhichIsTenTenthsOfAHundredPointBar() {
		BalanceConfig c = BalanceConfig.defaults();
		assertEquals(1.0, c.meleeHitReiatsu(), 1e-9);
		assertEquals(ONE_POINT, c.meleeHitTenths());
		assertEquals(1000, c.maxTenths(), "the bar is 1000 tenths = 100.0 points on the HUD");
		assertEquals(100.0, c.maxTenths() / 10.0, 1e-9);
	}

	@Test
	void aLandedMeleeHitWithAZanpakutoPays() {
		assertEquals(ONE_POINT, MeleeHitRules.reiatsuTenths(true, true, 3.0F, ONE_POINT));
		assertEquals(ONE_POINT, MeleeHitRules.reiatsuTenths(true, true, 0.01F, ONE_POINT), "any damage that landed");
	}

	@Test
	void nothingElseDoes() {
		assertEquals(0, MeleeHitRules.reiatsuTenths(false, true, 3.0F, ONE_POINT), "ability, kido or projectile damage is not melee");
		assertEquals(0, MeleeHitRules.reiatsuTenths(true, false, 3.0F, ONE_POINT), "another weapon or a bare hand");
		assertEquals(0, MeleeHitRules.reiatsuTenths(true, true, 0.0F, ONE_POINT), "a hit that did no damage (blocked to nothing)");
		assertEquals(0, MeleeHitRules.reiatsuTenths(true, true, -1.0F, ONE_POINT));
		assertEquals(0, MeleeHitRules.reiatsuTenths(true, true, Float.NaN, ONE_POINT));
		assertEquals(0, MeleeHitRules.reiatsuTenths(true, true, 3.0F, 0), "switched off");
	}

	@Test
	void theMachineAddsTheTenthsAndClampsAtTheMaximum() {
		Fx f = new Fx(500);
		assertEquals(ONE_POINT, f.sm.grantReiatsu(ONE_POINT));
		assertEquals(510, f.sm.reiatsu().value());
		f.sm.devSetReiatsu(995);
		assertEquals(5, f.sm.grantReiatsu(ONE_POINT), "only what fits below the maximum is added");
		assertEquals(1000, f.sm.reiatsu().value());
		assertEquals(0, f.sm.grantReiatsu(ONE_POINT), "a full bar stays full");
		assertEquals(0, f.sm.grantReiatsu(0));
		assertEquals(0, f.sm.grantReiatsu(-50), "never a drain");
		assertEquals(1000, f.sm.reiatsu().value());
	}

	@Test
	void hundredHitsFillAnEmptyBarAndWorkInAnyState() {
		Fx f = new Fx().toShikai(CharacterId.RUKIA);
		f.sm.devSetReiatsu(0);
		int hits = 0;
		while (f.sm.reiatsu().value() < f.cfg.maxTenths()) {
			f.sm.grantReiatsu(f.cfg.meleeHitTenths());
			hits++;
		}
		assertEquals(100, hits, "1.0 point per hit, 100 points in the bar");
	}

	@Test
	void aDeadPlayerGainsNothing() {
		Fx f = new Fx(300);
		f.sm.onDeath();
		int before = f.sm.reiatsu().value();
		assertEquals(0, f.sm.grantReiatsu(ONE_POINT));
		assertEquals(before, f.sm.reiatsu().value());
		assertEquals(new ReiatsuState(before, 1000), f.sm.reiatsu());
	}
}
