package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.GlowAtlasTable;

/**
 * Cells of the two glow atlases. Atlas 0 {@code textures/fx/fx_glow.png} (256 x 256, VFX_STORYBOARD 1.3.2, {@code gen_fx_textures.py}) and
 * atlas 1 {@code textures/fx/uryu_glow.png} (512 x 256, URYU_VFX_STORYBOARD 4.3, {@code gen_uryu_textures.py}). The rects come from
 * {@link GlowAtlasTable} (which {@code GlowAtlasTableTest} checks against the generators), the constant name is the upper case sprite name.
 * {@link FxGlowBatch} draws the sprites of atlas 1 through a second layer in the same frame.
 */
public enum GlowSprite {
	GLOW_SOFT(0, "glow_soft"),
	GLOW_CORE(0, "glow_core"),
	STAR4(0, "star4"),
	STAR6(0, "star6"),
	PETAL_GLOW(0, "petal_glow"),
	FEATHER(0, "feather"),
	FEATHER_B(0, "feather_b"),
	SPECK(0, "speck"),
	MIST(0, "mist"),
	FLARE(0, "flare"),
	STREAK(0, "streak"),
	LINE(0, "line"),
	RING_THIN(0, "ring_thin"),
	RING_SOFT(0, "ring_soft"),
	FROST_SIGIL(0, "frost_sigil"),
	CRACK(0, "crack"),
	PILLAR(0, "pillar"),

	// ---- atlas 1: the Quincy sprites (Uryu)
	ZEICHEN_STAR(1, "zeichen_star"),
	WEB_WHEEL(1, "web_wheel"),
	BURST_RAYS(1, "burst_rays"),
	CRESCENT(1, "crescent"),
	PLATFORM_DISC(1, "platform_disc"),
	SPARK_CLUSTER(1, "spark_cluster"),
	ZEICHEN_CROSS(1, "zeichen_cross"),
	PENT_LINE(1, "pent_line"),
	CHEVRONS(1, "chevrons"),
	DIAMOND_STAR(1, "diamond_star"),
	FLAKE(1, "flake"),
	NEEDLE(1, "needle"),
	VEIN_TRACE(1, "vein_trace"),
	GLITTER(1, "glitter"),
	DASH(1, "dash"),
	DASH_B(1, "dash_b"),
	CHAIN(1, "chain"),
	LENS(1, "lens"),
	FLECHETTE_TRAIL(1, "flechette_trail"),
	BEAM_GLITTER(1, "beam_glitter"),
	RUNE_BAND(1, "rune_band"),
	STAR_FIELD(1, "star_field"),
	FENCE(1, "fence");

	/** Size of atlas 0 (square). */
	public static final int ATLAS = GlowAtlasTable.FX_SIZE;
	/** Number of atlases ({@link #atlas} is 0 or 1). */
	public static final int ATLASES = 2;

	/** 0 = {@code fx_glow}, 1 = {@code uryu_glow}. */
	public final int atlas;
	public final int x;
	public final int y;
	public final int w;
	public final int h;
	/** UVs inset by half a texel (linear filtering must not bleed into the neighbouring cell). */
	public final float u0;
	public final float v0;
	public final float u1;
	public final float v1;
	/** Height over width. */
	public final float aspect;

	GlowSprite(int atlas, String name) {
		GlowAtlasTable.Rect r = atlas == 0 ? GlowAtlasTable.fx(name) : GlowAtlasTable.uryu(name);
		int aw = atlas == 0 ? GlowAtlasTable.FX_SIZE : GlowAtlasTable.URYU_WIDTH;
		int ah = atlas == 0 ? GlowAtlasTable.FX_SIZE : GlowAtlasTable.URYU_HEIGHT;
		this.atlas = atlas;
		this.x = r.x();
		this.y = r.y();
		this.w = r.w();
		this.h = r.h();
		this.u0 = (x + 0.5f) / aw;
		this.v0 = (y + 0.5f) / ah;
		this.u1 = (x + w - 0.5f) / aw;
		this.v1 = (y + h - 0.5f) / ah;
		this.aspect = (float) h / w;
	}

	private static final GlowSprite[] VALUES = values();

	public static GlowSprite of(int ordinal) {
		return VALUES[ordinal];
	}

	/** The sprites of one atlas, in declaration order. */
	public static GlowSprite[] ofAtlas(int atlas) {
		return java.util.Arrays.stream(VALUES).filter(s -> s.atlas == atlas).toArray(GlowSprite[]::new);
	}
}
