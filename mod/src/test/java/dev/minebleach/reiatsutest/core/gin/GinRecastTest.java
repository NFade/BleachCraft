package dev.minebleach.reiatsutest.core.gin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.gin.GinRecast.Kind;
import dev.minebleach.reiatsutest.core.gin.GinRecast.Outcome;
import dev.minebleach.reiatsutest.core.gin.GinRecast.Phase;
import dev.minebleach.reiatsutest.core.gin.GinRecast.Press;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The recast of Korose (design/GIN.md section 4): phases, cooldown outcomes, channel interrupt rules, the Renjin timer reset. */
class GinRecastTest {
	private static final GinRecast.Config CFG = GinRecast.Config.of(BalanceConfig.defaults().spec(AbilityId.KOROSE));
	private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static GinRecast marked(long now) {
		GinRecast r = new GinRecast(CFG);
		r.marked(TARGET, now);
		return r;
	}

	private static GinRecast channelling(long now) {
		GinRecast r = marked(now);
		assertEquals(Kind.STARTED, r.beginChannel(now + 10, 20.0).kind());
		return r;
	}

	@Test
	void configComesFromTheBalanceRow() {
		assertEquals(240, CFG.markTicks());
		assertEquals(30, CFG.channelTicks());
		assertEquals(600, CFG.cooldownDissolve());
		assertEquals(300, CFG.cooldownInterrupt());
		assertEquals(4.0f, CFG.interruptHp());
		assertEquals(64.0, CFG.range());
		assertFalse(CFG.keepsMark());
	}

	@Test
	void pressMeaningFollowsThePhase() {
		GinRecast r = new GinRecast(CFG);
		assertEquals(Press.MARK, r.decide(0));
		r.marked(TARGET, 100);
		assertEquals(Phase.MARKED, r.phase());
		assertEquals(Press.RECAST, r.decide(100));
		assertEquals(Press.RECAST, r.decide(100 + 239));
		assertEquals(Press.MARK, r.decide(100 + 240), "the 12 s are over: the key is a first cast again");
		GinRecast c = channelling(100);
		assertEquals(Press.BUSY, c.decide(115), "a press during the channel does nothing");
	}

	@Test
	void recastWithoutAMarkDoesNotStart() {
		GinRecast r = new GinRecast(CFG);
		assertEquals(Kind.NONE, r.beginChannel(0, 5).kind());
		assertEquals(Phase.NONE, r.phase());
		GinRecast late = marked(0);
		assertEquals(Kind.NONE, late.beginChannel(240, 5).kind(), "too late");
	}

	@Test
	void channelRunsOneAndAHalfSecondsThenDissolvesWithTheLongCooldown() {
		GinRecast r = marked(0);
		Outcome start = r.beginChannel(100, 30.0);
		assertEquals(Kind.STARTED, start.kind());
		assertEquals(-1, start.cooldownTicks(), "the recast is free: starting it changes no cooldown");
		assertEquals(Phase.CHANNEL, r.phase());
		assertEquals(130, r.channelEnd());
		for (long t = 101; t < 130; t++) {
			assertEquals(Kind.NONE, r.tick(t).kind(), "tick " + t);
		}
		Outcome end = r.tick(130);
		assertEquals(Kind.DISSOLVE, end.kind());
		assertEquals(600, end.cooldownTicks(), "30 s after the dissolve");
		assertTrue(end.changesCooldown());
		assertEquals(Phase.NONE, r.phase());
		assertEquals(Kind.NONE, r.tick(131).kind(), "the dissolve happens once");
	}

	@Test
	void recastReachIs64Blocks() {
		GinRecast ok = marked(0);
		assertEquals(Kind.STARTED, ok.beginChannel(10, 64.0).kind(), "exactly 64 is still in reach");
		GinRecast far = marked(0);
		Outcome o = far.beginChannel(10, 64.01);
		assertEquals(Kind.FIZZLED, o.kind());
		assertEquals(-1, o.cooldownTicks());
		assertEquals(Phase.NONE, far.phase(), "a target out of reach loses the sliver");
	}

	@Test
	void aMarkThatRunsOutIsDropped() {
		GinRecast r = marked(0);
		assertEquals(Kind.NONE, r.tick(239).kind());
		assertEquals(Kind.LOST, r.tick(240).kind());
		assertEquals(Phase.NONE, r.phase());
	}

	@Test
	void oneHitOfFourOrMoreInterruptsLessDoesNot() {
		GinRecast r = channelling(0);
		assertEquals(Kind.NONE, r.onHit(3.9f).kind());
		assertEquals(Kind.NONE, r.onHit(1.0f).kind());
		assertEquals(Kind.NONE, r.onHit(3.9f).kind(), "small hits do not add up: the rule is ONE hit");
		assertEquals(Phase.CHANNEL, r.phase());
		Outcome o = r.onHit(4.0f);
		assertEquals(Kind.INTERRUPTED, o.kind());
		assertEquals(300, o.cooldownTicks(), "15 s instead of 30");
		assertEquals(Phase.NONE, r.phase());
	}

