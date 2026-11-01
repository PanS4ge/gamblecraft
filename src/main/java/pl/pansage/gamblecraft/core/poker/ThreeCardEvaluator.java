package pl.pansage.gamblecraft.core.poker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import pl.pansage.gamblecraft.core.cards.Card;

/** Three Card Poker ranking: a straight beats a flush, and A-2-3 is the lowest straight. */
public final class ThreeCardEvaluator {
	public enum Category {
		HIGH_CARD, PAIR, FLUSH, STRAIGHT, THREE_OF_A_KIND, STRAIGHT_FLUSH;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public record Value(Category category, List<Integer> tiebreak) implements Comparable<Value> {
		@Override
		public int compareTo(Value other) {
			int cmp = Integer.compare(category.ordinal(), other.category.ordinal());
			for (int i = 0; cmp == 0 && i < tiebreak.size(); i++) {
				cmp = Integer.compare(tiebreak.get(i), other.tiebreak.get(i));
			}
			return cmp;
		}

		/** Dealer qualifies with queen-high or better. */
		public boolean qualifies() {
			return category != Category.HIGH_CARD || tiebreak.getFirst() >= 12;
		}
	}

	private ThreeCardEvaluator() {
	}

	public static Value evaluate(List<Card> three) {
		if (three.size() != 3) {
			throw new IllegalArgumentException("Need 3 cards");
		}
		List<Integer> v = new ArrayList<>(three.stream().map(c -> c.rank().pokerValue()).toList());
		v.sort(Comparator.reverseOrder());
		boolean flush = three.stream().allMatch(c -> c.suit() == three.getFirst().suit());
		boolean distinct = !v.get(0).equals(v.get(1)) && !v.get(1).equals(v.get(2));
		int straightHigh = 0;
		if (distinct && v.get(0) - v.get(2) == 2) {
			straightHigh = v.get(0);
		} else if (v.equals(List.of(14, 3, 2))) {
			straightHigh = 3;
		}

		if (straightHigh > 0 && flush) {
			return new Value(Category.STRAIGHT_FLUSH, List.of(straightHigh, 0, 0));
		}
		if (v.get(0).equals(v.get(2))) {
			return new Value(Category.THREE_OF_A_KIND, v);
		}
		if (straightHigh > 0) {
			return new Value(Category.STRAIGHT, List.of(straightHigh, 0, 0));
		}
		if (flush) {
			return new Value(Category.FLUSH, v);
		}
		if (v.get(0).equals(v.get(1))) {
			return new Value(Category.PAIR, List.of(v.get(0), v.get(2), 0));
		}
		if (v.get(1).equals(v.get(2))) {
			return new Value(Category.PAIR, List.of(v.get(1), v.get(0), 0));
		}
		return new Value(Category.HIGH_CARD, v);
	}
}
