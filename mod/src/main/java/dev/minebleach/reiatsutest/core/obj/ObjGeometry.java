package dev.minebleach.reiatsutest.core.obj;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a parsed OBJ object to model-space quads: object-origin offset via the meta table, the grip offset
 * (p_mc = p_obj - grip_obj + 0.5), V flip. Triangles become degenerate quads (corner 3 = corner 2).
 */
public final class ObjGeometry {
	/** 4 corners; pos[c] = {x,y,z} in blocks, uv[c] = {u,v} (V already flipped), nrm[c] = {x,y,z}. */
	public record Quad(float[][] pos, float[][] uv, float[][] nrm, boolean triangle) {
	}

	private ObjGeometry() {
	}

	public static List<Quad> toModelSpace(ObjMesh mesh, ObjMeta meta, String objectName) {
		float[] grip = meta.gripObj();
		float[] origin = meta.originObj(objectName);
		List<Quad> out = new ArrayList<>(mesh.faceCount());
		for (ObjMesh.Face f : mesh.faces) {
			float[][] pos = new float[4][];
			float[][] uv = new float[4][];
			float[][] nrm = new float[4][];
			for (int c = 0; c < 4; c++) {
				int k = Math.min(c, f.corners() - 1); // triangle: corner 3 = corner 2
				int vi = f.v()[k];
				float[] p = {
					mesh.positions[vi * 3] + origin[0],
					mesh.positions[vi * 3 + 1] + origin[1],
					mesh.positions[vi * 3 + 2] + origin[2]
				};
				pos[c] = AxisMapper.objToModel(p, grip);
				int ti = f.vt()[k];
				uv[c] = new float[] {mesh.uvs[ti * 2], AxisMapper.flipV(mesh.uvs[ti * 2 + 1])};
			}
			float[] flat = null;
			for (int c = 0; c < 4; c++) {
				int k = Math.min(c, f.corners() - 1);
				int ni = f.vn()[k];
				if (ni >= 0) {
					nrm[c] = new float[] {mesh.normals[ni * 3], mesh.normals[ni * 3 + 1], mesh.normals[ni * 3 + 2]};
				} else {
					if (flat == null) {
						flat = faceNormal(pos);
					}
					nrm[c] = flat;
				}
			}
			out.add(new Quad(pos, uv, nrm, f.corners() == 3));
		}
		return out;
	}

	static float[] faceNormal(float[][] p) {
		float ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
		float bx = p[2][0] - p[1][0], by = p[2][1] - p[1][1], bz = p[2][2] - p[1][2];
		float x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
		float len = (float) Math.sqrt(x * x + y * y + z * z);
		return len == 0 ? new float[] {0, 1, 0} : new float[] {x / len, y / len, z / len};
	}
}
