package pl.pansage.gamblecraft.sportsbook.feed;

/**
 * Decimal prices for the three outcomes (draw 0 where there is no draw market), whether GambleCraft's own model set
 * them, and who did ("DraftKings", "model").
 */
public record Odds(double home, double draw, double away, boolean model, String by) {
	public double price(Outcome outcome) {
		return switch (outcome) {
			case HOME -> home;
			case DRAW -> draw;
			case AWAY -> away;
		};
	}

	public boolean offers(Outcome outcome) {
		return price(outcome) > 1.0;
	}

	/** An American moneyline ("+140", "-200", "EVEN") as a decimal price, or 0 if it cannot be read. */
	public static double fromAmerican(String line) {
		if (line == null) {
			return 0;
		}
		String s = line.trim();
		if (s.equalsIgnoreCase("EVEN") || s.equalsIgnoreCase("EV")) {
			return 2.0;
		}
		try {
			return fromAmerican(Integer.parseInt(s.startsWith("+") ? s.substring(1) : s));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static double fromAmerican(int line) {
		if (line >= 100) {
			return round(1 + line / 100.0);
		}
		if (line <= -100) {
			return round(1 + 100.0 / -line);
		}
		return 0;
	}

	/** Two decimals, rounded down so the house never pays a hair more than the line. */
	public static double round(double price) {
		return Math.floor(price * 100 + 1e-6) / 100.0;
	}
}
