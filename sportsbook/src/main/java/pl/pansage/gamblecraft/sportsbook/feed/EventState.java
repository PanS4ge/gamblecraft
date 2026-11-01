package pl.pansage.gamblecraft.sportsbook.feed;

public enum EventState {
	SCHEDULED,
	LIVE,
	FINISHED,
	/** Moved to another day: bets on it are void. */
	POSTPONED,
	/** Called off or abandoned: bets on it are void. */
	CANCELLED;

	public static EventState of(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : SCHEDULED;
	}
}
