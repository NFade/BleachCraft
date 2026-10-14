package dev.minebleach.reiatsutest.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * 16 x 16 pixel icons of the side HUD (kido spells and shunpo), drawn with solid rectangles through {@link HudGfx#fill}: the
 * ability atlas has no cells for them and no new binary texture is added. Each icon is a character grid turned once into
 * horizontal runs; a run is one quad. 'k' is the ink outline (#0B0F1A), '.' is transparent.
 */
final class SideHudIcons {
	static final int SIZE = 16;

	static final Icon BYAKURAI = Icon.of(new String[] {
			"................",
			"..........kkkk..",
			".........kwwwbk.",
			"........kwwwbk..",
			".......kwwwbk...",
			"......kwwwbk....",
			".....kwwwbkkkk..",
			"....kwwwwwwwwbk.",
			"....kkkkkkwwbk..",
			"..........kwbk..",
			".........kwbk...",
			"........kwbk....",
			".......kwbk.....",
			"......kwk.......",
			".....kwk........",
			"......k........."},
			'k', 0x0B0F1A, 'w', 0xFFFFFF, 'b', 0xCFEFFF);

	static final Icon SHAKKAHO = Icon.of(new String[] {
			"................",
			"................",
			"..k.............",
			"..rk............",
			"...rrk..........",
			"....rrk.kkkkk...",
			".....rkkrooorkk.",
			".....krooyyoork.",
			".....kooywwyook.",
			".....kooywwyook.",
			".....krooyyoork.",
			"......kkrooorkk.",
			"........kkkkk...",
			"................",
			"................",
			"................"},
			'k', 0x0B0F1A, 'r', 0xA81E1E, 'o', 0xFF7A2A, 'y', 0xFFD860, 'w', 0xFFF6D8);

	static final Icon SAI = Icon.of(new String[] {
			"................",
			"................",
			"................",
			".....kkkkkk.....",
			"....kyyyyyyk....",
			"...kyykkkkyyk...",
			"..kyykp..pkyyk..",
			"..kyk..pp..kyk..",
			"..kyk..pp..kyk..",
			"..kyykp..pkyyk..",
			"...kyykkkkyyk...",
			"....kyyyyyyk....",
			".....kkkkkk.....",
			"................",
			"................",
			"................"},
			'k', 0x0B0F1A, 'y', 0xE8C66A, 'p', 0xB38CFF);

	/** Three chevrons with fading afterimages; white and pale blue. */
	static final Icon SHUNPO = chevrons(0xFFFFFF, 0xCFEFFF, 0x7FB8DF);
	/** Same shape in the blue of the Quincy flash step (Hirenkyaku). */
	static final Icon SHUNPO_URYU = chevrons(0xE6F0FF, 0x8FB8FF, 0x3F6FE0);

	private SideHudIcons() {
	}

	/** One horizontal run of one colour: x, y, length, rgb. */
	record Run(int x, int y, int len, int rgb) {
	}

	static final class Icon {
		private final List<Run> runs;

		private Icon(List<Run> runs) {
			this.runs = runs;
		}

		/** Draws at (x, y) of the current matrix; {@code brightness} scales the colour, {@code alpha} is the opacity. */
		void draw(HudGfx g, int x, int y, float brightness, float alpha) {
			int a = PlateHud.alphaInt(alpha) << 24;
			for (Run r : runs) {
				int rgb = r.rgb();
				int cr = Math.min(255, Math.round(((rgb >> 16) & 255) * brightness));
				int cg = Math.min(255, Math.round(((rgb >> 8) & 255) * brightness));
				int cb = Math.min(255, Math.round((rgb & 255) * brightness));
				g.fill(x + r.x(), y + r.y(), x + r.x() + r.len(), y + r.y() + 1, a | cr << 16 | cg << 8 | cb);
			}
		}

		int runCount() {
			return runs.size();
		}

		static Icon of(String[] rows, Object... palette) {
			char[][] grid = new char[SIZE][SIZE];
			for (int y = 0; y < SIZE; y++) {
				String row = y < rows.length ? rows[y] : "";
				for (int x = 0; x < SIZE; x++) {
					grid[y][x] = x < row.length() ? row.charAt(x) : '.';
				}
			}
			return fromGrid(grid, palette);
		}

		private static Icon fromGrid(char[][] grid, Object[] palette) {
			List<Run> runs = new ArrayList<>();
			for (int y = 0; y < SIZE; y++) {
				int x = 0;
				while (x < SIZE) {
					char c = grid[y][x];
					int rgb = c == '.' ? -1 : lookup(palette, c);
					if (rgb < 0) {
						x++;
						continue;
					}
					int e = x + 1;
					while (e < SIZE && grid[y][e] == c) {
						e++;
					}
					runs.add(new Run(x, y, e - x, rgb));
					x = e;
				}
			}
			return new Icon(List.copyOf(runs));
		}

		private static int lookup(Object[] palette, char c) {
			for (int i = 0; i + 1 < palette.length; i += 2) {
				if ((Character) palette[i] == c) {
					return (Integer) palette[i + 1];
				}
			}
			return -1;
		}
	}

	/** Three '>' chevrons (front, middle, back), two pixels thick, with an ink outline added around everything. */
	private static Icon chevrons(int front, int mid, int back) {
		char[][] grid = new char[SIZE][SIZE];
		for (char[] row : grid) {
			java.util.Arrays.fill(row, '.');
		}
		int[] offsets = {0, -4, -8};
		char[] colors = {'w', 'b', 'c'};
		for (int i = 0; i < 3; i++) {
			for (int r = 4; r <= 12; r++) {
				int x = 9 + offsets[i] + 4 - Math.abs(r - 8);
				for (int t = 0; t < 2; t++) {
					if (x + t >= 0 && x + t < SIZE) {
						grid[r][x + t] = colors[i];
					}
				}
			}
		}
		char[][] out = new char[SIZE][SIZE];
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				out[y][x] = grid[y][x];
				if (grid[y][x] == '.' && (neighbour(grid, x - 1, y) || neighbour(grid, x + 1, y) || neighbour(grid, x, y - 1) || neighbour(grid, x, y + 1))) {
					out[y][x] = 'k';
				}
			}
		}
		return Icon.fromGrid(out, new Object[] {'k', 0x0B0F1A, 'w', front, 'b', mid, 'c', back});
	}

	private static boolean neighbour(char[][] grid, int x, int y) {
		return x >= 0 && y >= 0 && x < SIZE && y < SIZE && grid[y][x] != '.';
	}
}
