package pl.pansage.gamblecraft.block;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.registry.ModItems;

/** The door of the VIP room: a suit is not enough, it opens only for a VIP card holder. */
public class VipDoorBlock extends CasinoDoorBlock {
	public VipDoorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected String refusal(Player player) {
		String shut = super.refusal(player);
		if (shut != null || player.isCreative()) {
			return shut;
		}
		return player.getInventory().countItem(ModItems.VIP_CARD) > 0 ? null : "gamblecraft.vip.members_only";
	}
}
