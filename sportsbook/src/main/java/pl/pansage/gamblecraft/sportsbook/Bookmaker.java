package pl.pansage.gamblecraft.sportsbook;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.Celebrate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.ItemCurrency;
import pl.pansage.gamblecraft.registry.ModSounds;
import pl.pansage.gamblecraft.sportsbook.bet.BetBook;
import pl.pansage.gamblecraft.sportsbook.bet.Coupon;
import pl.pansage.gamblecraft.sportsbook.bet.MineralPlan;
import pl.pansage.gamblecraft.sportsbook.bet.Pick;
import pl.pansage.gamblecraft.sportsbook.block.BettingTerminalBlock;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;
import pl.pansage.gamblecraft.sportsbook.item.CouponView;
import pl.pansage.gamblecraft.sportsbook.registry.SbRegistry;

/** The terminal's counter: selling coupons, paying them out, and telling players how their bets went. */
public final class Bookmaker {
	public static final double REACH = 8;
	private static final String KEY = "gamblecraft_sportsbook.bet.";

	public record Placed(boolean ok, Component message) {
	}

	private Bookmaker() {
	}

	private static Placed refuse(String reason, Object... args) {
		return new Placed(false, Component.translatable(KEY + reason, args).withStyle(ChatFormatting.RED));
	}

