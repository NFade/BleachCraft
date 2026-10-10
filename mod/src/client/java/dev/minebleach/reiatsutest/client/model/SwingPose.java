package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.LivingEntity;

/**
 * Third person slash of the drawn zanpakuto (T4), visible on the own player and on other players. Purely visual and client
 * side: the swing timing is the vanilla hand swing progress {@code s} (0..1 over 6 ticks = 0.3 s, longer or shorter with haste
 * or fatigue exactly like the vanilla arm), the server damage is untouched.
 *
 * <p>The pose is a short key frame animation with three parts: wind-up ({@code 0..tWind}: the arm goes up and back, the body
 * twists away, the wrist cocks the blade back), strike ({@code tWind..tStrike}: accelerating cut across the body, edge
 * leading), recovery ({@code tStrike..1}: back to the walking pose). The arm angles are absolute while it swings and blend
 * from and to the current walk / idle angle at both ends, so nothing pops. The wrist angles rotate only the held sword about
 * the fist. All numbers are for a right-handed main arm; the left hand mirrors yaw, roll and the body twist.
 *
 * <p>Tuning: {@code pose_override.json} (dev only, see {@link HeldPose}) key {@code "swing"} with any of the names below
 * (degrees, fractions).
 */
public final class SwingPose {
	/** Names and defaults (degrees; times are fractions of the swing progress). */
	private static final Map<String, Float> DEFAULTS = new LinkedHashMap<>();

	static {
		DEFAULTS.put("t_wind", 0.30f);
		DEFAULTS.put("t_strike", 0.58f);
		DEFAULTS.put("pitch_wind", -150f);
		DEFAULTS.put("pitch_strike", -55f);
		DEFAULTS.put("yaw_wind", 25f);
		DEFAULTS.put("yaw_strike", -40f);
		DEFAULTS.put("roll_wind", 20f);
		DEFAULTS.put("roll_strike", -15f);
		DEFAULTS.put("body_wind", 17f);
		DEFAULTS.put("body_strike", -22f);
		DEFAULTS.put("wrist_pitch_wind", -35f);
		DEFAULTS.put("wrist_pitch_strike", 40f);
		DEFAULTS.put("wrist_roll_wind", 0f);
		DEFAULTS.put("wrist_roll_strike", 0f);
		DEFAULTS.put("wrist_yaw_wind", 0f);
		DEFAULTS.put("wrist_yaw_strike", 0f);
	}

	private SwingPose() {
	}

	private static float val(String key) {
		JsonObject o = HeldPose.overrideObject();
		if (o != null && o.has("swing")) {
			JsonObject sw = o.getAsJsonObject("swing");
			if (sw.has(key)) {
				return sw.get(key).getAsFloat();
			}
		}
		return DEFAULTS.get(key);
	}

	/** True when the entity's main hand zanpakuto is drawn (in the hand, not in the hip scabbard): only then the slash replaces the vanilla swing. */
	public static boolean active(LivingEntity e) {
		if (e.preferredHand != net.minecraft.util.Hand.MAIN_HAND) {
			return false;
		}
		net.minecraft.item.ItemStack main = e.getMainHandStack();
		return main.getItem() instanceof ZanpakutoItem && DrawTracker.effectiveProgress(e, main) >= 1f;
	}

	private static float smooth(float x) {
		float t = Math.max(0f, Math.min(1f, x));
		return t * t * (3f - 2f * t);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Keyframe curve: from {@code rest} to {@code wind} (ease in-out), to {@code strike} (ease in: accelerates into the cut), back to {@code rest}. */
	private static float curve(float s, float rest, float wind, float strike) {
		float tw = val("t_wind");
		float ts = val("t_strike");
		if (s <= 0f) {
			return rest;
		}
		if (s < tw) {
			return lerp(rest, wind, smooth(s / tw));
		}
		if (s < ts) {
			float u = (s - tw) / (ts - tw);
			return lerp(wind, strike, u * u * (2f - u)); // fast into the cut, a touch of ease at the end
		}
		return lerp(strike, rest, smooth((s - ts) / (1f - ts)));
	}

	private static float rad(float deg) {
		return deg * (float) (Math.PI / 180.0);
	}

	/**
	 * Arm angles (radians: pitch, yaw, roll) at swing progress s for the arm that swings, given its current walk / idle angles;
	 * {@code sign} is +1 for the right arm, -1 for the left (mirrors yaw and roll).
	 */
	public static float[] arm(float s, float curPitch, float curYaw, float curRoll, float sign) {
		return new float[] {
			curve(s, curPitch, rad(val("pitch_wind")), rad(val("pitch_strike"))),
			curve(s, curYaw, sign * rad(val("yaw_wind")), sign * rad(val("yaw_strike"))),
			curve(s, curRoll, sign * rad(val("roll_wind")), sign * rad(val("roll_strike")))};
	}

	/** Body yaw twist (radians) at swing progress s. */
	public static float body(float s, float sign) {
		return sign * curve(s, 0f, rad(val("body_wind")), rad(val("body_strike")));
	}

	/** Wrist rotation of the sword about the fist (degrees: pitch about the hand X axis, roll, yaw). */
	public static float[] wrist(float s, float sign) {
		return new float[] {
			curve(s, 0f, val("wrist_pitch_wind"), val("wrist_pitch_strike")),
			sign * curve(s, 0f, val("wrist_roll_wind"), val("wrist_roll_strike")),
			sign * curve(s, 0f, val("wrist_yaw_wind"), val("wrist_yaw_strike"))};
	}
}
