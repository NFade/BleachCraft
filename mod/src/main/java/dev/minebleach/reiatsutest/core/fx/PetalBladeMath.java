package dev.minebleach.reiatsutest.core.fx;

/**
 * Pure clock of the sword that the petals build in the hand of Byakuya (R12). When the sword is not there (the shikai has only the hilt, the bankai has nothing in the
 * hand), a strike or a block pulls petals together into the sword: the film of the release dissolve played backwards (fragments fly in and join the pink glowing
 * blade, then the blade turns into the normal one) and, when nothing hits for a while, the same film forwards (the blade glows pink again and breaks into petals).
 * Two modes:
 * <ul>
 * <li><b>B</b> (default): the first strike or block builds the sword, it holds {@link Params#hold} seconds counted from the hit moment, every further strike or block
 * restarts that time; with no strikes it falls apart.</li>
 * <li><b>A</b>: the sword lives only for the strike: it falls apart {@link Params#holdA} seconds after the sweep (a block keeps it up for as long as the block).</li>
 * </ul>
 * {@link Timeline#form} is the film position: 0 = nothing, 1 = the complete sword. The client plays the dissolve film at {@code el = total * (1 - form)}, so the same
 * number serves the build up (form rises) and the break up (form falls). {@link Timeline#pink} says how pink the sword is (1 while the film runs, easing to the
 * normal colour after it has formed, and back to pink shortly before it breaks up).
 */
public final class PetalBladeMath {
	private PetalBladeMath() {
	}

	/** Numbers (the client reads them from {@code fx_override.json}, keys {@code petalBlade.*}). */
	public static final class Params {
		/** true = mode B (hold N seconds after the first strike), false = mode A (only for the strike). */
		public boolean holdMode = true;
		/** Mode B: seconds the sword holds after the hit moment of the last strike or block. */
		public double hold = 3.0;
		/** Mode A: seconds the sword stays after the sweep of a strike. */
		public double holdA = 0.25;
		/** Seconds the film of the build up and of the break up takes (form 0 to 1). */
		public double assemble = 0.55;
		public double disperse = 0.90;
		/** Seconds the pink eases into the normal colour once the sword has formed, and the pink hold before it breaks up. */
		public double settle = 1.45;
		public double lead = 0.25;
		/** Seconds of the sweep of a strike after its upswing (only for the swing clock that clears the swarm). */
		public double sweep = 0.12;
	}

	/** Phase of the sword for the report and the tests. */
	public enum Phase { NONE, ASSEMBLE, HOLD, DISPERSE }

	/** State of one sword: when it has to exist, how formed it is now. */
	public static final class Timeline {
		/** The sword stands (target form 1) while {@code now < holdUntil + lead}; the pink hold of the break up is the lead. */
		public double holdUntil = -1e9;
		/** Current form 0..1. */
		public double form;
		/** True while the owner blocks. */
		public boolean blocking;
		/** FX time at which the form last reached 1. */
		public double formedAt = -1e9;
		private double lastT = Double.NaN;
		private boolean swung;
		private double swingStart = -1e9;
		private double swingUp;

		/** A strike starts now: the upswing takes {@code upswing} seconds, the hit moment is {@code upswing} later. */
		public void strike(double now, double upswing, Params prm) {
			swung = true;
			swingStart = now;
			swingUp = Math.max(0.02, upswing);
			double hit = now + swingUp;
			double end = hit + prm.sweep;
			holdUntil = Math.max(holdUntil, prm.holdMode ? hit + prm.hold : end + prm.holdA);
		}

		/** An ability of the shikai (the swarm leaves for the attack or the barrier): the sword breaks up at once, without the pink lead and whatever time was left. */
		public void dispel() {
			holdUntil = -1e9;
			blocking = false;
		}

		/** A block starts or goes on: the sword stands (at least) while the block lasts. */
		public void block(double now, boolean start, Params prm) {
			blocking = true;
			double until = prm.holdMode && start ? now + prm.hold : now + 0.35;
			holdUntil = Math.max(holdUntil, until);
		}

		public void unblock(double now, Params prm) {
			if (blocking) {
				blocking = false;
				// the block ended: mode A lets go soon, mode B keeps what it has
				holdUntil = Math.max(holdUntil, now + (prm.holdMode ? 0 : prm.holdA));
			}
		}

		private boolean want(double now, Params prm) {
			return blocking || now < holdUntil + prm.lead;
		}

		/** Moves the form towards its target; call once per frame with the FX time (a second call with the same time changes nothing). Returns the form. */
		public double update(double now, Params prm) {
			double dt = Double.isNaN(lastT) ? 0 : Math.max(0, Math.min(0.1, now - lastT));
			lastT = now;
			double before = form;
			if (want(now, prm)) {
				form = Math.min(1, form + dt / Math.max(1e-3, prm.assemble));
			} else {
				form = Math.max(0, form - dt / Math.max(1e-3, prm.disperse));
			}
			if (form >= 1 && before < 1) {
				formedAt = now;
			}
			return form;
		}

		public Phase phase(double now, Params prm) {
			if (want(now, prm)) {
				return form >= 1 ? Phase.HOLD : Phase.ASSEMBLE;
			}
			return form > 0 ? Phase.DISPERSE : Phase.NONE;
		}

		/** How pink the sword is 0..1 (see the class comment). */
		public double pink(double now, Params prm) {
			switch (phase(now, prm)) {
				case ASSEMBLE, DISPERSE:
					return 1.0;
				case NONE:
					return 0.0;
				default:
					double settled = 1.0 - smooth((now - formedAt) / Math.max(1e-3, prm.settle));
					double leading = blocking ? 0.0 : smooth((now - holdUntil) / Math.max(1e-3, prm.lead));
					return Math.max(settled, leading);
			}
		}

		/** 0..1 how strongly the owner is swinging (for clearing the swarm in front of the sword): 1 from the start of a swing to the end of the sweep, then fades. */
		public double swingStrength(double now, Params prm) {
			double s = 0;
			if (swung) {
				double end = swingStart + swingUp + prm.sweep + 0.12;
				if (now >= swingStart && now <= end) {
					s = 1;
				} else if (now > end) {
					s = Math.max(0, 1 - (now - end) / 0.30);
				}
			}
			if (blocking) {
				s = Math.max(s, 0.6);
			}
			return s;
		}
	}

	static double smooth(double x) {
		double s = Math.max(0, Math.min(1, x));
		return s * s * (3 - 2 * s);
	}
}
