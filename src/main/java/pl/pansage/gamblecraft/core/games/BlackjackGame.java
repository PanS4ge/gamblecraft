package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Rank;
import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.Money;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.PlacedCard;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;

/**
 * Blackjack: dealer stands on soft 17 and peeks for blackjack, 3:2 naturals, double on any two cards,
 * split up to four hands (split aces get one card), insurance, late surrender.
 */
public final class BlackjackGame extends CardGame {
	private static final int MAX_HANDS_PER_SEAT = 4;

	private final Shoe shoe;
	private final boolean dealerHitsSoft17;
	private final List<BjHand> playerHands = new ArrayList<>();
	private final long[] insurance;
	private TableHand dealer;
	private BjHand current;
	private boolean insurancePhase;

	private static final class BjHand {
		final TableHand view;
		final int seat;
		long bet;
		long naturalPayout;
		boolean finished;
		boolean surrendered;
		boolean splitAces;
		boolean fromSplit;

		BjHand(TableHand view, int seat, long bet) {
			this.view = view;
			this.seat = seat;
			this.bet = bet;
		}
	}

	public BlackjackGame(int seats, int decks, boolean dealerHitsSoft17, RandomGenerator rng) {
		super(seats, rng);
		this.shoe = new Shoe(decks, rng);
		this.dealerHitsSoft17 = dealerHitsSoft17;
		this.insurance = new long[seats];
	}

	@Override
	public String id() {
		return "blackjack";
	}

	public Shoe shoe() {
		return shoe;
	}

	// ---------------------------------------------------------------- scoring

	/** Best total of the cards, counting aces as 1 where needed. */
	public static int total(List<Card> cards) {
		int total = 0;
		int aces = 0;
		for (Card card : cards) {
			total += card.rank().blackjackValue();
			if (card.rank() == Rank.ACE) {
				aces++;
			}
		}
		while (total > 21 && aces > 0) {
			total -= 10;
			aces--;
		}
		return total;
	}

	/** True if an ace is still counted as 11. */
	public static boolean isSoft(List<Card> cards) {
		int hard = 0;
		boolean ace = false;
		for (Card card : cards) {
			hard += card.rank() == Rank.ACE ? 1 : card.rank().blackjackValue();
			ace |= card.rank() == Rank.ACE;
		}
		return ace && hard + 10 <= 21;
	}

	private static List<Card> visible(TableHand hand) {
		return hand.cards.stream().filter(PlacedCard::faceUp).map(PlacedCard::card).toList();
	}

	private static void updateValue(TableHand hand) {
		List<Card> cards = visible(hand);
		int total = total(cards);
		hand.value = isSoft(cards) && total < 21
				? Msg.of("gamblecraft.value.soft", total - 10, total)
				: Msg.of("gamblecraft.value.points", total);
	}

	private static boolean isNatural(BjHand hand) {
		return !hand.fromSplit && hand.view.cards.size() == 2 && total(hand.view.cardList()) == 21;
	}

	// ---------------------------------------------------------------- round

	@Override
	protected void onStartRound() {
		if (shoe.needsShuffle()) {
			shoe.shuffle();
			log("gamblecraft.log.shuffle");
		}
		playerHands.clear();
		java.util.Arrays.fill(insurance, 0);
		insurancePhase = false;
		current = null;
		for (int seat : roundSeats()) {
			BjHand hand = new BjHand(new TableHand("s" + seat + "h0", seat, "gamblecraft.hand.player", false), seat, seats[seat].wager.amount());
			playerHands.add(hand);
			hands.add(hand.view);
		}
		dealer = new TableHand("dealer", DEALER, "gamblecraft.hand.dealer", false);
		hands.add(dealer);
		for (int pass = 0; pass < 2; pass++) {
			boolean faceUp = pass == 0;
			for (BjHand hand : List.copyOf(playerHands)) {
				then(() -> deal(hand.view, true));
			}
			then(() -> deal(dealer, faceUp));
		}
		then(this::afterDeal);
	}

