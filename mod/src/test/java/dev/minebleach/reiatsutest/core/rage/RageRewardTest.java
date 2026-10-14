package dev.minebleach.reiatsutest.core.rage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** R11 review fix 2: the frost field pulses pay no Rage; kido and abilities still pay the cast reward. */
class RageRewardTest {
	@Test
	void anUnrewardedHitPaysNothingWhateverItsDamageType() {
		assertEquals(RageReward.NONE, RageReward.classify(true, true), "a field pulse is INDIRECT_MAGIC but unrewarded");
		assertEquals(RageReward.NONE, RageReward.classify(true, false));
	}

	@Test
	void abilityAndKidoStillPayTheCastReward() {
		assertEquals(RageReward.ABILITY, RageReward.classify(false, true));
	}

	@Test
	void swordHitsStillPayTheDealtShare() {
		assertEquals(RageReward.DEALT, RageReward.classify(false, false));
	}

	@Test
	void theFieldUsesTheUnrewardedPathAndKidoDoesNot() throws IOException {
		Path main = Paths.get("src", "main", "java", "dev", "minebleach", "reiatsutest", "server");
		String field = Files.readString(main.resolve("BankaiField.java"), StandardCharsets.UTF_8);
		assertTrue(field.contains("Targeting.hurtUnrewarded("), "the pulses are marked");
		assertTrue(!field.contains("Targeting.hurtPlain("));
		String kido = Files.readString(main.resolve("KidoManager.java"), StandardCharsets.UTF_8);
		assertTrue(kido.contains("Targeting.hurtPlain(") && !kido.contains("hurtUnrewarded"), "kido keeps paying the cast reward (intended)");
		String rage = Files.readString(main.resolve("RageManager.java"), StandardCharsets.UTF_8);
		assertTrue(rage.contains("RageReward.classify(") && rage.contains("noRewardDepth"));
		String target = Files.readString(main.resolve("Targeting.java"), StandardCharsets.UTF_8);
		assertTrue(target.contains("RageManager.beginUnrewarded()") && target.contains("RageManager.endUnrewarded()"));
	}
}
