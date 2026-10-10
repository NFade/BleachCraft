package dev.minebleach.reiatsutest.client.fx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.profiler.Profiler;

/**
 * Wiring of the Rukia effects (phase 6 steps 6 and 7): world render hooks for the decals and meshes, client ticks for the
 * passive and the shell glints, the feature renderer, and the reset of every pool.
 */
public final class RukiaFx {
	private RukiaFx() {
	}

	/** Once from {@link FxClient#init()}. */
	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
			Profiler p = MinecraftClient.getInstance().getProfiler();
			p.push("reiatsu_fx");
			FxDecals.draw(ctx);
			FxMeshPass.draw(ctx);
			p.pop();
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			RukiaBankaiClient.tick(mc);
			RukiaEntityFx.tick(mc);
		});
		RukiaFeature.register();
	}

	/** Routing of an effect event of the Rukia abilities and the bankai release; false when the id is not one of ours. */
	public static boolean onEvent(dev.minebleach.reiatsutest.net.EffectEventS2C e) {
		int id = e.effectId();
		switch (id) {
			case 20, 21, 22 -> {
				RukiaShikaiFx.play(e);
				RukiaBankaiClient.flare(e.casterId());
			}
			case 23 -> RukiaBankaiFx.absoluteZero(e);
			case 2 -> RukiaBankaiFx.release(e);
			default -> {
				return false;
			}
		}
		return true;
	}

	public static void clearAll() {
		FxDecals.clear();
		FxMeshPass.clear();
		FxShapes.clear();
		FxGround.clear();
		FreezeDesat.clear();
		RukiaBankaiClient.clear();
		FxServerClock.reset();
	}
}
