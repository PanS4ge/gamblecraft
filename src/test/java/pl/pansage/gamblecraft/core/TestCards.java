package pl.pansage.gamblecraft.core;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableTypes.Settlement;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;

/** Helpers for core tests: card parsing ("As Kh 10d"), seeded RNG, seating and round driving. */
public final class TestCards {
	public static final UUID P1 = new UUID(0, 1);
	public static final UUID P2 = new UUID(0, 2);
	public static final UUID P3 = new UUID(0, 3);

	private TestCards() {
	}

	public static SplittableRandom rng(long seed) {
		return new SplittableRandom(seed);
	}

	/** Parses cards like {@code "As Kh 10d 2c"} (rank then suit letter). */
	public static List<Card> cards(String text) {
		return Arrays.stream(text.trim().split("\\s+")).map(TestCards::card).toList();
	}

	public static Card card(String token) {
		String rank = token.substring(0, token.length() - 1).toUpperCase();
		char suit = Character.toLowerCase(token.charAt(token.length() - 1));
		String suitCode = switch (suit) {
			case 'h' -> "hearts";
			case 'd' -> "diamonds";
			case 'c' -> "clubs";
			case 's' -> "spades";
			default -> throw new IllegalArgumentException(token);
		};
		String rankCode = switch (rank) {
			case "A" -> "ace";
			case "K" -> "king";
			case "Q" -> "queen";
			case "J" -> "jack";
			default -> rank;
		};
		return Card.fromCode(suitCode + "_" + rankCode);
	}

	public static int seat(CardGame game, UUID player, long bet) {
		int seat = game.sit(player, player.toString().substring(30));
		game.placeWager(seat, Wager.of(bet));
		return seat;
	}

	/** Runs automatic steps until a decision or the end of the round. */
	public static void run(CardGame game) {
		game.runToInput();
	}

	public static long payoutOf(List<Settlement> settlements, int seat) {
		return settlements.stream().filter(s -> s.seat() == seat).mapToLong(Settlement::payout).sum();
	}
}
