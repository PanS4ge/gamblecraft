package pl.pansage.gamblecraft.core.games;

import java.util.Locale;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.table.CardGame;

/** Every card game a dealer can run, with street (village) and casino table sizes. */
public enum GameKind {
	BLACKJACK,
	OCZKO,
	HOLDEM,
	VIDEO_POKER,
	THREE_CARD_POKER,
	BACCARAT,
	WAR,
	HI_LO;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static GameKind byId(String id) {
		return valueOf(id.toUpperCase(Locale.ROOT));
	}

	/**
	 * @param casino casino tables use 6-deck shoes and more seats; the village dealer uses his own single deck
	 * @param unit   smallest stake; Hold'em blinds are {@code unit}/{@code 2*unit}
	 */
	public CardGame create(boolean casino, long unit, RandomGenerator rng) {
		int seats = casino ? 6 : 4;
		int decks = casino ? 6 : 1;
		return switch (this) {
			case BLACKJACK -> new BlackjackGame(seats, decks, false, rng);
			case OCZKO -> new OczkoGame(seats, rng);
			case HOLDEM -> new HoldemGame(seats - 1, unit, unit * 2, rng);
			case VIDEO_POKER -> new VideoPokerGame(seats, rng);
			case THREE_CARD_POKER -> new ThreeCardPokerGame(seats, rng);
			case BACCARAT -> new BaccaratGame(seats, casino ? 8 : 1, rng);
			case WAR -> new CasinoWarGame(seats, decks, rng);
			case HI_LO -> new HiLoGame(rng);
		};
	}
}
