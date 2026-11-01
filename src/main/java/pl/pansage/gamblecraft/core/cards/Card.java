package pl.pansage.gamblecraft.core.cards;

import java.util.ArrayList;
import java.util.List;

/** One playing card. {@link #id()} (0..51) is the compact form used in saves and packets. */
public record Card(Suit suit, Rank rank) {
	public static final int DECK_SIZE = 52;
	private static final Card[] BY_ID = new Card[DECK_SIZE];

	static {
		for (Suit suit : Suit.values()) {
			for (Rank rank : Rank.values()) {
				Card card = new Card(suit, rank);
				BY_ID[card.id()] = card;
			}
		}
	}

	public int id() {
		return suit.ordinal() * 13 + rank.ordinal();
	}

	public static Card of(int id) {
		if (id < 0 || id >= DECK_SIZE) {
			throw new IllegalArgumentException("Card id out of range: " + id);
		}
		return BY_ID[id];
	}

	/** Texture/model name, e.g. {@code hearts_ace}. */
	public String code() {
		return suit.code() + "_" + rank.code();
	}

	public static Card fromCode(String code) {
		for (Card card : BY_ID) {
			if (card.code().equals(code)) {
				return card;
			}
		}
		throw new IllegalArgumentException("Unknown card code: " + code);
	}

	/** Short text such as {@code A♠}. */
	public String shortText() {
		return rank.symbol() + suit.symbol();
	}

	public static List<Card> fullDeck() {
		return new ArrayList<>(List.of(BY_ID));
	}

	@Override
	public String toString() {
		return shortText();
	}
}
