package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuDrawLight;
import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Where the bow is in the world (URYU_VFX_STORYBOARD 2: the anchors hand and nock) for every Uryu in view, third person and first person.
 * Third person at rest: {@code feet + R(body yaw) * (0.35 right, 1.10 up, 0.45 forward)}; third person while the bow is drawn: in front of
 * the shoulder along the look (the bow arm points at the target); first person: {@code camera + (0.30 right, -0.28 up, 0.55 forward)} in
 * camera space. The nock is the string hand: the hand moved back along the line of fire by {@link UryuDrawLight#nockDistance}. All numbers are
 * starting values, tunable through {@code run/fx_override.json} (keys {@code uryu.hand*}, {@code uryu.fpHand*}, {@code uryu.fpNockMin}); none
 * has been seen in the game (UNVERIFIED).
 */
final class UryuHand {
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	private UryuHand() {
	}

	/** True when the entity is the local player and the camera is first person. */
	static boolean firstPerson(Entity e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return e == mc.player && mc.options.getPerspective().isFirstPerson();
	}

	/** True when {@code e} holds a drawn Uryu bow in the main hand. */
	static boolean hasBow(LivingEntity e) {
		return UryuCrossItem.isBow(e.getMainHandStack());
	}

	/** True while {@code e} draws the bow (the use hold of the main hand). */
	static boolean drawing(LivingEntity e) {
		return hasBow(e) && e.isUsingItem() && e.getActiveHand() == Hand.MAIN_HAND;
	}

	/** The power 0..1 of the draw in progress (0 when not drawing). */
	static double drawPower(LivingEntity e, float td) {
		return drawing(e) ? UryuDrawLight.power(e.getItemUseTime() + td) : 0.0;
	}

	static Vec3d feet(Entity e, float td) {
		return e.getLerpedPos(td);
	}

	/** Display scale of the bow in the hand: third person 1.4, first person 1.8 ({@code uryu_cross_display}). */
	static double bowScale(boolean firstPerson) {
		return firstPerson ? UryuDrawLight.SCALE_FIRST : UryuDrawLight.SCALE_THIRD;
	}

	/** The right vector of a body or head yaw in degrees (the player's own right: yaw 0 looks along +z, so right is -x). */
	static Vec3d right(double yawDeg) {
		double a = Math.toRadians(yawDeg);
		return new Vec3d(-Math.cos(a), 0, -Math.sin(a));
	}

	/** The forward vector of a yaw in degrees. */
	static Vec3d forward(double yawDeg) {
		double a = Math.toRadians(yawDeg);
		return new Vec3d(-Math.sin(a), 0, Math.cos(a));
	}

	static double bodyYaw(LivingEntity e, float td) {
		return MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
	}

	// ------------------------------------------------------------------ the camera basis (first person)

	/** Camera axes in world space: [0] right, [1] up, [2] forward. */
	static Vec3d[] camBasis(Camera cam) {
		Vector3f r = new Vector3f(1, 0, 0).rotate(cam.getRotation());
		Vector3f u = new Vector3f(0, 1, 0).rotate(cam.getRotation());
		Vector3f f = new Vector3f(0, 0, -1).rotate(cam.getRotation());
		return new Vec3d[] {new Vec3d(r.x, r.y, r.z), new Vec3d(u.x, u.y, u.z), new Vec3d(f.x, f.y, f.z)};
	}

	private static Camera camera() {
		return MinecraftClient.getInstance().gameRenderer.getCamera();
	}

	// ------------------------------------------------------------------ anchors

	/** The line of fire: the look while the bow is drawn, the way the body faces otherwise. */
	static Vec3d aim(LivingEntity e, float td) {
		if (firstPerson(e)) {
			return camBasis(camera())[2];
		}
		if (drawing(e)) {
			return e.getRotationVec(td);
		}
		return forward(bodyYaw(e, td));
	}

	/** The bow hand (the grip). */
	static Vec3d hand(LivingEntity e, float td) {
		if (firstPerson(e)) {
			Camera cam = camera();
			Vec3d[] b = camBasis(cam);
			return cam.getPos().add(b[0].multiply(FxTune.d("uryu.fpHandRight", 0.30))).add(b[1].multiply(FxTune.d("uryu.fpHandUp", -0.28)))
					.add(b[2].multiply(FxTune.d("uryu.fpHandFwd", 0.55)));
		}
		if (drawing(e)) {
			Vec3d eye = e.getCameraPosVec(td);
			Vec3d look = e.getRotationVec(td);
			Vec3d right = look.crossProduct(UP);
			right = right.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : right.normalize();
			return eye.add(look.multiply(FxTune.d("uryu.handDrawFwd", 0.62))).add(0, FxTune.d("uryu.handDrawUp", -0.28), 0)
					.add(right.multiply(FxTune.d("uryu.handDrawSide", 0.10)));
		}
		double yaw = bodyYaw(e, td);
		Vec3d f = feet(e, td);
		return f.add(right(yaw).multiply(FxTune.d("uryu.handRight", 0.35))).add(0, FxTune.d("uryu.handUp", 1.10), 0)
				.add(forward(yaw).multiply(FxTune.d("uryu.handFwd", 0.45)));
	}

	/** The nock: the string hand, behind the grip along the line of fire by the draw. */
	static Vec3d nock(LivingEntity e, float td, double p) {
		boolean fp = firstPerson(e);
		if (fp) {
			Camera cam = camera();
			Vec3d[] b = camBasis(cam);
			double back = UryuDrawLight.nockDistance(p, UryuDrawLight.SCALE_FIRST);
			double fwd = Math.max(FxTune.d("uryu.fpNockMin", 0.30), FxTune.d("uryu.fpHandFwd", 0.55) - back);
			return cam.getPos().add(b[0].multiply(FxTune.d("uryu.fpHandRight", 0.30) - 0.06 * p)).add(b[1].multiply(FxTune.d("uryu.fpHandUp", -0.28) + 0.04 * p))
					.add(b[2].multiply(fwd));
		}
		return hand(e, td).subtract(aim(e, td).multiply(UryuDrawLight.nockDistance(p, UryuDrawLight.SCALE_THIRD)));
	}

	/** The bow tips (top, bottom) around the hand, half the bow length along the vertical. */
	static Vec3d[] tips(Vec3d hand, boolean firstPerson) {
		double d = UryuDrawLight.tipDistance(firstPerson ? FxTune.d("uryu.fpTipScale", 1.0) : UryuDrawLight.SCALE_THIRD);
		return new Vec3d[] {hand.add(0, d, 0), hand.add(0, -d, 0)};
	}

	/** Horizontal unit vector from {@code at} towards the camera: the normal of the vertical planes (wheel, cross) that face the viewer. */
	static Vec3d facingCamera(Vec3d at) {
		Vec3d d = camera().getPos().subtract(at);
		Vec3d h = new Vec3d(d.x, 0, d.z);
		return h.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : h.normalize();
	}
}
