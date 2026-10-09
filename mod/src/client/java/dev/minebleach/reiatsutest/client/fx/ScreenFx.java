package dev.minebleach.reiatsutest.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Screen effects of the storyboard (1.4): FLASH, VIGNETTE (dark / frost edge), GRADE (multiply, drawn at
 * {@code WorldRenderEvents.LAST} so the hand and the HUD stay ungraded), SHAKE (a view rotation applied in
 * {@code GameRenderer#tiltViewWhenHurt}). Flash and vignette are drawn under the HUD. All values are scaled by
 * {@code screenFxScale} (shake by {@code shakeScale}); reduce motion and photosensitive safe use the variants of 1.4.
 */
public final class ScreenFx {
	private static final Identifier VIGNETTE_DARK = ReiatsuTest.id("textures/gui/fx/vignette_dark.png");
	private static final Identifier FROST_EDGE = ReiatsuTest.id("textures/gui/fx/frost_edge.png");
	private static final Identifier SPEED_LINES = ReiatsuTest.id("textures/gui/fx/speed_lines.png");
	private static final float[] VIGNETTE_TINT = FxMath.hex("#0E1428");
	private static final float[] FROST_TINT = FxMath.hex("#CFEFFF");

	public enum Kind { DARK, FROST }

	/** A ramped, held, then released layer (vignette or grade). */
	public static final class Layer {
		final Kind kind;
		final float[] rgb;
		final double level;
		final double ramp;
		final double start;
		double releaseAt = Double.MAX_VALUE;
		double fade = 0.5;
		boolean dead;

		Layer(Kind kind, float[] rgb, double level, double ramp) {
			this.kind = kind;
			this.rgb = rgb;
			this.level = level;
			this.ramp = Math.max(1e-3, ramp);
			this.start = FxClock.now;
		}

		/** Starts the fade out now. */
		public void release(double fadeSeconds) {
			if (releaseAt == Double.MAX_VALUE) {
				releaseAt = FxClock.now;
				fade = Math.max(1e-3, fadeSeconds);
			}
		}

		/** 0..1 envelope. */
		double env() {
			double up = FxMath.ios((FxClock.now - start) / ramp);
			if (releaseAt == Double.MAX_VALUE) {
				return up;
			}
			double x = (FxClock.now - releaseAt) / fade;
			if (x >= 1) {
				dead = true;
				return 0;
			}
			return Math.min(up, 1 - FxMath.ios(x));
		}
	}

	private record Flash(double start, double rise, double hold, double fade, double peak, float[] rgb) {
		double alpha() {
			double t = FxClock.now - start;
			if (t < 0) {
				return 0;
			}
			if (t < rise) {
				return peak * t / rise;
			}
			if (t < rise + hold) {
				return peak;
			}
			double x = (t - rise - hold) / fade;
			return x >= 1 ? 0 : peak * (1 - x) * (1 - x);
		}

		boolean done() {
			return FxClock.now - start > rise + hold + fade;
		}
	}

	private record Shake(double start, double amp, double dur) {
	}

	private static final List<Flash> FLASHES = new ArrayList<>();
	private static final List<Layer> VIGNETTES = new ArrayList<>();
	private static final List<Layer> GRADES = new ArrayList<>();
	private static final List<Shake> SHAKES = new ArrayList<>();
	private static final List<Double> BIG_FLASH_TIMES = new ArrayList<>();
	private static double lastImpactFrame = -10;
	private static double impactStart = -10;
	private static boolean impactActive;
	private static double speedStart = -10;
	private static double speedLevel;
	private static double speedDur;
	private static float speedX = 0.5f;
	private static float speedY = 0.5f;
	/** Dev/stat: number of FLASH requests refused or downgraded by the photosensitivity limiter. */
	public static int limiterDowngrades;
	/** Dev/stat: peak alpha of the last drawn flash (harness frame log). */
	public static double lastFlashAlpha;

	private ScreenFx() {
	}

	public static void clear() {
		FLASHES.clear();
		VIGNETTES.clear();
		GRADES.clear();
		SHAKES.clear();
		BIG_FLASH_TIMES.clear();
		impactActive = false;
		speedLevel = 0;
	}

	// ------------------------------------------------------------------------------------------------ requests

	/** FLASH(P, tint): rise 0.05, hold 0.03, fade 0.14; short flashes 0.02 / 0.02 / 0.06; reduce motion: P <= 0.40, 0.07 s total. */
	public static void flash(double peak, float[] tint, boolean shortFlash, double strength) {
		double p = peak * strength * FxConfig.screenFxScale;
		if (p <= 0.001) {
			return;
		}
		boolean rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe;
		double rise = shortFlash ? 0.02 : 0.05;
		double hold = shortFlash ? 0.02 : 0.03;
		double fade = shortFlash ? 0.06 : 0.14;
		if (rm) {
			p = Math.min(p, 0.40);
			rise = 0.02;
			hold = 0.01;
			fade = 0.04;
		}
		// photosensitivity (1.4): at most 3 flashes above 0.25 per second per client
		if (p > 0.25) {
			BIG_FLASH_TIMES.removeIf(t -> FxClock.now - t > 1.0);
			if (BIG_FLASH_TIMES.size() >= 3) {
				p = 0.25;
				limiterDowngrades++;
			} else {
				BIG_FLASH_TIMES.add(FxClock.now);
			}
		}
		FLASHES.add(new Flash(FxClock.now, rise, hold, fade, p, tint));
	}

	/** VIGNETTE(kind, L, ramp): returns a handle to release it (fade). Reduce motion: constant alpha min(L, 0.15), no UV scale. */
	public static Layer vignette(Kind kind, double level, double ramp, double strength) {
		double l = level * strength * FxConfig.screenFxScale;
		if (FxConfig.reduceMotion || FxConfig.photosensitiveSafe) {
			l = Math.min(l, 0.15);
			ramp = 0.05;
		}
		Layer layer = new Layer(kind, kind == Kind.DARK ? VIGNETTE_TINT : FROST_TINT, l, ramp);
		VIGNETTES.add(layer);
		return layer;
	}

	/** GRADE(colour, s, ramp): multiply quad with lerp(white, colour, s); not a motion effect, unchanged by reduce motion. */
	public static Layer grade(float[] colour, double s, double ramp, double strength) {
		Layer layer = new Layer(null, colour, s * strength * FxConfig.gradeStrength, ramp);
		GRADES.add(layer);
		return layer;
	}

	/** SHAKE(A, T): sum of decaying sines on yaw and pitch (A in degrees); zero with reduce motion. */
	public static void shake(double amplitudeDeg, double duration, double strength) {
		if (FxConfig.reduceMotion || FxConfig.photosensitiveSafe) {
			return;
		}
		double a = amplitudeDeg * strength * FxConfig.shakeScale;
		if (a > 0.001) {
			SHAKES.add(new Shake(FxClock.now, a, duration));
		}
	}

	/** IMPACT_FRAME: white 0.85 for 17 ms then ink 0.6 for 33 ms; replaced by FLASH(0.40) short with reduce motion; at most once per second. */
	public static void impactFrame(double strength) {
		if (FxConfig.reduceMotion || FxConfig.photosensitiveSafe || FxClock.now - lastImpactFrame < 1.0) {
			if (FxClock.now - lastImpactFrame < 1.0) {
				limiterDowngrades++;
			}
			flash(FxConfig.reduceMotion || FxConfig.photosensitiveSafe ? 0.40 : 0.25, FxMath.hex("#FFFFFF"), true, strength);
			return;
		}
		lastImpactFrame = FxClock.now;
		impactStart = FxClock.now;
		impactActive = true;
	}

	/** SPEEDLINES(L, T) at a screen point (0..1); off with reduce motion. */
	public static void speedLines(double level, double duration, float sx, float sy) {
		if (FxConfig.reduceMotion || FxConfig.photosensitiveSafe) {
			return;
		}
		speedStart = FxClock.now;
		speedLevel = level * FxConfig.screenFxScale;
		speedDur = duration;
		speedX = sx;
		speedY = sy;
	}

	// ------------------------------------------------------------------------------------------------ shake hook

	/** Current view offsets in degrees, summed and clamped to 1.5 (SHAKE). Index 0 yaw, 1 pitch. */
	public static void shakeOffsets(double[] out) {
		double yaw = 0;
		double pitch = 0;
		for (int i = SHAKES.size() - 1; i >= 0; i--) {
			Shake s = SHAKES.get(i);
			double t = FxClock.now - s.start;
			if (t >= s.dur) {
				SHAKES.remove(i);
				continue;
			}
			double decay = (1 - t / s.dur);
			decay *= decay;
			yaw += s.amp * Math.sin(2 * Math.PI * 14 * t) * decay;
			pitch += 0.7 * s.amp * Math.sin(2 * Math.PI * 14 * t + Math.PI / 2) * decay;
		}
		out[0] = FxMath.clamp(yaw, -1.5, 1.5);
		out[1] = FxMath.clamp(pitch, -1.5, 1.5);
	}

	public static boolean shaking() {
		return !SHAKES.isEmpty();
	}

	// ------------------------------------------------------------------------------------------------ drawing

	/** Under the HUD (InGameHud#render HEAD): vignettes, flashes, impact frame, speed lines. */
	public static void renderUnderHud(DrawContext ctx) {
		if (FLASHES.isEmpty() && VIGNETTES.isEmpty() && !impactActive && speedLevel <= 0) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		for (int i = VIGNETTES.size() - 1; i >= 0; i--) {
			Layer v = VIGNETTES.get(i);
			double env = v.env();
			if (v.dead) {
				VIGNETTES.remove(i);
				continue;
			}
			double a = v.level * env;
			if (a <= 0.002) {
				continue;
			}
			float scale = 1.0f;
			if (v.kind == Kind.FROST && !FxConfig.reduceMotion && !FxConfig.photosensitiveSafe) {
				scale = (float) (1.15 - 0.15 * FxMath.ios((FxClock.now - v.start) / v.ramp));
			}
			RenderSystem.defaultBlendFunc();
			quadTex(m, v.kind == Kind.DARK ? VIGNETTE_DARK : FROST_EDGE, w, h, scale, v.rgb, (float) Math.min(1.0, a));
		}
		double flashTotal = 0;
		float fr = 0;
		float fg = 0;
		float fb = 0;
		for (int i = FLASHES.size() - 1; i >= 0; i--) {
			Flash f = FLASHES.get(i);
			if (f.done()) {
				FLASHES.remove(i);
				continue;
			}
			double a = f.alpha();
			if (a > 0.001) {
				RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
				quadColor(m, w, h, f.rgb[0], f.rgb[1], f.rgb[2], (float) Math.min(1.0, a));
				flashTotal = Math.max(flashTotal, a);
			}
		}
		lastFlashAlpha = flashTotal;
		if (impactActive) {
			double t = FxClock.now - impactStart;
			RenderSystem.defaultBlendFunc();
			if (t < 0.017) {
				quadColor(m, w, h, 1f, 1f, 1f, 0.85f);
			} else if (t < 0.050) {
				quadColor(m, w, h, 0x0B / 255f, 0x0F / 255f, 0x1E / 255f, 0.6f);
			} else {
				impactActive = false;
			}
		}
		if (speedLevel > 0) {
			double t = FxClock.now - speedStart;
			if (t >= speedDur) {
				speedLevel = 0;
			} else {
				float s = (float) (1.0 + 0.15 * t / speedDur);
				RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
				quadTexAt(m, SPEED_LINES, w, h, s, speedX, speedY, new float[] {1, 1, 1}, (float) speedLevel);
			}
		}
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}

	/** True while a GRADE with s > 0.3 is active (glows get x1.3, 1.7.1). */
	public static boolean gradeStrong() {
		for (Layer g : GRADES) {
			if (g.level * g.env() > 0.3) {
				return true;
			}
		}
		return false;
	}

	/** Combined multiply colour of all grades or null when there is none. */
	private static float[] gradeColor() {
		float r = 1;
		float g = 1;
		float b = 1;
		boolean any = false;
		for (int i = GRADES.size() - 1; i >= 0; i--) {
			Layer l = GRADES.get(i);
			double s = l.level * l.env();
			if (l.dead) {
				GRADES.remove(i);
				continue;
			}
			if (s <= 0.002) {
				continue;
			}
			any = true;
			r *= 1 + (l.rgb[0] - 1) * s;
			g *= 1 + (l.rgb[1] - 1) * s;
			b *= 1 + (l.rgb[2] - 1) * s;
		}
		return any ? new float[] {r, g, b} : null;
	}

	/** At WorldRenderEvents.LAST: multiply quad over the rendered world (before the hand, so hand and HUD are not graded). */
	public static void renderGrade(WorldRenderContext ctx) {
		float[] c = gradeColor();
		if (c == null) {
			return;
		}
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
		var sorter = RenderSystem.getVertexSorting();
		mv.pushMatrix();
		mv.identity();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(new Matrix4f(), sorter);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.DST_COLOR, GlStateManager.DstFactor.ZERO);
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		b.vertex(-1f, -1f, 0f).color(c[0], c[1], c[2], 1f);
		b.vertex(1f, -1f, 0f).color(c[0], c[1], c[2], 1f);
		b.vertex(1f, 1f, 0f).color(c[0], c[1], c[2], 1f);
		b.vertex(-1f, 1f, 0f).color(c[0], c[1], c[2], 1f);
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.setProjectionMatrix(proj, sorter);
		mv.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}

	private static void quadColor(Matrix4f m, int w, int h, float r, float g, float b, float a) {
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		bb.vertex(m, 0, h, 0).color(r, g, b, a);
		bb.vertex(m, w, h, 0).color(r, g, b, a);
		bb.vertex(m, w, 0, 0).color(r, g, b, a);
		bb.vertex(m, 0, 0, 0).color(r, g, b, a);
		BufferRenderer.drawWithGlobalProgram(bb.end());
	}

	private static void quadTex(Matrix4f m, Identifier tex, int w, int h, float scale, float[] rgb, float a) {
		quadTexAt(m, tex, w, h, scale, 0.5f, 0.5f, rgb, a);
	}

	private static void quadTexAt(Matrix4f m, Identifier tex, int w, int h, float scale, float cx, float cy, float[] rgb, float a) {
		float x0 = w * cx - w * scale * 0.5f;
		float x1 = w * cx + w * scale * 0.5f;
		float y0 = h * cy - h * scale * 0.5f;
		float y1 = h * cy + h * scale * 0.5f;
		MinecraftClient.getInstance().getTextureManager().getTexture(tex).setFilter(true, false);
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.setShaderTexture(0, tex);
		BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		bb.vertex(m, x0, y1, 0).texture(0, 1).color(rgb[0], rgb[1], rgb[2], a);
		bb.vertex(m, x1, y1, 0).texture(1, 1).color(rgb[0], rgb[1], rgb[2], a);
		bb.vertex(m, x1, y0, 0).texture(1, 0).color(rgb[0], rgb[1], rgb[2], a);
		bb.vertex(m, x0, y0, 0).texture(0, 0).color(rgb[0], rgb[1], rgb[2], a);
		BufferRenderer.drawWithGlobalProgram(bb.end());
	}
}
