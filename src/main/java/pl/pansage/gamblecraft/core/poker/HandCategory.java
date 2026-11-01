package pl.pansage.gamblecraft.core.poker;

import java.util.Locale;

/** Five-card poker hand categories, weakest first. */
public enum HandCategory {
	HIGH_CARD,
	PAIR,
	TWO_PAIR,
	THREE_OF_A_KIND,
	STRAIGHT,
	FLUSH,
	FULL_HOUSE,
	FOUR_OF_A_KIND,
	STRAIGHT_FLUSH,
	ROYAL_FLUSH;

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
