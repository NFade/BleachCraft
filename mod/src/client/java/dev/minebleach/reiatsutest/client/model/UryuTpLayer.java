package dev.minebleach.reiatsutest.client.model;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.minebleach.reiatsutest.core.combat.UryuTpPose;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * The third person pose of an Uryu player holding his bow (U-FX 11): a playerAnimator layer between the stance layer of {@link TpPoseLayer} (1500) and
 * {@link TpCastLayer} (1600) that hands the head (a nod added to the camera pitch), the torso, the arms (rotation and the shoulder pivots, which follow the
 * turned chest) and the legs the values of {@link UryuTpPose}, sampled by {@link UryuBowMotion} from the animations authored in
 * {@code tools/gen_uryu_poses.py}. Parts an animation does not key keep the value of the layers below (vanilla), so the loose left arm swings with the walk.
 * The Better Combat layer (2000) stays above. The item bone ({@code rightItem}) is NOT used: {@link UryuBowPose} keeps the bow upright with a matrix of its own
 * (a driven item bone would switch on the sword correction of {@link BcItemPose}).
 */
public final class UryuTpLayer implements IAnimation {
	private static final int PRIORITY = 1550;

	private final AbstractClientPlayerEntity player;

	private UryuTpLayer(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((p, stack) -> stack.addAnimLayer(PRIORITY, new UryuTpLayer(p)));
	}

	@Override
	public boolean isActive() {
		return UryuBowMotion.tpFrame(player) != null;
	}

	@Override
	public void setupAnim(float tickDelta) {
	}

	@Override
	public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
		if (type != TransformType.ROTATION && type != TransformType.POSITION) {
			return value0;
		}
		boolean known = "head".equals(modelName) || "torso".equals(modelName) || "rightArm".equals(modelName) || "leftArm".equals(modelName)
				|| "rightLeg".equals(modelName) || "leftLeg".equals(modelName);
		if (!known) {
			return value0;
		}
		UryuTpPose.Frame f = UryuBowMotion.tpFrame(player);
		if (f == null) {
			return value0;
		}
		double[] in = {value0.getX(), value0.getY(), value0.getZ()};
		double[] out = type == TransformType.ROTATION ? UryuTpPose.rotation(f, modelName, in) : UryuTpPose.position(f, modelName, in);
		if (out == in) {
			return value0;
		}
		return new Vec3f((float) out[0], (float) out[1], (float) out[2]);
	}
}
