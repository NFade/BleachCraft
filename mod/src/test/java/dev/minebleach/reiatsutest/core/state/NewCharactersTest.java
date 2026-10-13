package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.combat.CastGestures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Registration skeleton of Gin Ichimaru and Uryu Ishida (design/GIN.md, design/URYU.md): types, specs, casts, effect and command ids. */
class NewCharactersTest {
	private static final CharacterId GIN = CharacterId.GIN;
	private static final CharacterId URYU = CharacterId.URYU;
	private static final BalanceConfig CFG = BalanceConfig.defaults();

	/** ability, slot, cost in reiatsu tenths, cooldown in ticks (design tables: 10 reiatsu = 100 tenths, 4 s = 80 ticks). */
	private record Row(AbilityId id, String command, CharacterId who, ZanpakutoState state, int slot, int cost, int cooldown, int effect) {
	}

	private static final List<Row> ROWS = List.of(
			new Row(AbilityId.IKOROSE, "gin.shikai.ikorose", GIN, ZanpakutoState.SHIKAI, 0, 100, 80, 62),
			new Row(AbilityId.HYAPPONZASHI, "gin.shikai.hyapponzashi", GIN, ZanpakutoState.SHIKAI, 1, 180, 180, 63),
			new Row(AbilityId.SNAKE_HOOK, "gin.shikai.snake_hook", GIN, ZanpakutoState.SHIKAI, 2, 120, 160, 64),
			new Row(AbilityId.BUTOU_RENJIN, "gin.bankai.butou_renjin", GIN, ZanpakutoState.BANKAI, 0, 280, 280, 65),
			new Row(AbilityId.KOROSE, "gin.bankai.korose", GIN, ZanpakutoState.BANKAI, 1, 250, 240, 66),
			new Row(AbilityId.HAKUFUKU, "gin.bankai.hakufuku", GIN, ZanpakutoState.BANKAI, 2, 150, 360, 67),
			new Row(AbilityId.HEILIG_PFEIL, "uryu.shikai.heilig_pfeil", URYU, ZanpakutoState.SHIKAI, 0, 120, 60, 72),
			new Row(AbilityId.SPRENGER, "uryu.shikai.sprenger", URYU, ZanpakutoState.SHIKAI, 1, 250, 280, 73),
			new Row(AbilityId.HIRENKYAKU, "uryu.shikai.hirenkyaku", URYU, ZanpakutoState.SHIKAI, 2, 100, 120, 74),
			new Row(AbilityId.LICHT_REGEN, "uryu.bankai.licht_regen", URYU, ZanpakutoState.BANKAI, 0, 280, 320, 75),
			new Row(AbilityId.HOMING_VOLLEY, "uryu.bankai.homing_volley", URYU, ZanpakutoState.BANKAI, 1, 300, 320, 76),
			new Row(AbilityId.STERNENSTAUB, "uryu.bankai.sternenstaub", URYU, ZanpakutoState.BANKAI, 2, 500, 800, 77));

	/** A machine of the character in the state of the row (bankai through the real release, then the settle lock is waited out). */
	private static Fx fx(CharacterId who, ZanpakutoState state) {
		Fx f = new Fx();
		f.toShikai(who);
		if (state == ZanpakutoState.BANKAI) {
			f.toBankai(who);
			f.adv(f.cfg.settleTicks() + 1);
		}
		f.sm.devSetReiatsu(f.cfg.maxTenths());
		return f;
	}

	@Test
	void charactersAndAbilitiesAreRegisteredWithDistinctCodes() {
		assertEquals(3, GIN.code);
		assertEquals(4, URYU.code);
		assertEquals(GIN, CharacterId.fromCode((byte) 3));
		assertEquals(URYU, CharacterId.fromCode((byte) 4));
		assertEquals("gin", GIN.langKey());
		assertEquals("uryu", URYU.langKey());
		assertEquals("rukia", CharacterId.NONE.langKey(), "the HUD fallback stays Rukia");
		for (Row r : ROWS) {
			assertEquals(r.command, r.id.commandId);
			assertEquals(r.who, r.id.character);
			assertEquals(r.state, r.id.requiredState);
			assertEquals(r.slot, r.id.slot(), r.command);
			assertEquals(r.id, AbilityId.fromCode(r.id.code), r.command);
			assertEquals(r.id, AbilityId.fromCommandId(r.command));
			assertEquals(r.id, AbilityId.forSlot(r.who, r.state, r.slot));
			assertEquals(r.effect, CFG.spec(r.id).effectId(), r.command);
		}
		// the codes after the old ones are 12 to 23 in order, 4 stays unused (Rukia)
		assertNull(AbilityId.fromCode((byte) 4));
		assertEquals(12, AbilityId.IKOROSE.code);
		assertEquals(23, AbilityId.STERNENSTAUB.code);
	}

	@Test
	void everyNewAbilityIsCastableInItsStateWithItsCharacter() {
		for (Row r : ROWS) {
			Fx f = fx(r.who, r.state);
			int before = f.sm.reiatsu().value();
			TransitionResult res = f.cast(r.id, r.who);
			assertTrue(res.ok(), r.command + " " + res.reason());
			assertEquals(before - r.cost, f.sm.reiatsu().value(), r.command + " cost");
			assertEquals(r.cooldown, f.sm.cooldownRemaining(r.id), r.command + " cooldown");
			assertEquals(r.effect, Fx.events(res.events(), StateEvent.BroadcastEffect.class).get(0).effectId(), r.command);
		}
	}

