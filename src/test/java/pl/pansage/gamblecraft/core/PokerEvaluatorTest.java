package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.pansage.gamblecraft.core.TestCards.cards;

import java.util.List;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.poker.HandCategory;
import pl.pansage.gamblecraft.core.poker.HandValue;
import pl.pansage.gamblecraft.core.poker.PokerEvaluator;
import pl.pansage.gamblecraft.core.poker.ThreeCardEvaluator;

class PokerEvaluatorTest {
	private static HandValue eval(String text) {
		return PokerEvaluator.evaluate(cards(text));
	}

	@Test
	void cardIdsRoundTrip() {
		for (int id = 0; id < Card.DECK_SIZE; id++) {
			Card card = Card.of(id);
			assertEquals(id, card.id());
			assertEquals(card, Card.fromCode(card.code()));
		}
	}

	@Test
	void categories() {
		assertEquals(HandCategory.ROYAL_FLUSH, eval("As Ks Qs Js 10s").category());
		assertEquals(HandCategory.STRAIGHT_FLUSH, eval("9h 8h 7h 6h 5h").category());
		assertEquals(HandCategory.FOUR_OF_A_KIND, eval("7c 7d 7h 7s 2c").category());
		assertEquals(HandCategory.FULL_HOUSE, eval("3c 3d 3h 9s 9c").category());
		assertEquals(HandCategory.FLUSH, eval("Ad 9d 7d 4d 2d").category());
		assertEquals(HandCategory.STRAIGHT, eval("10c 9d 8h 7s 6c").category());
		assertEquals(HandCategory.THREE_OF_A_KIND, eval("Qc Qd Qh 9s 2c").category());
		assertEquals(HandCategory.TWO_PAIR, eval("Jc Jd 4h 4s Ac").category());
		assertEquals(HandCategory.PAIR, eval("10c 10d 4h 3s Ac").category());
		assertEquals(HandCategory.HIGH_CARD, eval("Kc 10d 4h 3s 2c").category());
	}

	@Test
	void wheelIsFiveHighStraight() {
		HandValue wheel = eval("Ac 2d 3h 4s 5c");
		assertEquals(HandCategory.STRAIGHT, wheel.category());
		assertEquals(5, wheel.tiebreak().getFirst());
		assertTrue(eval("2c 3d 4h 5s 6c").beats(wheel));
		assertEquals(HandCategory.STRAIGHT_FLUSH, eval("Ah 2h 3h 4h 5h").category());
	}

	@Test
	void kickersDecide() {
		assertTrue(eval("Ac Ad Kh 7s 2c").beats(eval("As Ah Qh Js 10c")));
		assertTrue(eval("Kc Kd 5h 5s 9c").beats(eval("Ks Kh 5d 5c 8c")));
		assertEquals(0, eval("Ac Kd 9h 7s 2c").compareTo(eval("Ad Kc 9s 7h 2d")));
		assertTrue(eval("3c 3d 3h 2s 2c").beats(eval("2h 2d 2s Ac Ad")));
	}

	@Test
	void bestFiveOfSeven() {
		HandValue v = PokerEvaluator.evaluate(cards("As Ks 2d 2c Qs Js 10s"));
		assertEquals(HandCategory.ROYAL_FLUSH, v.category());
		HandValue fh = PokerEvaluator.evaluate(cards("8c 8d 8h 4s 4c 4d Ac"));
		assertEquals(HandCategory.FULL_HOUSE, fh.category());
		assertEquals(List.of(8, 4), fh.tiebreak());
		HandValue flushOverStraight = PokerEvaluator.evaluate(cards("9h 8h 7c 6h 5d 2h Kh"));
		assertEquals(HandCategory.FLUSH, flushOverStraight.category());
	}

	@Test
	void threeCardRanking() {
		assertEquals(ThreeCardEvaluator.Category.STRAIGHT, ThreeCardEvaluator.evaluate(cards("Ac 2d 3h")).category());
		assertEquals(ThreeCardEvaluator.Category.STRAIGHT, ThreeCardEvaluator.evaluate(cards("Qc Kd Ah")).category());
		assertTrue(ThreeCardEvaluator.evaluate(cards("4c 5d 6h")).compareTo(ThreeCardEvaluator.evaluate(cards("2c 9c Kc"))) > 0);
		assertEquals(ThreeCardEvaluator.Category.STRAIGHT_FLUSH, ThreeCardEvaluator.evaluate(cards("Jc Qc Kc")).category());
		assertTrue(ThreeCardEvaluator.evaluate(cards("Qc 7d 2h")).qualifies());
		assertTrue(!ThreeCardEvaluator.evaluate(cards("Jc 9d 2h")).qualifies());
	}
}
