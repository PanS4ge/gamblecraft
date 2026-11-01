package pl.pansage.gamblecraft.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.CasinoDealerEntity;
import pl.pansage.gamblecraft.entity.StreetDealerEntity;
import pl.pansage.gamblecraft.network.TablePayloads.DealerStart;
import pl.pansage.gamblecraft.registry.ModEntities;

/**
 * The "pick any block as the table" step: after choosing a game at a village dealer, the player's next
 * right-click on a block near the dealer becomes the table.
 */
public final class TablePlacement {
	private static final int TIMEOUT_TICKS = 20 * 30;
	private static final double DEALER_RANGE = 8;
	private static final long MAX_BET = 64;

	private record Pending(int dealerId, GameKind kind, Item item, double value, long expires) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private TablePlacement() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			Pending pending = PENDING.get(player.getUUID());
			if (pending == null) {
				return InteractionResult.PASS;
			}
			if (level.getGameTime() > pending.expires) {
				PENDING.remove(player.getUUID());
				return InteractionResult.PASS;
			}
			PENDING.remove(player.getUUID());
			place(serverPlayer, (ServerLevel) level, hit.getBlockPos(), pending);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/** Handles the game choice from the dealer screen. */
	public static void request(ServerPlayer player, DealerStart start) {
		if (!AgeGate.require(player)) {
			return;
		}
		if (!(player.level().getEntity(start.dealerId()) instanceof StreetDealerEntity dealer) || dealer.distanceTo(player) > DEALER_RANGE) {
			return;
		}
		if (dealer.table() != null) {
			dealer.table().join(player);
			return;
		}
		GameKind kind;
		try {
			kind = GameKind.byId(start.game());
		} catch (IllegalArgumentException e) {
			return;
		}
		Identifier itemId = Identifier.tryParse(start.item());
		Item item = itemId == null ? null : BuiltInRegistries.ITEM.getValue(itemId);
		Double value = item == null ? null : dealer.economy(player.level()).get(item);
		if (value == null) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.dealer.not_accepted"));
			return;
		}
		PENDING.put(player.getUUID(), new Pending(dealer.getId(), kind, item, value, player.level().getGameTime() + TIMEOUT_TICKS));
		player.sendOverlayMessage(Component.translatable("gamblecraft.dealer.pick_block"));
	}

	private static void place(ServerPlayer player, ServerLevel level, BlockPos pos, Pending pending) {
		if (!(level.getEntity(pending.dealerId) instanceof StreetDealerEntity dealer) || !dealer.isAlive() || dealer.table() != null) {
			return;
		}
		createTable(player, dealer, pos, pending.kind, pending.item, pending.value);
	}

	/** Puts a table on top of {@code pos} for this dealer and seats the player. Returns null if the spot is unusable. */
	public static CardTableEntity createTable(ServerPlayer player, StreetDealerEntity dealer, BlockPos pos, GameKind kind, Item item, double value) {
		ServerLevel level = player.level();
		Vec3 top = Vec3.atBottomCenterOf(pos.above());
		// The dealer takes the side of the table away from the player.
		Vec3 towardsDealer = top.subtract(player.position());
		TableSession session = new TableSession(kind, false, new ItemCurrency(item, value), dealer.bank(), 1, MAX_BET,
				new java.util.SplittableRandom(level.getRandom().nextLong()));
		return open(player, dealer, pos, towardsDealer, session);
	}

	/** A casino table on the dealer's own felt: chips, six seats and a multi-deck shoe. */
	public static CardTableEntity createCasinoTable(ServerPlayer player, CasinoDealerEntity dealer, BlockPos pos) {
		ServerLevel level = player.level();
		Vec3 towardsDealer = dealer.position().subtract(Vec3.atBottomCenterOf(pos.above()));
		TableSession session = new TableSession(dealer.game(), true, ChipCurrency.INSTANCE, dealer.bank(), dealer.minBet(),
				dealer.maxBet(), new java.util.SplittableRandom(level.getRandom().nextLong()));
		return open(player, dealer, pos, towardsDealer, session);
	}

	/** The boss's heads-up duel on his felt: one stake for him for the whole match. */
	public static CardTableEntity createDuelTable(ServerPlayer player, CasinoDealerEntity boss, BlockPos pos, long smallBlind, long houseBank) {
		ServerLevel level = player.level();
		Vec3 towardsDealer = boss.position().subtract(Vec3.atBottomCenterOf(pos.above()));
		pl.pansage.gamblecraft.core.games.HoldemGame game = pl.pansage.gamblecraft.core.games.HoldemGame.duel(smallBlind, smallBlind * 2, houseBank,
				"Boss", new java.util.SplittableRandom(level.getRandom().nextLong()));
		TableSession session = new TableSession(pl.pansage.gamblecraft.core.games.GameKind.HOLDEM, game, ChipCurrency.INSTANCE, boss.bank(),
				smallBlind, smallBlind * 2);
		return open(player, boss, pos, towardsDealer, session);
	}

	private static CardTableEntity open(ServerPlayer player, StreetDealerEntity dealer, BlockPos pos, Vec3 towardsDealer, TableSession session) {
		ServerLevel level = player.level();
		Vec3 top = Vec3.atBottomCenterOf(pos.above());
		BlockState above = level.getBlockState(pos.above());
		if (!above.canBeReplaced() || top.distanceTo(dealer.position()) > DEALER_RANGE || top.distanceTo(player.position()) > 5.5) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.dealer.bad_block"));
			return null;
		}
		CardTableEntity table = ModEntities.CARD_TABLE.create(level, EntitySpawnReason.TRIGGERED);
		if (table == null) {
			return null;
		}
		float facing = (float) Math.toDegrees(Math.atan2(-towardsDealer.x, towardsDealer.z));
		facing = Math.round(facing / 90.0F) * 90.0F;
		table.setPos(top.x, top.y, top.z);
		table.setup(session, dealer.getId(), facing);
		level.addFreshEntity(table);
		dealer.setTable(table);
		table.join(player);
		return table;
	}
}
