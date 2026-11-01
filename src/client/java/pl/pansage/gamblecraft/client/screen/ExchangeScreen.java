package pl.pansage.gamblecraft.client.screen;

import java.util.Locale;

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
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.network.ScratchPayloads.ExchangeAction;
import pl.pansage.gamblecraft.network.ScratchPayloads.OpenExchange;
import pl.pansage.gamblecraft.network.TablePayloads.Accepted;

/** The village exchange counter: buy scratch cards, or sell village goods for emeralds (click: one, shift-click: all). */
public class ExchangeScreen extends Screen {
	private static final String[] TIERS = {"bronze", "silver", "gold"};
	private final OpenExchange offer;

	public ExchangeScreen(OpenExchange offer) {
		super(Component.translatable("entity.gamblecraft.lottery_clerk"));
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

	@Override
	protected void init() {
		int y = 52;
		for (int tier = 0; tier < offer.cardPrices().size(); tier++) {
			int t = tier;
			Component label = Component.translatable("gamblecraft.exchange.buy_card",
					Component.translatable("gamblecraft.scratch.tier." + TIERS[tier]), offer.cardPrices().get(tier));
			addRenderableWidget(Button.builder(label, b -> ClientPlayNetworking.send(new ExchangeAction(offer.clerkId(), true, "", t)))
					.bounds(this.width / 2 - 154 + tier * 104, y, 100, 20).build());
		}
	}

	private int[] goodsLayout() {
		int cols = Math.min(10, Math.max(1, offer.goods().size()));
		return new int[] {this.width / 2 - cols * 13, 110, cols};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int[] layout = goodsLayout();
		for (int i = 0; i < offer.goods().size(); i++) {
			int x = layout[0] + (i % layout[2]) * 26;
			int y = layout[1] + (i / layout[2]) * 26;
			if (event.x() >= x && event.x() < x + 22 && event.y() >= y && event.y() < y + 22) {
				int amount = this.minecraft.hasShiftDown() ? 9999 : 1;
				ClientPlayNetworking.send(new ExchangeAction(offer.clerkId(), false, offer.goods().get(i).item(), amount));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFE08A);
		graphics.centeredText(this.font, VillageRegistry.name(offer.village()), this.width / 2, 26, 0xFFDDDDDD);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.exchange.cards"), this.width / 2, 40, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.exchange.we_buy", (int) Math.round(offer.rate() * 100)),
				this.width / 2, 96, 0xFFFFFFFF);
		int[] layout = goodsLayout();
		Accepted hovered = null;
		for (int i = 0; i < offer.goods().size(); i++) {
			Accepted good = offer.goods().get(i);
			Item item = itemOf(good.item());
			int x = layout[0] + (i % layout[2]) * 26;
			int y = layout[1] + (i / layout[2]) * 26;
			graphics.fill(x, y, x + 22, y + 22, 0xA0000000);
			ItemStack stack = new ItemStack(item);
			graphics.item(stack, x + 3, y + 3);
			graphics.itemDecorations(this.font, stack, x + 3, y + 3, String.valueOf(count(item)));
			if (mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22) {
				hovered = good;
			}
		}
		if (hovered != null) {
			Item item = itemOf(hovered.item());
			graphics.centeredText(this.font, Component.translatable("gamblecraft.exchange.price", item.getName(new ItemStack(item)),
					String.format(Locale.ROOT, "%.2f", hovered.value() * offer.rate())), this.width / 2, this.height - 40, 0xFFDDDDDD);
		}
		graphics.centeredText(this.font, Component.translatable("gamblecraft.exchange.hint"), this.width / 2, this.height - 26, 0xFF999999);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.exchange.wallet", count(Items.EMERALD)), this.width / 2, this.height - 14,
				0xFF55FF55);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
