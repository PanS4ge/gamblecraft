package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.casino.Roulette;
import pl.pansage.gamblecraft.core.casino.RouletteLayout;

class RouletteLayoutTest {
	private static double[] centre(int n) {
		return new double[] {RouletteLayout.ZERO_END + (Roulette.column(n) - 0.5) * RouletteLayout.COLUMN_W, (Roulette.row(n) + 0.5) * RouletteLayout.ROW_H};
	}

	@Test
	void middleOfANumberIsStraightUp() {
		for (int n = 1; n <= 36; n++) {
			double[] c = centre(n);
			assertEquals(Roulette.straight(n), RouletteLayout.betAt(c[0], c[1]), "number " + n);
		}
		assertEquals(Roulette.straight(0), RouletteLayout.betAt(0.1, 0.3));
	}

	@Test
	void linesBetweenNumbersAreSplitsAndCorners() {
		double[] c17 = centre(17);
		double[] c20 = centre(20);
		assertEquals(Roulette.split(17, 20), RouletteLayout.betAt((c17[0] + c20[0]) / 2, c17[1]));
		double[] c16 = centre(16);
		assertEquals(Roulette.split(16, 17), RouletteLayout.betAt(c17[0], (c16[1] + c17[1]) / 2));
		assertEquals(Roulette.corner(Roulette.column(17), 1), RouletteLayout.betAt((c17[0] + c20[0]) / 2, (c16[1] + c17[1]) / 2));
	}

	@Test
	void outsideBets() {
		assertEquals(Roulette.columnBet(0), RouletteLayout.betAt(2.9, 0.1));
		assertEquals(Roulette.dozen(1), RouletteLayout.betAt(1.5, 0.7));
		assertEquals(Roulette.red(), RouletteLayout.betAt(RouletteLayout.ZERO_END + 4.5 * RouletteLayout.COLUMN_W, 0.9));
		assertNull(RouletteLayout.betAt(2.9, 0.9));
		assertNull(RouletteLayout.betAt(-0.1, 0.5));
	}

	@Test
	void chipsSitOnTheirBet() {
		assertArrayEquals(centre(5), RouletteLayout.anchor(Roulette.straight(5)), 1e-9);
		double[] split = RouletteLayout.anchor(Roulette.split(17, 20));
		assertEquals((centre(17)[0] + centre(20)[0]) / 2, split[0], 1e-9);
		for (RouletteLayout.Cell cell : RouletteLayout.cells()) {
			double[] a = RouletteLayout.anchor(cell.bet());
			assertEquals(cell.bet(), RouletteLayout.betAt(a[0], a[1]), cell.label());
		}
	}
}
