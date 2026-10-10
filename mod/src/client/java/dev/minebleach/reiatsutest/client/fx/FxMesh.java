package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/**
 * A mesh of the exported models in the local OBJ frame (Y up, object origin at the base), flattened to quads for the batched
 * renderers (petals, blades, hilt, ripples). Triangles become degenerate quads. UV V is flipped to the Minecraft convention.
 * Loaded once per file straight from {@code assets/reiatsu_test/models/obj/<model>/<object>.obj}.
 */
public final class FxMesh {
	private static final Map<String, FxMesh> CACHE = new HashMap<>();

	public final String name;
	public final int quads;
	/** 4 corners per quad, 3 floats per corner. */
	public final float[] pos;
	public final float[] uv;
	public final float[] nrm;
	public final float minY;
	public final float maxY;
	public final float centreU;
	public final float centreV;

	private FxMesh(String name, int quads, float[] pos, float[] uv, float[] nrm) {
		this.name = name;
		this.quads = quads;
		this.pos = pos;
		this.uv = uv;
		this.nrm = nrm;
		float lo = Float.MAX_VALUE;
		float hi = -Float.MAX_VALUE;
		double su = 0;
		double sv = 0;
		for (int c = 0; c < quads * 4; c++) {
			lo = Math.min(lo, pos[c * 3 + 1]);
			hi = Math.max(hi, pos[c * 3 + 1]);
			su += uv[c * 2];
			sv += uv[c * 2 + 1];
		}
		this.minY = lo;
		this.maxY = hi;
		this.centreU = (float) (su / (quads * 4));
		this.centreV = (float) (sv / (quads * 4));
	}

	/** The mesh of {@code object} in the folder {@code model}, or null when the file is missing (the caller falls back). */
	public static synchronized FxMesh load(String model, String object) {
		String key = model + "/" + object;
		if (CACHE.containsKey(key)) {
			return CACHE.get(key);
		}
		FxMesh m = null;
		try {
			Identifier id = ReiatsuTest.id("models/obj/" + model + "/" + object + ".obj");
			Resource r = MinecraftClient.getInstance().getResourceManager().getResource(id).orElse(null);
			if (r != null) {
				try (InputStream in = r.getInputStream()) {
					m = flatten(object, ObjParser.parse(in, id.toString()));
				}
			} else {
				ReiatsuTest.LOGGER.warn("[fx] mesh not found: {}", id);
			}
		} catch (Exception e) {
			ReiatsuTest.LOGGER.error("[fx] mesh {} failed to load", key, e);
		}
		CACHE.put(key, m);
		return m;
	}

	private static FxMesh flatten(String name, ObjMesh mesh) {
		int q = mesh.faceCount();
		float[] pos = new float[q * 12];
		float[] uv = new float[q * 8];
		float[] nrm = new float[q * 12];
		int qi = 0;
		for (ObjMesh.Face f : mesh.faces) {
			float[][] p = new float[4][3];
			for (int c = 0; c < 4; c++) {
				int k = Math.min(c, f.corners() - 1);
				int vi = f.v()[k];
				p[c][0] = mesh.positions[vi * 3];
				p[c][1] = mesh.positions[vi * 3 + 1];
				p[c][2] = mesh.positions[vi * 3 + 2];
				int ti = f.vt()[k];
				uv[qi * 8 + c * 2] = mesh.uvs[ti * 2];
				uv[qi * 8 + c * 2 + 1] = 1f - mesh.uvs[ti * 2 + 1];
			}
			float ax = p[1][0] - p[0][0];
			float ay = p[1][1] - p[0][1];
			float az = p[1][2] - p[0][2];
			float bx = p[2][0] - p[1][0];
			float by = p[2][1] - p[1][1];
			float bz = p[2][2] - p[1][2];
			float fx = ay * bz - az * by;
			float fy = az * bx - ax * bz;
			float fz = ax * by - ay * bx;
			float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
			if (fl > 0) {
				fx /= fl;
				fy /= fl;
				fz /= fl;
			} else {
				fx = 0;
				fy = 1;
				fz = 0;
			}
			for (int c = 0; c < 4; c++) {
				int k = Math.min(c, f.corners() - 1);
				int ni = f.vn()[k];
				pos[qi * 12 + c * 3] = p[c][0];
				pos[qi * 12 + c * 3 + 1] = p[c][1];
				pos[qi * 12 + c * 3 + 2] = p[c][2];
				if (ni >= 0) {
					nrm[qi * 12 + c * 3] = mesh.normals[ni * 3];
					nrm[qi * 12 + c * 3 + 1] = mesh.normals[ni * 3 + 1];
					nrm[qi * 12 + c * 3 + 2] = mesh.normals[ni * 3 + 2];
				} else {
					nrm[qi * 12 + c * 3] = fx;
					nrm[qi * 12 + c * 3 + 1] = fy;
					nrm[qi * 12 + c * 3 + 2] = fz;
				}
			}
			qi++;
		}
		return new FxMesh(name, q, pos, uv, nrm);
	}

