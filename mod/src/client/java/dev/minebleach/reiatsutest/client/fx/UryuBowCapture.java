package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.uryu.BowDraw;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * R13: where the bow of the LOCAL player really is in first person, published by {@code FpBow} (the archer's view) each frame as the matrix that maps the bow model
 * (origin = grip, +z the arrow axis, +y the limbs) to the camera frame of the hand pass (x right, y up, -z forward, blocks). The glow layers, the nock light and the Focus
 * feedback sit on it ({@link UryuAnchors#hand}) and so follow the pose of the archer's view whatever its numbers are. An attempt to take the same from the matrix stack of
 * the item render (as the dissolve glow of the Byakuya sword does) gave positions metres off in the dev harness, so the pose is handed over from the code that built it.
 * Third person has no capture: there the formula of {@link UryuAnchors} with the left hand (the bow is in the left hand since R12) is used.
 */
public final class UryuBowCapture {
	private static final long FRESH_NANOS = 120_000_000L;
	private static final Map<Integer, Pose> POSES = new HashMap<>();

	private UryuBowCapture() {
	}

	private static final class Pose {
		final Matrix4f m = new Matrix4f();
		long nanos;
	}

	/** The bow in world space: the grip, the nock for a draw {@code pull}, the arrow axis, the limb axis (unit vectors) and blocks per model unit. */
	public record Frame(double[] grip, double[] nock, double[] aim, double[] up, double scale) {
	}

	/** Called by {@code FpBow} each frame: {@code bowToCamera} maps model space (grip at the origin) to the camera frame of the hand pass. */
	public static void captureFirstPerson(int entityId, Matrix4f bowToCamera) {
		Pose p = POSES.computeIfAbsent(entityId, k -> new Pose());
		p.m.set(bowToCamera);
		p.nanos = System.nanoTime();
	}

	private static void toWorld(Matrix4f m, double x, double y, double z, double[] cam, double[] r, double[] u, double[] f, double k, double[] out) {
		Vector3f v = m.transformPosition((float) x, (float) y, (float) z, new Vector3f());
		for (int i = 0; i < 3; i++) {
			out[i] = cam[i] + r[i] * v.x * k + u[i] * v.y * k + f[i] * -v.z;
		}
	}

	/** The captured bow of this entity in world space, or null when there is no fresh capture. {@code pull} is the draw progress 0..1 (the nock follows it). */
	public static Frame frame(int entityId, double pull, UryuAnchors.Cam c) {
		Pose p = POSES.get(entityId);
		if (p == null || System.nanoTime() - p.nanos > FRESH_NANOS) {
			return null;
		}
		double k = ShikaiDissolve.handFovK();
		double[] g = new double[3];
		double[] a = new double[3];
		double[] u = new double[3];
		double[] n = new double[3];
		toWorld(p.m, 0, 0, 0, c.pos(), c.right(), c.up(), c.fwd(), k, g);
		toWorld(p.m, 0, 0, 1, c.pos(), c.right(), c.up(), c.fwd(), k, a);
		toWorld(p.m, 0, 1, 0, c.pos(), c.right(), c.up(), c.fwd(), k, u);
		toWorld(p.m, 0, 0, BowDraw.nockZ(pull) - 0.5, c.pos(), c.right(), c.up(), c.fwd(), k, n);
		double[] aim = {a[0] - g[0], a[1] - g[1], a[2] - g[2]};
		double[] up = {u[0] - g[0], u[1] - g[1], u[2] - g[2]};
		double scale = Math.sqrt(aim[0] * aim[0] + aim[1] * aim[1] + aim[2] * aim[2]);
		double ul = Math.sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2]);
		if (scale < 1.0E-6) {
			return null;
		}
		for (int i = 0; i < 3; i++) {
			aim[i] /= scale;
			up[i] = ul < 1.0E-6 ? (i == 1 ? 1.0 : 0.0) : up[i] / ul;
		}
		return new Frame(g, n, aim, up, scale);
	}

	public static void clear() {
		POSES.clear();
	}
}
