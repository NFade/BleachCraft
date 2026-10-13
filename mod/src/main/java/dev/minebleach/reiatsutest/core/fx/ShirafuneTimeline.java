package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.state.AbilitySpec;

/**
 * Timeline of the Rukia Shirafune effect against the server (r8 fx2 item 3). The server now takes the aim at the END of the cast
 * (release phase, 12 ticks = 0.6 s after the key) and sends the effect event at that moment, together with the damage. So the client
 * clock of the event starts AT the hit: the blade grows at once, the contact burst lands when the blade has reached its end, and the old
 * lead-in (a swirl 0.3 s before the blade, contact 0.6 s after the event) is gone from the event. The time before the release is covered
 * by the local cast telegraph ({@link #gather}).
 *
 * <p>All times are seconds from the event. They are the defaults; the client reads FxTune overrides over them.
 */
public final class ShirafuneTimeline {
	/** Time the old (press time) client timeline put the blade, the contact and the retraction at: kept for the tests that prove the shift. */
	public static final double OLD_BLADE_AT = 0.3;
	public static final double OLD_CONTACT_AT = 0.6;
	public static final double OLD_RETRACT_AT = 2.0;
	/** Seconds the blade needs to grow to full length (easeOutExpo, FxTune {@code shira.growTime}). */
	public static final double GROW = 0.2;

	public final double bladeAt;
	public final double crystalAt;
	public final double contactAt;
	public final double chillAt;
	public final double retractAt;
	public final double lifetime;

	private ShirafuneTimeline(double bladeAt, double crystalAt, double contactAt, double chillAt, double retractAt, double lifetime) {
		this.bladeAt = bladeAt;
		this.crystalAt = crystalAt;
		this.contactAt = contactAt;
		this.chillAt = chillAt;
		this.retractAt = retractAt;
		this.lifetime = lifetime;
	}

	/**
	 * The timeline for an event that arrives at the release. {@code lead} (default 0) delays everything, {@code grow} is the blade growth
	 * time. The blade starts at {@code lead}; the tip crystal rides in at 0.16 s (the old 0.46 - 0.3); the contact burst lands at half the growth
	 * time (easeOutExpo has covered 97 percent of the length by then); the frost sound follows 0.3 s after the contact; the blade retracts
	 * 1.7 s after it started (the old 2.0 - 0.3), and the timeline ends 0.4 s after that.
	 */
	public static ShirafuneTimeline atRelease(double lead, double grow) {
		double l = Math.max(0.0, lead);
		double g = Math.max(0.02, grow);
		double contact = l + g * 0.5;
		return new ShirafuneTimeline(l, l + 0.16, contact, contact + 0.3, l + 1.7, l + 2.1);
	}

	public static ShirafuneTimeline atRelease() {
		return atRelease(0.0, GROW);
	}

	/** The old press-time timeline, for comparison in tests. */
	public static ShirafuneTimeline atPress() {
		return new ShirafuneTimeline(OLD_BLADE_AT, 0.46, OLD_CONTACT_AT, 1.0, OLD_RETRACT_AT, 2.4);
	}

	/** Cast time of the ability in seconds: the tick of its last (release) phase over 20, 0 when it has no phases. */
	public static double castSeconds(AbilitySpec spec) {
		if (spec.phaseOffsets().isEmpty()) {
			return 0.0;
		}
		return spec.phaseOffsets().get(spec.releasePhase()) / 20.0;
	}

	/**
	 * Strength 0..1 of the gathering glow {@code p} (0..1) of the way through the cast: a smooth rise that is already 0.15 at the key press
	 * and 1 at the release, with a flicker of at most 8 percent in the last third so the sword looks charged. Monotonic without the flicker.
	 */
	public static double gather(double p) {
		double x = Math.max(0.0, Math.min(1.0, p));
		double base = 0.15 + 0.85 * (x * x * (3.0 - 2.0 * x));
		double flick = x > 0.66 ? 0.08 * Math.sin(x * 90.0) * (x - 0.66) / 0.34 : 0.0;
		return Math.max(0.0, Math.min(1.0, base + flick));
	}

	/** Same curve without the flicker (the glow size and the converging motes use it). */
	public static double gatherSmooth(double p) {
		double x = Math.max(0.0, Math.min(1.0, p));
		return 0.15 + 0.85 * (x * x * (3.0 - 2.0 * x));
	}

	/** Number of converging motes in the tick at progress {@code p}: {@code peak} at the release, 25 percent of it at the start. */
	public static int moteCount(double p, int peak) {
		return (int) Math.round(Math.max(0, peak) * (0.25 + 0.75 * gatherSmooth(p)));
	}
}
