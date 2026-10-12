package dev.minebleach.reiatsutest.client.model;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * The arms of the third person pose of a zanpakuto holder (tp_pose): a playerAnimator layer that overrides the rotation and the
 * shoulder pivot of the two arms with the numbers of {@code core.combat.TpPose}, blended with the vanilla pose by the weights of the
 * solver and by the eased hold weight. It sits below Better Combat's strike layer (priority 2000), so a Better Combat strike still plays
 * over it and the stance returns when the strike fades out. Better Combat's own katana stance is switched off for our swords
 * ({@code BcPoseMixin}); this layer is the stance. The layer is not part of the first person pass (default first person mode
 * of {@link IAnimation}), the first person arms belong to {@code FirstPersonHand}.
 *
 * <p>playerAnimator hands each layer the value the layers below it (and vanilla) produced for the part, so a weight below 1 is a
 * plain blend with the walk swing, the use pose and so on.
 *
 * <p>round6 tp_block: where playerAnimator runs (checked in the 2.0.4 jar): {@code PlayerModelMixin} injects into
 * {@code PlayerEntityModel#setAngles} at the first {@code ModelPart#copyTransform} call (the pants), i.e. after
 * {@code BipedEntityModel#setAngles} (vanilla arm poses, {@code ArmPose.BLOCK} included) and before the sleeves copy the arms, and it
 * writes {@code pivotX/Y/Z} and the rotation of the part with the value this stack returns. So the vanilla block pose cannot win
 * over this layer. What the layer wrote is remembered per player in a {@link Trace}; {@code PlayerEntityModelMixin} compares it with
 * the model at the RETURN of {@code setAngles} ({@link TpPoseDriver#finishPass}) and the {@code tp.debug} log shows the counters.
 */
public final class TpPoseLayer implements IAnimation {
	/** Below Better Combat's attack layer (2000), above everything of its pose layers. */
	private static final int PRIORITY = 1500;

	/** What this layer wrote into the arms of one player in the current model pass, and counters for the debug log (render thread). */
	static final class Trace {
		/** Index 0 = right arm, 1 = left arm. */
		final boolean[] hasRot = new boolean[2];
		final boolean[] hasPos = new boolean[2];
		final float[][] rot = new float[2][3];
		final float[][] pos = new float[2][3];
		int layerFrames;
		int passes;
		int restored;
		int applied;
		String lastPass = "none";
	}

	private static final Map<Integer, Trace> TRACES = new HashMap<>();

	static Trace trace(int entityId) {
		if (TRACES.size() > 64 && !TRACES.containsKey(entityId)) {
			TRACES.clear();
		}
		return TRACES.computeIfAbsent(entityId, k -> new Trace());
	}

	/** Start of a model pass of this player ({@code PlayerEntityModel#setAngles} HEAD): forget what the layer wrote before. */
	public static void beginPass(int entityId) {
		Trace t = trace(entityId);
		t.hasRot[0] = false;
		t.hasRot[1] = false;
		t.hasPos[0] = false;
		t.hasPos[1] = false;
	}

	private final AbstractClientPlayerEntity player;

	private TpPoseLayer(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((p, stack) -> stack.addAnimLayer(PRIORITY, new TpPoseLayer(p)));
	}

	@Override
	public boolean isActive() {
		return TpPoseDriver.layerWeight(player) > 0.002f;
	}

	@Override
	public void setupAnim(float tickDelta) {
	}

	@Override
	public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
		boolean right = "rightArm".equals(modelName);
		if (!right && !"leftArm".equals(modelName)) {
			return value0;
		}
		if (type != TransformType.ROTATION && type != TransformType.POSITION) {
			return value0;
		}
		float[] t = TpPoseDriver.armTarget(player, right);
		if (t == null) {
			return value0;
		}
		float w = t[0];
		float x0 = value0.getX();
		float y0 = value0.getY();
		float z0 = value0.getZ();
		int i = right ? 0 : 1;
		Trace tr = trace(player.getId());
		if (type == TransformType.ROTATION) {
			float ox = x0 + (t[1] - x0) * w;
			float oy = y0 + (t[2] - y0) * w;
			float oz = z0 + (t[3] - z0) * w;
			tr.rot[i][0] = ox;
			tr.rot[i][1] = oy;
			tr.rot[i][2] = oz;
			tr.hasRot[i] = true;
			if (right) {
				tr.layerFrames++;
			}
			return new Vec3f(ox, oy, oz);
		}
		float ox = x0 + t[4] * w;
		float oy = y0 + t[5] * w;
		float oz = z0 + t[6] * w;
		tr.pos[i][0] = ox;
		tr.pos[i][1] = oy;
		tr.pos[i][2] = oz;
		tr.hasPos[i] = true;
		return new Vec3f(ox, oy, oz);
	}
}
