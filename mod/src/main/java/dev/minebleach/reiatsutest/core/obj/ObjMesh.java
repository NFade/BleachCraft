package dev.minebleach.reiatsutest.core.obj;

import java.util.List;

/**
 * Parsed OBJ geometry (pure Java, no Minecraft types). Positions, uvs and normals are stored as flat
 * float arrays; faces index into them (0-based). See ADR section 1 "OBJ subset parsed".
 */
public final class ObjMesh {
	/** One face with 3 or 4 corners. {@code vn[i] == -1} means "no normal in the file". */
	public record Face(int[] v, int[] vt, int[] vn) {
		public int corners() {
			return v.length;
		}
	}

	public final String name;
	public final float[] positions;
	public final float[] uvs;
	public final float[] normals;
	public final List<Face> faces;
	public final List<String> warnings;

	public ObjMesh(String name, float[] positions, float[] uvs, float[] normals, List<Face> faces, List<String> warnings) {
		this.name = name;
		this.positions = positions;
		this.uvs = uvs;
		this.normals = normals;
		this.faces = faces;
		this.warnings = warnings;
	}

	public int vertexCount() {
		return positions.length / 3;
	}

	public int uvCount() {
		return uvs.length / 2;
	}

	public int normalCount() {
		return normals.length / 3;
	}

	public int faceCount() {
		return faces.size();
	}

	public int quadCount() {
		return (int) faces.stream().filter(f -> f.corners() == 4).count();
	}

	public int triCount() {
		return (int) faces.stream().filter(f -> f.corners() == 3).count();
	}
}
