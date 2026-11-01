package pl.pansage.gamblecraft.core.poker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import pl.pansage.gamblecraft.core.cards.Card;

/**
 * Best five-card hand out of 5 to 7 cards. Every 5-card combination is scored exactly
 * (at most 21 for Hold'em), so kickers and the wheel straight are always right.
 */
public final class PokerEvaluator {
	private PokerEvaluator() {
	}

	public static HandValue evaluate(List<Card> cards) {
		if (cards.size() < 5 || cards.size() > 7) {
			throw new IllegalArgumentException("Need 5-7 cards, got " + cards.size());
		}
		if (cards.size() == 5) {
			return evaluateFive(cards);
		}
		HandValue best = null;
		int n = cards.size();
		int[] idx = {0, 1, 2, 3, 4};
		while (true) {
			List<Card> five = new ArrayList<>(5);
			for (int i : idx) {
				five.add(cards.get(i));
			}
			HandValue value = evaluateFive(five);
			if (best == null || value.beats(best)) {
				best = value;
			}
			int i = 4;
			while (i >= 0 && idx[i] == n - 5 + i) {
				i--;
			}
			if (i < 0) {
				return best;
			}
			idx[i]++;
			for (int j = i + 1; j < 5; j++) {
				idx[j] = idx[j - 1] + 1;
			}
		}
	}

	static HandValue evaluateFive(List<Card> five) {
		List<Card> sorted = new ArrayList<>(five);
		sorted.sort(Comparator.comparingInt((Card c) -> c.rank().pokerValue()).reversed());

		boolean flush = sorted.stream().allMatch(c -> c.suit() == sorted.getFirst().suit());
		int straightHigh = straightHigh(sorted);

		// Distinct ranks ordered by how often they appear, then by rank: this is also the tie-break order.
		Map<Integer, Integer> counts = new TreeMap<>(Comparator.reverseOrder());
		for (Card c : sorted) {
			counts.merge(c.rank().pokerValue(), 1, Integer::sum);
		}
		List<Integer> groups = new ArrayList<>(counts.keySet());
		groups.sort(Comparator.comparingInt((Integer v) -> counts.get(v)).reversed().thenComparing(Comparator.reverseOrder()));
		int top = counts.get(groups.getFirst());
		int second = groups.size() > 1 ? counts.get(groups.get(1)) : 0;

		if (flush && straightHigh > 0) {
			HandCategory category = straightHigh == 14 ? HandCategory.ROYAL_FLUSH : HandCategory.STRAIGHT_FLUSH;
			return new HandValue(category, List.of(straightHigh), sorted);
		}
		if (top == 4) {
			return new HandValue(HandCategory.FOUR_OF_A_KIND, groups, sorted);
		}
		if (top == 3 && second == 2) {
			return new HandValue(HandCategory.FULL_HOUSE, groups, sorted);
		}
		if (flush) {
			return new HandValue(HandCategory.FLUSH, groups, sorted);
		}
		if (straightHigh > 0) {
			return new HandValue(HandCategory.STRAIGHT, List.of(straightHigh), sorted);
		}
		if (top == 3) {
			return new HandValue(HandCategory.THREE_OF_A_KIND, groups, sorted);
		}
		if (top == 2 && second == 2) {
			return new HandValue(HandCategory.TWO_PAIR, groups, sorted);
		}
		if (top == 2) {
			return new HandValue(HandCategory.PAIR, groups, sorted);
		}
		return new HandValue(HandCategory.HIGH_CARD, groups, sorted);
	}

	/** High card of a five-card straight (5 for the A-2-3-4-5 wheel), or 0. Expects cards sorted high to low. */
	private static int straightHigh(List<Card> sortedDesc) {
		int[] v = sortedDesc.stream().mapToInt(c -> c.rank().pokerValue()).toArray();
		for (int i = 1; i < v.length; i++) {
			if (v[i] == v[i - 1]) {
				return 0;
			}
		}
		if (v[0] - v[4] == 4) {
			return v[0];
		}
		if (v[0] == 14 && v[1] == 5 && v[4] == 2) {
			return 5;
		}
		return 0;
	}
}