	private void deal(TableHand hand, boolean faceUp) {
		hand.add(shoe.draw(), faceUp);
		updateValue(hand);
	}

	private void afterDeal() {
		if (dealer.cards.getFirst().card().rank() == Rank.ACE && firstInsuranceSeat(-1) >= 0) {
			insurancePhase = true;
			phase = Phase.PLAYER_TURN;
			activeSeat = firstInsuranceSeat(-1);
			log("gamblecraft.log.insurance_offered");
		} else {
			peek();
		}
	}

	private int firstInsuranceSeat(int after) {
		for (BjHand hand : playerHands) {
			if (hand.seat > after && hand.bet >= 2 && !isNatural(hand)) {
				return hand.seat;
			}
		}
		return -1;
	}

	private long seatBet(int seat) {
		for (BjHand hand : playerHands) {
			if (hand.seat == seat) {
				return hand.bet;
			}
		}
		return 0;
	}

	private void peek() {
		insurancePhase = false;
		activeSeat = -1;
		Card up = dealer.cards.getFirst().card();
		boolean canPeek = up.rank() == Rank.ACE || up.rank().blackjackValue() == 10;
		boolean dealerNatural = total(dealer.cardList()) == 21;
		if (canPeek && dealerNatural) {
			dealer.revealAll();
			updateValue(dealer);
			dealer.result = "blackjack";
			log("gamblecraft.log.dealer_blackjack");
			settleAll(true);
			return;
		}
		for (BjHand hand : playerHands) {
			if (isNatural(hand)) {
				hand.finished = true;
				hand.view.result = "blackjack";
				hand.naturalPayout = hand.bet + Money.scale(hand.bet, 3, 2, rng);
			}
		}
		phase = Phase.PLAYER_TURN;
		nextTurn();
	}

	private void nextTurn() {
		for (BjHand hand : playerHands) {
			hand.view.active = false;
		}
		for (BjHand hand : playerHands) {
			if (!hand.finished) {
				current = hand;
				activeSeat = hand.seat;
				hand.view.active = true;
				return;
			}
		}
		current = null;
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
		boolean anyLive = playerHands.stream().anyMatch(h -> !h.surrendered && h.naturalPayout == 0 && total(h.view.cardList()) <= 21);
		List<Card> cards = dealer.cardList();
		int total = total(cards);
		if (anyLive && (total < 17 || (total == 17 && isSoft(cards) && dealerHitsSoft17))) {
			deal(dealer, true);
			return;
		}
		if (total > 21) {
			dealer.result = "bust";
		}
		settleAll(false);
	}

	private void settleAll(boolean dealerNatural) {
		int dealerTotal = total(dealer.cardList());
		long[] payout = new long[seats.length];
		boolean[] natural = new boolean[seats.length];
		for (BjHand hand : playerHands) {
			hand.view.active = false;
			long win;
			int total = total(hand.view.cardList());
			if (dealerNatural) {
				win = isNatural(hand) ? hand.bet : 0;
				hand.view.result = isNatural(hand) ? "push" : "lose";
			} else if (hand.surrendered) {
				win = Money.scale(hand.bet, 1, 2, rng);
			} else if (hand.naturalPayout > 0) {
				win = hand.naturalPayout;
				natural[hand.seat] = true;
			} else if (total > 21) {
				win = 0;
			} else if (dealerTotal > 21 || total > dealerTotal) {
				win = hand.bet * 2;
				hand.view.result = "win";
			} else if (total == dealerTotal) {
				win = hand.bet;
				hand.view.result = "push";
			} else {
				win = 0;
				hand.view.result = "lose";
			}
			payout[hand.seat] += win;
		}
		for (int seat : roundSeats()) {
			if (dealerNatural) {
				payout[seat] += insurance[seat] * 3;
			}
			long committed = seats[seat].committed;
			String outcome = payout[seat] > committed ? (natural[seat] ? "blackjack" : "win")
					: payout[seat] == committed ? "push" : "lose";
			settle(seat, payout[seat], outcome);
		}
		finishRound();
	}

