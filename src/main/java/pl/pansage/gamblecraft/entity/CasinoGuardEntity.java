package pl.pansage.gamblecraft.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.game.CasinoSecurity;

/**
 * A casino security guard: a hulking figure in a black suit and dark glasses, called out by the alarm. Guards go for
 * the offenders with twice an iron golem's punch and cannot be knocked back. Anyone out of reach (up a pillar, on a
 * ledge, behind a gap) gets the shockwave: a second and a half of charging, then a sonic beam that knocks out the
 * blocks they built under themselves and slams them down. When the alarm ends, guards walk back to the security room.
 */
public class CasinoGuardEntity extends PathfinderMob {
	public static final int ACTION_PUNCH = 1;
	public static final int ACTION_CHARGE = 2;
	public static final int ACTION_FIRE = 3;
	public static final int CHARGE_TICKS = 30;
	public static final float SHOCKWAVE_DAMAGE = 8.0F;
	private static final int SHOCKWAVE_COOLDOWN = 60;
	private static final int SHOCKWAVE_RANGE = 20;
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(CasinoGuardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTIONS = SynchedEntityData.defineId(CasinoGuardEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_ELITE = SynchedEntityData.defineId(CasinoGuardEntity.class, EntityDataSerializers.BOOLEAN);

	public final AnimationState punchAnimation = new AnimationState();
	public final AnimationState chargeAnimation = new AnimationState();
	public final AnimationState fireAnimation = new AnimationState();
	private String casino = "";
	private BlockPos post;
	private boolean standingDown;
	private int standDownTicks;
	private int shockwaveCooldown;
	private int shockwaves;

	public CasinoGuardEntity(EntityType<? extends CasinoGuardEntity> type, Level level) {
		super(type, level);
		this.xpReward = 20;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 200.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 30.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.ARMOR, 6.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, 0);
		builder.define(DATA_ACTIONS, 0);
		builder.define(DATA_ELITE, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new ShockwaveGoal());
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
		goalSelector.addGoal(3, new StandDownGoal());
		goalSelector.addGoal(4, new HoldPostGoal());
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 5, false, false,
				(target, level) -> !standingDown && target instanceof Player player && CasinoSecurity.get(level.getServer()).isOffender(casino, player)));
	}

	/** The boss's own guards: tougher, in gold-trimmed suits, standing at their post in the VIP room. */
	public boolean elite() {
		return entityData.get(DATA_ELITE);
	}

	public void setElite(boolean elite) {
		entityData.set(DATA_ELITE, elite);
		if (elite) {
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0);
			getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(36.0);
			getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34);
			setHealth(getMaxHealth());
		}
	}

	/** Puts the guard on an alarm of {@code casino}, to return to {@code post} (may be null) afterwards. */
	public void onDuty(String casino, BlockPos post) {
		this.casino = casino;
		this.post = post;
	}

	public String casino() {
		return casino;
	}

	/** The alarm is over: back to the security room, and gone. */
	public void standDown() {
		standingDown = true;
		setTarget(null);
	}

	public boolean standingDown() {
		return standingDown;
	}

	/** The action last played (punch, charge, fire), for tests. */
	public int action() {
		return entityData.get(DATA_ACTION);
	}

	/** Shockwaves fired so far. */
	public int shockwaves() {
		return shockwaves;
	}

	/** For tests: what the shockwave goal thinks right now. */
	public String shockwaveState() {
		LivingEntity target = getTarget();
		return "cooldown " + shockwaveCooldown + " target " + (target == null ? "none" : String.format("dist %.1f rise %.1f sees %s", distanceTo(target),
				target.getY() - getY(), hasLineOfSight(target))) + " goals " + goalSelector.getAvailableGoals().stream()
				.filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList();
	}

	private void act(int action) {
		entityData.set(DATA_ACTION, action);
		entityData.set(DATA_ACTIONS, entityData.get(DATA_ACTIONS) + 1);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (key == DATA_ACTIONS && level().isClientSide()) {
			switch (entityData.get(DATA_ACTION)) {
				case ACTION_PUNCH -> punchAnimation.start(tickCount);
				case ACTION_CHARGE -> {
					fireAnimation.stop();
					chargeAnimation.start(tickCount);
				}
				case ACTION_FIRE -> {
					chargeAnimation.stop();
					fireAnimation.start(tickCount);
				}
				default -> chargeAnimation.stop();
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (shockwaveCooldown > 0) {
			shockwaveCooldown--;
		}
		// a guard without an alarm (the boss's, or one from an egg) keeps to where he was put
		if (casino.isEmpty() && post == null && onGround()) {
			post = blockPosition();
		}
		// guards called out by an alarm leave with it; one hatched from an egg stays put
		if (!casino.isEmpty() && !standingDown && tickCount % 20 == 0 && CasinoSecurity.get(level.getServer()).alarm(casino).isEmpty()) {
			standDown();
		}
		if (standingDown) {
			standDownTicks++;
			if (post == null || standDownTicks > 600 || distanceToSqr(Vec3.atBottomCenterOf(post)) < 4) {
				level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.2, getZ(), 12, 0.4, 0.8, 0.4, 0.02);
				discard();
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		act(ACTION_PUNCH);
		float base = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
		float damage = (int) base > 0 ? base / 2 + random.nextInt((int) base) : base;
		DamageSource source = damageSources().mobAttack(this);
		boolean hit = target.hurtServer(level, source, damage);
		if (hit) {
			double resist = target instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
			target.setDeltaMovement(target.getDeltaMovement().add(0, 0.4 * Math.max(0, 1 - resist), 0));
			sendMotion(target);
			EnchantmentHelper.doPostAttackEffects(level, target, source);
		}
		playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.9F);
		return hit;
	}

	/** Players move themselves: a push from the server has to be handed to their client. */
	public static void sendMotion(Entity target) {
		if (target instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		} else {
			target.needsSync = true;
		}
	}

	/** The sonic beam: knocks out what the target built under itself and slams it down and away. */
	private void fire(LivingEntity target) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		act(ACTION_FIRE);
		shockwaves++;
		Vec3 from = position().add(0, getBbHeight() * 0.7, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
		Vec3 dir = to.subtract(from).normalize();
		// the beam stops short of the target, so it does not fill the target's own view
		int steps = Mth.floor(from.distanceTo(to)) - 1;
		for (int i = 1; i < steps; i++) {
			Vec3 at = from.add(dir.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.1F);
		if (!hasLineOfSight(target)) {
			return;
		}
		target.hurtServer(level, damageSources().sonicBoom(this), SHOCKWAVE_DAMAGE);
		CasinoSecurity.get(level.getServer()).crumbleUnder(level, casino, target);
		boolean high = !target.onGround() || target.getY() - getY() > 2.5;
		Vec3 push = new Vec3(dir.x, 0, dir.z).normalize().scale(0.9);
		target.setDeltaMovement(push.x, high ? -1.8 : 0.35, push.z);
		target.stopFallFlying();
		sendMotion(target);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.security.guard_talk"));
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 1.0F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("gamblecraft_casino", casino);
		if (post != null) {
			output.store("gamblecraft_post", BlockPos.CODEC, post);
		}
		output.putBoolean("gamblecraft_standing_down", standingDown);
		output.putBoolean("gamblecraft_elite", elite());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		casino = input.getStringOr("gamblecraft_casino", "");
		post = input.read("gamblecraft_post", BlockPos.CODEC).orElse(null);
		standingDown = input.getBooleanOr("gamblecraft_standing_down", false);
		setElite(input.getBooleanOr("gamblecraft_elite", false));
	}

	/** Charges and fires the shockwave at a target the guard cannot reach on foot. */
	private final class ShockwaveGoal extends Goal {
		private LivingEntity aim;
		private int charge;
		private int reachCheck;
		private boolean reachable = true;

		ShockwaveGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			if (target == null || !target.isAlive() || shockwaveCooldown > 0 || standingDown) {
				return false;
			}
			double distance = distanceTo(target);
			if (distance > SHOCKWAVE_RANGE || !hasLineOfSight(target)) {
				return false;
			}
			if (target.getY() - getY() > 2.5) {
				return true;
			}
			if (distance < 5) {
				return false;
			}
			if (--reachCheck <= 0) {
				reachCheck = 20;
				Path path = getNavigation().createPath(target, 1);
				reachable = path != null && path.canReach();
			}
			return !reachable;
		}

		@Override
		public void start() {
			aim = getTarget();
			charge = CHARGE_TICKS;
			getNavigation().stop();
			act(ACTION_CHARGE);
			playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
		}

		@Override
		public boolean canContinueToUse() {
			return charge > 0 && aim != null && aim.isAlive() && !standingDown;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			getLookControl().setLookAt(aim, 30.0F, 30.0F);
			getNavigation().stop();
			if (charge % 4 == 0 && level() instanceof ServerLevel level) {
				// sparks gathering at the levelled hand
				Vec3 hand = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(1.6)).add(0, 1.9, 0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 4, 0.15, 0.15, 0.15, 0.05);
			}
			if (--charge == 0) {
				fire(aim);
			}
		}

		@Override
		public void stop() {
			if (charge > 0) {
				// the target got away or fell: lower the arm
				act(0);
			}
			shockwaveCooldown = SHOCKWAVE_COOLDOWN;
			reachCheck = 0;
			aim = null;
		}
	}

	/** With nobody to chase, back to the post. */
	private final class HoldPostGoal extends Goal {
		HoldPostGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !standingDown && casino.isEmpty() && post != null && getTarget() == null && distanceToSqr(Vec3.atBottomCenterOf(post)) > 4;
		}

		@Override
		public void start() {
			getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.8);
		}

		@Override
		public boolean canContinueToUse() {
			return getTarget() == null && !getNavigation().isDone();
		}
	}

	/** Walks back to the security room once the alarm is over. */
	private final class StandDownGoal extends Goal {
		StandDownGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return standingDown && post != null;
		}

		@Override
		public void tick() {
			if (getNavigation().isDone()) {
				getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
			}
		}
	}
}
