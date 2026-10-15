package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every first person gesture row of Uryu has its third person animation file, and the other way round. */
class UryuGestureCoverageTest {
	@Test
	void everyUryuGestureRowHasAnAnimationOrIsTheDrawStateMachine() {
		Map<String, CastGesture> rows = new HashMap<>();
		UryuGestures.addTo(rows);
		assertTrue(rows.size() >= 10, "rows: " + rows.keySet());
		Set<String> withAnim = new HashSet<>();
		for (String id : rows.keySet()) {
			if (id.equals(UryuGestures.DRAW) || id.equals(UryuGestures.RELEASE)) {
				// sampled by the pull state machine (animations draw and release)
				assertNotNull(UryuAnims.meta(UryuAnims.DRAW));
				assertNotNull(UryuAnims.meta(UryuAnims.RELEASE));
				continue;
			}
			UryuAnims.Meta m = UryuAnims.forGesture(id);
			assertNotNull(m, "no third person animation for the gesture " + id);
			assertNotNull(UryuTestData.track(m.anim()), id);
			withAnim.add(m.gesture());
		}
		// every animation that is started by a gesture has the row of that gesture
		for (UryuAnims.Meta m : UryuAnims.all().values()) {
			if (m.gesture() != null) {
				assertTrue(rows.containsKey(m.gesture()), "no first person row for " + m.gesture());
				assertEquals(m.gesture(), UryuAnims.forGesture(m.gesture()).gesture());
			}
		}
		assertEquals(UryuAnims.all().values().stream().filter(m -> m.gesture() != null).count(), withAnim.size());
	}
}
