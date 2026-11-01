package pl.pansage.gamblecraft.core.poker;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Suit;

/** Hints for the help panel: what a poker hand is, what it is drawing to, and which cards to keep in five-card draw. */
public final class PokerAdvisor {
	public enum Draw { FLUSH_DRAW, OPEN_ENDED, GUTSHOT }

	private PokerAdvisor() {
	}

	/** Best hand made from all the cards, or null with fewer than five. */
	public static HandValue best(List<Card> cards) {
		return cards.size() >= 5 && cards.size() <= 7 ? PokerEvaluator.evaluate(cards) : null;
	}

	/** Draws still possible with these cards (only meaningful with 2-6 cards before the river). */
	public static List<Draw> draws(List<Card> cards) {
		List<Draw> draws = new ArrayList<>();
		if (cards.size() < 4 || cards.size() > 6) {
			return draws;
		}
		Map<Suit, Integer> suits = new EnumMap<>(Suit.class);
		for (Card card : cards) {
			suits.merge(card.suit(), 1, Integer::sum);
		}
		if (suits.values().stream().anyMatch(n -> n == 4)) {
			draws.add(Draw.FLUSH_DRAW);
		}
		Set<Integer> ranks = rankSet(cards);
		if (hasStraight(ranks)) {
			return draws;
		}
		int outs = 0;
		for (int add = 2; add <= 14; add++) {
			if (!ranks.contains(add)) {
				Set<Integer> with = new TreeSet<>(ranks);
				with.add(add);
				if (with.contains(14)) {
					with.add(1);
				}
				if (hasStraight(with)) {
					outs++;
				}
			}
		}
		if (outs >= 2) {
			draws.add(Draw.OPEN_ENDED);
		} else if (outs == 1) {
			draws.add(Draw.GUTSHOT);
		}
		return draws;
	}

	private static Set<Integer> rankSet(List<Card> cards) {
		Set<Integer> ranks = new TreeSet<>();
		for (Card card : cards) {
			ranks.add(card.rank().pokerValue());
			if (card.rank().pokerValue() == 14) {
				ranks.add(1);
			}
		}
		return ranks;
	}

	private static boolean hasStraight(Set<Integer> ranks) {
		for (int high = 14; high >= 5; high--) {
			boolean all = true;
			for (int i = 0; i < 5 && all; i++) {
				all = ranks.contains(high - i);
			}
			if (all) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Simple Jacks-or-Better hold strategy, as a bit mask over the five cards: keep any paying hand, then four to a
	 * flush, four to a straight, low pairs, then up to two jacks-or-better high cards; otherwise draw five.
	 */
	public static int suggestHolds(List<Card> five) {
		HandValue value = PokerEvaluator.evaluate(five);
		switch (value.category()) {
			case STRAIGHT, FLUSH, FULL_HOUSE, STRAIGHT_FLUSH, ROYAL_FLUSH -> {
				return 0b11111;
			}
			case FOUR_OF_A_KIND, THREE_OF_A_KIND, TWO_PAIR, PAIR -> {
				if (value.category() != HandCategory.PAIR || value.tiebreak().getFirst() >= 11) {
					return sameRankMask(five, 2);
				}
			}
			default -> {
			}
		}
		for (Suit suit : Suit.values()) {
			int mask = 0;
			for (int i = 0; i < 5; i++) {
				if (five.get(i).suit() == suit) {
					mask |= 1 << i;
				}
			}
			if (Integer.bitCount(mask) == 4) {
				return mask;
			}
		}
		for (int skip = 0; skip < 5; skip++) {
			List<Card> four = new ArrayList<>(five);
			four.remove(skip);
			Set<Integer> ranks = rankSet(four);
			if (ranks.size() >= 4 && draws(four).contains(Draw.OPEN_ENDED)) {
				return 0b11111 & ~(1 << skip);
			}
		}
		if (value.category() == HandCategory.PAIR) {
			return sameRankMask(five, 2);
		}
		int mask = 0;
		int kept = 0;
		for (int i = 0; i < 5 && kept < 2; i++) {
			if (five.get(i).rank().pokerValue() >= 11) {
				mask |= 1 << i;
				kept++;
			}
		}
		return mask;
	}

	private static int sameRankMask(List<Card> five, int min) {
		int mask = 0;
		for (int i = 0; i < 5; i++) {
			int v = five.get(i).rank().pokerValue();
			if (five.stream().filter(c -> c.rank().pokerValue() == v).count() >= min) {
				mask |= 1 << i;
			}
		}
		return mask;
	}
}
