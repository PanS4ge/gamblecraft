package pl.pansage.gamblecraft.core.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.cards.Shoe;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.Money;
import pl.pansage.gamblecraft.core.table.TableHand;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;

/**
 * Higher or Lower: guess whether the next card is strictly higher or lower (aces high; equal loses).
 * Each right guess multiplies the stake by {@code EDGE / p}, where p is the exact chance from the cards left,
 * so every guess returns 97% on average. Cash out any time after the first right guess.
 */
public final class HiLoGame extends CardGame {
	public static final double EDGE = 0.97;
	public static final int MAX_STEPS = 12;

	private final Shoe shoe;
	private TableHand row;
	private Card current;
	private double multiplier;
	private int steps;

	public HiLoGame(RandomGenerator rng) {
		super(1, rng);
		this.shoe = new Shoe(1, rng);
	}

	@Override
	public String id() {
		return "hi_lo";
	}

	public double multiplier() {
		return multiplier;
	}

	public double chanceHigher() {
		int v = current.rank().pokerValue();
		return (double) shoe.count(c -> c.rank().pokerValue() > v) / shoe.remaining();
	}

	public double chanceLower() {
		int v = current.rank().pokerValue();
		return (double) shoe.count(c -> c.rank().pokerValue() < v) / shoe.remaining();
	}

	@Override
	protected void onStartRound() {
		shoe.shuffle();
		multiplier = 1.0;
		steps = 0;
		row = new TableHand("hilo", 0, "gamblecraft.hand.hilo", false);
		hands.add(row);
		then(() -> {
			current = shoe.draw();
			row.add(current, true);
			updateValue();
			phase = Phase.PLAYER_TURN;
			activeSeat = 0;
			row.active = true;
		});
	}

	private void updateValue() {
		double hi = chanceHigher();
		double lo = chanceLower();
		row.value = Msg.of("gamblecraft.value.hilo", fmt(multiplier),
				hi > 0 ? fmt(multiplier * EDGE / hi) : "-", lo > 0 ? fmt(multiplier * EDGE / lo) : "-");
	}

	private static String fmt(double x) {
		return String.format(Locale.ROOT, "%.2f", x);
	}

	@Override
	protected void advance() {
	}

	@Override
	protected List<ActionOption> legalActions(int seat) {
		List<ActionOption> options = new ArrayList<>();
		if (chanceHigher() > 0) {
			options.add(ActionOption.free(Act.HIGHER));
		}
		if (chanceLower() > 0) {
			options.add(ActionOption.free(Act.LOWER));
		}
		if (steps > 0) {
			options.add(ActionOption.free(Act.CASH_OUT));
		}
		return options;
	}

	@Override
	public Act defaultAct(int seat) {
		if (steps > 0) {
			return Act.CASH_OUT;
		}
		return chanceHigher() >= chanceLower() ? Act.HIGHER : Act.LOWER;
	}

	@Override
	protected void onAct(int seat, Act act, long cost, long amount) {
		if (act == Act.CASH_OUT) {
			cashOut();
			return;
		}
		double p = act == Act.HIGHER ? chanceHigher() : chanceLower();
		int before = current.rank().pokerValue();
		then(() -> {
			current = shoe.draw();
			row.add(current, true);
			int after = current.rank().pokerValue();
			boolean right = act == Act.HIGHER ? after > before : after < before;
			if (!right) {
				row.result = "lose";
				row.active = false;
				settle(0, 0, "lose");
				finishRound();
				return;
			}
			multiplier *= EDGE / p;
			steps++;
			if (steps >= MAX_STEPS || shoe.remaining() == 0) {
				cashOut();
			} else {
				updateValue();
			}
		});
	}

	private void cashOut() {
		long stake = seats[0].committed;
		long payout = Money.scale(stake, multiplier, rng);
		row.result = "cash_out";
		row.active = false;
		log("gamblecraft.log.hilo_cash_out", fmt(multiplier));
		settle(0, payout, payout > stake ? "win" : payout == stake ? "push" : "lose");
		finishRound();
	}
}
