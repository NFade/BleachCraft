package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.obj.ObjMeta;

/**
 * Size of the Byakuya bankai blade MESH (R11 T13), in mesh metres. The numbers come from the {@code "blade"} block of
 * {@code byakuya_bankai_meta.json}: the client reads the meta once (see {@code BladeSpecLoader}) and calls {@link #apply}; until then, and for
 * a meta without the block, the defaults below are the shipped model (straight slab 5.5 x 0.4026 x 0.1477, apex 0.201 m off the axis on the edge side).
 * This is a holder so that {@code core/} stays free of Minecraft: the pure maths ({@link BladeRowLayout}, {@link RowsPhase}, {@link StormMath},
 * {@link CondenseMath}) read the length here instead of a constant that was tuned to an old mesh.
 * <p>
 * Mesh space is the OBJ frame: local y = length, local x = thickness, local z = width (Blender (x, y, z) maps to OBJ (x, z, -y)). The world size of a blade is
 * the mesh size times {@code bankai.bladeScale} (and times {@code bankai.bladeLength / length}, a one-number resize that scales all three axes alike).
 */
public final class BladeSpec {
	public static final double DEFAULT_LENGTH = 5.5;
	public static final double DEFAULT_WIDTH = 0.4026;
	public static final double DEFAULT_THICKNESS = 0.1477;
	/** OBJ local z of the apex of the shipped mesh (-0.200997: Blender tip y 0.200997, mapped to -y). */
	public static final double DEFAULT_TIP_Z = -0.200997;
	/** Height of the glowing top band of the blade, mesh metres (the old 8 m mesh had 1.15 of 8; this is the same share of 5.5). */
	public static final double DEFAULT_GLOW_BAND = 0.8;

	private static volatile double length = DEFAULT_LENGTH;
	private static volatile double width = DEFAULT_WIDTH;
	private static volatile double thickness = DEFAULT_THICKNESS;
	private static volatile double tipZ = DEFAULT_TIP_Z;

	private BladeSpec() {
	}

	/** Length of the mesh along local y, metres (the apex height). */
	public static double length() {
		return length;
	}

	/** Width of the mesh across its broad face (local z extent), metres. */
	public static double width() {
		return width;
	}

	/** Thickness of the mesh (local x extent), metres. */
	public static double thickness() {
		return thickness;
	}

	/** OBJ local z of the apex (negative: the apex leans toward the edge side). */
	public static double tipZ() {
		return tipZ;
	}

	/** Local y from which the vertices glow (the top {@link #DEFAULT_GLOW_BAND} of the blade, or {@code band} metres). */
	public static double glowY(double band) {
		return length - Math.max(0.05, Math.min(band, length));
	}

	/**
	 * Takes the blade numbers of a parsed meta; a null block (or a non finite / non positive number) leaves the current values. The apex is converted from Blender
	 * z-up coordinates to the OBJ frame: (x, y, z) to (x, z, -y), so only its OBJ z ({@code -y}) is kept (its height is the length).
	 */
	public static void apply(ObjMeta.Blade b) {
		if (b == null) {
			return;
		}
		if (ok(b.length())) {
			length = b.length();
		}
		if (ok(b.width())) {
			width = b.width();
		}
		if (ok(b.thickness())) {
			thickness = b.thickness();
		}
		if (b.tip() != null && b.tip().length >= 3 && Double.isFinite(b.tip()[1])) {
			tipZ = -b.tip()[1];
		}
	}

	/** Back to the shipped defaults (tests, and a client that reloads its resources). */
	public static void reset() {
		length = DEFAULT_LENGTH;
		width = DEFAULT_WIDTH;
		thickness = DEFAULT_THICKNESS;
		tipZ = DEFAULT_TIP_Z;
	}

	private static boolean ok(double v) {
		return Double.isFinite(v) && v > 0.01;
	}
}