	// ---------------------------------------------------------------- decisions

	@Override
	protected List<ActionOption> legalActions(int seat) {
		if (insurancePhase) {
			return List.of(ActionOption.costing(Act.INSURANCE, seatBet(seat) / 2), ActionOption.free(Act.NO_INSURANCE));
		}
		BjHand hand = current;
		List<ActionOption> options = new ArrayList<>();
		options.add(ActionOption.free(Act.HIT));
		options.add(ActionOption.free(Act.STAND));
		if (hand.view.cards.size() == 2 && !hand.splitAces) {
			options.add(ActionOption.costing(Act.DOUBLE, hand.bet));
			List<Card> cards = hand.view.cardList();
			long seatHands = playerHands.stream().filter(h -> h.seat == seat).count();
			if (cards.get(0).rank().blackjackValue() == cards.get(1).rank().blackjackValue() && seatHands < MAX_HANDS_PER_SEAT) {
				options.add(ActionOption.costing(Act.SPLIT, hand.bet));
			}
			if (!hand.fromSplit && seatHands == 1) {
				options.add(ActionOption.free(Act.SURRENDER));
			}
		}
		return options;
	}

	@Override
	public Act defaultAct(int seat) {
		return insurancePhase ? Act.NO_INSURANCE : Act.STAND;
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		BjHand hand = current;
		switch (act) {
			case INSURANCE -> {
				insurance[seat] = cost;
				nextInsurance(seat);
			}
			case NO_INSURANCE -> nextInsurance(seat);
			case HIT -> {
				then(() -> {
					deal(hand.view, true);
					int total = total(hand.view.cardList());
					if (total > 21) {
						hand.finished = true;
						hand.view.result = "bust";
					} else if (total == 21) {
						hand.finished = true;
					}
				});
				then(this::nextTurn);
			}
			case STAND -> {
				hand.finished = true;
				nextTurn();
			}
			case DOUBLE -> {
				hand.bet += cost;
				hand.finished = true;
				then(() -> {
					deal(hand.view, true);
					if (total(hand.view.cardList()) > 21) {
						hand.view.result = "bust";
					}
				});
				then(this::nextTurn);
			}
			case SPLIT -> split(hand, cost);
			case SURRENDER -> {
				hand.surrendered = true;
				hand.finished = true;
				hand.view.result = "surrender";
				nextTurn();
			}
			default -> throw new IllegalStateException();
		}
	}

	private void nextInsurance(int seat) {
		int next = firstInsuranceSeat(seat);
		if (next >= 0) {
			activeSeat = next;
		} else {
			peek();
		}
	}

	private void split(BjHand hand, long cost) {
		int seatHands = (int) playerHands.stream().filter(h -> h.seat == hand.seat).count();
		TableHand view = new TableHand("s" + hand.seat + "h" + seatHands, hand.seat, "gamblecraft.hand.player", false);
		BjHand second = new BjHand(view, hand.seat, cost);
		PlacedCard moved = hand.view.cards.removeLast();
		view.cards.add(moved);
		boolean aces = moved.card().rank() == Rank.ACE;
		hand.fromSplit = second.fromSplit = true;
		hand.splitAces = second.splitAces = aces;
		playerHands.add(playerHands.indexOf(hand) + 1, second);
		hands.add(hands.indexOf(hand.view) + 1, view);
		updateValue(hand.view);
		updateValue(view);
		then(() -> deal(hand.view, true));
		then(() -> deal(view, true));
		then(() -> {
			for (BjHand h : List.of(hand, second)) {
				if (aces || total(h.view.cardList()) == 21) {
					h.finished = true;
				}
			}
			nextTurn();
		});
	}
}
