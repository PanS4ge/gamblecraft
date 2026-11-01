package pl.pansage.gamblecraft.entity;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.game.CasinoRegion;
import pl.pansage.gamblecraft.game.ChipCurrency;

/**
 * Hired muscle sent after one player (the victim): they go only for that player, take chips with every hit that
 * lands, and give up and slip away when the player is gone, dead, far off or back inside a casino. Whatever they
 * took drops when they are beaten. Their gear is theirs only: it never drops.
 */
public abstract class ThugEntity extends Monster {
	private static final int MAX_LIFE = 20 * 60 * 3;
	private static final double GIVE_UP_DISTANCE = 64;

	private UUID victim;
	private long stolen;
	private int life;
	private int leaving = -1;

	protected ThugEntity(EntityType<? extends ThugEntity> type, Level level) {
		super(type, level);
		this.xpReward = 10;
		setPersistenceRequired();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setDropChance(slot, 0.0F);
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 1, false, false,
				(target, level) -> leaving < 0 && target.getUUID().equals(victim)));
	}

	/** Puts {@code count} thugs on the ground 10 to 14 blocks behind {@code player} and sends them after them. */
	public static <T extends ThugEntity> java.util.List<T> send(ServerLevel level, ServerPlayer player, EntityType<T> type, int count) {
		java.util.List<T> sent = new java.util.ArrayList<>();
		Vec3 back = player.getLookAngle().multiply(-1, 0, -1).normalize();
		for (int i = 0; i < count; i++) {
			T thug = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (thug == null) {
				break;
			}
			for (int attempt = 0; attempt < 12; attempt++) {
				double angle = (i - (count - 1) / 2.0) * 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.6;
				double distance = 10 + level.getRandom().nextDouble() * 4;
				Vec3 dir = back.yRot((float) angle);
				int x = net.minecraft.util.Mth.floor(player.getX() + dir.x * distance);
				int z = net.minecraft.util.Mth.floor(player.getZ() + dir.z * distance);
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(y - player.getY()) > 8) {
					y = player.getBlockY();
				}
				thug.snapTo(x + 0.5, y, z + 0.5, player.getYRot(), 0);
				if (level.noCollision(thug)) {
					break;
				}
			}
			thug.hunt(player);
			level.addFreshEntity(thug);
			sent.add(thug);
		}
		return sent;
	}

	/** Sends this thug after {@code player}. */
	public void hunt(ServerPlayer player) {
		this.victim = player.getUUID();
		setTarget(player);
	}

	public UUID victim() {
		return victim;
	}

	public long stolen() {
		return stolen;
	}

	public boolean leaving() {
		return leaving >= 0;
	}

	/** Gives up: runs off and is gone in a few seconds. */
	public void leave() {
		if (leaving < 0) {
			leaving = 0;
			setTarget(null);
		}
	}

	/** What happens when a blow lands on the victim. */
	protected abstract void onHit(ServerLevel level, ServerPlayer victim);

	/** Takes up to {@code amount} chips off the victim; returns how many it got. */
	protected long snatch(ServerPlayer player, long amount) {
		long take = Math.min(amount, ChipCurrency.INSTANCE.balance(player));
		if (take > 0 && ChipCurrency.INSTANCE.withdraw(player, take)) {
			stolen += take;
			return take;
		}
		return 0;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof ServerPlayer player) {
			blowLanded(level, player);
		}
		return hit;
	}

	/** A blow landed on {@code player}: if that is the victim, the thug takes what it came for. */
	public void blowLanded(ServerLevel level, ServerPlayer player) {
		if (player.getUUID().equals(victim) && leaving < 0) {
			onHit(level, player);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		life++;
		if (leaving >= 0) {
			if (++leaving == 1 && victim != null) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(victim);
				if (player != null) {
					Vec3 away = position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(16).add(position());
					getNavigation().moveTo(away.x, away.y, away.z, 1.3);
				}
			}
			if (leaving > 80) {
				level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 10, 0.3, 0.6, 0.3, 0.02);
				discard();
			}
			return;
		}
		if (life % 20 == 0) {
			ServerPlayer player = victim == null ? null : level.getServer().getPlayerList().getPlayer(victim);
			if (player == null || !player.isAlive() || player.level() != level || player.distanceTo(this) > GIVE_UP_DISTANCE
					|| CasinoRegion.contains(level, player.blockPosition()) || life > MAX_LIFE) {
				leave();
			} else if (getTarget() == null) {
				setTarget(player);
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (stolen > 0) {
			for (var stack : ChipCurrency.INSTANCE.asStacks(stolen)) {
				spawnAtLocation(level, stack);
			}
			stolen = 0;
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (victim != null) {
			output.store("gamblecraft_victim", UUIDUtil.CODEC, victim);
		}
		output.putLong("gamblecraft_stolen", stolen);
		output.putInt("gamblecraft_life", life);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		victim = input.read("gamblecraft_victim", UUIDUtil.CODEC).orElse(null);
		stolen = input.getLongOr("gamblecraft_stolen", 0);
		life = input.getIntOr("gamblecraft_life", 0);
	}
}
