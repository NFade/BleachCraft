package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.core.uryu.WingBarrage;
import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Visuals of Uryu Ishida (design/URYU_PLAN.md U4 to U6). The server simulates everything; this class only draws. The arrows themselves
 * are {@link UryuArrowRenderer}; here: the Sprenger pentagon (five {@code uryu_seele} daggers drop into the ground, a glowing pentagram
 * pulses faster until the explosion), the explosion, the Seele Schneider lunge and its three slashes (a big Reishi blade built from
 * the {@code uryu_seele} mesh sweeps the cone, a crescent trail and a hit spark per target), the wings of the wing barrage (glowing
 * angular feathers behind the caster that flare with every volley) and the Reishi trail and platform of the Uryu shunpo.
 * Everything goes through {@link FxGlowBatch}, {@link FxShapes}, {@link UryuSeeleRenderer} and vanilla particles.
 */
public final class UryuFx {
	/** Effect ids 72 to 77 are Uryu's ability events (the server also sends the data packet for the ones that need exact positions). */
	public static boolean handles(int effectId) {
		return effectId >= 72 && effectId <= 77;
	}

	/** Stacks of Focus of the local player (server sync, drawn by the HUD). */
	public static volatile int focus;

	private static final float[] PALE = {0.66f, 0.85f, 0.96f};
	private static final float[] GLOW = {0.50f, 0.82f, 1.0f};
	private static final float[] WHITE = {1f, 1f, 1f};

	private static boolean inited;
	private static int nextGroup = 1;

	private UryuFx() {
	}

	private static void init() {
		if (inited) {
			return;
		}
		inited = true;
		UryuArrowRenderer.init();
		UryuSeeleRenderer.init();
		WorldRenderEvents.START.register(ctx -> frame());
		FxGlowBatch.addSource(UryuFx::emitGlow);
	}

	// ------------------------------------------------------------------ effect events

	/** Effect events: 74 the lunge of the Seele Schneider, 75 the wing barrage; the rest is drawn from the data packets. */
	public static void play(MinecraftClient client, EffectEventS2C e) {
		ClientWorld w = client.world;
		if (w == null) {
			return;
		}
		init();
		if (e.effectId() == 74) {
			lunge(w, e);
		} else if (e.effectId() == 75) {
			float[] pr = e.params();
			double seconds = pr.length > 4 ? pr[4] : UryuBalance.WING_TICKS / 20.0;
			WINGS.removeIf(x -> x.caster == e.casterId());
			Wing wing = new Wing();
			wing.caster = e.casterId();
			wing.born = FxClock.now;
			wing.life = seconds;
			WINGS.add(wing);
			FxSound.play(null, "entity.phantom.flap", e.x(), e.y() + 1.5, e.z(), 1.7, 0.9);
			FxSound.play(null, "block.amethyst_block.resonate", e.x(), e.y() + 1.5, e.z(), 1.8, 0.8);
		}
	}

	/** Lunge: three streaks along the 1.5 block run (hip, chest, head) and a dust of Reishi. */
	private static void lunge(ClientWorld w, EffectEventS2C e) {
		Vec3d d = new Vec3d(e.dx(), 0, e.dz());
		d = d.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : d.normalize();
		double len = UryuBalance.SEELE_LUNGE + 0.5;
		for (int i = 0; i < 3; i++) {
			double y = e.y() + 0.35 + i * 0.6;
			FxShapes.Strip s = FxShapes.strip(e.x(), y, e.z(), d.x, 0, d.z, 0).length(a -> len).width(a -> 0.18);
			s.color("#A9D8F5").life(0.25).intensity(a -> 0.8 * Math.max(0.0, 1.0 - a / 0.25));
		}
		for (int i = 0; i < 10; i++) {
			double t = i / 9.0 * len;
			w.addParticle(ParticleTypes.END_ROD, e.x() + d.x * t, e.y() + 0.2 + Math.random() * 1.4, e.z() + d.z * t, -d.x * 0.03, 0.01, -d.z * 0.03);
		}
		FxSound.play(null, "entity.player.attack.sweep", e.x(), e.y() + 1.0, e.z(), 1.7, 0.6);
	}

	// ------------------------------------------------------------------ data packets

