package dev.minebleach.reiatsutest.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.fx.PressureMath;
import dev.minebleach.reiatsutest.core.fx.PressurePlan;
import dev.minebleach.reiatsutest.core.fx.PressureStripes;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

/**
 * Reiatsu pressure (visual only): when somebody else releases a bankai within {@link PressureMath#FAR} blocks of the local player, the local
 * screen darkens at the edges and drains toward the caster's tint (Byakuya lilac white, Rukia pale blue), heaves four times like a heartbeat,
 * and (R11 T7) shakes, blurs and is crossed by vertical interference stripes, as in the anime scenes of crushing spiritual pressure. Strength
 * falls with the distance ({@link PressureMath#strength}, with a floor at 20..30 blocks). What may run is decided by the pure
 * {@link PressurePlan} (reduce motion removes the shake and the stripes, photosensitive safe removes the stripes, the blur and the shake).
 *
 * <p>Layers: the vignette, the grade and the shake use {@link ScreenFx}; the blur and the horizontal tear are one post pass ({@link PressureBlur});
 * the stripes are drawn here under the HUD ({@link #renderStripes}, hooked by {@code InGameHudMixin}).
 *
 * <p>Tunables: {@code pressure.shake} (0.9 degrees on a pulse), {@code pressure.blur} (1.0), {@code pressure.stripes} (1.0), {@code pressure.scale}
 * (1.0) in fx_override.json (dev, hot reloaded) or in the {@code pressure} section of {@code config/reiatsu_test_client.json}; also
 * {@code pressure.enabled} (1), {@code pressure.vignette} (0.75), {@code pressure.grade} (0.30) in fx_override.json.
 *
 * <p>Preview without a second player: {@code /reiatsu_fx pressure [k] [byakuya|rukia]}, {@code /reiatsu_fx pressure dist <blocks> [owner]},
 * {@code /reiatsu_fx pressure off}.
 */
public final class PressureFx {
	/** Pressures started since the start (harness statistics). */
	public static int played;
	/** Entity id used for the layers of a preview (no caster). */
	private static final int PREVIEW_OWNER = Integer.MIN_VALUE + 7;
	private static final float[] DARK_STRIPE = {0.03f, 0.03f, 0.07f};
	private static final float[] LIGHT_STRIPE = {0.96f, 0.96f, 1.0f};

	/** One running pressure on this screen (the blur and the stripes follow its age). */
	private record Active(double start, long seed, float[] tint, PressurePlan plan) {
	}

