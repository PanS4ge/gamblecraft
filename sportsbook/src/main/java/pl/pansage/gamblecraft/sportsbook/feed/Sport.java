package pl.pansage.gamblecraft.sportsbook.feed;

/** What is played: whether a draw is a bet of its own, and the home side's edge in rating points. */
public enum Sport {
	FOOTBALL(true, 60),
	BASKETBALL(false, 70),
	HOCKEY(false, 30),
	TENNIS(false, 0),
	MMA(false, 0);

	public final boolean draws;
	public final int homeAdvantage;

	Sport(boolean draws, int homeAdvantage) {
		this.draws = draws;
		this.homeAdvantage = homeAdvantage;
	}
}
