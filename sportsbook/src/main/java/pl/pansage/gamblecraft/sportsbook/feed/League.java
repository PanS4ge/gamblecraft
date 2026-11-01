package pl.pansage.gamblecraft.sportsbook.feed;

import java.util.List;

/**
 * The competitions on offer, each with its ids in the feeds and the sources to try, best first. ESPN dropped the
 * Ekstraklasa from its scoreboards, so the Polish league comes from TheSportsDB.
 */
public enum League {
	EKSTRAKLASA("ekstraklasa", Sport.FOOTBALL, null, "4422", 7, Source.THESPORTSDB),
	PREMIER_LEAGUE("premier_league", Sport.FOOTBALL, "soccer/eng.1", "4328", 7, Source.ESPN, Source.THESPORTSDB),
	LA_LIGA("la_liga", Sport.FOOTBALL, "soccer/esp.1", "4335", 7, Source.ESPN, Source.THESPORTSDB),
	BUNDESLIGA("bundesliga", Sport.FOOTBALL, "soccer/ger.1", "4331", 7, Source.ESPN, Source.OPENLIGADB, Source.THESPORTSDB),
	SERIE_A("serie_a", Sport.FOOTBALL, "soccer/ita.1", "4332", 7, Source.ESPN, Source.THESPORTSDB),
	CHAMPIONS_LEAGUE("champions_league", Sport.FOOTBALL, "soccer/uefa.champions", "4480", 7, Source.ESPN, Source.THESPORTSDB),
	NBA("nba", Sport.BASKETBALL, "basketball/nba", "4387", 3, Source.ESPN, Source.THESPORTSDB),
	NHL("nhl", Sport.HOCKEY, "hockey/nhl", "4380", 3, Source.ESPN, Source.NHL, Source.THESPORTSDB),
	ATP("atp", Sport.TENNIS, "tennis/atp", null, 2, Source.ESPN),
	WTA("wta", Sport.TENNIS, "tennis/wta", null, 2, Source.ESPN),
	UFC("ufc", Sport.MMA, "mma/ufc", null, 8, Source.ESPN);

	public final String id;
	public final Sport sport;
	/** {@code sport/league} in ESPN's scoreboard urls, or null. */
	public final String espn;
	/** TheSportsDB league id, or null. */
	public final String sportsDb;
	/** How many days of fixtures to offer. */
	public final int daysAhead;
	/** Sources that carry the league, in order of preference. */
	public final List<Source> sources;

	League(String id, Sport sport, String espn, String sportsDb, int daysAhead, Source... sources) {
		this.id = id;
		this.sport = sport;
		this.espn = espn;
		this.sportsDb = sportsDb;
		this.daysAhead = daysAhead;
		this.sources = List.of(sources);
	}

	public String translationKey() {
		return "gamblecraft_sportsbook.league." + id;
	}

	public static League byId(String id) {
		for (League league : values()) {
			if (league.id.equals(id)) {
				return league;
			}
		}
		return null;
	}

	public static League of(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
	}
}
