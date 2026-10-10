package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.client.ClientOptions;
import dev.minebleach.reiatsutest.core.obj.DrawRig;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The scabbard of the zanpakuto, drawn on the client WITHOUT an item (B4 step 2, decision (a) in LOG): straight from the baked
 * mesh into a vertex consumer. It is visible while the entity holds a zanpakuto item in its main hand, in every state:
 * <ul>
 * <li>first person: in the other hand, at the stow pose of the draw rig ({@link DrawRig}); the sheathed sword is the sword
 * transformed by the same pose, so blade and saya coincide until the draw. Called from the {@code HeldItemRenderer}
 * mixin once per frame; draws the arm that holds it too.</li>
 * <li>third person: on the hip opposite the sword hand ({@link ScabbardFeature}); while sheathed or sliding out it also draws
 * the sword inside it.</li>
 * </ul>
 */
public final class ScabbardRenderer {
	/** Vanilla first person fist (hand frame of an empty or holding hand, mirrored for the other side), in camera space. */
	private static final float FIST_X = 0.694f;
	private static final float FIST_Y = -0.538f;
	private static final float FIST_Z = -1.036f;

	/** How far (blocks, hand frame) the scabbard and the left hand are lowered when they leave the screen (toward the lower left). */
	private static final float SAYA_OUT_DX = 0.25f;
	private static final float SAYA_OUT_DY = 0.9f;

	private static final boolean DEBUG = Boolean.getBoolean("reiatsu.spike");

	private ScabbardRenderer() {
	}

	/** The baked model of a held zanpakuto stack when it carries a scabbard, else null. */
	public static ObjItemBakedModel modelOf(LivingEntity e, ItemStack stack) {
		if (!(stack.getItem() instanceof ZanpakutoItem)) {
			return null;
		}
		BakedModel bm = MinecraftClient.getInstance().getItemRenderer().getModel(stack, e.getWorld(), e, 0);
		if (bm instanceof ObjItemBakedModel om && om.sayaMesh() != null) {
			return om;
		}
		return null;
	}

	/** Mesh quads straight into a vertex consumer with the given transform (entity cutout layer of the block atlas). */
	public static void drawMesh(Mesh mesh, MatrixStack.Entry entry, VertexConsumer vc, int light) {
		Matrix4f pm = entry.getPositionMatrix();
		mesh.forEach(q -> {
			for (int c = 0; c < 4; c++) {
				vc.vertex(pm, q.x(c), q.y(c), q.z(c)).color(0xFFFFFFFF).texture(q.u(c), q.v(c))
						.overlay(OverlayTexture.DEFAULT_UV).light(light);
				if (q.hasNormal(c)) {
					vc.normal(entry, q.normalX(c), q.normalY(c), q.normalZ(c));
				} else {
					vc.normal(entry, 0f, 1f, 0f);
				}
			}
		});
	}

	// ------------------------------------------------------------------ first person

	/** Called at the end of {@code HeldItemRenderer#renderItem(F,...)}: matrices are in the camera frame (sway applied). */
	public static void renderFirstPerson(MatrixStack m, VertexConsumerProvider vcp, ClientPlayerEntity player, int light,
			ItemStack shown, float equip) {
		if (player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}
		// the stack the first person renderer shows (the cached one: during an equip animation it is the old item); equip 0..1
		// is the vanilla raise progress of the main hand, the scabbard rises with it
		ItemStack main = shown;
		ObjItemBakedModel om = modelOf(player, main);
		if (om == null) {
			return;
		}
		float eqY = -0.6f * (1f - Math.max(0f, Math.min(1f, equip)));
		boolean left = player.getMainArm() == Arm.LEFT;
		ModelTransformationMode mode = left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
		int i = left ? -1 : 1;
		Transformation tr = om.getTransformation().getTransformation(mode);
		DrawRig rig = om.rig(left);
		float prog = DrawTracker.effectiveProgress(player, main);
		float out = DrawRig.sayaOut(prog); // B4 polish: after the draw the scabbard leaves the first person screen
		if (out >= 1f) {
			return;
		}
		// the hand matrices carry the camera rotation: positions below are taken back into the frame the hand code works in
		Matrix4f baseInv = new Matrix4f(m.peek().getPositionMatrix()).invert();

		m.push();
		m.translate(i * 0.56f, -0.52f + eqY, -0.72f); // the resting main hand frame (swing does not move the scabbard)
		float[] lf = HeldPose.of(om.manifest()).lift(); // the scabbard shares the lift of the sword (stow pose compensated in the rig)
		m.translate(i * lf[0], lf[1], lf[2]);
		m.translate(-i * SAYA_OUT_DX * out, -SAYA_OUT_DY * out, 0f); // lowered out of the screen by the left hand
		tr.apply(left, m);
		m.translate(-0.5f, -0.5f, -0.5f);
		DrawRig.Rigid sp = rig.sayaAt(prog); // pulled back while the blade leaves it
		m.translate(sp.t()[0], sp.t()[1], sp.t()[2]);
		m.multiply(new Quaternionf(sp.q()[0], sp.q()[1], sp.q()[2], sp.q()[3]));
		VertexConsumer vc = vcp.getBuffer(TexturedRenderLayers.getEntityCutout());
		drawMesh(om.sayaMesh(), m.peek(), vc, light);

		if (ClientOptions.showFirstPersonHand) {
			// the other hand holds the scabbard: the point of the axis nearest to the vanilla fist of that side
			Matrix4f pm = new Matrix4f(baseInv).mul(m.peek().getPositionMatrix());
			Vector3f g = pm.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
			Vector3f tip = pm.transformPosition(new Vector3f(0.5f, 1.5f, 0.5f));
			Vector3f d = new Vector3f(tip).sub(g);
			float unit = d.length(); // one metre of blade in camera space
			d.div(unit);
			Vector3f fist = new Vector3f(-i * FIST_X, FIST_Y, FIST_Z);
			float s = new Vector3f(fist).sub(g).dot(d) / unit; // metres from the grip along the blade
			s = Math.max(0.12f, Math.min(0.70f, s));
			Vector3f hold = new Vector3f(g).fma(s * unit, d);
			Vector3f delta = hold.sub(fist);
			if (DEBUG && (System.nanoTime() / 1_000_000_000L) % 2 == 0) {
				dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[scabbard] g={} d={} unit={} s={} delta={}", g, d, unit, s, delta);
			}
			m.pop();
			m.push();
			m.translate(-i * 0.56f, -0.52f + eqY, -0.72f);
			m.translate(delta.x, delta.y, delta.z);
			FirstPersonHand.renderVanillaArmAt(MinecraftClient.getInstance(), player, !left, m, vcp, light);
		}
		m.pop();
	}

