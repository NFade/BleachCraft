package dev.minebleach.reiatsutest.client.fx;

import java.util.Arrays;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

/**
 * Terrain aware placement of the Rukia bankai standing effects (crystals, ground rings, the frost decal grid). Unlike
 * {@link FxGround#sample} (first solid top found scanning from feet + 3, which lands on tree canopies, trunks and roofs) every
 * query here is relative to a hint height and a step limit, and answers {@link Double#NaN} / {@code null} when the place is not
 * usable (a block taller than the step, no floor within reach, a crystal that cannot fit). Pure client reads of the world, no
 * state; all numbers tunable through {@code fx_override.json} (keys {@code place.*}).
 */
public final class FxPlace {
	/** Spot of a crystal after fitting (base point, possibly reduced scale). */
	public record Spot(double x, double y, double z, double scale) {
	}

	/** Statistics for the harness and the log. */
	public static int crystalsFitted;
	public static int crystalsShrunk;
	public static int crystalsRejected;

	private FxPlace() {
	}

	private static ClientWorld world() {
		return MinecraftClient.getInstance().world;
	}

	/**
	 * Walkable floor of the column of (x, z): the first block found scanning from {@code hintY + up} down to {@code hintY - down}
	 * that has a collision shape. NaN when that block reaches above {@code hintY + up} (a wall, a trunk, a leaf block at head height:
	 * the column is occupied) or when nothing solid is found (a pit deeper than {@code down}). Returns the top face height.
	 */
	public static double ground(ClientWorld w, double x, double z, double hintY, double up, double down) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int top = MathHelper.floor(hintY + up);
		int bottom = MathHelper.floor(hintY - down);
		double limit = hintY + up + 1e-3;
		for (int y = top; y >= bottom; y--) {
			pos.set(bx, y, bz);
			BlockState st = w.getBlockState(pos);
			if (st.isAir()) {
				continue;
			}
			VoxelShape sh = st.getCollisionShape(w, pos);
			if (sh.isEmpty()) {
				continue;
			}
			double t = y + sh.getMax(Direction.Axis.Y);
			return t > limit ? Double.NaN : t;
		}
		return Double.NaN;
	}

	/** True when the point is inside the collision shape of a block (partial blocks like slabs and carpets count only where they really are). */
	public static boolean blocked(ClientWorld w, BlockPos.Mutable pos, double x, double y, double z) {
		pos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
		BlockState st = w.getBlockState(pos);
		if (st.isAir()) {
			return false;
		}
		VoxelShape sh = st.getCollisionShape(w, pos);
		if (sh.isEmpty()) {
			return false;
		}
		double lx = x - pos.getX();
		double ly = y - pos.getY();
		double lz = z - pos.getZ();
		for (Box b : sh.getBoundingBoxes()) {
			if (b.contains(lx, ly, lz)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Height for a flat glow ring or sigil of radius {@code radius} around (x, z): the floor under the centre, lifted to the 80th
	 * percentile of the floors on three circles (0.35, 0.7, 1.0 of the radius) so that the ring is not buried by the higher half of
	 * a slope, but never more than {@code place.ringLiftMax} (0.5) above the centre so that it does not float. Blocked columns are
	 * ignored. Falls back to {@code hintY}.
	 */
	public static double ringY(double x, double z, double hintY, double radius) {
		ClientWorld w = world();
		if (w == null) {
			return hintY;
		}
		double c = ground(w, x, z, hintY, 1.0, 6.0);
		if (Double.isNaN(c)) {
			c = hintY;
		}
		double[] s = new double[36];
		int n = 0;
		double[] radii = {0.35, 0.7, 1.0};
		for (double rf : radii) {
			for (int k = 0; k < 12; k++) {
				double a = Math.PI * 2 * k / 12 + rf;
				double g = ground(w, x + Math.cos(a) * radius * rf, z + Math.sin(a) * radius * rf, c, 0.6, 1.5);
				if (!Double.isNaN(g)) {
					s[n++] = g;
				}
			}
		}
		if (n == 0) {
			return c;
		}
		Arrays.sort(s, 0, n);
		double p80 = s[(int) Math.floor(0.8 * (n - 1))];
		return Math.max(c, Math.min(p80, c + FxTune.d("place.ringLiftMax", 0.5)));
	}

	/**
	 * Fits a standing crystal at the column of (px, pz) near {@code hintY}. Rules (all tunable):
	 * <ul>
	 * <li>the base footprint (centre and four points at about half the base radius) must all have a floor within the step
	 * ({@code place.step} 1.0 up) and differ by at most {@code place.slope} (0.45); the base sits at the highest of them, so no part
	 * of the base is buried;</li>
	 * <li>the body, sampled along its axis (centre and four lateral points that taper with height), must be free of collision
	 * blocks; when it is not, the crystal is shrunk to what fits (the last free fraction minus 0.1) and rejected when that is
	 * under {@code place.minFit} (0.45) of the wanted size, so a crystal never pierces a canopy, a trunk or a roof.</li>
	 * </ul>
	 * Returns {@code null} when the crystal does not fit.
	 *
	 * @param meshHeight  height of the mesh at scale 1
	 * @param meshRadius  horizontal radius of the mesh at scale 1
	 * @param ax          axis of the crystal (lean), not normalised
	 */
	public static Spot fit(double px, double pz, double hintY, double ax, double ay, double az, double meshHeight, double meshRadius, double scale, double sink) {
		ClientWorld w = world();
		if (w == null) {
			return new Spot(px, hintY - sink, pz, scale);
		}
		double base = Math.max(0.1, Math.min(0.6, meshRadius * scale * 0.5 * FxTune.d("place.footprint", 1.0)));
		double step = FxTune.d("place.step", 1.0);
		double[][] fp = {{0, 0}, {base, 0}, {-base, 0}, {0, base}, {0, -base}};
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		for (double[] o : fp) {
			double g = ground(w, px + o[0], pz + o[1], hintY, step, 3.0);
			if (Double.isNaN(g)) {
				crystalsRejected++;
				return null;
			}
			lo = Math.min(lo, g);
			hi = Math.max(hi, g);
		}
		if (hi - lo > FxTune.d("place.slope", 0.45)) {
			crystalsRejected++;
			return null;
		}
		double by = hi - sink;
		double l = Math.sqrt(ax * ax + ay * ay + az * az);
		if (l < 1e-6) {
			ax = 0;
			ay = 1;
			az = 0;
			l = 1;
		}
		double ux = ax / l;
		double uy = ay / l;
		double uz = az / l;
		double h = meshHeight * scale;
		double rad = meshRadius * scale;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		double fit = 1.0;
		double[] ss = {0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1.0};
		outer:
		for (double s : ss) {
			double cx = px + ux * h * s;
			double cy = by + uy * h * s;
			double cz = pz + uz * h * s;
			double lat = rad * (1.0 - s * 0.75) * 0.7;
			// the first blocked sample cuts the crystal there (one step of the sample list and 0.1 more of margin)
			double[][] off = {{0, 0}, {lat, 0}, {-lat, 0}, {0, lat}, {0, -lat}};
			for (double[] o : off) {
				if (blocked(w, pos, cx + o[0], cy, cz + o[1])) {
					fit = s - 0.15 - 0.1;
					break outer;
				}
			}
		}
		if (fit < FxTune.d("place.minFit", 0.45)) {
			crystalsRejected++;
			return null;
		}
		if (fit < 1.0) {
			crystalsShrunk++;
		}
		crystalsFitted++;
		return new Spot(px, by, pz, scale * Math.min(1.0, fit));
	}

	/** {@link #fit} for a named mesh of {@link FxMeshes}. */
	public static Spot fitMesh(String mesh, double px, double pz, double hintY, double ax, double ay, double az, double scale, double sink) {
		FxMeshes.Mesh m = FxMeshes.get(mesh);
		if (m.quads == 0) {
			return new Spot(px, hintY - sink, pz, scale);
		}
		return fit(px, pz, hintY, ax, ay, az, m.height, m.radius, scale, sink);
	}
}
