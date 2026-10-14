package dev.minebleach.reiatsutest.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.hud.SideHudLayout.Rect;
import dev.minebleach.reiatsutest.core.hud.SideHudLayout.Result;
import org.junit.jupiter.api.Test;

/** Placement of the kido corner and the shunpo side cell at the GUI scales of VFX_STORYBOARD 7.9 and the four standard scales. */
class SideHudLayoutTest {
	/** Scaled sizes of the storyboard table 7.9: 1280x960 @4, 1280x720 @3, 1920x1080 @4, 1280x720 @2, 1920x1080 @2. */
	private static final int[][] STORYBOARD = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}};
	private static final int[][] WINDOWS = {{1280, 720}, {1366, 768}, {1920, 1080}, {2560, 1440}};
	private static final int TOP_BLOCK = 78; // plate block of the top left: y0 6 + denied toast 54 + 15 + slack

	private static Result layout(int w, int h) {
		return SideHudLayout.compute(w, h, SideHudLayout.compact(false, w), 0);
	}

	private static void forEachScreen(java.util.function.BiConsumer<Integer, Integer> check) {
		for (int[] s : STORYBOARD) {
			check.accept(s[0], s[1]);
		}
		for (int[] win : WINDOWS) {
			for (int scale = 1; scale <= 4; scale++) {
				check.accept(SideHudLayout.scaled(win[0], scale), SideHudLayout.scaled(win[1], scale));
			}
		}
	}

	@Test
	void kidoCellsAreInTheBottomLeftCornerSixPixelsFromTheEdges() {
		Result r = layout(480, 270);
		assertFalse(r.stacked());
		assertEquals(new Rect(6, 270 - 6 - 22, 22, 22), r.kido().get(0));
		assertEquals(new Rect(6 + 24, 242, 22, 22), r.kido().get(1));
		assertEquals(new Rect(6 + 48, 242, 22, 22), r.kido().get(2));
	}

	@Test
	void nothingOverlapsTheHotbarOrItsOffhandSlotAtAnyScale() {
		forEachScreen((w, h) -> {
			Result r = layout(w, h);
			Rect hb = SideHudLayout.hotbarWithOffhand(w, h);
			for (Rect k : r.kido()) {
				assertFalse(k.overlaps(hb), w + "x" + h + " kido " + k + " hotbar " + hb);
			}
			assertFalse(r.shunpo().overlaps(hb), w + "x" + h + " shunpo " + r.shunpo());
		});
	}

	@Test
	void everythingStaysOnScreenAndInsideTheLeftEdge() {
		forEachScreen((w, h) -> {
			Result r = layout(w, h);
			java.util.List<Rect> all = new java.util.ArrayList<>(r.kido());
			all.add(r.shunpo());
			for (Rect c : all) {
				assertTrue(c.x() >= 4 && c.y() >= 0 && c.right() <= w && c.bottom() <= h - 4, w + "x" + h + " " + c);
			}
		});
	}

	@Test
	void kidoRowStaysBelowTheChatLinesWheneverItIsARow() {
		forEachScreen((w, h) -> {
			Result r = layout(w, h);
			if (!r.stacked()) {
				for (Rect k : r.kido()) {
					assertTrue(k.y() >= h - SideHudLayout.CHAT_BOTTOM, w + "x" + h + " " + k);
				}
			}
		});
	}

	@Test
	void kidoCellsNeverOverlapEachOtherNorTheShunpoCell() {
		forEachScreen((w, h) -> {
			Result r = layout(w, h);
			for (int i = 0; i < 3; i++) {
				assertFalse(r.kido().get(i).overlaps(r.shunpo()), w + "x" + h);
				for (int j = i + 1; j < 3; j++) {
					assertFalse(r.kido().get(i).overlaps(r.kido().get(j)), w + "x" + h);
				}
			}
		});
	}

	@Test
	void shunpoCellIsVerticallyCenteredAtTheLeftEdge() {
		forEachScreen((w, h) -> {
			Result r = layout(w, h);
			Rect s = r.shunpo();
			assertEquals(SideHudLayout.margin(SideHudLayout.compact(false, w)), s.x(), w + "x" + h);
			assertTrue(Math.abs((s.y() + s.h() / 2) - h / 2) <= 1, w + "x" + h + " " + s);
		});
	}

	@Test
	void shunpoMovesBelowTheTopLeftBlockOnlyWhenNeeded() {
		Result free = SideHudLayout.compute(480, 270, false, TOP_BLOCK);
		assertEquals(124, free.shunpo().y()); // centred: (270 - 22) / 2
		Result pushed = SideHudLayout.compute(427, 200, false, TOP_BLOCK + 40);
		assertEquals(TOP_BLOCK + 40, pushed.shunpo().y());
		assertFalse(pushed.shunpo().overlaps(pushed.kido().get(0)));
	}

	@Test
	void narrowWindowStacksTheKidoCellsInAColumnTopToBottom() {
		Result r = layout(320, 240);
		assertTrue(r.stacked());
		assertEquals(r.kido().get(0).x(), r.kido().get(2).x());
		assertTrue(r.kido().get(0).y() < r.kido().get(1).y() && r.kido().get(1).y() < r.kido().get(2).y());
		assertEquals(240 - 4, r.kido().get(2).bottom()); // compact margin
	}

	@Test
	void rowFitsOnWideWindowsOnly() {
		assertTrue(SideHudLayout.rowFits(480, false));
		assertTrue(SideHudLayout.rowFits(427, SideHudLayout.compact(false, 427)));
		assertFalse(SideHudLayout.rowFits(320, true));
		assertTrue(SideHudLayout.compact(true, 900));
		assertTrue(SideHudLayout.compact(false, 359));
		assertFalse(SideHudLayout.compact(false, 360));
	}

	@Test
	void fourStandardGuiScalesOnFullHdGiveARowAtScalesOneToFour() {
		for (int scale = 1; scale <= 4; scale++) {
			int w = SideHudLayout.scaled(1920, scale);
			int h = SideHudLayout.scaled(1080, scale);
			assertFalse(layout(w, h).stacked(), "scale " + scale);
		}
	}
}
