package pl.pansage.gamblecraft.game;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Plain items as stakes (village dealers): one unit is one item without custom data. */
public record ItemCurrency(Item item, double unitValue) implements Currency {
	@Override
	public String id() {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(item);
	}

	@Override
	public Component name() {
		return item.getName(new ItemStack(item));
	}

	private boolean matches(ItemStack stack) {
		return stack.is(item) && stack.getComponentsPatch().isEmpty();
	}

	@Override
	public long balance(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		long total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	@Override
	public boolean withdraw(ServerPlayer player, long amount) {
		if (amount <= 0) {
			return amount == 0;
		}
		if (balance(player) < amount) {
			return false;
		}
		Inventory inventory = player.getInventory();
		long left = amount;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (matches(stack)) {
				int take = (int) Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inventory.setChanged();
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
		int max = new ItemStack(item).getMaxStackSize();
		while (amount > 0) {
			int n = (int) Math.min(max, amount);
			stacks.add(new ItemStack(item, n));
			amount -= n;
		}
		return stacks;
	}
}
