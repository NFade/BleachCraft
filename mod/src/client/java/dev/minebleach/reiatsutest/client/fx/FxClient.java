package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.registry.ModEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.profiler.Profiler;

/** Client FX initialisation and the per frame hooks (profiler section {@code reiatsu_fx}). */
public final class FxClient {
	/** Rolling statistics for the harness: CPU milliseconds spent in our world pass per frame. */
	public static final class Stats {
		public static double frameMs;
		public static double sumMs;
		public static double maxMs;
		public static long frames;

		public static void reset() {
			sumMs = 0;
			maxMs = 0;
			frames = 0;
		}

		public static double meanMs() {
			return frames == 0 ? 0 : sumMs / frames;
		}
	}

	private static long startNanos;

	private FxClient() {
	}

	/** Dev: treat the local caster as a remote one ({@code -Dreiatsu.fx.remote=true}): distance rules, no title card. */
	public static boolean forceRemote() {
		return Boolean.getBoolean("reiatsu.fx.remote");
	}

	public static void init() {
		FxConfig.load();
		FxParticles.register();
		EntityRendererRegistry.register(ModEntities.FX_ANCHOR, FxAnchorRenderer::new);
		WorldRenderEvents.START.register(ctx -> {
			Profiler p = MinecraftClient.getInstance().getProfiler();
			p.push("reiatsu_fx");
			long t0 = System.nanoTime();
			FxTune.poll();
			FxTimelines.beginFrame();
			MinecraftClient mcf = MinecraftClient.getInstance();
			FxScene.frame(mcf);
			SwarmFx.frame(mcf);
			RowsFx.frame(mcf);
			startNanos = System.nanoTime() - t0;
			p.pop();
		});
		PetalSwarmRenderer.init();
		BankaiBladeRenderer.init();
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ctx -> FxDepth.capture());
		WorldRenderEvents.LAST.register(ctx -> {
			Profiler p = MinecraftClient.getInstance().getProfiler();
			p.push("reiatsu_fx");
			long t0 = System.nanoTime();
			FxProbe.run("LAST-before");
			ScreenFx.renderGrade(ctx);
			FxDepth.restore();
			FxProbe.run("LAST-after-restore");
			FxGlowBatch.draw(ctx);
			double ms = (startNanos + System.nanoTime() - t0) / 1.0e6;
			Stats.frameMs = ms;
			Stats.sumMs += ms;
			Stats.frames++;
			if (ms > Stats.maxMs) {
				Stats.maxMs = ms;
			}
			p.pop();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			AuraFx.tick(client);
			SwarmFx.tick(client);
		});
		DrawFx.init();
	}
}
