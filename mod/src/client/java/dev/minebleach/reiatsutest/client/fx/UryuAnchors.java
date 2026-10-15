package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuBowAnchor;
import dev.minebleach.reiatsutest.core.fx.UryuDrawLight;
import dev.minebleach.reiatsutest.core.uryu.BowDraw;
import dev.minebleach.reiatsutest.registry.UryuCrossItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * The client half of {@link UryuBowAnchor}: reads the camera and an entity and answers where the bow hand, the nock and the tips are right now
 * (the render frame, interpolated). Shared by {@link UryuBowFx} (forming, draw light) and {@link UryuFocusFx} (the Focus layers).
 */
final class UryuAnchors {
	/** +1 right hand (built default), -1 left hand: the option {@code uryu.bowHand} of the poses branch maps here when it is merged. */
	static double handSign() {
		return FxTune.d("uryu.bowHandSign", 1.0) < 0 ? -1.0 : 1.0;
	}

	private UryuAnchors() {
	}

	/** Camera position and basis of the frame. */
	record Cam(double[] pos, double[] right, double[] up, double[] fwd) {
	}

	/** Bow hand of one entity: the grip, the nock (for the draw power {@code p}), the aim and the bow's long axis, the display scale. */
	record Hand(double[] grip, double[] nock, double[] aim, double[] up, double scale, boolean first, boolean drawing, double p, double[] feet) {
	}

	static Cam cam() {
		Camera c = MinecraftClient.getInstance().gameRenderer.getCamera();
		Vec3d pos = c.getPos();
		org.joml.Quaternionf q = c.getRotation();
		Vector3f r = new Vector3f(1, 0, 0).rotate(q);
		Vector3f u = new Vector3f(0, 1, 0).rotate(q);
		double yaw = Math.toRadians(c.getYaw());
		double pitch = Math.toRadians(c.getPitch());
		double[] f = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
		return new Cam(new double[] {pos.x, pos.y, pos.z}, new double[] {r.x, r.y, r.z}, new double[] {u.x, u.y, u.z}, f);
	}

	static float tickDelta() {
		return MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
	}

	/** True for the local player while the camera is in first person. */
	static boolean first(LivingEntity e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return e == mc.player && mc.options.getPerspective().isFirstPerson();
	}

	/** True when the entity holds the drawn Heilig Bogen in its main hand. */
	static boolean holdsBow(LivingEntity e) {
		ItemStack s = e.getMainHandStack();
		return UryuCrossItem.isBow(s);
	}

	/** True while the entity draws the bow (item in use, main hand). */
	static boolean drawing(LivingEntity e) {
		return holdsBow(e) && e.isUsingItem() && e.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND;
	}

	/** Draw power 0..1 of the entity this frame (0 when it does not draw). */
	static double power(LivingEntity e, float td) {
		return drawing(e) ? BowDraw.powerAt(e.getItemUseTime() + td) : 0.0;
	}

	/** Aim unit vector: the camera forward for the local first person view, else the look vector of the entity. */
	static double[] aim(LivingEntity e, float td, Cam cam) {
		if (first(e)) {
			return cam.fwd();
		}
		Vec3d v = e.getRotationVec(td);
		return new double[] {v.x, v.y, v.z};
	}

	static double displayScale(boolean first) {
		return first ? UryuDrawLight.SCALE_FIRST : UryuDrawLight.SCALE_THIRD;
	}

	/** The bow hand of an entity in this frame; {@code p} the draw power (0 idle). */
	static Hand hand(LivingEntity e, double p, float td, Cam cam) {
		boolean first = first(e);
		boolean drawing = p > 0 || drawing(e);
		double[] aim = aim(e, td, cam);
		double[] up = {0, 1, 0};
		double scale = displayScale(first);
		double[] grip;
		double[] nock;
		Vec3d f = e.getLerpedPos(td);
		double[] feet = {f.x, f.y, f.z};
		if (first) {
			grip = UryuBowAnchor.firstGrip(cam.pos(), cam.right(), cam.up(), cam.fwd(), handSign());
			nock = UryuBowAnchor.firstNock(cam.pos(), cam.fwd(), grip, aim, p, scale);
		} else {
			float yaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
			grip = UryuBowAnchor.thirdGrip(feet, yaw, aim, drawing, handSign());
			nock = UryuBowAnchor.nock(grip, aim, p, scale);
		}
		return new Hand(grip, nock, aim, up, scale, first, drawing, p, feet);
	}

	/** True when the entity is within {@code range} blocks of the camera. */
	static boolean near(LivingEntity e, Cam cam, double range) {
		double dx = e.getX() - cam.pos()[0];
		double dy = e.getY() - cam.pos()[1];
		double dz = e.getZ() - cam.pos()[2];
		return dx * dx + dy * dy + dz * dz <= range * range;
	}

	static boolean reduceMotion() {
		return FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
	}

	static float[] mix(float[] a, float[] b, double t) {
		float f = (float) Math.max(0.0, Math.min(1.0, t));
		return new float[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
	}

	static double[] add(double[] a, double[] d, double k) {
		return new double[] {a[0] + d[0] * k, a[1] + d[1] * k, a[2] + d[2] * k};
	}
}
