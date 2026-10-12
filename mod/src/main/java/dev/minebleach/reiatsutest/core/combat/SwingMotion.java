package dev.minebleach.reiatsutest.core.combat;

import java.util.Locale;

/**
 * The first person strike of the zanpakuto, drawn by the mod itself (BC5b: Better Combat's first person mode cancels the whole
 * hand pass, so its own animation cannot coexist with our arm, scabbard and sword lift). Better Combat only supplies the rhythm:
 * the strike starts on its attack start event, the hit lands after its upswing ticks, the animation name picks the profile.
 *
 * <p>Timeline for {@code up} seconds of upswing (the moment the hit lands):
 * <ol>
 * <li>0 .. up * windFrac: from the pose at the start to the WIND key (ease out);</li>
 * <li>up * windFrac .. up: WIND to HIT, accelerating (ease in), so the sword arrives at the HIT key exactly at the hit;</li>
 * <li>up .. up + follow: HIT to FOLLOW (ease out);</li>
 * <li>then FOLLOW back to the rest pose over {@code recover} seconds (smoothstep).</li>
 * </ol>
 * Keys are {@link FpPose} values of the right hand (the caller mirrors for the left). Shipped numbers were laid out against a
 * projection of the real sword (hand frame blade axis 0.12/0.97/0.23, display scale 2.4) and are UNVERIFIED in game; every key
 * can be replaced from {@code pose_override.json} (key path {@code swing.<profile>.<wind|hit|follow>.<pos|rot>}).
 */
public final class SwingMotion {
	/** Strike profiles; the first four are the katana combo of Better Combat. */
	public enum Profile {
		/** Horizontal, from the right to the left. */
		H_R("h_r", FpPose.of(0.06f, 0f, 0.02f, 8f, 0f, -28f), FpPose.of(-0.26f, 0.08f, -0.20f, -22f, 0f, 62f),
				FpPose.of(-0.36f, 0.12f, -0.26f, -28f, 0f, 80f)),
		/** Horizontal, backhand, from the left to the right. */
		H_L("h_l", FpPose.of(-0.22f, -0.02f, 0f, 10f, 0f, 45f), FpPose.of(0.20f, 0.06f, -0.22f, -22f, 0f, -55f),
				FpPose.of(0.28f, 0.08f, -0.28f, -28f, 0f, -72f)),
		/** Overhead chop that starts on the right. */
		V_R("v_r", FpPose.of(0.06f, 0.26f, 0.04f, 25f, 0f, -15f), FpPose.of(-0.04f, -0.12f, -0.32f, -80f, 0f, 8f),
				FpPose.of(-0.06f, -0.20f, -0.36f, -100f, 0f, 10f)),
		/** Overhead chop that starts on the left. */
		V_L("v_l", FpPose.of(-0.12f, 0.26f, 0.04f, 25f, 0f, 15f), FpPose.of(0.02f, -0.12f, -0.32f, -80f, 0f, -8f),
				FpPose.of(0.04f, -0.20f, -0.36f, -100f, 0f, -10f)),
		/** Thrust (animation names with "stab" or "thrust"). */
		STAB("stab", FpPose.of(0.02f, -0.02f, 0.14f, 0f, 0f, -6f), FpPose.of(-0.06f, 0.02f, -0.42f, -78f, 0f, 4f),
				FpPose.of(-0.07f, 0.02f, -0.46f, -82f, 0f, 4f)),
		/** Rising cut (animation names with "uppercut"). */
		UPPER("upper", FpPose.of(0.08f, -0.18f, 0.02f, 10f, 0f, -34f), FpPose.of(-0.16f, 0.22f, -0.22f, -34f, 0f, 38f),
				FpPose.of(-0.20f, 0.30f, -0.26f, -42f, 0f, 48f));

		public final String key;
		private final FpPose wind;
		private final FpPose hit;
		private final FpPose follow;

		Profile(String key, FpPose wind, FpPose hit, FpPose follow) {
			this.key = key;
			this.wind = wind;
			this.hit = hit;
			this.follow = follow;
		}
	}

	/** Which of the three keys. */
	public enum Key {
		WIND("wind"), HIT("hit"), FOLLOW("follow");

		final String key;

		Key(String key) {
			this.key = key;
		}
	}

	/**
	 * The keys of one strike (absolute poses) and the extra {@code arc} that is added on the way from WIND to HIT with the weight
	 * {@code sin(pi * u)} (zero at both keys), so the sword goes on a curve and not on the straight line between the keys.
	 */
	public record Keys(FpPose wind, FpPose hit, FpPose follow, FpPose arc) {
	}

