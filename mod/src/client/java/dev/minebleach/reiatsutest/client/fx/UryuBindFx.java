package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.BindFxPlan;
import dev.minebleach.reiatsutest.core.uryu.BindEnd;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * The Sprenger bind visual (URYU_VFX_STORYBOARD 2.8, U-FX 4): the entities the server says are ROOTED (S-U3 packets BIND and END, see
 * {@link UryuSprengerFx#onBind}) get, from the bind moment (0.8 s) until the explosion, a damage break or a cancel, a Quincy cross sigil under the feet (the cross of the pendant, never the pentagram: that belongs to the explosion),
 * three rings around the legs and five chains that run from the dagger beads to the chest (the three nearest entities in full, the others
 * only the sigil), plus a quiet resonating sound for the two nearest. The END reason picks the ending ({@link BindFxPlan#endLook}): damage
 * snaps the chains and shatters the rings, the explosion and a cancel fade everything. Everything is a per frame glow source (closed form of
 * the FX clock) so the harness freeze holds it; the numbers are {@link BindFxPlan}.
 */
final class UryuBindFx {
	static final class Bound {
		final int id;
		final double cx;
		final double cy;
		final double cz;
		final double start;
		final double until;
		double endedAt = -1;
		BindEnd reason;
		BindFxPlan.EndLook look;
		/** Last known feet position and size (the entity can be gone a moment after its death). */
		double x;
		double y;
		double z;
		double width = 0.6;
		double height = 1.8;
		boolean seen;
		double nextSound;

		Bound(int id, double cx, double cy, double cz, double start, double until) {
			this.id = id;
			this.cx = cx;
			this.cy = cy;
			this.cz = cz;
			this.start = start;
			this.until = until;
		}

		boolean ended() {
			return endedAt >= 0;
		}
	}

	/** Entity id to its root (BIND adds, END marks, {@link #frame} removes). */
	private static final Map<Integer, Bound> BOUND = new LinkedHashMap<>();
	/** FX clock of the last END(EXPLOSION) of the local player (the explosion that follows is an impact frame for him), or -10. */
	private static double localExplodedAt = -10;
	/** Dev/harness statistics: entities with the full visual in the last frame, chains drawn, bind packets seen. */
	static int lastFull;
	static int lastChains;
	static int bindsSeen;

	private static final float[] Q0 = {1f, 1f, 1f};
	private static final float[] Q1 = FxMath.hex(UryuPalette.Q1);
	private static final float[] Q3 = FxMath.hex(UryuPalette.Q3);
	private static final float[] Q7 = FxMath.hex(UryuPalette.Q7);

	private UryuBindFx() {
	}

	static void clear() {
		BOUND.clear();
		localExplodedAt = -10;
	}

	/** True while the server says this entity is rooted (a little longer than its time, for the END packet that is on its way). */
	static boolean isBound(int entityId) {
		Bound b = BOUND.get(entityId);
		return b != null && !b.ended() && FxClock.now <= b.until + 0.25;
	}

	/** True when the local player was freed by an explosion within the last 0.5 s (the BURST packet follows the END packets of the same tick). */
	static boolean localJustExploded(double now) {
		return now - localExplodedAt >= 0 && now - localExplodedAt < 0.5;
	}

	/** True while the local player is rooted. */
	static boolean localBound() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && isBound(mc.player.getId());
	}

	/** Number of entities still drawn (harness). */
	static int count() {
		return BOUND.size();
	}

	/** S-U3 BIND: {@code ids} = the entities, {@code d = {cx, cy, cz, ticks until the root ends}}. */
	static void onBind(int[] ids, float[] d) {
		if (d.length < 4) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		double now = FxClock.now;
		double until = now + d[3] / 20.0;
		float td = mc.getRenderTickCounter().getTickDelta(false);
		bindsSeen++;
		for (int id : ids) {
			Bound b = new Bound(id, d[0], d[1], d[2], now, until);
			b.nextSound = now;
			sample(b, w, td);
			BOUND.put(id, b);
			pop(b);
			if (mc.player != null && mc.player.getId() == id) {
				// the flash of the bind for the bound one (0.20 short, Q2; 0.15 at most with reduce motion)
				double peak = reduced() ? 0.15 : 0.20;
				ScreenFx.flash(peak, FxMath.hex(UryuPalette.Q2), true, 1.0);
			}
		}
		ReiatsuTest.LOGGER.info("[fx] sprenger bind: {} entities rooted for {} ticks at ({}, {}, {})", ids.length, d[3], d[0], d[1], d[2]);
	}

	/** S-U3 END: {@code ids} = the freed entities, {@code d = {cx, cy, cz, reason}} (codes of {@link BindEnd}). */
	static void onEnd(int[] ids, float[] d) {
		BindEnd reason = d.length >= 4 ? BindEnd.fromCode(Math.round(d[3])) : BindEnd.CANCEL;
		MinecraftClient mc = MinecraftClient.getInstance();
		double now = FxClock.now;
		float td = mc.getRenderTickCounter().getTickDelta(false);
		for (int id : ids) {
			Bound b = BOUND.get(id);
			if (b == null || b.ended()) {
				continue;
			}
			sample(b, mc.world, td);
			b.endedAt = now;
			b.reason = reason;
			b.look = BindFxPlan.endLook(reason);
			if (reason == BindEnd.EXPLOSION && mc.player != null && mc.player.getId() == id) {
				localExplodedAt = now;
			}
			if (b.look.snapChains()) {
				snap(b);
			}
		}
		ReiatsuTest.LOGGER.info("[fx] sprenger bind ends: {} entities, reason {}", ids.length, reason);
		if (reason == BindEnd.CANCEL && d.length >= 3) {
			UryuSprengerFx.onBindCancelled(d[0], d[1], d[2]);
		}
	}

	/** The chains snap: the break burst at the chest. */
	private static void snap(Bound b) {
		double cy = b.y + b.height * BindFxPlan.CHEST;
		FxGlowBatch.sprite(GlowSprite.STAR4).at(b.x, cy, b.z).size(0.9).life(0.18).curve(0.01, 0.7).color(UryuPalette.Q0).peak(1.0).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(b.x, b.y + 0.1, b.z).ground().size(BindFxPlan.ringRadius(b.width) * 2.0 / 0.4375, 1.0).sizeEase(FxMath.OC).life(0.25)
				.curve(0.01, 0.7).color(UryuPalette.Q2).peak(0.7).spawn();
		Random r = new Random(b.id * 31L + 7);
		for (int i = 0; i < BindFxPlan.DAMAGE.flakes(); i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = 2.0 + r.nextDouble() * 3.0;
			FxParticles.spec(FxParticles.Kind.FLECHETTE).at(b.x, cy, b.z).vel(Math.cos(a) * s, 1.0 + r.nextDouble() * 2.0, Math.sin(a) * s).drag(0.92).gravity(0.5).life(24 + r.nextInt(8))
					.size(0.3 + r.nextDouble() * 0.15).color(UryuPalette.Q3).seed(r.nextInt(3)).spin((r.nextDouble() - 0.5) * 0.5).spawn();
		}
		if (b.look.glassSound()) {
			FxSound.play(null, "block.glass.break", b.x, cy, b.z, 1.8, 0.4);
		}
	}

	/** The moment of the bind: a pop at the chest and a ring under the feet (the sigil and the chains fade in over 0.15 s). */
	private static void pop(Bound b) {
		double cy = b.y + b.height * BindFxPlan.CHEST;
		FxGlowBatch.sprite(GlowSprite.STAR4).at(b.x, cy, b.z).size(0.7).life(0.15).curve(0.01, 0.7).color(UryuPalette.Q1).peak(0.9).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(b.x, b.y + 0.08, b.z).ground().size(0.2, BindFxPlan.sigilSize(b.width) / 0.4375 * 0.9).sizeEase(FxMath.OC).life(0.25)
				.curve(0.01, 0.7).color(UryuPalette.Q2).peak(0.8).spawn();
		Random r = new Random(b.id * 17L + 3);
		for (int i = 0; i < 6; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			FxParticles.spec(FxParticles.Kind.FLECHETTE).at(b.x + Math.cos(a) * 0.3, b.y + 0.1, b.z + Math.sin(a) * 0.3).vel(Math.cos(a) * 1.5, 1.2, Math.sin(a) * 1.5).drag(0.93).gravity(0.3)
					.life(20).size(0.28).color(UryuPalette.Q3).seed(i).spin((r.nextDouble() - 0.5) * 0.4).spawn();
		}
	}

	private static boolean reduced() {
		return FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
	}

	/** Reads the current feet position and size of the entity into the record. */
	private static void sample(Bound b, ClientWorld w, float td) {
		if (w == null) {
			return;
		}
		Entity en = w.getEntityById(b.id);
		if (en != null && !en.isRemoved()) {
			Vec3d p = en.getLerpedPos(td);
			b.x = p.x;
			b.y = p.y;
			b.z = p.z;
			b.width = Math.max(0.3, en.getWidth());
			b.height = Math.max(0.6, en.getHeight());
			b.seen = true;
		} else if (!b.seen) {
			b.x = b.cx;
			b.y = b.cy;
			b.z = b.cz;
		}
	}

	/** Per frame (render START): old roots go, the resonating sound of the two nearest. */
	static void frame(double now) {
		if (BOUND.isEmpty()) {
			return;
		}
		BOUND.values().removeIf(b -> b.ended() ? now - b.endedAt > 1.0 : now > b.until + 2.0); // an END packet that never came (out of range): do not keep the root forever
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}
		float td = mc.getRenderTickCounter().getTickDelta(false);
		List<Bound> live = new ArrayList<>();
		for (Bound b : BOUND.values()) {
			if (!b.ended()) {
				sample(b, mc.world, td);
				live.add(b);
			}
		}
		if (live.isEmpty()) {
			return;
		}
		double[] dist = new double[live.size()];
		for (int i = 0; i < dist.length; i++) {
			Bound b = live.get(i);
			dist[i] = mc.player.squaredDistanceTo(b.x, b.y, b.z);
		}
		for (int idx : BindFxPlan.nearest(dist, BindFxPlan.SOUND_SLOTS)) {
			Bound b = live.get(idx);
			if (now >= b.nextSound && now - b.start >= 0) {
				b.nextSound = now + BindFxPlan.SOUND_EVERY;
				FxSound.play(null, BindFxPlan.SOUND_ID, b.x, b.y + b.height * 0.5, b.z, BindFxPlan.SOUND_PITCH, BindFxPlan.SOUND_VOLUME);
			}
		}
	}

	/** The glow of every root: sigil, rings, chains (the glow source of {@link UryuSprengerFx#emitGlow}). */
	static void emit(FxGlowBatch.Emitter e, double now) {
		lastFull = 0;
		lastChains = 0;
		if (BOUND.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		float td = mc.getRenderTickCounter().getTickDelta(false);
		List<Bound> vis = new ArrayList<>();
		for (Bound b : BOUND.values()) {
			if (!b.ended()) {
				sample(b, mc.world, td);
			}
			if (now - b.start >= 0 && (!b.ended() || now - b.endedAt < Math.max(b.look.sigilFade(), b.look.ringFade()) + 0.02)) {
				vis.add(b);
			}
		}
		if (vis.isEmpty()) {
			return;
		}
		double[] dist = new double[vis.size()];
		for (int i = 0; i < dist.length; i++) {
			Bound b = vis.get(i);
			dist[i] = Math.sqrt((b.x - e.cx) * (b.x - e.cx) + (b.z - e.cz) * (b.z - e.cz) + (b.y - e.cy) * (b.y - e.cy));
		}
		boolean[] full = new boolean[vis.size()];
		for (int idx : BindFxPlan.nearest(dist, BindFxPlan.MAX_FULL)) {
			full[idx] = true;
		}
		boolean rm = reduced();
		for (int i = 0; i < vis.size(); i++) {
			Bound b = vis.get(i);
			draw(e, b, now, full[i], rm);
			lastFull += full[i] ? 1 : 0;
		}
	}

	private static void draw(FxGlowBatch.Emitter e, Bound b, double now, boolean full, boolean rm) {
		double age = now - b.start;
		double endAge = b.ended() ? now - b.endedAt : -1.0;
		double ramp = BindFxPlan.rampIn(age);
		double sigFade = b.ended() ? BindFxPlan.endFade(endAge, b.look.sigilFade()) : 1.0;
		double sig = ramp * sigFade;
		if (sig > 0.004) {
			double size = BindFxPlan.sigilSize(b.width);
			e.ground(GlowSprite.ZEICHEN_CROSS, b.x, b.y + 0.06, b.z, size, size, Math.PI + BindFxPlan.sigilRotation(age, rm), Q1[0], Q1[1], Q1[2],
					BindFxPlan.SIGIL_PEAK * BindFxPlan.sigilFlicker(age, rm) * sig);
		}
		if (!full) {
			return;
		}
		double ringFade = ramp * (b.ended() ? BindFxPlan.endFade(endAge, b.look.ringFade()) : 1.0);
		if (ringFade > 0.004) {
			double burst = b.ended() && b.look.shatterRings() ? BindFxPlan.ringBurst(endAge, b.look.ringFade()) : 1.0;
			double rad = BindFxPlan.ringRadius(b.width) * burst;
			for (int k = 0; k < BindFxPlan.RING_HEIGHTS.length; k++) {
				double y = b.y + BindFxPlan.ringHeight(k, b.height);
				e.ground(GlowSprite.RING_THIN, b.x, y, b.z, rad / 0.4375, rad / 0.4375, 0.0, Q3[0], Q3[1], Q3[2], BindFxPlan.RING_INTENSITY * ringFade);
				double turn = rm ? 0.0 : 2.0 * Math.PI * BindFxPlan.ringTurns(k) * age;
				for (int m = 0; m < 2; m++) {
					double th = turn + Math.PI * m + k * 1.1;
					e.sprite(GlowSprite.STAR4, b.x + Math.cos(th) * rad, y, b.z + Math.sin(th) * rad, BindFxPlan.RING_BEAD, th, Q0[0], Q0[1], Q0[2], 0.7 * ringFade);
				}
			}
		}
		// the chains from the dagger beads to the chest
		double chainFade = ramp;
		double rem = 1.0;
		if (b.ended()) {
			if (b.look.snapChains()) {
				rem = BindFxPlan.snapRemaining(endAge);
				chainFade = ramp * rem;
			} else {
				chainFade = ramp * BindFxPlan.endFade(endAge, b.look.sigilFade());
			}
		}
		if (chainFade <= 0.004) {
			return;
		}
		double[][] hilts = UryuSprengerFx.hilts(b.cx, b.cy, b.cz);
		double[] to = {b.x, b.y + b.height * BindFxPlan.CHEST, b.z};
		// a snapping chain keeps only its two ends (35 percent each, shrinking), a whole one runs from the dagger to the chest
		double u1 = b.ended() && b.look.snapChains() ? 0.35 * rem : 1.0;
		double u2 = b.ended() && b.look.snapChains() ? 1.0 - 0.35 * rem : 1.0;
		for (int i = 0; i < BindFxPlan.CHAINS; i++) {
			double[] from = hilts[i];
			if (b.ended() && b.look.snapChains()) {
				path(e, from, to, 0.0, u1, chainFade);
				path(e, from, to, u2, 1.0, chainFade);
			} else {
				path(e, from, to, 0.0, 1.0, chainFade);
				double[] p = BindFxPlan.chainPoint(from, to, BindFxPlan.pulseAt(rm ? 0.5 : age, i));
				e.sprite(GlowSprite.GLOW_CORE, p[0], p[1], p[2], 0.22, 0.0, Q1[0], Q1[1], Q1[2], 0.7 * chainFade);
			}
			lastChains++;
		}
	}

	/** The part {@code u0 .. u1} of the two segment chain as light lines (Q7 band with a white core). */
	private static void path(FxGlowBatch.Emitter e, double[] from, double[] to, double u0, double u1, double fade) {
		if (u1 - u0 < 1e-3) {
			return;
		}
		double[][] pts = new double[3][];
		int n = 0;
		pts[n++] = BindFxPlan.chainPoint(from, to, u0);
		if (u0 < 0.5 && u1 > 0.5) {
			pts[n++] = BindFxPlan.chainMiddle(from, to);
		}
		pts[n++] = BindFxPlan.chainPoint(from, to, u1);
		for (int k = 0; k + 1 < n; k++) {
			double[] a = pts[k];
			double[] c = pts[k + 1];
			e.streak(GlowSprite.LINE, c[0], c[1], c[2], a[0], a[1], a[2], BindFxPlan.CHAIN_WIDTH, Q7[0], Q7[1], Q7[2], BindFxPlan.CHAIN_INTENSITY * fade);
			e.streak(GlowSprite.LINE, c[0], c[1], c[2], a[0], a[1], a[2], BindFxPlan.CHAIN_WIDTH * 0.35, Q0[0], Q0[1], Q0[2], BindFxPlan.CHAIN_INTENSITY * 0.8 * fade);
		}
	}

	/** Convenience for the shells: the pentagon centre of a bound record is the packet's (the harness reads it). */
	static double[] centreOf(int entityId) {
		Bound b = BOUND.get(entityId);
		return b == null ? null : new double[] {b.cx, b.cy, b.cz};
	}
}
