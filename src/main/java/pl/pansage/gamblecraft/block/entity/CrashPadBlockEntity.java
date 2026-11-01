package pl.pansage.gamblecraft.block.entity;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.core.casino.ArcadeMath;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/**
 * Crash: everyone bets, the rocket lifts off and the multiplier climbs; cash out before it explodes. The server
 * keeps the explosion point to itself until it happens, so no client can know when to jump.
 */
public class CrashPadBlockEntity extends ArcadeBlockEntity {
	public static final String ROCKET = "rocket";
	/** Height of the pad's top, where the rocket stands. */
	public static final double PAD_TOP = 6 / 16.0;
	private static final double ROCKET_LENGTH = 0.75;
	private static final double MAX_HEIGHT = 12;
	private static final List<String> OPTIONS = List.of(ROCKET);

	/** Where the rocket explodes, times 100. Not sent to clients before the explosion. */
	private int crashAt = 100;

	public CrashPadBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRASH_PAD, pos, state);
	}

	@Override
	public List<String> options() {
		return OPTIONS;
	}

	@Override
	public double payout(String option) {
		return 0;
	}

	@Override
	protected int bettingTicks() {
		return 200;
	}

	@Override
	protected int playTicks() {
		return (int) Math.ceil(ArcadeMath.crashSecondsTo(crashAt / 100.0) * 20) + 1;
	}

	@Override
	public String titleKey() {
		return "block.gamblecraft.crash_pad";
	}

	/** The multiplier {@code t} ticks after lift-off. */
	public static double multiplier(double t) {
		return ArcadeMath.crashMultiplierAt(Math.max(0, t) / 20.0);
	}

	/** Known to clients only once the rocket has exploded. */
	public double crashedAt() {
		return crashAt / 100.0;
	}

	@Override
	protected void startPlay(ServerLevel level) {
		crashAt = (int) Math.round(ArcadeMath.crashPoint(new java.util.SplittableRandom(level.getRandom().nextLong())) * 100);
		level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.0F, 0.8F);
	}

	@Override
	protected boolean playOver(long now) {
		return multiplier(now - phaseStart) * 100 >= crashAt;
	}

	@Override
	protected void tick(ServerLevel level) {
		if (phase == Phase.PLAYING) {
			long t = level.getGameTime() - phaseStart;
			if (t % 10 == 0) {
				level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.5F);
			}
			if (t % 2 == 0) {
				Vec3 at = Vec3.atBottomCenterOf(worldPosition).add(0, rocketY(level, worldPosition, multiplier(t)) - 0.1, 0);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.03, 0.02, 0.03, 0.01);
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y - 0.1, at.z, 3, 0.05, 0.05, 0.05, 0.01);
			}
		}
		super.tick(level);
	}

	/** Takes the player's chips off the rocket at the current multiplier. */
	public void cashOut(ServerPlayer player) {
		if (phase != Phase.PLAYING) {
			return;
		}
		double now = multiplier(level.getGameTime() - phaseStart);
		if (now * 100 >= crashAt) {
			return;
		}
		long won = 0;
		for (int i = 0; i < stakes.size(); i++) {
			Stake s = stakes.get(i);
			if (s.player().equals(player.getUUID()) && s.cashedOut() == 0) {
				int at = (int) Math.round(now * 100);
				stakes.set(i, new Stake(s.player(), s.option(), s.amount(), at));
				won += (long) Math.floor(s.amount() * at / 100.0);
			}
		}
		if (won > 0) {
			won += pl.pansage.gamblecraft.game.Luck.bonus(player, won, level.getRandom());
			pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).won(player.getUUID(), won);
			ChipCurrency.INSTANCE.deposit(player, won);
			player.sendOverlayMessage(Component.translatable("gamblecraft.crash.cashed", String.format(Locale.ROOT, "%.2f", now), won));
			pl.pansage.gamblecraft.game.Celebrate.win(player.level(), player, won, stakeOf(player.getUUID()), player.position(), false);
			sync();
		}
	}

	/** Cashed-out stakes were paid already; the rest went up with the rocket. */
	@Override
	protected long returnOf(Stake stake) {
		return 0;
	}

	@Override
	protected void settle(ServerLevel level) {
		phase = Phase.RESULT;
		phaseStart = level.getGameTime();
		Vec3 boom = Vec3.atBottomCenterOf(worldPosition).add(0, rocketY(level, worldPosition, crashAt / 100.0) + 0.4, 0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boom.x, boom.y, boom.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLAME, boom.x, boom.y, boom.z, 40, 0.5, 0.5, 0.5, 0.15);
		level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.2F);
		for (Stake stake : stakes) {
			if (stake.cashedOut() == 0) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(stake.player());
				if (player != null) {
					player.sendOverlayMessage(Component.translatable("gamblecraft.crash.boom", String.format(Locale.ROOT, "%.2f", crashAt / 100.0)));
				}
			}
		}
		sync();
	}

	/** How high (blocks above the pad) the rocket is at a multiplier: fast at first, then slower. */
	public static double rocketHeight(double multiplier) {
		return Math.min(MAX_HEIGHT, Math.log(multiplier) * 6);
	}

	/** How far the rocket can climb before its nose meets the first block above the pad. */
	public static double flightRoom(Level level, BlockPos pos) {
		for (int i = 1; i <= MAX_HEIGHT + 1; i++) {
			if (!level.getBlockState(pos.above(i)).getCollisionShape(level, pos.above(i)).isEmpty()) {
				return Math.max(0, i - PAD_TOP - ROCKET_LENGTH);
			}
		}
		return MAX_HEIGHT;
	}

	/** The rocket's base above the block's bottom: its climb, stopped by the ceiling. */
	public static double rocketY(Level level, BlockPos pos, double multiplier) {
		return PAD_TOP + Math.min(rocketHeight(multiplier), flightRoom(level, pos));
	}

	@Override
	public Component outcome() {
		return Component.translatable("gamblecraft.crash.boom", String.format(Locale.ROOT, "%.2f", crashAt / 100.0));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("crash_at", crashAt);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		crashAt = input.getIntOr("crash_at", 100);
	}

	/** Clients learn the explosion point only after it happened. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		if (phase == Phase.PLAYING) {
			tag.remove("crash_at");
		}
		return tag;
	}
}