	// ------------------------------------------------------------------ third person

	private static final float[] DEFAULT_DIR = {0f, 0.30f, 0.95f};

	/** Scabbard (and the sword while it is in it) on the hip of a biped, matrices at the entity origin of a feature renderer. */
	public static void renderHip(MatrixStack m, VertexConsumerProvider vcp, int light, LivingEntity e, BipedEntityModel<?> model) {
		if (e.isInvisible()) {
			return;
		}
		ItemStack main = e.getMainHandStack();
		ObjItemBakedModel om = modelOf(e, main);
		if (om == null) {
			return;
		}
		float p = DrawTracker.effectiveProgress(e, main);
		ItemManifest man = om.manifest();
		ItemManifest.Hip hip = man.hip != null ? man.hip : new ItemManifest.Hip(new float[] {6.2f, 9f, -1f}, DEFAULT_DIR, 1f);
		DrawRig rig = om.rig(false);

		Matrix4f base = new Matrix4f(m.peek().getPositionMatrix());
		VertexConsumer vc = vcp.getBuffer(TexturedRenderLayers.getEntityCutout());
		m.push();
		model.body.rotate(m);
		float side = e.getMainArm() == Arm.RIGHT ? 1f : -1f; // the scabbard hangs opposite the sword hand
		m.translate(side * hip.posPx()[0] / 16f, hip.posPx()[1] / 16f, hip.posPx()[2] / 16f);
		m.multiply(basis(hip.dir()));
		m.scale(hip.scale(), hip.scale(), hip.scale());
		m.translate(-0.5f, -0.5f, -0.5f);
		drawMesh(om.sayaMesh(), m.peek(), vc, light);
		DrawRig.Rigid arc = rig.arc(rig.clearAngle() * Math.min(1f, p / rig.slideEnd));
		if (p < rig.slideEnd) {
			m.push();
			m.translate(arc.t()[0], arc.t()[1], arc.t()[2]);
			m.multiply(new Quaternionf(arc.q()[0], arc.q()[1], arc.q()[2], arc.q()[3]));
			drawMesh(om.swordMesh(), m.peek(), vc, light);
			m.pop();
		}
		debugCompare(m, e, om, model, p);
		Matrix4f hipFrame = null;
		if (p >= rig.slideEnd && p < 1f) {
			// T3: the blade is clear of the mouth (end of the slide pose, in the frame of the hip)
			m.push();
			m.translate(arc.t()[0], arc.t()[1], arc.t()[2]);
			m.multiply(new Quaternionf(arc.q()[0], arc.q()[1], arc.q()[2], arc.q()[3]));
			hipFrame = new Matrix4f(m.peek().getPositionMatrix());
			m.pop();
		}
		m.pop();
		if (hipFrame != null) {
			// the blade travels from the hip to the hand; at p = 1 it is exactly where the hand item draws it
			Matrix4f handFrame = handFrame(m, e, om, model);
			float u = DrawRig.smooth((p - rig.slideEnd) / (1f - rig.slideEnd));
			blendFrames(m, base, hipFrame, handFrame, u);
			drawMesh(om.swordMesh(), m.peek(), vc, light);
			m.pop();
		}
	}

