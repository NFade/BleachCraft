package dev.minebleach.reiatsutest.core.combat;

import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shipped gestures of the abilities (design/FP_ABILITY_ANIMS.md, stage 1) and the table from an {@code effect_event} id to the
 * ability id. All numbers are deltas from the held stance (see {@link CastGesture}), laid out from the storyboard timings of
 * design/VFX_STORYBOARD.md; none of them has been seen in the game (UNVERIFIED), every one can be replaced from
 * {@code pose_override.json}, section {@code fp_cast.<ability id>}.
 *
 * <p>Convention reminders: forward is -z, {@code rx} below 0 tips the blade forward and down (the stance is about vertical, level is
 * about -90 from it), {@code rx} above 0 tips it back over the shoulder; positive camera pitch looks up.
 */
public final class CastGestures {
	/** Id of the sustained gesture of steering the swarm (see {@code ClientState.steering()}). */
	public static final String STEER = "steer";
	public static final String SEAL = "common.seal";
	public static final String RUKIA_SHIKAI = "rukia.shikai.release";
	public static final String RUKIA_BANKAI = "rukia.bankai.release";
	public static final String BYAKUYA_SHIKAI = "byakuya.shikai.release";
	public static final String BYAKUYA_BANKAI = "byakuya.bankai.release";
	/** Seconds of the pink hold of the Byakuya release in the effect, after which the gesture jerks the hand forward. */
	public static final float PINK_HOLD = 0.4f;

	private static final int SM = CastGesture.E_SMOOTH;
	private static final int OUT = CastGesture.E_OUT;
	private static final int IN = CastGesture.E_IN;
	private static final int LIN = CastGesture.E_LINEAR;
	private static final int BK = CastGesture.E_BACK;

	private static final Map<String, CastGesture> DEFAULTS = build();

	private CastGestures() {
	}

	public static Map<String, CastGesture> defaults() {
		return DEFAULTS;
	}

	/** Ability id of an effect event, or null when the event has no gesture (shunpo, block feedback, the end of the bankai). Senkei (34) is disabled in the game, its gesture is ready. */
	public static String idForEffect(int effectId) {
		switch (effectId) {
			case EffectIds.RUKIA_SHIKAI_RELEASE:
				return RUKIA_SHIKAI;
			case EffectIds.RUKIA_BANKAI_RELEASE:
				return RUKIA_BANKAI;
			case EffectIds.BYAKUYA_SHIKAI_RELEASE:
				return BYAKUYA_SHIKAI;
			case EffectIds.BYAKUYA_BANKAI_RELEASE:
				return BYAKUYA_BANKAI;
			case EffectIds.SEAL:
				return SEAL;
			default:
				break;
		}
		for (AbilityId a : AbilityId.values()) {
			if (effectOf(a) == effectId) {
				return a.commandId;
			}
		}
		for (KidoId k : KidoId.values()) {
			if (k.effectId == effectId) {
				return k.commandId;
			}
		}
		return null;
	}

	/** Effect id of an ability (the numbers of {@code BalanceConfig}: 20 to 23 Rukia, 30 to 34 Byakuya). */
	private static int effectOf(AbilityId a) {
		return switch (a) {
			case TSUKISHIRO -> 20;
			case HAKUREN -> 21;
			case SHIRAFUNE -> 22;
			case ICE_SPIKE -> 24;
			case MODE_ATTACK -> 30;
			case MODE_BARRIER -> 31;
			case SCATTER -> 32;
			case HAKUTEIKEN -> 33;
			case SENKEI -> 34;
			case CAGE -> 35;
			case IKOROSE -> 62;
			case HYAPPONZASHI -> 63;
			case SNAKE_HOOK -> 64;
			case BUTOU_RENJIN -> 65;
			case KOROSE -> 66;
			case HAKUFUKU -> 67;
			case HEILIG_PFEIL -> 72;
			case SPRENGER -> 73;
			case HIRENKYAKU -> 74;
			case LICHT_REGEN -> 75;
			case HOMING_VOLLEY -> 76;
			case STERNENSTAUB -> 77;
		};
	}

