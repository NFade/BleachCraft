package dev.minebleach.reiatsutest.client.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * One playing effect: a list of actions at times (seconds from packet receipt, FX clock), seeded randomness, HITSTOP
 * (freezes this timeline and its glow sprites and particles, not the world) and DUCK (quiets the other FX sounds of the
 * caster). All screen effects of the effect go through the helpers here so the remote-caster factor, the concurrency cap
 * and the quality tier apply uniformly.
 */
public final class EffectTimeline {
	private record Entry(double t, int order, Consumer<EffectTimeline> action) {
	}

	/** Index of the clock slot (HITSTOP bookkeeping in {@link FxTimelines}). */
	public final int slot;
	public final int effectId;
	public final long seed;
	/** Event position (caster feet at t = 0) and look vector. */
	public final Vec3d pos;
	public final Vec3d dir;
	public final float[] params;
	public final int casterId;
	/** True when the caster is the local player (no distance attenuation, HUD and title card). */
	public final boolean localCaster;
	/** Screen effect factor: 1 for the local caster, s(d) for others (1.4 distance rule). */
	public final double strength;
	/** Particle and glow count factor from the distance (1.6). */
	public final double lod;

	private final List<Entry> entries = new ArrayList<>();
	private int nextEntry;
	private int orderCounter;
	/** Local time in seconds. */
	double time;
	double holdRemaining;
	/** True while a {@link #pause} is running: the held time still advances the clock. */
	boolean clockContinues;
	double endAt = 1.0;
	boolean ended;
	/** Screen effects allowed (the oldest timelines lose them first when more than maxConcurrentFx run). */
	boolean screen = true;
	private double duckFrom = -1;
	private double duckTo = -1;
	private double duckFactor = 0.5;
	private boolean sorted;

	EffectTimeline(int slot, int effectId, long seed, Vec3d pos, Vec3d dir, float[] params, int casterId, boolean localCaster,
			double strength, double lod) {
		this.slot = slot;
		this.effectId = effectId;
		this.seed = seed;
		this.pos = pos;
		this.dir = dir;
		this.params = params;
		this.casterId = casterId;
		this.localCaster = localCaster;
		this.strength = strength;
		this.lod = lod;
	}

	/** Schedules an action at t seconds (before start only). */
	public EffectTimeline at(double t, Consumer<EffectTimeline> action) {
		entries.add(new Entry(t, orderCounter++, action));
		sorted = false;
		if (t + 0.5 > endAt) {
			endAt = t + 0.5;
		}
		return this;
	}

	/** Timeline lifetime (seconds); actions after it never run. */
	public EffectTimeline lifetime(double seconds) {
		endAt = seconds;
		return this;
	}

	/** HITSTOP: freezes this timeline (and sprites and particles it owns) for the given milliseconds. */
	public void hitstop(double ms) {
		holdRemaining = Math.max(holdRemaining, ms / 1000.0);
	}

	/**
	 * Absolute stop (5.4): like {@link #hitstop} the sprites and particles of this timeline stand still for the given milliseconds,
	 * but the timeline clock keeps running, so its later actions (cracks, shatter) stay in step with the server.
	 */
	public void pause(double ms) {
		holdRemaining = Math.max(holdRemaining, ms / 1000.0);
		clockContinues = true;
	}

	/** Marks a DUCK window in local time: other FX sounds of the caster play at the factor meanwhile (0.5, or 0 for the pause). */
	public EffectTimeline duck(double from, double to, double factor) {
		duckFrom = from;
		duckTo = to;
		duckFactor = factor;
		return this;
	}

	/** Duck factor currently imposed on other effects of the same caster (1 = none). */
	double duckNow() {
		return duckFrom >= 0 && time >= duckFrom && time < duckTo ? duckFactor : 1.0;
	}

	public double time() {
		return time;
	}

	/** Deterministic random for one action: same seed and salt give the same sequence on every client. */
	public Random rng(int salt) {
		return new Random(seed * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L);
	}

	public boolean isEnded() {
		return ended;
	}

	/** Advances the local clock by dt (FX seconds) and runs due actions. */
	void advance(double dt) {
		if (ended) {
			return;
		}
		if (!sorted) {
			entries.sort(Comparator.comparingDouble(Entry::t).thenComparingInt(Entry::order));
			sorted = true;
		}
		if (holdRemaining > 0) {
			double h = Math.min(holdRemaining, dt);
			holdRemaining -= h;
			FxTimelines.addHeld(slot, h);
			if (!clockContinues) {
				dt -= h;
			} else if (holdRemaining <= 0) {
				clockContinues = false;
			}
		}
		time += dt;
		while (nextEntry < entries.size() && entries.get(nextEntry).t <= time + 1e-9) {
			Entry e = entries.get(nextEntry++);
			try {
				e.action.accept(this);
			} catch (RuntimeException ex) {
				dev.minebleach.reiatsutest.ReiatsuTest.LOGGER.error("[fx] timeline {} action at {} failed", effectId, e.t, ex);
			}
		}
		if (time >= endAt && nextEntry >= entries.size()) {
			ended = true;
		}
	}

	// ------------------------------------------------------------------------------------------ caster access

	/** Live (interpolated) position of the caster's feet, or the event position when the entity is not loaded. */
	public Vec3d casterFeet(float tickDelta) {
		Entity e = caster();
		return e != null ? e.getLerpedPos(tickDelta) : pos;
	}

	public Entity caster() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.world != null && casterId >= 0 ? mc.world.getEntityById(casterId) : null;
	}

	// ------------------------------------------------------------------------------------------ screen effects

	public void flash(double peak, String tint, boolean shortFlash) {
		if (screen) {
			ScreenFx.flash(peak, FxMath.hex(tint), shortFlash, strength);
		}
	}

	public ScreenFx.Layer vignette(ScreenFx.Kind kind, double level, double ramp) {
		return screen ? ScreenFx.vignette(kind, level, ramp, strength) : null;
	}

	public ScreenFx.Layer grade(String colour, double s, double ramp) {
		return screen ? ScreenFx.grade(FxMath.hex(colour), s, ramp, strength) : null;
	}

	public void shake(double amplitudeDeg, double duration) {
		if (screen) {
			ScreenFx.shake(amplitudeDeg, duration, strength);
		}
	}

	// ------------------------------------------------------------------------------------------ sound

	public void sound(String id, double pitch, double volume) {
		FxSound.play(this, id, pos.x, pos.y + 1.0, pos.z, pitch, volume);
	}

	public void soundAt(String id, Vec3d at, double pitch, double volume) {
		FxSound.play(this, id, at.x, at.y, at.z, pitch, volume);
	}
}
