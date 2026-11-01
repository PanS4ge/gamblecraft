package pl.pansage.gamblecraft.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * Stealing scratch cards: sneak up behind a villager with an empty hand and use it. Sleeping villagers are easy
 * marks, walking ones are hard. Caught, the villager complains, remembers it and nearby iron golems step in.
 * Each villager can be tried once a day.
 */
public final class Pickpocket {
	private static final long COOLDOWN = 24000L;
	private static final Map<UUID, Long> LAST_TRY = new HashMap<>();

	private Pickpocket() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
					|| !(entity instanceof Villager villager) || villager.isBaby()) {
				return InteractionResult.PASS;
			}
			if (!behind(villager, player.position())) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer && AgeGate.require(serverPlayer)) {
				attempt(serverPlayer, villager);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** True if {@code from} is more than 110 degrees away from where the villager's body faces. */
	public static boolean behind(Villager villager, Vec3 from) {
		Vec3 toPlayer = from.subtract(villager.position());
		float bodyYaw = villager.yBodyRot;
		Vec3 facing = new Vec3(-Mth.sin(bodyYaw * Mth.DEG_TO_RAD), 0, Mth.cos(bodyYaw * Mth.DEG_TO_RAD));
		Vec3 flat = new Vec3(toPlayer.x, 0, toPlayer.z).normalize();
		return facing.dot(flat) < Math.cos(Math.toRadians(110));
	}

	private static void attempt(ServerPlayer player, Villager villager) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST_TRY.get(villager.getUUID());
		if (last != null && now - last < COOLDOWN) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.pickpocket.empty"));
			return;
		}
		LAST_TRY.put(villager.getUUID(), now);
		float chance = 0.4F;
		if (villager.isSleeping()) {
			chance += 0.35F;
		}
		if (villager.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
			chance -= 0.2F;
		}
		if (level.getRandom().nextFloat() < chance) {
			float roll = level.getRandom().nextFloat();
			Item tier = roll < 0.03F ? ModItems.SCRATCH_CARD_GOLD : roll < 0.25F ? ModItems.SCRATCH_CARD_SILVER : ModItems.SCRATCH_CARD_BRONZE;
			var card = new net.minecraft.world.item.ItemStack(tier);
			ScratchCardItem.tryBind(card, level, villager.blockPosition());
			if (!player.getInventory().add(card)) {
				player.drop(card, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			player.sendOverlayMessage(Component.translatable("gamblecraft.pickpocket.success"));
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.3F, 1.8F);
			return;
		}
		player.sendOverlayMessage(Component.translatable("gamblecraft.pickpocket.caught"));
		villager.setUnhappyCounter(60);
		villager.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, 25);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.2F, 0.8F);
		if (!player.isCreative()) {
			for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(16))) {
				golem.setTarget(player);
			}
		}
	}
}
