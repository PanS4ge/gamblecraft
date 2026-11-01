package pl.pansage.gamblecraft.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import pl.pansage.gamblecraft.core.table.Money;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ItemCurrency;
import pl.pansage.gamblecraft.game.VillageEconomy;
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.ScratchData;
import pl.pansage.gamblecraft.network.ScratchPayloads.ExchangeAction;
import pl.pansage.gamblecraft.network.ScratchPayloads.OpenExchange;
import pl.pansage.gamblecraft.network.TablePayloads.Accepted;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * The lottery clerk ("Kolekturzystka") of a village exchange: pays out scratch cards issued by her village only,
 * sells new cards, and buys the village's goods for emeralds a little below their trading value.
 */
public class LotteryClerkEntity extends CasinoNpc {
	public static final double EXCHANGE_RATE = 0.85;
	private static final Item[] CARDS = {ModItems.SCRATCH_CARD_BRONZE, ModItems.SCRATCH_CARD_SILVER, ModItems.SCRATCH_CARD_GOLD};

	public LotteryClerkEntity(EntityType<? extends LotteryClerkEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new StayHomeGoal(2));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	/** The bell of the village this clerk works for. */
	public Optional<BlockPos> villageBell(ServerLevel level) {
		return VillageRegistry.nearestBell(level, home != null ? home : blockPosition());
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!AgeGate.require(serverPlayer)) {
			return InteractionResult.CONSUME;
		}
		ItemStack held = player.getMainHandItem();
		if (held.getItem() instanceof ScratchCardItem && ScratchCardItem.data(held) != null && ScratchCardItem.data(held).scratched()) {
			redeem(serverPlayer, held);
		} else {
			openExchange(serverPlayer);
		}
		getLookControl().setLookAt(player);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Pays a scratched winning card issued by this village. */
	public void redeem(ServerPlayer player, ItemStack card) {
		ServerLevel level = player.level();
		ScratchData data = ScratchCardItem.data(card);
		ScratchCardItem.Tier tier = ((ScratchCardItem) card.getItem()).tier();
		if (data.claimed()) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.scratch.claimed"));
			return;
		}
		if (data.casino()) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.clerk.casino_card"));
			return;
		}
		if (data.multiplier() <= 0) {
			card.shrink(1);
			player.sendOverlayMessage(Component.translatable("gamblecraft.clerk.no_win"));
			level.playSound(null, blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1, 1);
			return;
		}
		Optional<BlockPos> bell = villageBell(level);
		if (bell.isEmpty() || bell.get().asLong() != data.bell()) {
			player.sendSystemMessage(Component.translatable("gamblecraft.clerk.wrong_village", VillageRegistry.name(data.name()),
					VillageRegistry.direction(blockPosition(), data.bellPos())));
			gesture(GESTURE_SULK);
			level.playSound(null, blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1, 1);
			return;
		}
		int prize = data.multiplier() * tier.price;
		card.shrink(1);
		new ItemCurrency(Items.EMERALD, 1).deposit(player, prize);
		player.sendSystemMessage(Component.translatable("gamblecraft.clerk.paid", prize));
		gesture(GESTURE_CHEER);
		level.playSound(null, blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1, 1);
		level.playSound(null, player.blockPosition(), prize >= tier.price * 10 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
				SoundSource.PLAYERS, 0.8F, 1.1F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.6, getZ(), 12, 0.5, 0.4, 0.5, 0.05);
	}

	public void openExchange(ServerPlayer player) {
		ServerLevel level = player.level();
		List<Accepted> goods = new ArrayList<>();
		for (Map.Entry<Item, Double> entry : VillageEconomy.scan(level, position()).entrySet()) {
			if (entry.getKey() != Items.EMERALD) {
				goods.add(new Accepted(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue()));
			}
		}
		int village = villageBell(level).map(VillageRegistry::nameIndex).orElse(-1);
		List<Integer> prices = new ArrayList<>();
		for (ScratchCardItem.Tier tier : ScratchCardItem.Tier.values()) {
			prices.add(tier.price);
		}
		ServerPlayNetworking.send(player, new OpenExchange(getId(), village, goods, prices, EXCHANGE_RATE));
	}

	public void handle(ServerPlayer player, ExchangeAction action) {
		if (!AgeGate.require(player) || player.distanceTo(this) > 8) {
			return;
		}
		ServerLevel level = player.level();
		ItemCurrency emeralds = new ItemCurrency(Items.EMERALD, 1);
		if (action.buyCard()) {
			int tier = action.count();
			Optional<BlockPos> bell = villageBell(level);
			if (tier < 0 || tier >= CARDS.length || bell.isEmpty()) {
				return;
			}
			int price = ScratchCardItem.Tier.values()[tier].price;
			if (!emeralds.withdraw(player, price)) {
				player.sendOverlayMessage(Component.translatable("gamblecraft.table.cannot_afford", Items.EMERALD.getName(new ItemStack(Items.EMERALD))));
				return;
			}
			ItemStack card = ScratchCardItem.issued(CARDS[tier], bell.get(), level.getRandom().nextLong());
			if (!player.getInventory().add(card)) {
				spawnAtLocation(level, card);
			}
			level.playSound(null, blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1, 1);
			return;
		}
		Identifier id = Identifier.tryParse(action.item());
		Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		Double value = item == null ? null : VillageEconomy.scan(level, position()).get(item);
		if (value == null || item == Items.EMERALD || action.count() <= 0) {
			return;
		}
		ItemCurrency goods = new ItemCurrency(item, value);
		int count = (int) Math.min(action.count(), goods.balance(player));
		if (count <= 0 || !goods.withdraw(player, count)) {
			return;
		}
		long paid = Money.scale(count, value * EXCHANGE_RATE, new java.util.SplittableRandom(level.getRandom().nextLong()));
		emeralds.deposit(player, paid);
		player.sendOverlayMessage(Component.translatable("gamblecraft.clerk.sold", count, item.getName(new ItemStack(item)), paid));
		level.playSound(null, blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1, 1);
	}
}