	private static final List<Active> ACTIVE = new ArrayList<>();

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
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			start(e.casterId(), e.seed(), PressureMath.strength(dist), byakuya, String.format(Locale.ROOT, "%.1f blocks", dist));
		} catch (RuntimeException ex) {
			ReiatsuTest.LOGGER.warn("[fx] reiatsu pressure failed: {}", ex.toString());
		}
	}

	/** Starts one pressure of a given distance strength 0..1; returns the plan that was used (OFF when nothing runs). */
	static PressurePlan start(int ownerId, long seed, double distanceStrength, boolean byakuya, String where) {
		PressurePlan plan = PressurePlan.of(distanceStrength, FxTune.d("pressure.scale", FxConfig.pressureScale), FxConfig.screenFxScale,
				FxTune.d("pressure.shake", FxConfig.pressureShake), FxTune.d("pressure.blur", FxConfig.pressureBlur),
				FxTune.d("pressure.stripes", FxConfig.pressureStripes), FxConfig.reduceMotion, FxConfig.photosensitiveSafe);
		double k = plan.strength();
		if (k < 0.03) {
			return PressurePlan.OFF;
		}
		float[] tint = FxMath.hex(byakuya ? "#CDBBE6" : "#BCD6EE");
		ScreenFx.Layer vignette = ScreenFx.vignette(ScreenFx.Kind.DARK, FxTune.d("pressure.vignette", 0.75), PressureMath.RAMP, k).owner(ownerId);
		ScreenFx.Layer grade = ScreenFx.grade(tint, FxTune.d("pressure.grade", 0.30), PressureMath.RAMP, k).owner(ownerId);
		FxDelayed.after(PressureMath.RAMP + PressureMath.HOLD, () -> {
			vignette.release(PressureMath.FADE);
			grade.release(PressureMath.FADE);
		});
		if (plan.shakeDeg() > 0) {
			for (double t : PressureMath.PULSES) {
				FxDelayed.after(t, () -> ScreenFx.shake(plan.shakeDeg(), PressureMath.PULSE_SHAKE_SECONDS, k));
			}
		}
		if (plan.tremorDeg() > 0) {
			ScreenFx.tremor(plan.tremorDeg(), k, (seed & 255) * 0.1);
		}
		if (plan.blurScale() > 0 || plan.stripes() > 0) {
			ACTIVE.add(new Active(FxClock.now, seed, tint, plan));
		}
		played++;
		ReiatsuTest.LOGGER.info("[fx] reiatsu pressure from owner {} ({}, {}), strength {} shake {} tremor {} blur {} stripes {}", ownerId,
				byakuya ? "Byakuya" : "Rukia", where, String.format(Locale.ROOT, "%.2f", k), plan.shakeDeg() > 0, plan.tremorDeg() > 0,
				plan.blurScale() > 0 && !PressureBlur.failed(), plan.stripes() > 0);
		return plan;
	}

	private static void prune() {
		ACTIVE.removeIf(a -> FxClock.now - a.start >= PressureMath.totalTime());
	}

	/** Drops every running pressure (dev command "off"). */
	public static void stop() {
		ACTIVE.clear();
		ScreenFx.releaseOwner(PREVIEW_OWNER, 0.4);
	}

	// ------------------------------------------------------------------------------------------------ per frame values for the post pass

	/** Blur radius in pixels now (the strongest running pressure); 0 when there is none. */
	public static double blurPx() {
		prune();
		double best = 0;
		for (Active a : ACTIVE) {
			if (a.plan.blurScale() > 0) {
				best = Math.max(best, PressureMath.blurPx(FxClock.now - a.start, a.plan.strength()) * a.plan.blurScale());
			}
		}
		return best;
	}

	/** Horizontal tear in pixels now; 0 between the pulses and whenever the stripes are off. */
	public static double tearPx() {
		double best = 0;
		for (Active a : ACTIVE) {
			if (a.plan.tearScale() > 0) {
				best = Math.max(best, PressureMath.tearPx(FxClock.now - a.start, a.plan.strength()) * a.plan.tearScale());
			}
		}
		return best;
	}

	/** Phase of the tear bands: changes with the stripe slot (twelve times a second) and with the seed. */
	public static double tearPhase() {
		if (ACTIVE.isEmpty()) {
			return 0;
		}
		Active a = ACTIVE.get(ACTIVE.size() - 1);
		return (PressureStripes.slot(FxClock.now - a.start) * 0.37 + (a.seed & 1023) * 0.001) % 97.0;
	}

	// ------------------------------------------------------------------------------------------------ stripes

	/** Under the HUD (InGameHud#render HEAD): the vertical interference stripes of every running pressure that has them. */
	public static void renderStripes(DrawContext ctx) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		int fbWidth = mc.getWindow().getFramebufferWidth();
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		prune();
		List<Active> drawn = new ArrayList<>(2);
		for (int i = ACTIVE.size() - 1; i >= 0 && drawn.size() < 2; i--) {
			if (ACTIVE.get(i).plan.stripes() > 0) {
				drawn.add(ACTIVE.get(i));
			}
		}
		if (drawn.isEmpty()) {
			return;
		}
		List<PressureStripes.Stripe> all = new ArrayList<>();
		List<float[]> tints = new ArrayList<>();
		for (Active a : drawn) {
			for (PressureStripes.Stripe s : PressureStripes.layout(a.seed, FxClock.now - a.start, a.plan.stripes(), fbWidth)) {
				all.add(s);
				tints.add(a.tint);
			}
		}
		if (all.isEmpty()) {
			return;
		}
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (int i = 0; i < all.size(); i++) {
			PressureStripes.Stripe s = all.get(i);
			float[] c = s.tone() == PressureStripes.TONE_DARK ? DARK_STRIPE : s.tone() == PressureStripes.TONE_LIGHT ? LIGHT_STRIPE : tints.get(i);
			float a = (float) Math.min(1.0, s.alpha());
			float x0 = (float) (s.x0() * w);
			float x1 = (float) (s.x1() * w);
			float y0 = (float) (s.y0() * h);
			float y1 = (float) (s.y1() * h);
			bb.vertex(m, x0, y1, 0).color(c[0], c[1], c[2], a);
			bb.vertex(m, x1, y1, 0).color(c[0], c[1], c[2], a);
			bb.vertex(m, x1, y0, 0).color(c[0], c[1], c[2], a);
			bb.vertex(m, x0, y0, 0).color(c[0], c[1], c[2], a);
		}
		BufferRenderer.drawWithGlobalProgram(bb.end());
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}

	// ------------------------------------------------------------------------------------------------ dev preview command

	/** Registers {@code /reiatsu_fx pressure ...} (client side only, for previews without a second player). */
	public static void registerCommand() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> pressure = ClientCommandManager.literal("pressure")
					.executes(c -> preview(c, 1.0, null, true))
					.then(ClientCommandManager.literal("off").executes(c -> {
						stop();
						c.getSource().sendFeedback(Text.literal("[reiatsu_fx] pressure stopped"));
						return Command.SINGLE_SUCCESS;
					}))
					.then(ownerBranch("byakuya", true))
					.then(ownerBranch("rukia", false))
					.then(ClientCommandManager.literal("dist")
							.then(ClientCommandManager.argument("blocks", DoubleArgumentType.doubleArg(0.0, 500.0))
									.executes(c -> previewDist(c, true))
									.then(ClientCommandManager.literal("byakuya").executes(c -> previewDist(c, true)))
									.then(ClientCommandManager.literal("rukia").executes(c -> previewDist(c, false)))))
					.then(ClientCommandManager.argument("k", DoubleArgumentType.doubleArg(0.0, 1.0))
							.executes(c -> preview(c, DoubleArgumentType.getDouble(c, "k"), null, true))
							.then(ClientCommandManager.literal("byakuya").executes(c -> preview(c, DoubleArgumentType.getDouble(c, "k"), null, true)))
							.then(ClientCommandManager.literal("rukia").executes(c -> preview(c, DoubleArgumentType.getDouble(c, "k"), null, false))));
			dispatcher.register(ClientCommandManager.literal("reiatsu_fx").then(pressure));
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> ownerBranch(String name, boolean byakuya) {
		return ClientCommandManager.literal(name)
				.executes(c -> preview(c, 1.0, null, byakuya))
				.then(ClientCommandManager.argument("k", DoubleArgumentType.doubleArg(0.0, 1.0))
						.executes(c -> preview(c, DoubleArgumentType.getDouble(c, "k"), null, byakuya)));
	}

	private static int previewDist(CommandContext<FabricClientCommandSource> c, boolean byakuya) {
		double blocks = DoubleArgumentType.getDouble(c, "blocks");
		return preview(c, PressureMath.strength(blocks), blocks, byakuya);
	}

	private static int preview(CommandContext<FabricClientCommandSource> c, double strength, Double blocks, boolean byakuya) {
		MinecraftClient mc = c.getSource().getClient();
		mc.execute(() -> {
			PressurePlan plan = start(PREVIEW_OWNER, System.nanoTime() & 0xFFFFFFFFL, strength, byakuya,
					blocks == null ? "preview" : String.format(Locale.ROOT, "preview at %.1f blocks", blocks));
			String msg = plan.strength() < 0.03
					? "[reiatsu_fx] nothing to show (strength " + String.format(Locale.ROOT, "%.2f", strength) + ", pressure.scale or the distance is too small)"
					: String.format(Locale.ROOT, "[reiatsu_fx] pressure %s, strength %.2f: shake %s, tremor %s, blur %s, stripes %s (reduceMotion=%s, photosensitiveSafe=%s)",
							byakuya ? "Byakuya" : "Rukia", plan.strength(), plan.shakeDeg() > 0 ? "on" : "off", plan.tremorDeg() > 0 ? "on" : "off",
							plan.blurScale() > 0 ? (PressureBlur.failed() ? "shader failed, see the log" : "on") : "off", plan.stripes() > 0 ? "on" : "off",
							FxConfig.reduceMotion, FxConfig.photosensitiveSafe);
			if (mc.player != null) {
				mc.player.sendMessage(Text.literal(msg), false);
			}
		});
		return Command.SINGLE_SUCCESS;
	}
}
