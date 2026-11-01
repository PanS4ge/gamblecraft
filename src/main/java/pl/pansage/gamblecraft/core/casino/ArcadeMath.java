package pl.pansage.gamblecraft.core.casino;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/** Plinko, the wheel of fortune, crash and dice. All multipliers are total return per unit staked. */
public final class ArcadeMath {
	private ArcadeMath() {
	}

	/** Whole chips paid for {@code stake} at {@code multiplier}, rounded down but immune to 2.3 * 100 = 229.999... */
	public static long payout(long stake, double multiplier) {
		return (long) Math.floor(stake * multiplier + 1e-6);
	}

	// ---------------------------------------------------------------- plinko

	public static final int PLINKO_ROWS = 12;

	public enum PlinkoRisk {
		LOW(10, 3, 1.6, 1.4, 1.1, 1, 0.4, 1, 1.1, 1.4, 1.6, 3, 10),
		MEDIUM(33, 11, 4, 2, 1.1, 0.6, 0.2, 0.6, 1.1, 2, 4, 11, 33),
		HIGH(170, 24, 8.1, 2, 0.7, 0.17, 0.17, 0.17, 0.7, 2, 8.1, 24, 170);

		public final double[] bins;

		PlinkoRisk(double... bins) {
			this.bins = bins;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Exact return: binomial chance of each bin times its multiplier. */
		public double expectedReturn() {
			double sum = 0;
			for (int k = 0; k <= PLINKO_ROWS; k++) {
				sum += binomial(PLINKO_ROWS, k) / Math.pow(2, PLINKO_ROWS) * bins[k];
			}
			return sum;
		}
	}

	/** {@code rights[i]} is true if the ball bounced right at row i; it lands in bin = number of rights. */
	public record PlinkoDrop(boolean[] rights, int bin, double multiplier) {
	}

	public static PlinkoDrop dropPlinko(PlinkoRisk risk, RandomGenerator rng) {
		boolean[] rights = new boolean[PLINKO_ROWS];
		int bin = 0;
		for (int i = 0; i < PLINKO_ROWS; i++) {
			rights[i] = rng.nextBoolean();
			if (rights[i]) {
				bin++;
			}
		}
		return new PlinkoDrop(rights, bin, risk.bins[bin]);
	}

	private static double binomial(int n, int k) {
		double r = 1;
		for (int i = 1; i <= k; i++) {
			r = r * (n - k + i) / i;
		}
		return r;
	}

	// ---------------------------------------------------------------- wheel of fortune

	/** Symbols players bet on, with segment counts out of 50 and total return when the wheel stops on them. */
	public enum WheelSymbol {
		ONE(24, 2),
		THREE(12, 4),
		SIX(7, 7),
		ELEVEN(4, 12),
		TWENTY_THREE(2, 24),
		JOKER(1, 48);

		public final int segments;
		public final int payout;

		WheelSymbol(int segments, int payout) {
			this.segments = segments;
			this.payout = payout;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public double expectedReturn() {
			return (double) segments * payout / WHEEL_SEGMENTS.length;
		}
	}

	/** Segment layout around the wheel, spreading each symbol out evenly. */
	public static final WheelSymbol[] WHEEL_SEGMENTS = buildWheel();

	private static WheelSymbol[] buildWheel() {
		int total = 0;
		for (WheelSymbol s : WheelSymbol.values()) {
			total += s.segments;
		}
		WheelSymbol[] wheel = new WheelSymbol[total];
		// Place rare symbols first at evenly spaced positions, then fill gaps in order.
		WheelSymbol[] byRarity = {WheelSymbol.JOKER, WheelSymbol.TWENTY_THREE, WheelSymbol.ELEVEN, WheelSymbol.SIX, WheelSymbol.THREE, WheelSymbol.ONE};
		for (WheelSymbol symbol : byRarity) {
			for (int i = 0; i < symbol.segments; i++) {
				int pos = (int) Math.round((i + 0.5) * total / symbol.segments + symbol.ordinal() * 3) % total;
				while (wheel[pos] != null) {
					pos = (pos + 1) % total;
				}
				wheel[pos] = symbol;
			}
		}
		return wheel;
	}

	public static int spinWheel(RandomGenerator rng) {
		return rng.nextInt(WHEEL_SEGMENTS.length);
	}

	// ---------------------------------------------------------------- crash

	public static final double CRASH_EDGE = 0.97;
	/** Multiplier growth per second: m(t) = e^(rate * t). */
	public static final double CRASH_RATE = 0.08;
	public static final double CRASH_MAX = 1000;

	/** Where the rocket explodes. Cashing out at m before that returns m; P(point >= m) = 0.97 / m. */
	public static double crashPoint(RandomGenerator rng) {
		double u = rng.nextDouble();
		double point = Math.floor(CRASH_EDGE / (1 - u) * 100) / 100;
		return Math.max(1.0, Math.min(CRASH_MAX, point));
	}

	public static double crashMultiplierAt(double seconds) {
		return Math.floor(Math.exp(CRASH_RATE * seconds) * 100) / 100;
	}

	public static double crashSecondsTo(double multiplier) {
		return Math.log(multiplier) / CRASH_RATE;
	}

	// ---------------------------------------------------------------- dice

	public enum DiceBet {
		UNDER_SEVEN(2.3),
		SEVEN(5.75),
		OVER_SEVEN(2.3),
		DOUBLES(5.75);

		public final double payout;

		DiceBet(double payout) {
			this.payout = payout;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public boolean wins(int a, int b) {
			int sum = a + b;
			return switch (this) {
				case UNDER_SEVEN -> sum < 7;
				case SEVEN -> sum == 7;
				case OVER_SEVEN -> sum > 7;
				case DOUBLES -> a == b;
			};
		}

		public double expectedReturn() {
			int hits = 0;
			for (int a = 1; a <= 6; a++) {
				for (int b = 1; b <= 6; b++) {
					if (wins(a, b)) {
						hits++;
					}
				}
			}
			return hits * payout / 36;
		}
	}

	public record DiceRoll(int a, int b) {
		public int sum() {
			return a + b;
		}
	}

	public static DiceRoll rollDice(RandomGenerator rng) {
		return new DiceRoll(rng.nextInt(1, 7), rng.nextInt(1, 7));
	}

	/** Random tumble keyframes for the client: list of (die face, dx, dz) per bounce. Cosmetic only. */
	public static List<int[]> diceBounces(RandomGenerator rng, int bounces) {
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i < bounces; i++) {
			out.add(new int[] {rng.nextInt(1, 7), rng.nextInt(-8, 9), rng.nextInt(-8, 9)});
		}
		return out;
	}
}
