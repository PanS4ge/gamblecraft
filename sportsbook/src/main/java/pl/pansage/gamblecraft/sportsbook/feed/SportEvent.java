package pl.pansage.gamblecraft.sportsbook.feed;

/**
 * One match or fight as a feed reports it. {@code key} is unique across sources ("espn:401878762"); scores are -1
 * while unknown; {@code result} is set once the event is finished (null for a finished event with no winner, such as
 * a drawn fight); {@code clock} is the live minute or period ("75'", "Q3 5:12") or empty; the records are the season
 * records the feed gives ("4-1-3") or empty, and feed the odds model.
 */
public record SportEvent(String key, League league, Source source, String home, String away, long kickoff, EventState state,
		int homeScore, int awayScore, Outcome result, String clock, Odds odds, String homeRecord, String awayRecord) {
	public SportEvent withOdds(Odds odds) {
		return new SportEvent(key, league, source, home, away, kickoff, state, homeScore, awayScore, result, clock, odds, homeRecord, awayRecord);
	}

	public SportEvent withState(EventState state, int homeScore, int awayScore, Outcome result, String clock) {
		return new SportEvent(key, league, source, home, away, kickoff, state, homeScore, awayScore, result, clock, odds, homeRecord, awayRecord);
	}

	public boolean hasScore() {
		return homeScore >= 0 && awayScore >= 0;
	}

	public String score() {
		return hasScore() ? homeScore + ":" + awayScore : "";
	}

	/** Bets are taken until a minute before the start. */
	public boolean open(long now) {
		return state == EventState.SCHEDULED && kickoff - now > 60_000L;
	}

	/** The winner by score, or a draw on equal scores where draws count. */
	public static Outcome byScore(Sport sport, int home, int away) {
		if (home < 0 || away < 0) {
			return null;
		}
		return home > away ? Outcome.HOME : home < away ? Outcome.AWAY : sport.draws ? Outcome.DRAW : null;
	}
}
