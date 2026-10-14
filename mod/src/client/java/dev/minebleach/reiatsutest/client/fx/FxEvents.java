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
		// the visual reiatsu pressure on the screens of the OTHER players near a bankai release (the event goes on to its real effect)
		if (id == dev.minebleach.reiatsutest.core.state.EffectIds.BYAKUYA_BANKAI_RELEASE || id == dev.minebleach.reiatsutest.core.state.EffectIds.RUKIA_BANKAI_RELEASE) {
			PressureFx.onBankaiRelease(client, e, id == dev.minebleach.reiatsutest.core.state.EffectIds.BYAKUYA_BANKAI_RELEASE);
		}
		if (id == dev.minebleach.reiatsutest.core.state.EffectIds.BLOCK_FEEDBACK) {
			BlockFx.play(client, e);
			return;
		}
		if (id == dev.minebleach.reiatsutest.core.state.EffectIds.SHUNPO) {
			ShunpoFx.play(client, e);
			return;
		}
		if (id == dev.minebleach.reiatsutest.core.state.EffectIds.BANKAI_CLEANUP) {
			UryuFx.clearWings(e.casterId()); // Uryu: the wings of the wing barrage go with his bankai
			BankaiEnd.onEvent(e);
			return;
		}
		if (GinFx.handles(id)) {
			GinFx.play(client, e);
			return;
		}
		if (KidoFx.handles(id)) {
			KidoFx.play(client, e);
			return;
		}
		if (UryuFx.handles(id)) {
			UryuFx.play(client, e);
			return;
		}
		if (id == 30 || id == 31) {
			ReiatsuTest.LOGGER.info("[fx] effect_event id={} caster={} seed={}", id, e.casterId(), e.seed());
			handled++;
			if (id == 30) {
				SwarmFx.onAttack(e);
			} else {
				SwarmFx.onBarrier(e);
			}
			return;
		}
		if (id == 35) {
			ReiatsuTest.LOGGER.info("[fx] effect_event id={} caster={} seed={} params={}", id, e.casterId(), e.seed(), java.util.Arrays.toString(e.params()));
			handled++;
			CageFx.play(e);
			return;
		}
		if (id == 32 || id == 33) {
			ReiatsuTest.LOGGER.info("[fx] effect_event id={} caster={} seed={} params={}", id, e.casterId(), e.seed(), java.util.Arrays.toString(e.params()));
			handled++;
			if (id == 32) {
				StormFx.onEvent(e);
			} else {
				HakuteikenFx.onEvent(e);
			}
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
			if (id == 4) {
				// the quiet drop (6.1): no flash, ring or burst; the ripples, the night and the rows are the release
				// (r7: the petal swarm around the owner and the big blades are different effects, the swarm is NOT ended here any more)
				RowsFx.onRelease(e);
			} else if (id == 3 && FxTune.d("release.pinkTime", 0.4) > 0.01) {
				// plan A6: the blade in the hand turns pink for 0.4 s (ReleaseHold tints the item model), then the flash, ring and petals start
				double hold = FxTune.d("release.pinkTime", 0.4);
				ReleaseHold.start(e.casterId(), hold);
				ShikaiDissolve.start(e.casterId(), hold); // plan G-glow: the blade dissolves into petals after the pink hold
				FxSound.play(null, "block.amethyst_block.resonate", e.x(), e.y() + 1.1, e.z(), 1.7, 0.35);
				FxDelayed.after(hold, () -> ReleaseFx.play(e));
				// the swarm launches from the hand at the end of the hold; release.swarmDelay (0 by default) moves its start later into the
				// dissolve, so that the dissolve petals (ShikaiDissolve) are already in the air when the swarm comes (they blend into its orbit)
				double swarmDelay = Math.max(0, FxTune.d("release.swarmDelay", 0.0));
				if (swarmDelay <= 0.001) {
					FxDelayed.after(hold, () -> SwarmFx.onRelease(e));
				} else {
					FxDelayed.after(hold + swarmDelay, () -> {
						if (SwarmFx.of(e.casterId()) == null) {
							SwarmFx.onRelease(e);
						}
					});
				}
			} else if (id <= 4) {
				ReleaseFx.play(e);
				if (id == 3) {
					SwarmFx.onRelease(e);
					ShikaiDissolve.start(e.casterId(), 0);
				}
			} else {
				SealFx.play(e);
			}
			return;
		}
		EffectPlaceholders.play(client, e);
	}
}
