package dev.minebleach.reiatsutest.core.obj;

/**
 * Alpha mask of an emissive texture (RGBA PNG, A = glow intensity). Used at bake time to decide whether the
 * emissive pass needs a quad at all: a quad is kept only if its UV rectangle covers at least one texel with
 * alpha greater than 0 (ADR section 1).
 */
public final class EmissiveMask {
	private final int width;
	private final int height;
	private final byte[] alpha;

	public EmissiveMask(int width, int height, byte[] alpha) {
		if (alpha.length != width * height) {
			throw new IllegalArgumentException("alpha size " + alpha.length + " != " + width + "x" + height);
		}
		this.width = width;
		this.height = height;
		this.alpha = alpha;
	}

	/** @param uv four {u, v} pairs with V already flipped to image space (0 = top row). */
	public boolean anyEmissive(float[][] uv) {
		float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
		for (float[] c : uv) {
			minU = Math.min(minU, c[0]);
			maxU = Math.max(maxU, c[0]);
			minV = Math.min(minV, c[1]);
			maxV = Math.max(maxV, c[1]);
		}
		int x0 = clamp((int) Math.floor(minU * width + 1e-4f), width);
		int x1 = clamp((int) Math.ceil(maxU * width - 1e-4f) - 1, width);
		int y0 = clamp((int) Math.floor(minV * height + 1e-4f), height);
		int y1 = clamp((int) Math.ceil(maxV * height - 1e-4f) - 1, height);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				if ((alpha[y * width + x] & 0xFF) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static int clamp(int v, int size) {
		return Math.max(0, Math.min(size - 1, v));
	}
}
