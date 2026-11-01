package pl.pansage.gamblecraft.client.screen;

import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.client.table.ClientPick;
import pl.pansage.gamblecraft.network.TablePayloads.Accepted;
import pl.pansage.gamblecraft.network.TablePayloads.DealerStart;
import pl.pansage.gamblecraft.network.TablePayloads.OpenDealer;

/** The village dealer's pitch: pick a game and what to bet with (only what this village trades in). */
public class DealerScreen extends Screen {
	private final OpenDealer offer;
	private String game;
	private String item;

	public DealerScreen(OpenDealer offer) {
		super(Component.translatable("entity.gamblecraft.street_dealer"));
		this.offer = offer;
		this.game = offer.games().isEmpty() ? null : offer.games().getFirst();
	}

	private static Item itemOf(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? null : BuiltInRegistries.ITEM.getValue(key);
	}

	private long owned(Item item) {
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
		if (item == null) {
			for (Accepted a : offer.accepted()) {
				Item it = itemOf(a.item());
				if (it != null && owned(it) > 0) {
					item = a.item();
					break;
				}
			}
			if (item == null && !offer.accepted().isEmpty()) {
				item = offer.accepted().getFirst().item();
			}
		}
		int cols = 4;
		int bw = 92;
		int x0 = this.width / 2 - (cols * (bw + 4)) / 2;
		int y = 40;
		List<String> games = offer.games();
		for (int i = 0; i < games.size(); i++) {
			String id = games.get(i);
			Component label = Component.translatable("gamblecraft.game." + id);
			if (id.equals(game)) {
				label = label.copy().withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.UNDERLINE);
			}
			Button button = Button.builder(label, b -> {
				game = id;
				rebuildWidgets();
			}).bounds(x0 + (i % cols) * (bw + 4), y + (i / cols) * 24, bw, 20).build();
			button.setTooltip(Tooltip.create(Component.translatable("gamblecraft.game." + id + ".help")));
			addRenderableWidget(button);
		}
		addRenderableWidget(Button.builder(Component.translatable("gamblecraft.dealer.deal"), b -> start())
				.bounds(this.width / 2 - 60, this.height - 34, 120, 20).build());
	}

	private void start() {
		if (game == null || item == null) {
			return;
		}
		ClientPlayNetworking.send(new DealerStart(offer.dealerId(), game, item));
		ClientPick.arm();
		onClose();
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		int[] layout = itemsLayout();
		for (int i = 0; i < offer.accepted().size(); i++) {
			int x = layout[0] + (i % layout[2]) * 24;
			int y = layout[1] + (i / layout[2]) * 24;
			if (event.x() >= x && event.x() < x + 20 && event.y() >= y && event.y() < y + 20) {
				item = offer.accepted().get(i).item();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** x, y, columns of the stake item grid. */
	private int[] itemsLayout() {
		int cols = Math.min(12, Math.max(1, offer.accepted().size()));
		return new int[] {this.width / 2 - cols * 12, 110, cols};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFE08A);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.dealer.purse", String.format(Locale.ROOT, "%.1f", offer.purse())),
				this.width / 2, 24, 0xFFAAFFAA);
		graphics.centeredText(this.font, Component.translatable("gamblecraft.dealer.stake"), this.width / 2, 98, 0xFFFFFFFF);
		int[] layout = itemsLayout();
		Accepted hovered = null;
		for (int i = 0; i < offer.accepted().size(); i++) {
			Accepted a = offer.accepted().get(i);
			Item it = itemOf(a.item());
			if (it == null) {
				continue;
			}
			int x = layout[0] + (i % layout[2]) * 24;
			int y = layout[1] + (i / layout[2]) * 24;
			boolean selected = a.item().equals(item);
			graphics.fill(x - 1, y - 1, x + 19, y + 19, selected ? 0xFFFFD700 : 0x80000000);
			graphics.fill(x, y, x + 18, y + 18, 0xFF3A3A3A);
			ItemStack stack = new ItemStack(it);
			graphics.item(stack, x + 1, y + 1);
			long have = owned(it);
			graphics.itemDecorations(this.font, stack, x + 1, y + 1, have > 0 ? String.valueOf(have) : "0");
			if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
				hovered = a;
			}
		}
		Accepted info = hovered;
		if (info == null) {
			for (Accepted a : offer.accepted()) {
				if (a.item().equals(item)) {
					info = a;
				}
			}
		}
		if (info != null && itemOf(info.item()) != null) {
			Item it = itemOf(info.item());
			Component line = Component.translatable("gamblecraft.dealer.value", it.getName(new ItemStack(it)),
					String.format(Locale.ROOT, "%.2f", info.value()));
			graphics.centeredText(this.font, line, this.width / 2, this.height - 50, 0xFFDDDDDD);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
