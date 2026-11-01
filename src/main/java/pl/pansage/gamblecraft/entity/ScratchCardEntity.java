package pl.pansage.gamblecraft.entity;

import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import pl.pansage.gamblecraft.core.casino.ScratchCards;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.ScratchData;
import pl.pansage.gamblecraft.network.ScratchPayloads.FoilSync;
import pl.pansage.gamblecraft.network.ScratchPayloads.OpenScratchTable;
import pl.pansage.gamblecraft.network.ScratchPayloads.ScratchCommand;
import pl.pansage.gamblecraft.network.ScratchPayloads.ScratchRub;
import pl.pansage.gamblecraft.network.ScratchPayloads.Strokes;
import pl.pansage.gamblecraft.registry.ModEntities;

/**
 * A scratch card lying on top of a block while its owner scratches it. The server keeps the card item and the
 * foil; everyone around sees the foil come off stroke by stroke, and the owner's arm reach for it. Once every
 * symbol shows, the card is settled (its prize was fixed by its seed all along) and goes back to its owner.
 */
public class ScratchCardEntity extends Entity {
	public static final int RUNNING = -2;
	public static final int LOST = -1;
	/** Stroke points are sent in quarters of a grid unit; this pair separates two strokes. */
	public static final int BREAK = -1;
	public static final double REACH = 6;
	private static final int RETURN_AFTER = 50;
	private static final int MAX_POINTS = 256;

