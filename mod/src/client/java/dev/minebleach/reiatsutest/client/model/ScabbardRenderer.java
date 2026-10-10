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
	public static void renderFirstPerson(MatrixStack m, VertexConsumerProvider vcp, ClientPlayerEntity player, int light) {
		if (player.isInvisible() || player.isUsingSpyglass()) {
			return;
		}
		ItemStack main = player.getMainHandStack();
		ObjItemBakedModel om = modelOf(player, main);
		if (om == null) {
			return;
		}
		boolean left = player.getMainArm() == Arm.LEFT;
		ModelTransformationMode mode = left ? ModelTransformationMode.FIRST_PERSON_LEFT_HAND : ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
		int i = left ? -1 : 1;
		Transformation tr = om.getTransformation().getTransformation(mode);
		DrawRig rig = om.rig(left);
		// the hand matrices carry the camera rotation: positions below are taken back into the frame the hand code works in
		Matrix4f baseInv = new Matrix4f(m.peek().getPositionMatrix()).invert();

		m.push();
		m.translate(i * 0.56f, -0.52f, -0.72f); // the resting main hand frame (equip and swing do not move the left hand)
		tr.apply(left, m);
		m.translate(-0.5f, -0.5f, -0.5f);
		m.translate(rig.stow.t()[0], rig.stow.t()[1], rig.stow.t()[2]);
		m.multiply(new Quaternionf(rig.stow.q()[0], rig.stow.q()[1], rig.stow.q()[2], rig.stow.q()[3]));
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
			s = Math.max(0.30f, Math.min(0.66f, s));
			Vector3f hold = new Vector3f(g).fma(s * unit, d);
			Vector3f delta = hold.sub(fist);
			if (DEBUG && (System.nanoTime() / 1_000_000_000L) % 2 == 0) {
				dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.info("[scabbard] g={} d={} unit={} s={} delta={}", g, d, unit, s, delta);
			}
			m.pop();
			m.push();
			m.translate(-i * 0.56f, -0.52f, -0.72f);
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

		m.push();
		model.body.rotate(m);
		float side = e.getMainArm() == Arm.RIGHT ? 1f : -1f; // the scabbard hangs opposite the sword hand
		m.translate(side * hip.posPx()[0] / 16f, hip.posPx()[1] / 16f, hip.posPx()[2] / 16f);
		m.multiply(basis(hip.dir()));
		m.scale(hip.scale(), hip.scale(), hip.scale());
		m.translate(-0.5f, -0.5f, -0.5f);
		VertexConsumer vc = vcp.getBuffer(TexturedRenderLayers.getEntityCutout());
		drawMesh(om.sayaMesh(), m.peek(), vc, light);
		if (p < rig.slideEnd) {
			DrawRig.Rigid arc = rig.arc(rig.clearAngle() * (p / rig.slideEnd));
			m.push();
			m.translate(arc.t()[0], arc.t()[1], arc.t()[2]);
			m.multiply(new Quaternionf(arc.q()[0], arc.q()[1], arc.q()[2], arc.q()[3]));
			drawMesh(om.swordMesh(), m.peek(), vc, light);
			m.pop();
		}
		m.pop();
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
