package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The table of the Uryu animations against the shipped files and the gesture ids of the game (U-FX 11). */
class UryuAnimsTest {
	/** End ticks written by tools/gen_uryu_poses.py (storyboard 3.9, with the extra animations of the ability poses). */
	private static final Map<String, Integer> END = Map.ofEntries(Map.entry("idle", 20), Map.entry("draw", 20), Map.entry("release", 14),
			Map.entry("release_z", 10), Map.entry("seele", 26), Map.entry("blut", 34), Map.entry("vollstandig", 40), Map.entry("licht_regen", 96),
			Map.entry("sternenstaub", 60), Map.entry("wings", 130), Map.entry("sprenger", 52));

	@Test
	void everyAnimationHasItsFileAndTheEndTickOfTheGenerator() {
		assertEquals(END.keySet(), Map.copyOf(UryuAnims.all()).keySet(), "the table and the generator list the same animations");
		for (String a : UryuAnims.names()) {
			UryuPoseTrack t = UryuTestData.track(a);
			assertEquals("uryu_" + a, t.name());
			assertEquals(END.get(a), t.endTick(), a);
			assertTrue(t.stopTick() >= t.endTick(), a);
		}
	}

	@Test
	void everyAbilityOfUryuStartsItsAnimation() {
		int n = 0;
		for (AbilityId a : AbilityId.values()) {
			if (a.character != CharacterId.URYU) {
				continue;
			}
			n++;
			UryuAnims.Meta m = UryuAnims.forGesture(a.commandId);
			assertNotNull(m, a.commandId);
			assertEquals(a.commandId, m.gesture());
			assertNotNull(CastGestures.defaults().get(a.commandId), "the first person gesture of " + a.commandId);
		}
		assertEquals(6, n);
	}

	@Test
	void theTwoReleaseEventsStartTheirAnimations() {
		assertEquals(UryuAnims.RELEASE_SHIKAI, CastGestures.idForEffect(EffectIds.URYU_SHIKAI_RELEASE));
		assertEquals(UryuAnims.RELEASE_BANKAI, CastGestures.idForEffect(EffectIds.URYU_BANKAI_RELEASE));
		assertEquals("blut", UryuAnims.forGesture(UryuAnims.RELEASE_SHIKAI).anim());
		assertEquals("vollstandig", UryuAnims.forGesture(UryuAnims.RELEASE_BANKAI).anim());
		assertNotNull(CastGestures.defaults().get(UryuAnims.RELEASE_SHIKAI));
		assertNotNull(CastGestures.defaults().get(UryuAnims.RELEASE_BANKAI));
		// the cast start (78) has no gesture of its own: the third person animation is started by it in UryuBowMotion, the first person rows at the release
		assertNull(CastGestures.idForEffect(EffectIds.URYU_CAST_START));
		assertNull(UryuAnims.forGesture(null));
		assertNull(UryuAnims.forGesture("rukia.shikai.release"));
	}

	@Test
	void theEventTickIsInsideTheAnimationAndTheStartTickBeforeIt() {
		for (UryuAnims.Meta m : UryuAnims.all().values()) {
			int end = UryuTestData.endTick(m.anim());
			assertTrue(m.eventTick() >= 0 && m.eventTick() <= end, m.anim());
			assertTrue(m.startTick() >= 0 && m.startTick() <= m.eventTick(), m.anim());
			assertTrue(m.look() == 0.0 || m.look() == 1.0, m.anim());
			assertTrue(m.axis() >= 0.0 && m.axis() <= 1.0, m.anim());
		}
		// the burst of the two releases, the slashes and the release of the aimed abilities are the key ticks of the storyboard
		assertEquals(12, UryuAnims.meta("blut").eventTick());
		assertEquals(13, UryuAnims.meta("vollstandig").eventTick());
		assertEquals(12, UryuAnims.meta("licht_regen").eventTick());
		assertEquals(16, UryuAnims.meta("sternenstaub").eventTick());
	}

	@Test
	void theAimedAnimationsFollowTheCameraAndTheBow() {
		for (String a : new String[] {"draw", "release", "release_z", "sternenstaub"}) {
			assertEquals(1.0, UryuAnims.meta(a).look(), a);
			assertEquals(1.0, UryuAnims.meta(a).axis(), a);
		}
		for (String a : new String[] {"idle", "blut", "vollstandig", "wings", "seele", "sprenger"}) {
			assertEquals(0.0, UryuAnims.meta(a).look(), a);
		}
	}
}
