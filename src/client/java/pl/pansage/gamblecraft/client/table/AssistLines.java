package pl.pansage.gamblecraft.client.table;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.games.BasicStrategy;
import pl.pansage.gamblecraft.core.games.HoldemBot;
import pl.pansage.gamblecraft.core.games.OczkoGame;
import pl.pansage.gamblecraft.core.poker.HandValue;
import pl.pansage.gamblecraft.core.poker.PokerAdvisor;
import pl.pansage.gamblecraft.core.poker.ThreeCardEvaluator;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.SeatView;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;
import pl.pansage.gamblecraft.network.TablePayloads.TableState;

/**
 * The help panel at a table. "Values" lists every visible card and what your hand is;
 * "full" adds a suggested move and, in Hold'em, your chance to win.
 */
public final class AssistLines {
	private static String equityKey;
	private static int equityPercent;

	private AssistLines() {
	}

	public static List<Component> build(TableState state, int level) {
		List<Component> lines = new ArrayList<>();
		if (level <= ClientConfig.ASSIST_OFF) {
			return lines;
		}
		TableSnapshot snap = state.snapshot();
		SeatView mine = ClientTables.mySeat(state);
		int mySeat = mine == null ? -99 : mine.index();

		lines.add(Component.translatable("gamblecraft.assist.on_table").withStyle(ChatFormatting.GOLD));
		for (HandView hand : snap.hands()) {
			if (hand.cards().isEmpty()) {
				continue;
			}
			MutableComponent line = Component.literal(ownerName(snap, hand, mySeat) + ": ").withStyle(ChatFormatting.GRAY);
			for (int id : hand.cards()) {
				line.append(cardText(id)).append(" ");
			}
			lines.add(line);
		}

		HandView own = snap.hands().stream().filter(h -> h.owner() == mySeat).findFirst().orElse(null);
		List<Card> mineCards = own == null ? List.of() : known(own.cards());
		List<Card> board = snap.hands().stream().filter(h -> h.owner() == CardGame.COMMUNITY).findFirst()
				.map(h -> known(h.cards())).orElse(List.of());
		Component handLine = describeHand(snap.game(), own, mineCards, board);
		if (handLine != null) {
			lines.add(Component.translatable("gamblecraft.assist.your_hand", handLine).withStyle(ChatFormatting.WHITE));
		}
		if (level >= ClientConfig.ASSIST_FULL) {
			lines.addAll(tips(snap, own, mineCards, board, mySeat));
		}
		return lines;
	}

	private static String ownerName(TableSnapshot snap, HandView hand, int mySeat) {
		if (hand.owner() == mySeat) {
			return Component.translatable("gamblecraft.assist.you").getString();
		}
		if (hand.owner() == CardGame.DEALER || hand.owner() == CardGame.COMMUNITY) {
			return Component.translatable(hand.label()).getString();
		}
		for (SeatView seat : snap.seats()) {
			if (seat.index() == hand.owner()) {
				return seat.name();
			}
		}
		return "?";
	}

	public static Component cardText(int id) {
		if (id < 0) {
			return Component.literal("??").withStyle(ChatFormatting.DARK_GRAY);
		}
		Card card = Card.of(id);
		return Component.literal(card.shortText()).withStyle(card.suit().isRed() ? ChatFormatting.RED : ChatFormatting.WHITE);
	}

	private static List<Card> known(List<Integer> ids) {
		List<Card> cards = new ArrayList<>();
		for (int id : ids) {
			if (id >= 0) {
				cards.add(Card.of(id));
			}
		}
		return cards;
	}

	private static Component describeHand(String game, HandView own, List<Card> cards, List<Card> board) {
		if (own == null || cards.isEmpty()) {
			return null;
		}
		switch (game) {
			case "holdem" -> {
				List<Card> all = new ArrayList<>(cards);
				all.addAll(board);
				HandValue best = PokerAdvisor.best(all);
				if (best != null) {
					return pokerName(best);
				}
				if (cards.size() == 2) {
					boolean pair = cards.get(0).rank() == cards.get(1).rank();
					boolean suited = cards.get(0).suit() == cards.get(1).suit();
					return Component.translatable(pair ? "gamblecraft.assist.pocket_pair" : suited ? "gamblecraft.assist.suited" : "gamblecraft.assist.offsuit");
				}
				return null;
			}
			case "video_poker" -> {
				return cards.size() == 5 ? pokerName(PokerAdvisor.best(cards)) : null;
			}
			case "three_card_poker" -> {
				return cards.size() == 3 ? Component.translatable("gamblecraft.poker3." + ThreeCardEvaluator.evaluate(cards).category().key()) : null;
			}
			default -> {
				return own.value() == null ? null : Component.translatable(own.value().key(), own.value().args().toArray());
			}
		}
	}

