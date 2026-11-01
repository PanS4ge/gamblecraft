package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.poker.HandCategory;
import pl.pansage.gamblecraft.core.poker.HandValue;
import pl.pansage.gamblecraft.core.poker.PokerEvaluator;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.PlacedCard;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;

/**
 * Five-card draw against a pay table (Jacks or Better, 8/5): five cards, hold any, draw once.
 * Every seat plays its own hand from its own freshly shuffled deck.
 */
public final class VideoPokerGame extends CardGame {
	/** Total returned per unit staked. */
	public static final Map<HandCategory, Integer> PAYTABLE = new EnumMap<>(Map.of(
			HandCategory.ROYAL_FLUSH, 250,
			HandCategory.STRAIGHT_FLUSH, 50,
			HandCategory.FOUR_OF_A_KIND, 25,
			HandCategory.FULL_HOUSE, 8,
			HandCategory.FLUSH, 5,
			HandCategory.STRAIGHT, 4,
			HandCategory.THREE_OF_A_KIND, 3,
			HandCategory.TWO_PAIR, 2,
			HandCategory.PAIR, 1));

	private final Shoe[] decks;
	private final List<Integer> queue = new ArrayList<>();

	public VideoPokerGame(int seats, RandomGenerator rng) {
		super(seats, rng);
		this.decks = new Shoe[seats];
		for (int i = 0; i < seats; i++) {
			decks[i] = new Shoe(1, rng);
		}
	}

	@Override
	public String id() {
		return "video_poker";
	}

	public Shoe deckOf(int seat) {
		return decks[seat];
	}

	/** Pay multiplier of a final hand: pairs only pay from jacks up. */
	public static int multiplier(HandValue value) {
		if (value.category() == HandCategory.PAIR && value.tiebreak().getFirst() < 11) {
			return 0;
		}
		return PAYTABLE.getOrDefault(value.category(), 0);
	}

	@Override
	protected void onStartRound() {
		queue.clear();
		for (int seat : roundSeats()) {
			decks[seat].shuffle();
			TableHand hand = new TableHand("s" + seat, seat, "gamblecraft.hand.player", false);
			hands.add(hand);
			queue.add(seat);
			for (int i = 0; i < 5; i++) {
				then(() -> {
					hand.add(decks[seat].draw(), true);
					if (hand.cards.size() == 5) {
						updateValue(hand);
					}
				});
			}
		}
		then(() -> {
			phase = Phase.PLAYER_TURN;
			nextSeat();
		});
	}

	private static void updateValue(TableHand hand) {
		HandValue value = PokerEvaluator.evaluate(hand.cardList());
		hand.value = multiplier(value) > 0 ? Msg.of("gamblecraft.poker." + value.category().key()) : Msg.of("gamblecraft.poker.nothing");
	}

	private void nextSeat() {
		if (activeSeat >= 0) {
			hand("s" + activeSeat).active = false;
		}
		if (queue.isEmpty()) {
			activeSeat = -1;
			finishRound();
			return;
		}
		activeSeat = queue.removeFirst();
		hand("s" + activeSeat).active = true;
	}

	@Override
	protected void advance() {
	}

	@Override
	protected List<ActionOption> legalActions(int seat) {
		return List.of(ActionOption.free(Act.HOLD), ActionOption.free(Act.DRAW));
	}

	@Override
	public Act defaultAct(int seat) {
		return Act.DRAW;
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		TableHand hand = hand("s" + seat);
		if (act == Act.HOLD) {
			if (amount < 0 || amount > 4) {
				throw new pl.pansage.gamblecraft.core.table.IllegalActionException("Hold index " + amount);
			}
			hand.marks ^= 1 << amount;
			return;
		}
		for (int i = 0; i < 5; i++) {
			int index = i;
			if ((hand.marks & (1 << i)) == 0) {
				then(() -> hand.cards.set(index, new PlacedCard(decks[seat].draw(), true)));
			}
		}
		then(() -> {
			HandValue value = PokerEvaluator.evaluate(hand.cardList());
			updateValue(hand);
			int mult = multiplier(value);
			long payout = seats[seat].committed * mult;
			hand.result = mult > 0 ? value.category().key() : "lose";
			hand.marks = mult > 0 ? winningMarks(hand.cardList(), value) : 0;
			settle(seat, payout, mult > 1 ? "win" : mult == 1 ? "push" : "lose");
			nextSeat();
		});
	}

	/** Highlights the cards that make the paying hand. */
	private static int winningMarks(List<Card> cards, HandValue value) {
		return switch (value.category()) {
			case PAIR, TWO_PAIR, THREE_OF_A_KIND, FOUR_OF_A_KIND -> {
				int marks = 0;
				for (int i = 0; i < cards.size(); i++) {
					int v = cards.get(i).rank().pokerValue();
					if (cards.stream().filter(c -> c.rank().pokerValue() == v).count() >= 2) {
						marks |= 1 << i;
					}
				}
				yield marks;
			}
			default -> 0b11111;
		};
	}
}
