package dev.minebleach.reiatsutest.core.hud;

import java.util.List;

/**
 * Placement of the two side groups of the HUD, in scaled GUI pixels (pure maths, tested): the three kido cells in the bottom left
 * corner and the shunpo cell at the left edge, vertically centred. Cells are 22 x 22 like the ability strip. The kido cells sit in
 * one row left of the hotbar (and its off hand slot); when that row does not fit (very small scaled width) they stack in one column
 * instead, first spell on top. The shunpo cell is centred on the screen height, pushed down below {@code topReserve} (plate, voice
 * row and denied toast at the top left) and never onto the kido cells.
 */
public final class SideHudLayout {
	public static final int CELL = 22;
	/** Width of the vanilla off hand slot left of the hotbar (the hotbar is 182 wide, centred). */
	public static final int OFFHAND = 29;
	public static final int HOTBAR_HALF = 91;
	public static final int HOTBAR_H = 22;
	/** Scaled width below which the HUD uses its compact mode (same rule as the plate). */
	public static final int COMPACT_BELOW_W = 360;
	/** Bottom of the vanilla chat lines above the bottom edge (chat sits at h - 40 and higher). */
	public static final int CHAT_BOTTOM = 40;
	private static final int MIN_GAP_TO_KIDO = 4;

	private SideHudLayout() {
	}

	public record Rect(int x, int y, int w, int h) {
		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}

		public boolean overlaps(Rect o) {
			return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
		}
	}

	/**
	 * @param kido    the cells of Byakurai, Shakkaho and Sai, in this order
	 * @param shunpo  the shunpo cell
	 * @param stacked true when the kido cells are stacked in a column (narrow window)
	 */
	public record Result(List<Rect> kido, Rect shunpo, boolean stacked) {
	}

	/** Compact mode of the HUD: the config flag or a narrow scaled window (VFX_STORYBOARD 7.9). */
	public static boolean compact(boolean configCompact, int w) {
		return configCompact || w < COMPACT_BELOW_W;
	}

	/** The vanilla hotbar. */
	public static Rect hotbar(int w, int h) {
		return new Rect(w / 2 - HOTBAR_HALF, h - HOTBAR_H, 2 * HOTBAR_HALF, HOTBAR_H);
	}

	/** The hotbar plus the room of the off hand slot on its left (what the side groups keep clear). */
	public static Rect hotbarWithOffhand(int w, int h) {
		return new Rect(w / 2 - HOTBAR_HALF - OFFHAND, h - HOTBAR_H, 2 * HOTBAR_HALF + OFFHAND, HOTBAR_H);
	}

	public static int margin(boolean compact) {
		return compact ? 4 : 6;
	}

	public static int gap(boolean compact) {
		return compact ? 1 : 2;
	}

	/** Width of the kido row. */
	public static int rowWidth(boolean compact) {
		return 3 * CELL + 2 * gap(compact);
	}

	/** True when the kido row fits between the left margin and the hotbar (with its off hand slot). */
	public static boolean rowFits(int w, boolean compact) {
		return margin(compact) + rowWidth(compact) <= w / 2 - HOTBAR_HALF - OFFHAND - 2;
	}

	/**
	 * @param w          scaled window width
	 * @param h          scaled window height
	 * @param compact    compact mode (smaller margin and gap)
	 * @param topReserve y below which the shunpo cell may start (bottom of the plate block); 0 for none
	 */
	public static Result compute(int w, int h, boolean compact, int topReserve) {
		int m = margin(compact);
		int gap = gap(compact);
		boolean stacked = !rowFits(w, compact);
		Rect[] k = new Rect[3];
		for (int i = 0; i < 3; i++) {
			if (stacked) {
				k[i] = new Rect(m, h - m - CELL - (2 - i) * (CELL + gap), CELL, CELL);
			} else {
				k[i] = new Rect(m + i * (CELL + gap), h - m - CELL, CELL, CELL);
			}
		}
		int kidoTop = Math.min(k[0].y(), Math.min(k[1].y(), k[2].y()));
		int y = (h - CELL) / 2;
		y = Math.max(y, topReserve);
		y = Math.min(y, kidoTop - MIN_GAP_TO_KIDO - CELL);
		y = Math.max(y, 0);
		return new Result(List.of(k[0], k[1], k[2]), new Rect(m, y, CELL, CELL), stacked);
	}

	/** Scaled window size of a real window at a GUI scale (vanilla rounds the division up). */
	public static int scaled(int pixels, int guiScale) {
		return (int) Math.ceil(pixels / (double) guiScale);
	}
}