	/** Dev check (-Dreiatsu.spike): the model space frame the hand item really got, captured in the held item mixin, against {@link #handFrame}. */
	private static Matrix4f realHand;
	private static int realHandId = -1;

	public static void captureHand(LivingEntity e, MatrixStack m, boolean leftHanded, ObjItemBakedModel om) {
		if (!DEBUG) {
			return;
		}
		m.push();
		ModelTransformationMode mode = leftHanded ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
		om.getTransformation().getTransformation(mode).apply(leftHanded, m);
		m.translate(-0.5f, -0.5f, -0.5f);
		realHand = new Matrix4f(m.peek().getPositionMatrix());
		realHandId = e.getId();
		m.pop();
	}

	private static void debugCompare(MatrixStack m, LivingEntity e, ObjItemBakedModel om, BipedEntityModel<?> model, float p) {
		if (!DEBUG || realHand == null || realHandId != e.getId() || p < 1f || (System.nanoTime() / 500_000_000L) % 4 != 0) {
			return;
		}
		Matrix4f mine = handFrame(m, e, om, model);
		float d = 0f;
		for (int c = 0; c < 4; c++) {
			for (int r = 0; r < 4; r++) {
				d = Math.max(d, Math.abs(mine.get(c, r) - realHand.get(c, r)));
			}
		}
		Vector3f s1 = mine.getScale(new Vector3f());
		Vector3f s2 = realHand.getScale(new Vector3f());
		dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[handframe] maxdiff={} predictedScale={} realScale={} mineT={} realT={}", d, s1, s2,
				mine.getTranslation(new Vector3f()), realHand.getTranslation(new Vector3f()));
	}

	/**
	 * Model space frame of the sword when it is in the hand at the end of the draw: exactly the chain the vanilla held item
	 * feature renderer builds (arm angle, the -90 / 180 turn, the hand offset, child scale) followed by the display transform
	 * of the third person hand mode and the model centre shift, as {@code ItemRenderer} applies it. Leaves {@code m} unchanged.
	 */
	private static Matrix4f handFrame(MatrixStack m, LivingEntity e, ObjItemBakedModel om, BipedEntityModel<?> model) {
		Arm arm = e.getMainArm();
		boolean left = arm == Arm.LEFT;
		m.push();
		if (model.child) {
			m.translate(0f, 0.75f, 0f);
			m.scale(0.5f, 0.5f, 0.5f);
		}
		model.setArmAngle(arm, m);
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
		m.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
		ModelTransformationMode mode = left ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
		om.getTransformation().getTransformation(mode).apply(left, m);
		m.translate(-0.5f, -0.5f, -0.5f);
		Matrix4f f = new Matrix4f(m.peek().getPositionMatrix());
		m.pop();
		return f;
	}

	/**
	 * Pushes onto {@code m} the model space frame blended between two frames (given in the same space as {@code base}, the matrix
	 * at the start of the feature): decomposed about the grip (model point 0.5, 0.5, 0.5) into position, rotation (slerp) and
	 * uniform scale (lerp), so the hilt moves on a straight line and the blade turns about it.
	 */
	static void blendFrames(MatrixStack m, Matrix4f base, Matrix4f a, Matrix4f b, float u) {
		Matrix4f inv = new Matrix4f(base).invert();
		Vector3f pa = new Vector3f();
		Vector3f pb = new Vector3f();
		Vector3f sa = new Vector3f();
		Vector3f sb = new Vector3f();
		Quaternionf qa = new Quaternionf();
		Quaternionf qb = new Quaternionf();
		Matrix4f ga = new Matrix4f(inv).mul(a).translate(0.5f, 0.5f, 0.5f);
		Matrix4f gb = new Matrix4f(inv).mul(b).translate(0.5f, 0.5f, 0.5f);
		ga.getTranslation(pa);
		gb.getTranslation(pb);
		ga.getScale(sa);
		gb.getScale(sb);
		ga.getNormalizedRotation(qa);
		gb.getNormalizedRotation(qb);
		Vector3f p = pa.lerp(pb, u);
		float sc = sa.x + (sb.x - sa.x) * u;
		Quaternionf q = qa.slerp(qb, u);
		m.push();
		m.translate(p.x, p.y, p.z);
		m.multiply(q);
		m.scale(sc, sc, sc);
		m.translate(-0.5f, -0.5f, -0.5f);
	}

	/**
	 * Rotation taking the model +Y (blade axis) to {@code dir}, with the edge side (model +Z) up (body space -Y) and the flat
	 * faces toward the sides.
	 */
	static Quaternionf basis(float[] dir) {
		Vector3f y = new Vector3f(dir[0], dir[1], dir[2]).normalize();
		Vector3f up = new Vector3f(0, -1, 0);
		Vector3f z = new Vector3f(up).fma(-up.dot(y), y).normalize();
		Vector3f x = new Vector3f(y).cross(z);
		return new Quaternionf().setFromNormalized(new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z));
	}
}
