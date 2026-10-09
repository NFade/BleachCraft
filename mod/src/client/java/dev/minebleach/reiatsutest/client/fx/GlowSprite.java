package dev.minebleach.reiatsutest.client.fx;

/** Cells of the glow atlas {@code textures/fx/fx_glow.png} (256 x 256, VFX_STORYBOARD 1.3.2). Rects match gen_fx_textures.py. */
public enum GlowSprite {
	GLOW_SOFT(0, 0, 64, 64),
	GLOW_CORE(64, 0, 32, 32),
	STAR4(96, 0, 32, 32),
	STAR6(128, 0, 32, 32),
	PETAL_GLOW(160, 0, 32, 32),
	FEATHER(192, 0, 32, 64),
	FEATHER_B(224, 0, 32, 64),
	SPECK(64, 32, 16, 16),
	MIST(80, 32, 48, 32),
	FLARE(128, 32, 64, 32),
	STREAK(0, 64, 128, 16),
	LINE(0, 80, 128, 16),
	RING_THIN(0, 96, 64, 64),
	RING_SOFT(64, 96, 64, 64),
	FROST_SIGIL(128, 64, 128, 128),
	CRACK(0, 160, 64, 64),
	PILLAR(64, 160, 32, 96);

	public static final int ATLAS = 256;
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

	GlowSprite(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.u0 = (x + 0.5f) / ATLAS;
		this.v0 = (y + 0.5f) / ATLAS;
		this.u1 = (x + w - 0.5f) / ATLAS;
		this.v1 = (y + h - 0.5f) / ATLAS;
		this.aspect = (float) h / w;
	}

	private static final GlowSprite[] VALUES = values();

	public static GlowSprite of(int ordinal) {
		return VALUES[ordinal];
	}
}
