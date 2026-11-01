package pl.pansage.gamblecraft.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import pl.pansage.gamblecraft.client.config.ClientConfig;

/** 18+ notice shown instead of the title screen until the player accepts. Deny quits the game. */
public class AgeGateScreen extends Screen {
	private static final int TEXT_WIDTH = 320;
	private static final Component BODY = Component.translatable("gamblecraft.age_gate.body");
	private static final Component HELP = Component.translatable("gamblecraft.age_gate.help");

	public AgeGateScreen() {
		super(Component.translatable("gamblecraft.age_gate.title"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int buttonY = this.height / 2 + 50;
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.age_gate.accept"), button -> accept())
				.bounds(centerX - 155, buttonY, 150, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.age_gate.deny"), button -> this.minecraft.stop())
				.bounds(centerX + 5, buttonY, 150, 20)
				.build());
	}

	private void accept() {
		ClientConfig config = ClientConfig.get();
		config.ageAccepted = true;
		config.save();
		this.minecraft.gui.setScreen(new TitleScreen());
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int centerX = this.width / 2;
		int top = this.height / 2 - 80;
		int left = centerX - TEXT_WIDTH / 2;

		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, top);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.centeredText(this.font, this.title, 0, 0, 0xFFFF5555);
		graphics.pose().popMatrix();

		int y = graphics.textWithWordWrap(this.font, BODY, left, top + 32, TEXT_WIDTH, 0xFFE8E8E8);
		graphics.textWithWordWrap(this.font, HELP, left, y + 8, TEXT_WIDTH, 0xFFAAAAAA);
	}
}
