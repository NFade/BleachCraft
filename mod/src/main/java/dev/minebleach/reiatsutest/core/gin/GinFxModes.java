package dev.minebleach.reiatsutest.core.gin;

/**
 * Layout of the {@code effect_event} of the Gin abilities (ids 62 to 67). The event the state machine sends at the key press carries the
 * generic 5 floats {aim x, y, z, size, size2} (fewer than {@link #MIN_LEN}): the client draws the wind-up / the pose only. Every event the
 * Gin glue sends itself has {@code params[0]} = one of the modes below and at least {@link #MIN_LEN} floats; x y z = the caster's eye and
 * dx dy dz = the unit look direction.
 *
 * <pre>
 * STRIKE    {mode, endX, endY, endZ, hit (0/1), quiet (0/1), index}   a thrust line from the hand to the end (Ikorose, Renjin n, Korose press 1)
 * SWEEP     {mode, radius, arcDeg, sweepTicks, 0, 0, 0}               Hyapponzashi: the blade sweeps arcDeg around d, starting at -arc/2
 * HOOK      {mode, anchorX, anchorY, anchorZ, hit (0/1), pullTicks, 0}  Snake Hook: the line to the anchor, held for pullTicks
 * CHANNEL   {mode, ticks, 0, 0, 0, 0, 0} + targetId                    Korose press 2: hand raised, target dissolving after ticks
 * DISSOLVE  {mode, x, y, z, 0, 0, 0} + targetId                         the target turns to dust
 * INTERRUPT {mode, 0, 0, 0, 0, 0, 0}                                    the channel broke
 * SLIVER    {mode, ticks, 0, 0, 0, 0, 0} + targetId     (owner only)   the sliver glows on targetId for ticks (again = timer reset)
 * GONE      {mode, 0, 0, 0, 0, 0, 0} + targetId         (owner only)   the sliver on targetId is gone
 * </pre>
 */
public final class GinFxModes {
	public static final int MIN_LEN = 6;
	public static final int STRIKE = 1;
	public static final int SWEEP = 2;
	public static final int HOOK = 3;
	public static final int CHANNEL = 4;
	public static final int DISSOLVE = 5;
	public static final int INTERRUPT = 6;
	public static final int SLIVER = 7;
	public static final int GONE = 8;

	private GinFxModes() {
	}
}
