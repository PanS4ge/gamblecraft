package pl.pansage.gamblecraft.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import pl.pansage.gamblecraft.menu.DeckMenu;

/** Chest-style deck view with a Shuffle button that makes the cards jitter while they are reordered. */
public class DeckScreen extends AbstractContainerScreen<DeckMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int SHUFFLE_TICKS = 10;
	private int shuffleTicks;

	public DeckScreen(DeckMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 114 + DeckMenu.ROWS * 18);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.deck.shuffle"), button -> {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, DeckMenu.BUTTON_SHUFFLE);
			shuffleTicks = SHUFFLE_TICKS;
		}).bounds(this.leftPos + this.imageWidth + 4, this.topPos + 16, 60, 20).build());
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (shuffleTicks > 0) {
			shuffleTicks--;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int top = DeckMenu.ROWS * 18 + 17;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, top, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos + top, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (shuffleTicks > 0) {
			float t = shuffleTicks - partialTick;
			graphics.pose().pushMatrix();
			graphics.pose().translate((float) Math.sin(t * 2.7) * t * 0.4F, (float) Math.cos(t * 3.1) * t * 0.25F);
			super.extractContents(graphics, mouseX, mouseY, partialTick);
			graphics.pose().popMatrix();
		} else {
			super.extractContents(graphics, mouseX, mouseY, partialTick);
		}
	}
}
