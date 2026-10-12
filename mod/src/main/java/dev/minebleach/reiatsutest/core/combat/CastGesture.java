package dev.minebleach.reiatsutest.core.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One hand and sword gesture of an ability (first person, short third person): a timeline of key poses per channel, easing, holds
 * (the whole pose stops for a while, Absolute zero and Hakuteiken), circles and tremor on top (conducting), an envelope that blends
 * the gesture in and out of the stance. Pure maths; the client ({@code FpCast}) keeps the clocks and draws.
 *
 * <p>Every value is a DELTA from the held stance, so a change of the stance ({@code fp_two.pos}) moves all gestures with it. Hand frame
 * of the item renderer, blocks and degrees, right hander layout (x to the right, y up, forward is -z); a left hander is mirrored by the
 * caller. Channels:
 * <ul>
 * <li>{@code R} (6): the main hand together with the sword as one rigid body: {@code px py pz rx ry rz}. Rotation about the pivot
 * between the fists: {@code rx} below 0 tips the blade forward and down (about -90: level), above 0 back towards the player,
 * {@code rz} above 0 turns the tip to the left, {@code ry} twists the blade about its own axis. With an empty hand (Byakuya bankai, {@link FreeHands})
 * it is the shift of the free right arm from its idle pose, and the last three numbers are a ROTATION VECTOR in degrees (axis times angle,
 * hand frame axes, applied after the idle turn) that turns the arm about the fist, not Euler angles ({@code L}: the same for the left arm).</li>
 * <li>{@code L} (7): the free off hand: {@code px py pz arx ary arz free}. {@code free} 0 = the fist rides on the hilt (follows the
 * sword) and the shift is added to that place, 1 = it stays where the stance holds it, independent of the sword, then the shift is
 * added. {@code arx ary arz} turn the arm about the fist on top of the standing arm turn (degrees).</li>
 * <li>{@code C} (4): camera {@code pitch yaw roll fov}: pitch above 0 looks up, degrees; fov in degrees.</li>
 * <li>{@code TR} and {@code TL} (3): third person arm deltas {@code pitch yaw roll} in degrees of the main and of the other arm;
 * pitch below 0 raises the arm forward (ModelPart xRot), roll above 0 swings the arm outwards.</li>
 * </ul>
 * Rows are {@code [t, values..., ease]} (ease optional, see the {@code E_} constants) and describe the key that is reached at
 * {@code t}; before the first key the channel eases from zero at t = 0, after the last key it holds that value (end every gesture on a
 * zero row or let the envelope blend it out over {@link #out()}).
 */
public final class CastGesture {
	public static final int R_DIM = 6;
	public static final int L_DIM = 7;
	public static final int C_DIM = 4;
	public static final int T_DIM = 3;

	public static final int E_SMOOTH = 0;
	/** Fast start, slow end. */
	public static final int E_OUT = 1;
	/** Slow start, fast end. */
	public static final int E_IN = 2;
	public static final int E_LINEAR = 3;
	/** Smooth with a small overshoot at the end (spring). */
	public static final int E_BACK = 4;

	/** A key: reached at {@code t} seconds of gesture time, eased from the previous key with {@code ease}. */
	public record Key(float t, float[] v, int ease) {
	}

	/** A circle on top of the channels. {@code target}: 'P' main hand position (x, y), 'T' tip circle (rx, rz in degrees), 'L' off hand position (x, y). */
	public record Orbit(char target, float t0, float t1, float radius, float hz, float phase) {
	}

	/** Tremor: {@code target} 'R' (y of the main hand) or 'L' (y of the off hand). */
	public record Vib(char target, float t0, float t1, float amp, float hz) {
	}

	/** The gesture time stops at {@code at} for {@code len} seconds of real time. */
	public record Hold(float at, float len) {
	}

	/** Sampled channels of one frame. {@code env} blends the gesture in and out (0..1), {@code hand} fades a free hand in and out. */
	public record Sample(float[] r, float[] l, float[] c, float[] tr, float[] tl, float env, float hand) {
		public static final Sample NONE = new Sample(new float[R_DIM], new float[L_DIM], new float[C_DIM], new float[T_DIM], new float[T_DIM], 0f, 0f);

		/** Everything scaled by {@code k} (the camera channel and {@code env} are left as they are). */
		public Sample scaled(float k) {
			return new Sample(mul(r, k), mul(l, k), c, mul(tr, k), mul(tl, k), env, hand);
		}

		/** {@code a} weighted {@code 1 - k} plus {@code b} weighted {@code k}. */
		public static Sample mix(Sample a, Sample b, float k) {
			return new Sample(lerp(a.r, b.r, k), lerp(a.l, b.l, k), lerp(a.c, b.c, k), lerp(a.tr, b.tr, k), lerp(a.tl, b.tl, k),
					a.env + (b.env - a.env) * k, a.hand + (b.hand - a.hand) * k);
		}

		public static Sample fade(Sample s, float k) {
			return new Sample(mul(s.r, k), mul(s.l, k), mul(s.c, k), mul(s.tr, k), mul(s.tl, k), s.env * k, s.hand * k);
		}
	}

	private final String id;
	private final float dur;
	private final float in;
	private final float out;
	private final float sustainAt;
	private final boolean freeHands;
	private final List<Key> r;
	private final List<Key> l;
	private final List<Key> c;
	private final List<Key> tr;
	private final List<Key> tl;
	private final List<Orbit> orbits;
	private final List<Vib> vibs;
	private final List<Hold> holds;

	private CastGesture(String id, float dur, float in, float out, float sustainAt, boolean freeHands, List<Key> r, List<Key> l, List<Key> c,
			List<Key> tr, List<Key> tl, List<Orbit> orbits, List<Vib> vibs, List<Hold> holds) {
		this.id = id;
		this.dur = dur;
		this.in = in;
		this.out = out;
		this.sustainAt = sustainAt;
		this.freeHands = freeHands;
		this.r = r;
		this.l = l;
		this.c = c;
		this.tr = tr;
		this.tl = tl;
		this.orbits = orbits;
		this.vibs = vibs;
		this.holds = holds;
	}

	public String id() {
		return id;
	}

	/** Gesture time of the last key; the envelope blends out after it. */
	public float dur() {
		return dur;
	}

	public float in() {
		return in;
	}

	public float out() {
		return out;
	}

	/** Gesture time at which a sustained gesture (the steering) waits for its release, or -1. */
	public float sustainAt() {
		return sustainAt;
	}

	/** True when the gesture is designed for empty hands (Byakuya bankai); informational: the client draws free arms whenever the held item shows no sword. */
	public boolean freeHands() {
		return freeHands;
	}

	public List<Key> keys(char channel) {
		return switch (channel) {
			case 'R' -> r;
			case 'L' -> l;
			case 'C' -> c;
			case 'r' -> tr;
			case 'l' -> tl;
			default -> List.of();
		};
	}

	public List<Orbit> orbits() {
		return orbits;
	}

	public List<Vib> vibs() {
		return vibs;
	}

	public List<Hold> holds() {
		return holds;
	}

	/** Real seconds from the start until the envelope is gone. */
	public float totalSeconds() {
		float h = 0f;
		for (Hold x : holds) {
			h += x.len;
		}
		return dur + out + h;
	}

	/** Gesture time at {@code real} seconds from the start (the holds stop it). */
	public float gestureTime(float real) {
		float acc = 0f;
		for (Hold h : holds) {
			float at = h.at + acc;
			if (real < at) {
				break;
			}
			if (real < at + h.len) {
				return h.at;
			}
			acc += h.len;
		}
		return real - acc;
	}

	/** Sample at real time {@code real}. */
	public Sample sampleReal(float real) {
		float g = gestureTime(real);
		return sample(g, g);
	}

	/**
	 * Sample at gesture time {@code g}. {@code orbitTime} is the clock of the circles and the tremor (the gesture time itself, or a
	 * free running clock for the sustained steering gesture whose own time waits at {@code sustainAt}).
	 */
	public Sample sample(float g, float orbitTime) {
		float env = 1f;
		if (in > 1e-4f) {
			env = FpEase.smooth(g / in);
		}
		if (g > dur) {
			env *= out > 1e-4f ? 1f - FpEase.smooth((g - dur) / out) : 0f;
		}
		float[] rv = channel(r, R_DIM, g);
		float[] lv = channel(l, L_DIM, g);
		float[] cv = channel(c, C_DIM, g);
		float[] trv = channel(tr, T_DIM, g);
		float[] tlv = channel(tl, T_DIM, g);
		for (Orbit o : orbits) {
			float ramp = FpEase.smooth((g - o.t0) / 0.15f) * FpEase.smooth((o.t1 - g) / 0.15f);
			if (ramp <= 0f) {
				continue;
			}
			float ph = (float) (2.0 * Math.PI * (o.hz * (orbitTime - o.t0) + o.phase));
			float cx = (float) Math.cos(ph) * o.radius * ramp;
			float cy = (float) Math.sin(ph) * o.radius * ramp;
			switch (o.target) {
				case 'P' -> {
					rv[0] += cx;
					rv[1] += cy;
				}
				case 'T' -> {
					rv[3] -= cx;
					rv[5] += cy;
				}
				case 'L' -> {
					lv[0] += cx;
					lv[1] += cy;
				}
				default -> {
				}
			}
		}
		for (Vib v : vibs) {
			float ramp = FpEase.smooth((g - v.t0) / 0.05f) * FpEase.smooth((v.t1 - g) / 0.05f);
			if (ramp <= 0f) {
				continue;
			}
			float s = (float) Math.sin(2.0 * Math.PI * v.hz * (orbitTime - v.t0)) * v.amp * ramp;
			if (v.target == 'R') {
				rv[1] += s;
			} else if (v.target == 'L') {
				lv[1] += s;
			}
		}
		if (env < 1f) {
			scale(rv, env);
			scale(lv, env);
			scale(cv, env);
			scale(trv, env);
			scale(tlv, env);
		}
		// the empty hands (Byakuya bankai) stay on screen in their idle pose since round 6 ({@code fp_cast free.idle}); this envelope only brings them in and out when that is switched off
		float hand = FpEase.smooth(g / 0.25f) * (g > dur ? 1f - FpEase.smooth((g - dur) / Math.max(1e-3f, out)) : 1f);
		return new Sample(rv, lv, cv, trv, tlv, env, hand);
	}

	private static float[] channel(List<Key> keys, int dim, float g) {
		float[] res = new float[dim];
		if (keys.isEmpty()) {
			return res;
		}
		float pt = 0f;
		float[] pv = new float[dim];
		for (Key k : keys) {
			if (g < k.t) {
				float u = k.t - pt > 1e-6f ? (g - pt) / (k.t - pt) : 1f;
				float e = ease(k.ease, u);
				for (int i = 0; i < dim; i++) {
					res[i] = pv[i] + (k.v[i] - pv[i]) * e;
				}
				return res;
			}
			pt = k.t;
			pv = k.v;
		}
		System.arraycopy(pv, 0, res, 0, dim);
		return res;
	}

	/** Easing by code (see the {@code E_} constants), input clamped to 0..1. */
	public static float ease(int code, float u) {
		u = FpEase.clamp01(u);
		return switch (code) {
			case E_OUT -> FpEase.out(u);
			case E_IN -> FpEase.in(u);
			case E_LINEAR -> u;
			case E_BACK -> {
				float x = u - 1f;
				yield 1f + 2.70158f * x * x * x + 1.70158f * x * x;
			}
			default -> FpEase.smooth(u);
		};
	}

	/**
	 * Copy with everything at or after {@code t0} moved by {@code dt} seconds (the pink hold of the Byakuya release is a tuning number
	 * of the effect, the gesture follows it). A negative shift never moves a row in front of {@code t0}.
	 */
	public CastGesture shiftedAfter(float t0, float dt) {
		if (Math.abs(dt) < 1e-6f) {
			return this;
		}
		Builder b = new Builder(id).dur(Math.max(t0, dur + dt)).in(in).out(out).sustain(sustainAt >= t0 ? sustainAt + dt : sustainAt);
		b.freeHands = freeHands;
		for (char ch : new char[] {'R', 'L', 'C', 'r', 'l'}) {
			for (Key k : keys(ch)) {
				b.add(ch, new Key(k.t >= t0 ? Math.max(t0, k.t + dt) : k.t, k.v, k.ease));
			}
		}
		for (Orbit o : orbits) {
			b.orbits.add(new Orbit(o.target, o.t0 >= t0 ? Math.max(t0, o.t0 + dt) : o.t0, o.t1 >= t0 ? Math.max(t0, o.t1 + dt) : o.t1, o.radius, o.hz,
					o.phase));
		}
		for (Vib v : vibs) {
			b.vibs.add(new Vib(v.target, v.t0 >= t0 ? Math.max(t0, v.t0 + dt) : v.t0, v.t1 >= t0 ? Math.max(t0, v.t1 + dt) : v.t1, v.amp, v.hz));
		}
		for (Hold h : holds) {
			b.holds.add(new Hold(h.at >= t0 ? Math.max(t0, h.at + dt) : h.at, h.len));
		}
		return b.build();
	}

	/** Builder with the same numbers as this gesture, for the override file (a channel that is given replaces the default one). */
	public Builder toBuilder() {
		Builder b = new Builder(id).dur(dur).in(in).out(out).sustain(sustainAt);
		b.freeHands = freeHands;
		for (char ch : new char[] {'R', 'L', 'C', 'r', 'l'}) {
			for (Key k : keys(ch)) {
				b.add(ch, k);
			}
		}
		b.orbits.addAll(orbits);
		b.vibs.addAll(vibs);
		b.holds.addAll(holds);
		return b;
	}

	public static Builder builder(String id) {
		return new Builder(id);
	}

	/** Mutable builder; rows are given as doubles so the tables stay short. */
	public static final class Builder {
		private final String id;
		private float dur = 1f;
		private float in = 0.10f;
		private float out = 0.35f;
		private float sustain = -1f;
		private boolean freeHands;
		private final List<Key> r = new ArrayList<>();
		private final List<Key> l = new ArrayList<>();
		private final List<Key> c = new ArrayList<>();
		private final List<Key> tr = new ArrayList<>();
		private final List<Key> tl = new ArrayList<>();
		private final List<Orbit> orbits = new ArrayList<>();
		private final List<Vib> vibs = new ArrayList<>();
		private final List<Hold> holds = new ArrayList<>();

		private Builder(String id) {
			this.id = id;
		}

		public Builder dur(double v) {
			dur = (float) v;
			return this;
		}

		public Builder in(double v) {
			in = (float) v;
			return this;
		}

		public Builder out(double v) {
			out = (float) v;
			return this;
		}

		public Builder sustain(double v) {
			sustain = (float) v;
			return this;
		}

		public Builder freeHands(boolean v) {
			freeHands = v;
			return this;
		}

		/** Clears one channel (to replace it from an override file). */
		public Builder clear(char channel) {
			switch (channel) {
				case 'R' -> r.clear();
				case 'L' -> l.clear();
				case 'C' -> c.clear();
				case 'r' -> tr.clear();
				case 'l' -> tl.clear();
				case 'O' -> orbits.clear();
				case 'V' -> vibs.clear();
				case 'H' -> holds.clear();
				default -> {
				}
			}
			return this;
		}

		/** Main hand and sword row: t, ease, then px py pz rx ry rz. */
		public Builder r(double t, int ease, double... v) {
			return add('R', t, ease, v);
		}

		/** Off hand row: t, ease, then px py pz arx ary arz free. */
		public Builder l(double t, int ease, double... v) {
			return add('L', t, ease, v);
		}

		/** Camera row: t, ease, then pitch yaw roll fov. */
		public Builder c(double t, int ease, double... v) {
			return add('C', t, ease, v);
		}

		/** Third person main arm row: t, ease, then pitch yaw roll. */
		public Builder tr(double t, int ease, double... v) {
			return add('r', t, ease, v);
		}

		/** Third person other arm row: t, ease, then pitch yaw roll. */
		public Builder tl(double t, int ease, double... v) {
			return add('l', t, ease, v);
		}

		private Builder add(char ch, double t, int ease, double[] v) {
			float[] f = new float[v.length];
			for (int i = 0; i < v.length; i++) {
				f[i] = (float) v[i];
			}
			return add(ch, new Key((float) t, f, ease));
		}

		/** Adds a ready key; the number of values must match the channel. */
		public Builder add(char ch, Key k) {
			int dim = switch (ch) {
				case 'R' -> R_DIM;
				case 'L' -> L_DIM;
				case 'C' -> C_DIM;
				default -> T_DIM;
			};
			if (k.v.length != dim) {
				throw new IllegalArgumentException("gesture " + id + " channel " + ch + " needs " + dim + " values, got " + k.v.length);
			}
			(switch (ch) {
				case 'R' -> r;
				case 'L' -> l;
				case 'C' -> c;
				case 'r' -> tr;
				default -> tl;
			}).add(k);
			return this;
		}

		public Builder orbit(char target, double t0, double t1, double radius, double hz, double phase) {
			orbits.add(new Orbit(target, (float) t0, (float) t1, (float) radius, (float) hz, (float) phase));
			return this;
		}

		public Builder vib(char target, double t0, double t1, double amp, double hz) {
			vibs.add(new Vib(target, (float) t0, (float) t1, (float) amp, (float) hz));
			return this;
		}

		public Builder hold(double at, double len) {
			holds.add(new Hold((float) at, (float) len));
			return this;
		}

		public CastGesture build() {
			Comparator<Key> byT = Comparator.comparingDouble(Key::t);
			List<List<Key>> all = List.of(r, l, c, tr, tl);
			for (List<Key> k : all) {
				k.sort(byT);
			}
			holds.sort(Comparator.comparingDouble(Hold::at));
			return new CastGesture(id, dur, in, out, sustain, freeHands, List.copyOf(r), List.copyOf(l), List.copyOf(c), List.copyOf(tr),
					List.copyOf(tl), List.copyOf(orbits), List.copyOf(vibs), Collections.unmodifiableList(new ArrayList<>(holds)));
		}
	}

	// ------------------------------------------------------------------ small vector helpers

	private static void scale(float[] a, float k) {
		for (int i = 0; i < a.length; i++) {
			a[i] *= k;
		}
	}

	private static float[] mul(float[] a, float k) {
		float[] o = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			o[i] = a[i] * k;
		}
		return o;
	}

	private static float[] lerp(float[] a, float[] b, float k) {
		float[] o = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			o[i] = a[i] + (b[i] - a[i]) * k;
		}
		return o;
	}
}