	/** The gesture with the Byakuya release adjusted to the pink hold of the effect ({@code release.pinkTime}). */
	public static CastGesture withPinkHold(CastGesture g, double pinkSeconds) {
		return g.shiftedAfter(PINK_HOLD, (float) pinkSeconds - PINK_HOLD);
	}

	private static void put(Map<String, CastGesture> m, CastGesture.Builder b) {
		CastGesture g = b.build();
		m.put(g.id(), g);
	}

	/** Third person: the main arm goes up to {@code rp} (pitch deg), the other to {@code lp} with {@code lroll} outward, over {@code up} s, back at the end. */
	private static CastGesture.Builder tp(CastGesture.Builder b, double up, double rp, double lp, double lroll) {
		return tp(b, up, rp, lp, lroll, b.build().dur());
	}

	private static CastGesture.Builder tp(CastGesture.Builder b, double up, double rp, double lp, double lroll, double end) {
		double e = Math.max(up + 0.1, end - 0.3);
		b.tr(up, OUT, rp, 0, 0).tr(e, SM, rp, 0, 0).tr(end, SM, 0, 0, 0);
		b.tl(up, OUT, lp, 0, lroll).tl(e, SM, lp, 0, lroll).tl(end, SM, 0, 0, 0);
		return b;
	}