	@Test
	void knockbackControlAndStateChangeInterrupt() {
		assertEquals(Kind.INTERRUPTED, channelling(0).onKnockback().kind());
		assertEquals(Kind.INTERRUPTED, channelling(0).onControl().kind());
		Outcome state = channelling(0).onStateChange();
		assertEquals(Kind.INTERRUPTED, state.kind());
		assertEquals(300, state.cooldownTicks());
	}

	@Test
	void interruptsOutsideAChannelDoNothing() {
		GinRecast none = new GinRecast(CFG);
		assertEquals(Kind.NONE, none.onHit(20f).kind());
		assertEquals(Kind.NONE, none.onKnockback().kind());
		GinRecast mark = marked(0);
		assertEquals(Kind.NONE, mark.onControl().kind(), "a plain mark is not touched by hits");
		assertEquals(Phase.MARKED, mark.phase());
		assertEquals(Kind.NONE, mark.onStateChange().kind());
	}

	@Test
	void aSpentChannelCannotDissolveLater() {
		GinRecast r = channelling(0);
		assertEquals(Kind.INTERRUPTED, r.onKnockback().kind());
		assertEquals(Kind.NONE, r.tick(1000).kind());
		assertNull(r.target());
	}

	@Test
	void keepsMarkVariantStaysMarkedUntilTheTimer() {
		GinRecast.Config keep = new GinRecast.Config(CFG.markTicks(), CFG.channelTicks(), CFG.cooldownDissolve(), CFG.cooldownInterrupt(), CFG.interruptHp(), CFG.range(), true);
		GinRecast r = new GinRecast(keep);
		r.marked(TARGET, 0);
		r.beginChannel(10, 5);
		Outcome o = r.onKnockback();
		assertEquals(Kind.INTERRUPTED, o.kind());
		assertEquals(Phase.MARKED, r.phase());
		assertEquals(Press.RECAST, r.decide(50), "the sliver is still there and can be recast");
		assertEquals(Kind.LOST, r.tick(240).kind());
	}

	@Test
	void cleanseDuringTheChannelEndsItAndOnlyTheTargetsDeathIsFree() {
		for (SliverBook.Reason why : SliverBook.Reason.values()) {
			if (why == SliverBook.Reason.DISSOLVED || why == SliverBook.Reason.INTERRUPTED || why == SliverBook.Reason.REPLACED) {
				continue;
			}
			GinRecast r = channelling(0);
			Outcome o = r.sliverGone(why);
			assertEquals(Kind.FIZZLED, o.kind(), why.name());
			boolean free = why == SliverBook.Reason.TARGET_DEATH || why == SliverBook.Reason.OWNER_DEATH || why == SliverBook.Reason.OWNER_LEFT_BANKAI;
			assertEquals(free ? -1 : 300, o.cooldownTicks(), why.name());
			assertEquals(Phase.NONE, r.phase(), why.name());
		}
	}

	@Test
	void cleanseOfAPlainMarkJustLosesIt() {
		for (SliverBook.Reason why : new SliverBook.Reason[] {SliverBook.Reason.MILK, SliverBook.Reason.TARGET_DEATH, SliverBook.Reason.OUT_OF_RANGE,
				SliverBook.Reason.OWNER_DEATH, SliverBook.Reason.OWNER_LEFT_BANKAI}) {
			GinRecast r = marked(0);
			Outcome o = r.sliverGone(why);
			assertEquals(Kind.LOST, o.kind(), why.name());
			assertEquals(-1, o.cooldownTicks());
			assertEquals(Press.MARK, r.decide(5));
		}
		assertEquals(Kind.NONE, new GinRecast(CFG).sliverGone(SliverBook.Reason.MILK).kind());
	}

	@Test
	void renjinRefreshStartsTheTwelveSecondsAgain() {
		GinRecast r = marked(0);
		assertTrue(r.refresh(TARGET, 200));
		assertEquals(440, r.expiry());
		assertEquals(Press.RECAST, r.decide(439));
		assertEquals(Press.MARK, r.decide(440));
		assertFalse(r.refresh(UUID.randomUUID(), 300), "only the marked target is refreshed");
		assertEquals(440, r.expiry());
		assertFalse(new GinRecast(CFG).refresh(TARGET, 1));
		assertFalse(marked(0).refresh(TARGET, 240), "a dead mark cannot be refreshed");
	}

	@Test
	void resetForgetsEverything() {
		GinRecast r = channelling(0);
		r.reset();
		assertEquals(Phase.NONE, r.phase());
		assertEquals(Press.MARK, r.decide(5));
	}
}
