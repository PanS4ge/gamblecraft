package pl.pansage.gamblecraft.game;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.core.casino.Chips;
import pl.pansage.gamblecraft.item.ChipItem;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * Casino chips as stakes: a unit is one chip of value 1, whatever denominations the player holds. Paying takes
 * every chip and gives back the change in the fewest chips, like a croupier colouring up.
 */
public final class ChipCurrency implements Currency {
	public static final ChipCurrency INSTANCE = new ChipCurrency();
	public static final String ID = "gamblecraft:chips";
	/** Chips per emerald, as the cashier counts them. */
	public static final int PER_EMERALD = 10;

	private ChipCurrency() {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(ModItems.CHIPS[3]);
	}

	@Override
	public Component name() {
		return Component.translatable("gamblecraft.chip.name");
	}

	@Override
	public double unitValue() {
		return 1.0 / PER_EMERALD;
	}

	private static int valueOf(ItemStack stack) {
		return stack.getItem() instanceof ChipItem chip ? chip.value() : 0;
	}

	@Override
	public long balance(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		long total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			total += (long) valueOf(stack) * stack.getCount();
		}
		return total;
	}

	@Override
	public boolean withdraw(ServerPlayer player, long amount) {
		if (amount <= 0) {
			return amount == 0;
		}
		long balance = balance(player);
		if (balance < amount) {
			return false;
		}
		// Small chips first; only break a bigger one when the small ones run out.
		Inventory inventory = player.getInventory();
		long left = amount;
		for (int d = 0; d < Chips.VALUES.length && left > 0; d++) {
			for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
				ItemStack stack = inventory.getItem(i);
				if (valueOf(stack) == Chips.VALUES[d]) {
					int take = (int) Math.min(stack.getCount(), (left + Chips.VALUES[d] - 1) / Chips.VALUES[d]);
					stack.shrink(take);
					left -= (long) take * Chips.VALUES[d];
				}
			}
		}
		inventory.setChanged();
		if (left < 0) {
			deposit(player, -left);
		}
		return true;
	}

	@Override
	public void deposit(ServerPlayer player, long amount) {
		for (ItemStack stack : asStacks(amount)) {
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
	}

	@Override
	public List<ItemStack> asStacks(long amount) {
		List<ItemStack> stacks = new ArrayList<>();
		long[] counts = Chips.breakdown(amount);
		for (int d = counts.length - 1; d >= 0; d--) {
			long n = counts[d];
			while (n > 0) {
				int take = (int) Math.min(64, n);
				stacks.add(new ItemStack(ModItems.CHIPS[d], take));
				n -= take;
			}
		}
		return stacks;
	}
}
