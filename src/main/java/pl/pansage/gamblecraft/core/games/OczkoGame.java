package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Rank;
import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.PlacedCard;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;

/**
 * Oczko, the Polish 21: a 24-card deck (nine to ace), ace 11, king 4, queen 3, jack 2.
 * Two aces ("perskie oczko") beat everything and pay 2:1; the dealer draws to 17; ties push.
 */
public final class OczkoGame extends CardGame {
	private final Shoe shoe;
	private final List<TableHand> playerHands = new ArrayList<>();
	private final boolean[] finished;
	private TableHand dealer;

	public OczkoGame(int seats, RandomGenerator rng) {
		super(seats, rng);
		this.shoe = new Shoe(1, card -> card.rank().ordinal() >= Rank.NINE.ordinal(), rng);
		this.finished = new boolean[seats];
	}

	@Override
	public String id() {
		return "oczko";
	}

	public Shoe shoe() {
		return shoe;
	}

	public static int value(Rank rank) {
		return switch (rank) {
			case ACE -> 11;
			case KING -> 4;
			case QUEEN -> 3;
			case JACK -> 2;
			default -> rank.pokerValue();
		};
	}

	public static int total(List<Card> cards) {
		return cards.stream().mapToInt(c -> value(c.rank())).sum();
	}

	public static boolean isPerskie(List<Card> cards) {
		return cards.size() == 2 && cards.stream().allMatch(c -> c.rank() == Rank.ACE);
	}

	private static void updateValue(TableHand hand) {
		List<Card> cards = hand.cards.stream().filter(PlacedCard::faceUp).map(PlacedCard::card).toList();
		hand.value = isPerskie(cards) ? Msg.of("gamblecraft.value.perskie") : Msg.of("gamblecraft.value.points", total(cards));
	}

	@Override
	protected void onStartRound() {
		shoe.shuffle();
		log("gamblecraft.log.shuffle");
		playerHands.clear();
		java.util.Arrays.fill(finished, false);
		for (int seat : roundSeats()) {
			TableHand hand = new TableHand("s" + seat, seat, "gamblecraft.hand.player", false);
			playerHands.add(hand);
			hands.add(hand);
		}
		dealer = new TableHand("dealer", DEALER, "gamblecraft.hand.dealer", false);
		hands.add(dealer);
		for (int pass = 0; pass < 2; pass++) {
			boolean faceUp = pass == 0;
			for (TableHand hand : List.copyOf(playerHands)) {
				then(() -> deal(hand, true));
			}
			then(() -> deal(dealer, faceUp));
		}
		then(() -> {
			for (TableHand hand : playerHands) {
				if (isPerskie(hand.cardList()) || total(hand.cardList()) == 21) {
					finished[hand.owner] = true;
				}
			}
			phase = Phase.PLAYER_TURN;
			nextTurn();
		});
	}

	private void deal(TableHand hand, boolean faceUp) {
		hand.add(shoe.draw(), faceUp);
		updateValue(hand);
	}

	private TableHand handOf(int seat) {
		return hand("s" + seat);
	}

	private void nextTurn() {
		for (TableHand hand : playerHands) {
			hand.active = false;
		}
		for (TableHand hand : playerHands) {
			if (!finished[hand.owner]) {
				activeSeat = hand.owner;
				hand.active = true;
				return;
			}
		}
		activeSeat = -1;
		phase = Phase.DEALER_TURN;
	}

	@Override
	protected void advance() {
		if (phase != Phase.DEALER_TURN) {
			return;
		}
		if (!dealer.cards.get(1).faceUp()) {
			dealer.revealAll();
			updateValue(dealer);
			return;
		}
		List<Card> cards = dealer.cardList();
		boolean anyLive = playerHands.stream().anyMatch(h -> !isPerskie(h.cardList()) && total(h.cardList()) <= 21);
		if (anyLive && !isPerskie(cards) && total(cards) < 17) {
			deal(dealer, true);
			return;
		}
		settleAll();
	}

	private void settleAll() {
		List<Card> d = dealer.cardList();
		boolean dPerskie = isPerskie(d);
		int dTotal = total(d);
		if (!dPerskie && dTotal > 21) {
			dealer.result = "bust";
		}
		for (TableHand hand : playerHands) {
			hand.active = false;
			List<Card> p = hand.cardList();
			long bet = seats[hand.owner].committed;
			long payout;
			String result;
			if (isPerskie(p)) {
				payout = dPerskie ? bet : bet * 3;
				result = dPerskie ? "push" : "perskie_oczko";
			} else if (total(p) > 21) {
				payout = 0;
				result = "bust";
			} else if (dPerskie) {
				payout = 0;
				result = "lose";
			} else if (dTotal > 21 || total(p) > dTotal) {
				payout = bet * 2;
				result = total(p) == 21 ? "oczko" : "win";
			} else if (total(p) == dTotal) {
				payout = bet;
				result = "push";
			} else {
				payout = 0;
				result = "lose";
			}
			hand.result = result;
			settle(hand.owner, payout, payout > bet ? "win" : payout == bet ? "push" : "lose");
		}
		finishRound();
	}

	@Override
	protected List<ActionOption> legalActions(int seat) {
		return List.of(ActionOption.free(Act.HIT), ActionOption.free(Act.STAND));
	}

	@Override
	public Act defaultAct(int seat) {
		return Act.STAND;
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		TableHand hand = handOf(seat);
		if (act == Act.STAND) {
			finished[seat] = true;
			nextTurn();
			return;
		}
		then(() -> {
			deal(hand, true);
			int total = total(hand.cardList());
			if (total > 21) {
				finished[seat] = true;
				hand.result = "bust";
			} else if (total == 21) {
				finished[seat] = true;
			}
		});
		then(this::nextTurn);
	}
}
