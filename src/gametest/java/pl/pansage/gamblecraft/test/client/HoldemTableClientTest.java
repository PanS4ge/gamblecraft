package pl.pansage.gamblecraft.test.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;
import pl.pansage.gamblecraft.client.screen.CardGameScreen;
import pl.pansage.gamblecraft.client.table.ClientTables;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.core.games.HoldemGame;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.SeatView;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.StreetDealerEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.TableSession;
import pl.pansage.gamblecraft.game.TablePlacement;
import pl.pansage.gamblecraft.registry.ModEntities;

/**
 * Texas Hold'em for three: the player and two more players on the server (Kasia and Tomek, connected like real
 * ones). Tomek folds, the other two check it down: a screenshot at every street and at the showdown, and at each
 * the check that this client was never sent a card it should not see (the others' hole cards, then Tomek's folded
 * ones even at the showdown).
 */
public class HoldemTableClientTest implements FabricClientGameTest {
	private static final String[] STREETS = {"holdem_preflop", "holdem_flop", "holdem_turn", "holdem_river"};
	private static final int[] BOARD = {0, 3, 4, 5};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			int tableId = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = player.level();
				player.getInventory().add(new ItemStack(Items.EMERALD, 64));
				Direction facing = player.getDirection();
				BlockPos feet = player.blockPosition();
				BlockPos tableBlock = feet.relative(facing, 2);
				level.setBlock(tableBlock, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
				StreetDealerEntity dealer = ModEntities.STREET_DEALER.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
				BlockPos dealerPos = tableBlock.relative(facing, 2);
				dealer.snapTo(dealerPos.getX() + 0.5, dealerPos.getY(), dealerPos.getZ() + 0.5, facing.getOpposite().toYRot(), 0);
				level.addFreshEntity(dealer);
				CardTableEntity table = TablePlacement.createTable(player, dealer, tableBlock, GameKind.HOLDEM, Items.EMERALD, 1.0);
				int i = 0;
				for (String name : List.of("Kasia", "Tomek")) {
					Direction side = i++ == 0 ? facing.getClockWise() : facing.getCounterClockWise();
					BlockPos at = tableBlock.relative(side, 2);
					ServerPlayer guest = guest(server, level, name);
					guest.connection.teleport(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, side.getOpposite().toYRot(), 30);
					AgeGate.markAccepted(guest);
					guest.getInventory().add(new ItemStack(Items.EMERALD, 64));
					table.join(guest);
					table.session().buyIn(guest, 40);
				}
				table.session().buyIn(player, 40);
				return table.getId();
			});
			context.waitForScreen(CardGameScreen.class);

			int street = 0;
			boolean started = false;
			for (int i = 0; i < 800; i++) {
				context.waitTicks(3);
				String step = world.getServer().computeOnServer(server -> drive(server, tableId));
				started |= !step.equals("waiting");
				if (step.startsWith("me:")) {
					int board = Integer.parseInt(step.substring(3));
					if (street < STREETS.length && board == BOARD[street]) {
						context.waitTicks(12);
						checkPrivacy(context, tableId, false);
						context.takeScreenshot(STREETS[street]);
						street++;
					}
					world.getServer().runOnServer(server -> {
						CardTableEntity table = (CardTableEntity) player(server).level().getEntity(tableId);
						TableSession session = table.session();
						int seat = session.game().seatOf(player(server).getUUID());
						Act act = session.game().options(seat).stream().anyMatch(o -> o.act() == Act.CHECK) ? Act.CHECK : Act.CALL;
						session.act(player(server), act, 0);
					});
				} else if (started && step.equals("settled")) {
					break;
				}
			}
			if (street < STREETS.length) {
				throw new AssertionError("The hand ended after " + street + " streets");
			}
			context.waitTicks(20);
			checkPrivacy(context, tableId, true);
			context.takeScreenshot("holdem_showdown");

			// the same table from the player's chair: two more players round it, everyone's own cards for their eyes only
			context.setScreen(() -> null);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), 38);
			});
			context.waitTicks(8);
			context.takeScreenshot("holdem_table_world");
		}
	}

	/** Plays the other players' turns; says whose turn it is ("me:<board cards>"), or "settled" / "waiting". */
	private static String drive(MinecraftServer server, int tableId) {
		List<ServerPlayer> guests = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p != player(server)) {
				guests.add(p);
				keepAlive(p);
			}
		}
		CardTableEntity table = (CardTableEntity) player(server).level().getEntity(tableId);
		HoldemGame game = (HoldemGame) table.session().game();
		if (game.phase() == Phase.SETTLED) {
			return "settled";
		}
		if (!game.awaitingInput()) {
			return game.isRoundRunning() ? "dealing" : "waiting";
		}
		TableSnapshot all = game.snapshot(null);
		int board = all.hands().stream().filter(h -> h.id().equals("board")).findFirst().map(h -> h.cards().size()).orElse(0);
		UUID who = all.seats().stream().filter(s -> s.index() == game.activeSeat()).map(SeatView::player).findFirst().orElse(null);
		if (player(server).getUUID().equals(who)) {
			return "me:" + board;
		}
		for (ServerPlayer guest : guests) {
			if (guest.getUUID().equals(who)) {
				int seat = game.seatOf(who);
				Act stay = game.options(seat).stream().anyMatch(o -> o.act() == Act.CHECK) ? Act.CHECK : Act.CALL;
				// Tomek throws his hand away before the flop; Kasia sees it through
				boolean fold = guest.getName().getString().equals("Tomek") && board == 0;
				if (!fold || table.session().act(guest, Act.FOLD, 0) != null) {
					table.session().act(guest, stay, 0);
				}
				return "acted";
			}
		}
		return "waiting";
	}

	/** What this client holds of the table: its own cards; the others' only at the showdown, and never folded ones. */
	private static void checkPrivacy(ClientGameTestContext context, int tableId, boolean showdown) {
		String problem = context.computeOnClient(client -> {
			ClientTables.View view = ClientTables.get(tableId);
			SeatView mine = ClientTables.mySeat(view.state);
			for (HandView hand : view.state.snapshot().hands()) {
				if (hand.owner() < 0 || mine == null) {
					continue;
				}
				boolean folded = "fold".equals(hand.result());
				boolean known = hand.cards().stream().allMatch(id -> id >= 0);
				boolean hidden = hand.cards().stream().allMatch(id -> id == -1);
				if (hand.owner() == mine.index() && !known) {
					return "own cards hidden";
				}
				if (hand.owner() != mine.index() && (!showdown || folded) && !hidden) {
					return "saw seat " + hand.owner() + (folded ? " (folded)" : "") + ": " + hand.cards();
				}
				if (hand.owner() != mine.index() && showdown && !folded && !known) {
					return "showdown hand of seat " + hand.owner() + " not shown";
				}
			}
			return null;
		});
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}

	/** Another player on the server, connected the way game tests connect their mock players. */
	private static ServerPlayer guest(MinecraftServer server, ServerLevel level, String name) {
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer guest = new ServerPlayer(server, level, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, guest, cookie);
		return guest;
	}

	/** Nobody answers the guests' keep-alives, so the test does, or they would time out mid-hand. */
	private static void keepAlive(ServerPlayer guest) {
		try {
			Field pending = ServerCommonPacketListenerImpl.class.getDeclaredField("keepAlivePending");
			pending.setAccessible(true);
			pending.setBoolean(guest.connection, false);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
