package pl.pansage.gamblecraft.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.sdl.SDLMouse;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.client.scratch.ClientScratch;
import pl.pansage.gamblecraft.client.scratch.ScratchGeometry;
import pl.pansage.gamblecraft.client.table.TableCamera;
import pl.pansage.gamblecraft.core.casino.ScratchCards;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.network.ScratchPayloads.ScratchCommand;

/**
 * Scratching a card that lies on a block, seen from above. Over the card the mouse pointer is replaced by the
 * player's own hand (drawn in the world by {@code ScratchCardRenderer}); holding the button rubs the foil off.
 * Escape steps back and leaves the card lying there.
 */
public class ScratchTableScreen extends Screen {
	private final ScratchCardEntity card;
	private boolean cursorHidden;
	private int settledAt = -1;
	private int ticks;

	public ScratchTableScreen(ScratchCardEntity card) {
		super(Component.translatable("gamblecraft.scratch.title"));
		this.card = card;
	}

	@Override
	protected void init() {
		int y = this.height - 28;
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.scratch.reveal_all"), b -> revealAll())
				.bounds(this.width / 2 - 154, y, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.scratch.view_panel"), b -> toPanel())
				.bounds(this.width / 2 - 50, y, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.scratch.pick_up"), b -> pickUp())
				.bounds(this.width / 2 + 54, y, 100, 20).build());
	}

	public ScratchCardEntity card() {
		return card;
	}

	public void revealAll() {
		if (card.result() == ScratchCardEntity.RUNNING) {
			java.util.List<float[]> flakes = new java.util.ArrayList<>();
			card.foil().revealAll((x, y) -> flakes.add(new float[] {x, y}));
			ClientScratch.effects(card, flakes);
			ClientPlayNetworking.send(new ScratchCommand(card.getId(), ScratchCommand.REVEAL_ALL));
		}
	}

	private void toPanel() {
		ClientConfig config = ClientConfig.get();
		config.scratchPanel = true;
		config.save();
		showCursor();
		TableCamera.release();
		this.minecraft.gui.setScreen(new ScratchScreen(card));
	}

	private void pickUp() {
		ClientPlayNetworking.send(new ScratchCommand(card.getId(), ScratchCommand.PICK_UP));
		onClose();
	}

	private double[] pick(double mouseX, double mouseY) {
		double[] grid = ScratchGeometry.pick(card, mouseX, mouseY, this.width, this.height);
		return grid != null && ScratchGeometry.onCard(grid[0], grid[1]) ? grid : null;
	}

	private boolean overButton(double mouseX, double mouseY) {
		return mouseY >= this.height - 32;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		double[] grid = overButton(mouseX, mouseY) ? null : pick(mouseX, mouseY);
		if (grid == null) {
			ClientScratch.hover(Double.NaN, Double.NaN);
			showCursor();
		} else {
			ClientScratch.hover(grid[0], grid[1]);
			hideCursor();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double[] grid = overButton(event.x(), event.y()) ? null : pick(event.x(), event.y());
		if (grid != null && event.button() == 0) {
			ClientScratch.hover(grid[0], grid[1]);
			ClientScratch.beginStroke();
			ClientScratch.strokeTo(grid[0], grid[1], null);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (ClientScratch.isStroking()) {
			double[] grid = ScratchGeometry.pick(card, event.x(), event.y(), this.width, this.height);
			if (grid != null) {
				ClientScratch.hover(grid[0], grid[1]);
				ClientScratch.strokeTo(grid[0], grid[1], null);
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		ClientScratch.endStroke();
		return super.mouseReleased(event);
	}

	private void hideCursor() {
		if (!cursorHidden) {
			SDLMouse.SDL_HideCursor();
			cursorHidden = true;
		}
	}

	private void showCursor() {
		if (cursorHidden) {
			SDLMouse.SDL_ShowCursor();
			cursorHidden = false;
		}
	}

	@Override
	public void tick() {
		ticks++;
		if (settledAt < 0 && card.result() != ScratchCardEntity.RUNNING) {
			settledAt = ticks;
			ClientScratch.endStroke();
		}
	}

	@Override
	public void onClose() {
		showCursor();
		ClientScratch.close();
		super.onClose();
	}

	@Override
	public void removed() {
		showCursor();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, this.width, 34, 0xA0000000, 0x00000000);
		graphics.fillGradient(0, this.height - 50, this.width, this.height, 0x00000000, 0xA0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		ScratchCardItem.Tier tier = ScratchCardItem.Tier.values()[Mth.clamp(card.tier(), 0, 2)];
		graphics.centeredText(this.font, Component.translatable("gamblecraft.scratch.title_tier",
				Component.translatable("gamblecraft.scratch.tier." + tier.key())), this.width / 2, 6, 0xFFFFE08A);
		graphics.centeredText(this.font, VillageRegistry.name(card.village()), this.width / 2, 18, 0xFFDDDDDD);
		int result = card.result();
		if (result == ScratchCardEntity.RUNNING) {
			graphics.centeredText(this.font, Component.translatable("gamblecraft.scratch.instructions"), this.width / 2, this.height - 46, 0xFFEEEEEE);
			return;
		}
		Component status = result >= 0
				? Component.translatable("gamblecraft.scratch.won", ScratchCards.Prize.values()[result].multiplier * tier.price)
				: Component.translatable("gamblecraft.scratch.lost");
		float age = ticks - Math.max(0, settledAt) + partialTick;
		float pop = age < 6 ? 0.6F + age / 6.0F * 1.4F : 2.0F - Math.min(0.4F, (age - 6) / 20.0F);
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.width / 2.0F, this.height / 2.0F - 30);
		graphics.pose().scale(pop, pop);
		graphics.centeredText(this.font, status, 0, -4, result >= 0 ? 0xFF55FF55 : 0xFFFF6666);
		graphics.pose().popMatrix();
	}
}
