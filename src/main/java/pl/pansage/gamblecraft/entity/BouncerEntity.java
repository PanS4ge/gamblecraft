package pl.pansage.gamblecraft.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.game.CasinoRegion;
import pl.pansage.gamblecraft.game.CasinoSecurity;
import pl.pansage.gamblecraft.game.Suit;

/**
 * The doorman outside the casino. He stands facing the street: anyone near the door without a full suit gets
 * turned back, and anyone found inside without one is walked out to the pavement.
 */
public class BouncerEntity extends CasinoNpc {
	private static final double TURN_BACK_RANGE = 2.6;
	private static final int WATCH_RANGE = 48;
	private static final long TALK_COOLDOWN = 100;

	/** The way he faces: out of the casino, towards the street. Fixed on his first tick. */
	private float guardYaw = Float.NaN;
	private final Map<UUID, Long> lastWarned = new HashMap<>();

	public BouncerEntity(EntityType<? extends BouncerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new StayHomeGoal(1));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	/** Unit vector from the door into the casino. */
	public Vec3 inward() {
		double rad = Math.toRadians(guardYaw);
		return new Vec3(Math.sin(rad), 0, -Math.cos(rad));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (Float.isNaN(guardYaw)) {
			guardYaw = getYRot();
		}
		if (tickCount % 10 != 0 || home() == null) {
			return;
		}
		CasinoSecurity security = CasinoSecurity.get(level.getServer());
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(WATCH_RANGE))) {
			boolean banned = security.banned(level, player);
			if (player.isCreative() || player.isSpectator() || Suit.isWearing(player) && !banned) {
				continue;
			}
			Vec3 fromDoor = player.position().subtract(Vec3.atBottomCenterOf(home()));
			double depth = fromDoor.dot(inward());
			Optional<CasinoRegion.Casino> inside = CasinoRegion.find(level, player.blockPosition());
			if (depth > 1.5 && inside.isPresent()) {
				// while the alarm sounds an offender is the guards' business
				if (!security.isOffender(inside.get().key(), player)) {
					escort(level, player, banned);
				}
			} else if (player.distanceTo(this) < TURN_BACK_RANGE) {
				turnBack(level, player, banned);
			}
		}
	}

	/** A shove back towards the street and a word about the dress code. */
	private void turnBack(ServerLevel level, ServerPlayer player, boolean banned) {
		Vec3 out = inward().scale(-1);
		player.setDeltaMovement(out.x * 0.7, 0.3, out.z * 0.7);
		// the client moves its own player: hand it the shove directly
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		getLookControl().setLookAt(player);
		warn(level, player, banned ? "gamblecraft.casino.banned" : "gamblecraft.casino.turned_back");
	}

	/** Walks a guest without a suit out: back to the pavement in front of the door. */
	private void escort(ServerLevel level, ServerPlayer player, boolean banned) {
		Vec3 spot = Vec3.atBottomCenterOf(home()).add(inward().scale(-3));
		BlockPos ground = BlockPos.containing(spot);
		player.teleportTo(level, spot.x, ground.getY(), spot.z, java.util.Set.<Relative>of(), guardYaw, 0, true);
		level.playSound(null, ground, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.6F);
		warn(level, player, banned ? "gamblecraft.casino.banned" : "gamblecraft.casino.escorted");
	}

	private void warn(ServerLevel level, ServerPlayer player, String key) {
		long now = level.getGameTime();
		Long last = lastWarned.get(player.getUUID());
		if (last == null || now - last > TALK_COOLDOWN) {
			lastWarned.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable(key));
			gesture(GESTURE_SULK);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable(Suit.isWearing(player) ? "gamblecraft.casino.welcome" : "gamblecraft.casino.dress_code"));
			gesture(Suit.isWearing(player) ? GESTURE_CHEER : GESTURE_SULK);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (!Float.isNaN(guardYaw)) {
			output.putFloat("gamblecraft_guard_yaw", guardYaw);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		guardYaw = input.getFloatOr("gamblecraft_guard_yaw", Float.NaN);
	}
}
