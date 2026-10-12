package dev.minebleach.reiatsutest.core.combat;

/**
 * Source of the tunable numbers of the first person block, parry and strike motion. The pure motion code asks for a dotted key
 * with the shipped default; the client answers from {@code pose_override.json} (dev hot reload) and falls back to the default.
 * Keys are paths into the {@code fp_*} sections, for example {@code "guard.left.s"} or {@code "swing.h_r.hit.pos"}.
 */
public interface FpParams {
	/** The shipped numbers only (production, tests). */
	FpParams DEFAULTS = new FpParams() {
		@Override
		public float num(String key, float def) {
			return def;
		}

		@Override
		public float[] vec(String key, float[] def) {
			return def;
		}
	};

	float num(String key, float def);

	/** A three number vector (x, y, z); the returned array is never modified by the callers. */
	float[] vec(String key, float[] def);
}
