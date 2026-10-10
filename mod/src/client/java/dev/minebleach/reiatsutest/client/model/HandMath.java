package dev.minebleach.reiatsutest.client.model;

import dev.minebleach.reiatsutest.core.obj.DrawRig;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import net.minecraft.client.render.model.json.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Display transform maths shared by the scabbard renderer and the first person arm: the exact chain of
 * {@code Transformation#apply} plus the item renderer's {@code translate(-0.5)}, as JOML, so poses given in the hand frame can
 * be converted to item model space and back.
 */
public final class HandMath {
	private static final float DEG = (float) (Math.PI / 180.0);

	private HandMath() {
	}

	/** Rotation quaternion of the display transform, with the same left hand mirroring as {@code Transformation#apply}. */
	public static Quaternionf rotation(Transformation tr, boolean leftHanded) {
		float g = tr.rotation.y();
		float h = tr.rotation.z();
		if (leftHanded) {
			g = -g;
			h = -h;
		}
		return new Quaternionf().rotationXYZ(tr.rotation.x() * DEG, g * DEG, h * DEG);
	}

	/** Item model space point to the hand frame (the frame {@code Transformation#apply} starts in). */
	public static Matrix4f modelToHand(Transformation tr, boolean leftHanded) {
		int i = leftHanded ? -1 : 1;
		return new Matrix4f().translate(i * tr.translation.x(), tr.translation.y(), tr.translation.z())
				.rotate(rotation(tr, leftHanded)).scale(tr.scale.x(), tr.scale.y(), tr.scale.z()).translate(-0.5f, -0.5f, -0.5f);
	}

	/** The stow pose of the manifest (hand frame numbers) as a rigid transform of item model space. */
	public static DrawRig.Rigid stowToModel(Transformation tr, boolean leftHanded, ItemManifest.Stow st) {
		float mx = leftHanded ? -1f : 1f;
		Quaternionf d = rotation(tr, leftHanded);
		Quaternionf qh = new Quaternionf().rotationZYX(mx * st.rot()[2] * DEG, mx * st.rot()[1] * DEG, st.rot()[0] * DEG);
		Quaternionf qm = new Quaternionf(d).invert().mul(qh).mul(d);
		Vector3f move = new Vector3f(mx * st.move()[0], st.move()[1], st.move()[2]);
		new Quaternionf(d).invert().transform(move);
		move.div(tr.scale.x());
		Vector3f o = new Vector3f(0.5f, 0.5f, 0.5f);
		Vector3f qo = new Vector3f(o);
		qm.transform(qo);
		float[] t = {o.x - qo.x + move.x, o.y - qo.y + move.y, o.z - qo.z + move.z};
		return new DrawRig.Rigid(new float[] {qm.x, qm.y, qm.z, qm.w}, t);
	}

	/** Item model space rigid transform to a JOML matrix (rotation then translation). */
	public static Matrix4f toMatrix(DrawRig.Rigid r) {
		return new Matrix4f().translate(r.t()[0], r.t()[1], r.t()[2]).rotate(new Quaternionf(r.q()[0], r.q()[1], r.q()[2], r.q()[3]));
	}
}
