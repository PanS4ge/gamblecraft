package pl.pansage.gamblecraft.sportsbook.bet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.Source;

/**
 * One selection on a coupon, with the price locked in when it was bought and what the feed last said about the
 * game. The source is kept so the game is settled from the feed it was bought from.
 */
public final class Pick {
	public enum Status {
		WAITING,
		LIVE,
		WON,
		LOST,
		VOID;

		public boolean decided() {
			return this == WON || this == LOST || this == VOID;
		}

		public static Status of(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : WAITING;
		}
	}

	public static final Codec<Pick> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("event").forGetter(p -> p.key),
			Codec.STRING.fieldOf("league").forGetter(p -> p.league.id),
			Codec.STRING.fieldOf("source").forGetter(p -> p.source.id),
			Codec.STRING.fieldOf("home").forGetter(p -> p.home),
			Codec.STRING.fieldOf("away").forGetter(p -> p.away),
			Codec.LONG.fieldOf("kickoff").forGetter(p -> p.kickoff),
			Codec.INT.fieldOf("selection").forGetter(p -> p.selection.ordinal()),
			Codec.DOUBLE.fieldOf("odds").forGetter(p -> p.odds),
			Codec.INT.fieldOf("status").forGetter(p -> p.status.ordinal()),
			Codec.STRING.optionalFieldOf("score", "").forGetter(p -> p.score),
			Codec.STRING.optionalFieldOf("clock", "").forGetter(p -> p.clock)
	).apply(i, (key, league, source, home, away, kickoff, selection, odds, status, score, clock) -> new Pick(key,
			League.byId(league) == null ? League.PREMIER_LEAGUE : League.byId(league), Source.byId(source) == null ? Source.ESPN : Source.byId(source),
			home, away, kickoff, Outcome.of(selection) == null ? Outcome.HOME : Outcome.of(selection), odds, Status.of(status), score, clock)));

	public final String key;
	public final League league;
	public final Source source;
	public final String home;
	public final String away;
	public final long kickoff;
	public final Outcome selection;
	public final double odds;
	public Status status;
	public String score;
	public String clock;

	public Pick(String key, League league, Source source, String home, String away, long kickoff, Outcome selection, double odds,
			Status status, String score, String clock) {
		this.key = key;
		this.league = league;
		this.source = source;
		this.home = home;
		this.away = away;
		this.kickoff = kickoff;
		this.selection = selection;
		this.odds = odds;
		this.status = status;
		this.score = score;
		this.clock = clock;
	}
}
