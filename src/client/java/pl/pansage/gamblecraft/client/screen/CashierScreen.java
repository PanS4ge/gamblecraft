package pl.pansage.gamblecraft.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pl.pansage.gamblecraft.core.casino.Chips;
import pl.pansage.gamblecraft.item.ChipItem;
import pl.pansage.gamblecraft.network.CasinoPayloads.CashierAction;
import pl.pansage.gamblecraft.network.CasinoPayloads.OpenCashier;
import pl.pansage.gamblecraft.network.CasinoPayloads.Rate;

/** The cashier's window: click a mineral to buy chips with it (shift: all of it), or cash chips out. */
public class CashierScreen extends Screen {
	private static final int TILE = 22;
	private static final int COLUMNS = 8;
	private final OpenCashier offer;

	public CashierScreen(OpenCashier offer) {
		super(Component.translatable("entity.gamblecraft.cashier"));
		this.offer = offer;
	}

	private static Item itemOf(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
	}

	private long count(Item item) {
		long n = 0;
		var inventory = this.minecraft.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private long chips() {
		long n = 0;
		var inventory = this.minecraft.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() instanceof ChipItem chip) {
				n += (long) chip.value() * stack.getCount();
			}
		}
		return n;
	}

	private int gridLeft() {
		return this.width / 2 - COLUMNS * (TILE + 4) / 2;
	}

	private int gridTop() {
		return 48;
	}

	private int cashOutTop() {
		int rows = (offer.rates().size() + COLUMNS - 1) / COLUMNS;
		return gridTop() + rows * (TILE + 4) + 26;
	}

	@Override
	protected void init() {
		int y = cashOutTop() + 12;
		for (Rate payout : offer.payouts()) {
			int row = y;
			int x = this.width / 2 + 10;
			addRenderableWidget(Button.builder(Component.literal("1"), b -> cashOut(payout, 1)).bounds(x, row, 30, 20).build());
			addRenderableWidget(Button.builder(Component.literal("10"), b -> cashOut(payout, 10)).bounds(x + 34, row, 30, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("gamblecraft.cashier.all"), b -> cashOut(payout, -1))
					.bounds(x + 68, row, 50, 20).build());
			y += 24;
		}
		// the VIP desk: the VIP card and pawn tickets (the server answers with a shop window)
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.cashier.desk"),
				b -> ClientPlayNetworking.send(new pl.pansage.gamblecraft.network.CasinoPayloads.ShopChoice(offer.entityId(), -1)))
				.bounds(this.width - 130, 8, 120, 20).build());
	}

	private void cashOut(Rate payout, int count) {
		ClientPlayNetworking.send(new CashierAction(offer.entityId(), false, payout.item(), count));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int i = 0; i < offer.rates().size(); i++) {
			int x = gridLeft() + (i % COLUMNS) * (TILE + 4);
			int y = gridTop() + (i / COLUMNS) * (TILE + 4);
			if (event.x() >= x && event.x() < x + TILE && event.y() >= y && event.y() < y + TILE) {
				int amount = this.minecraft.hasShiftDown() ? 9999 : 1;
				ClientPlayNetworking.send(new CashierAction(offer.entityId(), true, offer.rates().get(i).item(), amount));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFE08A);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.balance", chips()), this.width / 2, 22, 0xFF55FF55);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.buy"), this.width / 2, 36, 0xFFFFFFFF);
		Rate hovered = null;
		for (int i = 0; i < offer.rates().size(); i++) {
			Rate rate = offer.rates().get(i);
			Item item = itemOf(rate.item());
			int x = gridLeft() + (i % COLUMNS) * (TILE + 4);
			int y = gridTop() + (i / COLUMNS) * (TILE + 4);
			boolean over = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
			graphics.fill(x, y, x + TILE, y + TILE, over ? 0xC0405060 : 0xA0000000);
			ItemStack stack = new ItemStack(item);
			graphics.item(stack, x + 3, y + 3);
			long have = count(item);
			graphics.itemDecorations(this.font, stack, x + 3, y + 3, have > 0 ? String.valueOf(have) : "");
			if (over) {
				hovered = rate;
			}
		}
		int infoY = cashOutTop() - 16;
		if (hovered != null) {
			Item item = itemOf(hovered.item());
			graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.rate", item.getName(new ItemStack(item)), hovered.chips()),
					this.width / 2, infoY, 0xFFDDDDDD);
		} else {
			graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.buy_hint"), this.width / 2, infoY, 0xFF999999);
		}
		graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.cash_out", Math.round(offer.houseCut() * 100)),
				this.width / 2, cashOutTop(), 0xFFFFFFFF);
		int y = cashOutTop() + 12;
		for (Rate payout : offer.payouts()) {
			Item item = itemOf(payout.item());
			int x = this.width / 2 - 150;
			graphics.item(new ItemStack(item), x, y + 2);
			graphics.text(this.font, Component.translatable("gamblecraft.cashier.payout", item.getName(new ItemStack(item)), Chips.cost(1, payout.chips())),
					x + 20, y + 6, 0xFFDDDDDD);
			y += 24;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
