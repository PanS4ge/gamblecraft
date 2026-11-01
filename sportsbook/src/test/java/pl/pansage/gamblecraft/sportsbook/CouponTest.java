package pl.pansage.gamblecraft.sportsbook;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.sportsbook.bet.Coupon;
import pl.pansage.gamblecraft.sportsbook.bet.MineralPlan;
import pl.pansage.gamblecraft.sportsbook.bet.Pick;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.Source;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;

class CouponTest {
	private static final long NOW = 1_800_000_000_000L;
	private final Map<String, SportEvent> feed = new HashMap<>();

	private static Pick pick(String key, Outcome selection, double odds) {
		return new Pick(key, League.PREMIER_LEAGUE, Source.ESPN, "Home " + key, "Away " + key, NOW + 3600_000L, selection, odds, Pick.Status.WAITING, "", "");
	}

	private static Coupon coupon(long stake, Pick... picks) {
		return new Coupon(UUID.randomUUID(), UUID.randomUUID(), NOW, stake, false, new ArrayList<>(List.of(picks)), Coupon.State.OPEN, 0, false, 1);
	}

	private void report(String key, EventState state, int home, int away, Outcome result) {
		feed.put(key, new SportEvent(key, League.PREMIER_LEAGUE, Source.ESPN, "Home " + key, "Away " + key, NOW + 3600_000L, state, home, away, result,
				state == EventState.LIVE ? "67'" : "", null, "", ""));
	}

	@Test
	void accumulatorWinsWhenEveryPickWins() {
		Coupon c = coupon(100, pick("a", Outcome.HOME, 2.40), pick("b", Outcome.AWAY, 2.90));
		report("a", EventState.LIVE, 1, 0, null);
		assertTrue(c.update(feed::get, NOW, 1_000_000));
		assertEquals(Pick.Status.LIVE, c.picks.getFirst().status);
		assertEquals("1:0", c.picks.getFirst().score);
		assertEquals("67'", c.picks.getFirst().clock);
		assertEquals(Coupon.State.OPEN, c.state);
		report("a", EventState.FINISHED, 2, 0, Outcome.HOME);
		report("b", EventState.FINISHED, 0, 1, Outcome.AWAY);
		int version = c.version;
		assertTrue(c.update(feed::get, NOW, 1_000_000));
		assertTrue(c.version > version);
		assertEquals(Coupon.State.WON, c.state);
		assertEquals(696, c.payout);
		assertFalse(c.update(feed::get, NOW, 1_000_000), "a settled coupon stays as it is");
	}

	@Test
	void oneLostPickLosesAtOnce() {
		Coupon c = coupon(100, pick("a", Outcome.DRAW, 3.40), pick("b", Outcome.HOME, 1.80));
		report("a", EventState.FINISHED, 2, 1, Outcome.HOME);
		c.update(feed::get, NOW, 1_000_000);
		assertEquals(Coupon.State.LOST, c.state);
		assertEquals(Pick.Status.WAITING, c.picks.get(1).status);
		assertEquals(0, c.payout);
	}

	@Test
	void voidPicksCountAtOddsOne() {
		Coupon c = coupon(100, pick("a", Outcome.HOME, 2.00), pick("b", Outcome.HOME, 3.00));
		report("a", EventState.POSTPONED, -1, -1, null);
		report("b", EventState.FINISHED, 1, 0, Outcome.HOME);
		c.update(feed::get, NOW, 1_000_000);
		assertEquals(Coupon.State.WON, c.state);
		assertEquals(300, c.payout);
	}

	@Test
	void allVoidGivesTheStakeBack() {
		Coupon c = coupon(250, pick("a", Outcome.AWAY, 2.00));
		report("a", EventState.CANCELLED, -1, -1, null);
		c.update(feed::get, NOW, 1_000_000);
		assertEquals(Coupon.State.VOID, c.state);
		assertEquals(250, c.payout);
	}

	@Test
	void aGameTheFeedsLostIsVoidAfterThreeDays() {
		Coupon c = coupon(100, pick("gone", Outcome.HOME, 2.00));
		assertFalse(c.update(feed::get, NOW + 3600_000L * 24, 1_000_000));
		c.update(feed::get, NOW + Coupon.VOID_AFTER + 3600_000L * 2, 1_000_000);
		assertEquals(Coupon.State.VOID, c.state);
	}

	@Test
	void finishedWithoutAWinnerIsVoid() {
		Coupon c = coupon(100, pick("fight", Outcome.HOME, 1.70));
		report("fight", EventState.FINISHED, -1, -1, null);
		c.update(feed::get, NOW, 1_000_000);
		assertEquals(Coupon.State.VOID, c.state);
	}

	@Test
	void payoutIsCapped() {
		Coupon c = coupon(10_000, pick("a", Outcome.HOME, 40.0), pick("b", Outcome.HOME, 40.0));
		report("a", EventState.FINISHED, 1, 0, Outcome.HOME);
		report("b", EventState.FINISHED, 1, 0, Outcome.HOME);
		c.update(feed::get, NOW, 1_000_000);
		assertEquals(1_000_000, c.payout);
	}

	@Test
	void mineralsPayExactlyWhenTheyCan() {
		// diamonds 40, gold 5, copper 1
		MineralPlan plan = MineralPlan.of(87, new int[] {1, 5, 40}, new long[] {10, 3, 2});
		assertArrayEquals(new long[] {2, 1, 2}, plan.take());
		assertEquals(0, plan.change());
	}

	@Test
	void mineralsGiveChangeInChips() {
		MineralPlan plan = MineralPlan.of(100, new int[] {1, 5, 40}, new long[] {0, 0, 3});
		assertArrayEquals(new long[] {0, 0, 3}, plan.take());
		assertEquals(20, plan.change());
		MineralPlan cheaper = MineralPlan.of(43, new int[] {1, 5, 40}, new long[] {0, 1, 2});
		assertArrayEquals(new long[] {0, 1, 1}, cheaper.take());
		assertEquals(2, cheaper.change());
	}

	@Test
	void mineralsWorthTooLittle() {
		assertNull(MineralPlan.of(100, new int[] {1, 5, 40}, new long[] {10, 2, 1}));
	}
}
