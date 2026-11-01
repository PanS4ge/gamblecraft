package pl.pansage.gamblecraft.sportsbook.feed;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * ESPN's public scoreboards ({@code site.api.espn.com/apis/site/v2/sports/<sport>/<league>/scoreboard?dates=YYYYMMDD}),
 * one request per day (date ranges are refused now). Football games carry DraftKings' moneylines. Tennis events are
 * tournaments with their matches under {@code groupings}, UFC events are cards with one competition per fight.
 */
public final class EspnFeed implements FeedSource {
	public static final EspnFeed INSTANCE = new EspnFeed();
	private static final String BASE = "https://site.api.espn.com/apis/site/v2/sports/";

	private EspnFeed() {
	}

	@Override
	public Source id() {
		return Source.ESPN;
	}

	@Override
	public List<SportEvent> fetch(League league, LocalDate from, LocalDate to, Http http, long now) throws IOException {
		if (league.espn == null) {
			throw new IOException("ESPN does not carry " + league.id);
		}
		Map<String, SportEvent> out = new LinkedHashMap<>();
		IOException failure = null;
		int read = 0;
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			try {
				for (SportEvent event : parse(league, http.get(BASE + league.espn + "/scoreboard?dates=" + DateTimeFormatter.BASIC_ISO_DATE.format(day)))) {
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

	public static List<SportEvent> parse(League league, String body) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		List<SportEvent> events = new ArrayList<>();
		for (JsonObject event : Json.objects(root, "events")) {
			switch (league.sport) {
				case TENNIS -> {
					for (JsonObject grouping : Json.objects(event, "groupings")) {
						if (Json.str(grouping, "grouping", "slug").contains("singles")) {
							for (JsonObject match : Json.objects(grouping, "competitions")) {
								add(events, competition(league, match));
							}
						}
					}
				}
				case MMA -> {
					for (JsonObject fight : Json.objects(event, "competitions")) {
						add(events, competition(league, fight));
					}
				}
				default -> {
					List<JsonObject> competitions = Json.objects(event, "competitions");
					if (!competitions.isEmpty()) {
						add(events, competition(league, competitions.getFirst()));
					}
				}
			}
		}
		return events;
	}

	private static void add(List<SportEvent> events, SportEvent event) {
		if (event != null) {
			events.add(event);
		}
	}

	private static SportEvent competition(League league, JsonObject c) {
		String id = Json.str(c, "id");
		long kickoff = Json.time(Json.str(c, "date"));
		if (kickoff == 0) {
			kickoff = Json.time(Json.str(c, "startDate"));
		}
		List<JsonObject> competitors = Json.objects(c, "competitors");
		if (id.isEmpty() || kickoff == 0 || competitors.size() != 2) {
			return null;
		}
		JsonObject home = side(competitors, "home", 1, 0);
		JsonObject away = side(competitors, "away", 2, 1);
		String homeName = name(home);
		String awayName = name(away);
		if (homeName.isEmpty() || awayName.isEmpty() || homeName.equals("TBD") || awayName.equals("TBD")) {
			return null;
		}
		String statusName = Json.str(c, "status", "type", "name").toUpperCase();
		EventState state = state(statusName, Json.str(c, "status", "type", "state"), Json.bool(c, "status", "type", "completed"));
		int homeScore = score(league, home, state);
		int awayScore = score(league, away, state);
		Outcome result = null;
		if (state == EventState.FINISHED) {
			if (league.sport == Sport.FOOTBALL && (statusName.contains("AET") || statusName.contains("PEN"))) {
				// extra time or penalties: the 90 minutes the 1X2 market is about ended level
				result = Outcome.DRAW;
			} else if (Json.bool(home, "winner")) {
				result = Outcome.HOME;
			} else if (Json.bool(away, "winner")) {
				result = Outcome.AWAY;
			} else {
				result = SportEvent.byScore(league.sport, homeScore, awayScore);
			}
		}
		String clock = state == EventState.LIVE ? Json.str(c, "status", "type", "shortDetail") : "";
		return new SportEvent("espn:" + id, league, Source.ESPN, homeName, awayName, kickoff, state, homeScore, awayScore, result, clock,
				odds(league, c), record(home), record(away));
	}

	/** The competitor marked home/away, else the one with that order, else by position. */
	private static JsonObject side(List<JsonObject> competitors, String homeAway, int order, int index) {
		for (JsonObject competitor : competitors) {
			if (homeAway.equals(Json.str(competitor, "homeAway"))) {
				return competitor;
			}
		}
		for (JsonObject competitor : competitors) {
			if (Json.integer(competitor, "order") == order) {
				return competitor;
			}
		}
		return competitors.get(index);
	}

	private static String name(JsonObject competitor) {
		String team = Json.str(competitor, "team", "displayName");
		return team.isEmpty() ? Json.str(competitor, "athlete", "displayName") : team;
	}

	private static int score(League league, JsonObject competitor, EventState state) {
		if (state == EventState.SCHEDULED) {
			return -1;
		}
		if (league.sport == Sport.TENNIS) {
			// sets won
			int sets = 0;
			for (JsonObject set : Json.objects(competitor, "linescores")) {
				if (Json.bool(set, "winner")) {
					sets++;
				}
			}
			return sets;
		}
		if (league.sport == Sport.MMA) {
			return -1;
		}
		return Json.integer(competitor, "score");
	}

	private static String record(JsonObject competitor) {
		List<JsonObject> records = Json.objects(competitor, "records");
		return records.isEmpty() ? "" : Json.str(records.getFirst(), "summary");
	}

	static EventState state(String name, String state, boolean completed) {
		if (name.contains("POSTPONED")) {
			return EventState.POSTPONED;
		}
		if (name.contains("CANCEL") || name.contains("ABANDON")) {
			return EventState.CANCELLED;
		}
		return switch (state) {
			case "in" -> EventState.LIVE;
			case "post" -> completed ? EventState.FINISHED : EventState.POSTPONED;
			default -> EventState.SCHEDULED;
		};
	}

	/** The bookmaker's moneylines (DraftKings on ESPN), or null where the game has none. */
	private static Odds odds(League league, JsonObject c) {
		List<JsonObject> lines = Json.objects(c, "odds");
		if (lines.isEmpty()) {
			return null;
		}
		JsonObject o = lines.getFirst();
		double home = line(o, "home", "homeTeamOdds");
		double away = line(o, "away", "awayTeamOdds");
		double draw = 0;
		if (league.sport.draws) {
			draw = line(o, "draw", "drawOdds");
		}
		if (home <= 1 || away <= 1 || (league.sport.draws && draw <= 1)) {
			return null;
		}
		String by = Json.str(o, "provider", "displayName");
		return new Odds(home, draw, away, false, by.isEmpty() ? "DraftKings" : by);
	}

	private static double line(JsonObject o, String side, String legacy) {
		double price = Odds.fromAmerican(Json.str(o, "moneyline", side, "close", "odds"));
		if (price <= 1) {
			price = Odds.fromAmerican(Json.str(o, "moneyline", side, "open", "odds"));
		}
		if (price <= 1) {
			int ml = Json.integer(o, legacy, "moneyLine");
			price = ml == -1 ? 0 : Odds.fromAmerican(ml);
		}
		return price;
	}
}
