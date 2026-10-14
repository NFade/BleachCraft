package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Sprenger (H, SHIKAI, effect 73, URYU_VFX_STORYBOARD 2.7 and 2.8; U-FX 4 fills it). Today: five {@code uryu_seele} daggers drop into the
 * vertices of the pentagon ({@link UryuSeeleRenderer}), a glowing pentagram pulses faster until the explosion, the explosion is a flash, a
 * shock ring, a pillar of light and sparks. The BIND and END packets of S-U3 (the entities the root holds, and why it ended) are only
 * stored here for the bind visuals of 2.8. Called by {@link UryuFx}.
 */
final class UryuSprengerFx {
	private static final class Pentagon {
		int group;
		double cx;
		double cy;
		double cz;
		double r;
		double born;
		double life;
		double[] px = new double[5];
		double[] pz = new double[5];
	}

	private static final List<Pentagon> PENTAGONS = new ArrayList<>();
	private static int nextGroup = 1;

	/** What S-U3 told about one bound entity: the pentagon centre and the client time the root ends at (the server's untilTick converted). */
	record Bound(double cx, double cy, double cz, double untilClock) {
	}

	/** Entity id to the root it is held by (S-U3 BIND), removed by END. */
	private static final Map<Integer, Bound> BOUND = new HashMap<>();

	private UryuSprengerFx() {
	}

	static void init() {
		// nothing yet
	}

	/** S-U3 BIND: {@code ids} = the bound entities, {@code d = {cx, cy, cz, ticks until the root ends (counted from the packet)}}. */
	static void onBind(int[] ids, float[] d) {
		if (d.length < 4) {
			return;
		}
		double until = FxClock.now + d[3] / 20.0;
		for (int id : ids) {
			BOUND.put(id, new Bound(d[0], d[1], d[2], until));
		}
		ReiatsuTest.LOGGER.info("[fx] sprenger bind: {} entities rooted for {} ticks at ({}, {}, {})", ids.length, d[3], d[0], d[1], d[2]);
	}

	/** S-U3 END: {@code ids} = the freed entities, {@code d = {cx, cy, cz, reason}} with the codes of {@code BindEnd}. */
	static void onEnd(int[] ids, float[] d) {
		for (int id : ids) {
			BOUND.remove(id);
		}
		ReiatsuTest.LOGGER.info("[fx] sprenger bind ends: {} entities, reason {}", ids.length,
				d.length >= 4 ? dev.minebleach.reiatsutest.core.uryu.BindEnd.fromCode(Math.round(d[3])) : "?");
	}

	/** True while the server says this entity is rooted by a pentagon. */
	static boolean isBound(int entityId) {
		Bound b = BOUND.get(entityId);
		return b != null && FxClock.now <= b.untilClock() + 0.25;
	}

	/** Five Seele Schneider drop into the vertices, the pentagram lights up and speeds up until the explosion. */
	static void trap(double cx, double cy, double cz, double r, double seconds) {
		Pentagon pg = new Pentagon();
		pg.group = nextGroup++;
		pg.cx = cx;
		pg.cy = cy;
		pg.cz = cz;
		pg.r = r;
		pg.born = FxClock.now;
		pg.life = seconds;
		for (int i = 0; i < 5; i++) {
			double ang = -Math.PI / 2.0 + i * 2.0 * Math.PI / 5.0;
			pg.px[i] = cx + Math.cos(ang) * r;
			pg.pz[i] = cz + Math.sin(ang) * r;
			UryuSeeleRenderer.Seele s = new UryuSeeleRenderer.Seele();
			// tip down, leaning a little towards the centre; the tip stands in the ground, the hilt 0.9 m above it
			double lean = 0.18;
			Vec3d toCentre = new Vec3d(cx - pg.px[i], 0, cz - pg.pz[i]).normalize();
			s.dx = toCentre.x * lean;
			s.dy = -1;
			s.dz = toCentre.z * lean;
			Vec3d dd = new Vec3d(s.dx, s.dy, s.dz).normalize();
			s.scale = 3.4f;
			s.x = pg.px[i] - dd.x * 0.3 * s.scale + 0.0;
			s.y = cy + 0.05 - dd.y * 0.3 * s.scale;
			s.z = pg.pz[i] - dd.z * 0.3 * s.scale;
			s.born = FxClock.now + i * 0.05;
			s.fall = 0.22;
			s.dropHeight = 6.0;
			s.life = seconds + 0.3;
			s.group = pg.group;
			s.spinDegPerSec = 40;
			UryuSeeleRenderer.add(s);
		}
		PENTAGONS.removeIf(x -> FxClock.now - x.born > x.life + 0.5);
		PENTAGONS.add(pg);
		FxSound.play(null, "item.trident.hit_ground", cx, cy, cz, 1.6, 0.8);
		FxSound.play(null, "block.amethyst_block.resonate", cx, cy, cz, 0.7, 0.8);
	}

