package dev.minebleach.reiatsutest.core.fx;

/**
 * Ground plane pinhole fit of the blade rows on a frame of the anime (R12 task 1; the pixel measuring is {@code tools/measure_bankai_rows.py}).
 * <p>
 * The camera looks horizontally, its eye is {@code eye} blocks above the floor, the vanishing point is (vpX, vpY). A floor point at depth Z and
 * lateral X lands at {@code y = vpY + f * eye / Z}, {@code x = vpX + f * X / Z}, so from a blade base pixel (x, y) the floor position follows:
 * {@code Z = f * eye / (y - vpY)}, {@code X = (x - vpX) * eye / (y - vpY)}. The scale comes from the character: Byakuya stands bodyBlocks tall
 * on bodyPx pixels with his feet at feetY, which gives the eye height. The focal length cannot be measured from one frame (only depth ratios
 * can), so the absolute pitch rests on the assumed horizontal field of view; {@link #pitchForFov} shows how it moves.
 */
public final class BladeRowFit {
	private BladeRowFit() {
	}

	/** Result in blocks: lateral distance of the bases from the axis, how far the nearest blade stands behind the feet, spacing along the row. */
	public record Result(double eye, double halfWidth, double startBehind, double pitch, double bladeHeight) {
	}

	public static double focalPx(double imageWidthPx, double hfovDeg) {
		return (imageWidthPx / 2.0) / Math.tan(Math.toRadians(hfovDeg) / 2.0);
	}

	/** Eye height above the floor from the character: his feet pixel row, the horizon row and his height in pixels. */
	public static double eyeHeight(double feetY, double vpY, double bodyPx, double bodyBlocks) {
		return (feetY - vpY) * bodyBlocks / bodyPx;
	}

	public static double depth(double baseY, double vpY, double eye, double focalPx) {
		return focalPx * eye / (baseY - vpY);
	}

	public static double lateral(double x, double vpX, double baseY, double vpY, double eye) {
		return (x - vpX) * eye / (baseY - vpY);
	}

	/** Least squares slope of depth over the blade index (the pitch along one row); {@code rows[i] = {x, baseY}}, nearest first. */
	static double slope(double[] z) {
		int n = z.length;
		double mi = (n - 1) / 2.0;
		double mz = 0;
		for (double v : z) {
			mz += v / n;
		}
		double num = 0;
		double den = 0;
		for (int i = 0; i < n; i++) {
			num += (i - mi) * (z[i] - mz);
			den += (i - mi) * (i - mi);
		}
		return num / den;
	}

	/**
	 * Fit of both rows. {@code left} and {@code right} are {@code {x, baseY}} pixel pairs of the blade bases, nearest blade first; {@code topY}
	 * of the nearest blade gives its height. The nearest blade of each side must be well below the horizon.
	 */
	public static Result fit(double[][] left, double[][] right, double nearTopYLeft, double vpX, double vpY, double feetY, double bodyPx, double bodyBlocks, double imageW,
			double hfovDeg) {
		double eye = eyeHeight(feetY, vpY, bodyPx, bodyBlocks);
		double f = focalPx(imageW, hfovDeg);
		double[][][] sides = {left, right};
		double lat = 0;
		double pitch = 0;
		double near = 0;
		for (double[][] row : sides) {
			double[] z = new double[row.length];
			double l = 0;
			for (int i = 0; i < row.length; i++) {
				z[i] = depth(row[i][1], vpY, eye, f);
				l += Math.abs(lateral(row[i][0], vpX, row[i][1], vpY, eye)) / row.length;
			}
			lat += l / 2.0;
			pitch += slope(z) / 2.0;
			near += z[0] / 2.0;
		}
		double bodyDepth = depth(feetY, vpY, eye, f);
		double px = (left[0][1] - vpY) / eye;
		return new Result(eye, lat, near - bodyDepth, pitch, (left[0][1] - nearTopYLeft) / px);
	}

	/** The pitch the same pixels give for another assumed horizontal field of view (it scales with the focal length). */
	public static double pitchForFov(double pitchAtRef, double refFovDeg, double fovDeg) {
		return pitchAtRef * Math.tan(Math.toRadians(refFovDeg) / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);
	}
}
