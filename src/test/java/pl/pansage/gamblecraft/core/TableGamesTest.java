package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.pansage.gamblecraft.core.TestCards.P1;
import static pl.pansage.gamblecraft.core.TestCards.P2;
import static pl.pansage.gamblecraft.core.TestCards.P3;
import static pl.pansage.gamblecraft.core.TestCards.cards;
import static pl.pansage.gamblecraft.core.TestCards.payoutOf;
import static pl.pansage.gamblecraft.core.TestCards.rng;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.games.BaccaratGame;
import pl.pansage.gamblecraft.core.games.CasinoWarGame;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.core.games.HiLoGame;
import pl.pansage.gamblecraft.core.games.OczkoGame;
import pl.pansage.gamblecraft.core.games.ThreeCardPokerGame;
import pl.pansage.gamblecraft.core.games.VideoPokerGame;
import pl.pansage.gamblecraft.core.poker.PokerEvaluator;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.IllegalActionException;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.Settlement;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;

class TableGamesTest {
	@Test
	void oczkoPerskiePaysTwoToOne() {
		OczkoGame game = new OczkoGame(4, rng(3));
		game.shoe().stack(cards("Ac 9c Ad 10h"));
		TestCards.seat(game, P1, 5);
		game.startRound();
		game.runToInput();
		assertEquals(Phase.SETTLED, game.phase());
		assertEquals(15, payoutOf(game.drainSettlements(), 0));
	}

	@Test
	void oczkoValues() {
		assertEquals(21, OczkoGame.total(cards("Ac 10d")));
		assertEquals(9, OczkoGame.total(cards("Kc Qd Jh")));
		assertTrue(OczkoGame.isPerskie(cards("Ac Ad")));
	}

	@Test
	void baccaratTableau() {
		assertTrue(BaccaratGame.bankerDraws(3, 7));
		assertTrue(!BaccaratGame.bankerDraws(3, 8));
		assertTrue(BaccaratGame.bankerDraws(6, 6));
		assertTrue(!BaccaratGame.bankerDraws(6, -1));
		assertTrue(BaccaratGame.bankerDraws(5, -1));
		assertEquals(5, BaccaratGame.score(cards("8c 7d")));
	}

	@Test
	void baccaratHouseEdges() {
		RandomGenerator rng = rng(77);
		BaccaratGame game = new BaccaratGame(3, 8, rng);
		int[] seat = {game.sit(P1, "p"), game.sit(P2, "b"), game.sit(P3, "t")};
		long[] net = new long[3];
		int rounds = 400_000;
		for (int r = 0; r < rounds; r++) {
			for (int i = 0; i < 3; i++) {
				game.placeWager(seat[i], new Wager(100, i, 0));
			}
			game.startRound();
			game.runToInput();
			for (Settlement s : game.drainSettlements()) {
				net[s.seat()] += s.payout() - s.committed();
			}
		}
		double player = net[0] / (100.0 * rounds);
		double banker = net[1] / (100.0 * rounds);
		double tie = net[2] / (100.0 * rounds);
		assertTrue(Math.abs(player + 0.0124) < 0.006, "player " + player);
		assertTrue(Math.abs(banker + 0.0106) < 0.006, "banker " + banker);
		assertTrue(Math.abs(tie + 0.1436) < 0.03, "tie " + tie);
	}

	@Test
	void warTieGoesToWar() {
		CasinoWarGame game = new CasinoWarGame(4, 6, rng(4));
		game.shoe().stack(cards("7c 7d 2c 3c 4c Kc 2d"));
		int seat = TestCards.seat(game, P1, 10);
		game.startRound();
		game.runToInput();
		game.act(seat, Act.WAR, 0);
		game.runToInput();
		List<Settlement> s = game.drainSettlements();
		assertEquals(30, payoutOf(s, seat));
		assertEquals(20, s.getFirst().committed());
	}

	@Test
	void threeCardPokerDealerNotQualifying() {
		ThreeCardPokerGame game = new ThreeCardPokerGame(4, rng(5));
		// Deal order per pass: player then dealer.
		game.shoe().stack(cards("Ac 2d Ad 5h 9c Jc"));
		int seat = game.sit(P1, "p");
		game.placeWager(seat, new Wager(10, 0, 1));
		game.startRound();
		game.runToInput();
		game.act(seat, Act.PLAY, 0);
		game.runToInput();
		List<Settlement> s = game.drainSettlements();
		// Ante 1:1 (20) + play returned (10) + pair plus 1:1 (2).
		assertEquals(32, payoutOf(s, seat));
		assertEquals(21, s.getFirst().committed());
	}

