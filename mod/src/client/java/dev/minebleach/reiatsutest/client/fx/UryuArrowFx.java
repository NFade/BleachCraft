package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.UryuArrowLook;
import dev.minebleach.reiatsutest.core.fx.UryuBowForm;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * The one shot effects of the Heilig Pfeil (URYU_VFX_STORYBOARD 2.3): the muzzle when an arrow first appears (star, ring along the flight,
 * dashes shooting forward, the thin shot, the bow flash and the owner's recoil), the flake sparks along the flight of a focused arrow and the
 * hit (star, burst rays, radial dashes, flakes, Focus 3 ring and flash, glass sounds, a scar on a floor). The numbers are in
 * {@link UryuArrowLook} (unit tested); {@link UryuArrowRenderer} owns the arrows and calls this. Reduce Motion: no recoil (the shake refuses),
 * the flash stays far below 0.4. UNVERIFIED in game (harness {@code uryu_arrow}, shots p7_02_arrow_* and p7_02_hit_*).
 */
final class UryuArrowFx {
	/** Harness: muzzles and hits that were played. */
	static int muzzles;
	static int hits;
	static int sparks;

	private UryuArrowFx() {
	}

	/** The nearest player who holds the bow within {@link UryuArrowLook#OWN_ARROW_DISTANCE} of the first arrow position, or null. */
	static PlayerEntity shooterNear(double x, double y, double z) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w == null) {
			return null;
		}
		PlayerEntity best = null;
		double bd = UryuArrowLook.OWN_ARROW_DISTANCE * UryuArrowLook.OWN_ARROW_DISTANCE;
		for (PlayerEntity p : w.getPlayers()) {
			if (!UryuAnchors.holdsBow(p)) {
				continue;
			}
			double d = p.squaredDistanceTo(x, y, z);
			if (d <= bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** The muzzle at the first sighting of an arrow of kind BASIC or Z at {@code (x, y, z)} flying along {@code u} (unit). */
	static void muzzle(double x, double y, double z, Vec3d u, int kind, double p, PlayerEntity shooter, boolean own) {
		if (!UryuArrowLook.hasMuzzle(kind)) {
			return;
		}
		muzzles++;
		double sx = x - u.x * 0.3;
		double sy = y - u.y * 0.3;
		double sz = z - u.z * 0.3;
		double star = UryuArrowLook.muzzleStarSize(kind, p);
		FxGlowBatch.sprite(GlowSprite.STAR4).at(sx, sy, sz).size(star * 0.5, star).sizeEase(FxMath.OE).life(UryuArrowLook.MUZZLE_STAR_LIFE).curve(0.005, 0.7).color(UryuPalette.Q0)
				.peak(1.0).rot(Math.random() * 3.0, 1.5).spawn();
		FxGlowBatch.sprite(GlowSprite.RING_THIN).at(sx + u.x * 0.2, sy + u.y * 0.2, sz + u.z * 0.2).plane(u.x, u.y, u.z)
				.size(UryuArrowLook.MUZZLE_RING_FROM, UryuArrowLook.muzzleRingSize(kind)).sizeEase(FxMath.OC).life(UryuArrowLook.MUZZLE_RING_LIFE).curve(0.01, 0.7)
				.color(UryuPalette.Q1).peak(0.8).spawn();
		Random r = new Random(Double.doubleToLongBits(x * 31 + z));
		for (int i = 0; i < UryuArrowLook.MUZZLE_DASHES; i++) {
			double sp = UryuArrowLook.MUZZLE_DASH_SPEED * (0.8 + 0.4 * r.nextDouble());
			double jx = (r.nextDouble() - 0.5) * 0.18;
			double jy = (r.nextDouble() - 0.5) * 0.18;
			double jz = (r.nextDouble() - 0.5) * 0.18;
			FxGlowBatch.sprite(GlowSprite.DASH).at(sx, sy, sz).vel(u.x * sp + jx * sp, u.y * sp + jy * sp, u.z * sp + jz * sp).streak().streakTime(0.05).size(0.35)
					.life(UryuArrowLook.MUZZLE_DASH_LIFE).curve(0.005, 0.6).color(i % 2 == 0 ? UryuPalette.Q1 : UryuPalette.Q2).peak(0.9).spawn();
		}
		double vol = own || shooter == null ? 1.0 : UryuBowForm.REMOTE_VOLUME;
		for (UryuBowForm.Cue c : UryuArrowLook.muzzleCues(p)) {
			FxSound.play(null, c.id(), x, y, z, c.pitch(), c.volume() * vol);
		}
		if (shooter != null) {
			UryuBowFx.flash(shooter.getId());
		}
		if (own) {
			ScreenFx.shake(dev.minebleach.reiatsutest.core.fx.UryuDrawLight.RECOIL_DEG, dev.minebleach.reiatsutest.core.fx.UryuDrawLight.RECOIL_TIME, 1.0);
		}
	}

	/** Flake sparks of a focused arrow: one flake particle at {@code (x, y, z)} that drifts a little and tumbles for 8 ticks. */
	static void spark(double x, double y, double z, Random r) {
		sparks++;
		FxParticles.spec(FxParticles.Kind.FLECHETTE).at(x, y, z).vel((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6)
				.life(UryuArrowLook.FLAKE_LIFE_TICKS).size(UryuArrowLook.FLAKE_SIZE).color(UryuPalette.Q2).spin((r.nextDouble() - 0.5) * 0.6).seed(r.nextInt(8)).spawn();
	}

	/**
	 * The hit. {@code level} = the Focus level the hit is drawn at ({@link UryuArrowLook#hitLevel}), {@code type} entity or block,
	 * {@code floor} true when a block hit struck a floor (the scar lies on it), {@code own} when the local player shot the arrow.
	 */
	static void hit(double x, double y, double z, int type, int level, boolean floor, boolean own) {
		hits++;
		boolean entity = type == UryuArrowsS2C.IMPACT_ENTITY;
		Random r = new Random(Double.doubleToLongBits(x + y * 7 + z * 13));
		double star = entity ? UryuArrowLook.hitStarSize(level) : UryuArrowLook.BLOCK_STAR;
		FxGlowBatch.sprite(GlowSprite.STAR4).at(x, y, z).size(star * 0.4, star).sizeEase(FxMath.OE).life(UryuArrowLook.HIT_STAR_LIFE).curve(0.005, 0.7).color(UryuPalette.Q0)
				.peak(1.0).rot(r.nextDouble() * 3, 1.2).spawn();
		if (entity) {
			double rays = UryuArrowLook.hitRaysSize(level);
			FxGlowBatch.sprite(GlowSprite.BURST_RAYS).at(x, y, z).size(rays * 0.5, rays).sizeEase(FxMath.OE).life(UryuArrowLook.HIT_RAYS_LIFE).curve(0.005, 0.7)
					.color(UryuPalette.Q1).peak(0.9).rot(r.nextDouble() * Math.PI * 2, 0.0).spawn();
			FxGlowBatch.sprite(GlowSprite.GLOW_SOFT).at(x, y, z).size(1.6 + 0.4 * level).life(0.22).curve(0.01, 0.8).color(UryuPalette.Q3).peak(0.55).spawn();
		}
		int dashes = entity ? UryuArrowLook.hitDashes(level) : UryuArrowLook.BLOCK_DASHES;
		for (int i = 0; i < dashes; i++) {
			double[] d = UryuBowForm.gatherDirection(r.nextInt(64), 64);
			double sp = UryuArrowLook.HIT_DASH_SPEED * (0.7 + 0.5 * r.nextDouble());
			FxGlowBatch.sprite(GlowSprite.DASH).at(x, y, z).vel(d[0] * sp, d[1] * sp, d[2] * sp).streak().streakTime(0.05).size(0.4).life(UryuArrowLook.HIT_DASH_LIFE)
					.curve(0.005, 0.6).color(i % 3 == 0 ? UryuPalette.Q0 : UryuPalette.Q2).peak(0.9).spawn();
		}
		int flakes = entity ? UryuArrowLook.HIT_FLAKES : UryuArrowLook.BLOCK_FLAKES;
		for (int i = 0; i < flakes; i++) {
			spark(x, y, z, r);
		}
		if (entity && UryuArrowLook.hitRing(level)) {
			FxGlowBatch.Spec ring = FxGlowBatch.sprite(GlowSprite.RING_SOFT).at(x, y + (floor ? 0.06 : 0), z).size(UryuArrowLook.HIT_RING_FROM, UryuArrowLook.HIT_RING_TO)
					.sizeEase(FxMath.OC).life(UryuArrowLook.HIT_RING_LIFE).curve(0.01, 0.75).color(UryuPalette.Q2).peak(0.7);
			if (floor) {
				ring.ground();
			}
			ring.spawn();
			if (own) {
				ScreenFx.flash(UryuArrowLook.HIT_FLASH_PEAK, UryuPalette.FQ1, true, 1.0);
			}
		}
		if (!entity && floor && FxConfig.decals) {
			FxDecals.create(FxDecals.BURST_SCAR, x, z, y, UryuArrowLook.SCAR_RADIUS, 0.0).reveal(0.05).alpha(0.8).tint("#FFFFFF").fade(0.2, UryuArrowLook.SCAR_LIFE - 0.2);
		}
		for (UryuBowForm.Cue c : UryuArrowLook.hitCues(level, entity)) {
			FxSound.play(null, c.id(), x, y, z, c.pitch(), c.volume());
		}
	}
}
