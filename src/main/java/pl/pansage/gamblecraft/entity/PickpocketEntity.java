package pl.pansage.gamblecraft.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.game.CasinoRegion;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.PayoutMailbox;

/**
 * A pickpocket passing for a guest. Stand still for ten seconds near one and your chips are lighter by a fifth or
 * less; he slips away glowing (security has spotted him). Landing a blow on him sets off no alarm: security walks him
 * out, everyone he robbed gets their chips back and whoever caught him a reward.
 */
public class PickpocketEntity extends CasinoNpc {
	public static final int STILL_TICKS = pl.pansage.gamblecraft.game.GambleConfig.get().pickpocketStillSeconds * 20;
	public static final long REWARD = 25;
	private static final double REACH = 1.8;

	private record Watch(Vec3 pos, int still) {
	}

	private final Map<UUID, Watch> watching = new HashMap<>();
	private final Map<UUID, Long> loot = new HashMap<>();
	private UUID mark;
	private int fleeing;

	public PickpocketEntity(EntityType<? extends PickpocketEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(5, new StayHomeGoal(10));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.45));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	@Override
	protected boolean isFree() {
		return mark == null && fleeing <= 0;
	}

	public Map<UUID, Long> loot() {
		return loot;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.pickpocket.talk"));
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level) || tickCount % 10 != 0) {
			return;
		}
		if (fleeing > 0) {
			fleeing -= 10;
			return;
		}
		if (mark != null) {
			ServerPlayer victim = level.getServer().getPlayerList().getPlayer(mark);
			if (victim == null || victim.isCreative() || victim.isSpectator() || victim.distanceTo(this) > 16) {
				mark = null;
			} else if (victim.distanceTo(this) < REACH) {
				steal(level, victim);
			} else {
				getNavigation().moveTo(victim, 0.7);
			}
			return;
		}
		// who has been standing still the longest?
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(12))) {
			if (player.isCreative() || player.isSpectator() || ChipCurrency.INSTANCE.balance(player) <= 0
					|| !CasinoRegion.contains(level, player.blockPosition())) {
				watching.remove(player.getUUID());
				continue;
			}
			Watch was = watching.get(player.getUUID());
			int still = was != null && was.pos().distanceToSqr(player.position()) < 0.09 ? was.still() + 10 : 0;
			watching.put(player.getUUID(), new Watch(player.position(), still));
			if (still >= STILL_TICKS) {
				mark = player.getUUID();
			}
		}
		watching.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
	}

	/** Lifts five to twenty per cent of the victim's chips and makes off. */
	public long steal(ServerLevel level, ServerPlayer victim) {
		mark = null;
		watching.remove(victim.getUUID());
		long balance = ChipCurrency.INSTANCE.balance(victim);
		long amount = Math.max(1, balance * (5 + getRandom().nextInt(16)) / 100);
		if (balance <= 0 || !ChipCurrency.INSTANCE.withdraw(victim, amount)) {
			return 0;
		}
		loot.merge(victim.getUUID(), amount, Long::sum);
		victim.sendOverlayMessage(Component.translatable("gamblecraft.pickpocket.robbed", amount).withStyle(ChatFormatting.RED));
		victim.sendSystemMessage(Component.translatable("gamblecraft.pickpocket.spotted").withStyle(ChatFormatting.GRAY));
		addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, false));
		addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 6, 1, false, false));
		Vec3 away = position().subtract(victim.position()).multiply(1, 0, 1).normalize().scale(14).add(position());
		getNavigation().moveTo(away.x, away.y, away.z, 1.1);
		fleeing = 100;
		return amount;
	}

	/** Caught: no alarm; the loot goes back and the catcher is rewarded. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer catcher) {
			long returned = 0;
			for (Map.Entry<UUID, Long> entry : loot.entrySet()) {
				ServerPlayer victim = catcher.getUUID().equals(entry.getKey()) ? catcher : level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (victim != null) {
					ChipCurrency.INSTANCE.deposit(victim, entry.getValue());
					victim.sendOverlayMessage(Component.translatable("gamblecraft.pickpocket.returned", entry.getValue()).withStyle(ChatFormatting.GREEN));
				} else {
					PayoutMailbox.get(level.getServer()).post(entry.getKey(), ChipCurrency.INSTANCE.asStacks(entry.getValue()));
				}
				returned += entry.getValue();
			}
			loot.clear();
			if (returned > 0) {
				ChipCurrency.INSTANCE.deposit(catcher, REWARD);
			}
			catcher.sendSystemMessage(Component.translatable(returned > 0 ? "gamblecraft.pickpocket.caught" : "gamblecraft.pickpocket.caught_empty",
					returned, REWARD).withStyle(ChatFormatting.GREEN));
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
			level.playSound(null, blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0F, 1.3F);
			discard();
			return true;
		}
		return super.hurtServer(level, source, amount);
	}

	private static final Codec<Map<UUID, Long>> LOOT_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("gamblecraft_loot", LOOT_CODEC, loot);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		loot.clear();
		input.read("gamblecraft_loot", LOOT_CODEC).ifPresent(loot::putAll);
	}
}