	private static Map<String, CastGesture> build() {
		Map<String, CastGesture> m = new LinkedHashMap<>();

		// ---- Rukia, shikai release (Mae): the blade comes up beside the face, the left hand strokes its back, the blade turns forward
		put(m, tp(CastGesture.builder(RUKIA_SHIKAI).dur(1.3).out(0.35)
				.r(0.12, SM, 0.03, -0.01, 0.07, 10, 0, 0)
				.r(0.32, OUT, 0.05, 0.16, -0.10, -4, 0, 10)
				.r(0.58, SM, 0.02, 0.06, -0.14, -52, -18, 0)
				.r(0.85, SM, 0.04, 0.03, -0.12, -46, -18, 0)
				.r(1.30, SM, 0, 0, 0, 0, 0, 0)
				.l(0.12, SM, 0, 0, 0.02, 0, 0, 0, 0)
				.l(0.32, OUT, 0, 0.04, -0.06, 0, 0, 0, 0)
				.l(0.50, SM, 0, 0.22, -0.06, 0, 0, 0, 0)
				.l(0.75, SM, 0, 0.02, 0, 0, 0, 0, 0)
				.l(1.30, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.30, SM, 1.5, 0, 0, 3)
				.c(0.80, SM, 0, 0, 0, 0), 0.30, -50, -20, 20));

		// ---- Rukia, Tsukishiro: the tip draws the circle, the blade lies forward over the freeze, a lash on the break
		put(m, tp(CastGesture.builder("rukia.shikai.tsukishiro").dur(3.0).out(0.4)
				.r(0.10, SM, 0.04, -0.05, 0.06, 14, 0, 0)
				.r(0.32, SM, 0, -0.10, -0.10, -70, 0, 0)
				.r(0.55, SM, 0, -0.04, -0.10, -82, 0, 0)
				.r(1.00, SM, 0, -0.02, -0.15, -72, 0, 0)
				.r(1.80, SM, 0, -0.02, -0.15, -70, 0, 0)
				.r(2.00, SM, 0.07, 0.02, -0.04, -40, 0, 8)
				.r(2.10, OUT, -0.08, -0.10, -0.18, -96, 0, -14)
				.r(2.45, SM, 0, -0.02, -0.06, -50, 0, 0)
				.r(3.00, SM, 0, 0, 0, 0, 0, 0)
				.orbit('T', 0.06, 0.34, 24, 3.0, 0.25)
				.vib('R', 1.0, 1.9, 0.004, 3)
				.l(0.30, OUT, -0.12, -0.16, -0.12, 0, 0, 0, 1)
				.l(0.55, SM, -0.16, 0.08, -0.22, 0, 0, 0, 1)
				.l(1.00, SM, 0.05, -0.02, -0.12, 0, 0, 0, 1)
				.l(2.00, SM, 0.03, -0.02, -0.10, 0, 0, 0, 1)
				.l(2.10, OUT, -0.22, -0.06, -0.08, 0, 0, 0, 1)
				.l(2.60, SM, -0.05, -0.02, 0, 0, 0, 0, 0.5)
				.l(3.00, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(1.00, SM, -1.0, 0, 0, 0)
				.c(1.80, SM, -1.0, 0, 0, 0)
				.c(2.10, OUT, -2.0, 0, 0, 1.5)
				.c(2.70, SM, 0, 0, 0, 0), 0.30, -45, -60, 25));

		// ---- Rukia, Hakuren: the left hand to the ground (ice spikes), the blade gathered at the hip, the lunge along the wave
		put(m, tp(CastGesture.builder("rukia.shikai.hakuren").dur(3.1).out(0.4)
				.r(0.50, SM, 0.04, -0.04, 0.04, 8, 0, 0)
				.r(0.70, SM, 0.07, -0.10, 0.06, 20, 0, 6)
				.r(0.98, IN, 0.04, -0.06, 0.03, 12, 0, 4)
				.r(1.10, OUT, 0, -0.05, -0.26, -66, 0, 0)
				.r(1.30, SM, 0, -0.04, -0.22, -60, 0, 0)
				.r(2.60, SM, 0, -0.03, -0.24, -62, 0, 0)
				.r(3.10, SM, 0, 0, 0, 0, 0, 0)
				.l(0.18, OUT, -0.06, -0.10, -0.16, 20, 0, 0, 1)
				.l(0.30, SM, -0.06, -0.08, -0.16, 0, 0, 0, 1)
				.l(0.65, SM, -0.04, -0.05, -0.20, 0, 0, 0, 1)
				.l(1.00, OUT, -0.05, 0.06, -0.44, -35, 0, 0, 1)
				.l(2.60, SM, -0.05, 0.03, -0.38, -30, 0, 0, 1)
				.l(3.10, SM, 0, 0, 0, 0, 0, 0, 0)
				.vib('L', 0.30, 0.52, 0.03, 10)
				.c(1.00, OUT, 0, 0, 0, 2)
				.c(1.40, SM, -1, 0, 0, 0)
				.c(2.60, SM, 0, 0, 0, 0), 0.35, -40, 20, 10));

		// ---- Rukia, Shirafune: one exact thrust, the left hand points at the target
		put(m, tp(CastGesture.builder("rukia.shikai.shirafune").dur(2.4).out(0.35)
				.r(0.00, OUT, 0.09, -0.16, 0.06, 26, 0, 8)
				.r(0.30, SM, 0.03, -0.08, -0.10, -30, 0, 4)
				.r(0.55, IN, 0, -0.04, -0.14, -55, 0, 0)
				.r(0.64, OUT, 0, -0.02, -0.30, -72, 0, 0)
				.r(0.76, SM, 0, -0.03, -0.26, -68, 0, 0)
				.r(2.00, SM, 0, -0.03, -0.26, -68, 0, 0)
				.r(2.40, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, OUT, -0.04, 0.02, -0.28, -30, 0, 0, 1)
				.l(0.90, SM, -0.05, 0.04, -0.30, -32, 0, 0, 1)
				.l(1.90, SM, -0.05, 0.04, -0.30, -32, 0, 0, 1)
				.l(2.40, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.30, OUT, 0, 0, 0, 2)
				.c(0.64, SM, -1, 0, 0, 0)
				.c(0.90, SM, 0, 0, 0, 0), 0.25, -80, -70, 10));

		// ---- Rukia, bankai release: the blade overhead on the detonation, the left hand thrown out, the blade lowered to the hip
		put(m, tp(CastGesture.builder(RUKIA_BANKAI).dur(2.6).out(0.4)
				.r(0.00, SM, 0, 0.03, -0.08, -8, 0, 0)
				.r(0.12, OUT, 0.04, 0.24, -0.06, 50, 0, 12)
				.r(0.55, SM, 0.04, 0.22, -0.06, 46, 0, 12)
				.r(1.00, SM, 0.04, 0.08, -0.12, 8, 0, 6)
				.r(1.25, SM, 0.02, 0.02, -0.14, -30, 0, 0)
				.r(2.00, SM, 0, -0.03, -0.06, -48, 0, 0)
				.r(2.60, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, 0.08, 0, -0.14, 0, 0, 0, 0.6)
				.l(0.12, OUT, -0.28, 0.12, -0.10, 0, -25, 0, 1)
				.l(0.30, SM, -0.30, 0.14, -0.10, 0, -25, 0, 1)
				.l(0.86, SM, -0.30, 0.12, -0.10, 0, -25, 0, 1)
				.l(0.96, OUT, -0.42, 0.16, -0.08, 0, -25, 0, 1)
				.l(1.12, BK, -0.30, 0.06, -0.12, 0, -25, 0, 1)
				.l(2.00, SM, -0.12, -0.04, -0.10, 0, 0, 0, 1)
				.l(2.60, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.12, OUT, 0, 0, 0, 3)
				.c(0.45, SM, 0, 0, 0, 0)
				.c(1.20, SM, -1, 0, 0, 0)
				.c(2.00, SM, 0, 0, 0, 0), 0.12, -150, -20, 60, 2.2));

		// ---- Byakuya, shikai release (Chire): the hilt at the face through the pink hold, then the hand thrown forward, the left hand conducts
		put(m, tp(CastGesture.builder(BYAKUYA_SHIKAI).dur(2.2).out(0.4)
				.r(0.10, SM, 0.02, -0.04, 0.04, 8, 0, 0)
				.r(0.28, OUT, 0.04, 0.12, -0.10, -6, 0, 0)
				.r(0.40, SM, 0.04, 0.12, -0.10, -6, 0, 0)
				.r(0.58, OUT, 0.05, 0.08, -0.34, -52, 0, 10)
				.r(0.90, SM, 0.08, -0.02, -0.26, -34, 0, 4)
				.r(1.50, SM, 0.08, -0.04, -0.22, -28, 0, 0)
				.r(2.20, SM, 0, 0, 0, 0, 0, 0)
				.l(0.30, SM, 0, 0, 0, 0, 0, 0, 0)
				.l(0.45, OUT, -0.14, 0.12, -0.28, -25, 0, 0, 1)
				.l(1.60, SM, -0.12, 0.08, -0.26, -25, 0, 0, 1)
				.l(2.20, SM, 0, 0, 0, 0, 0, 0, 0)
				.orbit('L', 0.5, 1.4, 0.06, 1.5, 0)
				.orbit('L', 1.4, 2.0, 0.04, 0.8, 0)
				.c(0.60, OUT, -1, 0, 0, 0)
				.c(0.90, SM, 0, 0, 0, 0), 0.30, -60, -40, 15));

		// ---- Byakuya, shikai attack: the hilt swings to the aim, three pokes on the hits, the left hand points and trembles
		put(m, tp(CastGesture.builder("byakuya.shikai.mode_attack").dur(2.2).out(0.35)
				.r(0.00, OUT, 0.10, 0.02, -0.20, -34, -24, 0)
				.r(0.20, SM, 0.08, 0, -0.22, -45, -20, 0)
				.r(0.45, SM, 0.06, -0.01, -0.18, -40, 0, 0)
				.r(0.50, OUT, 0.06, -0.01, -0.27, -48, 0, 0)
				.r(0.55, SM, 0.06, -0.01, -0.18, -40, 0, 0)
				.r(0.60, OUT, 0.06, -0.01, -0.27, -48, 0, 0)
				.r(0.65, SM, 0.06, -0.01, -0.18, -40, 0, 0)
				.r(0.70, OUT, 0.06, -0.01, -0.27, -48, 0, 0)
				.r(0.76, SM, 0.06, -0.01, -0.18, -40, 0, 0)
				.r(1.00, SM, 0.08, -0.05, -0.20, -30, 0, 0)
				.r(1.60, SM, 0.08, -0.05, -0.20, -28, 0, 0)
				.r(2.20, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, -0.06, -0.10, -0.12, 0, 0, 0, 1)
				.l(0.50, OUT, -0.05, 0.02, -0.44, -30, 0, 0, 1)
				.l(1.50, SM, -0.05, 0.02, -0.40, -30, 0, 0, 1)
				.l(2.20, SM, 0, 0, 0, 0, 0, 0, 0)
				.vib('L', 1.0, 1.5, 0.01, 10)
				.c(0.50, OUT, -0.5, 0, 0, 0)
				.c(0.80, SM, 0, 0, 0, 0), 0.20, -50, -60, 10));

		// ---- Byakuya, shikai barrier: the hilt upright in front of the chest, the left hand draws the dome over it
		put(m, tp(CastGesture.builder("byakuya.shikai.mode_barrier").dur(5.4).out(0.5)
				.r(0.00, SM, 0.05, 0.04, -0.12, 0, 0, 0)
				.r(0.50, SM, 0.04, 0.03, -0.14, -6, 0, 0)
				.r(4.90, SM, 0.04, 0.03, -0.14, -6, 0, 0)
				.r(5.40, SM, 0, 0, 0, 0, 0, 0)
				.l(0.00, SM, 0, 0, -0.08, 0, 0, 0, 0.7)
				.l(0.30, SM, -0.18, 0.14, -0.14, -20, 0, 0, 1)
				.l(0.45, SM, -0.22, 0.30, -0.10, -45, 0, 0, 1)
				.l(0.60, SM, -0.08, 0.34, -0.06, -70, 0, 0, 1)
				.l(4.90, SM, -0.08, 0.34, -0.06, -70, 0, 0, 1)
				.l(5.40, SM, 0, 0, 0, 0, 0, 0, 0)
				.orbit('L', 0.7, 4.9, 0.008, 0.5, 0), 0.40, -40, -90, 25, 5.0));

		// ---- Byakuya, bankai release (empty hands, round 6): the hilt is dropped, both hands open palms up, then they part to the sides while the rows rise.
		// Rows of the empty hands: px py pz (shift from the idle fist), then a rotation VECTOR in degrees (see FreeHands); key poses laid out in a python
		// model of the arm chain (design/results/fp_swing_round6.md): palms up in front 0.2-0.9, parting 1.25-2.3, back to the idle 2.85-3.4.
		put(m, tp(CastGesture.builder(BYAKUYA_BANKAI).dur(3.4).out(0.4).freeHands(true)
				.r(0.2, OUT, -0.03, -0.15, 0.05, -66.3, 54.1, -109.2)
				.r(0.9, SM, -0.01, -0.11, 0.03, -63.1, 53.3, -109.8)
				.r(1.25, SM, 0.1, 0.01, -0.01, -41.7, 40.8, -115.9)
				.r(1.7, SM, 0.32, 0.11, -0.1, -8.6, 47.5, -116.4)
				.r(2.3, SM, 0.35, 0.09, -0.08, -4.7, 48.6, -120.9)
				.r(2.85, SM, 0.16, 0.01, -0.03, -48.4, 44.9, -114.0)
				.r(3.4, SM, 0, 0, 0, 0, 0, 0)
				.l(0.2, OUT, 0.39, 0.04, 0.04, -44.3, -46.1, 81.7, 1)
				.l(0.9, SM, 0.37, 0.08, 0.02, -41.8, -44.6, 82.0, 1)
				.l(1.25, SM, 0.24, 0.21, -0.02, -26.4, -27.9, 86.1, 1)
				.l(1.7, SM, 0.02, 0.31, -0.11, 3.9, -25.5, 89.5, 1)
				.l(2.3, SM, -0.01, 0.29, -0.09, 6.8, -25.2, 94.5, 1)
				.l(2.85, SM, 0.12, 0.15, -0.02, -26.0, -28.2, 85.9, 1)
				.l(3.4, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.18, SM, -1.5, 0, 0, 0)
				.c(0.90, SM, -1.5, 0, 0, 0)
				.c(1.50, SM, 0, 0, 0, 0), 0.50, -90, -50, 40, 3.0));

		// ---- Byakuya, Scatter (round 6): gather at the chest, the right hand sweeps forward palm out, both hands circle overhead palms up (opposite phase),
		// the right hand reaches to the aim palm forward and opens (palm up), the hands embrace, burst wide, then come back to the chest palms to self
		put(m, tp(CastGesture.builder("byakuya.bankai.scatter").dur(5.9).out(0.4).freeHands(true)
				.r(0.25, SM, -0.08, -0.07, 0.01, -46.6, 25.1, -43.3)
				.r(0.55, OUT, 0.16, 0.07, -0.15, -10.5, -35.7, -8.6)
				.r(0.8, SM, 0.26, 0.17, -0.07, -8.0, 68.0, -123.4)
				.r(1.4, SM, 0.26, 0.17, -0.07, -8.0, 68.0, -123.4)
				.r(1.5, OUT, -0.02, 0.13, -0.29, 21.9, -46.6, 22.4)
				.r(1.95, SM, -0.01, 0.11, -0.29, -33.8, 71.8, -99.9)
				.r(2.45, IN, -0.1, -0.03, -0.03, -45.4, 23.2, -44.7)
				.r(2.55, OUT, 0.34, 0.13, -0.07, -3.5, -38.7, -34.6)
				.r(2.85, SM, 0.28, 0.05, -0.05, -14.1, -8.5, -76.6)
				.r(4.5, LIN, 0.1, -0.07, 0.01, -43.5, 19.6, -54.3)
				.r(5.9, SM, 0, 0, 0, 0, 0, 0)
				.l(0.25, SM, 0.44, 0.13, 0.0, -26.8, -22.7, 13.2, 1)
				.l(0.55, OUT, 0.18, 0.27, -0.16, -6.4, 40.7, -27.2, 1)
				.l(0.8, SM, 0.08, 0.37, -0.08, 7.2, -42.7, 100.2, 1)
				.l(1.4, SM, 0.08, 0.37, -0.08, 7.2, -42.7, 100.2, 1)
				.l(1.5, OUT, 0, 0, 0, 0, 0, 0, 1)
				.l(1.95, SM, 0, 0, 0, 0, 0, 0, 1)
				.l(2.45, IN, 0.42, 0.17, -0.04, -26.3, -20.5, 14.3, 1)
				.l(2.55, OUT, 0.0, 0.33, -0.08, -2.8, 47.7, -1.0, 1)
				.l(2.85, SM, 0.06, 0.25, -0.06, -8.7, 19.9, 42.6, 1)
				.l(4.5, LIN, 0.2, 0.13, 0.0, -26.2, -15.6, 23.0, 1)
				.l(5.9, SM, 0, 0, 0, 0, 0, 0, 0)
				.orbit('P', 0.8, 1.45, 0.10, 0.6, 0)
				.orbit('L', 0.8, 1.45, 0.10, 0.6, 0.5)
				.c(0.80, SM, 0, 0, 0, 0)
				.c(2.55, OUT, -3, 0, 0, 0)
				.c(3.35, SM, 0, 0, 0, 0), 0.40, -110, -100, 55, 5.4));

		// ---- Byakuya, Hakuteiken (round 6): the right hand comes forward palm up (the fingers close in), the left hand comes to its wrist, a second of quiet,
		// one strike straight ahead (palm turns down), the left hand drops to the hip
		put(m, tp(CastGesture.builder("byakuya.bankai.hakuteiken").dur(3.2).out(0.4).freeHands(true).hold(1.4, 0.1)
				.r(0.45, SM, 0.02, 0.09, -0.13, -49.6, 59.7, -112.5)
				.r(1.0, SM, -0.06, 0.14, -0.23, -31.8, 59.1, -88.8)
				.r(1.4, SM, -0.08, 0.15, -0.25, -22.3, 46.9, -74.3)
				.r(1.52, OUT, -0.18, 0.15, -0.31, 5.3, -36.6, 8.9)
				.r(1.8, SM, -0.16, 0.13, -0.27, 8.9, -37.1, 7.9)
				.r(2.3, SM, -0.02, 0.03, -0.09, -65.3, 60.1, -106.5)
				.r(2.8, SM, 0, 0, 0, 0, 0, 0)
				.l(0.45, SM, -0.04, 0.07, -0.04, -17.3, -23.7, 80.5, 1)
				.l(1.0, SM, 0.32, 0.23, -0.16, -28.4, -32.7, 73.8, 1)
				.l(1.4, SM, 0.5, 0.25, -0.2, -21.9, -33.9, 62.5, 1)
				.l(1.52, OUT, 0, 0, 0, 0, 0, 0, 1)
				.l(2.8, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(1.52, OUT, -2, 0, 0, 4)
				.c(1.90, SM, 0, 0, 0, 0), 0.30, -100, 30, 20, 2.6));

		// ---- Byakuya, Senkei (round 6, the ability is disabled in the game; the gesture is ready for when it is built): both palms forward, the ring forms,
		// the release throws the hands up and apart
		put(m, tp(CastGesture.builder("byakuya.bankai.senkei").dur(3.1).out(0.4).freeHands(true)
				.r(0.4, SM, 0.04, 0.11, -0.07, 36.2, -61.3, 110.8)
				.r(0.75, OUT, 0.07, 0.15, -0.21, 16.1, -45.3, 26.4)
				.r(2.0, SM, 0.07, 0.15, -0.23, 18.6, -45.4, 23.8)
				.r(2.12, BK, 0.36, 0.17, -0.15, 9.6, -60.3, -30.3)
				.r(2.7, SM, 0.1, 0.07, -0.07, -61.9, 50.5, -111.1)
				.r(3.1, SM, 0, 0, 0, 0, 0, 0)
				.l(0.4, SM, 0.3, 0.31, -0.08, 38.7, 67.8, -146.8, 1)
				.l(0.75, OUT, 0.27, 0.35, -0.22, 19.3, 54.6, -61.5, 1)
				.l(2.0, SM, 0.27, 0.35, -0.24, 21.6, 55.8, -58.6, 1)
				.l(2.12, BK, -0.02, 0.37, -0.16, 3.3, 71.6, -5.3, 1)
				.l(2.7, SM, 0.2, 0.15, -0.06, -41.4, -41.7, 82.8, 1)
				.l(3.1, SM, 0, 0, 0, 0, 0, 0, 0)
				.vib('R', 0.9, 2.0, 0.004, 9)
				.vib('L', 0.9, 2.0, 0.004, 9)
				.c(0.75, SM, 0, 0, 0, 0)
				.c(2.00, SM, -2, 0, 0, 0)
				.c(2.70, SM, 0, 0, 0, 0), 0.40, -80, -80, 30, 2.8));

		// ---- Kido (the free left hand does the sign, the sword hand drops the blade out of the way; sealed: the right hand only)
		put(m, tp(CastGesture.builder("kido.byakurai").dur(1.1).out(0.3)
				.r(0.10, OUT, 0.08, -0.12, 0.04, 10, 0, 4)
				.r(0.80, SM, 0.08, -0.12, 0.04, 10, 0, 4)
				.r(1.10, SM, 0, 0, 0, 0, 0, 0)
				.l(0.10, OUT, 0.16, 0.26, -0.36, -20, 0, 0, 1)
				.l(0.16, OUT, 0.16, 0.26, -0.42, -20, 0, 0, 1)
				.l(0.30, SM, 0.16, 0.26, -0.38, -20, 0, 0, 1)
				.l(0.80, SM, 0.16, 0.26, -0.38, -20, 0, 0, 1)
				.l(1.10, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.10, OUT, 1.0, 0, 0, 0)
				.c(0.35, SM, 0, 0, 0, 0), 0.10, 0, -85, 0, 1.0));
		put(m, tp(CastGesture.builder("kido.shakkaho").dur(1.6).out(0.3)
				.r(0.12, OUT, 0.08, -0.12, 0.04, 10, 0, 4)
				.r(1.20, SM, 0.08, -0.12, 0.04, 10, 0, 4)
				.r(1.60, SM, 0, 0, 0, 0, 0, 0)
				.l(0.10, OUT, 0.14, 0.30, -0.34, -30, 0, 0, 1)
				.l(0.18, OUT, 0.14, 0.30, -0.44, -30, 0, 0, 1)
				.l(0.45, SM, 0.14, 0.30, -0.38, -30, 0, 0, 1)
				.l(1.20, SM, 0.14, 0.30, -0.38, -30, 0, 0, 1)
				.l(1.60, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.18, OUT, 0.8, 0, 0, 0)
				.c(0.50, SM, 0, 0, 0, 0), 0.12, 0, -80, 5, 1.4));
		put(m, tp(CastGesture.builder("kido.sai").dur(0.9).out(0.25)
				.r(0.08, OUT, 0.06, -0.08, 0.02, 6, 0, 0)
				.r(0.60, SM, 0.06, -0.08, 0.02, 6, 0, 0)
				.r(0.90, SM, 0, 0, 0, 0, 0, 0)
				.l(0.07, OUT, 0.10, 0.04, -0.40, -35, 0, 0, 1)
				.l(0.20, SM, 0.06, 0.02, -0.26, -30, 0, 0, 1)
				.l(0.60, SM, 0.06, 0.02, -0.26, -30, 0, 0, 1)
				.l(0.90, SM, 0, 0, 0, 0, 0, 0, 0), 0.08, 0, -50, 0, 0.8));

		// ---- Sealing the sword: the hands take the blade back to the scabbard (the draw rig does the rest)
		put(m, CastGesture.builder(SEAL).dur(0.7).out(0.25)
				.r(0.15, SM, 0.02, -0.10, 0.06, 20, 0, 0)
				.r(0.35, SM, 0, -0.14, 0.04, 32, 0, 0)
				.r(0.70, SM, 0, 0, 0, 0, 0, 0)
				.l(0.15, SM, 0, 0, 0, 0, 0, 0, 1)
				.l(0.35, SM, 0, 0, 0, 0, 0, 0, 1)
				.l(0.70, SM, 0, 0, 0, 0, 0, 0, 0)
				.c(0.30, SM, -0.5, 0, 0, 0)
				.c(0.70, SM, 0, 0, 0, 0));

		// ---- Steering the swarm (held key): the hand is thrown forward and conducts; sustained at 0.25 s while ClientState.steering()
		put(m, CastGesture.builder(STEER).dur(0.75).in(0.18).out(0.3).sustain(0.25)
				.r(0.25, OUT, 0.06, 0.08, -0.32, -48, 0, 6)
				.r(0.75, SM, 0, 0, 0, 0, 0, 0)
				.l(0.25, OUT, -0.12, 0.08, -0.28, -25, 0, 0, 1)
				.l(0.75, SM, 0, 0, 0, 0, 0, 0, 0)
				.orbit('L', 0.0, 0.55, 0.05, 0.8, 0)
				.orbit('T', 0.0, 0.55, 3, 0.8, 0.25)
				.tr(0.25, OUT, -55, 0, 0)
				.tr(0.75, SM, 0, 0, 0)
				.tl(0.25, OUT, -35, 0, 10)
				.tl(0.75, SM, 0, 0, 0));
		ExtraGestures.addTo(m); // round 6: ice spike, petal cage
		NewCharacterGestures.addTo(m); // gin-uryu: placeholder gestures of Gin and Uryu
		return java.util.Collections.unmodifiableMap(m);
	}
}
