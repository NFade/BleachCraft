package dev.minebleach.reiatsutest.core.fx;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The cell rects of the two additive glow atlases: atlas 0 {@code textures/fx/fx_glow.png} (256 x 256, VFX_STORYBOARD 1.3.2, written by
 * {@code tools/gen_fx_textures.py}) and atlas 1 {@code textures/fx/uryu_glow.png} (512 x 256, URYU_VFX_STORYBOARD 4.3, written by
 * {@code tools/gen_uryu_textures.py}). The client enum {@code GlowSprite} reads its rects from here, so the table is the one place the Java
 * side knows them; {@code GlowAtlasTableTest} parses the generator sources, the generated {@code *_atlas.json} and the PNG sizes and
 * fails when anything differs. Pure data (no Minecraft classes), so it is unit testable.
 */
public final class GlowAtlasTable {
	public record Rect(int x, int y, int w, int h) {
	}

	public static final int FX_SIZE = 256;
	public static final int URYU_WIDTH = 512;
	public static final int URYU_HEIGHT = 256;

	/** Atlas 0: Rukia, Byakuya and common sprites. */
	public static final Map<String, Rect> FX = new LinkedHashMap<>();
	/** Atlas 1: the Quincy sprites; {@code spare} is reserved and has no {@code GlowSprite}. */
	public static final Map<String, Rect> URYU = new LinkedHashMap<>();

	static {
		fx("glow_soft", 0, 0, 64, 64);
		fx("glow_core", 64, 0, 32, 32);
		fx("star4", 96, 0, 32, 32);
		fx("star6", 128, 0, 32, 32);
		fx("petal_glow", 160, 0, 32, 32);
		fx("feather", 192, 0, 32, 64);
		fx("feather_b", 224, 0, 32, 64);
		fx("speck", 64, 32, 16, 16);
		fx("mist", 80, 32, 48, 32);
		fx("flare", 128, 32, 64, 32);
		fx("streak", 0, 64, 128, 16);
		fx("line", 0, 80, 128, 16);
		fx("ring_thin", 0, 96, 64, 64);
		fx("ring_soft", 64, 96, 64, 64);
		fx("frost_sigil", 128, 64, 128, 128);
		fx("crack", 0, 160, 64, 64);
		fx("pillar", 64, 160, 32, 96);

		uryu("zeichen_star", 0, 0, 128, 128);
		uryu("web_wheel", 128, 0, 128, 128);
		uryu("burst_rays", 256, 0, 128, 128);
		uryu("crescent", 384, 0, 128, 32);
		uryu("platform_disc", 384, 32, 128, 64);
		uryu("spark_cluster", 384, 96, 128, 32);
		uryu("zeichen_cross", 0, 128, 64, 64);
		uryu("pent_line", 64, 128, 64, 64);
		uryu("chevrons", 128, 128, 64, 64);
		uryu("diamond_star", 192, 128, 64, 64);
		uryu("flake", 256, 128, 32, 64);
		uryu("needle", 288, 128, 32, 64);
		uryu("vein_trace", 320, 128, 64, 32);
		uryu("glitter", 320, 160, 64, 32);
		uryu("dash", 384, 128, 64, 16);
		uryu("dash_b", 384, 144, 64, 16);
		uryu("chain", 384, 160, 64, 32);
		uryu("lens", 448, 128, 64, 128);
		uryu("flechette_trail", 0, 192, 128, 16);
		uryu("beam_glitter", 0, 208, 128, 16);
		uryu("rune_band", 0, 224, 128, 32);
		uryu("star_field", 128, 192, 128, 64);
		uryu("fence", 256, 192, 128, 64);
		uryu("spare", 384, 192, 64, 64);
	}

	private GlowAtlasTable() {
	}

	private static void fx(String name, int x, int y, int w, int h) {
		FX.put(name, new Rect(x, y, w, h));
	}

	private static void uryu(String name, int x, int y, int w, int h) {
		URYU.put(name, new Rect(x, y, w, h));
	}

	public static Rect fx(String name) {
		Rect r = FX.get(name);
		if (r == null) {
			throw new IllegalArgumentException("no sprite " + name + " in the fx_glow atlas");
		}
		return r;
	}

	public static Rect uryu(String name) {
		Rect r = URYU.get(name);
		if (r == null) {
			throw new IllegalArgumentException("no sprite " + name + " in the uryu_glow atlas");
		}
		return r;
	}

	/** True when no two rects of the table overlap and all lie inside {@code width x height}. */
	public static boolean consistent(Map<String, Rect> table, int width, int height) {
		boolean[][] used = new boolean[height][width];
		for (Rect r : table.values()) {
			if (r.x() < 0 || r.y() < 0 || r.w() <= 0 || r.h() <= 0 || r.x() + r.w() > width || r.y() + r.h() > height) {
				return false;
			}
			for (int yy = r.y(); yy < r.y() + r.h(); yy++) {
				for (int xx = r.x(); xx < r.x() + r.w(); xx++) {
					if (used[yy][xx]) {
						return false;
					}
					used[yy][xx] = true;
				}
			}
		}
		return true;
	}
}
