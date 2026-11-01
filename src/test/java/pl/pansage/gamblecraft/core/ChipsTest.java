package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.casino.Chips;

class ChipsTest {
	@Test
	void breakdownUsesFewestChips() {
		assertArrayEquals(new long[] {2, 1, 1, 2, 1, 1}, Chips.breakdown(1732));
		assertArrayEquals(new long[] {0, 0, 0, 0, 0, 0}, Chips.breakdown(0));
		for (long amount = 0; amount < 3000; amount += 7) {
			assertEquals(amount, Chips.total(Chips.breakdown(amount)));
		}
	}

	@Test
	void cashingOutCostsTheHouseCut() {
		// an emerald is worth 10 chips; paying it out costs 10 / 0.9 = 11.1, so 12 chips
		assertEquals(12, Chips.cost(1, 10));
		assertEquals(9, Chips.payout(100, 10));
		assertEquals(0, Chips.payout(11, 10));
		assertEquals(1, Chips.payout(12, 10));
		for (long chips = 0; chips < 5000; chips += 13) {
			long units = Chips.payout(chips, 40);
			assertTrue(Chips.cost(units, 40) <= chips, "affordable at " + chips);
			assertTrue(Chips.cost(units + 1, 40) > chips, "maximal at " + chips);
		}
	}
}
