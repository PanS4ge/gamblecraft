package pl.pansage.gamblecraft.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.client.scratch.ClientScratch;
import pl.pansage.gamblecraft.client.table.TableCamera;
import pl.pansage.gamblecraft.core.casino.ScratchCards;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.network.ScratchPayloads.ScratchCommand;

/**
 * The panel view of a card lying on a block: the same card drawn big in the middle of the screen. Rubbing here
 * rubs the card in the world, so players around still see the foil come off. Silver dust flies on the panel too.
 */
public class ScratchScreen extends Screen {
	private static final int MAX_CELL = ScratchFoil.CELL;
	private static final int MIN_CELL = 28;
	private static final int BUTTONS = 24;
	private static final int[] TIER_COLORS = {0xFF8C5A2E, 0xFF8E99A4, 0xFFC9A227};

	private final ScratchCardEntity card;
	private final List<float[]> dust = new ArrayList<>();
	private final Random random = new Random();
	private int lastResult = ScratchCardEntity.RUNNING;
	private long finishedAt = -1;
	private int ticks;
	/** Cell size in GUI pixels, shrunk on short screens so the card and its buttons always fit. */
	private int cell = MAX_CELL;

	public ScratchScreen(ScratchCardEntity card) {
		super(Component.translatable("gamblecraft.scratch.title"));
		this.card = card;
	}

	/** GUI pixels per grid unit. */
	private float scale() {
		return cell / (float) ScratchFoil.CELL;
	}

	private int gridSize() {
		return Math.round(ScratchFoil.SIZE * scale());
	}

	private int panelW() {
		return gridSize() + 28;
	}

	private int panelH() {
		return gridSize() + 74;
	}

	private int panelTop() {
		return (this.height - panelH() - BUTTONS) / 2;
	}

	private int gridX() {
		return (this.width - gridSize()) / 2;
	}

	private int gridY() {
		return panelTop() + 40;
	}

