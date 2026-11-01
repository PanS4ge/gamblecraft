package pl.pansage.gamblecraft.sportsbook.feed;

/** A match result as the 1X2 / moneyline market sees it. */
public enum Outcome {
	HOME("1"),
	DRAW("X"),
	AWAY("2");

	public final String label;

	Outcome(String label) {
		this.label = label;
	}

	public static Outcome of(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
	}
}