	@Test
	void videoPokerPaysOnlyJacksOrBetter() {
		assertEquals(1, VideoPokerGame.multiplier(PokerEvaluator.evaluate(cards("Jc Jd 4h 3s 9c"))));
		assertEquals(0, VideoPokerGame.multiplier(PokerEvaluator.evaluate(cards("10c 10d 4h 3s 9c"))));
		assertEquals(250, VideoPokerGame.multiplier(PokerEvaluator.evaluate(cards("As Ks Qs Js 10s"))));
	}

	@Test
	void videoPokerHoldAndDraw() {
		VideoPokerGame game = new VideoPokerGame(4, rng(6));
		game.deckOf(0).stack(cards("Kc Kd 4h 3s 9c Kh 2d 7s"));
		int seat = TestCards.seat(game, P1, 3);
		game.startRound();
		game.runToInput();
		game.act(seat, Act.HOLD, 0);
		game.act(seat, Act.HOLD, 1);
		game.act(seat, Act.DRAW, 0);
		game.runToInput();
		// Kept K K, drew K 2 7: three kings pay 3 for 1.
		assertEquals(9, payoutOf(game.drainSettlements(), seat));
	}

	@Test
	void hiLoReturnsNinetySevenPercent() {
		RandomGenerator rng = rng(8);
		HiLoGame game = new HiLoGame(rng);
		int seat = game.sit(P1, "p");
		long staked = 0;
		long returned = 0;
		for (int r = 0; r < 200_000; r++) {
			game.placeWager(seat, Wager.of(100));
			game.startRound();
			game.runToInput();
			game.act(seat, game.chanceHigher() >= game.chanceLower() ? Act.HIGHER : Act.LOWER, 0);
			game.runToInput();
			if (game.awaitingInput()) {
				game.act(seat, Act.CASH_OUT, 0);
			}
			for (Settlement s : game.drainSettlements()) {
				returned += s.payout();
				staked += s.committed();
			}
		}
		double rtp = (double) returned / staked;
		assertTrue(Math.abs(rtp - HiLoGame.EDGE) < 0.01, "rtp " + rtp);
	}

	@Test
	void illegalActionsAreRejected() {
		CardGame game = GameKind.BLACKJACK.create(true, 1, rng(9));
		int seat = TestCards.seat(game, P1, 2);
		assertThrows(IllegalActionException.class, () -> game.act(seat, Act.HIT, 0));
		assertThrows(IllegalActionException.class, () -> game.placeWager(seat, Wager.of(0)));
	}

	@Test
	void everyGameSurvivesRandomPlay() {
		for (GameKind kind : GameKind.values()) {
			RandomGenerator rng = rng(kind.ordinal() + 100);
			CardGame game = kind.create(false, 1, rng);
			int seat = game.sit(P1, "p1");
			int seat2 = kind == GameKind.HI_LO ? -1 : game.sit(P2, "p2");
			for (int r = 0; r < 2_000; r++) {
				if (kind == GameKind.HOLDEM) {
					var holdem = (pl.pansage.gamblecraft.core.games.HoldemGame) game;
					for (int s : new int[] {seat, seat2}) {
						if (holdem.stackOf(s) < holdem.bigBlind()) {
							holdem.buyIn(s, 100);
						}
					}
				} else {
					game.placeWager(seat, kind == GameKind.BACCARAT ? new Wager(5, r % 3, 0) : new Wager(5, 0, kind == GameKind.THREE_CARD_POKER ? 1 : 0));
					if (seat2 >= 0) {
						game.placeWager(seat2, Wager.of(3));
					}
				}
				game.startRound();
				game.runToInput();
				int guard = 0;
				while (game.awaitingInput()) {
					int active = game.activeSeat();
					List<ActionOption> options = game.options(active);
					ActionOption pick = options.get(rng.nextInt(options.size()));
					long amount = pick.act() == Act.HOLD ? rng.nextInt(5) : pick.minCost() + (pick.maxCost() > pick.minCost() ? rng.nextLong(pick.maxCost() - pick.minCost() + 1) : 0);
					game.act(active, pick.act(), amount);
					game.runToInput();
					assertTrue(++guard < 500, kind + " never ends");
				}
				assertEquals(Phase.SETTLED, game.phase(), kind.id());
				for (Settlement s : game.drainSettlements()) {
					assertTrue(s.payout() >= 0, kind + " negative payout");
				}
			}
		}
	}

	@Test
	void cardTextIsReadable() {
		assertEquals("A♠", Card.fromCode("spades_ace").shortText());
	}
}