	/** The explosion: the daggers go, a flash, a shock ring, a pillar of light, sparks. */
	static void burst(ClientWorld w, double x, double y, double z, double r, boolean early) {
		Pentagon hit = null;
		double best = 4.0;
		for (Pentagon p : PENTAGONS) {
			double dd = Math.hypot(p.cx - x, p.cz - z);
			if (dd < best) {
				best = dd;
				hit = p;
			}
		}
		if (hit != null) {
			UryuSeeleRenderer.removeGroup(hit.group);
			PENTAGONS.remove(hit);
		}
		float[] tint = early ? UryuFx.WHITE : UryuFx.PALE;
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(x, y + 0.8, z).size(r * 2.4, r * 3.2).life(0.4).color(tint).peak(1.0).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR6).at(x, y + 1.0, z).size(r * 2.0, r * 0.4).life(0.25).color("#FFFFFF").peak(1.0).rot(0.3, 1.5).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(x, y + 0.1, z).ground().size(r * 0.8, r * 4.4).life(0.45).color(UryuFx.GLOW).peak(1.0).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(x, y + 0.12, z).ground().size(r * 0.5, r * 3.4).life(0.55).color("#FFFFFF").peak(0.8).spawn();
		FxShapes.Beam b = FxShapes.beam(x, z, y, 0).height(a -> 9.0 * Math.min(1.0, a / 0.08)).width(a -> r * 0.9 * Math.max(0.0, 1.0 - a / 0.4));
		b.color(early ? "#FFFFFF" : "#8ED6FF").life(0.4).intensity(a -> Math.max(0.0, 1.0 - a / 0.4));
		w.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, 0, 0, 0);
		for (int i = 0; i < 40; i++) {
			double a = Math.random() * Math.PI * 2;
			double s = 0.2 + Math.random() * 0.5;
			w.addParticle(ParticleTypes.END_ROD, x + Math.cos(a) * r * 0.4, y + 0.3, z + Math.sin(a) * r * 0.4, Math.cos(a) * s, 0.15 + Math.random() * 0.4, Math.sin(a) * s);
		}
		FxSound.play(null, "entity.generic.explode", x, y, z, 1.3, 1.0);
		FxSound.play(null, "block.amethyst_block.break", x, y, z, 0.6, 1.0);
	}

	/** Per frame (render START): old pentagons go. */
	static void frame(double now) {
		PENTAGONS.removeIf(p -> now - p.born > p.life + 0.6);
		BOUND.values().removeIf(b -> now > b.untilClock() + 2.0); // an END packet that never came (out of range): do not keep the root forever
	}

	/** The glow of the pentagons (pentagram, ring, disc, pillars at the daggers). */
	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		float[] glow = UryuFx.GLOW;
		float[] pale = UryuFx.PALE;
		for (Pentagon p : PENTAGONS) {
			double age = now - p.born;
			if (age < 0 || age > p.life + 0.2) {
				continue;
			}
			double k = Math.min(1.0, age / Math.max(0.1, p.life)); // 0 .. 1 towards the explosion
			double ramp = Math.min(1.0, age / 0.25);
			double pulse = 0.65 + 0.35 * Math.sin(age * (6.0 + 22.0 * k * k));
			double bound = Math.abs(age - UryuBalance.SPRENGER_BIND_AT / 20.0) < 0.18 ? 1.0 : 0.0; // the flash at the moment the legs are bound
			double inten = ramp * (0.55 + 0.45 * k) * pulse + bound * 0.6;
			double y = p.cy + 0.09;
			// the ring and the soft disc
			e.ground(GlowSprite.RING_THIN, p.cx, y, p.cz, p.r * 2.25, p.r * 2.25, age * 0.2, glow[0], glow[1], glow[2], 0.9 * inten);
			e.ground(GlowSprite.RING_SOFT, p.cx, y, p.cz, p.r * 2.6 * (1.0 - 0.05 * k), p.r * 2.6 * (1.0 - 0.05 * k), 0, pale[0], pale[1], pale[2], 0.35 * inten);
			// the star: every vertex to the second next one
			for (int i = 0; i < 5; i++) {
				int j = (i + 2) % 5;
				e.streak(GlowSprite.STREAK, p.px[i], y, p.pz[i], p.px[j], y, p.pz[j], 0.18 + 0.1 * k, glow[0], glow[1], glow[2], inten);
				e.streak(GlowSprite.STREAK, p.px[i], y + 0.01, p.pz[i], p.px[j], y + 0.01, p.pz[j], 0.07, 1f, 1f, 1f, 0.8 * inten);
			}
			// light pillars at the daggers and a glow on each hilt
			for (int i = 0; i < 5; i++) {
				e.streak(GlowSprite.STREAK, p.px[i], y + 2.2, p.pz[i], p.px[i], y, p.pz[i], 0.28, glow[0], glow[1], glow[2], 0.6 * inten);
				e.sprite(GlowSprite.GLOW_SOFT, p.px[i], y + 0.9, p.pz[i], 0.9, 0, pale[0], pale[1], pale[2], 0.7 * inten);
			}
		}
	}
}
