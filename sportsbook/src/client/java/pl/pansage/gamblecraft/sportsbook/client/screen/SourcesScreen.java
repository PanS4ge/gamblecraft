package pl.pansage.gamblecraft.sportsbook.client.screen;


import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.pansage.gamblecraft.sportsbook.client.ClientFeed;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Source;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.FeedSync;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.LeagueView;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.SetSwitch;

/**
 * Where the terminal's data comes from, as tick boxes: each feed, whether to use the bookmaker's football prices,
 * and each league with the feed it is read from right now. Operators (or the owner of a single-player world) can
 * change them; everyone else sees them greyed out.
 */
public class SourcesScreen extends Screen {
	private static final String KEY = "gamblecraft_sportsbook.sources.";
	private static final int SOURCE_ROW = 31;
	private static final int LEAGUE_ROW = 29;

	private final Screen parent;
	private int colA;
	private int colB;
	private int colW;

	public SourcesScreen(Screen parent) {
		super(Component.translatable(KEY + "title"));
		this.parent = parent;
	}

	private static void send(String key, boolean on) {
		ClientPlayNetworking.send(new SetSwitch(key, on));
	}

	@Override
	protected void init() {
		FeedSync feed = ClientFeed.get();
		boolean edit = feed != null && feed.canEdit();
		colW = Math.min(200, this.width / 2 - 12);
		colA = this.width / 2 - colW - 6;
		colB = this.width / 2 + 6;
		int y = 34;
		for (Source source : Source.values()) {
			Checkbox box = Checkbox.builder(Component.literal(source.displayName), this.font).pos(colA, y).selected(ClientFeed.on(source))
					.tooltip(Tooltip.create(Component.translatable(KEY + "source." + source.id + ".tooltip")))
					.onValueChange((checkbox, value) -> send("source:" + source.id, value)).build();
			box.active = edit;
			addRenderableWidget(box);
			y += SOURCE_ROW;
		}
		Checkbox odds = Checkbox.builder(Component.translatable(KEY + "draftkings"), this.font).pos(colA, y).selected(feed == null || feed.draftKings())
				.tooltip(Tooltip.create(Component.translatable(KEY + "draftkings.tooltip")))
				.onValueChange((checkbox, value) -> send("odds:draftkings", value)).build();
		odds.active = edit;
		addRenderableWidget(odds);
		int half = colW / 2;
		League[] leagues = League.values();
		int rows = (leagues.length + 1) / 2;
		for (int i = 0; i < leagues.length; i++) {
			League league = leagues[i];
			int x = colB + (i / rows) * half;
			int ly = 34 + (i % rows) * LEAGUE_ROW;
			Checkbox box = Checkbox.builder(Component.translatable(league.translationKey()), this.font).pos(x, ly).maxWidth(half - 2)
					.selected(ClientFeed.on(league)).onValueChange((checkbox, value) -> send("league:" + league.id, value)).build();
			box.active = edit;
			addRenderableWidget(box);
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(this.width / 2 - 60, this.height - 24, 120, 20).build());
	}

	/** The server sent new switches or a new read: rebuild the boxes from them. */
	public void feedChanged() {
		rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		FeedSync feed = ClientFeed.get();
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFD040);
		graphics.text(this.font, Component.translatable(KEY + "feeds"), colA, 22, 0xFFB0B0B0);
		graphics.text(this.font, Component.translatable(KEY + "leagues"), colB, 22, 0xFFB0B0B0);
		int y = 34;
		for (Source source : Source.values()) {
			int used = 0;
			boolean failing = false;
			for (League league : League.values()) {
				LeagueView view = ClientFeed.status(league);
				if (view != null && view.source() == source.ordinal()) {
					used += view.ok() ? 1 : 0;
					failing |= !view.ok();
				}
			}
			Component state = !ClientFeed.on(source) ? Component.translatable(KEY + "off").withStyle(ChatFormatting.DARK_GRAY)
					: used > 0 ? Component.translatable(KEY + "in_use", used).withStyle(ChatFormatting.GREEN)
					: failing ? Component.translatable(KEY + "failing").withStyle(ChatFormatting.RED)
					: Component.translatable(KEY + "standby").withStyle(ChatFormatting.GRAY);
			graphics.text(this.font, state, colA + colW - this.font.width(state), y + 4, 0xFFFFFFFF);
			String description = this.font.plainSubstrByWidth(Component.translatable(KEY + "source." + source.id).getString(), colW - 4);
			graphics.text(this.font, description, colA + 2, y + 19, 0xFF8C8C8C);
			y += SOURCE_ROW;
		}
		String odds = this.font.plainSubstrByWidth(Component.translatable(KEY + "draftkings.desc").getString(), colW - 4);
		graphics.text(this.font, odds, colA + 2, y + 19, 0xFF8C8C8C);
		int half = colW / 2;
		League[] leagues = League.values();
		int rows = (leagues.length + 1) / 2;
		for (int i = 0; i < leagues.length; i++) {
			League league = leagues[i];
			int x = colB + (i / rows) * half;
			int ly = 34 + (i % rows) * LEAGUE_ROW;
			LeagueView view = ClientFeed.status(league);
			Component line;
			int colour;
			if (!ClientFeed.on(league)) {
				line = Component.translatable(KEY + "off");
				colour = 0xFF606060;
			} else if (view == null) {
				line = Component.translatable(KEY + "loading");
				colour = 0xFF909090;
			} else if (view.source() < 0) {
				line = Component.translatable("gamblecraft_sportsbook.screen.status.no_source");
				colour = 0xFFFF6060;
			} else {
				String from = Source.of(view.source()).displayName;
				line = view.ok() ? Component.translatable(KEY + "league_ok", from, view.count()) : Component.translatable(KEY + "league_error", from);
				colour = view.ok() ? 0xFF70D070 : 0xFFFF6060;
			}
			graphics.text(this.font, this.font.plainSubstrByWidth(line.getString(), half - 4), x + 2, ly + 18, colour);
		}
		Component note = feed != null && feed.canEdit() ? Component.translatable(KEY + "note") : Component.translatable(KEY + "no_permission");
		graphics.centeredText(this.font, this.font.plainSubstrByWidth(note.getString(), this.width - 16), this.width / 2, this.height - 36, 0xFF909090);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
