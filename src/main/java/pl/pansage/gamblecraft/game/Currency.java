package pl.pansage.gamblecraft.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** What a table plays for: whole units the player gives and receives (items or chips). */
public interface Currency {
	/** Stable id sent to clients, e.g. {@code minecraft:wheat}. */
	String id();

	ItemStack icon();

	Component name();

	/** Value of one unit in emeralds (village prices); used by dealer purses. */
	double unitValue();

	long balance(ServerPlayer player);

	/** Takes {@code amount} units from the player; false (and nothing taken) if they do not have enough. */
	boolean withdraw(ServerPlayer player, long amount);

	/** Gives units to the player (or their payout mailbox if they are gone). */
	void deposit(ServerPlayer player, long amount);

	/** The units as item stacks, for mailboxes and drops. */
	java.util.List<ItemStack> asStacks(long amount);
}