	@Test
	void specsMatchTheTablesAndCostsArePositive() {
		for (Row r : ROWS) {
			AbilitySpec s = CFG.spec(r.id);
			assertNotNull(s, r.command);
			assertEquals(r.cost, s.costTenths(), r.command);
			assertEquals(r.cooldown, s.cooldownTicks(), r.command);
			assertTrue(s.costTenths() > 0, r.command + ": only the Byakuya bankai is free");
			assertTrue(s.enabled(), r.command);
		}
	}

	@Test
	void wrongCharacterWrongStateAndSealedAreDenied() {
		for (Row r : ROWS) {
			Fx f = fx(r.who, r.state);
			// every other held item is the wrong item for the ability
			for (CharacterId other : new CharacterId[] {CharacterId.RUKIA, CharacterId.BYAKUYA, GIN, URYU, CharacterId.NONE}) {
				if (other == r.who) {
					continue;
				}
				assertEquals(RejectReason.WRONG_ITEM, f.cast(r.id, other).reason(), r.command + " held by " + other);
			}
			// nothing was charged by the denied casts
			assertEquals(0, f.sm.cooldownRemaining(r.id), r.command);
			assertEquals(f.cfg.maxTenths(), f.sm.reiatsu().value(), r.command);
		}
		// an ability does not run sealed, drawn only, or in the other state
		Fx sealed = new Fx();
		assertEquals(RejectReason.NOT_IN_STATE, sealed.cast(AbilityId.IKOROSE, GIN).reason());
		Fx base = new Fx().toBase(URYU);
		assertEquals(RejectReason.NOT_IN_STATE, base.cast(AbilityId.HEILIG_PFEIL, URYU).reason(), "BASE has no abilities");
		Fx gin = new Fx().toShikai(GIN);
		assertEquals(RejectReason.NOT_IN_STATE, gin.cast(AbilityId.BUTOU_RENJIN, GIN).reason(), "bankai ability in shikai");
		Fx uryuBankai = fx(URYU, ZanpakutoState.BANKAI);
		assertEquals(RejectReason.NOT_IN_STATE, uryuBankai.cast(AbilityId.IKOROSE, URYU).reason(), "other character's ability");
	}

	@Test
	void releaseEffectsAndCommandsDoNotFallBackToRukia() {
		for (CharacterId c : new CharacterId[] {GIN, URYU}) {
			int s = EffectIds.shikaiRelease(c);
			int b = EffectIds.bankaiRelease(c);
			assertTrue(s != EffectIds.RUKIA_SHIKAI_RELEASE && b != EffectIds.RUKIA_BANKAI_RELEASE, c.name());
			assertTrue(s != EffectIds.BYAKUYA_SHIKAI_RELEASE && b != EffectIds.BYAKUYA_BANKAI_RELEASE, c.name());
			assertTrue(CommandIds.forTransition(c, ZanpakutoState.SHIKAI).startsWith(c.langKey() + ".shikai"));
			assertTrue(CommandIds.forTransition(c, ZanpakutoState.BANKAI).startsWith(c.langKey() + ".bankai"));
		}
		assertEquals(60, EffectIds.GIN_SHIKAI_RELEASE);
		assertEquals(61, EffectIds.GIN_BANKAI_RELEASE);
		assertEquals(70, EffectIds.URYU_SHIKAI_RELEASE);
		assertEquals(71, EffectIds.URYU_BANKAI_RELEASE);
		// the old two keep their ids
		assertEquals(EffectIds.RUKIA_SHIKAI_RELEASE, EffectIds.shikaiRelease(CharacterId.RUKIA));
		assertEquals(EffectIds.BYAKUYA_BANKAI_RELEASE, EffectIds.bankaiRelease(CharacterId.BYAKUYA));
		assertEquals(CommandIds.BYAKUYA_SHIKAI_RELEASE, CommandIds.forTransition(CharacterId.BYAKUYA, ZanpakutoState.SHIKAI));
		assertEquals(CommandIds.RUKIA_BANKAI_RELEASE, CommandIds.forTransition(CharacterId.RUKIA, ZanpakutoState.BANKAI));
	}

	@Test
	void allEffectIdsArePairwiseDifferentAndTheGestureLookupKnowsThem() {
		Map<Integer, String> seen = new HashMap<>();
		for (AbilityId a : AbilityId.values()) {
			assertNull(seen.put(CFG.spec(a).effectId(), a.commandId), "duplicate effect id of " + a);
		}
		for (int id : new int[] {EffectIds.GIN_SHIKAI_RELEASE, EffectIds.GIN_BANKAI_RELEASE, EffectIds.URYU_SHIKAI_RELEASE,
				EffectIds.URYU_BANKAI_RELEASE}) {
			assertNull(seen.put(id, "release"), "release effect id " + id + " is taken");
		}
		for (Row r : ROWS) {
			assertEquals(r.command, CastGestures.idForEffect(r.effect));
		}
	}

	@Test
	void everySlotOfTheNewCharactersIsFilledOnceInShikaiAndBankai() {
		for (CharacterId c : new CharacterId[] {GIN, URYU}) {
			for (ZanpakutoState st : new ZanpakutoState[] {ZanpakutoState.SHIKAI, ZanpakutoState.BANKAI}) {
				for (int slot = 0; slot < 3; slot++) {
					assertNotNull(AbilityId.forSlot(c, st, slot), c + " " + st + " slot " + slot);
				}
			}
			assertNull(AbilityId.forSlot(c, ZanpakutoState.BASE, 0), "BASE has no abilities");
		}
	}
}
