package pl.pansage.gamblecraft.core.games;

import java.util.List;
import java.util.Set;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.table.Act;

/** Blackjack basic strategy for many decks, dealer standing on soft 17, double after split, late surrender. */
public final class BasicStrategy {
	private BasicStrategy() {
	}

	/** The best move among {@code legal} for this hand against the dealer's up card. */
	public static Act decide(List<Card> hand, Card dealerUp, Set<Act> legal) {
		if (legal.contains(Act.NO_INSURANCE)) {
			return Act.NO_INSURANCE;
		}
		int d = dealerUp.rank().blackjackValue();
		int total = BlackjackGame.total(hand);
		boolean soft = BlackjackGame.isSoft(hand);
		boolean canDouble = legal.contains(Act.DOUBLE);
		if (legal.contains(Act.SPLIT) && hand.size() == 2 && hand.get(0).rank().blackjackValue() == hand.get(1).rank().blackjackValue()) {
			int p = hand.get(0).rank().blackjackValue();
			boolean split = switch (p) {
				case 11, 8 -> true;
				case 9 -> d <= 9 && d != 7;
				case 7 -> d <= 7;
				case 6 -> d <= 6;
				case 4 -> d == 5 || d == 6;
				case 3, 2 -> d <= 7;
				default -> false;
			};
			if (split) {
				return Act.SPLIT;
			}
		}
		if (legal.contains(Act.SURRENDER) && !soft && ((total == 16 && d >= 9) || (total == 15 && d == 10))) {
			return Act.SURRENDER;
		}
		if (soft) {
			if (total >= 19) {
				return total == 19 && d == 6 && canDouble ? Act.DOUBLE : Act.STAND;
			}
			if (total == 18) {
				if (d >= 2 && d <= 6) {
					return canDouble ? Act.DOUBLE : Act.STAND;
				}
				return d <= 8 ? Act.STAND : Act.HIT;
			}
			int lo = total == 17 ? 3 : total >= 15 ? 4 : 5;
			return canDouble && d >= lo && d <= 6 ? Act.DOUBLE : Act.HIT;
		}
		if (total >= 17) {
			return Act.STAND;
		}
		if (total >= 13) {
			return d <= 6 ? Act.STAND : Act.HIT;
		}
		if (total == 12) {
			return d >= 4 && d <= 6 ? Act.STAND : Act.HIT;
		}
		if (total == 11) {
			return canDouble && d != 11 ? Act.DOUBLE : Act.HIT;
		}
		if (total == 10) {
			return canDouble && d <= 9 ? Act.DOUBLE : Act.HIT;
		}
		if (total == 9) {
			return canDouble && d >= 3 && d <= 6 ? Act.DOUBLE : Act.HIT;
		}
		return Act.HIT;
	}
}
