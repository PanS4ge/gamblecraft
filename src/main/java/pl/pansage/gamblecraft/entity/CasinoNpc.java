package pl.pansage.gamblecraft.entity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import pl.pansage.gamblecraft.network.TablePayloads.DealerGesture;

/**
 * Base for GambleCraft's villager-like people (dealers, clerks...): villager body and sounds, no vanilla trading,
 * a home spot they wander back to, and gesture animations (shuffle, deal, cheer, sulk) played on every client.
 */
public abstract class CasinoNpc extends AbstractVillager {
	/** Gestures sent with {@link DealerGesture}; vanilla entity event ids are not free to reuse in 26.3. */
	public static final int GESTURE_SHUFFLE = 0;
	public static final int GESTURE_DEAL = 1;
	public static final int GESTURE_CHEER = 2;
	public static final int GESTURE_SULK = 3;

	public final AnimationState shuffleAnimation = new AnimationState();
	public final AnimationState dealAnimation = new AnimationState();
	public final AnimationState cheerAnimation = new AnimationState();
	public final AnimationState sulkAnimation = new AnimationState();
	/** Client: head-shake until this tick. */
	public int sulkUntil;
	protected BlockPos home;

	protected CasinoNpc(EntityType<? extends CasinoNpc> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.5);
	}

	public void setHome(BlockPos home) {
		this.home = home;
	}

	public BlockPos home() {
		return home;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (home == null && !level().isClientSide() && onGround()) {
			// People placed by structures remember where they were put on their first steps.
			home = blockPosition();
		}
	}

	/** Plays a gesture on every client that sees this NPC. */
	public void gesture(int gesture) {
		if (level() instanceof ServerLevel) {
			DealerGesture packet = new DealerGesture(getId(), gesture);
			for (ServerPlayer player : PlayerLookup.tracking(this)) {
				ServerPlayNetworking.send(player, packet);
			}
		}
	}

	/** Client side of {@link #gesture(int)}. */
	public void playGesture(int gesture) {
		switch (gesture) {
			case GESTURE_SHUFFLE -> shuffleAnimation.start(tickCount);
			case GESTURE_DEAL -> dealAnimation.start(tickCount);
			case GESTURE_CHEER -> cheerAnimation.start(tickCount);
			case GESTURE_SULK -> {
				sulkAnimation.start(tickCount);
				sulkUntil = tickCount + 40;
			}
			default -> {
			}
		}
	}

	/** Walks back home when it strays further than {@code range} blocks and nothing else keeps it busy. */
	protected final class StayHomeGoal extends Goal {
		private final int range;

		public StayHomeGoal(int range) {
			this.range = range;
			setFlags(java.util.EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return home != null && isFree() && blockPosition().distSqr(home) > (double) range * range;
		}

		@Override
		public void start() {
			getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.5);
		}

		@Override
		public boolean canContinueToUse() {
			return !getNavigation().isDone() && home != null;
		}
	}

	/** Whether the NPC may wander off (not busy with players). */
	protected boolean isFree() {
		return true;
	}

	// ---------------------------------------------------------------- villager plumbing

	@Override
	protected void updateTrades(ServerLevel level) {
	}

	@Override
	protected void rewardTradeXp(MerchantOffer offer) {
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.VILLAGER_YES;
	}

	@Override
	protected SoundEvent getTradeUpdatedSound(boolean success) {
		return success ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (home != null) {
			output.putLong("gamblecraft_home", home.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getLong("gamblecraft_home").ifPresent(pos -> home = BlockPos.of(pos));
	}
}
