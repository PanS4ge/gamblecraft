package pl.pansage.gamblecraft.sportsbook.feed;

/**
 * Where fixtures and results come from. All of them are public JSON feeds that need no account: ESPN's scoreboards
 * (the data its own site shows, with DraftKings' football prices), the NHL's official API, OpenLigaDB (a community
 * Bundesliga database) and TheSportsDB's free tier. Each can be switched off in the terminal's sources window.
 */
public enum Source {
	ESPN("espn", "ESPN"),
	NHL("nhl", "NHL.com"),
	OPENLIGADB("openligadb", "OpenLigaDB"),
	THESPORTSDB("thesportsdb", "TheSportsDB");

	public final String id;
	public final String displayName;

	Source(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public static Source byId(String id) {
		for (Source source : values()) {
			if (source.id.equals(id)) {
				return source;
			}
		}
		return null;
	}

	public static Source of(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
	}
}
