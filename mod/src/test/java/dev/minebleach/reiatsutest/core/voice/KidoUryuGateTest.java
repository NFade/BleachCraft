package dev.minebleach.reiatsutest.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import org.junit.jupiter.api.Test;

/** Uryu has no kido: with his cross in hand the kido phrases are not considered, in any state; his own phrases still fire. */
class KidoUryuGateTest {
	private final PhraseMatcher m = VoiceTestSupport.matcher();

	@Test
	void kidoCommandsAreInvalidWithTheQuincyCross() {
		PhraseBook book = VoiceTestSupport.book();
		for (KidoId k : KidoId.values()) {
			PhraseBook.Command c = book.command(k.commandId);
			for (ZanpakutoState st : ZanpakutoState.values()) {
				for (boolean full : new boolean[] {false, true}) {
					assertFalse(c.validIn(GateContext.of(CharacterId.URYU, st, full)), k.commandId + " " + st);
					assertTrue(c.validIn(GateContext.of(CharacterId.RUKIA, st, full)), k.commandId + " stays for Rukia in " + st);
					assertTrue(c.validIn(GateContext.of(CharacterId.NONE, st, full)), k.commandId + " stays with empty hands in " + st);
				}
			}
			assertTrue(c.validIn(GateContext.ungated()), "the test mode still considers every command");
		}
	}

	@Test
	void theKidoPhrasesDoNotFireForUryuInAnyState() {
		for (ZanpakutoState st : ZanpakutoState.values()) {
			for (String say : new String[] {"Hado no yon Byakurai", "Byakurai", "Shakkaho", "Hado 31", "Bakudo ichi Sai", "Bakudo 1"}) {
				PhraseMatcher.Result r = m.match(say, GateContext.of(CharacterId.URYU, st, true));
				assertFalse(r.matched() && r.commandId().startsWith("kido."), say + " in " + st + " fired " + r.commandId());
			}
		}
	}

	@Test
	void theKidoPhrasesStillFireForTheOthers() {
		assertEquals("kido.byakurai", m.match("Byakurai", GateContext.of(CharacterId.RUKIA, ZanpakutoState.SHIKAI, true)).commandId());
		assertEquals("kido.shakkaho", m.match("Shakkaho", GateContext.of(CharacterId.NONE, ZanpakutoState.SEALED, false)).commandId());
	}

	@Test
	void uryusOwnPhrasesStillFire() {
		assertEquals("uryu.shikai.release", m.match("Blut Arterie", GateContext.of(CharacterId.URYU, ZanpakutoState.BASE, false)).commandId());
		assertEquals("uryu.bankai.release",
				m.match("Quincy Vollstandig", GateContext.of(CharacterId.URYU, ZanpakutoState.SHIKAI, true)).commandId());
	}
}
