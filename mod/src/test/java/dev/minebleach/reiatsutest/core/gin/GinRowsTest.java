package dev.minebleach.reiatsutest.core.gin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.Fx;
import dev.minebleach.reiatsutest.core.state.RejectReason;
import dev.minebleach.reiatsutest.core.state.TransitionResult;
import dev.minebleach.reiatsutest.core.state.StateEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The numbers of the Gin rows of {@link BalanceConfig} (design/GIN.md section 4) and the cooldown seam of the recast. */
class GinRowsTest {
	private static final CharacterId GIN = CharacterId.GIN;
	private static final BalanceConfig CFG = BalanceConfig.defaults();

	private static AbilitySpec row(AbilityId a) {
		return CFG.spec(a);
	}

	@Test
	void ikorose() {
		AbilitySpec s = row(AbilityId.IKOROSE);
		assertEquals(14.0, s.num(AbilityParams.LENGTH));
		assertEquals(0.6, s.num(AbilityParams.HALF_WIDTH) * 2, 1e-9, "width 0.6");
		assertEquals(5.0f, s.floatNum(AbilityParams.DAMAGE));
		assertEquals(1.5, s.num(AbilityParams.KNOCKBACK));
		assertEquals(8, s.intNum(GinParams.ROOT_TICKS), "rooted 0.4 s");
		assertEquals(100, s.costTenths());
		assertEquals(80, s.cooldownTicks());
	}

	@Test
	void hyapponzashi() {
		AbilitySpec s = row(AbilityId.HYAPPONZASHI);
		assertEquals(8.0, s.num(AbilityParams.RADIUS));
		assertEquals(120.0, s.num(GinParams.ARC_DEGREES));
		assertEquals(3.0f, s.floatNum(AbilityParams.DAMAGE));
		assertEquals(8, s.intNum(AbilityParams.MAX_TARGETS));
		var offs = s.phaseOffsets();
		assertEquals(0, offs.get(0), "phase 0 starts the root");
		assertEquals(20, offs.get(1), "1.0 s wind-up");
		int slices = offs.size() - 1;
		int sweepEnd = offs.get(slices) + (offs.get(2) - offs.get(1));
		assertEquals(16, sweepEnd - offs.get(1), "swept over 0.8 s");
		assertEquals(46, s.intNum(GinParams.ROOT_TICKS));
		assertEquals(10, s.intNum(GinParams.ROOT_TICKS) - sweepEnd, "0.5 s recovery inside the root");
		assertEquals(180, s.costTenths());
		assertEquals(180, s.cooldownTicks());
	}

	@Test
	void snakeHook() {
		AbilitySpec s = row(AbilityId.SNAKE_HOOK);
		assertEquals(16.0, s.num(AbilityParams.LENGTH));
		assertEquals(2.0f, s.floatNum(AbilityParams.DAMAGE));
		assertEquals(1.5, s.num(GinParams.STOP_SHORT));
		assertTrue(s.num(GinParams.PULL_SPEED) * s.num(GinParams.PULL_TICKS) >= s.num(AbilityParams.LENGTH), "the pull can cover the whole line");
		assertEquals(120, s.costTenths());
		assertEquals(160, s.cooldownTicks());
	}

	@Test
	void butouRenjin() {
		AbilitySpec s = row(AbilityId.BUTOU_RENJIN);
		assertEquals(24.0, s.num(AbilityParams.LENGTH));
		assertEquals(1.5f, s.floatNum(AbilityParams.DAMAGE));
		assertEquals(8, s.phaseOffsets().size(), "8 thrusts");
		assertTrue(s.phaseOffsets().get(7) <= 20, "all inside 1.0 s");
		assertEquals(24, s.intNum(GinParams.ROOT_TICKS), "rooted 1.2 s");
		assertEquals(12.0f, s.floatNum(AbilityParams.DAMAGE) * s.phaseOffsets().size(), 1e-6, "up to 12 HP in all");
		assertEquals(280, s.costTenths());
		assertEquals(280, s.cooldownTicks());
	}

	@Test
	void korose() {
		AbilitySpec s = row(AbilityId.KOROSE);
		assertEquals(48.0, s.num(AbilityParams.LENGTH));
		assertEquals(9.0f, s.floatNum(AbilityParams.DAMAGE));
		assertEquals(18.0f, s.floatNum(GinParams.DISSOLVE_DAMAGE));
		assertEquals(240, s.intNum(GinParams.SLIVER_TICKS), "12 s");
		assertEquals(240, s.cooldownTicks(), "12 s after press 1");
		assertEquals(64.0, s.num(GinParams.RECAST_RANGE));
		assertEquals(30, s.intNum(GinParams.CHANNEL_TICKS), "1.5 s channel");
		assertEquals(600, s.intNum(GinParams.COOLDOWN_DISSOLVE), "30 s after the dissolve");
		assertEquals(300, s.intNum(GinParams.COOLDOWN_INTERRUPT), "15 s after an interrupted channel");
		assertEquals(4.0f, s.floatNum(GinParams.INTERRUPT_HP));
		assertEquals(250, s.costTenths(), "press 1 costs 25");
		assertEquals(27.0f, s.floatNum(AbilityParams.DAMAGE) + s.floatNum(GinParams.DISSOLVE_DAMAGE), "the full combo");
	}

