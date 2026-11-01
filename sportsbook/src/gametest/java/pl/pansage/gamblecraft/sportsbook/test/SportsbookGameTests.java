package pl.pansage.gamblecraft.sportsbook.test;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pl.pansage.gamblecraft.block.TallMachineBlock;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.sportsbook.Bookmaker;
import pl.pansage.gamblecraft.sportsbook.FeedService;
import pl.pansage.gamblecraft.sportsbook.bet.Coupon;
import pl.pansage.gamblecraft.sportsbook.bet.Pick;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Odds;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.Source;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;
import pl.pansage.gamblecraft.sportsbook.item.CouponView;
import pl.pansage.gamblecraft.sportsbook.registry.SbRegistry;

/** Buying coupons at a terminal and having them settled by what the feed reports, without the network. */
public class SportsbookGameTests {
	private static final long DAY = 24 * 3600_000L;

	private static BlockPos terminal(GameTestHelper helper) {
		BlockPos lower = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockState state = SbRegistry.BETTING_TERMINAL.defaultBlockState().setValue(TallMachineBlock.FACING, Direction.SOUTH);
		helper.getLevel().setBlock(lower, state, 3);
		helper.getLevel().setBlock(lower.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER), 3);
		return lower;
	}

	private static ServerPlayer punter(GameTestHelper helper, int hundreds) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos stand = helper.absolutePos(new BlockPos(1, 1, 3));
		player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		AgeGate.markAccepted(player);
		if (hundreds > 0) {
			player.getInventory().add(new ItemStack(ModItems.CHIPS[3], hundreds));
		}
		return player;
	}

	private static final java.util.concurrent.atomic.AtomicInteger GAMES = new java.util.concurrent.atomic.AtomicInteger();

	/** A key no other test uses, so tests running side by side do not settle each other's games. */
	private static String key(GameTestHelper helper, String name) {
		return "espn:test-" + GAMES.incrementAndGet() + "-" + name;
	}

	private static SportEvent game(String key, String home, String away, long kickoff, double h, double d, double a) {
		return new SportEvent(key, League.PREMIER_LEAGUE, Source.ESPN, home, away, kickoff, EventState.SCHEDULED, -1, -1, null, "",
				new Odds(h, d, a, false, "DraftKings"), "", "");
	}

	private static SportEvent report(SportEvent game, EventState state, int home, int away, Outcome result, String clock) {
		return game.withState(state, home, away, result, clock);
	}

	private static ItemStack coupon(ServerPlayer player) {
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(SbRegistry.BET_COUPON)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static Coupon book(GameTestHelper helper, ItemStack stack) {
		return pl.pansage.gamblecraft.sportsbook.bet.BetBook.get(helper.getLevel().getServer()).get(stack.get(SbRegistry.COUPON).id());
	}

	/** Two picks, a live score on the way, both won: a green pouch that pays the accumulator once. */
	@GameTest(maxTicks = 40)
	public void accumulatorWinsAndPaysOnce(GameTestHelper helper) {
		FeedService feed = FeedService.get();
		BlockPos pos = terminal(helper);
		ServerPlayer player = punter(helper, 5);
		long now = System.currentTimeMillis();
		SportEvent a = game(key(helper, "a"), "Liverpool", "Arsenal", now + DAY, 2.40, 3.55, 2.90);
		SportEvent b = game(key(helper, "b"), "Everton", "Newcastle United", now + DAY, 2.80, 3.30, 2.90);
		feed.put(a, b);
		Bookmaker.Placed placed = Bookmaker.place(player, pos, List.of(a.key(), b.key()), List.of(Outcome.HOME, Outcome.AWAY), 100, false);
		helper.assertTrue(placed.ok(), placed.message());
		helper.assertValueEqual(ChipCurrency.INSTANCE.balance(player), 400L, "chips after the stake");
		ItemStack stack = coupon(player);
		helper.assertFalse(stack.isEmpty(), "no coupon given");
		CouponView view = stack.get(SbRegistry.COUPON);
		helper.assertValueEqual(view.picks().size(), 2, "picks");
		helper.assertValueEqual(view.picks().getFirst().odds(), 2.40, "locked price");

		feed.put(report(a, EventState.LIVE, 1, 0, null, "34'"));
		Bookmaker.refresh(stack, helper.getLevel().getServer());
		view = stack.get(SbRegistry.COUPON);
		helper.assertValueEqual(Pick.Status.of(view.picks().getFirst().status()), Pick.Status.LIVE, "live pick");
		helper.assertValueEqual(view.picks().getFirst().score(), "1:0", "live score");

		feed.put(report(a, EventState.FINISHED, 2, 0, Outcome.HOME, ""), report(b, EventState.FINISHED, 0, 1, Outcome.AWAY, ""));
		Bookmaker.refresh(stack, helper.getLevel().getServer());
		view = stack.get(SbRegistry.COUPON);
		helper.assertValueEqual(view.couponState(), Coupon.State.WON, "coupon state");
		helper.assertValueEqual(view.payout(), 696L, "payout");
		helper.assertValueEqual(stack.get(DataComponents.CUSTOM_MODEL_DATA).getString(0), "won", "pouch model");

		ItemStack copy = stack.copy();
		Bookmaker.claim(player, stack);
		helper.assertValueEqual(ChipCurrency.INSTANCE.balance(player), 400L + 696L, "chips after the payout");
		helper.assertTrue(stack.isEmpty(), "the pouch is used up");
		Bookmaker.claim(player, copy);
		helper.assertValueEqual(ChipCurrency.INSTANCE.balance(player), 400L + 696L, "a copied pouch pays nothing");
		helper.succeed();
	}

	/** One lost pick loses the coupon straight away, with the other game still to come. */
	@GameTest(maxTicks = 40)
	public void oneLostPickLosesTheCoupon(GameTestHelper helper) {
		FeedService feed = FeedService.get();
		BlockPos pos = terminal(helper);
		ServerPlayer player = punter(helper, 2);
		long now = System.currentTimeMillis();
		SportEvent a = game(key(helper, "a"), "Raków Częstochowa", "Pogoń Szczecin", now + DAY, 1.95, 3.40, 3.80);
		SportEvent b = game(key(helper, "b"), "Lech Poznań", "Legia Warszawa", now + 2 * DAY, 2.10, 3.30, 3.40);
		feed.put(a, b);
		helper.assertTrue(Bookmaker.place(player, pos, List.of(a.key(), b.key()), List.of(Outcome.DRAW, Outcome.HOME), 50, false).ok(), "placed");
		ItemStack stack = coupon(player);
		feed.put(report(a, EventState.FINISHED, 2, 1, Outcome.HOME, ""));
		Bookmaker.refresh(stack, helper.getLevel().getServer());
		CouponView view = stack.get(SbRegistry.COUPON);
		helper.assertValueEqual(view.couponState(), Coupon.State.LOST, "coupon state");
		helper.assertValueEqual(Pick.Status.of(view.picks().get(1).status()), Pick.Status.WAITING, "second pick");
		helper.assertValueEqual(stack.get(DataComponents.CUSTOM_MODEL_DATA).getString(0), "lost", "lost model");
		Bookmaker.claim(player, stack);
		helper.assertValueEqual(ChipCurrency.INSTANCE.balance(player), 150L, "nothing paid");
		helper.succeed();
	}

	/** A postponed game makes a single void: the stake comes back. */
	@GameTest(maxTicks = 40)
	public void postponedGameGivesTheStakeBack(GameTestHelper helper) {
		FeedService feed = FeedService.get();
		BlockPos pos = terminal(helper);
		ServerPlayer player = punter(helper, 3);
		SportEvent a = game(key(helper, "a"), "Bayer Leverkusen", "VfB Stuttgart", System.currentTimeMillis() + DAY, 1.70, 4.00, 4.50);
		feed.put(a);
		helper.assertTrue(Bookmaker.place(player, pos, List.of(a.key()), List.of(Outcome.AWAY), 120, false).ok(), "placed");
		feed.put(report(a, EventState.POSTPONED, -1, -1, null, ""));
		ItemStack stack = coupon(player);
		Bookmaker.refresh(stack, helper.getLevel().getServer());
		helper.assertValueEqual(stack.get(SbRegistry.COUPON).couponState(), Coupon.State.VOID, "coupon state");
		Bookmaker.claim(player, stack);
		helper.assertValueEqual(ChipCurrency.INSTANCE.balance(player), 300L, "stake back");
		helper.succeed();
	}

	/** Minerals pay at the cashier's rates (a diamond is 40 chips) with the change in chips. */
	@GameTest(maxTicks = 40)
	public void mineralsPayAtTheCashierRates(GameTestHelper helper) {
		FeedService feed = FeedService.get();
		BlockPos pos = terminal(helper);
		ServerPlayer player = punter(helper, 0);
		player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
		SportEvent a = game(key(helper, "a"), "Inter", "Milan", System.currentTimeMillis() + DAY, 2.20, 3.20, 3.30);
		feed.put(a);
		Bookmaker.Placed placed = Bookmaker.place(player, pos, List.of(a.key()), List.of(Outcome.HOME), 100, true);
		helper.assertTrue(placed.ok(), placed.message());
		helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 0, "diamonds left");
		helper.assertValueEqual(ChipCurrency.INSTANCE.balance(player), 20L, "change in chips");
		helper.assertTrue(book(helper, coupon(player)).minerals, "marked as paid in minerals");
		helper.succeed();
	}

	/** Every village type can build a betting shop, and the shop has a whole terminal in it. */
	@GameTest(maxTicks = 20)
	public void villagesBuildBettingShops(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var pools = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL);
		for (String village : pl.pansage.gamblecraft.worldgen.VillageInjector.VILLAGES) {
			var pool = pools.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("village/" + village + "/houses"));
			helper.assertTrue(pool != null && pl.pansage.gamblecraft.worldgen.VillageInjector.contains(pool, "betting_shop_" + village),
					"no betting shop in the " + village + " pool");
			var template = server.getStructureTemplateManager().get(pl.pansage.gamblecraft.sportsbook.Sportsbook.id("village/betting_shop_" + village));
			helper.assertTrue(template.isPresent(), "no template for " + village);
			var terminal = template.get().filterBlocks(BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
					SbRegistry.BETTING_TERMINAL);
			helper.assertValueEqual(terminal.size(), 2, "terminal halves in the " + village + " shop");
		}
		helper.succeed();
	}

	/** No bets on games about to start or under way, none from afar, none above the limits or without the chips. */
	@GameTest(maxTicks = 40)
	public void closedGamesAndBadStakesAreRefused(GameTestHelper helper) {
		FeedService feed = FeedService.get();
		BlockPos pos = terminal(helper);
		ServerPlayer player = punter(helper, 1);
		long now = System.currentTimeMillis();
		SportEvent soon = game(key(helper, "soon"), "Real Madrid", "Barcelona", now + 30_000L, 2.30, 3.60, 2.90);
		SportEvent live = report(game(key(helper, "live"), "Juventus", "Napoli", now - 600_000L, 2.30, 3.10, 3.20), EventState.LIVE, 0, 0, null, "10'");
		SportEvent later = game(key(helper, "later"), "Bayern München", "Borussia Dortmund", now + DAY, 1.50, 4.80, 5.50);
		feed.put(soon, live, later);
		helper.assertFalse(Bookmaker.place(player, pos, List.of(soon.key()), List.of(Outcome.HOME), 50, false).ok(), "kick-off within a minute");
		helper.assertFalse(Bookmaker.place(player, pos, List.of(live.key()), List.of(Outcome.HOME), 50, false).ok(), "game under way");
		helper.assertFalse(Bookmaker.place(player, pos, List.of(later.key()), List.of(Outcome.HOME), 5, false).ok(), "below the minimum stake");
		helper.assertFalse(Bookmaker.place(player, pos, List.of(later.key()), List.of(Outcome.HOME), 500, false).ok(), "more than the player has");
		helper.assertFalse(Bookmaker.place(player, pos.offset(20, 0, 0), List.of(later.key()), List.of(Outcome.HOME), 50, false).ok(), "no terminal");
		helper.assertValueEqual(ChipCurrency.INSTANCE.balance(player), 100L, "nothing taken");
		helper.assertTrue(Bookmaker.place(player, pos, List.of(later.key()), List.of(Outcome.HOME), 50, false).ok(), "a good bet");
		helper.succeed();
	}
}
