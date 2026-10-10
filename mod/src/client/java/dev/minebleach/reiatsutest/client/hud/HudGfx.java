package dev.minebleach.reiatsutest.client.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.minebleach.reiatsutest.ReiatsuTest;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Batched textured quads for the HUD. All pixel art sheets live in one atlas ({@code hud_atlas.png}, layout generated into
 * {@link HudAtlasLayout}); callers address a sheet by its logical id ({@code HudAssets.GEM}) and sheet local texel
 * coordinates, the remap to the atlas happens here. Normal and additive quads of one texture are collected in two builders
 * and drawn together (normal first), so a whole plate is one or two draw calls; a different texture (soft shadow, tiled
 * overlay) flushes the batch. Text is queued into the context's vertex consumers and drawn at {@link #layerBreak()} /
 * {@link #end()} (always on top of the sprites before the break). Colours are floats 0..1, positions in scaled GUI
 * pixels.
 */
public final class HudGfx {
	public static final int NORMAL = 0;
	public static final int ADD = 1;
	private static final Identifier ATLAS = ReiatsuTest.id("textures/gui/hud/hud_atlas.png");
	private static final Map<Identifier, int[]> REMAP = new HashMap<>();

	static {
		for (Map.Entry<String, int[]> e : HudAtlasLayout.SHEETS.entrySet()) {
			REMAP.put(ReiatsuTest.id("textures/gui/hud/" + e.getKey() + ".png"), e.getValue());
		}
	}

	private DrawContext ctx;
	private final BufferAllocator allocN = new BufferAllocator(1 << 15);
	private final BufferAllocator allocA = new BufferAllocator(1 << 14);
	private BufferBuilder bufN;
	private BufferBuilder bufA;
	private Identifier tex;
	private int quadsN;
	private int quadsA;
	/** Statistic: draw calls issued in the last frame. */
	public int drawCalls;
	private final Map<Identifier, AbstractTexture> smoothDone = new HashMap<>();

	public void begin(DrawContext ctx) {
		this.ctx = ctx;
		this.bufN = null;
		this.bufA = null;
		this.tex = null;
		this.quadsN = 0;
		this.quadsA = 0;
		this.drawCalls = 0;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
	}

	public DrawContext ctx() {
		return ctx;
	}

	public void flush() {
		if (bufN != null) {
			if (quadsN > 0) {
				setup(false);
				BufferRenderer.drawWithGlobalProgram(bufN.end());
				drawCalls++;
			} else {
				bufN.endNullable();
			}
		}
		if (bufA != null) {
			if (quadsA > 0) {
				setup(true);
				BufferRenderer.drawWithGlobalProgram(bufA.end());
				drawCalls++;
			} else {
				bufA.endNullable();
			}
		}
		RenderSystem.defaultBlendFunc();
		bufN = null;
		bufA = null;
		quadsN = 0;
		quadsA = 0;
	}

	private void setup(boolean additive) {
		RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
		RenderSystem.setShaderTexture(0, tex);
		RenderSystem.enableBlend();
		if (additive) {
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		} else {
			RenderSystem.defaultBlendFunc();
		}
	}

	public void end() {
		layerBreak();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
	}

	/** Linear filtering for soft textures (shadow, glows); redone after a resource reload. */
	private void ensureSmooth(Identifier t) {
		if (t.equals(HudAssets.PLATE_SHADOW) || t.equals(HudAssets.TITLE_GLOW) || t.equals(HudAssets.SLOT_GLOW)) {
			AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(t);
			if (smoothDone.get(t) != texture) {
				texture.setFilter(true, false);
				smoothDone.put(t, texture);
			}
		}
	}

	/** Region (u, v, rw, rh) of sheet {@code t} drawn into the rectangle (x, y, w, h); tint rgba. */
	public void quad(Identifier t, int texW, int texH, float x, float y, float w, float h, float u, float v, float rw, float rh,
			float r, float g, float b, float a, int blend) {
		if (a <= 0.003f || w <= 0 || h <= 0) {
			return;
		}
		int[] sheet = REMAP.get(t);
		if (sheet != null) {
			t = ATLAS;
			u += sheet[0];
			v += sheet[1];
			texW = HudAtlasLayout.SIZE;
			texH = HudAtlasLayout.SIZE;
		}
		if ((bufN != null || bufA != null) && !t.equals(tex)) {
			flush();
		}
		if (tex == null || bufN == null && bufA == null) {
			tex = t;
			ensureSmooth(t);
		}
		BufferBuilder bb;
		if (blend == ADD) {
			if (bufA == null) {
				bufA = new BufferBuilder(allocA, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
			}
			bb = bufA;
			quadsA++;
		} else {
			if (bufN == null) {
				bufN = new BufferBuilder(allocN, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
			}
			bb = bufN;
			quadsN++;
		}
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		float u0 = u / texW;
		float u1 = (u + rw) / texW;
		float v0 = v / texH;
		float v1 = (v + rh) / texH;
		bb.vertex(m, x, y + h, 0).texture(u0, v1).color(r, g, b, a);
		bb.vertex(m, x + w, y + h, 0).texture(u1, v1).color(r, g, b, a);
		bb.vertex(m, x + w, y, 0).texture(u1, v0).color(r, g, b, a);
		bb.vertex(m, x, y, 0).texture(u0, v0).color(r, g, b, a);
	}

	/** 1:1 region. */
	public void sprite(Identifier t, int texW, int texH, float x, float y, float u, float v, float w, float h, float[] c, float a) {
		quad(t, texW, texH, x, y, w, h, u, v, w, h, c[0], c[1], c[2], a, NORMAL);
	}

	public void spriteAdd(Identifier t, int texW, int texH, float x, float y, float u, float v, float w, float h, float[] c, float a) {
		quad(t, texW, texH, x, y, w, h, u, v, w, h, c[0], c[1], c[2], a, ADD);
	}

	/** Solid rectangle, batched through the white pixel sheet. */
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		float a = ((argb >>> 24) & 255) / 255f;
		quad(HudAssets.PIXEL, 4, 4, x0, y0, x1 - x0, y1 - y0, 0, 0, 4, 4, ((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f,
				a, NORMAL);
	}

	public void nineSlice(Identifier sprite, int x, int y, int w, int h, float[] tint, float alpha) {
		flush();
		ctx.setShaderColor(tint[0], tint[1], tint[2], alpha);
		RenderSystem.enableBlend();
		ctx.drawGuiTexture(sprite, x, y, w, h);
		ctx.setShaderColor(1f, 1f, 1f, 1f);
	}

	/**
	 * Text is queued into the context's vertex consumers (one draw for all text of a layer, see {@link #layerBreak}), so it always
	 * ends up on top of the sprites drawn before the break. DrawContext#drawText would draw immediately, one call per string.
	 */
	public int text(TextRenderer font, String s, float x, float y, int argb, boolean shadow) {
		return font.draw(s, x, y, argb, shadow, ctx.getMatrices().peek().getPositionMatrix(), ctx.getVertexConsumers(),
				TextRenderer.TextLayerType.NORMAL, 0, 15728880, font.isRightToLeft());
	}

	public int text(TextRenderer font, Text s, float x, float y, int argb, boolean shadow) {
		return font.draw(s.asOrderedText(), x, y, argb, shadow, ctx.getMatrices().peek().getPositionMatrix(), ctx.getVertexConsumers(),
				TextRenderer.TextLayerType.NORMAL, 0, 15728880);
	}

	/** Draws the pending sprites and then the pending text: everything after this call is above everything before it. */
	public void layerBreak() {
		flush();
		ctx.draw();
	}

	/** Text at the origin of the current matrix (the caller translates / scales), for float placement and scaled titles. */
	public void textAt(TextRenderer font, String s, int argb, boolean shadow) {
		text(font, s, 0, 0, argb, shadow);
	}

	/** ARGB int from floats. */
	public static int argb(float[] c, float a) {
		int ai = Math.max(0, Math.min(255, Math.round(a * 255)));
		int r = Math.max(0, Math.min(255, Math.round(c[0] * 255)));
		int g = Math.max(0, Math.min(255, Math.round(c[1] * 255)));
		int b = Math.max(0, Math.min(255, Math.round(c[2] * 255)));
		return (ai << 24) | (r << 16) | (g << 8) | b;
	}
}
