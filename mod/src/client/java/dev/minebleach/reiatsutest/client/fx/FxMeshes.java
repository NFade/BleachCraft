package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.model.ObjModelRegistry;
import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The exported Rukia bankai meshes (crystals a to d, ice shell, shards, ribbon segments) as flat arrays for the effect
 * renderers (1.7.3): taken from {@link ObjModelRegistry} (the item manifest of the sword loads every object of its models, also
 * the ones no state uses), object origin removed, written quad by quad with a transform into any translucent entity layer
 * of the model texture. Mesh units are blocks; +Y is the long axis of a crystal (the Blender Z axis), a shell stands on y = 0
 * (box 1 x 2 x 1) and a ribbon segment hangs along -Y from its origin.
 */
public final class FxMeshes {
	public static final String ITEM = "sode_no_shirayuki";
	public static final String CRYSTAL_A = "rukia_bankai_crystal_a";
	public static final String CRYSTAL_B = "rukia_bankai_crystal_b";
	public static final String CRYSTAL_C = "rukia_bankai_crystal_c";
	public static final String CRYSTAL_D = "rukia_bankai_crystal_d";
	public static final String SHELL = "rukia_bankai_ice_shell";
	public static final String SHARD_A = "rukia_bankai_shard_a";
	public static final String SHARD_B = "rukia_bankai_shard_b";
	public static final String RIBBON_SEG = "rukia_bankai_ribbon_seg";
	public static final String RIBBON_TIP = "rukia_bankai_ribbon_tip";

	public static final Identifier DIFFUSE = ReiatsuTest.id("textures/item/rukia_bankai_diffuse.png");
	public static final Identifier EMISSIVE = ReiatsuTest.id("textures/item/rukia_bankai_emissive.png");
	public static final Identifier SWIRL = ReiatsuTest.id("textures/fx/frost_swirl.png");

	/** Corner data: x y z u v nx ny nz for 4 corners of every quad (triangles repeat the third corner). */
	public static final class Mesh {
		final float[] data;
		public final int quads;
		/** Local bounding box height (max y) for sizing. */
		public final float height;

		Mesh(float[] data, int quads, float height) {
			this.data = data;
			this.quads = quads;
			this.height = height;
		}
	}

	private static final Map<String, Mesh> MESHES = new HashMap<>();
	private static final Mesh EMPTY = new Mesh(new float[0], 0, 0);

	private FxMeshes() {
	}

	/** The mesh of an object (loaded on first use); an empty mesh while the registry has not parsed the sword manifest yet. */
	public static Mesh get(String object) {
		Mesh m = MESHES.get(object);
		if (m != null) {
			return m;
		}
		List<ObjGeometry.Quad> quads = ObjModelRegistry.localQuads(ITEM, object);
		if (quads.isEmpty()) {
			return EMPTY;
		}
		float[] d = new float[quads.size() * 32];
		float maxY = 0;
		int i = 0;
		for (ObjGeometry.Quad q : quads) {
			for (int c = 0; c < 4; c++) {
				d[i++] = q.pos()[c][0];
				d[i++] = q.pos()[c][1];
				d[i++] = q.pos()[c][2];
				d[i++] = q.uv()[c][0];
				d[i++] = q.uv()[c][1];
				d[i++] = q.nrm()[c][0];
				d[i++] = q.nrm()[c][1];
				d[i++] = q.nrm()[c][2];
				maxY = Math.max(maxY, q.pos()[c][1]);
			}
		}
		m = new Mesh(d, quads.size(), maxY);
		MESHES.put(object, m);
		ReiatsuTest.LOGGER.info("[fx] mesh {}: {} quads, height {}", object, quads.size(), maxY);
		return m;
	}

	public static boolean available() {
		return get(CRYSTAL_B).quads > 0;
	}

	/** Writes the mesh with transform {@code m} (object space to camera relative space); returns the vertex count. */
	public static int draw(VertexConsumer vc, Mesh mesh, Matrix4f m, int argb, int light) {
		if (mesh.quads == 0) {
			return 0;
		}
		Matrix3f nm = m.normal(new Matrix3f());
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		float[] d = mesh.data;
		for (int i = 0; i < d.length; i += 8) {
			m.transformPosition(d[i], d[i + 1], d[i + 2], p);
			nm.transform(d[i + 5], d[i + 6], d[i + 7], n);
			vc.vertex(p.x, p.y, p.z).color(argb).texture(d[i + 3], d[i + 4]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n.x, n.y, n.z);
		}
		return mesh.quads * 4;
	}

	/** ARGB from 0..1 floats. */
	public static int argb(double r, double g, double b, double a) {
		return ((int) (FxMath.clamp(a) * 255 + 0.5) << 24) | ((int) (FxMath.clamp(r) * 255 + 0.5) << 16) | ((int) (FxMath.clamp(g) * 255 + 0.5) << 8)
				| (int) (FxMath.clamp(b) * 255 + 0.5);
	}

	public static int argb(float[] rgb, double a) {
		return argb(rgb[0], rgb[1], rgb[2], a);
	}
}
