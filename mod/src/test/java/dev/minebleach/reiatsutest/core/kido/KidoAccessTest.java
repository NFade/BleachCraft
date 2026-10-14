package dev.minebleach.reiatsutest.core.kido;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.CharacterId;
import org.junit.jupiter.api.Test;

/** Uryu (a Quincy) has no kido; everybody else keeps the three spells. */
class KidoAccessTest {
	@Test
	void uryuHasNoKido() {
		assertFalse(KidoAccess.allowed(CharacterId.URYU));
		assertFalse(KidoAccess.allowed(CharacterId.URYU, CharacterId.NONE), "his cross in hand, sealed");
		assertFalse(KidoAccess.allowed(CharacterId.NONE, CharacterId.URYU), "his state active");
		assertFalse(KidoAccess.allowed(CharacterId.RUKIA, CharacterId.URYU), "his state active, another sword in hand");
		assertFalse(KidoAccess.allowed(CharacterId.URYU, CharacterId.URYU));
	}

	@Test
	void everybodyElseKeepsKido() {
		for (CharacterId c : CharacterId.values()) {
			if (c != CharacterId.URYU) {
				assertTrue(KidoAccess.allowed(c), c.name());
				assertTrue(KidoAccess.allowed(c, c), c.name());
				assertTrue(KidoAccess.allowed(c, CharacterId.NONE), c.name());
			}
		}
		assertTrue(KidoAccess.allowed(CharacterId.NONE, CharacterId.NONE), "empty hands");
	}

	@Test
	void kidoCommandIdsAreRecognised() {
		for (KidoId k : KidoId.values()) {
			assertTrue(KidoAccess.isKidoCommand(k.commandId), k.commandId);
		}
		assertFalse(KidoAccess.isKidoCommand("uryu.shikai.release"));
		assertFalse(KidoAccess.isKidoCommand("common.seal"));
		assertFalse(KidoAccess.isKidoCommand(null));
	}
}