	@Test
	void noSingleGinNumberPassesTheRosterCeiling() {
		for (AbilityId id : new AbilityId[] {AbilityId.IKOROSE, AbilityId.HYAPPONZASHI, AbilityId.SNAKE_HOOK, AbilityId.BUTOU_RENJIN, AbilityId.KOROSE}) {
			assertTrue(row(id).floatNum(AbilityParams.DAMAGE) <= 24.0f, id.name());
		}
		assertTrue(row(AbilityId.KOROSE).floatNum(GinParams.DISSOLVE_DAMAGE) <= 24.0f, "the dissolve stays at or below 24 HP (design section 2)");
	}

	@Test
	void hakufuku() {
		AbilitySpec s = row(AbilityId.HAKUFUKU);
		assertEquals(60, s.intNum(GinParams.HIDE_TICKS), "Invisibility 3 s");
		assertEquals(80, s.intNum(GinParams.FLASH_TICKS), "the next thrust within 4 s");
		assertEquals(150, s.costTenths());
		assertEquals(360, s.cooldownTicks());
	}

	// ---------------------------------------------------------------- the cooldown seam used by the recast

	@Test
	void pressOneChargesAndTheKeyIsBlockedUntilTheCooldownEnds() {
		Fx f = new Fx().toShikai(GIN).toBankai(GIN);
		f.adv(60);
		int before = f.sm.reiatsu().value();
		TransitionResult r = f.cast(AbilityId.KOROSE, GIN);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		assertEquals(before - 250, f.sm.reiatsu().value(), "press 1 costs 25");
		assertEquals(240, f.sm.cooldownRemaining(AbilityId.KOROSE));
		f.adv(1 + 12);
		f.sm.devSetReiatsu(1000);
		assertEquals(RejectReason.ON_COOLDOWN, f.cast(AbilityId.KOROSE, GIN).reason(),
				"this is why the recast must not go through the machine: its cooldown is still running");
	}

	@Test
	void theDissolveSetsThirtySecondsAndNothingElseMoves() {
		Fx f = new Fx().toShikai(GIN).toBankai(GIN);
		f.adv(60);
		assertTrue(f.cast(AbilityId.KOROSE, GIN).ok());
		f.adv(100);
		int reiatsu = f.sm.reiatsu().value();
		int renjin = f.sm.cooldownRemaining(AbilityId.BUTOU_RENJIN);
		f.sm.setCooldown(AbilityId.KOROSE, 600);
		assertEquals(600, f.sm.cooldownRemaining(AbilityId.KOROSE));
		assertEquals(renjin, f.sm.cooldownRemaining(AbilityId.BUTOU_RENJIN), "other abilities keep their cooldown");
		assertEquals(reiatsu, f.sm.reiatsu().value(), "setting a cooldown costs nothing");
		f.adv(599);
		f.sm.devSetReiatsu(1000);
		assertEquals(RejectReason.ON_COOLDOWN, f.cast(AbilityId.KOROSE, GIN).reason());
		f.adv(1);
		TransitionResult again = f.cast(AbilityId.KOROSE, GIN);
		assertTrue(again.ok(), "a first cast is possible again 30 s after the dissolve");
	}

	@Test
	void anInterruptedChannelSetsFifteenSeconds() {
		Fx f = new Fx().toShikai(GIN).toBankai(GIN);
		f.adv(60);
		assertTrue(f.cast(AbilityId.KOROSE, GIN).ok());
		f.adv(50);
		f.sm.setCooldown(AbilityId.KOROSE, 300);
		assertEquals(300, f.sm.cooldownRemaining(AbilityId.KOROSE));
		f.sm.setCooldown(AbilityId.KOROSE, 0);
		assertEquals(0, f.sm.cooldownRemaining(AbilityId.KOROSE), "0 clears");
	}

	@Test
	void setCooldownDoesNotChangeTheCastRulesOfOtherCharacters() {
		Fx f = new Fx().toShikai(CharacterId.RUKIA);
		f.sm.setCooldown(AbilityId.KOROSE, 600);
		TransitionResult r = f.cast(AbilityId.SHIRAFUNE, CharacterId.RUKIA);
		assertTrue(r.ok());
		assertEquals(row(AbilityId.SHIRAFUNE).cooldownTicks(), f.sm.cooldownRemaining(AbilityId.SHIRAFUNE));
		assertTrue(Fx.has(r.events(), StateEvent.CooldownStarted.class));
	}

	@Test
	void fullFlowWithTheRuleClass() {
		Fx f = new Fx().toShikai(GIN).toBankai(GIN);
		f.adv(60);
		GinRecast rc = new GinRecast(GinRecast.Config.of(row(AbilityId.KOROSE)));
		UUID target = UUID.randomUUID();
		long t0 = f.now();
		assertEquals(GinRecast.Press.MARK, rc.decide(t0));
		assertTrue(f.cast(AbilityId.KOROSE, GIN).ok());
		rc.marked(target, t0);
		f.adv(120);
		assertEquals(GinRecast.Press.RECAST, rc.decide(f.now()), "press 2 inside the 12 s");
		assertEquals(GinRecast.Kind.STARTED, rc.beginChannel(f.now(), 40).kind());
		f.adv(30);
		GinRecast.Outcome end = rc.tick(f.now());
		assertEquals(GinRecast.Kind.DISSOLVE, end.kind());
		f.sm.setCooldown(AbilityId.KOROSE, end.cooldownTicks());
		assertEquals(600, f.sm.cooldownRemaining(AbilityId.KOROSE));
	}
}
