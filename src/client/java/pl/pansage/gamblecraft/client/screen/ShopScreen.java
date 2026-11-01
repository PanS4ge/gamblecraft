package pl.pansage.gamblecraft.client.screen;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.ChipShop;
import pl.pansage.gamblecraft.network.CasinoPayloads.OpenShop;
import pl.pansage.gamblecraft.network.CasinoPayloads.ShopChoice;

/** What a casino person offers for chips: one button per offer, its icon beside it, and the player's chips. */
public class ShopScreen extends Screen {
	private final int entityId;
	private List<ChipShop.Offer> offers;

	public ShopScreen(OpenShop shop) {
		super(shop.title());
		this.entityId = shop.entityId();
		this.offers = shop.offers();
	}

	public int entityId() {
		return entityId;
	}

	/** The server sent the list again after a choice: same window, fresh offers. */
	public void refresh(OpenShop shop) {
		this.offers = shop.offers();
		rebuildWidgets();
	}

	@Override
	protected void init() {
		int top = Math.max(44, this.height / 2 - offers.size() * 13);
		for (int i = 0; i < offers.size(); i++) {
			int index = i;
			ChipShop.Offer offer = offers.get(i);
			Button button = Button.builder(offer.label(), b -> ClientPlayNetworking.send(new ShopChoice(entityId, index)))
					.bounds(this.width / 2 - 130, top + i * 26, 270, 20).build();
			button.active = offer.enabled();
			addRenderableWidget(button);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int top = Math.max(44, this.height / 2 - offers.size() * 13);
		graphics.centeredText(this.font, this.title, this.width / 2, top - 26, 0xFFFFE070);
		for (int i = 0; i < offers.size(); i++) {
			graphics.item(offers.get(i).icon(), this.width / 2 - 154, top + i * 26 + 2);
		}
		graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.balance", ClientCurrency.balance(this.minecraft.player, ChipCurrency.ID)),
				this.width / 2, top + offers.size() * 26 + 8, 0xFF55FF55);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
