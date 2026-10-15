package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.core.uryu.BowDraw;
import dev.minebleach.reiatsutest.core.uryu.BowFpPose;
import dev.minebleach.reiatsutest.core.uryu.BowHandMotion;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * R12, first person view of Uryu's bow for right handed players: the archer's view. The bow stands in the LEFT hand slot of the screen
 * (left of the centre, turned so its arc, the string and the arrow can be read), the left arm holds the grip, and the RIGHT arm comes
 * from the bottom right to the string: it follows the nock point of the bow model while the bow is drawn (the same draw curve as the
 * string, {@link BowDraw#nockZ}), holds at full draw, snaps forward on the release and relaxes back to the resting place of an empty
 * vanilla hand ({@link BowHandMotion}). Same condition as the third person pose: {@link UryuBowPose#bowInLeftHand}. Left handed players and
 * a non empty off hand keep the vanilla path (the plain display transform).
 *
 * <p>The vanilla first person item pass is REPLACED for this item ({@code HeldItemRendererBowMixin} cancels
 * {@code HeldItemRenderer#renderFirstPersonItem} at its head): vanilla's UseAction.BOW branch moves the item to the right hand side and
 * toward the camera, which would fight this pose; the equip lift (the equip progress argument) is kept, the swing is not. The item itself
 * is still drawn by {@code HeldItemRenderer#renderItem} with the FIRST_PERSON_RIGHT_HAND display transform; its display part is divided
 * out of the matrix first, so the pose below is the complete model placement (grip, rotation, scale) and does not depend on the numbers of
 * {@code uryu_cross_display.json} (the sealed pendant and the left handed players still use those).
 *
 * <p>Tuning without a rebuild: {@link BowFpPose} documents the {@code fp_bow} section of {@code pose_override.json}.
 */
public final class FpBow {
	/** Where the vanilla fist of an empty hand is in the hand frame of the vanilla hold (see {@link FirstPersonHand}), blocks. */
	private static final float FIST_X = 0.134f;
	private static final float FIST_Y = -0.018f;
	private static final float FIST_Z = -0.316f;
	private static final float RAD = (float) (Math.PI / 180.0);

	private static final BowHandMotion HAND = new BowHandMotion();
	private static long lastNanos;
	private static boolean active;
	private static double peakPull;

	private FpBow() {
	}

	/** True while this class draws the bow item (the arm of the generic zanpakuto hold and the sword motion must stay out of it). */
	public static boolean active() {
		return active;
	}

	/** True when the first person main hand pass of this stack is replaced by the archer's view. */
	public static boolean wants(AbstractClientPlayerEntity player, Hand hand, ItemStack item) {
		return hand == Hand.MAIN_HAND && player == MinecraftClient.getInstance().player && UryuBowPose.isBowStack(item)
				&& UryuBowPose.bowInLeftHand(player) && BowFpPose.of(FpMotion.params()).enabled();
	}

	/**
	 * Draws the arms and the bow. {@code equipProgress} is the argument of {@code renderFirstPersonItem} (0 = equipped, 1 = lowered);
	 * {@code drawItem} draws the held item with the given matrix stack ({@code HeldItemRenderer#renderItem} with FIRST_PERSON_RIGHT_HAND).
	 */
	public static void render(AbstractClientPlayerEntity player, ItemStack stack, float equipProgress, MatrixStack m,
			VertexConsumerProvider vcp, int light, Consumer<MatrixStack> drawItem) {
		MinecraftClient mc = MinecraftClient.getInstance();
		BowFpPose p = BowFpPose.of(FpMotion.params());
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		boolean using = player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND;
		float td = mc.getRenderTickCounter().getTickDelta(false);
		// the same draw progress as the string and the arrow of the model (ObjItemBakedModel#emitBowDraw)
		HAND.step(dt, using, BowDraw.powerAt(player.getItemUseTime() + td), p.readyIn(), p.readyOut(), p.snap());

		// R13: the bow rests low (the forearm comes in from the left) and rises and turns to the crosshair as the arrow is drawn
		float raise = dev.minebleach.reiatsutest.core.combat.FpEase.smooth(HAND.blend());
		if (raise < 0.02f) {
			peakPull = 0.0; // the hand is at rest again
		}
		peakPull = Math.max(peakPull, HAND.pull());
		BowFpPose.Placed pl = p.placed(raise, peakPull); // the peak of this draw: the snap of the release drops the pull to 0, the bow must not fall back with it
		Quaternionf q = new Quaternionf().rotateZ(pl.roll() * RAD).rotateX(pl.pitch() * RAD).rotateY((180f + pl.yaw()) * RAD);
		Vector3f grip = new Vector3f(pl.grip());
		Matrix4f bow = new Matrix4f().translate(grip).rotate(q).scale(p.scale()); // model space (centre = grip) to the hand frame

		// R13: hand the pose to the effects (glow, nock light, Focus), model space to the camera frame including the equip lift
		dev.minebleach.reiatsutest.client.fx.UryuBowCapture.captureFirstPerson(player.getId(), new Matrix4f().translate(0f, -0.6f * equipProgress, 0f).mul(bow));
		m.push();
		m.translate(0f, -0.6f * equipProgress, 0f); // the equip lift of vanilla
		if (ClientOptions.showFirstPersonHand && !player.isInvisible()) {
			// bow hand: the fist on the grip
			Vector3f leftFist = bow.transformPosition(new Vector3f()).add(p.leftOff()[0], p.leftOff()[1], p.leftOff()[2]);
			drawArm(mc, player, true, m, vcp, light, leftFist, pl.leftRot(), 1f);
			// draw hand: from the resting empty hand to the nock point of the string, rotated like the bow
			float e = dev.minebleach.reiatsutest.core.combat.FpEase.smooth(HAND.blend());
			Vector3f rest = restFist(false);
			Vector3f nock = bow.transformPosition(new Vector3f(0f, 0f, BowDraw.nockZ(HAND.pull()) - 0.5f))
					.add(p.rightOff()[0], p.rightOff()[1], p.rightOff()[2]);
			Vector3f rightFist = new Vector3f(rest).lerp(nock, e);
			drawArm(mc, player, false, m, vcp, light, rightFist, p.rightRot(), e);
		}
		// the bow: complete model placement, the display transform of the right hand (applied by the item renderer) divided out
		BakedModel model = mc.getItemRenderer().getModel(stack, player.getWorld(), player, 0);
		m.push();
		m.translate(grip.x, grip.y, grip.z);
		m.multiply(q);
		m.scale(p.scale(), p.scale(), p.scale());
		MatrixStack disp = new MatrixStack();
		model.getTransformation().getTransformation(ModelTransformationMode.FIRST_PERSON_RIGHT_HAND).apply(false, disp);
		Matrix4f dm = disp.peek().getPositionMatrix();
		Vector3f dt3 = dm.getTranslation(new Vector3f());
		Vector3f ds = dm.getScale(new Vector3f());
		Quaternionf dr = dm.getNormalizedRotation(new Quaternionf());
		m.scale(1f / ds.x, 1f / ds.y, 1f / ds.z);
		m.multiply(dr.conjugate());
		m.translate(-dt3.x, -dt3.y, -dt3.z);
		active = true;
		try {
			drawItem.accept(m);
		} finally {
			active = false;
		}
		m.pop();
		m.pop();
	}

	/** Place of the fist of a vanilla empty hand in the (equip lifted) hand frame: where the draw hand rests. */
	private static Vector3f restFist(boolean left) {
		float s = left ? -1f : 1f;
		return new Vector3f(s * (0.56f + FIST_X), -0.52f + FIST_Y, -0.72f + FIST_Z);
	}

	/**
	 * A vanilla arm with its fist on {@code fist} (hand frame), turned by {@code rotDeg * weight} about the fist (camera frame). Same method as
	 * {@link FpMotion#renderLeftGuard}: the frame of the main hand, shifted by the distance from the resting fist.
	 */
	private static void drawArm(MinecraftClient mc, AbstractClientPlayerEntity player, boolean left, MatrixStack m, VertexConsumerProvider vcp,
			int light, Vector3f fist, float[] rotDeg, float weight) {
		float s = left ? -1f : 1f;
		Vector3f d = new Vector3f(fist).sub(restFist(left));
		m.push();
		m.translate(s * 0.56f, -0.52f, -0.72f);
		m.translate(d.x, d.y, d.z);
		if (weight > 0.001f) {
			m.translate(s * FIST_X, FIST_Y, FIST_Z);
			m.multiply(new Quaternionf().rotationXYZ(rotDeg[0] * weight * RAD, rotDeg[1] * weight * RAD, rotDeg[2] * weight * RAD));
			m.translate(-s * FIST_X, -FIST_Y, -FIST_Z);
		}
		FirstPersonHand.renderVanillaArmAt(mc, player, left, m, vcp, light);
		m.pop();
	}
}
