package pl.pansage.gamblecraft.core.poker;

import java.util.List;

import pl.pansage.gamblecraft.core.cards.Card;

/**
 * An evaluated poker hand: category, tie-break ranks (2..14, most significant first)
 * and the cards that make the hand.
 */
public record HandValue(HandCategory category, List<Integer> tiebreak, List<Card> cards) implements Comparable<HandValue> {
	public HandValue {
		tiebreak = List.copyOf(tiebreak);
		cards = List.copyOf(cards);
	}

	@Override
	public int compareTo(HandValue other) {
		int cmp = Integer.compare(category.ordinal(), other.category.ordinal());
		if (cmp != 0) {
			return cmp;
		}
		for (int i = 0; i < Math.min(tiebreak.size(), other.tiebreak.size()); i++) {
			cmp = Integer.compare(tiebreak.get(i), other.tiebreak.get(i));
			if (cmp != 0) {
				return cmp;
			}
		}
		return 0;
	}

	public boolean beats(HandValue other) {
		return compareTo(other) > 0;
	}
}
