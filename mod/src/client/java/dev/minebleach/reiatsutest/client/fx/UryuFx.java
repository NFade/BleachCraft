package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Visuals of Uryu Ishida (design/URYU_PLAN.md U4 to U6, design/URYU_VFX_STORYBOARD.md). The server simulates everything; this class is only
 * the DISPATCHER: it routes the effect events (ids 72 to 78) and the {@link UryuArrowsS2C} data packets to one shell per effect family
 * and calls their per frame hooks. The arrows themselves are {@link UryuArrowRenderer}; the shells are {@link UryuBowFx} (the bow forming,
 * the draw light), {@link UryuAuraFx} (Focus, shunpo look, state aura), {@link UryuSprengerFx}, {@link UryuSeeleFx} (the combo),
 * {@link UryuWingFx} (wings and barrage), {@link UryuRainFx} (Licht Regen) and {@link UryuStarFx} (Sternenstaub). Everything goes through
 * {@link FxGlowBatch}, {@link FxShapes}, {@link UryuSeeleRenderer} and the particles; the shells keep today's placeholder looks until the U-FX
 * steps replace them.
 */
public final class UryuFx {
	/**
	 * Effect id 70 is the Blut Arterie release (U-FX 6), 72 to 77 are Uryu's ability events (the server also sends the data packet for the ones
	 * that need exact positions), 78 is the cast start of the abilities that are aimed at their release (S-U4). 71 (the bankai release) is
	 * still drawn by the common release effects; it joins here in U-FX 7.
	 */
	public static boolean handles(int effectId) {
		return effectId == 70 || effectId >= 72 && effectId <= 78;
	}

	/** Stacks of Focus of the local player (server sync, drawn by the HUD). */
	public static volatile int focus;

	/** Palette of the placeholder looks (the Quincy palette is {@link UryuPalette}). */
	static final float[] PALE = {0.66f, 0.85f, 0.96f};
	static final float[] GLOW = {0.50f, 0.82f, 1.0f};
	static final float[] WHITE = {1f, 1f, 1f};

	private static boolean inited;

	private UryuFx() {
	}

	private static void init() {
		if (inited) {
			return;
		}
		inited = true;
		UryuArrowRenderer.init();
		UryuSeeleRenderer.init();
		UryuBowFx.init();
		UryuAuraFx.init();
		UryuSprengerFx.init();
		UryuSeeleFx.init();
		UryuWingFx.init();
		WorldRenderEvents.START.register(ctx -> frame());
		FxGlowBatch.addSource(UryuFx::emitGlow);
	}

	/** Stacks of Focus of another Uryu player (S-U2, only what the server sent to the trackers). */
	public static int focusOf(int entityId) {
		return UryuAuraFx.focusOf(entityId);
	}

	/** True while the server says this entity is rooted by a Sprenger pentagon (S-U3). */
	public static boolean isBound(int entityId) {
		return UryuSprengerFx.isBound(entityId);
	}

	// ------------------------------------------------------------------ the draw of the Quincy cross (J)

	/** The draw and sheathe events of the item ({@link dev.minebleach.reiatsutest.client.model.DrawEvents}) for Uryu: the bow forms from Reishi (2.1) and is put away. */
	public static void onDraw(dev.minebleach.reiatsutest.client.model.DrawEvents.Phase phase, net.minecraft.entity.LivingEntity entity, boolean local) {
		init();
		UryuBowFx.onDraw(phase, entity, local);
	}

	// ------------------------------------------------------------------ effect events