	private static Component pokerName(HandValue value) {
		MutableComponent name = Component.translatable("gamblecraft.poker." + value.category().key());
		int top = value.tiebreak().getFirst();
		String rank = switch (top) {
			case 14 -> "A";
			case 13 -> "K";
			case 12 -> "Q";
			case 11 -> "J";
			default -> String.valueOf(top);
		};
		return name.append(" (" + rank + ")");
	}

	private static List<Component> tips(TableSnapshot snap, HandView own, List<Card> cards, List<Card> board, int mySeat) {
		List<Component> tips = new ArrayList<>();
		boolean myTurn = snap.activeSeat() == mySeat && !snap.actions().isEmpty();
		Set<Act> legal = EnumSet.noneOf(Act.class);
		for (ActionOption option : snap.actions()) {
			legal.add(option.act());
		}
		switch (snap.game()) {
			case "blackjack" -> {
				HandView active = snap.hands().stream().filter(h -> h.owner() == mySeat && h.active()).findFirst().orElse(own);
				HandView dealer = snap.hands().stream().filter(h -> h.owner() == CardGame.DEALER).findFirst().orElse(null);
				if (myTurn && active != null && dealer != null && !dealer.cards().isEmpty() && dealer.cards().getFirst() >= 0) {
					Act act = BasicStrategy.decide(known(active.cards()), Card.of(dealer.cards().getFirst()), legal);
					tips.add(tip(Component.translatable("gamblecraft.act." + act.key())));
				}
			}
			case "oczko" -> {
				if (myTurn && !cards.isEmpty()) {
					tips.add(tip(Component.translatable(OczkoGame.total(cards) < 15 ? "gamblecraft.act.hit" : "gamblecraft.act.stand")));
				}
			}
			case "holdem" -> {
				if (cards.size() == 2) {
					int opponents = (int) snap.hands().stream().filter(h -> h.owner() >= 0 && h.owner() != mySeat && !"fold".equals(h.result())).count();
					tips.add(Component.translatable("gamblecraft.assist.win_chance", equity(cards, board, Math.max(1, opponents)))
							.withStyle(ChatFormatting.AQUA));
					List<Card> all = new ArrayList<>(cards);
					all.addAll(board);
					for (PokerAdvisor.Draw draw : PokerAdvisor.draws(all)) {
						tips.add(Component.translatable("gamblecraft.assist.draw." + draw.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.AQUA));
					}
				}
			}
			case "video_poker" -> {
				if (myTurn && cards.size() == 5) {
					int mask = PokerAdvisor.suggestHolds(cards);
					if (mask == 0) {
						tips.add(tip(Component.translatable("gamblecraft.assist.draw_all")));
					} else {
						StringBuilder which = new StringBuilder();
						for (int i = 0; i < 5; i++) {
							if ((mask & (1 << i)) != 0) {
								which.append(which.isEmpty() ? "" : ", ").append(i + 1);
							}
						}
						tips.add(tip(Component.translatable("gamblecraft.assist.hold", which.toString())));
					}
				}
			}
			case "three_card_poker" -> {
				if (myTurn && cards.size() == 3) {
					ThreeCardEvaluator.Value value = ThreeCardEvaluator.evaluate(cards);
					List<Integer> t = value.tiebreak();
					boolean play = value.category() != ThreeCardEvaluator.Category.HIGH_CARD || t.get(0) >= 13
							|| t.get(0) == 12 && (t.get(1) > 6 || t.get(1) == 6 && t.get(2) >= 4);
					tips.add(tip(Component.translatable(play ? "gamblecraft.act.play" : "gamblecraft.act.fold")));
				}
			}
			case "war" -> {
				if (myTurn) {
					tips.add(tip(Component.translatable("gamblecraft.act.war")));
				}
			}
			case "hi_lo" -> {
				if (myTurn && !cards.isEmpty()) {
					int v = cards.getLast().rank().pokerValue();
					tips.add(Component.translatable("gamblecraft.assist.hilo_odds", (14 - v) * 100 / 13, (v - 2) * 100 / 13).withStyle(ChatFormatting.AQUA));
				}
			}
			case "baccarat" -> tips.add(Component.translatable("gamblecraft.assist.baccarat").withStyle(ChatFormatting.AQUA));
			default -> {
			}
		}
		return tips;
	}

	private static Component tip(Component move) {
		return Component.translatable("gamblecraft.assist.tip", move.copy().withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.AQUA);
	}

	/** Monte Carlo win chance, recomputed only when the cards or the number of opponents change. */
	private static int equity(List<Card> hole, List<Card> board, int opponents) {
		String key = hole + "|" + board + "|" + opponents;
		if (!key.equals(equityKey)) {
			equityKey = key;
			double sum = 0;
			SplittableRandom rng = new SplittableRandom(key.hashCode());
			for (int i = 0; i < 4; i++) {
				sum += HoldemBot.equity(hole, board, opponents, rng);
			}
			equityPercent = (int) Math.round(sum / 4 * 100);
		}
		return equityPercent;
	}
}
