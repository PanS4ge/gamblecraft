package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.casino.ArcadeMath;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.DiceBet;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.PlinkoRisk;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.WheelSymbol;
import pl.pansage.gamblecraft.core.casino.Roulette;
import pl.pansage.gamblecraft.core.casino.Roulette.Bet;
import pl.pansage.gamblecraft.core.casino.ScratchCards;
import pl.pansage.gamblecraft.core.casino.SlotMath;

class CasinoMathTest {
	@Test
	void arcadePayoutRoundsDownWithoutFloatingPointLoss() {
		assertEquals(230, ArcadeMath.payout(100, 2.3));
		assertEquals(575, ArcadeMath.payout(100, 5.75));
		assertEquals(57, ArcadeMath.payout(100, 0.57));
		assertEquals(5, ArcadeMath.payout(5, 1.1));
		assertEquals(0, ArcadeMath.payout(1, 0.17));
	}

	@Test
	void slotReturnIsAboutNinetyFivePercent() {
		double rtp = SlotMath.expectedReturn();
		assertTrue(rtp > 0.93 && rtp < 0.96, "slot rtp " + rtp);
		RandomGenerator rng = TestCards.rng(20);
		double sum = 0;
		int spins = 400_000;
		int hits = 0;
		for (int i = 0; i < spins; i++) {
			SlotMath.Spin spin = SlotMath.spin(rng);
			sum += spin.multiplier();
			if (!spin.wins().isEmpty()) {
				hits++;
			}
		}
		assertTrue(Math.abs(sum / spins - rtp) < 0.04, "simulated " + sum / spins);
		assertTrue(hits > spins * 0.2, "hit frequency " + (double) hits / spins);
	}

	@Test
	void jackpotNeedsSevensOnTheMiddleLine() {
		for (int a = 0; a < SlotMath.REEL_LENGTH; a++) {
			if (SlotMath.symbolAt(0, a) != SlotMath.Symbol.SEVEN) {
				continue;
			}
			for (int b = 0; b < SlotMath.REEL_LENGTH; b++) {
				for (int c = 0; c < SlotMath.REEL_LENGTH; c++) {
					if (SlotMath.symbolAt(1, b) == SlotMath.Symbol.SEVEN && SlotMath.symbolAt(2, c) == SlotMath.Symbol.SEVEN) {
						assertTrue(SlotMath.evaluate(new int[] {a, b, c}).jackpot());
					}
				}
			}
		}
	}

	@Test
	void everyRouletteBetHasTheSameEdge() {
		Bet[] bets = {Roulette.straight(17), Roulette.split(17, 20), Roulette.street(6), Roulette.corner(5, 0), Roulette.sixLine(3),
				Roulette.dozen(1), Roulette.columnBet(2), Roulette.red(), Roulette.black(), Roulette.even(), Roulette.odd(),
				Roulette.low(), Roulette.high(), Roulette.split(0, 2)};
		for (Bet bet : bets) {
			Roulette.validate(bet);
			int wins = 0;
			for (int n = 0; n <= 36; n++) {
				if (bet.wins(n)) {
					wins++;
				}
			}
			assertEquals(36.0 / 37, wins * bet.payout() / 37.0, 1e-9, bet.id());
			assertEquals(bet, Bet.fromId(bet.id()));
		}
		assertEquals(18, Roulette.red().numbers().size());
		assertEquals(37, new HashSet<>(Arrays.stream(Roulette.WHEEL).boxed().toList()).size());
	}

