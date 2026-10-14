package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Seele Schneider combo (B, SHIKAI, effect 74, URYU_VFX_STORYBOARD 2.9; U-FX 5 fills it). Today: the lunge (three streaks along the run
 * and END_ROD sparks), and per slash packet a big Reishi blade built from the {@code uryu_seele} mesh that sweeps the 100 degree cone in
 * 0.16 s with a glow crescent behind it. Called by {@link UryuFx}.
 */
final class UryuSeeleFx {
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

	private UryuSeeleFx() {
	}

	static void init() {
		// nothing yet
	}

	/** Lunge: three streaks along the 1.5 block run (hip, chest, head) and a dust of Reishi. */
	static void lunge(ClientWorld w, EffectEventS2C e) {
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

	/** One slash of the combo: a big Reishi blade sweeps the 100 degree cone in 0.16 s, a crescent stays behind. */
	static void slash(float[] d) {
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

	/** Per frame (render START): old slashes go, the blade of every live slash follows the sweep. */
	static void frame(double now) {
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
	}

	/** The crescent behind the blade and a glow along it. */
	static void emitGlow(FxGlowBatch.Emitter e, double now) {
		float[] glow = UryuFx.GLOW;
		float[] white = UryuFx.WHITE;
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
					e.streak(GlowSprite.STREAK, pt.x, pt.y, pt.z, prevP.x, prevP.y, prevP.z, 0.45 * f + 0.05, glow[0], glow[1], glow[2], fade * f);
					e.streak(GlowSprite.STREAK, pt.x, pt.y, pt.z, prevP.x, prevP.y, prevP.z, 0.14 * f, 1f, 1f, 1f, fade * f);
				}
				prevP = pt;
			}
			Vec3d dir = sweepDir(s, ease);
			Vec3d tip = new Vec3d(s.ox + dir.x * s.reach, s.oy - 0.35 + dir.y * s.reach, s.oz + dir.z * s.reach);
			Vec3d hilt = new Vec3d(s.ox + dir.x * 0.35, s.oy - 0.35 + dir.y * 0.35, s.oz + dir.z * 0.35);
			e.streak(GlowSprite.STREAK, tip.x, tip.y, tip.z, hilt.x, hilt.y, hilt.z, 0.5, glow[0], glow[1], glow[2], 0.8 * fade);
			e.sprite(GlowSprite.GLOW_SOFT, tip.x, tip.y, tip.z, 1.1, 0, white[0], white[1], white[2], 0.7 * fade);
		}
	}
}
