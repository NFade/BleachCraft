package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** S-U3: the reason codes of the END packet of a Sprenger root. */
class BindEndTest {
	@Test
	void theFourReasonsHaveDistinctStableCodes() {
		assertEquals(0, BindEnd.EXPIRE.code);
		assertEquals(1, BindEnd.DAMAGE.code);
		assertEquals(2, BindEnd.EXPLOSION.code);
		assertEquals(3, BindEnd.CANCEL.code);
		Set<Integer> seen = new HashSet<>();
		for (BindEnd e : BindEnd.values()) {
			assertTrue(seen.add(e.code), "duplicate code " + e);
		}
		assertEquals(4, seen.size());
	}

	@Test
	void everyCodeRoundTripsAndAnUnknownOneIsACancel() {
		for (BindEnd e : BindEnd.values()) {
			assertEquals(e, BindEnd.fromCode(e.code));
		}
		assertEquals(BindEnd.CANCEL, BindEnd.fromCode(-1));
		assertEquals(BindEnd.CANCEL, BindEnd.fromCode(99));
	}
}
