package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoRequest;
import dev.minebleach.reiatsutest.core.kido.KidoSpec;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * B5: the kido cast of the state machine. The spells need no state and no sword; cost, cooldown, the gap between two casts,
 * the reiatsu floor and the dead / spectator rule are checked here. Ability and kido cooldowns are independent.
 */
class KidoCastTest {
	private static TransitionResult kido(Fx f, KidoId k) {
		return f.sm.castKido(new KidoRequest(k, RequestSource.KEY, ++f.seq));
	}

	private static StateEvent.KidoCast cast(TransitionResult r) {
		List<StateEvent.KidoCast> l = Fx.events(r.events(), StateEvent.KidoCast.class);
		assertEquals(1, l.size());
		return l.get(0);
	}

	@Test
	void castsInSealedWithoutAnySword() {
		Fx f = new Fx();
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		TransitionResult r = kido(f, KidoId.BYAKURAI);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		assertEquals(KidoId.BYAKURAI, cast(r).kido());
		assertEquals(1000 - 100, f.sm.reiatsu().value());
		assertEquals(ZanpakutoState.SEALED, f.sm.state(), "a kido cast never changes the zanpakuto state");
		assertTrue(Fx.has(r.events(), StateEvent.ReiatsuSpent.class));
	}

	@Test
	void castsInEveryStateWithAnyHeldCharacter() {
		for (CharacterId who : new CharacterId[] {CharacterId.RUKIA, CharacterId.BYAKUYA}) {
			Fx f = new Fx();
			f.toBase(who);
			assertTrue(kido(f, KidoId.SAI).ok(), "BASE " + who);
			f.adv(120);
			f.tr(ZanpakutoState.SHIKAI, who);
			f.adv(f.cfg.transitionLockTicks() + 1);
			assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
			assertTrue(kido(f, KidoId.BYAKURAI).ok(), "SHIKAI " + who);
			f.adv(40);
			f.toBankai(who);
			f.adv(f.cfg.transitionLockTicks() + 1);
			assertEquals(ZanpakutoState.BANKAI, f.sm.state());
			assertTrue(kido(f, KidoId.SHAKKAHO).ok(), "BANKAI " + who);
			assertEquals(ZanpakutoState.BANKAI, f.sm.state());
		}
	}

	@Test
	void cooldownOfOneSpellDoesNotBlockTheOthersBeyondTheGap() {
		Fx f = new Fx();
		assertTrue(kido(f, KidoId.BYAKURAI).ok());
		assertEquals(ResultCode.COOLDOWN, kido(f, KidoId.SAI).code(), "inside the gap between two kido casts");
		f.adv(f.sm.kidoConfig().gcdTicks());
		assertTrue(kido(f, KidoId.SAI).ok(), "another spell right after the gap");
		f.adv(f.sm.kidoConfig().gcdTicks());
		TransitionResult again = kido(f, KidoId.BYAKURAI);
		assertEquals(RejectReason.ON_COOLDOWN, again.reason(), "the same spell is still cooling");
		f.adv(100);
		assertTrue(kido(f, KidoId.BYAKURAI).ok(), "after its cooldown of 100 ticks");
	}

	@Test
	void gapIsReportedAsGcd() {
		Fx f = new Fx();
		assertTrue(kido(f, KidoId.BYAKURAI).ok());
		assertEquals(RejectReason.GCD, kido(f, KidoId.SAI).reason());
	}

	@Test
	void cooldownIsReportedForTheSync() {
		Fx f = new Fx();
		kido(f, KidoId.SHAKKAHO);
		assertEquals(200, f.sm.kidoCooldownRemaining(KidoId.SHAKKAHO));
		assertEquals(0, f.sm.kidoCooldownRemaining(KidoId.SAI));
		f.adv(40);
		assertEquals(160, f.sm.kidoCooldownRemaining(KidoId.SHAKKAHO));
		assertEquals(List.of(KidoId.SHAKKAHO), List.copyOf(f.sm.kidoCooldownRemainingAll().keySet()));
		f.adv(160);
		assertTrue(f.sm.kidoCooldownRemainingAll().isEmpty());
	}

	@Test
	void kidoAndAbilityCooldownsAreIndependent() {
		Fx f = new Fx();
		f.toShikai(CharacterId.RUKIA);
		TransitionResult a = f.cast(AbilityId.SHIRAFUNE, CharacterId.RUKIA);
		assertTrue(a.ok());
		f.adv(f.cfg.gcdTicks() + 1);
		assertTrue(kido(f, KidoId.BYAKURAI).ok(), "kido while an ability is cooling");
		assertTrue(f.sm.cooldownRemaining(AbilityId.SHIRAFUNE) > 0);
		assertEquals(0, f.sm.cooldownRemaining(AbilityId.TSUKISHIRO));
	}

