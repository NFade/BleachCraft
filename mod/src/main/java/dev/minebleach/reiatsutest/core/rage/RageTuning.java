package dev.minebleach.reiatsutest.core.rage;

/**
 * R11 review fix 4: the three numbers of the Rage section that the HUD needs and that the server config can change
 * ({@code rage.shikaiCostTenths}, {@code rage.bankaiCostTenths}, {@code rage.bankaiMinShikaiTicks}). The server sends them to the owner with the Rage bar
 * ({@code ModAttachments.RAGE_TUNING}); until the first sync arrives the client uses {@link #defaults()}. The HUD questions ("is the bankai gem ready",
 * "how long until bankai", "how much does the release cost") are answered here so they can be tested. Pure Java.
 */
public record RageTuning(int shikaiCostTenths, int bankaiCostTenths, int bankaiMinShikaiTicks) {
	public RageTuning {
		shikaiCostTenths = Math.max(0, shikaiCostTenths);
		bankaiCostTenths = Math.max(0, bankaiCostTenths);
		bankaiMinShikaiTicks = Math.max(0, bankaiMinShikaiTicks);
	}

	/** The code defaults (what the client assumes before the first sync). */
	public static RageTuning defaults() {
		return of(RageConfig.defaults());
	}

	public static RageTuning of(RageConfig c) {
		return new RageTuning(c.shikaiCostTenths(), c.bankaiCostTenths(), c.bankaiMinShikaiTicks());
	}

	/** Rage needed for the release into shikai (not spent) or bankai (spent), in tenths. */
	public int releaseCostTenths(boolean bankai) {
		return bankai ? bankaiCostTenths : shikaiCostTenths;
	}

	/**
	 * Seconds of shikai still needed before bankai can be requested: 0 when not in shikai, when the state start is unknown (negative) or when the
	 * minimum time has passed. Times are client seconds on the same clock.
	 */
	public double shikaiHoldLeftSeconds(boolean inShikai, double nowSeconds, double stateChangeAtSeconds) {
		if (!inShikai || stateChangeAtSeconds < 0) {
			return 0.0;
		}
		return Math.max(0.0, bankaiMinShikaiTicks / 20.0 - (nowSeconds - stateChangeAtSeconds));
	}

	/** True when {@code rageTenths} pays the bankai release. */
	public boolean bankaiAffordable(int rageTenths) {
		return rageTenths >= bankaiCostTenths;
	}

	/**
	 * The whole seconds the toast "needs N more s" shows: the time left rounded up, at least 1 (the server refused, so some time is left even if the
	 * client clock thinks it is over: latency).
	 */
	public static long toastSeconds(double holdLeftSeconds) {
		return (long) Math.max(1.0, Math.ceil(holdLeftSeconds));
	}
}
