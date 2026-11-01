package pl.pansage.gamblecraft.sportsbook.feed;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * OpenLigaDB, a community database of German football ({@code api.openligadb.de}). It is organised by matchday, so a
 * fetch reads the current matchday and the ones either side of it and keeps the games inside the window.
 */
public final class OpenLigaFeed implements FeedSource {
	public static final OpenLigaFeed INSTANCE = new OpenLigaFeed();
	private static final String BASE = "https://api.openligadb.de/getmatchdata/bl1";
	private static final int MATCHDAYS = 34;

	private OpenLigaFeed() {
	}

	@Override
	public Source id() {
		return Source.OPENLIGADB;
	}

	@Override
	public List<SportEvent> fetch(League league, LocalDate from, LocalDate to, Http http, long now) throws IOException {
		if (league != League.BUNDESLIGA) {
			throw new IOException("OpenLigaDB only carries the Bundesliga here");
		}
		String current = http.get(BASE);
		JsonElement root = JsonParser.parseString(current);
		Map<String, SportEvent> out = new LinkedHashMap<>();
		List<JsonObject> matches = Json.objects(root);
		if (matches.isEmpty()) {
			return List.of();
		}
		int season = Json.integer(matches.getFirst(), "leagueSeason");
		int matchday = Json.integer(matches.getFirst(), "group", "groupOrderID");
		List<String> bodies = new ArrayList<>();
		bodies.add(current);
		for (int day : new int[] {matchday - 1, matchday + 1}) {
			if (season > 0 && day >= 1 && day <= MATCHDAYS) {
				bodies.add(http.get(BASE + "/" + season + "/" + day));
			}
		}
		for (String body : bodies) {
			for (SportEvent event : parse(body, now)) {
				LocalDate date = Instant.ofEpochMilli(event.kickoff()).atZone(ZoneOffset.UTC).toLocalDate();
				if (!date.isBefore(from) && !date.isAfter(to)) {
					out.put(event.key(), event);
				}
			}
		}
		return new ArrayList<>(out.values());
	}

	public static List<SportEvent> parse(String body, long now) {
		List<SportEvent> events = new ArrayList<>();
		for (JsonObject match : Json.objects(JsonParser.parseString(body))) {
			String id = Json.str(match, "matchID");
			long kickoff = Json.time(Json.str(match, "matchDateTimeUTC"));
			String home = Json.str(match, "team1", "teamName");
			String away = Json.str(match, "team2", "teamName");
			if (id.isEmpty() || kickoff == 0 || home.isEmpty() || away.isEmpty()) {
				continue;
			}
			boolean finished = Json.bool(match, "matchIsFinished");
			int homeScore = -1;
			int awayScore = -1;
			for (JsonObject result : Json.objects(match, "matchResults")) {
				// result type 2 is the final score after the regular 90 minutes
				if (Json.integer(result, "resultTypeID") == 2) {
					homeScore = Json.integer(result, "pointsTeam1");
					awayScore = Json.integer(result, "pointsTeam2");
				}
			}
			EventState state = finished ? EventState.FINISHED : kickoff <= now ? EventState.LIVE : EventState.SCHEDULED;
			if (state == EventState.LIVE && homeScore < 0) {
				homeScore = 0;
				awayScore = 0;
				for (JsonObject goal : Json.objects(match, "goals")) {
					homeScore = Math.max(homeScore, Json.integer(goal, "scoreTeam1"));
					awayScore = Math.max(awayScore, Json.integer(goal, "scoreTeam2"));
				}
			}
			Outcome result = finished ? SportEvent.byScore(Sport.FOOTBALL, homeScore, awayScore) : null;
			events.add(new SportEvent("openligadb:" + id, League.BUNDESLIGA, Source.OPENLIGADB, home, away, kickoff, state,
					homeScore, awayScore, result, state == EventState.LIVE ? "LIVE" : "", null, "", ""));
		}
		return events;
	}
}