	@Override
	protected void init() {
		cell = Mth.clamp((int) ((this.height - BUTTONS - 74 - 8) * ScratchFoil.CELL / (float) ScratchFoil.SIZE), MIN_CELL, MAX_CELL);
		int bottom = panelTop() + panelH() + 4;
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.scratch.reveal_all"), b -> revealAll())
				.bounds(this.width / 2 - 154, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.scratch.view_block"), b -> toBlock())
				.bounds(this.width / 2 - 50, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.scratch.done"), b -> onClose())
				.bounds(this.width / 2 + 54, bottom, 100, 20).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public void revealAll() {
		if (card.result() == ScratchCardEntity.RUNNING) {
			List<float[]> flakes = new ArrayList<>();
			card.foil().revealAll((x, y) -> flakes.add(new float[] {x, y}));
			ClientScratch.effects(card, flakes);
			ClientPlayNetworking.send(new ScratchCommand(card.getId(), ScratchCommand.REVEAL_ALL));
		}
	}

	private void toBlock() {
		ClientConfig config = ClientConfig.get();
		config.scratchPanel = false;
		config.save();
		if (config.overheadCamera) {
			TableCamera.activate(card);
		}
		this.minecraft.gui.setScreen(new ScratchTableScreen(card));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && overGrid(event.x(), event.y())) {
			ClientScratch.beginStroke();
			scratch(event.x(), event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (ClientScratch.isStroking()) {
			scratch(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		ClientScratch.endStroke();
		return super.mouseReleased(event);
	}

	private boolean overGrid(double x, double y) {
		int size = gridSize();
		return x >= gridX() - 6 && y >= gridY() - 6 && x <= gridX() + size + 6 && y <= gridY() + size + 6;
	}

	/** Rubs the card at a screen point; the dust that comes off flies across the panel. */
	private void scratch(double x, double y) {
		float s = scale();
		ClientScratch.strokeTo((x - gridX()) / s, (y - gridY()) / s, (fx, fy) -> {
			if (dust.size() < 300 && random.nextInt(3) == 0) {
				dust.add(new float[] {gridX() + fx * s, gridY() + fy * s, (random.nextFloat() - 0.5F) * 2, -random.nextFloat() * 1.5F, 0});
			}
		});
	}

	@Override
	public void tick() {
		ticks++;
		dust.removeIf(d -> d[1] > this.height);
		for (float[] d : dust) {
			d[0] += d[2];
			d[1] += d[3];
			d[3] += 0.25F;
		}
		int result = card.result();
		if (result != lastResult) {
			lastResult = result;
			finishedAt = ticks;
			ClientScratch.endStroke();
			if (result >= 0) {
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F));
				for (int i = 0; i < 80; i++) {
					dust.add(new float[] {this.width / 2.0F, this.height / 2.0F, (random.nextFloat() - 0.5F) * 8, -random.nextFloat() * 6, 1 + random.nextInt(5)});
				}
			}
		}
	}

	@Override
	public void onClose() {
		ClientScratch.close();
		super.onClose();
	}

	public boolean isFinished() {
		return card.result() != ScratchCardEntity.RUNNING;
	}

	public boolean isRevealed(int cell) {
		return card.foil().isRevealed(cell);
	}

	/** Grid position and size in GUI pixels: x, y, size, cell. */
	public int[] gridBounds() {
		return new int[] {gridX(), gridY(), gridSize(), cell};
	}

	private static Identifier symbol(int prize) {
		return GambleCraft.id("textures/gui/scratch/" + ScratchCards.Prize.values()[prize].key() + ".png");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		ScratchCardItem.Tier tier = ScratchCardItem.Tier.values()[Mth.clamp(card.tier(), 0, 2)];
		int color = TIER_COLORS[tier.ordinal()];
		int pw = panelW();
		int ph = panelH();
		int left = (this.width - pw) / 2;
		int top = panelTop();
		graphics.fill(left - 3, top - 3, left + pw + 3, top + ph + 3, 0xFF222222);
		graphics.fill(left, top, left + pw, top + ph, color);
		graphics.fill(left + 4, top + 4, left + pw - 4, top + ph - 4, 0xFFF4EBD0);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.scratch.title_tier", Component.translatable("gamblecraft.scratch.tier." + tier.key())),
				this.width / 2, top + 9, color);
		graphics.centeredText(this.font, VillageRegistry.name(card.village()), this.width / 2, top + 21, 0xFF5A4A2A);

		int[] cells = card.cells();
		int result = card.result();
		ScratchFoil foil = card.foil();
		float s = scale();
		int gx = gridX();
		int gy = gridY();
		int icon = this.cell - 14;
		for (int c = 0; c < cells.length && c < 9; c++) {
			int cx = gx + Math.round(ScratchFoil.cellX(c) * s);
			int cy = gy + Math.round(ScratchFoil.cellY(c) * s);
			int prize = cells[c];
			if (result >= 0 && prize == result) {
				float pulse = 0.5F + 0.5F * Mth.sin((ticks + partialTick) * 0.4F);
				graphics.fill(cx - 3, cy - 3, cx + this.cell + 3, cy + this.cell + 3, 0xFF000000 | (int) (200 + 55 * pulse) << 16 | (int) (160 + 60 * pulse) << 8);
			}
			graphics.fill(cx, cy, cx + this.cell, cy + this.cell, 0xFFFFFFFF);
			graphics.blit(RenderPipelines.GUI_TEXTURED, symbol(prize), cx + 7, cy + 3, 0.0F, 0.0F, icon, icon, 16, 16, 16, 16);
			int value = ScratchCards.Prize.values()[prize].multiplier * tier.price;
			graphics.centeredText(this.font, Component.literal(String.format(Locale.ROOT, "%d", value)), cx + this.cell / 2, cy + this.cell - 10, 0xFF3A6A2A);
			if (foil.isRevealed(c)) {
				continue;
			}
			float sub = this.cell / (float) ScratchFoil.SUB;
			for (int i = 0; i < ScratchFoil.SUB * ScratchFoil.SUB; i++) {
				if (foil.covered(c, i)) {
					int x0 = cx + (int) (i % ScratchFoil.SUB * sub);
					int y0 = cy + (int) (i / ScratchFoil.SUB * sub);
					int shade = 0xB8 + ((i * 7 + c * 13) % 5) * 8;
					graphics.fill(x0, y0, cx + (int) ((i % ScratchFoil.SUB + 1) * sub), cy + (int) ((i / ScratchFoil.SUB + 1) * sub),
							0xFF000000 | shade << 16 | shade << 8 | (shade + 8));
				}
			}
		}
		for (float[] d : dust) {
			int c = d[4] == 0 ? 0xFFC8C8D0 : new int[] {0xFFFF5555, 0xFF55FF55, 0xFF5599FF, 0xFFFFDD33, 0xFFFF77FF}[(int) d[4] - 1];
			graphics.fill((int) d[0], (int) d[1], (int) d[0] + 2, (int) d[1] + 2, c);
		}
		int footer = top + ph - 16;
		Component status;
		int statusColor;
		if (result == ScratchCardEntity.RUNNING) {
			status = Component.translatable("gamblecraft.scratch.instructions");
			statusColor = 0xFF5A4A2A;
		} else if (result >= 0) {
			status = Component.translatable("gamblecraft.scratch.won", ScratchCards.Prize.values()[result].multiplier * tier.price);
			statusColor = 0xFF1E8C1E;
		} else {
			status = Component.translatable("gamblecraft.scratch.lost");
			statusColor = 0xFFAA2222;
		}
		if (result >= 0) {
			float age = ticks - finishedAt + partialTick;
			float pop = age < 6 ? 0.6F + age / 6.0F * 0.7F : 1.3F - Math.min(0.3F, (age - 6) / 20.0F);
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.width / 2.0F, footer + 4);
			graphics.pose().scale(pop, pop);
			graphics.centeredText(this.font, status, 0, -4, statusColor);
			graphics.pose().popMatrix();
		} else {
			List<FormattedCharSequence> lines = this.font.split(status, pw - 16);
			for (int i = 0; i < lines.size(); i++) {
				graphics.centeredText(this.font, lines.get(i), this.width / 2, footer - (lines.size() - 1 - i) * 10, statusColor);
			}
		}
	}
}
