package pl.pansage.gamblecraft.sportsbook.test.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** For screenshots: the coupons side by side, large, with the description of one of them open. */
public class CouponShowcaseScreen extends Screen {
	private final List<ItemStack> coupons;
	private final int open;

	public CouponShowcaseScreen(List<ItemStack> coupons, int open) {
		super(Component.literal("Coupons"));
		this.coupons = coupons;
		this.open = open;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		for (int i = 0; i < coupons.size(); i++) {
			int x = 12;
			int y = 16 + i * 60;
			graphics.fill(x - 4, y - 4, x + 52, y + 52, i == open ? 0x9040A040 : 0x60000000);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(3, 3);
			graphics.item(coupons.get(i), 0, 0);
			graphics.pose().popMatrix();
		}
		if (open >= 0 && open < coupons.size()) {
			graphics.setComponentTooltipForNextFrame(this.font, Screen.getTooltipFromItem(this.minecraft, coupons.get(open)), 64, 18);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
