package dev.minebleach.reiatsutest.client.fx;

import java.util.Random;
import net.minecraft.util.math.Vec3d;

/** Small spawn helpers shared by the Rukia effects: particle and sprite bursts, frames (forward / right) of an event. */
final class RukiaKit {
	private RukiaKit() {
	}

	/** Horizontal unit forward of an event (falls back to +z). */
	static double[] flat(Vec3d dir) {
		double l = Math.hypot(dir.x, dir.z);
		return l < 1e-4 ? new double[] {0, 1} : new double[] {dir.x / l, dir.z / l};
	}

	/** Right hand side of the forward direction (x, z). */
	static double[] right(double[] f) {
		return new double[] {-f[1], f[0]};
	}

	/** The blade tip of the caster's right hand pose at the time of the event: hand 0.35 right, 0.45 forward, 1.1 up, plus {@code reach} along the look. */
	static Vec3d tip(EffectTimeline t, double reach, double upExtra) {
		double[] f = flat(t.dir);
		double[] r = right(f);
		Vec3d hand = new Vec3d(t.pos.x + r[0] * 0.35 + f[0] * 0.45, t.pos.y + 1.1 + upExtra, t.pos.z + r[1] * 0.35 + f[1] * 0.45);
		Vec3d d = t.dir.lengthSquared() < 1e-6 ? new Vec3d(f[0], 0, f[1]) : t.dir.normalize();
		return hand.add(d.multiply(reach));
	}

	static void frostMotes(EffectTimeline t, Random r, int n, double cx, double cy, double cz, double spreadXZ, double spreadY, double up, double outward, int life,
			double size, String color, String colorTo, double alpha) {
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double rad = Math.sqrt(r.nextDouble()) * spreadXZ;
			FxParticles.Spec p = FxParticles.spec(FxParticles.Kind.FROST_MOTE)
					.at(cx + Math.cos(a) * rad, cy + (r.nextDouble() - 0.3) * spreadY, cz + Math.sin(a) * rad)
					.vel(Math.cos(a) * outward * r.nextDouble(), up * (0.7 + 0.6 * r.nextDouble()), Math.sin(a) * outward * r.nextDouble()).drag(0.96).life(life + r.nextInt(5))
					.size(size * (0.7 + 0.6 * r.nextDouble())).seed(r.nextInt(4)).color(color).alpha(alpha).fade(0.4).owner(t);
			if (colorTo != null) {
				p.colorTo(colorTo);
			}
			p.spawn();
		}
	}

	static void iceShards(EffectTimeline t, Random r, int n, double cx, double cy, double cz, double spreadXZ, double speed, double up, double gravity, int life, double size) {
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double rad = Math.sqrt(r.nextDouble()) * spreadXZ;
			double s = speed * (0.4 + 0.8 * r.nextDouble());
			FxParticles.spec(FxParticles.Kind.ICE_SHARD).at(cx + Math.cos(a) * rad, cy + r.nextDouble() * 0.6, cz + Math.sin(a) * rad)
					.vel(Math.cos(a) * s, up * (0.5 + r.nextDouble()), Math.sin(a) * s).drag(0.94).gravity(gravity).life(life + r.nextInt(8))
					.size(size * (0.7 + 0.7 * r.nextDouble())).seed(r.nextInt(4)).spin((r.nextDouble() - 0.5) * 0.5).fade(0.3).owner(t).spawn();
		}
	}

	static void stars(EffectTimeline t, Random r, GlowSprite sprite, int n, double cx, double cy, double cz, double spreadXZ, double spreadY, double up, double outward, double life,
			double size, String color, double peak) {
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double rad = Math.sqrt(r.nextDouble()) * spreadXZ;
			FxGlowBatch.sprite(sprite).at(cx + Math.cos(a) * rad, cy + (r.nextDouble() - 0.3) * spreadY, cz + Math.sin(a) * rad)
					.vel(Math.cos(a) * outward * r.nextDouble(), up * (0.5 + r.nextDouble()), Math.sin(a) * outward * r.nextDouble()).drag(0.96).life(life * (0.7 + 0.6 * r.nextDouble()))
					.size(size * (0.6 + 0.8 * r.nextDouble())).color(color).peak(peak).twinkle().rot(r.nextDouble() * 6.28, (r.nextDouble() - 0.5) * 3).owner(t).spawn();
		}
	}

	/** Ground ring (RING_SOFT draws its ring at 0.375 of the width, RING_THIN at 0.4375). */
	static void ring(EffectTimeline t, double x, double y, double z, double fromR, double toR, double life, String color, double peak, boolean thin) {
		double k = thin ? 0.4375 : 0.375;
		FxGlowBatch.sprite(thin ? GlowSprite.RING_THIN : GlowSprite.RING_SOFT).at(x, y, z).ground().size(Math.max(0.05, fromR) / k * 1.0, toR / k).sizeEase(FxMath.OC).life(life)
				.curve(0.02, 0.8).color(color).peak(peak).owner(t).spawn();
	}
}
