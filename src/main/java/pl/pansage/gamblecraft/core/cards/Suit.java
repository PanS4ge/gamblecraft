package pl.pansage.gamblecraft.core.cards;

public enum Suit {
	HEARTS("hearts", '♥', true),
	DIAMONDS("diamonds", '♦', true),
	CLUBS("clubs", '♣', false),
	SPADES("spades", '♠', false);

	private final String code;
	private final char symbol;
	private final boolean red;

	Suit(String code, char symbol, boolean red) {
		this.code = code;
		this.symbol = symbol;
		this.red = red;
	}

	public String code() {
		return code;
	}

	public char symbol() {
		return symbol;
	}

	public boolean isRed() {
		return red;
	}
}
