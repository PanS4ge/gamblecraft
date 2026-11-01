package pl.pansage.gamblecraft.client.table;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.core.casino.Chips;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.item.ChipItem;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * What a table's currency id looks like on this side: a village item, or casino chips (which are not one item but
 * six denominations, so a stake shows as the biggest chip it contains and the balance adds their values up).
 */
public final class ClientCurrency {
	private ClientCurrency() {
	}

	public static boolean isChips(String currency) {
		return ChipCurrency.ID.equals(currency);
	}

	/** One item standing for {@code amount} of the currency. */
	public static ItemStack stack(String currency, long amount) {
		if (isChips(currency)) {
			int best = 0;
			for (int i = 0; i < Chips.VALUES.length; i++) {
				if (Chips.VALUES[i] <= Math.max(1, amount)) {
					best = i;
				}
			}
			return new ItemStack(ModItems.CHIPS[best]);
		}
		Identifier id = Identifier.tryParse(currency);
		return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id));
	}

	public static long balance(Player player, String currency) {
		boolean chips = isChips(currency);
		Item item = chips ? null : stack(currency, 1).getItem();
		long total = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (chips && stack.getItem() instanceof ChipItem chip) {
				total += (long) chip.value() * stack.getCount();
			} else if (!chips && stack.is(item) && stack.getComponentsPatch().isEmpty()) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
