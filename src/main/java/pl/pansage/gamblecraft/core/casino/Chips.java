package pl.pansage.gamblecraft.core.casino;

/**
 * Casino chips: the denominations, making change, and the cashier's maths. Buying chips is at face value; cashing
 * them out costs the house cut, so a round trip through the cashier always loses {@link #HOUSE_CUT}.
 */
public final class Chips {
	public static final int[] VALUES = {1, 5, 25, 100, 500, 1000};
	public static final double HOUSE_CUT = 0.10;

	private Chips() {
	}

	/** Fewest chips adding up to {@code amount}: a count per denomination, in the order of {@link #VALUES}. */
	public static long[] breakdown(long amount) {
		long[] counts = new long[VALUES.length];
		long left = Math.max(0, amount);
		for (int i = VALUES.length - 1; i >= 0; i--) {
			counts[i] = left / VALUES[i];
			left -= counts[i] * VALUES[i];
		}
		return counts;
	}

	public static long total(long[] counts) {
		long sum = 0;
		for (int i = 0; i < VALUES.length && i < counts.length; i++) {
			sum += counts[i] * VALUES[i];
		}
		return sum;
	}

	/** Chips the cashier takes to pay out {@code units} items worth {@code chipsPerUnit} chips each. */
	public static long cost(long units, int chipsPerUnit) {
		return (long) Math.ceil(units * chipsPerUnit / (1 - HOUSE_CUT) - 1e-9);
	}

	/** The most items worth {@code chipsPerUnit} the cashier pays for {@code chips}. */
	public static long payout(long chips, int chipsPerUnit) {
		long units = (long) Math.floor(chips * (1 - HOUSE_CUT) / chipsPerUnit + 1e-9);
		while (units > 0 && cost(units, chipsPerUnit) > chips) {
			units--;
		}
		return units;
	}
}
