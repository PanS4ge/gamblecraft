package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.pansage.gamblecraft.core.TestCards.card;
import static pl.pansage.gamblecraft.core.TestCards.cards;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.games.BasicStrategy;
import pl.pansage.gamblecraft.core.games.HoldemBot;
import pl.pansage.gamblecraft.core.poker.PokerAdvisor;
import pl.pansage.gamblecraft.core.poker.PokerAdvisor.Draw;
import pl.pansage.gamblecraft.core.table.Act;

class AdvisorTest {
	@Test
	void basicStrategyClassics() {
		var legal = EnumSet.of(Act.HIT, Act.STAND, Act.DOUBLE, Act.SPLIT, Act.SURRENDER);
		assertEquals(Act.SPLIT, BasicStrategy.decide(cards("8c 8d"), card("10h"), legal));
		assertEquals(Act.DOUBLE, BasicStrategy.decide(cards("6c 5d"), card("6h"), legal));
		assertEquals(Act.STAND, BasicStrategy.decide(cards("10c 3d"), card("4h"), legal));
		assertEquals(Act.HIT, BasicStrategy.decide(cards("10c 2d"), card("2h"), legal));
		assertEquals(Act.SURRENDER, BasicStrategy.decide(cards("10c 6d"), card("Ah"), legal));
	}

	@Test
	void drawsAreSpotted() {
		assertTrue(PokerAdvisor.draws(cards("Ah 7h 2h Kh 9c")).contains(Draw.FLUSH_DRAW));
		assertTrue(PokerAdvisor.draws(cards("8c 9d 10h Jc 2s")).contains(Draw.OPEN_ENDED));
		assertTrue(PokerAdvisor.draws(cards("8c 9d Jh Qc 2s")).contains(Draw.GUTSHOT));
	}

	@Test
	void videoPokerHolds() {
		assertEquals(0b00011, PokerAdvisor.suggestHolds(cards("Kc Kd 4h 7s 2c")));
		assertEquals(0b11110, PokerAdvisor.suggestHolds(cards("2s Ah 7h 2h Kh")));
		assertEquals(0b11111, PokerAdvisor.suggestHolds(cards("4c 5d 6h 7s 8c")));
		assertEquals(0, PokerAdvisor.suggestHolds(cards("2c 5d 9h 3s 7d")));
	}

	@Test
	void acesBeatRandomHands() {
		double equity = HoldemBot.equity(cards("As Ad"), java.util.List.of(), 1, TestCards.rng(5));
		assertTrue(equity > 0.75, "AA equity " + equity);
	}
}
