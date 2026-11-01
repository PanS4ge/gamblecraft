package pl.pansage.gamblecraft.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.network.CasinoPayloads.KioskBuy;
import pl.pansage.gamblecraft.registry.ModItems;

/** The casino scratch-card kiosk: three cards for chips; the cashier pays the winners. */
public class KioskScreen extends Screen {
	private final BlockPos pos;

	public KioskScreen(BlockPos pos) {
		super(Component.translatable("block.gamblecraft.scratch_kiosk"));
		this.pos = pos;
	}

	@Override
	protected void init() {
		ScratchCardItem.Tier[] tiers = ScratchCardItem.Tier.values();
		for (int i = 0; i < tiers.length; i++) {
			int tier = i;
			Component label = Component.translatable("gamblecraft.kiosk.buy", Component.translatable("gamblecraft.scratch.tier." + tiers[i].key()), tiers[i].chips());
			addRenderableWidget(Button.builder(label, b -> ClientPlayNetworking.send(new KioskBuy(pos, tier)))
					.bounds(this.width / 2 - 100, 60 + i * 26, 200, 20).build());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFE070);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.kiosk.hint"), this.width / 2, 32, 0xFFDDDDDD);
		ItemStack[] cards = {new ItemStack(ModItems.SCRATCH_CARD_BRONZE), new ItemStack(ModItems.SCRATCH_CARD_SILVER), new ItemStack(ModItems.SCRATCH_CARD_GOLD)};
		for (int i = 0; i < cards.length; i++) {
			graphics.item(cards[i], this.width / 2 - 124, 62 + i * 26);
		}
		graphics.centeredText(this.font, Component.translatable("gamblecraft.cashier.balance", ClientCurrency.balance(this.minecraft.player, ChipCurrency.ID)),
				this.width / 2, 146, 0xFF55FF55);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
