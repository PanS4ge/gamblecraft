package pl.pansage.gamblecraft.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import pl.pansage.gamblecraft.block.entity.SlotMachineBlockEntity;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.CasinoRegion;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.StashMaps;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * A regular who cannot leave the slot machines: he pulls the lever over and over and cheers or groans at the reels.
 * A mineral catches his eye (copper buys a little attention, netherite a lot): he follows the giver for thirty
 * seconds per tier. Walked far enough from the casino, he may come to his senses (the better the mineral, the
 * likelier), thank the player with what he has left (chips, a lucky coin, a gold watch, a pawn ticket, or for the
 * best minerals the map to his stash) and become an ordinary villager; or he bolts back to his machine.
 */
public class AddictedGamblerEntity extends CasinoNpc {
	public static final int FOLLOW_PER_TIER = 600;
	public static final int REWARD_DISTANCE = 48;
	private static final Map<Item, Integer> TIERS = Map.of(Items.COPPER_INGOT, 1, Items.IRON_INGOT, 2, Items.GOLD_INGOT, 3, Items.EMERALD, 4,
			Items.DIAMOND, 5, Items.NETHERITE_INGOT, 6);

	private UUID leader;
	private int tier;
	private long followUntil;
	private BoundingBox casino;
	private int nextPlay = 100;
	private SlotMachineBlockEntity watching;

	public AddictedGamblerEntity(EntityType<? extends AddictedGamblerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new FollowLeaderGoal());
		goalSelector.addGoal(3, new StayHomeGoal(1));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
	}

	public static int tierOf(ItemStack stack) {
		return TIERS.getOrDefault(stack.getItem(), 0);
	}

	public UUID leader() {
		return leader;
	}

	public int tier() {
		return tier;
	}

	@Override
	protected boolean isFree() {
		return leader == null;
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
		int offered = tierOf(held);
		if (offered == 0) {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.gambler.busy"));
			gesture(GESTURE_SULK);
			return InteractionResult.SUCCESS_SERVER;
		}
		held.consume(1, player);
		follow(serverPlayer, offered);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Takes the bait: follows {@code player} for thirty seconds per tier. */
	public void follow(ServerPlayer player, int offered) {
		boolean same = player.getUUID().equals(leader);
		leader = player.getUUID();
		tier = same ? Math.max(tier, offered) : offered;
		followUntil = player.level().getGameTime() + (long) offered * FOLLOW_PER_TIER;
		player.sendOverlayMessage(Component.translatable("gamblecraft.gambler.interested", offered * FOLLOW_PER_TIER / 20));
		gesture(GESTURE_CHEER);
		playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (casino == null && home != null && tickCount % 20 == 0) {
			casino = CasinoRegion.find(level, home).map(CasinoRegion.Casino::box).orElse(null);
		}
		if (leader != null) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(leader);
			if (player == null || player.level() != level || level.getGameTime() > followUntil) {
				if (player != null) {
					player.sendOverlayMessage(Component.translatable("gamblecraft.gambler.bored"));
				}
				leader = null;
			} else if (tickCount % 10 == 0 && casino != null && distanceTo(player) < 10
					&& !casino.inflatedBy(REWARD_DISTANCE).isInside(player.blockPosition())) {
				comeToSenses(level, player, getRandom());
			}
			return;
		}
		play(level);
	}

	/** At his machine: pulls the lever every few seconds and takes the result to heart. */
	private void play(ServerLevel level) {
		if (watching != null) {
			if (!watching.isRemoved() && watching.spinning()) {
				return;
			}
			gesture(!watching.isRemoved() && watching.lastWin() > 0 ? GESTURE_CHEER : GESTURE_SULK);
			watching = null;
		}
		if (--nextPlay > 0 || home == null) {
			return;
		}
		nextPlay = 120 + getRandom().nextInt(140);
		for (BlockPos pos : BlockPos.betweenClosed(blockPosition().offset(-2, 0, -2), blockPosition().offset(2, 0, 2))) {
			if (level.getBlockEntity(pos) instanceof SlotMachineBlockEntity machine && !machine.isUpper() && machine.npcSpin()) {
				getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
				gesture(GESTURE_DEAL);
				watching = machine;
				return;
			}
		}
	}

	/** Far from the casino with a friend: maybe he quits for good. */
	public void comeToSenses(ServerLevel level, ServerPlayer player, RandomSource random) {
		leader = null;
		if (random.nextDouble() < 0.15 + 0.12 * tier) {
			thank(level, player, random);
			recover(level, player);
		} else {
			player.sendOverlayMessage(Component.translatable("gamblecraft.gambler.relapse").withStyle(ChatFormatting.GRAY));
			gesture(GESTURE_SULK);
			if (home != null) {
				getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.2);
			}
		}
	}

	private void thank(ServerLevel level, ServerPlayer player, RandomSource random) {
		double roll = random.nextDouble();
		String kind;
		ItemStack gift;
		if (tier >= 5 && roll < 0.3) {
			kind = "map";
			gift = StashMaps.create(level, home != null ? home : blockPosition(), random);
		} else if (roll < 0.35) {
			kind = "chips";
			long chips = 50L * tier + random.nextInt(50 * tier + 1);
			ChipCurrency.INSTANCE.deposit(player, chips);
			player.sendSystemMessage(Component.translatable("gamblecraft.gambler.reward.chips", chips).withStyle(ChatFormatting.GOLD));
			return;
		} else if (roll < 0.55) {
			kind = "coin";
			gift = new ItemStack(ModItems.LUCKY_COIN);
		} else if (roll < 0.75) {
			kind = "watch";
			gift = new ItemStack(ModItems.GOLD_WATCH);
		} else {
			kind = "ticket";
			gift = new ItemStack(ModItems.PAWN_TICKET);
		}
		player.getInventory().placeItemBackInInventory(gift, net.minecraft.util.Prediction.SERVER_ONLY);
		player.sendSystemMessage(Component.translatable("gamblecraft.gambler.reward." + kind).withStyle(ChatFormatting.GOLD));
	}

	/** Done with gambling: an ordinary villager from now on. */
	private void recover(ServerLevel level, ServerPlayer player) {
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.CONVERSION);
		if (villager != null) {
			villager.snapTo(getX(), getY(), getZ(), getYRot(), getXRot());
			villager.setPersistenceRequired();
			level.addFreshEntity(villager);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 20, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("gamblecraft.gambler.recovered").withStyle(ChatFormatting.GREEN));
		discard();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (leader != null) {
			output.store("gamblecraft_leader", UUIDUtil.CODEC, leader);
		}
		output.putInt("gamblecraft_tier", tier);
		output.putLong("gamblecraft_follow_until", followUntil);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		leader = input.read("gamblecraft_leader", UUIDUtil.CODEC).orElse(null);
		tier = input.getIntOr("gamblecraft_tier", 0);
		followUntil = input.getLongOr("gamblecraft_follow_until", 0);
	}

	/** Keeps close behind the player who caught his interest. */
	private final class FollowLeaderGoal extends Goal {
		FollowLeaderGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return leader != null;
		}

		@Override
		public void tick() {
			Player player = level().getPlayerByUUID(leader);
			if (player == null) {
				return;
			}
			getLookControl().setLookAt(player);
			if (distanceTo(player) > 3) {
				getNavigation().moveTo(player, 1.0);
			} else {
				getNavigation().stop();
			}
		}
	}
}
