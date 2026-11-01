package pl.pansage.gamblecraft.item;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.registry.ModComponents;
import pl.pansage.gamblecraft.registry.ModItems;

/** One playing card. The face is the {@link ModComponents#CARD} component; the model is picked by custom model data. */
public class PlayingCardItem extends Item {
	public PlayingCardItem(Properties properties) {
		super(properties);
	}

	public static ItemStack stackOf(Card card) {
		ItemStack stack = new ItemStack(ModItems.CARD);
		stack.set(ModComponents.CARD, card.id());
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(card.code()), List.of()));
		return stack;
	}

	/** The card on this stack, or null for a blank card item. */
	public static Card cardOf(ItemStack stack) {
		Integer id = stack.get(ModComponents.CARD);
		return id == null ? null : Card.of(id);
	}

	public static Component cardName(Card card) {
		return Component.translatable("gamblecraft.card.name",
				Component.translatable("gamblecraft.rank." + card.rank().code()),
				Component.translatable("gamblecraft.suit." + card.suit().code()));
	}

	@Override
	public Component getName(ItemStack stack) {
		Card card = cardOf(stack);
		return card == null ? super.getName(stack) : cardName(card);
	}
}
