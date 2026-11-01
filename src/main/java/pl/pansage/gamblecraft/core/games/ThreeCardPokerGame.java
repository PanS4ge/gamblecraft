package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.poker.ThreeCardEvaluator;
import pl.pansage.gamblecraft.core.poker.ThreeCardEvaluator.Category;
import pl.pansage.gamblecraft.core.poker.ThreeCardEvaluator.Value;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;

/**
 * Three Card Poker. Wager amount = Ante, side = optional Pair Plus.
 * After seeing three cards the player folds or plays (Play bet = Ante). Dealer qualifies with queen-high:
 * if not, Ante pays 1:1 and Play is returned; otherwise the higher hand wins Ante and Play 1:1.
 * Ante bonus (straight 1, trips 4, straight flush 5) and Pair Plus pay regardless of the dealer.
 */
public final class ThreeCardPokerGame extends CardGame {
	private final Shoe shoe;
	private final List<Integer> queue = new ArrayList<>();
	private final boolean[] played;
	private TableHand dealer;

	public ThreeCardPokerGame(int seats, RandomGenerator rng) {
		super(seats, rng);
		this.shoe = new Shoe(1, rng);
		this.played = new boolean[seats];
	}

	@Override
	public String id() {
		return "three_card_poker";
	}

	public Shoe shoe() {
		return shoe;
	}

	@Override
	public boolean isValidWager(Wager wager) {
		return wager.amount() > 0 && wager.side() >= 0 && wager.option() == 0;
	}

	public static int pairPlus(Category category) {
		return switch (category) {
			case STRAIGHT_FLUSH -> 40;
			case THREE_OF_A_KIND -> 30;
			case STRAIGHT -> 6;
			case FLUSH -> 3;
			case PAIR -> 1;
			default -> 0;
		};
	}

	public static int anteBonus(Category category) {
		return switch (category) {
			case STRAIGHT_FLUSH -> 5;
			case THREE_OF_A_KIND -> 4;
			case STRAIGHT -> 1;
			default -> 0;
		};
	}

	@Override
	protected void onStartRound() {
		shoe.shuffle();
		queue.clear();
		java.util.Arrays.fill(played, false);
		List<TableHand> players = new ArrayList<>();
		for (int seat : roundSeats()) {
			TableHand hand = new TableHand("s" + seat, seat, "gamblecraft.hand.player", false);
			hands.add(hand);
			players.add(hand);
			queue.add(seat);
		}
		dealer = new TableHand("dealer", DEALER, "gamblecraft.hand.dealer", false);
		hands.add(dealer);
		for (int i = 0; i < 3; i++) {
			for (TableHand hand : players) {
				then(() -> {
					hand.add(shoe.draw(), true);
					if (hand.cards.size() == 3) {
						hand.value = Msg.of("gamblecraft.poker3." + ThreeCardEvaluator.evaluate(hand.cardList()).category().key());
					}
				});
			}
			then(() -> dealer.add(shoe.draw(), false));
		}
		then(() -> {
			phase = Phase.PLAYER_TURN;
			nextSeat();
		});
	}

	private void nextSeat() {
		if (activeSeat >= 0) {
			hand("s" + activeSeat).active = false;
		}
		if (queue.isEmpty()) {
			activeSeat = -1;
			phase = Phase.DEALER_TURN;
			return;
		}
		activeSeat = queue.removeFirst();
		hand("s" + activeSeat).active = true;
	}

	@Override
	protected void advance() {
		if (phase != Phase.DEALER_TURN) {
			return;
		}
		if (!dealer.cards.getFirst().faceUp()) {
			dealer.revealAll();
			Value d = ThreeCardEvaluator.evaluate(dealer.cardList());
			dealer.value = Msg.of("gamblecraft.poker3." + d.category().key());
			if (!d.qualifies()) {
				dealer.result = "not_qualified";
			}
			return;
		}
		settleAll();
	}

	private void settleAll() {
		Value d = ThreeCardEvaluator.evaluate(dealer.cardList());
		for (int seat : roundSeats()) {
			TableHand hand = hand("s" + seat);
			Value p = ThreeCardEvaluator.evaluate(hand.cardList());
			Wager w = seats[seat].wager;
			long ante = w.amount();
			long payout = 0;
			if (w.side() > 0) {
				int pp = pairPlus(p.category());
				payout += pp > 0 ? w.side() * (pp + 1) : 0;
			}
			if (played[seat]) {
				payout += ante * anteBonus(p.category());
				if (!d.qualifies()) {
					payout += ante * 2 + ante;
					hand.result = "win";
				} else {
					int cmp = p.compareTo(d);
					if (cmp > 0) {
						payout += ante * 4;
						hand.result = "win";
					} else if (cmp == 0) {
						payout += ante * 2;
						hand.result = "push";
					} else {
						hand.result = "lose";
					}
				}
			} else {
				hand.result = "fold";
			}
			long committed = seats[seat].committed;
			settle(seat, payout, payout > committed ? "win" : payout == committed ? "push" : "lose");
		}
		finishRound();
	}

	@Override
	protected List<ActionOption> legalActions(int seat) {
		return List.of(ActionOption.costing(Act.PLAY, seats[seat].wager.amount()), ActionOption.free(Act.FOLD));
	}

	@Override
	public Act defaultAct(int seat) {
		return Act.FOLD;
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		played[seat] = act == Act.PLAY;
		nextSeat();
	}
}
