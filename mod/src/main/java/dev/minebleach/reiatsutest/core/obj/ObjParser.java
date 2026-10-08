package dev.minebleach.reiatsutest.core.obj;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal OBJ parser for the subset in ADR section 1: v / vt / vn / f (3 or 4 corners, v/vt/vn, negative
 * indices), o / g names; usemtl, mtllib, s, l ignored; other keywords give one warning per keyword per file.
 */
public final class ObjParser {
	private ObjParser() {
	}

	public static ObjMesh parse(InputStream in, String source) throws IOException {
		return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), source);
	}

	public static ObjMesh parse(String text, String source) {
		FloatList pos = new FloatList();
		FloatList uv = new FloatList();
		FloatList nrm = new FloatList();
		List<ObjMesh.Face> faces = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<String> warned = new ArrayList<>();
		String name = null;

		String[] lines = text.split("\r?\n", -1);
		for (int i = 0; i < lines.length; i++) {
			int lineNo = i + 1;
			String line = lines[i];
			int hash = line.indexOf('#');
			if (hash >= 0) {
				line = line.substring(0, hash);
			}
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			String[] t = line.split("\\s+");
			switch (t[0]) {
				case "v" -> {
					need(t, 3, source, lineNo);
					pos.add(num(t[1], source, lineNo)).add(num(t[2], source, lineNo)).add(num(t[3], source, lineNo));
				}
				case "vt" -> {
					need(t, 2, source, lineNo);
					uv.add(num(t[1], source, lineNo)).add(num(t[2], source, lineNo));
				}
				case "vn" -> {
					need(t, 3, source, lineNo);
					nrm.add(num(t[1], source, lineNo)).add(num(t[2], source, lineNo)).add(num(t[3], source, lineNo));
				}
				case "f" -> faces.add(face(t, pos.size() / 3, uv.size() / 2, nrm.size() / 3, source, lineNo));
				case "o", "g" -> {
					if (name == null && t.length > 1) {
						name = t[1];
					}
				}
				case "usemtl", "mtllib", "s", "l" -> {
				}
				default -> {
					if (!warned.contains(t[0])) {
						warned.add(t[0]);
						warnings.add(source + ":" + lineNo + ": unsupported keyword " + t[0] + " ignored");
					}
				}
			}
		}
		return new ObjMesh(name == null ? "" : name, pos.toArray(), uv.toArray(), nrm.toArray(), faces, warnings);
	}

	private static void need(String[] t, int count, String src, int line) {
		if (t.length < count + 1) {
			throw new ObjParseException(src, line, t[0] + " needs at least " + count + " numbers");
		}
	}

	private static float num(String s, String src, int line) {
		try {
			return Float.parseFloat(s);
		} catch (NumberFormatException e) {
			throw new ObjParseException(src, line, "bad number " + s);
		}
	}

	private static ObjMesh.Face face(String[] t, int nv, int nvt, int nvn, String src, int line) {
		int corners = t.length - 1;
		if (corners < 3) {
			throw new ObjParseException(src, line, "face with fewer than 3 corners");
		}
		if (corners > 4) {
			throw new ObjParseException(src, line, "face with " + corners + " corners (n-gons not allowed, max 4)");
		}
		int[] v = new int[corners];
		int[] vt = new int[corners];
		int[] vn = new int[corners];
		for (int c = 0; c < corners; c++) {
			String[] p = t[c + 1].split("/", -1);
			if (p.length < 2 || p[1].isEmpty()) {
				throw new ObjParseException(src, line, "corner " + t[c + 1] + " has no vt (texture coordinate required)");
			}
			v[c] = index(p[0], nv, "v", src, line);
			vt[c] = index(p[1], nvt, "vt", src, line);
			vn[c] = (p.length >= 3 && !p[2].isEmpty()) ? index(p[2], nvn, "vn", src, line) : -1;
		}
		return new ObjMesh.Face(v, vt, vn);
	}

	private static int index(String s, int count, String what, String src, int line) {
		int idx;
		try {
			idx = Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new ObjParseException(src, line, "bad " + what + " index " + s);
		}
		int zero = idx > 0 ? idx - 1 : count + idx; // negative = relative to the current end
		if (idx == 0 || zero < 0 || zero >= count) {
			throw new ObjParseException(src, line, what + " index " + idx + " out of range (have " + count + ")");
		}
		return zero;
	}

	private static final class FloatList {
		private float[] a = new float[64];
		private int n;

		FloatList add(float f) {
			if (n == a.length) {
				a = Arrays.copyOf(a, n * 2);
			}
			a[n++] = f;
			return this;
		}

		int size() {
			return n;
		}

		float[] toArray() {
			return Arrays.copyOf(a, n);
		}
	}
}
