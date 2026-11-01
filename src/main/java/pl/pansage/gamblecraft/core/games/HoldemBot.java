package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.poker.HandValue;
import pl.pansage.gamblecraft.core.poker.PokerEvaluator;
import pl.pansage.gamblecraft.core.table.Act;

/**
 * The house dealer's Hold'em brain: Chen-style score before the flop, a quick Monte Carlo equity
 * after it, compared with pot odds, plus an occasional bluff so it is not fully predictable.
 */
public final class HoldemBot {
	private static final int SIMULATIONS = 120;

	public record Decision(Act act, long amount) {
	}

	private HoldemBot() {
	}

	public static Decision decide(List<Card> hole, List<Card> board, int opponents, long toCall, long pot,
			long stack, long minRaiseTotal, RandomGenerator rng) {
		double strength = board.isEmpty() ? preflop(hole) : equity(hole, board, Math.max(1, opponents), rng);
		double potOdds = toCall == 0 ? 0 : (double) toCall / (pot + toCall);
		double roll = rng.nextDouble();

		boolean canRaise = stack > toCall && minRaiseTotal <= stack;
		if (canRaise && (strength > 0.8 || (strength > 0.65 && roll < 0.45) || (toCall == 0 && roll < 0.07))) {
			long target = toCall + Math.max(minRaiseTotal - toCall, (long) (pot * (strength > 0.85 ? 1.0 : 0.6)));
			long amount = Math.min(stack, Math.max(minRaiseTotal, target));
			return new Decision(amount >= stack ? Act.ALL_IN : Act.RAISE, amount);
		}
		if (toCall == 0) {
			return new Decision(Act.CHECK, 0);
		}
		if (strength >= potOdds + 0.05 || (toCall * 20 <= pot && strength > 0.25)) {
			return new Decision(Act.CALL, Math.min(toCall, stack));
		}
		return new Decision(Act.FOLD, 0);
	}

	/** Chen formula scaled to roughly 0..1. */
	public static double preflop(List<Card> hole) {
		int a = hole.get(0).rank().pokerValue();
		int b = hole.get(1).rank().pokerValue();
		int high = Math.max(a, b);
		int low = Math.min(a, b);
		double score = switch (high) {
			case 14 -> 10;
			case 13 -> 8;
			case 12 -> 7;
			case 11 -> 6;
			default -> high / 2.0;
		};
		if (a == b) {
			score = Math.max(5, score * 2);
		}
		if (hole.get(0).suit() == hole.get(1).suit()) {
			score += 2;
		}
		int gap = high - low - 1;
		if (a != b) {
			score -= switch (gap) {
				case 0 -> 0;
				case 1 -> 1;
				case 2 -> 2;
				case 3 -> 4;
				default -> 5;
			};
			if (gap <= 1 && high < 12) {
				score += 1;
			}
		}
		return Math.max(0, Math.min(1, (score + 1) / 21.0));
	}

	/** Share of simulated showdowns won against random hands (ties count half). */
	public static double equity(List<Card> hole, List<Card> board, int opponents, RandomGenerator rng) {
		Set<Card> known = new HashSet<>(hole);
		known.addAll(board);
		List<Card> deck = new ArrayList<>();
		for (Card card : Card.fullDeck()) {
			if (!known.contains(card)) {
				deck.add(card);
			}
		}
		int need = 5 - board.size() + opponents * 2;
		double wins = 0;
		for (int sim = 0; sim < SIMULATIONS; sim++) {
			for (int i = 0; i < need; i++) {
				int j = i + rng.nextInt(deck.size() - i);
				Card tmp = deck.get(i);
				deck.set(i, deck.get(j));
				deck.set(j, tmp);
			}
			List<Card> fullBoard = new ArrayList<>(board);
			int k = 0;
			while (fullBoard.size() < 5) {
				fullBoard.add(deck.get(k++));
			}
			List<Card> mine = new ArrayList<>(hole);
			mine.addAll(fullBoard);
			HandValue me = PokerEvaluator.evaluate(mine);
			boolean lost = false;
			boolean tied = false;
			for (int o = 0; o < opponents && !lost; o++) {
				List<Card> theirs = new ArrayList<>(fullBoard);
				theirs.add(deck.get(k++));
				theirs.add(deck.get(k++));
				int cmp = PokerEvaluator.evaluate(theirs).compareTo(me);
				lost = cmp > 0;
				tied |= cmp == 0;
			}
			wins += lost ? 0 : tied ? 0.5 : 1;
		}
		return wins / SIMULATIONS;
	}
}
