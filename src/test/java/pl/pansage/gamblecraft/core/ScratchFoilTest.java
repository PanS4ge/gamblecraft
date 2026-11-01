package pl.pansage.gamblecraft.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;

class ScratchFoilTest {
	@Test
	void zigzagOverTheFirstRowRevealsItAndNothingElse() {
		ScratchFoil foil = new ScratchFoil();
		for (int y = 2; y < ScratchFoil.CELL; y += 7) {
			foil.rubLine(0, y, ScratchFoil.SIZE, y, null);
		}
		for (int cell = 0; cell < 3; cell++) {
			assertTrue(foil.isRevealed(cell), "cell " + cell);
		}
		for (int cell = 3; cell < 9; cell++) {
			assertFalse(foil.isRevealed(cell), "cell " + cell);
		}
		assertFalse(foil.allRevealed());
	}

	@Test
	void oneStrokeDoesNotRevealACell() {
		ScratchFoil foil = new ScratchFoil();
		int[] flakes = {0};
		int removed = foil.rubLine(0, ScratchFoil.CELL / 2.0, ScratchFoil.SIZE, ScratchFoil.CELL / 2.0, (x, y) -> flakes[0]++);
		assertTrue(removed > 0);
		assertEquals(removed, flakes[0]);
		assertFalse(foil.isRevealed(0));
	}

	@Test
	void bytesRoundTrip() {
		ScratchFoil foil = new ScratchFoil();
		foil.rubLine(10, 10, 140, 120, null);
		for (int y = 2; y < ScratchFoil.CELL; y += 7) {
			foil.rubLine(0, y, ScratchFoil.SIZE, y, null);
		}
		ScratchFoil copy = ScratchFoil.fromBytes(foil.toBytes());
		assertArrayEquals(foil.toBytes(), copy.toBytes());
		for (int cell = 0; cell < 9; cell++) {
			assertEquals(foil.isRevealed(cell), copy.isRevealed(cell));
		}
		foil.revealAll(null);
		assertTrue(ScratchFoil.fromBytes(foil.toBytes()).allRevealed());
	}
}
