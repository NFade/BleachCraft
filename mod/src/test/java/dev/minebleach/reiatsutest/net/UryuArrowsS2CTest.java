package dev.minebleach.reiatsutest.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.uryu.BindEnd;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** S-U2 (FOCUS with a caster id for the trackers) and S-U3 (BIND and END of the Sprenger roots): the packet contents. */
class UryuArrowsS2CTest {
	@Test
	void everyPacketKindHasItsOwnNumber() {
		byte[] kinds = {UryuArrowsS2C.ARROWS, UryuArrowsS2C.BURST, UryuArrowsS2C.LINE, UryuArrowsS2C.TRAP, UryuArrowsS2C.IMPACT, UryuArrowsS2C.FOCUS,
				UryuArrowsS2C.SLASH, UryuArrowsS2C.BIND, UryuArrowsS2C.END};
		Set<Byte> seen = new HashSet<>();
		for (byte k : kinds) {
			assertTrue(seen.add(k), "duplicate kind " + k);
		}
		assertEquals(7, UryuArrowsS2C.BIND);
		assertEquals(8, UryuArrowsS2C.END);
	}

	@Test
	void focusCarriesTheStacksAndTheCasterId() {
		UryuArrowsS2C p = UryuArrowsS2C.focus(412, 2, 1000);
		assertEquals(UryuArrowsS2C.FOCUS, p.kind());
		assertEquals(1000, p.tick());
		assertArrayEquals(new int[] {2, 412}, p.ids());
		assertEquals(2, p.focusStacks());
		assertEquals(412, p.focusCaster(-1));
		assertEquals(0, p.data().length);
	}

	@Test
	void anOldFocusPacketWithoutCasterIsTheReceiversOwn() {
		UryuArrowsS2C old = new UryuArrowsS2C(UryuArrowsS2C.FOCUS, 5, new int[] {3}, new float[0]);
		assertEquals(3, old.focusStacks());
		assertEquals(77, old.focusCaster(77), "no caster id: the fallback (the local player) is used");
		UryuArrowsS2C empty = new UryuArrowsS2C(UryuArrowsS2C.FOCUS, 5, new int[0], new float[0]);
		assertEquals(0, empty.focusStacks());
	}

	@Test
	void focusStacksAreClampedToThreeAndZero() {
		assertEquals(3, UryuArrowsS2C.focus(1, 9, 0).focusStacks());
		assertEquals(0, UryuArrowsS2C.focus(1, -4, 0).focusStacks());
		assertEquals(0, UryuArrowsS2C.focus(1, 0, 0).focusStacks(), "0 is sent too: it clears the Focus feedback of the others");
	}

	@Test
	void bindListsTheRootedEntitiesTheCentreAndTheTicksLeft() {
		UryuArrowsS2C p = UryuArrowsS2C.bind(2000, new int[] {5, 9, 12}, 10.5, 64.0, -3.25, 24);
		assertEquals(UryuArrowsS2C.BIND, p.kind());
		assertEquals(2000, p.tick());
		assertArrayEquals(new int[] {5, 9, 12}, p.ids());
		assertArrayEquals(new float[] {10.5f, 64.0f, -3.25f, 24f}, p.data());
		assertEquals(2024, p.tick() + Math.round(p.data()[3]), "the absolute until tick is the packet tick plus the ticks left");
	}

	@Test
	void endListsTheFreedEntitiesAndWhy() {
		for (BindEnd why : BindEnd.values()) {
			UryuArrowsS2C p = UryuArrowsS2C.end(2010, new int[] {5}, 1, 2, 3, why);
			assertEquals(UryuArrowsS2C.END, p.kind());
			assertArrayEquals(new int[] {5}, p.ids());
			assertEquals(4, p.data().length);
			assertEquals(why, BindEnd.fromCode(Math.round(p.data()[3])));
		}
	}
}
