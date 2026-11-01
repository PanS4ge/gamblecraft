package pl.pansage.gamblecraft.core.cards;

public enum Rank {
	TWO("2", "2"),
	THREE("3", "3"),
	FOUR("4", "4"),
	FIVE("5", "5"),
	SIX("6", "6"),
	SEVEN("7", "7"),
	EIGHT("8", "8"),
	NINE("9", "9"),
	TEN("10", "10"),
	JACK("jack", "J"),
	QUEEN("queen", "Q"),
	KING("king", "K"),
	ACE("ace", "A");

	private final String code;
	private final String symbol;

	Rank(String code, String symbol) {
		this.code = code;
		this.symbol = symbol;
	}

	public String code() {
		return code;
	}

	public String symbol() {
		return symbol;
	}

	/** 2..14, ace high. */
	public int pokerValue() {
		return ordinal() + 2;
	}

	/** Blackjack points with the ace counted as 11. */
	public int blackjackValue() {
		return switch (this) {
			case ACE -> 11;
			case JACK, QUEEN, KING -> 10;
			default -> pokerValue();
		};
	}

	/** Baccarat points: ace 1, tens and faces 0. */
	public int baccaratValue() {
		return switch (this) {
			case ACE -> 1;
			case TEN, JACK, QUEEN, KING -> 0;
			default -> pokerValue();
		};
	}
}
