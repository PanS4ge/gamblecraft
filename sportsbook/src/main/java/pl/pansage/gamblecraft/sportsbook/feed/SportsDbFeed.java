package pl.pansage.gamblecraft.sportsbook.feed;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * TheSportsDB's free tier ({@code thesportsdb.com/api/v1/json/123/eventsday.php?d=<date>&l=<league id>}); "123" is
 * the public key its documentation gives for free use. One request per day (per matchday for football), and a few
 * seconds between requests: the free tier answers 429 to anything faster.
 */
public final class SportsDbFeed implements FeedSource {
	public static final SportsDbFeed INSTANCE = new SportsDbFeed();
	private static final String API = "https://www.thesportsdb.com/api/v1/json/123/";
	private static final String BASE = API + "eventsday.php";
	private static final Set<String> FINAL = Set.of("FT", "AET", "PEN", "AOT", "AP", "MATCH FINISHED", "FINISHED", "FINAL", "AW", "WO");
	private static final Set<String> NOT_STARTED = Set.of("", "NS", "NOT STARTED", "TBD", "SCHEDULED");
	private static final Set<String> POSTPONED = Set.of("PST", "POSTPONED", "MATCH POSTPONED");
	private static final Set<String> CANCELLED = Set.of("CANC", "CANCELLED", "MATCH CANCELLED", "ABD", "ABANDONED", "MATCH ABANDONED", "SUSP");

	private SportsDbFeed() {
	}

	@Override
	public Source id() {
		return Source.THESPORTSDB;
	}

	@Override
	public List<SportEvent> fetch(League league, LocalDate from, LocalDate to, Http http, long now) throws IOException {
		if (league.sportsDb == null) {
			throw new IOException("TheSportsDB does not carry " + league.id);
		}
		if (league.sport == Sport.FOOTBALL && ChronoUnit.DAYS.between(from, to) > 3) {
			return byRounds(league, from, to, http, LocalDate.now(ZoneOffset.UTC));
		}
		Map<String, SportEvent> out = new LinkedHashMap<>();
		IOException failure = null;
		int read = 0;
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			try {
				for (SportEvent event : parse(league, http.get(BASE + "?d=" + day + "&l=" + league.sportsDb))) {
					out.put(event.key(), event);
				}
				read++;
			} catch (IOException | RuntimeException e) {
				failure = e instanceof IOException io ? io : new IOException(e);
			}
		}
		if (read == 0 && failure != null) {
			throw failure;
		}
		return new ArrayList<>(out.values());
	}

	/**
	 * Football by matchday, to spare the free tier: the next game names the current round, and a round (one request)
	 * holds all its games, where reading day by day takes a request for every day of the window.
	 */
	private static List<SportEvent> byRounds(League league, LocalDate from, LocalDate to, Http http, LocalDate today) throws IOException {
		List<JsonObject> next = Json.objects(JsonParser.parseString(http.get(API + "eventsnextleague.php?id=" + league.sportsDb)), "events");
		int round = next.isEmpty() ? -1 : Json.integer(next.getFirst(), "intRound");
		String season = next.isEmpty() ? "" : Json.str(next.getFirst(), "strSeason");
		if (round < 1 || season.isEmpty()) {
			throw new IOException("no current round");
		}
		long behind = Math.max(0, ChronoUnit.DAYS.between(from, today));
		long ahead = Math.max(0, ChronoUnit.DAYS.between(today, to));
		Map<String, SportEvent> out = new LinkedHashMap<>();
		for (long r = Math.max(1, round - (behind + 6) / 7 - 1); r <= round + (ahead + 6) / 7; r++) {
			// whole rounds are kept, results from before the window too: they teach the ratings
			for (SportEvent event : parse(league, http.get(API + "eventsround.php?id=" + league.sportsDb + "&r=" + r + "&s=" + season))) {
				out.put(event.key(), event);
			}
		}
		return new ArrayList<>(out.values());
	}

	public static List<SportEvent> parse(League league, String body) {
		List<SportEvent> events = new ArrayList<>();
		for (JsonObject e : Json.objects(JsonParser.parseString(body), "events")) {
			if (!league.sportsDb.equals(Json.str(e, "idLeague"))) {
				continue;
			}
			String id = Json.str(e, "idEvent");
			long kickoff = Json.time(Json.str(e, "strTimestamp"));
			if (kickoff == 0) {
				kickoff = Json.time(Json.str(e, "dateEvent") + "T" + (Json.str(e, "strTime").isEmpty() ? "12:00:00" : Json.str(e, "strTime")));
			}
			String home = Json.str(e, "strHomeTeam");
			String away = Json.str(e, "strAwayTeam");
			if (id.isEmpty() || kickoff == 0 || home.isEmpty() || away.isEmpty()) {
				continue;
			}
			String status = Json.str(e, "strStatus").trim().toUpperCase();
			int homeScore = Json.integer(e, "intHomeScore");
			int awayScore = Json.integer(e, "intAwayScore");
			EventState state;
			if ("yes".equalsIgnoreCase(Json.str(e, "strPostponed")) || POSTPONED.contains(status)) {
				state = EventState.POSTPONED;
			} else if (CANCELLED.contains(status)) {
				state = EventState.CANCELLED;
			} else if (FINAL.contains(status)) {
				state = EventState.FINISHED;
			} else if (NOT_STARTED.contains(status)) {
				state = EventState.SCHEDULED;
			} else {
				state = EventState.LIVE;
			}
			Outcome result = null;
			if (state == EventState.FINISHED) {
				result = league.sport == Sport.FOOTBALL && (status.equals("AET") || status.equals("PEN")) ? Outcome.DRAW
						: SportEvent.byScore(league.sport, homeScore, awayScore);
			}
			if (state == EventState.SCHEDULED) {
				homeScore = -1;
				awayScore = -1;
			}
			events.add(new SportEvent("thesportsdb:" + id, league, Source.THESPORTSDB, home, away, kickoff, state, homeScore, awayScore,
					result, state == EventState.LIVE ? status : "", null, "", ""));
		}
		return events;
	}
}
