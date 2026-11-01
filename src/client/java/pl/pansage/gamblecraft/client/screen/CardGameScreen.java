package pl.pansage.gamblecraft.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.client.render.CardTableRenderer;
import pl.pansage.gamblecraft.client.table.AssistLines;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.client.table.ClientTables;
import pl.pansage.gamblecraft.client.table.ClientTables.CardAnim;
import pl.pansage.gamblecraft.client.table.TableCamera;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.SeatView;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.network.TablePayloads.TableAction;
import pl.pansage.gamblecraft.network.TablePayloads.TableAction.Op;
import pl.pansage.gamblecraft.network.TablePayloads.TableState;

/**
 * The controls at a table. Doesn't pause. By default the cards are only on the table in the world and the camera
 * looks down at them from above the player's seat; optionally the cards are also shown in a panel on screen.
 */
public class CardGameScreen extends Screen {
	private static final int FELT = 0xD80B3D24;
	private static final int FELT_EDGE = 0xFFC9A227;
	private static final int BIG_W = 34;
	private static final int BIG_H = 48;
	private static final int SMALL_W = 22;
	private static final int SMALL_H = 31;

	private final int tableId;
	private int cardW = BIG_W;
	private int cardH = BIG_H;
	private TableState shown;
	private long amount = 1;
	private int option;
	private boolean sideBet;
	private long raise;
	private final List<CardHit> cardHits = new ArrayList<>();
	private final List<float[]> confetti = new ArrayList<>();
	private long confettiFor = Long.MIN_VALUE;

	private record CardHit(int x, int y, int w, int h, int index) {
	}

	public CardGameScreen(int tableId) {
		super(Component.translatable("gamblecraft.table.title"));
		this.tableId = tableId;
	}

	public int tableId() {
		return tableId;
	}

	private ClientTables.View view() {
		return ClientTables.get(tableId);
	}

	private static boolean panel() {
		return ClientConfig.get().cardPanel;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		ClientTables.View view = view();
		if (view == null || this.minecraft.level == null || this.minecraft.level.getEntity(tableId) == null) {
			onClose();
			return;
		}
		if (view.state != shown) {
			shown = view.state;
			rebuildWidgets();
		}
		confetti.removeIf(p -> p[1] > this.height + 10);
		for (float[] p : confetti) {
			p[0] += p[2];
			p[1] += p[3];
			p[3] += 0.15F;
		}
	}

	private void updateCamera() {
		if (!panel() && ClientConfig.get().overheadCamera && this.minecraft.level.getEntity(tableId) instanceof CardTableEntity table) {
			TableCamera.activate(table);
		} else {
			TableCamera.release();
		}
	}

	@Override
	public void removed() {
		super.removed();
		TableCamera.release();
	}

	private void send(Op op, Act act, long amount, int option, long side) {
		ClientPlayNetworking.send(new TableAction(tableId, op, act == null ? -1 : act.ordinal(), amount, option, side));
	}

	private Item currencyItem() {
		if (shown == null) {
			return Items.EMERALD;
		}
		ItemStack stack = ClientCurrency.stack(shown.currency(), shown.minBet() * 5);
		return stack.isEmpty() ? Items.EMERALD : stack.getItem();
	}

	private long balance() {
		return shown == null ? 0 : ClientCurrency.balance(this.minecraft.player, shown.currency());
	}

	// ---------------------------------------------------------------- widgets

