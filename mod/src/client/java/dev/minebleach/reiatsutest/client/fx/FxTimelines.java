package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Owner of the running {@link EffectTimeline}s and the per frame FX clock step. The dev freeze hook
 * ({@code -Dreiatsu.fx.freezeAt=<ms>}) freezes the whole FX clock exactly when the first running timeline reaches that
 * local time, so a screenshot shows the effect at the requested millisecond.
 */
public final class FxTimelines {
	private static final int SLOTS = 256;
	private static final double[] HELD = new double[SLOTS];
	private static final List<EffectTimeline> ACTIVE = new ArrayList<>();
	private static int nextSlot;
	/** Dev: freeze target in seconds (-1 = off), armed per start. */
	private static double freezeAt = -1;
	private static boolean freezeArmed;
	/** Frame number on which the freeze happened (0 = not frozen), for the harness. */
	public static long frozenFrame;
	public static double frozenAtTimeline = -1;

	static {
		String p = System.getProperty("reiatsu.fx.freezeAt");
		if (p != null && !p.isBlank()) {
			try {
				setFreezeAt(Double.parseDouble(p));
			} catch (NumberFormatException e) {
				ReiatsuTest.LOGGER.warn("[fx] bad -Dreiatsu.fx.freezeAt={}", p);
			}
		}
	}

	private FxTimelines() {
	}

	/** Arms the freeze hook for the next timeline(s): the clock stops when the first active timeline reaches {@code ms}. */
	public static void setFreezeAt(double ms) {
		freezeAt = ms < 0 ? -1 : ms / 1000.0;
		freezeArmed = freezeAt >= 0;
		frozenFrame = 0;
		frozenAtTimeline = -1;
	}

	public static double held(int slot) {
		return slot < 0 ? 0 : HELD[slot & (SLOTS - 1)];
	}

	static void addHeld(int slot, double d) {
		HELD[slot & (SLOTS - 1)] += d;
	}

	public static boolean held(EffectTimeline t) {
		return t.holdRemaining > 0;
	}

	/** True while the timeline of this slot is in HITSTOP (particles of that effect stand still). */
	public static boolean inHitstop(int slot) {
		for (EffectTimeline t : ACTIVE) {
			if (t.slot == slot) {
				return t.holdRemaining > 0;
			}
		}
		return false;
	}

	public static int activeCount() {
		return ACTIVE.size();
	}

	public static List<EffectTimeline> active() {
		return ACTIVE;
	}

	/** Creates a timeline for an effect event (not started until {@link #start}). */
	public static EffectTimeline create(int effectId, long seed, Vec3d pos, Vec3d dir, float[] params, int casterId, boolean forceRemote) {
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean local = !forceRemote && mc.player != null && mc.player.getId() == casterId;
		double d = 0;
		if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null && mc.player != null) {
			d = mc.gameRenderer.getCamera().getPos().distanceTo(pos);
		}
		double strength = local ? 1.0 : FxMath.screenStrength(d);
		double lod = FxMath.lod(d);
		int slot = (nextSlot++) & (SLOTS - 1);
		HELD[slot] = 0;
		return new EffectTimeline(slot, effectId, seed, pos, dir, params, casterId, local, strength, lod);
	}

	public static void start(EffectTimeline t) {
		ACTIVE.add(t);
		int max = FxConfig.maxConcurrentFx;
		// beyond maxConcurrentFx the oldest timelines lose their screen effects first (1.6)
		for (int i = 0; i < ACTIVE.size() - max; i++) {
			ACTIVE.get(i).screen = false;
		}
	}

	/** Sound duck factor for a sound played by {@code from}: the strongest duck of any other timeline of the same caster. */
	static double duckFactor(EffectTimeline from) {
		double f = 1.0;
		for (EffectTimeline t : ACTIVE) {
			if (t != from && t.casterId == from.casterId) {
				f = Math.min(f, t.duckNow());
			}
		}
		return f;
	}

	/** Once per rendered world frame (WorldRenderEvents.START). */
	public static void beginFrame() {
		double raw = FxClock.frozen ? 0 : FxClock.rawDt();
		if (FxClock.frozen) {
			FxClock.advance(0);
			return;
		}
		double dt = raw;
		boolean freezeNow = false;
		if (freezeArmed && !ACTIVE.isEmpty()) {
			EffectTimeline primary = ACTIVE.get(0);
			if (primary.time + dt >= freezeAt && !primary.ended) {
				dt = Math.max(0, freezeAt - primary.time);
				freezeNow = true;
			}
		}
		if (FxClock.freezeTarget >= 0 && FxClock.now + dt >= FxClock.freezeTarget) {
			dt = Math.max(0, FxClock.freezeTarget - FxClock.now);
			FxClock.freezeTarget = -1;
			freezeNow = true;
		}
		FxClock.advance(dt);
		for (int i = 0; i < ACTIVE.size(); i++) {
			ACTIVE.get(i).advance(dt);
		}
		ACTIVE.removeIf(EffectTimeline::isEnded);
		if (freezeNow) {
			FxClock.freeze();
			freezeArmed = false;
			frozenFrame = FxClock.frame;
			frozenAtTimeline = freezeAt;
		}
	}

	public static void clear() {
		ACTIVE.clear();
		FxGlowBatch.clear();
		ScreenFx.clear();
		FxParticles.clearAll();
		RukiaFx.clearAll();
	}

	/** Position helper for effects that follow an entity. */
	public static Vec3d lerpedFeet(Entity e, float tickDelta, Vec3d fallback) {
		return e != null ? e.getLerpedPos(tickDelta) : fallback;
	}
}
