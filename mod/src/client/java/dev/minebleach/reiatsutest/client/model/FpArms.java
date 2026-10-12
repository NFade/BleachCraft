package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.core.combat.ArmIk;
import dev.minebleach.reiatsutest.core.combat.FpGeometry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First person arms of the fp2 rig. Every arm of a zanpakuto pose is placed by {@link ArmIk}: the fist goes where it has to be (a point of the
 * hilt or of the scabbard, both rigid with the sword), the arm points to the shoulder behind and below the camera. The arms are drawn in "base
 * space", the camera space of the hand pass (view bobbing and the hand sway of vanilla included), so nothing depends on the hand frame pivots
 * of the item renderer.
 *
 * <p>Frame protocol (one per rendered frame): {@link #begin} at the head of {@code HeldItemRenderer#renderItem(F, ...)} remembers the base matrix;
 * {@link #captureHand} at the head of the item render remembers the hand frame (equip and swing offsets of vanilla) relative to it; the right arm
 * code stores the sword matrix with {@link #setSword}; the left arm and the scabbard hand read it later in the same frame.
 */
public final class FpArms {
	private static final Matrix4f BASE = new Matrix4f();
	private static final Matrix4f HAND = new Matrix4f();
	private static final Matrix4f SWORD = new Matrix4f();
	private static boolean baseOk;
	private static boolean handOk;
	private static boolean swordOk;
	private static boolean drawing;
	private static long lastLog;

	private FpArms() {
	}

	/** Head of the hand pass: the matrix stack carries base space (camera with bobbing and sway). */
	public static void begin(MatrixStack m) {
		BASE.set(m.peek().getPositionMatrix());
		baseOk = true;
		handOk = false;
		swordOk = false;
	}

	/** Head of the item render of the main hand: the hand frame relative to base space. */
	public static void captureHand(MatrixStack m) {
		if (!baseOk) {
			return;
		}
		HAND.set(BASE).invert().mul(m.peek().getPositionMatrix());
		handOk = true;
	}

	public static boolean hasHand() {
		return baseOk && handOk;
	}

	/** Hand frame of the main hand in base space (equip and swing offsets, no pose). */
	public static Matrix4f hand() {
		return new Matrix4f(HAND);
	}

	public static void setSword(Matrix4f modelToBase) {
		SWORD.set(modelToBase);
		swordOk = true;
	}

	/** True while {@link #draw} calls the vanilla arm renderer (read by {@code PlayerEntityRendererMixin}). */
	public static boolean drawing() {
		return drawing;
	}

	public static boolean hasSword() {
		return baseOk && swordOk;
	}

	/** Item model space (no draw rig) to base space of the held sword in this frame. */
	public static Matrix4f sword() {
		return new Matrix4f(SWORD);
	}

	/** A fresh matrix stack in base space for drawing. */
	public static MatrixStack stack() {
		MatrixStack ms = new MatrixStack();
		ms.multiplyPositionMatrix(BASE);
		return ms;
	}

	/**
	 * Draws one arm.
	 *
	 * @param armLeft whether the arm is the left one of the player
	 * @param fist where the fist centre has to be, base space
	 * @param blade unit direction of the held bar (hilt) in base space, or null
	 * @param roll extra roll about the arm axis, degrees
	 */
	public static void draw(MinecraftClient mc, AbstractClientPlayerEntity player, boolean armLeft, Vector3f fist, Vector3f blade, float roll,
			VertexConsumerProvider vcp, int light) {
		if (!baseOk) {
			return;
		}
		boolean slim = player.getSkinTextures().model() == SkinTextures.Model.SLIM;
		float maxStretch = FpMotion.num("arm.max_stretch", FpGeometry.MAX_STRETCH);
		float[] sh = ArmIk.shoulder(armLeft);
		float[] so = FpMotion.vec("arm.shoulder_offset", new float[3]);
		float[] shoulder = {sh[0] + (armLeft ? -so[0] : so[0]), sh[1] + so[1], sh[2] + so[2]};
		boolean square = FpMotion.num("arm.square", 1f) > 0.5f;
		ArmIk.Placement p = ArmIk.place(armLeft, slim, new float[] {fist.x, fist.y, fist.z}, shoulder,
				square && blade != null ? new float[] {blade.x, blade.y, blade.z} : null, roll, maxStretch);
		if (FpMotion.num("arm.debug", 0f) > 0.5f && System.nanoTime() - lastLog > 500_000_000L) {
			lastLog = System.nanoTime();
			float aspect = (float) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
			float[] nd = FpGeometry.ndc(new float[] {fist.x, fist.y, fist.z}, aspect);
			dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[fparm] {} fist=({}, {}, {}) ndc={} blade=({}, {}, {}) stretch={}", armLeft ? "left" : "right",
					String.format("%.2f", fist.x), String.format("%.2f", fist.y), String.format("%.2f", fist.z),
					nd == null ? "behind" : String.format("%.2f,%.2f", nd[0], nd[1]), String.format("%.2f", blade == null ? 0f : blade.x),
					String.format("%.2f", blade == null ? 0f : blade.y), String.format("%.2f", blade == null ? 0f : blade.z), String.format("%.2f", p.stretch()));
		}
		MatrixStack ms = stack();
		ms.translate(p.t()[0], p.t()[1], p.t()[2]);
		ms.multiply(new Quaternionf(p.q()[0], p.q()[1], p.q()[2], p.q()[3]));
		ms.scale(1f, p.stretch(), 1f);
		ms.translate(-p.fistLocal()[0], -p.fistLocal()[1], -p.fistLocal()[2]);
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
		drawing = true;
		try {
			if (armLeft) {
				renderer.renderLeftArm(ms, vcp, light, player);
			} else {
				renderer.renderRightArm(ms, vcp, light, player);
			}
		} finally {
			drawing = false;
		}
	}
}