	@Override
	protected void init() {
		updateCamera();
		ClientConfig config = ClientConfig.get();
		addRenderableWidget(Button.builder(Component.translatable(config.cardPanel ? "gamblecraft.view.panel" : "gamblecraft.view.table"), b -> {
			config.cardPanel = !config.cardPanel;
			config.save();
			rebuildWidgets();
		}).bounds(this.width - 112, 4, 108, 18).build());
		Button camera = Button.builder(Component.translatable(config.overheadCamera ? "gamblecraft.view.camera_on" : "gamblecraft.view.camera_off"), b -> {
			config.overheadCamera = !config.overheadCamera;
			config.save();
			rebuildWidgets();
		}).bounds(this.width - 112, 24, 108, 18).build();
		camera.active = !config.cardPanel;
		addRenderableWidget(camera);
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.view.assist_" + config.assistLevel), b -> {
			config.assistLevel = (config.assistLevel + 2) % 3;
			config.save();
			rebuildWidgets();
		}).bounds(this.width - 112, 44, 108, 18).build());

		shown = view() == null ? null : view().state;
		if (shown == null) {
			return;
		}
		TableSnapshot snap = shown.snapshot();
		SeatView mine = ClientTables.mySeat(shown);
		boolean holdem = snap.game().equals("holdem");
		amount = Mth.clamp(amount, shown.minBet(), Math.max(shown.minBet(), shown.maxBet()));
		List<Button> row = new ArrayList<>();
		List<Button> upper = new ArrayList<>();

		if (mine == null) {
			row.add(button("gamblecraft.table.sit", b -> send(Op.SIT, null, 0, 0, 0)));
		} else if (!snap.actions().isEmpty()) {
			for (ActionOption action : snap.actions()) {
				if (action.act() == Act.HOLD) {
					if (!panel()) {
						HandView own = snap.hands().stream().filter(h -> h.owner() == mine.index()).findFirst().orElse(null);
						for (int i = 0; i < 5; i++) {
							int index = i;
							boolean held = own != null && (own.marks() & (1 << i)) != 0;
							Component label = Component.translatable(held ? "gamblecraft.table.held_card" : "gamblecraft.table.hold_card", i + 1);
							upper.add(Button.builder(label, b -> send(Op.ACT, Act.HOLD, index, 0, 0)).width(56).build());
						}
					}
					continue;
				}
				if (action.act() == Act.RAISE) {
					raise = Mth.clamp(raise, action.minCost(), action.maxCost());
					row.add(small("-", b -> {
						raise = Math.max(action.minCost(), raise - Math.max(1, raise / 4));
						rebuildWidgets();
					}));
					row.add(Button.builder(Component.translatable("gamblecraft.act.raise", raise), b -> send(Op.ACT, Act.RAISE, raise, 0, 0))
							.width(70).build());
					row.add(small("+", b -> {
						raise = Math.min(action.maxCost(), raise + Math.max(1, raise / 4));
						rebuildWidgets();
					}));
					continue;
				}
				Component label = action.minCost() > 0
						? Component.translatable("gamblecraft.act.costing", Component.translatable("gamblecraft.act." + action.act().key()), action.minCost())
						: Component.translatable("gamblecraft.act." + action.act().key());
				row.add(Button.builder(label, b -> send(Op.ACT, action.act(), action.minCost(), 0, 0)).width(Math.max(50, this.font.width(label) + 12)).build());
			}
		} else if (snap.phase() == Phase.BETTING || snap.phase() == Phase.SETTLED) {
			row.add(small("-", b -> {
				amount = Math.max(shown.minBet(), amount - 1);
				rebuildWidgets();
			}));
			row.add(small("+", b -> {
				amount = Math.min(Math.max(shown.minBet(), shown.maxBet()), amount + 1);
				rebuildWidgets();
			}));
			row.add(small("x2", b -> {
				amount = Math.min(Math.max(shown.minBet(), shown.maxBet()), amount * 2);
				rebuildWidgets();
			}));
			row.add(small("Max", b -> {
				amount = Math.max(shown.minBet(), Math.min(shown.maxBet(), balance()));
				rebuildWidgets();
			}));
			if (snap.game().equals("baccarat")) {
				for (int i = 0; i < 3; i++) {
					int opt = i;
					Component label = Component.translatable("gamblecraft.baccarat.option." + i);
					upper.add(Button.builder(opt == option ? label.copy().withStyle(net.minecraft.ChatFormatting.GOLD) : label, b -> {
						option = opt;
						rebuildWidgets();
					}).width(56).build());
				}
			}
			if (snap.game().equals("three_card_poker")) {
				upper.add(Button.builder(Component.translatable(sideBet ? "gamblecraft.tcp.pair_plus_on" : "gamblecraft.tcp.pair_plus_off"), b -> {
					sideBet = !sideBet;
					rebuildWidgets();
				}).width(84).build());
			}
			if (holdem) {
				row.add(Button.builder(Component.translatable("gamblecraft.table.buy_in", amount), b -> send(Op.BUY_IN, null, amount, 0, 0))
						.width(80).build());
			} else {
				row.add(Button.builder(Component.translatable("gamblecraft.table.place_bet", amount), b -> send(Op.BET, null, amount, option, sideBet ? amount : 0))
						.width(80).build());
				if (mine.bet() > 0 && mine.status() == null) {
					row.add(button("gamblecraft.table.clear_bet", b -> send(Op.CLEAR_BET, null, 0, 0, 0)));
				}
			}
		}
		if (mine != null) {
			row.add(button("gamblecraft.table.leave", b -> {
				send(Op.LEAVE, null, 0, 0, 0);
				onClose();
			}));
		}
		layoutRow(row, this.height - 26);
		layoutRow(upper, this.height - 48);
	}

	private void layoutRow(List<Button> row, int y) {
		int total = row.stream().mapToInt(Button::getWidth).sum() + Math.max(0, row.size() - 1) * 4;
		int x = (this.width - total) / 2;
		for (Button button : row) {
			button.setPosition(x, y);
			addRenderableWidget(button);
			x += button.getWidth() + 4;
		}
	}

	private Button button(String key, Button.OnPress press) {
		Component label = Component.translatable(key);
		return Button.builder(label, press).width(Math.max(44, this.font.width(label) + 12)).build();
	}

	private Button small(String text, Button.OnPress press) {
		return Button.builder(Component.literal(text), press).width(text.length() > 2 ? 30 : 20).build();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (panel() && shown != null && shown.snapshot().actions().stream().anyMatch(a -> a.act() == Act.HOLD)) {
			for (CardHit hit : cardHits) {
				if (event.x() >= hit.x && event.x() < hit.x + hit.w && event.y() >= hit.y && event.y() < hit.y + hit.h) {
					send(Op.ACT, Act.HOLD, hit.index, 0, 0);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, this.height - 60, this.width, this.height, 0x00000000, 0xA0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		ClientTables.View view = view();
		if (view == null || view.state == null) {
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			return;
		}
		TableState state = view.state;
		TableSnapshot snap = state.snapshot();
		cardHits.clear();
		int infoY;
		List<Component> assist = AssistLines.build(state, ClientConfig.get().assistLevel);
		if (panel()) {
			infoY = drawPanel(graphics, view, state, partialTick);
			// Bottom-left corner of the felt, where no hand is drawn.
			drawAssist(graphics, assist, infoY - 10 - assist.size() * 10);
		} else {
			drawAssist(graphics, assist, drawHeader(graphics, state) + 4);
			infoY = this.height - 62;
		}
		SeatView mine = ClientTables.mySeat(state);
		if (mine != null) {
			Component info = snap.game().equals("holdem")
					? Component.translatable("gamblecraft.table.stack_info", mine.stack(), balance())
					: Component.translatable("gamblecraft.table.bet_info", mine.bet(), balance(), shown.minBet(), shown.maxBet());
			graphics.item(new ItemStack(currencyItem()), (this.width - this.font.width(info)) / 2 - 18, infoY - 4);
			graphics.centeredText(this.font, info, this.width / 2, infoY, 0xFFFFFFFF);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawResult(graphics, view, partialTick);
	}

	/** The help panel on the left: visible cards, your hand and (full help) a suggested move. */
	private void drawAssist(GuiGraphicsExtractor graphics, List<Component> lines, int top) {
		if (lines.isEmpty()) {
			return;
		}
		int w = 8;
		for (Component line : lines) {
			w = Math.max(w, this.font.width(line) + 8);
		}
		w = Math.min(w, 200);
		graphics.fill(4, top, 4 + w, top + 4 + lines.size() * 10, 0xB0000000);
		int y = top + 3;
		for (Component line : lines) {
			graphics.text(this.font, line, 8, y, 0xFFFFFFFF, true);
			y += 10;
		}
	}

	/** Compact header for table view: game, phase, pot, timer and the last few log lines. Returns its bottom y. */
	private int drawHeader(GuiGraphicsExtractor graphics, TableState state) {
		TableSnapshot snap = state.snapshot();
		int w = 170;
		int h = 30 + Math.min(snap.log().size(), 4) * 10;
		graphics.fill(4, 4, 4 + w, 4 + h, 0xB0000000);
		graphics.fill(4, 4, 4 + w, 5, FELT_EDGE);
		graphics.text(this.font, Component.translatable("gamblecraft.game." + snap.game()), 8, 8, 0xFFFFE08A, true);
		Component phase = Component.translatable("gamblecraft.phase." + snap.phase().name().toLowerCase(Locale.ROOT));
		graphics.text(this.font, phase, 4 + w - this.font.width(phase) - 4, 8, 0xFFE0E0E0, true);
		if (snap.pot() > 0) {
			graphics.text(this.font, Component.translatable("gamblecraft.table.pot", snap.pot()), 8, 18, 0xFFFFD700, true);
		}
		timer(graphics, state, 8, 28, w - 8);
		int y = 32;
		List<Msg> log = snap.log();
		for (Msg msg : log.subList(Math.max(0, log.size() - 4), log.size())) {
			graphics.text(this.font, Component.translatable(msg.key(), msg.args().toArray()), 8, y, 0xFFBBBBBB, true);
			y += 10;
		}
		return 4 + h;
	}

	private static void timer(GuiGraphicsExtractor graphics, TableState state, int x, int y, int width) {
		if (state.ticksLeft() > 0) {
			float frac = Mth.clamp(state.ticksLeft() / (float) (state.snapshot().phase() == Phase.PLAYER_TURN ? 600 : 100), 0, 1);
			graphics.fill(x, y, x + (int) (width * frac), y + 2, frac > 0.3F ? 0xFF55FF55 : 0xFFFF5555);
		}
	}

	/** Cards drawn on a felt panel; returns the y for the info line under it. */
	private int drawPanel(GuiGraphicsExtractor graphics, ClientTables.View view, TableState state, float partialTick) {
		TableSnapshot snap = state.snapshot();
		int panelW = Math.min(this.width - 20, 400);
		int panelH = Math.min(this.height - 50, 250);
		int left = (this.width - panelW) / 2;
		int top = 8;
		graphics.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, FELT_EDGE);
		graphics.fill(left, top, left + panelW, top + panelH, FELT);
		graphics.text(this.font, Component.translatable("gamblecraft.game." + snap.game()), left + 6, top + 5, 0xFFFFE08A, true);
		Component phase = Component.translatable("gamblecraft.phase." + snap.phase().name().toLowerCase(Locale.ROOT));
		graphics.text(this.font, phase, left + panelW - this.font.width(phase) - 6, top + 5, 0xFFE0E0E0, true);
		if (snap.pot() > 0) {
			graphics.centeredText(this.font, Component.translatable("gamblecraft.table.pot", snap.pot()), left + panelW / 2, top + 5, 0xFFFFD700);
		}
		timer(graphics, state, left + 6, top + 16, panelW - 12);

		float now = ClientTables.now() + partialTick;
		List<HandView> dealerHands = snap.hands().stream().filter(h -> h.owner() == CardGame.DEALER).toList();
		List<HandView> community = snap.hands().stream().filter(h -> h.owner() == CardGame.COMMUNITY).toList();
		List<HandView> seatHands = snap.hands().stream().filter(h -> h.owner() >= 0).toList();
		SeatView mine = ClientTables.mySeat(state);
		int mySeat = mine == null ? -99 : mine.index();
		List<HandView> own = seatHands.stream().filter(h -> h.owner() == mySeat).toList();
		List<HandView> others = seatHands.stream().filter(h -> h.owner() != mySeat).toList();
		int rows = (dealerHands.isEmpty() ? 0 : 1) + (community.isEmpty() ? 0 : 1) + (seatHands.isEmpty() ? 0 : 1);
		int rowH = (panelH - 24) / Math.max(1, rows);
		cardH = Mth.clamp(rowH - 34, 22, BIG_H);
		cardW = cardH * 88 / 124;
		int rowY = top + 22;
		if (!dealerHands.isEmpty()) {
			drawRow(graphics, view, snap, dealerHands, left, rowY, panelW, true, mySeat, now);
			rowY += rowH;
		}
		if (!community.isEmpty()) {
			drawRow(graphics, view, snap, community, left, rowY, panelW, true, mySeat, now);
			rowY += rowH;
		}
		if (!own.isEmpty()) {
			drawRow(graphics, view, snap, own, left, rowY, panelW / 2 + (others.isEmpty() ? panelW / 2 : 0), true, mySeat, now);
		}
		if (!others.isEmpty()) {
			int x = own.isEmpty() ? left : left + panelW / 2;
			drawRow(graphics, view, snap, others, x, rowY + (own.isEmpty() ? 0 : 8), own.isEmpty() ? panelW : panelW / 2, own.isEmpty(), mySeat, now);
		}
		int logY = top + 24;
		for (Msg msg : snap.log()) {
			Component line = Component.translatable(msg.key(), msg.args().toArray());
			graphics.text(this.font, line, Math.min(left + panelW + 6, this.width - 110), logY, 0xFFBBBBBB, true);
			logY += 10;
		}
		return top + panelH + 6;
	}

	private void drawRow(GuiGraphicsExtractor graphics, ClientTables.View view, TableSnapshot snap, List<HandView> hands, int left, int y, int width,
			boolean big, int mySeat, float now) {
		if (hands.isEmpty()) {
			return;
		}
		int slot = width / hands.size();
		for (int h = 0; h < hands.size(); h++) {
			HandView hand = hands.get(h);
			int cx = left + slot * h + slot / 2;
			int w = big ? cardW : Math.min(SMALL_W, cardW);
			int ch = big ? cardH : Math.min(SMALL_H, cardH);
			int labelColor = hand.active() ? 0xFFFFFF55 : 0xFFDDDDDD;
			graphics.centeredText(this.font, Component.literal(labelFor(snap, hand)), cx, y, labelColor);
			List<CardAnim> anims = view.cards.getOrDefault(hand.id(), List.of());
			int n = Math.max(1, anims.size());
			int step = Math.min(w + 3, Math.max(8, (slot - 10 - w) / Math.max(1, n - 1)));
			int rowW = w + step * (n - 1);
			int x0 = cx - rowW / 2;
			for (int i = 0; i < anims.size(); i++) {
				CardAnim anim = anims.get(i);
				float dealT = Mth.clamp((now - anim.dealtAt) / ClientTables.DEAL_TICKS, 0, 1);
				float e = 1 - (1 - dealT) * (1 - dealT);
				int tx = x0 + step * i;
				int ty = y + 11;
				int x = (int) Mth.lerp(e, this.width / 2.0F, tx);
				int yy = (int) Mth.lerp(e, -ch, ty);
				boolean held = (hand.marks() & (1 << i)) != 0;
				if (held && hand.owner() == mySeat) {
					yy -= 4;
				}
				float flip = 1;
				int card = anim.card;
				if (anim.flippedAt != Long.MIN_VALUE) {
					float flipT = Mth.clamp((now - anim.flippedAt) / ClientTables.FLIP_TICKS, 0, 1);
					flip = Math.abs(Mth.cos(flipT * Mth.PI));
					if (flipT < 0.5F) {
						card = -1;
					}
				}
				drawCard(graphics, card, x, yy, w, ch, flip);
				if (held) {
					graphics.centeredText(this.font, Component.translatable("gamblecraft.table.held"), x + w / 2, yy + ch + 1, 0xFFFFFF55);
				}
				if (hand.owner() == mySeat) {
					cardHits.add(new CardHit(x, yy, w, ch, i));
				}
			}
			int below = y + 13 + ch;
			if (hand.value() != null && ClientConfig.get().assistLevel >= ClientConfig.ASSIST_VALUES) {
				Msg v = hand.value();
				graphics.centeredText(this.font, Component.translatable(v.key(), v.args().toArray()), cx, below, 0xFFFFFFFF);
			}
			if (hand.result() != null) {
				Component result = Component.translatable("gamblecraft.result." + hand.result());
				int color = switch (hand.result()) {
					case "win", "blackjack", "oczko", "perskie_oczko", "war_win", "cash_out" -> 0xFF55FF55;
					case "push", "tie" -> 0xFFFFFF55;
					default -> 0xFFFF6655;
				};
				graphics.centeredText(this.font, result, cx, below + 9, color);
			}
		}
	}

	private String labelFor(TableSnapshot snap, HandView hand) {
		if (hand.owner() == CardGame.DEALER || hand.owner() == CardGame.COMMUNITY) {
			return Component.translatable(hand.label()).getString();
		}
		for (SeatView seat : snap.seats()) {
			if (seat.index() == hand.owner()) {
				return seat.name();
			}
		}
		return Component.translatable(hand.label()).getString();
	}

	private static void drawCard(GuiGraphicsExtractor graphics, int card, int x, int y, int w, int h, float flipScale) {
		int drawW = Math.max(1, (int) (w * flipScale));
		int dx = x + (w - drawW) / 2;
		graphics.fill(dx + 1, y + 1, dx + drawW + 1, y + h + 1, 0x60000000);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CardTableRenderer.texture(card), dx, y, 0.0F, 0.0F, drawW, h, 88, 124, 88, 124);
	}

	/** Big WIN / LOSE banner that pops in, with confetti for wins. */
	private void drawResult(GuiGraphicsExtractor graphics, ClientTables.View view, float partialTick) {
		float age = ClientTables.now() + partialTick - view.resultAt;
		if (view.result == null || age < 0 || age > 50) {
			return;
		}
		boolean won = view.result.equals("win") || view.result.equals("blackjack");
		if (won && confettiFor != view.resultAt) {
			confettiFor = view.resultAt;
			var random = this.minecraft.level.getRandom();
			int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA00};
			for (int i = 0; i < 70; i++) {
				confetti.add(new float[] {this.width / 2.0F, this.height / 3.0F, (random.nextFloat() - 0.5F) * 9, -random.nextFloat() * 7 - 1,
						colors[random.nextInt(colors.length)]});
			}
		}
		for (float[] p : confetti) {
			graphics.fill((int) p[0], (int) p[1], (int) p[0] + 3, (int) p[1] + 3, (int) p[4]);
		}
		float pop = age < 8 ? 0.5F + age / 8.0F * 1.2F : 1.7F - Math.min(0.5F, (age - 8) / 30.0F);
		int alpha = (int) (255 * Mth.clamp((50 - age) / 15.0F, 0, 1));
		Component text = switch (view.result) {
			case "blackjack" -> Component.translatable("gamblecraft.banner.blackjack", view.resultAmount);
			case "win" -> Component.translatable("gamblecraft.banner.win", view.resultAmount);
			case "push" -> Component.translatable("gamblecraft.banner.push");
			default -> Component.translatable("gamblecraft.banner.lose");
		};
		int color = (won ? 0xFFD700 : view.result.equals("push") ? 0xFFFFFF : 0xFF5555) | (Math.max(alpha, 5) << 24);
		int half = this.font.width(text) / 2 + 6;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.width / 2.0F, this.height / 3.0F);
		graphics.pose().scale(pop * 1.6F, pop * 1.6F);
		graphics.fill(-half, -8, half, 8, (alpha * 3 / 4) << 24);
		graphics.fill(-half, -8, half, -7, (won ? 0xFFD700 : 0xAA3333) | (alpha << 24));
		graphics.fill(-half, 7, half, 8, (won ? 0xFFD700 : 0xAA3333) | (alpha << 24));
		graphics.centeredText(this.font, text, 0, -4, color);
		graphics.pose().popMatrix();
	}
}