	/** The data packets: arrows, explosion, line, pentagon, hit sparks, Focus, slashes. */
	public static void onArrows(UryuArrowsS2C p) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		float[] d = p.data();
		if (p.kind() == UryuArrowsS2C.FOCUS) {
			focus = p.ids().length > 0 ? Math.max(0, Math.min(3, p.ids()[0])) : 0;
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
					UryuArrowRenderer.impact(d[0], d[1], d[2], Math.round(d[3]), Math.round(d[4]));
				}
			}
			case UryuArrowsS2C.BURST -> {
				if (d.length >= 5) {
					burst(w, d[0], d[1], d[2], d[3], d[4] > 0.5f);
				}
			}
			case UryuArrowsS2C.LINE -> {
				if (d.length >= 7) {
					line(new Vec3d(d[0], d[1], d[2]), new Vec3d(d[3], d[4], d[5]), d[6]);
				}
			}
			case UryuArrowsS2C.TRAP -> {
				if (d.length >= 5) {
					trap(d[0], d[1], d[2], d[3], d[4] / 20.0);
				}
			}
			case UryuArrowsS2C.SLASH -> {
				if (d.length >= 9) {
					slash(d);
				}
			}
			default -> { }
		}
	}

	private static void line(Vec3d a, Vec3d b, double halfWidth) {
		Vec3d d = b.subtract(a);
		double len = d.length();
		if (len < 0.5) {
			return;
		}
		Vec3d u = d.multiply(1.0 / len);
		double life = 0.45;
		FxShapes.Strip glow = FxShapes.strip(a.x, a.y, a.z, u.x, u.y, u.z, 0).length(x -> len).width(x -> halfWidth * 2.0 * Math.max(0.0, 1.0 - x / life));
		glow.color("#7FD0FF").life(life).intensity(x -> 0.7 * Math.max(0.0, 1.0 - x / life));
		FxShapes.Strip core = FxShapes.strip(a.x, a.y, a.z, u.x, u.y, u.z, 0).length(x -> len).width(x -> 0.25);
		core.color("#FFFFFF").life(life).intensity(x -> Math.max(0.0, 1.0 - x / life));
	}

	// ------------------------------------------------------------------ Sprenger

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

	/** Five Seele Schneider drop into the vertices, the pentagram lights up and speeds up until the explosion. */
	private static void trap(double cx, double cy, double cz, double r, double seconds) {
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
	private static void burst(ClientWorld w, double x, double y, double z, double r, boolean early) {
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
		float[] tint = early ? WHITE : PALE;
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(x, y + 0.8, z).size(r * 2.4, r * 3.2).life(0.4).color(tint).peak(1.0).spawn();
		FxGlowBatch.sprite(GlowSprite.STAR6).at(x, y + 1.0, z).size(r * 2.0, r * 0.4).life(0.25).color("#FFFFFF").peak(1.0).rot(0.3, 1.5).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(x, y + 0.1, z).ground().size(r * 0.8, r * 4.4).life(0.45).color(GLOW).peak(1.0).spawn();
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

	// ------------------------------------------------------------------ Seele Schneider slashes

	private static final class Slash {
		double ox;
		double oy;
		double oz;
		Vec3d look;
		Vec3d plane;
		double born;
		double reach;
		double halfCone;
		int index;
		UryuSeeleRenderer.Seele blade;
	}

	private static final List<Slash> SLASHES = new ArrayList<>();
	private static final double SLASH_TIME = 0.16;

	/** One slash of the combo: a big Reishi blade sweeps the 100 degree cone in 0.16 s, a crescent stays behind. */
	private static void slash(float[] d) {
		Slash s = new Slash();
		s.ox = d[0];
		s.oy = d[1];
		s.oz = d[2];
		s.look = new Vec3d(d[3], d[4], d[5]).normalize();
		s.index = Math.round(d[6]);
		s.reach = d[7];
		s.halfCone = Math.toRadians(d[8] / 2.0);
		s.born = FxClock.now;
		// the plane of the sweep, perpendicular to the look: 0 and 1 a slightly tilted horizontal sweep (right to left, then back), 2 an overhead vertical chop
		Vec3d up = Math.abs(s.look.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
		Vec3d right = s.look.crossProduct(up).normalize();
		Vec3d top = right.crossProduct(s.look).normalize();
		double tilt = Math.toRadians(s.index == 0 ? 18 : s.index == 1 ? -18 : 90);
		double sign = s.index == 1 ? -1 : 1;
		// in-plane axis: rotate "right" about the look by the tilt; sweep runs from +axis to -axis (sign flips the direction)
		s.plane = right.multiply(Math.cos(tilt)).add(top.multiply(Math.sin(tilt))).multiply(sign);
		UryuSeeleRenderer.Seele blade = new UryuSeeleRenderer.Seele();
		blade.scale = 8.5f;
		blade.born = s.born;
		blade.life = SLASH_TIME + 0.1;
		blade.color = 0xFFFFFFFF;
		s.blade = UryuSeeleRenderer.add(blade);
		SLASHES.add(s);
		FxGlowBatch.sprite(GlowSprite.STAR4).at(s.ox + s.look.x * 0.6, s.oy + s.look.y * 0.6 - 0.2, s.oz + s.look.z * 0.6).size(1.2, 0.2).life(0.15).color("#FFFFFF").peak(0.9).spawn();
	}

	private static Vec3d sweepDir(Slash s, double u) {
		double ang = (1.0 - 2.0 * u) * s.halfCone; // from +cone to -cone
		return s.look.multiply(Math.cos(ang)).add(s.plane.multiply(Math.sin(ang))).normalize();
	}

	// ------------------------------------------------------------------ wings

	private static final class Wing {
		int caster;
		double born;
		double life;
	}

	private static final List<Wing> WINGS = new ArrayList<>();

	/** The bankai of a player ended (any reason): his wings go with it. */
	public static void clearWings(int casterId) {
		WINGS.removeIf(w -> w.caster == casterId);
	}

	// ------------------------------------------------------------------ shunpo

	/** The Uryu shunpo: a blue Reishi trail along the path and a Reishi platform disc under the feet at the start and the arrival. */
	public static void shunpoReishi(Vec3d start, Vec3d delta) {
		init();
		double len = delta.length();
		if (len > 0.5) {
			Vec3d u = delta.multiply(1.0 / len);
			double life = 0.4;
			for (int i = 0; i < 3; i++) {
				double y = start.y + 0.3 + i * 0.6;
				FxShapes.Strip s = FxShapes.strip(start.x, y, start.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> 0.34 * Math.max(0.0, 1.0 - a / life));
				s.color("#7FD0FF").life(life).intensity(a -> 0.9 * Math.max(0.0, 1.0 - a / life));
			}
			FxShapes.Strip core = FxShapes.strip(start.x, start.y + 0.9, start.z, u.x, u.y, u.z, 0).length(a -> len).width(a -> 0.12);
			core.color("#FFFFFF").life(life).intensity(a -> Math.max(0.0, 1.0 - a / life));
		}
		disc(start.x, start.y, start.z);
		disc(start.x + delta.x, start.y + delta.y, start.z + delta.z);
	}

	/** A Reishi platform: a pale blue disc with a thin rim and a soft glow, fading over 0.7 s. */
	private static void disc(double x, double y, double z) {
		FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(x, y + 0.04, z).ground().size(2.6, 3.0).life(0.7).color(GLOW).peak(0.55).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(x, y + 0.06, z).ground().size(1.6, 2.6).life(0.7).color(PALE).peak(0.9).rot(0.3, 1.2).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(x, y + 0.05, z).ground().size(1.0, 2.2).life(0.5).color("#FFFFFF").peak(0.5).spawn();
	}

	// ------------------------------------------------------------------ per frame

	private static void frame() {
		double now = FxClock.now;
		SLASHES.removeIf(s -> now - s.born > SLASH_TIME + 0.25);
		for (Slash s : SLASHES) {
			double u = Math.min(1.0, (now - s.born) / SLASH_TIME);
			double ease = u * u * (3 - 2 * u);
			Vec3d dir = sweepDir(s, ease);
			UryuSeeleRenderer.Seele b = s.blade;
			// the hilt sits in front of the chest, the blade runs outwards along the sweep direction
			b.x = s.ox + dir.x * 0.35;
			b.y = s.oy + dir.y * 0.35 - 0.35;
			b.z = s.oz + dir.z * 0.35;
			b.dx = dir.x;
			b.dy = dir.y;
			b.dz = dir.z;
		}
		PENTAGONS.removeIf(p -> now - p.born > p.life + 0.6);
	}

	private static void emitGlow(FxGlowBatch.Emitter e) {
		double now = FxClock.now;
		// pentagrams
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
			e.ground(GlowSprite.RING_THIN, p.cx, y, p.cz, p.r * 2.25, p.r * 2.25, age * 0.2, GLOW[0], GLOW[1], GLOW[2], 0.9 * inten);
			e.ground(GlowSprite.RING_SOFT, p.cx, y, p.cz, p.r * 2.6 * (1.0 - 0.05 * k), p.r * 2.6 * (1.0 - 0.05 * k), 0, PALE[0], PALE[1], PALE[2], 0.35 * inten);
			// the star: every vertex to the second next one
			for (int i = 0; i < 5; i++) {
				int j = (i + 2) % 5;
				e.streak(GlowSprite.STREAK, p.px[i], y, p.pz[i], p.px[j], y, p.pz[j], 0.18 + 0.1 * k, GLOW[0], GLOW[1], GLOW[2], inten);
				e.streak(GlowSprite.STREAK, p.px[i], y + 0.01, p.pz[i], p.px[j], y + 0.01, p.pz[j], 0.07, 1f, 1f, 1f, 0.8 * inten);
			}
			// light pillars at the daggers and a glow on each hilt
			for (int i = 0; i < 5; i++) {
				e.streak(GlowSprite.STREAK, p.px[i], y + 2.2, p.pz[i], p.px[i], y, p.pz[i], 0.28, GLOW[0], GLOW[1], GLOW[2], 0.6 * inten);
				e.sprite(GlowSprite.GLOW_SOFT, p.px[i], y + 0.9, p.pz[i], 0.9, 0, PALE[0], PALE[1], PALE[2], 0.7 * inten);
			}
		}
		// slashes: the crescent behind the blade and a glow along it
		for (Slash s : SLASHES) {
			double age = now - s.born;
			if (age < 0) {
				continue;
			}
			double u = Math.min(1.0, age / SLASH_TIME);
			double ease = u * u * (3 - 2 * u);
			double fade = Math.max(0.0, 1.0 - Math.max(0.0, age - SLASH_TIME) / 0.25);
			Vec3d prevP = null;
			int steps = 12;
			for (int i = 0; i <= steps; i++) {
				double t = ease * i / steps;
				Vec3d dir = sweepDir(s, t);
				Vec3d pt = new Vec3d(s.ox + dir.x * s.reach * 0.85, s.oy - 0.35 + dir.y * s.reach * 0.85, s.oz + dir.z * s.reach * 0.85);
				if (prevP != null) {
					double f = (double) i / steps;
					e.streak(GlowSprite.STREAK, pt.x, pt.y, pt.z, prevP.x, prevP.y, prevP.z, 0.45 * f + 0.05, GLOW[0], GLOW[1], GLOW[2], fade * f);
					e.streak(GlowSprite.STREAK, pt.x, pt.y, pt.z, prevP.x, prevP.y, prevP.z, 0.14 * f, 1f, 1f, 1f, fade * f);
				}
				prevP = pt;
			}
			Vec3d dir = sweepDir(s, ease);
			Vec3d tip = new Vec3d(s.ox + dir.x * s.reach, s.oy - 0.35 + dir.y * s.reach, s.oz + dir.z * s.reach);
			Vec3d hilt = new Vec3d(s.ox + dir.x * 0.35, s.oy - 0.35 + dir.y * 0.35, s.oz + dir.z * 0.35);
			e.streak(GlowSprite.STREAK, tip.x, tip.y, tip.z, hilt.x, hilt.y, hilt.z, 0.5, GLOW[0], GLOW[1], GLOW[2], 0.8 * fade);
			e.sprite(GlowSprite.GLOW_SOFT, tip.x, tip.y, tip.z, 1.1, 0, WHITE[0], WHITE[1], WHITE[2], 0.7 * fade);
		}
		// wings
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		WINGS.removeIf(w -> now - w.born > w.life + 0.4);
		float td = mc.getRenderTickCounter().getTickDelta(false);
		for (Wing w : WINGS) {
			Entity en = mc.world.getEntityById(w.caster);
			if (!(en instanceof LivingEntity le) || !le.isAlive()) {
				continue;
			}
			wing(e, w, le, td, now);
		}
	}

	/** One pair of wings: two sharp swept spars per side, a fan of feathers between them and the trailing edge. */
	private static void wing(FxGlowBatch.Emitter e, Wing w, LivingEntity le, float td, double now) {
		double age = now - w.born;
		double open = Math.min(1.0, age / 0.35);
		double flare = FxMath.ob(open); // easeOutBack: the wings snap open
		double endFade = age > w.life ? Math.max(0.0, 1.0 - (age - w.life) / 0.4) : 1.0;
		double cyc = (age % (UryuBalance.WING_CYCLE / 20.0)) / (UryuBalance.WING_CYCLE / 20.0);
		double volley = Math.exp(-cyc * 7.0); // a pulse with every volley
		double inten = endFade * (0.65 + 0.5 * volley);
		double yaw = le.prevBodyYaw + (le.bodyYaw - le.prevBodyYaw) * td;
		double fx = le.prevX + (le.getX() - le.prevX) * td;
		double fy = le.prevY + (le.getY() - le.prevY) * td;
		double fz = le.prevZ + (le.getZ() - le.prevZ) * td;
		V3 feet = new V3(fx, fy, fz);
		double grow = (0.35 + 0.65 * flare) * (1.0 + 0.07 * volley);
		for (int side = 0; side < 2; side++) {
			V3 root = shoulder(side, yaw, feet);
			V3 up = scaledTip(WingBarrage.tip(side, yaw, feet), root, grow);
			V3 low = scaledTip(WingBarrage.tip(2 + side, yaw, feet), root, grow);
			// the spars
			e.streak(GlowSprite.STREAK, up.x(), up.y(), up.z(), root.x(), root.y(), root.z(), 0.2, GLOW[0], GLOW[1], GLOW[2], inten);
			e.streak(GlowSprite.STREAK, low.x(), low.y(), low.z(), root.x(), root.y(), root.z(), 0.2, GLOW[0], GLOW[1], GLOW[2], inten);
			e.streak(GlowSprite.STREAK, up.x(), up.y(), up.z(), root.x(), root.y(), root.z(), 0.07, 1f, 1f, 1f, 0.8 * inten);
			// the feathers: a fan from the root between the two tips, longer in the middle, angular trailing edge
			V3 prev = up;
			int n = 5;
			for (int i = 1; i <= n; i++) {
				double t = (double) i / n;
				V3 mid = up.lerp(low, t);
				double extend = 1.0 + 0.18 * Math.sin(Math.PI * t); // the middle feathers are the longest: a pointed outline
				V3 tip = new V3(root.x() + (mid.x() - root.x()) * extend, root.y() + (mid.y() - root.y()) * extend, root.z() + (mid.z() - root.z()) * extend);
				e.streak(GlowSprite.STREAK, tip.x(), tip.y(), tip.z(), root.x(), root.y(), root.z(), 0.13, PALE[0], PALE[1], PALE[2], 0.7 * inten);
				e.streak(GlowSprite.STREAK, tip.x(), tip.y(), tip.z(), prev.x(), prev.y(), prev.z(), 0.09, GLOW[0], GLOW[1], GLOW[2], 0.8 * inten);
				e.sprite(GlowSprite.GLOW_SOFT, tip.x(), tip.y(), tip.z(), 0.35 + 0.35 * volley, 0, WHITE[0], WHITE[1], WHITE[2], 0.5 * inten);
				prev = tip;
			}
			e.sprite(GlowSprite.GLOW_SOFT, up.x(), up.y(), up.z(), 0.9 + 1.0 * volley, 0, GLOW[0], GLOW[1], GLOW[2], 0.8 * inten);
			e.sprite(GlowSprite.STAR4, up.x(), up.y(), up.z(), 0.5 + 0.9 * volley, age * 1.5, 1f, 1f, 1f, 0.9 * inten * (0.4 + 0.6 * volley));
		}
	}

	/** Where a wing grows from, on the back at shoulder height, one per side (0 right, 1 left). */
	private static V3 shoulder(int side, double bodyYawDeg, V3 feet) {
		double yaw = Math.toRadians(bodyYawDeg);
		double rx = -Math.cos(yaw) * 0.18 * (side == 0 ? 1 : -1);
		double rz = -Math.sin(yaw) * 0.18 * (side == 0 ? 1 : -1);
		double bx = Math.sin(yaw) * 0.22; // a little behind the spine
		double bz = -Math.cos(yaw) * 0.22;
		return new V3(feet.x() + rx + bx, feet.y() + 1.35, feet.z() + rz + bz);
	}

	private static V3 scaledTip(V3 tip, V3 root, double k) {
		return new V3(root.x() + (tip.x() - root.x()) * k, root.y() + (tip.y() - root.y()) * k, root.z() + (tip.z() - root.z()) * k);
	}
}
