package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.client.model.FpMotion;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Client feedback of a block (effect id 41, BC5b), for the player who blocked: the first person sword recoil ({@link FpMotion}),
 * a small FOV kick, a short shake and a few sparks in front of the face on a parry; a shorter shake on a blocked hit. Sound and the
 * world sparks come from the server ({@code BlockParry}). Other players ignore the event.
 *
 * <p>Tuning ({@code fx_override.json}, {@link FxTune}): {@code parry.fov} (degrees, 1.0), {@code parry.fovTime} (0.18 s),
 * {@code parry.shake} (degrees, 0.5), {@code parry.shakeTime} (0.12 s), {@code parry.flash} (screen flash peak, 0.10, 0 = off),
 * {@code parry.sparks} (count, 10, 0 = off), {@code blockhit.shake} (0.3), {@code blockhit.shakeTime} (0.15). Reduce motion and
 * photosensitive safe switch the FOV kick and the shake off ({@link ScreenFx}) and cap the flash.
 */
public final class BlockFx {
	private static final float[] FLASH_TINT = FxMath.hex("#DDF3FF");

	private BlockFx() {
	}

	public static void play(MinecraftClient client, EffectEventS2C e) {
		ClientPlayerEntity me = client.player;
		if (me == null || e.casterId() != me.getId()) {
			return;
		}
		boolean parried = e.params().length > 0 && e.params()[0] > 0.5f;
		float side = sideOf(me.getYaw(), e.dx(), e.dz());
		if (parried) {
			FpMotion.onParry(side);
			ScreenFx.shunpoRush(0.0, FxTune.d("parry.fov", 1.0), FxTune.d("parry.fovTime", 0.18));
			ScreenFx.shake(FxTune.d("parry.shake", 0.5), FxTune.d("parry.shakeTime", 0.12), 1.0);
			ScreenFx.flash(FxTune.d("parry.flash", 0.10), FLASH_TINT, true, 1.0);
			sparks(client, me, (int) Math.round(FxTune.d("parry.sparks", 10)));
		} else {
			FpMotion.onBlockedHit(side);
			ScreenFx.shake(FxTune.d("blockhit.shake", 0.3), FxTune.d("blockhit.shakeTime", 0.15), 1.0);
		}
	}

	/** +1 when the attacker (horizontal vector dx, dz from the player) is on the right of the view direction, -1 on the left, 0 ahead or behind. */
	static float sideOf(float yawDeg, float dx, float dz) {
		double len = Math.sqrt((double) dx * dx + (double) dz * dz);
		if (len < 1e-4) {
			return 0f;
		}
		double yaw = Math.toRadians(yawDeg);
		// yaw 0 looks along +z; the right hand side is -x there
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		return (float) MathHelper.clamp((dx * rx + dz * rz) / len, -1.0, 1.0);
	}

	private static void sparks(MinecraftClient client, ClientPlayerEntity me, int n) {
		if (n <= 0 || client.world == null) {
			return;
		}
		Vec3d eye = me.getEyePos();
		Vec3d look = me.getRotationVec(1.0F);
		Vec3d at = eye.add(look.multiply(0.85)).add(0.0, -0.1, 0.0);
		var rnd = client.world.random;
		for (int i = 0; i < n; i++) {
			double vx = (rnd.nextDouble() - 0.5) * 0.5;
			double vy = (rnd.nextDouble() - 0.3) * 0.4;
			double vz = (rnd.nextDouble() - 0.5) * 0.5;
			client.world.addParticle(i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK, at.x + (rnd.nextDouble() - 0.5) * 0.3,
					at.y + (rnd.nextDouble() - 0.5) * 0.3, at.z + (rnd.nextDouble() - 0.5) * 0.3, vx, vy, vz);
		}
	}
}
