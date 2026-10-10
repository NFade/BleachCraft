package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import net.minecraft.client.MinecraftClient;

/**
 * Entry of every {@code effect_event} on the client. The release (1 to 4) and seal (10, 11) events have their real effects
 * (phase 6 step 2); the ability events (20 to 34) still use the phase 4 placeholder particles until their steps.
 */
public final class FxEvents {
	/** Statistics for the harness: events handled by the real effects. */
	public static int handled;

	private FxEvents() {
	}

	public static void onEffectEvent(MinecraftClient client, EffectEventS2C e) {
		int id = e.effectId();
		if (client.world == null) {
			return;
		}
		FxServerClock.sync(e.startTick());
		if (RukiaFx.onEvent(e)) {
			handled++;
			ReiatsuTest.LOGGER.info("[fx] effect_event id={} caster={} seed={} pos=({}, {}, {}) params={}", id, e.casterId(), e.seed(),
					String.format(java.util.Locale.ROOT, "%.2f", e.x()), String.format(java.util.Locale.ROOT, "%.2f", e.y()), String.format(java.util.Locale.ROOT, "%.2f", e.z()),
					java.util.Arrays.toString(e.params()));
			return;
		}
		if ((id >= 1 && id <= 4) || id == 10 || id == 11) {
			ReiatsuTest.LOGGER.info("[fx] effect_event id={} caster={} seed={} pos=({}, {}, {})", id, e.casterId(), e.seed(),
					String.format(java.util.Locale.ROOT, "%.2f", e.x()), String.format(java.util.Locale.ROOT, "%.2f", e.y()),
					String.format(java.util.Locale.ROOT, "%.2f", e.z()));
			handled++;
			if (id <= 4) {
				ReleaseFx.play(e);
			} else {
				SealFx.play(e);
			}
			return;
		}
		EffectPlaceholders.play(client, e);
	}
}
