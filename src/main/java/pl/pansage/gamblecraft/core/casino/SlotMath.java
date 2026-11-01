package pl.pansage.gamblecraft.core.casino;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Three-reel, three-row slot with five lines (three rows and two diagonals). The stake covers all lines;
 * every line paying three of a kind returns {@code multiplier x stake}, two cherries from the left return half.
 * Three sevens on the middle line also win the progressive jackpot. Base return is about 95%.
 */
public final class SlotMath {
	public enum Symbol {
		CHERRY(9, 2),
		LEMON(8, 3.5),
		BELL(6, 7),
		APPLE(5, 12),
		BAR(4, 24),
		EMERALD(3, 45),
		DIAMOND(2, 110),
		SEVEN(1, 777),
		CREEPER(2, 25);

		/** Stops on each 40-stop reel. */
		public final int weight;
		public final double multiplier;

		Symbol(int weight, double multiplier) {
			this.weight = weight;
			this.multiplier = multiplier;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final int REEL_LENGTH = 40;
	public static final int REELS = 3;
	public static final double TWO_CHERRIES = 0.5;
	/** Rows (top, middle, bottom) used by each line, one entry per reel. */
	public static final int[][] LINES = {
			{1, 1, 1},
			{0, 0, 0},
			{2, 2, 2},
			{0, 1, 2},
			{2, 1, 0},
	};

	/** Fixed reel strips: same symbols on every reel, each in its own shuffled order. */
	private static final Symbol[][] STRIPS = new Symbol[REELS][];

	static {
		for (int reel = 0; reel < REELS; reel++) {
			List<Symbol> strip = new ArrayList<>();
			for (Symbol symbol : Symbol.values()) {
				for (int i = 0; i < symbol.weight; i++) {
					strip.add(symbol);
				}
			}
			SplittableRandom order = new SplittableRandom(0x510750L + reel);
			for (int i = strip.size() - 1; i > 0; i--) {
				int j = order.nextInt(i + 1);
				Symbol tmp = strip.get(i);
				strip.set(i, strip.get(j));
				strip.set(j, tmp);
			}
			STRIPS[reel] = strip.toArray(Symbol[]::new);
		}
	}

	public record LineWin(int line, Symbol symbol, double multiplier) {
	}

	/** {@code grid[row][reel]}; stops are the middle-row positions on each strip. */
	public record Spin(int[] stops, Symbol[][] grid, List<LineWin> wins, double multiplier, boolean jackpot) {
		public boolean nearMiss() {
			return wins.isEmpty() && grid[1][0] == grid[1][1] && grid[1][0].multiplier >= Symbol.BAR.multiplier;
		}
	}

	private SlotMath() {
	}

	public static Symbol symbolAt(int reel, int position) {
		return STRIPS[reel][Math.floorMod(position, REEL_LENGTH)];
	}

	public static Spin spin(RandomGenerator rng) {
		int[] stops = new int[REELS];
		for (int reel = 0; reel < REELS; reel++) {
			stops[reel] = rng.nextInt(REEL_LENGTH);
		}
		return evaluate(stops);
	}

	public static Spin evaluate(int[] stops) {
		Symbol[][] grid = new Symbol[3][REELS];
		for (int reel = 0; reel < REELS; reel++) {
			for (int row = 0; row < 3; row++) {
				grid[row][reel] = symbolAt(reel, stops[reel] + row - 1);
			}
		}
		List<LineWin> wins = new ArrayList<>();
		double total = 0;
		for (int line = 0; line < LINES.length; line++) {
			Symbol a = grid[LINES[line][0]][0];
			Symbol b = grid[LINES[line][1]][1];
			Symbol c = grid[LINES[line][2]][2];
			double pay = 0;
			if (a == b && b == c) {
				pay = a.multiplier;
			} else if (a == Symbol.CHERRY && b == Symbol.CHERRY) {
				pay = TWO_CHERRIES;
			}
			if (pay > 0) {
				wins.add(new LineWin(line, a, pay));
				total += pay;
			}
		}
		boolean jackpot = grid[1][0] == Symbol.SEVEN && grid[1][1] == Symbol.SEVEN && grid[1][2] == Symbol.SEVEN;
		return new Spin(stops, grid, List.copyOf(wins), total, jackpot);
	}

	/** Exact average return per unit staked, by enumerating every stop combination. */
	public static double expectedReturn() {
		double sum = 0;
		int[] stops = new int[REELS];
		for (stops[0] = 0; stops[0] < REEL_LENGTH; stops[0]++) {
			for (stops[1] = 0; stops[1] < REEL_LENGTH; stops[1]++) {
				for (stops[2] = 0; stops[2] < REEL_LENGTH; stops[2]++) {
					sum += evaluate(stops).multiplier();
				}
			}
		}
		return sum / Math.pow(REEL_LENGTH, REELS);
	}
}