	@Test
	void notEnoughReiatsuAndTheFloorOfOnePoint() {
		Fx f = new Fx(100); // 10.0: Byakurai costs exactly 10.0 and may never take the bar to zero (R2)
		TransitionResult r = kido(f, KidoId.BYAKURAI);
		assertEquals(ResultCode.DENIED_REIATSU, r.code());
		assertEquals(100, f.sm.reiatsu().value(), "a refused cast costs nothing");
		assertEquals(0, f.sm.kidoCooldownRemaining(KidoId.BYAKURAI), "and starts no cooldown");
		Fx g = new Fx(101);
		assertTrue(kido(g, KidoId.BYAKURAI).ok());
		assertEquals(1, g.sm.reiatsu().value());
	}

	@Test
	void deadAndSpectatorCannotCast() {
		Fx f = new Fx();
		f.sm.setSpectator(true);
		assertEquals(RejectReason.DEAD_OR_SPECTATOR, kido(f, KidoId.SAI).reason());
		f.sm.setSpectator(false);
		f.sm.onDeath();
		assertEquals(RejectReason.DEAD_OR_SPECTATOR, kido(f, KidoId.SAI).reason());
	}

	@Test
	void deathClearsKidoCooldowns() {
		Fx f = new Fx();
		kido(f, KidoId.SAI);
		assertTrue(f.sm.kidoCooldownRemaining(KidoId.SAI) > 0);
		f.sm.onDeath();
		f.sm.onRespawn();
		assertEquals(0, f.sm.kidoCooldownRemaining(KidoId.SAI));
		f.adv(60);
		assertTrue(kido(f, KidoId.SAI).ok());
	}

	@Test
	void voiceSequenceSkipsTheStaleCheckButKeepsEveryOtherRule() {
		Fx f = new Fx();
		assertTrue(f.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.VOICE, StateMachine.SERVER_SEQ)).ok());
		f.adv(10);
		TransitionResult again = f.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.VOICE, StateMachine.SERVER_SEQ));
		assertEquals(RejectReason.ON_COOLDOWN, again.reason());
	}

	@Test
	void staleKeySequenceIsDropped() {
		Fx f = new Fx();
		assertTrue(f.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.KEY, 5)).ok());
		f.adv(120);
		assertEquals(RejectReason.STALE_SEQ, f.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.KEY, 5)).reason());
	}

	@Test
	void configurableSwitchesAndNumbers() {
		EnumMap<KidoId, KidoSpec> m = new EnumMap<>(KidoId.class);
		KidoConfig d = KidoConfig.defaults();
		for (KidoId k : KidoId.values()) {
			m.put(k, d.spec(k));
		}
		m.put(KidoId.SAI, d.spec(KidoId.SAI).withEnabled(false));
		m.put(KidoId.BYAKURAI, d.spec(KidoId.BYAKURAI).withCost(0).withCooldown(5));
		Fx f = new Fx();
		f.sm.setKidoConfig(new KidoConfig(true, 0, m));
		assertEquals(RejectReason.NOT_IN_STATE, kido(f, KidoId.SAI).reason(), "a disabled spell is refused");
		assertTrue(kido(f, KidoId.BYAKURAI).ok());
		assertEquals(1000, f.sm.reiatsu().value(), "free spell");
		f.adv(5);
		assertTrue(kido(f, KidoId.BYAKURAI).ok(), "cooldown 5 ticks and no gap");
		Fx off = new Fx();
		off.sm.setKidoConfig(new KidoConfig(false, 10, m));
		assertEquals(RejectReason.NOT_IN_STATE, kido(off, KidoId.BYAKURAI).reason(), "master switch");
		off.sm.setKidoConfig(null);
		assertTrue(kido(off, KidoId.BYAKURAI).ok(), "null means the defaults");
	}

	@Test
	void aKidoCastKeepsTheShikaiAlive() {
		Fx f = new Fx();
		f.toShikai(CharacterId.BYAKUYA);
		int idle = f.cfg.shikaiIdleTicks();
		f.adv(idle - 100);
		assertTrue(kido(f, KidoId.SAI).ok());
		f.adv(200);
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state(), "a cast counts as activity for the idle timer");
		assertFalse(f.sm.isDead());
	}
}
