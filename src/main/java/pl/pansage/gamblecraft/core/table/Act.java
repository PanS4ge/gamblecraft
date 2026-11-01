package pl.pansage.gamblecraft.core.table;

import java.util.Locale;

/** Every player decision across all card games. */
public enum Act {
	HIT, STAND, DOUBLE, SPLIT, SURRENDER, INSURANCE, NO_INSURANCE,
	FOLD, CHECK, CALL, RAISE, ALL_IN,
	PLAY,
	/** Video poker: toggle hold on the card at index {@code amount}. */
	HOLD,
	DRAW,
	WAR,
	HIGHER, LOWER, CASH_OUT;

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
