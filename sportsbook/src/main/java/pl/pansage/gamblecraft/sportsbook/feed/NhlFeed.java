package pl.pansage.gamblecraft.sportsbook.feed;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The NHL's own API: {@code api-web.nhle.com/v1/schedule/<date>} gives a week of games with their scores. */
public final class NhlFeed implements FeedSource {
	public static final NhlFeed INSTANCE = new NhlFeed();
	private static final String BASE = "https://api-web.nhle.com/v1/schedule/";

	private NhlFeed() {
	}

	@Override
	public Source id() {
		return Source.NHL;
	}

	@Override
	public List<SportEvent> fetch(League league, LocalDate from, LocalDate to, Http http, long now) throws IOException {
		if (league != League.NHL) {
			throw new IOException("NHL.com only carries the NHL");
		}
		Map<String, SportEvent> out = new LinkedHashMap<>();
		for (LocalDate week = from; !week.isAfter(to); week = week.plusDays(7)) {
			for (SportEvent event : parse(http.get(BASE + week))) {
				out.put(event.key(), event);
			}
		}
		return new ArrayList<>(out.values());
	}

	public static List<SportEvent> parse(String body) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		List<SportEvent> events = new ArrayList<>();
		for (JsonObject day : Json.objects(root, "gameWeek")) {
			for (JsonObject game : Json.objects(day, "games")) {
				String id = Json.str(game, "id");
				long kickoff = Json.time(Json.str(game, "startTimeUTC"));
				String home = team(Json.obj(game, "homeTeam"));
				String away = team(Json.obj(game, "awayTeam"));
				if (id.isEmpty() || kickoff == 0 || home.isEmpty() || away.isEmpty()) {
					continue;
				}
				EventState state = switch (Json.str(game, "gameScheduleState")) {
					case "PPD" -> EventState.POSTPONED;
					case "CNCL" -> EventState.CANCELLED;
					default -> switch (Json.str(game, "gameState")) {
						case "LIVE", "CRIT" -> EventState.LIVE;
						case "FINAL", "OFF" -> EventState.FINISHED;
						default -> EventState.SCHEDULED;
					};
				};
				int homeScore = state == EventState.SCHEDULED ? -1 : Json.integer(game, "homeTeam", "score");
				int awayScore = state == EventState.SCHEDULED ? -1 : Json.integer(game, "awayTeam", "score");
				// the moneyline counts overtime and the shootout, which the final score already includes
				Outcome result = state == EventState.FINISHED ? SportEvent.byScore(Sport.HOCKEY, homeScore, awayScore) : null;
				String clock = "";
				if (state == EventState.LIVE) {
					String type = Json.str(game, "periodDescriptor", "periodType");
					clock = "REG".equals(type) || type.isEmpty() ? "P" + Json.integer(game, "periodDescriptor", "number") : type;
				}
				events.add(new SportEvent("nhl:" + id, League.NHL, Source.NHL, home, away, kickoff, state, homeScore, awayScore, result, clock,
						null, "", ""));
			}
		}
		return events;
	}

	private static String team(JsonObject team) {
		if (team == null) {
			return "";
		}
		String place = Json.str(team, "placeName", "default");
		String name = Json.str(team, "commonName", "default");
		if (name.isEmpty()) {
			name = Json.str(team, "name", "default");
		}
		return (place.isEmpty() ? name : place + " " + name).trim();
	}
}
