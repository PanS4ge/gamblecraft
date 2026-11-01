package pl.pansage.gamblecraft.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import pl.pansage.gamblecraft.game.ChipShop;
import pl.pansage.gamblecraft.item.Drinks;

/** A waitress doing the rounds of the hall with her tray: she stops by the players and takes their orders. */
public class WaitressEntity extends CasinoNpc implements ChipShop.Keeper {
	private static final long OFFER_AGAIN = 1200;
	private final Map<UUID, Long> offered = new HashMap<>();

	public WaitressEntity(EntityType<? extends WaitressEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(5, new StayHomeGoal(14));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.45));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level) || tickCount % 40 != 0) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(6))) {
			Long last = offered.get(player.getUUID());
			if (player.isSpectator() || last != null && now - last < OFFER_AGAIN) {
				continue;
			}
			offered.put(player.getUUID(), now);
			getNavigation().moveTo(player, 0.6);
			getLookControl().setLookAt(player);
			player.sendOverlayMessage(Component.translatable("gamblecraft.waitress.offer"));
			gesture(GESTURE_DEAL);
			break;
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			getNavigation().stop();
			ChipShop.open(serverPlayer, this);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component shopTitle() {
		return Component.translatable("gamblecraft.waitress.title");
	}

	@Override
	public List<ChipShop.Offer> offers(ServerPlayer player) {
		return Drinks.offers(player);
	}

	@Override
	public void choose(ServerPlayer player, int index) {
		if (Drinks.sell(player, index)) {
			gesture(GESTURE_CHEER);
		}
	}
}