	private static final EntityDataAccessor<Float> FACING = SynchedEntityData.defineId(ScratchCardEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ScratchCardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(ScratchCardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> VILLAGE = SynchedEntityData.defineId(ScratchCardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> CELLS = SynchedEntityData.defineId(ScratchCardEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> RESULT = SynchedEntityData.defineId(ScratchCardEntity.class, EntityDataSerializers.INT);

	private ItemStack card = ItemStack.EMPTY;
	private UUID ownerUuid;
	private ScratchFoil foil = new ScratchFoil();
	private int doneAge;
	private int lastSwing;
	private int[] cellsCache = new int[0];
	private String cellsKey = "";

	public ScratchCardEntity(EntityType<? extends ScratchCardEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** The top of {@code support} if a card can lie there, else NaN. */
	public static double restingHeight(Level level, BlockPos support) {
		VoxelShape shape = level.getBlockState(support).getCollisionShape(level, support);
		if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.4) {
			return Double.NaN;
		}
		double top = support.getY() + shape.max(Direction.Axis.Y);
		BlockPos above = BlockPos.containing(support.getX() + 0.5, top + 0.05, support.getZ() + 0.5);
		if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
			return Double.NaN;
		}
		AABB spot = new AABB(support.getX(), top, support.getZ(), support.getX() + 1, top + 0.2, support.getZ() + 1);
		return level.getEntitiesOfClass(ScratchCardEntity.class, spot).isEmpty() ? top : Double.NaN;
	}

	/** Lays the card on {@code support} with its bottom edge towards the player and opens the scratching view. */
	public static ScratchCardEntity place(ServerPlayer player, ItemStack card, BlockPos support) {
		ServerLevel level = player.level();
		double top = restingHeight(level, support);
		ScratchCardEntity entity = ModEntities.SCRATCH_CARD.create(level, EntitySpawnReason.TRIGGERED);
		if (entity == null || Double.isNaN(top)) {
			return null;
		}
		float facing = player.getYRot();
		double rad = Math.toRadians(facing);
		// a little towards the player, so the card sits on their side of the block
		entity.snapTo(support.getX() + 0.5 + Math.sin(rad) * 0.08, top + 0.002, support.getZ() + 0.5 - Math.cos(rad) * 0.08, facing, 0);
		entity.card = card;
		entity.ownerUuid = player.getUUID();
		ScratchData data = ScratchCardItem.data(card);
		entity.entityData.set(FACING, facing);
		entity.entityData.set(OWNER, player.getId());
		entity.entityData.set(TIER, ((ScratchCardItem) card.getItem()).tier().ordinal());
		entity.entityData.set(VILLAGE, data.name());
		entity.entityData.set(CELLS, encode(ScratchCardItem.outcome(level, data).cells()));
		level.addFreshEntity(entity);
		level.playSound(null, entity.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 1.4F);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		ServerPlayNetworking.send(player, new OpenScratchTable(entity.getId()));
		return entity;
	}

	private static String encode(int[] cells) {
		StringBuilder sb = new StringBuilder();
		for (int cell : cells) {
			sb.append((char) ('0' + cell));
		}
		return sb.toString();
	}

	public float facing() {
		return entityData.get(FACING);
	}

	public int ownerId() {
		return entityData.get(OWNER);
	}

	public int tier() {
		return entityData.get(TIER);
	}

	public int village() {
		return entityData.get(VILLAGE);
	}

	/** -2 while scratching, -1 once settled without a win, else the winning symbol. */
	public int result() {
		return entityData.get(RESULT);
	}

	public int[] cells() {
		String key = entityData.get(CELLS);
		if (!key.equals(cellsKey)) {
			cellsKey = key;
			cellsCache = key.chars().map(c -> c - '0').toArray();
		}
		return cellsCache;
	}

	public ScratchFoil foil() {
		return foil;
	}

	public void setFoil(ScratchFoil foil) {
		this.foil = foil;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FACING, 0.0F);
		builder.define(OWNER, -1);
		builder.define(TIER, 0);
		builder.define(VILLAGE, -1);
		builder.define(CELLS, "");
		builder.define(RESULT, RUNNING);
	}

	private ServerPlayer owner() {
		return ownerUuid != null && level() instanceof ServerLevel level && level.getPlayerByUUID(ownerUuid) instanceof ServerPlayer player ? player : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (card.isEmpty()) {
			discard();
			return;
		}
		BlockPos support = BlockPos.containing(getX(), getY() - 0.05, getZ());
		if (level.getBlockState(support).getCollisionShape(level, support).isEmpty()) {
			spawnAtLocation(level, card);
			card = ItemStack.EMPTY;
			discard();
			return;
		}
		ServerPlayer owner = owner();
		if (owner != null && owner.getId() != entityData.get(OWNER)) {
			entityData.set(OWNER, owner.getId());
		}
		if (result() != RUNNING && ++doneAge >= RETURN_AFTER) {
			giveBack(owner);
		}
	}

	private void giveBack(ServerPlayer player) {
		if (player != null && player.distanceTo(this) < REACH * 3) {
			if (!player.getInventory().add(card)) {
				player.drop(card, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.3F);
		} else {
			spawnAtLocation((ServerLevel) level(), card);
		}
		card = ItemStack.EMPTY;
		discard();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!AgeGate.require(serverPlayer) || result() != RUNNING) {
			return InteractionResult.CONSUME;
		}
		ServerPlayer owner = owner();
		boolean mine = player.getUUID().equals(ownerUuid);
		if (player.isShiftKeyDown()) {
			if (mine || owner == null || owner.distanceTo(this) > REACH * 2) {
				giveBack(serverPlayer);
			} else {
				serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.scratch.not_yours"));
			}
		} else if (mine) {
			ServerPlayNetworking.send(serverPlayer, new OpenScratchTable(getId()));
		} else {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.scratch.not_yours"));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Strokes from the owner: rub the server's foil, show them to everyone else, settle once all is uncovered. */
	public void handle(ServerPlayer player, ScratchRub rub) {
		if (!player.getUUID().equals(ownerUuid) || result() != RUNNING || player.distanceTo(this) > REACH || rub.points().size() > MAX_POINTS * 2) {
			return;
		}
		int removed = applyStrokes(foil, rub.points(), null);
		Strokes strokes = new Strokes(getId(), rub.points());
		for (ServerPlayer viewer : PlayerLookup.tracking(this)) {
			if (viewer != player) {
				ServerPlayNetworking.send(viewer, strokes);
			}
		}
		if (removed > 0 && tickCount - lastSwing >= 6) {
			lastSwing = tickCount;
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		if (foil.allRevealed()) {
			settle(player);
		}
	}

	public void handle(ServerPlayer player, ScratchCommand command) {
		if (!player.getUUID().equals(ownerUuid) || result() != RUNNING || player.distanceTo(this) > REACH) {
			return;
		}
		if (command.op() == ScratchCommand.REVEAL_ALL) {
			foil.revealAll(null);
			syncFoil();
			settle(player);
		} else if (command.op() == ScratchCommand.PICK_UP) {
			giveBack(player);
		}
	}

	/** Applies stroke points (quarters of grid units, {@link #BREAK} pairs between strokes) to a foil. */
	public static int applyStrokes(ScratchFoil foil, List<Integer> points, ScratchFoil.Flakes flakes) {
		int removed = 0;
		double lastX = Double.NaN;
		double lastY = Double.NaN;
		for (int i = 0; i + 1 < points.size(); i += 2) {
			if (points.get(i) == BREAK) {
				lastX = Double.NaN;
				continue;
			}
			double x = points.get(i) / 4.0;
			double y = points.get(i + 1) / 4.0;
			removed += Double.isNaN(lastX) ? foil.rub(x, y, flakes) : foil.rubLine(lastX, lastY, x, y, flakes);
			lastX = x;
			lastY = y;
		}
		return removed;
	}

	private void syncFoil() {
		FoilSync sync = new FoilSync(getId(), foil.toBytes());
		for (ServerPlayer viewer : PlayerLookup.tracking(this)) {
			ServerPlayNetworking.send(viewer, sync);
		}
	}

	public void sendFoilTo(ServerPlayer player) {
		ServerPlayNetworking.send(player, new FoilSync(getId(), foil.toBytes()));
	}

	/** Every symbol shows: the card is scratched for good, with fanfare for everyone around. */
	private void settle(ServerPlayer player) {
		ServerLevel level = player.level();
		int multiplier = ScratchCardItem.settle(level, card);
		int win = winningSymbol(cells());
		entityData.set(RESULT, multiplier > 0 && win >= 0 ? win : LOST);
		doneAge = 0;
		player.sendSystemMessage(ScratchCardItem.resultLine(card, player.blockPosition()));
		double y = getY() + 0.15;
		if (multiplier > 0) {
			ScratchCardItem.Tier tier = ((ScratchCardItem) card.getItem()).tier();
			boolean big = multiplier >= 10;
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), y, getZ(), big ? 60 : 25, 0.2, 0.15, 0.2, big ? 0.45 : 0.25);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), y, getZ(), 10, 0.35, 0.1, 0.35, 0.02);
			level.playSound(null, blockPosition(), big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
					0.9F, multiplier * tier.price >= 50 ? 0.9F : 1.2F);
		} else {
			level.sendParticles(ParticleTypes.SMOKE, getX(), y, getZ(), 8, 0.2, 0.02, 0.2, 0.01);
			level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}

	public static int winningSymbol(int[] cells) {
		int[] counts = new int[ScratchCards.Prize.values().length];
		int best = -1;
		for (int cell : cells) {
			if (cell >= 0 && cell < counts.length && ++counts[cell] >= 3) {
				best = cell;
			}
		}
		return best;
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
		card = input.read("card", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		ownerUuid = input.read("owner", UUIDUtil.CODEC).orElse(null);
		entityData.set(FACING, input.getFloatOr("facing", 0));
		ScratchData data = ScratchCardItem.data(card);
		if (data != null && card.getItem() instanceof ScratchCardItem item && level() instanceof ServerLevel level) {
			entityData.set(TIER, item.tier().ordinal());
			entityData.set(VILLAGE, data.name());
			entityData.set(CELLS, encode(ScratchCardItem.outcome(level, data).cells()));
			if (data.scratched()) {
				entityData.set(RESULT, LOST);
				doneAge = RETURN_AFTER;
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (!card.isEmpty()) {
			output.store("card", ItemStack.CODEC, card);
		}
		if (ownerUuid != null) {
			output.store("owner", UUIDUtil.CODEC, ownerUuid);
		}
		output.putFloat("facing", facing());
	}
}
