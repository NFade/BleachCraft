package dev.minebleach.reiatsutest.core.fx;

/**
 * The first person hand is drawn with a fixed field of view of 70 degrees (vanilla {@code GameRenderer#getFov} with {@code changingFov} false ignores the option), while the
 * world is drawn with the field of view of the options. A point of the hand, taken as a world position without correcting for that, lands on another place on the screen:
 * with a world fov of 96 degrees the root and the tip of a sword came out at about 0.62 of their distance from the screen centre (R12 round 4, measured on a screenshot).
 * To make a world effect sit on what the hand pass draws, the offset of the point from the camera is scaled across the view by {@link #scale} and left alone along the view.
 */
public final class HandFov {
	/** The fixed field of view of the hand pass, degrees. */
	public static final double HAND_FOV = 70.0;

	private HandFov() {
	}

	/** Same from {@code tan(fov / 2)} of the world pass, i.e. {@code 1 / m11} of its projection matrix (exact, with the sprint and speed effect on the fov). */
	public static double scaleFromTan(double tanHalfFov) {
		return Math.max(0.1, Math.min(10.0, tanHalfFov)) / Math.tan(Math.toRadians(HAND_FOV) / 2);
	}

	/** Factor for the sideways and vertical offsets (in camera axes) of a point of the hand pass to get the same screen position in a pass with {@code worldFovDeg}. */
	public static double scale(double worldFovDeg) {
		double w = Math.max(10.0, Math.min(170.0, worldFovDeg));
		return Math.tan(Math.toRadians(w) / 2) / Math.tan(Math.toRadians(HAND_FOV) / 2);
	}
}
