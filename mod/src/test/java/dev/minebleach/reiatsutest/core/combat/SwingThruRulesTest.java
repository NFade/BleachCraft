package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R11 T11. */
class SwingThruRulesTest {
	@Test
	void grassWithAZanpakutoSwingsInsteadOfBreaking() {
		assertTrue(SwingThruRules.shouldSwing(true, true, 0.0F));
	}

	@Test
	void otherItemsAreUntouched() {
		assertFalse(SwingThruRules.shouldSwing(false, true, 0.0F));
	}

	@Test
	void solidOrHarderBlocksCanStillBeDug() {
		assertFalse(SwingThruRules.shouldSwing(true, false, 0.0F), "a block you collide with");
		assertFalse(SwingThruRules.shouldSwing(true, true, 0.2F), "vines, snow layers and the like");
		assertFalse(SwingThruRules.shouldSwing(true, false, 1.5F), "stone");
	}

	@Test
	void unbreakableIsNotAGrassCase() {
		assertFalse(SwingThruRules.shouldSwing(true, true, -1.0F));
		assertFalse(SwingThruRules.shouldSwing(true, true, Float.NaN));
	}
}
