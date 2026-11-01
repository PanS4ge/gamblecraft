package pl.pansage.gamblecraft.sportsbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.sportsbook.feed.EspnFeed;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.NhlFeed;
import pl.pansage.gamblecraft.sportsbook.feed.Odds;
import pl.pansage.gamblecraft.sportsbook.feed.OddsModel;
import pl.pansage.gamblecraft.sportsbook.feed.OpenLigaFeed;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.Sport;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;
import pl.pansage.gamblecraft.sportsbook.feed.SportsDbFeed;

/** The feed readers against real responses saved on 1 November 2026. */
class FeedParsersTest {
	static String fixture(String name) {
		try (InputStream in = FeedParsersTest.class.getResourceAsStream("/feeds/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static SportEvent find(List<SportEvent> events, String home) {
		return events.stream().filter(e -> e.home().equals(home)).findFirst().orElseThrow(() -> new AssertionError("no game for " + home));
	}

	@Test
	void espnFootballCarriesDraftKingsPrices() {
		List<SportEvent> events = EspnFeed.parse(League.PREMIER_LEAGUE, fixture("espn_eng_scheduled.json"));
		SportEvent game = find(events, "Newcastle United");
		assertEquals("Everton", game.away());
		assertEquals(EventState.SCHEDULED, game.state());
		assertEquals(Instant.parse("2026-11-02T20:00:00Z").toEpochMilli(), game.kickoff());
		assertNotNull(game.odds());
		// +140 / +255 / +190 in the feed
		assertEquals(2.40, game.odds().home(), 1e-9);
		assertEquals(3.55, game.odds().draw(), 1e-9);
		assertEquals(2.90, game.odds().away(), 1e-9);
		assertFalse(game.odds().model());
		assertEquals("DraftKings", game.odds().by());
		assertEquals("3-2-3", game.homeRecord());
		assertEquals(-1, game.homeScore());
	}

	@Test
	void espnFootballResults() {
		List<SportEvent> events = EspnFeed.parse(League.BUNDESLIGA, fixture("espn_ger_final.json"));
		SportEvent leverkusen = find(events, "Bayer Leverkusen");
		assertEquals(EventState.FINISHED, leverkusen.state());
		assertEquals(2, leverkusen.homeScore());
		assertEquals(4, leverkusen.awayScore());
		assertEquals(Outcome.AWAY, leverkusen.result());
		SportEvent gladbach = find(events, "Borussia Mönchengladbach");
		assertEquals(Outcome.DRAW, gladbach.result());
	}

	@Test
	void espnBasketballHasNoDrawAndNoPrice() {
		List<SportEvent> events = EspnFeed.parse(League.NBA, fixture("espn_nba.json"));
		assertFalse(events.isEmpty());
		SportEvent game = events.stream().filter(e -> e.away().equals("Oklahoma City Thunder")).findFirst().orElseThrow();
		assertEquals("4-2", game.awayRecord());
		for (SportEvent e : events) {
			assertTrue(e.result() != Outcome.DRAW);
		}
	}

	@Test
	void espnTennisReadsSinglesMatches() {
		List<SportEvent> events = EspnFeed.parse(League.ATP, fixture("espn_atp.json"));
		assertTrue(events.size() > 10, "matches: " + events.size());
		SportEvent finished = events.stream().filter(e -> e.state() == EventState.FINISHED).findFirst().orElseThrow();
		assertNotNull(finished.result());
		assertTrue(finished.homeScore() >= 0);
		assertFalse(finished.home().contains("/"), "no doubles: " + finished.home());
	}

	@Test
	void espnUfcReadsEveryFight() {
		List<SportEvent> events = EspnFeed.parse(League.UFC, fixture("espn_ufc.json"));
		assertEquals(12, events.size());
		SportEvent fight = events.stream().filter(e -> e.away().equals("Tainara Lisboa") || e.home().equals("Tainara Lisboa")).findFirst().orElseThrow();
		assertEquals(EventState.FINISHED, fight.state());
		assertNotNull(fight.result());
	}

	@Test
	void nhlSchedule() {
		List<SportEvent> events = NhlFeed.parse(fixture("nhl_schedule.json"));
		assertFalse(events.isEmpty());
		SportEvent first = events.getFirst();
		assertEquals(EventState.FINISHED, first.state());
		assertTrue(first.home().contains(" "), first.home());
		assertEquals(first.homeScore() > first.awayScore() ? Outcome.HOME : Outcome.AWAY, first.result());
	}

	@Test
	void openLigaDb() {
		List<SportEvent> events = OpenLigaFeed.parse(fixture("openligadb_bl1.json"), Instant.parse("2026-11-01T12:00:00Z").toEpochMilli());
		SportEvent elversberg = find(events, "SV 07 Elversberg");
		assertEquals("1. FSV Mainz 05", elversberg.away());
		assertEquals(EventState.FINISHED, elversberg.state());
		assertEquals(4, elversberg.homeScore());
		assertEquals(1, elversberg.awayScore());
		assertEquals(Outcome.HOME, elversberg.result());
	}

	@Test
	void theSportsDbEkstraklasa() {
		List<SportEvent> events = SportsDbFeed.parse(League.EKSTRAKLASA, fixture("tsdb_ekstraklasa_day.json"));
		assertEquals(3, events.size());
		SportEvent game = find(events, "Zagłębie Lubin");
		assertEquals("Korona Kielce", game.away());
		assertEquals(EventState.SCHEDULED, game.state());
		assertEquals(Instant.parse("2026-11-02T17:00:00Z").toEpochMilli(), game.kickoff());
		assertNull(game.odds());
	}

	@Test
	void theSportsDbResult() {
		List<SportEvent> events = SportsDbFeed.parse(League.PREMIER_LEAGUE, fixture("tsdb_epl_past.json"));
		SportEvent villa = find(events, "Aston Villa");
		assertEquals(EventState.FINISHED, villa.state());
		assertEquals(Outcome.DRAW, villa.result());
	}

	@Test
	void espnFetchFallsThroughDays() throws IOException {
		List<String> asked = new ArrayList<>();
		List<SportEvent> events = EspnFeed.INSTANCE.fetch(League.PREMIER_LEAGUE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), url -> {
			asked.add(url);
			if (url.endsWith("20261102")) {
				return fixture("espn_eng_scheduled.json");
			}
			throw new IOException("400");
		}, 0);
		assertEquals(3, asked.size());
		assertTrue(asked.getFirst().endsWith("soccer/eng.1/scoreboard?dates=20261101"));
		assertFalse(events.isEmpty());
	}

	@Test
	void theSportsDbReadsFootballByRounds() throws IOException {
		List<String> asked = new ArrayList<>();
		LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
		List<SportEvent> events = SportsDbFeed.INSTANCE.fetch(League.EKSTRAKLASA, today.minusDays(29), today.minusDays(2), url -> {
			asked.add(url);
			return url.contains("eventsnextleague") ? fixture("tsdb_ekstraklasa_next.json") : fixture("tsdb_ekstraklasa_round13.json");
		}, 0);
		assertTrue(asked.getFirst().contains("eventsnextleague.php?id=4422"));
		assertTrue(asked.size() <= 9, "requests: " + asked.size());
		assertTrue(asked.stream().anyMatch(u -> u.contains("eventsround.php?id=4422&r=13&s=2026-2027")), asked.toString());
		assertNotNull(events);
	}

	@Test
	void americanOdds() {
		assertEquals(2.40, Odds.fromAmerican("+140"), 1e-9);
		assertEquals(1.5, Odds.fromAmerican("-200"), 1e-9);
		assertEquals(2.0, Odds.fromAmerican("EVEN"), 1e-9);
		assertEquals(1.33, Odds.fromAmerican(-300), 1e-9);
		assertEquals(0, Odds.fromAmerican("n/a"), 1e-9);
	}

	@Test
	void modelPricesCarryTheMargin() {
		Odds even = OddsModel.prices(Sport.TENNIS, 1500, 1500, 0.07);
		assertEquals(even.home(), even.away(), 1e-9);
		assertEquals(1.86, even.home(), 0.01);
		Odds football = OddsModel.prices(Sport.FOOTBALL, 1700, 1400, 0.07);
		double book = 1 / football.home() + 1 / football.draw() + 1 / football.away();
		assertTrue(book > 1.06 && book < 1.09, "overround " + book);
		assertTrue(football.home() < football.away());
	}

	@Test
	void modelLearnsFromResults() {
		OddsModel model = new OddsModel();
		long t = 0;
		for (int i = 0; i < 6; i++) {
			SportEvent win = new SportEvent("t:" + i, League.NBA, null, "Strong", "Weak", t, EventState.FINISHED, 110, 90, Outcome.HOME, "", null, "", "");
			assertTrue(model.learn(win));
			assertFalse(model.learn(win));
		}
		SportEvent next = new SportEvent("t:next", League.NBA, null, "Weak", "Strong", t, EventState.SCHEDULED, -1, -1, null, "", null, "", "");
		Odds odds = model.price(next, 0.07);
		assertTrue(odds.home() > odds.away(), odds.toString());
	}
}
