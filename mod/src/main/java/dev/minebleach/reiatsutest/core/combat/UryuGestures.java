package dev.minebleach.reiatsutest.core.combat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First person hands of Uryu (design/URYU_VFX_STORYBOARD.md 3.3 to 3.8): the pull of the bow, the release, and the ability gestures, as
 * {@link CastGesture} rows (replace the placeholder thrust of {@link NewCharacterGestures}). R is the bow hand (the bow follows it as one body), L the
 * string / free hand ({@code free} 1: it keeps its own place), C the camera. Every number is a delta from the vanilla bow hold (there is no sword stance),
 * start values: UNVERIFIED in the game, tune with {@code pose_override.json}, section {@code fp_cast.<id>}.
 *
 * <p>{@link #DRAW} is sampled by the DRAW TIME in seconds (ticks / 20, so the rows below are the pull at 0.25 / 0.5 / 1.0 s = 5 / 10 / 20 ticks) and held at its
 * last row for as long as the key is held; {@link #RELEASE} starts at the full draw; the ability gestures start at the effect event. The third person arms
 * of the same gestures are the animations of {@link UryuAnims} (the {@code TR} / {@code TL} rows are not used for Uryu).
 */
public final class UryuGestures {
	public static final String DRAW = "uryu.draw";
	public static final String RELEASE = "uryu.release";

	private static final int SM = CastGesture.E_SMOOTH;
	private static final int OUT = CastGesture.E_OUT;
	private static final int IN = CastGesture.E_IN;

	private static Map<String, CastGesture> all;

	private UryuGestures() {
	}

	/** All Uryu gestures by id (built once). */
	public static synchronized Map<String, CastGesture> all() {
		if (all == null) {
			Map<String, CastGesture> m = new LinkedHashMap<>();
			addTo(m);
			all = java.util.Collections.unmodifiableMap(m);
		}
		return all;
	}

	/** True for the gestures whose first rows are not the rest hold: the shot after a real draw and the rain and the star (they continue the pose of the draw before them). */
	public static boolean startsFromPose(String id) {
		return RELEASE.equals(id) || "uryu.bankai.licht_regen".equals(id) || "uryu.bankai.sternenstaub".equals(id);
	}

	/** True when the gesture has rows for the free hand: the first person arm of the string / free hand is drawn while it runs. */
	public static boolean hasStringHand(String id) {
		CastGesture g = all().get(id);
		return g != null && !g.keys('L').isEmpty();
	}

	/** The first person gesture the pull state machine samples for an animation of {@link UryuAnims} ({@link #DRAW} for the draw, {@link #RELEASE} for the release), or null. */
	public static String rowId(String anim) {
		return UryuAnims.DRAW.equals(anim) ? DRAW : UryuAnims.RELEASE.equals(anim) ? RELEASE : null;
	}

	/** Time in seconds into the rows of {@link #rowId} for an animation tick ({@link #DRAW} holds at its last row for as long as the key is held). */
	public static float rowTime(String anim, double tick, double dur) {
		float t = (float) (tick / UryuBowFlow.TPS);
		return UryuAnims.DRAW.equals(anim) ? (float) Math.min(t, dur) : t;
	}

	static void addTo(Map<String, CastGesture> m) {
		put(m, draw());
		put(m, release(RELEASE));
		// Z: the same release (the arrow leaves at the event), the quick draw before it has no hands of its own
		put(m, release("uryu.shikai.heilig_pfeil"));
		put(m, sprenger());
		put(m, seele());
		put(m, blutArterie());
		put(m, vollstandig());
		put(m, wingBarrage());
		put(m, lichtRegen());
		put(m, sternenstaub());
	}

	private static void put(Map<String, CastGesture> m, CastGesture.Builder b) {
		CastGesture g = b.build();
		m.put(g.id(), g);
	}

	/** 3.3: the bow rises a little and leans to the target, the string hand comes up from below to the right cheek (0.30 model units of string), FOV 1.5 narrower. */
	private static CastGesture.Builder draw() {
		return CastGesture.builder(DRAW).dur(1.0).in(0.04).out(0.2)
				.r(0.00, SM, 0, 0, 0, 0, 0, 0)
				.r(0.25, SM, 0.02, 0.01, -0.03, -4, 0, 0)
				.r(0.50, SM, 0.03, 0.02, -0.06, -8, 0, 2)
				.r(1.00, SM, 0.03, 0.03, -0.08, -10, 0, 3)
				.l(0.00, SM, 0.10, -0.20, 0.10, 0, 0, 0, 1)
				.l(0.50, SM, 0.05, 0.04, 0.12, 0, 0, 0, 1)
				.l(1.00, SM, -0.02, 0.10, 0.22, 0, 0, 0, 1)
				.c(1.00, SM, 0, 0, 0, -1.5);
	}

	/** 3.4: the bow kicks up, the string hand leaves back and out of the picture, the shot recoil of the camera. */
	private static CastGesture.Builder release(String id) {
		// the shot after a real draw starts at the pose the draw ended in; the quick draw of the Z arrow has no draw of its own and brings the hands in over 0.1 s
		return CastGesture.builder(id).dur(0.5).in(RELEASE.equals(id) ? 0.0 : 0.10).out(0.2)
				.r(0.00, SM, 0.03, 0.03, -0.08, -10, 0, 3)
				.r(0.05, OUT, 0.03, 0.04, -0.02, -4, 0, 4)
				.r(0.25, SM, 0.01, 0.01, 0.0, 0, 0, 0)
				.r(0.50, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.02, 0.10, 0.22, 0, 0, 0, 1)
				.l(0.05, OUT, 0.12, 0.14, 0.40, 8, -10, 6, 1)
				.l(0.30, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.00, SM, 0, 0, 0, 0)
				.c(0.05, OUT, 0.4, 0, 0, 0)
				.c(0.25, SM, 0, 0, 0, 0);
	}

	/** 3.5 Sprenger: five overhand tosses of 0.2 s with the bow hand (the dagger leaves at the top), then the hand holds the guard until the explosion. */
	private static CastGesture.Builder sprenger() {
		CastGesture.Builder b = CastGesture.builder("uryu.shikai.sprenger").dur(2.4).out(0.3);
		for (int i = 0; i < 5; i++) {
			double t = 0.2 * i;
			if (i == 0) {
				b.r(t, SM, 0.06, -0.10, 0.05, 20, 0, 6);
			}
			b.r(t + 0.10, OUT, 0.02, 0.10, -0.12, -50, 0, -4);
			b.r(t + 0.20, IN, 0.06, -0.10, 0.05, 20, 0, 6);
		}
		return b.r(1.15, SM, 0.0, -0.05, -0.10, -30, 0, 0)
				.r(2.00, SM, 0.0, -0.05, -0.10, -30, 0, 0)
				.r(2.40, SM, 0, 0, 0, 0, 0, 0);
	}

	/** 3.6 Seele Schneider: the free hand (the blade is drawn by the effect) sweeps across, back, up and chops down while the bow hand tucks the bow away. */
	private static CastGesture.Builder seele() {
		return CastGesture.builder("uryu.shikai.seele_schneider").dur(0.9).out(0.25)
				.r(0.00, SM, 0.05, -0.08, 0.02, 12, 0, 6)
				.r(0.90, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.26, 0.04, -0.20, 0, 0, 0, 1)
				.l(0.15, IN, 0.12, 0.00, -0.38, 0, -30, 20, 1)
				.l(0.40, IN, -0.30, 0.04, -0.34, 0, 25, -20, 1)
				.l(0.60, IN, -0.02, 0.28, -0.28, 0, 0, 0, 1)
				.l(0.65, OUT, -0.02, -0.12, -0.42, 0, 0, 0, 1)
				.l(0.90, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.15, OUT, 0, 1.5, 0, 3)
				.c(0.40, OUT, 0, -1.5, 0, 0)
				.c(0.65, OUT, -2.5, 0, 0, 2)
				.c(0.90, SM, 0, 0, 0, 0);
	}

	/** 3.7 Blut Arterie release: the bow hand crosses to the chest and is flung out, the free hand rises from below, the same on the vein burst at 0.55 s. */
	private static CastGesture.Builder blutArterie() {
		return CastGesture.builder(UryuAnims.RELEASE_SHIKAI).dur(1.2).out(0.4)
				.r(0.00, SM, 0, 0, 0, 0, 0, 0)
				.r(0.30, SM, -0.12, 0.10, -0.12, 12, -20, 18)
				.r(0.55, OUT, 0.20, -0.04, -0.04, -6, 18, -16)
				.r(1.00, SM, 0.06, 0, -0.02, 0, 0, 0)
				.r(1.20, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.10, -0.60, 0.0, 0, 0, 0, 1)
				.l(0.30, SM, 0.15, 0.00, -0.30, 0, -30, -10, 1)
				.l(0.55, OUT, -0.42, 0.10, -0.18, 0, 25, -20, 1)
				.l(1.20, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.55, OUT, 2, 0, 0, 3.5)
				.c(1.00, SM, 0, 0, 0, 0);
	}

	/** 3.8 Quincy: Vollstaendig: the hands cross, then are thrown wide and up on the burst at 0.65 s, the camera gathers (FOV narrower) and opens. */
	private static CastGesture.Builder vollstandig() {
		return CastGesture.builder(UryuAnims.RELEASE_BANKAI).dur(2.2).out(0.5)
				.r(0.00, SM, 0, 0, 0, 0, 0, 0)
				.r(0.25, SM, -0.10, 0.08, -0.10, 14, -22, 16)
				.r(0.65, OUT, 0.34, 0.10, -0.02, -10, 25, -30)
				.r(1.00, SM, 0.18, 0.06, -0.04, -4, 8, -14)
				.r(1.60, SM, 0.06, 0, 0, 0, 0, 0)
				.r(2.20, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.10, -0.60, 0, 0, 0, 0, 1)
				.l(0.25, SM, 0.14, 0.02, -0.28, 0, -30, -8, 1)
				.l(0.65, OUT, -0.52, 0.12, -0.10, 0, 30, -30, 1)
				.l(1.00, SM, -0.30, 0.04, -0.12, 0, 15, -15, 1)
				.l(2.00, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.25, SM, -1.5, 0, 0, -1)
				.c(0.65, OUT, 2.5, 0, 0, 3.5)
				.c(1.80, SM, 0, 0, 0, 0);
	}

	/** 3.5 wing barrage: the bow hand turned out to the side, the free hand forward, a slow tremor while the wings fire (6 s). */
	private static CastGesture.Builder wingBarrage() {
		return CastGesture.builder("uryu.bankai.wing_barrage").dur(6.4).out(0.4)
				.r(0.00, OUT, 0.04, -0.02, -0.08, -8, 0, -22)
				.r(6.00, SM, 0.04, -0.02, -0.08, -8, 0, -22)
				.r(6.40, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, OUT, -0.08, 0.04, -0.08, 0, 0, 0, 1)
				.l(6.00, SM, -0.08, 0.04, -0.08, 0, 0, 0, 1)
				.l(6.40, SM, 0, 0, 0, 0, 0, 0, 0)
				.vib('R', 0.2, 6.0, 0.004, 4);
	}

	/**
	 * 3.5 Licht Regen: the effect event comes at the RELEASE (0.6 s after the key), so the rows start with the bow already raised (storyboard time minus 0.4 s):
	 * the bow hand goes up toward the sky, the string hand flicks away on the release, the camera looks up (4 degrees: the camera nudge of the repository stays at 4 degrees, CastGestureTest; the storyboard had 9), everything held for the 3 s of rain.
	 */
	private static CastGesture.Builder lichtRegen() {
		return CastGesture.builder("uryu.bankai.licht_regen").dur(3.8).in(0.0).out(0.4)
				.r(0.00, SM, 0.04, 0.12, -0.10, 40, 0, 4)
				.r(0.20, OUT, 0.05, 0.18, -0.12, 55, 0, 6)
				.r(3.50, SM, 0.04, 0.10, -0.08, 30, 0, 3)
				.r(3.80, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.02, 0.10, 0.22, 0, 0, 0, 1)
				.l(0.20, OUT, 0.12, 0.06, 0.40, 8, -10, 6, 1)
				.l(0.60, SM, 0.10, 0.04, 0.30, 4, -6, 4, 1)
				.l(3.60, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.00, SM, 2, 0, 0, -2)
				.c(0.20, SM, 4, 0, 0, -3)
				.c(3.50, SM, 0, 0, 0, 0)
				.vib('R', 0.4, 3.4, 0.003, 10);
	}

	/**
	 * 3.5 Sternenstaub: the effect event comes at the release (0.8 s), the rows start at the full draw: the bow kicks up with twice the recoil of the basic shot,
	 * the string hand leaves farther (z 0.62: the first person rows of the repository stay under 0.65 blocks, CastGestureTest), the camera pushes back and opens.
	 */
	private static CastGesture.Builder sternenstaub() {
		return CastGesture.builder("uryu.bankai.sternenstaub").dur(1.6).in(0.0).out(0.4)
				.r(0.00, SM, 0.03, 0.03, -0.08, -10, 0, 3)
				.r(0.05, OUT, 0.03, 0.08, 0.04, 2, 0, 8)
				.r(0.40, SM, 0.01, 0.02, 0.0, 0, 0, 0)
				.r(1.60, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.02, 0.10, 0.22, 0, 0, 0, 1)
				.l(0.05, OUT, 0.24, 0.16, 0.62, 16, -20, 12, 1)
				.l(0.50, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.00, SM, 0, 0, 0, -3)
				.c(0.05, OUT, 1.2, 0, 0, 5)
				.c(0.60, SM, 0, 0, 0, 0);
	}
}
