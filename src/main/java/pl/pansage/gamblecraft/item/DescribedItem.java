package pl.pansage.gamblecraft.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An item with one line of description under its name ({@code <description id>.desc}). */
public class DescribedItem extends Item {
	public DescribedItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
	}
}
