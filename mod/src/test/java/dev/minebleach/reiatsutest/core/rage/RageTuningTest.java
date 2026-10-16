package dev.minebleach.reiatsutest.core.rage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** R11 review fix 4: the HUD takes the Rage costs and the minimum shikai time from the server config (RageTuning), not from the code defaults. */
class RageTuningTest {
	private static RageTuning from(String json) {
		return RageTuning.of(RageConfig.fromRoot(JsonParser.parseString(json).getAsJsonObject()));
	}

	@Test
	void defaultsAreTheRageConfigDefaults() {
		RageConfig d = RageConfig.defaults();
		RageTuning t = RageTuning.defaults();
		assertEquals(d.shikaiCostTenths(), t.shikaiCostTenths());
		assertEquals(d.bankaiCostTenths(), t.bankaiCostTenths());
		assertEquals(d.bankaiMinShikaiTicks(), t.bankaiMinShikaiTicks());
		assertEquals(d.releaseCostTenths(true), t.releaseCostTenths(true));
		assertEquals(d.releaseCostTenths(false), t.releaseCostTenths(false));
	}

	@Test
	void aChangedServerConfigReachesTheTuning() {
		RageTuning t = from("{\"rage\": {\"shikaiCostTenths\": 100, \"bankaiCostTenths\": 500, \"bankaiMinShikaiTicks\": 0}}");
		assertEquals(0, t.releaseCostTenths(false), "R14: shikai is free, the old key in the file is ignored");
		assertEquals(500, t.releaseCostTenths(true));
		assertEquals(0, t.bankaiMinShikaiTicks());
		assertFalse(t.bankaiAffordable(499));
		assertTrue(t.bankaiAffordable(500));
	}

	@Test
	void holdLeftFollowsTheConfiguredMinimumNotThe20SecondDefault() {
		RageTuning none = from("{\"rage\": {\"bankaiMinShikaiTicks\": 0}}");
		assertEquals(0.0, none.shikaiHoldLeftSeconds(true, 5.0, 4.9), "0 ticks: bankai at once (the old client waited 20 s)");
		RageTuning long60 = from("{\"rage\": {\"bankaiMinShikaiTicks\": 1200}}");
		assertEquals(55.0, long60.shikaiHoldLeftSeconds(true, 5.0, 0.0), 1e-9, "60 s minimum, 5 s in shikai");
		assertEquals(0.0, long60.shikaiHoldLeftSeconds(true, 61.0, 0.0), 1e-9);
		RageTuning dflt = RageTuning.defaults();
		assertEquals(20.0 - 5.0, dflt.shikaiHoldLeftSeconds(true, 5.0, 0.0), 1e-9);
	}

	@Test
	void holdLeftIsZeroOutsideShikaiOrWithoutAStart() {
		RageTuning t = RageTuning.defaults();
		assertEquals(0.0, t.shikaiHoldLeftSeconds(false, 5.0, 0.0));
		assertEquals(0.0, t.shikaiHoldLeftSeconds(true, 5.0, -1.0), "state start unknown");
	}

	@Test
	void theToastShowsAtLeastOneSecondAndRoundsUp() {
		assertEquals(1, RageTuning.toastSeconds(0.0), "refused by the server although the client clock says it is over");
		assertEquals(1, RageTuning.toastSeconds(0.2));
		assertEquals(4, RageTuning.toastSeconds(3.1));
		assertEquals(3, RageTuning.toastSeconds(3.0));
	}

	@Test
	void negativeNumbersAreClamped() {
		RageTuning t = new RageTuning(-5, -1, -100);
		assertEquals(0, t.shikaiCostTenths());
		assertEquals(0, t.bankaiCostTenths());
		assertEquals(0, t.bankaiMinShikaiTicks());
	}
}
