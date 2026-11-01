package pl.pansage.gamblecraft.core.games;

import java.util.List;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.Money;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;

/**
 * Punto Banco. Wager option 0 = Player (1:1), 1 = Banker (1:1 minus 5% commission), 2 = Tie (8:1).
 * Player and Banker bets are returned on a tie. No decisions: the whole round is dealt by the rules.
 */
public final class BaccaratGame extends CardGame {
	public static final int PLAYER = 0;
	public static final int BANKER = 1;
	public static final int TIE = 2;

	private final Shoe shoe;
	private TableHand player;
	private TableHand banker;

	public BaccaratGame(int seats, int decks, RandomGenerator rng) {
		super(seats, rng);
		this.shoe = new Shoe(decks, rng);
	}

	@Override
	public String id() {
		return "baccarat";
	}

	public Shoe shoe() {
		return shoe;
	}

	@Override
	public boolean isValidWager(Wager wager) {
		return wager.amount() > 0 && wager.side() == 0 && wager.option() >= PLAYER && wager.option() <= TIE;
	}

	public static int score(List<Card> cards) {
		return cards.stream().mapToInt(c -> c.rank().baccaratValue()).sum() % 10;
	}

	/** Punto Banco tableau: does the banker draw, given its score and the player's third card value (-1 if the player stood)? */
	public static boolean bankerDraws(int bankerScore, int playerThird) {
		if (playerThird < 0) {
			return bankerScore <= 5;
		}
		return switch (bankerScore) {
			case 0, 1, 2 -> true;
			case 3 -> playerThird != 8;
			case 4 -> playerThird >= 2 && playerThird <= 7;
			case 5 -> playerThird >= 4 && playerThird <= 7;
			case 6 -> playerThird == 6 || playerThird == 7;
			default -> false;
		};
	}

	@Override
	protected void onStartRound() {
		if (shoe.needsShuffle()) {
			shoe.shuffle();
			log("gamblecraft.log.shuffle");
		}
		player = new TableHand("player", COMMUNITY, "gamblecraft.hand.punto", false);
		banker = new TableHand("banker", DEALER, "gamblecraft.hand.banco", false);
		hands.add(player);
		hands.add(banker);
		for (int i = 0; i < 2; i++) {
			then(() -> deal(player));
			then(() -> deal(banker));
		}
		then(this::thirdCards);
	}

	private void deal(TableHand hand) {
		hand.add(shoe.draw(), true);
		hand.value = Msg.of("gamblecraft.value.points", score(hand.cardList()));
	}

	private void thirdCards() {
		int p = score(player.cardList());
		int b = score(banker.cardList());
		if (p >= 8 || b >= 8) {
			log("gamblecraft.log.natural");
			then(this::settleAll);
			return;
		}
		if (p <= 5) {
			then(() -> deal(player));
			then(() -> {
				int third = player.cards.get(2).card().rank().baccaratValue();
				if (bankerDraws(score(banker.cardList()), third)) {
					then(() -> deal(banker));
				}
				then(this::settleAll);
			});
		} else {
			if (bankerDraws(b, -1)) {
				then(() -> deal(banker));
			}
			then(this::settleAll);
		}
	}

	private void settleAll() {
		int p = score(player.cardList());
		int b = score(banker.cardList());
		int winner = p > b ? PLAYER : b > p ? BANKER : TIE;
		(winner == PLAYER ? player : winner == BANKER ? banker : player).result = winner == TIE ? "tie" : "win";
		log("gamblecraft.log.baccarat_result", p, b);
		for (int seat : roundSeats()) {
			Wager w = seats[seat].wager;
			long a = w.amount();
			long payout;
			if (w.option() == winner) {
				payout = switch (winner) {
					case PLAYER -> a * 2;
					case BANKER -> a + Money.scale(a, 95, 100, rng);
					default -> a * 9;
				};
			} else {
				payout = winner == TIE ? a : 0;
			}
			settle(seat, payout, payout > a ? "win" : payout == a ? "push" : "lose");
		}
		finishRound();
	}

	@Override
	protected void advance() {
	}

	@Override
	protected List<ActionOption> legalActions(int seat) {
		return List.of();
	}

	@Override
	public Act defaultAct(int seat) {
		return Act.STAND;
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		throw new IllegalStateException("Baccarat has no decisions");
	}
}
