package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.pansage.gamblecraft.core.TestCards.P1;
import static pl.pansage.gamblecraft.core.TestCards.P2;
import static pl.pansage.gamblecraft.core.TestCards.P3;
import static pl.pansage.gamblecraft.core.TestCards.cards;
import static pl.pansage.gamblecraft.core.TestCards.rng;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.games.HoldemGame;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;

class HoldemGameTest {
	@Test
	void sidePotsGoToTheRightPlayers() {
		HoldemGame game = new HoldemGame(5, 1, 2, rng(10));
		int s0 = game.sit(P1, "short");
		int s1 = game.sit(P2, "mid");
		int s2 = game.sit(P3, "big");
		game.buyIn(s0, 50);
		game.buyIn(s1, 100);
		game.buyIn(s2, 200);
		// Button seat 0, SB seat 1, BB seat 2. Hole cards go seat 1, 2, 0, twice; then burn + flop, burn + turn, burn + river.
		game.shoe().stack(cards("Kc Qc Ac Kd Qd Ad 4s 2c 7d 9h 5s Js 6s 3c"));
		game.startRound();
		game.runToInput();
		assertEquals(s0, game.activeSeat());
		game.act(s0, Act.ALL_IN, 0);
		game.runToInput();
		game.act(s1, Act.ALL_IN, 0);
		game.runToInput();
		game.act(s2, Act.ALL_IN, 0);
		game.runToInput();
		assertEquals(Phase.SETTLED, game.phase());
		assertEquals(150, game.stackOf(s0));
		assertEquals(100, game.stackOf(s1));
		assertEquals(100, game.stackOf(s2));
	}

	@Test
	void holeCardsArePrivateUntilShowdown() {
		HoldemGame game = new HoldemGame(5, 1, 2, rng(11));
		int a = game.sit(P1, "a");
		int b = game.sit(P2, "b");
		game.buyIn(a, 100);
		game.buyIn(b, 100);
		game.startRound();
		game.runToInput();
		TableSnapshot view = game.snapshot(P1);
		HandView mine = view.hands().stream().filter(h -> h.owner() == a).findFirst().orElseThrow();
		HandView theirs = view.hands().stream().filter(h -> h.owner() == b).findFirst().orElseThrow();
		assertTrue(mine.cards().stream().allMatch(c -> c >= 0));
		assertTrue(theirs.cards().stream().allMatch(c -> c == -1));
		assertEquals(null, theirs.value());
	}

	@Test
	void chipsAreConservedInRandomMultiwayPlay() {
		RandomGenerator rng = rng(12);
		HoldemGame game = new HoldemGame(5, 1, 2, rng);
		int[] seats = {game.sit(P1, "a"), game.sit(P2, "b"), game.sit(P3, "c")};
		long bought = 0;
		for (int hand = 0; hand < 3_000; hand++) {
			for (int s : seats) {
				if (game.stackOf(s) < game.bigBlind()) {
					game.buyIn(s, 60);
					bought += 60;
				}
			}
			game.startRound();
			game.runToInput();
			while (game.awaitingInput()) {
				int active = game.activeSeat();
				List<ActionOption> options = game.options(active);
				ActionOption pick = options.get(rng.nextInt(options.size()));
				long amount = pick.maxCost() > pick.minCost() ? pick.minCost() + rng.nextLong(pick.maxCost() - pick.minCost() + 1) : pick.minCost();
				game.act(active, pick.act(), amount);
				game.runToInput();
			}
			assertEquals(Phase.SETTLED, game.phase());
			long total = 0;
			for (int s : seats) {
				total += game.stackOf(s);
			}
			assertEquals(bought, total, "hand " + hand);
		}
	}

	@Test
	void duelHouseKeepsOneStakeAndCanBeBroken() {
		RandomGenerator rng = rng(14);
		HoldemGame game = HoldemGame.duel(50, 100, 1000, "Boss", rng);
		int seat = game.sit(P1, "challenger");
		game.buyIn(seat, 5000);
		long total = 6000;
		boolean houseLostOnce = false;
		for (int hand = 0; hand < 400 && !game.houseBroke() && game.stackOf(seat) >= 100; hand++) {
			assertTrue(game.canStart());
			long before = hand == 0 ? 1000 : game.houseStack();
			game.startRound();
			game.runToInput();
			while (game.awaitingInput()) {
				// the challenger shoves every hand: the house is broke or the challenger is within a few hands
				List<ActionOption> options = game.options(seat);
				Act act = options.stream().anyMatch(o -> o.act() == Act.ALL_IN) ? Act.ALL_IN : Act.CALL;
				game.act(seat, act, 0);
				game.runToInput();
			}
			assertEquals(Phase.SETTLED, game.phase());
			// the house never tops itself up: its chips and the challenger's add up to what came to the table
			assertEquals(total, game.stackOf(seat) + game.houseStack(), "hand " + hand);
			houseLostOnce |= game.houseStack() < before;
		}
		assertTrue(houseLostOnce);
		if (game.houseBroke()) {
			assertTrue(!game.canStart());
		}
	}

	@Test
	void soloPlayerFacesTheHouse() {
		RandomGenerator rng = rng(13);
		HoldemGame game = new HoldemGame(5, 1, 2, rng);
		int seat = game.sit(P1, "solo");
		game.buyIn(seat, 200);
		for (int hand = 0; hand < 500 && game.stackOf(seat) >= 2; hand++) {
			game.startRound();
			game.runToInput();
			while (game.awaitingInput()) {
				assertEquals(seat, game.activeSeat());
				List<ActionOption> options = game.options(seat);
				Act act = options.stream().anyMatch(o -> o.act() == Act.CHECK) ? Act.CHECK : Act.CALL;
				game.act(seat, act, 0);
				game.runToInput();
			}
			assertEquals(Phase.SETTLED, game.phase());
		}
		assertTrue(game.snapshot(P1).seats().stream().anyMatch(s -> s.player().equals(HoldemGame.HOUSE)));
	}
}
