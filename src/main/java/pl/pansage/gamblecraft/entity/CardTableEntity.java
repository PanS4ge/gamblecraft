package pl.pansage.gamblecraft.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.TableTypes.Wager;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.PayoutMailbox;
import pl.pansage.gamblecraft.game.TableSession;
import pl.pansage.gamblecraft.network.TablePayloads.TableAction;
import pl.pansage.gamblecraft.network.TablePayloads.TableState;
import pl.pansage.gamblecraft.network.TablePayloads.TableToss;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Invisible anchor of a card game lying on top of any block. The server keeps the {@link TableSession};
 * clients only get per-player snapshots and draw the cards. Not saved: if it unloads, every stake is refunded.
 */
public class CardTableEntity extends Entity {
	private static final EntityDataAccessor<Integer> DEALER = SynchedEntityData.defineId(CardTableEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> GAME = SynchedEntityData.defineId(CardTableEntity.class, EntityDataSerializers.STRING);
	/** Direction (degrees) from the table centre towards the dealer's side. */
	private static final EntityDataAccessor<Float> FACING = SynchedEntityData.defineId(CardTableEntity.class, EntityDataSerializers.FLOAT);

	private TableSession session;
	private int sentVersion = -1;
	private final Set<UUID> openFor = new HashSet<>();

	public CardTableEntity(EntityType<? extends CardTableEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setup(TableSession session, int dealerId, float facing) {
		this.session = session;
		this.entityData.set(DEALER, dealerId);
		this.entityData.set(GAME, session.kind().id());
		this.entityData.set(FACING, facing);
		session.setMailbox((id, stacks) -> {
			if (level() instanceof ServerLevel serverLevel) {
				PayoutMailbox.get(serverLevel.getServer()).post(id, stacks);
			}
		});
	}

	public TableSession session() {
		return session;
	}

	public int dealerId() {
		return entityData.get(DEALER);
	}

	public String gameId() {
		return entityData.get(GAME);
	}

	public float facing() {
		return entityData.get(FACING);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DEALER, -1);
		builder.define(GAME, "");
		builder.define(FACING, 0.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (session == null) {
			discard();
			return;
		}
		Entity dealer = level.getEntity(dealerId());
		boolean keep = session.tick(level.getServer(), position()) && dealer != null && dealer.isAlive();
		if (!keep) {
			discard();
			return;
		}
		if (session.version() != sentVersion || tickCount % 10 == 0) {
			sentVersion = session.version();
			broadcast();
		}
	}

	private void broadcast() {
		for (ServerPlayer player : PlayerLookup.tracking(this)) {
			ServerPlayNetworking.send(player, stateFor(player, openFor.remove(player.getUUID())));
		}
	}

	private TableState stateFor(ServerPlayer player, boolean open) {
		return new TableState(getId(), dealerId(), session.currency().id(), session.minBet(), session.maxBet(), session.ticksLeft(), open,
				session.snapshotFor(player.getUUID()));
	}

	/** Seats the player and opens the game screen for them. */
	public void join(ServerPlayer player) {
		if (session == null || !AgeGate.require(player)) {
			return;
		}
		Component error = session.sit(player);
		if (error != null) {
			player.sendOverlayMessage(error);
		}
		openFor.add(player.getUUID());
		ServerPlayNetworking.send(player, stateFor(player, true));
		openFor.remove(player.getUUID());
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			join(serverPlayer);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	public void handle(ServerPlayer player, TableAction action) {
		if (session == null || !AgeGate.require(player) || player.position().distanceTo(position()) > TableSession.MAX_DISTANCE) {
			return;
		}
		Act[] acts = Act.values();
		Component error = switch (action.op()) {
			case SIT -> session.sit(player);
			case LEAVE -> session.leave(player);
			case BET -> session.bet(player, new Wager(action.amount(), action.option(), action.side()));
			case CLEAR_BET -> session.clearBet(player);
			case BUY_IN -> session.buyIn(player, action.amount());
			case ACT -> action.act() >= 0 && action.act() < acts.length
					? session.act(player, acts[action.act()], action.amount())
					: Component.translatable("gamblecraft.table.not_allowed");
		};
		if (error != null) {
			player.sendOverlayMessage(error);
			return;
		}
		switch (action.op()) {
			case BET, BUY_IN -> toss(player, false);
			case ACT -> toss(player, action.amount() <= 0 || action.act() == Act.HOLD.ordinal());
			default -> {
			}
		}
	}

	/** The player's arm swings and a stake or a card flies from their hand to their place on the table. */
	private void toss(ServerPlayer player, boolean card) {
		int seat = session.game().seatOf(player.getUUID());
		if (seat < 0) {
			return;
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		TableToss toss = new TableToss(getId(), player.getId(), seat, card, session.currency().id());
		for (ServerPlayer viewer : PlayerLookup.tracking(this)) {
			ServerPlayNetworking.send(viewer, toss);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (session != null && level() instanceof ServerLevel level) {
			MinecraftServer server = level.getServer();
			session.close(server);
			if (level.getEntity(dealerId()) instanceof StreetDealerEntity dealer) {
				dealer.tableClosed(this);
			}
		}
		super.remove(reason);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
