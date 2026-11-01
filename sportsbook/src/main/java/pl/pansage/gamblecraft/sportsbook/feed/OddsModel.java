package pl.pansage.gamblecraft.sportsbook.feed;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * GambleCraft's own prices for games no bookmaker in the feeds has priced (NBA, NHL, tennis, UFC, the Ekstraklasa):
 * an Elo rating per team, learned from every result the feeds report, blended with the season record when the feed
 * gives one, turned into probabilities and then into decimal odds with the house margin on top.
 */
public final class OddsModel {
	public static final double BASE = 1500;
	private static final int REMEMBERED = 6000;

	private final Map<String, Double> ratings;
	private final Map<String, Integer> games;
	/** Results already counted, oldest first. */
	private final LinkedHashSet<String> counted;

	public OddsModel() {
		this(new HashMap<>(), new HashMap<>(), new LinkedHashSet<>());
	}

	public OddsModel(Map<String, Double> ratings, Map<String, Integer> games, Set<String> counted) {
		this.ratings = new HashMap<>(ratings);
		this.games = new HashMap<>(games);
		this.counted = new LinkedHashSet<>(counted);
	}

	public Map<String, Double> ratings() {
		return ratings;
	}

	public Map<String, Integer> games() {
		return games;
	}

	public Set<String> counted() {
		return counted;
	}

	/** Results learned for a league (each counted once per side). */
	public int played(League league) {
		int n = 0;
		for (Map.Entry<String, Integer> entry : games.entrySet()) {
			if (entry.getKey().startsWith(league.id + "|")) {
				n += entry.getValue();
			}
		}
		return n / 2;
	}

	static String team(League league, String name) {
		String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
		return league.id + "|" + plain;
	}

	/** Learns from a finished event once; true if it was new. */
	public boolean learn(SportEvent event) {
		if (event.state() != EventState.FINISHED || event.result() == null || counted.contains(event.key())) {
			return false;
		}
		counted.add(event.key());
		while (counted.size() > REMEMBERED) {
			counted.remove(counted.iterator().next());
		}
		String home = team(event.league(), event.home());
		String away = team(event.league(), event.away());
		double rh = ratings.getOrDefault(home, BASE);
		double ra = ratings.getOrDefault(away, BASE);
		double expected = 1 / (1 + Math.pow(10, -(rh + event.league().sport.homeAdvantage - ra) / 400));
		double actual = switch (event.result()) {
			case HOME -> 1;
			case DRAW -> 0.5;
			case AWAY -> 0;
		};
		double k = event.league().sport == Sport.FOOTBALL ? 24 : 18;
		ratings.put(home, rh + k * (actual - expected));
		ratings.put(away, ra - k * (actual - expected));
		games.merge(home, 1, Integer::sum);
		games.merge(away, 1, Integer::sum);
		return true;
	}

	/** A team's strength: its record where the feed gives one, its learned Elo once it has a few games, or both. */
	public double rating(League league, String name, String record) {
		String key = team(league, name);
		double fromRecord = fromRecord(league.sport, record);
		int played = games.getOrDefault(key, 0);
		double elo = ratings.getOrDefault(key, BASE);
		if (Double.isNaN(fromRecord)) {
			return elo;
		}
		return played >= 5 ? (fromRecord + elo) / 2 : fromRecord;
	}

	/** "W-L", "W-D-L" (football), "W-L-OTL" (hockey) or "W-L-D" (fights) as a rating; NaN if unreadable. */
	static double fromRecord(Sport sport, String record) {
		if (record == null || !record.matches("\\d+(-\\d+){1,2}")) {
			return Double.NaN;
		}
		String[] parts = record.split("-");
		double[] n = new double[parts.length];
		for (int i = 0; i < parts.length; i++) {
			n[i] = Double.parseDouble(parts[i]);
		}
		double won;
		double played;
		if (n.length == 2) {
			won = n[0];
			played = n[0] + n[1];
		} else if (sport == Sport.FOOTBALL) {
			won = n[0] + 0.5 * n[1];
			played = n[0] + n[1] + n[2];
		} else if (sport == Sport.HOCKEY) {
			won = n[0] + 0.3 * n[2];
			played = n[0] + n[1] + n[2];
		} else {
			won = n[0] + 0.5 * n[2];
			played = n[0] + n[1] + n[2];
		}
		// two wins and two losses of prior keep a 3-0 start from looking unbeatable
		double p = Math.clamp((won + 2) / (played + 4), 0.05, 0.95);
		return BASE + 400 * Math.log10(p / (1 - p));
	}

	/** Prices from ratings: logistic win chance with the home edge; football takes a share for the draw first. */
	public static Odds prices(Sport sport, double home, double away, double margin) {
		double diff = home + sport.homeAdvantage - away;
		double homeWins = 1 / (1 + Math.pow(10, -diff / 400));
		if (sport.draws) {
			double draw = 0.28 - 0.10 * Math.min(1, Math.abs(diff) / 400);
			return new Odds(price((1 - draw) * homeWins, margin), price(draw, margin), price((1 - draw) * (1 - homeWins), margin), true, "model");
		}
		return new Odds(price(homeWins, margin), 0, price(1 - homeWins, margin), true, "model");
	}

	public Odds price(SportEvent event, double margin) {
		return prices(event.league().sport, rating(event.league(), event.home(), event.homeRecord()),
				rating(event.league(), event.away(), event.awayRecord()), margin);
	}

	static double price(double probability, double margin) {
		return Math.clamp(Odds.round(1 / (probability * (1 + margin))), 1.01, 51.0);
	}
}
