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

	/** Wing barrage (U-FX 8): tip fires seen, dashes of the flare, cross sprites emitted. */
	public static int wingTipFires() {
		return UryuWingFx.tipFires;
	}

	public static int wingBarrageDashes() {
		return UryuWingFx.barrageDashes;
	}

	public static int wingCrossSprites() {
		return UryuWingFx.crossSprites;
	}

	/** Licht Regen (U-FX 9): outline quads, sky card quads, landings seen, splashes drawn, puffs, glitter and haze specks. */
	public static int rainOutlineQuads() {
		return UryuRainFx.outlineQuads;
	}

	public static int rainCardQuads() {
		return UryuRainFx.cardQuads;
	}

	public static int rainLandings() {
		return UryuRainFx.landings;
	}

	public static int rainSplashes() {
		return UryuRainFx.splashes;
	}

	public static int rainSpecks() {
		return UryuRainFx.specks;
	}

	/** Releases (effect 71) seen. */
	public static int wingReleases() {
		return UryuWingFx.releases;
	}

	public static int starCasts() {
		return UryuStarFx.casts;
	}

	public static int starLaunches() {
		return UryuStarFx.launches;
	}

	public static int starFlakeQuads() {
		return UryuStarFx.flakeQuads;
	}

	public static int starBeamQuads() {
		return UryuStarFx.beamQuads;
	}

	public static int starRings() {
		return UryuStarFx.rings;
	}

	public static int starSpecks() {
		return UryuStarFx.specks;
	}

	public static int starBursts() {
		return UryuStarFx.bursts;
	}

	public static int shunpoDashes() {
		return UryuShunpoFx.dashes;
	}

	public static int shunpoDiscs() {
		return UryuShunpoFx.discsSpawned;
	}

	/** Harness: drops the Quincy shunpo platforms in flight. */
	public static void clearShunpo() {
		UryuShunpoFx.clear();
	}

	/** Harness: drops the Sternenstaub casts in flight. */
	public static void clearStar() {
		UryuStarFx.clear();
	}
}