	/**
	 * Effect events: 70 the Blut Arterie release, 74 the lunge of the Seele Schneider, 75 the wing barrage, 76 the Licht Regen square, 78 the cast start of Licht Regen
	 * and Sternenstaub (params[3] says which); the rest is drawn from the data packets.
	 */
	public static void play(MinecraftClient client, EffectEventS2C e) {
		ClientWorld w = client.world;
		if (w == null) {
			return;
		}
		init();
		switch (e.effectId()) {
			case 73 -> UryuSprengerFx.onCast(e);
			case 70 -> UryuAuraFx.releaseShikai(e); // Blut Arterie (U-FX 6)
			case 74 -> UryuSeeleFx.lunge(w, e);
			case 75 -> UryuWingFx.barrage(e);
			case 76 -> UryuRainFx.rainSquare(e);
			case 78 -> {
				float[] p = e.params();
				if (p.length >= 5) {
					if (Math.round(p[3]) == 76) {
						UryuRainFx.castStart(e);
					} else if (Math.round(p[3]) == 77) {
						UryuStarFx.castStart(e);
					}
				}
			}
			default -> { }
		}
	}

	// ------------------------------------------------------------------ data packets

	/** The data packets: arrows, explosion, line, pentagon, hit sparks, Focus, slashes, binds. */
	public static void onArrows(UryuArrowsS2C p) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		float[] d = p.data();
		if (p.kind() == UryuArrowsS2C.FOCUS) {
			init(); // the Focus feedback needs its per frame sources from the first packet on
			UryuAuraFx.onFocus(p.ids());
			return;
		}
		if (w == null) {
			return;
		}
		init();
		switch (p.kind()) {
			case UryuArrowsS2C.ARROWS -> UryuArrowRenderer.onList(p.ids(), d);
			case UryuArrowsS2C.IMPACT -> {
				if (d.length >= 5) {
					if (Math.round(d[4]) == UryuArrowsS2C.KIND_SLASH) {
						UryuSeeleFx.hit(w, d[0], d[1], d[2]); // a Seele Schneider stroke hit a target (U-FX 5)
					} else {
						UryuArrowRenderer.impact(d[0], d[1], d[2], Math.round(d[3]), Math.round(d[4]));
					}
				}
			}
			case UryuArrowsS2C.BURST -> {
				if (d.length >= 5) {
					UryuSprengerFx.burst(w, d[0], d[1], d[2], d[3], d[4] > 0.5f);
				}
			}
			case UryuArrowsS2C.LINE -> {
				if (d.length >= 7) {
					UryuStarFx.line(new Vec3d(d[0], d[1], d[2]), new Vec3d(d[3], d[4], d[5]), d[6]);
				}
			}
			case UryuArrowsS2C.TRAP -> {
				if (d.length >= 5) {
					UryuSprengerFx.trap(d[0], d[1], d[2], d[3], d[4] / 20.0);
				}
			}
			case UryuArrowsS2C.SLASH -> {
				if (d.length >= 9) {
					UryuSeeleFx.slash(d);
				}
			}
			case UryuArrowsS2C.BIND -> UryuSprengerFx.onBind(p.ids(), d);
			case UryuArrowsS2C.END -> UryuSprengerFx.onEnd(p.ids(), d);
			default -> { }
		}
	}

	// ------------------------------------------------------------------ cleanup

	/** The bankai of a player ended (any reason): his wings go with it. */
	public static void clearWings(int casterId) {
		UryuWingFx.clearWings(casterId);
	}

	// ------------------------------------------------------------------ shunpo

	/** The Uryu shunpo: a blue Reishi trail along the path and a Reishi platform disc under the feet at the start and the arrival. */
	public static void shunpoReishi(Vec3d start, Vec3d delta) {
		init();
		UryuAuraFx.shunpoReishi(start, delta);
	}

	// ------------------------------------------------------------------ per frame

	private static void frame() {
		double now = FxClock.now;
		UryuAuraFx.frame();
		UryuSeeleFx.frame(now);
		UryuSprengerFx.frame(now);
	}

	private static void emitGlow(FxGlowBatch.Emitter e) {
		double now = FxClock.now;
		UryuSprengerFx.emitGlow(e, now);
		UryuSeeleFx.emitGlow(e, now);
		UryuWingFx.emitGlow(e, now);
	}
}
