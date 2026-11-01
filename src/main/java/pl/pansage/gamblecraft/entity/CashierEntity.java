package pl.pansage.gamblecraft.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import pl.pansage.gamblecraft.core.casino.Chips;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.ItemCurrency;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.ScratchData;
import pl.pansage.gamblecraft.network.CasinoPayloads.CashierAction;
import pl.pansage.gamblecraft.network.CasinoPayloads.OpenCashier;
import pl.pansage.gamblecraft.network.CasinoPayloads.Rate;

/**
 * The casino cashier: sells chips for minerals at face value and cashes chips out into emeralds or diamonds,
 * keeping the house cut ({@link Chips#HOUSE_CUT}).
 */
public class CashierEntity extends CasinoNpc implements pl.pansage.gamblecraft.game.ChipShop.Keeper {
	/** Chips per mineral; a storage block is worth nine. */
	public static final Map<Item, Integer> RATES = rates();
	/** What chips can be cashed out into, and how many chips one is worth before the house cut. */
	public static final Map<Item, Integer> PAYOUTS = Map.of(Items.EMERALD, ChipCurrency.PER_EMERALD, Items.DIAMOND, 40);

	public CashierEntity(EntityType<? extends CashierEntity> type, Level level) {
		super(type, level);
	}

	private static Map<Item, Integer> rates() {
		Map<Item, Integer> rates = new LinkedHashMap<>();
		Item[] minerals = {Items.COPPER_INGOT, Items.LAPIS_LAZULI, Items.REDSTONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.EMERALD,
				Items.DIAMOND, Items.NETHERITE_INGOT};
		// the copper block is a collection of oxidation stages in 26.3; the bare one is plain "copper_block"
		Item[] blocks = {BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("copper_block")), Items.LAPIS_BLOCK, Items.REDSTONE_BLOCK,
				Items.IRON_BLOCK, Items.GOLD_BLOCK, Items.EMERALD_BLOCK, Items.DIAMOND_BLOCK, Items.NETHERITE_BLOCK};
		int[] chips = {1, 1, 1, 2, 5, 10, 40, 250};
		for (int i = 0; i < minerals.length; i++) {
			rates.put(minerals[i], chips[i]);
		}
		for (int i = 0; i < blocks.length; i++) {
			rates.put(blocks[i], chips[i] * 9);
		}
		return rates;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new StayHomeGoal(2));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
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
		ScratchData card = held.getItem() instanceof ScratchCardItem ? ScratchCardItem.data(held) : null;
		if (card != null && card.casino() && card.scratched()) {
			redeem(serverPlayer, held);
			return InteractionResult.SUCCESS_SERVER;
		}
		ServerPlayNetworking.send(serverPlayer, new OpenCashier(getId(), toRates(RATES), toRates(PAYOUTS), Chips.HOUSE_CUT));
		getLookControl().setLookAt(player);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static List<Rate> toRates(Map<Item, Integer> map) {
		List<Rate> rates = new ArrayList<>();
		map.forEach((item, chips) -> rates.add(new Rate(BuiltInRegistries.ITEM.getKey(item).toString(), chips)));
		return rates;
	}

	public void handle(ServerPlayer player, CashierAction action) {
		if (!AgeGate.require(player) || player.distanceTo(this) > 8 || action.count() == 0) {
			return;
		}
		Identifier id = Identifier.tryParse(action.item());
		Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		if (action.buy()) {
			buy(player, item, action.count());
		} else {
			cashOut(player, item, action.count());
		}
	}

	/** A scratched casino card: winners are paid in chips, losers go in the bin. */
	public void redeem(ServerPlayer player, ItemStack card) {
		ScratchData data = ScratchCardItem.data(card);
		ScratchCardItem.Tier tier = ((ScratchCardItem) card.getItem()).tier();
		card.shrink(1);
		if (data.multiplier() <= 0) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.clerk.no_win"));
			return;
		}
		long prize = (long) data.multiplier() * tier.chips();
		pl.pansage.gamblecraft.game.PlayerLedger.get(player.level().getServer()).won(player.getUUID(), prize);
		pl.pansage.gamblecraft.game.Celebrate.win(player.level(), player, prize, tier.chips(), player.position(), false);
		ChipCurrency.INSTANCE.deposit(player, prize);
		player.sendOverlayMessage(Component.translatable("gamblecraft.cashier.scratch_paid", prize));
		gesture(GESTURE_CHEER);
		clink(player);
	}

	// ---------------------------------------------------------------- the VIP desk (ChipShop): VIP card, pawn tickets

	public static final long VIP_PRICE = pl.pansage.gamblecraft.game.GambleConfig.get().vipCardPrice;

	@Override
	public Component shopTitle() {
		return Component.translatable("gamblecraft.cashier.desk");
	}

	@Override
	public List<pl.pansage.gamblecraft.game.ChipShop.Offer> offers(ServerPlayer player) {
		long turnover = pl.pansage.gamblecraft.game.PlayerLedger.get(player.level().getServer()).turnover(player.getUUID());
		boolean free = turnover >= pl.pansage.gamblecraft.game.PlayerLedger.VIP_TURNOVER;
		boolean has = player.getInventory().countItem(pl.pansage.gamblecraft.registry.ModItems.VIP_CARD) > 0;
		Component vip = free ? Component.translatable("gamblecraft.cashier.vip_free", turnover)
				: Component.translatable("gamblecraft.cashier.vip_buy", VIP_PRICE, turnover, pl.pansage.gamblecraft.game.PlayerLedger.VIP_TURNOVER);
		boolean ticket = player.getInventory().countItem(pl.pansage.gamblecraft.registry.ModItems.PAWN_TICKET) > 0;
		return List.of(
				new pl.pansage.gamblecraft.game.ChipShop.Offer(new ItemStack(pl.pansage.gamblecraft.registry.ModItems.VIP_CARD), vip,
						!has && (free || ChipCurrency.INSTANCE.balance(player) >= VIP_PRICE)),
				new pl.pansage.gamblecraft.game.ChipShop.Offer(new ItemStack(pl.pansage.gamblecraft.registry.ModItems.PAWN_TICKET),
						Component.translatable("gamblecraft.cashier.pawn"), ticket));
	}

	@Override
	public void choose(ServerPlayer player, int index) {
		if (index == 0) {
			sellVipCard(player);
		} else if (index == 1) {
			redeemPawnTicket(player);
		}
	}

	/** The VIP card: free once a player has staked {@link pl.pansage.gamblecraft.game.PlayerLedger#VIP_TURNOVER} chips, else for chips. */
	public boolean sellVipCard(ServerPlayer player) {
		long turnover = pl.pansage.gamblecraft.game.PlayerLedger.get(player.level().getServer()).turnover(player.getUUID());
		if (turnover < pl.pansage.gamblecraft.game.PlayerLedger.VIP_TURNOVER && !ChipCurrency.INSTANCE.withdraw(player, VIP_PRICE)) {
			return false;
		}
		ItemStack card = new ItemStack(pl.pansage.gamblecraft.registry.ModItems.VIP_CARD);
		card.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable("gamblecraft.cashier.vip_card_of", player.getName()));
		player.getInventory().placeItemBackInInventory(card, net.minecraft.util.Prediction.SERVER_ONLY);
		player.sendSystemMessage(Component.translatable("gamblecraft.cashier.vip_welcome").withStyle(net.minecraft.ChatFormatting.GOLD));
		gesture(GESTURE_CHEER);
		clink(player);
		return true;
	}

	/** A pawn ticket from the gambler: whatever he pawned, something valuable. */
	public ItemStack redeemPawnTicket(ServerPlayer player) {
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize() && slot < 0; i++) {
			if (player.getInventory().getItem(i).is(pl.pansage.gamblecraft.registry.ModItems.PAWN_TICKET)) {
				slot = i;
			}
		}
		if (slot < 0) {
			return ItemStack.EMPTY;
		}
		player.getInventory().getItem(slot).shrink(1);
		net.minecraft.util.RandomSource r = getRandom();
		ItemStack prize = switch (r.nextInt(6)) {
			case 0 -> new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3 + r.nextInt(4));
			case 1 -> new ItemStack(net.minecraft.world.item.Items.EMERALD_BLOCK, 2);
			case 2 -> new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 2);
			case 3 -> new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE);
			case 4 -> new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP, 1 + r.nextInt(2));
			default -> new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
		};
		player.sendSystemMessage(Component.translatable("gamblecraft.cashier.pawn_paid", prize.getHoverName(), prize.getCount())
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		ItemStack given = prize.copy();
		player.getInventory().placeItemBackInInventory(prize, net.minecraft.util.Prediction.SERVER_ONLY);
		clink(player);
		return given;
	}

	/** Minerals in, chips out at face value. */
	public void buy(ServerPlayer player, Item item, int count) {
		Integer rate = item == null ? null : RATES.get(item);
		if (rate == null || count <= 0) {
			return;
		}
		ItemCurrency minerals = new ItemCurrency(item, 1);
		long n = Math.min(count, minerals.balance(player));
		if (n <= 0 || !minerals.withdraw(player, n)) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.table.cannot_afford", item.getName(new ItemStack(item))));
			return;
		}
		long chips = n * rate;
		ChipCurrency.INSTANCE.deposit(player, chips);
		player.sendOverlayMessage(Component.translatable("gamblecraft.cashier.bought", chips, n, item.getName(new ItemStack(item))));
		clink(player);
	}

	/** Chips in, emeralds or diamonds out, less the house cut. {@code count < 0}: as many as the chips allow. */
	public void cashOut(ServerPlayer player, Item item, int count) {
		Integer perUnit = item == null ? null : PAYOUTS.get(item);
		if (perUnit == null) {
			return;
		}
		ChipCurrency chips = ChipCurrency.INSTANCE;
		long most = Chips.payout(chips.balance(player), perUnit);
		long units = count < 0 ? most : Math.min(count, most);
		if (units <= 0) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.cashier.not_enough", Chips.cost(1, perUnit)));
			return;
		}
		long cost = Chips.cost(units, perUnit);
		if (!chips.withdraw(player, cost)) {
			return;
		}
		new ItemCurrency(item, 1).deposit(player, units);
		player.sendOverlayMessage(Component.translatable("gamblecraft.cashier.paid_out", units, item.getName(new ItemStack(item)), cost));
		clink(player);
	}

	private void clink(ServerPlayer player) {
		ServerLevel level = player.level();
		gesture(GESTURE_DEAL);
		level.playSound(null, blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8F, 1.1F);
		level.playSound(null, player.blockPosition(), pl.pansage.gamblecraft.registry.ModSounds.CHIP_CLINK, SoundSource.PLAYERS, 0.8F, 1.0F);
	}
}