	@Test
	void forgedRouletteBetsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> Bet.fromId("split:1,5"));
		assertThrows(IllegalArgumentException.class, () -> Bet.fromId("straight:2,3"));
		assertThrows(IllegalArgumentException.class, () -> Bet.fromId("corner:1,2,3,4"));
	}

	@Test
	void feltClicksMapToBets() {
		assertEquals(Roulette.straight(0), Roulette.betAt(0.5, 1.5));
		// Column 1 is 1,2,3; middle row is 2.
		assertEquals(Roulette.straight(2), Roulette.betAt(1.5, 1.5));
		assertEquals(Roulette.split(2, 5), Roulette.betAt(1.95, 1.5));
		assertEquals(Roulette.split(2, 3), Roulette.betAt(1.5, 1.05));
		assertEquals(Roulette.corner(1, 0), Roulette.betAt(1.97, 0.97));
		assertEquals(Roulette.street(4), Roulette.betAt(4.5, 3.05));
		assertEquals(Roulette.dozen(2), Roulette.betAt(11, 3.6));
		assertEquals(Roulette.red(), Roulette.betAt(5.5, 4.5));
		assertEquals(Roulette.columnBet(0), Roulette.betAt(13.5, 0.5));
		assertNull(Roulette.betAt(0.5, 4.5));
		for (double x = 0; x < 14; x += 0.07) {
			for (double y = 0; y < 5; y += 0.07) {
				Bet bet = Roulette.betAt(x, y);
				if (bet != null) {
					Roulette.validate(bet);
				}
			}
		}
	}

	@Test
	void plinkoAndWheelReturns() {
		for (PlinkoRisk risk : PlinkoRisk.values()) {
			assertEquals(13, risk.bins.length);
			double rtp = risk.expectedReturn();
			assertTrue(rtp > 0.96 && rtp < 0.98, risk + " " + rtp);
		}
		assertEquals(50, ArcadeMath.WHEEL_SEGMENTS.length);
		for (WheelSymbol symbol : WheelSymbol.values()) {
			assertEquals(symbol.segments, Arrays.stream(ArcadeMath.WHEEL_SEGMENTS).filter(s -> s == symbol).count());
			assertTrue(symbol.expectedReturn() >= 0.94 && symbol.expectedReturn() <= 0.98, symbol.key());
		}
		RandomGenerator rng = TestCards.rng(21);
		ArcadeMath.PlinkoDrop drop = ArcadeMath.dropPlinko(PlinkoRisk.HIGH, rng);
		int rights = 0;
		for (boolean r : drop.rights()) {
			rights += r ? 1 : 0;
		}
		assertEquals(rights, drop.bin());
	}

	@Test
	void crashCashOutReturnsNinetySevenPercent() {
		RandomGenerator rng = TestCards.rng(22);
		int rounds = 400_000;
		for (double target : new double[] {1.5, 2.0, 5.0}) {
			double sum = 0;
			for (int i = 0; i < rounds; i++) {
				if (ArcadeMath.crashPoint(rng) >= target) {
					sum += target;
				}
			}
			assertEquals(ArcadeMath.CRASH_EDGE, sum / rounds, 0.02, "target " + target);
		}
		assertEquals(2.0, ArcadeMath.crashMultiplierAt(ArcadeMath.crashSecondsTo(2.0) + 1e-6), 0.011);
	}

	@Test
	void diceReturns() {
		for (DiceBet bet : DiceBet.values()) {
			assertEquals(0.958, bet.expectedReturn(), 0.001, bet.key());
		}
	}

	@Test
	void scratchCardsShowExactlyTheirPrize() {
		assertTrue(Math.abs(ScratchCards.expectedReturn() - 0.755) < 0.01);
		double sum = 0;
		int n = 200_000;
		for (long seed = 0; seed < n; seed++) {
			ScratchCards.Card card = ScratchCards.generate(seed);
			assertEquals(ScratchCards.CELLS, card.cells().length);
			int triples = 0;
			Set<Integer> seen = new HashSet<>();
			for (int symbol : card.cells()) {
				if (seen.add(symbol)) {
					int count = (int) Arrays.stream(card.cells()).filter(c -> c == symbol).count();
					assertTrue(count <= 3);
					if (count == 3) {
						triples++;
						assertNotNull(card.prize());
						assertEquals(card.prize().ordinal(), symbol);
					}
				}
			}
			assertEquals(card.prize() == null ? 0 : 1, triples);
			sum += card.multiplier();
		}
		assertEquals(ScratchCards.expectedReturn(), sum / n, 0.05);
		assertEquals(ScratchCards.generate(42).prize(), ScratchCards.generate(42).prize());
	}
}
