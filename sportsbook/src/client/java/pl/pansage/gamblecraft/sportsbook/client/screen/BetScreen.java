package pl.pansage.gamblecraft.sportsbook.client.screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.sportsbook.client.ClientFeed;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.item.CouponItem;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.EventView;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.FeedSync;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.LeagueView;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.PlaceBet;

/**
 * The betting window: leagues on the left, the league's games with their prices in the middle (click a price to put
 * it on the slip, again to take it off), the slip with the stake and the buy button on the right.
 */
public class BetScreen extends Screen {
	private static final String KEY = "gamblecraft_sportsbook.screen.";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final int ROW = 26;
	private static final int TAB = 15;
	private static final int ODDS_W = 35;
	/** Remembered between openings, like the slip. */
	private static League league;
	private static String stakeText = "100";
	private static boolean minerals;

	private record Hit(int x0, int y0, int x1, int y1, Runnable action, Component tooltip) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	private final BlockPos terminal;
	private final List<Hit> hits = new ArrayList<>();
	private double scroll;
	private int left;
	private int leftW;
	private int mid;
	private int midW;
	private int right;
	private int rightW;
	private int top;
	private int bottom;
	private int slipBottom;
	private EditBox stake;
	private Button currency;
	private Button place;
	private Component message;
	private boolean messageOk;

	public BetScreen(BlockPos terminal) {
		super(Component.translatable(KEY + "title"));
		this.terminal = terminal;
	}

	@Override
	protected void init() {
		left = 8;
		leftW = 96;
		rightW = 134;
		right = this.width - 8 - rightW;
		mid = left + leftW + 8;
		midW = right - 8 - mid;
		top = 24;
		bottom = this.height - 8;
		slipBottom = bottom - 106;
		if (league == null || !ClientFeed.on(league)) {
			league = null;
			for (League l : League.values()) {
				if (ClientFeed.on(l) && (league == null || (ClientFeed.events(league).isEmpty() && !ClientFeed.events(l).isEmpty()))) {
					league = l;
				}
			}
		}
		int y = slipBottom;
		stake = new EditBox(this.font, right + 50, y + 13, rightW - 54, 16, Component.translatable(KEY + "stake"));
		stake.setMaxLength(7);
		stake.setValue(stakeText);
		stake.setResponder(text -> stakeText = text);
		addRenderableWidget(stake);
		currency = addRenderableWidget(Button.builder(currencyLabel(), b -> {
			minerals = !minerals;
			b.setMessage(currencyLabel());
		}).bounds(right + 4, y + 33, rightW - 8, 18).build());
		place = addRenderableWidget(Button.builder(Component.translatable(KEY + "place"), b -> place())
				.bounds(right + 4, y + 68, rightW - 8, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(KEY + "sources"), b -> this.minecraft.gui.setScreen(new SourcesScreen(this)))
				.bounds(left, bottom - 20, leftW, 20).build());
	}

	private Component currencyLabel() {
		return Component.translatable(minerals ? KEY + "pay_minerals" : KEY + "pay_chips");
	}