	/**
	 * Writes one instance: world position = origin + M * local, where M is the 3x3 {@code m} (row major, 9 floats, scale included) and origin
	 * is camera relative. {@code color} is ARGB; {@code light} the packed lightmap value. Normals use M without scale (unit length is
	 * restored here).
	 */
	public void write(BufferBuilder b, float[] m, double ox, double oy, double oz, int color, int light) {
		int n = quads * 4;
		for (int c = 0; c < n; c++) {
			float x = pos[c * 3];
			float y = pos[c * 3 + 1];
			float z = pos[c * 3 + 2];
			float wx = (float) (ox + m[0] * x + m[1] * y + m[2] * z);
			float wy = (float) (oy + m[3] * x + m[4] * y + m[5] * z);
			float wz = (float) (oz + m[6] * x + m[7] * y + m[8] * z);
			float nx0 = nrm[c * 3];
			float ny0 = nrm[c * 3 + 1];
			float nz0 = nrm[c * 3 + 2];
			float nx = m[0] * nx0 + m[1] * ny0 + m[2] * nz0;
			float ny = m[3] * nx0 + m[4] * ny0 + m[5] * nz0;
			float nz = m[6] * nx0 + m[7] * ny0 + m[8] * nz0;
			float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (nl > 1e-6f) {
				nx /= nl;
				ny /= nl;
				nz /= nl;
			}
			b.vertex(wx, wy, wz, color, uv[c * 2], uv[c * 2 + 1], OverlayTexture.DEFAULT_UV, light, nx, ny, nz);
		}
	}

	/**
	 * Blade variant: vertices at local y >= {@code tipY} are lit full bright and tinted {@code tipColor} (the tip glow without a pass,
	 * 1.7.3), the rest use {@code light} and {@code bodyColor}. {@code emissive} (0..1) pushes the whole blade toward full bright
	 * (the moonlight sweep).
	 */
	public void writeBlade(BufferBuilder b, float[] m, double ox, double oy, double oz, int light, float tipY, int bodyColor, int tipColor, float emissive) {
		int n = quads * 4;
		int blockL = light & 0xFFFF;
		int skyL = (light >> 16) & 0xFFFF;
		int boosted = ((int) (skyL + (240 - skyL) * emissive) << 16) | (int) (blockL + (240 - blockL) * emissive);
		for (int c = 0; c < n; c++) {
			float x = pos[c * 3];
			float y = pos[c * 3 + 1];
			float z = pos[c * 3 + 2];
			float wx = (float) (ox + m[0] * x + m[1] * y + m[2] * z);
			float wy = (float) (oy + m[3] * x + m[4] * y + m[5] * z);
			float wz = (float) (oz + m[6] * x + m[7] * y + m[8] * z);
			float nx0 = nrm[c * 3];
			float ny0 = nrm[c * 3 + 1];
			float nz0 = nrm[c * 3 + 2];
			float nx = m[0] * nx0 + m[1] * ny0 + m[2] * nz0;
			float ny = m[3] * nx0 + m[4] * ny0 + m[5] * nz0;
			float nz = m[6] * nx0 + m[7] * ny0 + m[8] * nz0;
			float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (nl > 1e-6f) {
				nx /= nl;
				ny /= nl;
				nz /= nl;
			}
			boolean tip = y >= tipY;
			b.vertex(wx, wy, wz, tip ? tipColor : bodyColor, uv[c * 2], uv[c * 2 + 1], OverlayTexture.DEFAULT_UV, tip ? 0xF000F0 : boosted, nx, ny, nz);
		}
	}
}
