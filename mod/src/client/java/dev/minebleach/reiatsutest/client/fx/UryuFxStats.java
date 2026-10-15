package dev.minebleach.reiatsutest.client.fx;

/** Counters of the Uryu bow, arrow and Focus effects for the harness (client.dev cannot see the package private ones). */
public final class UryuFxStats {
	private UryuFxStats() {
	}

	/** Muzzle flashes played. */
	public static int muzzles() {
		return UryuArrowFx.muzzles;
	}

	/** Arrow hits played. */
	public static int hits() {
		return UryuArrowFx.hits;
	}

	/** Flake sparks spawned along arrows and at hits. */
	public static int sparks() {
		return UryuArrowFx.sparks;
	}

	/** Glow sprites the Focus layers 2 and 3 wrote in the last frame. */
	public static int focusSprites() {
		return UryuFocusFx.lastSprites;
	}

	/** Focus gains (pops) and losses (reset bursts) played. */
	public static int pops() {
		return UryuFocusFx.pops;
	}

	public static int losses() {
		return UryuFocusFx.losses;
	}
}
