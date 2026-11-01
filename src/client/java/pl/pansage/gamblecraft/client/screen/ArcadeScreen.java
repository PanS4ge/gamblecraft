package pl.pansage.gamblecraft.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import pl.pansage.gamblecraft.block.entity.ArcadeBlockEntity;
import pl.pansage.gamblecraft.block.entity.CrashPadBlockEntity;
import pl.pansage.gamblecraft.block.entity.DiceTableBlockEntity;
import pl.pansage.gamblecraft.block.entity.FortuneWheelBlockEntity;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.network.CasinoPayloads.ArcadeAction;

/**
 * Betting window of a shared-round game (wheel of fortune, dice, crash). It reads the machine's synced state every
 * frame, so the countdown, everyone's stakes and the crash multiplier are live; the machine itself stays in view.
 */
public class ArcadeScreen extends Screen {
	private static final int[] AMOUNTS = {1, 5, 25, 100, 500};
	private final BlockPos pos;
	private int amount = 5;
	private final List<Button> optionButtons = new ArrayList<>();
	private final List<Button> amountButtons = new ArrayList<>();
	private Button takeBack;
	private Button cashOut;

	public ArcadeScreen(BlockPos pos) {
		super(Component.empty());
		this.pos = pos;
	}

	private ArcadeBlockEntity game() {
		return this.minecraft.level != null && this.minecraft.level.getBlockEntity(pos) instanceof ArcadeBlockEntity game ? game : null;
	}

	private String optionKey(ArcadeBlockEntity game, String option) {
		if (game instanceof FortuneWheelBlockEntity) {
			return "gamblecraft.wheel.symbol." + option;
		}
		if (game instanceof DiceTableBlockEntity) {
			return "gamblecraft.dice.bet." + option;
		}
		return "gamblecraft.crash.rocket";
	}

	@Override
	protected void init() {
		ArcadeBlockEntity game = game();
		if (game == null) {
			return;
		}
		optionButtons.clear();
		amountButtons.clear();
		int right = this.width - 10;
		int y = 44;
		for (int i = 0; i < AMOUNTS.length; i++) {
			int value = AMOUNTS[i];
			Button button = Button.builder(Component.literal(String.valueOf(value)), b -> amount = value).bounds(right - 160 + i * 32, y, 30, 16).build();
			amountButtons.add(addRenderableWidget(button));
		}
		List<String> options = game.options();
		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i);
			Button button = Button.builder(Component.empty(), b -> ClientPlayNetworking.send(new ArcadeAction(pos, ArcadeAction.BET, option, amount)))
					.bounds(right - 160, 64 + i * 20, 158, 18).build();
			optionButtons.add(addRenderableWidget(button));
		}
		int below = 64 + options.size() * 20 + 4;
		takeBack = addRenderableWidget(Button.builder(Component.translatable("gamblecraft.arcade.take_back"),
				b -> ClientPlayNetworking.send(new ArcadeAction(pos, ArcadeAction.TAKE_BACK, "", 0))).bounds(right - 160, below, 158, 18).build());
		if (game instanceof CrashPadBlockEntity) {
			cashOut = addRenderableWidget(Button.builder(Component.empty(),
					b -> ClientPlayNetworking.send(new ArcadeAction(pos, ArcadeAction.CASH_OUT, "", 0))).bounds(right - 160, below + 22, 158, 28).build());
		}
		tick();
	}

	@Override
	public void tick() {
		ArcadeBlockEntity game = game();
		if (game == null) {
			onClose();
			return;
		}
		if (takeBack == null) {
			// the machine was not loaded yet when the window opened
			rebuildWidgets();
			return;
		}
		boolean open = game.phase() == ArcadeBlockEntity.Phase.IDLE || game.phase() == ArcadeBlockEntity.Phase.BETTING;
		for (int i = 0; i < AMOUNTS.length && i < amountButtons.size(); i++) {
			amountButtons.get(i).setMessage(Component.literal((AMOUNTS[i] == amount ? "> " : "") + AMOUNTS[i]));
		}
		List<String> options = game.options();
		for (int i = 0; i < options.size() && i < optionButtons.size(); i++) {
			String option = options.get(i);
			double payout = game.payout(option);
			Component name = Component.translatable(optionKey(game, option));
			Component label = payout > 0
					? Component.translatable("gamblecraft.arcade.option", name, String.format(Locale.ROOT, "%s", trim(payout)), game.totalOn(option))
					: Component.translatable("gamblecraft.arcade.option_plain", name, game.totalOn(option));
			optionButtons.get(i).setMessage(label);
			optionButtons.get(i).active = open;
		}
		takeBack.active = game.phase() == ArcadeBlockEntity.Phase.BETTING && game.stakeOf(this.minecraft.player.getUUID()) > 0;
		if (cashOut != null) {
			boolean flying = game.phase() == ArcadeBlockEntity.Phase.PLAYING;
			boolean in = game.stakes().stream().anyMatch(s -> s.player().equals(this.minecraft.player.getUUID()) && s.cashedOut() == 0);
			double now = CrashPadBlockEntity.multiplier(this.minecraft.level.getGameTime() - game.phaseStart());
			cashOut.setMessage(Component.translatable("gamblecraft.crash.cash_out", String.format(Locale.ROOT, "%.2f", flying ? now : 1.0)));
			cashOut.active = flying && in;
		}
	}

	private static String trim(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(this.width - 180, 0, this.width, this.height, 0x60000000, 0xB0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		ArcadeBlockEntity game = game();
		if (game == null) {
			return;
		}
		int x = this.width - 90;
		long now = this.minecraft.level.getGameTime();
		graphics.centeredText(this.font, Component.translatable(game.titleKey()), x, 8, 0xFFFFE070);
		Component status = switch (game.phase()) {
			case IDLE -> Component.translatable("gamblecraft.arcade.idle");
			case BETTING -> Component.translatable("gamblecraft.arcade.betting", game.secondsLeft(now));
			case PLAYING -> game instanceof CrashPadBlockEntity
					? Component.translatable("gamblecraft.crash.flying", String.format(Locale.ROOT, "%.2f", CrashPadBlockEntity.multiplier(now - game.phaseStart() + partialTick)))
					: Component.translatable("gamblecraft.roulette.no_more_bets");
			case RESULT -> game.outcome();
		};
		graphics.centeredText(this.font, status, x, 22, game.phase() == ArcadeBlockEntity.Phase.PLAYING ? 0xFF60FF60 : 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.arcade.amount", amount), x, 34, 0xFFAAAAAA);
		long mine = game.stakeOf(this.minecraft.player.getUUID());
		int bottom = takeBack == null ? this.height - 26 : Math.max(this.height - 26, takeBack.getY() + (cashOut != null ? 54 : 24));
		graphics.centeredText(this.font, Component.translatable("gamblecraft.arcade.your_stake", mine), x, bottom, 0xFFFFE070);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.balance",
				ClientCurrency.balance(this.minecraft.player, ChipCurrency.ID)), x, bottom + 12, 0xFF55FF55);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
