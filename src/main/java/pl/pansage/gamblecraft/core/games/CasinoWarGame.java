package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.Money;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;

/**
 * Casino War (Wojna): one card each against the dealer's card, higher wins 1:1.
 * On a tie the player goes to war (doubles the stake: three cards are burned and one more card each is dealt;
 * winning or tying the war pays the raise 1:1 and returns the original stake) or surrenders half.
 */
public final class CasinoWarGame extends CardGame {
	private final Shoe shoe;
	private final List<Integer> tied = new ArrayList<>();
	private final long[] payout;
	private TableHand dealer;

	public CasinoWarGame(int seats, int decks, RandomGenerator rng) {
		super(seats, rng);
		this.shoe = new Shoe(decks, rng);
		this.payout = new long[seats];
	}

	@Override
	public String id() {
		return "war";
	}

	public Shoe shoe() {
		return shoe;
	}

	private static int rank(TableHand hand, int index) {
		return hand.cards.get(index).card().rank().pokerValue();
	}

	private void deal(TableHand hand) {
		Card card = shoe.draw();
		hand.add(card, true);
		hand.value = Msg.of("gamblecraft.value.card", card.rank().symbol());
	}

	@Override
	protected void onStartRound() {
		if (shoe.needsShuffle()) {
			shoe.shuffle();
			log("gamblecraft.log.shuffle");
		}
		tied.clear();
		java.util.Arrays.fill(payout, 0);
		for (int seat : roundSeats()) {
			TableHand hand = new TableHand("s" + seat, seat, "gamblecraft.hand.player", false);
			hands.add(hand);
			then(() -> deal(hand));
		}
		dealer = new TableHand("dealer", DEALER, "gamblecraft.hand.dealer", false);
		hands.add(dealer);
		then(() -> deal(dealer));
		then(this::compare);
	}

	private void compare() {
		int d = rank(dealer, 0);
		for (int seat : roundSeats()) {
			TableHand hand = hand("s" + seat);
			int p = rank(hand, 0);
			long bet = seats[seat].wager.amount();
			if (p > d) {
				payout[seat] = bet * 2;
				hand.result = "win";
			} else if (p < d) {
				hand.result = "lose";
			} else {
				tied.add(seat);
				hand.result = "tie";
			}
		}
		if (tied.isEmpty()) {
			settleAll();
		} else {
			log("gamblecraft.log.war_tie");
			phase = Phase.PLAYER_TURN;
			activeSeat = tied.removeFirst();
			hand("s" + activeSeat).active = true;
		}
	}

	private void nextTied() {
		hand("s" + activeSeat).active = false;
		if (tied.isEmpty()) {
			activeSeat = -1;
			phase = Phase.DEALER_TURN;
			then(this::settleAll);
		} else {
			activeSeat = tied.removeFirst();
			hand("s" + activeSeat).active = true;
		}
	}

	private void settleAll() {
		for (int seat : roundSeats()) {
			long p = payout[seat];
			long committed = seats[seat].committed;
			settle(seat, p, p > committed ? "win" : p == committed ? "push" : "lose");
		}
		finishRound();
	}

	@Override
	protected void advance() {
	}

	@Override
	protected List<ActionOption> legalActions(int seat) {
		return List.of(ActionOption.costing(Act.WAR, seats[seat].wager.amount()), ActionOption.free(Act.SURRENDER));
	}

	@Override
	public Act defaultAct(int seat) {
		return Act.SURRENDER;
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		long bet = seats[seat].wager.amount();
		TableHand hand = hand("s" + seat);
		if (act == Act.SURRENDER) {
			payout[seat] = Money.scale(bet, 1, 2, rng);
			hand.result = "surrender";
			nextTied();
			return;
		}
		TableHand playerWar = new TableHand("w" + seat, seat, "gamblecraft.hand.war", false);
		TableHand dealerWar = new TableHand("dw" + seat, DEALER, "gamblecraft.hand.dealer_war", false);
		hands.add(hands.indexOf(dealer), playerWar);
		hands.add(dealerWar);
		then(() -> {
			for (int i = 0; i < 3; i++) {
				shoe.draw();
			}
			log("gamblecraft.log.burn", 3);
		});
		then(() -> deal(playerWar));
		then(() -> deal(dealerWar));
		then(() -> {
			boolean won = rank(playerWar, 0) >= rank(dealerWar, 0);
			payout[seat] = won ? bet + cost * 2 : 0;
			hand.result = won ? "war_win" : "war_lose";
			nextTied();
		});
	}
}
