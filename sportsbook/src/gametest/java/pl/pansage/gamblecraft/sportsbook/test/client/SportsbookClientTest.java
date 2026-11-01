package pl.pansage.gamblecraft.sportsbook.test.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pl.pansage.gamblecraft.block.TallMachineBlock;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.sportsbook.Bookmaker;
import pl.pansage.gamblecraft.sportsbook.FeedService;
import pl.pansage.gamblecraft.sportsbook.client.ClientFeed;
import pl.pansage.gamblecraft.sportsbook.client.screen.BetScreen;
import pl.pansage.gamblecraft.sportsbook.client.screen.SourcesScreen;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Odds;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.feed.Source;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.EventView;
import pl.pansage.gamblecraft.sportsbook.registry.SbRegistry;

/**
 * The terminal with the real feeds switched on (whatever games are on when the test runs), the betting window and
 * the sources window, then coupons in every state (on demonstration games) and a pouch being paid out.
 */
public class SportsbookClientTest implements FabricClientGameTest {
	private static final long DAY = 24 * 3600_000L;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			BlockPos terminal = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().add(new ItemStack(ModItems.CHIPS[3], 10));
				BlockPos pos = player.blockPosition().offset(0, 0, 4);
				BlockState state = SbRegistry.BETTING_TERMINAL.defaultBlockState().setValue(TallMachineBlock.FACING, Direction.NORTH);
				player.level().setBlock(pos, state, 3);
				player.level().setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER), 3);
				// the real feeds, for real fixtures on the screens
				FeedService.offline = false;
				FeedService.get().refresh();
				return pos;
			});
			// up to a minute and a half for most leagues to come in (TheSportsDB, with the Ekstraklasa, is the slowest)
			for (int i = 0; i < 90 && world.getServer().computeOnServer(server -> FeedService.get().status().values().stream()
					.filter(FeedService.Status::ok).count()) < 9; i++) {
				context.waitTicks(20);
			}
			// and for last weeks' results, which give the house's own prices (the Ekstraklasa's among them) their spread
			for (int i = 0; i < 120 && world.getServer().computeOnServer(server -> FeedService.get().warming()); i++) {
				context.waitTicks(20);
			}
			int real = world.getServer().computeOnServer(server -> FeedService.get().visible(System.currentTimeMillis()).size());
			System.out.println("[sportsbook test] games from the real feeds: " + real);
			world.getServer().runOnServer(server -> FeedService.get().status().forEach((league, status) ->
					System.out.println("[sportsbook test] " + league.id + ": " + status)));
			if (real < 4) {
				world.getServer().runOnServer(server -> FeedService.get().put(demo("espn:demo-f1", League.PREMIER_LEAGUE, "Liverpool", "Arsenal", 2.40, 3.55, 2.90),
						demo("espn:demo-f2", League.NBA, "Boston Celtics", "New York Knicks", 1.62, 0, 2.30)));
			}

			// the terminal in the world, its screen scrolling the games
			view(world, terminal, 1.75, 0, 12);
			context.waitTicks(30);
			context.takeScreenshot("sportsbook_terminal");

			// the betting window with a three-game accumulator on the slip
			context.runOnClient(client -> {
				ClientFeed.SLIP.clear();
				long now = System.currentTimeMillis();
				List<EventView> open = new ArrayList<>(ClientFeed.events().stream().filter(e -> ClientFeed.open(e, now)).toList());
				// the Ekstraklasa first, then games with the bookmaker's prices
				open.sort(Comparator.comparing((EventView e) -> e.league() != League.EKSTRAKLASA.ordinal()).thenComparing(EventView::model)
						.thenComparingLong(EventView::kickoff));
				List<Integer> leagues = new ArrayList<>();
				for (EventView e : open) {
					if (ClientFeed.SLIP.size() < 3 && !leagues.contains(e.league())) {
						leagues.add(e.league());
						ClientFeed.SLIP.put(e.key(), e.oddsHome() <= e.oddsAway() ? Outcome.HOME : Outcome.AWAY);
					}
				}
				if (!open.isEmpty()) {
					BetScreen.show(League.of(open.getFirst().league()));
				}
			});
			context.setScreen(() -> new BetScreen(terminal));
			context.waitTicks(5);
			context.takeScreenshot("sportsbook_bet_screen");
			context.setScreen(() -> new SourcesScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("sportsbook_sources");
			context.setScreen(() -> null);

			// coupons on demonstration games: an accumulator under way, a won single, a lost single
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().add(new ItemStack(Items.DIAMOND, 12));
				FeedService feed = FeedService.get();
				SportEvent a = demo("espn:demo-a", League.PREMIER_LEAGUE, "Liverpool", "Arsenal", 2.40, 3.55, 2.90);
				SportEvent b = demo("thesportsdb:demo-b", League.EKSTRAKLASA, "Raków Częstochowa", "Pogoń Szczecin", 1.95, 3.40, 3.80);
				SportEvent c = demo("espn:demo-c", League.NBA, "Boston Celtics", "New York Knicks", 1.62, 0, 2.30);
				SportEvent d = demo("espn:demo-d", League.BUNDESLIGA, "Bayern München", "Borussia Dortmund", 1.50, 4.80, 5.50);
				SportEvent e = demo("espn:demo-e", League.SERIE_A, "Inter", "Milan", 2.20, 3.20, 3.30);
				feed.put(a, b, c, d, e);
				Bookmaker.place(player, terminal, List.of(a.key(), b.key(), c.key()), List.of(Outcome.HOME, Outcome.DRAW, Outcome.AWAY), 100, false);
				Bookmaker.place(player, terminal, List.of(d.key()), List.of(Outcome.HOME), 200, true);
				Bookmaker.place(player, terminal, List.of(e.key()), List.of(Outcome.AWAY), 100, false);
				feed.put(a.withState(EventState.FINISHED, 2, 1, Outcome.HOME, ""), b.withState(EventState.LIVE, 1, 1, null, "67'"),
						d.withState(EventState.FINISHED, 3, 1, Outcome.HOME, ""), e.withState(EventState.FINISHED, 2, 0, Outcome.HOME, ""));
			});
			context.waitTicks(30);
			List<ItemStack> coupons = context.computeOnClient(client -> {
				List<ItemStack> out = new ArrayList<>();
				for (ItemStack stack : client.player.getInventory()) {
					if (stack.is(SbRegistry.BET_COUPON)) {
						out.add(stack.copy());
					}
				}
				return out;
			});
			for (int i = 0; i < coupons.size(); i++) {
				int shown = i;
				context.setScreen(() -> new CouponShowcaseScreen(coupons, shown));
				context.waitTicks(4);
				context.takeScreenshot("sportsbook_coupon_" + i);
			}
			context.setScreen(() -> null);

			// the won pouch paid out at the terminal
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				for (ItemStack stack : player.getInventory()) {
					if (stack.is(SbRegistry.BET_COUPON) && stack.get(SbRegistry.COUPON).couponState() == pl.pansage.gamblecraft.sportsbook.bet.Coupon.State.WON) {
						Bookmaker.claim(player, stack);
						break;
					}
				}
			});
			context.waitTicks(14);
			context.takeScreenshot("sportsbook_pouch_paid");
		}
	}

	private static SportEvent demo(String key, League league, String home, String away, double h, double d, double a) {
		Source source = Source.byId(key.substring(0, key.indexOf(':')));
		boolean bookmaker = league.sport.draws && league != League.EKSTRAKLASA;
		return new SportEvent(key, league, source, home, away, System.currentTimeMillis() + DAY, EventState.SCHEDULED, -1, -1, null, "",
				new Odds(h, d, a, !bookmaker, bookmaker ? "DraftKings" : "model"), "", "");
	}

	private static void view(TestSingleplayerContext world, BlockPos machine, double back, double up, float pitch) {
		world.getServer().runOnServer(server -> player(server).connection.teleport(machine.getX() + 0.5, machine.getY() + up,
				machine.getZ() + 0.5 - back, 0, pitch));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