	private long stakeValue() {
		try {
			return Long.parseLong(stakeText.replaceAll("[^0-9]", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void place() {
		List<String> keys = new ArrayList<>();
		List<Integer> selections = new ArrayList<>();
		for (Map.Entry<String, Outcome> entry : ClientFeed.SLIP.entrySet()) {
			keys.add(entry.getKey());
			selections.add(entry.getValue().ordinal());
		}
		if (!keys.isEmpty()) {
			ClientPlayNetworking.send(new PlaceBet(terminal, keys, selections, stakeValue(), minerals));
		}
	}

	/** The server's answer to a purchase. */
	public void result(boolean ok, Component message) {
		this.message = message;
		this.messageOk = ok;
	}

	private void toggle(EventView e, Outcome outcome) {
		FeedSync feed = ClientFeed.get();
		if (ClientFeed.SLIP.get(e.key()) == outcome) {
			ClientFeed.SLIP.remove(e.key());
		} else if (ClientFeed.SLIP.containsKey(e.key()) || feed == null || ClientFeed.SLIP.size() < feed.maxPicks()) {
			ClientFeed.SLIP.put(e.key(), outcome);
		} else {
			result(false, Component.translatable("gamblecraft_sportsbook.bet.too_many", feed.maxPicks()).withStyle(ChatFormatting.RED));
		}
	}

	@Override
	public void tick() {
		long now = System.currentTimeMillis();
		FeedSync feed = ClientFeed.get();
		long value = stakeValue();
		boolean slipOpen = !ClientFeed.SLIP.isEmpty();
		for (String key : ClientFeed.SLIP.keySet()) {
			EventView e = ClientFeed.event(key);
			slipOpen &= e != null && ClientFeed.open(e, now);
		}
		place.active = slipOpen && feed != null && value >= feed.minStake() && value <= feed.maxStake();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(left - 3, top - 3, left + leftW + 3, bottom + 3, 0xC0101418);
		graphics.fill(mid - 3, top - 3, mid + midW + 3, bottom + 3, 0xC0101418);
		graphics.fill(right - 3, top - 3, right + rightW + 3, bottom + 3, 0xC0141A12);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		hits.clear();
		long now = System.currentTimeMillis();
		graphics.centeredText(this.font, this.title, this.width / 2, 9, 0xFFFFD040);
		long chips = ClientCurrency.balance(this.minecraft.player, ChipCurrency.ID);
		Component balance = Component.translatable(KEY + "balance", chips, mineralWorth());
		graphics.text(this.font, balance, this.width - 8 - this.font.width(balance), 9, 0xFF70E070);
		leagues(graphics, mouseX, mouseY);
		games(graphics, mouseX, mouseY, now);
		slip(graphics, mouseX, mouseY, now);
		for (Hit hit : hits) {
			if (hit.tooltip() != null && hit.contains(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(this.font, hit.tooltip(), mouseX, mouseY);
			}
		}
	}

	/** What the minerals in the pockets would buy in chips, at the cashier's rates. */
	private long mineralWorth() {
		long worth = 0;
		for (ItemStack stack : this.minecraft.player.getInventory()) {
			Integer rate = CashierEntity.RATES.get(stack.getItem());
			if (rate != null && stack.getComponentsPatch().isEmpty()) {
				worth += (long) rate * stack.getCount();
			}
		}
		return worth;
	}

	private void leagues(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable(KEY + "leagues"), left, top, 0xFFB0B0B0);
		int y = top + 12;
		for (League l : League.values()) {
			if (!ClientFeed.on(l)) {
				continue;
			}
			int open = 0;
			int live = 0;
			long now = System.currentTimeMillis();
			for (EventView e : ClientFeed.events(l)) {
				open += ClientFeed.open(e, now) ? 1 : 0;
				live += e.state() == EventState.LIVE.ordinal() ? 1 : 0;
			}
			boolean selected = l == league;
			boolean hover = mouseX >= left && mouseX < left + leftW && mouseY >= y && mouseY < y + TAB;
			graphics.fill(left, y, left + leftW, y + TAB - 1, selected ? 0xFF2E5E30 : hover ? 0xFF2A3440 : 0x40202830);
			String count = live > 0 ? "●" + live : String.valueOf(open);
			String name = this.font.plainSubstrByWidth(Component.translatable(l.translationKey()).getString(), leftW - 9 - this.font.width(count));
			graphics.text(this.font, name, left + 3, y + 3, open + live > 0 ? 0xFFFFFFFF : 0xFF808080);
			graphics.text(this.font, count, left + leftW - 3 - this.font.width(count), y + 3, live > 0 ? 0xFFFF5050 : 0xFFA0A0A0);
			League target = l;
			hits.add(new Hit(left, y, left + leftW, y + TAB, () -> {
				league = target;
				scroll = 0;
			}, status(l)));
			y += TAB;
		}
	}

	private Component status(League l) {
		LeagueView view = ClientFeed.status(l);
		if (view == null) {
			return Component.translatable(KEY + "status.waiting");
		}
		String source = view.source() < 0 ? "-" : pl.pansage.gamblecraft.sportsbook.feed.Source.of(view.source()).displayName;
		return view.ok() ? Component.translatable(KEY + "status.ok", source, view.count())
				: view.source() < 0 && view.message().isEmpty() ? Component.translatable(KEY + "status.no_source")
				: Component.translatable(KEY + "status.error", source, view.message());
	}

	private void games(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now) {
		if (league == null) {
			graphics.text(this.font, Component.translatable(KEY + "no_leagues"), mid + 4, top + 4, 0xFFA0A0A0);
			return;
		}
		graphics.text(this.font, Component.translatable(league.translationKey()), mid, top, 0xFFFFD040);
		List<EventView> games = ClientFeed.events(league);
		int listTop = top + 12;
		if (games.isEmpty()) {
			graphics.textWithWordWrap(this.font, ClientFeed.get() == null ? Component.translatable(KEY + "status.waiting") : status(league),
					mid + 2, listTop + 4, midW - 4, 0xFFA0A0A0);
			return;
		}
		int height = games.size() * ROW;
		scroll = Math.max(0, Math.min(scroll, height - (bottom - listTop)));
		graphics.enableScissor(mid, listTop, mid + midW, bottom);
		int y = listTop - (int) scroll;
		for (EventView e : games) {
			if (y > bottom) {
				break;
			}
			if (y + ROW >= listTop) {
				row(graphics, e, mid, y, midW, mouseX, mouseY, now, listTop);
			}
			y += ROW;
		}
		graphics.disableScissor();
	}

	private void row(GuiGraphicsExtractor graphics, EventView e, int x, int y, int w, int mouseX, int mouseY, long now, int listTop) {
		graphics.text(this.font, this.font.plainSubstrByWidth(e.home() + " – " + e.away(), w - 4), x + 2, y + 2, 0xFFFFFFFF);
		EventState state = EventState.of(e.state());
		boolean open = ClientFeed.open(e, now);
		List<Outcome> outcomes = e.oddsDraw() > 1 ? List.of(Outcome.HOME, Outcome.DRAW, Outcome.AWAY) : List.of(Outcome.HOME, Outcome.AWAY);
		int room = open ? w - outcomes.size() * (ODDS_W + 2) - 4 : w - 4;
		var kickoff = Instant.ofEpochMilli(e.kickoff()).atZone(ZoneId.systemDefault());
		String time = this.font.width(TIME.format(kickoff)) <= room ? TIME.format(kickoff) : CLOCK.format(kickoff);
		Component when = switch (state) {
			case SCHEDULED -> Component.literal(time).withStyle(open ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY);
			case LIVE -> Component.literal("● " + e.clock() + " " + e.score()).withStyle(ChatFormatting.RED);
			case FINISHED -> Component.translatable(KEY + "finished", e.score()).withStyle(ChatFormatting.DARK_GRAY);
			case POSTPONED -> Component.translatable(KEY + "postponed").withStyle(ChatFormatting.DARK_GRAY);
			case CANCELLED -> Component.translatable(KEY + "cancelled").withStyle(ChatFormatting.DARK_GRAY);
		};
		graphics.text(this.font, when, x + 2, y + 14, 0xFFFFFFFF);
		if (open) {
			int bx = x + w - outcomes.size() * (ODDS_W + 2);
			Component by = e.model() ? Component.translatable(KEY + "odds_model") : Component.translatable(KEY + "odds_bookmaker", "DraftKings");
			for (Outcome outcome : outcomes) {
				double price = ClientFeed.price(e, outcome);
				if (price > 1) {
					boolean picked = ClientFeed.SLIP.get(e.key()) == outcome;
					boolean hover = mouseX >= bx && mouseX < bx + ODDS_W && mouseY >= y + 11 && mouseY < y + 23 && mouseY >= listTop;
					graphics.fill(bx, y + 11, bx + ODDS_W, y + 23, picked ? 0xFF2E8B3A : hover ? 0xFF46525E : 0xFF2A323C);
					String odds = CouponItem.odds(price);
					if (this.font.width(outcome.label) + this.font.width(odds) + 6 <= ODDS_W) {
						graphics.text(this.font, outcome.label, bx + 2, y + 13, picked ? 0xFFFFFFFF : 0xFF909090);
					}
					graphics.text(this.font, odds, bx + ODDS_W - 2 - this.font.width(odds), y + 13, picked ? 0xFFFFFFFF : 0xFFFFD040);
					if (y + 11 >= listTop && y + 23 <= bottom) {
						hits.add(new Hit(bx, y + 11, bx + ODDS_W, y + 23, () -> toggle(e, outcome), by));
					}
				}
				bx += ODDS_W + 2;
			}
		}
		graphics.fill(x, y + ROW - 1, x + w, y + ROW, 0x30FFFFFF);
	}

	private void slip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now) {
		FeedSync feed = ClientFeed.get();
		graphics.text(this.font, Component.translatable(KEY + "slip", ClientFeed.SLIP.size()), right + 2, top, 0xFFFFD040);
		int y = top + 12;
		double total = 1;
		int shown = 0;
		int hidden = 0;
		for (Map.Entry<String, Outcome> entry : new ArrayList<>(ClientFeed.SLIP.entrySet())) {
			EventView e = ClientFeed.event(entry.getKey());
			double price = e == null ? 0 : ClientFeed.price(e, entry.getValue());
			total *= Math.max(1, price);
			if (y + 22 > slipBottom - 12) {
				hidden++;
				continue;
			}
			boolean open = e != null && ClientFeed.open(e, now);
			String teams = e == null ? entry.getKey() : e.home() + " – " + e.away();
			graphics.text(this.font, this.font.plainSubstrByWidth(teams, rightW - 16), right + 2, y, open ? 0xFFC8C8C8 : 0xFFFF6060);
			String pick = entry.getValue().label + " · " + (e == null ? "?" : entry.getValue() == Outcome.HOME ? e.home()
					: entry.getValue() == Outcome.AWAY ? e.away() : Component.translatable("gamblecraft_sportsbook.draw").getString());
			FormattedCharSequence line = Component.literal(this.font.plainSubstrByWidth(pick, rightW - 44)).getVisualOrderText();
			graphics.text(this.font, line, right + 2, y + 10, 0xFF90C090);
			String odds = open ? CouponItem.odds(price) : "—";
			graphics.text(this.font, odds, right + rightW - 14 - this.font.width(odds), y + 10, 0xFFFFD040);
			boolean hover = mouseX >= right + rightW - 11 && mouseX < right + rightW && mouseY >= y && mouseY < y + 10;
			graphics.text(this.font, "×", right + rightW - 8, y, hover ? 0xFFFF6060 : 0xFF909090);
			String key = entry.getKey();
			hits.add(new Hit(right + rightW - 11, y, right + rightW, y + 10, () -> ClientFeed.SLIP.remove(key), null));
			y += 22;
			shown++;
		}
		if (hidden > 0) {
			graphics.text(this.font, Component.translatable(KEY + "more", hidden), right + 2, y, 0xFF909090);
		}
		if (shown + hidden == 0) {
			graphics.textWithWordWrap(this.font, Component.translatable(KEY + "slip_empty"), right + 2, y, rightW - 4, 0xFF808080);
		}
		int s = slipBottom;
		graphics.text(this.font, Component.translatable(KEY + "total_odds", CouponItem.odds(total)), right + 2, s, 0xFFFFFFFF);
		graphics.text(this.font, Component.translatable(KEY + "stake"), right + 2, s + 17, 0xFFC8C8C8);
		long value = stakeValue();
		long max = feed == null ? Long.MAX_VALUE : feed.maxPayout();
		long potential = ClientFeed.SLIP.isEmpty() ? 0 : Math.min(max, (long) Math.floor(value * total + 1e-6));
		graphics.text(this.font, Component.translatable(KEY + "potential", potential), right + 2, s + 56, 0xFFFFD040);
		if (feed != null && (value < feed.minStake() || value > feed.maxStake())) {
			graphics.text(this.font, Component.translatable(KEY + "limits", feed.minStake(), feed.maxStake()), right + 2, s + 91, 0xFFFF8080);
		} else if (message != null) {
			graphics.textWithWordWrap(this.font, message, right + 2, s + 91, rightW - 4, messageOk ? 0xFF70E070 : 0xFFFF7070);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		for (Hit hit : List.copyOf(hits)) {
			if (hit.contains(event.x(), event.y())) {
				hit.action().run();
				this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= mid && mouseX < mid + midW) {
			scroll -= scrollY * ROW;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** For tests: picks the league shown. */
	public static void show(League shown) {
		league = shown;
	}
}
