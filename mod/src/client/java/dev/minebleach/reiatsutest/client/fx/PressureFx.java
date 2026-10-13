package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.PressureMath;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import net.minecraft.client.MinecraftClient;

/**
 * Reiatsu pressure (visual only): when somebody else releases a bankai within {@link PressureMath#FAR} blocks of the local player, the local
 * screen darkens at the edges and drains toward the caster's tint (Byakuya lilac white, Rukia pale blue), and heaves four times like a
 * heartbeat, as in the anime scenes of crushing spiritual pressure. Strength falls with the distance ({@link PressureMath#intensity}). It
 * uses the existing screen layers (vignette, grade, shake), so reduce motion and the photosensitivity options of {@link ScreenFx} apply.
 * Tunables in fx_override.json: {@code pressure.enabled} (1), {@code pressure.scale} (1.0), {@code pressure.vignette} (0.75), {@code pressure.grade} (0.30),
 * {@code pressure.shake} (0.35 degrees).
 */
public final class PressureFx {
	/** Pressures started since the start (harness statistics). */
	public static int played;

	private PressureFx() {
	}

	/** Called for the release event of a bankai (effect ids 2 and 4); never for the caster's own screen. */
	public static void onBankaiRelease(MinecraftClient mc, EffectEventS2C e, boolean byakuya) {
		try {
			if (mc.player == null || mc.player.getId() == e.casterId() || FxTune.d("pressure.enabled", 1.0) < 0.5) {
				return;
			}
			double dx = mc.player.getX() - e.x();
			double dy = mc.player.getY() - e.y();
			double dz = mc.player.getZ() - e.z();
			double k = PressureMath.intensity(Math.sqrt(dx * dx + dy * dy + dz * dz)) * FxTune.d("pressure.scale", 1.0);
			if (k < 0.03) {
				return;
			}
			ScreenFx.Layer vignette = ScreenFx.vignette(ScreenFx.Kind.DARK, FxTune.d("pressure.vignette", 0.75), PressureMath.RAMP, k).owner(e.casterId());
			ScreenFx.Layer grade = ScreenFx.grade(FxMath.hex(byakuya ? "#CDBBE6" : "#BCD6EE"), FxTune.d("pressure.grade", 0.30), PressureMath.RAMP, k).owner(e.casterId());
			FxDelayed.after(PressureMath.RAMP + PressureMath.HOLD, () -> {
				vignette.release(PressureMath.FADE);
				grade.release(PressureMath.FADE);
			});
			double amp = FxTune.d("pressure.shake", 0.35);
			for (double t : PressureMath.PULSES) {
				FxDelayed.after(t, () -> ScreenFx.shake(amp, 0.35, k));
			}
			played++;
			ReiatsuTest.LOGGER.info("[fx] reiatsu pressure from owner {} ({}), strength {}", e.casterId(), byakuya ? "Byakuya" : "Rukia", String.format(java.util.Locale.ROOT, "%.2f", k));
		} catch (RuntimeException ex) {
			ReiatsuTest.LOGGER.warn("[fx] reiatsu pressure failed: {}", ex.toString());
		}
	}
}
