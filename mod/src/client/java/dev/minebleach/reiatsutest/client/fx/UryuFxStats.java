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

	// ------------------------------------------------------------------ Sprenger (U-FX 4)

	/** Harness: forgets every pentagon, root and dagger and zeroes the Sprenger counters. */
	public static void sprengerReset() {
		UryuSprengerFx.reset();
	}

	/** TRAP packets, BURST packets and cancelled pentagons seen. */
	public static int sprengerTraps() {
		return UryuSprengerFx.trapsSeen;
	}

	public static int sprengerBursts() {
		return UryuSprengerFx.burstsSeen;
	}

	public static int sprengerCancels() {
		return UryuSprengerFx.cancels;
	}

	/** Pentagons alive, fence curtains drawn in the last frame, daggers drawn in the last frame. */
	public static int sprengerLive() {
		return UryuSprengerFx.count();
	}

	public static int sprengerCurtains() {
		return UryuSprengerFx.lastCurtains;
	}

	public static int daggersDrawn() {
		return UryuSeeleRenderer.lastDrawn;
	}

	/** BIND packets seen, roots alive, roots drawn in full and chains drawn in the last frame. */
	public static int binds() {
		return UryuBindFx.bindsSeen;
	}

	public static int roots() {
		return UryuBindFx.count();
	}

	public static int rootsFull() {
		return UryuBindFx.lastFull;
	}

	public static int rootChains() {
		return UryuBindFx.lastChains;
	}

	// ------------------------------------------------------------------ wings (U-FX 7)

	/** Stardust specks and tip stars spawned along the wings; ends of the bankai state handled (wings broken into flakes). */
	public static int wingSpecks() {
		return UryuWingFx.specks;
	}

	public static int wingTipStars() {
		return UryuWingFx.tipStars;
	}

	public static int wingEnds() {
		return UryuWingFx.ends;
	}

	/** Releases (effect 71) seen. */
	public static int wingReleases() {
		return UryuWingFx.releases;
	}
}
