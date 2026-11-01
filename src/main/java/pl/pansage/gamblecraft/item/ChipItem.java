package pl.pansage.gamblecraft.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** One casino chip of a fixed value. Chips are bought and cashed out at the cashier and staked at casino tables. */
public class ChipItem extends Item {
	private final int value;

	public ChipItem(int value, Properties properties) {
		super(properties);
		this.value = value;
	}

	public int value() {
		return value;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable("gamblecraft.chip.value", value).withStyle(ChatFormatting.GOLD));
		if (stack.getCount() > 1) {
			lines.accept(Component.translatable("gamblecraft.chip.stack_value", (long) value * stack.getCount()).withStyle(ChatFormatting.GRAY));
		}
	}
}
