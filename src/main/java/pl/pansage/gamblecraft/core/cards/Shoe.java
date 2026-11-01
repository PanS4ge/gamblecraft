package pl.pansage.gamblecraft.core.cards;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * One or more decks shuffled together. Cards come off the end of the list.
 * {@link #needsShuffle()} tells the table to reshuffle between rounds once the cut card is reached.
 */
public final class Shoe {
	private final List<Card> cards = new ArrayList<>();
	private final List<Card> template = new ArrayList<>();
	private final RandomGenerator rng;
	private final int cutCard;

	public Shoe(int decks, RandomGenerator rng) {
		this(decks, card -> true, rng);
	}

	/** A shoe containing only the cards accepted by {@code filter} (e.g. Oczko uses nines and up). */
	public Shoe(int decks, Predicate<Card> filter, RandomGenerator rng) {
		this.rng = rng;
		for (int d = 0; d < Math.max(1, decks); d++) {
			for (Card card : Card.fullDeck()) {
				if (filter.test(card)) {
					template.add(card);
				}
			}
		}
		this.cutCard = decks > 1 ? template.size() / 4 : 0;
		shuffle();
	}

	/** Puts every card back and shuffles (Fisher-Yates with the table's generator). */
	public void shuffle() {
		cards.clear();
		cards.addAll(template);
		for (int i = cards.size() - 1; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			Card tmp = cards.get(i);
			cards.set(i, cards.get(j));
			cards.set(j, tmp);
		}
		if (rigged != null) {
			putOnTop(rigged);
		}
	}

	public Card draw() {
		if (cards.isEmpty()) {
			shuffle();
		}
		rigged = null;
		return cards.removeLast();
	}

	private List<Card> rigged;

	/**
	 * Test helper: the next cards drawn will be {@code top} in order, even if the game shuffles first.
	 * (The rigged cards are added on top, so the shoe may briefly hold duplicates.)
	 */
	public void stack(Collection<Card> top) {
		rigged = new ArrayList<>(top);
		putOnTop(rigged);
	}

	private void putOnTop(List<Card> ordered) {
		for (int i = ordered.size() - 1; i >= 0; i--) {
			cards.add(ordered.get(i));
		}
	}

	/** How many undealt cards match {@code test} (exact odds for Hi-Lo). */
	public int count(Predicate<Card> test) {
		int n = 0;
		for (Card card : cards) {
			if (test.test(card)) {
				n++;
			}
		}
		return n;
	}

	public boolean needsShuffle() {
		return cards.size() <= cutCard + 15;
	}

	public int remaining() {
		return cards.size();
	}

	public int size() {
		return template.size();
	}
}
