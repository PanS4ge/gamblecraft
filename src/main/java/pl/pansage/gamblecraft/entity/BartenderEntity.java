package pl.pansage.gamblecraft.entity;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import pl.pansage.gamblecraft.game.ChipShop;
import pl.pansage.gamblecraft.item.Drinks;

/** The barman behind the lobby bar: drinks for chips. */
public class BartenderEntity extends CasinoNpc implements ChipShop.Keeper {
	public BartenderEntity(EntityType<? extends BartenderEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new StayHomeGoal(1));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			ChipShop.open(serverPlayer, this);
			getLookControl().setLookAt(player);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component shopTitle() {
		return Component.translatable("gamblecraft.bar.title");
	}

	@Override
	public List<ChipShop.Offer> offers(ServerPlayer player) {
		return Drinks.offers(player);
	}

	@Override
	public void choose(ServerPlayer player, int index) {
		if (Drinks.sell(player, index)) {
			gesture(GESTURE_DEAL);
		}
	}
}
