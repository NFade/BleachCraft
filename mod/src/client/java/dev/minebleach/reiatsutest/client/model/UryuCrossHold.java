package dev.minebleach.reiatsutest.client.model;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Quaternionf;

/**
 * R14: the sealed Quincy cross in the hand of Uryu, third person. Before the bow is drawn the cross hangs on its chain from the fist; the arm used
 * to hang straight down, so the pendant was behind the thigh and could not be seen. This layer (playerAnimator, between the stance layer 1500 and
 * the bow layer 1550) lifts the main arm forward by {@code uryu.cross.armPitch} degrees (default -35) and out by {@code uryu.cross.armRoll} (22): the hand is at hip height beside the body, the pendant hangs where it is seen from the front and from behind
 * with the eased weight of {@link #weight}; {@link #itemCorrection} turns the item back by the same angle about the hand so the chain still hangs
 * down. The first person counterpart is {@code UryuFpHold.push}. UNVERIFIED in the game: the harness scenario {@code r14_cross} takes the pictures.
 * Tunables (FxTune): {@code uryu.cross.arm} (0 = off), {@code uryu.cross.armPitch}, {@code uryu.cross.armYaw}, {@code uryu.cross.armRoll} in degrees.
 */
public final class UryuCrossHold implements IAnimation {
	private static final int PRIORITY = 1480;
	private static final float RAD = (float) (Math.PI / 180.0);
	/** Seconds the arm takes to rise and to fall again. */
	private static final double EASE_SECONDS = 0.28;

	private static final class St {
		double w;
		long nanos;
	}

	private static final Map<Integer, St> STATES = new HashMap<>();

	private final AbstractClientPlayerEntity player;

	private UryuCrossHold(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((p, stack) -> stack.addAnimLayer(PRIORITY, new UryuCrossHold(p)));
	}

	/** World change. */
	public static void clear() {
		STATES.clear();
	}

	/** True when the entity holds the SEALED cross (not the drawn bow) in the main hand in a body state the pose can handle. */
	static boolean wants(LivingEntity e) {
		ItemStack main = e.getMainHandStack();
		return FxTune.d("uryu.cross.arm", 1.0) > 0.5 && main.getItem() instanceof UryuCrossItem && !UryuCrossItem.isBow(main) && e.isAlive()
				&& !e.isUsingItem() && !e.isFallFlying() && !e.isSwimming() && !e.isSleeping() && !e.isClimbing();
	}

	/** The eased 0..1 weight of the raised arm of this entity (the state moves with the render clock, so a frozen frame stays still). */
	static float weight(LivingEntity e) {
		boolean want = wants(e);
		St st = STATES.get(e.getId());
		if (st == null) {
			if (!want) {
				return 0f;
			}
			if (STATES.size() > 64) {
				STATES.clear();
			}
			st = new St();
			st.nanos = System.nanoTime();
			STATES.put(e.getId(), st);
		}
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - st.nanos) / 1.0e9);
		st.nanos = now;
		double target = want ? 1.0 : 0.0;
		double step = dt / EASE_SECONDS;
		st.w = st.w < target ? Math.min(target, st.w + step) : Math.max(target, st.w - step);
		if (st.w <= 0.0 && !want) {
			STATES.remove(e.getId());
			return 0f;
		}
		double k = st.w; // smoothstep
		return (float) (k * k * (3.0 - 2.0 * k));
	}

	private static float armPitchRad() {
		return (float) FxTune.d("uryu.cross.armPitch", -35.0) * RAD;
	}

	/**
	 * The turn that keeps the chain hanging down while the arm is lifted: about the X axis of the hand frame, by minus the extra pitch of the arm (the
	 * display transform of the item is solved for the idle arm at -18 degrees). Null when the arm is not lifted (nothing to correct).
	 */
	static Quaternionf itemCorrection(LivingEntity e, boolean leftHanded) {
		if (!(e instanceof AbstractClientPlayerEntity) || leftHanded != (e.getMainArm() == Arm.LEFT)) {
			return null;
		}
		float w = weight(e);
		if (w < 0.002f) {
			return null;
		}
		// R15 round 3: the whole arm turn is undone, not only the pitch: the arm is lifted AND turned out (roll) and in (yaw), the chain must still hang straight down.
		// Frame of the arm: the item is drawn with R_arm * C, we want R_idle, so C = R_arm^-1 * R_idle ({@code uryu.cross.itemTurn} scales it, 1 = fully upright)
		float idle = (float) Math.toRadians(-18.0);
		float side = leftHanded ? -1f : 1f;
		float pitch = idle + (armPitchRad() - idle) * w;
		float yaw = (float) FxTune.d("uryu.cross.armYaw", -5.0) * RAD * side * w;
		float roll = (float) FxTune.d("uryu.cross.armRoll", 22.0) * RAD * side * w;
		Quaternionf arm = new Quaternionf().rotationZYX(roll, yaw, pitch);
		// R15 round 4: a few degrees more about the shoulder axis, the bottom of the pendant towards the body (positive X = backwards), so the chain hangs exactly vertical
		float tilt = (float) FxTune.d("uryu.cross.tpTilt", 8.0) * RAD;
		Quaternionf c = arm.conjugate(new Quaternionf()).mul(new Quaternionf().rotationX(tilt)).mul(new Quaternionf().rotationX(idle));
		float k = (float) FxTune.d("uryu.cross.itemTurn", 1.0);
		return new Quaternionf().slerp(c, k);
	}

	@Override
	public boolean isActive() {
		return wants(player) || STATES.containsKey(player.getId());
	}

	@Override
	public void setupAnim(float tickDelta) {
	}

	@Override
	public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
		if (type != TransformType.ROTATION) {
			return value0;
		}
		boolean main = (player.getMainArm() == Arm.RIGHT) ? "rightArm".equals(modelName) : "leftArm".equals(modelName);
		if (!main) {
			return value0;
		}
		float w = weight(player);
		if (w < 0.002f) {
			return value0;
		}
		float side = player.getMainArm() == Arm.RIGHT ? 1f : -1f;
		float x = armPitchRad();
		float y = (float) FxTune.d("uryu.cross.armYaw", -5.0) * RAD * side;
		float z = (float) FxTune.d("uryu.cross.armRoll", 22.0) * RAD * side;
		return new Vec3f(value0.getX() + (x - value0.getX()) * w, value0.getY() + (y - value0.getY()) * w, value0.getZ() + (z - value0.getZ()) * w);
	}
}
