package dev.minebleach.reiatsutest.client.model;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.minebleach.reiatsutest.core.combat.CastGesture;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Arm;

/**
 * Third person ability gesture: a playerAnimator layer above the stance layer of {@link TpPoseLayer} (priority 1500) and below the strike
 * layer of Better Combat (2000) that ADDS small arm rotations of the current {@link CastGesture} (rows {@code TR} for the sword arm,
 * {@code TL} for the other one; pitch below 0 raises the arm forward, roll above 0 swings it outwards). Only the arms, no body lean, no
 * item channel (the held sword follows the arm; {@code rightItem} stays untouched on purpose, see tp_pose.md). Minimal by design: the long
 * clips of the scatter and the release belong to a later stage.
 */
public final class TpCastLayer implements IAnimation {
	private static final int PRIORITY = 1600;
	private static final float RAD = (float) (Math.PI / 180.0);

	private final AbstractClientPlayerEntity player;

	private TpCastLayer(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((p, stack) -> stack.addAnimLayer(PRIORITY, new TpCastLayer(p)));
	}

	@Override
	public boolean isActive() {
		return FpCast.tpActive(player.getId());
	}

	@Override
	public void setupAnim(float tickDelta) {
	}

	@Override
	public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
		boolean right = "rightArm".equals(modelName);
		if ((!right && !"leftArm".equals(modelName)) || type != TransformType.ROTATION) {
			return value0;
		}
		CastGesture.Sample s = FpCast.tpSample(player.getId());
		if (s == null) {
			return value0;
		}
		boolean isMain = right == (player.getMainArm() == Arm.RIGHT);
		float[] a = isMain ? s.tr() : s.tl();
		if (a[0] == 0f && a[1] == 0f && a[2] == 0f) {
			return value0;
		}
		// roll above 0 is outwards: the right arm swings out with +z, the left one with -z; yaw mirrors with the left arm
		float yaw = right ? a[1] : -a[1];
		float roll = right ? a[2] : -a[2];
		return new Vec3f(value0.getX() + a[0] * RAD, value0.getY() + yaw * RAD, value0.getZ() + roll * RAD);
	}
}
