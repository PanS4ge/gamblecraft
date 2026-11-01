package pl.pansage.gamblecraft.core.casino;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Scratch cards: nine cells, a prize is won by three matching symbols. The outcome is drawn from the prize table
 * first, then a layout that shows exactly that outcome is generated, all from one seed, so the server can recreate
 * a card from its seed and the client never needs to know the answer before scratching.
 */
public final class ScratchCards {
	public static final int CELLS = 9;

	/** Prize symbols, worst to best, with total return per unit of the card price and chance in 1/100000. */
	public enum Prize {
		CLOVER(1, 18_000),
		HORSESHOE(2, 9_000),
		BELL(3, 4_000),
		COIN(5, 2_000),
		STAR(10, 1_000),
		CROWN(50, 100),
		DIAMOND(250, 10);

		public final int multiplier;
		public final int weight;

		Prize(int multiplier, int weight) {
			this.multiplier = multiplier;
			this.weight = weight;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final int WEIGHT_TOTAL = 100_000;

	/** {@code cells} hold Prize ordinals; {@code prize} is null for a losing card. */
	public record Card(int[] cells, Prize prize) {
		public int multiplier() {
			return prize == null ? 0 : prize.multiplier;
		}
	}

	private ScratchCards() {
	}

	public static double expectedReturn() {
		double sum = 0;
		for (Prize prize : Prize.values()) {
			sum += (double) prize.weight / WEIGHT_TOTAL * prize.multiplier;
		}
		return sum;
	}

	/** Prize for a seed, with {@code luck} in [0,1) shifting a little probability from losing to the smallest prize. */
	public static Card generate(long seed) {
		SplittableRandom rng = new SplittableRandom(seed);
		int roll = rng.nextInt(WEIGHT_TOTAL);
		Prize prize = null;
		int acc = 0;
		for (Prize p : Prize.values()) {
			acc += p.weight;
			if (roll < acc) {
				prize = p;
				break;
			}
		}
		List<Integer> cells = new ArrayList<>(CELLS);
		List<Integer> others = new ArrayList<>();
		for (Prize p : Prize.values()) {
			if (p != prize) {
				others.add(p.ordinal());
			}
		}
		Collections.shuffle(others, new java.util.Random(rng.nextLong()));
		if (prize != null) {
			for (int i = 0; i < 3; i++) {
				cells.add(prize.ordinal());
			}
		}
		// Fill with other symbols, at most two of each, so no second triple appears.
		int index = 0;
		while (cells.size() < CELLS) {
			int symbol = others.get(index % others.size());
			if (Collections.frequency(cells, symbol) < 2) {
				cells.add(symbol);
			}
			index++;
		}
		Collections.shuffle(cells, new java.util.Random(rng.nextLong()));
		return new Card(cells.stream().mapToInt(Integer::intValue).toArray(), prize);
	}
}