	/** Sells a coupon for the picks ({@code keys[i]} backed with {@code selections[i]}), paid in chips or minerals. */
	public static Placed place(ServerPlayer player, BlockPos terminal, List<String> keys, List<Outcome> selections, long stake, boolean minerals) {
		if (!AgeGate.require(player)) {
			return refuse("age_gate");
		}
		if (!(player.level().getBlockState(terminal).getBlock() instanceof BettingTerminalBlock)
				|| player.position().distanceTo(Vec3.atCenterOf(terminal)) > REACH) {
			return refuse("too_far");
		}
		FeedService feed = FeedService.get();
		SportsbookConfig config = SportsbookConfig.get();
		if (feed == null || keys.isEmpty() || keys.size() != selections.size()) {
			return refuse("empty");
		}
		if (keys.size() > config.maxPicks) {
			return refuse("too_many", config.maxPicks);
		}
		if (new HashSet<>(keys).size() != keys.size()) {
			return refuse("same_game");
		}
		if (stake < config.minStake || stake > config.maxStake) {
			return refuse("stake_limits", config.minStake, config.maxStake);
		}
		long now = System.currentTimeMillis();
		List<Pick> picks = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			SportEvent event = feed.event(keys.get(i));
			Outcome selection = selections.get(i);
			if (event == null || selection == null || !event.open(now) || event.odds() == null || !event.odds().offers(selection)) {
				return refuse("closed", event == null ? keys.get(i) : event.home() + " – " + event.away());
			}
			if (!config.on(event.league())) {
				return refuse("closed", event.home() + " – " + event.away());
			}
			picks.add(new Pick(event.key(), event.league(), event.source(), event.home(), event.away(), event.kickoff(), selection,
					event.odds().price(selection), Pick.Status.WAITING, "", ""));
		}
		if (!pay(player, stake, minerals)) {
			return refuse(minerals ? "no_minerals" : "no_chips", stake);
		}
		Coupon coupon = new Coupon(UUID.randomUUID(), player.getUUID(), now, stake, minerals, picks, Coupon.State.OPEN, 0, false, 1);
		BetBook.get(player.level().getServer()).add(coupon);
		ItemStack item = new ItemStack(SbRegistry.BET_COUPON);
		apply(item, coupon);
		player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
		player.level().playSound(null, terminal, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.9F, 1.2F);
		return new Placed(true, Component.translatable(KEY + "placed", picks.size(), pl.pansage.gamblecraft.sportsbook.item.CouponItem.odds(coupon.totalOdds()),
				coupon.potential(config.maxPayout)).withStyle(ChatFormatting.GREEN));
	}

	/** Chips straight from the pockets, or minerals at the cashier's rates with the change in chips. */
	static boolean pay(ServerPlayer player, long stake, boolean minerals) {
		if (!minerals) {
			return ChipCurrency.INSTANCE.withdraw(player, stake);
		}
		List<Item> kinds = new ArrayList<>(CashierEntity.RATES.keySet());
		int[] values = new int[kinds.size()];
		long[] counts = new long[kinds.size()];
		for (int i = 0; i < kinds.size(); i++) {
			values[i] = CashierEntity.RATES.get(kinds.get(i));
			counts[i] = new ItemCurrency(kinds.get(i), 1).balance(player);
		}
		MineralPlan plan = MineralPlan.of(stake, values, counts);
		if (plan == null) {
			return false;
		}
		for (int i = 0; i < kinds.size(); i++) {
			if (plan.take()[i] > 0) {
				new ItemCurrency(kinds.get(i), 1).withdraw(player, plan.take()[i]);
			}
		}
		if (plan.change() > 0) {
			ChipCurrency.INSTANCE.deposit(player, plan.change());
		}
		return true;
	}

	/** Copies the book's coupon onto the item: description, name colour and model (slip, pouch, torn slip). */
	public static void apply(ItemStack stack, Coupon coupon) {
		stack.set(SbRegistry.COUPON, CouponView.of(coupon));
		String look = switch (coupon.state) {
			case OPEN -> "open";
			case WON -> "won";
			case LOST -> "lost";
			case VOID -> "void";
		};
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(look), List.of()));
	}

	/** Brings a carried coupon up to date with the book. */
	public static void refresh(ItemStack stack, MinecraftServer server) {
		CouponView view = stack.get(SbRegistry.COUPON);
		if (view == null || server == null) {
			return;
		}
		Coupon coupon = BetBook.get(server).get(view.id());
		if (coupon != null && coupon.version != view.version()) {
			apply(stack, coupon);
		}
	}

	/** Using a coupon: a pouch pays out (once, whatever copies exist), anything else says how it stands. */
	public static void claim(ServerPlayer player, ItemStack stack) {
		CouponView view = stack.get(SbRegistry.COUPON);
		if (view == null) {
			return;
		}
		BetBook book = BetBook.get(player.level().getServer());
		Coupon coupon = book.get(view.id());
		if (coupon == null) {
			player.sendOverlayMessage(Component.translatable(KEY + "unknown").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (coupon.version != view.version()) {
			apply(stack, coupon);
		}
		switch (coupon.state) {
			case OPEN -> player.sendOverlayMessage(Component.translatable(KEY + "pending", coupon.decided(), coupon.picks.size()));
			case LOST -> player.sendOverlayMessage(Component.translatable(KEY + "lost").withStyle(ChatFormatting.RED));
			case WON, VOID -> {
				if (!book.claim(coupon)) {
					player.sendOverlayMessage(Component.translatable(KEY + "already_paid").withStyle(ChatFormatting.GRAY));
					stack.shrink(1);
					return;
				}
				ChipCurrency.INSTANCE.deposit(player, coupon.payout);
				stack.shrink(1);
				if (coupon.state == Coupon.State.WON) {
					Celebrate.win(player.level(), player, coupon.payout, coupon.stake, player.position().add(0, 1, 0), false);
					player.level().playSound(null, player.blockPosition(), ModSounds.COINS, SoundSource.PLAYERS, 0.8F, 1.0F);
				} else {
					player.level().playSound(null, player.blockPosition(), ModSounds.CHIP_CLINK, SoundSource.PLAYERS, 0.8F, 1.0F);
				}
				player.sendOverlayMessage(Component.translatable(KEY + "collected", coupon.payout).withStyle(ChatFormatting.GREEN));
			}
		}
	}

	/** A coupon was just decided: its owner hears of it if they are online. */
	static void decided(MinecraftServer server, Coupon coupon) {
		ServerPlayer owner = server.getPlayerList().getPlayer(coupon.owner);
		if (owner == null) {
			return;
		}
		String code = coupon.id.toString().substring(0, 4).toUpperCase();
		switch (coupon.state) {
			case WON -> {
				owner.sendSystemMessage(Component.translatable(KEY + "news.won", code, coupon.payout).withStyle(ChatFormatting.GREEN));
				owner.level().playSound(null, owner.blockPosition(), ModSounds.WIN, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
			case LOST -> owner.sendSystemMessage(Component.translatable(KEY + "news.lost", code).withStyle(ChatFormatting.RED));
			case VOID -> owner.sendSystemMessage(Component.translatable(KEY + "news.void", code, coupon.payout).withStyle(ChatFormatting.GRAY));
			default -> {
			}
		}
	}
}
