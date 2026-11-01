package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.pansage.gamblecraft.core.TestCards.P1;
import static pl.pansage.gamblecraft.core.TestCards.cards;
import static pl.pansage.gamblecraft.core.TestCards.payoutOf;
import static pl.pansage.gamblecraft.core.TestCards.rng;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.games.BasicStrategy;
import pl.pansage.gamblecraft.core.games.BlackjackGame;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.Settlement;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;

class BlackjackGameTest {
	private static BlackjackGame game(String deal) {
		BlackjackGame game = new BlackjackGame(4, 6, false, rng(1));
		game.shoe().stack(cards(deal));
		return game;
	}

	private static List<Settlement> play(BlackjackGame game, int bet, Act... acts) {
		int seat = TestCards.seat(game, P1, bet);
		game.startRound();
		game.runToInput();
		for (Act act : acts) {
			game.act(seat, act, 0);
			game.runToInput();
		}
		assertEquals(Phase.SETTLED, game.phase());
		return game.drainSettlements();
	}

	@Test
	void naturalPaysThreeToTwo() {
		// Deal order: player, dealer up, player, dealer hole.
		List<Settlement> s = play(game("As 9c Kd 7h"), 2);
		assertEquals(5, payoutOf(s, 0));
		assertEquals("blackjack", s.getFirst().outcome());
	}

	@Test
	void insuranceAgainstDealerBlackjack() {
		List<Settlement> s = play(game("9c Ah 7d Kc"), 10, Act.INSURANCE);
		assertEquals(15, payoutOf(s, 0));
		assertEquals(15, s.getFirst().committed());
	}

	@Test
	void standBeatsDealerBust() {
		List<Settlement> s = play(game("10c 6d 8h 10s Kh"), 4, Act.STAND);
		assertEquals(8, payoutOf(s, 0));
	}

	@Test
	void doubleDown() {
		List<Settlement> s = play(game("5c 9c 6d 8h Kc"), 3, Act.DOUBLE);
		assertEquals(12, payoutOf(s, 0));
		assertEquals(6, s.getFirst().committed());
	}

	@Test
	void splitAcesGetOneCardEach() {
		// Player A A, dealer 9 + 7 = 16, split cards K and Q, dealer draws 10 and busts.
		List<Settlement> s = play(game("As 9c Ad 7h Kc Qd 10c"), 5, Act.SPLIT);
		assertEquals(20, payoutOf(s, 0));
		assertEquals(10, s.getFirst().committed());
	}

	@Test
	void surrenderReturnsHalf() {
		List<Settlement> s = play(game("10c 10d 6h 9s"), 8, Act.SURRENDER);
		assertEquals(4, payoutOf(s, 0));
	}

	@Test
	void bustLoses() {
		List<Settlement> s = play(game("10c 7d 6h 10s Kd"), 8, Act.HIT);
		assertEquals(0, payoutOf(s, 0));
	}

	@Test
	void dealerHoleCardIsHiddenUntilReveal() {
		BlackjackGame game = game("10c 9d 6h 10s");
		int seat = TestCards.seat(game, P1, 2);
		game.startRound();
		game.runToInput();
		TableSnapshot snap = game.snapshot(P1);
		HandView dealer = snap.hands().stream().filter(h -> h.id().equals("dealer")).findFirst().orElseThrow();
		assertEquals(-1, dealer.cards().get(1));
		assertTrue(snap.actions().stream().anyMatch(o -> o.act() == Act.DOUBLE));
		game.act(seat, Act.STAND, 0);
	}

	@Test
	void houseEdgeWithBasicStrategyIsAboutHalfPercent() {
		RandomGenerator rng = rng(2024);
		BlackjackGame game = new BlackjackGame(4, 6, false, rng);
		int seat = game.sit(P1, "p1");
		long staked = 0;
		long net = 0;
		int rounds = 300_000;
		for (int r = 0; r < rounds; r++) {
			game.placeWager(seat, Wager.of(2));
			game.startRound();
			game.runToInput();
			while (game.awaitingInput()) {
				TableSnapshot snap = game.snapshot(P1);
				HandView active = snap.hands().stream().filter(HandView::active).findFirst().orElse(snap.hands().getFirst());
				HandView dealer = snap.hands().stream().filter(h -> h.id().equals("dealer")).findFirst().orElseThrow();
				List<Card> mine = active.cards().stream().map(Card::of).toList();
				Act act = BasicStrategy.decide(mine, Card.of(dealer.cards().getFirst()),
						snap.actions().stream().map(ActionOption::act).collect(java.util.stream.Collectors.toSet()));
				game.act(seat, act, 0);
				game.runToInput();
			}
			for (Settlement s : game.drainSettlements()) {
				net += s.payout() - s.committed();
			}
			staked += 2;
		}
		double edge = (double) net / staked;
		assertTrue(edge > -0.015 && edge < 0.003, "Player edge " + edge);
	}
}
