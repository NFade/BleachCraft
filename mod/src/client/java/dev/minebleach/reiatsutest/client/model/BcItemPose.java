package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * BC5 decision 3: the held sword in third person while a Better Combat animation moves the item bone ({@code rightItem} /
 * {@code leftItem} of playerAnimator, the BC strike animations). BC authored those item rotations for a vanilla sword, whose
 * resting blade points almost straight along the item model Y axis (display transform {@code [0,-90,55]} of
 * {@code item/handheld}: blade direction (0, 0.985, -0.174)). Our model is displayed by {@code [45,180,0]} (blade direction
 * (0, 0.707, 0.707), tuned for the resting vanilla hold). The animation is applied before the display transform, so one fixed
 * correction {@code D} between them makes the blade follow the BC animation as a vanilla sword would:
 * {@code D * (our resting blade) = (vanilla resting blade)}, i.e. a rotation of -55 degrees about the item X axis
 * (computed with JOML {@code Quaternionf.rotationTo}, flat sides face -X in both, so no roll). {@code D} turns about the grip,
 * which sits at the display translation {@code (0,-2,1.75)} px. UNVERIFIED in game; tune with the file below.
 *
 * <p>The correction is only applied while the animation really drives the item bone (probe of the playerAnimator stack with an
 * impossible default value), eased in and out over {@code fade_ticks}, so the resting hold keeps the look tuned for it.
 *
 * <p>Hot reload (dev only, {@link HeldPose}): {@code pose_override.json} key {@code "bc_item"}:
 * {@code {"enabled": 1, "rot": [-55, 0, 0], "pos": [0, 0, 0], "pivot": [0, -2, 1.75], "fade_ticks": 3}}, all keys optional.
 * {@code rot} degrees (x, y, z), {@code pos} and {@code pivot} in item model pixels (1/16 block), right-handed numbers; the left
 * hand mirrors y and z rotation and the x of the translations like vanilla's display transform.
 */
public final class BcItemPose {
	private static final float[] ROT = {-55f, 0f, 0f};
	private static final float[] POS = {0f, 0f, 0f};
	private static final float[] PIVOT = {0f, -2f, 1.75f};
	private static final float FADE_TICKS = 3f;
	/** A value no animation produces; the stack hands it back unchanged when nothing animates the item bone. */
	private static final float PROBE = 12345.678f;

	private static final class Fade {
		float w;
		long lastNanos;
	}

	private static final Map<Integer, Fade> FADES = new HashMap<>();

	private BcItemPose() {
	}

	private static float[] vec(String key, float[] def) {
		JsonObject o = HeldPose.overrideObject();
		if (o != null && o.has("bc_item")) {
			JsonObject b = o.getAsJsonObject("bc_item");
			if (b.has(key)) {
				JsonArray a = b.getAsJsonArray(key);
				return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
			}
		}
		return def;
	}

	private static float num(String key, float def) {
		JsonObject o = HeldPose.overrideObject();
		if (o != null && o.has("bc_item")) {
			JsonObject b = o.getAsJsonObject("bc_item");
			if (b.has(key)) {
				return b.get(key).getAsFloat();
			}
		}
		return def;
	}

	/** True when a playerAnimator layer (Better Combat's strike) currently drives the item bone of this arm. */
	static boolean drivenByAnimation(LivingEntity e, boolean left, float tickDelta) {
		if (!(e instanceof AbstractClientPlayerEntity p)) {
			return false;
		}
		try {
			AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(p);
			Vec3f r = stack.get3DTransform(left ? "leftItem" : "rightItem", TransformType.ROTATION, tickDelta, new Vec3f(PROBE, PROBE, PROBE));
			return r != null && (r.getX() != PROBE || r.getY() != PROBE || r.getZ() != PROBE);
		} catch (RuntimeException ex) {
			return false;
		}
	}

	/** Eased weight 0..1 of the correction for this entity right now (render thread). */
	static float weight(LivingEntity e, boolean left, float tickDelta) {
		long now = System.nanoTime();
		Fade f = FADES.computeIfAbsent(e.getId(), k -> new Fade());
		boolean on = num("enabled", 1f) > 0.5f && drivenByAnimation(e, left, tickDelta);
		float dt = f.lastNanos == 0L ? 0f : Math.min(0.25f, (now - f.lastNanos) / 1.0e9f);
		f.lastNanos = now;
		float rate = dt / Math.max(0.02f, num("fade_ticks", FADE_TICKS) * 0.05f);
		f.w = on ? Math.min(1f, f.w + rate) : Math.max(0f, f.w - rate);
		if (FADES.size() > 64) {
			FADES.values().removeIf(x -> now - x.lastNanos > 2_000_000_000L);
		}
		return f.w * f.w * (3f - 2f * f.w); // smoothstep
	}

	/**
	 * Pushes the correction onto the matrix stack when it applies (call at the head of the held item render, after the animation
	 * was applied). Returns true when a matrix was pushed; the caller pops it after the item was drawn.
	 */
	public static boolean push(LivingEntity e, boolean leftHanded, float tickDelta, MatrixStack matrices) {
		float w = weight(e, leftHanded, tickDelta);
		if (w <= 0.001f) {
			return false;
		}
		float s = leftHanded ? -1f : 1f;
		float[] rot = vec("rot", ROT);
		float[] pos = vec("pos", POS);
		float[] pv = vec("pivot", PIVOT);
		Quaternionf q = new Quaternionf().rotationXYZ((float) Math.toRadians(rot[0] * w), (float) Math.toRadians(s * rot[1] * w),
				(float) Math.toRadians(s * rot[2] * w));
		matrices.push();
		matrices.translate(s * (pv[0] + pos[0] * w) / 16f, (pv[1] + pos[1] * w) / 16f, (pv[2] + pos[2] * w) / 16f);
		matrices.multiply(q);
		matrices.translate(-s * pv[0] / 16f, -pv[1] / 16f, -pv[2] / 16f);
		return true;
	}
}