	/** Timing numbers (seconds); {@code windFrac} is the share of the upswing spent on the wind-up. */
	public record Timing(float windFrac, float follow, float recover) {
		public static final Timing DEFAULT = new Timing(0.55f, 0.07f, 0.22f);
	}

	private SwingMotion() {
	}

	/**
	 * Profile for a Better Combat attack animation name (for example {@code bettercombat:two_handed_slash_vertical_left}); unknown or
	 * missing names fall back to the combo index (0 = H_R, 1 = V_L, 2 = V_R, 3 = H_L, which is the katana combo order).
	 */
	public static Profile profileFor(String animation, int comboIndex) {
		if (animation != null) {
			String a = animation.toLowerCase(Locale.ROOT);
			boolean left = a.endsWith("_left") || a.contains("_left_");
			if (a.contains("stab") || a.contains("thrust") || a.contains("pierce")) {
				return Profile.STAB;
			}
			if (a.contains("uppercut") || a.contains("upper")) {
				return Profile.UPPER;
			}
			if (a.contains("vertical") || a.contains("overhead") || a.contains("chop")) {
				return left ? Profile.V_L : Profile.V_R;
			}
			if (a.contains("horizontal") || a.contains("slash") || a.contains("sweep")) {
				return left ? Profile.H_L : Profile.H_R;
			}
		}
		return switch (Math.floorMod(comboIndex, 4)) {
			case 1 -> Profile.V_L;
			case 2 -> Profile.V_R;
			case 3 -> Profile.H_L;
			default -> Profile.H_R;
		};
	}

	/** The key pose of a profile, replaced by {@code swing.<profile>.<key>.pos / .rot} when present, scaled by {@code swing.scale}. */
	public static FpPose key(FpParams pr, Profile p, Key k) {
		FpPose def = switch (k) {
			case WIND -> p.wind;
			case HIT -> p.hit;
			case FOLLOW -> p.follow;
		};
		String base = "swing." + p.key + "." + k.key;
		FpPose v = FpPose.of(pr.vec(base + ".pos", def.pos()), pr.vec(base + ".rot", def.rot()));
		return v.scaled(pr.num("swing.scale", 1f));
	}

	public static Timing timing(FpParams pr) {
		Timing d = Timing.DEFAULT;
		return new Timing(clampF(pr.num("swing.wind_frac", d.windFrac()), 0.1f, 0.9f), Math.max(0.01f, pr.num("swing.follow", d.follow())),
				Math.max(0.02f, pr.num("swing.recover", d.recover())));
	}

	/** Total length in seconds of a strike whose hit lands {@code up} seconds after the start. */
	public static float duration(float up, Timing t) {
		return Math.max(up, 0.03f) + t.follow() + t.recover();
	}

	/**
	 * The pose {@code t} seconds after the strike started. {@code origin} is the pose at the start (a strike that interrupts another
	 * one starts from where the hand is, so there is no jump); {@code up} is the time until the hit lands. After the end the result is
	 * {@link FpPose#ZERO}.
	 */
	public static FpPose sample(FpParams pr, Profile p, FpPose origin, float t, float up, Timing tm) {
		return sample(new Keys(key(pr, p, Key.WIND), key(pr, p, Key.HIT), key(pr, p, Key.FOLLOW), FpPose.ZERO), FpPose.ZERO, origin, t, up, tm);
	}

	/**
	 * Same timeline with explicit keys and a rest pose that the sword returns to (the two handed stance, see {@link TwoHandMotion};
	 * {@link FpPose#ZERO} for the one handed hold). Before the start it is {@code origin}, after the end {@code rest}.
	 */
	public static FpPose sample(Keys keys, FpPose rest, FpPose origin, float t, float up, Timing tm) {
		up = Math.max(up, 0.03f);
		FpPose wind = keys.wind();
		FpPose hit = keys.hit();
		FpPose follow = keys.follow();
		float tw = up * tm.windFrac();
		if (t <= 0f) {
			return origin;
		}
		if (t < tw) {
			return origin.lerp(wind, FpEase.out(t / tw));
		}
		if (t < up) {
			float e = FpEase.in((t - tw) / (up - tw));
			return wind.lerp(hit, e).plus(keys.arc().scaled((float) Math.sin(Math.PI * e)));
		}
		float t2 = t - up;
		if (t2 < tm.follow()) {
			return hit.lerp(follow, FpEase.out(t2 / tm.follow()));
		}
		float t3 = t2 - tm.follow();
		if (t3 < tm.recover()) {
			return follow.lerp(rest, FpEase.smooth(t3 / tm.recover()));
		}
		return rest;
	}

	private static float clampF(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
